package sv.edu.ues.colibri.desktop.ui.views;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.application.soporte.SoporteTecnicoService;
import sv.edu.ues.colibri.desktop.ui.ResponsiveLayouts;
import sv.edu.ues.colibri.desktop.ui.UiDialogs;
import sv.edu.ues.colibri.domain.model.BitacoraEntrada;
import sv.edu.ues.colibri.domain.model.Contador;
import sv.edu.ues.colibri.domain.model.RolUsuario;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Panel de soporte técnico. La navegación entre áreas se realiza desde la barra
 * lateral; esta vista renderiza una única sección a la vez.
 */
public class SoporteTecnicoView extends VBox {

    public static final int SECCION_USUARIOS = 0;
    public static final int SECCION_BD = 1;
    public static final int SECCION_ENTORNO = 2;
    public static final int SECCION_MONITOREO = 3;
    public static final int SECCION_ACCESOS = 4;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private static final String[] TITULOS = {
            "Usuarios",
            "Base de datos",
            "Entorno",
            "Monitoreo",
            "Accesos"
    };

    private static final String[] AYUDAS = {
            "Cuentas del sistema, roles y estado de acceso.",
            "Copias de seguridad y restauración de PostgreSQL.",
            "Conexión, rutas de reportes y dispositivo de impresión.",
            "Bitácora técnica de la aplicación.",
            "Auditoría de inicios de sesión y desbloqueos."
    };

    public SoporteTecnicoView(AppContext context, int seccion) {
        setSpacing(18);

        Label titulo = new Label(TITULOS[seccion]);
        titulo.getStyleClass().add("view-title");
        Label ayuda = new Label(AYUDAS[seccion]);
        ayuda.getStyleClass().add("view-subtitle");

        VBox cuerpo = switch (seccion) {
            case SECCION_BD -> baseDatos(context);
            case SECCION_ENTORNO -> entorno(context);
            case SECCION_MONITOREO -> monitoreo(context);
            case SECCION_ACCESOS -> accesos(context);
            default -> usuarios(context);
        };
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        getChildren().addAll(titulo, ayuda, cuerpo);
    }

