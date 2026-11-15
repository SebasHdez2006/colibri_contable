package sv.edu.ues.colibri.application.partida;

import sv.edu.ues.colibri.application.auth.AuthService;
import sv.edu.ues.colibri.application.catalogo.CatalogoService;
import sv.edu.ues.colibri.application.periodo.PeriodoAppService;
import sv.edu.ues.colibri.domain.exception.DomainException;
import sv.edu.ues.colibri.domain.model.BitacoraEntrada;
import sv.edu.ues.colibri.domain.model.EstadoPartida;
import sv.edu.ues.colibri.domain.model.EventoContable;
import sv.edu.ues.colibri.domain.model.Partida;
import sv.edu.ues.colibri.domain.model.PartidaDetalle;
import sv.edu.ues.colibri.domain.model.Periodo;
import sv.edu.ues.colibri.domain.model.OrigenPartida;
import sv.edu.ues.colibri.domain.model.TipoPartida;
import sv.edu.ues.colibri.persistence.jdbc.JdbcSupport;
import sv.edu.ues.colibri.persistence.repo.BitacoraRepository;
import sv.edu.ues.colibri.persistence.repo.EventoRepository;
import sv.edu.ues.colibri.persistence.repo.PartidaRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CU02 — registro, aprobación, contabilización atómica y reversión (H.2).
 */
/**
 * Servicio de negocio para registrar, aprobar, contabilizar y revertir partidas.
 */
public class PartidaService {

    public record CadenaEvidencia(String documento, String evento, String folio,
                                  String bitacora, String referencia) {
    }

    private final JdbcSupport jdbc;
    private final AuthService authService;
    private final CatalogoService catalogoService;
    private final PeriodoAppService periodoAppService;
    private final PartidaRepository partidaRepository = new PartidaRepository();
    private final EventoRepository eventoRepository = new EventoRepository();
    private final BitacoraRepository bitacoraRepository = new BitacoraRepository();

    /**
     * Crea el servicio de partidas.
     *
     * @param jdbc soporte transaccional
     * @param authService servicio de autenticación
     * @param catalogoService servicio de catálogo de cuentas
     * @param periodoAppService servicio de períodos contables
     */
    public PartidaService(JdbcSupport jdbc, AuthService authService,
                          CatalogoService catalogoService, PeriodoAppService periodoAppService) {
        this.jdbc = jdbc;
        this.authService = authService;
        this.catalogoService = catalogoService;
        this.periodoAppService = periodoAppService;
    }

