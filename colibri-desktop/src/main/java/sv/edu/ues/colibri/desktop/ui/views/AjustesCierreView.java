package sv.edu.ues.colibri.desktop.ui.views;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.desktop.ui.ResponsiveLayouts;
import sv.edu.ues.colibri.desktop.ui.UiCatalogos;
import sv.edu.ues.colibri.desktop.ui.UiDialogs;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ajustes de período y cierre contable: devengos, depreciación, amortización,
 * liquidación de IVA y cierre del período con sus controles.
 */
public class AjustesCierreView extends VBox {

    public AjustesCierreView(AppContext context) {
        setSpacing(14);
        setPadding(new Insets(16));

        Label titulo = new Label("Ajustes y cierre de período");
        titulo.getStyleClass().add("view-title");
        Label ayuda = new Label("Ajustes del mes y cierre del período. Cada ajuste genera su partida en el libro diario.");
        ayuda.getStyleClass().add("view-subtitle");
        ayuda.setWrapText(true);
        ayuda.setMinWidth(0);
        ayuda.setMaxWidth(Double.MAX_VALUE);

        DatePicker fecha = new DatePicker(LocalDate.now());
        fecha.setPrefWidth(240);
        fecha.setMinWidth(200);
        fecha.setTooltip(new Tooltip("Fecha contable; debe caer en el período abierto."));
        UiCatalogos.ayuda(fecha, "Fecha de todos los ajustes; debe caer en el período abierto.");

        TextField a1 = campoImporte("USD — suscripciones devengadas");
        TextField a2 = campoImporte("USD — consumo de nube");
        TextField a3 = campoImporte("USD — alquiler devengado");
        TextField a4 = campoImporte("USD — gastos pendientes");

        Button btnA1 = botonAjuste("Devengo de suscripciones",
                "Gasto del mes por suscripciones SaaS devengadas.",
                () -> context.cierre().ajusteDevengoSaas(new BigDecimal(a1.getText()), fecha.getValue()));
        Button btnA2 = botonAjuste("Consumo de nube",
                "Consumo de servicios en la nube del mes.",
                () -> context.cierre().ajusteConsumoNube(new BigDecimal(a2.getText()), fecha.getValue()));
        Button btnA3 = botonAjuste("Consumo de alquiler",
                "Alquiler devengado del mes.",
                () -> context.cierre().ajusteConsumoRenta(new BigDecimal(a3.getText()), fecha.getValue()));
        Button btnA4 = botonAjuste("Gasto devengado",
                "Gasto devengado pendiente de factura.",
                () -> context.cierre().ajusteGastoDevengado(new BigDecimal(a4.getText()), fecha.getValue()));

        Button btnDep = botonAjuste("Depreciar activos fijos",
                "Genera la cuota de depreciación del mes; no duplica.",
                () -> context.cierre().depreciarPeriodo(fecha.getValue()));
        btnDep.getStyleClass().add("primary-button");
        Button btnAmo = botonAjuste("Amortizar intangibles",
                "Genera la cuota de amortización del mes; no duplica.",
                () -> context.cierre().amortizarIntangiblesPeriodo(fecha.getValue()));

        Button btnIva = new Button("Liquidar IVA del período");
        btnIva.setTooltip(new Tooltip("Cruza débito y crédito fiscal del mes."));
        btnIva.setOnAction(e -> run(() -> {
            LocalDate ini = fecha.getValue().withDayOfMonth(1);
            return context.cierre().liquidarIva(ini, fecha.getValue());
        }));

        Button cerrarConAjustes = new Button("Cerrar período con ajustes automáticos");
        cerrarConAjustes.setTooltip(new Tooltip("Ejecuta los ajustes del mes y cierra el período."));
        cerrarConAjustes.getStyleClass().add("primary-button");
        cerrarConAjustes.setOnAction(e -> {
            if (!UiDialogs.confirmar("Cierre con ajustes",
                    "Se ejecutarán los ajustes automáticos del mes y luego se cerrará el período. ¿Continuar?")) {
                return;
            }
            try {
                String resumen = context.cierre().cerrarPeriodoConAjustes();
                UiDialogs.info("Cierre de período", resumen);
            } catch (Exception ex) {
                UiDialogs.error(ex.getMessage());
            }
        });

        Button cerrar = new Button("Cerrar período (sin ajustes)");
        cerrar.setTooltip(new Tooltip("Cierra sin ejecutar ajustes; úselo solo si ya se registraron."));
        cerrar.setOnAction(e -> {
            if (!UiDialogs.confirmar("Cierre", "¿Cerrar el período abierto? Bloqueará la captura.")) {
                return;
            }
            try {
                context.cierre().cerrarPeriodoActual();
                UiDialogs.info("Cierre", "Período cerrado correctamente.");
            } catch (Exception ex) {
                UiDialogs.error(ex.getMessage());
            }
        });

        // Cada devengo es una columna: etiqueta del importe, una nota breve y el
        // botón justo debajo del campo que le corresponde.
        javafx.scene.layout.GridPane manuales = new javafx.scene.layout.GridPane();
        manuales.setHgap(14);
        manuales.setVgap(10);
        manuales.getColumnConstraints().addAll(cuatroColumnas(), cuatroColumnas(),
                cuatroColumnas(), cuatroColumnas());
        agregarAjusteManual(manuales, 0, 0, "Suscripciones (USD)", a1,
                "Gasto del mes por suscripciones devengadas.", btnA1);
        agregarAjusteManual(manuales, 1, 0, "Consumo de nube (USD)", a2,
                "Consumo de nube del mes.", btnA2);
        agregarAjusteManual(manuales, 2, 0, "Consumo de alquiler (USD)", a3,
                "Alquiler devengado del mes.", btnA3);
        agregarAjusteManual(manuales, 3, 0, "Gastos pendientes (USD)", a4,
                "Gasto devengado sin factura.", btnA4);

        VBox ajustes = ResponsiveLayouts.sectionCard("Ajustes manuales del mes",
                "Devengos que no se generan automáticamente.", manuales);

        VBox automaticos = ResponsiveLayouts.sectionCard("Ajustes automáticos y cierre",
                filaAccion("Depreciación de activos fijos",
                        "Cuota de depreciación del mes; no duplica.", btnDep),
                filaAccion("Amortización de intangibles",
                        "Cuota de amortización del mes; no duplica.", btnAmo),
                filaAccion("Liquidación de IVA",
                        "Liquida débito y crédito fiscal del mes.", btnIva),
                filaAccion("Cierre con ajustes automáticos",
                        "Ejecuta los ajustes del mes y cierra.", cerrarConAjustes),
                filaAccion("Cierre sin ajustes",
                        "Cierra el período tal como está.", cerrar));

        VBox periodoCard = ResponsiveLayouts.sectionCard("Fecha del ajuste",
                "Fecha contable de todos los ajustes del mes.",
                ResponsiveLayouts.row(ResponsiveLayouts.field("Fecha contable", fecha)));

        getChildren().addAll(titulo, ayuda, periodoCard, ajustes, automaticos);
    }

