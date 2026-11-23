package sv.edu.ues.colibri.desktop.ui.views;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
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
import javafx.util.StringConverter;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.application.operacion.OperacionContableService;
import sv.edu.ues.colibri.desktop.ui.ResponsiveLayouts;
import sv.edu.ues.colibri.desktop.ui.UiCampos;
import sv.edu.ues.colibri.desktop.ui.UiDialogs;
import sv.edu.ues.colibri.desktop.ui.UiTables;
import sv.edu.ues.colibri.domain.exception.DomainException;
import sv.edu.ues.colibri.domain.model.Documento;
import sv.edu.ues.colibri.domain.model.Partida;
import sv.edu.ues.colibri.domain.model.PartidaDetalle;
import sv.edu.ues.colibri.domain.model.Periodo;
import sv.edu.ues.colibri.domain.model.Tercero;
import sv.edu.ues.colibri.domain.model.TipoPartida;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Vista de terceros, documentos fiscales y registro de operaciones con IVA,
 * anticipos aplicables a DTEs, previsualización de asientos y retención de IVA.
 */
public class DocumentosView extends VBox {

    private static final String APLICAR_ANTICIPO = "Aplicar anticipo a DTE";

    private final AppContext context;
    private final TableView<Tercero> tablaTerceros = new TableView<>();
    private final TableView<Documento> tablaDocumentos = new TableView<>();
    private final Map<UUID, Tercero> tercerosPorId = new HashMap<>();

    private final ComboBox<String> plantilla = new ComboBox<>(FXCollections.observableArrayList(
            "Venta licencia", "Venta SaaS ingreso", "Compra", "Cobro", "Pago", "Anticipo SaaS",
            APLICAR_ANTICIPO));
    private final DatePicker fecha = new DatePicker();
    private final TextField importe = new TextField("1000");
    private final CheckBox incluido = new CheckBox("IVA incluido");
    private final CheckBox retencionCheck = new CheckBox("Retención de IVA sufrida");
    private final TextField retencion = new TextField();
    private final TextField cuenta = new TextField();
    private final ComboBox<OperacionContableService.DocumentoSaldo> comboAnticipo = new ComboBox<>();
    private final ComboBox<OperacionContableService.DocumentoSaldo> comboDte = new ComboBox<>();
    private final TextField importeAplicacion = new TextField();
    private final TextField nitField = new TextField();
    private final TextField razonField = new TextField();
    private final CheckBox cli = new CheckBox("Cliente");
    private final CheckBox prov = new CheckBox("Proveedor");
    private final CheckBox activoT = new CheckBox("Activo");
    private final Button guardarT = new Button("Guardar tercero");
    private final Button editarT = new Button("Editar seleccionado");
    private final Button nuevoT = new Button("Nuevo");
    private final Label modoTercero = new Label("Modo: nuevo tercero");
    private Tercero terceroEnEdicion = null;
    private final Label estadoPeriodo = new Label();

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
        Label ayuda = new Label("Registre la operación eligiendo la plantilla, revise el asiento con "
                + "«Previsualizar» y luego contabilice. Los anticipos pueden aplicarse a un DTE "
                + "concreto del mismo cliente.");
        ayuda.getStyleClass().add("view-subtitle");
        ayuda.setWrapText(true);

        Periodo abierto = context.periodos().periodoAbierto().orElse(null);
        fecha.setValue(abierto != null ? abierto.getFechaInicio() : LocalDate.now());
        estadoPeriodo.setText(abierto == null ? "Sin período abierto"
                : "Período: " + nombreMes(abierto) + " · " + etiquetaEstado(abierto));
        estadoPeriodo.getStyleClass().add(abierto != null
                && abierto.getEstado() == sv.edu.ues.colibri.domain.model.EstadoPeriodo.ABIERTO
                ? "status-pill" : "warning-pill");

