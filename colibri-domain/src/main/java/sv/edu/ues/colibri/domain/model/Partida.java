package sv.edu.ues.colibri.domain.model;

import sv.edu.ues.colibri.domain.exception.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Representa un asiento o partida contable compuesta por varias líneas.
 */
public final class Partida {

    private final UUID id;
    private TipoPartida tipo;
    private String folio;
    private LocalDate fechaContable;
    private UUID periodoId;
    private String concepto;
    private EstadoPartida estado;
    private OrigenPartida origen;
    private String documentoSoporte;
    private UUID eventoId;
    private UUID partidaRevertidaId;
    private String motivoAnulacion;
    private UUID creadoPor;
    private LocalDateTime creadoEn;
    private LocalDateTime actualizadoEn;
    private final List<PartidaDetalle> detalles = new ArrayList<>();

    /**
     * Crea una partida con su estado, fechas y metadatos de auditoría.
     *
     * @param id identificador único
     * @param tipo tipo de partida
     * @param folio folio asignado o {@code null}
     * @param fechaContable fecha contable
     * @param periodoId identificador del período
     * @param concepto concepto de la partida
     * @param estado estado inicial
     * @param origen origen de creación
     * @param documentoSoporte documento de respaldo opcional
     * @param eventoId identificador del evento asociado
     * @param partidaRevertidaId partida origen si se trata de una reversión
     * @param motivoAnulacion motivo de anulación opcional
     * @param creadoPor usuario creador
     * @param creadoEn instante de creación
     * @param actualizadoEn instante de última actualización
     */
    public Partida(UUID id, TipoPartida tipo, String folio, LocalDate fechaContable, UUID periodoId,
                   String concepto, EstadoPartida estado, OrigenPartida origen, String documentoSoporte,
                   UUID eventoId, UUID partidaRevertidaId, String motivoAnulacion,
                   UUID creadoPor, LocalDateTime creadoEn, LocalDateTime actualizadoEn) {
        this.id = Objects.requireNonNull(id);
        this.tipo = Objects.requireNonNull(tipo);
        this.folio = folio;
        this.fechaContable = Objects.requireNonNull(fechaContable);
        this.periodoId = Objects.requireNonNull(periodoId);
        this.concepto = Objects.requireNonNull(concepto);
        this.estado = Objects.requireNonNull(estado);
        this.origen = Objects.requireNonNull(origen);
        this.documentoSoporte = documentoSoporte;
        this.eventoId = eventoId;
        this.partidaRevertidaId = partidaRevertidaId;
        this.motivoAnulacion = motivoAnulacion;
        this.creadoPor = creadoPor;
        this.creadoEn = creadoEn;
        this.actualizadoEn = actualizadoEn;
    }

    /**
     * Crea una partida en estado borrador con fechas de auditoría actuales.
     *
     * @param tipo tipo de partida
     * @param fecha fecha contable
     * @param periodoId identificador del período
     * @param concepto concepto de la partida
     * @param documentoSoporte documento de respaldo opcional
     * @param eventoId identificador del evento asociado
     * @param contadorId usuario creador
     * @return partida nueva en estado borrador
     */
    public static Partida borrador(TipoPartida tipo, LocalDate fecha, UUID periodoId, String concepto,
                                   String documentoSoporte, UUID eventoId, UUID contadorId) {
        return borrador(tipo, fecha, periodoId, concepto, documentoSoporte, eventoId, contadorId,
                OrigenPartida.MANUAL);
    }

    public static Partida borrador(TipoPartida tipo, LocalDate fecha, UUID periodoId, String concepto,
                                   String documentoSoporte, UUID eventoId, UUID contadorId,
                                   OrigenPartida origen) {
        LocalDateTime ahora = LocalDateTime.now();
        return new Partida(UUID.randomUUID(), tipo, null, fecha, periodoId, concepto,
                EstadoPartida.BORRADOR, origen, documentoSoporte, eventoId, null, null,
                contadorId, ahora, ahora);
    }

