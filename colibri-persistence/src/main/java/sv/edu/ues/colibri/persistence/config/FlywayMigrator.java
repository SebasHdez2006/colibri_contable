package sv.edu.ues.colibri.persistence.config;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;

/**
 * Ejecuta las migraciones de base de datos con Flyway.
 */
public final class FlywayMigrator {

    private FlywayMigrator() {
    }

    /**
     * Migra la base configurada sin abrir la interfaz gráfica.
     *
     * <p>Usado por {@code scripts/run-colibri.ps1} para llevar el esquema a la
     * versión más reciente antes de aplicar los scripts SQL de reset/seed.</p>
     *
     * @param args ignorados
     */
    public static void main(String[] args) {
        DatabaseConfig config = DatabaseConfig.load();
        HikariDataSource ds = (HikariDataSource) DataSourceFactory.createMigrate(config);
        try {
            migrate(ds);
        } finally {
            ds.close();
        }
    }

    /**
     * Aplica las migraciones disponibles sobre la fuente de datos indicada.
     *
     * @param dataSource fuente de datos a migrar
     */
    public static void migrate(DataSource dataSource) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load()
                .migrate();
    }
}
