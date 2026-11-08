package sv.edu.ues.colibri.desktop.ui.views;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.application.configuracion.ParametrizacionService;
import sv.edu.ues.colibri.application.inventario.InventarioCostoService;
import sv.edu.ues.colibri.desktop.ui.ResponsiveLayouts;
import sv.edu.ues.colibri.desktop.ui.UiDialogs;
import sv.edu.ues.colibri.domain.model.Tercero;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Vista operativa para valorar inventario y costear órdenes, servicios y licencias.
 */
public class InventarioCostosView extends VBox {

    public enum Panel {
        DIGITALES, SERVICIOS, MEDIDA, LICENCIAS, SAAS
    }

    private final AppContext context;
    private final Label ordenLbl = new Label();
    private final ListView<String> lotes = new ListView<>();
    private final ComboBox<Tercero> cliente = new ComboBox<>();

    /**
     * Crea la vista de costeo para el panel funcional indicado.
     *
     * @param context contexto de aplicación con acceso a inventario y parametrización
     * @param panel sección funcional a presentar
     */
    public InventarioCostosView(AppContext context, Panel panel) {
        this.context = context;
        setSpacing(16);
        setPadding(new Insets(8, 0, 0, 0));
        DatePicker fecha = new DatePicker(fechaPeriodoAbierto());
        configurarSelectores();

        switch (panel) {
            case DIGITALES -> getChildren().addAll(panelDigitales(fecha));
            case SERVICIOS -> getChildren().addAll(panelServicios(fecha));
            case MEDIDA -> getChildren().addAll(panelMedida(fecha));
            case LICENCIAS -> getChildren().addAll(panelLicencias());
            case SAAS -> getChildren().addAll(panelSaas(fecha));
        }
        refrescar();
    }

    private javafx.scene.Node[] panelDigitales(DatePicker fecha) {
        TextField codigoLote = new TextField();
        codigoLote.setPromptText("Ej. LOTE-INV-2026-01");
        TextField unidades = new TextField();
        unidades.setPromptText("Cantidad de licencias");
        TextField costo = new TextField();
        costo.setPromptText("Costo total de liberación");
        Button liberar = new Button("Liberar lote");
        liberar.setOnAction(e -> ejecutar(() -> {
            var info = context.inventario().liberarLote(codigoLote.getText().trim(),
                    Integer.parseInt(unidades.getText().trim()), new BigDecimal(costo.getText().trim()), fecha.getValue());
            UiDialogs.info("Lote liberado", "%s: costo unitario $%s · %d licencias disponibles".formatted(
                    info.codigo(), info.costoUnitario(), info.disponibles()));
            codigoLote.clear();
            unidades.clear();
            costo.clear();
            refrescar();
        }));
        VBox liberacion = tarjeta("Liberación de licencias",
                ResponsiveLayouts.row(ResponsiveLayouts.field("Fecha", fecha),
                        ResponsiveLayouts.field("Código del lote", codigoLote),
                        ResponsiveLayouts.field("Unidades", unidades),
                        ResponsiveLayouts.field("Costo total (USD)", costo), liberar));

        TextField precio = new TextField();
        precio.setPromptText("Precio neto, sin IVA");
        Button vender = new Button("Registrar venta y costo");
        vender.setOnAction(e -> ejecutar(() -> {
            if (cliente.getValue() == null) {
                throw new IllegalArgumentException("Seleccione el cliente al que se concede la licencia.");
            }
            var p = context.inventario().venderLicenciaPeps(cliente.getValue().getId(),
                    fecha.getValue(), new BigDecimal(precio.getText().trim()), null);
            UiDialogs.info("Venta contabilizada", "Folio %s. Se aplicaron ingreso, IVA y costo de ventas por PEPS.".formatted(p.getFolio()));
            precio.clear();
            refrescar();
        }));
        VBox venta = tarjeta("Concesión de licencia",
                new Label("PEPS automático: el sistema toma la licencia disponible del lote liberado primero."),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Cliente", cliente),
                        ResponsiveLayouts.field("Precio neto (USD)", precio), vender));

