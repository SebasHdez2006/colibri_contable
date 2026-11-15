package sv.edu.ues.colibri.domain.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Fórmulas puras para ajustes de cierre y reconocimiento por devengo. */
public final class AjustesCalculator {

    private static final int ESCALA_MONETARIA = 2;
    private static final int ESCALA_CALCULO = 8;
    private static final RoundingMode REDONDEO = RoundingMode.HALF_UP;

    /** Resultado seguro de un cargo de depreciación sin exceder la base depreciable. */
    public record DepreciacionSegura(BigDecimal costo, BigDecimal valorResidual,
                                     BigDecimal baseDepreciable, BigDecimal depreciacionAcumuladaInicial,
                                     BigDecimal cuotaTeoricaPorMes, BigDecimal cargoPeriodo,
                                     BigDecimal depreciacionAcumuladaFinal,
                                     boolean totalmenteDepreciado) {
    }

    private AjustesCalculator() {
    }

    /** Interés simple acumulado = capital × tasa anual × meses / 12. */
    public static BigDecimal interesAcumulado(BigDecimal capital, BigDecimal tasaAnual, int meses) {
        BigDecimal principal = importeNoNegativo(capital, "capital");
        BigDecimal tasa = tasaNoNegativa(tasaAnual, "tasa anual");
        if (meses < 0) {
            throw new IllegalArgumentException("Los meses acumulados no pueden ser negativos.");
        }
        return dinero(principal.multiply(tasa).multiply(BigDecimal.valueOf(meses))
                .divide(BigDecimal.valueOf(12), ESCALA_CALCULO, REDONDEO));
    }

    /** Gasto diferido = importe pagado / meses cubiertos × meses consumidos. */
    public static BigDecimal gastoDiferidoConsumido(BigDecimal importePagado, int mesesCubiertos,
                                                    int mesesConsumidos) {
        BigDecimal importe = importeNoNegativo(importePagado, "importe pagado");
        validarMeses(mesesCubiertos, "meses cubiertos", false);
        validarMeses(mesesConsumidos, "meses consumidos", true);
        if (mesesConsumidos > mesesCubiertos) {
            throw new IllegalArgumentException("Los meses consumidos no pueden exceder los meses cubiertos.");
        }
        return dinero(importe.multiply(BigDecimal.valueOf(mesesConsumidos))
                .divide(BigDecimal.valueOf(mesesCubiertos), ESCALA_CALCULO, REDONDEO));
    }

    /** Depreciación anual de línea recta = (costo − residual) / vida útil en años. */
    public static BigDecimal depreciacionAnual(BigDecimal costo, BigDecimal valorResidual,
                                               int vidaUtilAnios) {
        BigDecimal base = baseDepreciable(costo, valorResidual);
        validarMeses(vidaUtilAnios, "vida útil en años", false);
        return dinero(base.divide(BigDecimal.valueOf(vidaUtilAnios), ESCALA_CALCULO, REDONDEO));
    }

    /** Depreciación periódica = depreciación anual / períodos del año. */
    public static BigDecimal depreciacionPeriodica(BigDecimal depreciacionAnual,
                                                   int periodosPorAnio) {
        BigDecimal anual = importeNoNegativo(depreciacionAnual, "depreciación anual");
        validarMeses(periodosPorAnio, "períodos por año", false);
        return dinero(anual.divide(BigDecimal.valueOf(periodosPorAnio), ESCALA_CALCULO, REDONDEO));
    }

    /**
     * Calcula un cargo de línea recta seguro. El cargo se limita al saldo pendiente
     * aun si el llamador solicita más períodos que la vida útil restante.
     */
    public static DepreciacionSegura depreciacionSegura(BigDecimal costo, BigDecimal valorResidual,
                                                        int vidaUtilMeses,
                                                        BigDecimal depreciacionAcumulada,
                                                        int mesesACargar) {
        BigDecimal costoActivo = importeNoNegativo(costo, "costo del activo");
        BigDecimal residual = importeNoNegativo(valorResidual, "valor residual");
        BigDecimal base = baseDepreciable(costoActivo, residual);
        validarMeses(vidaUtilMeses, "vida útil en meses", false);
        validarMeses(mesesACargar, "meses a cargar", true);
        BigDecimal acumulada = importeNoNegativo(depreciacionAcumulada,
                "depreciación acumulada");
        if (acumulada.compareTo(base) > 0) {
            throw new IllegalArgumentException("La depreciación acumulada no puede exceder la base depreciable.");
        }
        BigDecimal cuotaTeorica = base.divide(BigDecimal.valueOf(vidaUtilMeses), ESCALA_CALCULO, REDONDEO);
        BigDecimal cargoPlaneado = dinero(cuotaTeorica.multiply(BigDecimal.valueOf(mesesACargar)));
        BigDecimal restante = dinero(base.subtract(acumulada));
        BigDecimal cargo = cargoPlaneado.min(restante);
        BigDecimal acumuladaFinal = dinero(acumulada.add(cargo));
        return new DepreciacionSegura(costoActivo, residual, base, acumulada,
                cuotaTeorica, cargo, acumuladaFinal, acumuladaFinal.compareTo(base) == 0);
    }

