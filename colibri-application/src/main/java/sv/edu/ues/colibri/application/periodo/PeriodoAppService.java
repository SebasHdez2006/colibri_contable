package sv.edu.ues.colibri.application.periodo;

import sv.edu.ues.colibri.application.auth.AuthService;
import sv.edu.ues.colibri.domain.exception.DomainException;
import sv.edu.ues.colibri.domain.model.BitacoraEntrada;
import sv.edu.ues.colibri.domain.model.Ejercicio;
import sv.edu.ues.colibri.domain.model.Empresa;
import sv.edu.ues.colibri.domain.model.EstadoPeriodo;
import sv.edu.ues.colibri.domain.model.Periodo;
import sv.edu.ues.colibri.persistence.jdbc.JdbcSupport;
import sv.edu.ues.colibri.persistence.repo.BitacoraRepository;
import sv.edu.ues.colibri.persistence.repo.EmpresaRepository;
import sv.edu.ues.colibri.persistence.repo.PeriodoRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

/**
 * Servicio de negocio para empresa, ejercicios y períodos contables.
 */
public class PeriodoAppService {

    private final JdbcSupport jdbc;
    private final AuthService authService;
    private final PeriodoRepository periodoRepository = new PeriodoRepository();
    private final EmpresaRepository empresaRepository = new EmpresaRepository();
    private final BitacoraRepository bitacoraRepository = new BitacoraRepository();

    /**
     * Crea el servicio de períodos.
     *
     * @param jdbc soporte transaccional
     * @param authService servicio de autenticación
     */
    public PeriodoAppService(JdbcSupport jdbc, AuthService authService) {
        this.jdbc = jdbc;
        this.authService = authService;
    }

    /**
     * Obtiene la empresa configurada.
     *
     * @return empresa, si existe
     */
    public Optional<Empresa> empresa() {
        authService.exigirContador();
        return jdbc.inTransaction(empresaRepository::obtenerUnica);
    }

    /**
     * Actualiza los datos básicos de la empresa.
     *
     * @param razonSocial razón social
     * @param nombreComercial nombre comercial
     * @param nit NIT
     * @param nrc NRC
     */
    public void actualizarEmpresa(String razonSocial, String nombreComercial, String nit, String nrc) {
        actualizarEmpresa(razonSocial, nombreComercial, nit, nrc, null, null, null, null, null, null, null);
    }

