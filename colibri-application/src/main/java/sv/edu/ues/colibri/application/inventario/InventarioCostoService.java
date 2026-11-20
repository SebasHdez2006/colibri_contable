package sv.edu.ues.colibri.application.inventario;

import sv.edu.ues.colibri.application.auth.AuthService;
import sv.edu.ues.colibri.application.configuracion.ParametrizacionService;
import sv.edu.ues.colibri.application.partida.PartidaService;
import sv.edu.ues.colibri.domain.exception.DomainException;
import sv.edu.ues.colibri.domain.model.BitacoraEntrada;
import sv.edu.ues.colibri.domain.model.Partida;
import sv.edu.ues.colibri.domain.model.PartidaDetalle;
import sv.edu.ues.colibri.domain.model.TipoPartida;
import sv.edu.ues.colibri.persistence.jdbc.JdbcSupport;
import sv.edu.ues.colibri.persistence.jdbc.PersistenceException;
import sv.edu.ues.colibri.persistence.repo.BitacoraRepository;
import sv.edu.ues.colibri.persistence.repo.PartidaRepository;
import sv.edu.ues.colibri.persistence.repo.TerceroRepository;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Inventario de licencias y costeo por órdenes, PEPS y procesos SaaS.
 */
public class InventarioCostoService {

    private final JdbcSupport jdbc;
    private final AuthService auth;
    private final PartidaService partidas;
    private final ParametrizacionService parametrizacion;
    private final PartidaRepository partidaRepository = new PartidaRepository();
    private final TerceroRepository terceros = new TerceroRepository();
    private final BitacoraRepository bitacoraRepository = new BitacoraRepository();

    /**
     * Crea el servicio de inventario y costeo.
     *
     * @param jdbc            soporte transaccional
     * @param auth            servicio de autenticación
     * @param partidas        servicio de partidas
     * @param parametrizacion servicio de parametrización
     */
    public InventarioCostoService(JdbcSupport jdbc, AuthService auth, PartidaService partidas,
            ParametrizacionService parametrizacion) {
        this.jdbc = jdbc;
        this.auth = auth;
        this.partidas = partidas;
        this.parametrizacion = parametrizacion;
    }

    public record LoteInfo(UUID id, String codigo, int disponibles, BigDecimal costoUnitario,
            String productoCodigo, String productoNombre, String versionLabel) {
    }

    /** Producto del catálogo digital que agrupa versiones liberables. */
    public record ProductoInfo(UUID id, String codigo, String nombre, String modalidad,
            boolean activo) {
    }

    /** Versión de un producto con su estado dentro del ciclo EN_PRODUCCION → LIBERADA → DESCONTINUADA. */
    public record VersionProductoInfo(UUID id, UUID productoId, String productoCodigo,
            String productoNombre, String versionLabel, String estado) {
    }

    public record OrdenResumen(String codigo, String descripcion, String estado) {
    }

    public record OrdenInfo(UUID id, String codigo, String descripcion, BigDecimal presupuesto,
            BigDecimal precioVenta, BigDecimal costoAcumulado, BigDecimal margen) {
    }

    /**
     * Rentabilidad de una orden de servicio con su estado del ciclo de vida.
     */
    public record OrdenRentabilidad(String codigo, String descripcion, String estado,
            BigDecimal presupuesto, BigDecimal precioVenta, BigDecimal costoAcumulado,
            BigDecimal margen) {
    }

    /**
     * Resultado gerencial de la comparación entre el presupuesto de la orden y
     * el costo real acumulado en los registros contables.
     */
    public record DesviacionOrdenInfo(UUID id, String codigo, BigDecimal costoEstimado,
            BigDecimal costoReal, BigDecimal variacionAbsoluta,
            BigDecimal variacionPorcentual, BigDecimal indiceEjecucion,
            BigDecimal ahorroPresupuestario, BigDecimal sobrecosto,
            BigDecimal utilidadEsperada, BigDecimal utilidadReal,
            BigDecimal rentabilidadSobreVenta, String diagnostico) {
    }

    /**
     * Unidad reservada por PEPS. El lote se conserva para restituir exactamente
     * la disponibilidad afectada si la contabilización posterior falla.
     */
    private record ReservaLicencia(UUID licenciaId, UUID loteId, int numero,
            BigDecimal costoContable, String codigoLote) {
    }

    /** Movimiento del kárdex de licencias de un lote. */
    public record KardexInfo(long secuencia, LocalDate fecha, String tipo,
            Integer licenciaNumero, int unidades, BigDecimal costoUnitario,
            BigDecimal importe, int saldoUnidades, BigDecimal saldoValor,
            String detalle, String folio) {
    }

    /** Licencia entregada pendiente de devolución o revocación. */
    public record LicenciaEntregadaInfo(UUID id, String codigoLote, int numero,
            String cliente, LocalDate fechaEntrega, BigDecimal costoUnitario) {
    }

    /** Estado actual de una licencia para los ciclos de devolución/revocación. */
    private record LicenciaCicloVida(UUID id, UUID loteId, int numero,
            String codigoLote, BigDecimal costoUnitario, String estado) {
    }

    /** Saldo corrido del kárdex de un lote. */
    private record SaldoKardex(int unidades, BigDecimal valor) {
    }

    /** Licencia leída junto con la partida de su última entrega, si existe. */
    private record DatosDevolucion(LicenciaCicloVida licencia, UUID partidaVentaId) {
    }

    /** Desglose del costo SaaS por proceso para un período y sus usuarios-mes. */
    public record CostoSaasInfo(BigDecimal alojamiento, BigDecimal soporte,
            BigDecimal infraestructuraCompartida, int usuariosMes,
            BigDecimal total, BigDecimal costoUsuarioMes) {
    }

    /** Costo SaaS asignado a un plan por su participación en usuarios-mes. */
    public record CostoSaasPlanInfo(String codigoPlan, String nombrePlan, int usuariosMesPlan,
            int usuariosMesTotal, BigDecimal alojamiento,
            BigDecimal soporte, BigDecimal infraestructuraCompartida,
            BigDecimal totalAsignado, BigDecimal costoUsuarioMes) {
    }

    /** Acumulación de los elementos del costo de un servicio facturado por plan. */
    public record PlanCostoInfo(String codigoPlan, String nombrePlan, String tipoFacturacion,
            BigDecimal manoObra, BigDecimal serviciosDirectos,
            BigDecimal indirectos, BigDecimal total) {
    }

    /** Costos reales acumulados en orden_consumo, agrupados por tipo de costo. */
    public record CostosHistoricosInfo(BigDecimal mod, BigDecimal servicios,
            BigDecimal cif, BigDecimal total) {
    }

    /** Comparación gerencial de métodos; no registra ni permite una salida UEPS. */
    public record ComparativoInventarioInfo(int unidadesSalida, BigDecimal costoPeps,
            BigDecimal costoPromedio, BigDecimal costoUeps,
            BigDecimal saldoPeps, BigDecimal saldoPromedio,
            BigDecimal saldoUeps) {
    }

    /**
     * Punto del diagrama «presupuesto vs. costo real» del tablero de inicio:
     * un objeto de la línea elegida con su presupuesto y su costo real.
     * En proyectos es la orden de servicio; en licencias el lote; en accesos
     * el plan SaaS con su facturación presupuestada y su costo asignado.
     */
    public record PuntoCostoPresupuesto(String etiqueta, String detalle,
            BigDecimal presupuesto, BigDecimal costoReal) {
    }

    /**
     * Fila de la tabla de estructura de costos del tablero: precio unitario
     * y costo desagregado por rubro (materia prima / servicios directos, mano
     * de obra y CIF) de un proyecto, licencia o acceso. El precio unitario es
     * por proyecto, por licencia o por usuario-mes según la línea.
     */
    public record EstructuraCostoInfo(String tipo, String codigo, String detalle,
            BigDecimal precioUnitario, BigDecimal materiaPrima, BigDecimal manoObra,
            BigDecimal cif) {
        public BigDecimal costoTotal() {
            return materiaPrima.add(manoObra).add(cif);
        }
    }

    private record PlanRef(UUID id, String codigo, String nombre, String modalidad,
            String tipoFacturacion) {
    }

    record SaasParticipacion(UUID planId, String codigo, int usuariosMes) {
    }

    private record ParticipacionCentavos(UUID planId, String codigo, BigInteger base,
            BigInteger residuo) {
    }

