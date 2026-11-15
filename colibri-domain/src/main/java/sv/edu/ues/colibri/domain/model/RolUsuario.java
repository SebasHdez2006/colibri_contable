package sv.edu.ues.colibri.domain.model;

/**
 * Roles funcionales disponibles para un usuario del sistema.
 */
public enum RolUsuario {
    /** Usuario operativo del área contable. */
    CONTADOR,
    /** Usuario con privilegios técnicos y administrativos. */
    SOPORTE_TECNICO;

    /**
     * Obtiene la etiqueta legible para interfaces y reportes.
     *
     * @return nombre descriptivo del rol
     */
    public String etiqueta() {
        return this == SOPORTE_TECNICO ? "Soporte técnico" : "Contador";
    }
}
