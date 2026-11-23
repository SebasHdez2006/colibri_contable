package sv.edu.ues.colibri.application.cierre;

import sv.edu.ues.colibri.application.auth.AuthService;
import sv.edu.ues.colibri.application.configuracion.ParametrizacionService;
import sv.edu.ues.colibri.application.partida.PartidaService;
import sv.edu.ues.colibri.application.periodo.PeriodoAppService;
import sv.edu.ues.colibri.domain.exception.DomainException;
import sv.edu.ues.colibri.domain.model.EstadoPartida;
import sv.edu.ues.colibri.domain.model.Partida;
import sv.edu.ues.colibri.domain.model.PartidaDetalle;
import sv.edu.ues.colibri.domain.model.Periodo;
import sv.edu.ues.colibri.domain.model.TipoPartida;
import sv.edu.ues.colibri.persistence.jdbc.JdbcSupport;
import sv.edu.ues.colibri.persistence.jdbc.PersistenceException;
import sv.edu.ues.colibri.persistence.repo.PartidaRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Ajustes frecuentes, estimaciones y cierre mensual.
 *
 * <p>Los eventos de ajuste usan una clave estable por período. Con ello el
 * contador puede reintentar una operación sin duplicar partidas, cuotas de
 * depreciación, amortización ni provisiones.</p>
 */
public class CierreService {

    private final JdbcSupport jdbc;
    private final AuthService auth;
    private final PartidaService partidas;
    private final PeriodoAppService periodos;
    private final ParametrizacionService parametrizacion;
    private final PartidaRepository partidaRepository = new PartidaRepository();

    /**
     * Crea el servicio de ajustes y cierre.
     *
     * @param jdbc soporte transaccional
     * @param auth servicio de autenticación
     * @param partidas servicio de partidas
     * @param periodos servicio de períodos
     * @param parametrizacion servicio de parametrización
     */
    public CierreService(JdbcSupport jdbc, AuthService auth, PartidaService partidas,
                         PeriodoAppService periodos, ParametrizacionService parametrizacion) {
        this.jdbc = jdbc;
        this.auth = auth;
        this.partidas = partidas;
        this.periodos = periodos;
        this.parametrizacion = parametrizacion;
    }

    public record IntangibleInfo(UUID id, String codigo, String nombre, BigDecimal costo,
                                 int vidaMeses, LocalDate fechaUso, BigDecimal valorResidual,
                                 BigDecimal amortizacionAcumulada, boolean activo) {
    }

    public record ActivoFijoInfo(UUID id, String codigo, String nombre, BigDecimal costo,
                                 int vidaMeses, LocalDate fechaUso, BigDecimal valorResidual,
                                 BigDecimal depreciacionAcumulada, boolean activo) {
    }

    private record CuotaActivo(UUID activoId, String cuentaGasto, String cuentaAcumulada,
                               BigDecimal importe) {
    }

    /**
     * El importe bruto del lote se conserva separado de la corrección acumulada.
     * Así la evaluación posterior se compara contra el valor neto, no vuelve a
     * castigar el mismo costo cada mes.
     */
    private record LoteDeterioro(UUID id, BigDecimal valorBruto, BigDecimal deterioroAcumulado) {
        BigDecimal valorEnLibros() {
            return valorBruto.subtract(deterioroAcumulado).max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
        }
    }

    /** Cédula de cartera creada antes de la partida y vinculada después de contabilizarla. */
    private record EstimacionSnapshot(BigDecimal base, BigDecimal porcentaje, BigDecimal objetivo,
                                      UUID partidaId) {
    }

    /** Cédula de valor neto realizable creada antes de la partida correspondiente. */
    private record DeterioroSnapshot(BigDecimal costoEnLibros, BigDecimal valorRecuperable,
                                     BigDecimal ajuste, UUID partidaId) {
        boolean esReversion() {
            return ajuste.signum() < 0;
        }
    }

    /**
     * Genera el ajuste de devengo de SaaS.
     *
     * @param importe importe del ajuste
     * @param fecha fecha contable
     * @return partida generada
     */
    public Partida ajusteDevengoSaas(BigDecimal importe, LocalDate fecha) {
        BigDecimal monto = importePositivo(importe, "El importe del devengo SaaS");
        return asiento(fecha, "A1 Devengo SaaS del mes", "AJ-A1-" + fecha,
                List.of(
                        PartidaDetalle.nueva("210702", monto, BigDecimal.ZERO),
                        PartidaDetalle.nueva("410402", BigDecimal.ZERO, monto)
                ));
    }

    /**
     * Genera el ajuste por consumo de nube.
     *
     * @param importe importe del ajuste
     * @param fecha fecha contable
     * @return partida generada
     */
    public Partida ajusteConsumoNube(BigDecimal importe, LocalDate fecha) {
        BigDecimal monto = importePositivo(importe, "El importe del consumo de nube");
        return asiento(fecha, "A2 Consumo nube", "AJ-A2-" + fecha,
                List.of(
                        PartidaDetalle.nueva("510201", monto, BigDecimal.ZERO),
                        PartidaDetalle.nueva("110401", BigDecimal.ZERO, monto)
                ));
    }

    /**
     * Genera el ajuste por consumo de renta.
     *
     * @param importe importe del ajuste
     * @param fecha fecha contable
     * @return partida generada
     */
    public Partida ajusteConsumoRenta(BigDecimal importe, LocalDate fecha) {
        BigDecimal monto = importePositivo(importe, "El importe del consumo de renta");
        return asiento(fecha, "A3 Consumo renta", "AJ-A3-" + fecha,
                List.of(
                        PartidaDetalle.nueva("610102", monto, BigDecimal.ZERO),
                        PartidaDetalle.nueva("110402", BigDecimal.ZERO, monto)
                ));
    }

    /** F-AJU-02: consumo proporcional de un pago diferido. */
    /**
     * Reconoce el consumo proporcional de un pago diferido.
     *
     * @param importePagado importe total pagado
     * @param mesesCubiertos meses cubiertos por el pago
     * @param mesesConsumidos meses a reconocer
     * @param cuentaGasto cuenta de gasto
     * @param cuentaDiferido cuenta diferida
     * @param fecha fecha contable
     * @return partida generada
     */
    public Partida ajusteConsumoDiferido(BigDecimal importePagado, int mesesCubiertos,
                                         int mesesConsumidos, String cuentaGasto,
                                         String cuentaDiferido, LocalDate fecha) {
        validarEnteroPositivo(mesesCubiertos, "Los meses cubiertos");
        if (mesesConsumidos <= 0 || mesesConsumidos > mesesCubiertos) {
            throw new DomainException("Los meses consumidos deben estar entre 1 y los meses cubiertos.");
        }
        BigDecimal importe = importePositivo(importePagado, "El importe pagado")
                .multiply(BigDecimal.valueOf(mesesConsumidos))
                .divide(BigDecimal.valueOf(mesesCubiertos), 2, RoundingMode.HALF_UP);
        return asiento(fecha, "Consumo de pago diferido", "AJ-DIF-" + fecha + "-" + cuentaDiferido,
                List.of(PartidaDetalle.nueva(cuentaGasto, importe, BigDecimal.ZERO),
                        PartidaDetalle.nueva(cuentaDiferido, BigDecimal.ZERO, importe)));
    }

    /**
     * Genera un ajuste por gasto devengado.
     *
     * @param importe importe del ajuste
     * @param fecha fecha contable
     * @return partida generada
     */
    public Partida ajusteGastoDevengado(BigDecimal importe, LocalDate fecha) {
        BigDecimal monto = importePositivo(importe, "El importe del gasto devengado");
        return asiento(fecha, "A4 Servicios públicos devengados", "AJ-A4-" + fecha,
                List.of(
                        PartidaDetalle.nueva("610102", monto, BigDecimal.ZERO),
                        PartidaDetalle.nueva("210802", BigDecimal.ZERO, monto)
                ));
    }

