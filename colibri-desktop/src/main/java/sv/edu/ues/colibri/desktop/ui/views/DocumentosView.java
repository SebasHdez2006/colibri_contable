package sv.edu.ues.colibri.desktop.ui.views;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.desktop.ui.ResponsiveLayouts;
import sv.edu.ues.colibri.desktop.ui.UiDialogs;
import sv.edu.ues.colibri.domain.exception.DomainException;
import sv.edu.ues.colibri.domain.model.Documento;
import sv.edu.ues.colibri.domain.model.Partida;
import sv.edu.ues.colibri.domain.model.Tercero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Vista de terceros, documentos fiscales y registro de operaciones con IVA.
 */
public class DocumentosView extends VBox {

    private final AppContext context;
    private final TableView<Tercero> tablaTerceros = new TableView<>();
    private final TableView<Documento> tablaDocumentos = new TableView<>();
    private final Map<UUID, Tercero> tercerosPorId = new HashMap<>();

    /**
     * Crea la vista para administrar terceros, documentos y plantillas de operación.
     *
     * @param context contexto de aplicación con acceso a operaciones contables
     */
    public DocumentosView(AppContext context) {
        this.context = context;
        setSpacing(16);
        setPadding(new Insets(16));

        Label titulo = new Label("Documentos, terceros e IVA");
        titulo.getStyleClass().add("view-title");
        Label ayuda = new Label("Se muestra la información por bloques para que cada dato tenga jerarquía visual. Los terceros y documentos se presentan en tablas con código, nombre y estado.");
        ayuda.getStyleClass().add("view-subtitle");

        TextField nit = new TextField();
        nit.setPromptText("NIT");
        TextField razon = new TextField();
        razon.setPromptText("Razón social");
        CheckBox cli = new CheckBox("Cliente");
        CheckBox prov = new CheckBox("Proveedor");
        Button guardarT = new Button("Guardar tercero");
        guardarT.setOnAction(e -> {
            try {
                Tercero t = new Tercero(UUID.randomUUID(), nit.getText().trim(), null,
                        razon.getText().trim(), null, cli.isSelected(), prov.isSelected(), true);
                context.operaciones().guardarTercero(t);
                refrescar();
                UiDialogs.info("Tercero", "Guardado: " + t);
            } catch (Exception ex) {
                UiDialogs.error(ex.getMessage());
            }
        });

        DatePicker fecha = new DatePicker(context.periodos().periodoAbierto()
                .map(p -> p.getFechaInicio()).orElse(LocalDate.now()));
        TextField importe = new TextField("1000");
        CheckBox incluido = new CheckBox("IVA incluido");
        ComboBox<String> plantilla = new ComboBox<>(FXCollections.observableArrayList(
                "Venta licencia", "Venta SaaS ingreso", "Compra", "Cobro", "Pago", "Anticipo SaaS"));
        plantilla.setValue("Venta licencia");
        CheckBox aplicarRetencionIva = new CheckBox("Aplicar retención IVA (con comprobante)");
        aplicarRetencionIva.setDisable(true);
        plantilla.valueProperty().addListener((observable, anterior, actual) -> {
            boolean esCobro = "Cobro".equals(actual);
            aplicarRetencionIva.setDisable(!esCobro);
            if (!esCobro) {
                aplicarRetencionIva.setSelected(false);
            }
        });
        TextField cuenta = new TextField();
        cuenta.setPromptText("Opcional: reemplaza la cuenta parametrizada");

        Button ejecutar = new Button("Registrar y contabilizar");
        ejecutar.setOnAction(e -> {
            Tercero sel = tablaTerceros.getSelectionModel().getSelectedItem();
            if (sel == null) {
                UiDialogs.error("Seleccione un tercero");
                return;
            }
            try {
                BigDecimal mon = new BigDecimal(importe.getText().trim());
                String cuentaOverride = cuenta.getText().trim().isEmpty() ? null : cuenta.getText().trim();
                Partida p = switch (plantilla.getValue()) {
                    case "Venta licencia" -> context.operaciones().registrarVenta(
                            sel.getId(), fecha.getValue(), mon, incluido.isSelected(),
                            cuentaOverride, null, null);
                    case "Venta SaaS ingreso" -> context.operaciones().registrarVenta(
                            sel.getId(), fecha.getValue(), mon, incluido.isSelected(),
                            cuentaOverride, null, null);
                    case "Compra" -> context.operaciones().registrarCompra(
                            sel.getId(), fecha.getValue(), mon, incluido.isSelected(),
                            cuentaOverride, null);
                    case "Cobro" -> context.operaciones().registrarCobro(
                            sel.getId(), fecha.getValue(), mon, null,
                            aplicarRetencionIva.isSelected()
                                    ? context.parametrizacion().parametrosFiscales().retencionIva()
                                    : BigDecimal.ZERO,
                            null);
                    case "Pago" -> context.operaciones().registrarPago(
                            sel.getId(), fecha.getValue(), mon, null, null);
                    case "Anticipo SaaS" -> context.operaciones().registrarAnticipoSaas(
                            sel.getId(), fecha.getValue(), mon, null);
                    default -> throw new DomainException("Plantilla no soportada");
                };
                UiDialogs.info("Contabilizado", "Folio " + p.getFolio());
                refrescar();
            } catch (Exception ex) {
                UiDialogs.error(ex.getMessage());
            }
        });

        VBox form = ResponsiveLayouts.sectionCard("Captura de tercero y documento",
                "Cada grupo conserva su propio bloque para mejorar la lectura y evitar que el texto quede sobre fondos grises.",
                ResponsiveLayouts.row(ResponsiveLayouts.field("NIT", nit),
                        ResponsiveLayouts.field("Razón social", razon), cli, prov, guardarT),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Plantilla", plantilla),
                        ResponsiveLayouts.field("Fecha", fecha)),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Importe (USD)", importe), incluido,
                        aplicarRetencionIva, ResponsiveLayouts.field("Cuenta opcional de ingreso o gasto", cuenta), ejecutar));

        configurarTablaTerceros();
        configurarTablaDocumentos();

        VBox panelTerceros = ResponsiveLayouts.sectionCard("Terceros",
                "Se listan con NIT, razón social, perfil y estado para identificar rápidamente al registro correcto.",
                tablaTerceros);
        VBox panelDocumentos = ResponsiveLayouts.sectionCard("Documentos",
                "La información queda resumida por fecha, tipo, tercero, código de generación y totales.",
                tablaDocumentos);
        VBox.setVgrow(tablaTerceros, Priority.ALWAYS);
        VBox.setVgrow(tablaDocumentos, Priority.ALWAYS);

        var listas = ResponsiveLayouts.row(panelTerceros, panelDocumentos);
        getChildren().addAll(titulo, ayuda, form, listas);
        refrescar();
    }

    private void configurarTablaTerceros() {
        TableColumn<Tercero, String> cNit = new TableColumn<>("NIT");
        cNit.setCellValueFactory(c -> new SimpleStringProperty(nvl(c.getValue().getNit())));
        TableColumn<Tercero, String> cRazon = new TableColumn<>("Razón social");
        cRazon.setCellValueFactory(c -> new SimpleStringProperty(nvl(c.getValue().getRazonSocial())));
        TableColumn<Tercero, String> cPerfil = new TableColumn<>("Perfil");
        cPerfil.setCellValueFactory(c -> new SimpleStringProperty(perfilTercero(c.getValue())));
        TableColumn<Tercero, String> cEstado = new TableColumn<>("Estado");
        cEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isActivo() ? "Activo" : "Inactivo"));
        tablaTerceros.getColumns().setAll(cNit, cRazon, cPerfil, cEstado);
        tablaTerceros.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        tablaTerceros.setPlaceholder(new Label("No hay terceros registrados."));
        tablaTerceros.setMinHeight(220);
    }

    private void configurarTablaDocumentos() {
        TableColumn<Documento, String> cFecha = new TableColumn<>("Fecha");
        cFecha.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFechaEmision().toString()));
        TableColumn<Documento, String> cTipo = new TableColumn<>("Tipo");
        cTipo.setCellValueFactory(c -> new SimpleStringProperty(nvl(c.getValue().getTipo())));
        TableColumn<Documento, String> cTercero = new TableColumn<>("Tercero");
        cTercero.setCellValueFactory(c -> new SimpleStringProperty(descripcionTercero(c.getValue())));
        TableColumn<Documento, String> cCodigo = new TableColumn<>("Código / control");
        cCodigo.setCellValueFactory(c -> new SimpleStringProperty(descripcionCodigo(c.getValue())));
        TableColumn<Documento, String> cBase = new TableColumn<>("Base");
        cBase.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getBaseGravada().toPlainString()));
        TableColumn<Documento, String> cIva = new TableColumn<>("IVA");
        cIva.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getIva().toPlainString()));
        TableColumn<Documento, String> cTotal = new TableColumn<>("Total");
        cTotal.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTotal().toPlainString()));
        TableColumn<Documento, String> cEstado = new TableColumn<>("Estado");
        cEstado.setCellValueFactory(c -> new SimpleStringProperty(nvl(c.getValue().getEstado())));
        tablaDocumentos.getColumns().setAll(cFecha, cTipo, cTercero, cCodigo, cBase, cIva, cTotal, cEstado);
        tablaDocumentos.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        tablaDocumentos.setPlaceholder(new Label("No hay documentos registrados."));
        tablaDocumentos.setMinHeight(220);
    }

    private void refrescar() {
        var terceros = FXCollections.observableArrayList(context.operaciones().listarTerceros());
        tablaTerceros.setItems(terceros);
        tercerosPorId.clear();
        for (Tercero tercero : terceros) {
            tercerosPorId.put(tercero.getId(), tercero);
        }
        tablaDocumentos.setItems(FXCollections.observableArrayList(context.operaciones().listarDocumentos()));
    }

    private String descripcionTercero(Documento documento) {
        if (documento.getTerceroId() != null) {
            Tercero tercero = tercerosPorId.get(documento.getTerceroId());
            if (tercero != null) {
                return tercero.getNit() + " · " + tercero.getRazonSocial();
            }
        }
        if (documento.getReceptorNit() != null && !documento.getReceptorNit().isBlank()) {
            return documento.getReceptorNit();
        }
        return "(sin tercero)";
    }

    private String descripcionCodigo(Documento documento) {
        if (documento.getNumeroControl() != null && !documento.getNumeroControl().isBlank()) {
            return documento.getNumeroControl() + " · " + nvl(documento.getCodigoGeneracion());
        }
        return nvl(documento.getCodigoGeneracion());
    }

    private String perfilTercero(Tercero tercero) {
        if (tercero.isCliente() && tercero.isProveedor()) {
            return "Cliente y proveedor";
        }
        if (tercero.isCliente()) {
            return "Cliente";
        }
        if (tercero.isProveedor()) {
            return "Proveedor";
        }
        return "Sin perfil";
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }
}
