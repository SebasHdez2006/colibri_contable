package sv.edu.ues.colibri.desktop.ui.views;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.application.configuracion.ParametrizacionService;
import sv.edu.ues.colibri.application.nomina.PlanillaService;
import sv.edu.ues.colibri.desktop.ui.ResponsiveLayouts;
import sv.edu.ues.colibri.desktop.ui.UiCatalogos;
import sv.edu.ues.colibri.desktop.ui.UiDialogs;
import sv.edu.ues.colibri.domain.model.PartidaDetalle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Devengo agregado de planilla con conciliación previa del asiento.
 *
 * <p>Las tasas laborales se ingresan por período porque pueden estar sujetas
 * a topes y normativa vigente. Las cargas patronales y las provisiones se
 * calculan por {@link PlanillaService}, no en la interfaz.</p>
 */
public final class PlanillaView extends VBox {

    private final AppContext context;
    private final DatePicker fecha;
    private final TextField empleados = campo("15", "Cantidad de trabajadores incluidos");
    private final TextField salario = campo("15500.00", "Planilla nominal agregada en USD");
    private final TextField tasaIsss = campo("0.03", "Ejemplo: 0.03 equivale a 3 %");
    private final TextField tasaPrevisional = campo("0.0725", "Ejemplo: 0.0725 equivale a 7.25 %");
    private final TextField isr = campo("0.00", "Retención de renta del período");
    private final TextField otrasDeducciones = campo("0.00", "Deducciones laborales adicionales");
    private final TextField otrasPrestaciones = campo("0.00", "Prestaciones contractuales adicionales");
    private ComboBox<ParametrizacionService.CentroCosto> centroCosto;
    private final Label resumen = new Label("Complete la cédula y previsualice el asiento antes de contabilizar.");
    private final TextArea asiento = new TextArea();

