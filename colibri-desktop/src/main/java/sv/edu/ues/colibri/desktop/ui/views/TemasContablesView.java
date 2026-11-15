package sv.edu.ues.colibri.desktop.ui.views;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import sv.edu.ues.colibri.desktop.ui.layout.MasterDetail;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/**
 * Mapa navegable de los temas exigidos por la rúbrica. Cada grupo es una sección
 * del menú lateral; cada tema se elige en una lista y enlaza al módulo operativo
 * que automatiza su proceso.
 */
public final class TemasContablesView {

    private TemasContablesView() {
    }

    /** Grupo de temas que se muestra como una sección del menú lateral. */
    public record Grupo(String titulo, List<Tema> temas) {}

    /** Tema de la rúbrica con su concepto, su automatización y el módulo que lo opera. */
    public record Tema(String titulo, String concepto, String automatizacion, String modulo) {}

    /**
     * Construye la vista de un grupo: lista de temas a la izquierda y detalle a la derecha.
     *
     * @param grupo       grupo de temas a mostrar
     * @param abrirModulo acción que navega al módulo operativo del tema
     * @return vista lista para insertarse en una página
     */
    public static VBox vista(Grupo grupo, Consumer<Tema> alAbrir) {
        Label titulo = new Label(grupo.titulo());
        titulo.getStyleClass().add("view-title");
        Label ayuda = new Label("Cada tema explica el control aplicado y permite abrir el módulo donde se registra, calcula o reporta la información.");
        ayuda.getStyleClass().add("view-subtitle");
        ayuda.setWrapText(true);
        MasterDetail<Tema> maestro = new MasterDetail<>(grupo.temas(), Tema::titulo,
                tema -> contenido(tema, alAbrir));
        VBox.setVgrow(maestro, Priority.ALWAYS);
        VBox vista = new VBox(titulo, ayuda, maestro);
        vista.getStyleClass().add("topics-view");
        return vista;
    }

