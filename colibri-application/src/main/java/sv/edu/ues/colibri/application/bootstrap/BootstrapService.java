package sv.edu.ues.colibri.application.bootstrap;

import sv.edu.ues.colibri.domain.model.BitacoraEntrada;
import sv.edu.ues.colibri.domain.model.Contador;
import sv.edu.ues.colibri.domain.model.Ejercicio;
import sv.edu.ues.colibri.domain.model.Empresa;
import sv.edu.ues.colibri.domain.model.EstadoPeriodo;
import sv.edu.ues.colibri.domain.model.Periodo;
import sv.edu.ues.colibri.domain.model.RolUsuario;
import sv.edu.ues.colibri.domain.security.PasswordHasher;
import sv.edu.ues.colibri.persistence.jdbc.JdbcSupport;
import sv.edu.ues.colibri.persistence.repo.BitacoraRepository;
import sv.edu.ues.colibri.persistence.repo.ContadorRepository;
import sv.edu.ues.colibri.persistence.repo.EmpresaRepository;
import sv.edu.ues.colibri.persistence.repo.PeriodoRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Inicializa empresa, ejercicio 2026, períodos y credencial del contador si no existen.
 * La contraseña inicial y el código de recuperación se generan en la primera ejecución;
 * el código se escribe en %USERPROFILE%\.colibri\recovery-code.txt
 */
/**
 * Servicio de arranque que crea datos iniciales cuando la base está vacía.
 */
public class BootstrapService {

    public static final String USUARIO_DEFAULT = "contador";
    public static final String PASSWORD_DEFAULT = "Colibri2026!";
    public static final String USUARIO_SOPORTE = "soporte";
    public static final String PASSWORD_SOPORTE = "Soporte2026!";

    private final JdbcSupport jdbc;
    private final EmpresaRepository empresaRepository = new EmpresaRepository();
    private final ContadorRepository contadorRepository = new ContadorRepository();
    private final PeriodoRepository periodoRepository = new PeriodoRepository();
    private final BitacoraRepository bitacoraRepository = new BitacoraRepository();

    private String codigoRecuperacionGenerado;

