package sv.edu.ues.colibri.application.operacion;

import sv.edu.ues.colibri.application.auth.AuthService;
import sv.edu.ues.colibri.application.configuracion.ParametrizacionService;
import sv.edu.ues.colibri.application.partida.PartidaService;
import sv.edu.ues.colibri.domain.exception.DomainException;
import sv.edu.ues.colibri.domain.iva.IvaCalculator;
import sv.edu.ues.colibri.domain.model.BitacoraEntrada;
import sv.edu.ues.colibri.domain.model.Documento;
import sv.edu.ues.colibri.domain.model.Partida;
import sv.edu.ues.colibri.domain.model.PartidaDetalle;
import sv.edu.ues.colibri.domain.model.Tercero;
import sv.edu.ues.colibri.domain.model.TipoPartida;
import sv.edu.ues.colibri.persistence.jdbc.JdbcSupport;
import sv.edu.ues.colibri.persistence.repo.BitacoraRepository;
import sv.edu.ues.colibri.persistence.repo.DocumentoRepository;
import sv.edu.ues.colibri.persistence.repo.EmpresaRepository;
import sv.edu.ues.colibri.persistence.repo.TerceroRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Servicio de operaciones contables para ventas, compras, cobros, pagos y anticipos.
 */
public class OperacionContableService {

    private final JdbcSupport jdbc;
    private final AuthService auth;
    private final PartidaService partidas;
    private final ParametrizacionService parametrizacion;
    private final TerceroRepository terceros = new TerceroRepository();
    private final DocumentoRepository documentos = new DocumentoRepository();
    private final EmpresaRepository empresas = new EmpresaRepository();
    private final BitacoraRepository bitacora = new BitacoraRepository();

    /**
     * Crea el servicio de operaciones contables.
     *
     * @param jdbc soporte transaccional
     * @param auth servicio de autenticación
     * @param partidas servicio de partidas
     * @param parametrizacion servicio de parametrización
     */
    public OperacionContableService(JdbcSupport jdbc, AuthService auth, PartidaService partidas,
                                    ParametrizacionService parametrizacion) {
        this.jdbc = jdbc;
        this.auth = auth;
        this.partidas = partidas;
        this.parametrizacion = parametrizacion;
    }

    /**
     * Lista los terceros registrados.
     *
     * @return lista de terceros
     */
    public List<Tercero> listarTerceros() {
        auth.exigirContador();
        return jdbc.inTransaction(terceros::listar);
    }

    /**
     * Inserta o actualiza un tercero y registra la acción en bitácora.
     *
     * @param t tercero a persistir
     * @return tercero guardado
     */
    public Tercero guardarTercero(Tercero t) {
        var contador = auth.exigirContador();
        jdbc.runInTransaction(conn -> {
            if (terceros.porId(conn, t.getId()).isPresent()) {
                terceros.actualizar(conn, t);
            } else {
                terceros.insertar(conn, t);
            }
            bitacora.registrar(conn, BitacoraEntrada.de(contador.getId(), "TERCERO", "TERCERO",
                    t.getId().toString(), null, t.getNit(), t.getRazonSocial()));
        });
        return t;
    }

    /**
     * Lista los documentos tributarios registrados.
     *
     * @return lista de documentos
     */
    public List<Documento> listarDocumentos() {
        auth.exigirContador();
        return jdbc.inTransaction(documentos::listar);
    }

    /**
     * Registra una venta y genera el documento y la partida correspondiente.
     *
     * @param clienteId identificador del cliente
     * @param fecha fecha de la operación
     * @param baseOTotal importe base o total según {@code ivaIncluido}
     * @param ivaIncluido {@code true} si el importe incluye IVA
     * @param cuentaIngreso cuenta de ingreso opcional
     * @param cuentaCliente cuenta por cobrar opcional
     * @param concepto concepto contable
     * @return partida contabilizada
     */
    public Partida registrarVenta(UUID clienteId, LocalDate fecha, BigDecimal baseOTotal,
                                  boolean ivaIncluido, String cuentaIngreso, String cuentaCliente,
                                  String concepto) {
        auth.exigirContador();
        var fiscal = parametrizacion.exigirParametrosFiscales();
        var perfil = parametrizacion.exigirPerfilPosting("VENTA");
        String cuentaPorCobrar = parametrizacion.resolverCuenta(cuentaCliente, perfil.cuentaDebe(),
                "cuenta por cobrar de la venta");
        String cuentaDeIngreso = parametrizacion.resolverCuenta(cuentaIngreso, perfil.cuentaHaber(),
                "cuenta de ingreso de la venta");
        PlantillaDraft draft = jdbc.inTransaction(conn -> {
            Tercero cliente = terceros.porId(conn, clienteId)
                    .orElseThrow(() -> new DomainException("Cliente no encontrado"));
            if (!cliente.isCliente()) {
                throw new DomainException("El tercero no está marcado como cliente");
            }
            var empresa = empresas.obtenerUnica(conn).orElseThrow();
            var ivaR = calc(baseOTotal, ivaIncluido, fiscal.iva());
            Documento doc = nuevoDoc(conn, "CCF",
                    nitEmpresa(empresa), "DTE-" + System.currentTimeMillis(),
                    "SIM-VTA-" + shortId(), cliente.getNit(), cliente.getId(), fecha, ivaR, ivaIncluido);
            return new PlantillaDraft(doc, "DOC-VENTA-" + doc.getId(),
                    List.of(
                            PartidaDetalle.nueva(cuentaPorCobrar, ivaR.total(), BigDecimal.ZERO),
                            PartidaDetalle.nueva(cuentaDeIngreso, BigDecimal.ZERO, ivaR.base()),
                            PartidaDetalle.nueva(fiscal.cuentaDebito(), BigDecimal.ZERO, ivaR.iva())
                    ),
                    concepto != null ? concepto : "Venta " + cliente.getRazonSocial(),
                    TipoPartida.DI, fecha, "VENTA");
        });
        return contabilizarPlantilla(draft);
    }

