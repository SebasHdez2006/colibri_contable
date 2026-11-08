package sv.edu.ues.colibri.desktop.ui.views;

import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.application.costos.CosteoGerencialService;
import sv.edu.ues.colibri.application.costos.EstimacionCostosService;
import sv.edu.ues.colibri.desktop.ui.ResponsiveLayouts;
import sv.edu.ues.colibri.desktop.ui.UiCatalogos;
import sv.edu.ues.colibri.desktop.ui.UiDialogs;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Pantalla gerencial para estimar costos, contrastar métodos y comparar
 * presupuestos contra el costo real registrado en las órdenes.
 */
public final class EstimacionCostosView extends VBox {

    private final AppContext context;

    /**
     * Crea la vista de estimación y análisis de costos.
     *
     * @param context contexto de aplicación con acceso a los registros de costos
     */
    public EstimacionCostosView(AppContext context) {
        this.context = Objects.requireNonNull(context, "El contexto de aplicación es obligatorio.");
        EstimacionCostosService motor = new EstimacionCostosService();
        ComboBox<EstimacionCostosService.Metodo> metodo = new ComboBox<>(
                FXCollections.observableArrayList(EstimacionCostosService.Metodo.values()));
        metodo.setValue(EstimacionCostosService.Metodo.TRES_PUNTOS);
        UiCatalogos.ayuda(metodo, "Técnica de estimación; las etiquetas de los parámetros cambian según el método.");
        TextField parametro1 = new TextField();
        TextField parametro2 = new TextField();
        TextField parametro3 = new TextField();
        TextField parametro4 = new TextField();
        UiCatalogos.ayuda(parametro1, "Parámetro del método seleccionado; la etiqueta se actualiza al cambiar la técnica.");
        UiCatalogos.ayuda(parametro2, "Parámetro del método seleccionado.");
        UiCatalogos.ayuda(parametro3, "Parámetro del método seleccionado.");
        UiCatalogos.ayuda(parametro4, "Parámetro del método seleccionado.");
        VBox parametros = new VBox(8);
        configurarParametros(metodo.getValue(), parametros, parametro1, parametro2, parametro3, parametro4);
        metodo.valueProperty().addListener((observable, anterior, actual) ->
                configurarParametros(actual, parametros, parametro1, parametro2, parametro3, parametro4));
        TextField tarifa = new TextField("12");
        tarifa.setPromptText("USD/hora");
        UiCatalogos.ayuda(tarifa, "Tarifa por hora de la mano de obra directa en USD.");
        TextField cif = new TextField("20");
        cif.setPromptText("%");
        UiCatalogos.ayuda(cif, "Porcentaje de costos indirectos de fabricación sobre la mano de obra.");
        TextField margen = new TextField("30");
        margen.setPromptText("%");
        UiCatalogos.ayuda(margen, "Margen de utilidad objetivo sobre el costo total, en %.");
        Label resultado = new Label("Seleccione una técnica y complete sus insumos específicos.");
        resultado.setWrapText(true);
        Button calcular = new Button("Calcular costo y precio unitario");
        calcular.setOnAction(evento -> {
            try {
                BigDecimal p1 = valor(parametro1);
                BigDecimal p2 = valor(parametro2);
                BigDecimal p3 = valor(parametro3);
                BigDecimal p4 = valor(parametro4);
                BigDecimal horas = switch (metodo.getValue()) {
                    case JUICIO_EXPERTOS -> motor.juicioExpertos(p1, p2, p3, java.util.List.of(
                            new BigDecimal("1"), new BigDecimal("4"), new BigDecimal("1")));
                    case ANALOGIA -> motor.analogia(p1, p2, p3, p4);
                    case PUNTOS_FUNCION -> motor.puntosFuncion(p1, p2);
                    case COSMIC -> motor.cosmic(p1, p2);
                    case COCOMO -> motor.cocomo(p1, p2, p3, p4);
                    case DESGASTE_TRABAJO -> motor.desgasteTrabajo(p1, p2);
                    case LINEAS_CODIGO -> motor.lineasCodigo(p1, p2);
                    case TRES_PUNTOS -> motor.tresPuntos(p1, p2, p3);
                    case SLIM_PUTNAM -> motor.slimPutnam(p1, p2, p3);
                };
                var r = motor.costearEsfuerzo(metodo.getValue(), horas, new BigDecimal(tarifa.getText()),
                        new BigDecimal(cif.getText()), new BigDecimal(margen.getText()));
                resultado.setText("Método: %s · Esfuerzo: %s h · Directo: $%s · CIF: $%s · "
                        .formatted(r.metodo(), r.esfuerzoHoras(), r.costoDirecto(), r.cif())
                        + "Costo total: $%s · Precio unitario: $%s · Margen: $%s%n%s"
                        .formatted(r.costoTotal(), r.precioUnitario(), r.margen(), r.recomendacion()));
            } catch (Exception ex) {
                UiDialogs.error(ex.getMessage());
            }
        });
        Label politicas = new Label("Materiales y licencias se controlan por lote y PEPS; "
                + "promedio y UEPS quedan disponibles para análisis. La mano de obra se registra como MOD por "
                + "horas/tarifa y el CIF se aplica por centro de costo.\n\nAcumulación: órdenes de trabajo "
                + "para software a la medida; por procesos para SaaS; costos estándar/directos y ABC para "
                + "análisis gerencial. Toda venta debe documentar canal, plan, precio neto, IVA y costo reconocido.");
        politicas.setWrapText(true);
        VBox estimacion = ResponsiveLayouts.sectionCard(
                "Estimación de costos de software y planeación de distribución/venta",
                "Calcule el esfuerzo, el costo total y un precio unitario recomendado.",
                ResponsiveLayouts.row(ResponsiveLayouts.field("Método", metodo),
                        ResponsiveLayouts.field("Tarifa/hora", tarifa), ResponsiveLayouts.field("CIF %", cif),
                        ResponsiveLayouts.field("Margen %", margen)),
                parametros, calcular, resultado);
        getChildren().addAll(estimacion, calculadoraGerencial(), comparacionEstimadoReal(),
                ResponsiveLayouts.sectionCard("Políticas de costos y ventas", politicas));
    }

