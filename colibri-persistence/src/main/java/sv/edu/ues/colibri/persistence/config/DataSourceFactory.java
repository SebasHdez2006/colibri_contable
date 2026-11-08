package sv.edu.ues.colibri.persistence.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

/**
 * Factoría de fuentes de datos para ejecución normal y para migraciones.
 */
public final class DataSourceFactory {

    private DataSourceFactory() {
    }

    /**
     * Crea el pool de conexiones utilizado por la aplicación en tiempo de ejecución.
     *
     * @param config configuración cargada desde archivo o variables de entorno
     * @return fuente de datos preparada para operación normal
     */
    public static HikariDataSource create(DatabaseConfig config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.getJdbcUrl());
        hikari.setUsername(config.getUsername());
        hikari.setPassword(config.getPassword());
        hikari.setMaximumPoolSize(5);
        hikari.setPoolName("colibri-pool");
        hikari.setAutoCommit(false);
        return new HikariDataSource(hikari);
    }

    /**
     * Crea la fuente de datos utilizada por Flyway durante las migraciones.
     *
     * @param config configuración cargada desde archivo o variables de entorno
     * @return fuente de datos con parámetros adecuados para migración
     */
    public static DataSource createMigrate(DatabaseConfig config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.getJdbcUrl());
        hikari.setUsername(config.getMigrateUsername());
        hikari.setPassword(config.getMigratePassword());
        // Flyway puede pedir más de una conexión; pool=1 provoca timeout.
        hikari.setMaximumPoolSize(3);
        hikari.setMinimumIdle(0);
        hikari.setPoolName("colibri-migrate");
        hikari.setAutoCommit(true);
        return new HikariDataSource(hikari);
    }
}