    /**
     * Guarda o actualiza una partida en estado borrador.
     *
     * @param tipo tipo de partida
     * @param fecha fecha contable
     * @param concepto concepto de la partida
     * @param documentoSoporte documento soporte opcional
     * @param claveEvento clave de evento para idempotencia
     * @param detalles líneas de detalle
     * @param partidaIdExistente identificador de una partida previa a editar
     * @return partida persistida
     */
    public Partida guardarBorrador(TipoPartida tipo, LocalDate fecha, String concepto,
                                   String documentoSoporte, String claveEvento,
                                   List<PartidaDetalle> detalles, UUID partidaIdExistente) {
        var contador = authService.exigirContador();
        Periodo periodo = periodoAppService.exigirPeriodoAbiertoParaFecha(fecha);
        validarCuentasMovimiento(detalles);

        return jdbc.inTransaction(conn -> {
            UUID eventoId = asegurarEvento(conn, claveEvento, concepto);

            Partida partida;
            if (partidaIdExistente != null) {
                partida = partidaRepository.buscarPorId(conn, partidaIdExistente)
                        .orElseThrow(() -> new DomainException("Partida no encontrada"));
                if (partida.getEstado() != EstadoPartida.BORRADOR
                        && partida.getEstado() != EstadoPartida.APROBADA) {
                    throw new DomainException("No se puede editar una partida " + partida.getEstado());
                }
                partida.setTipo(tipo);
                partida.setFechaContable(fecha);
                partida.setPeriodoId(periodo.getId());
                partida.setConcepto(concepto);
                partida.setDocumentoSoporte(documentoSoporte);
                partida.setEventoId(eventoId);
                partida.reemplazarDetalles(detalles);
                partida.setActualizadoEn(LocalDateTime.now());
                partidaRepository.actualizarBorrador(conn, partida);
            } else {
                partida = Partida.borrador(tipo, fecha, periodo.getId(), concepto,
                        documentoSoporte, eventoId, contador.getId(), origen(claveEvento));
                partida.reemplazarDetalles(detalles);
                partidaRepository.insertar(conn, partida);
            }

            bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                    contador.getId(), "GUARDAR_BORRADOR", "PARTIDA", partida.getId().toString(),
                    null, partida.getEstado().name(), concepto));
            return partida;
        });
    }

    private OrigenPartida origen(String claveEvento) {
        if (claveEvento == null) return OrigenPartida.MANUAL;
        String clave = claveEvento.toUpperCase();
        if (clave.startsWith("DOC-VENTA")) return OrigenPartida.VENTA;
        if (clave.contains("PLANILLA")) return OrigenPartida.PLANILLA;
        if (clave.contains("CIF")) return OrigenPartida.CIF;
        if (clave.startsWith("AJ-") || clave.contains("CIERRE")) return OrigenPartida.CIERRE;
        if (clave.startsWith("SEED-")) return OrigenPartida.SEED;
        return OrigenPartida.MANUAL;
    }

    public CadenaEvidencia cadenaEvidencia(UUID partidaId) {
        authService.exigirContador();
        return jdbc.inTransaction(conn -> {
            try (var ps = conn.prepareStatement("""
                    SELECT COALESCE(d.codigo_generacion, d.numero_control, '(sin documento)') documento,
                           e.clave_unica evento, p.folio,
                           COALESCE(b.accion, '(sin bitácora)') bitacora,
                           COALESCE(b.referencia, b.entidad_id, '') referencia
                    FROM partida p
                    LEFT JOIN documento_evento de ON de.partida_id = p.id
                    LEFT JOIN documento d ON d.id = de.documento_id
                    LEFT JOIN evento_contable e ON e.id = p.evento_id
                    LEFT JOIN LATERAL (
                        SELECT accion, referencia, entidad_id
                        FROM bitacora
                        WHERE entidad = 'PARTIDA' AND entidad_id = p.id::text
                        ORDER BY fecha_hora DESC LIMIT 1
                    ) b ON TRUE
                    WHERE p.id = ?
                    """)) {
                ps.setObject(1, partidaId);
                try (var rs = ps.executeQuery()) {
                    if (!rs.next()) throw new DomainException("Partida no encontrada");
                    return new CadenaEvidencia(rs.getString("documento"), rs.getString("evento"),
                            rs.getString("folio"), rs.getString("bitacora"), rs.getString("referencia"));
                }
            } catch (java.sql.SQLException e) {
                throw new sv.edu.ues.colibri.persistence.jdbc.PersistenceException(
                        "No se pudo consultar la cadena de evidencia", e);
            }
        });
    }

    /**
     * Aprueba una partida existente.
     *
     * @param partidaId identificador de la partida
     * @return partida aprobada
     */
    public Partida aprobar(UUID partidaId) {
        var contador = authService.exigirContador();
        return jdbc.inTransaction(conn -> {
            Partida partida = partidaRepository.buscarPorId(conn, partidaId)
                    .orElseThrow(() -> new DomainException("Partida no encontrada"));
            validarCuentasMovimiento(partida.getDetalles());
            Periodo periodo = periodoAppService.exigirPeriodoAbiertoParaFecha(partida.getFechaContable());
            if (!periodo.getId().equals(partida.getPeriodoId())) {
                throw new DomainException("La fecha no corresponde al período de la partida.");
            }
            partida.aprobar();
            partidaRepository.actualizarEstadoYFolio(conn, partida);
            bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                    contador.getId(), "APROBAR", "PARTIDA", partidaId.toString(),
                    EstadoPartida.BORRADOR.name(), EstadoPartida.APROBADA.name(), null));
            return partida;
        });
    }

    /**
     * Contabilización atómica con idempotencia por evento (H02 / XI.B.3).
     */
    /**
     * Contabiliza una partida de forma atómica e idempotente.
     *
     * @param partidaId identificador de la partida
     * @return partida contabilizada
     */
    public Partida contabilizar(UUID partidaId) {
        var contador = authService.exigirContador();
        return jdbc.inTransaction(conn -> {
            Partida partida = partidaRepository.buscarPorId(conn, partidaId)
                    .orElseThrow(() -> new DomainException("Partida no encontrada"));

            if (partida.getEventoId() != null) {
                Optional<EventoContable> evento = eventoRepository.buscarPorId(conn, partida.getEventoId());
                if (evento.isPresent() && evento.get().getPartidaContabilizadaId() != null) {
                    return partidaRepository.buscarPorId(conn, evento.get().getPartidaContabilizadaId())
                            .orElseThrow(() -> new DomainException(
                                    "Evento ya contabilizado pero partida no encontrada"));
                }
            }

            if (partida.getEstado() == EstadoPartida.BORRADOR) {
                partida.aprobar();
                partidaRepository.actualizarEstadoYFolio(conn, partida);
                partida = partidaRepository.buscarPorId(conn, partidaId).orElseThrow();
            }

            Periodo periodo = periodoAppService.exigirPeriodoAbiertoParaFecha(partida.getFechaContable());
            if (!periodo.admiteCaptura()) {
                throw new DomainException("Período cerrado o en revisión: no se puede contabilizar.");
            }
            validarCuentasMovimiento(partida.getDetalles());
            partida.validarParaAprobacion();

            int anio = partida.getFechaContable().getYear();
            String folio = partidaRepository.asignarFolio(conn, partida.getTipo(), anio);
            partida.marcarContabilizada(folio);
            partidaRepository.actualizarEstadoYFolio(conn, partida);

            if (partida.getEventoId() != null) {
                eventoRepository.vincularPartida(conn, partida.getEventoId(), partida.getId());
            }

            bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                    contador.getId(), "CONTABILIZAR", "PARTIDA", partida.getId().toString(),
                    EstadoPartida.APROBADA.name(), folio, null));
            return partidaRepository.buscarPorId(conn, partidaId).orElseThrow();
        });
    }

    /**
     * Anula una partida existente.
     *
     * @param partidaId identificador de la partida
     * @param motivo motivo de anulación
     * @return partida anulada
     */
    public Partida anular(UUID partidaId, String motivo) {
        var contador = authService.exigirContador();
        return jdbc.inTransaction(conn -> {
            Partida partida = partidaRepository.buscarPorId(conn, partidaId)
                    .orElseThrow(() -> new DomainException("Partida no encontrada"));
            String anterior = partida.getEstado().name();
            partida.anular(motivo);
            partidaRepository.actualizarEstadoYFolio(conn, partida);
            bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                    contador.getId(), "ANULAR", "PARTIDA", partidaId.toString(),
                    anterior, EstadoPartida.ANULADA.name(), motivo));
            return partida;
        });
    }

    /**
     * Genera y contabiliza la reversión de una partida ya contabilizada.
     *
     * @param partidaId identificador de la partida original
     * @param fechaReversion fecha contable de la reversión
     * @param motivo motivo de la reversión
     * @return partida de reversión contabilizada
     */
    public Partida revertir(UUID partidaId, LocalDate fechaReversion, String motivo) {
        var contador = authService.exigirContador();
        Periodo periodo = periodoAppService.exigirPeriodoAbiertoParaFecha(fechaReversion);

        return jdbc.inTransaction(conn -> {
            Partida original = partidaRepository.buscarPorId(conn, partidaId)
                    .orElseThrow(() -> new DomainException("Partida no encontrada"));
            if (original.getEstado() != EstadoPartida.CONTABILIZADA) {
                throw new DomainException("Solo se revierten partidas contabilizadas.");
            }

            String clave = "REV-" + original.getId() + "-" + System.currentTimeMillis();
            EventoContable evento = new EventoContable(
                    UUID.randomUUID(), clave, "Reversión de " + original.getFolio(),
                    null, LocalDateTime.now());
            eventoRepository.insertar(conn, evento);

            Partida reversion = original.crearReversion(
                    fechaReversion, periodo.getId(), evento.getId(), contador.getId());
            reversion.setDocumentoSoporte(motivo);
            partidaRepository.insertar(conn, reversion);

            reversion.aprobar();
            partidaRepository.actualizarEstadoYFolio(conn, reversion);
            String folio = partidaRepository.asignarFolio(conn, TipoPartida.AJ, fechaReversion.getYear());
            reversion.marcarContabilizada(folio);
            partidaRepository.actualizarEstadoYFolio(conn, reversion);
            eventoRepository.vincularPartida(conn, evento.getId(), reversion.getId());

            original.marcarRevertida();
            partidaRepository.actualizarEstadoYFolio(conn, original);

            bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                    contador.getId(), "REVERTIR", "PARTIDA", original.getId().toString(),
                    original.getFolio(), reversion.getFolio(), motivo));
            return reversion;
        });
    }

    /**
     * Lista las partidas en borrador del contador autenticado.
     *
     * @return lista de partidas borrador
     */
    public List<Partida> listarBorradores() {
        authService.exigirContador();
        return jdbc.inTransaction((java.sql.Connection conn) ->
                partidaRepository.listarPorEstado(conn, EstadoPartida.BORRADOR));
    }

    /**
     * Lista las partidas más recientes visibles para el contador autenticado.
     *
     * @param limite cantidad máxima de resultados
     * @return lista de partidas recientes
     */
    public List<Partida> listarRecientes(int limite) {
        return listarRecientes(null, null, null, null, limite);
    }

    /**
     * Lista las partidas recientes aplicando filtros opcionales.
     *
     * @param estado estado a filtrar o {@code null} para todos
     * @param tipo tipo de partida a filtrar o {@code null} para todos
     * @param desde fecha contable mínima o {@code null}
     * @param hasta fecha contable máxima o {@code null}
     * @param limite cantidad máxima de resultados
     * @return lista de partidas recientes filtradas
     */
    public List<Partida> listarRecientes(EstadoPartida estado, TipoPartida tipo,
                                         LocalDate desde, LocalDate hasta, int limite) {
        authService.exigirContador();
        return jdbc.inTransaction((java.sql.Connection conn) ->
                partidaRepository.listarFiltradas(conn, estado, tipo, desde, hasta, limite));
    }

    /**
     * Busca una partida por identificador.
     *
     * @param id identificador de la partida
     * @return partida encontrada o vacío
     */
    public Optional<Partida> buscar(UUID id) {
        authService.exigirContador();
        return jdbc.inTransaction((java.sql.Connection conn) ->
                partidaRepository.buscarPorId(conn, id));
    }

    /**
     * Asegura la existencia de un evento contable para idempotencia.
     *
     * @param conn conexión activa
     * @param claveEvento clave externa del evento
     * @param descripcion descripción del evento
     * @return identificador del evento existente o creado
     */
    private UUID asegurarEvento(java.sql.Connection conn, String claveEvento, String descripcion) {
        String clave = (claveEvento == null || claveEvento.isBlank())
                ? "EVT-" + UUID.randomUUID()
                : claveEvento.trim();

        Optional<EventoContable> existente = eventoRepository.buscarPorClave(conn, clave);
        if (existente.isPresent()) {
            if (existente.get().getPartidaContabilizadaId() != null) {
                throw new DomainException(
                        "El evento '" + clave + "' ya fue contabilizado. No se permite duplicar.");
            }
            return existente.get().getId();
        }
        EventoContable evento = new EventoContable(
                UUID.randomUUID(), clave, descripcion, null, LocalDateTime.now());
        eventoRepository.insertar(conn, evento);
        return evento.getId();
    }

    /**
     * Verifica que todas las cuentas de una partida admitan movimiento.
     *
     * @param detalles líneas a validar
     */
    private void validarCuentasMovimiento(List<PartidaDetalle> detalles) {
        for (PartidaDetalle d : detalles) {
            catalogoService.exigirMovimientoActiva(d.getCodigoCuenta());
        }
    }
}