    /** F-AJU-01: capital × tasa anual × meses / 12. */
    /**
     * Reconoce el interés acumulado de un período.
     *
     * @param capital capital base
     * @param tasaAnual tasa anual
     * @param meses meses devengados
     * @param fecha fecha contable
     * @return partida generada
     */
    public Partida ajusteInteresAcumulado(BigDecimal capital, BigDecimal tasaAnual,
                                          int meses, LocalDate fecha) {
        validarTasa(tasaAnual, "La tasa anual");
        validarEnteroPositivo(meses, "Los meses devengados");
        BigDecimal importe = importePositivo(capital, "El capital")
                .multiply(tasaAnual)
                .multiply(BigDecimal.valueOf(meses))
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        var perfil = parametrizacion.exigirPerfilPosting("INTERES_DEVENGADO");
        return asiento(fecha, "Interés acumulado", "AJ-INT-" + fecha,
                List.of(PartidaDetalle.nueva(perfil.cuentaDebe(), importe, BigDecimal.ZERO),
                        PartidaDetalle.nueva(perfil.cuentaHaber(), BigDecimal.ZERO, importe)));
    }

    /** F-AJU-09: ingreso total × período devengado / período total. */
    /**
     * Reconoce un ingreso devengado aún no facturado.
     *
     * @param ingresoTotal ingreso total del contrato
     * @param unidadesDevengadas unidades devengadas
     * @param unidadesTotales unidades totales de cobertura
     * @param cuentaIngreso cuenta de ingreso
     * @param fecha fecha contable
     * @return partida generada
     */
    public Partida ajusteIngresoDevengado(BigDecimal ingresoTotal, int unidadesDevengadas,
                                          int unidadesTotales, String cuentaIngreso,
                                          LocalDate fecha) {
        validarEnteroPositivo(unidadesTotales, "Las unidades totales de cobertura");
        if (unidadesDevengadas <= 0 || unidadesDevengadas > unidadesTotales) {
            throw new DomainException("Las unidades devengadas deben estar entre 1 y el total de cobertura.");
        }
        BigDecimal importe = importePositivo(ingresoTotal, "El ingreso total")
                .multiply(BigDecimal.valueOf(unidadesDevengadas))
                .divide(BigDecimal.valueOf(unidadesTotales), 2, RoundingMode.HALF_UP);
        var perfil = parametrizacion.exigirPerfilPosting("INGRESO_DEVENGADO");
        String ingreso = parametrizacion.resolverCuenta(cuentaIngreso, perfil.cuentaHaber(),
                "cuenta de ingreso devengado");
        return asiento(fecha, "Ingreso acumulado no facturado", "AJ-ING-" + fecha + "-" + ingreso,
                List.of(PartidaDetalle.nueva(perfil.cuentaDebe(), importe, BigDecimal.ZERO),
                        PartidaDetalle.nueva(ingreso, BigDecimal.ZERO, importe)));
    }

    /** A5: liquida IVA débito contra crédito; el remanente permanece como crédito fiscal. */
    /**
     * Liquida el IVA entre débito y crédito fiscal.
     *
     * @param desde fecha inicial
     * @param hasta fecha final
     * @return partida generada
     */
    public Partida liquidarIva(LocalDate desde, LocalDate hasta) {
        auth.exigirContador();
        validarRango(desde, hasta);
        String clave = "AJ-A5-" + desde + "-" + hasta;
        Optional<Partida> existente = partidaContabilizada(clave);
        if (existente.isPresent()) {
            return existente.get();
        }
        var fiscal = parametrizacion.exigirParametrosFiscales();
        BigDecimal[] saldos = jdbc.inTransaction(conn -> {
            // Se toma el saldo no liquidado acumulado a la fecha. De esta forma
            // un crédito fiscal remanente de meses anteriores compensa el débito
            // corriente, como exige F-IVA-06.
            BigDecimal debito = saldoCuentaHasta(conn, fiscal.cuentaDebito(), hasta, true);
            BigDecimal credito = saldoCuentaHasta(conn, fiscal.cuentaCredito(), hasta, false);
            return new BigDecimal[]{debito, credito};
        });
        BigDecimal debito = saldos[0].max(BigDecimal.ZERO);
        BigDecimal credito = saldos[1].max(BigDecimal.ZERO);
        if (debito.signum() <= 0) {
            String remanente = credito.signum() > 0
                    ? "; el crédito fiscal de $" + credito + " se arrastra al siguiente período"
                    : "";
            throw new DomainException("No hay IVA débito pendiente para liquidar" + remanente + ".");
        }
        BigDecimal creditoAplicado = debito.min(credito);
        BigDecimal porPagar = debito.subtract(creditoAplicado);
        List<PartidaDetalle> lineas;
        List<PartidaDetalle> calculadas = new ArrayList<>();
        calculadas.add(PartidaDetalle.nueva(fiscal.cuentaDebito(), debito, BigDecimal.ZERO));
        if (creditoAplicado.signum() > 0) {
            calculadas.add(PartidaDetalle.nueva(fiscal.cuentaCredito(), BigDecimal.ZERO, creditoAplicado));
        }
        if (porPagar.signum() > 0) {
            calculadas.add(PartidaDetalle.nueva(fiscal.cuentaPorPagar(), BigDecimal.ZERO, porPagar));
        }
        lineas = List.copyOf(calculadas);
        return asiento(hasta, "A5 Liquidación IVA", clave, lineas);
    }

    /**
     * Cierra el período contable actual.
     */
    public void cerrarPeriodoActual() {
        auth.exigirContador();
        Periodo p = periodos.periodoAbierto()
                .orElseThrow(() -> new DomainException("No hay período abierto"));
        if (hayCedulasPendientes(p.getId())) {
            throw new DomainException("No se puede cerrar el período mientras existan cédulas de ajuste "
                    + "pendientes de vincular a su partida.");
        }
        exigirSinPartidasPendientes(p);
        periodos.marcarCerrado(p);
    }

    /**
     * Cierra un período específico aplicando los mismos controles que el cierre
     * del período abierto: no permite cerrar mientras existan cédulas de ajuste
     * pendientes de vincular a su partida.
     */
    public void cerrarPeriodo(Periodo periodo) {
        auth.exigirContador();
        if (periodo == null) {
            throw new DomainException("Seleccione el período que desea cerrar.");
        }
        if (periodo.getEstado() == sv.edu.ues.colibri.domain.model.EstadoPeriodo.CERRADO) {
            throw new DomainException("El período ya está cerrado.");
        }
        if (hayCedulasPendientes(periodo.getId())) {
            throw new DomainException("No se puede cerrar el período mientras existan cédulas de ajuste "
                    + "pendientes de vincular a su partida.");
        }
        exigirSinPartidasPendientes(periodo);
        periodos.marcarCerrado(periodo);
    }

    /**
     * Cierra el período abierto ejecutando antes los ajustes automáticos del mes:
     * depreciación de activos fijos, amortización de intangibles y liquidación de IVA.
     * Cada ajuste es idempotente; cuando no hay base para calcularlo se omite y se
     * informa en el resumen devuelto.
     *
     * @return resumen de los ajustes generados u omitidos y del cierre
     */
    public String cerrarPeriodoConAjustes() {
        auth.exigirContador();
        Periodo p = periodos.periodoAbierto()
                .orElseThrow(() -> new DomainException("No hay período abierto"));
        List<String> resumen = new ArrayList<>();
        LocalDate corte = p.getFechaFin();
        ejecutarAjuste(resumen, "Depreciación de activos fijos",
                () -> depreciarPeriodo(corte));
        ejecutarAjuste(resumen, "Amortización de intangibles",
                () -> amortizarIntangiblesPeriodo(corte));
        ejecutarAjuste(resumen, "Liquidación de IVA",
                () -> liquidarIva(p.getFechaInicio(), corte));
        if (hayCedulasPendientes(p.getId())) {
            throw new DomainException("No se puede cerrar el período mientras existan cédulas de ajuste "
                    + "pendientes de vincular a su partida.");
        }
        exigirSinPartidasPendientes(p);
        periodos.marcarCerrado(p);
        resumen.add("Período " + p.getMes() + " cerrado.");
        return String.join("\n", resumen);
    }

