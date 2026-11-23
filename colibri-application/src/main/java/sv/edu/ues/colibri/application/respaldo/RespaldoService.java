package sv.edu.ues.colibri.application.respaldo;

import sv.edu.ues.colibri.application.auth.AuthService;
import sv.edu.ues.colibri.domain.exception.DomainException;
import sv.edu.ues.colibri.domain.model.BitacoraEntrada;
import sv.edu.ues.colibri.persistence.config.DatabaseConfig;
import sv.edu.ues.colibri.persistence.jdbc.JdbcSupport;
import sv.edu.ues.colibri.persistence.repo.BitacoraRepository;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Servicio de respaldo físico de la base de datos mediante {@code pg_dump}.
 */
public class RespaldoService {

    private final AuthService auth;
    private final DatabaseConfig config;
    private final JdbcSupport jdbc;
    private final BitacoraRepository bitacora = new BitacoraRepository();

    /**
     * Crea el servicio de respaldo.
     *
     * @param auth servicio de autenticación
     * @param config configuración de base de datos
     * @param jdbc soporte transaccional
     */
    public RespaldoService(AuthService auth, DatabaseConfig config, JdbcSupport jdbc) {
        this.auth = auth;
        this.config = config;
        this.jdbc = jdbc;
    }

    /**
     * Genera un respaldo comprimido de la base de datos.
     *
     * @return ruta del archivo generado
     */
    public Path respaldar() {
        // pg_dump extrae toda la base, incluidos saldos contables y hashes de
        // contraseñas: la copia de respaldo corresponde al dueño de la información
        // (contador), no al soporte técnico externo.
        var contador = auth.exigirContador();
        try {
            Path dir = Path.of(System.getProperty("user.home"), ".colibri", "backups");
            Files.createDirectories(dir);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path out = dir.resolve("colibri-" + stamp + ".dump");

            String url = config.getJdbcUrl(); // jdbc:postgresql://host:port/db
            String host = "127.0.0.1";
            String port = "5434";
            String db = "colibri";
            if (url.contains("//")) {
                String rest = url.substring(url.indexOf("//") + 2);
                String[] hp = rest.split("/")[0].split(":");
                host = hp[0];
                if (hp.length > 1) port = hp[1];
                db = rest.split("/")[1].split("\\?")[0];
            }

            Path pgDump = Path.of("C:\\Program Files\\PostgreSQL\\18\\bin\\pg_dump.exe");
            if (!Files.isRegularFile(pgDump)) {
                throw new DomainException("No se encontró pg_dump en " + pgDump);
            }
            ProcessBuilder pb = new ProcessBuilder(
                    pgDump.toString(),
                    "-h", host, "-p", port, "-U", config.getUsername(),
                    "-F", "c", "-f", out.toString(), db
            );
            pb.environment().put("PGPASSWORD", config.getPassword());
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            StringBuilder log = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    log.append(line).append('\n');
                }
            }
            int code = proc.waitFor();
            if (code != 0) {
                throw new DomainException("pg_dump falló (" + code + "): " + log);
            }
            jdbc.runInTransaction(conn -> bitacora.registrar(conn, BitacoraEntrada.de(
                    contador.getId(), "RESPALDO", "BACKUP", out.toString(),
                    null, out.getFileName().toString(), null)));
            return out;
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            throw new DomainException("No se pudo crear respaldo: " + e.getMessage(), e);
        }
    }
}
