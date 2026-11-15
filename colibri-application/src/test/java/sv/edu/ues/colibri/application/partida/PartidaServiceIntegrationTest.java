package sv.edu.ues.colibri.application.partida;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.application.bootstrap.BootstrapService;
import sv.edu.ues.colibri.application.bootstrap.CasoContableSeedService;
import sv.edu.ues.colibri.domain.exception.DomainException;
import sv.edu.ues.colibri.domain.model.Partida;
import sv.edu.ues.colibri.domain.model.PartidaDetalle;
import sv.edu.ues.colibri.domain.model.TipoPartida;
import sv.edu.ues.colibri.persistence.config.DatabaseConfig;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Requiere PostgreSQL con base colibri_test. Se omite si no está disponible.
 */
class PartidaServiceIntegrationTest {

    private static AppContext context;
    private static boolean disponible;

    @BeforeAll
    static void setup() {
        String url = System.getenv().getOrDefault("COLIBRI_TEST_DB_URL",
                "jdbc:postgresql://127.0.0.1:5434/colibri_test");
        String user = System.getenv().getOrDefault("COLIBRI_TEST_DB_USER", "colibri");
        String pass = System.getenv().getOrDefault("COLIBRI_TEST_DB_PASSWORD", "colibri");
        try {
            DriverManager.getConnection(url, user, pass).close();
            disponible = true;
            DatabaseConfig config = new DatabaseConfig(url, user, pass, user, pass);
            context = AppContext.iniciarConConfig(config);
            context.auth().login(BootstrapService.USUARIO_DEFAULT,
                    BootstrapService.PASSWORD_DEFAULT.toCharArray());
            if (!context.catalogo().catalogoAprobado()) {
                context.catalogo().aprobarCatalogoInicial();
            }
        } catch (Exception e) {
            disponible = false;
            System.err.println("Saltando integración: " + e.getMessage());
        }
    }

    @AfterAll
    static void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    static boolean dbDisponible() {
        return disponible;
    }

    @Test
    @EnabledIf("dbDisponible")
    void catalogoSemillaCargaTablaVIII() {
        assertTrue(context.catalogo().contarCuentas() >= 170);
    }

    @Test
    @EnabledIf("dbDisponible")
    void descuadreDeUnCentavoNoContabiliza() {
        Partida borrador = context.partidas().guardarBorrador(
                TipoPartida.DI, LocalDate.of(2026, 1, 12), "Descuadre", "T-01",
                "EVT-DESCUADRE-" + UUID.randomUUID(),
                List.of(
                        PartidaDetalle.nueva("110102", new BigDecimal("10.00"), BigDecimal.ZERO),
                        PartidaDetalle.nueva("310101", BigDecimal.ZERO, new BigDecimal("9.99"))
                ),
                null);
        assertThrows(DomainException.class, () -> context.partidas().contabilizar(borrador.getId()));
    }

    @Test
    @EnabledIf("dbDisponible")
    void eventoDuplicadoNoDobleContabiliza() {
        String clave = "EVT-IDEMP-" + UUID.randomUUID();
        List<PartidaDetalle> lineas = List.of(
                PartidaDetalle.nueva("110102", new BigDecimal("500.00"), BigDecimal.ZERO),
                PartidaDetalle.nueva("310101", BigDecimal.ZERO, new BigDecimal("500.00"))
        );
        Partida p1 = context.partidas().guardarBorrador(
                TipoPartida.DI, LocalDate.of(2026, 1, 12), "Aporte test", "ACTA",
                clave, lineas, null);
        Partida cont = context.partidas().contabilizar(p1.getId());
        assertNotNull(cont.getFolio());

        DomainException ex = assertThrows(DomainException.class, () ->
                context.partidas().guardarBorrador(
                        TipoPartida.DI, LocalDate.of(2026, 1, 12), "Duplicado", "ACTA",
                        clave, lineas, null));
        assertTrue(ex.getMessage().toLowerCase().contains("contabilizado")
                || ex.getMessage().toLowerCase().contains("duplic"));

        // Reintento de contabilizar la misma partida es idempotente
        Partida otra = context.partidas().contabilizar(p1.getId());
        assertEquals(cont.getFolio(), otra.getFolio());
    }

    @Test
    @EnabledIf("dbDisponible")
    void cuentaAgrupadoraNoRecibeMovimiento() {
        assertThrows(DomainException.class, () ->
                context.partidas().guardarBorrador(
                        TipoPartida.DI, LocalDate.of(2026, 1, 12), "Inválida", "X",
                        "EVT-AGR-" + UUID.randomUUID(),
                        List.of(
                                PartidaDetalle.nueva("1101", new BigDecimal("1.00"), BigDecimal.ZERO),
                                PartidaDetalle.nueva("310101", BigDecimal.ZERO, new BigDecimal("1.00"))
                        ),
                        null));
    }

