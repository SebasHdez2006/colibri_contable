package sv.edu.ues.colibri.desktop.ui.views;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.desktop.ui.CatalogoCombo;
import sv.edu.ues.colibri.desktop.ui.ResponsiveLayouts;
import sv.edu.ues.colibri.desktop.ui.UiDialogs;
import sv.edu.ues.colibri.desktop.ui.UiTables;
import sv.edu.ues.colibri.desktop.ui.UiTheme;
import sv.edu.ues.colibri.domain.exception.DomainException;
import sv.edu.ues.colibri.domain.model.Cuenta;
import sv.edu.ues.colibri.domain.model.Partida;
import sv.edu.ues.colibri.domain.model.PartidaDetalle;
import sv.edu.ues.colibri.domain.model.Periodo;
import sv.edu.ues.colibri.domain.model.PlantillaPartida;
import sv.edu.ues.colibri.domain.model.TipoPartida;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Plantillas de asiento recurrente: el contador define una vez la estructura
 * (depreciación, provisiones, ISSS) y genera el borrador del mes con un clic.
 */
public class PlantillasView extends VBox {

    private final AppContext context;
    private final TableView<PlantillaPartida> tabla = new TableView<>();

    public PlantillasView(AppContext context) {
        this.context = context;
        setSpacing(10);
        setPadding(new Insets(16));

        Label titulo = new Label("Plantillas de asiento recurrente");
        titulo.getStyleClass().add("view-title");
        Label ayuda = new Label("Defina una vez la estructura del asiento mensual y genere el borrador "
                + "del período con un clic. La plantilla nunca contabiliza por sí sola: siempre "
                + "crea un borrador revisable en el libro diario.");
        ayuda.getStyleClass().add("view-subtitle");
        ayuda.setWrapText(true);

        agregarColumna(tabla, "Código", PlantillaPartida::codigo);
        agregarColumna(tabla, "Nombre", PlantillaPartida::nombre);
        agregarColumna(tabla, "Tipo", p -> p.tipo().getEtiqueta());
        agregarColumna(tabla, "Concepto", PlantillaPartida::concepto);
        agregarColumna(tabla, "Líneas", p -> String.valueOf(p.detalles().size()));
        tabla.setPlaceholder(new Label("Sin plantillas. Cree la primera con «Nueva plantilla»."));
        VBox.setVgrow(tabla, Priority.ALWAYS);

        Button nueva = new Button("Nueva plantilla");
        Button ver = new Button("Ver líneas");
        Button generar = new Button("Generar borrador del mes");
        nueva.setOnAction(e -> {
            PlantillaPartida creada = formularioNueva();
            if (creada != null) {
                try {
                    context.plantillas().guardar(creada);
                    refrescar();
                    UiDialogs.info("Plantilla", "Plantilla «" + creada.nombre() + "» guardada.");
                } catch (Exception ex) {
                    UiDialogs.error(ex.getMessage());
                }
            }
        });
        ver.setOnAction(e -> {
            PlantillaPartida sel = tabla.getSelectionModel().getSelectedItem();
            if (sel == null) {
                UiDialogs.error("Seleccione una plantilla de la lista.");
                return;
            }
            UiDialogs.mostrar("Líneas de «" + sel.nombre() + "»", tablaLineas(sel.detalles()));
        });
        generar.setOnAction(e -> {
            PlantillaPartida sel = tabla.getSelectionModel().getSelectedItem();
            if (sel == null) {
                UiDialogs.error("Seleccione una plantilla de la lista.");
                return;
            }
            LocalDate fecha = fechaBorrador();
            if (fecha == null) {
                return;
            }
            if (!UiDialogs.confirmar("Generar borrador",
                    "Se creará un borrador «" + sel.nombre() + "» con fecha " + fecha
                            + " para revisar y contabilizar después. ¿Continuar?")) {
                return;
            }
            try {
                Partida borrador = context.plantillas().generarBorrador(sel.codigo(), fecha);
                UiDialogs.info("Borrador generado",
                        "Partida en estado " + borrador.getEstado() + ". Revísela en «Libro diario».");
            } catch (Exception ex) {
                UiDialogs.error(ex.getMessage());
            }
        });

        getChildren().addAll(titulo, ayuda, UiTables.conBusqueda(tabla, "plantillas", plantilla ->
                        plantilla.codigo() + " " + plantilla.nombre() + " " + plantilla.concepto()
                                + " " + plantilla.tipo().getEtiqueta()),
                ResponsiveLayouts.row(nueva, ver, generar));
        refrescar();
    }

    private void refrescar() {
        try {
            tabla.setItems(FXCollections.observableArrayList(context.plantillas().listar()));
        } catch (Exception ex) {
            tabla.setItems(FXCollections.observableArrayList());
            UiDialogs.error(ex.getMessage());
        }
    }

