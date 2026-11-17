package sv.edu.ues.colibri.domain.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Fórmulas puras de costos, mano de obra y toma de decisiones.
 *
 * <p>Los importes monetarios se redondean a dos decimales con
 * {@link RoundingMode#HALF_UP}. Las tasas, factores y unidades equivalentes se
 * conservan a ocho decimales para que el redondeo monetario ocurra al asignar
 * un importe. Esta clase no consulta ni modifica estado alguno.</p>
 */
public final class CostosCalculator {

    public static final int ESCALA_MONETARIA = 2;
    public static final int ESCALA_CALCULO = 8;
    public static final RoundingMode REDONDEO = RoundingMode.HALF_UP;

    public static final BigDecimal TASA_ISSS_LABORAL = new BigDecimal("0.03");
    public static final BigDecimal TASA_PREVISIONAL_LABORAL = new BigDecimal("0.0725");
    public static final BigDecimal TASA_ISSS_PATRONAL = new BigDecimal("0.075");
    public static final BigDecimal TASA_PREVISIONAL_PATRONAL = new BigDecimal("0.0875");
    public static final BigDecimal TASA_INCAF = new BigDecimal("0.01");
    public static final int MINIMO_TRABAJADORES_INCAF = 10;

    /** Estado de la diferencia entre CIF reales y aplicados. */
    public enum EstadoCif {
        EQUILIBRADO, SUBAPLICADO, SOBREAPLICADO
    }

    /** Alternativa que resulta más conveniente en un análisis gerencial. */
    public enum Opcion {
        A, B, CONSERVAR, VENDER, FABRICAR, COMPRAR, INDIFERENTE
    }

    /** Elementos del costo del producto y sus agregados didácticos. */
    public record ComponentesCosto(BigDecimal materialesDirectos, BigDecimal manoObraDirecta,
                                   BigDecimal cif, BigDecimal costoProduccion,
                                   BigDecimal costoPrimo, BigDecimal costoConversion) {
    }

    /** Resultado de aplicar una tasa predeterminada de CIF. */
    public record AnalisisCif(BigDecimal cifEstimados, BigDecimal baseEstimada,
                              BigDecimal tasaPredeterminada, BigDecimal baseReal,
                              BigDecimal cifAplicados, BigDecimal cifReales,
                              BigDecimal variacion, EstadoCif estado) {
    }

    /** Resumen de cargas, costo integral y factores de recargo de un trabajador. */
    public record ResumenPlanilla(BigDecimal salarioNominal, BigDecimal isssLaboral,
                                  BigDecimal previsionalLaboral, BigDecimal liquidoAntesRenta,
                                  BigDecimal isssPatronal, BigDecimal previsionalPatronal,
                                  BigDecimal incafTotalPlanilla, BigDecimal incafAsignado,
                                  BigDecimal aportesPatronales, BigDecimal prestacionesLey,
                                  BigDecimal prestacionesContractuales, BigDecimal costoIntegral,
                                  BigDecimal factorRecargo, BigDecimal factorRecargoEficiencia) {
    }

    /** Costo y resultado económico de una orden de servicio. */
    public record ResultadoOrden(BigDecimal costoTotal, BigDecimal unidades,
                                 BigDecimal costoUnitario, BigDecimal factorPrecio,
                                 BigDecimal precioSugerido, BigDecimal precioFacturado,
                                 BigDecimal utilidadReal) {
    }

    /** Conciliación de unidades físicas de un proceso. */
    public record BalanceFisico(BigDecimal inventarioInicial, BigDecimal unidadesIniciadas,
                                BigDecimal inventarioFinal, BigDecimal unidadesTransferidas) {
    }

    /** Unidades equivalentes calculadas con las convenciones del material de referencia. */
    public record UnidadesEquivalentes(BigDecimal simpleFinal, BigDecimal terminadasPeps,
                                       BigDecimal iniciadasYTerminadasPeps,
                                       BigDecimal promedioPonderado) {
    }

    /** Distribución de un elemento de costo entre inventario final y unidades transferidas. */
    public record AsignacionProceso(BigDecimal costoTotalPorDistribuir, BigDecimal costoUnitario,
                                    BigDecimal costoInventarioFinal,
                                    BigDecimal costoUnidadesTransferidas) {
    }

    public record AnalisisDiferencial(BigDecimal costoA, BigDecimal costoB,
                                      BigDecimal diferenciaAmenosB, Opcion recomendacion) {
    }

    public record AnalisisConservarVender(BigDecimal ahorroConservar, BigDecimal precioVenta,
                                          BigDecimal costoHundido, BigDecimal resultadoConservar,
                                          BigDecimal resultadoVender, Opcion recomendacion) {
    }

    public record AnalisisFabricarComprar(BigDecimal materialesDirectos, BigDecimal manoObraDirecta,
                                          BigDecimal cifVariables, BigDecimal cifFijosEvitables,
                                          BigDecimal costoFabricar, BigDecimal precioComprar,
                                          BigDecimal diferenciaFabricarMenosComprar,
                                          Opcion recomendacion) {
    }

    private CostosCalculator() {
    }

    /** CP = materiales directos + MOD + CIF; también informa costo primo y de conversión. */
    public static ComponentesCosto costoProducto(BigDecimal materialesDirectos,
                                                 BigDecimal manoObraDirecta,
                                                 BigDecimal cif) {
        BigDecimal materiales = importeNoNegativo(materialesDirectos, "materiales directos");
        BigDecimal manoObra = importeNoNegativo(manoObraDirecta, "mano de obra directa");
        BigDecimal indirectos = importeNoNegativo(cif, "CIF");
        BigDecimal primo = dinero(materiales.add(manoObra));
        BigDecimal conversion = dinero(manoObra.add(indirectos));
        return new ComponentesCosto(materiales, manoObra, indirectos,
                dinero(primo.add(indirectos)), primo, conversion);
    }

    /** Productividad = unidades de producción / unidades de insumo. */
    public static BigDecimal productividad(BigDecimal unidadesProduccion, BigDecimal unidadesInsumo) {
        return dividir(cantidadNoNegativa(unidadesProduccion, "unidades de producción"),
                cantidadPositiva(unidadesInsumo, "unidades de insumo"));
    }

    /** Tasa CIF = CIF estimados / base de aplicación estimada. */
    public static BigDecimal tasaPredeterminadaCif(BigDecimal cifEstimados, BigDecimal baseEstimada) {
        return dividir(importeNoNegativo(cifEstimados, "CIF estimados"),
                cantidadPositiva(baseEstimada, "base estimada"));
    }

    /** CIF aplicados = base real consumida × tasa predeterminada. */
    public static BigDecimal cifAplicados(BigDecimal baseReal, BigDecimal tasaPredeterminada) {
        return dinero(cantidadNoNegativa(baseReal, "base real")
                .multiply(tasaNoNegativa(tasaPredeterminada, "tasa predeterminada")));
    }

    /** Variación CIF = CIF reales − CIF aplicados. Un valor positivo representa subaplicación. */
    public static BigDecimal variacionCif(BigDecimal cifReales, BigDecimal cifAplicados) {
        return dinero(importeNoNegativo(cifReales, "CIF reales")
                .subtract(importeNoNegativo(cifAplicados, "CIF aplicados")));
    }

    /** Calcula tasa, aplicación y diferencia de CIF en una sola operación consistente. */
    public static AnalisisCif analizarCif(BigDecimal cifEstimados, BigDecimal baseEstimada,
                                         BigDecimal baseReal, BigDecimal cifReales) {
        BigDecimal tasa = tasaPredeterminadaCif(cifEstimados, baseEstimada);
        BigDecimal aplicados = cifAplicados(baseReal, tasa);
        BigDecimal reales = importeNoNegativo(cifReales, "CIF reales");
        BigDecimal variacion = variacionCif(reales, aplicados);
        EstadoCif estado = variacion.signum() > 0 ? EstadoCif.SUBAPLICADO
                : variacion.signum() < 0 ? EstadoCif.SOBREAPLICADO : EstadoCif.EQUILIBRADO;
        return new AnalisisCif(importeNoNegativo(cifEstimados, "CIF estimados"),
                cantidadPositiva(baseEstimada, "base estimada"), tasa,
                cantidadNoNegativa(baseReal, "base real"), aplicados, reales, variacion, estado);
    }

    /** Salario diario del sector privado: salario semanal / 5.5 días. */
    public static BigDecimal salarioDiarioPrivado(BigDecimal salarioSemanal) {
        return dinero(dividir(importeNoNegativo(salarioSemanal, "salario semanal"), new BigDecimal("5.5")));
    }

    /** Salario diario para jornada de cinco días: salario semanal / 5 días. */
    public static BigDecimal salarioDiarioCincoDias(BigDecimal salarioSemanal) {
        return dinero(dividir(importeNoNegativo(salarioSemanal, "salario semanal"), BigDecimal.valueOf(5)));
    }

    /** Vacaciones = 15 días de salario base × 1.30. */
    public static BigDecimal remuneracionVacaciones(BigDecimal salarioDiario) {
        return dinero(importeNoNegativo(salarioDiario, "salario diario")
                .multiply(BigDecimal.valueOf(15)).multiply(new BigDecimal("1.30")));
    }

    /** Costo por hora productiva = costo integral / horas productivas. */
    public static BigDecimal costoHoraProductiva(BigDecimal costoIntegral, BigDecimal horasProductivas) {
        return dinero(dividir(importeNoNegativo(costoIntegral, "costo integral"),
                cantidadPositiva(horasProductivas, "horas productivas")));
    }

    /**
     * Calcula planilla y costo integral. INCAF se determina sobre la planilla total
     * sólo si hay al menos diez trabajadores y se asigna proporcionalmente al salario.
     * La eficiencia se expresa entre 0 y 1 (por ejemplo, 0.75).
     */
    public static ResumenPlanilla calcularPlanilla(BigDecimal salarioNominal,
                                                   BigDecimal planillaMensualTotal,
                                                   int cantidadTrabajadores,
                                                   BigDecimal prestacionesLey,
                                                   BigDecimal prestacionesContractuales,
                                                   BigDecimal eficiencia) {
        BigDecimal salario = importeNoNegativo(salarioNominal, "salario nominal");
        if (salario.signum() == 0) {
            throw new IllegalArgumentException("El salario nominal debe ser mayor que cero.");
        }
        BigDecimal planilla = importeNoNegativo(planillaMensualTotal, "planilla mensual total");
        if (planilla.signum() == 0) {
            throw new IllegalArgumentException("La planilla mensual total debe ser mayor que cero.");
        }
        if (planilla.compareTo(salario) < 0) {
            throw new IllegalArgumentException("La planilla mensual total no puede ser menor que el salario nominal.");
        }
        if (cantidadTrabajadores <= 0) {
            throw new IllegalArgumentException("La cantidad de trabajadores debe ser mayor que cero.");
        }
        BigDecimal prestaciones = importeNoNegativo(prestacionesLey, "prestaciones de ley");
        BigDecimal contractuales = importeNoNegativo(prestacionesContractuales,
                "prestaciones contractuales");
        BigDecimal factorEficiencia = proporcion(eficiencia, "eficiencia");

        BigDecimal isssLaboral = porcentaje(salario, TASA_ISSS_LABORAL);
        BigDecimal previsionalLaboral = porcentaje(salario, TASA_PREVISIONAL_LABORAL);
        BigDecimal liquido = dinero(salario.subtract(isssLaboral).subtract(previsionalLaboral));
        BigDecimal isssPatronal = porcentaje(salario, TASA_ISSS_PATRONAL);
        BigDecimal previsionalPatronal = porcentaje(salario, TASA_PREVISIONAL_PATRONAL);
        BigDecimal incafTotal = cantidadTrabajadores >= MINIMO_TRABAJADORES_INCAF
                ? porcentaje(planilla, TASA_INCAF) : ceroMonetario();
        BigDecimal incafAsignado = incafTotal.signum() == 0 ? ceroMonetario()
                : dinero(incafTotal.multiply(salario).divide(planilla, ESCALA_CALCULO, REDONDEO));
        BigDecimal aportes = dinero(isssPatronal.add(previsionalPatronal).add(incafAsignado));
        BigDecimal integral = dinero(salario.add(prestaciones).add(aportes).add(contractuales));
        BigDecimal recargo = dividir(integral, salario);
        // Una eficiencia menor implica menos tiempo productivo disponible para
        // absorber el mismo costo integral. Por tanto el recargo debe crecer,
        // no reducirse: costo integral / (salario x eficiencia).
        BigDecimal recargoEficiencia = dividir(integral, salario.multiply(factorEficiencia));
        return new ResumenPlanilla(salario, isssLaboral, previsionalLaboral, liquido,
                isssPatronal, previsionalPatronal, incafTotal, incafAsignado, aportes,
                prestaciones, contractuales, integral, recargo, recargoEficiencia);
    }

    /** Costo unitario de la orden = costo total acumulado / unidades producidas. */
    public static BigDecimal costoUnitarioOrden(BigDecimal costoTotal, BigDecimal unidadesProducidas) {
        return dinero(dividir(importeNoNegativo(costoTotal, "costo total de la orden"),
                cantidadPositiva(unidadesProducidas, "unidades producidas")));
    }

    /** Precio sugerido = costo total × factor de precio (1.60 representa 160 %). */
    public static BigDecimal precioPorFactor(BigDecimal costoTotal, BigDecimal factorPrecio) {
        return dinero(importeNoNegativo(costoTotal, "costo total de la orden")
                .multiply(cantidadPositiva(factorPrecio, "factor de precio")));
    }

    /** Utilidad real = precio facturado − costo total real. */
    public static BigDecimal utilidadRealOrden(BigDecimal precioFacturado, BigDecimal costoTotalReal) {
        return dinero(importeNoNegativo(precioFacturado, "precio facturado")
                .subtract(importeNoNegativo(costoTotalReal, "costo total real")));
    }

    /** Resume las tres fórmulas de órdenes sin requerir una orden persistida. */
    public static ResultadoOrden analizarOrden(BigDecimal costoTotal, BigDecimal unidadesProducidas,
                                               BigDecimal factorPrecio, BigDecimal precioFacturado) {
        BigDecimal costo = importeNoNegativo(costoTotal, "costo total de la orden");
        BigDecimal unidades = cantidadPositiva(unidadesProducidas, "unidades producidas");
        BigDecimal factor = cantidadPositiva(factorPrecio, "factor de precio");
        BigDecimal precio = importeNoNegativo(precioFacturado, "precio facturado");
        return new ResultadoOrden(costo, unidades, costoUnitarioOrden(costo, unidades), factor,
                precioPorFactor(costo, factor), precio, utilidadRealOrden(precio, costo));
    }

    /** Unidades transferidas = inventario inicial + unidades iniciadas − inventario final. */
    public static BalanceFisico balanceFisico(BigDecimal inventarioInicial,
                                              BigDecimal unidadesIniciadas,
                                              BigDecimal inventarioFinal) {
        BigDecimal inicial = cantidadNoNegativa(inventarioInicial, "inventario inicial");
        BigDecimal iniciadas = cantidadNoNegativa(unidadesIniciadas, "unidades iniciadas");
        BigDecimal finalProceso = cantidadNoNegativa(inventarioFinal, "inventario final");
        BigDecimal transferidas = cantidad(inicial.add(iniciadas).subtract(finalProceso));
        if (transferidas.signum() < 0) {
            throw new IllegalArgumentException("El inventario final no puede exceder las unidades disponibles.");
        }
        return new BalanceFisico(inicial, iniciadas, finalProceso, transferidas);
    }

    /** UE simple = unidades en proceso × grado de avance. */
    public static BigDecimal unidadesEquivalentesSimples(BigDecimal unidadesEnProceso,
                                                         BigDecimal gradoAvance) {
        return cantidad(cantidadNoNegativa(unidadesEnProceso, "unidades en proceso")
                .multiply(proporcion(gradoAvance, "grado de avance")));
    }

    /**
     * Devuelve UE por los métodos PEPS y promedio. Los avances se expresan entre
     * 0 y 1; por ejemplo, 0.25 representa 25 %.
     */
    public static UnidadesEquivalentes unidadesEquivalentes(BigDecimal unidadesTransferidas,
                                                            BigDecimal inventarioInicial,
                                                            BigDecimal avanceInicial,
                                                            BigDecimal inventarioFinal,
                                                            BigDecimal avanceFinal,
                                                            BigDecimal iniciadasYTerminadas) {
        BigDecimal transferidas = cantidadNoNegativa(unidadesTransferidas, "unidades transferidas");
        BigDecimal inicial = cantidadNoNegativa(inventarioInicial, "inventario inicial");
        BigDecimal finalProceso = cantidadNoNegativa(inventarioFinal, "inventario final");
        BigDecimal ai = proporcion(avanceInicial, "avance inicial");
        BigDecimal af = proporcion(avanceFinal, "avance final");
        BigDecimal iniciadasTerminadas = cantidadNoNegativa(iniciadasYTerminadas,
                "unidades iniciadas y terminadas");
        BigDecimal simpleFinal = unidadesEquivalentesSimples(finalProceso, af);
        BigDecimal terminadasPeps = cantidad(transferidas.add(simpleFinal)
                .subtract(inicial.multiply(ai)));
        BigDecimal iniciadasYTerminadasPeps = cantidad(iniciadasTerminadas
                .add(inicial.multiply(BigDecimal.ONE.subtract(ai))).add(simpleFinal));
        BigDecimal promedio = cantidad(transferidas.add(simpleFinal));
        if (terminadasPeps.signum() < 0 || iniciadasYTerminadasPeps.signum() < 0) {
            throw new IllegalArgumentException("Las unidades equivalentes no pueden ser negativas.");
        }
        return new UnidadesEquivalentes(simpleFinal, terminadasPeps,
                iniciadasYTerminadasPeps, promedio);
    }

    /** Costo unitario por proceso = costo a asignar / unidades equivalentes. */
    public static BigDecimal costoUnitarioProceso(BigDecimal costoAsignar,
                                                  BigDecimal unidadesEquivalentes) {
        return dividir(importeNoNegativo(costoAsignar, "costo a asignar"),
                cantidadPositiva(unidadesEquivalentes, "unidades equivalentes"));
    }

    /**
     * PEPS: usa el costo del período para la unidad equivalente y asigna el costo
     * inicial restante a las unidades transferidas.
     */
    public static AsignacionProceso asignarPeps(BigDecimal costoInicial,
                                                BigDecimal costoPeriodo,
                                                BigDecimal unidadesEquivalentesPeriodo,
                                                BigDecimal unidadesEquivalentesFinales) {
        BigDecimal inicial = importeNoNegativo(costoInicial, "costo inicial");
        BigDecimal periodo = importeNoNegativo(costoPeriodo, "costo del período");
        BigDecimal unitario = costoUnitarioProceso(periodo, unidadesEquivalentesPeriodo);
        BigDecimal finalProceso = dinero(unitario.multiply(cantidadNoNegativa(
                unidadesEquivalentesFinales, "unidades equivalentes finales")));
        BigDecimal total = dinero(inicial.add(periodo));
        return new AsignacionProceso(total, unitario, finalProceso, dinero(total.subtract(finalProceso)));
    }

    /**
     * UEPS comparativo: aplica el costo más reciente a las unidades transferidas
     * y conserva como inventario final el costo residual por distribuir.
     */
    public static AsignacionProceso asignarUeps(BigDecimal costoInicial,
                                                BigDecimal costoPeriodo,
                                                BigDecimal unidadesEquivalentesPeriodo,
                                                BigDecimal unidadesTransferidas) {
        BigDecimal inicial = importeNoNegativo(costoInicial, "costo inicial");
        BigDecimal periodo = importeNoNegativo(costoPeriodo, "costo del período");
        BigDecimal unitario = costoUnitarioProceso(periodo, unidadesEquivalentesPeriodo);
        BigDecimal transferido = dinero(unitario.multiply(cantidadNoNegativa(
                unidadesTransferidas, "unidades transferidas")));
        BigDecimal total = dinero(inicial.add(periodo));
        if (transferido.compareTo(total) > 0) {
            throw new IllegalArgumentException("El costo de unidades transferidas no puede exceder el costo disponible.");
        }
        return new AsignacionProceso(total, unitario, dinero(total.subtract(transferido)), transferido);
    }

    /** Promedio: combina costo inicial y corriente antes de distribuirlo por UE. */
    public static AsignacionProceso asignarPromedio(BigDecimal costoInicial,
                                                    BigDecimal costoPeriodo,
                                                    BigDecimal unidadesEquivalentesPromedio,
                                                    BigDecimal unidadesEquivalentesFinales) {
        BigDecimal total = dinero(importeNoNegativo(costoInicial, "costo inicial")
                .add(importeNoNegativo(costoPeriodo, "costo del período")));
        BigDecimal unitario = costoUnitarioProceso(total, unidadesEquivalentesPromedio);
        BigDecimal finalProceso = dinero(unitario.multiply(cantidadNoNegativa(
                unidadesEquivalentesFinales, "unidades equivalentes finales")));
        if (finalProceso.compareTo(total) > 0) {
            throw new IllegalArgumentException("El costo de inventario final no puede exceder el costo disponible.");
        }
        return new AsignacionProceso(total, unitario, finalProceso, dinero(total.subtract(finalProceso)));
    }

    /** Costo diferencial = costos pertinentes A − costos pertinentes B. */
    public static AnalisisDiferencial analizarDiferencial(BigDecimal costosA, BigDecimal costosB) {
        BigDecimal a = importeNoNegativo(costosA, "costos pertinentes A");
        BigDecimal b = importeNoNegativo(costosB, "costos pertinentes B");
        BigDecimal diferencia = dinero(a.subtract(b));
        Opcion recomendacion = diferencia.signum() < 0 ? Opcion.A
                : diferencia.signum() > 0 ? Opcion.B : Opcion.INDIFERENTE;
        return new AnalisisDiferencial(a, b, diferencia, recomendacion);
    }

    /** Compara conservar o vender, mostrando el costo hundido sin tratarlo como recuperable. */
    public static AnalisisConservarVender analizarConservarOVender(BigDecimal ahorroConservar,
                                                                    BigDecimal precioVenta,
                                                                    BigDecimal costoHundido) {
        BigDecimal ahorro = importeNoNegativo(ahorroConservar, "ahorro por conservar");
        BigDecimal venta = importeNoNegativo(precioVenta, "precio de venta");
        BigDecimal hundido = importeNoNegativo(costoHundido, "costo hundido");
        BigDecimal conservar = dinero(ahorro.subtract(hundido));
        BigDecimal vender = dinero(venta.subtract(hundido));
        Opcion recomendacion = conservar.compareTo(vender) > 0 ? Opcion.CONSERVAR
                : conservar.compareTo(vender) < 0 ? Opcion.VENDER : Opcion.INDIFERENTE;
        return new AnalisisConservarVender(ahorro, venta, hundido, conservar, vender, recomendacion);
    }

    /** Costo de oportunidad = beneficio de la mejor alternativa descartada. */
    public static BigDecimal costoOportunidad(BigDecimal beneficioMejorAlternativaDescartada) {
        return importeNoNegativo(beneficioMejorAlternativaDescartada,
                "beneficio de la alternativa descartada");
    }

    /** Compara precio de compra contra MPD + MOD + CIF variables + CIF fijos evitables. */
    public static AnalisisFabricarComprar analizarFabricarComprar(BigDecimal materialesDirectos,
                                                                  BigDecimal manoObraDirecta,
                                                                  BigDecimal cifVariables,
                                                                  BigDecimal cifFijosEvitables,
                                                                  BigDecimal precioComprar) {
        BigDecimal materiales = importeNoNegativo(materialesDirectos, "materiales directos");
        BigDecimal manoObra = importeNoNegativo(manoObraDirecta, "mano de obra directa");
        BigDecimal variables = importeNoNegativo(cifVariables, "CIF variables");
        BigDecimal fijos = importeNoNegativo(cifFijosEvitables, "CIF fijos evitables");
        BigDecimal compra = importeNoNegativo(precioComprar, "precio de compra");
        BigDecimal fabricar = dinero(materiales.add(manoObra).add(variables).add(fijos));
        BigDecimal diferencia = dinero(fabricar.subtract(compra));
        Opcion recomendacion = diferencia.signum() < 0 ? Opcion.FABRICAR
                : diferencia.signum() > 0 ? Opcion.COMPRAR : Opcion.INDIFERENTE;
        return new AnalisisFabricarComprar(materiales, manoObra, variables, fijos, fabricar,
                compra, diferencia, recomendacion);
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
        return cantidad(valor);
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
            throw new IllegalArgumentException("La " + etiqueta + " debe estar entre 0 y 1.");
        }
        return proporcion;
    }

    private static BigDecimal porcentaje(BigDecimal base, BigDecimal tasa) {
        return dinero(base.multiply(tasa));
    }

    private static BigDecimal dividir(BigDecimal dividendo, BigDecimal divisor) {
        return dividendo.divide(divisor, ESCALA_CALCULO, REDONDEO);
    }

    private static BigDecimal dinero(BigDecimal valor) {
        return valor.setScale(ESCALA_MONETARIA, REDONDEO);
    }

    private static BigDecimal cantidad(BigDecimal valor) {
        return valor.setScale(ESCALA_CALCULO, REDONDEO);
    }

    private static BigDecimal ceroMonetario() {
        return BigDecimal.ZERO.setScale(ESCALA_MONETARIA, REDONDEO);
    }
}
