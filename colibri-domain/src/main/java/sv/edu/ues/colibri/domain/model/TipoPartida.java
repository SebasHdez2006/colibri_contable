package sv.edu.ues.colibri.domain.model;

/**
 * Tipos funcionales de partida contable.
 */
public enum TipoPartida {
    /** Partida de ingresos. */
    IN("Partida de ingreso"),
    /** Partida de egresos. */
    EG("Partida de egreso"),
    /** Partida de diario. */
    DI("Partida de diario"),
    /** Ajuste contable. */
    AJ("Ajuste contable"),
    /** Apertura de ejercicio. */
    AP("Apertura de ejercicio"),
    /** Cierre de ejercicio. */
    CI("Cierre de ejercicio");

    private final String etiqueta;

    /**
     * Crea el tipo con la etiqueta descriptiva usada en UI y reportes.
     *
     * @param etiqueta descripción legible del tipo
     */
    TipoPartida(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /**
     * Obtiene la etiqueta descriptiva del tipo.
     *
     * @return etiqueta legible
     */
    public String getEtiqueta() {
        return etiqueta;
    }

    /**
     * Obtiene el prefijo de folio asociado al tipo.
     *
     * @return prefijo técnico del folio
     */
    public String prefijoFolio() {
        return name();
    }
}
