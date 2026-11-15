package sv.edu.ues.colibri.application.costos;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Servicio gerencial para estimar costos, esfuerzo y precio sin generar asientos contables.
 */
public final class EstimacionCostosService {
    private static final BigDecimal HORAS_MES = new BigDecimal("160");
    private static final BigDecimal COCOMO_A = new BigDecimal("2.94");
    private static final BigDecimal COCOMO_B = new BigDecimal("0.91");

    /**
     * Métodos de estimación soportados por el servicio.
     */
    public enum Metodo {
        JUICIO_EXPERTOS("Juicio de expertos"), ANALOGIA("Por analogía"),
        PUNTOS_FUNCION("Puntos de función"), COSMIC("COSMIC"), COCOMO("COCOMO"),
        DESGASTE_TRABAJO("Desgaste de trabajo"), LINEAS_CODIGO("Líneas de código"),
        TRES_PUNTOS("Tres puntos (PERT)"), SLIM_PUTNAM("SLIM / Putnam");
        private final String etiqueta;
        Metodo(String etiqueta) { this.etiqueta = etiqueta; }
        public String etiqueta() { return etiqueta; }
        @Override public String toString() { return etiqueta; }
    }

    /**
     * Resultado calculado de una estimación de costos.
     */
    public record Resultado(Metodo metodo, BigDecimal esfuerzoHoras, BigDecimal costoDirecto,
                            BigDecimal cif, BigDecimal costoTotal, BigDecimal precioUnitario,
                            BigDecimal margen, String recomendacion) {}

    /**
     * Resumen estadístico de los costos totales obtenidos con varios métodos de estimación.
     * La varianza y la desviación estándar son muestrales, porque los métodos representan
     * escenarios alternativos de un mismo trabajo y no una población completa.
     */
    public record EstadisticasEstimacion(int muestras, BigDecimal promedio, BigDecimal mediana,
                                         BigDecimal minimo, BigDecimal maximo, BigDecimal rango,
                                         BigDecimal varianzaMuestral,
                                         BigDecimal desviacionEstandarMuestral,
                                         BigDecimal coeficienteVariacion) {}

    /**
     * Estima el costo total a partir de una base según el método seleccionado.
     *
     * @param metodo método de estimación
     * @param base base técnica o funcional del método
     * @param tarifaHora tarifa por hora
     * @param cifPorcentaje porcentaje de costos indirectos
     * @param margenPorcentaje porcentaje de margen deseado
     * @return resultado de la estimación
     */
    public Resultado estimar(Metodo metodo, BigDecimal base, BigDecimal tarifaHora,
                             BigDecimal cifPorcentaje, BigDecimal margenPorcentaje) {
        exigir(metodo, "método"); exigirPositivo(base, "base de estimación");
        BigDecimal horas = switch (metodo) {
            case JUICIO_EXPERTOS -> juicioExpertos(base.multiply(bd("0.85")), base, base.multiply(bd("1.25")), List.of(bd("1"), bd("1"), bd("1")));
            case ANALOGIA -> analogia(base, base, base, bd("1"));
            case PUNTOS_FUNCION -> puntosFuncion(base, bd("1.20"));
            case COSMIC -> cosmic(base, bd("1.10"));
            case COCOMO -> cocomo(base, bd("0"), bd("1"), HORAS_MES);
            case DESGASTE_TRABAJO -> desgasteTrabajo(base, bd("15"));
            case LINEAS_CODIGO -> lineasCodigo(base, bd("1.3333333333"));
            case TRES_PUNTOS -> tresPuntos(base.multiply(bd("0.80")), base, base.multiply(bd("1.30")));
            case SLIM_PUTNAM -> slimPutnam(base, bd("10"), bd("1"));
        };
        return costear(metodo, horas, tarifaHora, cifPorcentaje, margenPorcentaje);
    }

    /**
     * Convierte un esfuerzo en horas en costo y precio.
     *
     * @param metodo método de estimación utilizado
     * @param horas esfuerzo en horas
     * @param tarifaHora tarifa por hora
     * @param cifPorcentaje porcentaje de costos indirectos
     * @param margenPorcentaje porcentaje de margen deseado
     * @return resultado del costeo
     */
    public Resultado costearEsfuerzo(Metodo metodo, BigDecimal horas, BigDecimal tarifaHora,
                                     BigDecimal cifPorcentaje, BigDecimal margenPorcentaje) {
        exigir(metodo, "método");
        exigirPositivo(horas, "esfuerzo en horas");
        return costear(metodo, horas, tarifaHora, cifPorcentaje, margenPorcentaje);
    }

