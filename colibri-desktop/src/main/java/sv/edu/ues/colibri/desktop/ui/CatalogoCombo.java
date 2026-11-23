package sv.edu.ues.colibri.desktop.ui;

import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * ComboBox editable con filtrado incremental sobre un catálogo cerrado.
 * <p>
 * El usuario escribe para filtrar por código o denominación; al confirmar
 * (Enter o pérdida de foco) un texto que coincide con la clave de una opción
 * la selecciona. Si el texto no coincide con ninguna opción se conserva como
 * valor libre, de modo que la capa de dominio siga siendo la validación
 * final.
 *
 * @param <T> tipo de las opciones del catálogo
 */
public final class CatalogoCombo<T> extends ComboBox<T> {

    private static final int MAX_SUGERENCIAS = 500;

    private final List<T> todos;
    private final Function<T, String> clave;
    private final Function<String, List<T>> buscador;
    private final StringConverter<T> converter;
    private boolean sincronizando;
    private Runnable alConfirmar;

    /**
     * Crea el combo de catálogo.
     *
     * @param items catálogo completo de opciones
     * @param converter conversor de visualización de cada opción
     * @param clave extractor de la clave persistible de cada opción
     * @param buscador función que filtra el catálogo según el texto escrito
     */
    public CatalogoCombo(List<T> items, StringConverter<T> converter,
                         Function<T, String> clave, Function<String, List<T>> buscador) {
        super(FXCollections.observableArrayList());
        this.todos = List.copyOf(items);
        this.converter = Objects.requireNonNull(converter);
        this.clave = Objects.requireNonNull(clave);
        this.buscador = Objects.requireNonNull(buscador);
        setEditable(true);
        setConverter(new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? null : converter.toString(value);
            }

            @Override
            public T fromString(String text) {
                return resolverObjeto(text);
            }
        });
        TextField editor = getEditor();
        editor.textProperty().addListener((obs, anterior, nuevo) -> {
            if (sincronizando) {
                return;
            }
            T seleccion = getValue();
            if (seleccion != null && Objects.equals(nuevo, converter.toString(seleccion))) {
                return;
            }
            aplicarFiltro(nuevo == null ? "" : nuevo.trim());
        });
        editor.setOnAction(e -> resolverEscrito());
        editor.focusedProperty().addListener((obs, anterior, tieneFoco) -> {
            if (!tieneFoco) {
                resolverEscrito();
            }
        });
        aplicarFiltro("");
    }

    private void aplicarFiltro(String texto) {
        List<T> filtrados = buscador.apply(texto);
        if (filtrados.size() > MAX_SUGERENCIAS) {
            filtrados = filtrados.subList(0, MAX_SUGERENCIAS);
        }
        getItems().setAll(filtrados);
        if (texto.isEmpty()) {
            return;
        }
        T encontrado = resolverObjeto(texto);
        if (encontrado != null) {
            setValue(encontrado);
        }
    }

    /**
     * Resuelve el objeto detrás de un texto del editor: primero por la clave
     * persistible y luego por la representación visible completa («código —
     * denominación»). JavaFX invoca {@code fromString} al confirmar la edición
     * (Enter, {@code commitValue} y pérdida de foco del combo); sin la segunda
     * vía, el texto que el propio control escribe tras elegir una opción del
     * popup no lograba reconstruir la selección y el valor se perdía al salir
     * del campo.
     */
    private T resolverObjeto(String text) {
        String buscado = text == null ? "" : text.trim();
        if (buscado.isEmpty()) {
            return null;
        }
        T porClave = todos.stream()
                .filter(t -> {
                    String k = clave.apply(t);
                    return k != null && k.equalsIgnoreCase(buscado);
                })
                .findFirst().orElse(null);
        if (porClave != null) {
            return porClave;
        }
        return todos.stream()
                .filter(t -> {
                    String texto = converter.toString(t);
                    return texto != null && texto.equalsIgnoreCase(buscado);
                })
                .findFirst().orElse(null);
    }

    private void resolverEscrito() {
        String texto = textoEditor();
        if (!texto.isEmpty()) {
            T encontrado = resolverObjeto(texto);
            if (encontrado != null) {
                setValue(encontrado);
            }
        }
        if (alConfirmar != null) {
            alConfirmar.run();
        }
    }

    /**
     * Registra una acción invocada cada vez que el texto escrito se confirma
     * (Enter o pérdida de foco). La usan las celdas editables de la tabla.
     *
     * @param alConfirmar acción a ejecutar tras resolver la selección
     */
    public void setAlConfirmar(Runnable alConfirmar) {
        this.alConfirmar = alConfirmar;
    }

    /**
     * Devuelve la clave comprometida: la de la opción seleccionada cuando el
     * texto visible coincide con ella, o el texto escrito en caso contrario.
     *
     * @return código de catálogo o texto libre, nunca {@code null}
     */
    public String codigo() {
        T valor = getValue();
        if (valor != null && Objects.equals(textoEditor(), converter.toString(valor))) {
            return clave.apply(valor);
        }
        return textoEditor();
    }

    /**
     * Selecciona una opción por su clave. Si ninguna coincide, deja el texto
     * como valor libre para que el dominio lo valide.
     *
     * @param codigo clave a seleccionar o texto libre
     */
    public void seleccionarCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            limpiar();
            return;
        }
        String buscado = codigo.trim();
        T encontrado = resolverObjeto(buscado);
        if (encontrado != null) {
            setValue(encontrado);
        } else {
            setValue(null);
            sincronizando = true;
            try {
                getEditor().setText(buscado);
            } finally {
                sincronizando = false;
            }
        }
    }

    /** Limpia la selección y el texto escrito. */
    public void limpiar() {
        setValue(null);
        sincronizando = true;
        try {
            getEditor().clear();
        } finally {
            sincronizando = false;
        }
        aplicarFiltro("");
    }

    private String textoEditor() {
        String texto = getEditor().getText();
        return texto == null ? "" : texto.trim();
    }
}
