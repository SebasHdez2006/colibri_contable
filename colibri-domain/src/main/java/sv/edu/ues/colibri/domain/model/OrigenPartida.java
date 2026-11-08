package sv.edu.ues.colibri.domain.model;

/**
 * Origen de generación de una partida contable.
 */
public enum OrigenPartida {
    /** Partida capturada manualmente por un usuario. */
    MANUAL,
    /** Partida generada por un proceso automático. */
    AUTOMATICO,
    VENTA,
    PLANILLA,
    CIF,
    CIERRE,
    SEED
}