    /**
     * Registra una compra y genera el documento y la partida correspondiente.
     *
     * @param proveedorId identificador del proveedor
     * @param fecha fecha de la operación
     * @param baseOTotal importe base o total según {@code ivaIncluido}
     * @param ivaIncluido {@code true} si el importe incluye IVA
     * @param cuentaGastoOActivo cuenta de gasto o activo opcional
     * @param concepto concepto contable
     * @return partida contabilizada
     */
    public Partida registrarCompra(UUID proveedorId, LocalDate fecha, BigDecimal baseOTotal,
                                   boolean ivaIncluido, String cuentaGastoOActivo, String concepto) {
        auth.exigirContador();
        var fiscal = parametrizacion.exigirParametrosFiscales();
        var perfil = parametrizacion.exigirPerfilPosting("COMPRA");
        String cuentaGasto = parametrizacion.resolverCuenta(cuentaGastoOActivo, perfil.cuentaDebe(),
                "cuenta de gasto o activo de la compra");
        String cuentaPorPagar = parametrizacion.resolverCuenta(null, perfil.cuentaHaber(),
                "cuenta por pagar de la compra");
        PlantillaDraft draft = jdbc.inTransaction(conn -> {
            Tercero prov = terceros.porId(conn, proveedorId)
                    .orElseThrow(() -> new DomainException("Proveedor no encontrado"));
            if (!prov.isProveedor()) {
                throw new DomainException("El tercero no está marcado como proveedor");
            }
            var empresa = empresas.obtenerUnica(conn).orElseThrow();
            var ivaR = calc(baseOTotal, ivaIncluido, fiscal.iva());
            Documento doc = nuevoDoc(conn, "CCF",
                    prov.getNit(), "DTE-P-" + System.currentTimeMillis(),
                    "SIM-CMP-" + shortId(), nitEmpresa(empresa), prov.getId(), fecha, ivaR, ivaIncluido);
            return new PlantillaDraft(doc, "DOC-COMPRA-" + doc.getId(),
                    List.of(
                            PartidaDetalle.nueva(cuentaGasto, ivaR.base(), BigDecimal.ZERO),
                            PartidaDetalle.nueva(fiscal.cuentaCredito(), ivaR.iva(), BigDecimal.ZERO),
                            PartidaDetalle.nueva(cuentaPorPagar, BigDecimal.ZERO, ivaR.total())
                    ),
                    concepto != null ? concepto : "Compra " + prov.getRazonSocial(),
                    TipoPartida.DI, fecha, "COMPRA");
        });
        return contabilizarPlantilla(draft);
    }

