package sv.edu.ues.colibri.domain.model;

/**
 * Estado operativo de una cuenta del catálogo.
 */
public enum EstadoCuenta {
    /** La cuenta puede utilizarse normalmente. */
    ACTIVA,
    /** La cuenta fue retirada de uso por administración. */
    DESACTIVADA,
    /** La cuenta está restringida por una regla de negocio. */
    BLOQUEADA
}
