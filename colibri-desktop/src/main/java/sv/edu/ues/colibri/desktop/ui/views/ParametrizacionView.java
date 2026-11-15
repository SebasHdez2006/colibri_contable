package sv.edu.ues.colibri.desktop.ui.views;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.application.configuracion.ParametrizacionService;
import sv.edu.ues.colibri.desktop.ui.ResponsiveLayouts;
import sv.edu.ues.colibri.desktop.ui.UiCatalogos;
import sv.edu.ues.colibri.desktop.ui.UiDialogs;
import sv.edu.ues.colibri.domain.model.Cuenta;
import sv.edu.ues.colibri.domain.model.EstadoCuenta;
import sv.edu.ues.colibri.domain.model.NaturalezaCuenta;

import java.math.BigDecimal;

/**
 * Vista de configuración contable y fiscal del sistema.
 */
public class ParametrizacionView extends VBox {

    /** Secciones de la parametrización; cada una es una entrada del menú lateral. */
    public enum Seccion {
        CATALOGO("Catálogo de cuentas"), MAPEO("Mapeo de módulos"), PERIODOS("Períodos contables"),
        FISCAL("Parámetros fiscales"), POLITICAS("Políticas y secuenciales"), CENTROS("Centros de costo");

        private final String etiqueta;

        Seccion(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        /**
         * Texto visible de la sección.
         *
         * @return etiqueta para menú y encabezado
         */
        public String etiqueta() {
            return etiqueta;
        }
    }

    /**
     * Crea una sección de la parametrización contable y fiscal.
     *
     * @param context contexto de aplicación con acceso a los servicios de configuración
     * @param seccion sección que se muestra
     */
    public ParametrizacionView(AppContext context, Seccion seccion) {
        Label titulo = new Label(seccion.etiqueta());
        titulo.getStyleClass().add("view-title");
        Label ayuda = new Label(textoAyuda(seccion));
        ayuda.getStyleClass().add("view-subtitle");
        ayuda.setWrapText(true);

        VBox cuerpo = switch (seccion) {
            case CATALOGO -> catalogo(context);
            case MAPEO -> posting(context);
            case PERIODOS -> periodos(context);
            case FISCAL -> fiscal(context);
            case POLITICAS -> politicas(context);
            case CENTROS -> centros(context);
        };
        VBox.setVgrow(cuerpo, javafx.scene.layout.Priority.ALWAYS);
        getChildren().addAll(titulo, ayuda, cuerpo);
    }