    /** Tasa de agotamiento = importe original del recurso / unidades estimadas. */
    public static BigDecimal tasaAgotamiento(BigDecimal importeOriginal,
                                             BigDecimal unidadesEstimadas) {
        return importeNoNegativo(importeOriginal, "importe original del recurso")
                .divide(cantidadPositiva(unidadesEstimadas, "unidades estimadas"), ESCALA_CALCULO, REDONDEO);
    }

    /** Cargo de agotamiento = unidades extraídas × tasa de agotamiento. */
    public static BigDecimal cargoAgotamiento(BigDecimal unidadesExtraidas,
                                              BigDecimal tasaAgotamiento) {
        return dinero(cantidadNoNegativa(unidadesExtraidas, "unidades extraídas")
                .multiply(tasaNoNegativa(tasaAgotamiento, "tasa de agotamiento")));
    }

    /** Amortización = valor del activo / número de períodos de duración. */
    public static BigDecimal amortizacion(BigDecimal valorActivo, int periodosDuracion) {
        BigDecimal valor = importeNoNegativo(valorActivo, "valor del activo");
        validarMeses(periodosDuracion, "períodos de duración", false);
        return dinero(valor.divide(BigDecimal.valueOf(periodosDuracion), ESCALA_CALCULO, REDONDEO));
    }

    /** Estimación de incobrables = saldo de clientes o ventas a crédito × porcentaje esperado. */
    public static BigDecimal estimacionIncobrables(BigDecimal baseEstimacion,
                                                   BigDecimal porcentajeEstimado) {
        return dinero(importeNoNegativo(baseEstimacion, "base de estimación")
                .multiply(proporcion(porcentajeEstimado, "porcentaje estimado")));
    }

    /** Ingreso acumulado = ingreso total × período devengado / período total del contrato. */
    public static BigDecimal ingresoAcumulado(BigDecimal ingresoTotalContrato,
                                             int periodoDevengado, int periodoTotal) {
        BigDecimal ingreso = importeNoNegativo(ingresoTotalContrato, "ingreso total del contrato");
        validarMeses(periodoDevengado, "período devengado", true);
        validarMeses(periodoTotal, "período total", false);
        if (periodoDevengado > periodoTotal) {
            throw new IllegalArgumentException("El período devengado no puede exceder el período total.");
        }
        return dinero(ingreso.multiply(BigDecimal.valueOf(periodoDevengado))
                .divide(BigDecimal.valueOf(periodoTotal), ESCALA_CALCULO, REDONDEO));
    }

    private static BigDecimal baseDepreciable(BigDecimal costo, BigDecimal valorResidual) {
        BigDecimal costoActivo = importeNoNegativo(costo, "costo del activo");
        BigDecimal residual = importeNoNegativo(valorResidual, "valor residual");
        if (residual.compareTo(costoActivo) > 0) {
            throw new IllegalArgumentException("El valor residual no puede exceder el costo del activo.");
        }
        return dinero(costoActivo.subtract(residual));
    }

    private static void validarMeses(int valor, String etiqueta, boolean permiteCero) {
        if (valor < 0 || (!permiteCero && valor == 0)) {
            throw new IllegalArgumentException("Los " + etiqueta + (permiteCero
                    ? " no pueden ser negativos." : " deben ser mayores que cero."));
        }
    }

    private static BigDecimal importeNoNegativo(BigDecimal valor, String etiqueta) {
        if (valor == null || valor.signum() < 0) {
            throw new IllegalArgumentException("El importe de " + etiqueta + " no puede ser negativo.");
        }
        return dinero(valor);
    }

    private static BigDecimal cantidadNoNegativa(BigDecimal valor, String etiqueta) {
        if (valor == null || valor.signum() < 0) {
            throw new IllegalArgumentException("La cantidad de " + etiqueta + " no puede ser negativa.");
        }
        return valor.setScale(ESCALA_CALCULO, REDONDEO);
    }

    private static BigDecimal cantidadPositiva(BigDecimal valor, String etiqueta) {
        BigDecimal resultado = cantidadNoNegativa(valor, etiqueta);
        if (resultado.signum() == 0) {
            throw new IllegalArgumentException("La cantidad de " + etiqueta + " debe ser mayor que cero.");
        }
        return resultado;
    }

    private static BigDecimal tasaNoNegativa(BigDecimal valor, String etiqueta) {
        if (valor == null || valor.signum() < 0) {
            throw new IllegalArgumentException("La " + etiqueta + " no puede ser negativa.");
        }
        return valor.setScale(ESCALA_CALCULO, REDONDEO);
    }

    private static BigDecimal proporcion(BigDecimal valor, String etiqueta) {
        BigDecimal proporcion = tasaNoNegativa(valor, etiqueta);
        if (proporcion.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("El " + etiqueta + " debe estar entre 0 y 1.");
        }
        return proporcion;
    }

    private static BigDecimal dinero(BigDecimal valor) {
        return valor.setScale(ESCALA_MONETARIA, REDONDEO);
    }
}