        lotes.setPlaceholder(new Label("Aún no se han liberado lotes de licencias."));
        lotes.setMinHeight(170);
        lotes.setMaxHeight(Double.MAX_VALUE);
        VBox existencias = tarjeta("Inventario perpetuo PEPS (disponible y entregada)", lotes);
        return new javafx.scene.Node[]{liberacion, venta, existencias};
    }

    private javafx.scene.Node[] panelServicios(DatePicker fecha) {
        TableView<ParametrizacionService.PlanServicio> tablaPlanes = new TableView<>();
        TableColumn<ParametrizacionService.PlanServicio, String> cCod = new TableColumn<>("Código");
        cCod.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().codigo()));
        TableColumn<ParametrizacionService.PlanServicio, String> cNom = new TableColumn<>("Plan / servicio");
        cNom.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().nombre()));
        TableColumn<ParametrizacionService.PlanServicio, String> cMod = new TableColumn<>("Modalidad");
        cMod.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().modalidad()));
        TableColumn<ParametrizacionService.PlanServicio, String> cTar = new TableColumn<>("Tarifa");
        cTar.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().tarifa().toPlainString()));
        tablaPlanes.getColumns().addAll(cCod, cNom, cMod, cTar);
        tablaPlanes.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        tablaPlanes.setItems(FXCollections.observableArrayList(context.parametrizacion().listarPlanes()));
        tablaPlanes.setMinHeight(180);
        tablaPlanes.setMaxHeight(Double.MAX_VALUE);
        TextField pCod = new TextField();
        TextField pNom = new TextField();
        TextField pTar = new TextField("50");
        ComboBox<String> pMod = new ComboBox<>(FXCollections.observableArrayList("LICENCIA", "SAAS", "PROYECTO", "SERVICIO"));
        pMod.setValue("SAAS");
        ComboBox<String> pFacturacion = new ComboBox<>(FXCollections.observableArrayList(
                "USUARIO_MES", "POR_PLAN", "POR_HITO", "POR_ENTREGA", "UNICA"));
        pFacturacion.setValue("USUARIO_MES");
        TextField pCta = new TextField("410402");
        ComboBox<ParametrizacionService.PlanServicio> planCosto = new ComboBox<>();
        configurarPlanSelector(planCosto, "SERVICIO");
        Button guardarPlan = new Button("Guardar plan");
        guardarPlan.setOnAction(e -> ejecutar(() -> {
            context.parametrizacion().guardarPlan(new ParametrizacionService.PlanServicio(
                    UUID.randomUUID(), pCod.getText(), pNom.getText(), pMod.getValue(),
                    new BigDecimal(pTar.getText()), pFacturacion.getValue(), pCta.getText(), true));
            tablaPlanes.setItems(FXCollections.observableArrayList(context.parametrizacion().listarPlanes()));
            recargarPlanes(planCosto, "SERVICIO");
        }));
        VBox catalogo = tarjeta("Catálogo de planes SaaS y servicios de desarrollo/mantenimiento",
                tablaPlanes,
                ResponsiveLayouts.row(ResponsiveLayouts.field("Código", pCod),
                        ResponsiveLayouts.field("Nombre", pNom),
                        ResponsiveLayouts.field("Modalidad", pMod),
                        ResponsiveLayouts.field("Facturación", pFacturacion),
                        ResponsiveLayouts.field("Tarifa", pTar),
                        ResponsiveLayouts.field("Cuenta ingreso", pCta), guardarPlan));
        TextField costoDescripcion = new TextField();
        costoDescripcion.setPromptText("Ej. Horas de implementación");
        TextField costoCantidad = new TextField("1");
        TextField costoTarifa = new TextField("0");
        ComboBox<String> tipoCosto = new ComboBox<>(FXCollections.observableArrayList("MOD", "SERVICIO", "CIF"));
        tipoCosto.setValue("MOD");
        Label acumulado = new Label("Registre MOD, recursos directos o CIF en el plan facturado.");
        Button registrarCosto = new Button("Acumular costo del plan");
        registrarCosto.setOnAction(e -> ejecutar(() -> {
            if (planCosto.getValue() == null) {
                throw new IllegalArgumentException("Seleccione un plan de servicio facturado.");
            }
            var costoPlan = context.inventario().registrarConsumoPlan(planCosto.getValue().codigo(),
                    tipoCosto.getValue(), costoDescripcion.getText(),
                    new BigDecimal(costoCantidad.getText().trim()), new BigDecimal(costoTarifa.getText().trim()),
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
        TextField tarifa = new TextField("12");
        Button consumoManual = new Button("Registrar MOD");
        consumoManual.setOnAction(e -> ejecutar(() -> {
            context.inventario().registrarConsumoOrden(codigoOrden.getText().trim(), "MOD", "Horas de desarrollo",
                    new BigDecimal(horas.getText()), new BigDecimal(tarifa.getText()), fecha.getValue());
            refrescar();
        }));
        VBox costos = tarjeta("Costeo por órdenes de servicio",
                new Label("Acumula mano de obra directa, recursos directos y CIF aplicados sobre la orden."),
                ordenLbl,
                ResponsiveLayouts.row(ResponsiveLayouts.field("Fecha", fecha),
                        ResponsiveLayouts.field("Orden", codigoOrden), cargarOs, reconocer),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Horas MOD", horas),
                        ResponsiveLayouts.field("Tarifa", tarifa), consumoManual));
        return new javafx.scene.Node[]{costos};
    }

    private javafx.scene.Node[] panelLicencias() {
        lotes.setPlaceholder(new Label("Libere lotes desde Inventario → Digitales."));
        lotes.setMinHeight(220);
        lotes.setMaxHeight(Double.MAX_VALUE);
        TextField unidadesAnalisis = new TextField("1");
        Label comparativo = new Label("Comparativo analítico: no genera asiento ni habilita UEPS.");
        Button calcularComparativo = new Button("Comparar PEPS / promedio / UEPS");
        calcularComparativo.setOnAction(e -> ejecutar(() -> {
            var analisis = context.inventario().analizarValoracionInventario(
                    Integer.parseInt(unidadesAnalisis.getText().trim()));
            comparativo.setText("Salida %d unidades · PEPS costo $%s / saldo $%s · Promedio costo $%s / saldo $%s · "
                    + "UEPS costo $%s / saldo $%s (sólo comparación; las ventas reales usan PEPS).".formatted(
                    analisis.unidadesSalida(), analisis.costoPeps(), analisis.saldoPeps(),
                    analisis.costoPromedio(), analisis.saldoPromedio(), analisis.costoUeps(), analisis.saldoUeps()));
        }));
        VBox existencias = tarjeta("Costo promedio por versión y PEPS exacto",
                new Label("El costo de cada licencia se fija al liberar el lote y la salida usa automáticamente el lote disponible más antiguo."),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Unidades para análisis", unidadesAnalisis),
                        calcularComparativo), comparativo,
                lotes);
        return new javafx.scene.Node[]{existencias};
    }

    private javafx.scene.Node[] panelSaas(DatePicker fecha) {
        ComboBox<ParametrizacionService.PlanServicio> plan = new ComboBox<>();
        configurarPlanSelector(plan, "SAAS");
        TextField usuarios = new TextField("10");
        Label saasLbl = new Label("Registre usuarios-mes y calcule el costo por proceso para cada plan SaaS.");
        Button registrarUsuarios = new Button("Guardar usuarios-mes");
        registrarUsuarios.setOnAction(e -> ejecutar(() -> {
            if (plan.getValue() == null) {
                throw new IllegalArgumentException("Seleccione un plan SaaS.");
            }
            context.inventario().registrarUsuariosMesSaas(plan.getValue().codigo(), fecha.getValue(),
                    Integer.parseInt(usuarios.getText().trim()));
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
            saasLbl.setText("%s: %d de %d usuarios-mes · Alojamiento $%s · Soporte $%s · "
                    + "Infraestructura $%s · Total asignado $%s · Costo $%s por usuario-mes".formatted(
                    desglose.codigoPlan(), desglose.usuariosMesPlan(), desglose.usuariosMesTotal(),
                    desglose.alojamiento(), desglose.soporte(), desglose.infraestructuraCompartida(),
                    desglose.totalAsignado(), desglose.costoUsuarioMes()));
        }));
        VBox card = tarjeta("Costeo por procesos / servidor / usuario-mes",
                ResponsiveLayouts.row(ResponsiveLayouts.field("Corte", fecha),
                        ResponsiveLayouts.field("Plan SaaS", plan),
                        ResponsiveLayouts.field("Usuarios SaaS del mes", usuarios), registrarUsuarios, saas),
                saasLbl);
        return new javafx.scene.Node[]{card};
    }

    private void configurarSelectores() {
        cliente.setConverter(new StringConverter<>() {
            @Override public String toString(Tercero value) {
                return value == null ? "" : value.getRazonSocial();
            }
            @Override public Tercero fromString(String ignored) { return null; }
        });
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

    private VBox tarjeta(String titulo, javafx.scene.Node... contenido) {
        Label heading = new Label(titulo);
        heading.getStyleClass().add("section-heading");
        VBox card = new VBox(10);
        card.getChildren().add(heading);
        card.getChildren().addAll(contenido);
        card.getStyleClass().add("form-card");
        return card;
    }

    private void ejecutar(Accion action) {
        try {
            action.run();
        } catch (Exception ex) {
            UiDialogs.error(ex.getMessage());
        }
    }

    private void refrescar() {
        try {
            InventarioCostoService.OrdenInfo o = context.inventario().ordenPrincipal();
            BigDecimal pct = o.precioVenta().signum() == 0 ? BigDecimal.ZERO
                    : o.margen().divide(o.precioVenta(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            ordenLbl.setText("%s · Costo acumulado $%s · Precio $%s · Margen $%s (%s%%)%s".formatted(
                    o.codigo(), o.costoAcumulado(), o.precioVenta(), o.margen(), pct.setScale(2, RoundingMode.HALF_UP),
                    pct.compareTo(new BigDecimal("35")) < 0 ? "  — ALERTA: margen bajo" : ""));
            cliente.setItems(FXCollections.observableArrayList(
                    context.operaciones().listarTerceros().stream().filter(Tercero::isCliente).toList()));
            lotes.setItems(FXCollections.observableArrayList(context.inventario().listarLotes().stream()
                    .map(l -> "%s  ·  Disponibles: %d  ·  Costo promedio por unidad: $%s".formatted(
                            l.codigo(), l.disponibles(), l.costoUnitario())).toList()));
        } catch (Exception ex) {
            ordenLbl.setText(ex.getMessage());
        }
    }

    private LocalDate fechaPeriodoAbierto() {
        return context.periodos().periodoAbierto().map(p -> p.getFechaInicio()).orElse(LocalDate.now());
    }

    @FunctionalInterface
    private interface Accion { void run() throws Exception; }
}
