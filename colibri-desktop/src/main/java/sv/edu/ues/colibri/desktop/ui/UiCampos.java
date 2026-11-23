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

    private UiCampos() {
    }

    /**
     * Parsea un importe obligatorio. Tolera espacios, símbolo {@code $} y
     * separadores de miles con coma ({@code 1,000.00}).
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
                    "El importe no es numérico: «" + texto.trim() + "». Use formato 1250.75.");
        }
        return valor;
    }

    /**
     * Parsea un importe opcional; devuelve {@code null} si el campo está vacío
     * o no es numérico (para que el llamador decida el mensaje).
     *
     * @param texto contenido del campo
     * @return importe en escala 2 o {@code null}
     */
    public static BigDecimal importeONull(String texto) {
        BigDecimal valor = decimalONull(texto);
        return valor == null ? null : valor.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Parsea un decimal sin fijar escala (tasas, porcentajes, factores).
     * Tolera espacios, símbolo {@code $} y comas de miles.
     *
     * @param texto contenido del campo
     * @return decimal o {@code null} si vacío/no numérico
     */
    public static BigDecimal decimalONull(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String limpio = texto.trim().replace(",", "").replace("$", "");
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
                    "El importe no es numérico: «" + texto.trim() + "». Use formato 1250.75.");
        }
        return valor;
    }

    /**
     * Parsea un entero opcional (unidades, meses, porcentaje entero).
     *
     * @param texto contenido del campo
     * @return entero o {@code null} si vacío/no numérico
     */
    public static Integer enteroONull(String texto) {
        BigDecimal v = importeONull(texto);
        return v == null ? null : v.intValue();
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