        plantilla.setValue("Venta licencia");
        plantilla.setMaxWidth(Double.MAX_VALUE);
        cuenta.setPromptText("Opcional: código de cuenta que reemplaza la parametrizada");
        cuenta.setMaxWidth(Double.MAX_VALUE);
        retencion.setPromptText("Importe retenido (USD)");
        retencion.setPrefWidth(160);
        retencion.setDisable(true);
        importeAplicacion.setPromptText("Importe a aplicar");
        importeAplicacion.setPrefWidth(160);
        comboAnticipo.setConverter(converterDoc());
        comboDte.setConverter(converterDoc());
        comboAnticipo.setPromptText("Anticipo con saldo");
        comboDte.setPromptText("DTE destino");
        comboAnticipo.setMaxWidth(Double.MAX_VALUE);
        comboDte.setMaxWidth(Double.MAX_VALUE);
        comboAnticipo.setDisable(true);
        comboDte.setDisable(true);
        importeAplicacion.setDisable(true);

        plantilla.valueProperty().addListener((obs, antes, ahora) -> {
            boolean esCobro = "Cobro".equals(ahora);
            retencionCheck.setDisable(!esCobro);
            if (!esCobro) {
                retencionCheck.setSelected(false);
                retencion.setText("");
            }
            boolean esAnticipo = APLICAR_ANTICIPO.equals(ahora);
            comboAnticipo.setDisable(!esAnticipo);
            comboDte.setDisable(!esAnticipo);
            importeAplicacion.setDisable(!esAnticipo);
            if (esAnticipo) {
                cargarSaldos();
            } else {
                comboAnticipo.getItems().clear();
                comboDte.getItems().clear();
            }
        });
        tablaTerceros.getSelectionModel().selectedItemProperty().addListener((obs, antes, ahora) -> {
            if (APLICAR_ANTICIPO.equals(plantilla.getValue())) {
                cargarSaldos();
            }
        });
        retencionCheck.selectedProperty().addListener((obs, antes, ahora) -> {
            retencion.setDisable(!ahora);
            if (ahora) {
                sugerirRetencion();
            }
        });
        importe.textProperty().addListener((obs, antes, ahora) -> {
            if (retencionCheck.isSelected()) {
                sugerirRetencion();
            }
        });

        Button ejecutar = new Button("Registrar y contabilizar");
        ejecutar.setDefaultButton(true);
        ejecutar.setOnAction(e -> ejecutarOperacion());
        Button previsualizar = new Button("Previsualizar asiento");
        previsualizar.setOnAction(e -> previsualizarOperacion());

        VBox cardTercero = ResponsiveLayouts.sectionCard("Tercero de la operación",
                "Identifique al cliente o proveedor. Los combos de anticipo y DTE se "
                        + "filtran por el tercero seleccionado en la tabla.",
                ResponsiveLayouts.row(ResponsiveLayouts.field("NIT", nitField),
                        ResponsiveLayouts.field("Razón social", razonField), cli, prov, activoT,
                        editarT, nuevoT, guardarT),
                modoTercero);