                @Test
                @EnabledIf("dbDisponible")
                void usuariosPersonalesPersistenBloqueoYPermitenDesactivacion() {
                context.auth().login(BootstrapService.USUARIO_SOPORTE,
                    BootstrapService.PASSWORD_SOPORTE.toCharArray());
                String usuario = "test-" + UUID.randomUUID().toString().substring(0, 8);
                String password = "ClaveSegura2026!";
                context.auth().crearUsuario(usuario, "Usuario de prueba", password.toCharArray());
                context.auth().logout();

                for (int intento = 0; intento < 5; intento++) {
                    assertThrows(DomainException.class,
                        () -> context.auth().login(usuario, "incorrecta".toCharArray()));
                }
                DomainException bloqueada = assertThrows(DomainException.class,
                    () -> context.auth().login(usuario, password.toCharArray()));
                assertTrue(bloqueada.getMessage().contains("bloqueada"));

                context.auth().login(BootstrapService.USUARIO_SOPORTE,
                    BootstrapService.PASSWORD_SOPORTE.toCharArray());
                var nuevaCuenta = context.auth().listarUsuarios().stream()
                    .filter(c -> c.getUsuario().equals(usuario)).findFirst().orElseThrow();
                context.auth().cambiarEstadoUsuario(nuevaCuenta.getId(), false);
                assertThrows(DomainException.class,
                    () -> context.auth().login(usuario, password.toCharArray()));

                context.auth().logout();
                context.auth().login(BootstrapService.USUARIO_DEFAULT,
                    BootstrapService.PASSWORD_DEFAULT.toCharArray());
                }

                @Test
                @EnabledIf("dbDisponible")
                void librosPresentanCuentasTHojaDeTrabajoYEstadosBalanceados() {
                String evento = "EVT-REPORTES-" + UUID.randomUUID();
                Partida partida = context.partidas().guardarBorrador(
                    TipoPartida.DI, LocalDate.of(2026, 1, 12), "Movimiento para reportes", "TEST",
                    evento, List.of(
                        PartidaDetalle.nueva("110102", new BigDecimal("125.00"), BigDecimal.ZERO),
                        PartidaDetalle.nueva("310101", BigDecimal.ZERO, new BigDecimal("125.00"))
                    ), null);
                context.partidas().contabilizar(partida.getId());

                var lineasT = context.libros().cuentaT("110102",
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
                assertTrue(lineasT.stream().anyMatch(l -> l.folio() != null));

                var hoja = context.libros().hojaTrabajo(
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
                assertTrue(hoja.stream().anyMatch(l -> l.cuenta().equals("110102")));
                assertTrue(hoja.stream().anyMatch(l -> l.cuenta().equals("310101")));

                var situacion = context.libros().estadoSituacion(LocalDate.of(2026, 1, 31));
                var diferencia = situacion.stream().filter(l -> l.concepto().equals("DIFERENCIA DE BALANCE"))
                    .findFirst().orElseThrow().importe();
                assertEquals(0, diferencia.compareTo(BigDecimal.ZERO));

                var resultados = context.libros().estadoResultados(
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
                assertTrue(resultados.stream().anyMatch(l -> l.concepto().equals("UTILIDAD BRUTA")));
                assertTrue(resultados.stream().anyMatch(l -> l.concepto().equals("UTILIDAD (PÉRDIDA) NETA DEL PERÍODO")));
                }

    @Test
    @EnabledIf("dbDisponible")
    void semillasCoincidenConCasoDYSonIdempotentes() {
        new CasoContableSeedService(context.jdbc()).asegurarDatosIniciales();
        Map<String, BigDecimal> saldos = context.jdbc().inTransaction(conn -> {
            Map<String, BigDecimal> resultado = new LinkedHashMap<>();
            try (var ps = conn.prepareStatement("""
                    SELECT d.codigo_cuenta, SUM(d.debe - d.haber) AS saldo
                    FROM partida p
                    JOIN partida_detalle d ON d.partida_id = p.id
                    JOIN evento_contable e ON e.id = p.evento_id
                    WHERE e.clave_unica LIKE 'SEED-CASO-D-2026-%'
                      AND p.fecha_contable BETWEEN ? AND ?
                    GROUP BY d.codigo_cuenta
                    """)) {
                ps.setObject(1, LocalDate.of(2026, 1, 1));
                ps.setObject(2, LocalDate.of(2026, 1, 31));
                try (var rs = ps.executeQuery()) {
                    while (rs.next()) {
                        resultado.put(rs.getString("codigo_cuenta"), rs.getBigDecimal("saldo"));
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("No se pudieron comprobar los saldos del caso D", e);
            }
            return resultado;
        });

        BigDecimal debe = saldos.values().stream().filter(s -> s.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal haber = saldos.values().stream().filter(s -> s.signum() < 0)
                .map(BigDecimal::negate).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, debe.compareTo(new BigDecimal("10430.00")));
        assertEquals(0, haber.compareTo(new BigDecimal("10430.00")));
        assertEquals(0, saldos.get("110102").compareTo(new BigDecimal("8824.80")));
        assertEquals(0, saldos.get("110301").compareTo(new BigDecimal("104.00")));
        assertEquals(0, saldos.get("110702").compareTo(new BigDecimal("400.00")));

        var orden = context.inventario().ordenPrincipal();
        assertEquals(0, orden.costoAcumulado().compareTo(new BigDecimal("1800.00")));
        assertEquals(0, orden.margen().compareTo(new BigDecimal("700.00")));

        long partidasSemilla = context.jdbc().inTransaction(conn -> {
            try (var ps = conn.prepareStatement("""
                    SELECT COUNT(*) FROM evento_contable
                    WHERE clave_unica LIKE 'SEED-CASO-D-2026-%'
                    """); var rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            } catch (SQLException e) {
                throw new IllegalStateException("No se pudo comprobar la idempotencia de la semilla", e);
            }
        });
        assertEquals(17, partidasSemilla);
    }
}