    /**
     * Registra un cobro y aplica retención de IVA cuando corresponde.
     *
     * @param clienteId identificador del cliente
     * @param fecha fecha de la operación
     * @param importe importe cobrado
     * @param cuentaCliente cuenta por cobrar opcional
     * @param retencionIva importe retenido de IVA
     * @param concepto concepto contable
     * @return partida contabilizada
     */
    public Partida registrarCobro(UUID clienteId, LocalDate fecha, BigDecimal importe,
                                  String cuentaCliente, BigDecimal retencionIva, String concepto) {
        auth.exigirContador();
        var fiscal = parametrizacion.exigirParametrosFiscales();
        var perfil = parametrizacion.exigirPerfilPosting("COBRO");
        String cuentaBanco = parametrizacion.resolverCuenta(null, perfil.cuentaDebe(),
                "cuenta de banco del cobro");
        String cuentaPorCobrar = parametrizacion.resolverCuenta(cuentaCliente, perfil.cuentaHaber(),
                "cuenta por cobrar del cobro");
        // La retención sólo procede cuando existe un comprobante aplicable.
        // Un importe nulo o cero significa que el cobro no tiene retención;
        // el llamador que la aplique debe pasar expresamente el importe
        // calculado con la tasa vigente o el importe del comprobante.
        BigDecimal ret = retencionIva == null ? BigDecimal.ZERO : retencionIva;
        if (ret.signum() < 0 || ret.compareTo(importe) > 0) {
            throw new DomainException("La retención de IVA debe estar entre cero y el importe del cobro.");
        }
        BigDecimal banco = importe.subtract(ret);
        PlantillaDraft draft = jdbc.inTransaction(conn -> {
            Tercero cli = terceros.porId(conn, clienteId)
                    .orElseThrow(() -> new DomainException("Cliente no encontrado"));
            var empresa = empresas.obtenerUnica(conn).orElseThrow();
            var ivaR = new IvaCalculator.Resultado(importe, BigDecimal.ZERO, importe);
            Documento doc = nuevoDoc(conn, "RECIBO",
                    nitEmpresa(empresa), "REC-" + System.currentTimeMillis(),
                    "SIM-COB-" + shortId(), cli.getNit(), cli.getId(), fecha, ivaR, false);
            List<PartidaDetalle> lineas = new ArrayList<>();
            lineas.add(PartidaDetalle.nueva(cuentaBanco, banco, BigDecimal.ZERO));
            if (ret.signum() > 0) {
                lineas.add(PartidaDetalle.nueva(fiscal.cuentaRetencionIva(), ret, BigDecimal.ZERO));
            }
            lineas.add(PartidaDetalle.nueva(cuentaPorCobrar, BigDecimal.ZERO, importe));
            return new PlantillaDraft(doc, "DOC-COBRO-" + doc.getId(), lineas,
                    concepto != null ? concepto : "Cobro " + cli.getRazonSocial(),
                    TipoPartida.IN, fecha, "COBRO");
        });
        return contabilizarPlantilla(draft);
    }

    /**
     * Registra un pago a proveedor.
     *
     * @param proveedorId identificador del proveedor
     * @param fecha fecha de la operación
     * @param importe importe pagado
     * @param cuentaProveedor cuenta por pagar opcional
     * @param concepto concepto contable
     * @return partida contabilizada
     */
    public Partida registrarPago(UUID proveedorId, LocalDate fecha, BigDecimal importe,
                                 String cuentaProveedor, String concepto) {
        auth.exigirContador();
        var perfil = parametrizacion.exigirPerfilPosting("PAGO");
        String cuentaPorPagar = parametrizacion.resolverCuenta(cuentaProveedor, perfil.cuentaDebe(),
                "cuenta por pagar del pago");
        String cuentaBanco = parametrizacion.resolverCuenta(null, perfil.cuentaHaber(),
                "cuenta de banco del pago");
        PlantillaDraft draft = jdbc.inTransaction(conn -> {
            Tercero prov = terceros.porId(conn, proveedorId)
                    .orElseThrow(() -> new DomainException("Proveedor no encontrado"));
            var ivaR = new IvaCalculator.Resultado(importe, BigDecimal.ZERO, importe);
            Documento doc = nuevoDoc(conn, "COMPROBANTE",
                    "BANCO", "PAG-" + System.currentTimeMillis(),
                    "SIM-PAG-" + shortId(), prov.getNit(), prov.getId(), fecha, ivaR, false);
            return new PlantillaDraft(doc, "DOC-PAGO-" + doc.getId(),
                    List.of(
                            PartidaDetalle.nueva(cuentaPorPagar, importe, BigDecimal.ZERO),
                            PartidaDetalle.nueva(cuentaBanco, BigDecimal.ZERO, importe)
                    ),
                    concepto != null ? concepto : "Pago " + prov.getRazonSocial(),
                    TipoPartida.EG, fecha, "PAGO");
        });
        return contabilizarPlantilla(draft);
    }

