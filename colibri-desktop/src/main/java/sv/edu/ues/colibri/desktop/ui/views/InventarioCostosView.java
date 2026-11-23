package sv.edu.ues.colibri.desktop.ui.views;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.application.configuracion.ParametrizacionService;
import sv.edu.ues.colibri.application.inventario.InventarioCostoService;
import sv.edu.ues.colibri.desktop.ui.ResponsiveLayouts;
import sv.edu.ues.colibri.desktop.ui.UiCampos;
import sv.edu.ues.colibri.desktop.ui.UiCatalogos;
import sv.edu.ues.colibri.desktop.ui.UiDialogs;
import sv.edu.ues.colibri.desktop.ui.UiTables;
import sv.edu.ues.colibri.domain.model.Cuenta;
import sv.edu.ues.colibri.domain.model.Tercero;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Vista operativa para valorar inventario y costear órdenes, servicios y licencias.
 */
public class InventarioCostosView extends VBox {

    public enum Panel {
        DIGITALES, SERVICIOS, MEDIDA, LICENCIAS, SAAS, KARDEX, PRODUCTOS
    }

    private final AppContext context;
    private final sv.edu.ues.colibri.desktop.ui.nav.Navegador navegador;
    private final Label ordenLbl = new Label();
    private final ListView<String> lotes = new ListView<>();
    private final ComboBox<Tercero> cliente = new ComboBox<>();
    private final ComboBox<InventarioCostoService.LicenciaEntregadaInfo> licenciaGestion = new ComboBox<>();
    private final ComboBox<InventarioCostoService.LoteInfo> loteDeterioro = new ComboBox<>();
    private final ComboBox<InventarioCostoService.VersionProductoInfo> versionLote = new ComboBox<>();
    private final TableView<InventarioCostoService.OrdenRentabilidad> tablaOrdenes = new TableView<>();
    private TableView<InventarioCostoService.ProductoInfo> tablaProductos;
    private TableView<InventarioCostoService.VersionProductoInfo> tablaVersiones;
    private ComboBox<InventarioCostoService.ProductoInfo> productoVersion;

    /**
     * Crea la vista de costeo para el panel funcional indicado.
     *
     * @param context contexto de aplicación con acceso a inventario y parametrización
     * @param panel sección funcional a presentar
     */
    public InventarioCostosView(AppContext context, Panel panel) {
        this(context, panel, null);
    }

    /**
     * Crea la vista de costeo para el panel funcional indicado.
     *
     * @param context contexto de aplicación con acceso a inventario y parametrización
     * @param panel sección funcional a presentar
     * @param navegador acción para saltar a otra sección del sistema (puede ser {@code null})
     */
    public InventarioCostosView(AppContext context, Panel panel,
            sv.edu.ues.colibri.desktop.ui.nav.Navegador navegador) {
        this.context = context;
        this.navegador = navegador;
        DatePicker fecha = new DatePicker(fechaPeriodoAbierto());
        configurarSelectores();

        String[] encabezado = encabezadoPanel(panel);
        Label titulo = new Label(encabezado[0]);
        titulo.getStyleClass().add("view-title");
        Label subtitulo = new Label(encabezado[1]);
        subtitulo.getStyleClass().add("view-subtitle");
        getChildren().addAll(titulo, subtitulo);

        switch (panel) {
            case DIGITALES -> getChildren().addAll(panelDigitales(fecha));
            case SERVICIOS -> getChildren().addAll(panelServicios(fecha));
            case MEDIDA -> getChildren().addAll(panelMedida(fecha));
            case LICENCIAS -> getChildren().addAll(panelLicencias());
            case SAAS -> getChildren().addAll(panelSaas(fecha));
            case KARDEX -> getChildren().addAll(panelKardex());
            case PRODUCTOS -> getChildren().addAll(panelProductos());
        }
        refrescar();
    }

    private javafx.scene.Node[] panelDigitales(DatePicker fecha) {
        TextField codigoLote = new TextField();
        codigoLote.setPromptText("Ej. LOTE-INV-2026-01");
        UiCatalogos.ayuda(codigoLote, "Identificador único del lote; aparece en el inventario PEPS.");
        TextField unidades = new TextField();
        unidades.setPromptText("Cantidad de licencias");
        UiCatalogos.ayuda(unidades, "Número de licencias o unidades que entran al inventario.");
        TextField costoUnitario = new TextField();
        costoUnitario.setPromptText("Costo por licencia");
        UiCatalogos.ayuda(costoUnitario, "Costo unitario en USD; el sistema calcula el costo total del lote.");
        Label costoTotal = new Label("Costo total: —");
        costoTotal.getStyleClass().add("result-panel");
        ComboBox<Tercero> clienteLote = new ComboBox<>();
        clienteLote.setConverter(converterTercero());
        clienteLote.setPromptText("Cliente titular (opcional)");
        UiCatalogos.ayuda(clienteLote, "Cliente al que quedarán asignadas las licencias liberadas; puede quedar vacío.");
        versionLote.setConverter(converterVersion());
        versionLote.setPromptText("Producto / versión");
        UiCatalogos.ayuda(versionLote, "Versión de producto LIBERADA contra la que se crea el lote; "
                + "se administra en Inventario → Productos.");
        try {
            clienteLote.setItems(FXCollections.observableArrayList(
                    context.operaciones().listarTerceros().stream().filter(Tercero::isCliente).toList()));
        } catch (Exception ignored) {
            // Sin clientes disponibles el campo queda vacío.
        }
        Runnable actualizarTotal = () -> {
            Integer cant = UiCampos.enteroONull(unidades.getText());
            BigDecimal unitario = UiCampos.importeONull(costoUnitario.getText());
            if (cant == null || cant <= 0 || unitario == null || unitario.signum() < 0) {
                costoTotal.setText("Costo total: —");
                return;
            }
            BigDecimal total = unitario.multiply(BigDecimal.valueOf(cant))
                    .setScale(2, RoundingMode.HALF_UP);
            costoTotal.setText("Costo total: $" + total.toPlainString()
                    + "  (" + cant + " × $" + unitario.stripTrailingZeros().toPlainString() + ")");
        };
        unidades.textProperty().addListener((o, a, b) -> actualizarTotal.run());
        costoUnitario.textProperty().addListener((o, a, b) -> actualizarTotal.run());
        Button liberar = new Button("Liberar lote");
        liberar.setOnAction(e -> ejecutar(() -> {
            int cant = UiCampos.entero(unidades.getText(), "número de licencias");
            BigDecimal unitario = UiCampos.importe(costoUnitario.getText());
            if (cant <= 0) {
                throw new IllegalArgumentException("La cantidad de licencias debe ser mayor que cero.");
            }
            if (unitario.signum() <= 0) {
                throw new IllegalArgumentException("El costo unitario debe ser mayor que cero.");
            }
            BigDecimal total = unitario.multiply(BigDecimal.valueOf(cant))
                    .setScale(2, RoundingMode.HALF_UP);
            var info = context.inventario().liberarLote(codigoLote.getText().trim(),
                    cant, total, fecha.getValue(),
                    clienteLote.getValue() == null ? null : clienteLote.getValue().getId(),
                    versionLote.getValue() == null ? null : versionLote.getValue().id());
            UiDialogs.info("Lote liberado", "%s: costo unitario $%s · %d licencias disponibles".formatted(
                    info.codigo(), info.costoUnitario(), info.disponibles()));
            codigoLote.clear();
            unidades.clear();
            costoUnitario.clear();
            clienteLote.setValue(null);
            actualizarTotal.run();
            refrescar();
        }));
        VBox liberacion = tarjeta("Liberación de licencias",
                ResponsiveLayouts.row(ResponsiveLayouts.field("Fecha", fecha),
                        ResponsiveLayouts.field("Código del lote", codigoLote),
                        ResponsiveLayouts.field("Producto / versión", versionLote),
                        ResponsiveLayouts.field("Cliente titular", clienteLote),
                        ResponsiveLayouts.field("Unidades", unidades),
                        ResponsiveLayouts.field("Costo unitario (USD)", costoUnitario), liberar),
                costoTotal);

        TextField precio = new TextField();
        precio.setPromptText("Precio neto, sin IVA");
        UiCatalogos.ayuda(precio, "Precio de venta de la licencia sin incluir IVA.");
        Button vender = new Button("Registrar venta y costo");
        vender.setOnAction(e -> ejecutar(() -> {
            if (cliente.getValue() == null) {
                throw new IllegalArgumentException("Seleccione el cliente al que se concede la licencia.");
            }
            var p = context.inventario().venderLicenciaPeps(cliente.getValue().getId(),
                    fecha.getValue(), UiCampos.importe(precio.getText()), null);
            UiDialogs.info("Venta contabilizada", "Folio %s. Se aplicaron ingreso, IVA y costo de ventas por PEPS.".formatted(p.getFolio()));
            precio.clear();
            refrescar();
        }));
        VBox venta = tarjeta("Concesión de licencia",
                new Label("PEPS automático: el sistema toma la licencia disponible del lote liberado primero."),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Cliente", cliente),
                        ResponsiveLayouts.field("Precio neto (USD)", precio), vender));

