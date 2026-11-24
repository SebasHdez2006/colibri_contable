package sv.edu.ues.colibri.desktop.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Formateo monetario único de la interfaz de escritorio.
 * <p>
 * Toda cifra financiera visible (tablas, reportes, comprobantes y mensajes)
 * debe pasar por {@link #dinero(BigDecimal)} para que el usuario lea siempre
 * el mismo patrón: símbolo de dólar, coma para los miles y punto para los
 * decimales — por ejemplo {@code $ 640,023.92}.
 */
public final class UiFormatos {

    /**
     * Patrón contable fijo en locale {@link Locale#US}: la coma agrupa miles
     * y el punto separa decimales sin depender de la configuración regional
     * del equipo. Se instancia por hilo porque las exportaciones corren fuera
     * del hilo de interfaz y {@link DecimalFormat} no es thread-safe.
     */
    private static final ThreadLocal<DecimalFormat> MONEDA = ThreadLocal.withInitial(() -> {
        DecimalFormat formato = new DecimalFormat("$ #,##0.00",
                DecimalFormatSymbols.getInstance(Locale.US));
        formato.setRoundingMode(RoundingMode.HALF_UP);
        return formato;
    });

    private UiFormatos() {
    }

    /**
     * Formatea un importe como moneda visible: {@code $ 640,023.92}. Los
     * valores negativos se muestran entre paréntesis — {@code ($ 1,234.56)} —
     * siguiendo la convención contable usada en los estados financieros.
     *
     * @param monto importe a mostrar; {@code null} se representa como {@code —}
     * @return texto monetario con símbolo, miles y dos decimales
     */
    public static String dinero(BigDecimal monto) {
        if (monto == null) {
            return "—";
        }
        String texto = MONEDA.get().format(monto.abs());
        return monto.signum() < 0 ? "(" + texto + ")" : texto;
    }
}
