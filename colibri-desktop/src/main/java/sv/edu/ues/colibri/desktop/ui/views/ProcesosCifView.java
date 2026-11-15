package sv.edu.ues.colibri.desktop.ui.views;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.application.configuracion.ParametrizacionService;
import sv.edu.ues.colibri.application.costos.CifService;
import sv.edu.ues.colibri.application.costos.CosteoPorProcesosService;
import sv.edu.ues.colibri.desktop.ui.ResponsiveLayouts;
import sv.edu.ues.colibri.desktop.ui.UiCatalogos;
import sv.edu.ues.colibri.desktop.ui.UiDialogs;
import sv.edu.ues.colibri.domain.calculation.CostosCalculator;
import sv.edu.ues.colibri.domain.model.Cuenta;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cédula auditable de costo por procesos y análisis de costos indirectos.
 * Los cálculos permanecen en los servicios de aplicación/dominio; esta vista
 * solamente reúne la evidencia del período y presenta sus resultados.
 */
public final class ProcesosCifView extends VBox {

    private final AppContext context;
    private final DatePicker fecha;

    private final TextField codigoProceso = campo("SAAS-01", "Identificador único del proceso para el período.");
    private final TextField unidadesIniciales = campo("0", "Unidades que estaban en proceso al inicio.");
    private final TextField avanceInicialMaterial = campo("0", "Proporción entre 0 y 1.");
    private final TextField avanceInicialConversion = campo("0", "Proporción entre 0 y 1.");
    private final TextField costoInicialMaterial = campo("0", "Costo inicial de material en USD.");
    private final TextField costoInicialConversion = campo("0", "Costo inicial de conversión en USD.");
    private final TextField unidadesIniciadas = campo("0", "Unidades agregadas al proceso durante el período.");
    private final TextField unidadesTerminadas = campo("0", "Unidades transferidas o terminadas en el período.");
    private final TextField unidadesFinales = campo("0", "Unidades que permanecen en proceso al cierre.");
    private final TextField avanceFinalMaterial = campo("0", "Proporción entre 0 y 1.");
    private final TextField avanceFinalConversion = campo("0", "Proporción entre 0 y 1.");
    private final TextField costoPeriodoMaterial = campo("0", "Costo del período de material en USD.");
    private final TextField costoPeriodoConversion = campo("0", "Costo del período de conversión en USD.");
    private final TextArea resultadoProceso = resultado("Resultado de la cédula de proceso");

    private ComboBox<ParametrizacionService.CentroCosto> centroCosto;
    private final TextField cifEstimado = campo("", "CIF presupuestado del período en USD.");
    private final TextField baseEstimada = campo("", "Base presupuestada positiva, por ejemplo horas productivas.");
    private final TextField baseReal = campo("", "Base real utilizada durante el período.");
    private final TextField cifReal = campo("", "CIF realmente incurrido en USD.");
    private final Label resultadoCif = new Label("Ingrese el presupuesto y la ejecución para obtener la tasa y la variación CIF.");
    private final TextField ordenCif = campo("", "Código de la orden que recibirá el CIF aplicado.");
    private final TextField baseAplicada = campo("", "Base real consumida por esta orden.");
    private final TextField descripcionCif = campo("", "Explicación de la aplicación a la orden.");
    private ComboBox<Cuenta> cuentaCifReal;
    private final Label resultadoContable = new Label("Después de guardar la cédula, distribuya la base real entre órdenes y cierre la variación.");

    public ProcesosCifView(AppContext context) {
        this.context = context;

        fecha = new DatePicker(context.periodos().periodoAbierto()
                .map(periodo -> periodo.getFechaFin()).orElse(LocalDate.now()));
        fecha.setAccessibleText("Fecha contable del proceso y análisis CIF");
        fecha.setAccessibleHelp("La cédula y el análisis se guardan para el período abierto que contiene esta fecha.");
        centroCosto = UiCatalogos.campoCentroCosto(context,
                "Centro de costo que acumula los costos indirectos de fabricación.");
        cuentaCifReal = UiCatalogos.campoCuenta(context, "520101",
                "Subcuenta 5201xx donde se acumula el CIF real; el selector sólo muestra cuentas de ese grupo.",
                cuenta -> cuenta.getCodigo() != null && cuenta.getCodigo().matches("5201\\d{2}"));
        cuentaCifReal.setPromptText("5201xx · busque la subcuenta");
        cuentaCifReal.setMinWidth(240);
        cuentaCifReal.setPrefWidth(280);

        Label titulo = new Label("Costeo por procesos y CIF");
        titulo.getStyleClass().add("view-title");
        Label ayuda = new Label("Registre la cédula física del servicio recurrente y el análisis de costos "
                + "indirectos de fabricación (CIF). Los avances se expresan de 0 a 1; PEPS "
                + "(primero en entrar, primero en salir) es la política informativa y promedio/UEPS "
                + "(último en entrar, primero en salir) quedan como comparación.");
        ayuda.setWrapText(true);
        ayuda.getStyleClass().add("view-subtitle");

        getChildren().addAll(titulo, ayuda, tarjetaProceso(), tarjetaCif());
    }

