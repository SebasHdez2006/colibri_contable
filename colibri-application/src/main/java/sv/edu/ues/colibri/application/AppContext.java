package sv.edu.ues.colibri.application;

import com.zaxxer.hikari.HikariDataSource;
import sv.edu.ues.colibri.application.auth.AuthService;
import sv.edu.ues.colibri.application.bootstrap.BootstrapService;
import sv.edu.ues.colibri.application.bootstrap.CasoContableSeedService;
import sv.edu.ues.colibri.application.catalogo.CatalogoService;
import sv.edu.ues.colibri.application.cierre.CierreService;
import sv.edu.ues.colibri.application.configuracion.ParametrizacionService;
import sv.edu.ues.colibri.application.costos.CosteoPorProcesosService;
import sv.edu.ues.colibri.application.costos.CifService;
import sv.edu.ues.colibri.application.inventario.InventarioCostoService;
import sv.edu.ues.colibri.application.libros.LibroContableService;
import sv.edu.ues.colibri.application.nomina.PlanillaService;
import sv.edu.ues.colibri.application.operacion.OperacionContableService;
import sv.edu.ues.colibri.application.panel.PanelInicioService;
import sv.edu.ues.colibri.application.partida.PartidaService;
import sv.edu.ues.colibri.application.periodo.PeriodoAppService;
import sv.edu.ues.colibri.application.plantilla.PlantillaPartidaService;
import sv.edu.ues.colibri.application.respaldo.RespaldoService;
import sv.edu.ues.colibri.application.soporte.SoporteTecnicoService;
import sv.edu.ues.colibri.persistence.config.DataSourceFactory;
import sv.edu.ues.colibri.persistence.config.DatabaseConfig;
import sv.edu.ues.colibri.persistence.config.FlywayMigrator;
import sv.edu.ues.colibri.persistence.jdbc.JdbcSupport;
import sv.edu.ues.colibri.reports.ReportService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Contenedor de dependencias y ciclo de vida de la aplicación.
 *
 * Centraliza la creación de servicios, repositorios y recursos compartidos
 * usados por la interfaz de escritorio.
 */
public final class AppContext implements AutoCloseable {


    private final DatabaseConfig config;
    private final HikariDataSource dataSource;
    private final JdbcSupport jdbc;
    private final AuthService authService;
    private final CatalogoService catalogoService;
    private final PeriodoAppService periodoAppService;
    private final PartidaService partidaService;
    private final OperacionContableService operacionService;
    private final InventarioCostoService inventarioService;
    private final CosteoPorProcesosService costeoPorProcesosService;
    private final CifService cifService;
    private final LibroContableService libroService;
    private final PlanillaService planillaService;
    private final CierreService cierreService;
    private final ParametrizacionService parametrizacionService;
    private final PanelInicioService panelService;
    private final PlantillaPartidaService plantillaService;
    private final RespaldoService respaldoService;
    private final SoporteTecnicoService soporteTecnicoService;
    private final ReportService reportService;
    private final BootstrapService bootstrapService;
    private final CasoContableSeedService casoContableSeedService;

    private AppContext(DatabaseConfig config, HikariDataSource dataSource) {
        this.config = config;
        this.dataSource = dataSource;
        this.jdbc = new JdbcSupport(dataSource);
        this.authService = new AuthService(jdbc);
        this.catalogoService = new CatalogoService(jdbc, authService);
        this.periodoAppService = new PeriodoAppService(jdbc, authService);
        this.partidaService = new PartidaService(jdbc, authService, catalogoService, periodoAppService);
        this.parametrizacionService = new ParametrizacionService(jdbc, authService);
        this.operacionService = new OperacionContableService(jdbc, authService, partidaService,
                parametrizacionService);
        this.inventarioService = new InventarioCostoService(jdbc, authService, partidaService,
                parametrizacionService);
        this.costeoPorProcesosService = new CosteoPorProcesosService(jdbc, authService, periodoAppService);
        this.cifService = new CifService(jdbc, authService, periodoAppService, partidaService);
        this.libroService = new LibroContableService(jdbc, authService);
        this.planillaService = new PlanillaService(jdbc, authService, partidaService, periodoAppService);
        this.cierreService = new CierreService(jdbc, authService, partidaService, periodoAppService,
                parametrizacionService);
        this.panelService = new PanelInicioService(jdbc, authService);
        this.plantillaService = new PlantillaPartidaService(jdbc, authService, partidaService,
                periodoAppService);
        this.respaldoService = new RespaldoService(authService, config, jdbc);
        this.soporteTecnicoService = new SoporteTecnicoService(jdbc, authService);
        this.reportService = new ReportService();
        this.bootstrapService = new BootstrapService(jdbc);
        this.casoContableSeedService = new CasoContableSeedService(jdbc);
    }

    /**
     * Crea un contexto de aplicación usando la configuración local por defecto.
     *
     * @return contexto completamente inicializado
     */
    public static AppContext iniciar() {
        return iniciarConConfig(DatabaseConfig.load());
    }

