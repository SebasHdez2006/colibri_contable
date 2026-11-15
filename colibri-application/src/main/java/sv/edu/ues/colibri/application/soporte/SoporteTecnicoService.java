package sv.edu.ues.colibri.application.soporte;

import sv.edu.ues.colibri.application.auth.AuthService;
import sv.edu.ues.colibri.domain.exception.DomainException;
import sv.edu.ues.colibri.domain.model.BitacoraEntrada;
import sv.edu.ues.colibri.persistence.jdbc.JdbcSupport;
import sv.edu.ues.colibri.persistence.jdbc.PersistenceException;
import sv.edu.ues.colibri.persistence.repo.BitacoraRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * Servicio de soporte técnico para configuración, diagnósticos y bitácora.
 */
public class SoporteTecnicoService {

    private final JdbcSupport jdbc;
    private final AuthService auth;
    private final BitacoraRepository bitacora = new BitacoraRepository();

    /**
     * Fotografía del entorno técnico persistido en base de datos.
     */
    public record Entorno(String jdbcUrl, String dbUser, String rutaReportes, String rutaDte,
                          String impresora, LocalDateTime ultimoRespaldo) {
    }

    /**
     * Entrada de log técnico de la aplicación.
     */
    public record LogTecnico(LocalDateTime fecha, String nivel, String modulo, String mensaje, String traza) {
    }

    /**
     * Crea el servicio de soporte técnico.
     *
     * @param jdbc soporte transaccional
     * @param auth servicio de autenticación
     */
    public SoporteTecnicoService(JdbcSupport jdbc, AuthService auth) {
        this.jdbc = jdbc;
        this.auth = auth;
    }