    private void ejecutarAjuste(List<String> resumen, String nombre,
                                java.util.function.Supplier<Partida> ajuste) {
        try {
            Partida partida = ajuste.get();
            resumen.add(nombre + ": partida " + (partida.getFolio() != null
                    ? partida.getFolio() : "generada"));
        } catch (DomainException e) {
            resumen.add(nombre + ": omitido — " + e.getMessage());
        } catch (Exception e) {
            resumen.add(nombre + ": falló — " + e.getMessage());
        }
    }

    /**
     * F-AJU-03: calcula sólo activos disponibles para uso, limita la última
     * cuota al valor depreciable y persiste una cuota única por activo y período.
     */
    /**
     * Genera la depreciación del período para los activos elegibles.
     *
     * @param fecha fecha contable
     * @return partida generada
     */
    public Partida depreciarPeriodo(LocalDate fecha) {
        auth.exigirContador();
        Periodo periodo = periodoExistentePara(fecha);
        String clave = "AJ-DEP-" + periodo.getId();
        Optional<Partida> existente = partidaContabilizada(clave);
        if (existente.isPresent()) {
            completarCuotasPendientes(existente.get(), periodo, cuotasPendientes(periodo,
                    "depreciacion_cuota", "activo_fijo", "cuenta_dep_acum"), "depreciacion_cuota",
                    "activo_fijo", "depreciacion_acum");
            return existente.get();
        }
        exigirPeriodoCapturable(periodo);
        List<CuotaActivo> cuotas = asegurarSnapshotCuotas(periodo, fecha, "depreciacion_cuota",
                "activo_fijo", "depreciacion_acum", "cuenta_dep_acum");
        if (cuotas.isEmpty()) {
            throw new DomainException("No hay activos fijos elegibles para depreciar en el período.");
        }
        Partida partida = asiento(fecha, "Depreciación lineal del período", clave, lineasCuotas(cuotas));
        completarCuotasPendientes(partida, periodo, cuotas, "depreciacion_cuota", "activo_fijo",
                "depreciacion_acum");
        return partida;
    }