    public PlanillaView(AppContext context) {
        this.context = context;

        fecha = new DatePicker(context.periodos().periodoAbierto()
                .map(periodo -> periodo.getFechaFin()).orElse(LocalDate.now()));
        fecha.setAccessibleText("Fecha contable de la planilla");
        centroCosto = UiCatalogos.campoCentroCosto(context,
                "Centro de costo que recibe el gasto de planilla del período.");

        Label titulo = new Label("Planilla y cargas laborales");
        titulo.getStyleClass().add("view-title");
        Label ayuda = new Label("Registre el devengo agregado del período. Revise tasas, topes y retenciones "
                + "antes de confirmar; la aplicación conserva los importes y tasas utilizados para auditoría.");
        ayuda.setWrapText(true);
        ayuda.getStyleClass().add("view-subtitle");

        Button previsualizar = new Button("Previsualizar cálculo y asiento");
        previsualizar.setAccessibleHelp("Calcula planilla, cargas y partida sin modificar los libros.");
        previsualizar.getStyleClass().add("secondary-button");
        previsualizar.setOnAction(evento -> previsualizar());

        Button contabilizar = new Button("Contabilizar planilla");
        contabilizar.setAccessibleHelp("Registra una partida de ajuste única para el período abierto.");
        contabilizar.getStyleClass().add("primary-button");
        contabilizar.setOnAction(evento -> contabilizar());

        VBox datos = new VBox(10,
                encabezado("Datos del período"),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Fecha contable", fecha),
                        ResponsiveLayouts.field("Empleados", empleados),
                        ResponsiveLayouts.field("Salario nominal total (USD)", salario),
                        ResponsiveLayouts.field("Centro de costo", centroCosto)),
                encabezado("Retenciones laborales"),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Tasa ISSS laboral", tasaIsss),
                        ResponsiveLayouts.field("Tasa previsional laboral", tasaPrevisional),
                        ResponsiveLayouts.field("ISR retenido (USD)", isr),
                        ResponsiveLayouts.field("Otras deducciones (USD)", otrasDeducciones)),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Otras prestaciones (USD)", otrasPrestaciones),
                        previsualizar, contabilizar));
        datos.getStyleClass().add("form-card");

        resumen.setWrapText(true);
        resumen.setMaxWidth(Double.MAX_VALUE);
        resumen.setAccessibleText("Resumen calculado de planilla");
        resumen.getStyleClass().add("section-note");

        asiento.setEditable(false);
        asiento.setWrapText(true);
        asiento.setPromptText("La previsualización mostrará las líneas Debe y Haber.");
        asiento.setAccessibleText("Previsualización del asiento de planilla");
        asiento.setAccessibleHelp("Muestra la partida balanceada antes de contabilizar la planilla.");
        asiento.setPrefRowCount(7);
        VBox conciliacion = new VBox(10, encabezado("Conciliación previa"), resumen, asiento);
        conciliacion.getStyleClass().add("form-card");

        getChildren().addAll(titulo, ayuda, datos, conciliacion);
    }

    private void previsualizar() {
        try {
            PlanillaService.Solicitud solicitud = solicitud();
            PlanillaService.Resultado resultado = context.planilla().calcularConfigurado(solicitud);
            mostrarResultado(resultado, context.planilla().previsualizarAsientoConfigurado(solicitud), null);
        } catch (Exception ex) {
            UiDialogs.error(mensaje(ex));
        }
    }

    private void contabilizar() {
        try {
            PlanillaService.Solicitud solicitud = solicitud();
            PlanillaService.Resultado resultado = context.planilla().calcularConfigurado(solicitud);
            if (!UiDialogs.confirmar("Contabilizar planilla",
                    "Se registrará una partida única para el período seleccionado por $"
                            + resultado.costoTotalEmpresa() + ". ¿Desea continuar?")) {
                return;
            }
            PlanillaService.PlanillaRegistrada registrada = context.planilla().registrar(solicitud);
            mostrarResultado(registrada.resultado(), context.planilla().previsualizarAsientoConfigurado(solicitud),
                    registrada.partida().getFolio());
            UiDialogs.info("Planilla contabilizada", "Folio " + registrada.partida().getFolio()
                    + ". La planilla queda documentada para este período.");
        } catch (Exception ex) {
            UiDialogs.error(mensaje(ex));
        }
    }

    private PlanillaService.Solicitud solicitud() {
        return new PlanillaService.Solicitud(fecha.getValue(), entero(empleados, "empleados"),
                decimal(salario, "salario nominal"), decimal(tasaIsss, "tasa ISSS laboral"),
                decimal(tasaPrevisional, "tasa previsional laboral"), decimal(isr, "ISR retenido"),
                decimal(otrasDeducciones, "otras deducciones"), decimal(otrasPrestaciones, "otras prestaciones"),
                UiCatalogos.codigoCentroCosto(centroCosto, "GENERAL"));
    }

    private void mostrarResultado(PlanillaService.Resultado resultado, List<PartidaDetalle> lineas,
                                  String folio) {
        String estado = folio == null ? "Vista previa" : "Contabilizada · Folio " + folio;
        resumen.setText("%s · Líquido por pagar: $%s · Cargas patronales: $%s · "
                        + "Prestaciones provisionadas: $%s · Costo total empresa: $%s"
                .formatted(estado, resultado.liquidoPorPagar(), resultado.cargasPatronales(),
                        resultado.prestacionesProvisionadas(), resultado.costoTotalEmpresa()));
        asiento.setText(formatearLineas(lineas));
    }

    private static String formatearLineas(List<PartidaDetalle> lineas) {
        StringBuilder texto = new StringBuilder("Cuenta                 Debe          Haber\n");
        BigDecimal debe = BigDecimal.ZERO;
        BigDecimal haber = BigDecimal.ZERO;
        for (PartidaDetalle linea : lineas) {
            debe = debe.add(linea.getDebe());
            haber = haber.add(linea.getHaber());
            texto.append("%-18s %12s %12s%n".formatted(linea.getCodigoCuenta(),
                    importe(linea.getDebe()), importe(linea.getHaber())));
        }
        texto.append("%nTotales            %12s %12s".formatted(importe(debe), importe(haber)));
        return texto.toString();
    }

    private static String importe(BigDecimal valor) {
        return valor.signum() == 0 ? "" : "$" + valor;
    }

    private static TextField campo(String valorInicial, String ayuda) {
        TextField campo = new TextField(valorInicial);
        campo.setAccessibleHelp(ayuda);
        Tooltip.install(campo, new Tooltip(ayuda));
        return campo;
    }

    private static Label encabezado(String texto) {
        Label titulo = new Label(texto);
        titulo.getStyleClass().add("section-heading");
        return titulo;
    }

    private static BigDecimal decimal(TextField campo, String etiqueta) {
        String texto = campo.getText() == null ? "" : campo.getText().trim();
        if (texto.isEmpty()) {
            throw new IllegalArgumentException("Ingrese " + etiqueta + ".");
        }
        try {
            return new BigDecimal(texto);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("El valor de " + etiqueta + " debe ser numérico.");
        }
    }

    private static int entero(TextField campo, String etiqueta) {
        String texto = campo.getText() == null ? "" : campo.getText().trim();
        if (texto.isEmpty()) {
            throw new IllegalArgumentException("Ingrese " + etiqueta + ".");
        }
        try {
            return Integer.parseInt(texto);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("El valor de " + etiqueta + " debe ser un entero.");
        }
    }

    private static String mensaje(Exception ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank()
                ? "No se pudo procesar la planilla." : ex.getMessage();
    }
}