    /**
     * Registra un anticipo de SaaS y su documento asociado.
     *
     * @param clienteId identificador del cliente
     * @param fecha fecha de la operación
     * @param base base imponible del anticipo
     * @param concepto concepto contable
     * @return partida contabilizada
     */
    public Partida registrarAnticipoSaas(UUID clienteId, LocalDate fecha, BigDecimal base, String concepto) {
        auth.exigirContador();
        var fiscal = parametrizacion.exigirParametrosFiscales();
        var perfil = parametrizacion.exigirPerfilPosting("ANTICIPO");
        String cuentaBanco = parametrizacion.resolverCuenta(null, perfil.cuentaDebe(),
                "cuenta de banco del anticipo");
        String cuentaAnticipo = parametrizacion.resolverCuenta(null, perfil.cuentaHaber(),
                "cuenta de anticipo del cliente");
        var ivaR = IvaCalculator.desdeNeto(base, fiscal.iva());
        PlantillaDraft draft = jdbc.inTransaction(conn -> {
            Tercero cli = terceros.porId(conn, clienteId).orElseThrow();
            var empresa = empresas.obtenerUnica(conn).orElseThrow();
            Documento doc = nuevoDoc(conn, "CCF",
                    nitEmpresa(empresa), "ANT-" + System.currentTimeMillis(),
                    "SIM-ANT-" + shortId(), cli.getNit(), cli.getId(), fecha, ivaR, false);
            return new PlantillaDraft(doc, "DOC-ANTICIPO-" + doc.getId(),
                    List.of(
                            PartidaDetalle.nueva(cuentaBanco, ivaR.total(), BigDecimal.ZERO),
                            PartidaDetalle.nueva(cuentaAnticipo, BigDecimal.ZERO, ivaR.base()),
                            PartidaDetalle.nueva(fiscal.cuentaDebito(), BigDecimal.ZERO, ivaR.iva())
                    ),
                    concepto != null ? concepto : "Anticipo SaaS " + cli.getRazonSocial(),
                    TipoPartida.IN, fecha, "ANTICIPO");
        });
        return contabilizarPlantilla(draft);
    }

    private Partida contabilizarPlantilla(PlantillaDraft draft) {
        Partida borrador = partidas.guardarBorrador(draft.tipo(), draft.fecha(), draft.concepto(),
                draft.doc().getCodigoGeneracion(), draft.claveEvento(),
                new ArrayList<>(draft.lineas()), null);
        Partida cont = partidas.contabilizar(borrador.getId());
        jdbc.runInTransaction(conn -> {
            documentos.vincularEvento(conn, draft.doc().getId(), cont.getEventoId(),
                    draft.tipoEvento(), cont.getId());
            documentos.marcarContabilizado(conn, draft.doc().getId());
        });
        return cont;
    }

    private Documento nuevoDoc(java.sql.Connection conn, String tipo, String emisor, String numCtrl,
                               String codigoGen, String receptor, UUID terceroId, LocalDate fecha,
                               IvaCalculator.Resultado ivaR, boolean incluido) {
        Documento doc = new Documento(UUID.randomUUID(), tipo, emisor, numCtrl, codigoGen, receptor,
                terceroId, null, fecha, ivaR.base(), ivaR.iva(), BigDecimal.ZERO, ivaR.total(),
                incluido, "REGISTRADO", true);
        documentos.insertar(conn, doc);
        return doc;
    }

    private static IvaCalculator.Resultado calc(BigDecimal baseOTotal, boolean incluido, BigDecimal tasaIva) {
        return incluido
                ? IvaCalculator.desdeIncluido(baseOTotal, tasaIva)
                : IvaCalculator.desdeNeto(baseOTotal, tasaIva);
    }

    private static String nitEmpresa(sv.edu.ues.colibri.domain.model.Empresa e) {
        return e.getNit() != null ? e.getNit() : "SIMULADO-NIT";
    }

    /**
     * Importa un borrador de partida desde contenido CSV.
     *
     * @param contenido texto CSV con cuentas y montos
     * @param fecha fecha contable
     * @return partida en estado borrador
     */
    public Partida importarBorradorCsv(String contenido, LocalDate fecha) {
        auth.exigirContador();
        if (contenido == null || contenido.isBlank()) {
            throw new DomainException("El archivo CSV está vacío.");
        }
        List<PartidaDetalle> lineas = new ArrayList<>();
        for (String linea : contenido.split("\\R")) {
            if (linea.isBlank() || linea.toLowerCase().startsWith("cuenta")) {
                continue;
            }
            String[] cols = linea.split(",", -1);
            if (cols.length < 3) {
                throw new DomainException("Cada línea debe tener cuenta,debe,haber[,concepto]: " + linea);
            }
            lineas.add(PartidaDetalle.nueva(
                    cols[0].trim(),
                    new BigDecimal(cols[1].trim().isEmpty() ? "0" : cols[1].trim()),
                    new BigDecimal(cols[2].trim().isEmpty() ? "0" : cols[2].trim())));
        }
        if (lineas.size() < 2) {
            throw new DomainException("El archivo debe incluir al menos dos líneas de asiento.");
        }
        return partidas.guardarBorrador(TipoPartida.DI, fecha, "Importación CSV de asientos",
                "CSV", "IMP-" + UUID.randomUUID(), lineas, null);
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private record PlantillaDraft(Documento doc, String claveEvento, List<PartidaDetalle> lineas,
                                  String concepto, TipoPartida tipo, LocalDate fecha, String tipoEvento) {
    }
}