    private static BigDecimal valor(TextField campo) {
        String texto = campo.getText() == null ? "" : campo.getText().trim();
        return texto.isEmpty() ? BigDecimal.ZERO : new BigDecimal(texto);
    }

    private static void configurarParametros(EstimacionCostosService.Metodo metodo, VBox contenedor,
                                             TextField p1, TextField p2, TextField p3, TextField p4) {
        String l1;
        String l2;
        String l3 = null;
        String l4 = null;
        String v1;
        String v2;
        String v3 = "";
        String v4 = "";
        switch (metodo) {
            case JUICIO_EXPERTOS -> { l1 = "Optimista"; l2 = "Probable"; l3 = "Pesimista"; v1 = "80"; v2 = "100"; v3 = "130"; }
            case ANALOGIA -> { l1 = "Esfuerzo comparable"; l2 = "Tamaño comparable"; l3 = "Tamaño objetivo"; l4 = "Factor de ajuste"; v1 = "100"; v2 = "100"; v3 = "100"; v4 = "1"; }
            case PUNTOS_FUNCION -> { l1 = "Puntos ajustados"; l2 = "Horas por punto"; v1 = "100"; v2 = "1.2"; }
            case COSMIC -> { l1 = "CFP"; l2 = "Horas por CFP"; v1 = "100"; v2 = "1.1"; }
            case COCOMO -> { l1 = "KLOC"; l2 = "Σ factores de escala"; l3 = "Π multiplicadores de esfuerzo"; l4 = "Horas por mes"; v1 = "10"; v2 = "0"; v3 = "1"; v4 = "160"; }
            case DESGASTE_TRABAJO -> { l1 = "Horas productivas"; l2 = "Desgaste %"; v1 = "100"; v2 = "15"; }
            case LINEAS_CODIGO -> { l1 = "KLOC"; l2 = "KLOC por persona-mes"; v1 = "100"; v2 = "1.3333333333"; }
            case TRES_PUNTOS -> { l1 = "Optimista"; l2 = "Probable"; l3 = "Pesimista"; v1 = "80"; v2 = "100"; v3 = "130"; }
            case SLIM_PUTNAM -> { l1 = "Tamaño"; l2 = "Productividad Ck"; l3 = "Duración (años)"; v1 = "100"; v2 = "10"; v3 = "1"; }
            default -> throw new IllegalStateException("Método no soportado: " + metodo);
        }
        p1.setText(v1); p2.setText(v2); p3.setText(v3); p4.setText(v4);
        contenedor.getChildren().clear();
        if (l4 != null) {
            contenedor.getChildren().add(ResponsiveLayouts.row(ResponsiveLayouts.field(l1, p1),
                    ResponsiveLayouts.field(l2, p2), ResponsiveLayouts.field(l3, p3), ResponsiveLayouts.field(l4, p4)));
        } else if (l3 != null) {
            contenedor.getChildren().add(ResponsiveLayouts.row(ResponsiveLayouts.field(l1, p1),
                    ResponsiveLayouts.field(l2, p2), ResponsiveLayouts.field(l3, p3)));
        } else {
            contenedor.getChildren().add(ResponsiveLayouts.row(ResponsiveLayouts.field(l1, p1),
                    ResponsiveLayouts.field(l2, p2)));
        }
    }

