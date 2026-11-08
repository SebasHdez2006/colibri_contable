package sv.edu.ues.colibri.domain.exception;

/**
 * Excepción de dominio para reglas de negocio inválidas o estados no permitidos.
 */
public class DomainException extends RuntimeException {

    /**
     * Crea una excepción de dominio con mensaje descriptivo.
     *
     * @param message descripción del problema de negocio
     */
    public DomainException(String message) {
        super(message);
    }

    /**
     * Crea una excepción de dominio con mensaje y causa raíz.
     *
     * @param message descripción del problema de negocio
     * @param cause causa técnica asociada
     */
    public DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
