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
import java.util.Optional;

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
        var contador = authService.exigirContador();
        jdbc.runInTransaction(conn -> {
            String anterior = periodo.getEstado().name();
            periodo.setEstado(nuevo);
            periodo.setVersion(periodo.getVersion() + 1);
            periodoRepository.actualizarEstado(conn, periodo);
            bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                    contador.getId(), "ESTADO_PERIODO", "PERIODO", periodo.getId().toString(),
                    anterior, nuevo.name(), "Cambio de estado de período"));
        });
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
     * Reabre un período.
     *
     * @param periodo período a reabrir
     */
    public void reabrir(Periodo periodo) {
        cambiarEstadoPeriodo(periodo, EstadoPeriodo.ABIERTO);
    }

    /**
     * Coloca un período en revisión.
     *
     * @param periodo período a actualizar
     */
    public void ponerEnRevision(Periodo periodo) {
        cambiarEstadoPeriodo(periodo, EstadoPeriodo.EN_REVISION);
    }
}
