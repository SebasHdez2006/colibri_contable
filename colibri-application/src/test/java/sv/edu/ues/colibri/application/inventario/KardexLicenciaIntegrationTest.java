package sv.edu.ues.colibri.application.inventario;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.application.bootstrap.BootstrapService;
import sv.edu.ues.colibri.domain.exception.DomainException;
import sv.edu.ues.colibri.domain.model.Partida;
import sv.edu.ues.colibri.domain.model.Tercero;
import sv.edu.ues.colibri.persistence.config.DatabaseConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Requiere PostgreSQL con base colibri_test. Se omite si no está disponible.
 */
class KardexLicenciaIntegrationTest {

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
    void kardexRegistraLiberacionEntregaDevolucionYRevocacion() {
        LocalDate fecha = context.periodos().periodoAbierto()
                .map(p -> p.getFechaInicio()).orElse(LocalDate.of(2026, 1, 12));
        Tercero cliente = context.operaciones().guardarTercero(new Tercero(
                UUID.randomUUID(), "TEST-KARDEX-" + UUID.randomUUID().toString().substring(0, 8),
                null, "Cliente kárdex de prueba", null, true, false, true));

        String codigoLote = "TEST-KARDEX-" + UUID.randomUUID().toString().substring(0, 8);
        var lote = context.inventario().liberarLote(codigoLote, 3,
                new BigDecimal("300.00"), fecha);

        List<InventarioCostoService.KardexInfo> kardex =
                context.inventario().kardexDeLote(lote.id());
        assertEquals(1, kardex.size());
        assertEquals("LIBERACION", kardex.get(0).tipo());
        assertEquals(3, kardex.get(0).saldoUnidades());
        assertEquals(0, kardex.get(0).saldoValor().compareTo(new BigDecimal("300.00")));

        Set<UUID> antesVenta = idsEntregadas();
        Partida venta = context.inventario().venderLicenciaPeps(cliente.getId(), fecha,
                new BigDecimal("150.00"), null);
        assertNotNull(venta.getFolio());
        UUID licenciaVendida = idsEntregadas().stream()
                .filter(id -> !antesVenta.contains(id)).findFirst().orElseThrow();
        var infoVendida = context.inventario().listarLicenciasEntregadas().stream()
                .filter(l -> l.id().equals(licenciaVendida)).findFirst().orElseThrow();

        UUID loteVenta = loteIdDe(infoVendida.codigoLote());
        kardex = context.inventario().kardexDeLote(loteVenta);
        var entrega = kardex.get(kardex.size() - 1);
        assertEquals("ENTREGA", entrega.tipo());
        assertEquals(-1, entrega.unidades());
        assertEquals(infoVendida.numero(), entrega.licenciaNumero().intValue());
        assertEquals(venta.getFolio(), entrega.folio());
        int saldoDespuesVenta = entrega.saldoUnidades();
        BigDecimal valorDespuesVenta = entrega.saldoValor();

        Partida reversa = context.inventario().devolverLicencia(licenciaVendida, fecha,
                "Devolución de prueba");
        assertNotNull(reversa);
        assertNotNull(reversa.getFolio());

        kardex = context.inventario().kardexDeLote(loteVenta);
        var devolucion = kardex.get(kardex.size() - 1);
        assertEquals("DEVOLUCION", devolucion.tipo());
        assertEquals(1, devolucion.unidades());
        assertEquals(saldoDespuesVenta + 1, devolucion.saldoUnidades());
        assertEquals(0, devolucion.saldoValor().compareTo(valorDespuesVenta
                .add(infoVendida.costoUnitario().setScale(2, RoundingMode.HALF_UP))));
        assertEquals(reversa.getFolio(), devolucion.folio());
        assertTrue(context.inventario().listarLicenciasEntregadas().stream()
                .noneMatch(l -> l.id().equals(licenciaVendida)));

        // Ni una segunda devolución ni una revocación aplican fuera de ENTREGADA.
        assertThrows(DomainException.class,
                () -> context.inventario().devolverLicencia(licenciaVendida, fecha, "duplicada"));
        assertThrows(DomainException.class,
                () -> context.inventario().revocarLicencia(licenciaVendida, fecha, "no aplica"));

        UUID disponibleId = primerLicenciaDisponible();
        if (disponibleId != null) {
            assertThrows(DomainException.class,
                    () -> context.inventario().revocarLicencia(disponibleId, fecha, "x"));
            assertThrows(DomainException.class,
                    () -> context.inventario().devolverLicencia(disponibleId, fecha, "x"));
        }

        // La unidad devuelta vuelve a ser elegible por PEPS; al revenderla y
        // revocarla, el kárdex registra la revocación sin mover existencias.
        Set<UUID> antesRevocacion = idsEntregadas();
        context.inventario().venderLicenciaPeps(cliente.getId(), fecha,
                new BigDecimal("150.00"), null);
        UUID segunda = idsEntregadas().stream()
                .filter(id -> !antesRevocacion.contains(id)).findFirst().orElseThrow();
        var infoSegunda = context.inventario().listarLicenciasEntregadas().stream()
                .filter(l -> l.id().equals(segunda)).findFirst().orElseThrow();
        UUID loteSegunda = loteIdDe(infoSegunda.codigoLote());
        kardex = context.inventario().kardexDeLote(loteSegunda);
        int saldoPrevioRevocacion = kardex.get(kardex.size() - 1).saldoUnidades();

        context.inventario().revocarLicencia(segunda, fecha, "Derecho revocado");
        kardex = context.inventario().kardexDeLote(loteSegunda);
        var revocacion = kardex.get(kardex.size() - 1);
        assertEquals("REVOCACION", revocacion.tipo());
        assertEquals(0, revocacion.unidades());
        assertEquals(saldoPrevioRevocacion, revocacion.saldoUnidades());
        assertEquals(infoSegunda.numero(), revocacion.licenciaNumero().intValue());
        assertTrue(context.inventario().listarLicenciasEntregadas().stream()
                .noneMatch(l -> l.id().equals(segunda)));
    }

    private Set<UUID> idsEntregadas() {
        return context.inventario().listarLicenciasEntregadas().stream()
                .map(InventarioCostoService.LicenciaEntregadaInfo::id)
                .collect(Collectors.toSet());
    }

    private UUID loteIdDe(String codigoLote) {
        return context.inventario().listarLotes().stream()
                .filter(l -> l.codigo().equals(codigoLote)).findFirst().orElseThrow().id();
    }

    private UUID primerLicenciaDisponible() {
        return context.jdbc().inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM licencia WHERE estado='DISPONIBLE' LIMIT 1")) {
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? (UUID) rs.getObject(1) : null;
                }
            } catch (SQLException e) {
                throw new IllegalStateException("No se pudo consultar licencias disponibles", e);
            }
        });
    }
}