    /**
     * Crea el servicio de inicialización.
     *
     * @param jdbc soporte JDBC transaccional
     */
    public BootstrapService(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Obtiene el código de recuperación generado durante el arranque.
     *
     * @return código de recuperación o {@code null}
     */
    public String getCodigoRecuperacionGenerado() {
        return codigoRecuperacionGenerado;
    }

    /**
     * Garantiza la existencia de empresa, usuarios demo y ejercicio inicial.
     */
    public void asegurarDatosIniciales() {
        jdbc.runInTransaction(conn -> {
            if (empresaRepository.obtenerUnica(conn).isEmpty()) {
                Empresa empresa = new Empresa(
                        UUID.randomUUID(),
                        "Soluciones Empresariales Colibrí, S.A. de C.V.",
                        "Colibrí",
                        "SIMULADO-NIT",
                        "SIMULADO-NRC",
                        "USD",
                        "America/El_Salvador",
                        LocalDateTime.now()
                );
                empresaRepository.insertar(conn, empresa);
            }

            Empresa empresa = empresaRepository.obtenerUnica(conn).orElseThrow();

            asegurarUsuarioDemo(conn, USUARIO_DEFAULT, "Contador Colibrí", PASSWORD_DEFAULT,
                    RolUsuario.CONTADOR, "contador@colibri.sv");
            asegurarUsuarioDemo(conn, USUARIO_SOPORTE, "Soporte Técnico Colibrí", PASSWORD_SOPORTE,
                    RolUsuario.SOPORTE_TECNICO, "soporte@colibri.sv");

            if (periodoRepository.buscarEjercicioPorAnio(conn, 2026).isEmpty()) {
                int anio = 2026;
                Ejercicio ejercicio = new Ejercicio(
                        UUID.randomUUID(),
                        empresa.getId(),
                        anio,
                        LocalDate.of(anio, 1, 1),
                        LocalDate.of(anio, 12, 31),
                        true,
                        1
                );
                periodoRepository.insertarEjercicio(conn, ejercicio);
                for (int mes = 1; mes <= 12; mes++) {
                    YearMonth ym = YearMonth.of(anio, mes);
                    Periodo periodo = new Periodo(
                            UUID.randomUUID(),
                            ejercicio.getId(),
                            mes,
                            ym.atDay(1),
                            ym.atEndOfMonth(),
                            mes == 1 ? EstadoPeriodo.ABIERTO : EstadoPeriodo.ABIERTO,
                            1
                    );
                    periodoRepository.insertarPeriodo(conn, periodo);
                }
            }

            asegurarAsientosAperturaActivos(conn);
        });
    }

    /**
     * Garantiza que cada activo fijo registrado tenga su asiento de apertura en
     * el mayor (Debe cuenta_activo / Haber 310301 Utilidades retenidas). Los
     * activos semilla como AF-PC-001 se insertaron directamente en la tabla sin
     * partida, lo que dejaba el auxiliar con saldo y el mayor en cero. El proceso
     * es idempotente: se omite si ya existe el evento de apertura.
     *
     * @param conn conexión activa de la transacción de arranque
     */
    private void asegurarAsientosAperturaActivos(java.sql.Connection conn) {
        String sql = """
                DO $$
                DECLARE
                    af RECORD;
                    v_periodo UUID;
                    v_anio INTEGER;
                    v_evento UUID;
                    v_partida UUID;
                    v_folio VARCHAR(20);
                    v_seq BIGINT;
                    v_fecha DATE;
                BEGIN
                    FOR af IN
                        SELECT codigo, nombre, cuenta_activo, costo, fecha_uso
                        FROM activo_fijo
                        WHERE costo IS NOT NULL AND costo > 0
                          AND cuenta_activo IS NOT NULL
                          AND cuenta_activo ~ '^\\d{6}$'
                          AND EXISTS (SELECT 1 FROM cuenta c WHERE c.codigo = activo_fijo.cuenta_activo)
                          AND EXISTS (SELECT 1 FROM cuenta c WHERE c.codigo = '310301')
                          -- Si la cuenta de activo ya tiene saldo mayorizado (p. ej. los
                          -- seeds capitalizan los activos en el asiento de apertura y en
                          -- compras del ejercicio), crear la apertura duplicaría el activo.
                          AND NOT EXISTS (
                              SELECT 1 FROM (
                                  SELECT d.codigo_cuenta, SUM(d.debe - d.haber) AS saldo
                                  FROM partida_detalle d
                                  JOIN partida p ON p.id = d.partida_id
                                  WHERE p.estado IN ('CONTABILIZADA','REVERTIDA')
                                  GROUP BY d.codigo_cuenta
                              ) s
                              WHERE s.codigo_cuenta = activo_fijo.cuenta_activo
                                AND s.saldo <> 0
                          )
                    LOOP
                        IF EXISTS (SELECT 1 FROM evento_contable
                                   WHERE clave_unica = 'APERTURA-AF-' || af.codigo) THEN
                            CONTINUE;
                        END IF;
                        SELECT p.id, e.anio INTO v_periodo, v_anio
                        FROM periodo p JOIN ejercicio e ON e.id = p.ejercicio_id
                        WHERE af.fecha_uso BETWEEN p.fecha_inicio AND p.fecha_fin
                        ORDER BY p.fecha_inicio LIMIT 1;
                        IF v_periodo IS NULL THEN
                            SELECT p.id, e.anio INTO v_periodo, v_anio
                            FROM periodo p JOIN ejercicio e ON e.id = p.ejercicio_id
                            ORDER BY p.fecha_inicio LIMIT 1;
                        END IF;
                        IF v_periodo IS NULL THEN
                            CONTINUE;
                        END IF;
                        -- La fecha contable debe caer dentro del período elegido; una
                        -- fecha_uso fuera de todo ejercicio (p. ej. 2024) se traslada al
                        -- inicio del período para no romper la coherencia diario/mayor.
                        SELECT CASE WHEN af.fecha_uso BETWEEN p.fecha_inicio AND p.fecha_fin
                                    THEN af.fecha_uso ELSE p.fecha_inicio END
                        INTO v_fecha
                        FROM periodo p WHERE p.id = v_periodo;
                        v_evento := gen_random_uuid();
                        v_partida := gen_random_uuid();
                        INSERT INTO folio_secuencia (anio, tipo, ultimo) VALUES (v_anio, 'AP', 1)
                        ON CONFLICT (anio, tipo) DO UPDATE SET ultimo = folio_secuencia.ultimo + 1
                        RETURNING ultimo INTO v_seq;
                        v_folio := 'AP-' || v_anio || '-' || lpad(v_seq::text, 6, '0');
                        INSERT INTO evento_contable (id, clave_unica, descripcion)
                        VALUES (v_evento, 'APERTURA-AF-' || af.codigo,
                                'Apertura de activo fijo ' || af.codigo || ' ' || af.nombre);
                        INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto,
                                             estado, origen, documento_soporte, evento_id,
                                             creado_en, actualizado_en)
                        VALUES (v_partida, 'AP', v_folio, v_fecha,
                                v_periodo,
                                'Apertura activo fijo ' || af.codigo || ' ' || af.nombre,
                                'CONTABILIZADA', 'SEED', 'Apertura inicial de activo fijo',
                                v_evento, now(), now());
                        INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, debe, haber)
                        VALUES (gen_random_uuid(), v_partida, 1, af.cuenta_activo, af.costo, 0),
                               (gen_random_uuid(), v_partida, 2, '310301', 0, af.costo);
                        UPDATE evento_contable SET partida_contabilizada_id = v_partida
                        WHERE id = v_evento;
                    END LOOP;
                END $$;
                """;
        try (var st = conn.createStatement()) {
            st.execute(sql);
        } catch (java.sql.SQLException e) {
            throw new sv.edu.ues.colibri.persistence.jdbc.PersistenceException(
                    "No se pudieron generar los asientos de apertura de activos fijos", e);
        }
    }

    /**
     * Crea las cuentas iniciales solo cuando todavía no existen. Un arranque no
     * es un mecanismo de recuperación: nunca debe restablecer credenciales,
     * roles, estado ni datos de una cuenta ya administrada.
     */
    /**
     * Crea una cuenta demo únicamente si todavía no existe.
     *
     * @param conn conexión activa de la transacción
     * @param usuario nombre de usuario
     * @param nombre nombre visible
     * @param password contraseña inicial
     * @param rol rol funcional
     * @param email correo electrónico
     */
    private void asegurarUsuarioDemo(java.sql.Connection conn, String usuario, String nombre,
                                     String password, RolUsuario rol, String email) {
        var existente = contadorRepository.buscarPorUsuario(conn, usuario);
        if (existente.isPresent()) {
            return;
        }

        byte[] salt = PasswordHasher.generarSal();
        byte[] hash = PasswordHasher.hash(password.toCharArray(), salt);
        String codigo = PasswordHasher.generarCodigoRecuperacion();
        byte[] codigoSalt = PasswordHasher.generarSal();
        byte[] codigoHash = PasswordHasher.hash(codigo.toCharArray(), codigoSalt);
        if (USUARIO_DEFAULT.equals(usuario)) {
            this.codigoRecuperacionGenerado = codigo;
        }
        Contador creado = new Contador(
                UUID.randomUUID(), usuario, nombre, hash, salt, 0, null,
                codigoHash, codigoSalt, true, null, rol, email);
        contadorRepository.insertar(conn, creado);
        bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                creado.getId(), "BOOTSTRAP", "USUARIO", creado.getId().toString(),
                null, usuario, "Cuenta inicial creada"));
    }
}
