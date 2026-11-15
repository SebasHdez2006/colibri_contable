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
import sv.edu.ues.colibri.desktop.ui.UiCatalogos;
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

        Label titulo = new Label(TITULOS[seccion]);
        titulo.getStyleClass().add("view-title");
        Label ayuda = new Label(AYUDAS[seccion]);
        ayuda.setWrapText(true);
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
        col(tabla, "Estado", c -> c.isActivo() ? "Activo" : "Inactivo");
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        Runnable refrescar = () -> {
            tabla.setItems(FXCollections.observableArrayList(context.auth().listarUsuarios()));
            activos.setText("Usuarios activos: " + context.auth().contarActivos());
        };
        refrescar.run();

        TextField usuario = new TextField();
        usuario.setPromptText("Ej. jperez");
        UiCatalogos.ayuda(usuario, "Nombre de usuario con el que la persona iniciará sesión.");
        TextField nombre = new TextField();
        nombre.setPromptText("Nombre completo");
        UiCatalogos.ayuda(nombre, "Nombre visible del usuario en la sesión y bitácoras.");
        TextField email = new TextField();
        email.setPromptText("correo@empresa.com");
        UiCatalogos.ayuda(email, "Correo de contacto del usuario; opcional.");
        PasswordField password = new PasswordField();
        password.setPromptText("Contraseña inicial");
        UiCatalogos.ayuda(password, "Contraseña temporal; al crear el usuario se entrega un código de recuperación.");
        ComboBox<RolUsuario> rol = new ComboBox<>(FXCollections.observableArrayList(RolUsuario.values()));
        rol.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(RolUsuario value) {
                return value == null ? "" : value.etiqueta();
            }
            @Override public RolUsuario fromString(String value) {
                return java.util.Arrays.stream(RolUsuario.values())
                        .filter(r -> r.etiqueta().equals(value)).findFirst().orElse(null);
            }
        });
        rol.setValue(RolUsuario.CONTADOR);
        CheckBox activo = new CheckBox("Cuenta activa");
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
            if (!UiDialogs.confirmar("Respaldo", "¿Generar una copia de seguridad de la base de "
                    + "datos? Se guardará un archivo de respaldo en el equipo.")) {
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
        url.setPromptText("jdbc:postgresql://host:5432/colibri");
        UiCatalogos.ayuda(url, "URL JDBC de la base de datos; formato jdbc:postgresql://servidor:puerto/base.");
        TextField user = new TextField(actual.dbUser());
        user.setPromptText("Usuario SGBD");
        UiCatalogos.ayuda(user, "Usuario de la base de datos PostgreSQL.");
        PasswordField pass = new PasswordField();
        pass.setPromptText("Solo si cambia");
        UiCatalogos.ayuda(pass, "Contraseña del usuario SGBD; déjelo vacío para conservar la actual.");
        TextField reportes = new TextField(nvl(actual.rutaReportes()));
        reportes.setPromptText("C:\\Reportes");
        UiCatalogos.ayuda(reportes, "Carpeta donde se generan los archivos PDF/Excel exportados.");
        TextField dte = new TextField(nvl(actual.rutaDte()));
        dte.setPromptText("C:\\DTE");
        UiCatalogos.ayuda(dte, "Carpeta de documentos tributarios electrónicos (DTE).");
        TextField impresora = new TextField(nvl(actual.impresora()));
        impresora.setPromptText("Nombre de la impresora");
        UiCatalogos.ayuda(impresora, "Nombre exacto de la impresora configurada en el sistema.");
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
                ResponsiveLayouts.row(
                        ResponsiveLayouts.field("URL de conexión (JDBC)", url),
                        ResponsiveLayouts.field("Usuario de la base de datos", user),
                        ResponsiveLayouts.field("Contraseña (solo si cambia)", pass)),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Ruta de reportes", reportes),
                        ResponsiveLayouts.field("Ruta de DTE (documentos tributarios)", dte),
                        ResponsiveLayouts.field("Impresora", impresora)),
                probar);
        box.getStyleClass().add("form-card");
        return box;
    }

    private VBox monitoreo(AppContext context) {
        ComboBox<String> nivel = new ComboBox<>(FXCollections.observableArrayList(
                "TODOS", "INFO", "WARN", "ERROR", "FATAL"));
        nivel.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(String value) {
                return value == null ? "" : etiquetaNivel(value);
            }
            @Override public String fromString(String value) {
                return value;
            }
        });
        nivel.setValue("TODOS");
        TableView<SoporteTecnicoService.LogTecnico> tabla = new TableView<>();
        col(tabla, "Fecha", l -> l.fecha().format(FMT));
        col(tabla, "Nivel", l -> etiquetaNivel(l.nivel()));
        col(tabla, "Módulo", SoporteTecnicoService.LogTecnico::modulo);
        col(tabla, "Mensaje", SoporteTecnicoService.LogTecnico::mensaje);
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
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
        col(tabla, "Acción", e -> etiquetaAccion(e.getAccion()));
        col(tabla, "Detalle", e -> detalleAccion(e));
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableView<Contador> usuarios = new TableView<>();
        col(usuarios, "Usuario", Contador::getUsuario);
        col(usuarios, "Estado", c -> c.estaBloqueado(LocalDateTime.now()) ? "Bloqueado" : (c.isActivo() ? "Activo" : "Inactivo"));
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

    private String nvlFecha(LocalDateTime value) {
        return value == null ? "Sin respaldos registrados" : value.format(FMT);
    }

    private static String etiquetaNivel(String nivel) {
        if (nivel == null) {
            return "";
        }
        return switch (nivel) {
            case "TODOS" -> "Todos";
            case "INFO" -> "Información";
            case "WARN" -> "Aviso";
            case "ERROR" -> "Error";
            case "FATAL" -> "Crítico";
            default -> nivel;
        };
    }

    private static String etiquetaAccion(String accion) {
        if (accion == null) {
            return "";
        }
        return switch (accion) {
            case "LOGIN" -> "Inicio de sesión";
            case "LOGIN_FALLIDO" -> "Inicio de sesión fallido";
            case "LOGOUT" -> "Cierre de sesión";
            case "CREAR_USUARIO" -> "Crear usuario";
            case "CLAVE_TEMPORAL" -> "Emitir clave temporal";
            case "ACTIVAR_USUARIO" -> "Activar usuario";
            case "DESACTIVAR_USUARIO" -> "Desactivar usuario";
            case "RECUPERACION" -> "Recuperación de clave";
            case "TERCERO" -> "Tercero";
            case "PARTIDA" -> "Partida contable";
            case "PERIODO" -> "Período";
            default -> accion;
        };
    }

    private static String detalleAccion(BitacoraEntrada e) {
        String detalle = nvl(e.getValorNuevo());
        if ("true".equals(detalle)) {
            detalle = "activado";
        } else if ("false".equals(detalle)) {
            detalle = "desactivado";
        }
        String motivo = nvl(e.getMotivo());
        return (detalle + " " + motivo).trim();
    }

    private static String nvl(String v) {
        return v == null ? "" : v;
    }
}
