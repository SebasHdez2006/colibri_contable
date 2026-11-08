package sv.edu.ues.colibri.domain.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Fórmulas del método analítico de mercancías y del estado de resultados. */
public final class ResultadosCalculator {

    private static final int ESCALA_MONETARIA = 2;
    private static final RoundingMode REDONDEO = RoundingMode.HALF_UP;

    /** Cédula de mercancías disponibles, costo de ventas y utilidad bruta. */
    public record ResultadoMercantil(BigDecimal ventasNetas, BigDecimal comprasTotales,
                                    BigDecimal comprasNetas, BigDecimal inventarioInicial,
                                    BigDecimal mercanciasDisponibles, BigDecimal inventarioFinal,
                                    BigDecimal costoVentas, BigDecimal utilidadBruta) {
    }

    private ResultadosCalculator() {
    }

    /** Ventas netas = ventas − devoluciones sobre venta − descuentos sobre venta. */
    public static BigDecimal ventasNetas(BigDecimal ventas, BigDecimal devoluciones,
                                         BigDecimal descuentos) {
        BigDecimal netas = importeNoNegativo(ventas, "ventas")
                .subtract(importeNoNegativo(devoluciones, "devoluciones sobre venta"))
                .subtract(importeNoNegativo(descuentos, "descuentos sobre venta"));
        if (netas.signum() < 0) {
            throw new IllegalArgumentException("Las devoluciones y descuentos no pueden exceder las ventas.");
        }
        return dinero(netas);
    }

    /** Compras totales = compras + gastos sobre compras. */
    public static BigDecimal comprasTotales(BigDecimal compras, BigDecimal gastosSobreCompras) {
        return dinero(importeNoNegativo(compras, "compras")
                .add(importeNoNegativo(gastosSobreCompras, "gastos sobre compras")));
    }

    /** Compras netas = compras totales − devoluciones sobre compra − descuentos sobre compra. */
    public static BigDecimal comprasNetas(BigDecimal comprasTotales, BigDecimal devoluciones,
                                          BigDecimal descuentos) {
        BigDecimal netas = importeNoNegativo(comprasTotales, "compras totales")
                .subtract(importeNoNegativo(devoluciones, "devoluciones sobre compra"))
                .subtract(importeNoNegativo(descuentos, "descuentos sobre compra"));
        if (netas.signum() < 0) {
            throw new IllegalArgumentException("Las devoluciones y descuentos no pueden exceder las compras totales.");
        }
        return dinero(netas);
    }

    /** Mercancías disponibles = inventario inicial + compras netas. */
    public static BigDecimal mercanciasDisponibles(BigDecimal inventarioInicial,
                                                   BigDecimal comprasNetas) {
        return dinero(importeNoNegativo(inventarioInicial, "inventario inicial")
                .add(importeNoNegativo(comprasNetas, "compras netas")));
    }

    /** Costo de ventas = mercancías disponibles − inventario final. */
    public static BigDecimal costoVentas(BigDecimal mercanciasDisponibles, BigDecimal inventarioFinal) {
        BigDecimal disponibles = importeNoNegativo(mercanciasDisponibles, "mercancías disponibles");
        BigDecimal finalInventario = importeNoNegativo(inventarioFinal, "inventario final");
        if (finalInventario.compareTo(disponibles) > 0) {
            throw new IllegalArgumentException("El inventario final no puede exceder las mercancías disponibles.");
        }
        return dinero(disponibles.subtract(finalInventario));
    }

    /** Utilidad o pérdida bruta = ventas netas − costo de ventas. */
    public static BigDecimal utilidadBruta(BigDecimal ventasNetas, BigDecimal costoVentas) {
        return dinero(importeNoNegativo(ventasNetas, "ventas netas")
                .subtract(importeNoNegativo(costoVentas, "costo de ventas")));
    }

    /** Utilidad de operación = utilidad bruta − gastos de operación. */
    public static BigDecimal utilidadOperacion(BigDecimal utilidadBruta, BigDecimal gastosOperacion) {
        return dinero(resultado(utilidadBruta, "utilidad bruta")
                .subtract(importeNoNegativo(gastosOperacion, "gastos de operación")));
    }

    /** Resultado antes de impuestos = operación + ingresos financieros − gastos financieros + otros resultados. */
    public static BigDecimal utilidadAntesImpuestos(BigDecimal utilidadOperacion,
                                                    BigDecimal ingresosFinancieros,
                                                    BigDecimal gastosFinancieros,
                                                    BigDecimal otrosResultados) {
        return dinero(resultado(utilidadOperacion, "utilidad de operación")
                .add(importeNoNegativo(ingresosFinancieros, "ingresos financieros"))
                .subtract(importeNoNegativo(gastosFinancieros, "gastos financieros"))
                .add(resultado(otrosResultados, "otros resultados")));
    }

    /** Utilidad neta = utilidad antes de impuestos − impuestos directos. */
    public static BigDecimal utilidadNeta(BigDecimal utilidadAntesImpuestos, BigDecimal impuestosDirectos) {
        return dinero(resultado(utilidadAntesImpuestos, "utilidad antes de impuestos")
                .subtract(importeNoNegativo(impuestosDirectos, "impuestos directos")));
    }

    /** Construye la cédula completa del método analítico. */
    public static ResultadoMercantil calcularResultadoMercantil(BigDecimal ventas,
                                                                 BigDecimal devolucionesVenta,
                                                                 BigDecimal descuentosVenta,
                                                                 BigDecimal compras,
                                                                 BigDecimal gastosSobreCompras,
                                                                 BigDecimal devolucionesCompra,
                                                                 BigDecimal descuentosCompra,
                                                                 BigDecimal inventarioInicial,
                                                                 BigDecimal inventarioFinal) {
        BigDecimal ventasNetas = ventasNetas(ventas, devolucionesVenta, descuentosVenta);
        BigDecimal comprasTotales = comprasTotales(compras, gastosSobreCompras);
        BigDecimal comprasNetas = comprasNetas(comprasTotales, devolucionesCompra, descuentosCompra);
        BigDecimal inicial = importeNoNegativo(inventarioInicial, "inventario inicial");
        BigDecimal disponibles = mercanciasDisponibles(inicial, comprasNetas);
        BigDecimal finalInventario = importeNoNegativo(inventarioFinal, "inventario final");
        BigDecimal costoVentas = costoVentas(disponibles, finalInventario);
        return new ResultadoMercantil(ventasNetas, comprasTotales, comprasNetas, inicial,
                disponibles, finalInventario, costoVentas, utilidadBruta(ventasNetas, costoVentas));
    }

    private static BigDecimal importeNoNegativo(BigDecimal valor, String etiqueta) {
        if (valor == null || valor.signum() < 0) {
            throw new IllegalArgumentException("El importe de " + etiqueta + " no puede ser negativo.");
        }
        return dinero(valor);
    }

    private static BigDecimal resultado(BigDecimal valor, String etiqueta) {
        if (valor == null) {
            throw new IllegalArgumentException("El importe de " + etiqueta + " es obligatorio.");
        }
        return dinero(valor);
    }

    private static BigDecimal dinero(BigDecimal valor) {
        return valor.setScale(ESCALA_MONETARIA, REDONDEO);
    }
}