    private static javafx.scene.layout.ColumnConstraints cuatroColumnas() {
        javafx.scene.layout.ColumnConstraints col = new javafx.scene.layout.ColumnConstraints();
        col.setPercentWidth(25);
        col.setHgrow(javafx.scene.layout.Priority.ALWAYS);
        return col;
    }

    private static void agregarAjusteManual(javafx.scene.layout.GridPane grid, int columna, int fila,
            String etiqueta, TextField campo, String explicacion, Button boton) {
        VBox celda = new VBox(6, ResponsiveLayouts.field(etiqueta, campo),
                nota(explicacion), boton);
        celda.setFillWidth(true);
        grid.add(celda, columna, fila);
    }

    private static VBox filaAccion(String tituloAccion, String explicacion, Button boton) {
        Label titulo = new Label(tituloAccion);
        titulo.getStyleClass().add("section-heading");
        VBox texto = new VBox(2, titulo, nota(explicacion));
        texto.setFillWidth(true);
        javafx.scene.layout.HBox fila = new javafx.scene.layout.HBox(14, texto, boton);
        fila.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox envoltura = new VBox(4, fila);
        envoltura.getStyleClass().add("form-card");
        envoltura.setPadding(new Insets(10, 12, 10, 12));
        return envoltura;
    }

    private static Label nota(String texto) {
        Label nota = new Label(texto);
        nota.setWrapText(true);
        nota.getStyleClass().add("section-note");
        return nota;
    }

    private TextField campoImporte(String prompt) {
        TextField campo = new TextField();
        campo.setPromptText(prompt);
        return campo;
    }

    private Button botonAjuste(String texto, String ayuda, java.util.concurrent.Callable<
            sv.edu.ues.colibri.domain.model.Partida> accion) {
        Button boton = new Button(texto);
        boton.setTooltip(new Tooltip(ayuda));
        boton.setOnAction(e -> run(accion));
        return boton;
    }

    private void run(java.util.concurrent.Callable<sv.edu.ues.colibri.domain.model.Partida> action) {
        try {
            var p = action.call();
            UiDialogs.info("Ajuste registrado", "Folio " + p.getFolio());
        } catch (Exception ex) {
            UiDialogs.error(ex.getMessage());
        }
    }
}