    /**
     * Resume la dispersión de los costos totales calculados por los métodos de estimación.
     *
     * @param resultados estimaciones que se desean contrastar
     * @return promedio, mediana, extremos y medidas de variabilidad de los costos totales
     */
    public EstadisticasEstimacion resumirCostos(List<Resultado> resultados) {
        if (resultados == null || resultados.isEmpty()) {
            throw new IllegalArgumentException("Se requiere al menos una estimación para calcular estadísticas.");
        }
        List<BigDecimal> costos = new ArrayList<>(resultados.size());
        for (Resultado resultado : resultados) {
            if (resultado == null || resultado.costoTotal() == null) {
                throw new IllegalArgumentException("Cada estimación debe incluir un costo total.");
            }
            costos.add(resultado.costoTotal());
        }
        costos.sort(Comparator.naturalOrder());
        int muestras = costos.size();
        BigDecimal suma = costos.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal promedio = suma.divide(BigDecimal.valueOf(muestras), 8, RoundingMode.HALF_UP);
        BigDecimal mediana = muestras % 2 == 0
                ? costos.get(muestras / 2 - 1).add(costos.get(muestras / 2))
                        .divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP)
                : costos.get(muestras / 2);
        BigDecimal minimo = costos.getFirst();
        BigDecimal maximo = costos.getLast();
        BigDecimal rango = maximo.subtract(minimo);