    private VBox tarjetaProceso() {
        Label subtitulo = encabezado("Cédula de costo por procesos");
        Label nota = new Label("La conciliación física debe cumplir: inicial + iniciadas − final = terminadas.");
        nota.setWrapText(true);
        nota.getStyleClass().add("section-note");

        Button cargar = new Button("Cargar cédula del período");
        cargar.getStyleClass().add("secondary-button");
        cargar.setAccessibleHelp("Recupera la cédula previamente guardada para el proceso y período indicados.");
        cargar.setOnAction(evento -> cargarProceso());

        Button guardar = new Button("Guardar cédula y calcular");
        guardar.getStyleClass().add("primary-button");
        guardar.setAccessibleHelp("Actualiza la cédula del proceso para el período abierto y muestra los tres métodos de costeo.");
        guardar.setOnAction(evento -> guardarProceso());

        VBox card = new VBox(10,
                subtitulo,
                nota,
                ResponsiveLayouts.row(ResponsiveLayouts.field("Fecha contable", fecha),
                        ResponsiveLayouts.field("Código de proceso", codigoProceso), cargar, guardar),
                encabezado("Balance físico"),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Unidades iniciales", unidadesIniciales),
                        ResponsiveLayouts.field("Unidades iniciadas", unidadesIniciadas),
                        ResponsiveLayouts.field("Unidades terminadas", unidadesTerminadas),
                        ResponsiveLayouts.field("Unidades finales", unidadesFinales)),
                encabezado("Material"),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Avance inicial material (0–1)", avanceInicialMaterial),
                        ResponsiveLayouts.field("Costo inicial material (USD)", costoInicialMaterial),
                        ResponsiveLayouts.field("Avance final material (0–1)", avanceFinalMaterial),
                        ResponsiveLayouts.field("Costo período material (USD)", costoPeriodoMaterial)),
                encabezado("Conversión"),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Avance inicial conversión (0–1)", avanceInicialConversion),
                        ResponsiveLayouts.field("Costo inicial conversión (USD)", costoInicialConversion),
                        ResponsiveLayouts.field("Avance final conversión (0–1)", avanceFinalConversion),
                        ResponsiveLayouts.field("Costo período conversión (USD)", costoPeriodoConversion)),
                encabezado("Resultado calculado"), resultadoProceso);
        card.getStyleClass().add("form-card");
        return card;
    }

    private VBox tarjetaCif() {
        Label subtitulo = encabezado("CIF: presupuesto, aplicación y variación");
        Label nota = new Label("Tasa predeterminada = CIF estimado ÷ base estimada; "
                + "variación = CIF real − CIF aplicado. Guarde primero la cédula, luego distribuya su base real entre las órdenes.");
        nota.setWrapText(true);
        nota.getStyleClass().add("section-note");

        Button guardar = new Button("Guardar análisis CIF");
        guardar.getStyleClass().add("primary-button");
        guardar.setAccessibleHelp("Guarda los importes CIF del período y muestra la tasa, aplicación y variación.");
        guardar.setOnAction(evento -> guardarCif());

        Button aplicar = new Button("Aplicar CIF a orden");
        aplicar.getStyleClass().add("primary-button");
        aplicar.setAccessibleHelp("Registra la aplicación de CIF de la orden seleccionada y crea su partida trazable.");
        aplicar.setOnAction(evento -> aplicarCifAOrden());

        Button cerrar = new Button("Cerrar variación CIF");
        cerrar.getStyleClass().add("secondary-button");
        cerrar.setAccessibleHelp("Cancela los controles CIF y lleva la sub o sobreaplicación a costo de proyectos.");
        cerrar.setOnAction(evento -> cerrarVariacionCif());

        resultadoCif.setWrapText(true);
        resultadoCif.setMaxWidth(Double.MAX_VALUE);
        resultadoCif.setAccessibleText("Resultado del análisis de costos indirectos de fabricación");
        resultadoCif.getStyleClass().addAll("section-note", "result-panel");
        resultadoContable.setWrapText(true);
        resultadoContable.setMaxWidth(Double.MAX_VALUE);
        resultadoContable.setAccessibleText("Resultado de la aplicación y cierre contable del CIF");
        resultadoContable.getStyleClass().addAll("section-note", "result-panel");

        VBox card = new VBox(10,
                subtitulo,
                nota,
                ResponsiveLayouts.row(ResponsiveLayouts.field("Centro de costo", centroCosto),
                        ResponsiveLayouts.field("CIF estimado (USD)", cifEstimado),
                        ResponsiveLayouts.field("Base estimada", baseEstimada),
                        ResponsiveLayouts.field("Base real", baseReal),
                        ResponsiveLayouts.field("CIF real (USD)", cifReal), guardar),
                resultadoCif,
                encabezado("Aplicación a orden y cierre de la variación"),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Código de orden", ordenCif),
                        ResponsiveLayouts.field("Base aplicada", baseAplicada),
                        ResponsiveLayouts.field("Descripción", descripcionCif), aplicar),
                ResponsiveLayouts.row(ResponsiveLayouts.field("Cuenta CIF real (5201xx)", cuentaCifReal), cerrar),
                resultadoContable);
        card.getStyleClass().add("form-card");
        return card;
    }

    private void guardarProceso() {
        try {
            CosteoPorProcesosService.ProcesoEntrada entrada = entradaProceso();
            CosteoPorProcesosService.ResultadoProceso calculado = context.procesos().guardar(entrada);
            mostrarProceso(calculado);
            UiDialogs.info("Cédula guardada", "El proceso " + entrada.codigoProceso()
                    + " quedó actualizado para el período seleccionado.");
        } catch (Exception ex) {
            UiDialogs.error(mensaje(ex, "No se pudo guardar la cédula de proceso."));
        }
    }

    private void cargarProceso() {
        try {
            context.procesos().buscar(texto(codigoProceso, "código de proceso"), fechaObligatoria())
                    .ifPresentOrElse(this::aplicarProceso,
                            () -> UiDialogs.info("Cédula no encontrada",
                                    "No existe una cédula guardada para este proceso y período."));
        } catch (Exception ex) {
            UiDialogs.error(mensaje(ex, "No se pudo cargar la cédula de proceso."));
        }
    }

    private void aplicarProceso(CosteoPorProcesosService.ProcesoEntrada entrada) {
        codigoProceso.setText(entrada.codigoProceso());
        unidadesIniciales.setText(numero(entrada.unidadesIniciales()));
        avanceInicialMaterial.setText(numero(entrada.avanceInicialMaterial()));
        avanceInicialConversion.setText(numero(entrada.avanceInicialConversion()));
        costoInicialMaterial.setText(numero(entrada.costoInicialMaterial()));
        costoInicialConversion.setText(numero(entrada.costoInicialConversion()));
        unidadesIniciadas.setText(numero(entrada.unidadesIniciadas()));
        unidadesTerminadas.setText(numero(entrada.unidadesTerminadas()));
        unidadesFinales.setText(numero(entrada.unidadesFinales()));
        avanceFinalMaterial.setText(numero(entrada.avanceFinalMaterial()));
        avanceFinalConversion.setText(numero(entrada.avanceFinalConversion()));
        costoPeriodoMaterial.setText(numero(entrada.costoPeriodoMaterial()));
        costoPeriodoConversion.setText(numero(entrada.costoPeriodoConversion()));
        mostrarProceso(CosteoPorProcesosService.calcular(entrada));
    }

    private void guardarCif() {
        try {
            CostosCalculator.AnalisisCif analisis = context.cif().guardar(new CifService.CifEntrada(
                    fechaObligatoria(), centroNormalizado(), decimal(cifEstimado, "CIF estimado"),
                    decimal(baseEstimada, "base estimada"), decimal(baseReal, "base real"),
                    decimal(cifReal, "CIF real")));
            resultadoCif.setText("Centro %s · Tasa predeterminada: $%s por unidad de base · "
                            + "CIF aplicado: $%s · CIF real: $%s · Variación: $%s (%s)."
                    .formatted(centroNormalizado(), numero(analisis.tasaPredeterminada()),
                            dinero(analisis.cifAplicados()), dinero(analisis.cifReales()),
                            dinero(analisis.variacion()), etiquetaCif(analisis.estado())));
            UiDialogs.info("Análisis CIF guardado", "La evidencia presupuestaria y real quedó actualizada.");
        } catch (Exception ex) {
            UiDialogs.error(mensaje(ex, "No se pudo guardar el análisis CIF."));
        }
    }

    private void aplicarCifAOrden() {
        try {
            CifService.AplicacionOrden aplicacion = context.cif().aplicarAOrden(
                    new CifService.AplicacionOrdenEntrada(fechaObligatoria(), centroNormalizado(),
                            texto(ordenCif, "código de orden"), decimal(baseAplicada, "base aplicada"),
                            descripcionCif.getText()));
            resultadoContable.setText("Orden %s · Base aplicada: %s · Tasa: $%s · CIF aplicado: $%s · Folio %s."
                    .formatted(aplicacion.codigoOrden(), numero(aplicacion.baseAplicada()),
                            numero(aplicacion.tasaPredeterminada()), dinero(aplicacion.importeAplicado()),
                            aplicacion.partida().getFolio()));
            UiDialogs.info("CIF aplicado", "La orden " + aplicacion.codigoOrden()
                    + " recibió $" + aplicacion.importeAplicado() + " de CIF. Folio "
                    + aplicacion.partida().getFolio());
        } catch (Exception ex) {
            UiDialogs.error(mensaje(ex, "No se pudo aplicar CIF a la orden."));
        }
    }

    private void cerrarVariacionCif() {
        try {
            if (!UiDialogs.confirmar("Cerrar variación CIF",
                    "Se cancelarán los controles CIF del centro y período seleccionados. ¿Desea continuar?")) {
                return;
            }
            String cuenta = UiCatalogos.codigoCuenta(cuentaCifReal);
            if (cuenta.isBlank()) {
                throw new IllegalArgumentException("Ingrese la cuenta CIF real.");
            }
            CifService.CierreVariacion cierre = context.cif().cerrarVariacion(fechaObligatoria(),
                    centroNormalizado(), cuenta);
            resultadoContable.setText("Cierre CIF · Centro %s · Real: $%s · Aplicado: $%s · Variación: $%s (%s) · Folio %s."
                    .formatted(cierre.centroCosto(), dinero(cierre.cifReal()), dinero(cierre.cifAplicadoContable()),
                            dinero(cierre.variacion()), etiquetaCif(cierre.estado()), cierre.partida().getFolio()));
            UiDialogs.info("Variación CIF cerrada", "Folio " + cierre.partida().getFolio()
                    + ". Variación " + etiquetaCif(cierre.estado()) + ": $" + cierre.variacion());
        } catch (Exception ex) {
            UiDialogs.error(mensaje(ex, "No se pudo cerrar la variación CIF."));
        }
    }

    private CosteoPorProcesosService.ProcesoEntrada entradaProceso() {
        return new CosteoPorProcesosService.ProcesoEntrada(
                texto(codigoProceso, "código de proceso"), fechaObligatoria(),
                decimal(unidadesIniciales, "unidades iniciales"),
                decimal(avanceInicialMaterial, "avance inicial de material"),
                decimal(avanceInicialConversion, "avance inicial de conversión"),
                decimal(costoInicialMaterial, "costo inicial de material"),
                decimal(costoInicialConversion, "costo inicial de conversión"),
                decimal(unidadesIniciadas, "unidades iniciadas"),
                decimal(unidadesTerminadas, "unidades terminadas"),
                decimal(unidadesFinales, "unidades finales"),
                decimal(avanceFinalMaterial, "avance final de material"),
                decimal(avanceFinalConversion, "avance final de conversión"),
                decimal(costoPeriodoMaterial, "costo del período de material"),
                decimal(costoPeriodoConversion, "costo del período de conversión"));
    }

    private void mostrarProceso(CosteoPorProcesosService.ResultadoProceso resultado) {
        CostosCalculator.BalanceFisico balance = resultado.balanceFisico();
        resultadoProceso.setText("BALANCE FÍSICO\nInicial: %s  +  Iniciadas: %s  −  Final: %s  =  Transferidas: %s%n%n"
                        + "%s%n%n%s%n%n"
                        + "TOTAL PEPS · Inventario final: $%s · Transferido: $%s%n"
                        + "TOTAL PROMEDIO · Inventario final: $%s · Transferido: $%s%n"
                        + "UEPS se muestra sólo como comparación gerencial; no es política financiera de Colibrí."
                .formatted(numero(balance.inventarioInicial()), numero(balance.unidadesIniciadas()),
                        numero(balance.inventarioFinal()), numero(balance.unidadesTransferidas()),
                        resumenElemento("MATERIAL", resultado.material()),
                        resumenElemento("CONVERSIÓN", resultado.conversion()),
                        dinero(resultado.costoInventarioFinalPeps()), dinero(resultado.costoTransferidoPeps()),
                        dinero(resultado.costoInventarioFinalPromedio()), dinero(resultado.costoTransferidoPromedio())));
    }

    private static String resumenElemento(String nombre, CosteoPorProcesosService.ElementoProceso elemento) {
        CostosCalculator.UnidadesEquivalentes ue = elemento.unidadesEquivalentes();
        return "%s · UE final: %s · UE PEPS: %s · UE promedio: %s%n"
                        + "PEPS: $%s/u · final $%s · transferido $%s%n"
                        + "Promedio: $%s/u · final $%s · transferido $%s%n"
                        + "UEPS (comparativo): $%s/u · final $%s · transferido $%s"
                .formatted(nombre, numero(ue.simpleFinal()), numero(ue.terminadasPeps()),
                        numero(ue.promedioPonderado()),
                        dinero(elemento.peps().costoUnitario()), dinero(elemento.peps().costoInventarioFinal()),
                        dinero(elemento.peps().costoUnidadesTransferidas()),
                        dinero(elemento.promedio().costoUnitario()), dinero(elemento.promedio().costoInventarioFinal()),
                        dinero(elemento.promedio().costoUnidadesTransferidas()),
                        dinero(elemento.ueps().costoUnitario()), dinero(elemento.ueps().costoInventarioFinal()),
                        dinero(elemento.ueps().costoUnidadesTransferidas()));
    }

    private LocalDate fechaObligatoria() {
        if (fecha.getValue() == null) {
            throw new IllegalArgumentException("Seleccione la fecha contable.");
        }
        return fecha.getValue();
    }

    private String centroNormalizado() {
        return UiCatalogos.codigoCentroCosto(centroCosto, "GENERAL");
    }

    private static TextField campo(String inicial, String ayuda) {
        TextField campo = new TextField(inicial);
        campo.setAccessibleHelp(ayuda);
        Tooltip.install(campo, new Tooltip(ayuda));
        return campo;
    }

    private static TextArea resultado(String textoAccesible) {
        TextArea area = new TextArea();
        area.setEditable(false);
        area.setWrapText(true);
        // El estilo común de campos fija una altura mínima propia. Esta salida
        // necesita una regla específica porque contiene una cédula completa; de
        // otro modo JavaFX la reduce visualmente a una sola línea al recalcular
        // la página desplazable.
        area.setPrefRowCount(15);
        area.setPromptText("Los resultados calculados aparecerán aquí.");
        area.setAccessibleText(textoAccesible);
        area.setAccessibleHelp("Muestra la conciliación física, unidades equivalentes y asignación por método.");
        area.getStyleClass().add("process-result-output");
        return area;
    }

    private static Label encabezado(String texto) {
        Label titulo = new Label(texto);
        titulo.getStyleClass().add("section-heading");
        return titulo;
    }

    private static BigDecimal decimal(TextField campo, String etiqueta) {
        String texto = campo.getText() == null ? "" : campo.getText().trim();
        if (texto.isEmpty()) {
            throw new IllegalArgumentException("Ingrese " + etiqueta + ".");
        }
        try {
            return new BigDecimal(texto);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("El valor de " + etiqueta + " debe ser numérico.");
        }
    }

    private static String texto(TextField campo, String etiqueta) {
        String texto = campo.getText() == null ? "" : campo.getText().trim();
        if (texto.isEmpty()) {
            throw new IllegalArgumentException("Ingrese " + etiqueta + ".");
        }
        return texto;
    }

    private static String numero(BigDecimal valor) {
        return valor == null ? "0" : valor.stripTrailingZeros().toPlainString();
    }

    private static String dinero(BigDecimal valor) {
        return "$" + (valor == null ? "0.00" : valor.setScale(2).toPlainString());
    }

    private static String etiquetaCif(CostosCalculator.EstadoCif estado) {
        return switch (estado) {
            case EQUILIBRADO -> "equilibrado";
            case SUBAPLICADO -> "subaplicado";
            case SOBREAPLICADO -> "sobreaplicado";
        };
    }

    private static String mensaje(Exception ex, String alternativa) {
        return ex.getMessage() == null || ex.getMessage().isBlank() ? alternativa : ex.getMessage();
    }
}