    /**
     * Lista los lotes de licencias registrados.
     *
     * @return lista de lotes
     */
    public List<LoteInfo> listarLotes() {
        auth.exigirContador();
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT lo.id, lo.codigo_lote, lo.unidades_disponibles, lo.costo_unitario,
                           p.codigo AS producto_codigo, p.nombre AS producto_nombre,
                           pv.version_label
                    FROM lote_licencia lo
                    JOIN producto_version pv ON pv.id = lo.version_id
                    JOIN producto p ON p.id = pv.producto_id
                    ORDER BY lo.codigo_lote
                    """);
                    ResultSet rs = ps.executeQuery()) {
                List<LoteInfo> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new LoteInfo((UUID) rs.getObject("id"), rs.getString("codigo_lote"),
                            rs.getInt("unidades_disponibles"), rs.getBigDecimal("costo_unitario"),
                            rs.getString("producto_codigo"), rs.getString("producto_nombre"),
                            rs.getString("version_label")));
                }
                return list;
            } catch (SQLException e) {
                throw new PersistenceException("Error al listar lotes", e);
            }
        });
    }

    /**
     * Lista las órdenes de servicio registradas para selección de auxiliares.
     *
     * @return lista de órdenes
     */
    public List<OrdenResumen> listarOrdenes() {
        auth.exigirContador();
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT codigo, descripcion, estado FROM orden_servicio ORDER BY codigo");
                    ResultSet rs = ps.executeQuery()) {
                List<OrdenResumen> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new OrdenResumen(rs.getString("codigo"),
                            rs.getString("descripcion"), rs.getString("estado")));
                }
                return list;
            } catch (SQLException e) {
                throw new PersistenceException("Error al listar órdenes de servicio", e);
            }
        });
    }

    /**
     * Lista los productos del catálogo digital con su estado de alta.
     *
     * @return lista de productos ordenados por código
     */
    public List<ProductoInfo> listarProductos() {
        auth.exigirContador();
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, codigo, nombre, modalidad, activo FROM producto ORDER BY codigo");
                    ResultSet rs = ps.executeQuery()) {
                List<ProductoInfo> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new ProductoInfo((UUID) rs.getObject("id"), rs.getString("codigo"),
                            rs.getString("nombre"), rs.getString("modalidad"), rs.getBoolean("activo")));
                }
                return list;
            } catch (SQLException e) {
                throw new PersistenceException("Error al listar productos", e);
            }
        });
    }

    /**
     * Lista las versiones de producto con su estado de liberación.
     *
     * @return lista de versiones ordenadas por producto y etiqueta
     */
    public List<VersionProductoInfo> listarVersiones() {
        auth.exigirContador();
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT pv.id, pv.producto_id, p.codigo AS producto_codigo,
                           p.nombre AS producto_nombre, pv.version_label, pv.estado
                    FROM producto_version pv
                    JOIN producto p ON p.id = pv.producto_id
                    ORDER BY p.codigo, pv.version_label
                    """);
                    ResultSet rs = ps.executeQuery()) {
                List<VersionProductoInfo> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new VersionProductoInfo((UUID) rs.getObject("id"),
                            (UUID) rs.getObject("producto_id"), rs.getString("producto_codigo"),
                            rs.getString("producto_nombre"), rs.getString("version_label"),
                            rs.getString("estado")));
                }
                return list;
            } catch (SQLException e) {
                throw new PersistenceException("Error al listar versiones de producto", e);
            }
        });
    }

    /**
     * Guarda o actualiza un producto del catálogo digital. La modalidad debe
     * ser LICENCIA, SAAS o PROYECTO, según el CHECK del esquema.
     *
     * @param codigo código único del producto
     * @param nombre nombre comercial del producto
     * @param modalidad LICENCIA, SAAS o PROYECTO
     * @param activo si el producto admite nuevas versiones y operaciones
     */
    public void guardarProducto(String codigo, String nombre, String modalidad, boolean activo) {
        var contador = auth.exigirContador();
        String cod = exigirTexto(codigo, "El código del producto");
        if (cod.length() > 40) {
            throw new DomainException("El código del producto no puede exceder 40 caracteres.");
        }
        String nom = exigirTexto(nombre, "El nombre del producto");
        if (nom.length() > 200) {
            throw new DomainException("El nombre del producto no puede exceder 200 caracteres.");
        }
        String mod = exigirTexto(modalidad, "La modalidad del producto").toUpperCase(Locale.ROOT);
        if (!List.of("LICENCIA", "SAAS", "PROYECTO").contains(mod)) {
            throw new DomainException("Modalidad de producto inválida. Use LICENCIA, SAAS o PROYECTO.");
        }
        jdbc.runInTransaction(conn -> {
            String anterior = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT nombre || '|' || modalidad || '|' || activo FROM producto WHERE codigo=?")) {
                ps.setString(1, cod);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        anterior = rs.getString(1);
                    }
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al leer el producto " + cod, e);
            }
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO producto (id, codigo, nombre, modalidad, activo)
                    VALUES (?,?,?,?,?)
                    ON CONFLICT (codigo) DO UPDATE SET nombre=EXCLUDED.nombre,
                        modalidad=EXCLUDED.modalidad, activo=EXCLUDED.activo
                    """)) {
                ps.setObject(1, UUID.randomUUID());
                ps.setString(2, cod);
                ps.setString(3, nom);
                ps.setString(4, mod);
                ps.setBoolean(5, activo);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new PersistenceException("Error al guardar el producto " + cod, e);
            }
            bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                    contador.getId(), "PRODUCTO", "PRODUCTO", cod, anterior,
                    nom + "|" + mod + "|" + activo, null));
        });
    }

    /**
     * Registra una versión nueva del producto en estado EN_PRODUCCION.
     *
     * @param productoId identificador del producto
     * @param versionLabel etiqueta de la versión (ej. 2.0)
     */
    public void guardarVersion(UUID productoId, String versionLabel) {
        var contador = auth.exigirContador();
        if (productoId == null) {
            throw new DomainException("Seleccione el producto de la versión.");
        }
        String etiqueta = exigirTexto(versionLabel, "La etiqueta de la versión");
        if (etiqueta.length() > 40) {
            throw new DomainException("La etiqueta de la versión no puede exceder 40 caracteres.");
        }
        jdbc.runInTransaction(conn -> {
            String productoCodigo;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT codigo, activo FROM producto WHERE id=?")) {
                ps.setObject(1, productoId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new DomainException("Producto no encontrado para la versión.");
                    }
                    if (!rs.getBoolean("activo")) {
                        throw new DomainException("El producto está inactivo; "
                                + "reactívelo antes de crear versiones.");
                    }
                    productoCodigo = rs.getString("codigo");
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al validar el producto de la versión", e);
            }
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO producto_version (id, producto_id, version_label, estado)
                    VALUES (?,?,?,'EN_PRODUCCION')
                    """)) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, productoId);
                ps.setString(3, etiqueta);
                ps.executeUpdate();
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    throw new DomainException("El producto " + productoCodigo
                            + " ya tiene la versión " + etiqueta + ".");
                }
                throw new PersistenceException("Error al crear la versión " + etiqueta, e);
            }
            bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                    contador.getId(), "VERSION_PRODUCTO", "PRODUCTO", productoCodigo, null,
                    etiqueta + " · EN_PRODUCCION", null));
        });
    }

    /**
     * Avanza el estado de una versión dentro del ciclo
     * EN_PRODUCCION → LIBERADA → DESCONTINUADA (DESCONTINUADA es terminal y no
     * impide vender los lotes ya liberados contra esa versión).
     *
     * @param versionId identificador de la versión
     * @param estado estado destino
     */
    public void cambiarEstadoVersion(UUID versionId, String estado) {
        var contador = auth.exigirContador();
        if (versionId == null) {
            throw new DomainException("Seleccione la versión a actualizar.");
        }
        String destino = exigirTexto(estado, "El estado destino").toUpperCase(Locale.ROOT);
        if (!List.of("EN_PRODUCCION", "LIBERADA", "DESCONTINUADA").contains(destino)) {
            throw new DomainException("Estado de versión inválido.");
        }
        jdbc.runInTransaction(conn -> {
            String actual;
            String etiqueta;
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT pv.estado, pv.version_label, p.codigo
                    FROM producto_version pv
                    JOIN producto p ON p.id = pv.producto_id
                    WHERE pv.id=? FOR UPDATE OF pv
                    """)) {
                ps.setObject(1, versionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new DomainException("Versión no encontrada.");
                    }
                    actual = rs.getString("estado");
                    etiqueta = rs.getString("version_label") + " · " + rs.getString("codigo");
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al leer la versión", e);
            }
            if (actual.equals(destino)) {
                throw new DomainException("La versión ya está en estado " + destino + ".");
            }
            boolean valida = switch (actual) {
                case "EN_PRODUCCION" -> "LIBERADA".equals(destino)
                        || "DESCONTINUADA".equals(destino);
                case "LIBERADA" -> "DESCONTINUADA".equals(destino);
                default -> false;
            };
            if (!valida) {
                throw new DomainException("Transición no permitida: " + actual + " → " + destino
                        + ". El ciclo es EN_PRODUCCION → LIBERADA → DESCONTINUADA.");
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE producto_version SET estado=? WHERE id=?")) {
                ps.setString(1, destino);
                ps.setObject(2, versionId);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new PersistenceException("Error al actualizar la versión", e);
            }
            bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                    contador.getId(), "VERSION_PRODUCTO", "VERSION", versionId.toString(),
                    actual, destino + " · " + etiqueta, null));
        });
    }

    /**
     * Resuelve la versión contra la que se libera un lote. Una versión explícita
     * debe existir y estar LIBERADA; sin selección se toma la primera versión
     * liberada del catálogo en orden determinista.
     */
    private VersionProductoInfo resolverVersionParaLiberacion(UUID versionId) {
        return jdbc.inTransaction(conn -> {
            String sql = """
                    SELECT pv.id, pv.producto_id, p.codigo AS producto_codigo,
                           p.nombre AS producto_nombre, pv.version_label, pv.estado
                    FROM producto_version pv
                    JOIN producto p ON p.id = pv.producto_id
                    WHERE pv.estado = 'LIBERADA'
                    """;
            if (versionId != null) {
                sql += " AND pv.id = ?";
            } else {
                sql += " ORDER BY p.codigo, pv.version_label LIMIT 1";
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                if (versionId != null) {
                    ps.setObject(1, versionId);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        if (versionId != null) {
                            throw new DomainException("La versión seleccionada no existe o no está "
                                    + "LIBERADA; libérela en el catálogo de productos primero.");
                        }
                        throw new DomainException("No hay una versión LIBERADA en el catálogo de "
                                + "productos; cree y libere una antes de liberar lotes.");
                    }
                    return new VersionProductoInfo((UUID) rs.getObject("id"),
                            (UUID) rs.getObject("producto_id"), rs.getString("producto_codigo"),
                            rs.getString("producto_nombre"), rs.getString("version_label"),
                            rs.getString("estado"));
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al resolver la versión del lote", e);
            }
        });
    }

    /**
     * Simula PEPS, promedio ponderado y UEPS sobre las licencias disponibles.
     * Es una comparación de política y no crea partidas ni altera existencias:
     * el único método habilitado para una venta real es
     * {@link #venderLicenciaPeps}.
     */
    public ComparativoInventarioInfo analizarValoracionInventario(int unidadesSalida) {
        auth.exigirContador();
        if (unidadesSalida <= 0) {
            throw new DomainException("Indique al menos una unidad para el análisis de inventario.");
        }
        List<BigDecimal> costos = jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT li.costo_unitario
                    FROM licencia li
                    JOIN lote_licencia lo ON lo.id=li.lote_id
                    WHERE li.estado IN ('DISPONIBLE','DEVUELTA')
                    ORDER BY lo.fecha_liberacion NULLS LAST, lo.codigo_lote, li.numero
                    """);
                    ResultSet rs = ps.executeQuery()) {
                List<BigDecimal> disponibles = new ArrayList<>();
                while (rs.next()) {
                    // Se toma el importe que quedó asignado a la licencia al
                    // liberar el lote. Así la simulación coincide exactamente
                    // con el costo que se descargaría en una venta real,
                    // incluidos los centavos distribuidos entre unidades.
                    disponibles.add(rs.getBigDecimal("costo_unitario"));
                }
                return disponibles;
            } catch (SQLException e) {
                throw new PersistenceException("Error al analizar la valoración de inventario", e);
            }
        });
        return compararValoracion(costos, unidadesSalida);
    }

    /**
     * Calcula una salida hipotética a partir de los costos unitarios ya
     * asignados a las licencias disponibles. No muta el inventario; está
     * separado del acceso JDBC para mantener verificable la comparación.
     */
    static ComparativoInventarioInfo compararValoracion(List<BigDecimal> costos, int unidadesSalida) {
        if (costos == null || costos.isEmpty()) {
            throw new DomainException("No hay licencias disponibles para analizar la valoración.");
        }
        if (unidadesSalida <= 0) {
            throw new DomainException("Indique al menos una unidad para el análisis de inventario.");
        }
        if (unidadesSalida > costos.size()) {
            throw new DomainException("No hay suficientes licencias disponibles para la salida analizada.");
        }
        if (costos.stream().anyMatch(costo -> costo == null || costo.signum() < 0)) {
            throw new IllegalArgumentException("Cada licencia disponible debe tener un costo unitario válido.");
        }
        BigDecimal existencia = costos.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2,
                RoundingMode.HALF_UP);
        BigDecimal peps = costos.subList(0, unidadesSalida).stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal ueps = costos.subList(costos.size() - unidadesSalida, costos.size()).stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal promedio = existencia.divide(BigDecimal.valueOf(costos.size()), 12, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(unidadesSalida)).setScale(2, RoundingMode.HALF_UP);
        return new ComparativoInventarioInfo(unidadesSalida, peps, promedio, ueps,
                existencia.subtract(peps), existencia.subtract(promedio), existencia.subtract(ueps));
    }

    /** Libera lote de N licencias con costo total (ej. caso D: 5 × $100 = $500). */
    public LoteInfo liberarLote(String codigoLote, int unidades, BigDecimal costoTotal, LocalDate fecha) {
        return liberarLote(codigoLote, unidades, costoTotal, fecha, null, null);
    }

    /**
     * Libera lote de N licencias con costo total y, opcionalmente, el cliente
     * titular de cada licencia liberada. El cliente queda registrado en la
     * licencia desde su creación para trazabilidad comercial.
     */
    public LoteInfo liberarLote(String codigoLote, int unidades, BigDecimal costoTotal,
            LocalDate fecha, UUID clienteId) {
        return liberarLote(codigoLote, unidades, costoTotal, fecha, clienteId, null);
    }

    /**
     * Libera lote de N licencias contra una versión de producto del catálogo.
     * La versión debe existir y estar en estado LIBERADA; con {@code versionId}
     * en {@code null} se usa la primera versión liberada del catálogo en orden
     * determinista, pensada para consumidores que no manejan el catálogo.
     * El cliente queda registrado en la licencia desde su creación.
     */
    public LoteInfo liberarLote(String codigoLote, int unidades, BigDecimal costoTotal,
            LocalDate fecha, UUID clienteId, UUID versionId) {
        auth.exigirContador();
        if (clienteId != null) {
            exigirClienteActivo(clienteId);
        }
        VersionProductoInfo version = resolverVersionParaLiberacion(versionId);
        var perfil = parametrizacion.exigirPerfilPosting("INVENTARIO");
        String cuentaInventario = parametrizacion.resolverCuenta(null, perfil.cuentaDebe(),
                "cuenta de inventario del lote");
        String cuentaOrigen = parametrizacion.resolverCuenta(null, perfil.cuentaHaber(),
                "cuenta de origen del lote");
        if (codigoLote == null || codigoLote.isBlank()) {
            throw new DomainException("El código de lote es obligatorio");
        }
        if (unidades <= 0) {
            throw new DomainException("Unidades deben ser > 0");
        }
        if (costoTotal == null || costoTotal.signum() <= 0) {
            throw new DomainException("El costo total del lote debe ser mayor que cero");
        }
        boolean existe = jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM lote_licencia WHERE codigo_lote=?")) {
                ps.setString(1, codigoLote.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al validar el lote", e);
            }
        });
        if (existe) {
            throw new DomainException(
                    "Ya existe el lote '" + codigoLote.trim() + "'. No se generó una partida duplicada.");
        }
        BigDecimal costoContable = costoTotal.setScale(2, RoundingMode.HALF_UP);
        if (costoContable.signum() <= 0) {
            throw new DomainException("El costo total del lote debe ser al menos un centavo");
        }
        BigDecimal unitario = costoContable.divide(BigDecimal.valueOf(unidades), 6, RoundingMode.HALF_UP);

        UUID loteId = UUID.randomUUID();
        Partida borrador = null;
        Partida contabilizada = null;
        try {
            // Compra/producción → inventario según el perfil parametrizado.
            borrador = partidas.guardarBorrador(TipoPartida.DI, fecha,
                    "Liberación lote " + codigoLote, codigoLote, "LOTE-" + codigoLote,
                    List.of(
                            PartidaDetalle.nueva(cuentaInventario, costoContable, BigDecimal.ZERO),
                            PartidaDetalle.nueva(cuentaOrigen, BigDecimal.ZERO, costoContable)),
                    null);
            // Si hay IVA en compra real se usa plantilla de compra; aquí costo neto ya en
            // banco.
            contabilizada = partidas.contabilizar(borrador.getId());
            Partida partidaFinal = contabilizada;
            jdbc.runInTransaction(conn -> {
                try {
                    // Revalida dentro de la transacción: una versión descontinuada
                    // entre la resolución y la escritura no debe recibir lotes.
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT estado FROM producto_version WHERE id=? FOR UPDATE")) {
                        ps.setObject(1, version.id());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next() || !"LIBERADA".equals(rs.getString("estado"))) {
                                throw new DomainException("La versión del lote ya no está LIBERADA.");
                            }
                        }
                    }
                    try (PreparedStatement ps = conn.prepareStatement("""
                            INSERT INTO lote_licencia (id, version_id, codigo_lote, unidades_estimadas, costo_total,
                                costo_unitario, unidades_disponibles, fecha_liberacion, partida_id)
                            VALUES (?,?,?,?,?,?,?,?,?)
                            """)) {
                        ps.setObject(1, loteId);
                        ps.setObject(2, version.id());
                        ps.setString(3, codigoLote);
                        ps.setInt(4, unidades);
                        ps.setBigDecimal(5, costoContable);
                        ps.setBigDecimal(6, unitario);
                        ps.setInt(7, unidades);
                        ps.setObject(8, fecha);
                        ps.setObject(9, partidaFinal.getId());
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = conn.prepareStatement("""
                            INSERT INTO licencia (id, lote_id, numero, estado, costo_unitario, cliente_id)
                            VALUES (?,?,?,'DISPONIBLE',?,?)
                            """)) {
                        for (int i = 1; i <= unidades; i++) {
                            ps.setObject(1, UUID.randomUUID());
                            ps.setObject(2, loteId);
                            ps.setInt(3, i);
                            ps.setBigDecimal(4, costoContablePorUnidad(costoContable, unidades, i));
                            if (clienteId == null) {
                                ps.setNull(5, java.sql.Types.OTHER);
                            } else {
                                ps.setObject(5, clienteId);
                            }
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                    registrarKardex(conn, loteId, null, "LIBERACION", fecha, unidades, unitario,
                            costoContable, partidaFinal.getId(),
                            "Liberación de " + unidades + " licencias",
                            auth.exigirContador().getId());
                    bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                            auth.exigirContador().getId(), "LIBERAR_LOTE", "LOTE",
                            codigoLote.trim(), null,
                            unidades + " licencias · costo " + costoContable + " · " + fecha, null));
                } catch (SQLException e) {
                    throw new PersistenceException("Error al crear lote/licencias", e);
                }
            });
            return new LoteInfo(loteId, codigoLote, unidades, unitario,
                    version.productoCodigo(), version.productoNombre(), version.versionLabel());
        } catch (RuntimeException ex) {
            if (contabilizada != null) {
                UUID partidaId = contabilizada.getId();
                compensar(ex, () -> partidas.revertir(partidaId, fecha,
                        "Lote no creado en auxiliar; partida de inventario revertida."));
            } else if (borrador != null) {
                UUID partidaId = borrador.getId();
                compensar(ex, () -> partidas.anular(partidaId,
                        "Lote no creado en auxiliar."));
            }
            throw ex;
        }
    }

    /**
     * Venta de licencia descargable por PEPS: ingreso + IVA + salida del lote
     * disponible con fecha de liberación más antigua. La interfaz no permite
     * elegir un lote para impedir que el costo se seleccione arbitrariamente.
     */
    public Partida venderLicenciaPeps(UUID clienteId, LocalDate fecha,
            BigDecimal precioNeto, String cuentaIngreso) {
        auth.exigirContador();
        exigirClienteActivo(clienteId);
        var fiscal = parametrizacion.exigirParametrosFiscales();
        var perfilVenta = parametrizacion.exigirPerfilPosting("VENTA");
        var perfilCosto = parametrizacion.exigirPerfilPosting("COSTO_VENTA");
        String cuentaPorCobrar = parametrizacion.resolverCuenta(null, perfilVenta.cuentaDebe(),
                "cuenta por cobrar de la venta de licencia");
        String cuentaDeIngreso = parametrizacion.resolverCuenta(cuentaIngreso, perfilVenta.cuentaHaber(),
                "cuenta de ingreso de la venta de licencia");
        String cuentaCostoVenta = parametrizacion.resolverCuenta(null, perfilCosto.cuentaDebe(),
                "cuenta de costo de venta de licencia");
        String cuentaInventario = parametrizacion.resolverCuenta(null, perfilCosto.cuentaHaber(),
                "cuenta de inventario de licencia");
        if (precioNeto == null || precioNeto.signum() <= 0) {
            throw new DomainException("El precio neto debe ser mayor que cero");
        }
        ReservaLicencia reserva = reservarLicenciaPeps();

        var iva = sv.edu.ues.colibri.domain.iva.IvaCalculator.desdeNeto(
                precioNeto, fiscal.iva());

        List<PartidaDetalle> lineas = List.of(
                PartidaDetalle.nueva(cuentaPorCobrar, iva.total(), BigDecimal.ZERO),
                PartidaDetalle.nueva(cuentaDeIngreso, BigDecimal.ZERO, iva.base()),
                PartidaDetalle.nueva(fiscal.cuentaDebito(), BigDecimal.ZERO, iva.iva()),
                PartidaDetalle.nueva(cuentaCostoVenta, reserva.costoContable(), BigDecimal.ZERO),
                PartidaDetalle.nueva(cuentaInventario, BigDecimal.ZERO, reserva.costoContable()));
        Partida borrador = null;
        Partida contabilizada = null;
        boolean entregaConfirmada = false;
        try {
            // La entrega se confirma antes de la contabilización. Si esta falla, el
            // bloque catch revierte la entrega y devuelve la unidad al lote, por lo
            // que no queda una partida de venta contabilizada sin licencia entregada.
            entregarReserva(reserva, clienteId, fecha);
            entregaConfirmada = true;
            borrador = partidas.guardarBorrador(TipoPartida.DI, fecha, "Venta licencia lote " + reserva.codigoLote(),
                    "LIC-" + reserva.codigoLote(), "VTA-LIC-" + UUID.randomUUID(), new ArrayList<>(lineas), null);
            contabilizada = partidas.contabilizar(borrador.getId());
            vincularPartidaKardex(reserva.licenciaId(), contabilizada.getId());
            jdbc.runInTransaction(conn -> bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                    auth.exigirContador().getId(), "VENTA_LICENCIA", "LICENCIA",
                    reserva.licenciaId().toString(), null,
                    "Lote " + reserva.codigoLote() + " · neto " + precioNeto + " · " + fecha, null)));
            return contabilizada;
        } catch (RuntimeException ex) {
            if (contabilizada != null) {
                UUID partidaId = contabilizada.getId();
                compensar(ex, () -> partidas.revertir(partidaId, fecha,
                        "Venta de licencia no completada; partida revertida."));
            } else if (borrador != null) {
                UUID borradorId = borrador.getId();
                compensar(ex, () -> partidas.anular(borradorId,
                        "Venta de licencia no completada; inventario restituido."));
            }
            if (entregaConfirmada) {
                compensar(ex, () -> cancelarEntrega(reserva.loteId(), reserva.licenciaId()));
            } else {
                compensar(ex, () -> liberarReserva(reserva.loteId(), reserva.licenciaId()));
            }
            throw ex;
        }
    }

    private void exigirClienteActivo(UUID clienteId) {
        if (clienteId == null) {
            throw new DomainException("El cliente es obligatorio para conceder una licencia");
        }
        jdbc.inTransaction(conn -> {
            var cliente = terceros.porId(conn, clienteId)
                    .orElseThrow(() -> new DomainException("Cliente no encontrado"));
            if (!cliente.isCliente()) {
                throw new DomainException("El tercero no está marcado como cliente");
            }
            if (!cliente.isActivo()) {
                throw new DomainException("El cliente está inactivo");
            }
            return cliente;
        });
    }

    private void compensar(RuntimeException original, Runnable accion) {
        try {
            accion.run();
        } catch (RuntimeException compensacion) {
            original.addSuppressed(compensacion);
        }
    }

    /**
     * Reserva una unidad aplicando PEPS por fecha de liberación, código de lote
     * y número de licencia. El bloqueo de fila evita que dos operaciones tomen
     * la misma unidad en ejecuciones concurrentes.
     */
    private ReservaLicencia reservarLicenciaPeps() {
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement disponible = conn.prepareStatement("""
                    SELECT li.id AS licencia_id, li.numero, lo.id AS lote_id, lo.costo_total,
                           lo.unidades_estimadas, lo.codigo_lote
                    FROM licencia li
                    JOIN lote_licencia lo ON lo.id = li.lote_id
                    WHERE li.estado IN ('DISPONIBLE','DEVUELTA') AND lo.unidades_disponibles > 0
                    ORDER BY lo.fecha_liberacion NULLS LAST, lo.codigo_lote, li.numero
                    FOR UPDATE OF li, lo
                    LIMIT 1
                    """)) {
                try (ResultSet rs = disponible.executeQuery()) {
                    if (!rs.next()) {
                        throw new DomainException("No hay licencias disponibles para aplicar PEPS");
                    }
                    UUID licenciaId = (UUID) rs.getObject("licencia_id");
                    UUID loteId = (UUID) rs.getObject("lote_id");
                    BigDecimal costo = costoContablePorUnidad(rs.getBigDecimal("costo_total"),
                            rs.getInt("unidades_estimadas"), rs.getInt("numero"));
                    String codigo = rs.getString("codigo_lote");
                    try (PreparedStatement licencia = conn.prepareStatement(
                            "UPDATE licencia SET estado='RESERVADA' WHERE id=? AND estado IN ('DISPONIBLE','DEVUELTA')")) {
                        licencia.setObject(1, licenciaId);
                        if (licencia.executeUpdate() != 1) {
                            throw new DomainException("No se pudo reservar la licencia PEPS seleccionada");
                        }
                    }
                    try (PreparedStatement lote = conn.prepareStatement("""
                            UPDATE lote_licencia SET unidades_disponibles = unidades_disponibles - 1
                            WHERE id=? AND unidades_disponibles > 0
                            """)) {
                        lote.setObject(1, loteId);
                        if (lote.executeUpdate() != 1) {
                            throw new DomainException("No se pudo actualizar la disponibilidad del lote PEPS");
                        }
                    }
                    return new ReservaLicencia(licenciaId, loteId, rs.getInt("numero"), costo, codigo);
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al reservar licencia por PEPS", e);
            }
        });
    }

    /**
     * Distribuye los centavos residuales de un lote entre sus primeras unidades.
     * Así la suma de todas las salidas coincide exactamente con el costo total
     * contabilizado al liberar el lote (por ejemplo, $10.00 / 3 = 3.34, 3.33,
     * 3.33).
     */
    static BigDecimal costoContablePorUnidad(BigDecimal costoTotal, int unidades, int numero) {
        if (costoTotal == null || unidades <= 0 || numero < 1 || numero > unidades) {
            throw new IllegalArgumentException("Lote o número de licencia inválido para distribuir el costo");
        }
        BigDecimal total = costoTotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal base = total.divide(BigDecimal.valueOf(unidades), 2, RoundingMode.DOWN);
        int unidadesConCentavoExtra = total.subtract(base.multiply(BigDecimal.valueOf(unidades)))
                .movePointRight(2).intValueExact();
        return numero <= unidadesConCentavoExtra
                ? base.add(new BigDecimal("0.01"))
                : base;
    }

    private void entregarReserva(ReservaLicencia reserva, UUID clienteId, LocalDate fecha) {
        jdbc.runInTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    UPDATE licencia SET estado='ENTREGADA', cliente_id=?, fecha_entrega=?, documento_id=NULL
                    WHERE id=? AND estado='RESERVADA'
                    """)) {
                ps.setObject(1, clienteId);
                ps.setObject(2, fecha);
                ps.setObject(3, reserva.licenciaId());
                if (ps.executeUpdate() != 1) {
                    throw new DomainException("No se pudo confirmar la entrega de la licencia reservada");
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al entregar licencia", e);
            }
            registrarKardex(conn, reserva.loteId(), reserva.licenciaId(), "ENTREGA", fecha,
                    -1, reserva.costoContable(), reserva.costoContable().negate(), null,
                    "Entrega PEPS · licencia #" + reserva.numero(),
                    auth.exigirContador().getId());
        });
    }

    private void liberarReserva(UUID loteId, UUID licenciaId) {
        jdbc.runInTransaction(conn -> {
            try (PreparedStatement licencia = conn.prepareStatement(
                    "UPDATE licencia SET estado='DISPONIBLE' WHERE id=? AND estado='RESERVADA'")) {
                licencia.setObject(1, licenciaId);
                if (licencia.executeUpdate() == 1) {
                    try (PreparedStatement lote = conn.prepareStatement(
                            "UPDATE lote_licencia SET unidades_disponibles = unidades_disponibles + 1 WHERE id=?")) {
                        lote.setObject(1, loteId);
                        lote.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                throw new PersistenceException("No se pudo liberar la reserva de licencia", e);
            }
        });
    }

    /**
     * Revierte una entrega interna si la contabilización posterior no se completa.
     */
    private void cancelarEntrega(UUID loteId, UUID licenciaId) {
        jdbc.runInTransaction(conn -> {
            try (PreparedStatement licencia = conn.prepareStatement("""
                    UPDATE licencia
                    SET estado='DISPONIBLE', cliente_id=NULL, fecha_entrega=NULL, documento_id=NULL
                    WHERE id=? AND estado='ENTREGADA'
                    """)) {
                licencia.setObject(1, licenciaId);
                if (licencia.executeUpdate() != 1) {
                    throw new DomainException("No se pudo restituir la licencia entregada");
                }
                try (PreparedStatement lote = conn.prepareStatement(
                        "UPDATE lote_licencia SET unidades_disponibles = unidades_disponibles + 1 WHERE id=?")) {
                    lote.setObject(1, loteId);
                    if (lote.executeUpdate() != 1) {
                        throw new DomainException("No se pudo restituir la disponibilidad del lote");
                    }
                }
                // Sólo se retira el movimiento de esta entrega: las entregas
                // ya contabilizadas conservan su partida en el kárdex.
                try (PreparedStatement kardex = conn.prepareStatement("""
                        DELETE FROM licencia_movimiento
                        WHERE licencia_id=? AND tipo='ENTREGA' AND partida_id IS NULL
                        """)) {
                    kardex.setObject(1, licenciaId);
                    kardex.executeUpdate();
                }
            } catch (SQLException e) {
                throw new PersistenceException("No se pudo cancelar la entrega de licencia", e);
            }
        });
    }

    /**
     * Asienta el movimiento del kárdex con el saldo resultante del lote.
     * Bloquea la fila del lote para serializar los saldos entre operaciones
     * concurrentes sobre el mismo inventario.
     */
    private void registrarKardex(Connection conn, UUID loteId, UUID licenciaId, String tipo,
            LocalDate fecha, int unidades, BigDecimal costoUnitario, BigDecimal importe,
            UUID partidaId, String detalle, UUID creadoPor) throws SQLException {
        try (PreparedStatement lock = conn.prepareStatement(
                "SELECT id FROM lote_licencia WHERE id=? FOR UPDATE")) {
            lock.setObject(1, loteId);
            if (!lock.executeQuery().next()) {
                throw new DomainException("El lote del movimiento de kárdex no existe.");
            }
        }
        SaldoKardex saldo;
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT saldo_unidades, saldo_valor FROM licencia_movimiento
                WHERE lote_id=? ORDER BY secuencia DESC LIMIT 1
                """)) {
            ps.setObject(1, loteId);
            try (ResultSet rs = ps.executeQuery()) {
                saldo = rs.next()
                        ? new SaldoKardex(rs.getInt("saldo_unidades"), rs.getBigDecimal("saldo_valor"))
                        : new SaldoKardex(0, BigDecimal.ZERO.setScale(2));
            }
        }
        int saldoUnidades = saldo.unidades() + unidades;
        BigDecimal saldoValor = saldo.valor().add(importe).setScale(2, RoundingMode.HALF_UP);
        if (saldoUnidades < 0) {
            throw new DomainException("El kárdex del lote quedaría con existencias negativas.");
        }
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO licencia_movimiento (id, lote_id, licencia_id, tipo, fecha, unidades,
                    costo_unitario, importe, saldo_unidades, saldo_valor, partida_id, detalle,
                    creado_por)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, loteId);
            if (licenciaId == null) {
                ps.setNull(3, java.sql.Types.OTHER);
            } else {
                ps.setObject(3, licenciaId);
            }
            ps.setString(4, tipo);
            ps.setObject(5, fecha);
            ps.setInt(6, unidades);
            ps.setBigDecimal(7, costoUnitario);
            ps.setBigDecimal(8, importe);
            ps.setInt(9, saldoUnidades);
            ps.setBigDecimal(10, saldoValor);
            if (partidaId == null) {
                ps.setNull(11, java.sql.Types.OTHER);
            } else {
                ps.setObject(11, partidaId);
            }
            ps.setString(12, detalle == null || detalle.isBlank() ? null : detalle.trim());
            if (creadoPor == null) {
                ps.setNull(13, java.sql.Types.OTHER);
            } else {
                ps.setObject(13, creadoPor);
            }
            ps.executeUpdate();
        }
    }

    /** Vincula la partida contabilizada con el movimiento de entrega del kárdex. */
    private void vincularPartidaKardex(UUID licenciaId, UUID partidaId) {
        jdbc.runInTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    UPDATE licencia_movimiento SET partida_id=?
                    WHERE licencia_id=? AND tipo='ENTREGA' AND partida_id IS NULL
                    """)) {
                ps.setObject(1, partidaId);
                ps.setObject(2, licenciaId);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new PersistenceException("Error al vincular la partida del kárdex", e);
            }
        });
    }

    /**
     * Lista las licencias entregadas disponibles para devolución o revocación.
     */
    public List<LicenciaEntregadaInfo> listarLicenciasEntregadas() {
        auth.exigirContador();
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT l.id, lo.codigo_lote, l.numero, COALESCE(t.razon_social, '') AS razon_social,
                           l.fecha_entrega, l.costo_unitario
                    FROM licencia l
                    JOIN lote_licencia lo ON lo.id=l.lote_id
                    LEFT JOIN tercero t ON t.id=l.cliente_id
                    WHERE l.estado='ENTREGADA'
                    ORDER BY lo.codigo_lote, l.numero
                    """);
                    ResultSet rs = ps.executeQuery()) {
                List<LicenciaEntregadaInfo> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new LicenciaEntregadaInfo((UUID) rs.getObject("id"),
                            rs.getString("codigo_lote"), rs.getInt("numero"),
                            rs.getString("razon_social"),
                            rs.getObject("fecha_entrega", LocalDate.class),
                            rs.getBigDecimal("costo_unitario")));
                }
                return list;
            } catch (SQLException e) {
                throw new PersistenceException("Error al listar licencias entregadas", e);
            }
        });
    }

    /**
     * Kárdex cronológico de un lote: liberaciones, entregas PEPS, devoluciones
     * y revocaciones con el saldo de unidades y valor después de cada hecho.
     */
    public List<KardexInfo> kardexDeLote(UUID loteId) {
        auth.exigirContador();
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT m.secuencia, m.fecha, m.tipo, l.numero AS licencia_numero,
                           m.unidades, m.costo_unitario, m.importe, m.saldo_unidades,
                           m.saldo_valor, m.detalle, p.folio
                    FROM licencia_movimiento m
                    LEFT JOIN licencia l ON l.id=m.licencia_id
                    LEFT JOIN partida p ON p.id=m.partida_id
                    WHERE m.lote_id=?
                    ORDER BY m.secuencia
                    """)) {
                ps.setObject(1, loteId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<KardexInfo> list = new ArrayList<>();
                    while (rs.next()) {
                        list.add(new KardexInfo(rs.getLong("secuencia"),
                                rs.getObject("fecha", LocalDate.class), rs.getString("tipo"),
                                (Integer) rs.getObject("licencia_numero"), rs.getInt("unidades"),
                                rs.getBigDecimal("costo_unitario"), rs.getBigDecimal("importe"),
                                rs.getInt("saldo_unidades"), rs.getBigDecimal("saldo_valor"),
                                rs.getString("detalle"), rs.getString("folio")));
                    }
                    return list;
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al consultar el kárdex del lote", e);
            }
        });
    }

    /**
     * Devuelve una licencia entregada al inventario disponible. Cuando la venta
     * quedó trazada en el kárdex con su partida (ventas 1:1 registradas en la
     * aplicación), se contabiliza además la reversión completa de ingreso, IVA,
     * costo de ventas e inventario. Las ventas históricas agregadas del Anexo L
     * no tienen partida por unidad: en ese caso sólo reingresa la unidad y la
     * corrección del ingreso corresponde a un ajuste manual.
     *
     * @return la partida de reversa contabilizada, o {@code null} si la venta
     *         original no tiene partida trazable
     */
    public Partida devolverLicencia(UUID licenciaId, LocalDate fecha, String motivo) {
        auth.exigirContador();
        if (fecha == null) {
            throw new DomainException("La fecha de devolución es obligatoria");
        }
        DatosDevolucion datos = jdbc.inTransaction(conn -> {
            LicenciaCicloVida licencia = cargarLicenciaCicloVida(conn, licenciaId);
            UUID partidaVenta = null;
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT partida_id FROM licencia_movimiento
                    WHERE licencia_id=? AND tipo='ENTREGA' AND partida_id IS NOT NULL
                    ORDER BY secuencia DESC LIMIT 1
                    """)) {
                ps.setObject(1, licenciaId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        partidaVenta = (UUID) rs.getObject(1);
                    }
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al leer el kárdex de la licencia", e);
            }
            return new DatosDevolucion(licencia, partidaVenta);
        });
        if (!"ENTREGADA".equals(datos.licencia().estado())) {
            throw new DomainException("Sólo una licencia entregada puede devolverse al inventario.");
        }
        List<PartidaDetalle> lineas = datos.partidaVentaId() == null ? null
                : jdbc.inTransaction(conn -> lineasDeReversa(conn, datos.partidaVentaId()));
        Partida reversa = null;
        Partida borrador = null;
        try {
            if (lineas != null && !lineas.isEmpty()) {
                borrador = partidas.guardarBorrador(TipoPartida.DI, fecha,
                        "Devolución licencia " + datos.licencia().codigoLote() + " #"
                                + datos.licencia().numero(),
                        "DEV-LIC-" + datos.licencia().codigoLote(),
                        "DEV-" + licenciaId + "-" + UUID.randomUUID(),
                        new ArrayList<>(lineas), null);
                reversa = partidas.contabilizar(borrador.getId());
            }
            UUID partidaReversaId = reversa == null ? null : reversa.getId();
            LicenciaCicloVida licencia = datos.licencia();
            jdbc.runInTransaction(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE licencia SET estado='DEVUELTA' WHERE id=? AND estado='ENTREGADA'")) {
                    ps.setObject(1, licenciaId);
                    if (ps.executeUpdate() != 1) {
                        throw new DomainException(
                                "La licencia ya no está entregada; no se devolvió al inventario.");
                    }
                } catch (SQLException e) {
                    throw new PersistenceException("Error al devolver la licencia", e);
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE lote_licencia SET unidades_disponibles = unidades_disponibles + 1 WHERE id=?")) {
                    ps.setObject(1, licencia.loteId());
                    ps.executeUpdate();
                } catch (SQLException e) {
                    throw new PersistenceException("Error al reingresar la licencia al lote", e);
                }
                registrarKardex(conn, licencia.loteId(), licenciaId, "DEVOLUCION", fecha, 1,
                        licencia.costoUnitario(), licencia.costoUnitario(), partidaReversaId,
                        motivoKardex(motivo, "Devolución de licencia"),
                        auth.exigirContador().getId());
                bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                        auth.exigirContador().getId(), "DEVOLVER_LICENCIA", "LICENCIA",
                        licenciaId.toString(), "ENTREGADA", "DEVUELTA",
                        motivoKardex(motivo, "Devolución de licencia")));
            });
            return reversa;
        } catch (RuntimeException ex) {
            if (reversa != null) {
                UUID id = reversa.getId();
                compensar(ex, () -> partidas.revertir(id, fecha,
                        "Devolución de licencia no completada; reversa descartada."));
            } else if (borrador != null) {
                UUID id = borrador.getId();
                compensar(ex, () -> partidas.anular(id,
                        "Devolución de licencia no completada."));
            }
            throw ex;
        }
    }

    /**
     * Revoca el derecho de una licencia entregada sin devolverla al inventario:
     * la unidad ya salió con su venta y el tratamiento del ingreso se define
     * contractualmente fuera del prototipo. El hecho queda asentado en el
     * kárdex con saldo sin movimiento de existencias.
     */
    public void revocarLicencia(UUID licenciaId, LocalDate fecha, String motivo) {
        auth.exigirContador();
        if (fecha == null) {
            throw new DomainException("La fecha de revocación es obligatoria");
        }
        jdbc.runInTransaction(conn -> {
            LicenciaCicloVida licencia;
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT l.id, l.lote_id, l.numero, lo.codigo_lote, l.costo_unitario, l.estado
                    FROM licencia l JOIN lote_licencia lo ON lo.id=l.lote_id
                    WHERE l.id=? FOR UPDATE OF l
                    """)) {
                ps.setObject(1, licenciaId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new DomainException("Licencia no encontrada.");
                    }
                    licencia = new LicenciaCicloVida((UUID) rs.getObject("id"),
                            (UUID) rs.getObject("lote_id"), rs.getInt("numero"),
                            rs.getString("codigo_lote"), rs.getBigDecimal("costo_unitario"),
                            rs.getString("estado"));
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al cargar la licencia", e);
            }
            if (!"ENTREGADA".equals(licencia.estado())) {
                throw new DomainException("Sólo una licencia entregada puede revocarse.");
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE licencia SET estado='REVOCADA' WHERE id=? AND estado='ENTREGADA'")) {
                ps.setObject(1, licenciaId);
                if (ps.executeUpdate() != 1) {
                    throw new DomainException("La licencia ya no está entregada; no se revocó.");
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al revocar la licencia", e);
            }
            registrarKardex(conn, licencia.loteId(), licenciaId, "REVOCACION", fecha, 0,
                    licencia.costoUnitario(), BigDecimal.ZERO, null,
                    motivoKardex(motivo, "Revocación de derecho de licencia"),
                    auth.exigirContador().getId());
            bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                    auth.exigirContador().getId(), "REVOCAR_LICENCIA", "LICENCIA",
                    licenciaId.toString(), "ENTREGADA", "REVOCADA",
                    motivoKardex(motivo, "Revocación de derecho de licencia")));
        });
    }

    private LicenciaCicloVida cargarLicenciaCicloVida(Connection conn, UUID licenciaId) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT l.id, l.lote_id, l.numero, lo.codigo_lote, l.costo_unitario, l.estado
                FROM licencia l JOIN lote_licencia lo ON lo.id=l.lote_id
                WHERE l.id=?
                """)) {
            ps.setObject(1, licenciaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new DomainException("Licencia no encontrada.");
                }
                return new LicenciaCicloVida((UUID) rs.getObject("id"),
                        (UUID) rs.getObject("lote_id"), rs.getInt("numero"),
                        rs.getString("codigo_lote"), rs.getBigDecimal("costo_unitario"),
                        rs.getString("estado"));
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al cargar la licencia", e);
        }
    }

    /**
     * Lee las líneas de la partida de venta invirtiendo Debe y Haber para
     * producir la reversa exacta del asiento original.
     */
    private List<PartidaDetalle> lineasDeReversa(Connection conn, UUID partidaId) {
        List<PartidaDetalle> lineas = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT codigo_cuenta, auxiliar, centro_costo, debe, haber
                FROM partida_detalle WHERE partida_id=? ORDER BY linea
                """)) {
            ps.setObject(1, partidaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lineas.add(PartidaDetalle.nueva(rs.getString("codigo_cuenta"),
                            rs.getString("auxiliar"), rs.getString("centro_costo"),
                            rs.getBigDecimal("haber"), rs.getBigDecimal("debe")));
                }
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al leer las líneas de la venta", e);
        }
        return lineas;
    }

    private String motivoKardex(String motivo, String defecto) {
        String texto = motivo == null || motivo.isBlank() ? defecto : motivo.trim();
        return texto.length() > 300 ? texto.substring(0, 300) : texto;
    }

    /**
     * Obtiene la orden de referencia usada por los casos demostrativos.
     *
     * @return información de la orden principal
     */
    public OrdenInfo ordenPrincipal() {
        auth.exigirContador();
        return jdbc.inTransaction(conn -> cargarOrden(conn, "OS2026001"));
    }

    /**
     * Crea una orden de servicio nueva en estado ABIERTA.
     *
     * @param codigo código único de la orden (ej. OS2027001)
     * @param descripcion descripción del trabajo a realizar
     * @param presupuesto costo estimado de la orden
     * @param precioVenta precio pactado con el cliente
     * @param clienteId identificador del cliente (opcional)
     * @return la orden creada
     */
    public OrdenInfo crearOrden(String codigo, String descripcion, BigDecimal presupuesto,
            BigDecimal precioVenta, UUID clienteId) {
        var contador = auth.exigirContador();
        String cod = exigirTexto(codigo, "El código de la orden");
        if (cod.length() > 40) {
            throw new DomainException("El código de la orden no puede exceder 40 caracteres.");
        }
        String desc = exigirTexto(descripcion, "La descripción de la orden");
        if (desc.length() > 500) {
            throw new DomainException("La descripción no puede exceder 500 caracteres.");
        }
        validarImporte(presupuesto, "presupuesto");
        validarImporte(precioVenta, "precio de venta");
        try {
            jdbc.runInTransaction(conn -> {
                if (clienteId != null) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT 1 FROM tercero WHERE id=?")) {
                        ps.setObject(1, clienteId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                throw new DomainException("Cliente no encontrado para la orden.");
                            }
                        }
                    } catch (SQLException e) {
                        throw new PersistenceException("Error al validar el cliente de la orden", e);
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement("""
                        INSERT INTO orden_servicio (id, codigo, cliente_id, descripcion,
                                presupuesto, precio_venta, estado)
                        VALUES (?,?,?,?,?,?,'ABIERTA')
                        """)) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setString(2, cod);
                    ps.setObject(3, clienteId);
                    ps.setString(4, desc);
                    ps.setBigDecimal(5, presupuesto.setScale(2, RoundingMode.HALF_UP));
                    ps.setBigDecimal(6, precioVenta.setScale(2, RoundingMode.HALF_UP));
                    ps.executeUpdate();
                } catch (SQLException e) {
                    if ("23505".equals(e.getSQLState())) {
                        throw new DomainException("Ya existe una orden con el código " + cod + ".");
                    }
                    throw new PersistenceException("Error al crear la orden " + cod, e);
                }
                bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                        contador.getId(), "ORDEN_SERVICIO", "ORDEN_SERVICIO", cod, null,
                        "ABIERTA — presupuesto " + presupuesto + " — venta " + precioVenta, null));
            });
        } catch (PersistenceException e) {
            throw e;
        }
        return jdbc.inTransaction(conn -> cargarOrden(conn, cod));
    }

    /**
     * Lista las órdenes con su estado, presupuesto, costo acumulado y margen.
     *
     * @return detalle de rentabilidad por orden
     */
    public List<OrdenRentabilidad> listarOrdenesRentabilidad() {
        auth.exigirContador();
        return jdbc.inTransaction(conn -> {
            List<OrdenRentabilidad> lista = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT o.codigo, o.descripcion, o.estado, o.presupuesto, o.precio_venta,
                           COALESCE((SELECT SUM(c.importe)
                                     FROM orden_consumo c
                                     JOIN partida p ON p.id=c.partida_id
                                     WHERE c.orden_id=o.id AND p.estado='CONTABILIZADA'),0) AS costo
                    FROM orden_servicio o ORDER BY o.codigo
                    """);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BigDecimal precio = rs.getBigDecimal("precio_venta");
                    BigDecimal costo = rs.getBigDecimal("costo");
                    lista.add(new OrdenRentabilidad(rs.getString("codigo"),
                            rs.getString("descripcion"), rs.getString("estado"),
                            rs.getBigDecimal("presupuesto"), precio, costo,
                            precio.subtract(costo)));
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al listar las órdenes", e);
            }
            return lista;
        });
    }

    /**
     * Cambia el estado de una orden respetando el ciclo
     * ABIERTA → EN_PROCESO → ENTREGADA → CERRADA (o CANCELADA desde ABIERTA/EN_PROCESO).
     *
     * @param codigo código de la orden
     * @param nuevoEstado estado destino
     */
    public void cambiarEstadoOrden(String codigo, String nuevoEstado) {
        var contador = auth.exigirContador();
        String cod = exigirTexto(codigo, "El código de la orden");
        String destino = exigirTexto(nuevoEstado, "El estado destino").toUpperCase(Locale.ROOT);
        jdbc.runInTransaction(conn -> {
            String actual;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT estado FROM orden_servicio WHERE codigo=? FOR UPDATE")) {
                ps.setString(1, cod);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new DomainException("Orden no encontrada: " + cod);
                    }
                    actual = rs.getString("estado");
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al leer la orden " + cod, e);
            }
            if (!transicionEstadoOrdenValida(actual, destino)) {
                throw new DomainException("Transición no permitida: " + actual + " → " + destino
                        + ". El ciclo es ABIERTA → EN_PROCESO → ENTREGADA → CERRADA; "
                        + "CANCELADA solo desde ABIERTA o EN_PROCESO.");
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE orden_servicio SET estado=? WHERE codigo=?")) {
                ps.setString(1, destino);
                ps.setString(2, cod);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new PersistenceException("Error al actualizar la orden " + cod, e);
            }
            bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                    contador.getId(), "ORDEN_SERVICIO", "ORDEN_SERVICIO", cod, actual,
                    destino, null));
        });
    }

    private static boolean transicionEstadoOrdenValida(String actual, String destino) {
        return switch (actual) {
            case "ABIERTA" -> "EN_PROCESO".equals(destino) || "CANCELADA".equals(destino);
            case "EN_PROCESO" -> "ENTREGADA".equals(destino) || "CANCELADA".equals(destino);
            case "ENTREGADA" -> "CERRADA".equals(destino);
            default -> false;
        };
    }

    /**
     * Datos editables de una orden de servicio para precargar el formulario.
     */
    public record OrdenDetalle(String codigo, String descripcion, String estado,
            BigDecimal presupuesto, BigDecimal precioVenta, UUID clienteId) {
    }

    /**
     * Lee los datos actuales de una orden para editarla.
     *
     * @param codigo código de la orden
     * @return datos editables de la orden
     */
    public OrdenDetalle ordenDetalle(String codigo) {
        auth.exigirContador();
        String cod = exigirTexto(codigo, "El código de la orden");
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT codigo, descripcion, estado, presupuesto, precio_venta, cliente_id
                    FROM orden_servicio WHERE codigo=?
                    """)) {
                ps.setString(1, cod);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new DomainException("Orden no encontrada: " + cod);
                    }
                    return new OrdenDetalle(rs.getString("codigo"), rs.getString("descripcion"),
                            rs.getString("estado"), rs.getBigDecimal("presupuesto"),
                            rs.getBigDecimal("precio_venta"), (UUID) rs.getObject("cliente_id"));
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al leer la orden " + cod, e);
            }
        });
    }

    /**
     * Actualiza la descripción, presupuesto, precio de venta y cliente de una
     * orden. Sólo aplica mientras la orden está ABIERTA o EN_PROCESO: una vez
     * entregada o cerrada sus condiciones comerciales ya no se modifican.
     * Cada cambio queda registrado en bitácora con los valores anteriores.
     *
     * @param codigo código de la orden
     * @param descripcion nueva descripción
     * @param presupuesto nuevo presupuesto
     * @param precioVenta nuevo precio de venta
     * @param clienteId nuevo cliente (puede ser {@code null})
     */
    public void actualizarOrden(String codigo, String descripcion, BigDecimal presupuesto,
            BigDecimal precioVenta, UUID clienteId) {
        var contador = auth.exigirContador();
        String cod = exigirTexto(codigo, "El código de la orden");
        String desc = exigirTexto(descripcion, "La descripción de la orden");
        if (desc.length() > 500) {
            throw new DomainException("La descripción no puede exceder 500 caracteres.");
        }
        validarImporte(presupuesto, "presupuesto");
        validarImporte(precioVenta, "precio de venta");
        jdbc.runInTransaction(conn -> {
            String estado;
            String anterior;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT estado, descripcion || '|' || presupuesto || '|' || precio_venta || '|'"
                            + " || COALESCE(cliente_id::text,'') FROM orden_servicio WHERE codigo=? FOR UPDATE")) {
                ps.setString(1, cod);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new DomainException("Orden no encontrada: " + cod);
                    }
                    estado = rs.getString("estado");
                    anterior = rs.getString(1);
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al leer la orden " + cod, e);
            }
            if (!"ABIERTA".equals(estado) && !"EN_PROCESO".equals(estado)) {
                throw new DomainException("Sólo se pueden editar órdenes ABIERTA o EN_PROCESO; "
                        + "la orden " + cod + " está " + estado + ".");
            }
            if (clienteId != null) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT 1 FROM tercero WHERE id=?")) {
                    ps.setObject(1, clienteId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new DomainException("Cliente no encontrado para la orden.");
                        }
                    }
                } catch (SQLException e) {
                    throw new PersistenceException("Error al validar el cliente de la orden", e);
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("""
                    UPDATE orden_servicio SET descripcion=?, presupuesto=?, precio_venta=?, cliente_id=?
                    WHERE codigo=?
                    """)) {
                ps.setString(1, desc);
                ps.setBigDecimal(2, presupuesto.setScale(2, RoundingMode.HALF_UP));
                ps.setBigDecimal(3, precioVenta.setScale(2, RoundingMode.HALF_UP));
                ps.setObject(4, clienteId);
                ps.setString(5, cod);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new PersistenceException("Error al actualizar la orden " + cod, e);
            }
            bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                    contador.getId(), "ORDEN_SERVICIO", "ORDEN_SERVICIO", cod, anterior,
                    desc + "|" + presupuesto + "|" + precioVenta + "|" + clienteId, null));
        });
    }

    /**
     * Compara el costo estimado de una orden con su costo real acumulado y
     * calcula varianza, cumplimiento, ahorro, sobrecosto y rentabilidad.
     *
     * @param codigoOrden código de la orden a evaluar
     * @return comparación gerencial entre el presupuesto y el costo real
     */
    public DesviacionOrdenInfo analizarDesviacionOrden(String codigoOrden) {
        auth.exigirContador();
        OrdenInfo orden = jdbc
                .inTransaction(conn -> cargarOrden(conn, exigirTexto(codigoOrden, "El código de la orden")));
        return compararCostos(orden.id(), orden.codigo(), orden.presupuesto(), orden.costoAcumulado(),
                orden.precioVenta());
    }

    /**
     * Compara dos importes y devuelve sus indicadores de desviación.
     *
     * @param id            identificador de referencia de la orden
     * @param codigo        código legible de la orden
     * @param costoEstimado costo presupuestado o estimado
     * @param costoReal     costo real acumulado en los registros
     * @param precioVenta   precio pactado o esperado para la orden
     * @return indicadores de variación y rentabilidad
     */
    public DesviacionOrdenInfo compararCostos(UUID id, String codigo, BigDecimal costoEstimado,
            BigDecimal costoReal, BigDecimal precioVenta) {
        validarImporte(costoEstimado, "costo estimado");
        validarImporte(costoReal, "costo real");
        validarImporte(precioVenta, "precio de venta");
        BigDecimal estimado = redondear(costoEstimado);
        BigDecimal real = redondear(costoReal);
        BigDecimal variacion = real.subtract(estimado).setScale(2, RoundingMode.HALF_UP);
        BigDecimal variacionPorcentual = estimado.signum() == 0
                ? BigDecimal.ZERO
                : variacion.divide(estimado, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal indiceEjecucion = estimado.signum() == 0
                ? BigDecimal.ZERO
                : real.divide(estimado, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal ahorro = estimado.subtract(real).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal sobrecosto = real.subtract(estimado).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal utilidadEsperada = redondear(precioVenta).subtract(estimado).setScale(2, RoundingMode.HALF_UP);
        BigDecimal utilidadReal = redondear(precioVenta).subtract(real).setScale(2, RoundingMode.HALF_UP);
        BigDecimal rentabilidad = precioVenta.signum() == 0
                ? BigDecimal.ZERO
                : utilidadReal.divide(precioVenta, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        String diagnostico = variacion.signum() > 0
                ? "Sobrecosto de $%s frente al presupuesto.".formatted(sobrecosto)
                : variacion.signum() < 0
                        ? "Ahorro presupuestario de $%s frente al estimado.".formatted(ahorro)
                        : "Ejecución exactamente al costo estimado.";
        return new DesviacionOrdenInfo(id, codigo, estimado, real, variacion, variacionPorcentual,
                indiceEjecucion, ahorro, sobrecosto, utilidadEsperada, utilidadReal, rentabilidad, diagnostico);
    }

    /**
     * Suma los costos reales registrados en orden_consumo cuyas partidas están
     * contabilizadas, agrupados por tipo de costo (MOD, SERVICIO, CIF).
     *
     * @return importes históricos por rubro de costo
     */
    public CostosHistoricosInfo costosHistoricos() {
        auth.exigirContador();
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT c.tipo, COALESCE(SUM(c.importe), 0) AS importe
                    FROM orden_consumo c
                    JOIN partida p ON p.id = c.partida_id
                    WHERE p.estado = 'CONTABILIZADA'
                    GROUP BY c.tipo
                    """)) {
                BigDecimal mod = BigDecimal.ZERO;
                BigDecimal servicios = BigDecimal.ZERO;
                BigDecimal cif = BigDecimal.ZERO;
                BigDecimal total = BigDecimal.ZERO;
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        BigDecimal importe = rs.getBigDecimal("importe");
                        total = total.add(importe);
                        switch (rs.getString("tipo")) {
                            case "MOD" -> mod = mod.add(importe);
                            case "SERVICIO" -> servicios = servicios.add(importe);
                            case "CIF" -> cif = cif.add(importe);
                            default -> { }
                        }
                    }
                }
                return new CostosHistoricosInfo(mod, servicios, cif, total);
            } catch (SQLException e) {
                throw new PersistenceException("Error al consultar los costos históricos", e);
            }
        });
    }

    /**
     * Puntos del diagrama «presupuesto vs. costo real» del tablero de inicio,
     * uno por objeto de la línea elegida: PROYECTO (órdenes de servicio),
     * LICENCIA (lotes) o ACCESO (planes SaaS).
     *
     * @param linea PROYECTO, LICENCIA o ACCESO
     * @return puntos presupuestado vs. real ordenados por código
     */
    public List<PuntoCostoPresupuesto> presupuestoVsCostoReal(String linea) {
        auth.exigirContador();
        String seleccion = linea == null ? "" : linea.trim().toUpperCase(Locale.ROOT);
        return switch (seleccion) {
            case "PROYECTO" -> jdbc.inTransaction(this::puntosPresupuestoProyecto);
            case "LICENCIA" -> jdbc.inTransaction(this::puntosPresupuestoLicencia);
            case "ACCESO" -> puntosPresupuestoAcceso();
            default -> throw new DomainException(
                    "Línea de costo no soportada: " + (linea == null ? "" : linea));
        };
    }

    /** Órdenes de servicio: presupuesto de la orden contra su costo real contabilizado. */
    private List<PuntoCostoPresupuesto> puntosPresupuestoProyecto(Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT o.codigo, o.descripcion, o.presupuesto,
                       COALESCE((SELECT SUM(c.importe)
                                 FROM orden_consumo c
                                 JOIN partida p ON p.id = c.partida_id
                                 WHERE c.orden_id = o.id AND p.estado='CONTABILIZADA'), 0) AS costo
                FROM orden_servicio o
                WHERE o.estado <> 'CANCELADA'
                ORDER BY o.codigo
                """);
             ResultSet rs = ps.executeQuery()) {
            List<PuntoCostoPresupuesto> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(new PuntoCostoPresupuesto(rs.getString("codigo"),
                        rs.getString("descripcion"), rs.getBigDecimal("presupuesto"),
                        rs.getBigDecimal("costo")));
            }
            return lista;
        } catch (SQLException e) {
            throw new PersistenceException("Error al comparar presupuesto y costo de órdenes", e);
        }
    }

    /**
     * Lotes de licencia: el costo presupuestado del lote (su costo total PEPS)
     * contra el costo real neto de las unidades entregadas según el kárdex.
     */
    private List<PuntoCostoPresupuesto> puntosPresupuestoLicencia(Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT lo.codigo_lote,
                       p.nombre || ' · v' || pv.version_label AS detalle,
                       lo.costo_total AS presupuesto,
                       COALESCE((SELECT -SUM(m.importe)
                                 FROM licencia_movimiento m
                                 WHERE m.lote_id = lo.id
                                   AND m.tipo IN ('ENTREGA','DEVOLUCION')), 0) AS costo
                FROM lote_licencia lo
                JOIN producto_version pv ON pv.id = lo.version_id
                JOIN producto p ON p.id = pv.producto_id
                ORDER BY lo.codigo_lote
                """);
             ResultSet rs = ps.executeQuery()) {
            List<PuntoCostoPresupuesto> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(new PuntoCostoPresupuesto(rs.getString("codigo_lote"),
                        rs.getString("detalle"), rs.getBigDecimal("presupuesto"),
                        rs.getBigDecimal("costo")));
            }
            return lista;
        } catch (SQLException e) {
            throw new PersistenceException("Error al comparar presupuesto y costo de lotes", e);
        }
    }

    /**
     * Planes SaaS: la facturación presupuestada (tarifa pactada por usuario-mes
     * multiplicada por los usuarios-mes acumulados) contra el costo real que el
     * plan absorbió del pool de procesos 510201–510203 en el mismo horizonte.
     */
    private List<PuntoCostoPresupuesto> puntosPresupuestoAcceso() {
        return costosSaasPorPlan().stream()
                .map(p -> new PuntoCostoPresupuesto(p.codigo(), p.nombre(),
                        p.tarifaBase().multiply(BigDecimal.valueOf(p.usuariosMes()))
                                .setScale(2, RoundingMode.HALF_UP),
                        p.totalAsignado()))
                .toList();
    }

    /**
     * Costo SaaS asignado a cada plan con usuarios-mes en todo el horizonte
     * registrado: tarifa pactada, usuarios-mes acumulados y su parte del pool
     * de procesos 510201–510203.
     */
    private List<PlanCostoSaas> costosSaasPorPlan() {
        LocalDate[] rango = jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT MIN(periodo) AS desde, MAX(periodo) AS hasta FROM saas_usuario_mes")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || rs.getObject("desde") == null) {
                        return new LocalDate[] {null, null};
                    }
                    return new LocalDate[] {rs.getObject("desde", LocalDate.class),
                            rs.getObject("hasta", LocalDate.class)};
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al leer el rango de usuarios-mes", e);
            }
        });
        if (rango[0] == null) {
            return List.of();
        }
        List<PlanCostoSaas> planes = jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT s.plan_id, p.codigo, p.nombre, p.tarifa_base,
                           SUM(s.usuarios_mes) AS usuarios_mes
                    FROM saas_usuario_mes s
                    JOIN plan_servicio p ON p.id = s.plan_id
                    WHERE p.modalidad='SAAS'
                    GROUP BY s.plan_id, p.codigo, p.nombre, p.tarifa_base
                    ORDER BY p.codigo
                    """);
                 ResultSet rs = ps.executeQuery()) {
                List<PlanCostoSaas> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(new PlanCostoSaas((UUID) rs.getObject("plan_id"),
                            rs.getString("codigo"), rs.getString("nombre"),
                            rs.getBigDecimal("tarifa_base"), rs.getInt("usuarios_mes"), null));
                }
                return lista;
            } catch (SQLException e) {
                throw new PersistenceException("Error al listar planes SaaS con usuarios-mes", e);
            }
        });
        if (planes.isEmpty()) {
            return List.of();
        }
        List<SaasParticipacion> participaciones = planes.stream()
                .map(p -> new SaasParticipacion(p.planId(), p.codigo(), p.usuariosMes()))
                .toList();
        int usuariosTotales = planes.stream().mapToInt(PlanCostoSaas::usuariosMes).sum();
        CostoSaasInfo proceso = calcularCostoSaas(rango[0], rango[1], usuariosTotales);
        List<PlanCostoSaas> resultado = new ArrayList<>();
        for (PlanCostoSaas plan : planes) {
            BigDecimal total = asignarPorUsuariosMes(proceso.alojamiento(), participaciones, plan.planId())
                    .add(asignarPorUsuariosMes(proceso.soporte(), participaciones, plan.planId()))
                    .add(asignarPorUsuariosMes(proceso.infraestructuraCompartida(),
                            participaciones, plan.planId()));
            resultado.add(new PlanCostoSaas(plan.planId(), plan.codigo(), plan.nombre(),
                    plan.tarifaBase(), plan.usuariosMes(),
                    total.setScale(2, RoundingMode.HALF_UP)));
        }
        return resultado;
    }

    /** Plan SaaS con tarifa pactada, usuarios-mes y costo asignado del pool. */
    private record PlanCostoSaas(UUID planId, String codigo, String nombre,
            BigDecimal tarifaBase, int usuariosMes, BigDecimal totalAsignado) {
    }

    /**
     * Estructura de costos del tablero: una fila por proyecto, lote de
     * licencias o plan SaaS con su precio unitario y el costo desagregado por
     * rubro (materia prima / servicios directos, mano de obra y CIF).
     *
     * @return filas ordenadas por línea comercial y código
     */
    public List<EstructuraCostoInfo> estructuraCostos() {
        auth.exigirContador();
        List<EstructuraCostoInfo> lista = new ArrayList<>();
        lista.addAll(jdbc.inTransaction(this::filasEstructuraProyecto));
        lista.addAll(jdbc.inTransaction(this::filasEstructuraLicencia));
        lista.addAll(filasEstructuraAcceso());
        return lista;
    }

    /** Órdenes de servicio: precio de venta contra MOD, servicios directos y CIF. */
    private List<EstructuraCostoInfo> filasEstructuraProyecto(Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT o.codigo, o.descripcion, o.precio_venta,
                       COALESCE(SUM(CASE WHEN c.tipo IN ('SERVICIO','OTRO')
                            AND p.estado='CONTABILIZADA' THEN c.importe END), 0) AS mp,
                       COALESCE(SUM(CASE WHEN c.tipo='MOD'
                            AND p.estado='CONTABILIZADA' THEN c.importe END), 0) AS mod,
                       COALESCE(SUM(CASE WHEN c.tipo='CIF'
                            AND p.estado='CONTABILIZADA' THEN c.importe END), 0) AS cif
                FROM orden_servicio o
                LEFT JOIN orden_consumo c ON c.orden_id = o.id
                LEFT JOIN partida p ON p.id = c.partida_id
                WHERE o.estado <> 'CANCELADA'
                GROUP BY o.id, o.codigo, o.descripcion, o.precio_venta
                ORDER BY o.codigo
                """);
             ResultSet rs = ps.executeQuery()) {
            List<EstructuraCostoInfo> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(new EstructuraCostoInfo("PROYECTO", rs.getString("codigo"),
                        rs.getString("descripcion"), rs.getBigDecimal("precio_venta"),
                        rs.getBigDecimal("mp"), rs.getBigDecimal("mod"), rs.getBigDecimal("cif")));
            }
            return lista;
        } catch (SQLException e) {
            throw new PersistenceException("Error al leer la estructura de costos de órdenes", e);
        }
    }

    /**
     * Lotes de licencia: precio unitario de venta promedio de las unidades
     * entregadas (partidas de venta trazadas en el kárdex) contra el costo
     * unitario del lote. El costo de un lote se reconoce íntegro al liberarse,
     * por lo que no hay desglose MOD/CIF: va completo en materia prima.
     */
    private List<EstructuraCostoInfo> filasEstructuraLicencia(Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT lo.codigo_lote,
                       p.nombre || ' · v' || pv.version_label AS detalle,
                       lo.costo_unitario,
                       v.precio_promedio
                FROM lote_licencia lo
                JOIN producto_version pv ON pv.id = lo.version_id
                JOIN producto p ON p.id = pv.producto_id
                LEFT JOIN (
                    SELECT lm.lote_id, AVG(px.precio) AS precio_promedio
                    FROM licencia_movimiento lm
                    JOIN partida pt ON pt.id = lm.partida_id AND pt.estado='CONTABILIZADA'
                    JOIN (
                        SELECT d.partida_id, SUM(d.haber - d.debe) AS precio
                        FROM partida_detalle d
                        WHERE d.codigo_cuenta LIKE '4%'
                        GROUP BY d.partida_id
                    ) px ON px.partida_id = lm.partida_id
                    WHERE lm.tipo='ENTREGA' AND lm.partida_id IS NOT NULL
                    GROUP BY lm.lote_id
                ) v ON v.lote_id = lo.id
                ORDER BY lo.codigo_lote
                """);
             ResultSet rs = ps.executeQuery()) {
            List<EstructuraCostoInfo> lista = new ArrayList<>();
            while (rs.next()) {
                BigDecimal costoUnitario = rs.getBigDecimal("costo_unitario");
                lista.add(new EstructuraCostoInfo("LICENCIA", rs.getString("codigo_lote"),
                        rs.getString("detalle"), rs.getBigDecimal("precio_promedio"),
                        costoUnitario, BigDecimal.ZERO, BigDecimal.ZERO));
            }
            return lista;
        } catch (SQLException e) {
            throw new PersistenceException("Error al leer la estructura de costos de licencias", e);
        }
    }

    /**
     * Planes SaaS: tarifa pactada por usuario-mes contra el costo asignado
     * por usuario-mes del pool de procesos. El costo SaaS es indirecto por
     * naturaleza (alojamiento, soporte e infraestructura), por eso va en CIF.
     */
    private List<EstructuraCostoInfo> filasEstructuraAcceso() {
        List<EstructuraCostoInfo> lista = new ArrayList<>();
        for (PlanCostoSaas plan : costosSaasPorPlan()) {
            BigDecimal unitario = plan.totalAsignado()
                    .divide(BigDecimal.valueOf(plan.usuariosMes()), 4, RoundingMode.HALF_UP);
            lista.add(new EstructuraCostoInfo("ACCESO", plan.codigo(),
                    plan.nombre() + " · " + plan.usuariosMes() + " usuario(s)-mes",
                    plan.tarifaBase(), BigDecimal.ZERO, BigDecimal.ZERO, unitario));
        }
        return lista;
    }

    /**
     * Registra el consumo acumulado de una orden de servicio.
     *
     * @param codigoOrden código de la orden
     * @param tipo        tipo de costo
     * @param descripcion descripción del consumo
     * @param cantidad    cantidad consumida
     * @param tarifa      tarifa aplicada
     * @param fecha       fecha contable
     * @return información actualizada de la orden
     */
    public OrdenInfo registrarConsumoOrden(String codigoOrden, String tipo, String descripcion,
            BigDecimal cantidad, BigDecimal tarifa, LocalDate fecha) {
        auth.exigirContador();
        String orden = exigirTexto(codigoOrden, "El código de la orden");
        String clase = normalizarTipoCosto(tipo);
        String detalle = exigirTexto(descripcion, "La descripción del consumo");
        validarConsumo(cantidad, tarifa, fecha);
        UUID ordenId = exigirOrdenId(orden);
        BigDecimal importe = cantidad.multiply(tarifa).setScale(2, RoundingMode.HALF_UP);
        String cuentaCosto = switch (clase) {
            case "MOD" -> "110501";
            case "SERVICIO" -> "110502";
            case "CIF" -> "110503";
            default -> throw new DomainException("Tipo de costo no admitido");
        };
        // Acumula en servicios en proceso contra banco/indirectos aplicados
        String haber = "CIF".equals(clase) ? "520201" : "110102";
        Partida contabilizada = null;
        try {
            Partida p = partidas.guardarBorrador(TipoPartida.DI, fecha,
                    "Costo " + orden + " " + detalle, orden,
                    "ORD-" + orden + "-" + UUID.randomUUID(),
                    List.of(
                            PartidaDetalle.nueva(cuentaCosto, importe, BigDecimal.ZERO),
                            PartidaDetalle.nueva(haber, BigDecimal.ZERO, importe)),
                    null);
            contabilizada = partidas.contabilizar(p.getId());
            Partida partidaFinal = contabilizada;
            return jdbc.inTransaction(conn -> {
                try {
                    try (PreparedStatement ps = conn.prepareStatement(
                            """
                                    INSERT INTO orden_consumo (id, orden_id, tipo, descripcion, cantidad, tarifa, importe, fecha, partida_id)
                                    VALUES (?,?,?,?,?,?,?,?,?)
                                    """)) {
                        ps.setObject(1, UUID.randomUUID());
                        ps.setObject(2, ordenId);
                        ps.setString(3, clase);
                        ps.setString(4, detalle);
                        ps.setBigDecimal(5, cantidad);
                        ps.setBigDecimal(6, tarifa);
                        ps.setBigDecimal(7, importe);
                        ps.setObject(8, fecha);
                        ps.setObject(9, partidaFinal.getId());
                        ps.executeUpdate();
                    }
                    bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                            auth.exigirContador().getId(), "CONSUMO_ORDEN", "ORDEN_SERVICIO",
                            orden, null,
                            clase + " · " + detalle + " · $" + importe + " · " + fecha, null));
                    return cargarOrden(conn, orden);
                } catch (SQLException e) {
                    throw new PersistenceException("Error al registrar consumo", e);
                }
            });
        } catch (RuntimeException ex) {
            if (contabilizada != null) {
                UUID partidaId = contabilizada.getId();
                compensar(ex, () -> partidas.revertir(partidaId, fecha,
                        "Consumo de orden no registrado en auxiliar; partida revertida."));
            }
            throw ex;
        }
    }

    /** Reconoce costo de orden a resultados (5101) desde 1105. */
    public Partida reconocerCostoOrden(String codigoOrden, LocalDate fecha) {
        auth.exigirContador();
        Partida reconocido = jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT id FROM partida
                    WHERE concepto = ? AND estado = 'CONTABILIZADA'
                    ORDER BY fecha_contable DESC LIMIT 1
                    """)) {
                ps.setString(1, "Reconocimiento costo " + codigoOrden);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? partidaRepository.buscarPorId(conn, (UUID) rs.getObject(1)).orElse(null)
                            : null;
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al comprobar costo reconocido", e);
            }
        });
        if (reconocido != null) {
            return reconocido;
        }
        OrdenInfo info = jdbc.inTransaction(conn -> cargarOrden(conn, codigoOrden));
        if (info.costoAcumulado().signum() <= 0) {
            throw new DomainException("La orden no tiene costo acumulado");
        }
        List<PartidaDetalle> lineas = new ArrayList<>();
        // Cada elemento conserva su cuenta de resultados y su cuenta en proceso.
        jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT c.tipo, COALESCE(SUM(c.importe), 0) AS importe
                    FROM orden_consumo c
                    JOIN partida p ON p.id=c.partida_id
                    WHERE c.orden_id=? AND p.estado='CONTABILIZADA'
                    GROUP BY c.tipo
                    """)) {
                ps.setObject(1, info.id());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        BigDecimal importe = rs.getBigDecimal("importe");
                        if (importe.signum() > 0) {
                            String tipo = rs.getString("tipo");
                            String cuentaProceso = switch (tipo) {
                                case "MOD" -> "110501";
                                case "CIF" -> "110503";
                                default -> "110502";
                            };
                            String cuentaResultado = switch (tipo) {
                                case "MOD" -> "510101";
                                case "CIF" -> "510103";
                                default -> "510102";
                            };
                            lineas.add(PartidaDetalle.nueva(cuentaResultado, importe, BigDecimal.ZERO));
                            lineas.add(PartidaDetalle.nueva(cuentaProceso, BigDecimal.ZERO, importe));
                        }
                    }
                }
                return null;
            } catch (SQLException e) {
                throw new PersistenceException("Error al desglosar costos de la orden", e);
            }
        });
        Partida p = partidas.guardarBorrador(TipoPartida.DI, fecha,
                "Reconocimiento costo " + codigoOrden, codigoOrden,
                "REC-" + codigoOrden + "-" + UUID.randomUUID(),
                lineas, null);
        Partida reconocida = partidas.contabilizar(p.getId());
        jdbc.runInTransaction(conn -> bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                auth.exigirContador().getId(), "RECONOCER_COSTO_ORDEN", "ORDEN_SERVICIO",
                codigoOrden, null, "Costo acumulado reconocido a resultados · " + fecha, null)));
        return reconocida;
    }

    private OrdenInfo cargarOrden(Connection conn, String codigo) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT o.id, o.codigo, o.descripcion, o.presupuesto, o.precio_venta,
                       COALESCE((SELECT SUM(c.importe)
                                 FROM orden_consumo c
                                 JOIN partida p ON p.id=c.partida_id
                                 WHERE c.orden_id=o.id AND p.estado='CONTABILIZADA'),0) AS costo
                FROM orden_servicio o WHERE o.codigo=?
                """)) {
            ps.setString(1, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new DomainException("Orden no encontrada");
                }
                BigDecimal presupuesto = rs.getBigDecimal("presupuesto");
                BigDecimal precio = rs.getBigDecimal("precio_venta");
                BigDecimal costo = rs.getBigDecimal("costo");
                BigDecimal margen = precio.subtract(costo);
                return new OrdenInfo((UUID) rs.getObject("id"), rs.getString("codigo"),
                        rs.getString("descripcion"), presupuesto, precio, costo, margen);
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al cargar orden", e);
        }
    }

    /**
     * Carga los consumos demostrativos de la orden OS2026001 si aún no existen.
     *
     * @param fecha fecha contable
     */
    public void cargarConsumosOs2026001(LocalDate fecha) {
        OrdenInfo orden = ordenPrincipal();
        if (orden.costoAcumulado().compareTo(new BigDecimal("1800.00")) == 0) {
            return;
        }
        if (orden.costoAcumulado().signum() > 0) {
            throw new DomainException("La orden ya tiene consumos registrados; no se duplicaron.");
        }
        // Caso: MOD $1,200 (100h × $12), servicios $300, CIF $300
        registrarConsumoOrden("OS2026001", "MOD", "Análisis/desarrollo/pruebas",
                new BigDecimal("100"), new BigDecimal("12"), fecha);
        registrarConsumoOrden("OS2026001", "SERVICIO", "Recursos exclusivos",
                BigDecimal.ONE, new BigDecimal("300"), fecha);
        registrarConsumoOrden("OS2026001", "CIF", "CIF $3 × 100 h",
                new BigDecimal("100"), new BigDecimal("3"), fecha);
    }

    /**
     * Registra un costo de implementación, capacitación o soporte contra el plan
     * facturado. Estos servicios se reconocen en el período, pero el auxiliar
     * conserva el objeto de costo (plan) y sus elementos MOD, directos y CIF.
     */
    /**
     * Registra un consumo imputado a un plan de servicio.
     *
     * @param codigoPlan  código del plan
     * @param tipo        tipo de costo
     * @param descripcion descripción del consumo
     * @param cantidad    cantidad consumida
     * @param tarifa      tarifa aplicada
     * @param fecha       fecha contable
     * @return costos acumulados del plan
     */
    public PlanCostoInfo registrarConsumoPlan(String codigoPlan, String tipo, String descripcion,
            BigDecimal cantidad, BigDecimal tarifa, LocalDate fecha) {
        auth.exigirContador();
        PlanRef plan = exigirPlanActivo(codigoPlan, "SERVICIO");
        String clase = normalizarTipoCosto(tipo);
        String detalle = exigirTexto(descripcion, "La descripción del consumo");
        validarConsumo(cantidad, tarifa, fecha);
        BigDecimal importe = cantidad.multiply(tarifa).setScale(2, RoundingMode.HALF_UP);
        String cuentaCosto = switch (clase) {
            case "MOD" -> "510101";
            case "SERVICIO" -> "510102";
            case "CIF" -> "510103";
            default -> throw new DomainException("Tipo de costo no admitido");
        };
        String cuentaOrigen = "CIF".equals(clase) ? "520201" : "110102";
        Partida contabilizada = null;
        try {
            Partida borrador = partidas.guardarBorrador(TipoPartida.DI, fecha,
                    "Costo plan " + plan.codigo() + " " + detalle, plan.codigo(),
                    "PLAN-" + plan.codigo() + "-" + UUID.randomUUID(),
                    List.of(
                            PartidaDetalle.nueva(cuentaCosto, plan.codigo(), "CC-03", importe, BigDecimal.ZERO),
                            PartidaDetalle.nueva(cuentaOrigen, plan.codigo(), "CC-03", BigDecimal.ZERO, importe)),
                    null);
            contabilizada = partidas.contabilizar(borrador.getId());
            Partida partidaFinal = contabilizada;
            return jdbc.inTransaction(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        """
                                INSERT INTO plan_consumo (id, plan_id, tipo, descripcion, cantidad, tarifa, importe, fecha, partida_id)
                                VALUES (?,?,?,?,?,?,?,?,?)
                                """)) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, plan.id());
                    ps.setString(3, clase);
                    ps.setString(4, detalle);
                    ps.setBigDecimal(5, cantidad);
                    ps.setBigDecimal(6, tarifa);
                    ps.setBigDecimal(7, importe);
                    ps.setObject(8, fecha);
                    ps.setObject(9, partidaFinal.getId());
                    ps.executeUpdate();
                    bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                            auth.exigirContador().getId(), "CONSUMO_PLAN", "PLAN_SERVICIO",
                            plan.codigo(), null,
                            clase + " · " + detalle + " · $" + importe + " · " + fecha, null));
                    return cargarCostoPlan(conn, plan, null, null);
                } catch (SQLException e) {
                    throw new PersistenceException("Error al registrar el consumo del plan", e);
                }
            });
        } catch (RuntimeException ex) {
            if (contabilizada != null) {
                UUID partidaId = contabilizada.getId();
                compensar(ex, () -> partidas.revertir(partidaId, fecha,
                        "Consumo de plan no registrado en auxiliar; partida revertida."));
            }
            throw ex;
        }
    }

    /**
     * Devuelve la acumulación de costos reales de un servicio por plan facturado.
     */
    public PlanCostoInfo costoPlanFacturado(String codigoPlan, LocalDate desde, LocalDate hasta) {
        auth.exigirContador();
        validarRango(desde, hasta);
        PlanRef plan = exigirPlanActivo(codigoPlan, "SERVICIO");
        return jdbc.inTransaction(conn -> cargarCostoPlan(conn, plan, desde, hasta));
    }

    /**
     * Costeo por procesos SaaS. Agrupa alojamiento, soporte e infraestructura
     * compartida y distribuye el total sobre usuarios-mes activos del período.
     */
    /**
     * Calcula el costo SaaS por proceso para un rango de fechas.
     *
     * @param desde    fecha inicial
     * @param hasta    fecha final
     * @param usuarios cantidad de usuarios del período
     * @return costo SaaS calculado
     */
    public CostoSaasInfo costoSaasPorProceso(LocalDate desde, LocalDate hasta, int usuarios) {
        auth.exigirContador();
        validarRango(desde, hasta);
        if (usuarios <= 0) {
            throw new DomainException("Indique usuarios activos del mes.");
        }
        return calcularCostoSaas(desde, hasta, usuarios);
    }

    /**
     * Persiste el denominador de costeo SaaS: usuarios con cobertura durante un
     * mes para un plan. Registrar nuevamente el plan y período reemplaza la
     * medición anterior; no duplica usuarios-mes.
     */
    /**
     * Registra la base de usuarios-mes de un plan SaaS.
     *
     * @param codigoPlan  código del plan
     * @param periodo     período mensual
     * @param usuariosMes usuarios-mes del período
     */
    public void registrarUsuariosMesSaas(String codigoPlan, LocalDate periodo, int usuariosMes) {
        auth.exigirContador();
        if (periodo == null) {
            throw new DomainException("El período SaaS es obligatorio.");
        }
        if (usuariosMes <= 0) {
            throw new DomainException("Los usuarios-mes deben ser mayores que cero.");
        }
        PlanRef plan = exigirPlanActivo(codigoPlan, "SAAS");
        LocalDate mes = periodo.withDayOfMonth(1);
        jdbc.runInTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO saas_usuario_mes (plan_id, periodo, usuarios_mes)
                    VALUES (?,?,?)
                    ON CONFLICT (plan_id, periodo) DO UPDATE
                    SET usuarios_mes=EXCLUDED.usuarios_mes, actualizado_en=CURRENT_TIMESTAMP
                    """)) {
                ps.setObject(1, plan.id());
                ps.setObject(2, mes);
                ps.setInt(3, usuariosMes);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new PersistenceException("Error al registrar usuarios-mes del plan SaaS", e);
            }
            bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                    auth.exigirContador().getId(), "USUARIOS_MES_SAAS", "PLAN_SAAS",
                    plan.codigo(), null, mes + " · " + usuariosMes + " usuarios-mes", null));
        });
    }

    /**
     * Prorratea el proceso SaaS entre planes por usuarios-mes persistidos. El
     * resultado es de costeo gerencial; los costos de alojamiento, soporte e
     * infraestructura ya están respaldados por las partidas del período.
     */
    /**
     * Distribuye el costo SaaS por proceso a un plan concreto.
     *
     * @param codigoPlan código del plan
     * @param desde      fecha inicial
     * @param hasta      fecha final
     * @return costo asignado al plan
     */
    public CostoSaasPlanInfo costoSaasPorProcesoPlan(String codigoPlan, LocalDate desde, LocalDate hasta) {
        auth.exigirContador();
        validarRango(desde, hasta);
        PlanRef plan = exigirPlanActivo(codigoPlan, "SAAS");
        List<SaasParticipacion> participaciones = jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT s.plan_id, p.codigo, SUM(s.usuarios_mes) AS usuarios_mes
                    FROM saas_usuario_mes s
                    JOIN plan_servicio p ON p.id=s.plan_id
                    WHERE p.modalidad='SAAS'
                      AND s.periodo BETWEEN ? AND ?
                    GROUP BY s.plan_id, p.codigo
                    ORDER BY p.codigo, s.plan_id
                    """)) {
                ps.setObject(1, desde.withDayOfMonth(1));
                ps.setObject(2, hasta.withDayOfMonth(1));
                try (ResultSet rs = ps.executeQuery()) {
                    List<SaasParticipacion> resultado = new ArrayList<>();
                    while (rs.next()) {
                        resultado.add(new SaasParticipacion((UUID) rs.getObject("plan_id"),
                                rs.getString("codigo"), rs.getInt("usuarios_mes")));
                    }
                    return resultado;
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al obtener usuarios-mes SaaS", e);
            }
        });
        SaasParticipacion participacionPlan = participaciones.stream()
                .filter(p -> p.planId().equals(plan.id())).findFirst().orElse(null);
        if (participacionPlan == null || participacionPlan.usuariosMes() <= 0) {
            throw new DomainException("No hay usuarios-mes registrados para el plan " + plan.codigo() + ".");
        }
        int usuariosTotales = participaciones.stream().mapToInt(SaasParticipacion::usuariosMes).sum();
        CostoSaasInfo proceso = calcularCostoSaas(desde, hasta, usuariosTotales);
        BigDecimal alojamiento = asignarPorUsuariosMes(proceso.alojamiento(), participaciones, plan.id());
        BigDecimal soporte = asignarPorUsuariosMes(proceso.soporte(), participaciones, plan.id());
        BigDecimal infraestructura = asignarPorUsuariosMes(proceso.infraestructuraCompartida(), participaciones,
                plan.id());
        BigDecimal total = alojamiento.add(soporte).add(infraestructura);
        return new CostoSaasPlanInfo(plan.codigo(), plan.nombre(), participacionPlan.usuariosMes(), usuariosTotales,
                alojamiento,
                soporte, infraestructura, total, proceso.costoUsuarioMes());
    }

    private CostoSaasInfo calcularCostoSaas(LocalDate desde, LocalDate hasta, int usuarios) {
        BigDecimal[] componentes = jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT
                      COALESCE(SUM(CASE WHEN d.codigo_cuenta='510201' THEN d.debe - d.haber ELSE 0 END), 0)
                        AS alojamiento,
                      COALESCE(SUM(CASE WHEN d.codigo_cuenta='510202' THEN d.debe - d.haber ELSE 0 END), 0)
                        AS soporte,
                      COALESCE(SUM(CASE WHEN d.codigo_cuenta='510203' THEN d.debe - d.haber ELSE 0 END), 0)
                        AS infraestructura
                    FROM partida_detalle d
                    JOIN partida p ON p.id = d.partida_id
                    WHERE p.estado IN ('CONTABILIZADA','REVERTIDA')
                      AND p.fecha_contable BETWEEN ? AND ?
                      AND d.codigo_cuenta IN ('510201','510202','510203')
                    """)) {
                ps.setObject(1, desde);
                ps.setObject(2, hasta);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return new BigDecimal[] {
                            rs.getBigDecimal("alojamiento").max(BigDecimal.ZERO),
                            rs.getBigDecimal("soporte").max(BigDecimal.ZERO),
                            rs.getBigDecimal("infraestructura").max(BigDecimal.ZERO)
                    };
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al calcular costo SaaS", e);
            }
        });
        BigDecimal total = componentes[0].add(componentes[1]).add(componentes[2]);
        return new CostoSaasInfo(componentes[0], componentes[1], componentes[2], usuarios,
                total, total.divide(BigDecimal.valueOf(usuarios), 2, RoundingMode.HALF_UP));
    }

    /**
     * Aplica el método de restos mayores en centavos. La asignación de cada
     * componente queda exactamente conciliada con su bolsa de costo, y un
     * empate se resuelve por código de plan para que sea reproducible.
     */
    static BigDecimal asignarPorUsuariosMes(BigDecimal importe, List<SaasParticipacion> participaciones,
            UUID planId) {
        int usuariosTotales = participaciones.stream().mapToInt(SaasParticipacion::usuariosMes).sum();
        if (usuariosTotales <= 0) {
            throw new DomainException("No hay usuarios-mes para distribuir el costo SaaS.");
        }
        BigInteger centavos = importe.setScale(2, RoundingMode.HALF_UP).movePointRight(2).toBigIntegerExact();
        BigInteger divisor = BigInteger.valueOf(usuariosTotales);
        List<ParticipacionCentavos> bases = new ArrayList<>();
        BigInteger sumaResiduos = BigInteger.ZERO;
        for (SaasParticipacion participacion : participaciones) {
            BigInteger producto = centavos.multiply(BigInteger.valueOf(participacion.usuariosMes()));
            BigInteger[] division = producto.divideAndRemainder(divisor);
            bases.add(new ParticipacionCentavos(participacion.planId(), participacion.codigo(), division[0],
                    division[1]));
            sumaResiduos = sumaResiduos.add(division[1]);
        }
        int centavosRestantes = sumaResiduos.divide(divisor).intValueExact();
        bases.sort((a, b) -> {
            int porResiduo = b.residuo().compareTo(a.residuo());
            return porResiduo != 0 ? porResiduo : a.codigo().compareTo(b.codigo());
        });
        for (int i = 0; i < bases.size(); i++) {
            ParticipacionCentavos actual = bases.get(i);
            if (actual.planId().equals(planId)) {
                BigInteger asignado = actual.base().add(i < centavosRestantes ? BigInteger.ONE : BigInteger.ZERO);
                return new BigDecimal(asignado, 2);
            }
        }
        throw new DomainException("El plan no participa en la distribución SaaS.");
    }

    private PlanCostoInfo cargarCostoPlan(Connection conn, PlanRef plan, LocalDate desde, LocalDate hasta) {
        String sql = """
                SELECT
                  COALESCE(SUM(CASE WHEN c.tipo='MOD' THEN c.importe ELSE 0 END), 0) AS mod,
                  COALESCE(SUM(CASE WHEN c.tipo='SERVICIO' THEN c.importe ELSE 0 END), 0) AS servicio,
                  COALESCE(SUM(CASE WHEN c.tipo='CIF' THEN c.importe ELSE 0 END), 0) AS cif
                FROM plan_consumo c
                JOIN partida p ON p.id=c.partida_id
                WHERE c.plan_id=? AND p.estado='CONTABILIZADA'
                """ + (desde == null ? "" : " AND c.fecha BETWEEN ? AND ?");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, plan.id());
            if (desde != null) {
                ps.setObject(2, desde);
                ps.setObject(3, hasta);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                BigDecimal mod = rs.getBigDecimal("mod");
                BigDecimal servicio = rs.getBigDecimal("servicio");
                BigDecimal cif = rs.getBigDecimal("cif");
                return new PlanCostoInfo(plan.codigo(), plan.nombre(), plan.tipoFacturacion(), mod, servicio, cif,
                        mod.add(servicio).add(cif));
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al consultar costos del plan", e);
        }
    }

    private PlanRef exigirPlanActivo(String codigoPlan, String modalidadEsperada) {
        String codigo = exigirTexto(codigoPlan, "El código del plan");
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT id, codigo, nombre, modalidad, tipo_facturacion, activo
                    FROM plan_servicio
                    WHERE codigo=?
                    """)) {
                ps.setString(1, codigo);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new DomainException("Plan no encontrado: " + codigo);
                    }
                    if (!rs.getBoolean("activo")) {
                        throw new DomainException("El plan está inactivo: " + codigo);
                    }
                    String modalidad = rs.getString("modalidad");
                    if (modalidadEsperada != null && !modalidadEsperada.equals(modalidad)) {
                        throw new DomainException("El plan " + codigo + " debe ser de modalidad "
                                + modalidadEsperada + ".");
                    }
                    return new PlanRef((UUID) rs.getObject("id"), rs.getString("codigo"),
                            rs.getString("nombre"), modalidad, rs.getString("tipo_facturacion"));
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al validar el plan", e);
            }
        });
    }

    private UUID exigirOrdenId(String codigoOrden) {
        return jdbc.inTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM orden_servicio WHERE codigo=?")) {
                ps.setString(1, codigoOrden);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new DomainException("Orden no encontrada: " + codigoOrden);
                    }
                    return (UUID) rs.getObject(1);
                }
            } catch (SQLException e) {
                throw new PersistenceException("Error al validar la orden", e);
            }
        });
    }

    private String normalizarTipoCosto(String tipo) {
        String normalizado = exigirTexto(tipo, "El tipo de costo").toUpperCase(Locale.ROOT);
        if (!"MOD".equals(normalizado) && !"SERVICIO".equals(normalizado) && !"CIF".equals(normalizado)) {
            throw new DomainException("Tipo de costo inválido. Use MOD, SERVICIO o CIF.");
        }
        return normalizado;
    }

    private String exigirTexto(String valor, String etiqueta) {
        if (valor == null || valor.isBlank()) {
            throw new DomainException(etiqueta + " es obligatorio.");
        }
        return valor.trim();
    }

    private void validarConsumo(BigDecimal cantidad, BigDecimal tarifa, LocalDate fecha) {
        if (cantidad == null || cantidad.signum() <= 0) {
            throw new DomainException("La cantidad debe ser mayor que cero.");
        }
        if (tarifa == null || tarifa.signum() < 0) {
            throw new DomainException("La tarifa no puede ser negativa.");
        }
        if (fecha == null) {
            throw new DomainException("La fecha del consumo es obligatoria.");
        }
    }

    private void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || hasta.isBefore(desde)) {
            throw new DomainException("El rango de fechas para el costeo es inválido.");
        }
    }

    private static void validarImporte(BigDecimal importe, String etiqueta) {
        if (importe == null || importe.signum() < 0) {
            throw new DomainException("El " + etiqueta + " no puede ser negativo.");
        }
    }

    private static BigDecimal redondear(BigDecimal importe) {
        return importe.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Compatibilidad para consumidores que sólo requieren el costo unitario SaaS.
     */
    /**
     * Obtiene el costo por usuario-mes del proceso SaaS.
     *
     * @param desde    fecha inicial
     * @param hasta    fecha final
     * @param usuarios cantidad de usuarios
     * @return costo por usuario-mes
     */
    public BigDecimal costoSaasUsuarioMes(LocalDate desde, LocalDate hasta, int usuarios) {
        return costoSaasPorProceso(desde, hasta, usuarios).costoUsuarioMes();
    }
}