    private LocalDate fechaBorrador() {
        Periodo abierto = context.periodos().periodoAbierto().orElse(null);
        if (abierto != null) {
            return abierto.getFechaFin();
        }
        String texto = UiDialogs.pedirTexto("Fecha del borrador",
                "No hay período abierto. Ingrese la fecha contable del borrador (AAAA-MM-DD):");
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(texto.trim());
        } catch (Exception ex) {
            UiDialogs.error("Fecha inválida: use el formato AAAA-MM-DD.");
            return null;
        }
    }

    private PlantillaPartida formularioNueva() {
        Dialog<PlantillaPartida> dialog = new Dialog<>();
        dialog.setTitle("Nueva plantilla de asiento");
        dialog.setHeaderText("Defina la estructura reutilizable del asiento recurrente.");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField codigo = new TextField();
        codigo.setPromptText("Se genera desde el tipo y el nombre");
        TextField nombre = new TextField();
        nombre.setPromptText("Ej. Depreciación mensual");
        ComboBox<TipoPartida> tipo = new ComboBox<>(FXCollections.observableArrayList(TipoPartida.values()));
        tipo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(TipoPartida value) {
                return value == null ? "" : value.getEtiqueta();
            }

            @Override
            public TipoPartida fromString(String value) {
                return java.util.Arrays.stream(TipoPartida.values())
                        .filter(t -> t.getEtiqueta().equals(value)).findFirst().orElse(null);
            }
        });
        tipo.setValue(TipoPartida.AJ);
        TextField concepto = new TextField();
        concepto.setPromptText("Concepto que llevará cada borrador generado");

        ObservableList<PartidaDetalle> lineas = FXCollections.observableArrayList();
        TableView<PartidaDetalle> tablaLineas = new TableView<>(lineas);
        agregarColumna(tablaLineas, "Cuenta", PartidaDetalle::getCodigoCuenta);
        agregarColumna(tablaLineas, "Debe", d -> d.getDebe().toPlainString());
        agregarColumna(tablaLineas, "Haber", d -> d.getHaber().toPlainString());
        tablaLineas.setPrefHeight(180);
        tablaLineas.setMinHeight(140);

        List<Cuenta> cuentas = cuentasMovimiento();
        CatalogoCombo<Cuenta> cuenta = new CatalogoCombo<>(cuentas, conversorCuenta(),
                Cuenta::getCodigo, this::buscarCuentasPlantilla);
        cuenta.getEditor().setPromptText("Código o nombre de la cuenta");
        TextField debe = new TextField("0");
        TextField haber = new TextField("0");
        Button agregar = new Button("Agregar línea");
        agregar.setOnAction(e -> {
            try {
                String codigoCuenta = cuenta.codigo().trim();
                if (codigoCuenta.isEmpty()) {
                    throw new DomainException("Seleccione o escriba la cuenta de la línea.");
                }
                BigDecimal importeDebe = importe(debe.getText());
                BigDecimal importeHaber = importe(haber.getText());
                if (importeDebe.signum() == 0 && importeHaber.signum() == 0) {
                    throw new DomainException("La línea no puede tener Debe y Haber en cero.");
                }
                PartidaDetalle linea = PartidaDetalle.nueva(codigoCuenta, importeDebe, importeHaber);
                linea.validarImportes();
                lineas.add(linea);
                cuenta.limpiar();
                debe.setText("0");
                haber.setText("0");
            } catch (Exception ex) {
                UiDialogs.error("Línea inválida: " + ex.getMessage());
            }
        });
        Button quitar = new Button("Quitar línea");
        quitar.setOnAction(e -> {
            PartidaDetalle sel = tablaLineas.getSelectionModel().getSelectedItem();
            if (sel != null) {
                lineas.remove(sel);
            }
        });

        Label error = new Label();
        error.setWrapText(true);
        error.getStyleClass().add("error-text");
        error.setVisible(false);
        error.setManaged(false);

        boolean[] codigoAutomatico = {true};
        String[] ultimoGenerado = {""};
        Runnable sugerirCodigo = () -> {
            if (!codigoAutomatico[0]) {
                return;
            }
            String base = tipo.getValue() == null ? "PLANTILLA"
                    : tipo.getValue().prefijoFolio();
            String slugNombre = slug(nombre.getText());
            String nuevo = base + "-" + (slugNombre.isEmpty() ? "PLANTILLA" : slugNombre);
            ultimoGenerado[0] = nuevo;
            codigo.setText(nuevo);
        };
        tipo.valueProperty().addListener((o, a, b) -> sugerirCodigo.run());
        nombre.textProperty().addListener((o, a, b) -> sugerirCodigo.run());
        codigo.textProperty().addListener((o, a, b) -> {
            String actual = b == null ? "" : b.trim();
            codigoAutomatico[0] = actual.isEmpty() || actual.equals(ultimoGenerado[0]);
        });
        sugerirCodigo.run();

        VBox contenido = new VBox(10,
                ResponsiveLayouts.row(ResponsiveLayouts.field("Código", codigo),
                        ResponsiveLayouts.field("Nombre", nombre)),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Tipo de partida", tipo),
                        ResponsiveLayouts.field("Concepto", concepto)),
                new Label("Líneas del asiento (al menos dos; Debe y Haber deben cuadrar):"),
                UiTables.conBusqueda(tablaLineas, "líneas de la plantilla", linea ->
                        linea.getCodigoCuenta() + " " + linea.getDebe() + " " + linea.getHaber()),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Cuenta", cuenta),
                        ResponsiveLayouts.field("Debe", debe), ResponsiveLayouts.field("Haber", haber),
                        agregar, quitar),
                error);
        contenido.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(contenido);
        UiTheme.style(dialog);

        PlantillaPartida[] resultado = new PlantillaPartida[1];
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.addEventFilter(javafx.event.ActionEvent.ACTION, evento -> {
            try {
                resultado[0] = construirPlantilla(codigo, nombre, tipo, concepto, lineas);
                error.setText("");
                error.setVisible(false);
                error.setManaged(false);
            } catch (Exception ex) {
                resultado[0] = null;
                error.setText(ex.getMessage());
                error.setVisible(true);
                error.setManaged(true);
                evento.consume();
            }
        });
        dialog.setResultConverter(bt -> bt == ButtonType.OK ? resultado[0] : null);
        return dialog.showAndWait().orElse(null);
    }

    private PlantillaPartida construirPlantilla(TextField codigo, TextField nombre,
                                               ComboBox<TipoPartida> tipo, TextField concepto,
                                               List<PartidaDetalle> lineas) {
        if (codigo.getText().isBlank() || nombre.getText().isBlank()
                || concepto.getText().isBlank()) {
            throw new DomainException("Código, nombre y concepto son obligatorios.");
        }
        if (lineas.isEmpty()) {
            throw new DomainException("Agregue al menos dos líneas contables al asiento.");
        }
        for (PartidaDetalle linea : lineas) {
            linea.validarImportes();
        }
        if (lineas.size() < 2) {
            throw new DomainException("La plantilla necesita al menos dos líneas.");
        }
        BigDecimal totalDebe = lineas.stream().map(PartidaDetalle::getDebe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalHaber = lineas.stream().map(PartidaDetalle::getHaber)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalDebe.compareTo(totalHaber) != 0) {
            throw new DomainException("Las líneas no cuadran: Debe " + totalDebe
                    + " ≠ Haber " + totalHaber);
        }
        return new PlantillaPartida(UUID.randomUUID(), codigo.getText().trim().toUpperCase(),
                nombre.getText().trim(), tipo.getValue(), concepto.getText().trim(), true,
                LocalDateTime.now(), new ArrayList<>(lineas));
    }

    private List<Cuenta> cuentasMovimiento() {
        try {
            return context.catalogo().listar().stream()
                    .filter(Cuenta::puedeRecibirMovimiento)
                    .sorted(java.util.Comparator.comparing(Cuenta::getCodigo))
                    .toList();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<Cuenta> buscarCuentasPlantilla(String texto) {
        String q = texto == null ? "" : texto.trim().toLowerCase();
        return cuentasMovimiento().stream()
                .filter(c -> q.isEmpty() || c.getCodigo().toLowerCase().contains(q)
                        || c.getNombre().toLowerCase().contains(q))
                .toList();
    }

    private static javafx.util.StringConverter<Cuenta> conversorCuenta() {
        return new javafx.util.StringConverter<>() {
            @Override
            public String toString(Cuenta value) {
                return value == null ? "" : value.getCodigo() + " — " + value.getNombre();
            }

            @Override
            public Cuenta fromString(String value) {
                return null;
            }
        };
    }

    private static BigDecimal importe(String texto) {
        String limpio = texto == null ? "" : texto.trim();
        if (limpio.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(limpio);
        } catch (NumberFormatException ex) {
            throw new DomainException("El importe debe ser numérico: «" + limpio + "».");
        }
    }

    private static String slug(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        String normalizado = java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalizado.toUpperCase().replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private static Node tablaLineas(List<PartidaDetalle> detalles) {
        TableView<PartidaDetalle> tabla = new TableView<>(FXCollections.observableArrayList(detalles));
        agregarColumna(tabla, "Cuenta", PartidaDetalle::getCodigoCuenta);
        agregarColumna(tabla, "Auxiliar", PartidaDetalle::getAuxiliar);
        agregarColumna(tabla, "Centro", PartidaDetalle::getCentroCosto);
        agregarColumna(tabla, "Debe", d -> d.getDebe().toPlainString());
        agregarColumna(tabla, "Haber", d -> d.getHaber().toPlainString());
        tabla.setPrefSize(560, 320);
        return UiTables.conBusqueda(tabla, "líneas de la plantilla", linea ->
                linea.getCodigoCuenta() + " " + linea.getAuxiliar() + " " + linea.getCentroCosto()
                        + " " + linea.getDebe() + " " + linea.getHaber());
    }

    private static <T> void agregarColumna(TableView<T> tabla, String titulo,
            java.util.function.Function<T, String> valor) {
        TableColumn<T, String> columna = new TableColumn<>(titulo);
        columna.setCellValueFactory(c -> new SimpleStringProperty(
                valor.apply(c.getValue()) == null ? "" : valor.apply(c.getValue())));
        tabla.getColumns().add(columna);
    }
}
