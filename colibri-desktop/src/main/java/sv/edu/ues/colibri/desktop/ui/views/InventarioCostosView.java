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
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.application.configuracion.ParametrizacionService;
import sv.edu.ues.colibri.application.inventario.InventarioCostoService;
import sv.edu.ues.colibri.desktop.ui.ResponsiveLayouts;
import sv.edu.ues.colibri.desktop.ui.UiCatalogos;
import sv.edu.ues.colibri.desktop.ui.UiDialogs;
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
        DIGITALES, SERVICIOS, MEDIDA, LICENCIAS, SAAS, KARDEX
    }

    private final AppContext context;
    private final Label ordenLbl = new Label();
    private final ListView<String> lotes = new ListView<>();
    private final ComboBox<Tercero> cliente = new ComboBox<>();
    private final ComboBox<InventarioCostoService.LicenciaEntregadaInfo> licenciaGestion = new ComboBox<>();

    /**
     * Crea la vista de costeo para el panel funcional indicado.
     *
     * @param context contexto de aplicación con acceso a inventario y parametrización
     * @param panel sección funcional a presentar
     */
    public InventarioCostosView(AppContext context, Panel panel) {
        this.context = context;
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
        try {
            clienteLote.setItems(FXCollections.observableArrayList(
                    context.operaciones().listarTerceros().stream().filter(Tercero::isCliente).toList()));
        } catch (Exception ignored) {
            // Sin clientes disponibles el campo queda vacío.
        }
        Runnable actualizarTotal = () -> {
            try {
                int cant = Integer.parseInt(unidades.getText().trim());
                BigDecimal unitario = new BigDecimal(costoUnitario.getText().trim());
                if (cant <= 0 || unitario.signum() < 0) {
                    throw new NumberFormatException();
                }
                BigDecimal total = unitario.multiply(BigDecimal.valueOf(cant))
                        .setScale(2, RoundingMode.HALF_UP);
                costoTotal.setText("Costo total: $" + total.toPlainString()
                        + "  (" + cant + " × $" + unitario.stripTrailingZeros().toPlainString() + ")");
            } catch (Exception ex) {
                costoTotal.setText("Costo total: —");
            }
        };
        unidades.textProperty().addListener((o, a, b) -> actualizarTotal.run());
        costoUnitario.textProperty().addListener((o, a, b) -> actualizarTotal.run());
        Button liberar = new Button("Liberar lote");
        liberar.setOnAction(e -> ejecutar(() -> {
            int cant = Integer.parseInt(unidades.getText().trim());
            BigDecimal unitario = new BigDecimal(costoUnitario.getText().trim());
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
                    clienteLote.getValue() == null ? null : clienteLote.getValue().getId());
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
                    fecha.getValue(), new BigDecimal(precio.getText().trim()), null);
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

        lotes.setPlaceholder(new Label("Aún no se han liberado lotes de licencias."));
        lotes.setMaxHeight(Double.MAX_VALUE);
        VBox existencias = tarjeta("Inventario perpetuo PEPS (primero en entrar, primero en salir)",
                lotes);
        return new javafx.scene.Node[]{liberacion, venta, gestion, existencias};
    }

    private javafx.scene.Node[] panelKardex() {
        ComboBox<InventarioCostoService.LoteInfo> loteKardex = new ComboBox<>();
        loteKardex.setConverter(new StringConverter<>() {
            @Override public String toString(InventarioCostoService.LoteInfo value) {
                return value == null ? ""
                        : value.codigo() + " · " + value.disponibles() + " disponibles";
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
        Runnable cargar = () -> {
            var lote = loteKardex.getValue();
            tabla.setItems(lote == null ? FXCollections.emptyObservableList()
                    : FXCollections.observableArrayList(
                            context.inventario().kardexDeLote(lote.id())));
        };
        loteKardex.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> cargar.run());
        Button actualizar = new Button("Actualizar");
        actualizar.setOnAction(e -> cargar.run());
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
                new Label("Movimientos cronológicos por lote con saldo de unidades y valor bajo PEPS. "
                        + "La salida descuenta existencias; la devolución las repone y la revocación "
                        + "no mueve unidades porque la licencia ya había salido con su venta."),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Lote", loteKardex), actualizar), tabla);
        return new javafx.scene.Node[]{card};
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
        tablaPlanes.getColumns().addAll(cCod, cNom, cMod, cTar);
        tablaPlanes.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
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
                "Cuenta de ingreso donde se acreditan las ventas del plan.");
        ComboBox<ParametrizacionService.PlanServicio> planCosto = new ComboBox<>();
        configurarPlanSelector(planCosto, "SERVICIO");
        Button guardarPlan = new Button("Guardar plan");
        guardarPlan.setOnAction(e -> ejecutar(() -> {
            context.parametrizacion().guardarPlan(new ParametrizacionService.PlanServicio(
                    UUID.randomUUID(), pCod.getText(), pNom.getText(), pMod.getValue(),
                    new BigDecimal(pTar.getText()), pFacturacion.getValue(), UiCatalogos.codigoCuenta(pCta), true));
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
        horas.setPromptText("Horas");
        UiCatalogos.ayuda(horas, "Horas de mano de obra directa consumidas en la orden.");
        TextField tarifa = new TextField("12");
        tarifa.setPromptText("USD/hora");
        UiCatalogos.ayuda(tarifa, "Tarifa por hora de la mano de obra directa.");
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
                    Integer.parseInt(unidadesAnalisis.getText().trim()));
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
            saasLbl.setText(("%s: %d de %d usuarios-mes · Alojamiento $%s · Soporte $%s · "
                    + "Infraestructura $%s · Total asignado $%s · Costo $%s por usuario-mes").formatted(
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
            UiDialogs.error(ex.getMessage());
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
        refrescarClientes();
        refrescarLotes();
        refrescarLicenciasEntregadas();
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
            lotes.setItems(FXCollections.observableArrayList(context.inventario().listarLotes().stream()
                    .map(l -> "%s  ·  Disponibles: %d  ·  Costo promedio por unidad: $%s".formatted(
                            l.codigo(), l.disponibles(), l.costoUnitario())).toList()));
        } catch (Exception ignored) {
            lotes.getItems().clear();
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