    /**
     * Calcula el total del debe.
     *
     * @return suma de los importes al debe
     */
    public BigDecimal totalDebe() {
        return detalles.stream()
                .map(PartidaDetalle::getDebe)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula el total del haber.
     *
     * @return suma de los importes al haber
     */
    public BigDecimal totalHaber() {
        return detalles.stream()
                .map(PartidaDetalle::getHaber)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula la diferencia absoluta entre debe y haber.
     *
     * @return diferencia monetaria
     */
    public BigDecimal diferencia() {
        return totalDebe().subtract(totalHaber()).abs();
    }

    /**
     * Indica si la partida está equilibrada y con saldo positivo.
     *
     * @return {@code true} si debe y haber coinciden
     */
    public boolean estaEquilibrada() {
        return totalDebe().compareTo(totalHaber()) == 0
                && totalDebe().compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Sustituye todas las líneas de la partida.
     *
     * @param nuevos nuevas líneas a registrar
     */
    public void reemplazarDetalles(List<PartidaDetalle> nuevos) {
        if (estado == EstadoPartida.CONTABILIZADA || estado == EstadoPartida.REVERTIDA
                || estado == EstadoPartida.ANULADA) {
            throw new DomainException("No se pueden modificar los detalles de una partida " + estado.name());
        }
        detalles.clear();
        int linea = 1;
        for (PartidaDetalle d : nuevos) {
            d.setPartidaId(id);
            d.setLinea(linea++);
            detalles.add(d);
        }
        if (estado == EstadoPartida.APROBADA) {
            estado = EstadoPartida.BORRADOR;
        }
    }

    /**
     * Aprueba la partida después de validar sus detalles.
     */
    public void aprobar() {
        exigirEstado(EstadoPartida.BORRADOR);
        validarParaAprobacion();
        estado = EstadoPartida.APROBADA;
    }

    /**
     * Marca la partida como contabilizada y asigna el folio definitivo.
     *
     * @param folioAsignado folio contable definitivo
     */
    public void marcarContabilizada(String folioAsignado) {
        exigirEstado(EstadoPartida.APROBADA);
        validarParaAprobacion();
        this.folio = Objects.requireNonNull(folioAsignado);
        this.estado = EstadoPartida.CONTABILIZADA;
    }

    /**
     * Anula la partida cuando aún no ha sido contabilizada.
     *
     * @param motivo motivo de anulación
     */
    public void anular(String motivo) {
        if (estado != EstadoPartida.BORRADOR && estado != EstadoPartida.APROBADA) {
            throw new DomainException("Solo se pueden anular partidas en borrador o aprobadas.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new DomainException("La anulación requiere un motivo.");
        }
        this.motivoAnulacion = motivo.trim();
        this.estado = EstadoPartida.ANULADA;
    }

    /**
     * Marca la partida como revertida.
     */
    public void marcarRevertida() {
        exigirEstado(EstadoPartida.CONTABILIZADA);
        this.estado = EstadoPartida.REVERTIDA;
    }

    /**
     * Valida que la partida pueda aprobarse o contabilizarse.
     */
    public void validarParaAprobacion() {
        if (detalles.size() < 2) {
            throw new DomainException("Se requieren al menos dos líneas para aprobar o contabilizar.");
        }
        for (PartidaDetalle d : detalles) {
            d.validarImportes();
        }
        if (!estaEquilibrada()) {
            throw new DomainException(
                    "Debe y Haber deben ser iguales a centavos. Diferencia: " + diferencia());
        }
        if (concepto == null || concepto.isBlank()) {
            throw new DomainException("El concepto es obligatorio.");
        }
    }

    /**
     * Genera una partida de reversión a partir de esta partida.
     *
     * @param fecha fecha contable de la reversión
     * @param periodoId período destino
     * @param nuevoEventoId evento asociado a la reversión
     * @param contadorId usuario creador
     * @return nueva partida de reversión
     */
    public Partida crearReversion(LocalDate fecha, UUID periodoId, UUID nuevoEventoId, UUID contadorId) {
        if (estado != EstadoPartida.CONTABILIZADA) {
            throw new DomainException("Solo se puede revertir una partida contabilizada.");
        }
        if (partidaRevertidaId != null) {
            throw new DomainException("Una partida de reversión no puede revertirse; "
                    + "registre una corrección nueva en un período abierto.");
        }
        Partida reversion = borrador(TipoPartida.AJ, fecha, periodoId,
                "Reversión de " + (folio != null ? folio : id),
                documentoSoporte, nuevoEventoId, contadorId);
        reversion.partidaRevertidaId = this.id;
        List<PartidaDetalle> invertidos = new ArrayList<>();
        for (PartidaDetalle d : detalles) {
            PartidaDetalle inv = PartidaDetalle.nueva(d.getCodigoCuenta(), d.getHaber(), d.getDebe());
            inv.setAuxiliar(d.getAuxiliar());
            inv.setCentroCosto(d.getCentroCosto());
            invertidos.add(inv);
        }
        reversion.reemplazarDetalles(invertidos);
        return reversion;
    }

    /**
     * Verifica que la partida se encuentre en el estado esperado.
     *
     * @param esperado estado requerido
     */
    private void exigirEstado(EstadoPartida esperado) {
        if (estado != esperado) {
            throw new DomainException("La partida debe estar en estado " + esperado + " (actual: " + estado + ").");
        }
    }

    /**
     * Obtiene el identificador de la partida.
     *
     * @return UUID de la partida
     */
    public UUID getId() {
        return id;
    }

    /**
     * Obtiene el tipo de partida.
     *
     * @return tipo contable
     */
    public TipoPartida getTipo() {
        return tipo;
    }

    /**
     * Actualiza el tipo de partida.
     *
     * @param tipo nuevo tipo
     */
    public void setTipo(TipoPartida tipo) {
        this.tipo = Objects.requireNonNull(tipo);
    }

    /**
     * Obtiene el folio asignado.
     *
     * @return folio o {@code null}
     */
    public String getFolio() {
        return folio;
    }

    /**
     * Obtiene la fecha contable.
     *
     * @return fecha contable
     */
    public LocalDate getFechaContable() {
        return fechaContable;
    }

    /**
     * Actualiza la fecha contable.
     *
     * @param fechaContable nueva fecha
     */
    public void setFechaContable(LocalDate fechaContable) {
        this.fechaContable = Objects.requireNonNull(fechaContable);
    }

    /**
     * Obtiene el período asociado.
     *
     * @return UUID del período
     */
    public UUID getPeriodoId() {
        return periodoId;
    }

    /**
     * Actualiza el período asociado.
     *
     * @param periodoId nuevo período
     */
    public void setPeriodoId(UUID periodoId) {
        this.periodoId = Objects.requireNonNull(periodoId);
    }

    /**
     * Obtiene el concepto de la partida.
     *
     * @return concepto
     */
    public String getConcepto() {
        return concepto;
    }

    /**
     * Actualiza el concepto de la partida.
     *
     * @param concepto nuevo concepto
     */
    public void setConcepto(String concepto) {
        this.concepto = Objects.requireNonNull(concepto);
    }

    /**
     * Obtiene el estado actual de la partida.
     *
     * @return estado de la partida
     */
    public EstadoPartida getEstado() {
        return estado;
    }

    /**
     * Obtiene el origen de la partida.
     *
     * @return origen de generación
     */
    public OrigenPartida getOrigen() {
        return origen;
    }

    /**
     * Obtiene el documento de soporte.
     *
     * @return documento de soporte o {@code null}
     */
    public String getDocumentoSoporte() {
        return documentoSoporte;
    }

    /**
     * Actualiza el documento de soporte.
     *
     * @param documentoSoporte nuevo documento de soporte
     */
    public void setDocumentoSoporte(String documentoSoporte) {
        this.documentoSoporte = documentoSoporte;
    }

    /**
     * Obtiene el identificador del evento asociado.
     *
     * @return UUID del evento o {@code null}
     */
    public UUID getEventoId() {
        return eventoId;
    }

    /**
     * Actualiza el evento asociado.
     *
     * @param eventoId nuevo identificador de evento
     */
    public void setEventoId(UUID eventoId) {
        this.eventoId = eventoId;
    }

    /**
     * Obtiene la partida original que fue revertida.
     *
     * @return UUID de la partida revertida o {@code null}
     */
    public UUID getPartidaRevertidaId() {
        return partidaRevertidaId;
    }

    /**
     * Obtiene el motivo de anulación.
     *
     * @return motivo o {@code null}
     */
    public String getMotivoAnulacion() {
        return motivoAnulacion;
    }

    /**
     * Obtiene el usuario creador.
     *
     * @return UUID del creador o {@code null}
     */
    public UUID getCreadoPor() {
        return creadoPor;
    }

    /**
     * Obtiene el instante de creación.
     *
     * @return fecha y hora de creación
     */
    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    /**
     * Obtiene el instante de última actualización.
     *
     * @return fecha y hora de actualización
     */
    public LocalDateTime getActualizadoEn() {
        return actualizadoEn;
    }

    /**
     * Actualiza el instante de última modificación.
     *
     * @param actualizadoEn nuevo instante
     */
    public void setActualizadoEn(LocalDateTime actualizadoEn) {
        this.actualizadoEn = actualizadoEn;
    }

    /**
     * Obtiene las líneas de la partida como lista no modificable.
     *
     * @return detalles de la partida
     */
    public List<PartidaDetalle> getDetalles() {
        return Collections.unmodifiableList(detalles);
    }

    /**
     * Reemplaza el contenido interno de detalles sin validar reglas de negocio.
     *
     * @param lista detalles previamente construidos
     */
    public void cargarDetalles(List<PartidaDetalle> lista) {
        detalles.clear();
        detalles.addAll(lista);
    }
}