    private static String textoAyuda(Seccion seccion) {
        return switch (seccion) {
            case CATALOGO -> "Administre el plan de cuentas: cree subcuentas de 6 dígitos bajo su "
                    + "cuenta padre de 4 dígitos, edite denominaciones y active o bloquee cuentas. "
                    + "Sólo las cuentas de movimiento pueden usarse en las partidas.";
            case MAPEO -> "Defina las cuentas por defecto que usa cada evento operativo (ventas, "
                    + "compras, cobros, pagos, inventario, depreciación, planilla) al generar sus "
                    + "asientos automáticos. También puede registrar eventos nuevos.";
            case PERIODOS -> "Los períodos son meses del ejercicio. «Abierto» permite la captura, "
                    + "«en auditoría» admite ajustes y «cerrado» bloquea el mes. El cierre verifica "
                    + "que no existan cédulas de ajuste pendientes antes de bloquear.";
            case FISCAL -> "Alícuotas y cuentas tributarias: IVA, retenciones de IVA e ISR, pago a "
                    + "cuenta y catálogo del Ministerio de Hacienda. Se aplican a los documentos "
                    + "electrónicos y a la liquidación de IVA del período.";
            case POLITICAS -> "Parámetros del sistema: máscaras de folio, tolerancia de cuadre, "
                    + "inmutabilidad de partidas y políticas de inventario, CIF y planilla "
                    + "(tasas laborales y patronales, INCAF y umbral de empleados).";
            case CENTROS -> "Centros de costo para distribuir gastos y análisis por unidad "
                    + "organizativa. Los centros activos se pueden asignar en las líneas de las "
                    + "partidas contables.";
        };
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
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
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
            if (UiDialogs.confirmar("Cerrar período", "¿Cerrar el mes " + sel.getMes()
                    + "? Se verificará que no haya cédulas de ajuste pendientes.")) {
                run(() -> { context.cierre().cerrarPeriodo(sel); refrescar.run(); });
            }
        });
        VBox box = new VBox(10, new Label("Abierto permite captura. En auditoría deja ajustes. "
                + "Cerrado bloquea el mes tras verificar las cédulas pendientes."),
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
        addCol(tabla, "Estado", c -> estadoTexto(c.getEstado()));
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
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
        codigo.setPromptText("Ej. 110205");
        UiCatalogos.ayuda(codigo, "Código de 6 dígitos; las subcuentas usan el código del padre de 4 dígitos.");
        TextField nombre = new TextField();
        nombre.setPromptText("Denominación de la cuenta");
        TextField padre = new TextField();
        padre.setPromptText("Cuenta de 4 dígitos");
        UiCatalogos.ayuda(padre, "Código de la cuenta padre (nivel de 4 dígitos) bajo la que se crea la subcuenta.");
        ComboBox<NaturalezaCuenta> nat = new ComboBox<>(FXCollections.observableArrayList(NaturalezaCuenta.values()));
        nat.setConverter(converterNaturaleza());
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
        estado.setConverter(converterEstado());
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
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setItems(FXCollections.observableArrayList(context.parametrizacion().listarPosting()));
        ComboBox<Cuenta> debe = UiCatalogos.campoCuenta(context, null,
                "Cuenta de cargo por defecto del evento; vacío conserva la actual.");
        ComboBox<Cuenta> haber = UiCatalogos.campoCuenta(context, null,
                "Cuenta de abono por defecto del evento; vacío conserva la actual.");
        ComboBox<Cuenta> iva = UiCatalogos.campoCuenta(context, null,
                "Cuenta de IVA del evento, si aplica; vacío conserva la actual.");
        Button guardar = new Button("Guardar mapeo seleccionado");
        guardar.setOnAction(e -> {
            var sel = tabla.getSelectionModel().getSelectedItem();
            if (sel == null) {
                UiDialogs.error("Seleccione un evento operativo.");
                return;
            }
            run(() -> {
                String nuevoDebe = UiCatalogos.codigoCuenta(debe);
                String nuevoHaber = UiCatalogos.codigoCuenta(haber);
                String nuevaIva = UiCatalogos.codigoCuenta(iva);
                context.parametrizacion().guardarPosting(new ParametrizacionService.PostingProfile(
                        sel.evento(), sel.etiqueta(),
                        nuevoDebe.isBlank() ? sel.cuentaDebe() : nuevoDebe,
                        nuevoHaber.isBlank() ? sel.cuentaHaber() : nuevoHaber,
                        nuevaIva.isBlank() ? sel.cuentaIva() : nuevaIva));
                tabla.setItems(FXCollections.observableArrayList(context.parametrizacion().listarPosting()));
                UiDialogs.info("Posting", "Cuentas automáticas actualizadas.");
            });
        });
        tabla.getSelectionModel().selectedItemProperty().addListener((o, a, sel) -> {
            if (sel != null) {
                UiCatalogos.seleccionarCuenta(debe, sel.cuentaDebe());
                UiCatalogos.seleccionarCuenta(haber, sel.cuentaHaber());
                UiCatalogos.seleccionarCuenta(iva, sel.cuentaIva());
            }
        });

        TextField nuevoEvento = new TextField();
        nuevoEvento.setPromptText("Ej. AJUSTE_INVENTARIO");
        UiCatalogos.ayuda(nuevoEvento, "Código del evento nuevo: mayúsculas, dígitos y guion bajo.");
        TextField nuevaEtiqueta = new TextField();
        nuevaEtiqueta.setPromptText("Descripción visible del evento");
        Button crear = new Button("Crear evento");
        crear.setAccessibleHelp("Registra un perfil de contabilización nuevo con las cuentas elegidas.");
        crear.setOnAction(e -> run(() -> {
            String evento = nuevoEvento.getText() == null ? "" : nuevoEvento.getText().trim()
                    .toUpperCase(java.util.Locale.ROOT);
            if (evento.isBlank() || nuevaEtiqueta.getText() == null || nuevaEtiqueta.getText().isBlank()) {
                throw new IllegalArgumentException("Indique el código y la descripción del evento nuevo.");
            }
            String d = UiCatalogos.codigoCuenta(debe);
            String h = UiCatalogos.codigoCuenta(haber);
            String iv = UiCatalogos.codigoCuenta(iva);
            if (d.isBlank() || h.isBlank()) {
                throw new IllegalArgumentException("Seleccione las cuentas Debe y Haber del nuevo evento.");
            }
            context.parametrizacion().guardarPosting(new ParametrizacionService.PostingProfile(
                    evento, nuevaEtiqueta.getText().trim(), d, h, iv.isBlank() ? null : iv));
            tabla.setItems(FXCollections.observableArrayList(context.parametrizacion().listarPosting()));
            nuevoEvento.clear();
            nuevaEtiqueta.clear();
            UiDialogs.info("Posting", "Evento «" + evento + "» registrado.");
        }));

        VBox box = new VBox(10, new Label("Cuentas por defecto para ventas, compras, inventario, "
                + "depreciación, planilla y demás eventos operativos."),
                tabla,
                ResponsiveLayouts.row(ResponsiveLayouts.field("Debe", debe),
                        ResponsiveLayouts.field("Haber", haber), ResponsiveLayouts.field("IVA", iva), guardar),
                new Label("Nuevo evento (el código se normaliza a mayúsculas):"),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Evento", nuevoEvento),
                        ResponsiveLayouts.field("Descripción", nuevaEtiqueta), crear));
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
        ComboBox<Cuenta> cCred = cuentaFiscal(context, p.cuentaCredito(), "Cuenta de IVA crédito fiscal.");
        ComboBox<Cuenta> cDeb = cuentaFiscal(context, p.cuentaDebito(), "Cuenta de IVA débito fiscal.");
        ComboBox<Cuenta> cPagar = cuentaFiscal(context, p.cuentaPorPagar(), "Cuenta de IVA por pagar.");
        ComboBox<Cuenta> cRet = cuentaFiscal(context, p.cuentaRetencionIva(), "Cuenta de retención de IVA por pagar.");
        ComboBox<Cuenta> cPago = cuentaFiscal(context, p.cuentaPagoCuenta(), "Cuenta de pago a cuenta ISR.");
        TextField mh = new TextField(p.catalogoMh());
        mh.setPromptText("Código del catálogo MH");
        UiCatalogos.ayuda(mh, "Código del catálogo del Ministerio de Hacienda para clasificar el gasto.");
        Button guardar = new Button("Guardar parámetros fiscales");
        guardar.setOnAction(e -> run(() -> {
            context.parametrizacion().guardarFiscal(new ParametrizacionService.ParametroFiscal(
                    new BigDecimal(iva.getText()), new BigDecimal(retIva.getText()),
                    new BigDecimal(pago.getText()), new BigDecimal(isrServ.getText()),
                    new BigDecimal(isrInt.getText()), codigo(cCred, "IVA crédito"), codigo(cDeb, "IVA débito"),
                    codigo(cPagar, "IVA por pagar"), codigo(cRet, "retención IVA"), codigo(cPago, "pago a cuenta"),
                    mh.getText()));
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
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setItems(FXCollections.observableArrayList(context.parametrizacion().listarPoliticas()));
        TextField valor = new TextField();
        valor.setPromptText("Nuevo valor de la política");
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
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        Runnable refrescar = () -> tabla.setItems(FXCollections.observableArrayList(context.parametrizacion().listarCentros()));
        refrescar.run();
        TextField codigo = new TextField();
        codigo.setPromptText("Ej. CC-01");
        UiCatalogos.ayuda(codigo, "Código corto del centro de costo; es el que se captura en las partidas.");
        TextField nombre = new TextField();
        nombre.setPromptText("Ej. Administración");
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

    private ComboBox<Cuenta> cuentaFiscal(AppContext context, String valor, String ayuda) {
        return UiCatalogos.campoCuenta(context, valor, ayuda);
    }

    private static String codigo(ComboBox<Cuenta> combo, String etiqueta) {
        String valor = UiCatalogos.codigoCuenta(combo);
        if (valor.isBlank()) {
            throw new IllegalArgumentException("Seleccione la cuenta de " + etiqueta + ".");
        }
        return valor;
    }

    private static String estadoTexto(EstadoCuenta estado) {
        return switch (estado) {
            case ACTIVA -> "Activa";
            case DESACTIVADA -> "Desactivada";
            case BLOQUEADA -> "Bloqueada";
        };
    }

    private static StringConverter<EstadoCuenta> converterEstado() {
        return new StringConverter<>() {
            @Override public String toString(EstadoCuenta value) {
                if (value == null) return "";
                return switch (value) {
                    case ACTIVA -> "Activa";
                    case DESACTIVADA -> "Desactivada";
                    case BLOQUEADA -> "Bloqueada";
                };
            }
            @Override public EstadoCuenta fromString(String text) {
                if (text == null) return null;
                return switch (text) {
                    case "Activa" -> EstadoCuenta.ACTIVA;
                    case "Desactivada" -> EstadoCuenta.DESACTIVADA;
                    case "Bloqueada" -> EstadoCuenta.BLOQUEADA;
                    default -> null;
                };
            }
        };
    }

    private static StringConverter<NaturalezaCuenta> converterNaturaleza() {
        return new StringConverter<>() {
            @Override public String toString(NaturalezaCuenta value) {
                if (value == null) return "";
                return switch (value) {
                    case DEUDORA -> "Deudora";
                    case ACREEDORA -> "Acreedora";
                    case AMBAS -> "Deudora y acreedora";
                };
            }
            @Override public NaturalezaCuenta fromString(String text) {
                if (text == null) return null;
                return switch (text) {
                    case "Deudora" -> NaturalezaCuenta.DEUDORA;
                    case "Acreedora" -> NaturalezaCuenta.ACREEDORA;
                    case "Deudora y acreedora", "Ambas" -> NaturalezaCuenta.AMBAS;
                    default -> null;
                };
            }
        };
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
