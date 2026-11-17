package sv.edu.ues.colibri.desktop.ui.nav;

import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.desktop.ui.BrandIcon.Symbol;
import sv.edu.ues.colibri.desktop.ui.nav.NavItem.Arquetipo;
import sv.edu.ues.colibri.desktop.ui.views.AjustesCierreView;
import sv.edu.ues.colibri.desktop.ui.views.DocumentosView;
import sv.edu.ues.colibri.desktop.ui.views.EmpresaPeriodosView;
import sv.edu.ues.colibri.desktop.ui.views.EstimacionCostosView;
import sv.edu.ues.colibri.desktop.ui.views.InicioView;
import sv.edu.ues.colibri.desktop.ui.views.InventarioCostosView;
import sv.edu.ues.colibri.desktop.ui.views.InventarioCostosView.Panel;
import sv.edu.ues.colibri.desktop.ui.views.ParametrizacionView;
import sv.edu.ues.colibri.desktop.ui.views.ParametrizacionView.Seccion;
import sv.edu.ues.colibri.desktop.ui.views.PartidasView;
import sv.edu.ues.colibri.desktop.ui.views.PlanillaView;
import sv.edu.ues.colibri.desktop.ui.views.PlantillasView;
import sv.edu.ues.colibri.desktop.ui.views.ProcesosCifView;
import sv.edu.ues.colibri.desktop.ui.views.ReportesView;
import sv.edu.ues.colibri.desktop.ui.views.SocietariosView;
import sv.edu.ues.colibri.desktop.ui.views.SoporteTecnicoView;
import sv.edu.ues.colibri.desktop.ui.views.TemasContablesView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static sv.edu.ues.colibri.desktop.ui.nav.NavItem.Arquetipo.FILL;
import static sv.edu.ues.colibri.desktop.ui.nav.NavItem.Arquetipo.SCROLL;

/**
 * Árbol de navegación por rol. Una sola definición alimenta el menú lateral, el
 * breadcrumb, los enlaces entre módulos y las pruebas.
 */
public final class NavRegistry {

    /** Destino de los enlaces internos de módulo (p. ej. desde Temas contables). */
    private static final Map<String, String> MODULOS = Map.of(
            "Empresa", "nav-empresa",
            "Parametrización", "nav-parametrizacion",
            "Registros", "nav-registros",
            "Inventario", "nav-inventario",
            "Costos", "nav-costos",
            "Reportes", "nav-reportes");

    /**
     * Destino exacto (hoja de navegación) de cada tema contable. Sin entrada
     * propia, el tema abre la primera sección de su módulo.
     */
    private static final Map<String, String> DESTINOS_TEMAS = Map.ofEntries(
            Map.entry("La contabilidad y empresa", "nav-empresa"),
            Map.entry("Clasificación de las empresas según actividad económica", "nav-empresa"),
            Map.entry("Principios contables generalmente aceptados",
                    "nav-parametrizacion-politicas-y-secuenciales"),
            Map.entry("Diseño de un sistema contable", "nav-parametrizacion-mapeo-de-modulos"),
            Map.entry("Introducción al concepto de partida doble", "nav-registros-libro-diario"),
            Map.entry("Métodos de registros mercantiles", "nav-reportes-obligatorios"),
            Map.entry("La cuenta T", "nav-reportes-obligatorios"),
            Map.entry("Estados financieros y sus elementos", "nav-reportes-obligatorios"),
            Map.entry("Asientos de ajuste", "nav-registros-ajustes"),
            Map.entry("Hoja de trabajo", "nav-reportes-obligatorios"),
            Map.entry("Costos para la toma de decisiones", "nav-costos-a-la-medida"),
            Map.entry("Generalidades de la contabilidad de costos", "nav-costos-a-la-medida"),
            Map.entry("Elementos del costo de un producto", "nav-costos-a-la-medida"),
            Map.entry("Representación de cómo se acumulan los costos en cada tipo de empresa",
                    "nav-costos-a-la-medida"),
            Map.entry("Contabilización y control de los materiales", "nav-inventario-digitales"),
            Map.entry("Manejo de inventarios: UEPS, PEPS y Costo Promedio", "nav-inventario-digitales"),
            Map.entry("Contabilización y control de la mano de obra", "nav-costos-a-la-medida"),
            Map.entry("Políticas de trabajo y manejo de recurso humano",
                    "nav-parametrizacion-politicas-y-secuenciales"),
            Map.entry("Contabilización y control de los costos indirectos de fabricación",
                    "nav-costos-procesos-y-cif"),
            Map.entry("CIF específicos de los sistemas informáticos", "nav-costos-procesos-y-cif"),
            Map.entry("Métodos de estimación de costos", "nav-costos-estimacion-y-politicas"),
            Map.entry("Cálculos del precio unitario", "nav-costos-estimacion-y-politicas"),
            Map.entry("Planeación de distribución y venta de software",
                    "nav-costos-estimacion-y-politicas"),
            Map.entry("Sistemas de costos por órdenes de trabajo", "nav-costos-a-la-medida"),
            Map.entry("Sistemas de costos por proceso", "nav-costos-suscripcion-saas"),
            Map.entry("Sistemas de costos estándar y directos", "nav-costos-estimacion-y-politicas"),
            Map.entry("Costos basados en actividades", "nav-costos-estimacion-y-politicas"));

