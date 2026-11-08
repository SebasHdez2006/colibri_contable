package sv.edu.ues.colibri.domain.model;

/**
 * Naturaleza contable de una cuenta.
 */
public enum NaturalezaCuenta {
    /** La cuenta normalmente presenta saldo deudor. */
    DEUDORA,
    /** La cuenta normalmente presenta saldo acreedor. */
    ACREEDORA,
    /** La cuenta puede operar con saldo deudor o acreedor. */
    AMBAS
}
