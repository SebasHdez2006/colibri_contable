package sv.edu.ues.colibri.desktop.ui;

import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Tooltip;
import javafx.util.StringConverter;
import sv.edu.ues.colibri.application.AppContext;
import sv.edu.ues.colibri.application.configuracion.ParametrizacionService;
import sv.edu.ues.colibri.domain.model.Cuenta;
import sv.edu.ues.colibri.domain.model.EstadoCuenta;
import sv.edu.ues.colibri.domain.model.NaturalezaCuenta;

import java.util.Comparator;
import java.util.List;

/**
 * Selectores de catálogo contable. Los combos son editables para conservar la
 * captura libre de códigos, pero siempre muestran "código · nombre" para que el
 * usuario reconozca el significado de cada clave.
 */
public final class UiCatalogos {

    private UiCatalogos() {
    }

    /**
     * Combo editable de cuentas contables con denominación visible.
     *
     * @param context      contexto de aplicación
     * @param valorInicial código preseleccionado (puede ser {@code null})
     * @param ayuda        texto de ayuda para tooltip y accesibilidad
     * @return selector listo para insertar en un formulario
     */
    public static ComboBox<Cuenta> campoCuenta(AppContext context, String valorInicial, String ayuda) {
        return campoCuenta(context, valorInicial, ayuda, cuenta -> true);
    }

