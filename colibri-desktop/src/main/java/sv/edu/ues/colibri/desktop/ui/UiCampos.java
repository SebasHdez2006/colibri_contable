package sv.edu.ues.colibri.desktop.ui;

import sv.edu.ues.colibri.domain.exception.DomainException;

import java.math.BigDecimal;

/**
 * Utilidades de captura para campos de texto de la interfaz.
 * Centraliza el parseo de importes para que un valor no numérico produzca
 * un mensaje contable claro en español en lugar de una
 * {@link NumberFormatException} técnica.
 */
public final class UiCampos {

    /** Decimal genérico: hasta 6 decimales (tasas, factores, porcentajes). */
    private static final java.util.regex.Pattern DECIMAL_PERMITIDO =
            java.util.regex.Pattern.compile("-?\\d{1,18}(\\.\\d{1,6})?");
    /** Importe monetario: máximo 2 decimales, 16 dígitos enteros (NUMERIC(18,2)). */
    private static final java.util.regex.Pattern IMPORTE_PERMITIDO =
            java.util.regex.Pattern.compile("-?\\d{1,16}(\\.\\d{1,2})?");
    /** Entero: hasta 9 dígitos (caben en un int con signo). */
    private static final java.util.regex.Pattern ENTERO_PERMITIDO =
            java.util.regex.Pattern.compile("-?\\d{1,9}");

    private UiCampos() {
    }

    /**
     * Parsea un importe obligatorio con formato estricto: solo dígitos y un
     * punto decimal con máximo dos cifras. Se rechazan símbolos ({@code $},
     * {@code %}), comas, letras, notación científica y signos distintos del
     * menos inicial.
     *
     * @param texto contenido del campo
     * @return importe en escala 2
     * @throws DomainException si está vacío o no es numérico
     */
    public static BigDecimal importe(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new DomainException("El importe es obligatorio.");
        }
        BigDecimal valor = importeONull(texto);
        if (valor == null) {
            throw new DomainException(
                    "El importe no es válido: «" + texto.trim()
                            + "». Use solo dígitos y punto decimal, formato 1250.75.");
        }
        return valor;
    }

    /**
     * Parsea un importe opcional con formato estricto; devuelve {@code null}
     * si el campo está vacío o contiene caracteres no admitidos.
     *
     * @param texto contenido del campo
     * @return importe en escala 2 o {@code null}
     */
    public static BigDecimal importeONull(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.trim();
        if (limpio.isEmpty() || !IMPORTE_PERMITIDO.matcher(limpio).matches()) {
            return null;
        }
        try {
            return new BigDecimal(limpio).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parsea un decimal sin fijar escala (tasas, porcentajes, factores) con
     * formato estricto: solo dígitos, signo menos inicial y punto decimal.
     *
     * @param texto contenido del campo
     * @return decimal o {@code null} si vacío/no numérico
     */
    public static BigDecimal decimalONull(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.trim();
        if (limpio.isEmpty() || !DECIMAL_PERMITIDO.matcher(limpio).matches()) {
            return null;
        }
        try {
            return new BigDecimal(limpio);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parsea un decimal obligatorio sin fijar escala (tasas, porcentajes).
     *
     * @param texto contenido del campo
     * @param etiqueta nombre del campo para el mensaje
     * @return decimal
     * @throws DomainException si está vacío o no es numérico
     */
    public static BigDecimal decimal(String texto, String etiqueta) {
        BigDecimal valor = decimalONull(texto);
        if (valor == null) {
            throw new DomainException(
                    "El valor de " + etiqueta + " debe ser numérico (ej. 0.13).");
        }
        return valor;
    }

    /**
     * Parsea un importe que admite cero: campo vacío devuelve {@code 0.00}
     * pero un texto no numérico produce error claro.
     *
     * @param texto contenido del campo
     * @return importe en escala 2 (nunca {@code null})
     * @throws DomainException si el texto no es numérico
     */
    public static BigDecimal importeOCero(String texto) {
        if (texto == null || texto.isBlank()) {
            return BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP);
        }
        BigDecimal valor = importeONull(texto);
        if (valor == null) {
            throw new DomainException(
                    "El importe no es válido: «" + texto.trim()
                            + "». Use solo dígitos y punto decimal, formato 1250.75.");
        }
        return valor;
    }

    /**
     * Parsea un entero opcional (unidades, meses, año de ejercicio). Exige
     * dígitos puros: un decimal como {@code 2026.9} se rechaza en lugar de
     * truncarse en silencio.
     *
     * @param texto contenido del campo
     * @return entero o {@code null} si vacío/no entero
     */
    public static Integer enteroONull(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.trim();
        if (limpio.isEmpty() || !ENTERO_PERMITIDO.matcher(limpio).matches()) {
            return null;
        }
        try {
            return Integer.valueOf(limpio);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parsea un entero obligatorio con mensaje claro.
     *
     * @param texto contenido del campo
     * @param etiqueta nombre del campo para el mensaje
     * @return entero
     * @throws DomainException si está vacío o no es numérico
     */
    public static int entero(String texto, String etiqueta) {
        Integer v = enteroONull(texto);
        if (v == null) {
            throw new DomainException("El " + etiqueta + " debe ser un número entero.");
        }
        return v;
    }
}
