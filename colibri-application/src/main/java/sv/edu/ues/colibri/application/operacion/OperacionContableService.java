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
import sv.edu.ues.colibri.persistence.jdbc.PersistenceException;
import sv.edu.ues.colibri.persistence.repo.BitacoraRepository;
import sv.edu.ues.colibri.persistence.repo.DocumentoRepository;
import sv.edu.ues.colibri.persistence.repo.EmpresaRepository;
import sv.edu.ues.colibri.persistence.repo.TerceroRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Servicio de operaciones contables para ventas, compras, cobros, pagos y anticipos.
 */
public class OperacionContableService {

    /**
     * Vista previa de las líneas que generará una operación, sin persistir
     * documento ni partida. Permite al contador revisar el asiento antes de
     * contabilizarlo.
     *
     * @param concepto concepto contable del asiento
     * @param tipo tipo de partida resultante
     * @param fecha fecha contable
     * @param lineas líneas Debe/Haber que se generarían
     */
    public record OperacionPreview(String concepto, TipoPartida tipo, LocalDate fecha,
                                   List<PartidaDetalle> lineas) {
        public OperacionPreview {
            lineas = List.copyOf(lineas);
        }
    }

    /**
     * Previsualiza el asiento de una venta sin contabilizarlo.
     */
    public OperacionPreview previsualizarVenta(LocalDate fecha, BigDecimal baseOTotal,
                                               boolean ivaIncluido, String cuentaIngreso) {
        var fiscal = parametrizacion.exigirParametrosFiscales();
        var perfil = parametrizacion.exigirPerfilPosting("VENTA");
        String ingreso = parametrizacion.resolverCuenta(cuentaIngreso, perfil.cuentaHaber(),
                "cuenta de ingreso de la venta");
        var iva = calc(baseOTotal, ivaIncluido, fiscal.iva());
        return new OperacionPreview("Venta", TipoPartida.DI, fecha, List.of(
                PartidaDetalle.nueva(perfil.cuentaDebe(), iva.total(), BigDecimal.ZERO),
                PartidaDetalle.nueva(ingreso, BigDecimal.ZERO, iva.base()),
                PartidaDetalle.nueva(fiscal.cuentaDebito(), BigDecimal.ZERO, iva.iva())));
    }

    /**
     * Previsualiza el asiento de una compra sin contabilizarlo.
     */
    public OperacionPreview previsualizarCompra(LocalDate fecha, BigDecimal baseOTotal,
                                                boolean ivaIncluido, String cuentaGasto) {
        var fiscal = parametrizacion.exigirParametrosFiscales();
        var perfil = parametrizacion.exigirPerfilPosting("COMPRA");
        String gasto = parametrizacion.resolverCuenta(cuentaGasto, perfil.cuentaDebe(),
                "cuenta de gasto o activo de la compra");
        var iva = calc(baseOTotal, ivaIncluido, fiscal.iva());
        return new OperacionPreview("Compra", TipoPartida.DI, fecha, List.of(
                PartidaDetalle.nueva(gasto, iva.base(), BigDecimal.ZERO),
                PartidaDetalle.nueva(fiscal.cuentaCredito(), iva.iva(), BigDecimal.ZERO),
                PartidaDetalle.nueva(perfil.cuentaHaber(), BigDecimal.ZERO, iva.total())));
    }

    /**
     * Previsualiza el asiento de un cobro con retención de IVA sin contabilizarlo.
     */
    public OperacionPreview previsualizarCobro(LocalDate fecha, BigDecimal importe,
                                               BigDecimal retencionIva) {
        var fiscal = parametrizacion.exigirParametrosFiscales();
        var perfil = parametrizacion.exigirPerfilPosting("COBRO");
        BigDecimal ret = retencionIva == null ? BigDecimal.ZERO : retencionIva;
        if (ret.signum() < 0 || ret.compareTo(importe) > 0) {
            throw new DomainException("La retención de IVA debe estar entre cero y el importe del cobro.");
        }
        List<PartidaDetalle> lineas = new ArrayList<>();
        lineas.add(PartidaDetalle.nueva(perfil.cuentaDebe(), importe.subtract(ret), BigDecimal.ZERO));
        if (ret.signum() > 0) {
            lineas.add(PartidaDetalle.nueva(fiscal.cuentaRetencionIva(), ret, BigDecimal.ZERO));
        }
        lineas.add(PartidaDetalle.nueva(perfil.cuentaHaber(), BigDecimal.ZERO, importe));
        return new OperacionPreview("Cobro", TipoPartida.IN, fecha, lineas);
    }