    /**
     * Actualiza el perfil institucional completo de la empresa.
     *
     * @param razonSocial razón social
     * @param nombreComercial nombre comercial
     * @param nit NIT
     * @param nrc NRC
     * @param actividadEconomica actividad económica
     * @param direccionFiscal dirección fiscal
     * @param logoRuta ruta del logo
     * @param representanteLegal representante legal
     * @param duiRepresentante DUI del representante
     * @param contadorNombre nombre del contador
     * @param registroCvpcpa registro profesional
     */
    public void actualizarEmpresa(String razonSocial, String nombreComercial, String nit, String nrc,
                                  String actividadEconomica, String direccionFiscal, String logoRuta,
                                  String representanteLegal, String duiRepresentante,
                                  String contadorNombre, String registroCvpcpa) {
        var contador = authService.exigirContador();
        jdbc.runInTransaction(conn -> {
            Empresa empresa = empresaRepository.obtenerUnica(conn)
                    .orElseThrow(() -> new DomainException("Empresa no configurada"));
            String anterior = empresa.getRazonSocial();
            empresa.setRazonSocial(razonSocial);
            empresa.setNombreComercial(nombreComercial);
            empresa.setNit(nit);
            empresa.setNrc(nrc);
            empresa.setActividadEconomica(actividadEconomica);
            empresa.setDireccionFiscal(direccionFiscal);
            empresa.setLogoRuta(logoRuta);
            empresa.setRepresentanteLegal(representanteLegal);
            empresa.setDuiRepresentante(duiRepresentante);
            empresa.setContadorNombre(contadorNombre);
            empresa.setRegistroCvpcpa(registroCvpcpa);
            empresa.setActualizadoEn(LocalDateTime.now());
            empresaRepository.actualizar(conn, empresa);
            bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                    contador.getId(), "ACTUALIZAR_EMPRESA", "EMPRESA", empresa.getId().toString(),
                    anterior, razonSocial, null));
        });
    }

    /**
     * Lista los ejercicios registrados.
     *
     * @return ejercicios existentes
     */
    public List<Ejercicio> listarEjercicios() {
        authService.exigirContador();
        return jdbc.inTransaction(periodoRepository::listarEjercicios);
    }

    /**
     * Lista los períodos de un ejercicio.
     *
     * @param ejercicio ejercicio a consultar
     * @return lista de períodos
     */
    public List<Periodo> listarPeriodos(Ejercicio ejercicio) {
        authService.exigirContador();
        return jdbc.inTransaction((java.sql.Connection conn) ->
                periodoRepository.listarPorEjercicio(conn, ejercicio.getId()));
    }

    /**
     * Obtiene el período abierto actual, si existe.
     *
     * @return período abierto o vacío
     */
    public Optional<Periodo> periodoAbierto() {
        authService.exigirContador();
        return jdbc.inTransaction(periodoRepository::periodoAbiertoActual);
    }

    /**
     * Recupera el período que contiene la fecha sin autorizar captura. Es útil
     * para completar una operación ya contabilizada que quedó con su cédula
     * auxiliar pendiente; crear o contabilizar movimientos sigue requiriendo
     * {@link #exigirPeriodoAbiertoParaFecha(LocalDate)}.
     */
    /**
     * Obtiene el período que contiene una fecha determinada.
     *
     * @param fecha fecha contable
     * @return período encontrado
     */
    public Periodo exigirPeriodoParaFecha(LocalDate fecha) {
        authService.exigirContador();
        if (fecha == null) {
            throw new DomainException("La fecha contable es obligatoria.");
        }
        return jdbc.inTransaction(conn -> periodoRepository.buscarPorFecha(conn, fecha)
                .orElseThrow(() -> new DomainException("No hay período para la fecha " + fecha)));
    }

    /**
     * Obtiene el período para una fecha y exige que esté abierto.
     *
     * @param fecha fecha contable
     * @return período abierto correspondiente a la fecha
     */
    public Periodo exigirPeriodoAbiertoParaFecha(LocalDate fecha) {
        if (fecha.isAfter(LocalDate.now())) {
            throw new DomainException("No se admiten fechas futuras (hoy es "
                    + LocalDate.now() + ").");
        }
        Periodo periodo = exigirPeriodoParaFecha(fecha);
        if (!periodo.admiteCaptura()) {
            throw new DomainException(
                    "El período " + periodo.getMes() + " está " + periodo.getEstado()
                            + ". No se admite captura hasta reapertura.");
        }
        return periodo;
    }

    /**
     * Cambia el estado de un período y registra auditoría.
     *
     * @param periodo período objetivo
     * @param nuevo nuevo estado
     */
    public void cambiarEstadoPeriodo(Periodo periodo, EstadoPeriodo nuevo) {
        cambiarEstadoPeriodo(periodo, nuevo, null);
    }

    /**
     * Cambia el estado de un período con motivo opcional y bloqueo optimista
     * por versión. Al reabrir ({@code ABIERTO}) exige que ningún período
     * posterior del mismo ejercicio permanezca cerrado: la reapertura debe
     * hacerse del más reciente al más antiguo para no alterar saldos
     * históricos por debajo de un cierre vigente.
     *
     * @param periodo período objetivo
     * @param nuevo nuevo estado
     * @param motivo justificación documentada del cambio (obligatoria al reabrir)
     */
    public void cambiarEstadoPeriodo(Periodo periodo, EstadoPeriodo nuevo, String motivo) {
        var contador = authService.exigirContador();
        jdbc.runInTransaction(conn -> {
            if (nuevo == EstadoPeriodo.ABIERTO) {
                exigirSinPeriodosPosterioresCerrados(conn, periodo);
            }
            String anterior = periodo.getEstado().name();
            int versionAnterior = periodo.getVersion();
            periodo.setEstado(nuevo);
            periodo.setVersion(periodo.getVersion() + 1);
            periodoRepository.actualizarEstado(conn, periodo, versionAnterior);
            String detalle = motivo == null || motivo.isBlank()
                    ? "Cambio de estado de período" : motivo.trim();
            bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                    contador.getId(), "ESTADO_PERIODO", "PERIODO", periodo.getId().toString(),
                    anterior, nuevo.name(), detalle));
        });
    }

    private void exigirSinPeriodosPosterioresCerrados(java.sql.Connection conn, Periodo periodo) {
        boolean bloqueado = periodoRepository.listarPorEjercicio(conn, periodo.getEjercicioId())
                .stream()
                .anyMatch(p -> p.getFechaInicio().isAfter(periodo.getFechaInicio())
                        && p.getEstado() == EstadoPeriodo.CERRADO);
        if (bloqueado) {
            throw new DomainException("No se puede reabrir el mes " + periodo.getMes()
                    + " del ejercicio mientras existan períodos posteriores cerrados. "
                    + "Reábralos en orden cronológico inverso, del más reciente al más antiguo.");
        }
    }

    /**
     * Marca un período como cerrado.
     *
     * @param periodo período a cerrar
     */
    public void marcarCerrado(Periodo periodo) {
        cambiarEstadoPeriodo(periodo, EstadoPeriodo.CERRADO);
    }

    /**
     * Reabre un período cerrado o en revisión. Exige motivo documentado y
     * re-autenticación del contador, y bloquea la operación si existen
     * períodos posteriores cerrados en el mismo ejercicio.
     *
     * @param periodo período a reabrir
     * @param motivo justificación de la reapertura (obligatoria)
     * @param clave contraseña del usuario en sesión para re-autenticar
     */
    public void reabrir(Periodo periodo, String motivo, char[] clave) {
        if (periodo == null) {
            throw new DomainException("Seleccione el período que desea reabrir.");
        }
        if (periodo.getEstado() == EstadoPeriodo.ABIERTO) {
            throw new DomainException("El período " + periodo.getMes() + " ya está abierto.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new DomainException(
                    "La reapertura de un período requiere un motivo documentado.");
        }
        authService.exigirClaveActual(clave);
        cambiarEstadoPeriodo(periodo, EstadoPeriodo.ABIERTO, motivo.trim());
    }

    /**
     * Coloca un período en revisión.
     *
     * @param periodo período a actualizar
     */
    public void ponerEnRevision(Periodo periodo) {
        cambiarEstadoPeriodo(periodo, EstadoPeriodo.EN_REVISION);
    }

    /**
     * Crea un ejercicio contable anual con sus doce períodos mensuales.
     * Todos los meses quedan cerrados excepto {@code mesInicial}, que queda
     * abierto para comenzar la captura. Registra la acción en bitácora.
     *
     * @param anio año del ejercicio
     * @param mesInicial mes (1-12) que queda abierto
     * @return ejercicio creado
     */
    public Ejercicio crearEjercicio(int anio, int mesInicial) {
        var contador = authService.exigirContador();
        if (anio < 2000 || anio > 2100) {
            throw new DomainException("El año del ejercicio debe estar entre 2000 y 2100.");
        }
        if (mesInicial < 1 || mesInicial > 12) {
            throw new DomainException("El mes inicial debe estar entre 1 y 12.");
        }
        return jdbc.inTransaction(conn -> {
            Empresa empresa = empresaRepository.obtenerUnica(conn)
                    .orElseThrow(() -> new DomainException("Empresa no configurada"));
            boolean existe = periodoRepository.listarEjercicios(conn).stream()
                    .anyMatch(e -> e.getEmpresaId().equals(empresa.getId()) && e.getAnio() == anio);
            if (existe) {
                throw new DomainException("Ya existe el ejercicio " + anio + " para la empresa.");
            }
            int versionCatalogo = periodoRepository.versionCatalogoVigente(conn);
            Ejercicio ejercicio = new Ejercicio(UUID.randomUUID(), empresa.getId(), anio,
                    LocalDate.of(anio, 1, 1), LocalDate.of(anio, 12, 31), true, versionCatalogo);
            periodoRepository.insertarEjercicio(conn, ejercicio);
            for (int mes = 1; mes <= 12; mes++) {
                YearMonth ym = YearMonth.of(anio, mes);
                Periodo periodo = new Periodo(UUID.randomUUID(), ejercicio.getId(), mes,
                        ym.atDay(1), ym.atEndOfMonth(),
                        mes == mesInicial ? EstadoPeriodo.ABIERTO : EstadoPeriodo.CERRADO, 1);
                periodoRepository.insertarPeriodo(conn, periodo);
            }
            bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                    contador.getId(), "CREAR_EJERCICIO", "EJERCICIO", ejercicio.getId().toString(),
                    null, "Ejercicio " + anio + " con 12 períodos; abierto el mes " + mesInicial, null));
            return ejercicio;
        });
    }

    /**
     * Crea un período mensual faltante dentro de un ejercicio existente.
     * El período nace cerrado: debe abrirse de forma explícita antes de capturar.
     *
     * @param ejercicio ejercicio al que pertenece el período
     * @param mes mes calendario (1-12)
     * @return período creado
     */
    public Periodo crearPeriodo(Ejercicio ejercicio, int mes) {
        var contador = authService.exigirContador();
        if (ejercicio == null) {
            throw new DomainException("Seleccione el ejercicio del nuevo período.");
        }
        if (mes < 1 || mes > 12) {
            throw new DomainException("El mes del período debe estar entre 1 y 12.");
        }
        return jdbc.inTransaction(conn -> {
            YearMonth ym = YearMonth.of(ejercicio.getAnio(), mes);
            LocalDate inicio = ym.atDay(1).isBefore(ejercicio.getFechaInicio())
                    ? ejercicio.getFechaInicio() : ym.atDay(1);
            LocalDate fin = ym.atEndOfMonth().isAfter(ejercicio.getFechaFin())
                    ? ejercicio.getFechaFin() : ym.atEndOfMonth();
            if (inicio.isAfter(fin)) {
                throw new DomainException("El mes " + mes + " no corresponde al ejercicio "
                        + ejercicio.getAnio() + ".");
            }
            boolean duplicado = periodoRepository.listarPorEjercicio(conn, ejercicio.getId()).stream()
                    .anyMatch(pp -> pp.getMes() == mes);
            if (duplicado) {
                throw new DomainException("El ejercicio " + ejercicio.getAnio()
                        + " ya tiene el mes " + mes + ".");
            }
            Periodo periodo = new Periodo(UUID.randomUUID(), ejercicio.getId(), mes,
                    inicio, fin, EstadoPeriodo.CERRADO, 1);
            periodoRepository.insertarPeriodo(conn, periodo);
            bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                    contador.getId(), "CREAR_PERIODO", "PERIODO", periodo.getId().toString(),
                    null, "Mes " + mes + " (" + inicio + " a " + fin + ") en ejercicio "
                            + ejercicio.getAnio(), null));
            return periodo;
        });
    }

    /**
     * Activa o desactiva un ejercicio anual. Un ejercicio inactivo deja de
     * participar en la captura de operaciones. No se puede desactivar mientras
     * conserve un período abierto: primero debe cerrarse o moverse la captura
     * a otro ejercicio.
     *
     * @param ejercicio ejercicio a actualizar
     * @param activo nuevo estado
     */
    public void cambiarEstadoEjercicio(Ejercicio ejercicio, boolean activo) {
        var contador = authService.exigirContador();
        if (ejercicio == null) {
            throw new DomainException("Seleccione el ejercicio a actualizar.");
        }
        jdbc.runInTransaction(conn -> {
            if (!activo) {
                boolean hayAbierto = periodoRepository.listarPorEjercicio(conn, ejercicio.getId())
                        .stream().anyMatch(p -> p.getEstado() == EstadoPeriodo.ABIERTO);
                if (hayAbierto) {
                    throw new DomainException("No se puede desactivar el ejercicio "
                            + ejercicio.getAnio() + " mientras tenga períodos abiertos.");
                }
            }
            String anterior = ejercicio.isActivo() ? "ACTIVO" : "INACTIVO";
            ejercicio.setActivo(activo);
            periodoRepository.actualizarActivoEjercicio(conn, ejercicio.getId(), activo);
            bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                    contador.getId(), "ESTADO_EJERCICIO", "EJERCICIO", ejercicio.getId().toString(),
                    anterior, activo ? "ACTIVO" : "INACTIVO", null));
        });
    }
}