        BigDecimal sumaCuadrados = costos.stream()
                .map(costo -> costo.subtract(promedio).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal varianza = muestras > 1
                ? sumaCuadrados.divide(BigDecimal.valueOf(muestras - 1), 8, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal desviacion = varianza.signum() == 0
                ? BigDecimal.ZERO
                : varianza.sqrt(MathContext.DECIMAL64);
        BigDecimal coeficiente = promedio.signum() == 0
                ? BigDecimal.ZERO
                : desviacion.multiply(BigDecimal.valueOf(100))
                        .divide(promedio, 8, RoundingMode.HALF_UP);
        return new EstadisticasEstimacion(muestras, scale(promedio), scale(mediana), scale(minimo),
                scale(maximo), scale(rango), scale(varianza), scale(desviacion), scale(coeficiente));
    }

    /**
     * Calcula una estimación por juicio de expertos usando promedio ponderado.
     *
     * @param optimista estimación optimista
     * @param probable estimación probable
     * @param pesimista estimación pesimista
     * @param pesos pesos asignados a cada experto
     * @return estimación ponderada
     */
    public BigDecimal juicioExpertos(BigDecimal optimista, BigDecimal probable, BigDecimal pesimista, List<BigDecimal> pesos) {
        exigirPositivo(optimista, "estimación optimista"); exigirPositivo(probable, "estimación probable"); exigirPositivo(pesimista, "estimación pesimista");
        if (pesos == null || pesos.size() != 3) throw new IllegalArgumentException("Se requieren tres pesos de expertos.");
        pesos.forEach(peso -> exigirPositivo(peso, "peso de experto"));
        BigDecimal suma = pesos.stream().reduce(BigDecimal.ZERO, BigDecimal::add); exigirPositivo(suma, "suma de pesos");
        return optimista.multiply(pesos.get(0)).add(probable.multiply(pesos.get(1))).add(pesimista.multiply(pesos.get(2))).divide(suma, 8, RoundingMode.HALF_UP);
    }

    /**
     * Estima por analogía escalando un esfuerzo comparable a un tamaño objetivo.
     *
     * @param esfuerzoComparable esfuerzo del caso comparable
     * @param tamanoComparable tamaño del caso comparable
     * @param tamanoObjetivo tamaño objetivo
     * @param factorAjuste factor de ajuste por riesgo o complejidad
     * @return esfuerzo estimado
     */
    public BigDecimal analogia(BigDecimal esfuerzoComparable, BigDecimal tamanoComparable, BigDecimal tamanoObjetivo, BigDecimal factorAjuste) {
        exigirPositivo(esfuerzoComparable, "esfuerzo comparable"); exigirPositivo(tamanoComparable, "tamaño comparable"); exigirPositivo(tamanoObjetivo, "tamaño objetivo"); exigirPositivo(factorAjuste, "factor de ajuste");
        return esfuerzoComparable.multiply(tamanoObjetivo).divide(tamanoComparable, 8, RoundingMode.HALF_UP).multiply(factorAjuste);
    }

    /**
     * Convierte puntos de función en horas de esfuerzo.
     *
     * @param puntosAjustados puntos de función ajustados
     * @param horasPorPunto horas por punto de función
     * @return esfuerzo estimado
     */
    public BigDecimal puntosFuncion(BigDecimal puntosAjustados, BigDecimal horasPorPunto) { return producto(puntosAjustados, horasPorPunto, "puntos ajustados", "horas por punto"); }

    /**
     * Convierte puntos COSMIC en horas de esfuerzo.
     *
     * @param cfp puntos COSMIC
     * @param horasPorCfp horas por punto COSMIC
     * @return esfuerzo estimado
     */
    public BigDecimal cosmic(BigDecimal cfp, BigDecimal horasPorCfp) { return producto(cfp, horasPorCfp, "CFP", "horas por CFP"); }

    /**
     * Calcula esfuerzo con el modelo COCOMO II.
     *
     * @param kloc tamaño del software en miles de líneas de código
     * @param sumaFactoresEscala suma de factores de escala
     * @param multiplicadoresEsfuerzo multiplicadores de esfuerzo
     * @param horasPorMes conversión de persona-mes a horas
     * @return esfuerzo estimado en horas
     */
    public BigDecimal cocomo(BigDecimal kloc, BigDecimal sumaFactoresEscala, BigDecimal multiplicadoresEsfuerzo, BigDecimal horasPorMes) {
        exigirPositivo(kloc, "KLOC"); exigirNoNegativo(sumaFactoresEscala, "factores de escala"); exigirPositivo(multiplicadoresEsfuerzo, "multiplicadores de esfuerzo"); exigirPositivo(horasPorMes, "horas por mes");
        double exponente = COCOMO_B.doubleValue() + 0.01d * sumaFactoresEscala.doubleValue();
        double personaMes = COCOMO_A.doubleValue() * Math.pow(kloc.doubleValue(), exponente) * multiplicadoresEsfuerzo.doubleValue();
        return bd(personaMes).multiply(horasPorMes);
    }

    /**
     * Corrige horas productivas según el porcentaje de desgaste o retrabajo.
     *
     * @param horasProductivas horas productivas observadas
     * @param porcentajeDesgaste porcentaje de desgaste
     * @return horas corregidas
     */
    public BigDecimal desgasteTrabajo(BigDecimal horasProductivas, BigDecimal porcentajeDesgaste) {
        exigirPositivo(horasProductivas, "horas productivas"); exigirRango(porcentajeDesgaste, "porcentaje de desgaste", bd("0"), bd("99.99"));
        return horasProductivas.divide(BigDecimal.ONE.subtract(porcentajeDesgaste.divide(bd("100"), 8, RoundingMode.HALF_UP)), 8, RoundingMode.HALF_UP);
    }

    /**
     * Estima horas a partir de líneas de código y productividad histórica.
     *
     * @param kloc tamaño en miles de líneas de código
     * @param klocPorPersonaMes productividad en KLOC por persona-mes
     * @return esfuerzo estimado
     */
    public BigDecimal lineasCodigo(BigDecimal kloc, BigDecimal klocPorPersonaMes) {
        exigirPositivo(kloc, "KLOC"); exigirPositivo(klocPorPersonaMes, "productividad KLOC/persona-mes");
        return kloc.divide(klocPorPersonaMes, 8, RoundingMode.HALF_UP).multiply(HORAS_MES);
    }

    /**
     * Calcula el esfuerzo esperado con la técnica PERT.
     *
     * @param optimista estimación optimista
     * @param probable estimación probable
     * @param pesimista estimación pesimista
     * @return esfuerzo esperado
     */
    public BigDecimal tresPuntos(BigDecimal optimista, BigDecimal probable, BigDecimal pesimista) {
        exigirPositivo(optimista, "estimación optimista"); exigirPositivo(probable, "estimación probable"); exigirPositivo(pesimista, "estimación pesimista");
        return optimista.add(probable.multiply(bd("4"))).add(pesimista).divide(bd("6"), 8, RoundingMode.HALF_UP);
    }

    /**
     * Calcula el esfuerzo con el modelo SLIM/Putnam.
     *
     * @param tamano tamaño estimado del producto
     * @param productividadCk productividad histórica Ck
     * @param duracionAnos duración estimada en años
     * @return esfuerzo estimado en horas
     */
    public BigDecimal slimPutnam(BigDecimal tamano, BigDecimal productividadCk, BigDecimal duracionAnos) {
        exigirPositivo(tamano, "tamaño SLIM"); exigirPositivo(productividadCk, "productividad Ck"); exigirPositivo(duracionAnos, "duración en años");
        double personaAnos = Math.pow(tamano.doubleValue() / (productividadCk.doubleValue() * Math.pow(duracionAnos.doubleValue(), 4d / 3d)), 3d);
        return bd(personaAnos).multiply(bd("2080"));
    }

    private Resultado costear(Metodo metodo, BigDecimal horas, BigDecimal tarifaHora, BigDecimal cifPorcentaje, BigDecimal margenPorcentaje) {
        exigirPositivo(tarifaHora, "tarifa por hora"); exigirRango(cifPorcentaje, "CIF", bd("0"), null); exigirRango(margenPorcentaje, "margen", bd("0"), bd("99.99"));
        BigDecimal directo = horas.multiply(tarifaHora);
        BigDecimal cif = directo.multiply(cifPorcentaje).divide(bd("100"), 2, RoundingMode.HALF_UP);
        BigDecimal total = directo.add(cif).setScale(2, RoundingMode.HALF_UP);
        BigDecimal divisor = BigDecimal.ONE.subtract(margenPorcentaje.divide(bd("100"), 8, RoundingMode.HALF_UP));
        BigDecimal precio = total.divide(divisor, 2, RoundingMode.HALF_UP);
        return new Resultado(metodo, scale(horas), scale(directo), cif, total, precio, scale(precio.subtract(total)), recomendacion(metodo));
    }

    private String recomendacion(Metodo m) {
        return switch (m) {
            case JUICIO_EXPERTOS -> "Conservar estimaciones y pesos de expertos.";
            case ANALOGIA -> "Documentar tamaño, comparable y factor de riesgo.";
            case PUNTOS_FUNCION, COSMIC -> "Conservar conteo funcional y productividad histórica.";
            case COCOMO -> "Registrar KLOC, factores de escala y multiplicadores.";
            case DESGASTE_TRABAJO -> "Medir horas improductivas y retrabajo por orden.";
            case LINEAS_CODIGO -> "Conservar KLOC y productividad por tecnología.";
            case TRES_PUNTOS -> "Conservar estimaciones optimista, probable y pesimista.";
            case SLIM_PUTNAM -> "Registrar tamaño, Ck y duración; calibrar con históricos.";
        };
    }

    private static BigDecimal producto(BigDecimal a, BigDecimal b, String na, String nb) { exigirPositivo(a, na); exigirPositivo(b, nb); return a.multiply(b); }
    private static BigDecimal bd(String v) { return new BigDecimal(v); }
    private static BigDecimal bd(double v) { return BigDecimal.valueOf(v); }
    private static BigDecimal scale(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }
    private static void exigir(Object value, String nombre) { if (value == null) throw new IllegalArgumentException("El " + nombre + " es obligatorio."); }
    private static void exigirPositivo(BigDecimal v, String n) { exigirNoNegativo(v, n); if (v.signum() == 0) throw new IllegalArgumentException("La " + n + " debe ser mayor que cero."); }
    private static void exigirNoNegativo(BigDecimal v, String n) { if (v == null || v.signum() < 0) throw new IllegalArgumentException("El " + n + " no puede ser negativo."); }
    private static void exigirRango(BigDecimal v, String n, BigDecimal min, BigDecimal max) { exigirNoNegativo(v, n); if (v.compareTo(min) < 0 || (max != null && v.compareTo(max) > 0)) throw new IllegalArgumentException("El " + n + " está fuera de rango."); }
}