    /**
     * Previsualiza el asiento de un anticipo SaaS sin contabilizarlo.
     */
    public OperacionPreview previsualizarAnticipo(LocalDate fecha, BigDecimal base) {
        var fiscal = parametrizacion.exigirParametrosFiscales();
        var perfil = parametrizacion.exigirPerfilPosting("ANTICIPO");
        var iva = IvaCalculator.desdeNeto(base, fiscal.iva());
        return new OperacionPreview("Anticipo SaaS", TipoPartida.IN, fecha, List.of(
                PartidaDetalle.nueva(perfil.cuentaDebe(), iva.total(), BigDecimal.ZERO),
                PartidaDetalle.nueva(perfil.cuentaHaber(), BigDecimal.ZERO, iva.base()),
                PartidaDetalle.nueva(fiscal.cuentaDebito(), BigDecimal.ZERO, iva.iva())));
    }

    /**
     * Previsualiza el asiento de un pago a proveedor sin contabilizarlo.
     */
    public OperacionPreview previsualizarPago(LocalDate fecha, BigDecimal importe) {
        var perfil = parametrizacion.exigirPerfilPosting("PAGO");
        return new OperacionPreview("Pago", TipoPartida.EG, fecha, List.of(
                PartidaDetalle.nueva(perfil.cuentaDebe(), importe, BigDecimal.ZERO),
                PartidaDetalle.nueva(perfil.cuentaHaber(), BigDecimal.ZERO, importe)));
    }

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
        if (t == null) {
            throw new DomainException("El tercero es obligatorio.");
        }
        String nit = t.getNit() == null ? "" : t.getNit().trim();
        String razon = t.getRazonSocial() == null ? "" : t.getRazonSocial().trim();
        if (nit.isEmpty()) {
            throw new DomainException("El NIT del tercero es obligatorio.");
        }
        if (nit.length() > 30) {
            throw new DomainException("El NIT no puede superar 30 caracteres.");
        }
        if (razon.isEmpty()) {
            throw new DomainException("La razón social del tercero es obligatoria.");
        }
        if (razon.length() > 200) {
            throw new DomainException("La razón social no puede superar 200 caracteres.");
        }
        t.setNit(nit);
        t.setRazonSocial(razon);
        jdbc.runInTransaction(conn -> {
            try {
                if (terceros.porId(conn, t.getId()).isPresent()) {
                    terceros.actualizar(conn, t);
                } else {
                    terceros.insertar(conn, t);
                }
            } catch (PersistenceException e) {
                if (esViolacionUnicaNIT(e)) {
                    throw new DomainException("Ya existe un tercero registrado con el NIT " + nit + ".");
                }
                throw e;
            }
            bitacora.registrar(conn, BitacoraEntrada.de(contador.getId(), "TERCERO", "TERCERO",
                    t.getId().toString(), null, t.getNit(), t.getRazonSocial()));
        });
        return t;
    }

    private static boolean esViolacionUnicaNIT(Throwable e) {
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (String.valueOf(c.getMessage()).contains("uq_tercero_nit")) {
                return true;
            }
        }
        return false;
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
        LocalDate fechaOp = fechaObligatoria(fecha);
        BigDecimal montoOp = importePositivo(baseOTotal, "importe de la venta");
        var fiscal = parametrizacion.exigirParametrosFiscales();
        var perfil = parametrizacion.exigirPerfilPosting("VENTA");
        String cuentaPorCobrar = parametrizacion.resolverCuenta(cuentaCliente, perfil.cuentaDebe(),
                "cuenta por cobrar de la venta");
        String cuentaDeIngreso = parametrizacion.resolverCuenta(cuentaIngreso, perfil.cuentaHaber(),
                "cuenta de ingreso de la venta");
        // Documento, partida y vínculo se confirman en una sola transacción: si la
        // contabilización falla, el documento no queda huérfano en estado REGISTRADO.
        return jdbc.inTransaction(conn -> {
            Tercero cliente = terceros.porId(conn, clienteId)
                    .orElseThrow(() -> new DomainException("Cliente no encontrado"));
            if (!cliente.isCliente()) {
                throw new DomainException("El tercero no está marcado como cliente");
            }
            var empresa = empresas.obtenerUnica(conn).orElseThrow();
            var ivaR = calc(montoOp, ivaIncluido, fiscal.iva());
            Documento doc = nuevoDoc(conn, "CCF",
                    nitEmpresa(empresa), "DTE-" + System.currentTimeMillis(),
                    "SIM-VTA-" + shortId(), cliente.getNit(), cliente.getId(), fechaOp, ivaR, ivaIncluido);
            PlantillaDraft draft = new PlantillaDraft(doc, "DOC-VENTA-" + doc.getId(),
                    List.of(
                            PartidaDetalle.nueva(cuentaPorCobrar, ivaR.total(), BigDecimal.ZERO),
                            PartidaDetalle.nueva(cuentaDeIngreso, BigDecimal.ZERO, ivaR.base()),
                            PartidaDetalle.nueva(fiscal.cuentaDebito(), BigDecimal.ZERO, ivaR.iva())
                    ),
                    concepto != null ? concepto : "Venta " + cliente.getRazonSocial(),
                    TipoPartida.DI, fechaOp, "VENTA");
            return contabilizarPlantilla(draft);
        });
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
        LocalDate fechaOp = fechaObligatoria(fecha);
        BigDecimal montoOp = importePositivo(baseOTotal, "importe de la compra");
        var fiscal = parametrizacion.exigirParametrosFiscales();
        var perfil = parametrizacion.exigirPerfilPosting("COMPRA");
        String cuentaGasto = parametrizacion.resolverCuenta(cuentaGastoOActivo, perfil.cuentaDebe(),
                "cuenta de gasto o activo de la compra");
        String cuentaPorPagar = parametrizacion.resolverCuenta(null, perfil.cuentaHaber(),
                "cuenta por pagar de la compra");
        return jdbc.inTransaction(conn -> {
            Tercero prov = terceros.porId(conn, proveedorId)
                    .orElseThrow(() -> new DomainException("Proveedor no encontrado"));
            if (!prov.isProveedor()) {
                throw new DomainException("El tercero no está marcado como proveedor");
            }
            var empresa = empresas.obtenerUnica(conn).orElseThrow();
            var ivaR = calc(montoOp, ivaIncluido, fiscal.iva());
            Documento doc = nuevoDoc(conn, "CCF",
                    prov.getNit(), "DTE-P-" + System.currentTimeMillis(),
                    "SIM-CMP-" + shortId(), nitEmpresa(empresa), prov.getId(), fechaOp, ivaR, ivaIncluido);
            PlantillaDraft draft = new PlantillaDraft(doc, "DOC-COMPRA-" + doc.getId(),
                    List.of(
                            PartidaDetalle.nueva(cuentaGasto, ivaR.base(), BigDecimal.ZERO),
                            PartidaDetalle.nueva(fiscal.cuentaCredito(), ivaR.iva(), BigDecimal.ZERO),
                            PartidaDetalle.nueva(cuentaPorPagar, BigDecimal.ZERO, ivaR.total())
                    ),
                    concepto != null ? concepto : "Compra " + prov.getRazonSocial(),
                    TipoPartida.DI, fechaOp, "COMPRA");
            return contabilizarPlantilla(draft);
        });
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
        LocalDate fechaOp = fechaObligatoria(fecha);
        BigDecimal importeOp = importePositivo(importe, "importe del cobro");
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
        if (ret.signum() < 0 || ret.compareTo(importeOp) > 0) {
            throw new DomainException("La retención de IVA debe estar entre cero y el importe del cobro.");
        }
        BigDecimal banco = importeOp.subtract(ret);
        return jdbc.inTransaction(conn -> {
            Tercero cli = terceros.porId(conn, clienteId)
                    .orElseThrow(() -> new DomainException("Cliente no encontrado"));
            var empresa = empresas.obtenerUnica(conn).orElseThrow();
            var ivaR = new IvaCalculator.Resultado(importeOp, BigDecimal.ZERO, importeOp);
            Documento doc = nuevoDoc(conn, "RECIBO",
                    nitEmpresa(empresa), "REC-" + System.currentTimeMillis(),
                    "SIM-COB-" + shortId(), cli.getNit(), cli.getId(), fechaOp, ivaR, false);
            List<PartidaDetalle> lineas = new ArrayList<>();
            lineas.add(PartidaDetalle.nueva(cuentaBanco, banco, BigDecimal.ZERO));
            if (ret.signum() > 0) {
                lineas.add(PartidaDetalle.nueva(fiscal.cuentaRetencionIva(), ret, BigDecimal.ZERO));
            }
            lineas.add(PartidaDetalle.nueva(cuentaPorCobrar, BigDecimal.ZERO, importeOp));
            PlantillaDraft draft = new PlantillaDraft(doc, "DOC-COBRO-" + doc.getId(), lineas,
                    concepto != null ? concepto : "Cobro " + cli.getRazonSocial(),
                    TipoPartida.IN, fechaOp, "COBRO");
            return contabilizarPlantilla(draft);
        });
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
        LocalDate fechaOp = fechaObligatoria(fecha);
        BigDecimal importeOp = importePositivo(importe, "importe del pago");
        var perfil = parametrizacion.exigirPerfilPosting("PAGO");
        String cuentaPorPagar = parametrizacion.resolverCuenta(cuentaProveedor, perfil.cuentaDebe(),
                "cuenta por pagar del pago");
        String cuentaBanco = parametrizacion.resolverCuenta(null, perfil.cuentaHaber(),
                "cuenta de banco del pago");
        return jdbc.inTransaction(conn -> {
            Tercero prov = terceros.porId(conn, proveedorId)
                    .orElseThrow(() -> new DomainException("Proveedor no encontrado"));
            var ivaR = new IvaCalculator.Resultado(importeOp, BigDecimal.ZERO, importeOp);
            Documento doc = nuevoDoc(conn, "COMPROBANTE",
                    "BANCO", "PAG-" + System.currentTimeMillis(),
                    "SIM-PAG-" + shortId(), prov.getNit(), prov.getId(), fechaOp, ivaR, false);
            PlantillaDraft draft = new PlantillaDraft(doc, "DOC-PAGO-" + doc.getId(),
                    List.of(
                            PartidaDetalle.nueva(cuentaPorPagar, importeOp, BigDecimal.ZERO),
                            PartidaDetalle.nueva(cuentaBanco, BigDecimal.ZERO, importeOp)
                    ),
                    concepto != null ? concepto : "Pago " + prov.getRazonSocial(),
                    TipoPartida.EG, fechaOp, "PAGO");
            return contabilizarPlantilla(draft);
        });
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
        LocalDate fechaOp = fechaObligatoria(fecha);
        BigDecimal baseOp = importePositivo(base, "base del anticipo");
        var fiscal = parametrizacion.exigirParametrosFiscales();
        var perfil = parametrizacion.exigirPerfilPosting("ANTICIPO");
        String cuentaBanco = parametrizacion.resolverCuenta(null, perfil.cuentaDebe(),
                "cuenta de banco del anticipo");
        String cuentaAnticipo = parametrizacion.resolverCuenta(null, perfil.cuentaHaber(),
                "cuenta de anticipo del cliente");
        var ivaR = IvaCalculator.desdeNeto(baseOp, fiscal.iva());
        return jdbc.inTransaction(conn -> {
            Tercero cli = terceros.porId(conn, clienteId).orElseThrow();
            var empresa = empresas.obtenerUnica(conn).orElseThrow();
            Documento doc = nuevoDoc(conn, "CCF",
                    nitEmpresa(empresa), "ANT-" + System.currentTimeMillis(),
                    "SIM-ANT-" + shortId(), cli.getNit(), cli.getId(), fechaOp, ivaR, false);
            PlantillaDraft draft = new PlantillaDraft(doc, "DOC-ANTICIPO-" + doc.getId(),
                    List.of(
                            PartidaDetalle.nueva(cuentaBanco, ivaR.total(), BigDecimal.ZERO),
                            PartidaDetalle.nueva(cuentaAnticipo, BigDecimal.ZERO, ivaR.base()),
                            PartidaDetalle.nueva(fiscal.cuentaDebito(), BigDecimal.ZERO, ivaR.iva())
                    ),
                    concepto != null ? concepto : "Anticipo SaaS " + cli.getRazonSocial(),
                    TipoPartida.IN, fechaOp, "ANTICIPO");
            return contabilizarPlantilla(draft);
        });
    }

    /**
     * Aplica parte de un anticipo a un DTE concreto y deja trazabilidad en
     * {@code aplicacion_pago}. La operación reduce simultáneamente el pasivo
     * del anticipo y la cuenta por cobrar del documento.
     *
     * @param anticipoId identificador del documento de anticipo
     * @param dteId identificador del DTE al que se aplica
     * @param importe importe a aplicar
     * @param fecha fecha contable
     * @return partida contabilizada
     */
    public Partida aplicarAnticipo(UUID anticipoId, UUID dteId, BigDecimal importe, LocalDate fecha) {
        auth.exigirContador();
        BigDecimal monto = importe == null ? BigDecimal.ZERO : importe.setScale(2, RoundingMode.HALF_UP);
        if (monto.signum() <= 0) {
            throw new DomainException("El importe de aplicación debe ser mayor que cero.");
        }
        var fiscal = parametrizacion.exigirParametrosFiscales();
        var anticipoPerfil = parametrizacion.exigirPerfilPosting("ANTICIPO");
        var ventaPerfil = parametrizacion.exigirPerfilPosting("VENTA");
        // Validación, partida y trazabilidad en aplicacion_pago confirman en una
        // sola transacción para no dejar aplicaciones sin respaldo contable.
        return jdbc.inTransaction(conn -> {
            Documento anticipo = documentos.porId(conn, anticipoId)
                    .orElseThrow(() -> new DomainException("Anticipo no encontrado"));
            Documento dte = documentos.porId(conn, dteId)
                    .orElseThrow(() -> new DomainException("DTE no encontrado"));
            if (!anticipo.getTipo().equals("CCF") || anticipo.getTerceroId() == null
                    || !anticipo.getTerceroId().equals(dte.getTerceroId())
                    || !List.of("CCF", "FACTURA").contains(dte.getTipo())
                    || !"CONTABILIZADO".equals(dte.getEstado())) {
                throw new DomainException("El anticipo y el DTE deben pertenecer al mismo cliente.");
            }
            BigDecimal disponible = anticipo.getTotal().subtract(documentos.importeAplicado(conn, anticipoId));
            if (monto.compareTo(disponible) > 0) {
                throw new DomainException("El importe supera el saldo disponible del anticipo.");
            }
            BigDecimal saldoDte = dte.getTotal().subtract(documentos.importeAplicadoEnDocumento(conn, dteId));
            if (monto.compareTo(saldoDte) > 0) {
                throw new DomainException("El importe supera el saldo pendiente del DTE.");
            }
            // El anticipo mantiene separado el pasivo por la base (210702) del IVA
            // cobrado por anticipado (210201). La aplicación liquida cada componente
            // en proporción al total aplicado: el pasivo nunca queda deudor y el
            // débito fiscal no se duplica con el que genera el DTE.
            BigDecimal totalAnticipo = anticipo.getTotal();
            BigDecimal baseAplicada = totalAnticipo.signum() > 0
                    ? monto.multiply(anticipo.getBaseGravada())
                            .divide(totalAnticipo, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal ivaAplicada = monto.subtract(baseAplicada);
            List<PartidaDetalle> lineas = new ArrayList<>();
            if (baseAplicada.signum() > 0) {
                lineas.add(PartidaDetalle.nueva(anticipoPerfil.cuentaHaber(),
                        baseAplicada, BigDecimal.ZERO));
            }
            if (ivaAplicada.signum() > 0) {
                lineas.add(PartidaDetalle.nueva(fiscal.cuentaDebito(),
                        ivaAplicada, BigDecimal.ZERO));
            }
            lineas.add(PartidaDetalle.nueva(ventaPerfil.cuentaDebe(), BigDecimal.ZERO, monto));
            Partida borrador = partidas.guardarBorrador(TipoPartida.DI, fecha,
                    "Aplicación de anticipo a DTE " + dteId,
                    "APLICACION-" + anticipoId + "-" + dteId,
                    "APLICACION-ANTICIPO-" + anticipoId + "-" + dteId, lineas, null);
            Partida contabilizada = partidas.contabilizar(borrador.getId());
            documentos.registrarAplicacion(conn, anticipoId, dteId,
                    contabilizada.getId(), monto, fecha, contabilizada.getConcepto());
            return contabilizada;
        });
    }

    /**
     * Documento con su saldo pendiente de aplicación o cobro.
     *
     * @param documento documento tributario
     * @param saldo importe aún no aplicado/cobrado
     */
    public record DocumentoSaldo(Documento documento, BigDecimal saldo) {
    }

    /**
     * Lista los anticipos del cliente con saldo disponible para aplicar a DTEs.
     *
     * @param clienteId identificador del cliente
     * @return anticipos con saldo positivo
     */
    public List<DocumentoSaldo> anticiposConSaldo(UUID clienteId) {
        auth.exigirContador();
        return jdbc.inTransaction(conn -> documentos.listarAnticipos(conn, clienteId).stream()
                .map(d -> new DocumentoSaldo(d,
                        d.getTotal().subtract(documentos.importeAplicado(conn, d.getId()))))
                .filter(s -> s.saldo().signum() > 0)
                .toList());
    }

    /**
     * Lista los DTEs del cliente con saldo pendiente de cobro o aplicación.
     *
     * @param clienteId identificador del cliente
     * @return DTEs con saldo positivo
     */
    public List<DocumentoSaldo> dtesConSaldo(UUID clienteId) {
        auth.exigirContador();
        return jdbc.inTransaction(conn -> documentos.listarDtesCliente(conn, clienteId).stream()
                .map(d -> new DocumentoSaldo(d,
                        d.getTotal().subtract(documentos.importeAplicadoEnDocumento(conn, d.getId()))))
                .filter(s -> s.saldo().signum() > 0)
                .toList());
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

    private static LocalDate fechaObligatoria(LocalDate fecha) {
        if (fecha == null) {
            throw new DomainException("La fecha de la operación es obligatoria.");
        }
        return fecha;
    }

    private static BigDecimal importePositivo(BigDecimal valor, String etiqueta) {
        if (valor == null) {
            throw new DomainException("El " + etiqueta + " es obligatorio.");
        }
        BigDecimal normalizado = valor.setScale(2, RoundingMode.HALF_UP);
        if (normalizado.signum() <= 0) {
            throw new DomainException("El " + etiqueta + " debe ser mayor que cero.");
        }
        return normalizado;
    }

    private static String nitEmpresa(sv.edu.ues.colibri.domain.model.Empresa e) {
        return e.getNit() != null ? e.getNit() : "SIMULADO-NIT";
    }

    /**
     * Emite una Nota de Crédito (NC) o Nota de Débito (ND) contra un documento
     * ya contabilizado, por el total del documento. La NC revierte la partida
     * asociada y registra montos negativos; la ND duplica el asiento original.
     * La nota queda vinculada al documento origen y a la partida de ajuste
     * para que el Libro de IVA netee el efecto. Todo ocurre en una sola
     * transacción: si algo falla, no queda ni la nota ni la reversión.
     *
     * @param documentoOrigenId documento que se ajusta
     * @param fecha fecha de emisión de la nota
     * @param motivo justificación del ajuste (obligatoria)
     * @param notaDebito {@code true} para ND, {@code false} para NC
     * @return documento de ajuste emitido y contabilizado
     */
    public Documento emitirNotaAjuste(UUID documentoOrigenId, LocalDate fecha, String motivo,
                                      boolean notaDebito) {
        var contador = auth.exigirContador();
        if (motivo == null || motivo.isBlank()) {
            throw new DomainException("La nota de ajuste requiere un motivo documentado.");
        }
        if (fecha == null) {
            throw new DomainException("La fecha de la nota de ajuste es obligatoria.");
        }
        String tipoNota = notaDebito ? "ND" : "NC";
        return jdbc.inTransaction(conn -> {
            Documento origen = documentos.porId(conn, documentoOrigenId)
                    .orElseThrow(() -> new DomainException("Documento no encontrado."));
            if (!"CONTABILIZADO".equals(origen.getEstado())) {
                throw new DomainException(
                        "Solo se emiten notas de ajuste sobre documentos contabilizados.");
            }
            if ("NC".equals(origen.getTipo()) || "ND".equals(origen.getTipo())) {
                throw new DomainException("No se ajusta una nota de crédito o débito; "
                        + "emita la nota contra el documento original.");
            }
            DocumentoRepository.EnlaceDocumento enlace = documentos
                    .enlaceContable(conn, documentoOrigenId)
                    .orElseThrow(() -> new DomainException(
                            "El documento no tiene partida contable asociada; no puede ajustarse."));
            String codigoGen = "NOTA-" + tipoNota + "-" + shortId();
            Partida partidaAjuste;
            if (notaDebito) {
                Partida original = partidas.buscar(enlace.partidaId())
                        .orElseThrow(() -> new DomainException("Partida del documento no encontrada."));
                List<PartidaDetalle> lineas = new ArrayList<>();
                for (PartidaDetalle d : original.getDetalles()) {
                    lineas.add(PartidaDetalle.nueva(d.getCodigoCuenta(), d.getAuxiliar(),
                            d.getCentroCosto(), d.getDebe(), d.getHaber()));
                }
                Partida borrador = partidas.guardarBorrador(TipoPartida.AJ, fecha,
                        "Nota de débito " + origen.getCodigoGeneracion(),
                        codigoGen, "ND-" + origen.getId(), lineas, null);
                partidaAjuste = partidas.contabilizar(borrador.getId());
            } else {
                partidaAjuste = partidas.revertir(enlace.partidaId(), fecha, motivo.trim());
            }
            Documento nota = new Documento(UUID.randomUUID(), tipoNota, origen.getEmisorNit(),
                    origen.getNumeroControl(), codigoGen, origen.getReceptorNit(),
                    origen.getTerceroId(), origen.getContratoId(), fecha,
                    signoImporte(origen.getBaseGravada(), notaDebito),
                    signoImporte(origen.getIva(), notaDebito),
                    signoImporte(origen.getExento(), notaDebito),
                    signoImporte(origen.getTotal(), notaDebito),
                    origen.isIvaIncluido(), "REGISTRADO", true);
            documentos.insertar(conn, nota);
            documentos.vincularOrigen(conn, nota.getId(), origen.getId());
            documentos.vincularEvento(conn, nota.getId(), partidaAjuste.getEventoId(),
                    enlace.tipoEvento(), partidaAjuste.getId());
            documentos.marcarContabilizado(conn, nota.getId());
            bitacora.registrar(conn, BitacoraEntrada.de(
                    contador.getId(), notaDebito ? "NOTA_DEBITO" : "NOTA_CREDITO",
                    "DOCUMENTO", nota.getId().toString(),
                    origen.getCodigoGeneracion(), codigoGen, motivo.trim()));
            return nota;
        });
    }

    private static BigDecimal signoImporte(BigDecimal valor, boolean positivo) {
        BigDecimal v = valor == null ? BigDecimal.ZERO : valor;
        return positivo ? v : v.negate();
    }

    /**
     * Importa un borrador de partida desde contenido CSV.
     * <p>
     * Formatos admitidos por línea:
     * <ul>
     *   <li>{@code cuenta,debe,haber} (compatibilidad histórica)</li>
     *   <li>{@code cuenta,auxiliar,centro,debe,haber}</li>
     *   <li>{@code cuenta,auxiliar,centro,debe,haber,concepto}</li>
     * </ul>
     * La primera fila se omite si inicia con {@code cuenta}.
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
        String concepto = "Importación CSV de asientos";
        int numeroLinea = 0;
        for (String linea : contenido.split("\\R")) {
            numeroLinea++;
            if (linea.isBlank() || linea.toLowerCase().startsWith("cuenta")) {
                continue;
            }
            String[] cols = linea.split(",", -1);
            if (cols.length == 3) {
                PartidaDetalle detalle = PartidaDetalle.nueva(
                        cols[0].trim(), null, null,
                        importeCsv(cols[1], numeroLinea), importeCsv(cols[2], numeroLinea));
                validarUnLado(detalle, numeroLinea);
                lineas.add(detalle);
            } else if (cols.length >= 5) {
                String auxiliar = cols[1].trim().isEmpty() ? null : cols[1].trim();
                String centro = cols[2].trim().isEmpty() ? null : cols[2].trim();
                PartidaDetalle detalle = PartidaDetalle.nueva(
                        cols[0].trim(), auxiliar, centro,
                        importeCsv(cols[3], numeroLinea), importeCsv(cols[4], numeroLinea));
                validarUnLado(detalle, numeroLinea);
                lineas.add(detalle);
                if (cols.length >= 6 && !cols[5].trim().isEmpty()) {
                    concepto = cols[5].trim();
                }
            } else {
                throw new DomainException(
                        "Línea " + numeroLinea + ": cada línea debe tener cuenta,debe,haber "
                                + "o cuenta,auxiliar,centro,debe,haber[,concepto]: " + linea);
            }
        }
        if (lineas.size() < 2) {
            throw new DomainException("El archivo debe incluir al menos dos líneas de asiento.");
        }
        return partidas.guardarBorrador(TipoPartida.DI, fecha, concepto,
                "CSV", "IMP-" + UUID.randomUUID(), lineas, null);
    }

    private static BigDecimal importeCsv(String texto, int numeroLinea) {
        String limpio = texto == null ? "" : texto.trim();
        if (limpio.isEmpty()) {
            return BigDecimal.ZERO;
        }
        if (!limpio.matches("-?\\d{1,16}(\\.\\d{1,2})?")) {
            throw new DomainException("Línea " + numeroLinea + ": importe inválido '" + limpio
                    + "'. Use solo dígitos y punto decimal, formato 1250.75.");
        }
        BigDecimal valor = new BigDecimal(limpio);
        if (valor.signum() < 0) {
            throw new DomainException("Línea " + numeroLinea + ": el importe no puede ser negativo.");
        }
        return valor;
    }

    /**
     * Exige la regla de partida doble por línea: el importe va en Debe o en
     * Haber, nunca en ambos ni en ninguno, con número de línea para guiar al
     * contador que corrige el archivo.
     */
    private static void validarUnLado(PartidaDetalle detalle, int numeroLinea) {
        boolean conDebe = detalle.getDebe().signum() > 0;
        boolean conHaber = detalle.getHaber().signum() > 0;
        if (conDebe && conHaber) {
            throw new DomainException("Línea " + numeroLinea + ": la cuenta "
                    + detalle.getCodigoCuenta()
                    + " tiene importe en Debe y Haber a la vez; use una sola columna.");
        }
        if (!conDebe && !conHaber) {
            throw new DomainException("Línea " + numeroLinea + ": la cuenta "
                    + detalle.getCodigoCuenta()
                    + " no tiene importe en Debe ni en Haber.");
        }
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private record PlantillaDraft(Documento doc, String claveEvento, List<PartidaDetalle> lineas,
                                  String concepto, TipoPartida tipo, LocalDate fecha, String tipoEvento) {
    }
}