    private NavRegistry() {
    }

    /**
     * Árbol del contador.
     *
     * @param ctx contexto de servicios
     * @param nav navegador para los enlaces entre secciones
     * @return grupos y hojas de primer nivel
     */
    public static List<NavItem> contador(AppContext ctx, Navegador nav) {
        List<NavItem> arbol = new ArrayList<>();
        arbol.add(NavItem.hoja("Inicio", Symbol.HOME, SCROLL,
                () -> new InicioView(ctx, () -> nav.ir("nav-registros-libro-diario"))));
        arbol.add(temas(nav));
        arbol.add(NavItem.hoja("Empresa", Symbol.BUILDING, SCROLL, () -> new EmpresaPeriodosView(ctx)));
        arbol.add(grupo("Parametrización", Symbol.CATALOG,
                parametrizacion("Catálogo de cuentas", Seccion.CATALOGO, ctx),
                parametrizacion("Mapeo de módulos", Seccion.MAPEO, ctx),
                parametrizacion("Períodos contables", Seccion.PERIODOS, ctx),
                parametrizacion("Parámetros fiscales", Seccion.FISCAL, ctx),
                parametrizacion("Políticas y secuenciales", Seccion.POLITICAS, ctx),
                parametrizacion("Centros de costo", Seccion.CENTROS, ctx)));
        arbol.add(grupo("Registros", Symbol.JOURNAL,
                h("nav-registros", "Documentos", SCROLL, () -> new DocumentosView(ctx)),
                h("nav-registros", "Libro diario", SCROLL, () -> new PartidasView(ctx)),
                h("nav-registros", "Plantillas", SCROLL, () -> new PlantillasView(ctx)),
                h("nav-registros", "Planilla", SCROLL, () -> new PlanillaView(ctx)),
                h("nav-registros", "Ajustes", SCROLL, () -> new AjustesCierreView(ctx))));
        arbol.add(grupo("Inventario", Symbol.INVENTORY,
                h("nav-inventario", "Digitales", SCROLL, () -> new InventarioCostosView(ctx, Panel.DIGITALES)),
                h("nav-inventario", "Kárdex", SCROLL, () -> new InventarioCostosView(ctx, Panel.KARDEX)),
                h("nav-inventario", "Servicios / Accesos", SCROLL,
                        () -> new InventarioCostosView(ctx, Panel.SERVICIOS))));
        arbol.add(grupo("Costos", Symbol.MONEY,
                h("nav-costos", "A la medida", SCROLL, () -> new InventarioCostosView(ctx, Panel.MEDIDA)),
                h("nav-costos", "Licencias descargables", SCROLL, () -> new InventarioCostosView(ctx, Panel.LICENCIAS)),
                h("nav-costos", "Suscripción SaaS", SCROLL, () -> new InventarioCostosView(ctx, Panel.SAAS)),
                h("nav-costos", "Procesos y CIF", SCROLL, () -> new ProcesosCifView(ctx)),
                h("nav-costos", "Estimación y políticas", SCROLL, () -> new EstimacionCostosView(ctx))));
        arbol.add(grupo("Reportes", Symbol.REPORT,
                h("nav-reportes", "Obligatorios", SCROLL, () -> new ReportesView(ctx, List.of(
                        ReportesView.DIARIO, ReportesView.MAYOR, ReportesView.BALANZA, ReportesView.CUENTA_T,
                        ReportesView.HOJA_TRABAJO, ReportesView.SITUACION, ReportesView.RESULTADOS,
                        ReportesView.LIBRO_IVA_COMPRAS, ReportesView.LIBRO_IVA_CCF, ReportesView.LIBRO_IVA_FCF))),
                h("nav-reportes", "Auxiliares", SCROLL, () -> new ReportesView(ctx, List.of(
                        ReportesView.AUX_CLIENTES, ReportesView.AUX_PROVEEDORES,
                        ReportesView.AUX_INVENTARIO, ReportesView.AUX_ORDEN, ReportesView.AUX_CC))),
                h("nav-reportes", "Societarios", SCROLL, SocietariosView::new)));
        return arbol;
    }