        licenciaGestion.setPromptText("Licencia entregada");
        UiCatalogos.ayuda(licenciaGestion, "Licencia en estado ENTREGADA sobre la que se aplicará devolución o revocación.");
        TextField motivoGestion = new TextField();
        motivoGestion.setPromptText("Motivo (opcional)");
        UiCatalogos.ayuda(motivoGestion, "Se asienta en el kárdex del lote como detalle del movimiento.");
        Button devolver = new Button("Devolver al inventario");
        devolver.setOnAction(e -> ejecutar(() -> {
            var seleccion = licenciaGestion.getValue();
            if (seleccion == null) {
                throw new IllegalArgumentException("Seleccione la licencia entregada.");
            }
            if (!UiDialogs.confirmar("Confirmar devolución",
                    "La licencia volverá a estar disponible por PEPS. Si su venta tiene partida "
                            + "trazable en el kárdex, se revertirá el asiento completo "
                            + "(ingreso, IVA, costo de ventas e inventario).")) {
                return;
            }
            var reversa = context.inventario().devolverLicencia(seleccion.id(), fecha.getValue(),
                    motivoGestion.getText());
            UiDialogs.info("Licencia devuelta", reversa == null
                    ? "La unidad reingresó al inventario. La venta original no tiene partida por "
                            + "unidad: ajuste el ingreso manualmente si corresponde."
                    : "La unidad reingresó al inventario y se contabilizó la reversa con folio "
                            + reversa.getFolio() + ".");
            motivoGestion.clear();
            refrescar();
        }));
        Button revocar = new Button("Revocar derecho");
        revocar.setOnAction(e -> ejecutar(() -> {
            var seleccion = licenciaGestion.getValue();
            if (seleccion == null) {
                throw new IllegalArgumentException("Seleccione la licencia entregada.");
            }
            if (!UiDialogs.confirmar("Confirmar revocación",
                    "El derecho de uso queda revocado y la unidad NO regresa al inventario. "
                            + "El tratamiento del ingreso se realiza por separado.")) {
                return;
            }
            context.inventario().revocarLicencia(seleccion.id(), fecha.getValue(),
                    motivoGestion.getText());
            UiDialogs.info("Licencia revocada",
                    "El derecho quedó revocado; el movimiento consta en el kárdex del lote.");
            motivoGestion.clear();
            refrescar();
        }));
        VBox gestion = tarjeta("Devolución y revocación de licencias",
                new Label("La devolución reingresa la unidad al stock PEPS; la revocación la da "
                        + "de baja sin reingreso. Ambos hechos quedan en el kárdex."),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Licencia entregada", licenciaGestion),
                        ResponsiveLayouts.field("Motivo", motivoGestion), devolver, revocar));

        loteDeterioro.setConverter(new StringConverter<>() {
            @Override public String toString(InventarioCostoService.LoteInfo value) {
                return value == null ? "" : etiquetaLote(value);
            }
            @Override public InventarioCostoService.LoteInfo fromString(String ignored) { return null; }
        });
        loteDeterioro.setPromptText("Lote a evaluar");
        UiCatalogos.ayuda(loteDeterioro, "Lote de licencias disponible cuyo valor en libros se comparará "
                + "con el valor recuperable de mercado.");
        // El DatePicker de la liberación no puede reutilizarse aquí: un nodo
        // JavaFX sólo admite un padre, así que el deterioro lleva el suyo.
        DatePicker fechaDeterioro = new DatePicker(fechaPeriodoAbierto());
        TextField valorRecuperable = new TextField();
        valorRecuperable.setPromptText("Valor recuperable total (USD)");
        UiCatalogos.ayuda(valorRecuperable, "Valor de mercado estimado del lote completo; debe ser menor "
                + "o igual al costo bruto. Si sube después, el sistema revierte el deterioro.");
        Button aplicarDeterioro = new Button("Aplicar deterioro");
        aplicarDeterioro.setOnAction(e -> ejecutar(() -> {
            var lote = loteDeterioro.getValue();
            if (lote == null) {
                throw new IllegalArgumentException("Seleccione el lote a evaluar.");
            }
            if (fechaDeterioro.getValue() == null) {
                throw new IllegalArgumentException("Seleccione la fecha del ajuste.");
            }
            BigDecimal recuperable = UiCampos.importeONull(valorRecuperable.getText());
            if (recuperable == null) {
                throw new IllegalArgumentException("Ingrese el valor recuperable en USD.");
            }
            if (recuperable.signum() < 0) {
                throw new IllegalArgumentException("El valor recuperable no puede ser negativo.");
            }
            var partida = context.cierre().deteriorarLoteAValorRecuperable(
                    lote.codigo(), recuperable, fechaDeterioro.getValue());
            UiDialogs.info("Deterioro aplicado",
                    "Se generó la partida " + partida.getFolio() + " con folio de ajuste por valor "
                            + "recuperable $" + recuperable.toPlainString()
                            + ". Revísela en el Libro diario: nace en borrador y debe aprobarse y "
                            + "contabilizarse para afectar el mayor.");
            valorRecuperable.clear();
            refrescar();
        }));
        VBox deterioro = tarjeta("Deterioro de inventario (valor recuperable)",
                new Label("Genera una partida en borrador por la baja del lote; si el valor "
                        + "recuperable sube en un período posterior, el ajuste se revierte solo."),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Lote", loteDeterioro),
                        ResponsiveLayouts.field("Valor recuperable (USD)", valorRecuperable),
                        ResponsiveLayouts.field("Fecha", fechaDeterioro), aplicarDeterioro));

        lotes.setPlaceholder(new Label("Aún no se han liberado lotes de licencias."));
        lotes.setMaxHeight(Double.MAX_VALUE);
        VBox existencias = tarjeta("Inventario perpetuo PEPS (primero en entrar, primero en salir)",
                resumenCuentasPeps(), lotes);
        return new javafx.scene.Node[]{liberacion, venta, gestion, deterioro, existencias};
    }

    /**
     * Resume las cuentas que intervienen en la venta PEPS de licencias según
     * el mapeo de módulos (VENTA y COSTO_VENTA) y el IVA débito fiscal, con
     * acceso directo al kárdex del lote.
     */
    private javafx.scene.Node resumenCuentasPeps() {
        VBox resumen = new VBox(6);
        resumen.getStyleClass().add("peps-accounts");
        try {
            var venta = context.parametrizacion().exigirPerfilPosting("VENTA");
            var costo = context.parametrizacion().exigirPerfilPosting("COSTO_VENTA");
            String ivaDebito = context.parametrizacion().parametrosFiscales().cuentaDebito();
            agregarLineaCuenta(resumen, "Por cobrar (VENTA · debe)", venta.cuentaDebe());
            agregarLineaCuenta(resumen, "Ingreso por licencia (VENTA · haber)", venta.cuentaHaber());
            agregarLineaCuenta(resumen, "Costo de ventas (COSTO_VENTA · debe)", costo.cuentaDebe());
            agregarLineaCuenta(resumen, "Inventario PEPS (COSTO_VENTA · haber)", costo.cuentaHaber());
            if (ivaDebito != null && !ivaDebito.isBlank()) {
                agregarLineaCuenta(resumen, "IVA débito fiscal", ivaDebito);
            }
        } catch (Exception ex) {
            Label aviso = new Label("Configure el mapeo VENTA y COSTO_VENTA en "
                    + "Parametrización → Mapeo de módulos para ver las cuentas del método.");
            aviso.setWrapText(true);
            aviso.getStyleClass().add("section-note");
            resumen.getChildren().add(aviso);
        }
        if (navegador != null) {
            Button verKardex = new Button("Ver kárdex de licencias");
            verKardex.setOnAction(e -> navegador.ir("nav-inventario-kardex"));
            resumen.getChildren().add(ResponsiveLayouts.row(verKardex));
        }
        return resumen;
    }

    private void agregarLineaCuenta(VBox resumen, String etiqueta, String codigo) {
        Label linea = new Label(etiqueta + ":  " + nombreCuenta(codigo));
        linea.setWrapText(true);
        linea.getStyleClass().add("muted-caption");
        resumen.getChildren().add(linea);
    }

    private Map<String, String> cacheNombresCuentas;

    private String nombreCuenta(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return "—";
        }
        if (cacheNombresCuentas == null) {
            cacheNombresCuentas = new java.util.HashMap<>();
            try {
                context.catalogo().listar().forEach(cuenta ->
                        cacheNombresCuentas.put(cuenta.getCodigo(), cuenta.getCodigo()
                                + " · " + cuenta.getNombre()));
            } catch (Exception ex) {
                // Sin catálogo las celdas muestran sólo el código.
            }
        }
        return cacheNombresCuentas.getOrDefault(codigo, codigo);
    }

    private javafx.scene.Node[] panelKardex() {
        ComboBox<InventarioCostoService.LoteInfo> loteKardex = new ComboBox<>();
        loteKardex.setConverter(new StringConverter<>() {
            @Override public String toString(InventarioCostoService.LoteInfo value) {
                return value == null ? "" : etiquetaLote(value) + " · " + value.disponibles()
                        + " disponibles";
            }
            @Override public InventarioCostoService.LoteInfo fromString(String ignored) { return null; }
        });
        loteKardex.setPromptText("Lote del kárdex");
        UiCatalogos.ayuda(loteKardex, "Lote cuyas entradas, salidas PEPS, devoluciones y revocaciones se listan en orden.");
        TableView<InventarioCostoService.KardexInfo> tabla = new TableView<>();
        TableColumn<InventarioCostoService.KardexInfo, String> cFecha = new TableColumn<>("Fecha");
        cFecha.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().fecha() == null ? "" : c.getValue().fecha().toString()));
        TableColumn<InventarioCostoService.KardexInfo, String> cTipo = new TableColumn<>("Movimiento");
        cTipo.setCellValueFactory(c -> new SimpleStringProperty(
                etiquetaMovimiento(c.getValue().tipo())));
        TableColumn<InventarioCostoService.KardexInfo, String> cLic = new TableColumn<>("Licencia");
        cLic.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().licenciaNumero() == null ? "—" : "#" + c.getValue().licenciaNumero()));
        TableColumn<InventarioCostoService.KardexInfo, String> cEntrada = new TableColumn<>("Entrada");
        cEntrada.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().unidades() > 0 ? String.valueOf(c.getValue().unidades()) : ""));
        TableColumn<InventarioCostoService.KardexInfo, String> cSalida = new TableColumn<>("Salida");
        cSalida.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().unidades() < 0 ? String.valueOf(-c.getValue().unidades()) : ""));
        TableColumn<InventarioCostoService.KardexInfo, String> cCosto = new TableColumn<>("Costo unit.");
        cCosto.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().unidades() == 0 ? "—" : dinero(c.getValue().costoUnitario())));
        TableColumn<InventarioCostoService.KardexInfo, String> cImporte = new TableColumn<>("Importe");
        cImporte.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().unidades() == 0 ? "—" : dinero(c.getValue().importe())));
        TableColumn<InventarioCostoService.KardexInfo, String> cSaldoU = new TableColumn<>("Saldo und.");
        cSaldoU.setCellValueFactory(c -> new SimpleStringProperty(
                String.valueOf(c.getValue().saldoUnidades())));
        TableColumn<InventarioCostoService.KardexInfo, String> cSaldoV = new TableColumn<>("Saldo valor");
        cSaldoV.setCellValueFactory(c -> new SimpleStringProperty(
                dinero(c.getValue().saldoValor())));
        TableColumn<InventarioCostoService.KardexInfo, String> cDetalle = new TableColumn<>("Detalle");
        cDetalle.setCellValueFactory(c -> {
            String detalle = c.getValue().detalle() == null ? "" : c.getValue().detalle();
            String folio = c.getValue().folio();
            return new SimpleStringProperty(folio == null || folio.isBlank()
                    ? detalle : folio + (detalle.isBlank() ? "" : " · " + detalle));
        });
        tabla.getColumns().addAll(cFecha, cTipo, cLic, cEntrada, cSalida, cCosto, cImporte,
                cSaldoU, cSaldoV, cDetalle);
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("Seleccione un lote para ver su kárdex."));
        tabla.setMinHeight(260);
        tabla.setPrefHeight(420);
        DatePicker kardexDesde = new DatePicker();
        kardexDesde.setPromptText("Desde");
        DatePicker kardexHasta = new DatePicker();
        kardexHasta.setPromptText("Hasta");
        ComboBox<String> tipoKardex = new ComboBox<>(FXCollections.observableArrayList(
                "Todos", "LIBERACION", "ENTREGA", "DEVOLUCION", "REVOCACION"));
        tipoKardex.setConverter(converterEtiquetas(Map.of(
                "LIBERACION", "Liberación", "ENTREGA", "Salida PEPS",
                "DEVOLUCION", "Devolución", "REVOCACION", "Revocación")));
        tipoKardex.setValue("Todos");
        UiCatalogos.ayuda(tipoKardex, "Filtra el kárdex por tipo de movimiento.");
        Label totalesKardex = new Label("—");
        totalesKardex.getStyleClass().add("section-note");
        totalesKardex.setWrapText(true);
        Runnable cargar = () -> {
            var lote = loteKardex.getValue();
            if (lote == null) {
                UiTables.reemplazarFilas(tabla, java.util.List.of());
                totalesKardex.setText("—");
                return;
            }
            try {
                var movimientos = context.inventario().kardexDeLote(lote.id()).stream()
                        .filter(m -> kardexDesde.getValue() == null || m.fecha() == null
                                || !m.fecha().isBefore(kardexDesde.getValue()))
                        .filter(m -> kardexHasta.getValue() == null || m.fecha() == null
                                || !m.fecha().isAfter(kardexHasta.getValue()))
                        .filter(m -> "Todos".equals(tipoKardex.getValue())
                                || tipoKardex.getValue().equals(m.tipo()))
                        .toList();
                UiTables.reemplazarFilas(tabla, movimientos);
                int entradas = movimientos.stream().mapToInt(m -> Math.max(m.unidades(), 0)).sum();
                int salidas = movimientos.stream().mapToInt(m -> Math.max(-m.unidades(), 0)).sum();
                BigDecimal importe = movimientos.stream().map(InventarioCostoService.KardexInfo::importe)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                totalesKardex.setText("%d movimientos · Entradas %d und · Salidas %d und · Importe neto %s"
                        .formatted(movimientos.size(), entradas, salidas, dinero(importe)));
            } catch (Exception ex) {
                UiTables.reemplazarFilas(tabla, java.util.List.of());
                totalesKardex.setText("No se pudo leer el kárdex: " + ex.getMessage());
            }
        };
        loteKardex.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> cargar.run());
        kardexDesde.valueProperty().addListener((o, a, b) -> cargar.run());
        kardexHasta.valueProperty().addListener((o, a, b) -> cargar.run());
        tipoKardex.valueProperty().addListener((o, a, b) -> cargar.run());
        try {
            var disponibles = context.inventario().listarLotes();
            loteKardex.setItems(FXCollections.observableArrayList(disponibles));
            if (!disponibles.isEmpty()) {
                loteKardex.getSelectionModel().selectFirst();
            }
        } catch (Exception ignored) {
            loteKardex.getItems().clear();
        }
        VBox card = tarjeta("Kárdex de licencias",
                new Label("Entrada, salida PEPS, devolución o revocación por lote con saldo "
                        + "de unidades y valor. Elija el lote y, si quiere, acote por fechas o tipo."),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Lote", loteKardex),
                        ResponsiveLayouts.field("Desde", kardexDesde),
                        ResponsiveLayouts.field("Hasta", kardexHasta),
                        ResponsiveLayouts.field("Tipo", tipoKardex)),
                totalesKardex,
                UiTables.conBusqueda(tabla, "movimientos del kárdex", movimiento -> movimiento.fecha()
                        + " " + etiquetaMovimiento(movimiento.tipo()) + " " + movimiento.licenciaNumero()
                        + " " + movimiento.detalle() + " " + movimiento.folio()));
        return new javafx.scene.Node[]{card};
    }

    private javafx.scene.Node[] panelProductos() {
        tablaProductos = new TableView<>();
        TableColumn<InventarioCostoService.ProductoInfo, String> prCod = new TableColumn<>("Código");
        prCod.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().codigo()));
        TableColumn<InventarioCostoService.ProductoInfo, String> prNom = new TableColumn<>("Producto");
        prNom.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().nombre()));
        TableColumn<InventarioCostoService.ProductoInfo, String> prMod = new TableColumn<>("Modalidad");
        prMod.setCellValueFactory(c -> new SimpleStringProperty(etiquetaModalidad(c.getValue().modalidad())));
        TableColumn<InventarioCostoService.ProductoInfo, String> prEst = new TableColumn<>("Estado");
        prEst.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().activo() ? "Activo" : "Inactivo"));
        tablaProductos.getColumns().addAll(prCod, prNom, prMod, prEst);
        tablaProductos.setPlaceholder(new Label("Aún no hay productos. Cree el primero con el formulario."));
        tablaProductos.setMinHeight(120);
        tablaProductos.setPrefHeight(200);
        tablaProductos.setMaxHeight(260);
        tablaProductos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaProductos.getStyleClass().add("inventory-plan-table");
        TextField prodCod = new TextField();
        prodCod.setPromptText("Ej. COL-FACT");
        UiCatalogos.ayuda(prodCod, "Código único del producto; agrupa las versiones y los lotes liberados.");
        TextField prodNom = new TextField();
        prodNom.setPromptText("Ej. Colibrí Facturación");
        UiCatalogos.ayuda(prodNom, "Nombre comercial del producto.");
        ComboBox<String> prodMod = new ComboBox<>(FXCollections.observableArrayList(
                "LICENCIA", "SAAS", "PROYECTO"));
        prodMod.setConverter(converterEtiquetas(Map.of(
                "LICENCIA", "Licencia", "SAAS", "Suscripción SaaS",
                "PROYECTO", "Proyecto a la medida")));
        prodMod.setValue("LICENCIA");
        UiCatalogos.ayuda(prodMod, "Tipo de producto digital; define cómo se costea y vende.");
        CheckBox prodActivo = new CheckBox("Producto activo");
        prodActivo.setSelected(true);
        tablaProductos.getSelectionModel().selectedItemProperty().addListener((o, a, sel) -> {
            if (sel == null) {
                return;
            }
            prodCod.setText(sel.codigo());
            prodNom.setText(sel.nombre());
            prodMod.setValue(sel.modalidad());
            prodActivo.setSelected(sel.activo());
        });
        Button guardarProducto = new Button("Guardar producto");
        guardarProducto.setOnAction(e -> ejecutar(() -> {
            context.inventario().guardarProducto(prodCod.getText().trim(), prodNom.getText().trim(),
                    prodMod.getValue(), prodActivo.isSelected());
            UiDialogs.info("Producto guardado",
                    "El producto quedó registrado en el catálogo de inventario digital.");
            refrescar();
        }));
        Button nuevoProducto = new Button("Nuevo");
        nuevoProducto.setOnAction(e -> {
            prodCod.clear();
            prodNom.clear();
            prodMod.setValue("LICENCIA");
            prodActivo.setSelected(true);
        });
        VBox catalogoProductos = tarjeta("Productos del catálogo digital",
                new Label("Productos que agrupan las versiones liberables. Un producto inactivo "
                        + "conserva su historial pero no admite versiones nuevas."),
                UiTables.conBusqueda(tablaProductos, "productos del catálogo",
                        p -> p.codigo() + " " + p.nombre() + " " + p.modalidad()
                                + (p.activo() ? "" : " inactivo")),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Código", prodCod),
                        ResponsiveLayouts.field("Nombre", prodNom),
                        ResponsiveLayouts.field("Modalidad", prodMod), prodActivo,
                        guardarProducto, nuevoProducto));

        tablaVersiones = new TableView<>();
        TableColumn<InventarioCostoService.VersionProductoInfo, String> vProd = new TableColumn<>("Producto");
        vProd.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().productoNombre()
                + " (" + c.getValue().productoCodigo() + ")"));
        TableColumn<InventarioCostoService.VersionProductoInfo, String> vEtiq = new TableColumn<>("Versión");
        vEtiq.setCellValueFactory(c -> new SimpleStringProperty("v" + c.getValue().versionLabel()));
        TableColumn<InventarioCostoService.VersionProductoInfo, String> vEst = new TableColumn<>("Estado");
        vEst.setCellValueFactory(c -> new SimpleStringProperty(etiquetaEstadoVersion(c.getValue().estado())));
        tablaVersiones.getColumns().addAll(vProd, vEtiq, vEst);
        tablaVersiones.setPlaceholder(new Label("Aún no hay versiones. Cree la primera con el formulario."));
        tablaVersiones.setMinHeight(120);
        tablaVersiones.setPrefHeight(200);
        tablaVersiones.setMaxHeight(260);
        tablaVersiones.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaVersiones.getStyleClass().add("inventory-plan-table");
        productoVersion = new ComboBox<>();
        productoVersion.setConverter(converterProducto());
        productoVersion.setPromptText("Producto");
        UiCatalogos.ayuda(productoVersion, "Producto al que pertenecerá la nueva versión.");
        TextField versionEtiqueta = new TextField();
        versionEtiqueta.setPromptText("Ej. 2.0");
        UiCatalogos.ayuda(versionEtiqueta, "Etiqueta de la versión; debe ser única dentro del producto.");
        Button crearVersion = new Button("Crear versión");
        crearVersion.setOnAction(e -> ejecutar(() -> {
            if (productoVersion.getValue() == null) {
                throw new IllegalArgumentException("Seleccione el producto de la versión.");
            }
            context.inventario().guardarVersion(productoVersion.getValue().id(),
                    versionEtiqueta.getText().trim());
            UiDialogs.info("Versión creada",
                    "La versión nace EN_PRODUCCION; libérela para poder crear lotes con ella.");
            versionEtiqueta.clear();
            refrescar();
        }));
        ComboBox<String> estadoVersion = new ComboBox<>();
        estadoVersion.setPromptText("Estado destino");
        Button cambiarEstadoVersion = new Button("Cambiar estado");
        cambiarEstadoVersion.setOnAction(e -> ejecutar(() -> {
            var sel = tablaVersiones.getSelectionModel().getSelectedItem();
            if (sel == null) {
                throw new IllegalArgumentException("Seleccione una versión de la tabla.");
            }
            if (estadoVersion.getValue() == null) {
                throw new IllegalArgumentException("Seleccione el estado destino.");
            }
            context.inventario().cambiarEstadoVersion(sel.id(), estadoVersion.getValue());
            UiDialogs.info("Estado actualizado",
                    "La versión quedó en " + etiquetaEstadoVersion(estadoVersion.getValue()) + ".");
            refrescar();
        }));
        tablaVersiones.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b == null) {
                return;
            }
            estadoVersion.setItems(FXCollections.observableArrayList(opcionesEstadoVersion(b.estado())));
            estadoVersion.setValue(null);
        });
        VBox catalogoVersiones = tarjeta("Versiones de producto",
                new Label("Cada versión recorre EN_PRODUCCION → LIBERADA → DESCONTINUADA. "
                        + "Sólo las LIBERADA admiten la liberación de nuevos lotes."),
                UiTables.conBusqueda(tablaVersiones, "versiones de producto",
                        v -> v.productoCodigo() + " " + v.productoNombre() + " v" + v.versionLabel()
                                + " " + v.estado()),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Producto", productoVersion),
                        ResponsiveLayouts.field("Versión", versionEtiqueta), crearVersion),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Estado destino", estadoVersion),
                        cambiarEstadoVersion));
        return new javafx.scene.Node[]{catalogoProductos, catalogoVersiones};
    }

    private static StringConverter<InventarioCostoService.VersionProductoInfo> converterVersion() {
        return new StringConverter<>() {
            @Override public String toString(InventarioCostoService.VersionProductoInfo value) {
                return value == null ? ""
                        : value.productoNombre() + " · v" + value.versionLabel();
            }
            @Override public InventarioCostoService.VersionProductoInfo fromString(String ignored) {
                return null;
            }
        };
    }

    private static StringConverter<InventarioCostoService.ProductoInfo> converterProducto() {
        return new StringConverter<>() {
            @Override public String toString(InventarioCostoService.ProductoInfo value) {
                return value == null ? "" : value.codigo() + " · " + value.nombre();
            }
            @Override public InventarioCostoService.ProductoInfo fromString(String ignored) {
                return null;
            }
        };
    }

    private static String etiquetaLote(InventarioCostoService.LoteInfo lote) {
        String version = lote.versionLabel() == null ? ""
                : " · " + lote.productoCodigo() + " v" + lote.versionLabel();
        return lote.codigo() + version + " · " + lote.disponibles() + " disp. · $"
                + lote.costoUnitario().toPlainString() + " c/u";
    }

    private static String etiquetaEstadoVersion(String estado) {
        return switch (estado == null ? "" : estado) {
            case "EN_PRODUCCION" -> "En producción";
            case "LIBERADA" -> "Liberada";
            case "DESCONTINUADA" -> "Descontinuada";
            default -> estado;
        };
    }

    private static java.util.List<String> opcionesEstadoVersion(String estado) {
        return switch (estado == null ? "" : estado) {
            case "EN_PRODUCCION" -> java.util.List.of("LIBERADA", "DESCONTINUADA");
            case "LIBERADA" -> java.util.List.of("DESCONTINUADA");
            default -> java.util.List.of();
        };
    }

    private static String etiquetaMovimiento(String tipo) {
        return switch (tipo == null ? "" : tipo) {
            case "LIBERACION" -> "Liberación de lote";
            case "ENTREGA" -> "Salida · entrega PEPS";
            case "DEVOLUCION" -> "Devolución";
            case "REVOCACION" -> "Revocación";
            default -> tipo;
        };
    }

    private javafx.scene.Node[] panelServicios(DatePicker fecha) {
        TableView<ParametrizacionService.PlanServicio> tablaPlanes = new TableView<>();
        TableColumn<ParametrizacionService.PlanServicio, String> cCod = new TableColumn<>("Código");
        cCod.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().codigo()));
        TableColumn<ParametrizacionService.PlanServicio, String> cNom = new TableColumn<>("Plan / servicio");
        cNom.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().nombre()));
        TableColumn<ParametrizacionService.PlanServicio, String> cMod = new TableColumn<>("Modalidad");
        cMod.setCellValueFactory(c -> new SimpleStringProperty(etiquetaModalidad(c.getValue().modalidad())));
        TableColumn<ParametrizacionService.PlanServicio, String> cTar = new TableColumn<>("Tarifa");
        cTar.setCellValueFactory(c -> new SimpleStringProperty("$" + c.getValue().tarifa().toPlainString()));
        TableColumn<ParametrizacionService.PlanServicio, String> cCta = new TableColumn<>("Cuenta ingreso");
        cCta.setCellValueFactory(c -> new SimpleStringProperty(nombreCuenta(c.getValue().cuentaIngreso())));
        TableColumn<ParametrizacionService.PlanServicio, String> cEst = new TableColumn<>("Estado");
        cEst.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().activo() ? "Activo" : "Inactivo"));
        tablaPlanes.getColumns().addAll(cCod, cNom, cMod, cTar, cCta, cEst);
        tablaPlanes.setItems(FXCollections.observableArrayList(context.parametrizacion().listarPlanes()));
        // TableView puede contraerse a la altura exclusiva del encabezado al
        // estar dentro de una VBox. Reservamos filas reales para que el
        // catálogo no parezca vacío cuando ya existen planes configurados.
        tablaPlanes.setMinHeight(136);
        tablaPlanes.setPrefHeight(188);
        tablaPlanes.setMaxHeight(220);
        tablaPlanes.setPlaceholder(new Label("Aún no hay planes configurados. Cree el primero con el formulario."));
        tablaPlanes.getStyleClass().add("inventory-plan-table");
        TextField pCod = new TextField();
        pCod.setPromptText("Ej. SaaS-STARTER");
        UiCatalogos.ayuda(pCod, "Código único del plan; se usa al facturar y costear.");
        TextField pNom = new TextField();
        pNom.setPromptText("Ej. Plan Starter mensual");
        UiCatalogos.ayuda(pNom, "Nombre comercial del plan o servicio.");
        TextField pTar = new TextField("50");
        UiCatalogos.ayuda(pTar, "Tarifa del plan en USD por período de facturación.");
        ComboBox<String> pMod = new ComboBox<>(FXCollections.observableArrayList("LICENCIA", "SAAS", "PROYECTO", "SERVICIO"));
        pMod.setConverter(converterEtiquetas(Map.of(
                "LICENCIA", "Licencia", "SAAS", "Suscripción SaaS",
                "PROYECTO", "Proyecto a la medida", "SERVICIO", "Servicio")));
        pMod.setValue("SAAS");
        UiCatalogos.ayuda(pMod, "Modalidad comercial del plan; define cómo se acumula el costo.");
        ComboBox<String> pFacturacion = new ComboBox<>(FXCollections.observableArrayList(
                "USUARIO_MES", "POR_PLAN", "POR_HITO", "POR_ENTREGA", "UNICA"));
        pFacturacion.setConverter(converterEtiquetas(Map.of(
                "USUARIO_MES", "Por usuario y mes", "POR_PLAN", "Por plan",
                "POR_HITO", "Por hito", "POR_ENTREGA", "Por entrega", "UNICA", "Pago único")));
        pFacturacion.setValue("USUARIO_MES");
        UiCatalogos.ayuda(pFacturacion, "Periodicidad con que se factura el plan.");
        ComboBox<Cuenta> pCta = UiCatalogos.campoCuenta(context, "410402",
                "Cuenta de ingreso donde se acreditan las ventas del plan; sólo se ofrecen cuentas de ingreso activas.",
                cuenta -> "INGRESOS".equalsIgnoreCase(cuenta.getRubro())
                        && cuenta.isAdmiteMovimiento()
                        && cuenta.getEstado() == sv.edu.ues.colibri.domain.model.EstadoCuenta.ACTIVA);
        ComboBox<ParametrizacionService.PlanServicio> planCosto = new ComboBox<>();
        configurarPlanSelector(planCosto, "SERVICIO");
        CheckBox planActivo = new CheckBox("Plan activo");
        planActivo.setSelected(true);
        tablaPlanes.getSelectionModel().selectedItemProperty().addListener((o, a, sel) -> {
            if (sel == null) {
                return;
            }
            pCod.setText(sel.codigo());
            pNom.setText(sel.nombre());
            pMod.setValue(sel.modalidad());
            pFacturacion.setValue(sel.tipoFacturacion());
            pTar.setText(sel.tarifa().toPlainString());
            UiCatalogos.seleccionarCuenta(pCta, sel.cuentaIngreso());
            planActivo.setSelected(sel.activo());
        });
        Button guardarPlan = new Button("Guardar plan");
        guardarPlan.setOnAction(e -> ejecutar(() -> {
            context.parametrizacion().guardarPlan(new ParametrizacionService.PlanServicio(
                    UUID.randomUUID(), pCod.getText(), pNom.getText(), pMod.getValue(),
                    UiCampos.importe(pTar.getText()), pFacturacion.getValue(), UiCatalogos.codigoCuenta(pCta),
                    planActivo.isSelected()));
            UiTables.reemplazarFilas(tablaPlanes, context.parametrizacion().listarPlanes());
            recargarPlanes(planCosto, "SERVICIO");
        }));
        Button nuevoPlan = new Button("Nuevo plan");
        nuevoPlan.setOnAction(e -> {
            pCod.clear();
            pNom.clear();
            pTar.setText("50");
            pMod.setValue("SAAS");
            pFacturacion.setValue("USUARIO_MES");
            UiCatalogos.seleccionarCuenta(pCta, "410402");
            planActivo.setSelected(true);
        });
        Button irCatalogoCuentas = new Button("Ir al catálogo de cuentas");
        irCatalogoCuentas.setOnAction(e -> {
            if (navegador != null) {
                navegador.ir("nav-parametrizacion-catalogo-de-cuentas");
            }
        });
        UiCatalogos.ayuda(irCatalogoCuentas, "Si la cuenta de ingreso del plan no existe, créela en el catálogo.");
        VBox catalogo = tarjeta("Catálogo de planes SaaS y servicios",
                UiTables.conBusqueda(tablaPlanes, "planes y servicios", planFila -> planFila.codigo()
                        + " " + planFila.nombre() + " " + planFila.modalidad() + " "
                        + planFila.tarifa() + " " + planFila.tipoFacturacion()
                        + " " + nombreCuenta(planFila.cuentaIngreso())
                        + (planFila.activo() ? "" : " inactivo")),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Código", pCod),
                        ResponsiveLayouts.field("Nombre", pNom),
                        ResponsiveLayouts.field("Modalidad", pMod),
                        ResponsiveLayouts.field("Facturación", pFacturacion),
                        ResponsiveLayouts.field("Tarifa", pTar),
                        ResponsiveLayouts.field("Cuenta ingreso", pCta), planActivo,
                        guardarPlan, nuevoPlan, irCatalogoCuentas));
        TextField costoDescripcion = new TextField();
        costoDescripcion.setPromptText("Ej. Horas de implementación");
        TextField costoCantidad = new TextField("1");
        costoCantidad.setPromptText("1");
        UiCatalogos.ayuda(costoCantidad, "Cantidad del recurso consumido (horas, licencias, GB, etc.).");
        TextField costoTarifa = new TextField("0");
        costoTarifa.setPromptText("0.00");
        UiCatalogos.ayuda(costoTarifa, "Tarifa unitaria en USD del recurso.");
        ComboBox<String> tipoCosto = new ComboBox<>(FXCollections.observableArrayList("MOD", "SERVICIO", "CIF"));
        tipoCosto.setConverter(converterEtiquetas(Map.of(
                "MOD", "MOD · Mano de obra directa", "SERVICIO", "Servicio / recurso directo",
                "CIF", "CIF · Costo indirecto")));
        tipoCosto.setValue("MOD");
        UiCatalogos.ayuda(tipoCosto, "Clasificación del costo que se acumula en el plan.");
        Label acumulado = new Label("Registre MOD, recursos directos o CIF en el plan facturado.");
        Button registrarCosto = new Button("Acumular costo del plan");
        registrarCosto.setOnAction(e -> ejecutar(() -> {
            if (planCosto.getValue() == null) {
                throw new IllegalArgumentException("Seleccione un plan de servicio facturado.");
            }
            var costoPlan = context.inventario().registrarConsumoPlan(planCosto.getValue().codigo(),
                    tipoCosto.getValue(), costoDescripcion.getText(),
                    UiCampos.importe(costoCantidad.getText()), UiCampos.importe(costoTarifa.getText()),
                    fecha.getValue());
            acumulado.setText("Plan %s · MOD $%s · Directos $%s · CIF $%s · Total $%s".formatted(
                    costoPlan.codigoPlan(), costoPlan.manoObra(), costoPlan.serviciosDirectos(),
                    costoPlan.indirectos(), costoPlan.total()));
            costoDescripcion.clear();
            refrescar();
        }));
        VBox costosPlan = tarjeta("Costos agregados por plan facturado",
                new Label("Para servicios distintos de licencias, SaaS y órdenes a medida, el plan es el objeto de costo."),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Fecha", fecha),
                        ResponsiveLayouts.field("Plan de servicio", planCosto),
                        ResponsiveLayouts.field("Elemento", tipoCosto)),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Descripción", costoDescripcion),
                        ResponsiveLayouts.field("Cantidad", costoCantidad),
                        ResponsiveLayouts.field("Tarifa", costoTarifa), registrarCosto), acumulado);
        return new javafx.scene.Node[]{catalogo, costosPlan};
    }

    private javafx.scene.Node[] panelMedida(DatePicker fecha) {
        TextField codigoOrden = new TextField("OS2026001");
        codigoOrden.setPromptText("Código de orden de servicio");
        Button cargarOs = new Button("Cargar costos de ejemplo ($1,800)");
        cargarOs.setOnAction(e -> ejecutar(() -> {
            if (!"OS2026001".equalsIgnoreCase(codigoOrden.getText().trim())) {
                throw new IllegalArgumentException("La carga de ejemplo corresponde únicamente a OS2026001.");
            }
            context.inventario().cargarConsumosOs2026001(fecha.getValue());
            UiDialogs.info("Costos acumulados", "Se registraron MOD, recursos técnicos y CIF para OS2026001.");
            refrescar();
        }));
        Button reconocer = new Button("Reconocer costo de la orden");
        reconocer.setOnAction(e -> ejecutar(() -> {
            var p = context.inventario().reconocerCostoOrden(codigoOrden.getText().trim(), fecha.getValue());
            UiDialogs.info("Costo reconocido", "Folio %s. Se descargaron los componentes acumulados de la orden.".formatted(p.getFolio()));
            refrescar();
        }));
        TextField horas = new TextField("100");
        horas.setPromptText("Horas");
        UiCatalogos.ayuda(horas, "Horas de mano de obra directa consumidas en la orden.");
        TextField tarifa = new TextField("12");
        tarifa.setPromptText("USD/hora");
        UiCatalogos.ayuda(tarifa, "Tarifa por hora de la mano de obra directa.");
        Button consumoManual = new Button("Registrar MOD");
        consumoManual.setOnAction(e -> ejecutar(() -> {
            context.inventario().registrarConsumoOrden(codigoOrden.getText().trim(), "MOD", "Horas de desarrollo",
                    UiCampos.importe(horas.getText()), UiCampos.importe(tarifa.getText()), fecha.getValue());
            refrescar();
        }));
        // --- Ciclo de vida de órdenes de servicio ---
        if (tablaOrdenes.getColumns().isEmpty()) {
            TableColumn<InventarioCostoService.OrdenRentabilidad, String> ocCod = new TableColumn<>("Código");
            ocCod.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().codigo()));
            TableColumn<InventarioCostoService.OrdenRentabilidad, String> ocDesc = new TableColumn<>("Descripción");
            ocDesc.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().descripcion()));
            TableColumn<InventarioCostoService.OrdenRentabilidad, String> ocEstado = new TableColumn<>("Estado");
            ocEstado.setCellValueFactory(c -> new SimpleStringProperty(etiquetaEstadoOrden(c.getValue().estado())));
            TableColumn<InventarioCostoService.OrdenRentabilidad, String> ocPres = new TableColumn<>("Presupuesto");
            ocPres.setCellValueFactory(c -> new SimpleStringProperty(dinero(c.getValue().presupuesto())));
            TableColumn<InventarioCostoService.OrdenRentabilidad, String> ocVenta = new TableColumn<>("Precio venta");
            ocVenta.setCellValueFactory(c -> new SimpleStringProperty(dinero(c.getValue().precioVenta())));
            TableColumn<InventarioCostoService.OrdenRentabilidad, String> ocCosto = new TableColumn<>("Costo");
            ocCosto.setCellValueFactory(c -> new SimpleStringProperty(dinero(c.getValue().costoAcumulado())));
            TableColumn<InventarioCostoService.OrdenRentabilidad, String> ocMargen = new TableColumn<>("Margen");
            ocMargen.setCellValueFactory(c -> new SimpleStringProperty(dinero(c.getValue().margen())));
            TableColumn<InventarioCostoService.OrdenRentabilidad, String> ocPct = new TableColumn<>("Margen %");
            ocPct.setCellValueFactory(c -> {
                var o = c.getValue();
                BigDecimal pct = o.precioVenta().signum() == 0 ? BigDecimal.ZERO
                        : o.margen().divide(o.precioVenta(), 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100));
                return new SimpleStringProperty(
                        pct.setScale(1, RoundingMode.HALF_UP).toPlainString() + " %");
            });
            tablaOrdenes.getColumns().addAll(ocCod, ocDesc, ocEstado, ocPres, ocVenta, ocCosto, ocMargen, ocPct);
            tablaOrdenes.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            tablaOrdenes.setPlaceholder(new Label("No hay órdenes de servicio registradas."));
            tablaOrdenes.setMinHeight(160);
            tablaOrdenes.setPrefHeight(280);
        }
        TextField ordCod = new TextField();
        ordCod.setPromptText("OS-2027-001");
        UiCatalogos.ayuda(ordCod, "Código único de la orden; después se usa para acumular y reconocer costos.");
        TextField ordDesc = new TextField();
        ordDesc.setPromptText("Descripción del trabajo");
        TextField ordPres = new TextField("0.00");
        ordPres.setPromptText("Presupuesto");
        TextField ordVenta = new TextField("0.00");
        ordVenta.setPromptText("Precio de venta");
        ComboBox<Tercero> clienteOrd = new ComboBox<>();
        clienteOrd.setConverter(converterTercero());
        clienteOrd.setPromptText("Cliente (opcional)");
        try {
            clienteOrd.setItems(FXCollections.observableArrayList(
                    context.operaciones().listarTerceros().stream().filter(Tercero::isCliente).toList()));
        } catch (Exception ignored) {
            clienteOrd.getItems().clear();
        }
        Button crearOrd = new Button("Crear orden");
        crearOrd.setOnAction(e -> ejecutar(() -> {
            BigDecimal pres = UiCampos.importeOCero(ordPres.getText());
            BigDecimal venta = UiCampos.importeOCero(ordVenta.getText());
            var creada = context.inventario().crearOrden(ordCod.getText().trim(), ordDesc.getText().trim(),
                    pres, venta, clienteOrd.getValue() == null ? null : clienteOrd.getValue().getId());
            UiDialogs.info("Orden creada", "Se registró " + creada.codigo() + " con estado ABIERTA.");
            ordCod.clear();
            ordDesc.clear();
            ordPres.setText("0.00");
            ordVenta.setText("0.00");
            clienteOrd.setValue(null);
            refrescar();
        }));
        ComboBox<String> estadoDestino = new ComboBox<>();
        estadoDestino.setPromptText("Estado destino");
        Button cambiarEstado = new Button("Cambiar estado");
        cambiarEstado.setOnAction(e -> ejecutar(() -> {
            var sel = tablaOrdenes.getSelectionModel().getSelectedItem();
            if (sel == null) {
                throw new IllegalArgumentException("Seleccione una orden de la tabla.");
            }
            if (estadoDestino.getValue() == null) {
                throw new IllegalArgumentException("Seleccione el estado destino.");
            }
            context.inventario().cambiarEstadoOrden(sel.codigo(), estadoDestino.getValue());
            UiDialogs.info("Estado actualizado",
                    sel.codigo() + " → " + etiquetaEstadoOrden(estadoDestino.getValue()));
            refrescar();
        }));
        tablaOrdenes.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            if (b == null) {
                return;
            }
            codigoOrden.setText(b.codigo());
            estadoDestino.setItems(FXCollections.observableArrayList(opcionesEstadoOrden(b.estado())));
            estadoDestino.setValue(null);
        });
        Button editarOrd = new Button("Editar seleccionada");
        editarOrd.setOnAction(e -> ejecutar(() -> {
            var sel = tablaOrdenes.getSelectionModel().getSelectedItem();
            if (sel == null) {
                throw new IllegalArgumentException("Seleccione una orden de la tabla.");
            }
            var detalle = context.inventario().ordenDetalle(sel.codigo());
            ordCod.setText(detalle.codigo());
            ordDesc.setText(detalle.descripcion());
            ordPres.setText(detalle.presupuesto().toPlainString());
            ordVenta.setText(detalle.precioVenta().toPlainString());
            clienteOrd.setValue(context.operaciones().listarTerceros().stream()
                    .filter(t -> detalle.clienteId() != null && detalle.clienteId().equals(t.getId()))
                    .findFirst().orElse(null));
            UiDialogs.info("Orden cargada", "Revise los datos y pulse «Guardar cambios»; "
                    + "sólo aplica a órdenes ABIERTA o EN_PROCESO.");
        }));
        Button guardarCambios = new Button("Guardar cambios");
        guardarCambios.setOnAction(e -> ejecutar(() -> {
            var sel = tablaOrdenes.getSelectionModel().getSelectedItem();
            if (sel == null) {
                throw new IllegalArgumentException("Seleccione la orden a modificar en la tabla.");
            }
            BigDecimal pres = UiCampos.importeOCero(ordPres.getText());
            BigDecimal venta = UiCampos.importeOCero(ordVenta.getText());
            context.inventario().actualizarOrden(sel.codigo(), ordDesc.getText().trim(),
                    pres, venta, clienteOrd.getValue() == null ? null : clienteOrd.getValue().getId());
            UiDialogs.info("Orden actualizada", "Los cambios de " + sel.codigo()
                    + " quedaron registrados en bitácora.");
            refrescar();
        }));
        VBox ciclo = tarjeta("Ciclo de vida de órdenes de servicio",
                new Label("Defina presupuesto y precio, acumule costos y avance el estado. "
                        + "Seleccione una fila para costearla o «Editar» para corregir sus datos."),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Código", ordCod),
                        ResponsiveLayouts.field("Descripción", ordDesc),
                        ResponsiveLayouts.field("Presupuesto", ordPres),
                        ResponsiveLayouts.field("Precio venta", ordVenta),
                        ResponsiveLayouts.field("Cliente", clienteOrd), crearOrd,
                        editarOrd, guardarCambios),
                UiTables.conBusqueda(tablaOrdenes, "órdenes de servicio",
                        o -> o.codigo() + " " + o.descripcion() + " " + o.estado()),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Estado destino", estadoDestino),
                        cambiarEstado));
        VBox costos = tarjeta("Costeo por órdenes de servicio",
                new Label("Acumula mano de obra directa, recursos directos y CIF aplicados sobre la orden."),
                ordenLbl,
                ResponsiveLayouts.row(ResponsiveLayouts.field("Fecha", fecha),
                        ResponsiveLayouts.field("Orden", codigoOrden), cargarOs, reconocer),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Horas MOD", horas),
                        ResponsiveLayouts.field("Tarifa", tarifa), consumoManual));
        return new javafx.scene.Node[]{ciclo, costos};
    }

    private javafx.scene.Node[] panelLicencias() {
        lotes.setPlaceholder(new Label("No hay lotes disponibles. Libere un lote desde Inventario → Digitales."));
        // El listado sirve como comprobante de existencias, no como área de
        // trabajo principal. Una altura acotada evita una pantalla vacía.
        lotes.setMinHeight(118);
        lotes.setPrefHeight(164);
        lotes.setMaxHeight(200);
        lotes.getStyleClass().add("inventory-lot-list");
        TextField unidadesAnalisis = new TextField("1");
        unidadesAnalisis.setPromptText("Unidades");
        UiCatalogos.ayuda(unidadesAnalisis, "Cantidad de unidades de salida para comparar los métodos de valoración.");
        Label estadoComparacion = new Label("Indique las unidades y calcule una salida hipotética.");
        estadoComparacion.getStyleClass().add("section-note");
        Label pepsCosto = valorComparacion("—");
        Label pepsSaldo = valorComparacion("—");
        Label promedioCosto = valorComparacion("—");
        Label promedioSaldo = valorComparacion("—");
        Label uepsCosto = valorComparacion("—");
        Label uepsSaldo = valorComparacion("—");
        GridPane comparativo = cuadriculaComparacion(pepsCosto, pepsSaldo, promedioCosto,
                promedioSaldo, uepsCosto, uepsSaldo);
        Button calcularComparativo = new Button("Comparar PEPS / promedio / UEPS");
        calcularComparativo.setOnAction(e -> ejecutar(() -> {
            var analisis = context.inventario().analizarValoracionInventario(
                    UiCampos.entero(unidadesAnalisis.getText(), "unidades para análisis"));
            pepsCosto.setText(dinero(analisis.costoPeps()));
            pepsSaldo.setText(dinero(analisis.saldoPeps()));
            promedioCosto.setText(dinero(analisis.costoPromedio()));
            promedioSaldo.setText(dinero(analisis.saldoPromedio()));
            uepsCosto.setText(dinero(analisis.costoUeps()));
            uepsSaldo.setText(dinero(analisis.saldoUeps()));
            estadoComparacion.setText("Salida hipotética de %d unidad(es). PEPS es el método aplicado "
                    + "en las ventas reales; promedio y UEPS se muestran sólo como referencia.".formatted(
                            analisis.unidadesSalida()));
        }));
        VBox existencias = tarjeta("Valoración de licencias disponibles",
                descripcionValoracion(),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Unidades para análisis", unidadesAnalisis),
                        calcularComparativo), estadoComparacion, comparativo,
                etiquetaExistencias(), lotes);
        return new javafx.scene.Node[]{existencias};
    }

    private javafx.scene.Node[] panelSaas(DatePicker fecha) {
        ComboBox<ParametrizacionService.PlanServicio> plan = new ComboBox<>();
        configurarPlanSelector(plan, "SAAS");
        TextField usuarios = new TextField("10");
        usuarios.setPromptText("Usuarios");
        UiCatalogos.ayuda(usuarios, "Cantidad de usuarios SaaS facturados en el mes del corte.");
        Label saasLbl = new Label("Registre usuarios-mes y calcule el costo por proceso para cada plan SaaS.");
        Button registrarUsuarios = new Button("Guardar usuarios-mes");
        registrarUsuarios.setOnAction(e -> ejecutar(() -> {
            if (plan.getValue() == null) {
                throw new IllegalArgumentException("Seleccione un plan SaaS.");
            }
            context.inventario().registrarUsuariosMesSaas(plan.getValue().codigo(), fecha.getValue(),
                    UiCampos.entero(usuarios.getText(), "usuarios del mes"));
            saasLbl.setText("Usuarios-mes guardados para %s en %s.".formatted(plan.getValue().codigo(),
                    fecha.getValue().withDayOfMonth(1)));
        }));
        Button saas = new Button("Calcular costo por plan");
        saas.setOnAction(e -> ejecutar(() -> {
            if (plan.getValue() == null) {
                throw new IllegalArgumentException("Seleccione un plan SaaS.");
            }
            var desde = fecha.getValue().withDayOfMonth(1);
            var desglose = context.inventario().costoSaasPorProcesoPlan(plan.getValue().codigo(), desde,
                    fecha.getValue());
            String avisoCero = desglose.totalAsignado().signum() == 0
                    ? " · Sin gastos en las cuentas CIF " + nombreCuenta("510201") + ", "
                            + nombreCuenta("510202") + " y " + nombreCuenta("510203")
                            + " del período: registre primero la partida de costos indirectos."
                    : "";
            saasLbl.setText(("%s: %d de %d usuarios-mes · Alojamiento $%s · Soporte $%s · "
                    + "Infraestructura $%s · Total asignado $%s · Costo $%s por usuario-mes%s").formatted(
                    desglose.codigoPlan(), desglose.usuariosMesPlan(), desglose.usuariosMesTotal(),
                    desglose.alojamiento(), desglose.soporte(), desglose.infraestructuraCompartida(),
                    desglose.totalAsignado(), desglose.costoUsuarioMes(), avisoCero));
        }));
        VBox card = tarjeta("Costeo por procesos / servidor / usuario-mes",
                ResponsiveLayouts.row(ResponsiveLayouts.field("Corte", fecha),
                        ResponsiveLayouts.field("Plan SaaS", plan),
                        ResponsiveLayouts.field("Usuarios SaaS del mes", usuarios), registrarUsuarios, saas),
                saasLbl);
        return new javafx.scene.Node[]{card};
    }

    private void configurarSelectores() {
        cliente.setConverter(converterTercero());
        licenciaGestion.setConverter(new StringConverter<>() {
            @Override public String toString(InventarioCostoService.LicenciaEntregadaInfo value) {
                if (value == null) {
                    return "";
                }
                String cliente = value.cliente() == null || value.cliente().isBlank()
                        ? "" : " · " + value.cliente();
                return value.codigoLote() + " · #" + value.numero() + cliente
                        + (value.fechaEntrega() == null ? "" : " · " + value.fechaEntrega());
            }
            @Override public InventarioCostoService.LicenciaEntregadaInfo fromString(String ignored) {
                return null;
            }
        });
    }

    private static StringConverter<Tercero> converterTercero() {
        return new StringConverter<>() {
            @Override public String toString(Tercero value) {
                if (value == null) {
                    return "";
                }
                String nit = value.getNit() == null || value.getNit().isBlank() ? "" : value.getNit() + " · ";
                return nit + value.getRazonSocial();
            }
            @Override public Tercero fromString(String ignored) { return null; }
        };
    }

    private static StringConverter<String> converterEtiquetas(Map<String, String> etiquetas) {
        return new StringConverter<>() {
            @Override public String toString(String value) {
                return value == null ? "" : etiquetas.getOrDefault(value, value);
            }
            @Override public String fromString(String text) {
                for (Map.Entry<String, String> e : etiquetas.entrySet()) {
                    if (e.getValue().equals(text)) {
                        return e.getKey();
                    }
                }
                return text;
            }
        };
    }

    private void configurarPlanSelector(ComboBox<ParametrizacionService.PlanServicio> selector, String modalidad) {
        selector.setConverter(new StringConverter<>() {
            @Override public String toString(ParametrizacionService.PlanServicio value) {
                return value == null ? "" : value.codigo() + " · " + value.nombre();
            }
            @Override public ParametrizacionService.PlanServicio fromString(String ignored) { return null; }
        });
        recargarPlanes(selector, modalidad);
    }

    private void recargarPlanes(ComboBox<ParametrizacionService.PlanServicio> selector, String modalidad) {
        String seleccionado = selector.getValue() == null ? null : selector.getValue().codigo();
        var planes = context.parametrizacion().listarPlanes().stream()
                .filter(p -> p.activo() && modalidad.equals(p.modalidad())).toList();
        selector.setItems(FXCollections.observableArrayList(planes));
        if (seleccionado != null) {
            planes.stream().filter(p -> seleccionado.equals(p.codigo())).findFirst().ifPresent(selector::setValue);
        }
        if (selector.getValue() == null && !planes.isEmpty()) {
            selector.setValue(planes.getFirst());
        }
    }

    private static String[] encabezadoPanel(Panel panel) {
        return switch (panel) {
            case DIGITALES -> new String[]{"Digitales",
                    "Licencias y productos digitales valuados por PEPS."};
            case SERVICIOS -> new String[]{"Servicios / Accesos",
                    "Planes de servicio, accesos y costos por orden de trabajo."};
            case MEDIDA -> new String[]{"A la medida",
                    "Proyectos de software a medida: estimación, ejecución y costos."};
            case LICENCIAS -> new String[]{"Licencias descargables",
                    "Versiones, lotes liberados y ventas con costo PEPS."};
            case SAAS -> new String[]{"Suscripción SaaS",
                    "Planes por usuario-mes, hitos y costos recurrentes."};
            case KARDEX -> new String[]{"Kárdex de licencias",
                    "Movimientos cronológicos por lote con saldo de unidades y valor."};
            case PRODUCTOS -> new String[]{"Productos y versiones",
                    "Catálogo de productos digitales y el ciclo de vida de sus versiones."};
        };
    }

    private VBox tarjeta(String titulo, javafx.scene.Node... contenido) {
        Label heading = new Label(titulo);
        heading.getStyleClass().add("section-heading");
        VBox card = new VBox(10);
        card.getChildren().add(heading);
        card.getChildren().addAll(contenido);
        card.getStyleClass().add("form-card");
        return card;
    }

    private static String etiquetaModalidad(String modalidad) {
        return switch (modalidad == null ? "" : modalidad) {
            case "LICENCIA" -> "Licencia";
            case "SAAS" -> "SaaS";
            case "PROYECTO" -> "Proyecto a medida";
            case "SERVICIO" -> "Servicio";
            default -> modalidad;
        };
    }

    private void ejecutar(Accion action) {
        try {
            action.run();
        } catch (Exception ex) {
            UiDialogs.errorDe(ex);
        }
    }

    private Label valorComparacion(String texto) {
        Label valor = new Label(texto);
        valor.getStyleClass().add("valuation-value");
        return valor;
    }

    private GridPane cuadriculaComparacion(Label pepsCosto, Label pepsSaldo,
                                            Label promedioCosto, Label promedioSaldo,
                                            Label uepsCosto, Label uepsSaldo) {
        GridPane tabla = new GridPane();
        tabla.setHgap(0);
        tabla.setVgap(0);
        tabla.setPadding(new Insets(0));
        tabla.getStyleClass().add("valuation-grid");
        String[] encabezados = {"Método", "Costo de salida", "Saldo posterior"};
        for (int columna = 0; columna < encabezados.length; columna++) {
            Label encabezado = new Label(encabezados[columna]);
            encabezado.getStyleClass().add("valuation-header");
            encabezado.setMaxWidth(Double.MAX_VALUE);
            GridPane.setHgrow(encabezado, Priority.ALWAYS);
            tabla.add(encabezado, columna, 0);
        }
        agregarFilaValoracion(tabla, 1, "PEPS (aplicado)", pepsCosto, pepsSaldo);
        agregarFilaValoracion(tabla, 2, "Promedio ponderado", promedioCosto, promedioSaldo);
        agregarFilaValoracion(tabla, 3, "UEPS (referencia)", uepsCosto, uepsSaldo);
        for (int columna = 0; columna < 3; columna++) {
            javafx.scene.layout.ColumnConstraints constraint = new javafx.scene.layout.ColumnConstraints();
            constraint.setPercentWidth(columna == 0 ? 40 : 30);
            constraint.setHgrow(Priority.ALWAYS);
            tabla.getColumnConstraints().add(constraint);
        }
        return tabla;
    }

    private void agregarFilaValoracion(GridPane tabla, int fila, String metodo,
                                       Label costo, Label saldo) {
        Label etiqueta = new Label(metodo);
        etiqueta.getStyleClass().add("valuation-method");
        etiqueta.setMaxWidth(Double.MAX_VALUE);
        costo.setMaxWidth(Double.MAX_VALUE);
        saldo.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(etiqueta, Priority.ALWAYS);
        GridPane.setHgrow(costo, Priority.ALWAYS);
        GridPane.setHgrow(saldo, Priority.ALWAYS);
        tabla.add(etiqueta, 0, fila);
        tabla.add(costo, 1, fila);
        tabla.add(saldo, 2, fila);
    }

    private Label descripcionValoracion() {
        Label descripcion = new Label("Cada licencia conserva el costo que recibió al liberar su lote. "
                + "La comparación usa esos importes exactos; sólo PEPS registra salidas reales.");
        descripcion.setWrapText(true);
        descripcion.getStyleClass().add("section-note");
        return descripcion;
    }

    private Label etiquetaExistencias() {
        Label etiqueta = new Label("Lotes y unidades disponibles");
        etiqueta.getStyleClass().add("data-label");
        return etiqueta;
    }

    private static String dinero(BigDecimal monto) {
        return "$" + monto.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private void refrescar() {
        refrescarOrdenPrincipal();
        refrescarOrdenes();
        refrescarClientes();
        refrescarLotes();
        refrescarLicenciasEntregadas();
        refrescarVersionesLiberables();
        refrescarCatalogo();
    }

    private void refrescarVersionesLiberables() {
        try {
            var actuales = context.inventario().listarVersiones().stream()
                    .filter(v -> "LIBERADA".equals(v.estado())).toList();
            UUID sel = versionLote.getValue() == null ? null : versionLote.getValue().id();
            versionLote.setItems(FXCollections.observableArrayList(actuales));
            actuales.stream().filter(v -> v.id().equals(sel))
                    .findFirst().ifPresent(versionLote::setValue);
            if (versionLote.getValue() == null && !actuales.isEmpty()) {
                versionLote.setValue(actuales.getFirst());
            }
        } catch (Exception ignored) {
            versionLote.getItems().clear();
        }
    }

    private void refrescarCatalogo() {
        if (tablaProductos == null || tablaVersiones == null) {
            return;
        }
        try {
            var productos = context.inventario().listarProductos();
            var selP = tablaProductos.getSelectionModel().getSelectedItem();
            String codSel = selP == null ? null : selP.codigo();
            UiTables.reemplazarFilas(tablaProductos, productos);
            if (codSel != null) {
                productos.stream().filter(p -> p.codigo().equals(codSel)).findFirst()
                        .ifPresent(tablaProductos.getSelectionModel()::select);
            }
        } catch (Exception ignored) {
            UiTables.reemplazarFilas(tablaProductos, java.util.List.of());
        }
        try {
            var versiones = context.inventario().listarVersiones();
            var selV = tablaVersiones.getSelectionModel().getSelectedItem();
            UUID idSel = selV == null ? null : selV.id();
            UiTables.reemplazarFilas(tablaVersiones, versiones);
            if (idSel != null) {
                versiones.stream().filter(v -> v.id().equals(idSel)).findFirst()
                        .ifPresent(tablaVersiones.getSelectionModel()::select);
            }
        } catch (Exception ignored) {
            UiTables.reemplazarFilas(tablaVersiones, java.util.List.of());
        }
        if (productoVersion != null) {
            try {
                var activos = context.inventario().listarProductos().stream()
                        .filter(InventarioCostoService.ProductoInfo::activo).toList();
                UUID sel = productoVersion.getValue() == null ? null : productoVersion.getValue().id();
                productoVersion.setItems(FXCollections.observableArrayList(activos));
                activos.stream().filter(p -> p.id().equals(sel))
                        .findFirst().ifPresent(productoVersion::setValue);
                if (productoVersion.getValue() == null && !activos.isEmpty()) {
                    productoVersion.setValue(activos.getFirst());
                }
            } catch (Exception ignored) {
                productoVersion.getItems().clear();
            }
        }
    }

    private void refrescarOrdenes() {
        try {
            var sel = tablaOrdenes.getSelectionModel().getSelectedItem();
            String codigoSel = sel == null ? null : sel.codigo();
            var lista = context.inventario().listarOrdenesRentabilidad();
            UiTables.reemplazarFilas(tablaOrdenes, lista);
            if (codigoSel != null) {
                lista.stream().filter(o -> o.codigo().equals(codigoSel)).findFirst()
                        .ifPresent(tablaOrdenes.getSelectionModel()::select);
            }
        } catch (Exception ignored) {
            UiTables.reemplazarFilas(tablaOrdenes, java.util.List.of());
        }
    }

    private static String etiquetaEstadoOrden(String estado) {
        return switch (estado == null ? "" : estado) {
            case "ABIERTA" -> "Abierta";
            case "EN_PROCESO" -> "En proceso";
            case "ENTREGADA" -> "Entregada";
            case "CERRADA" -> "Cerrada";
            case "CANCELADA" -> "Cancelada";
            default -> estado == null ? "—" : estado;
        };
    }

    private static java.util.List<String> opcionesEstadoOrden(String estado) {
        return switch (estado == null ? "" : estado) {
            case "ABIERTA" -> java.util.List.of("EN_PROCESO", "CANCELADA");
            case "EN_PROCESO" -> java.util.List.of("ENTREGADA", "CANCELADA");
            case "ENTREGADA" -> java.util.List.of("CERRADA");
            default -> java.util.List.of();
        };
    }

    /** Una ausencia de la orden demostrativa no debe impedir mostrar inventario real. */
    private void refrescarOrdenPrincipal() {
        try {
            InventarioCostoService.OrdenInfo o = context.inventario().ordenPrincipal();
            BigDecimal pct = o.precioVenta().signum() == 0 ? BigDecimal.ZERO
                    : o.margen().divide(o.precioVenta(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            ordenLbl.setText("%s · Costo acumulado $%s · Precio $%s · Margen $%s (%s%%)%s".formatted(
                    o.codigo(), o.costoAcumulado(), o.precioVenta(), o.margen(), pct.setScale(2, RoundingMode.HALF_UP),
                    pct.compareTo(new BigDecimal("35")) < 0 ? "  — ALERTA: margen bajo" : ""));
        } catch (Exception ex) {
            ordenLbl.setText("Seleccione una orden de servicio para consultar sus costos.");
        }
    }

    private void refrescarClientes() {
        try {
            cliente.setItems(FXCollections.observableArrayList(
                    context.operaciones().listarTerceros().stream().filter(Tercero::isCliente).toList()));
        } catch (Exception ignored) {
            cliente.getItems().clear();
        }
    }

    private void refrescarLotes() {
        try {
            var disponibles = context.inventario().listarLotes();
            lotes.setItems(FXCollections.observableArrayList(disponibles.stream()
                    .map(l -> "%s%s  ·  Disponibles: %d  ·  Costo promedio por unidad: $%s".formatted(
                            l.codigo(),
                            l.versionLabel() == null ? ""
                                    : " (" + l.productoCodigo() + " v" + l.versionLabel() + ")",
                            l.disponibles(), l.costoUnitario())).toList()));
            UUID sel = loteDeterioro.getValue() == null ? null : loteDeterioro.getValue().id();
            loteDeterioro.setItems(FXCollections.observableArrayList(disponibles));
            disponibles.stream().filter(l -> l.id().equals(sel))
                    .findFirst().ifPresent(loteDeterioro::setValue);
        } catch (Exception ignored) {
            lotes.getItems().clear();
            loteDeterioro.getItems().clear();
        }
    }

    private void refrescarLicenciasEntregadas() {
        try {
            var actuales = context.inventario().listarLicenciasEntregadas();
            UUID seleccionada = licenciaGestion.getValue() == null ? null : licenciaGestion.getValue().id();
            licenciaGestion.setItems(FXCollections.observableArrayList(actuales));
            actuales.stream().filter(l -> l.id().equals(seleccionada))
                    .findFirst().ifPresent(licenciaGestion::setValue);
        } catch (Exception ignored) {
            licenciaGestion.getItems().clear();
        }
    }

    private LocalDate fechaPeriodoAbierto() {
        return context.periodos().periodoAbierto().map(p -> p.getFechaInicio()).orElse(LocalDate.now());
    }

    @FunctionalInterface
    private interface Accion { void run() throws Exception; }
}
