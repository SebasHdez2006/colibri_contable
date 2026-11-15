package sv.edu.ues.colibri.persistence.jdbc;

/**
 * Excepción no verificada para fallos de infraestructura de persistencia.
 */
public class PersistenceException extends RuntimeException {

    /**
     * Crea una excepción de persistencia con mensaje descriptivo.
     *
     * @param message descripción del error
     */
    public PersistenceException(String message) {
        super(message);
    }

    /**
     * Crea una excepción de persistencia con causa raíz.
     *
     * @param message descripción del error
     * @param cause excepción original
     */
    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
