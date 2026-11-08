package sv.edu.ues.colibri.desktop.ui.views;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.desktop.ui.BrandAssets;
import sv.edu.ues.colibri.domain.model.Partida;
import sv.edu.ues.colibri.domain.model.Periodo;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;

/** Resumen operativo: muestra los controles que requieren atención antes de registrar movimientos. */
public class InicioView extends VBox {

    public InicioView(AppContext context, Runnable irPartidas) {
        setSpacing(16);
        setPadding(new Insets(16));

        HBox hero = new HBox(BrandAssets.vistaImagotipo(300));
        hero.getStyleClass().add("brand-hero");
        hero.setAlignment(Pos.CENTER_LEFT);

        Label titulo = new Label("Resumen contable");
        titulo.getStyleClass().add("view-title");
        Label subtitulo = new Label("Revise el período, el catálogo y los borradores antes de contabilizar.");
        subtitulo.getStyleClass().add("view-subtitle");

        Periodo periodo = context.periodos().periodoAbierto().orElse(null);
        long cuentas = context.catalogo().contarCuentas();
        boolean catalogoAprobado = context.catalogo().catalogoAprobado();
        var borradores = context.partidas().listarBorradores();

        VBox tarjetaPeriodo = metrica("Período activo",
                periodo == null ? "Sin período abierto" : mes(periodo.getMes()) + " " + periodo.getFechaInicio().getYear(),
                periodo == null ? "Configure o reabra un período para capturar." : "Captura habilitada hasta " + periodo.getFechaFin(),
                periodo == null ? "warning-pill" : "status-pill");
        VBox tarjetaCatalogo = metrica("Catálogo de cuentas", String.valueOf(cuentas),
                catalogoAprobado ? "Aprobado para movimientos" : "Pendiente de aprobación",
                catalogoAprobado ? "status-pill" : "warning-pill");
        VBox tarjetaBorradores = metrica("Borradores pendientes", String.valueOf(borradores.size()),
                borradores.isEmpty() ? "Sin partidas por revisar" : "Requieren revisión y aprobación",
                borradores.isEmpty() ? "status-pill" : "warning-pill");
        HBox indicadores = new HBox(12, tarjetaPeriodo, tarjetaCatalogo, tarjetaBorradores);
        indicadores.getStyleClass().add("metrics-row");

        Button aprobarCatalogo = new Button("Aprobar catálogo inicial");
        aprobarCatalogo.setDisable(catalogoAprobado);
        aprobarCatalogo.setOnAction(e -> {
            try {
                context.catalogo().aprobarCatalogoInicial();
                aprobarCatalogo.setDisable(true);
                tarjetaCatalogo.getChildren().set(2, detalle("Aprobado para movimientos", "status-pill"));
            } catch (Exception ex) {
                sv.edu.ues.colibri.desktop.ui.UiDialogs.error(ex.getMessage());
            }
        });

        Label pendientesTitulo = new Label("Borradores por revisar");
        pendientesTitulo.getStyleClass().add("section-heading");
        ListView<String> lista = new ListView<>();
        lista.setPlaceholder(new Label("No hay borradores pendientes. El libro diario está al día."));
        for (Partida p : borradores) {
            lista.getItems().add("%s  ·  %s  ·  Debe $%s / Haber $%s".formatted(
                    p.getFechaContable(), p.getConcepto(), p.totalDebe(), p.totalHaber()));
        }
        lista.setMinHeight(180);
        lista.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(lista, Priority.ALWAYS);

        Button ir = new Button("Ir a registros");
        ir.setOnAction(e -> irPartidas.run());
        HBox acciones = new HBox(10, aprobarCatalogo, ir);
        acciones.getStyleClass().add("toolbar");

        VBox pendientes = new VBox(10, pendientesTitulo,
                new Label("Los borradores pueden estar descuadrados; una partida solo se contabiliza cuando Debe y Haber coinciden exactamente."),
                lista);
        pendientes.getStyleClass().add("surface-card");
        VBox.setVgrow(pendientes, Priority.ALWAYS);
        getChildren().addAll(hero, titulo, subtitulo, indicadores, acciones, pendientes);
    }

    private VBox metrica(String etiqueta, String valor, String detalle, String estiloDetalle) {
        Label label = new Label(etiqueta);
        label.getStyleClass().add("metric-label");
        Label value = new Label(valor);
        value.getStyleClass().add("metric-value");
        VBox card = new VBox(6, label, value, detalle(detalle, estiloDetalle));
        card.getStyleClass().add("metric-card");
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private Label detalle(String texto, String estilo) {
        Label label = new Label(texto);
        label.getStyleClass().add(estilo);
        return label;
    }

    private String mes(int numero) {
        String nombre = Month.of(numero).getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-SV"));
        return Character.toUpperCase(nombre.charAt(0)) + nombre.substring(1);
    }
}