    /**
     * Árbol de soporte técnico (sin grupos).
     *
     * @param ctx contexto de servicios
     * @return hojas de primer nivel
     */
    public static List<NavItem> soporte(AppContext ctx) {
        return List.of(
                NavItem.hoja("Usuarios", Symbol.USERS, SCROLL,
                        () -> new SoporteTecnicoView(ctx, SoporteTecnicoView.SECCION_USUARIOS)),
                NavItem.hoja("Base de datos", Symbol.BACKUP, SCROLL,
                        () -> new SoporteTecnicoView(ctx, SoporteTecnicoView.SECCION_BD)),
                NavItem.hoja("Entorno", Symbol.BUILDING, SCROLL,
                        () -> new SoporteTecnicoView(ctx, SoporteTecnicoView.SECCION_ENTORNO)),
                NavItem.hoja("Monitoreo", Symbol.REPORT, SCROLL,
                        () -> new SoporteTecnicoView(ctx, SoporteTecnicoView.SECCION_MONITOREO)),
                NavItem.hoja("Accesos", Symbol.KEY, SCROLL,
                        () -> new SoporteTecnicoView(ctx, SoporteTecnicoView.SECCION_ACCESOS)));
    }

    /**
     * Id de navegación del módulo operativo citado por un tema contable.
     *
     * @param modulo nombre del módulo ("Registros", "Costos"...)
     * @return id del grupo u hoja
     * @throws IllegalArgumentException si el módulo no existe
     */
    public static String idDeModulo(String modulo) {
        String id = MODULOS.get(modulo);
        if (id == null) {
            throw new IllegalArgumentException("Módulo no disponible: " + modulo);
        }
        return id;
    }

    private static NavItem temas(Navegador nav) {
        List<NavItem> hijos = TemasContablesView.grupos().stream()
                .map(g -> NavItem.seccion("nav-temas-contables", g.titulo(), FILL,
                        () -> TemasContablesView.vista(g,
                                tema -> nav.ir(idDestinoTema(tema)))))
                .toList();
        return NavItem.grupo("Temas contables", Symbol.DOCUMENT, hijos);
    }

    private static String idDestinoTema(TemasContablesView.Tema tema) {
        String destino = DESTINOS_TEMAS.get(tema.titulo());
        return destino != null ? destino : idDeModulo(tema.modulo());
    }

    private static NavItem parametrizacion(String etiqueta, Seccion seccion, AppContext ctx) {
        return NavItem.seccion("nav-parametrizacion", etiqueta, SCROLL, () -> new ParametrizacionView(ctx, seccion));
    }

    private static NavItem h(String idGrupo, String etiqueta, Arquetipo pagina,
                             java.util.function.Supplier<? extends javafx.scene.Node> fabrica) {
        return NavItem.seccion(idGrupo, etiqueta, pagina, fabrica);
    }

    private static NavItem grupo(String etiqueta, Symbol icono, NavItem... hijos) {
        return NavItem.grupo(etiqueta, icono, List.of(hijos));
    }
}
