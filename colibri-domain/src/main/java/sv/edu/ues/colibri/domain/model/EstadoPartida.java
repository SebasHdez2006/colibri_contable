package sv.edu.ues.colibri.domain.model;

/**
 * Estados posibles de una partida contable.
 */
public enum EstadoPartida {
    /** Partida creada pero aún editable. */
    BORRADOR,
    /** Partida revisada y lista para contabilización. */
    APROBADA,
    /** Partida ya registrada en contabilidad. */
    CONTABILIZADA,
    /** Partida anulada por corrección o rechazo. */
    ANULADA,
    /** Partida revertida mediante asiento compensatorio. */
    REVERTIDA
}