    /**
     * Grupos de temas exigidos por la rúbrica, en el orden del menú.
     *
     * @return grupos con sus temas
     */
    public static List<Grupo> grupos() {
        return List.of(
                grupo("Empresa y fundamentos", List.of(
                        tema("La contabilidad y empresa", "Define la entidad, su actividad, período y responsables que originan los registros.",
                                "El perfil empresarial alimenta encabezados, firmas, moneda, períodos y controles fiscales.", "Empresa"),
                        tema("Clasificación de las empresas según actividad económica", "Clasifica la entidad por su giro económico para adaptar catálogo, procesos y reportes.",
                                "Se registra la actividad CAT-019 de programación informática y queda disponible en el perfil corporativo.", "Empresa"),
                        tema("Principios contables generalmente aceptados", "Aplica devengo, consistencia, empresa en marcha, materialidad y prudencia al reconocer operaciones.",
                                "Las políticas parametrizadas documentan el principio y la partida contabilizada queda inmutable y trazable.", "Parametrización"),
                        tema("Diseño de un sistema contable", "Integra catálogo, períodos, documentos, reglas de contabilización, centros de costo y reportes.",
                                "El sistema valida cuentas afectables, perfiles de posting, correlativos y estados de período antes de registrar.", "Parametrización"),
                        tema("Introducción al concepto de partida doble", "Toda operación tiene al menos un cargo y un abono por el mismo importe.",
                                "El diario compara Debe/Haber en tiempo real y bloquea la contabilización de partidas descuadradas.", "Registros")
                )),
                grupo("Registros y estados", List.of(
                        tema("Métodos de registros mercantiles", "Organiza la evidencia cronológica y sistemática en libro diario, mayor, auxiliares y libros de IVA.",
                                "Se generan Diario, Mayor, Balanza, auxiliares y formatos de IVA a partir de partidas contabilizadas.", "Reportes"),
                        tema("La cuenta T", "Representa cargos, abonos, totales y saldo de cada cuenta.",
                                "El reporte Cuenta T calcula saldo inicial, movimientos, totales y saldo final por cuenta seleccionada.", "Reportes"),
                        tema("Estados financieros y sus elementos", "Presenta activos, pasivos, patrimonio, ingresos, costos, gastos y flujos del período.",
                                "El sistema emite situación financiera, resultados, cambios en patrimonio y flujos de efectivo.", "Reportes"),
                        tema("Asientos de ajuste", "Reconoce devengos, diferimientos, depreciaciones, estimaciones y liquidaciones al cierre.",
                                "Los ajustes usan tipo AJ, período controlado, evidencia y reversión auditables.", "Registros"),
                        tema("Hoja de trabajo", "Concilia balanza, ajustes, balanza ajustada, resultados, capital y balance general.",
                                "La hoja de trabajo multicolumnar se genera automáticamente desde las partidas y ajustes del período.", "Reportes")
                )),
                grupo("Costos y control", List.of(
                        tema("Costos para la toma de decisiones", "Relaciona costo, margen, volumen y precio para decidir si vender, ajustar o priorizar una línea.",
                                "La calculadora compara costo directo, estándar y ABC antes de fijar margen y precio unitario.", "Costos"),
                        tema("Generalidades de la contabilidad de costos", "Identifica, mide, acumula y asigna recursos consumidos por un producto, plan o proyecto.",
                                "Colibrí usa órdenes para desarrollo, procesos para SaaS y planes facturados para servicios.", "Costos"),
                        tema("Elementos del costo de un producto", "Separa materiales o recursos directos, mano de obra directa y costos indirectos de fabricación.",
                                "Cada consumo se clasifica como MOD, servicio/recurso directo o CIF y queda asociado al objeto de costo.", "Costos"),
                        tema("Representación de cómo se acumulan los costos en cada tipo de empresa", "Muestra la ruta de acumulación desde el recurso hasta el producto, servicio, orden o proceso.",
                                "Órdenes: MOD + directos + CIF por proyecto; SaaS: CIF por usuarios-mes; servicios: costo por plan facturado.", "Costos"),
                        tema("Contabilización y control de los materiales", "Controla adquisición, consumo, disponibilidad y salida de recursos directos.",
                                "Los lotes de licencias y consumos de orden mantienen fecha, cantidad, tarifa, importe y partida de respaldo.", "Inventario"),
                        tema("Manejo de inventarios: UEPS, PEPS y Costo Promedio", "Compara métodos de valoración y aplica la política aprobada de salida.",
                                "Las ventas de licencias usan PEPS exacto; la pantalla conserva promedio y UEPS como análisis comparativo sin alterar asientos.", "Inventario"),
                        tema("Contabilización y control de la mano de obra", "Registra horas, tarifa y asignación de trabajo para separar MOD de trabajo indirecto.",
                                "Las horas de desarrollo se acumulan por orden o plan con centro de costo y generan el soporte contable del consumo.", "Costos"),
                        tema("Políticas de trabajo y manejo de recurso humano", "Establece registro, aprobación y trazabilidad de horas, tarifas y responsables.",
                                "Las políticas de RR. HH. exigen registro por orden/plan y aprobación del responsable antes de costear.", "Parametrización"),
                        tema("Contabilización y control de los costos indirectos de fabricación", "Acumula y aplica costos comunes que no son rastreables directamente a una unidad.",
                                "Los CIF se registran por centro de costo, se aplican a órdenes/planes y se comparan contra el estándar.", "Costos"),
                        tema("CIF específicos de los sistemas informáticos", "Incluye infraestructura cloud, servidores, soporte, herramientas, licencias técnicas y aseguramiento de calidad.",
                                "El proceso SaaS distribuye alojamiento, soporte e infraestructura por usuarios-mes y el ABC permite ver actividades específicas.", "Costos")
                )),
                grupo("Estimación y comercialización", List.of(
                        tema("Métodos de estimación de costos", "Incluye juicio de expertos, analogía, puntos de función, COSMIC, COCOMO, desgaste de trabajo, LOC, tres puntos y SLIM/Putnam.",
                                "La calculadora selecciona el método, documenta el esfuerzo, aplica CIF, margen y una recomendación de uso.", "Costos"),
                        tema("Cálculos del precio unitario", "Convierte costo total y margen objetivo en precio de venta por unidad, licencia, usuario-mes o entrega.",
                                "El cálculo presenta costo directo, CIF, costo total, precio unitario y margen antes de registrar la venta.", "Costos"),
                        tema("Planeación de distribución y venta de software", "Define línea comercial, plan, canal, modalidad de facturación, precio neto, IVA y costo reconocido.",
                                "Los planes distinguen licencia, SaaS, proyecto y servicio; la venta aplica el perfil contable y el IVA parametrizado.", "Costos"),
                        tema("Sistemas de costos por órdenes de trabajo", "Acumula recursos identificables para cada contrato o desarrollo a la medida.",
                                "La orden de servicio registra MOD, recursos directos y CIF; al entregar, reconoce el costo acumulado.", "Costos"),
                        tema("Sistemas de costos por proceso", "Acumula costos continuos de un proceso homogéneo y los prorratea por unidad de actividad.",
                                "SaaS distribuye alojamiento, soporte e infraestructura por usuarios-mes y plan activo.", "Costos"),
                        tema("Sistemas de costos estándar y directos", "Compara costo esperado y real, separa elementos directos y revela variaciones para gestión.",
                                "La calculadora entrega costo directo, estándar, variación y costo unitario para justificar decisiones.", "Costos"),
                        tema("Costos basados en actividades", "Asigna costos indirectos según las actividades que consumen los recursos, no sólo por volumen.",
                                "El cálculo ABC suma actividades como calidad e infraestructura y muestra el CIF aplicado por unidad.", "Costos")
                ))
        );
    }

    private static Grupo grupo(String titulo, List<Tema> temas) {
        return new Grupo(titulo, temas);
    }

    private static Node contenido(Tema tema, Consumer<Tema> alAbrir) {
        Label titulo = new Label(tema.titulo());
        titulo.getStyleClass().add("section-heading");
        Label concepto = etiqueta("Concepto", tema.concepto());
        Label automatizacion = etiqueta("Automatización en Colibrí", tema.automatizacion());
        Label destino = new Label("Módulo operativo: " + tema.modulo() + ".");
        destino.getStyleClass().add("section-note");
        Button abrir = new Button("Abrir " + tema.modulo());
        abrir.setOnAction(evento -> alAbrir.accept(tema));
        VBox contenido = new VBox(14, titulo, concepto, automatizacion, destino, abrir);
        contenido.getStyleClass().add("form-card");
        return contenido;
    }

    private static Label etiqueta(String titulo, String texto) {
        Label etiqueta = new Label(titulo + ":\n" + texto);
        etiqueta.setWrapText(true);
        etiqueta.setMaxWidth(Double.MAX_VALUE);
        return etiqueta;
    }

    private static Tema tema(String titulo, String concepto, String automatizacion, String modulo) {
        return new Tema(titulo, concepto, automatizacion, modulo);
    }

}
