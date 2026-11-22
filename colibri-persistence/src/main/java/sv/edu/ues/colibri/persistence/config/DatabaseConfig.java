package sv.edu.ues.colibri.persistence.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Configuración de acceso a base de datos cargada desde archivo y variables de entorno.
 */
public final class DatabaseConfig {

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String migrateUsername;
    private final String migratePassword;

    /**
     * Crea la configuración de acceso a base de datos.
     *
     * @param jdbcUrl URL JDBC principal
     * @param username usuario de aplicación
     * @param password contraseña de aplicación
     * @param migrateUsername usuario de migración, o el mismo usuario si es {@code null}
     * @param migratePassword contraseña de migración, o la misma contraseña si es {@code null}
     */
    public DatabaseConfig(String jdbcUrl, String username, String password,
                          String migrateUsername, String migratePassword) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.migrateUsername = migrateUsername != null ? migrateUsername : username;
        this.migratePassword = migratePassword != null ? migratePassword : password;
    }

    /**
     * Carga la configuración desde classpath, archivo local y variables de entorno.
     *
     * @return configuración resultante
     */
    public static DatabaseConfig load() {
        Properties props = new Properties();
        try (InputStream in = DatabaseConfig.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException ignored) {
            // defaults vacíos
        }

        Path homeConfig = Path.of(System.getProperty("user.home"), ".colibri", "application.local.properties");
        loadFile(props, homeConfig);

        overrideEnv(props, "COLIBRI_DB_URL", "db.url");
        overrideEnv(props, "COLIBRI_DB_USER", "db.user");
        overrideEnv(props, "COLIBRI_DB_PASSWORD", "db.password");
        overrideEnv(props, "COLIBRI_DB_MIGRATE_USER", "db.migrate.user");
        overrideEnv(props, "COLIBRI_DB_MIGRATE_PASSWORD", "db.migrate.password");

        return new DatabaseConfig(
                props.getProperty("db.url", "jdbc:postgresql://127.0.0.1:5432/colibri"),
                props.getProperty("db.user", "colibri_app"),
                props.getProperty("db.password", "colibri_app"),
                props.getProperty("db.migrate.user", props.getProperty("db.user", "colibri")),
                props.getProperty("db.migrate.password", props.getProperty("db.password", "colibri"))
        );
    }

    private static void loadFile(Properties props, Path path) {
        if (Files.isRegularFile(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                props.load(in);
            } catch (IOException e) {
                throw new IllegalStateException("No se pudo leer " + path, e);
            }
        }
    }

    private static void overrideEnv(Properties props, String env, String key) {
        String value = System.getenv(env);
        if (value != null && !value.isBlank()) {
            props.setProperty(key, value);
        }
    }

    /**
     * Obtiene la URL JDBC principal.
     *
     * @return URL JDBC
     */
    public String getJdbcUrl() {
        return jdbcUrl;
    }

    /**
     * Obtiene el usuario de aplicación.
     *
     * @return usuario
     */
    public String getUsername() {
        return username;
    }

    /**
     * Obtiene la contraseña de aplicación.
     *
     * @return contraseña
     */
    public String getPassword() {
        return password;
    }

    /**
     * Obtiene el usuario de migración.
     *
     * @return usuario de migración
     */
    public String getMigrateUsername() {
        return migrateUsername;
    }

    /**
     * Obtiene la contraseña de migración.
     *
     * @return contraseña de migración
     */
    public String getMigratePassword() {
        return migratePassword;
    }
}