    /** Registra un intangible para su posterior amortización periódica. */
    /**
     * Registra un activo intangible para su amortización posterior.
     *
     * @param codigo código del intangible
     * @param nombre nombre del intangible
     * @param costo costo de adquisición
     * @param vidaMeses vida útil en meses
     * @param fechaUso fecha de inicio de uso
     * @param valorResidual valor residual
     * @param cuentaActivo cuenta de activo
     * @param cuentaAmortizacion cuenta de amortización acumulada
     * @param cuentaGasto cuenta de gasto
     * @return información del intangible registrado
     */
    public IntangibleInfo registrarIntangible(String codigo, String nombre, BigDecimal costo, int vidaMeses,
                                               LocalDate fechaUso, BigDecimal valorResidual,
                                               String cuentaActivo, String cuentaAmortizacion,
                                               String cuentaGasto) {
        auth.exigirContador();
        String codigoNormalizado = textoObligatorio(codigo, "El código del intangible");
        String nombreNormalizado = textoObligatorio(nombre, "El nombre del intangible");
        BigDecimal costoNormalizado = importePositivo(costo, "El costo del intangible");
        validarEnteroPositivo(vidaMeses, "La vida útil en meses");
        if (fechaUso == null) {
            throw new DomainException("La fecha de disponibilidad de uso es obligatoria.");
        }
        BigDecimal residual = valorResidual == null ? BigDecimal.ZERO : monetario(valorResidual);
        if (residual.signum() < 0 || residual.compareTo(costoNormalizado) >= 0) {
            throw new DomainException("El valor residual debe ser mayor o igual a cero y menor que el costo.");
        }
        var perfil = parametrizacion.exigirPerfilPosting("AMORTIZACION");
        String activo = parametrizacion.resolverCuenta(cuentaActivo, "120301", "cuenta del intangible");
        String acumulada = parametrizacion.resolverCuenta(cuentaAmortizacion, perfil.cuentaHaber(),
                "cuenta de amortización acumulada");
        String gasto = parametrizacion.resolverCuenta(cuentaGasto, perfil.cuentaDebe(),
                "cuenta de gasto de amortización");
        UUID id = UUID.randomUUID();
        jdbc.runInTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO activo_intangible (id, codigo, nombre, cuenta_activo, cuenta_amort_acum,
                        cuenta_gasto, costo, vida_meses, fecha_uso, valor_residual)
                    VALUES (?,?,?,?,?,?,?,?,?,?)
                    """)) {
                ps.setObject(1, id);
                ps.setString(2, codigoNormalizado);
                ps.setString(3, nombreNormalizado);
                ps.setString(4, activo);
                ps.setString(5, acumulada);
                ps.setString(6, gasto);
                ps.setBigDecimal(7, costoNormalizado);
                ps.setInt(8, vidaMeses);
                ps.setObject(9, fechaUso);
                ps.setBigDecimal(10, residual);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new PersistenceException("No se pudo registrar el intangible", e);
            }
        });
        return new IntangibleInfo(id, codigoNormalizado, nombreNormalizado, costoNormalizado, vidaMeses,
                fechaUso, residual, BigDecimal.ZERO.setScale(2), true);
    }

    /**
     * Registra un activo fijo tangible para su depreciación periódica posterior.
     *
     * @param codigo código del activo fijo
     * @param nombre nombre del activo fijo
     * @param costo costo de adquisición
     * @param vidaMeses vida útil en meses
     * @param fechaUso fecha de inicio de uso
     * @param valorResidual valor residual
     * @param cuentaActivo cuenta de activo
     * @param cuentaDepreciacion cuenta de depreciación acumulada
     * @param cuentaGasto cuenta de gasto
     * @return información del activo fijo registrado
     */
    public ActivoFijoInfo registrarActivoFijo(String codigo, String nombre, BigDecimal costo, int vidaMeses,
                                             LocalDate fechaUso, BigDecimal valorResidual,
                                             String cuentaActivo, String cuentaDepreciacion,
                                             String cuentaGasto) {
        auth.exigirContador();
        String codigoNormalizado = textoObligatorio(codigo, "El código del activo fijo");
        String nombreNormalizado = textoObligatorio(nombre, "El nombre del activo fijo");
        BigDecimal costoNormalizado = importePositivo(costo, "El costo del activo fijo");
        validarEnteroPositivo(vidaMeses, "La vida útil en meses");
        if (fechaUso == null) {
            throw new DomainException("La fecha de disponibilidad de uso es obligatoria.");
        }
        BigDecimal residual = valorResidual == null ? BigDecimal.ZERO : monetario(valorResidual);
        if (residual.signum() < 0 || residual.compareTo(costoNormalizado) >= 0) {
            throw new DomainException("El valor residual debe ser mayor o igual a cero y menor que el costo.");
        }
        var perfil = parametrizacion.exigirPerfilPosting("DEPRECIACION");
        String activo = parametrizacion.resolverCuenta(cuentaActivo, "120101", "cuenta del activo fijo");
        String acumulada = parametrizacion.resolverCuenta(cuentaDepreciacion, perfil.cuentaHaber(),
                "cuenta de depreciación acumulada");
        String gasto = parametrizacion.resolverCuenta(cuentaGasto, perfil.cuentaDebe(),
                "cuenta de gasto de depreciación");
        UUID id = UUID.randomUUID();
        jdbc.runInTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO activo_fijo (id, codigo, nombre, cuenta_activo, cuenta_dep_acum,
                        cuenta_gasto, costo, vida_meses, fecha_uso, valor_residual)
                    VALUES (?,?,?,?,?,?,?,?,?,?)
                    """)) {
                ps.setObject(1, id);
                ps.setString(2, codigoNormalizado);
                ps.setString(3, nombreNormalizado);
                ps.setString(4, activo);
                ps.setString(5, acumulada);
                ps.setString(6, gasto);
                ps.setBigDecimal(7, costoNormalizado);
                ps.setInt(8, vidaMeses);
                ps.setObject(9, fechaUso);
                ps.setBigDecimal(10, residual);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new PersistenceException("Error al registrar activo fijo", e);
            }
        });
        return new ActivoFijoInfo(id, codigoNormalizado, nombreNormalizado, costoNormalizado, vidaMeses,
                fechaUso, residual, BigDecimal.ZERO.setScale(2), true);
    }

    /**
     * Da de baja un activo fijo retirándolo del servicio activo.
     *
     * @param codigo código del activo
     * @param motivo motivo de la baja
     */
    public void darDeBajaActivoFijo(String codigo, String motivo) {
        auth.exigirContador();
        String cod = textoObligatorio(codigo, "El código del activo fijo");
        jdbc.runInTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    UPDATE activo_fijo SET activo = FALSE WHERE codigo = ? AND activo = TRUE
                    """)) {
                ps.setString(1, cod);
                if (ps.executeUpdate() == 0) {
                    throw new DomainException("Activo fijo no encontrado o ya dado de baja: " + cod);
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al dar de baja el activo fijo", e);
            }
        });
    }

    /**
     * Lista los activos fijos registrados.
     *
     * @return lista de activos fijos
     */
    public List<ActivoFijoInfo> listarActivosFijos() {
        auth.exigirContador();
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT id, codigo, nombre, costo, vida_meses, fecha_uso, valor_residual,
                           depreciacion_acum, activo
                    FROM activo_fijo
                    ORDER BY codigo
                    """);
                 ResultSet rs = ps.executeQuery()) {
                List<ActivoFijoInfo> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new ActivoFijoInfo((UUID) rs.getObject("id"), rs.getString("codigo"),
                            rs.getString("nombre"), rs.getBigDecimal("costo"), rs.getInt("vida_meses"),
                            rs.getObject("fecha_uso", LocalDate.class), rs.getBigDecimal("valor_residual"),
                            rs.getBigDecimal("depreciacion_acum"), rs.getBoolean("activo")));
                }
                return list;
            } catch (SQLException e) {
                throw new PersistenceException("Error al listar activos fijos", e);
            }
        });
    }

    /**
     * Lista los intangibles registrados.
     *
     * @return lista de intangibles
     */
    public List<IntangibleInfo> listarIntangibles() {
        auth.exigirContador();
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT id, codigo, nombre, costo, vida_meses, fecha_uso, valor_residual,
                           amortizacion_acum, activo
                    FROM activo_intangible ORDER BY codigo
                    """ ); ResultSet rs = ps.executeQuery()) {
                List<IntangibleInfo> resultado = new ArrayList<>();
                while (rs.next()) {
                    resultado.add(new IntangibleInfo((UUID) rs.getObject("id"), rs.getString("codigo"),
                            rs.getString("nombre"), rs.getBigDecimal("costo"), rs.getInt("vida_meses"),
                            rs.getObject("fecha_uso", LocalDate.class), rs.getBigDecimal("valor_residual"),
                            rs.getBigDecimal("amortizacion_acum"), rs.getBoolean("activo")));
                }
                return resultado;
            } catch (SQLException e) {
                throw new PersistenceException("No se pudieron consultar los intangibles", e);
            }
        });
    }

    /** F-AJU-07: amortiza intangibles disponibles y no amortizados por completo. */
    /**
     * Genera la amortización del período para los intangibles elegibles.
     *
     * @param fecha fecha contable
     * @return partida generada
     */
    public Partida amortizarIntangiblesPeriodo(LocalDate fecha) {
        auth.exigirContador();
        Periodo periodo = periodoExistentePara(fecha);
        String clave = "AJ-AMO-" + periodo.getId();
        Optional<Partida> existente = partidaContabilizada(clave);
        if (existente.isPresent()) {
            completarCuotasPendientes(existente.get(), periodo, cuotasPendientes(periodo,
                    "amortizacion_cuota", "activo_intangible", "cuenta_amort_acum"), "amortizacion_cuota",
                    "activo_intangible", "amortizacion_acum");
            return existente.get();
        }
        exigirPeriodoCapturable(periodo);
        List<CuotaActivo> cuotas = asegurarSnapshotCuotas(periodo, fecha, "amortizacion_cuota",
                "activo_intangible", "amortizacion_acum", "cuenta_amort_acum");
        if (cuotas.isEmpty()) {
            throw new DomainException("No hay intangibles elegibles para amortizar en el período.");
        }
        Partida partida = asiento(fecha, "Amortización de intangibles del período", clave, lineasCuotas(cuotas));
        completarCuotasPendientes(partida, periodo, cuotas, "amortizacion_cuota", "activo_intangible",
                "amortizacion_acum");
        return partida;
    }

    /**
     * F-AJU-08: ajusta la provisión de cartera al importe objetivo. No vuelve a
     * cargar la misma estimación cada mes: compara la provisión vigente con
     * base × porcentaje y también registra la reversión cuando el objetivo baja.
     */
    /**
     * Genera o revierte la estimación de incobrables hasta el objetivo calculado.
     *
     * @param baseEstimacion base de cálculo
     * @param porcentaje porcentaje de estimación
     * @param fecha fecha contable
     * @return partida generada
     */
    public Partida estimarIncobrables(BigDecimal baseEstimacion, BigDecimal porcentaje, LocalDate fecha) {
        auth.exigirContador();
        Periodo periodo = periodoExistentePara(fecha);
        validarTasa(porcentaje, "El porcentaje de incobrables");
        BigDecimal base = importeNoNegativo(baseEstimacion, "La base de estimación");
        BigDecimal objetivo = base.multiply(porcentaje).setScale(2, RoundingMode.HALF_UP);
        String clave = "AJ-INC-" + periodo.getId();
        Optional<Partida> existente = partidaContabilizada(clave);
        if (existente.isPresent()) {
            EstimacionSnapshot snapshot = exigirSnapshotEstimacion(periodo, base, porcentaje, objetivo);
            vincularEstimacionPendiente(existente.get(), periodo, snapshot);
            return existente.get();
        }
        exigirPeriodoCapturable(periodo);
        var perfil = parametrizacion.exigirPerfilPosting("INCOBRABLES");
        BigDecimal vigente = saldoCuentaHasta(perfil.cuentaHaber(), fecha, true);
        Optional<EstimacionSnapshot> pendiente = jdbc.inTransaction(conn ->
                buscarSnapshotEstimacion(conn, periodo.getId()));
        if (pendiente.isEmpty() && objetivo.subtract(vigente).signum() == 0) {
            throw new DomainException("La provisión vigente ya coincide con la estimación objetivo de cartera.");
        }
        EstimacionSnapshot snapshot = pendiente.orElseGet(() ->
                prepararSnapshotEstimacion(periodo, base, porcentaje, objetivo));
        validarMismaEstimacion(snapshot, base, porcentaje, objetivo);
        BigDecimal ajuste = snapshot.objetivo().subtract(vigente).setScale(2, RoundingMode.HALF_UP);
        if (ajuste.signum() == 0) {
            throw new DomainException("La cédula pendiente ya no requiere movimiento; revísela antes de contabilizar.");
        }
        List<PartidaDetalle> lineas = ajuste.signum() > 0
                ? List.of(PartidaDetalle.nueva(perfil.cuentaDebe(), ajuste, BigDecimal.ZERO),
                PartidaDetalle.nueva(perfil.cuentaHaber(), BigDecimal.ZERO, ajuste))
                : List.of(PartidaDetalle.nueva(perfil.cuentaHaber(), ajuste.abs(), BigDecimal.ZERO),
                PartidaDetalle.nueva(perfil.cuentaDebe(), BigDecimal.ZERO, ajuste.abs()));
        String concepto = ajuste.signum() > 0 ? "Estimación por deterioro de clientes"
                : "Reversión de estimación por deterioro de clientes";
        Partida partida = asiento(fecha, concepto, clave, lineas);
        vincularEstimacionPendiente(partida, periodo, snapshot);
        return partida;
    }

    /**
     * Compatibilidad para quien ya calculó un deterioro. El importe se traduce
     * a un valor recuperable neto del lote, para conservar una sola cédula y
     * evitar que el método manual duplique el cálculo automático.
     */
    /**
     * Calcula el deterioro de inventario a partir de un importe de ajuste.
     *
     * @param importe importe de deterioro
     * @param fecha fecha contable
     * @param lote identificador del lote
     * @return partida generada
     */
    public Partida deterioroInventario(BigDecimal importe, LocalDate fecha, String lote) {
        BigDecimal deterioro = importePositivo(importe, "El monto de deterioro");
        String codigoLote = textoObligatorio(lote, "El lote o versión");
        LoteDeterioro evaluado = cargarLoteParaDeterioro(codigoLote);
        BigDecimal recuperable = evaluado.valorEnLibros().subtract(deterioro).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        return deteriorarLoteAValorRecuperable(codigoLote, recuperable, fecha);
    }

    /** Evalúa un lote digital al menor entre costo en libros y valor recuperable. */
    /**
     * Ajusta un lote al valor recuperable especificado.
     *
     * @param codigoLote código del lote
     * @param valorRecuperable valor recuperable objetivo
     * @param fecha fecha contable
     * @return partida generada
     */
    public Partida deteriorarLoteAValorRecuperable(String codigoLote, BigDecimal valorRecuperable,
                                                   LocalDate fecha) {
        auth.exigirContador();
        Periodo periodo = periodoExistentePara(fecha);
        String lote = textoObligatorio(codigoLote, "El lote");
        BigDecimal recuperable = importeNoNegativo(valorRecuperable, "El valor recuperable");
        LoteDeterioro evaluado = cargarLoteParaDeterioro(lote);
        if (recuperable.compareTo(evaluado.valorBruto()) > 0) {
            throw new DomainException("El valor recuperable no puede exceder el costo bruto disponible del lote.");
        }
        BigDecimal deterioroObjetivo = evaluado.valorBruto().subtract(recuperable)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal ajuste = deterioroObjetivo.subtract(evaluado.deterioroAcumulado())
                .setScale(2, RoundingMode.HALF_UP);
        String clave = "AJ-DET-" + periodo.getId() + "-" + lote;
        Optional<Partida> existente = partidaContabilizada(clave);
        if (existente.isPresent()) {
            DeterioroSnapshot snapshot = exigirSnapshotDeterioro(evaluado.id(), periodo, recuperable);
            vincularDeterioroPendiente(existente.get(), evaluado.id(), periodo, snapshot);
            return existente.get();
        }
        exigirPeriodoCapturable(periodo);
        Optional<DeterioroSnapshot> pendiente = jdbc.inTransaction(conn ->
                buscarSnapshotDeterioro(conn, evaluado.id(), periodo.getId()));
        if (pendiente.isEmpty() && ajuste.signum() == 0) {
            throw new DomainException("El deterioro acumulado del lote ya coincide con su valor recuperable.");
        }
        DeterioroSnapshot snapshot = pendiente.orElseGet(() ->
                prepararSnapshotDeterioro(evaluado, periodo, recuperable, ajuste));
        validarMismoDeterioro(snapshot, recuperable);
        var perfil = parametrizacion.exigirPerfilPosting("DETERIORO_INVENTARIO");
        List<PartidaDetalle> lineas = snapshot.ajuste().signum() > 0
                ? List.of(PartidaDetalle.nueva(perfil.cuentaDebe(), lote, "CC-02", snapshot.ajuste(), BigDecimal.ZERO),
                PartidaDetalle.nueva(perfil.cuentaHaber(), lote, null, BigDecimal.ZERO, snapshot.ajuste()))
                : List.of(PartidaDetalle.nueva(perfil.cuentaHaber(), lote, "CC-02", snapshot.ajuste().abs(), BigDecimal.ZERO),
                PartidaDetalle.nueva(perfil.cuentaDebe(), lote, null, BigDecimal.ZERO, snapshot.ajuste().abs()));
        String concepto = snapshot.ajuste().signum() > 0 ? "Deterioro de inventario digital " + lote
                : "Reversión de deterioro de inventario digital " + lote;
        Partida partida = asiento(fecha, concepto, clave, lineas);
        vincularDeterioroPendiente(partida, evaluado.id(), periodo, snapshot);
        return partida;
    }

    private Partida asiento(LocalDate fecha, String concepto, String clave, List<PartidaDetalle> lineas) {
        auth.exigirContador();
        if (fecha == null) {
            throw new DomainException("La fecha contable es obligatoria.");
        }
        Optional<Partida> existente = partidaContabilizada(clave);
        if (existente.isPresent()) {
            return existente.get();
        }
        try {
            Partida borrador = partidas.guardarBorrador(TipoPartida.AJ, fecha, concepto, concepto, clave,
                    new ArrayList<>(lineas), null);
            return partidas.contabilizar(borrador.getId());
        } catch (DomainException e) {
            return partidaContabilizada(clave).orElseThrow(() -> e);
        }
    }

    private Optional<Partida> partidaContabilizada(String clave) {
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT partida_contabilizada_id FROM evento_contable WHERE clave_unica = ?
                    """)) {
                ps.setString(1, clave);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || rs.getObject(1) == null) {
                        return Optional.empty();
                    }
                    Optional<Partida> partida = partidaRepository.buscarPorId(conn, (UUID) rs.getObject(1));
                    if (partida.isPresent() && partida.get().getEstado() == EstadoPartida.REVERTIDA) {
                        throw new DomainException("El ajuste " + clave + " fue revertido; "
                                + "registre una corrección nueva en otro período abierto.");
                    }
                    return partida.filter(p -> p.getEstado() == EstadoPartida.CONTABILIZADA);
                }
            } catch (SQLException e) {
                throw new PersistenceException("No se pudo comprobar la idempotencia del ajuste", e);
            }
        });
    }

    /** Busca el período aun si ya cerró; nunca habilita crear una partida nueva. */
    private Periodo periodoExistentePara(LocalDate fecha) {
        if (fecha == null) {
            throw new DomainException("La fecha contable es obligatoria.");
        }
        return periodos.exigirPeriodoParaFecha(fecha);
    }

    private void exigirPeriodoCapturable(Periodo periodo) {
        if (!periodo.admiteCaptura()) {
            throw new DomainException("El período " + periodo.getMes() + " está " + periodo.getEstado()
                    + ". No se admite captura hasta reapertura.");
        }
    }

    private List<CuotaActivo> cuotasDepreciacion(Periodo periodo, LocalDate fecha) {
        return cuotas(periodo, fecha, "activo_fijo", "depreciacion_cuota", "depreciacion_acum",
                "cuenta_dep_acum");
    }

    private List<CuotaActivo> cuotasAmortizacion(Periodo periodo, LocalDate fecha) {
        return cuotas(periodo, fecha, "activo_intangible", "amortizacion_cuota", "amortizacion_acum",
                "cuenta_amort_acum");
    }

    private List<CuotaActivo> cuotas(Periodo periodo, LocalDate fecha, String tablaActivo,
                                      String tablaCuota, String columnaAcumulada,
                                      String columnaCuentaAcumulada) {
        String sql = """
                SELECT a.id, a.costo, a.valor_residual, a.vida_meses, a.%s AS acumulada,
                       a.cuenta_gasto, a.%s AS cuenta_acumulada
                FROM %s a
                WHERE a.activo = TRUE AND a.fecha_uso <= ?
                  AND NOT EXISTS (
                    SELECT 1 FROM %s c WHERE c.activo_id = a.id AND c.periodo_id = ?
                  )
                ORDER BY a.codigo
                """.formatted(columnaAcumulada, columnaCuentaAcumulada, tablaActivo, tablaCuota);
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, fecha);
                ps.setObject(2, periodo.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    List<CuotaActivo> resultado = new ArrayList<>();
                    while (rs.next()) {
                        BigDecimal depreciable = rs.getBigDecimal("costo")
                                .subtract(rs.getBigDecimal("valor_residual"));
                        BigDecimal restante = depreciable.subtract(rs.getBigDecimal("acumulada"));
                        if (restante.signum() <= 0) {
                            continue;
                        }
                        BigDecimal cuotaNormal = depreciable.divide(
                                BigDecimal.valueOf(rs.getInt("vida_meses")), 2, RoundingMode.HALF_UP);
                        BigDecimal cuota = cuotaNormal.min(restante).setScale(2, RoundingMode.HALF_UP);
                        if (cuota.signum() > 0) {
                            resultado.add(new CuotaActivo((UUID) rs.getObject("id"),
                                    rs.getString("cuenta_gasto"), rs.getString("cuenta_acumulada"), cuota));
                        }
                    }
                    return resultado;
                }
            } catch (SQLException e) {
                throw new PersistenceException("No se pudieron calcular las cuotas del período", e);
            }
        });
    }

    private List<PartidaDetalle> lineasCuotas(List<CuotaActivo> cuotas) {
        Map<String, BigDecimal> debe = new LinkedHashMap<>();
        Map<String, BigDecimal> haber = new LinkedHashMap<>();
        for (CuotaActivo cuota : cuotas) {
            debe.merge(cuota.cuentaGasto(), cuota.importe(), BigDecimal::add);
            haber.merge(cuota.cuentaAcumulada(), cuota.importe(), BigDecimal::add);
        }
        List<PartidaDetalle> lineas = new ArrayList<>();
        debe.forEach((cuenta, importe) -> lineas.add(PartidaDetalle.nueva(cuenta, monetario(importe), BigDecimal.ZERO)));
        haber.forEach((cuenta, importe) -> lineas.add(PartidaDetalle.nueva(cuenta, BigDecimal.ZERO, monetario(importe))));
        return lineas;
    }

    /**
     * Recupera las cuotas ya fotografiadas antes de contabilizar. La fotografía
     * evita que un activo agregado entre un fallo y el reintento se una a una
     * partida anterior que no lo contenía.
     */
    private List<CuotaActivo> cuotasPendientes(Periodo periodo, String tablaCuota, String tablaActivo,
                                               String columnaCuentaAcumulada) {
        String sql = """
                SELECT c.activo_id, c.importe, a.cuenta_gasto, a.%s AS cuenta_acumulada
                FROM %s c JOIN %s a ON a.id=c.activo_id
                WHERE c.periodo_id=? AND c.partida_id IS NULL
                ORDER BY a.codigo
                """.formatted(columnaCuentaAcumulada, tablaCuota, tablaActivo);
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, periodo.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    List<CuotaActivo> resultado = new ArrayList<>();
                    while (rs.next()) {
                        resultado.add(new CuotaActivo((UUID) rs.getObject("activo_id"),
                                rs.getString("cuenta_gasto"), rs.getString("cuenta_acumulada"),
                                rs.getBigDecimal("importe")));
                    }
                    return resultado;
                }
            } catch (SQLException e) {
                throw new PersistenceException("No se pudieron recuperar las cuotas pendientes", e);
            }
        });
    }

    private List<CuotaActivo> asegurarSnapshotCuotas(Periodo periodo, LocalDate fecha, String tablaCuota,
                                                      String tablaActivo, String columnaAcumulada,
                                                      String columnaCuentaAcumulada) {
        List<CuotaActivo> pendientes = cuotasPendientes(periodo, tablaCuota, tablaActivo, columnaCuentaAcumulada);
        if (!pendientes.isEmpty()) {
            return pendientes;
        }
        List<CuotaActivo> calculadas = cuotas(periodo, fecha, tablaActivo, tablaCuota, columnaAcumulada,
                columnaCuentaAcumulada);
        if (calculadas.isEmpty()) {
            return calculadas;
        }
        jdbc.runInTransaction(conn -> guardarSnapshotCuotas(conn, periodo, calculadas, tablaCuota));
        return cuotasPendientes(periodo, tablaCuota, tablaActivo, columnaCuentaAcumulada);
    }

    private void guardarSnapshotCuotas(java.sql.Connection conn, Periodo periodo, List<CuotaActivo> cuotas,
                                       String tablaCuota) {
        String insertar = "INSERT INTO %s (id, activo_id, periodo_id, importe, partida_id) VALUES (?,?,?,?,NULL) "
                + "ON CONFLICT (activo_id, periodo_id) DO NOTHING";
        try (PreparedStatement ins = conn.prepareStatement(insertar.formatted(tablaCuota))) {
            for (CuotaActivo cuota : cuotas) {
                ins.setObject(1, UUID.randomUUID());
                ins.setObject(2, cuota.activoId());
                ins.setObject(3, periodo.getId());
                ins.setBigDecimal(4, cuota.importe());
                ins.executeUpdate();
            }
        } catch (SQLException e) {
            throw new PersistenceException("No se pudieron fotografiar las cuotas del período", e);
        }
    }

    /** Vincula la fotografía a la partida y sólo entonces actualiza el acumulado del activo. */
    private void completarCuotasPendientes(Partida partida, Periodo periodo, List<CuotaActivo> cuotas,
                                           String tablaCuota, String tablaActivo,
                                           String columnaAcumulada) {
        if (cuotas.isEmpty()) {
            return;
        }
        String actualizar = """
                UPDATE %s
                SET %s = LEAST(costo - valor_residual, %s + ?)
                WHERE id = ?
                """.formatted(tablaActivo, columnaAcumulada, columnaAcumulada);
        jdbc.runInTransaction(conn -> {
            String vincular = "UPDATE %s SET partida_id=? WHERE activo_id=? AND periodo_id=? AND partida_id IS NULL"
                    .formatted(tablaCuota);
            try (PreparedStatement link = conn.prepareStatement(vincular);
                 PreparedStatement upd = conn.prepareStatement(actualizar)) {
                for (CuotaActivo cuota : cuotas) {
                    link.setObject(1, partida.getId());
                    link.setObject(2, cuota.activoId());
                    link.setObject(3, periodo.getId());
                    if (link.executeUpdate() == 1) {
                        upd.setBigDecimal(1, cuota.importe());
                        upd.setBigDecimal(2, cuota.importe());
                        upd.setObject(3, cuota.activoId());
                        upd.executeUpdate();
                    } else if (!partida.getId().equals(partidaVinculadaCuota(conn, tablaCuota,
                            cuota.activoId(), periodo.getId()))) {
                        throw new DomainException("La cédula de cuota fue vinculada a otra partida; "
                                + "no es seguro actualizar el auxiliar.");
                    }
                }
            } catch (SQLException e) {
                throw new PersistenceException("No se pudieron persistir las cuotas del período", e);
            }
        });
    }

    private UUID partidaVinculadaCuota(java.sql.Connection conn, String tablaCuota, UUID activoId,
                                       UUID periodoId) throws SQLException {
        String sql = "SELECT partida_id FROM %s WHERE activo_id=? AND periodo_id=?".formatted(tablaCuota);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, activoId);
            ps.setObject(2, periodoId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return (UUID) rs.getObject(1);
            }
        }
    }

    private LoteDeterioro cargarLoteParaDeterioro(String codigoLote) {
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT l.id,
                           ROUND(l.unidades_disponibles * l.costo_unitario, 2) AS valor_bruto,
                           COALESCE(SUM(CASE
                               WHEN p.estado = 'CONTABILIZADA'
                                   THEN CASE WHEN d.es_reversion THEN -d.importe ELSE d.importe END
                               ELSE 0
                           END), 0) AS deterioro_acumulado
                    FROM lote_licencia l
                    LEFT JOIN deterioro_lote_licencia d ON d.lote_id = l.id
                    LEFT JOIN partida p ON p.id = d.partida_id
                    WHERE l.codigo_lote = ?
                    GROUP BY l.id, l.unidades_disponibles, l.costo_unitario
                    """)) {
                ps.setString(1, codigoLote);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new DomainException("No existe el lote de licencias: " + codigoLote);
                    }
                    return new LoteDeterioro((UUID) rs.getObject("id"), rs.getBigDecimal("valor_bruto"),
                            rs.getBigDecimal("deterioro_acumulado"));
                }
            } catch (SQLException e) {
                throw new PersistenceException("No se pudo evaluar el lote de licencias", e);
            }
        });
    }

    private Optional<EstimacionSnapshot> buscarSnapshotEstimacion(java.sql.Connection conn, UUID periodoId) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT base_estimacion, porcentaje, importe, partida_id
                FROM estimacion_cartera_cuota WHERE periodo_id=?
                """)) {
            ps.setObject(1, periodoId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new EstimacionSnapshot(rs.getBigDecimal("base_estimacion"),
                        rs.getBigDecimal("porcentaje"), rs.getBigDecimal("importe"),
                        (UUID) rs.getObject("partida_id")));
            }
        } catch (SQLException e) {
            throw new PersistenceException("No se pudo consultar la cédula de cartera", e);
        }
    }

    private EstimacionSnapshot exigirSnapshotEstimacion(Periodo periodo, BigDecimal base,
                                                         BigDecimal porcentaje, BigDecimal objetivo) {
        return jdbc.inTransaction(conn -> {
            EstimacionSnapshot snapshot = buscarSnapshotEstimacion(conn, periodo.getId()).orElseThrow(() ->
                    new DomainException("La partida de cartera existe sin su cédula previa; no es seguro "
                            + "reconstruirla con datos nuevos."));
            validarMismaEstimacion(snapshot, base, porcentaje, objetivo);
            return snapshot;
        });
    }

    private EstimacionSnapshot prepararSnapshotEstimacion(Periodo periodo, BigDecimal base,
                                                           BigDecimal porcentaje, BigDecimal objetivo) {
        return jdbc.inTransaction(conn -> {
            Optional<EstimacionSnapshot> existente = buscarSnapshotEstimacion(conn, periodo.getId());
            if (existente.isPresent()) {
                validarMismaEstimacion(existente.get(), base, porcentaje, objetivo);
                return existente.get();
            }
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO estimacion_cartera_cuota
                        (id, periodo_id, base_estimacion, porcentaje, importe, partida_id)
                    VALUES (?,?,?,?,?,NULL)
                    ON CONFLICT (periodo_id) DO NOTHING
                    """)) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, periodo.getId());
                ps.setBigDecimal(3, base);
                ps.setBigDecimal(4, porcentaje);
                ps.setBigDecimal(5, objetivo);
                ps.executeUpdate();
                return buscarSnapshotEstimacion(conn, periodo.getId()).orElseThrow(() ->
                        new PersistenceException("No se pudo recuperar la cédula de cartera creada"));
            } catch (SQLException e) {
                throw new PersistenceException("No se pudo fotografiar la estimación de cartera", e);
            }
        });
    }

    private void validarMismaEstimacion(EstimacionSnapshot snapshot, BigDecimal base,
                                        BigDecimal porcentaje, BigDecimal objetivo) {
        if (snapshot.base().compareTo(base) != 0 || snapshot.porcentaje().compareTo(porcentaje) != 0
                || snapshot.objetivo().compareTo(objetivo) != 0) {
            throw new DomainException("Ya existe una cédula de cartera pendiente o contabilizada para este período; "
                    + "sus datos no pueden cambiarse sin revertir el ajuste.");
        }
    }

    private void vincularEstimacionPendiente(Partida partida, Periodo periodo, EstimacionSnapshot snapshot) {
        if (snapshot.partidaId() != null && !snapshot.partidaId().equals(partida.getId())) {
            throw new DomainException("La cédula de cartera ya está vinculada a otra partida y no puede reemplazarse.");
        }
        jdbc.runInTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    UPDATE estimacion_cartera_cuota SET partida_id=?
                    WHERE periodo_id=? AND partida_id IS NULL
                    """)) {
                ps.setObject(1, partida.getId());
                ps.setObject(2, periodo.getId());
                if (ps.executeUpdate() == 0) {
                    EstimacionSnapshot actual = buscarSnapshotEstimacion(conn, periodo.getId()).orElseThrow(() ->
                            new DomainException("No existe la cédula de cartera que se intentó vincular."));
                    if (!partida.getId().equals(actual.partidaId())) {
                        throw new DomainException("La cédula de cartera fue vinculada a otra partida; "
                                + "no es seguro continuar.");
                    }
                }
            } catch (SQLException e) {
                throw new PersistenceException("No se pudo vincular la cédula de cartera con su partida", e);
            }
        });
    }

    private Optional<DeterioroSnapshot> buscarSnapshotDeterioro(java.sql.Connection conn, UUID loteId,
                                                                  UUID periodoId) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT costo_en_libros, valor_recuperable, importe, es_reversion, partida_id
                FROM deterioro_lote_licencia WHERE lote_id=? AND periodo_id=?
                """)) {
            ps.setObject(1, loteId);
            ps.setObject(2, periodoId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                BigDecimal importe = rs.getBigDecimal("importe");
                BigDecimal ajuste = rs.getBoolean("es_reversion") ? importe.negate() : importe;
                return Optional.of(new DeterioroSnapshot(rs.getBigDecimal("costo_en_libros"),
                        rs.getBigDecimal("valor_recuperable"), ajuste, (UUID) rs.getObject("partida_id")));
            }
        } catch (SQLException e) {
            throw new PersistenceException("No se pudo consultar la cédula de deterioro", e);
        }
    }

    private DeterioroSnapshot exigirSnapshotDeterioro(UUID loteId, Periodo periodo,
                                                       BigDecimal recuperable) {
        return jdbc.inTransaction(conn -> {
            DeterioroSnapshot snapshot = buscarSnapshotDeterioro(conn, loteId, periodo.getId()).orElseThrow(() ->
                    new DomainException("La partida de deterioro existe sin su cédula previa; no es seguro "
                            + "reconstruirla con un valor recuperable nuevo."));
            validarMismoDeterioro(snapshot, recuperable);
            return snapshot;
        });
    }

    private DeterioroSnapshot prepararSnapshotDeterioro(LoteDeterioro lote, Periodo periodo,
                                                         BigDecimal recuperable, BigDecimal ajuste) {
        return jdbc.inTransaction(conn -> {
            Optional<DeterioroSnapshot> existente = buscarSnapshotDeterioro(conn, lote.id(), periodo.getId());
            if (existente.isPresent()) {
                validarMismoDeterioro(existente.get(), recuperable);
                return existente.get();
            }
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO deterioro_lote_licencia
                        (id, lote_id, periodo_id, costo_en_libros, valor_recuperable, importe,
                         es_reversion, partida_id)
                    VALUES (?,?,?,?,?,?,?,NULL)
                    ON CONFLICT (lote_id, periodo_id) DO NOTHING
                    """)) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, lote.id());
                ps.setObject(3, periodo.getId());
                ps.setBigDecimal(4, lote.valorEnLibros());
                ps.setBigDecimal(5, recuperable);
                ps.setBigDecimal(6, ajuste.abs());
                ps.setBoolean(7, ajuste.signum() < 0);
                ps.executeUpdate();
                return buscarSnapshotDeterioro(conn, lote.id(), periodo.getId()).orElseThrow(() ->
                        new PersistenceException("No se pudo recuperar la cédula de deterioro creada"));
            } catch (SQLException e) {
                throw new PersistenceException("No se pudo fotografiar la evaluación de deterioro", e);
            }
        });
    }

    private void validarMismoDeterioro(DeterioroSnapshot snapshot, BigDecimal recuperable) {
        if (snapshot.valorRecuperable().compareTo(recuperable) != 0) {
            throw new DomainException("Ya existe una cédula de deterioro pendiente o contabilizada para este lote y "
                    + "período; revierta el ajuste antes de modificar su valor recuperable.");
        }
    }

    /** Vincula la cédula previamente fotografiada y actualiza el valor recuperable del lote. */
    private void vincularDeterioroPendiente(Partida partida, UUID loteId, Periodo periodo,
                                            DeterioroSnapshot snapshot) {
        if (snapshot.partidaId() != null && !snapshot.partidaId().equals(partida.getId())) {
            throw new DomainException("La cédula de deterioro ya está vinculada a otra partida y no puede reemplazarse.");
        }
        jdbc.runInTransaction(conn -> {
            try (PreparedStatement vincular = conn.prepareStatement("""
                         UPDATE deterioro_lote_licencia SET partida_id=?
                         WHERE lote_id=? AND periodo_id=? AND partida_id IS NULL
                          """);
                 PreparedStatement actualizar = conn.prepareStatement(
                         "UPDATE lote_licencia SET valor_recuperable=? WHERE id=?")) {
                vincular.setObject(1, partida.getId());
                vincular.setObject(2, loteId);
                vincular.setObject(3, periodo.getId());
                if (vincular.executeUpdate() == 1) {
                    actualizar.setBigDecimal(1, snapshot.valorRecuperable());
                    actualizar.setObject(2, loteId);
                    actualizar.executeUpdate();
                } else {
                    DeterioroSnapshot actual = buscarSnapshotDeterioro(conn, loteId, periodo.getId())
                            .orElseThrow(() -> new DomainException(
                                    "No existe la cédula de deterioro que se intentó vincular."));
                    if (!partida.getId().equals(actual.partidaId())) {
                        throw new DomainException("La cédula de deterioro fue vinculada a otra partida; "
                                + "no es seguro continuar.");
                    }
                }
            } catch (SQLException e) {
                throw new PersistenceException("No se pudo registrar la evaluación de deterioro", e);
            }
        });
    }

    /** Un cierre no debe congelar una cédula creada antes de su asiento. */
    /**
     * Impide cerrar un período que aún tiene partidas en borrador o aprobadas:
     * quedarían imposibilitadas de contabilizarse al bloquearse el mes.
     */
    private void exigirSinPartidasPendientes(Periodo periodo) {
        int pendientes = jdbc.inTransaction(conn ->
                partidaRepository.contarPendientesPorPeriodo(conn, periodo.getId()));
        if (pendientes > 0) {
            throw new DomainException("No se puede cerrar el período mientras existan " + pendientes
                    + " partida(s) en borrador o aprobadas sin contabilizar. "
                    + "Contabilícelas, anúlelas o reviértalas antes del cierre.");
        }
    }

    private boolean hayCedulasPendientes(UUID periodoId) {
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT EXISTS (
                        SELECT 1 FROM depreciacion_cuota WHERE periodo_id=? AND partida_id IS NULL
                        UNION ALL
                        SELECT 1 FROM amortizacion_cuota WHERE periodo_id=? AND partida_id IS NULL
                        UNION ALL
                        SELECT 1 FROM estimacion_cartera_cuota WHERE periodo_id=? AND partida_id IS NULL
                        UNION ALL
                        SELECT 1 FROM deterioro_lote_licencia WHERE periodo_id=? AND partida_id IS NULL
                        UNION ALL
                        SELECT 1 FROM planilla_periodo WHERE periodo_id=? AND partida_id IS NULL
                    )
                    """)) {
                for (int indice = 1; indice <= 5; indice++) {
                    ps.setObject(indice, periodoId);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getBoolean(1);
                }
            } catch (SQLException e) {
                throw new PersistenceException("No se pudo comprobar el estado de las cédulas de cierre", e);
            }
        });
    }

    private BigDecimal saldoCuentaHasta(String codigo, LocalDate hasta, boolean saldoAcreedor) {
        return jdbc.inTransaction(conn -> saldoCuentaHasta(conn, codigo, hasta, saldoAcreedor));
    }

    private BigDecimal saldoCuentaHasta(java.sql.Connection conn, String codigo,
                                        LocalDate hasta, boolean saldoAcreedor) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT COALESCE(SUM(d.debe - d.haber),0)
                FROM partida_detalle d
                JOIN partida p ON p.id = d.partida_id
                WHERE d.codigo_cuenta=? AND p.estado IN ('CONTABILIZADA','REVERTIDA')
                  AND p.fecha_contable <= ?
                """)) {
            ps.setString(1, codigo);
            ps.setObject(2, hasta);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                BigDecimal saldo = rs.getBigDecimal(1);
                return saldoAcreedor ? saldo.negate().max(BigDecimal.ZERO) : saldo.max(BigDecimal.ZERO);
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al calcular saldo de " + codigo, e);
        }
    }

    private void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || hasta.isBefore(desde)) {
            throw new DomainException("El rango de fechas es inválido.");
        }
    }

    private void validarEnteroPositivo(int valor, String etiqueta) {
        if (valor <= 0) {
            throw new DomainException(etiqueta + " debe ser mayor que cero.");
        }
    }

    private void validarTasa(BigDecimal tasa, String etiqueta) {
        if (tasa == null || tasa.signum() < 0 || tasa.compareTo(BigDecimal.ONE) > 0) {
            throw new DomainException(etiqueta + " debe estar entre 0 y 1.");
        }
    }

    private String textoObligatorio(String valor, String etiqueta) {
        if (valor == null || valor.isBlank()) {
            throw new DomainException(etiqueta + " es obligatorio.");
        }
        return valor.trim();
    }

    private BigDecimal importePositivo(BigDecimal valor, String etiqueta) {
        BigDecimal normalizado = monetario(valor);
        if (normalizado.signum() <= 0) {
            throw new DomainException(etiqueta + " debe ser mayor que cero.");
        }
        return normalizado;
    }

    private BigDecimal importeNoNegativo(BigDecimal valor, String etiqueta) {
        BigDecimal normalizado = monetario(valor);
        if (normalizado.signum() < 0) {
            throw new DomainException(etiqueta + " no puede ser negativo.");
        }
        return normalizado;
    }

    private static BigDecimal monetario(BigDecimal valor) {
        if (valor == null) {
            throw new DomainException("El importe es obligatorio.");
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}