    /**
     * Obtiene la configuración técnica activa.
     *
     * @return entorno técnico configurado
     */
    public Entorno entorno() {
        auth.exigirSoporteTecnico();
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM entorno_tecnico LIMIT 1");
                 ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new DomainException("No hay entorno técnico configurado.");
                }
                return new Entorno(rs.getString("jdbc_url"), rs.getString("db_user"),
                        rs.getString("ruta_reportes"), rs.getString("ruta_dte"),
                        rs.getString("impresora"),
                        rs.getObject("ultimo_respaldo", LocalDateTime.class));
            } catch (Exception e) {
                throw new PersistenceException("Error al leer entorno", e);
            }
        });
    }

    /**
     * Prueba la conexión y guarda la configuración técnica si es válida.
     *
     * @param jdbcUrl URL JDBC a validar
     * @param dbUser usuario de base de datos
     * @param password contraseña de base de datos
     * @param rutaReportes ruta local para reportes
     * @param rutaDte ruta local para DTE
     * @param impresora nombre de impresora
     * @return mensaje de confirmación
     */
    public String probarYGuardar(String jdbcUrl, String dbUser, String password,
                                 String rutaReportes, String rutaDte, String impresora) {
        var tecnico = auth.exigirSoporteTecnico();
        try {
            Properties props = new Properties();
            props.setProperty("user", dbUser);
            if (password != null && !password.isBlank()) {
                props.setProperty("password", password);
            }
            try (var ignored = DriverManager.getConnection(jdbcUrl, props)) {
                // conexión alcanzada
            }
        } catch (Exception e) {
            throw new DomainException("No se alcanzó PostgreSQL: " + e.getMessage());
        }
        if (rutaReportes != null && !rutaReportes.isBlank()) {
            try {
                Files.createDirectories(Path.of(rutaReportes));
            } catch (Exception e) {
                throw new DomainException("La ruta de reportes no es válida: " + e.getMessage());
            }
        }
        jdbc.runInTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    UPDATE entorno_tecnico SET jdbc_url=?, db_user=?, ruta_reportes=?, ruta_dte=?,
                        impresora=?, actualizado_en=?
                    """)) {
                ps.setString(1, jdbcUrl);
                ps.setString(2, dbUser);
                ps.setString(3, rutaReportes);
                ps.setString(4, rutaDte);
                ps.setString(5, impresora);
                ps.setObject(6, LocalDateTime.now());
                ps.executeUpdate();
            }
            bitacora.registrar(conn, BitacoraEntrada.de(
                    tecnico.getId(), "ENTORNO", "SISTEMA", "entorno", null, jdbcUrl, "Configuración de entorno"));
        });
        persistirLocal(jdbcUrl, dbUser, password, rutaReportes, impresora);
        return "Conexión correcta. Configuración guardada.";
    }

    /**
     * Registra la fecha del último respaldo ejecutado.
     *
     * @param cuando instante del respaldo
     */
    public void registrarUltimoRespaldo(LocalDateTime cuando) {
        auth.exigirSoporteTecnico();
        jdbc.runInTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE entorno_tecnico SET ultimo_respaldo=?, actualizado_en=?")) {
                ps.setObject(1, cuando);
                ps.setObject(2, LocalDateTime.now());
                ps.executeUpdate();
            }
        });
    }

    /**
     * Inserta una entrada de log técnico.
     *
     * @param nivel nivel de severidad
     * @param modulo módulo origen
     * @param mensaje mensaje del evento
     * @param traza traza técnica opcional
     */
    public void registrarLog(String nivel, String modulo, String mensaje, String traza) {
        jdbc.runInTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO log_tecnico (id, fecha_hora, nivel, modulo, mensaje, traza)
                    VALUES (?,?,?,?,?,?)
                    """)) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, LocalDateTime.now());
                ps.setString(3, nivel);
                ps.setString(4, modulo);
                ps.setString(5, mensaje);
                ps.setString(6, traza);
                ps.executeUpdate();
            }
        });
    }

    /**
     * Lista los logs técnicos filtrados por nivel.
     *
     * @param nivel nivel a filtrar o {@code TODOS}
     * @return lista de logs
     */
    public List<LogTecnico> listarLogs(String nivel) {
        auth.exigirSoporteTecnico();
        return jdbc.inTransaction(conn -> {
            String sql = nivel == null || nivel.isBlank() || "TODOS".equals(nivel)
                    ? "SELECT fecha_hora, nivel, modulo, mensaje, traza FROM log_tecnico ORDER BY fecha_hora DESC LIMIT 200"
                    : "SELECT fecha_hora, nivel, modulo, mensaje, traza FROM log_tecnico WHERE nivel=? ORDER BY fecha_hora DESC LIMIT 200";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if (sql.contains("WHERE")) {
                    ps.setString(1, nivel);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    List<LogTecnico> list = new ArrayList<>();
                    while (rs.next()) {
                        list.add(new LogTecnico(rs.getObject(1, LocalDateTime.class),
                                rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)));
                    }
                    return list;
                }
            } catch (Exception e) {
                throw new PersistenceException("Error al listar logs", e);
            }
        });
    }

    /**
     * Inserta logs de demostración si la tabla está vacía.
     */
    public void sembrarLogsDemoSiVacio() {
        jdbc.runInTransaction(conn -> {
            try (PreparedStatement count = conn.prepareStatement("SELECT COUNT(*) FROM log_tecnico");
                 ResultSet rs = count.executeQuery()) {
                rs.next();
                if (rs.getLong(1) > 0) {
                    return;
                }
            }
            insertarLog(conn, "INFO", "Sistema", "Arranque de Colibrí", null);
            insertarLog(conn, "WARN", "Sesión", "Intento fallido de autenticación", null);
            insertarLog(conn, "ERROR", "SQL", "Consulta no disponible durante mantenimiento",
                    "org.postgresql.util.PSQLException: connection refused");
        });
    }

    private void insertarLog(java.sql.Connection conn, String nivel, String modulo, String mensaje, String traza)
            throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO log_tecnico (id, fecha_hora, nivel, modulo, mensaje, traza)
                VALUES (?,?,?,?,?,?)
                """)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, LocalDateTime.now());
            ps.setString(3, nivel);
            ps.setString(4, modulo);
            ps.setString(5, mensaje);
            ps.setString(6, traza);
            ps.executeUpdate();
        }
    }

    private void persistirLocal(String jdbcUrl, String dbUser, String password,
                                String rutaReportes, String impresora) {
        try {
            Path dir = Path.of(System.getProperty("user.home"), ".colibri");
            Files.createDirectories(dir);
            Path file = dir.resolve("application.local.properties");
            Properties props = new Properties();
            if (Files.isRegularFile(file)) {
                try (var in = Files.newInputStream(file)) {
                    props.load(in);
                }
            }
            props.setProperty("db.url", jdbcUrl);
            props.setProperty("db.user", dbUser);
            if (password != null && !password.isBlank()) {
                props.setProperty("db.password", password);
            }
            if (rutaReportes != null) {
                props.setProperty("app.ruta.reportes", rutaReportes);
            }
            if (impresora != null) {
                props.setProperty("app.impresora", impresora);
            }
            try (var out = Files.newOutputStream(file)) {
                props.store(out, "Colibri entorno tecnico");
            }
        } catch (Exception ignored) {
        }
    }
}
