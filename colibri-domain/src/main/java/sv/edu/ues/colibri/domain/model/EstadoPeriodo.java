package sv.edu.ues.colibri.domain.model;

/**
 * Estados de control de un período contable.
 */
public enum EstadoPeriodo {
    /** El período admite operaciones normales. */
    ABIERTO,
    /** El período está en verificación previa al cierre. */
    EN_REVISION,
    /** El período fue cerrado y ya no debe recibir operaciones. */
    CERRADO
}