        VBox cardOperacion = ResponsiveLayouts.sectionCard("Operación y documento",
                "Elija la plantilla, el importe y la fecha; use «Previsualizar» para ver las "
                        + "líneas Debe/Haber antes de contabilizar.",
                ResponsiveLayouts.row(ResponsiveLayouts.field("Plantilla", plantilla),
                        ResponsiveLayouts.field("Fecha", fecha), estadoPeriodo),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Importe (USD)", importe), incluido,
                        ResponsiveLayouts.field("Cuenta opcional", cuenta)),
                ResponsiveLayouts.row(retencionCheck, ResponsiveLayouts.field("Retención IVA", retencion)),
                ResponsiveLayouts.row(previsualizar, ejecutar));

        VBox cardAnticipo = ResponsiveLayouts.sectionCard("Aplicación de anticipo a DTE",
                "Disponible sólo con la plantilla «Aplicar anticipo a DTE»: descuenta el saldo "
                        + "del anticipo y lo traslada al documento de venta elegido.",
                ResponsiveLayouts.row(ResponsiveLayouts.field("Anticipo", comboAnticipo),
                        ResponsiveLayouts.field("DTE destino", comboDte),
                        ResponsiveLayouts.field("Importe aplicación", importeAplicacion)));

        TextField motivoNota = new TextField();
        motivoNota.setPromptText("Motivo de la nota (obligatorio)");
        Button notaCredito = new Button("Emitir nota de crédito");
        Button notaDebito = new Button("Emitir nota de débito");
        notaCredito.setOnAction(e -> emitirNota(false, motivoNota));
        notaDebito.setOnAction(e -> emitirNota(true, motivoNota));
        VBox cardNotas = ResponsiveLayouts.sectionCard("Notas de crédito y débito",
                "Seleccione un documento CONTABILIZADO en la tabla «Documentos» y emita la "
                        + "nota por el total del documento. La NC revierte el asiento; la ND "
                        + "lo duplica. La fecha de la nota es la del campo «Fecha».",
                ResponsiveLayouts.row(ResponsiveLayouts.field("Motivo", motivoNota),
                        notaCredito, notaDebito));

        configurarTablaTerceros();
        configurarTablaDocumentos();

        VBox panelTerceros = ResponsiveLayouts.sectionCard("Terceros",
                "Seleccione el cliente o proveedor de la operación. Los combos de anticipo y "
                        + "DTE se filtran por el tercero seleccionado.",
                UiTables.conBusqueda(tablaTerceros, "terceros", tercero -> tercero.getNit()
                        + " " + tercero.getRazonSocial() + " " + perfilTercero(tercero)));
        VBox panelDocumentos = ResponsiveLayouts.sectionCard("Documentos",
                "Documentos tributarios emitidos con su estado de contabilización.",
                UiTables.conBusqueda(tablaDocumentos, "documentos", documento ->
                        documento.getFechaEmision() + " " + documento.getTipo() + " "
                                + descripcionTercero(documento) + " " + descripcionCodigo(documento)
                                + " " + documento.getEstado()));
        VBox.setVgrow(tablaTerceros, Priority.ALWAYS);
        VBox.setVgrow(tablaDocumentos, Priority.ALWAYS);
        tablaTerceros.setPrefHeight(330);
        tablaDocumentos.setPrefHeight(330);

        // Cada listado ocupa una fila completa del ancho de la ventana.
        getChildren().addAll(titulo, ayuda, cardTercero, cardOperacion,
                cardAnticipo, cardNotas, panelTerceros, panelDocumentos);
        configurarTercero();
        refrescar();
    }

    private void configurarTercero() {
        nitField.setPromptText("NIT del tercero");
        razonField.setPromptText("Razón social");
        guardarT.setOnAction(e -> {
            try {
                Tercero t = terceroEnEdicion == null
                        ? new Tercero(UUID.randomUUID(), nitField.getText().trim(), null,
                                razonField.getText().trim(), null, cli.isSelected(), prov.isSelected(),
                                activoT.isSelected())
                        : new Tercero(terceroEnEdicion.getId(), nitField.getText().trim(),
                                terceroEnEdicion.getNrc(), razonField.getText().trim(),
                                terceroEnEdicion.getNombreComercial(), cli.isSelected(),
                                prov.isSelected(), activoT.isSelected());
                context.operaciones().guardarTercero(t);
                limpiarFormularioTercero();
                refrescar();
                UiDialogs.info("Tercero", "Guardado: " + t.getRazonSocial());
            } catch (Exception ex) {
                UiDialogs.errorDe(ex);
            }
        });
        editarT.setOnAction(e -> {
            Tercero sel = tablaTerceros.getSelectionModel().getSelectedItem();
            if (sel == null) {
                UiDialogs.error("Seleccione un tercero de la tabla para editarlo.");
                return;
            }
            terceroEnEdicion = sel;
            nitField.setText(sel.getNit());
            razonField.setText(sel.getRazonSocial());
            cli.setSelected(sel.isCliente());
            prov.setSelected(sel.isProveedor());
            activoT.setSelected(sel.isActivo());
            guardarT.setText("Actualizar tercero");
            modoTercero.setText("Editando: " + sel.getNit() + " · " + sel.getRazonSocial());
        });
        nuevoT.setOnAction(e -> limpiarFormularioTercero());
    }

    private void limpiarFormularioTercero() {
        terceroEnEdicion = null;
        nitField.clear();
        razonField.clear();
        cli.setSelected(false);
        prov.setSelected(false);
        activoT.setSelected(true);
        guardarT.setText("Guardar tercero");
        modoTercero.setText("Modo: nuevo tercero");
    }

    private void emitirNota(boolean debito, TextField motivoNota) {
        try {
            Documento sel = tablaDocumentos.getSelectionModel().getSelectedItem();
            if (sel == null) {
                throw new DomainException(
                        "Seleccione el documento a ajustar en la tabla «Documentos».");
            }
            Documento nota = context.operaciones().emitirNotaAjuste(
                    sel.getId(), fecha.getValue(), motivoNota.getText(), debito);
            UiDialogs.info("Nota emitida", (debito ? "Nota de débito " : "Nota de crédito ")
                    + nota.getCodigoGeneracion() + " · Total "
                    + nota.getTotal().toPlainString());
            motivoNota.clear();
            refrescar();
        } catch (Exception ex) {
            UiDialogs.errorDe(ex);
        }
    }

    private void ejecutarOperacion() {
        try {
            Partida p = aplicarAnticipo() ? aplicarAnticipoPartida() : registrarPartida();
            UiDialogs.info("Contabilizado", "Folio " + p.getFolio());
            refrescar();
        } catch (Exception ex) {
            UiDialogs.errorDe(ex);
        }
    }

    private void previsualizarOperacion() {
        try {
            OperacionContableService.OperacionPreview preview = aplicarAnticipo()
                    ? previewAnticipo() : previewPartida();
            UiDialogs.mostrar("Vista previa del asiento — " + preview.concepto(), tablaPreview(preview));
        } catch (Exception ex) {
            UiDialogs.errorDe(ex);
        }
    }

    private boolean aplicarAnticipo() {
        return APLICAR_ANTICIPO.equals(plantilla.getValue());
    }

    private Tercero terceroSeleccionado() {
        Tercero sel = tablaTerceros.getSelectionModel().getSelectedItem();
        if (sel == null) {
            throw new DomainException("Seleccione un tercero de la tabla.");
        }
        return sel;
    }

    private Partida registrarPartida() {
        Tercero sel = terceroSeleccionado();
        BigDecimal mon = UiCampos.importe(importe.getText());
        BigDecimal ret = retencionCobro(mon);
        String cuentaOverride = cuenta.getText().trim().isEmpty() ? null : cuenta.getText().trim();
        return switch (plantilla.getValue()) {
            case "Venta licencia", "Venta SaaS ingreso" -> context.operaciones().registrarVenta(
                    sel.getId(), fecha.getValue(), mon, incluido.isSelected(),
                    cuentaOverride, null, null);
            case "Compra" -> context.operaciones().registrarCompra(
                    sel.getId(), fecha.getValue(), mon, incluido.isSelected(), cuentaOverride, null);
            case "Cobro" -> context.operaciones().registrarCobro(
                    sel.getId(), fecha.getValue(), mon, null, ret, null);
            case "Pago" -> context.operaciones().registrarPago(
                    sel.getId(), fecha.getValue(), mon, null, null);
            case "Anticipo SaaS" -> context.operaciones().registrarAnticipoSaas(
                    sel.getId(), fecha.getValue(), mon, null);
            default -> throw new DomainException("Plantilla no soportada");
        };
    }

    private OperacionContableService.OperacionPreview previewPartida() {
        terceroSeleccionado();
        BigDecimal mon = UiCampos.importe(importe.getText());
        BigDecimal ret = retencionCobro(mon);
        String cuentaOverride = cuenta.getText().trim().isEmpty() ? null : cuenta.getText().trim();
        return switch (plantilla.getValue()) {
            case "Venta licencia" -> context.operaciones().previsualizarVenta(fecha.getValue(),
                    mon, incluido.isSelected(),
                    cuentaOverride != null ? cuentaOverride : "410602");
            case "Venta SaaS ingreso" -> context.operaciones().previsualizarVenta(fecha.getValue(),
                    mon, incluido.isSelected(),
                    cuentaOverride != null ? cuentaOverride : "410402");
            case "Compra" -> context.operaciones().previsualizarCompra(fecha.getValue(),
                    mon, incluido.isSelected(),
                    cuentaOverride != null ? cuentaOverride : "610102");
            case "Cobro" -> context.operaciones().previsualizarCobro(fecha.getValue(), mon, ret);
            case "Pago" -> context.operaciones().previsualizarPago(fecha.getValue(), mon);
            case "Anticipo SaaS" -> context.operaciones().previsualizarAnticipo(fecha.getValue(), mon);
            default -> throw new DomainException("Plantilla no soportada");
        };
    }

    private OperacionContableService.DocumentoSaldo anticipoSel() {
        OperacionContableService.DocumentoSaldo anticipo = comboAnticipo.getValue();
        OperacionContableService.DocumentoSaldo dte = comboDte.getValue();
        if (anticipo == null || dte == null) {
            throw new DomainException("Seleccione el anticipo y el DTE destino.");
        }
        return anticipo;
    }

    private Partida aplicarAnticipoPartida() {
        OperacionContableService.DocumentoSaldo anticipo = anticipoSel();
        BigDecimal monto = UiCampos.importe(importeAplicacion.getText());
        return context.operaciones().aplicarAnticipo(anticipo.documento().getId(),
                comboDte.getValue().documento().getId(), monto, fecha.getValue());
    }

    private OperacionContableService.OperacionPreview previewAnticipo() {
        OperacionContableService.DocumentoSaldo anticipo = anticipoSel();
        OperacionContableService.DocumentoSaldo dte = comboDte.getValue();
        BigDecimal monto = UiCampos.importe(importeAplicacion.getText());
        var anticipoPerfil = context.parametrizacion().exigirPerfilPosting("ANTICIPO");
        var ventaPerfil = context.parametrizacion().exigirPerfilPosting("VENTA");
        return new OperacionContableService.OperacionPreview(
                "Aplicación de anticipo a DTE " + dte.documento().getCodigoGeneracion(),
                TipoPartida.DI, fecha.getValue(), List.of(
                        PartidaDetalle.nueva(anticipoPerfil.cuentaHaber(), monto, BigDecimal.ZERO),
                        PartidaDetalle.nueva(ventaPerfil.cuentaDebe(), BigDecimal.ZERO, monto)));
    }

    private BigDecimal retencionCobro(BigDecimal mon) {
        if (!"Cobro".equals(plantilla.getValue()) || !retencionCheck.isSelected()) {
            return BigDecimal.ZERO;
        }
        String texto = retencion.getText().trim();
        if (texto.isEmpty()) {
            throw new DomainException("Ingrese el importe de retención de IVA o use el valor sugerido.");
        }
        return UiCampos.importe(texto);
    }

    private void sugerirRetencion() {
        try {
            BigDecimal mon = UiCampos.importeONull(importe.getText());
            if (mon == null) {
                return;
            }
            BigDecimal tasa = context.parametrizacion().parametrosFiscales().retencionIva();
            retencion.setText(mon.multiply(tasa).setScale(2, RoundingMode.HALF_UP).toPlainString());
        } catch (Exception ignored) {
        }
    }

    private void cargarSaldos() {
        Tercero sel = tablaTerceros.getSelectionModel().getSelectedItem();
        if (sel == null || !sel.isCliente()) {
            comboAnticipo.getItems().clear();
            comboDte.getItems().clear();
            return;
        }
        try {
            comboAnticipo.setItems(FXCollections.observableArrayList(
                    context.operaciones().anticiposConSaldo(sel.getId())));
            comboDte.setItems(FXCollections.observableArrayList(
                    context.operaciones().dtesConSaldo(sel.getId())));
        } catch (Exception ex) {
            comboAnticipo.getItems().clear();
            comboDte.getItems().clear();
            UiDialogs.errorDe(ex);
        }
    }

    private static StringConverter<OperacionContableService.DocumentoSaldo> converterDoc() {
        return new StringConverter<>() {
            @Override
            public String toString(OperacionContableService.DocumentoSaldo value) {
                if (value == null) {
                    return "";
                }
                Documento d = value.documento();
                return d.getCodigoGeneracion() + " | " + d.getFechaEmision()
                        + " | total " + d.getTotal() + " | saldo " + value.saldo();
            }

            @Override
            public OperacionContableService.DocumentoSaldo fromString(String value) {
                return null;
            }
        };
    }

    private static Node tablaPreview(OperacionContableService.OperacionPreview preview) {
        TableView<PartidaDetalle> tabla = new TableView<>(FXCollections.observableArrayList(preview.lineas()));
        agregarColumna(tabla, "Cuenta", PartidaDetalle::getCodigoCuenta);
        agregarColumna(tabla, "Auxiliar", PartidaDetalle::getAuxiliar);
        agregarColumna(tabla, "Centro", PartidaDetalle::getCentroCosto);
        agregarColumna(tabla, "Debe", d -> d.getDebe().toPlainString());
        agregarColumna(tabla, "Haber", d -> d.getHaber().toPlainString());
        tabla.setPrefSize(560, 260);

        BigDecimal totalDebe = preview.lineas().stream().map(PartidaDetalle::getDebe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalHaber = preview.lineas().stream().map(PartidaDetalle::getHaber)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        VBox contenido = new VBox(8,
                new Label("Tipo: " + preview.tipo().getEtiqueta()
                        + "  ·  Fecha: " + preview.fecha()
                        + "  ·  Debe: " + totalDebe + "  ·  Haber: " + totalHaber
                        + (totalDebe.compareTo(totalHaber) == 0 ? "  (cuadra)" : "  (NO cuadra)")),
                UiTables.conBusqueda(tabla, "líneas del asiento", linea -> linea.getCodigoCuenta()
                        + " " + linea.getAuxiliar() + " " + linea.getCentroCosto()
                        + " " + linea.getDebe() + " " + linea.getHaber()));
        contenido.setPadding(new Insets(10));
        return contenido;
    }

    private static <T> void agregarColumna(TableView<T> tabla, String titulo,
            java.util.function.Function<T, String> valor) {
        TableColumn<T, String> columna = new TableColumn<>(titulo);
        columna.setCellValueFactory(c -> new SimpleStringProperty(
                valor.apply(c.getValue()) == null ? "" : valor.apply(c.getValue())));
        tabla.getColumns().add(columna);
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
        tablaTerceros.setPlaceholder(new Label("No hay terceros registrados."));
        tablaTerceros.setMinWidth(460);
        tablaTerceros.setMinHeight(320);
        tablaTerceros.setMaxHeight(Double.MAX_VALUE);
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
        tablaDocumentos.setMinWidth(780);
        tablaDocumentos.setMinHeight(320);
        tablaDocumentos.setMaxHeight(Double.MAX_VALUE);
        cTercero.setPrefWidth(220);
        cCodigo.setPrefWidth(190);
    }

    private void refrescar() {
        var terceros = FXCollections.observableArrayList(context.operaciones().listarTerceros());
        UiTables.reemplazarFilas(tablaTerceros, terceros);
        tercerosPorId.clear();
        for (Tercero tercero : terceros) {
            tercerosPorId.put(tercero.getId(), tercero);
        }
        UiTables.reemplazarFilas(tablaDocumentos, context.operaciones().listarDocumentos());
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

    private static String nombreMes(Periodo periodo) {
        String nombre = java.time.Month.of(periodo.getMes())
                .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.forLanguageTag("es-SV"));
        return Character.toUpperCase(nombre.charAt(0)) + nombre.substring(1)
                + " " + periodo.getFechaInicio().getYear();
    }

    private static String etiquetaEstado(Periodo periodo) {
        return switch (periodo.getEstado()) {
            case ABIERTO -> "Abierto";
            case EN_REVISION -> "En revisión";
            case CERRADO -> "Cerrado";
        };
    }
}
