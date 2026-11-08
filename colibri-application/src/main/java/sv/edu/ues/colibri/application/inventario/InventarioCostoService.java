package sv.edu.ues.colibri.application.inventario;

import sv.edu.ues.colibri.application.auth.AuthService;
import sv.edu.ues.colibri.application.configuracion.ParametrizacionService;
import sv.edu.ues.colibri.application.partida.PartidaService;
import sv.edu.ues.colibri.domain.exception.DomainException;
import sv.edu.ues.colibri.domain.model.Partida;
import sv.edu.ues.colibri.domain.model.PartidaDetalle;
import sv.edu.ues.colibri.domain.model.TipoPartida;
import sv.edu.ues.colibri.persistence.jdbc.JdbcSupport;
import sv.edu.ues.colibri.persistence.jdbc.PersistenceException;
import sv.edu.ues.colibri.persistence.repo.PartidaRepository;
import sv.edu.ues.colibri.persistence.repo.TerceroRepository;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Inventario de licencias y costeo por órdenes, PEPS y procesos SaaS.
 */
public class InventarioCostoService {

    private final JdbcSupport jdbc;
    private final AuthService auth;
    private final PartidaService partidas;
    private final ParametrizacionService parametrizacion;
    private final PartidaRepository partidaRepository = new PartidaRepository();
    private final TerceroRepository terceros = new TerceroRepository();

    /**
     * Crea el servicio de inventario y costeo.
     *
     * @param jdbc            soporte transaccional
     * @param auth            servicio de autenticación
     * @param partidas        servicio de partidas
     * @param parametrizacion servicio de parametrización
     */
    public InventarioCostoService(JdbcSupport jdbc, AuthService auth, PartidaService partidas,
            ParametrizacionService parametrizacion) {
        this.jdbc = jdbc;
        this.auth = auth;
        this.partidas = partidas;
        this.parametrizacion = parametrizacion;
    }

    public record LoteInfo(UUID id, String codigo, int disponibles, BigDecimal costoUnitario) {
    }

    public record OrdenResumen(String codigo, String descripcion, String estado) {
    }

    public record OrdenInfo(UUID id, String codigo, String descripcion, BigDecimal presupuesto,
            BigDecimal precioVenta, BigDecimal costoAcumulado, BigDecimal margen) {
    }

    /**
     * Resultado gerencial de la comparación entre el presupuesto de la orden y
     * el costo real acumulado en los registros contables.
     */
    public record DesviacionOrdenInfo(UUID id, String codigo, BigDecimal costoEstimado,
            BigDecimal costoReal, BigDecimal variacionAbsoluta,
            BigDecimal variacionPorcentual, BigDecimal indiceEjecucion,
            BigDecimal ahorroPresupuestario, BigDecimal sobrecosto,
            BigDecimal utilidadEsperada, BigDecimal utilidadReal,
            BigDecimal rentabilidadSobreVenta, String diagnostico) {
    }

    /**
     * Unidad reservada por PEPS. El lote se conserva para restituir exactamente
     * la disponibilidad afectada si la contabilización posterior falla.
     */
    private record ReservaLicencia(UUID licenciaId, UUID loteId, BigDecimal costoContable,
            String codigoLote) {
    }

    /** Desglose del costo SaaS por proceso para un período y sus usuarios-mes. */
    public record CostoSaasInfo(BigDecimal alojamiento, BigDecimal soporte,
            BigDecimal infraestructuraCompartida, int usuariosMes,
            BigDecimal total, BigDecimal costoUsuarioMes) {
    }

    /** Costo SaaS asignado a un plan por su participación en usuarios-mes. */
    public record CostoSaasPlanInfo(String codigoPlan, String nombrePlan, int usuariosMesPlan,
            int usuariosMesTotal, BigDecimal alojamiento,
            BigDecimal soporte, BigDecimal infraestructuraCompartida,
            BigDecimal totalAsignado, BigDecimal costoUsuarioMes) {
    }

    /** Acumulación de los elementos del costo de un servicio facturado por plan. */
    public record PlanCostoInfo(String codigoPlan, String nombrePlan, String tipoFacturacion,
            BigDecimal manoObra, BigDecimal serviciosDirectos,
            BigDecimal indirectos, BigDecimal total) {
    }

    /** Comparación gerencial de métodos; no registra ni permite una salida UEPS. */
    public record ComparativoInventarioInfo(int unidadesSalida, BigDecimal costoPeps,
            BigDecimal costoPromedio, BigDecimal costoUeps,
            BigDecimal saldoPeps, BigDecimal saldoPromedio,
            BigDecimal saldoUeps) {
    }

    private record PlanRef(UUID id, String codigo, String nombre, String modalidad,
            String tipoFacturacion) {
    }

    record SaasParticipacion(UUID planId, String codigo, int usuariosMes) {
    }

    private record ParticipacionCentavos(UUID planId, String codigo, BigInteger base,
            BigInteger residuo) {
    }

