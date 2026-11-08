package sv.edu.ues.colibri.desktop.ui.views;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.desktop.ui.ResponsiveLayouts;
import sv.edu.ues.colibri.desktop.ui.UiDialogs;

import java.math.BigDecimal;
import java.time.LocalDate;

public class AjustesCierreView extends VBox {

    public AjustesCierreView(AppContext context) {
        setSpacing(10);
        setPadding(new Insets(16));

        Label titulo = new Label("Ajustes y cierre");
        titulo.getStyleClass().add("view-title");

        DatePicker fecha = new DatePicker(LocalDate.now());
        TextField a1 = new TextField();
        TextField a2 = new TextField();
        TextField a3 = new TextField();
        TextField a4 = new TextField();
        a1.setPromptText("Importe en USD");
        a2.setPromptText("Importe en USD");
        a3.setPromptText("Importe en USD");
        a4.setPromptText("Importe en USD");

        Button btnA1 = new Button("Registrar devengo de suscripción");
        btnA1.setOnAction(e -> run(() -> context.cierre().ajusteDevengoSaas(
                new BigDecimal(a1.getText()), fecha.getValue())));
        Button btnA2 = new Button("Registrar consumo de nube");
        btnA2.setOnAction(e -> run(() -> context.cierre().ajusteConsumoNube(
                new BigDecimal(a2.getText()), fecha.getValue())));
        Button btnA3 = new Button("Registrar consumo de alquiler");
        btnA3.setOnAction(e -> run(() -> context.cierre().ajusteConsumoRenta(
                new BigDecimal(a3.getText()), fecha.getValue())));
        Button btnA4 = new Button("Registrar gasto devengado");
        btnA4.setOnAction(e -> run(() -> context.cierre().ajusteGastoDevengado(
                new BigDecimal(a4.getText()), fecha.getValue())));
        Button btnA5 = new Button("Liquidar IVA del período");
        btnA5.setOnAction(e -> run(() -> {
            LocalDate ini = fecha.getValue().withDayOfMonth(1);
            return context.cierre().liquidarIva(ini, fecha.getValue());
        }));
        Button cerrar = new Button("Cerrar período abierto");
        cerrar.setOnAction(e -> {
            if (!UiDialogs.confirmar("Cierre", "¿Cerrar el período abierto? Bloqueará captura.")) {
                return;
            }
            try {
                context.cierre().cerrarPeriodoActual();
                UiDialogs.info("Cierre", "Período cerrado");
            } catch (Exception ex) {
                UiDialogs.error(ex.getMessage());
            }
        });

        VBox ajustes = new VBox(8,
            ResponsiveLayouts.row(ResponsiveLayouts.field("Suscripciones devengadas", a1), btnA1),
            ResponsiveLayouts.row(ResponsiveLayouts.field("Consumo de nube", a2), btnA2),
            ResponsiveLayouts.row(ResponsiveLayouts.field("Consumo de alquiler", a3), btnA3),
            ResponsiveLayouts.row(ResponsiveLayouts.field("Gastos pendientes", a4), btnA4));

        getChildren().addAll(titulo, new Label("Fecha contable"), fecha, ajustes, btnA5, cerrar);
    }

    private void run(java.util.concurrent.Callable<sv.edu.ues.colibri.domain.model.Partida> action) {
        try {
            var p = action.call();
            UiDialogs.info("Ajuste", "Folio " + p.getFolio());
        } catch (Exception ex) {
            UiDialogs.error(ex.getMessage());
        }
    }
}