    private VBox calculadoraGerencial() {
        CosteoGerencialService motor = new CosteoGerencialService();
        TextField materiales = new TextField("400");
        materiales.setPromptText("USD");
        UiCatalogos.ayuda(materiales, "Materiales y recursos directos consumidos, en USD.");
        TextField mod = new TextField("600");
        mod.setPromptText("USD");
        UiCatalogos.ayuda(mod, "MOD: mano de obra directa (horas × tarifa), en USD.");
        TextField cifEstandar = new TextField("200");
        cifEstandar.setPromptText("USD");
        UiCatalogos.ayuda(cifEstandar, "Costos indirectos de fabricación presupuestados, en USD.");
        TextField real = new TextField("1250");
        real.setPromptText("USD");
        UiCatalogos.ayuda(real, "Costo real total registrado, en USD; sirve para calcular la variación.");
        TextField unidades = new TextField("100");
        unidades.setPromptText("Unidades");
        UiCatalogos.ayuda(unidades, "Cantidad de unidades producidas o vendidas.");
        TextField calidad = new TextField("80");
        calidad.setPromptText("USD");
        UiCatalogos.ayuda(calidad, "Costo de la actividad de calidad para el cálculo ABC.");
        TextField infraestructura = new TextField("120");
        infraestructura.setPromptText("USD");
        UiCatalogos.ayuda(infraestructura, "Costo de la actividad de infraestructura para el cálculo ABC.");
        Label salida = new Label("Ingrese los importes para comparar el costo por método.");
        salida.setWrapText(true);
        Button calcular = new Button("Comparar métodos de costeo");
        calcular.setOnAction(evento -> {
            try {
                BigDecimal mat = new BigDecimal(materiales.getText());
                BigDecimal manoObra = new BigDecimal(mod.getText());
                int cantidad = Integer.parseInt(unidades.getText());
                var directo = motor.calcularDirecto(mat, manoObra, cantidad);
                var estandar = motor.analizarEstandar(mat, manoObra, new BigDecimal(cifEstandar.getText()),
                        new BigDecimal(real.getText()), cantidad);
                var abc = motor.calcularAbc(mat, manoObra, java.util.List.of(
                        new CosteoGerencialService.ActividadAbc("Calidad", new BigDecimal(calidad.getText())),
                        new CosteoGerencialService.ActividadAbc("Infraestructura", new BigDecimal(infraestructura.getText()))),
                        cantidad);
                salida.setText("Directo: $%s total / $%s por unidad · Estándar: $%s total / variación $%s · "
                        .formatted(directo.total(), directo.unitario(), estandar.totalEstandar(), estandar.variacion())
                        + "ABC: CIF aplicado $%s, total $%s / $%s por unidad."
                        .formatted(abc.cifPorActividades(), abc.total(), abc.unitario()));
            } catch (Exception ex) {
                UiDialogs.error(ex.getMessage());
            }
        });
        return ResponsiveLayouts.sectionCard("Costos directos, estándar y basado en actividades (ABC)",
                "Compare los métodos antes de fijar el precio.",
                ResponsiveLayouts.row(ResponsiveLayouts.field("Materiales directos", materiales),
                        ResponsiveLayouts.field("MOD", mod), ResponsiveLayouts.field("CIF estándar", cifEstandar),
                        ResponsiveLayouts.field("Costo real", real), ResponsiveLayouts.field("Unidades", unidades)),
                ResponsiveLayouts.row(ResponsiveLayouts.field("ABC · Calidad", calidad),
                        ResponsiveLayouts.field("ABC · Infraestructura", infraestructura), calcular), salida);
    }

    private VBox comparacionEstimadoReal() {
        TextField orden = new TextField("OS2026001");
        orden.setPromptText("Ej. OS2026001");
        UiCatalogos.ayuda(orden, "Código de la orden de servicio a comparar.");
        Label salida = new Label("Consulte una orden para comparar el presupuesto contra el costo real acumulado.");
        salida.setWrapText(true);
        Button comparar = new Button("Comparar estimado y real");
        comparar.setOnAction(evento -> {
            try {
                var r = context.inventario().analizarDesviacionOrden(orden.getText().trim());
                salida.setText("Orden %s · Estimado $%s · Real $%s · Variación $%s (%s%%) · "
                        .formatted(r.codigo(), r.costoEstimado(), r.costoReal(), r.variacionAbsoluta(),
                                r.variacionPorcentual())
                        + "Índice de ejecución %s%% · Ahorro $%s · Sobrecosto $%s · Utilidad esperada $%s · "
                        .formatted(r.indiceEjecucion(), r.ahorroPresupuestario(), r.sobrecosto(),
                                r.utilidadEsperada())
                        + "Utilidad real $%s · Rentabilidad sobre venta %s%%\n%s"
                        .formatted(r.utilidadReal(), r.rentabilidadSobreVenta(), r.diagnostico()));
            } catch (Exception ex) {
                UiDialogs.error(ex.getMessage());
            }
        });
        return ResponsiveLayouts.sectionCard("Comparación de costos estimados y reales",
                "La estimación toma el presupuesto de la orden y la compara con el costo real registrado en las partidas.",
                ResponsiveLayouts.row(ResponsiveLayouts.field("Orden", orden), comparar), salida);
    }
}