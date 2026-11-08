package sv.edu.ues.colibri.application.costos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Servicio de costeo gerencial para métodos directo, estándar y ABC.
 */
public final class CosteoGerencialService {

    /**
     * Resultado del costeo directo.
     */
    public record CostoDirecto(BigDecimal materiales, BigDecimal manoObraDirecta,
                               BigDecimal total, BigDecimal unitario) {}

    /**
     * Resultado del costeo estándar.
     */
    public record CostoEstandar(BigDecimal materialesEstandar, BigDecimal manoObraEstandar,
                                BigDecimal cifEstandar, BigDecimal totalEstandar,
                                BigDecimal totalReal, BigDecimal variacion, BigDecimal unitarioEstandar) {}

    /**
     * Actividad usada como base del costeo ABC.
     */
    public record ActividadAbc(String nombre, BigDecimal costoAplicado) {}

    /**
     * Resultado del costeo basado en actividades.
     */
    public record CostoAbc(BigDecimal materiales, BigDecimal manoObraDirecta,
                            BigDecimal cifPorActividades, BigDecimal total, BigDecimal unitario) {}

    /**
     * Calcula el costo directo y el costo unitario.
     *
     * @param materiales costo de materiales
     * @param manoObraDirecta costo de mano de obra directa
     * @param unidades cantidad de unidades producidas
     * @return resultado del costeo directo
     */
    public CostoDirecto calcularDirecto(BigDecimal materiales, BigDecimal manoObraDirecta, int unidades) {
        validarImporte(materiales, "materiales");
        validarImporte(manoObraDirecta, "mano de obra directa");
        validarUnidades(unidades);
        BigDecimal total = materiales.add(manoObraDirecta).setScale(2, RoundingMode.HALF_UP);
        return new CostoDirecto(redondear(materiales), redondear(manoObraDirecta), total, porUnidad(total, unidades));
    }

    /**
     * Calcula la variación entre costo estándar y costo real.
     *
     * @param materiales costo estándar de materiales
     * @param manoObraDirecta costo estándar de mano de obra directa
     * @param cif costo estándar de CIF
     * @param totalReal costo real observado
     * @param unidades cantidad de unidades producidas
     * @return resultado del análisis estándar
     */
    public CostoEstandar analizarEstandar(BigDecimal materiales, BigDecimal manoObraDirecta,
                                          BigDecimal cif, BigDecimal totalReal, int unidades) {
        validarImporte(materiales, "materiales estándar");
        validarImporte(manoObraDirecta, "mano de obra estándar");
        validarImporte(cif, "CIF estándar");
        validarImporte(totalReal, "costo real");
        validarUnidades(unidades);
        BigDecimal estandar = materiales.add(manoObraDirecta).add(cif).setScale(2, RoundingMode.HALF_UP);
        return new CostoEstandar(redondear(materiales), redondear(manoObraDirecta), redondear(cif), estandar,
                redondear(totalReal), totalReal.subtract(estandar).setScale(2, RoundingMode.HALF_UP),
                porUnidad(estandar, unidades));
    }

    /**
     * Calcula el costo ABC total y unitario.
     *
     * @param materiales costo de materiales
     * @param manoObraDirecta costo de mano de obra directa
     * @param actividades actividades que componen el CIF
     * @param unidades cantidad de unidades producidas
     * @return resultado del costeo ABC
     */
    public CostoAbc calcularAbc(BigDecimal materiales, BigDecimal manoObraDirecta,
                                 List<ActividadAbc> actividades, int unidades) {
        validarImporte(materiales, "materiales");
        validarImporte(manoObraDirecta, "mano de obra directa");
        validarUnidades(unidades);
        if (actividades == null || actividades.isEmpty()) {
            throw new IllegalArgumentException("Registre al menos una actividad para el costeo ABC.");
        }
        BigDecimal cif = actividades.stream().map(ActividadAbc::costoAplicado)
                .peek(costo -> validarImporte(costo, "costo de actividad"))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = materiales.add(manoObraDirecta).add(cif).setScale(2, RoundingMode.HALF_UP);
        return new CostoAbc(redondear(materiales), redondear(manoObraDirecta), cif, total, porUnidad(total, unidades));
    }

    private static BigDecimal porUnidad(BigDecimal total, int unidades) {
        return total.divide(BigDecimal.valueOf(unidades), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal redondear(BigDecimal importe) {
        return importe.setScale(2, RoundingMode.HALF_UP);
    }

    private static void validarImporte(BigDecimal importe, String etiqueta) {
        if (importe == null || importe.signum() < 0) {
            throw new IllegalArgumentException("El importe de " + etiqueta + " no puede ser negativo.");
        }
    }

    private static void validarUnidades(int unidades) {
        if (unidades <= 0) {
            throw new IllegalArgumentException("Las unidades deben ser mayores que cero.");
        }
    }
}
