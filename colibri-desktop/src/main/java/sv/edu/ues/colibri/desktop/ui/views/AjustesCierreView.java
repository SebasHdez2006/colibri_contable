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
        Label ayuda = new Label("Ejecute los ajustes del mes antes de cerrar: devengos, depreciación "
                + "de activos fijos, amortización de intangibles y liquidación de IVA. Cada ajuste "
                + "genera su partida en el libro diario y puede revisarse antes de contabilizar "
                + "definitivamente el cierre.");
        ayuda.getStyleClass().add("view-subtitle");
        ayuda.setWrapText(true);

        DatePicker fecha = new DatePicker(LocalDate.now());
        fecha.setPrefWidth(240);
        fecha.setMinWidth(200);
        fecha.setTooltip(new Tooltip("Fecha contable de los ajustes. Debe caer dentro del período abierto."));
        UiCatalogos.ayuda(fecha, "Todos los ajustes y la liquidación de IVA usan esta fecha; debe pertenecer al período abierto.");

        TextField a1 = campoImporte("Ej.: 1500.00 — suscripciones devengadas del mes");
        TextField a2 = campoImporte("Ej.: 320.50 — consumo de nube del mes");
        TextField a3 = campoImporte("Ej.: 800.00 — alquiler devengado");
        TextField a4 = campoImporte("Ej.: 450.00 — gastos pendientes de registrar");

        Button btnA1 = botonAjuste("Devengo de suscripciones",
                "Registra como gasto del período las suscripciones SaaS devengadas.",
                () -> context.cierre().ajusteDevengoSaas(new BigDecimal(a1.getText()), fecha.getValue()));
        Button btnA2 = botonAjuste("Consumo de nube",
                "Registra el consumo de servicios en la nube del período.",
                () -> context.cierre().ajusteConsumoNube(new BigDecimal(a2.getText()), fecha.getValue()));
        Button btnA3 = botonAjuste("Consumo de alquiler",
                "Registra el alquiler devengado del período.",
                () -> context.cierre().ajusteConsumoRenta(new BigDecimal(a3.getText()), fecha.getValue()));
        Button btnA4 = botonAjuste("Gasto devengado",
                "Registra un gasto devengado pendiente de factura.",
                () -> context.cierre().ajusteGastoDevengado(new BigDecimal(a4.getText()), fecha.getValue()));

        Button btnDep = botonAjuste("Depreciar activos fijos",
                "Recorre los activos fijos elegibles y genera la partida de depreciación "
                        + "del período. Es idempotente: no duplica si ya existe.",
                () -> context.cierre().depreciarPeriodo(fecha.getValue()));
        btnDep.getStyleClass().add("primary-button");
        Button btnAmo = botonAjuste("Amortizar intangibles",
                "Recorre los activos intangibles y genera la partida de amortización "
                        + "del período. Es idempotente.",
                () -> context.cierre().amortizarIntangiblesPeriodo(fecha.getValue()));

        Button btnIva = new Button("Liquidar IVA del período");
        btnIva.setTooltip(new Tooltip("Cruza débito y crédito fiscal del mes y genera la partida "
                + "de liquidación de IVA."));
        btnIva.setOnAction(e -> run(() -> {
            LocalDate ini = fecha.getValue().withDayOfMonth(1);
            return context.cierre().liquidarIva(ini, fecha.getValue());
        }));

        Button cerrarConAjustes = new Button("Cerrar período con ajustes automáticos");
        cerrarConAjustes.setTooltip(new Tooltip("Ejecuta depreciación, amortización y liquidación de "
                + "IVA del mes y después cierra el período. Muestra un resumen de lo generado."));
        cerrarConAjustes.getStyleClass().add("primary-button");
        cerrarConAjustes.setOnAction(e -> {
            if (!UiDialogs.confirmar("Cierre con ajustes",
                    "Se ejecutarán los ajustes automáticos del mes (depreciación, amortización e "
                            + "IVA) y luego se cerrará el período, bloqueando la captura. ¿Continuar?")) {
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
        cerrar.setTooltip(new Tooltip("Cierra el período sin ejecutar ajustes automáticos. "
                + "Use solo si los ajustes ya fueron registrados manualmente."));
        cerrar.setOnAction(e -> {
            if (!UiDialogs.confirmar("Cierre", "¿Cerrar el período abierto? Bloqueará la captura de "
                    + "nuevas operaciones en ese mes.")) {
                return;
            }
            try {
                context.cierre().cerrarPeriodoActual();
                UiDialogs.info("Cierre", "Período cerrado correctamente.");
            } catch (Exception ex) {
                UiDialogs.error(ex.getMessage());
            }
        });

        VBox ajustes = ResponsiveLayouts.sectionCard("Ajustes manuales del mes",
                "Devengos y gastos que no se generan automáticamente.",
                ResponsiveLayouts.row(ResponsiveLayouts.field("Suscripciones devengadas (USD)", a1), btnA1),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Consumo de nube (USD)", a2), btnA2),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Consumo de alquiler (USD)", a3), btnA3),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Gastos pendientes (USD)", a4), btnA4));

        VBox automaticos = ResponsiveLayouts.sectionCard("Ajustes automáticos y cierre",
                "Depreciación y amortización recorren los activos registrados; la liquidación de "
                        + "IVA cruza débito y crédito fiscal del mes.",
                ResponsiveLayouts.row(btnDep, btnAmo, btnIva),
                ResponsiveLayouts.row(cerrarConAjustes, cerrar));

        VBox periodoCard = ResponsiveLayouts.sectionCard("Fecha del ajuste",
                "Los devengos, la depreciación, la amortización y la liquidación de IVA se "
                        + "registran con esta fecha contable.",
                ResponsiveLayouts.row(ResponsiveLayouts.field("Fecha contable", fecha)));

        getChildren().addAll(titulo, ayuda, periodoCard, ajustes, automaticos);
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