    private VBox usuarios(AppContext context) {
        Label activos = new Label();
        activos.getStyleClass().add("status-pill");
        TableView<Contador> tabla = new TableView<>();
        col(tabla, "Usuario", Contador::getUsuario);
        col(tabla, "Nombre", Contador::getNombre);
        col(tabla, "Correo", c -> nvl(c.getEmail()));
        col(tabla, "Rol", c -> c.getRol().etiqueta());
        col(tabla, "Estado", c -> c.isActivo() ? "Activa" : "Inactiva");
        tabla.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        tabla.setMinHeight(220);
        Runnable refrescar = () -> {
            tabla.setItems(FXCollections.observableArrayList(context.auth().listarUsuarios()));
            activos.setText("Usuarios activos: " + context.auth().contarActivos());
        };
        refrescar.run();

        TextField usuario = new TextField();
        TextField nombre = new TextField();
        TextField email = new TextField();
        PasswordField password = new PasswordField();
        ComboBox<RolUsuario> rol = new ComboBox<>(FXCollections.observableArrayList(RolUsuario.values()));
        rol.setValue(RolUsuario.CONTADOR);
        CheckBox activo = new CheckBox("Activa");
        activo.setSelected(true);
        Button nuevo = new Button("Nuevo usuario");
        nuevo.setOnAction(e -> run(() -> {
            String codigo = context.auth().crearUsuario(usuario.getText(), nombre.getText(),
                    email.getText(), rol.getValue(), password.getText().toCharArray());
            UiDialogs.info("Usuario creado", "Entregue el código de recuperación:\n" + codigo);
            usuario.clear(); nombre.clear(); email.clear(); password.clear();
            refrescar.run();
        }));
        Button guardar = new Button("Guardar cambios");
        guardar.setOnAction(e -> {
            Contador sel = tabla.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            run(() -> {
                context.auth().actualizarUsuario(sel.getId(), nombre.getText(), email.getText(), activo.isSelected());
                refrescar.run();
            });
        });
        tabla.getSelectionModel().selectedItemProperty().addListener((o, a, sel) -> {
            if (sel != null) {
                usuario.setText(sel.getUsuario());
                nombre.setText(sel.getNombre());
                email.setText(nvl(sel.getEmail()));
                rol.setValue(sel.getRol());
                activo.setSelected(sel.isActivo());
            }
        });
        VBox box = new VBox(12, new HBox(12, activos), tabla,
                ResponsiveLayouts.row(ResponsiveLayouts.field("Usuario", usuario),
                        ResponsiveLayouts.field("Nombre completo", nombre),
                        ResponsiveLayouts.field("Correo", email)),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Rol", rol),
                        ResponsiveLayouts.field("Contraseña inicial", password), activo, nuevo, guardar));
        box.getStyleClass().add("form-card");
        VBox.setVgrow(tabla, Priority.ALWAYS);
        return box;
    }

    private VBox baseDatos(AppContext context) {
        Label ultimo = new Label("Último respaldo: " + nvlFecha(context.soporte().entorno().ultimoRespaldo()));
        ultimo.getStyleClass().add("status-pill");
        Button generar = new Button("Generar respaldo");
        generar.setOnAction(e -> {
            if (!UiDialogs.confirmar("Respaldo", "¿Ejecutar pg_dump de la base colibri?")) {
                return;
            }
            run(() -> {
                var path = context.respaldo().respaldar();
                context.soporte().registrarUltimoRespaldo(LocalDateTime.now());
                ultimo.setText("Último respaldo: " + LocalDateTime.now().format(FMT));
                UiDialogs.info("Respaldo", "Creado: " + path);
            });
        });
        VBox box = new VBox(12, new Label("Respaldos de la base de datos PostgreSQL. La restauración se valida en un entorno local aislado."),
                ultimo, generar);
        box.getStyleClass().add("form-card");
        return box;
    }

    private VBox entorno(AppContext context) {
        var actual = context.soporte().entorno();
        TextField url = new TextField(actual.jdbcUrl());
        TextField user = new TextField(actual.dbUser());
        PasswordField pass = new PasswordField();
        TextField reportes = new TextField(nvl(actual.rutaReportes()));
        TextField dte = new TextField(nvl(actual.rutaDte()));
        TextField impresora = new TextField(nvl(actual.impresora()));
        Label estado = new Label("Rutas y conexiones activas");
        estado.getStyleClass().add("section-note");
        Button probar = new Button("Probar conexión / Guardar configuración");
        probar.setOnAction(e -> run(() -> {
            String msg = context.soporte().probarYGuardar(url.getText().trim(), user.getText().trim(),
                    pass.getText(), reportes.getText().trim(), dte.getText().trim(), impresora.getText().trim());
            estado.setText(msg);
            UiDialogs.info("Entorno", msg);
        }));
        VBox box = new VBox(12, estado,
                ResponsiveLayouts.row(ResponsiveLayouts.field("JDBC / IP:puerto", url),
                        ResponsiveLayouts.field("Usuario SGBD", user),
                        ResponsiveLayouts.field("Password (si cambia)", pass)),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Ruta de reportes", reportes),
                        ResponsiveLayouts.field("Ruta DTE", dte),
                        ResponsiveLayouts.field("Impresora", impresora)),
                probar);
        box.getStyleClass().add("form-card");
        return box;
    }

    private VBox monitoreo(AppContext context) {
        ComboBox<String> nivel = new ComboBox<>(FXCollections.observableArrayList("TODOS", "INFO", "WARN", "ERROR", "FATAL"));
        nivel.setValue("TODOS");
        TableView<SoporteTecnicoService.LogTecnico> tabla = new TableView<>();
        col(tabla, "Fecha", l -> l.fecha().format(FMT));
        col(tabla, "Nivel", SoporteTecnicoService.LogTecnico::nivel);
        col(tabla, "Módulo", SoporteTecnicoService.LogTecnico::modulo);
        col(tabla, "Mensaje", SoporteTecnicoService.LogTecnico::mensaje);
        tabla.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        tabla.setMinHeight(220);
        Runnable refrescar = () -> tabla.setItems(FXCollections.observableArrayList(
                context.soporte().listarLogs(nivel.getValue())));
        nivel.setOnAction(e -> refrescar.run());
        tabla.setOnMouseClicked(ev -> {
            if (ev.getClickCount() == 2) {
                var sel = tabla.getSelectionModel().getSelectedItem();
                if (sel != null) {
                    UiDialogs.info("Traza", nvl(sel.traza()).isBlank() ? sel.mensaje() : sel.traza());
                }
            }
        });
        refrescar.run();
        VBox box = new VBox(12, new Label("Bitácora técnica. Doble clic sobre una fila para ver el detalle."),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Nivel", nivel)), tabla);
        box.getStyleClass().add("form-card");
        VBox.setVgrow(tabla, Priority.ALWAYS);
        return box;
    }

    private VBox accesos(AppContext context) {
        TableView<BitacoraEntrada> tabla = new TableView<>();
        col(tabla, "Fecha", e -> e.getFechaHora().format(FMT));
        col(tabla, "Acción", BitacoraEntrada::getAccion);
        col(tabla, "Detalle", e -> nvl(e.getValorNuevo()) + " " + nvl(e.getMotivo()));
        tabla.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        tabla.setMinHeight(220);
        TableView<Contador> usuarios = new TableView<>();
        col(usuarios, "Usuario", Contador::getUsuario);
        col(usuarios, "Estado", c -> c.estaBloqueado(LocalDateTime.now()) ? "Bloqueada" : (c.isActivo() ? "Activa" : "Inactiva"));
        usuarios.setMaxHeight(Double.MAX_VALUE);
        Runnable refrescar = () -> {
            tabla.setItems(FXCollections.observableArrayList(context.auth().listarAccesos()));
            usuarios.setItems(FXCollections.observableArrayList(context.auth().listarUsuarios()));
        };
        Button desbloquear = new Button("Desbloquear cuenta / Generar clave temporal");
        desbloquear.setOnAction(e -> {
            Contador sel = usuarios.getSelectionModel().getSelectedItem();
            if (sel == null) {
                UiDialogs.error("Seleccione la cuenta bloqueada.");
                return;
            }
            run(() -> {
                String clave = context.auth().desbloquearYEmitirClave(sel.getId());
                UiDialogs.info("Clave temporal", "Entregue esta clave de un solo uso:\n" + clave);
                refrescar.run();
            });
        });
        refrescar.run();
        VBox box = new VBox(12, new Label("Auditoría de inicios de sesión, fallos y desbloqueos."),
                tabla, usuarios, desbloquear);
        box.getStyleClass().add("form-card");
        VBox.setVgrow(tabla, Priority.ALWAYS);
        VBox.setVgrow(usuarios, Priority.ALWAYS);
        return box;
    }

    private <T> void col(TableView<T> tabla, String titulo, java.util.function.Function<T, String> getter) {
        TableColumn<T, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(v -> new SimpleStringProperty(getter.apply(v.getValue())));
        tabla.getColumns().add(c);
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

    private String nvlFecha(LocalDateTime value) {
        return value == null ? "Sin respaldos registrados" : value.format(FMT);
    }
}
