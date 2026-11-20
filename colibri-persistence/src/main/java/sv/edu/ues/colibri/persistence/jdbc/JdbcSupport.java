package sv.edu.ues.colibri.persistence.jdbc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

/**
 * Utilidad de apoyo para ejecutar operaciones JDBC con manejo uniforme de transacciones.
 */
public final class JdbcSupport {

    private final DataSource dataSource;
    private final ThreadLocal<Connection> transaccionActual = new ThreadLocal<>();

    /**
     * Crea el helper de transacciones para la fuente de datos indicada.
     *
     * @param dataSource fuente de datos base
     */
    public JdbcSupport(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Obtiene la fuente de datos administrada por el helper.
     *
     * @return fuente de datos asociada
     */
    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Ejecuta una función dentro de una transacción manual.
     *
     * @param work operación a ejecutar con la conexión abierta
     * @param <T> tipo de retorno de la operación
     * @return resultado producido por la operación
     */
    public <T> T inTransaction(Function<Connection, T> work) {
        // Propagación REQUIRED: si ya existe una transacción en este hilo, la
        // operación se une a ella en lugar de abrir una conexión independiente.
        // Así un fallo en cualquier paso revierte TODO el trabajo multi-paso.
        Connection existente = transaccionActual.get();
        if (existente != null) {
            return work.apply(existente);
        }
        try (Connection conn = dataSource.getConnection()) {
            boolean previous = conn.getAutoCommit();
            conn.setAutoCommit(false);
            transaccionActual.set(conn);
            try {
                T result = work.apply(conn);
                conn.commit();
                return result;
            } catch (RuntimeException e) {
                conn.rollback();
                throw e;
            } catch (Exception e) {
                conn.rollback();
                throw new PersistenceException("Error en transacción", e);
            } finally {
                transaccionActual.remove();
                conn.setAutoCommit(previous);
            }
        } catch (SQLException e) {
            throw new PersistenceException("No se pudo obtener conexión", e);
        }
    }

    /**
     * Ejecuta una acción dentro de una transacción manual sin valor de retorno.
     *
     * @param work acción a ejecutar
     */
    public void runInTransaction(SqlConsumer work) {
        inTransaction(conn -> {
            try {
                work.accept(conn);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new PersistenceException("Error en transacción", e);
            }
            return Boolean.TRUE;
        });
    }

    @FunctionalInterface
    public interface SqlConsumer {
        void accept(Connection connection) throws Exception;
    }
}
