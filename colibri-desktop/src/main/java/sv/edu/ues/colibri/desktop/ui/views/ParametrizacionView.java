package sv.edu.ues.colibri.desktop.ui.views;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.application.configuracion.ParametrizacionService;
import sv.edu.ues.colibri.desktop.ui.ResponsiveLayouts;
import sv.edu.ues.colibri.desktop.ui.UiDialogs;
import sv.edu.ues.colibri.domain.model.EstadoCuenta;
import sv.edu.ues.colibri.domain.model.NaturalezaCuenta;

import java.math.BigDecimal;

/**
 * Vista de configuración contable y fiscal del sistema.
 */
public class ParametrizacionView extends VBox {

    /**
     * Crea la pantalla de parametrización con pestañas de configuración.
     *
     * @param context contexto de aplicación con acceso a los servicios de configuración
     */
    public ParametrizacionView(AppContext context) {
        setSpacing(16);
        setPadding(new Insets(16));
        Label titulo = new Label("Parametrización contable y fiscal");
        titulo.getStyleClass().add("view-title");
        Label ayuda = new Label("CU-PAR-01 a CU-PAR-06. Configure catálogo, posting, alícuotas, correlativos y centros de costo antes de capturar.");
        ayuda.getStyleClass().add("view-subtitle");

        TabPane tabs = new TabPane(
                tab("Catálogo de cuentas", catalogo(context)),
                tab("Mapeo de módulos", posting(context)),
                tab("Períodos contables", periodos(context)),
                tab("Parámetros fiscales", fiscal(context)),
                tab("Políticas y secuenciales", politicas(context)),
                tab("Centros de costo", centros(context))
        );
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabs, javafx.scene.layout.Priority.ALWAYS);
        getChildren().addAll(titulo, ayuda, tabs);
    }

    private Tab tab(String titulo, javafx.scene.Node content) {
        Tab tab = new Tab(titulo, content);
        tab.setClosable(false);
        return tab;
    }

    private VBox periodos(AppContext context) {
        TableView<sv.edu.ues.colibri.domain.model.Periodo> tabla = new TableView<>();
        addCol(tabla, "Mes", p -> String.valueOf(p.getMes()));
        addCol(tabla, "Inicio", p -> p.getFechaInicio().toString());
        addCol(tabla, "Fin", p -> p.getFechaFin().toString());
        addCol(tabla, "Estado", p -> switch (p.getEstado()) {
            case ABIERTO -> "Abierto";
            case EN_REVISION -> "En auditoría";
            case CERRADO -> "Cerrado";
        });
        tabla.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        Runnable refrescar = () -> {
            tabla.getItems().clear();
            context.periodos().listarEjercicios()
                    .forEach(ej -> tabla.getItems().addAll(context.periodos().listarPeriodos(ej)));
        };
        refrescar.run();
        Button abierto = new Button("Abrir / reabrir");
        Button auditoria = new Button("Poner en auditoría");
        Button cerrado = new Button("Cerrar período");
        abierto.setOnAction(e -> {
            var sel = tabla.getSelectionModel().getSelectedItem();
            if (sel != null) run(() -> { context.periodos().reabrir(sel); refrescar.run(); });
        });
        auditoria.setOnAction(e -> {
            var sel = tabla.getSelectionModel().getSelectedItem();
            if (sel != null) run(() -> { context.periodos().ponerEnRevision(sel); refrescar.run(); });
        });
        cerrado.setOnAction(e -> {
            var sel = tabla.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            if (UiDialogs.confirmar("Cerrar período", "¿Cerrar el mes " + sel.getMes() + "?")) {
                run(() -> { context.periodos().marcarCerrado(sel); refrescar.run(); });
            }
        });
        VBox box = new VBox(10, new Label("Abierto permite captura. En auditoría deja ajustes. Cerrado bloquea el mes."),
                tabla, ResponsiveLayouts.row(abierto, auditoria, cerrado));
        box.getStyleClass().add("form-card");
        VBox.setVgrow(tabla, javafx.scene.layout.Priority.ALWAYS);
        return box;
    }

    private VBox catalogo(AppContext context) {
        TableView<sv.edu.ues.colibri.domain.model.Cuenta> tabla = new TableView<>();
        addCol(tabla, "Código", c -> c.getCodigo());
        addCol(tabla, "Nombre", c -> c.getNombre());
        addCol(tabla, "Naturaleza", c -> switch (c.getNaturaleza()) {
            case DEUDORA -> "Deudora";
            case ACREEDORA -> "Acreedora";
            case AMBAS -> "Ambas";
        });
        addCol(tabla, "Afectable", c -> c.isAdmiteMovimiento() ? "Sí" : "No");
        addCol(tabla, "Estado", c -> c.getEstado().name());
        tabla.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        Runnable refrescar = () -> tabla.setItems(FXCollections.observableArrayList(context.catalogo().listar()));
        refrescar.run();

        TextField filtro = new TextField();
        filtro.setPromptText("Filtrar código o nombre");
        filtro.textProperty().addListener((o, a, b) -> {
            String t = b == null ? "" : b.toLowerCase();
            tabla.setItems(FXCollections.observableArrayList(
                    context.catalogo().listar().stream()
                            .filter(c -> c.getCodigo().toLowerCase().contains(t)
                                    || c.getNombre().toLowerCase().contains(t))
                            .toList()));
        });

        TextField codigo = new TextField();
        TextField nombre = new TextField();
        TextField padre = new TextField();
        padre.setPromptText("Cuenta de 4 dígitos");
        ComboBox<NaturalezaCuenta> nat = new ComboBox<>(FXCollections.observableArrayList(NaturalezaCuenta.values()));
        nat.setValue(NaturalezaCuenta.DEUDORA);
        Button crear = new Button("Crear subcuenta");
        crear.setOnAction(e -> run(() -> {
            context.catalogo().crearSubcuenta(codigo.getText().trim(), nombre.getText().trim(),
                    padre.getText().trim(), nat.getValue(), "GASTOS");
            refrescar.run();
        }));
        Button editar = new Button("Editar denominación");
        editar.setOnAction(e -> {
            var sel = tabla.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            String nuevo = UiDialogs.pedirTexto("Denominación", "Nuevo nombre para " + sel.getCodigo());
            if (nuevo != null) {
                run(() -> {
                    context.catalogo().actualizarDenominacion(sel.getCodigo(), nuevo.trim());
                    refrescar.run();
                });
            }
        });
        ComboBox<EstadoCuenta> estado = new ComboBox<>(FXCollections.observableArrayList(EstadoCuenta.values()));
        Button cambiar = new Button("Cambiar estado");
        cambiar.setOnAction(e -> {
            var sel = tabla.getSelectionModel().getSelectedItem();
            if (sel == null || estado.getValue() == null) return;
            run(() -> {
                context.catalogo().cambiarEstado(sel.getCodigo(), estado.getValue());
                refrescar.run();
            });
        });
        VBox box = new VBox(10, filtro, tabla,
                ResponsiveLayouts.row(ResponsiveLayouts.field("Código 6 dígitos", codigo),
                        ResponsiveLayouts.field("Nombre", nombre),
                        ResponsiveLayouts.field("Padre 4 dígitos", padre), nat, crear),
                ResponsiveLayouts.row(editar, estado, cambiar));
        VBox.setVgrow(tabla, javafx.scene.layout.Priority.ALWAYS);
        box.getStyleClass().add("form-card");
        return box;
    }

    private VBox posting(AppContext context) {
        TableView<ParametrizacionService.PostingProfile> tabla = new TableView<>();
        addCol(tabla, "Evento", ParametrizacionService.PostingProfile::evento);
        addCol(tabla, "Etiqueta", ParametrizacionService.PostingProfile::etiqueta);
        addCol(tabla, "Debe", ParametrizacionService.PostingProfile::cuentaDebe);
        addCol(tabla, "Haber", ParametrizacionService.PostingProfile::cuentaHaber);
        addCol(tabla, "IVA", p -> nvl(p.cuentaIva()));
        tabla.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        tabla.setItems(FXCollections.observableArrayList(context.parametrizacion().listarPosting()));
        TextField debe = new TextField();
        TextField haber = new TextField();
        TextField iva = new TextField();
        Button guardar = new Button("Guardar mapeo seleccionado");
        guardar.setOnAction(e -> {
            var sel = tabla.getSelectionModel().getSelectedItem();
            if (sel == null) {
                UiDialogs.error("Seleccione un evento operativo.");
                return;
            }
            run(() -> {
                context.parametrizacion().guardarPosting(new ParametrizacionService.PostingProfile(
                        sel.evento(), sel.etiqueta(),
                        debe.getText().isBlank() ? sel.cuentaDebe() : debe.getText().trim(),
                        haber.getText().isBlank() ? sel.cuentaHaber() : haber.getText().trim(),
                        iva.getText().isBlank() ? sel.cuentaIva() : iva.getText().trim()));
                tabla.setItems(FXCollections.observableArrayList(context.parametrizacion().listarPosting()));
                UiDialogs.info("Posting", "Cuentas automáticas actualizadas.");
            });
        });
        tabla.getSelectionModel().selectedItemProperty().addListener((o, a, sel) -> {
            if (sel != null) {
                debe.setText(sel.cuentaDebe());
                haber.setText(sel.cuentaHaber());
                iva.setText(nvl(sel.cuentaIva()));
            }
        });
        VBox box = new VBox(10, new Label("Cuentas por defecto para ventas, compras, inventario, depreciación y planilla."),
                tabla, ResponsiveLayouts.row(ResponsiveLayouts.field("Debe", debe),
                        ResponsiveLayouts.field("Haber", haber), ResponsiveLayouts.field("IVA", iva), guardar));
        box.getStyleClass().add("form-card");
        VBox.setVgrow(tabla, javafx.scene.layout.Priority.ALWAYS);
        return box;
    }

    private VBox fiscal(AppContext context) {
        var p = context.parametrizacion().parametrosFiscales();
        TextField iva = new TextField(p.iva().toPlainString());
        TextField retIva = new TextField(p.retencionIva().toPlainString());
        TextField pago = new TextField(p.pagoCuenta().toPlainString());
        TextField isrServ = new TextField(p.retencionIsrServicios().toPlainString());
        TextField isrInt = new TextField(p.retencionIsrIntangibles().toPlainString());
        TextField cCred = new TextField(p.cuentaCredito());
        TextField cDeb = new TextField(p.cuentaDebito());
        TextField cPagar = new TextField(p.cuentaPorPagar());
        TextField cRet = new TextField(p.cuentaRetencionIva());
        TextField cPago = new TextField(p.cuentaPagoCuenta());
        TextField mh = new TextField(p.catalogoMh());
        Button guardar = new Button("Guardar parámetros fiscales");
        guardar.setOnAction(e -> run(() -> {
            context.parametrizacion().guardarFiscal(new ParametrizacionService.ParametroFiscal(
                    new BigDecimal(iva.getText()), new BigDecimal(retIva.getText()),
                    new BigDecimal(pago.getText()), new BigDecimal(isrServ.getText()),
                    new BigDecimal(isrInt.getText()), cCred.getText(), cDeb.getText(),
                    cPagar.getText(), cRet.getText(), cPago.getText(), mh.getText()));
            UiDialogs.info("Fiscal", "Alícuotas y cuentas tributarias actualizadas.");
        }));
        VBox box = new VBox(10,
                ResponsiveLayouts.row(ResponsiveLayouts.field("IVA", iva),
                        ResponsiveLayouts.field("Retención IVA", retIva),
                        ResponsiveLayouts.field("Pago a cuenta ISR", pago)),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Retención ISR servicios", isrServ),
                        ResponsiveLayouts.field("Retención ISR intangibles", isrInt),
                        ResponsiveLayouts.field("Catálogo MH", mh)),
                ResponsiveLayouts.row(ResponsiveLayouts.field("IVA crédito", cCred),
                        ResponsiveLayouts.field("IVA débito", cDeb),
                        ResponsiveLayouts.field("IVA por pagar", cPagar)),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Retención IVA (activo)", cRet),
                        ResponsiveLayouts.field("Pago a cuenta", cPago), guardar));
        box.getStyleClass().add("form-card");
        return box;
    }

    private VBox politicas(AppContext context) {
        TableView<ParametrizacionService.Parametro> tabla = new TableView<>();
        addCol(tabla, "Clave", ParametrizacionService.Parametro::clave);
        addCol(tabla, "Valor", ParametrizacionService.Parametro::valor);
        addCol(tabla, "Descripción", p -> nvl(p.descripcion()));
        tabla.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        tabla.setItems(FXCollections.observableArrayList(context.parametrizacion().listarPoliticas()));
        TextField valor = new TextField();
        Button guardar = new Button("Actualizar valor");
        guardar.setOnAction(e -> {
            var sel = tabla.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            run(() -> {
                context.parametrizacion().guardarPolitica(sel.clave(), valor.getText().trim());
                tabla.setItems(FXCollections.observableArrayList(context.parametrizacion().listarPoliticas()));
            });
        });
        tabla.getSelectionModel().selectedItemProperty().addListener((o, a, sel) -> {
            if (sel != null) valor.setText(sel.valor());
        });
        VBox box = new VBox(10, new Label("Políticas de partida doble, devengo, inventarios, CIF y RR. HH.; "
                + "máscaras de folio, tolerancia $0.00 e inmutabilidad de partidas."),
                tabla, ResponsiveLayouts.row(ResponsiveLayouts.field("Valor", valor), guardar));
        box.getStyleClass().add("form-card");
        VBox.setVgrow(tabla, javafx.scene.layout.Priority.ALWAYS);
        return box;
    }

    private VBox centros(AppContext context) {
        TableView<ParametrizacionService.CentroCosto> tabla = new TableView<>();
        addCol(tabla, "Código", ParametrizacionService.CentroCosto::codigo);
        addCol(tabla, "Nombre", ParametrizacionService.CentroCosto::nombre);
        addCol(tabla, "Estado", c -> c.activo() ? "Activo" : "Inactivo");
        tabla.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        Runnable refrescar = () -> tabla.setItems(FXCollections.observableArrayList(context.parametrizacion().listarCentros()));
        refrescar.run();
        TextField codigo = new TextField();
        TextField nombre = new TextField();
        CheckBox activo = new CheckBox("Activo");
        activo.setSelected(true);
        Button guardar = new Button("Guardar centro");
        guardar.setOnAction(e -> run(() -> {
            context.parametrizacion().guardarCentro(new ParametrizacionService.CentroCosto(
                    codigo.getText(), nombre.getText(), activo.isSelected()));
            refrescar.run();
        }));
        VBox box = new VBox(10, tabla, ResponsiveLayouts.row(
                ResponsiveLayouts.field("Código", codigo), ResponsiveLayouts.field("Nombre", nombre), activo, guardar));
        box.getStyleClass().add("form-card");
        VBox.setVgrow(tabla, javafx.scene.layout.Priority.ALWAYS);
        return box;
    }

    private <T> void addCol(TableView<T> tabla, String titulo, java.util.function.Function<T, String> getter) {
        TableColumn<T, String> col = new TableColumn<>(titulo);
        col.setCellValueFactory(c -> new SimpleStringProperty(getter.apply(c.getValue())));
        tabla.getColumns().add(col);
    }

    private void run(Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            UiDialogs.error(ex.getMessage());
        }
    }

    private String nvl(String v) {
        return v == null ? "" : v;
    }
}