    /**
     * Combo editable de cuentas contables limitado a las que cumplen el filtro.
     * El texto libre sigue permitiendo capturar un código válido que aún no
     * exista en el catálogo; el filtro sólo reduce las sugerencias mostradas.
     *
     * @param context      contexto de aplicación
     * @param valorInicial código preseleccionado (puede ser {@code null})
     * @param ayuda        texto de ayuda para tooltip y accesibilidad
     * @param filtro       predicado que decide qué cuentas se ofrecen
     * @return selector listo para insertar en un formulario
     */
    public static ComboBox<Cuenta> campoCuenta(AppContext context, String valorInicial,
                                               String ayuda, java.util.function.Predicate<Cuenta> filtro) {
        List<Cuenta> cuentas;
        try {
            cuentas = context.catalogo().listar().stream()
                    .filter(filtro)
                    .sorted(Comparator.comparing(Cuenta::getCodigo))
                    .toList();
        } catch (Exception ex) {
            cuentas = List.of();
        }
        ComboBox<Cuenta> combo = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(cuentas));
        combo.setEditable(true);
        combo.setPromptText("Código o busque la cuenta");
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Cuenta value) {
                return value == null ? "" : value.getCodigo() + " · " + value.getNombre();
            }

            @Override
            public Cuenta fromString(String text) {
                return null;
            }
        });
        if (valorInicial != null && !valorInicial.isBlank()) {
            String codigo = valorInicial.trim();
            combo.setValue(cuentas.stream()
                    .filter(c -> c.getCodigo().equalsIgnoreCase(codigo))
                    .findFirst().orElse(null));
            if (combo.getValue() == null) {
                combo.getEditor().setText(codigo);
            }
        }
        ayuda(combo, ayuda);
        return combo;
    }

    /**
     * Preselecciona una cuenta en el combo por código; si no existe en el
     * catálogo, deja el código en el editor.
     *
     * @param combo  selector de cuenta
     * @param codigo código a seleccionar (puede ser {@code null})
     */
    public static void seleccionarCuenta(ComboBox<Cuenta> combo, String codigo) {
        if (combo == null || codigo == null || codigo.isBlank()) {
            if (combo != null) {
                combo.setValue(null);
                combo.getEditor().clear();
            }
            return;
        }
        String limpio = codigo.trim();
        combo.setValue(combo.getItems().stream()
                .filter(c -> c.getCodigo().equalsIgnoreCase(limpio))
                .findFirst().orElse(null));
        if (combo.getValue() == null) {
            combo.getEditor().setText(limpio);
        }
    }

    /**
     * Texto del código de cuenta ingresado o seleccionado, sin el nombre.
     *
     * @param combo selector de cuenta
     * @return código de cuenta o cadena vacía
     */
    public static String codigoCuenta(ComboBox<Cuenta> combo) {
        if (combo == null) {
            return "";
        }
        Cuenta valor = combo.getValue();
        if (valor != null) {
            return valor.getCodigo();
        }
        String texto = combo.getEditor().getText();
        if (texto == null || texto.isBlank()) {
            return "";
        }
        return texto.trim().split("\\s+")[0];
    }

    /**
     * Combo editable de centros de costo con denominación visible.
     *
     * @param context contexto de aplicación
     * @param ayuda   texto de ayuda para tooltip y accesibilidad
     * @return selector listo para insertar en un formulario
     */
    public static ComboBox<ParametrizacionService.CentroCosto> campoCentroCosto(
            AppContext context, String ayuda) {
        List<ParametrizacionService.CentroCosto> centros;
        try {
            centros = context.parametrizacion().listarCentros().stream()
                    .filter(ParametrizacionService.CentroCosto::activo)
                    .sorted(Comparator.comparing(ParametrizacionService.CentroCosto::codigo))
                    .toList();
        } catch (Exception ex) {
            centros = List.of();
        }
        ComboBox<ParametrizacionService.CentroCosto> combo =
                new ComboBox<>(javafx.collections.FXCollections.observableArrayList(centros));
        combo.setEditable(true);
        combo.setPromptText("Seleccione o escriba el centro de costo");
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(ParametrizacionService.CentroCosto value) {
                return value == null ? "" : value.codigo() + " · " + value.nombre();
            }

            @Override
            public ParametrizacionService.CentroCosto fromString(String text) {
                return null;
            }
        });
        if (!centros.isEmpty()) {
            combo.setValue(centros.get(0));
        }
        ayuda(combo, ayuda);
        return combo;
    }

    /**
     * Código de centro de costo ingresado o seleccionado.
     *
     * @param combo selector de centro de costo
     * @param porDefecto valor cuando no hay selección ni texto
     * @return código de centro de costo
     */
    public static String codigoCentroCosto(
            ComboBox<ParametrizacionService.CentroCosto> combo, String porDefecto) {
        if (combo == null) {
            return porDefecto;
        }
        ParametrizacionService.CentroCosto valor = combo.getValue();
        if (valor != null) {
            return valor.codigo();
        }
        String texto = combo.getEditor().getText();
        if (texto == null || texto.isBlank()) {
            return porDefecto;
        }
        return texto.trim().split("\\s+")[0];
    }

    /**
     * Converter que muestra el estado de cuenta en español.
     *
     * @return converter para {@link ComboBox} de {@link EstadoCuenta}
     */
    public static StringConverter<EstadoCuenta> converterEstadoCuenta() {
        return new StringConverter<>() {
            @Override public String toString(EstadoCuenta value) {
                if (value == null) {
                    return "";
                }
                return switch (value) {
                    case ACTIVA -> "Activa";
                    case DESACTIVADA -> "Desactivada";
                    case BLOQUEADA -> "Bloqueada";
                };
            }
            @Override public EstadoCuenta fromString(String text) {
                if (text == null) {
                    return null;
                }
                return switch (text) {
                    case "Activa" -> EstadoCuenta.ACTIVA;
                    case "Desactivada" -> EstadoCuenta.DESACTIVADA;
                    case "Bloqueada" -> EstadoCuenta.BLOQUEADA;
                    default -> null;
                };
            }
        };
    }

    /**
     * Converter que muestra la naturaleza de cuenta en español.
     *
     * @return converter para {@link ComboBox} de {@link NaturalezaCuenta}
     */
    public static StringConverter<NaturalezaCuenta> converterNaturalezaCuenta() {
        return new StringConverter<>() {
            @Override public String toString(NaturalezaCuenta value) {
                if (value == null) {
                    return "";
                }
                return switch (value) {
                    case DEUDORA -> "Deudora";
                    case ACREEDORA -> "Acreedora";
                    case AMBAS -> "Deudora y acreedora";
                };
            }
            @Override public NaturalezaCuenta fromString(String text) {
                if (text == null) {
                    return null;
                }
                return switch (text) {
                    case "Deudora" -> NaturalezaCuenta.DEUDORA;
                    case "Acreedora" -> NaturalezaCuenta.ACREEDORA;
                    case "Deudora y acreedora", "Ambas" -> NaturalezaCuenta.AMBAS;
                    default -> null;
                };
            }
        };
    }

    /**
     * Asocia tooltip visible y ayuda accesible a un control.
     *
     * @param nodo  control
     * @param ayuda texto de ayuda
     */
    public static void ayuda(Node nodo, String ayuda) {
        if (nodo == null || ayuda == null || ayuda.isBlank()) {
            return;
        }
        Tooltip.install(nodo, new Tooltip(ayuda));
        if (nodo instanceof javafx.scene.control.Control control) {
            control.setAccessibleHelp(ayuda);
        }
    }
}