    /**
     * Lista los lotes de licencias registrados.
     *
     * @return lista de lotes
     */
    public List<LoteInfo> listarLotes() {
        auth.exigirContador();
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, codigo_lote, unidades_disponibles, costo_unitario FROM lote_licencia ORDER BY codigo_lote");
                    ResultSet rs = ps.executeQuery()) {
                List<LoteInfo> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new LoteInfo((UUID) rs.getObject("id"), rs.getString("codigo_lote"),
                            rs.getInt("unidades_disponibles"), rs.getBigDecimal("costo_unitario")));
                }
                return list;
            } catch (SQLException e) {
                throw new PersistenceException("Error al listar lotes", e);
            }
        });
    }

    /**
     * Lista las órdenes de servicio registradas para selección de auxiliares.
     *
     * @return lista de órdenes
     */
    public List<OrdenResumen> listarOrdenes() {
        auth.exigirContador();
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT codigo, descripcion, estado FROM orden_servicio ORDER BY codigo");
                    ResultSet rs = ps.executeQuery()) {
                List<OrdenResumen> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new OrdenResumen(rs.getString("codigo"),
                            rs.getString("descripcion"), rs.getString("estado")));
                }
                return list;
            } catch (SQLException e) {
                throw new PersistenceException("Error al listar órdenes de servicio", e);
            }
        });
    }

    /**
     * Simula PEPS, promedio ponderado y UEPS sobre las licencias disponibles.
     * Es una comparación de política y no crea partidas ni altera existencias:
     * el único método habilitado para una venta real es
     * {@link #venderLicenciaPeps}.
     */
    public ComparativoInventarioInfo analizarValoracionInventario(int unidadesSalida) {
        auth.exigirContador();
        if (unidadesSalida <= 0) {
            throw new DomainException("Indique al menos una unidad para el análisis de inventario.");
        }
        List<BigDecimal> costos = jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT li.numero, lo.costo_total, lo.unidades_estimadas
                    FROM licencia li
                    JOIN lote_licencia lo ON lo.id=li.lote_id
                    WHERE li.estado='DISPONIBLE'
                    ORDER BY lo.fecha_liberacion NULLS LAST, lo.codigo_lote, li.numero
                    """);
                    ResultSet rs = ps.executeQuery()) {
                List<BigDecimal> disponibles = new ArrayList<>();
                while (rs.next()) {
                    disponibles.add(costoContablePorUnidad(rs.getBigDecimal("costo_total"),
                            rs.getInt("unidades_estimadas"), rs.getInt("numero")));
                }
                return disponibles;
            } catch (SQLException e) {
                throw new PersistenceException("Error al analizar la valoración de inventario", e);
            }
        });
        if (unidadesSalida > costos.size()) {
            throw new DomainException("No hay suficientes licencias disponibles para la salida analizada.");
        }
        BigDecimal existencia = costos.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2,
                RoundingMode.HALF_UP);
        BigDecimal peps = costos.subList(0, unidadesSalida).stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal ueps = costos.subList(costos.size() - unidadesSalida, costos.size()).stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal promedio = existencia.divide(BigDecimal.valueOf(costos.size()), 12, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(unidadesSalida)).setScale(2, RoundingMode.HALF_UP);
        return new ComparativoInventarioInfo(unidadesSalida, peps, promedio, ueps,
                existencia.subtract(peps), existencia.subtract(promedio), existencia.subtract(ueps));
    }

    /** Libera lote de N licencias con costo total (ej. caso D: 5 × $100 = $500). */
    public LoteInfo liberarLote(String codigoLote, int unidades, BigDecimal costoTotal, LocalDate fecha) {
        auth.exigirContador();
        var perfil = parametrizacion.exigirPerfilPosting("INVENTARIO");
        String cuentaInventario = parametrizacion.resolverCuenta(null, perfil.cuentaDebe(),
                "cuenta de inventario del lote");
        String cuentaOrigen = parametrizacion.resolverCuenta(null, perfil.cuentaHaber(),
                "cuenta de origen del lote");
        if (codigoLote == null || codigoLote.isBlank()) {
            throw new DomainException("El código de lote es obligatorio");
        }
        if (unidades <= 0) {
            throw new DomainException("Unidades deben ser > 0");
        }
        if (costoTotal == null || costoTotal.signum() <= 0) {
            throw new DomainException("El costo total del lote debe ser mayor que cero");
        }
        boolean existe = jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM lote_licencia WHERE codigo_lote=?")) {
                ps.setString(1, codigoLote.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al validar el lote", e);
            }
        });
        if (existe) {
            throw new DomainException(
                    "Ya existe el lote '" + codigoLote.trim() + "'. No se generó una partida duplicada.");
        }
        BigDecimal costoContable = costoTotal.setScale(2, RoundingMode.HALF_UP);
        if (costoContable.signum() <= 0) {
            throw new DomainException("El costo total del lote debe ser al menos un centavo");
        }
        BigDecimal unitario = costoContable.divide(BigDecimal.valueOf(unidades), 6, RoundingMode.HALF_UP);

        UUID loteId = UUID.randomUUID();
        Partida borrador = null;
        Partida contabilizada = null;
        try {
            // Compra/producción → inventario según el perfil parametrizado.
            borrador = partidas.guardarBorrador(TipoPartida.DI, fecha,
                    "Liberación lote " + codigoLote, codigoLote, "LOTE-" + codigoLote,
                    List.of(
                            PartidaDetalle.nueva(cuentaInventario, costoContable, BigDecimal.ZERO),
                            PartidaDetalle.nueva(cuentaOrigen, BigDecimal.ZERO, costoContable)),
                    null);
            // Si hay IVA en compra real se usa plantilla de compra; aquí costo neto ya en
            // banco.
            contabilizada = partidas.contabilizar(borrador.getId());
            Partida partidaFinal = contabilizada;
            jdbc.runInTransaction(conn -> {
                UUID versionId = UUID.fromString("dddddddd-0001-0001-0001-000000000001");
                try {
                    try (PreparedStatement ps = conn.prepareStatement("""
                            INSERT INTO lote_licencia (id, version_id, codigo_lote, unidades_estimadas, costo_total,
                                costo_unitario, unidades_disponibles, fecha_liberacion, partida_id)
                            VALUES (?,?,?,?,?,?,?,?,?)
                            """)) {
                        ps.setObject(1, loteId);
                        ps.setObject(2, versionId);
                        ps.setString(3, codigoLote);
                        ps.setInt(4, unidades);
                        ps.setBigDecimal(5, costoContable);
                        ps.setBigDecimal(6, unitario);
                        ps.setInt(7, unidades);
                        ps.setObject(8, fecha);
                        ps.setObject(9, partidaFinal.getId());
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = conn.prepareStatement("""
                            INSERT INTO licencia (id, lote_id, numero, estado, costo_unitario)
                            VALUES (?,?,?,'DISPONIBLE',?)
                            """)) {
                        for (int i = 1; i <= unidades; i++) {
                            ps.setObject(1, UUID.randomUUID());
                            ps.setObject(2, loteId);
                            ps.setInt(3, i);
                            ps.setBigDecimal(4, costoContablePorUnidad(costoContable, unidades, i));
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                } catch (SQLException e) {
                    throw new PersistenceException("Error al crear lote/licencias", e);
                }
            });
            return new LoteInfo(loteId, codigoLote, unidades, unitario);
        } catch (RuntimeException ex) {
            if (contabilizada != null) {
                UUID partidaId = contabilizada.getId();
                compensar(ex, () -> partidas.revertir(partidaId, fecha,
                        "Lote no creado en auxiliar; partida de inventario revertida."));
            } else if (borrador != null) {
                UUID partidaId = borrador.getId();
                compensar(ex, () -> partidas.anular(partidaId,
                        "Lote no creado en auxiliar."));
            }
            throw ex;
        }
    }

    /**
     * Venta de licencia descargable por PEPS: ingreso + IVA + salida del lote
     * disponible con fecha de liberación más antigua. La interfaz no permite
     * elegir un lote para impedir que el costo se seleccione arbitrariamente.
     */
    public Partida venderLicenciaPeps(UUID clienteId, LocalDate fecha,
            BigDecimal precioNeto, String cuentaIngreso) {
        auth.exigirContador();
        exigirClienteActivo(clienteId);
        var fiscal = parametrizacion.exigirParametrosFiscales();
        var perfilVenta = parametrizacion.exigirPerfilPosting("VENTA");
        var perfilCosto = parametrizacion.exigirPerfilPosting("COSTO_VENTA");
        String cuentaPorCobrar = parametrizacion.resolverCuenta(null, perfilVenta.cuentaDebe(),
                "cuenta por cobrar de la venta de licencia");
        String cuentaDeIngreso = parametrizacion.resolverCuenta(cuentaIngreso, perfilVenta.cuentaHaber(),
                "cuenta de ingreso de la venta de licencia");
        String cuentaCostoVenta = parametrizacion.resolverCuenta(null, perfilCosto.cuentaDebe(),
                "cuenta de costo de venta de licencia");
        String cuentaInventario = parametrizacion.resolverCuenta(null, perfilCosto.cuentaHaber(),
                "cuenta de inventario de licencia");
        if (precioNeto == null || precioNeto.signum() <= 0) {
            throw new DomainException("El precio neto debe ser mayor que cero");
        }
        ReservaLicencia reserva = reservarLicenciaPeps();

        var iva = sv.edu.ues.colibri.domain.iva.IvaCalculator.desdeNeto(
                precioNeto, fiscal.iva());

        List<PartidaDetalle> lineas = List.of(
                PartidaDetalle.nueva(cuentaPorCobrar, iva.total(), BigDecimal.ZERO),
                PartidaDetalle.nueva(cuentaDeIngreso, BigDecimal.ZERO, iva.base()),
                PartidaDetalle.nueva(fiscal.cuentaDebito(), BigDecimal.ZERO, iva.iva()),
                PartidaDetalle.nueva(cuentaCostoVenta, reserva.costoContable(), BigDecimal.ZERO),
                PartidaDetalle.nueva(cuentaInventario, BigDecimal.ZERO, reserva.costoContable()));
        Partida borrador = null;
        boolean entregaConfirmada = false;
        try {
            // La entrega se confirma antes de la contabilización. Si esta falla, el
            // bloque catch revierte la entrega y devuelve la unidad al lote, por lo
            // que no queda una partida de venta contabilizada sin licencia entregada.
            entregarReserva(reserva.licenciaId(), clienteId, fecha);
            entregaConfirmada = true;
            borrador = partidas.guardarBorrador(TipoPartida.DI, fecha, "Venta licencia lote " + reserva.codigoLote(),
                    "LIC-" + reserva.codigoLote(), "VTA-LIC-" + UUID.randomUUID(), new ArrayList<>(lineas), null);
            return partidas.contabilizar(borrador.getId());
        } catch (RuntimeException ex) {
            if (borrador != null) {
                UUID borradorId = borrador.getId();
                compensar(ex, () -> partidas.anular(borradorId,
                        "Venta de licencia no completada; inventario restituido."));
            }
            if (entregaConfirmada) {
                compensar(ex, () -> cancelarEntrega(reserva.loteId(), reserva.licenciaId()));
            } else {
                compensar(ex, () -> liberarReserva(reserva.loteId(), reserva.licenciaId()));
            }
            throw ex;
        }
    }

    private void exigirClienteActivo(UUID clienteId) {
        if (clienteId == null) {
            throw new DomainException("El cliente es obligatorio para conceder una licencia");
        }
        jdbc.inTransaction(conn -> {
            var cliente = terceros.porId(conn, clienteId)
                    .orElseThrow(() -> new DomainException("Cliente no encontrado"));
            if (!cliente.isCliente()) {
                throw new DomainException("El tercero no está marcado como cliente");
            }
            if (!cliente.isActivo()) {
                throw new DomainException("El cliente está inactivo");
            }
            return cliente;
        });
    }

    private void compensar(RuntimeException original, Runnable accion) {
        try {
            accion.run();
        } catch (RuntimeException compensacion) {
            original.addSuppressed(compensacion);
        }
    }

    /**
     * Reserva una unidad aplicando PEPS por fecha de liberación, código de lote
     * y número de licencia. El bloqueo de fila evita que dos operaciones tomen
     * la misma unidad en ejecuciones concurrentes.
     */
    private ReservaLicencia reservarLicenciaPeps() {
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement disponible = conn.prepareStatement("""
                    SELECT li.id AS licencia_id, li.numero, lo.id AS lote_id, lo.costo_total,
                           lo.unidades_estimadas, lo.codigo_lote
                    FROM licencia li
                    JOIN lote_licencia lo ON lo.id = li.lote_id
                    WHERE li.estado='DISPONIBLE' AND lo.unidades_disponibles > 0
                    ORDER BY lo.fecha_liberacion NULLS LAST, lo.codigo_lote, li.numero
                    FOR UPDATE OF li, lo
                    LIMIT 1
                    """)) {
                try (ResultSet rs = disponible.executeQuery()) {
                    if (!rs.next()) {
                        throw new DomainException("No hay licencias disponibles para aplicar PEPS");
                    }
                    UUID licenciaId = (UUID) rs.getObject("licencia_id");
                    UUID loteId = (UUID) rs.getObject("lote_id");
                    BigDecimal costo = costoContablePorUnidad(rs.getBigDecimal("costo_total"),
                            rs.getInt("unidades_estimadas"), rs.getInt("numero"));
                    String codigo = rs.getString("codigo_lote");
                    try (PreparedStatement licencia = conn.prepareStatement(
                            "UPDATE licencia SET estado='RESERVADA' WHERE id=? AND estado='DISPONIBLE'")) {
                        licencia.setObject(1, licenciaId);
                        if (licencia.executeUpdate() != 1) {
                            throw new DomainException("No se pudo reservar la licencia PEPS seleccionada");
                        }
                    }
                    try (PreparedStatement lote = conn.prepareStatement("""
                            UPDATE lote_licencia SET unidades_disponibles = unidades_disponibles - 1
                            WHERE id=? AND unidades_disponibles > 0
                            """)) {
                        lote.setObject(1, loteId);
                        if (lote.executeUpdate() != 1) {
                            throw new DomainException("No se pudo actualizar la disponibilidad del lote PEPS");
                        }
                    }
                    return new ReservaLicencia(licenciaId, loteId, costo, codigo);
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al reservar licencia por PEPS", e);
            }
        });
    }

    /**
     * Distribuye los centavos residuales de un lote entre sus primeras unidades.
     * Así la suma de todas las salidas coincide exactamente con el costo total
     * contabilizado al liberar el lote (por ejemplo, $10.00 / 3 = 3.34, 3.33,
     * 3.33).
     */
    static BigDecimal costoContablePorUnidad(BigDecimal costoTotal, int unidades, int numero) {
        if (costoTotal == null || unidades <= 0 || numero < 1 || numero > unidades) {
            throw new IllegalArgumentException("Lote o número de licencia inválido para distribuir el costo");
        }
        BigDecimal total = costoTotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal base = total.divide(BigDecimal.valueOf(unidades), 2, RoundingMode.DOWN);
        int unidadesConCentavoExtra = total.subtract(base.multiply(BigDecimal.valueOf(unidades)))
                .movePointRight(2).intValueExact();
        return numero <= unidadesConCentavoExtra
                ? base.add(new BigDecimal("0.01"))
                : base;
    }

    private void entregarReserva(UUID licenciaId, UUID clienteId, LocalDate fecha) {
        jdbc.runInTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    UPDATE licencia SET estado='ENTREGADA', cliente_id=?, fecha_entrega=?, documento_id=NULL
                    WHERE id=? AND estado='RESERVADA'
                    """)) {
                ps.setObject(1, clienteId);
                ps.setObject(2, fecha);
                ps.setObject(3, licenciaId);
                if (ps.executeUpdate() != 1) {
                    throw new DomainException("No se pudo confirmar la entrega de la licencia reservada");
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al entregar licencia", e);
            }
        });
    }

    private void liberarReserva(UUID loteId, UUID licenciaId) {
        jdbc.runInTransaction(conn -> {
            try (PreparedStatement licencia = conn.prepareStatement(
                    "UPDATE licencia SET estado='DISPONIBLE' WHERE id=? AND estado='RESERVADA'")) {
                licencia.setObject(1, licenciaId);
                if (licencia.executeUpdate() == 1) {
                    try (PreparedStatement lote = conn.prepareStatement(
                            "UPDATE lote_licencia SET unidades_disponibles = unidades_disponibles + 1 WHERE id=?")) {
                        lote.setObject(1, loteId);
                        lote.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                throw new PersistenceException("No se pudo liberar la reserva de licencia", e);
            }
        });
    }

    /**
     * Revierte una entrega interna si la contabilización posterior no se completa.
     */
    private void cancelarEntrega(UUID loteId, UUID licenciaId) {
        jdbc.runInTransaction(conn -> {
            try (PreparedStatement licencia = conn.prepareStatement("""
                    UPDATE licencia
                    SET estado='DISPONIBLE', cliente_id=NULL, fecha_entrega=NULL, documento_id=NULL
                    WHERE id=? AND estado='ENTREGADA'
                    """)) {
                licencia.setObject(1, licenciaId);
                if (licencia.executeUpdate() != 1) {
                    throw new DomainException("No se pudo restituir la licencia entregada");
                }
                try (PreparedStatement lote = conn.prepareStatement(
                        "UPDATE lote_licencia SET unidades_disponibles = unidades_disponibles + 1 WHERE id=?")) {
                    lote.setObject(1, loteId);
                    if (lote.executeUpdate() != 1) {
                        throw new DomainException("No se pudo restituir la disponibilidad del lote");
                    }
                }
            } catch (SQLException e) {
                throw new PersistenceException("No se pudo cancelar la entrega de licencia", e);
            }
        });
    }

    /**
     * Obtiene la orden de referencia usada por los casos demostrativos.
     *
     * @return información de la orden principal
     */
    public OrdenInfo ordenPrincipal() {
        auth.exigirContador();
        return jdbc.inTransaction(conn -> cargarOrden(conn, "OS2026001"));
    }

    /**
     * Compara el costo estimado de una orden con su costo real acumulado y
     * calcula varianza, cumplimiento, ahorro, sobrecosto y rentabilidad.
     *
     * @param codigoOrden código de la orden a evaluar
     * @return comparación gerencial entre el presupuesto y el costo real
     */
    public DesviacionOrdenInfo analizarDesviacionOrden(String codigoOrden) {
        auth.exigirContador();
        OrdenInfo orden = jdbc
                .inTransaction(conn -> cargarOrden(conn, exigirTexto(codigoOrden, "El código de la orden")));
        return compararCostos(orden.id(), orden.codigo(), orden.presupuesto(), orden.costoAcumulado(),
                orden.precioVenta());
    }

    /**
     * Compara dos importes y devuelve sus indicadores de desviación.
     *
     * @param id            identificador de referencia de la orden
     * @param codigo        código legible de la orden
     * @param costoEstimado costo presupuestado o estimado
     * @param costoReal     costo real acumulado en los registros
     * @param precioVenta   precio pactado o esperado para la orden
     * @return indicadores de variación y rentabilidad
     */
    public DesviacionOrdenInfo compararCostos(UUID id, String codigo, BigDecimal costoEstimado,
            BigDecimal costoReal, BigDecimal precioVenta) {
        validarImporte(costoEstimado, "costo estimado");
        validarImporte(costoReal, "costo real");
        validarImporte(precioVenta, "precio de venta");
        BigDecimal estimado = redondear(costoEstimado);
        BigDecimal real = redondear(costoReal);
        BigDecimal variacion = real.subtract(estimado).setScale(2, RoundingMode.HALF_UP);
        BigDecimal variacionPorcentual = estimado.signum() == 0
                ? BigDecimal.ZERO
                : variacion.divide(estimado, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal indiceEjecucion = estimado.signum() == 0
                ? BigDecimal.ZERO
                : real.divide(estimado, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal ahorro = estimado.subtract(real).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal sobrecosto = real.subtract(estimado).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal utilidadEsperada = redondear(precioVenta).subtract(estimado).setScale(2, RoundingMode.HALF_UP);
        BigDecimal utilidadReal = redondear(precioVenta).subtract(real).setScale(2, RoundingMode.HALF_UP);
        BigDecimal rentabilidad = precioVenta.signum() == 0
                ? BigDecimal.ZERO
                : utilidadReal.divide(precioVenta, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        String diagnostico = variacion.signum() > 0
                ? "Sobrecosto de $%s frente al presupuesto.".formatted(sobrecosto)
                : variacion.signum() < 0
                        ? "Ahorro presupuestario de $%s frente al estimado.".formatted(ahorro)
                        : "Ejecución exactamente al costo estimado.";
        return new DesviacionOrdenInfo(id, codigo, estimado, real, variacion, variacionPorcentual,
                indiceEjecucion, ahorro, sobrecosto, utilidadEsperada, utilidadReal, rentabilidad, diagnostico);
    }

    /**
     * Registra el consumo acumulado de una orden de servicio.
     *
     * @param codigoOrden código de la orden
     * @param tipo        tipo de costo
     * @param descripcion descripción del consumo
     * @param cantidad    cantidad consumida
     * @param tarifa      tarifa aplicada
     * @param fecha       fecha contable
     * @return información actualizada de la orden
     */
    public OrdenInfo registrarConsumoOrden(String codigoOrden, String tipo, String descripcion,
            BigDecimal cantidad, BigDecimal tarifa, LocalDate fecha) {
        auth.exigirContador();
        String orden = exigirTexto(codigoOrden, "El código de la orden");
        String clase = normalizarTipoCosto(tipo);
        String detalle = exigirTexto(descripcion, "La descripción del consumo");
        validarConsumo(cantidad, tarifa, fecha);
        UUID ordenId = exigirOrdenId(orden);
        BigDecimal importe = cantidad.multiply(tarifa).setScale(2, RoundingMode.HALF_UP);
        String cuentaCosto = switch (clase) {
            case "MOD" -> "110501";
            case "SERVICIO" -> "110502";
            case "CIF" -> "110503";
            default -> throw new DomainException("Tipo de costo no admitido");
        };
        // Acumula en servicios en proceso contra banco/indirectos aplicados
        String haber = "CIF".equals(clase) ? "520201" : "110102";
        Partida contabilizada = null;
        try {
            Partida p = partidas.guardarBorrador(TipoPartida.DI, fecha,
                    "Costo " + orden + " " + detalle, orden,
                    "ORD-" + orden + "-" + UUID.randomUUID(),
                    List.of(
                            PartidaDetalle.nueva(cuentaCosto, importe, BigDecimal.ZERO),
                            PartidaDetalle.nueva(haber, BigDecimal.ZERO, importe)),
                    null);
            contabilizada = partidas.contabilizar(p.getId());
            Partida partidaFinal = contabilizada;
            return jdbc.inTransaction(conn -> {
                try {
                    try (PreparedStatement ps = conn.prepareStatement(
                            """
                                    INSERT INTO orden_consumo (id, orden_id, tipo, descripcion, cantidad, tarifa, importe, fecha, partida_id)
                                    VALUES (?,?,?,?,?,?,?,?,?)
                                    """)) {
                        ps.setObject(1, UUID.randomUUID());
                        ps.setObject(2, ordenId);
                        ps.setString(3, clase);
                        ps.setString(4, detalle);
                        ps.setBigDecimal(5, cantidad);
                        ps.setBigDecimal(6, tarifa);
                        ps.setBigDecimal(7, importe);
                        ps.setObject(8, fecha);
                        ps.setObject(9, partidaFinal.getId());
                        ps.executeUpdate();
                    }
                    return cargarOrden(conn, orden);
                } catch (SQLException e) {
                    throw new PersistenceException("Error al registrar consumo", e);
                }
            });
        } catch (RuntimeException ex) {
            if (contabilizada != null) {
                UUID partidaId = contabilizada.getId();
                compensar(ex, () -> partidas.revertir(partidaId, fecha,
                        "Consumo de orden no registrado en auxiliar; partida revertida."));
            }
            throw ex;
        }
    }

    /** Reconoce costo de orden a resultados (5101) desde 1105. */
    public Partida reconocerCostoOrden(String codigoOrden, LocalDate fecha) {
        auth.exigirContador();
        Partida reconocido = jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT id FROM partida
                    WHERE concepto = ? AND estado = 'CONTABILIZADA'
                    ORDER BY fecha_contable DESC LIMIT 1
                    """)) {
                ps.setString(1, "Reconocimiento costo " + codigoOrden);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? partidaRepository.buscarPorId(conn, (UUID) rs.getObject(1)).orElse(null)
                            : null;
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al comprobar costo reconocido", e);
            }
        });
        if (reconocido != null) {
            return reconocido;
        }
        OrdenInfo info = jdbc.inTransaction(conn -> cargarOrden(conn, codigoOrden));
        if (info.costoAcumulado().signum() <= 0) {
            throw new DomainException("La orden no tiene costo acumulado");
        }
        List<PartidaDetalle> lineas = new ArrayList<>();
        // Cada elemento conserva su cuenta de resultados y su cuenta en proceso.
        jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT c.tipo, COALESCE(SUM(c.importe), 0) AS importe
                    FROM orden_consumo c
                    JOIN partida p ON p.id=c.partida_id
                    WHERE c.orden_id=? AND p.estado='CONTABILIZADA'
                    GROUP BY c.tipo
                    """)) {
                ps.setObject(1, info.id());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        BigDecimal importe = rs.getBigDecimal("importe");
                        if (importe.signum() > 0) {
                            String tipo = rs.getString("tipo");
                            String cuentaProceso = switch (tipo) {
                                case "MOD" -> "110501";
                                case "CIF" -> "110503";
                                default -> "110502";
                            };
                            String cuentaResultado = switch (tipo) {
                                case "MOD" -> "510101";
                                case "CIF" -> "510103";
                                default -> "510102";
                            };
                            lineas.add(PartidaDetalle.nueva(cuentaResultado, importe, BigDecimal.ZERO));
                            lineas.add(PartidaDetalle.nueva(cuentaProceso, BigDecimal.ZERO, importe));
                        }
                    }
                }
                return null;
            } catch (SQLException e) {
                throw new PersistenceException("Error al desglosar costos de la orden", e);
            }
        });
        Partida p = partidas.guardarBorrador(TipoPartida.DI, fecha,
                "Reconocimiento costo " + codigoOrden, codigoOrden,
                "REC-" + codigoOrden + "-" + UUID.randomUUID(),
                lineas, null);
        return partidas.contabilizar(p.getId());
    }

    private OrdenInfo cargarOrden(Connection conn, String codigo) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT o.id, o.codigo, o.descripcion, o.presupuesto, o.precio_venta,
                       COALESCE((SELECT SUM(c.importe)
                                 FROM orden_consumo c
                                 JOIN partida p ON p.id=c.partida_id
                                 WHERE c.orden_id=o.id AND p.estado='CONTABILIZADA'),0) AS costo
                FROM orden_servicio o WHERE o.codigo=?
                """)) {
            ps.setString(1, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new DomainException("Orden no encontrada");
                }
                BigDecimal presupuesto = rs.getBigDecimal("presupuesto");
                BigDecimal precio = rs.getBigDecimal("precio_venta");
                BigDecimal costo = rs.getBigDecimal("costo");
                BigDecimal margen = precio.subtract(costo);
                return new OrdenInfo((UUID) rs.getObject("id"), rs.getString("codigo"),
                        rs.getString("descripcion"), presupuesto, precio, costo, margen);
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al cargar orden", e);
        }
    }

    /**
     * Carga los consumos demostrativos de la orden OS2026001 si aún no existen.
     *
     * @param fecha fecha contable
     */
    public void cargarConsumosOs2026001(LocalDate fecha) {
        OrdenInfo orden = ordenPrincipal();
        if (orden.costoAcumulado().compareTo(new BigDecimal("1800.00")) == 0) {
            return;
        }
        if (orden.costoAcumulado().signum() > 0) {
            throw new DomainException("La orden ya tiene consumos registrados; no se duplicaron.");
        }
        // Caso: MOD $1,200 (100h × $12), servicios $300, CIF $300
        registrarConsumoOrden("OS2026001", "MOD", "Análisis/desarrollo/pruebas",
                new BigDecimal("100"), new BigDecimal("12"), fecha);
        registrarConsumoOrden("OS2026001", "SERVICIO", "Recursos exclusivos",
                BigDecimal.ONE, new BigDecimal("300"), fecha);
        registrarConsumoOrden("OS2026001", "CIF", "CIF $3 × 100 h",
                new BigDecimal("100"), new BigDecimal("3"), fecha);
    }

    /**
     * Registra un costo de implementación, capacitación o soporte contra el plan
     * facturado. Estos servicios se reconocen en el período, pero el auxiliar
     * conserva el objeto de costo (plan) y sus elementos MOD, directos y CIF.
     */
    /**
     * Registra un consumo imputado a un plan de servicio.
     *
     * @param codigoPlan  código del plan
     * @param tipo        tipo de costo
     * @param descripcion descripción del consumo
     * @param cantidad    cantidad consumida
     * @param tarifa      tarifa aplicada
     * @param fecha       fecha contable
     * @return costos acumulados del plan
     */
    public PlanCostoInfo registrarConsumoPlan(String codigoPlan, String tipo, String descripcion,
            BigDecimal cantidad, BigDecimal tarifa, LocalDate fecha) {
        auth.exigirContador();
        PlanRef plan = exigirPlanActivo(codigoPlan, "SERVICIO");
        String clase = normalizarTipoCosto(tipo);
        String detalle = exigirTexto(descripcion, "La descripción del consumo");
        validarConsumo(cantidad, tarifa, fecha);
        BigDecimal importe = cantidad.multiply(tarifa).setScale(2, RoundingMode.HALF_UP);
        String cuentaCosto = switch (clase) {
            case "MOD" -> "510101";
            case "SERVICIO" -> "510102";
            case "CIF" -> "510103";
            default -> throw new DomainException("Tipo de costo no admitido");
        };
        String cuentaOrigen = "CIF".equals(clase) ? "520201" : "110102";
        Partida contabilizada = null;
        try {
            Partida borrador = partidas.guardarBorrador(TipoPartida.DI, fecha,
                    "Costo plan " + plan.codigo() + " " + detalle, plan.codigo(),
                    "PLAN-" + plan.codigo() + "-" + UUID.randomUUID(),
                    List.of(
                            PartidaDetalle.nueva(cuentaCosto, plan.codigo(), "CC-03", importe, BigDecimal.ZERO),
                            PartidaDetalle.nueva(cuentaOrigen, plan.codigo(), "CC-03", BigDecimal.ZERO, importe)),
                    null);
            contabilizada = partidas.contabilizar(borrador.getId());
            Partida partidaFinal = contabilizada;
            return jdbc.inTransaction(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        """
                                INSERT INTO plan_consumo (id, plan_id, tipo, descripcion, cantidad, tarifa, importe, fecha, partida_id)
                                VALUES (?,?,?,?,?,?,?,?,?)
                                """)) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, plan.id());
                    ps.setString(3, clase);
                    ps.setString(4, detalle);
                    ps.setBigDecimal(5, cantidad);
                    ps.setBigDecimal(6, tarifa);
                    ps.setBigDecimal(7, importe);
                    ps.setObject(8, fecha);
                    ps.setObject(9, partidaFinal.getId());
                    ps.executeUpdate();
                    return cargarCostoPlan(conn, plan, null, null);
                } catch (SQLException e) {
                    throw new PersistenceException("Error al registrar el consumo del plan", e);
                }
            });
        } catch (RuntimeException ex) {
            if (contabilizada != null) {
                UUID partidaId = contabilizada.getId();
                compensar(ex, () -> partidas.revertir(partidaId, fecha,
                        "Consumo de plan no registrado en auxiliar; partida revertida."));
            }
            throw ex;
        }
    }

    /**
     * Devuelve la acumulación de costos reales de un servicio por plan facturado.
     */
    public PlanCostoInfo costoPlanFacturado(String codigoPlan, LocalDate desde, LocalDate hasta) {
        auth.exigirContador();
        validarRango(desde, hasta);
        PlanRef plan = exigirPlanActivo(codigoPlan, "SERVICIO");
        return jdbc.inTransaction(conn -> cargarCostoPlan(conn, plan, desde, hasta));
    }

    /**
     * Costeo por procesos SaaS. Agrupa alojamiento, soporte e infraestructura
     * compartida y distribuye el total sobre usuarios-mes activos del período.
     */
    /**
     * Calcula el costo SaaS por proceso para un rango de fechas.
     *
     * @param desde    fecha inicial
     * @param hasta    fecha final
     * @param usuarios cantidad de usuarios del período
     * @return costo SaaS calculado
     */
    public CostoSaasInfo costoSaasPorProceso(LocalDate desde, LocalDate hasta, int usuarios) {
        auth.exigirContador();
        validarRango(desde, hasta);
        if (usuarios <= 0) {
            throw new DomainException("Indique usuarios activos del mes.");
        }
        return calcularCostoSaas(desde, hasta, usuarios);
    }

    /**
     * Persiste el denominador de costeo SaaS: usuarios con cobertura durante un
     * mes para un plan. Registrar nuevamente el plan y período reemplaza la
     * medición anterior; no duplica usuarios-mes.
     */
    /**
     * Registra la base de usuarios-mes de un plan SaaS.
     *
     * @param codigoPlan  código del plan
     * @param periodo     período mensual
     * @param usuariosMes usuarios-mes del período
     */
    public void registrarUsuariosMesSaas(String codigoPlan, LocalDate periodo, int usuariosMes) {
        auth.exigirContador();
        if (periodo == null) {
            throw new DomainException("El período SaaS es obligatorio.");
        }
        if (usuariosMes <= 0) {
            throw new DomainException("Los usuarios-mes deben ser mayores que cero.");
        }
        PlanRef plan = exigirPlanActivo(codigoPlan, "SAAS");
        LocalDate mes = periodo.withDayOfMonth(1);
        jdbc.runInTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO saas_usuario_mes (plan_id, periodo, usuarios_mes)
                    VALUES (?,?,?)
                    ON CONFLICT (plan_id, periodo) DO UPDATE
                    SET usuarios_mes=EXCLUDED.usuarios_mes, actualizado_en=CURRENT_TIMESTAMP
                    """)) {
                ps.setObject(1, plan.id());
                ps.setObject(2, mes);
                ps.setInt(3, usuariosMes);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new PersistenceException("Error al registrar usuarios-mes del plan SaaS", e);
            }
        });
    }

    /**
     * Prorratea el proceso SaaS entre planes por usuarios-mes persistidos. El
     * resultado es de costeo gerencial; los costos de alojamiento, soporte e
     * infraestructura ya están respaldados por las partidas del período.
     */
    /**
     * Distribuye el costo SaaS por proceso a un plan concreto.
     *
     * @param codigoPlan código del plan
     * @param desde      fecha inicial
     * @param hasta      fecha final
     * @return costo asignado al plan
     */
    public CostoSaasPlanInfo costoSaasPorProcesoPlan(String codigoPlan, LocalDate desde, LocalDate hasta) {
        auth.exigirContador();
        validarRango(desde, hasta);
        PlanRef plan = exigirPlanActivo(codigoPlan, "SAAS");
        List<SaasParticipacion> participaciones = jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT s.plan_id, p.codigo, SUM(s.usuarios_mes) AS usuarios_mes
                    FROM saas_usuario_mes s
                    JOIN plan_servicio p ON p.id=s.plan_id
                    WHERE p.modalidad='SAAS'
                      AND s.periodo BETWEEN ? AND ?
                    GROUP BY s.plan_id, p.codigo
                    ORDER BY p.codigo, s.plan_id
                    """)) {
                ps.setObject(1, desde.withDayOfMonth(1));
                ps.setObject(2, hasta.withDayOfMonth(1));
                try (ResultSet rs = ps.executeQuery()) {
                    List<SaasParticipacion> resultado = new ArrayList<>();
                    while (rs.next()) {
                        resultado.add(new SaasParticipacion((UUID) rs.getObject("plan_id"),
                                rs.getString("codigo"), rs.getInt("usuarios_mes")));
                    }
                    return resultado;
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al obtener usuarios-mes SaaS", e);
            }
        });
        SaasParticipacion participacionPlan = participaciones.stream()
                .filter(p -> p.planId().equals(plan.id())).findFirst().orElse(null);
        if (participacionPlan == null || participacionPlan.usuariosMes() <= 0) {
            throw new DomainException("No hay usuarios-mes registrados para el plan " + plan.codigo() + ".");
        }
        int usuariosTotales = participaciones.stream().mapToInt(SaasParticipacion::usuariosMes).sum();
        CostoSaasInfo proceso = calcularCostoSaas(desde, hasta, usuariosTotales);
        BigDecimal alojamiento = asignarPorUsuariosMes(proceso.alojamiento(), participaciones, plan.id());
        BigDecimal soporte = asignarPorUsuariosMes(proceso.soporte(), participaciones, plan.id());
        BigDecimal infraestructura = asignarPorUsuariosMes(proceso.infraestructuraCompartida(), participaciones,
                plan.id());
        BigDecimal total = alojamiento.add(soporte).add(infraestructura);
        return new CostoSaasPlanInfo(plan.codigo(), plan.nombre(), participacionPlan.usuariosMes(), usuariosTotales,
                alojamiento,
                soporte, infraestructura, total, proceso.costoUsuarioMes());
    }

    private CostoSaasInfo calcularCostoSaas(LocalDate desde, LocalDate hasta, int usuarios) {
        BigDecimal[] componentes = jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT
                      COALESCE(SUM(CASE WHEN d.codigo_cuenta='510201' THEN d.debe - d.haber ELSE 0 END), 0)
                        AS alojamiento,
                      COALESCE(SUM(CASE WHEN d.codigo_cuenta='510202' THEN d.debe - d.haber ELSE 0 END), 0)
                        AS soporte,
                      COALESCE(SUM(CASE WHEN d.codigo_cuenta='510203' THEN d.debe - d.haber ELSE 0 END), 0)
                        AS infraestructura
                    FROM partida_detalle d
                    JOIN partida p ON p.id = d.partida_id
                    WHERE p.estado IN ('CONTABILIZADA','REVERTIDA')
                      AND p.fecha_contable BETWEEN ? AND ?
                      AND d.codigo_cuenta IN ('510201','510202','510203')
                    """)) {
                ps.setObject(1, desde);
                ps.setObject(2, hasta);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return new BigDecimal[] {
                            rs.getBigDecimal("alojamiento").max(BigDecimal.ZERO),
                            rs.getBigDecimal("soporte").max(BigDecimal.ZERO),
                            rs.getBigDecimal("infraestructura").max(BigDecimal.ZERO)
                    };
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al calcular costo SaaS", e);
            }
        });
        BigDecimal total = componentes[0].add(componentes[1]).add(componentes[2]);
        return new CostoSaasInfo(componentes[0], componentes[1], componentes[2], usuarios,
                total, total.divide(BigDecimal.valueOf(usuarios), 2, RoundingMode.HALF_UP));
    }

    /**
     * Aplica el método de restos mayores en centavos. La asignación de cada
     * componente queda exactamente conciliada con su bolsa de costo, y un
     * empate se resuelve por código de plan para que sea reproducible.
     */
    static BigDecimal asignarPorUsuariosMes(BigDecimal importe, List<SaasParticipacion> participaciones,
            UUID planId) {
        int usuariosTotales = participaciones.stream().mapToInt(SaasParticipacion::usuariosMes).sum();
        if (usuariosTotales <= 0) {
            throw new DomainException("No hay usuarios-mes para distribuir el costo SaaS.");
        }
        BigInteger centavos = importe.setScale(2, RoundingMode.HALF_UP).movePointRight(2).toBigIntegerExact();
        BigInteger divisor = BigInteger.valueOf(usuariosTotales);
        List<ParticipacionCentavos> bases = new ArrayList<>();
        BigInteger sumaResiduos = BigInteger.ZERO;
        for (SaasParticipacion participacion : participaciones) {
            BigInteger producto = centavos.multiply(BigInteger.valueOf(participacion.usuariosMes()));
            BigInteger[] division = producto.divideAndRemainder(divisor);
            bases.add(new ParticipacionCentavos(participacion.planId(), participacion.codigo(), division[0],
                    division[1]));
            sumaResiduos = sumaResiduos.add(division[1]);
        }
        int centavosRestantes = sumaResiduos.divide(divisor).intValueExact();
        bases.sort((a, b) -> {
            int porResiduo = b.residuo().compareTo(a.residuo());
            return porResiduo != 0 ? porResiduo : a.codigo().compareTo(b.codigo());
        });
        for (int i = 0; i < bases.size(); i++) {
            ParticipacionCentavos actual = bases.get(i);
            if (actual.planId().equals(planId)) {
                BigInteger asignado = actual.base().add(i < centavosRestantes ? BigInteger.ONE : BigInteger.ZERO);
                return new BigDecimal(asignado, 2);
            }
        }
        throw new DomainException("El plan no participa en la distribución SaaS.");
    }

    private PlanCostoInfo cargarCostoPlan(Connection conn, PlanRef plan, LocalDate desde, LocalDate hasta) {
        String sql = """
                SELECT
                  COALESCE(SUM(CASE WHEN c.tipo='MOD' THEN c.importe ELSE 0 END), 0) AS mod,
                  COALESCE(SUM(CASE WHEN c.tipo='SERVICIO' THEN c.importe ELSE 0 END), 0) AS servicio,
                  COALESCE(SUM(CASE WHEN c.tipo='CIF' THEN c.importe ELSE 0 END), 0) AS cif
                FROM plan_consumo c
                JOIN partida p ON p.id=c.partida_id
                WHERE c.plan_id=? AND p.estado='CONTABILIZADA'
                """ + (desde == null ? "" : " AND c.fecha BETWEEN ? AND ?");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, plan.id());
            if (desde != null) {
                ps.setObject(2, desde);
                ps.setObject(3, hasta);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                BigDecimal mod = rs.getBigDecimal("mod");
                BigDecimal servicio = rs.getBigDecimal("servicio");
                BigDecimal cif = rs.getBigDecimal("cif");
                return new PlanCostoInfo(plan.codigo(), plan.nombre(), plan.tipoFacturacion(), mod, servicio, cif,
                        mod.add(servicio).add(cif));
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al consultar costos del plan", e);
        }
    }

    private PlanRef exigirPlanActivo(String codigoPlan, String modalidadEsperada) {
        String codigo = exigirTexto(codigoPlan, "El código del plan");
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT id, codigo, nombre, modalidad, tipo_facturacion, activo
                    FROM plan_servicio
                    WHERE codigo=?
                    """)) {
                ps.setString(1, codigo);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new DomainException("Plan no encontrado: " + codigo);
                    }
                    if (!rs.getBoolean("activo")) {
                        throw new DomainException("El plan está inactivo: " + codigo);
                    }
                    String modalidad = rs.getString("modalidad");
                    if (modalidadEsperada != null && !modalidadEsperada.equals(modalidad)) {
                        throw new DomainException("El plan " + codigo + " debe ser de modalidad "
                                + modalidadEsperada + ".");
                    }
                    return new PlanRef((UUID) rs.getObject("id"), rs.getString("codigo"),
                            rs.getString("nombre"), modalidad, rs.getString("tipo_facturacion"));
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al validar el plan", e);
            }
        });
    }

    private UUID exigirOrdenId(String codigoOrden) {
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM orden_servicio WHERE codigo=?")) {
                ps.setString(1, codigoOrden);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new DomainException("Orden no encontrada: " + codigoOrden);
                    }
                    return (UUID) rs.getObject(1);
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al validar la orden", e);
            }
        });
    }

    private String normalizarTipoCosto(String tipo) {
        String normalizado = exigirTexto(tipo, "El tipo de costo").toUpperCase(Locale.ROOT);
        if (!"MOD".equals(normalizado) && !"SERVICIO".equals(normalizado) && !"CIF".equals(normalizado)) {
            throw new DomainException("Tipo de costo inválido. Use MOD, SERVICIO o CIF.");
        }
        return normalizado;
    }

    private String exigirTexto(String valor, String etiqueta) {
        if (valor == null || valor.isBlank()) {
            throw new DomainException(etiqueta + " es obligatorio.");
        }
        return valor.trim();
    }

    private void validarConsumo(BigDecimal cantidad, BigDecimal tarifa, LocalDate fecha) {
        if (cantidad == null || cantidad.signum() <= 0) {
            throw new DomainException("La cantidad debe ser mayor que cero.");
        }
        if (tarifa == null || tarifa.signum() < 0) {
            throw new DomainException("La tarifa no puede ser negativa.");
        }
        if (fecha == null) {
            throw new DomainException("La fecha del consumo es obligatoria.");
        }
    }

    private void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || hasta.isBefore(desde)) {
            throw new DomainException("El rango de fechas para el costeo es inválido.");
        }
    }

    private static void validarImporte(BigDecimal importe, String etiqueta) {
        if (importe == null || importe.signum() < 0) {
            throw new DomainException("El " + etiqueta + " no puede ser negativo.");
        }
    }

    private static BigDecimal redondear(BigDecimal importe) {
        return importe.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Compatibilidad para consumidores que sólo requieren el costo unitario SaaS.
     */
    /**
     * Obtiene el costo por usuario-mes del proceso SaaS.
     *
     * @param desde    fecha inicial
     * @param hasta    fecha final
     * @param usuarios cantidad de usuarios
     * @return costo por usuario-mes
     */
    public BigDecimal costoSaasUsuarioMes(LocalDate desde, LocalDate hasta, int usuarios) {
        return costoSaasPorProceso(desde, hasta, usuarios).costoUsuarioMes();
    }
}