    /**
     * Crea un contexto de aplicación a partir de la configuración indicada.
     *
     * @param config configuración de base de datos y credenciales
     * @return contexto inicializado y con semillas/servicios listos
     */
    public static AppContext iniciarConConfig(DatabaseConfig config) {
        HikariDataSource migratePool = (HikariDataSource) DataSourceFactory.createMigrate(config);
        try {
            FlywayMigrator.migrate(migratePool);
        } finally {
            migratePool.close();
        }
        HikariDataSource appDs = DataSourceFactory.create(config);
        AppContext ctx = new AppContext(config, appDs);
        ctx.bootstrapService.asegurarDatosIniciales();
        ctx.soporteTecnicoService.sembrarLogsDemoSiVacio();
        if (semillaCasoDemoHabilitada()) {
            ctx.casoContableSeedService.asegurarDatosIniciales();
        }
        ctx.persistirCodigoRecuperacionSiNuevo();
        return ctx;
    }

    /**
     * Determina si debe cargarse la semilla didáctica de casos contables.
     *
     * @return {@code true} solo cuando la propiedad o la variable de entorno lo habilitan
     */
    static boolean semillaCasoDemoHabilitada() {
        return semillaCasoDemoHabilitada(
                System.getProperty("colibri.seed.demo"),
                System.getenv("COLIBRI_SEED_DEMO"));
    }

    /**
     * Evalúa las señales de activación de la semilla de demostración.
     *
     * @param propiedad valor de la propiedad JVM
     * @param entorno valor de la variable de entorno
     * @return {@code true} si cualquiera de las dos fuentes indica "true"
     */
    static boolean semillaCasoDemoHabilitada(String propiedad, String entorno) {
        return "true".equalsIgnoreCase(propiedad) || "true".equalsIgnoreCase(entorno);
    }

    /**
     * Persiste el código de recuperación solo cuando la inicialización generó uno nuevo.
     */
    private void persistirCodigoRecuperacionSiNuevo() {
        String codigo = bootstrapService.getCodigoRecuperacionGenerado();
        if (codigo == null) {
            return;
        }
        try {
            Path dir = Path.of(System.getProperty("user.home"), ".colibri");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("recovery-code.txt"), """
                    Código de recuperación de un solo uso (guarde en lugar seguro):
                    %s

                    Usuario: %s
                    """.formatted(codigo, BootstrapService.USUARIO_DEFAULT));
        } catch (IOException ignored) {
        }
    }

    /**
     * Obtiene el servicio de autenticación.
     *
     * @return servicio de autenticación
     */
    public AuthService auth() { return authService; }

    /**
     * Obtiene el servicio de catálogo.
     *
     * @return servicio de catálogo
     */
    public CatalogoService catalogo() { return catalogoService; }

    /**
     * Obtiene el servicio de períodos.
     *
     * @return servicio de períodos
     */
    public PeriodoAppService periodos() { return periodoAppService; }

    /**
     * Obtiene el servicio de partidas.
     *
     * @return servicio de partidas
     */
    public PartidaService partidas() { return partidaService; }

    /**
     * Obtiene el servicio de operaciones contables.
     *
     * @return servicio de operaciones
     */
    public OperacionContableService operaciones() { return operacionService; }

    /**
     * Obtiene el servicio de inventario y costeo.
     *
     * @return servicio de inventario
     */
    public InventarioCostoService inventario() { return inventarioService; }

    /**
     * Obtiene el servicio de costeo por procesos.
     *
     * @return servicio de procesos
     */
    public CosteoPorProcesosService procesos() { return costeoPorProcesosService; }

    /**
     * Obtiene el servicio de CIF.
     *
     * @return servicio de CIF
     */
    public CifService cif() { return cifService; }

    /**
     * Obtiene el servicio de libros contables.
     *
     * @return servicio de libros
     */
    public LibroContableService libros() { return libroService; }

    /**
     * Obtiene el servicio de planillas.
     *
     * @return servicio de planillas
     */
    public PlanillaService planilla() { return planillaService; }

    /**
     * Obtiene el servicio de cierre contable.
     *
     * @return servicio de cierre
     */
    public CierreService cierre() { return cierreService; }

    /**
     * Obtiene el servicio de parametrización.
     *
     * @return servicio de parametrización
     */
    public ParametrizacionService parametrizacion() { return parametrizacionService; }

    /**
     * Obtiene el servicio del tablero de inicio con métricas gerenciales.
     *
     * @return servicio de panel de inicio
     */
    public PanelInicioService panel() { return panelService; }

    /**
     * Obtiene el servicio de plantillas de asiento recurrente.
     *
     * @return servicio de plantillas de partida
     */
    public PlantillaPartidaService plantillas() { return plantillaService; }

    /**
     * Obtiene el servicio de respaldo.
     *
     * @return servicio de respaldo
     */
    public RespaldoService respaldo() { return respaldoService; }

    /**
     * Obtiene el servicio de soporte técnico.
     *
     * @return servicio de soporte
     */
    public SoporteTecnicoService soporte() { return soporteTecnicoService; }

    /**
     * Obtiene el servicio de reportes.
     *
     * @return servicio de reportes
     */
    public ReportService reports() { return reportService; }

    /**
     * Obtiene el soporte JDBC compartido.
     *
     * @return soporte JDBC
     */
    public JdbcSupport jdbc() { return jdbc; }

    /**
     * Obtiene la configuración de base de datos cargada.
     *
     * @return configuración activa
     */
    public DatabaseConfig config() { return config; }

    /**
     * Libera el pool de conexiones asociado al contexto.
     */
    @Override
    public void close() {
        dataSource.close();
    }
}
