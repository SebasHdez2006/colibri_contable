package sv.edu.ues.colibri.desktop.ui;

import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

/**
 * Iconos vectoriales compartidos por la interfaz. Se mantienen como SVGPath para
 * que escalen de forma nítida y hereden el color de cada variante de botón.
 */
public final class BrandIcon {

    private BrandIcon() {
    }

    /**
     * Conjunto de símbolos SVG reutilizables en la interfaz.
     */
    public enum Symbol {
        HOME("M3 11.5 12 4l9 7.5v8a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"),
        BUILDING("M4 21V4h10v4h6v13h-6v-3h-4v3zm3-13h2V6H7zm0 4h2v-2H7zm0 4h2v-2H7zm6-4h2v-2h-2zm0 4h2v-2h-2zm4 0h2v-2h-2zm0-4h2v-2h-2z"),
        CATALOG("M4 3h14a2 2 0 0 1 2 2v15H6a2 2 0 0 1-2-2zm3 3v11h10V6zm2 2h6v2H9zm0 4h6v2H9z"),
        DOCUMENT("M6 2h8l5 5v15H6zm8 2.5V8h3.5zM9 12h6v-2H9zm0 4h6v-2H9zm0 4h6v-2H9z"),
        JOURNAL("M4 3h15a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H4zm4 3H6v12h2zm3 1h6V5h-6zm0 4h6V9h-6zm0 4h6v-2h-6zm0 4h6v-2h-6z"),
        INVENTORY("M12 2 3 6.5v11L12 22l9-4.5v-11zm0 2.2 5.7 2.85L12 9.9 6.3 7.05zM5 8.6l6 3v7.85l-6-3zm14 0v7.85l-6 3V11.6z"),
        ADJUST("M4 6h10v2H4zm14-2h2v6h-2zM10 11h10v2H10zm-6-2h2v6H4zm0 7h10v2H4zm14-2h2v6h-2z"),
        REPORT("M4 21V10h4v11zm6 0V3h4v18zm6 0V7h4v14z"),
        USERS("M16 11a4 4 0 1 0-3.9-5 5 5 0 0 1 0 6A7 7 0 0 1 18 19v2h4v-2a8 8 0 0 0-6-8zm-9 1a4 4 0 1 0 0-8 4 4 0 0 0 0 8zm0 2c-4.4 0-7 2.2-7 5v2h14v-2c0-2.8-2.6-5-7-5z"),
        BACKUP("M12 3a9 9 0 0 0-8.5 6H1l3.5 4L8 9H5.7A7 7 0 1 1 6 16l-1.7 1A9 9 0 1 0 12 3zm-1 5v5l4.3 2.5 1-1.7-3.3-1.9V8z"),
        LOGOUT("M10 3H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h6v-2H4V5h6zm4.6 4.6L16 9l-2 2H7v2h7l2 2-1.4 1.4L20 12z"),
        LOGIN("M10 3H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h6v-2H4V5h6zm4.6 4.6L20 12l-5.4 4.4L13.2 15l2-2H8v-2h7.2l-2-2z"),
        SAVE("M4 3h13l3 3v15H4zm3 2v5h8V5zm0 9v5h10v-5z"),
        EDIT("M4 16.5V20h3.5L18 9.5 14.5 6zm16-9a1 1 0 0 0 0-1.4L17.9 4a1 1 0 0 0-1.4 0L15 5.5 18.5 9z"),
        SWITCH("M7 7h11l-3-3 1.4-1.4L21.8 8l-5.4 5.4L15 12l3-3H7zm10 10H6l3 3-1.4 1.4L2.2 16l5.4-5.4L9 12l-3 3h11z"),
        PLUS("M11 4h2v7h7v2h-7v7h-2v-7H4v-2h7z"),
        MINUS("M4 11h16v2H4z"),
        CHECK("M9.2 18 3.5 12.3l1.8-1.8 3.9 3.9L18.7 5l1.8 1.8z"),
        CANCEL("M6.3 4.9 12 10.6l5.7-5.7 1.4 1.4-5.7 5.7 5.7 5.7-1.4 1.4-5.7-5.7-5.7 5.7-1.4-1.4 5.7-5.7-5.7-5.7z"),
        UNDO("M7.4 7H17a5 5 0 0 1 0 10h-3v-2h3a3 3 0 0 0 0-6H7.4l3.3 3.3-1.4 1.4L3.6 8l5.7-5.7 1.4 1.4z"),
        REFRESH("M17.7 6.3A8 8 0 0 0 4.6 9H2l4 4 4-4H6.7a6 6 0 1 1 .7 7.7L6 18.1A8 8 0 1 0 4.6 9h2a6 6 0 0 1 9.7-1.3L14 10h8V2z"),
        DOWNLOAD("M11 3h2v10.2l3.1-3.1 1.4 1.4-5.5 5.5-5.5-5.5 1.4-1.4 3.1 3.1zM4 19h16v2H4z"),
        PLAY("M8 5v14l11-7z"),
        MENU("M3 6h18v2H3zm0 5h18v2H3zm0 5h18v2H3z"),
        CHEVRON_LEFT("M15.4 7.4 14 6l-6 6 6 6 1.4-1.4L10.8 12z"),
        CHEVRON_RIGHT("M8.6 7.4 10 6l6 6-6 6-1.4-1.4L13.2 12z"),
        LOCK("M6 10V8a6 6 0 0 1 12 0v2h2v12H4V10zm2 0h8V8a4 4 0 0 0-8 0zm3 4v4h2v-4z"),
        KEY("M14 3a7 7 0 0 0-6.7 9L2 17.3V22h4v-2h3v-3h2.7A7 7 0 1 0 14 3zm0 2a5 5 0 1 1 0 10 5 5 0 0 1 0-10zm2 3a2 2 0 1 0 0 4 2 2 0 0 0 0-4z"),
        MONEY("M12 2v2c-3.3 0-6 1.8-6 4.5 0 3.1 2.8 4 6 4.7 2.4.5 4 1 4 2.3 0 1.2-1.4 2-4 2-2.4 0-4-1-4.4-2.3L5.2 16c.7 2.2 2.8 3.5 6.8 3.5V22h2v-2.5c3.6-.5 5-2.5 5-4.3 0-3.3-3.1-4.1-6.2-4.8C10.3 9.8 9 9.3 9 8.3c0-.9 1-1.8 3-1.8 2.2 0 3.4.8 4 1.8l2.3-1.3C17.4 5.2 15.8 4.3 14 4V2z"),
        SEARCH("M15.5 14h-.79l-.28-.27a6.5 6.5 0 1 0-.7.7l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0A4.5 4.5 0 1 1 14 9.5 4.5 4.5 0 0 1 9.5 14z");

        private final String path;

        Symbol(String path) {
            this.path = path;
        }
    }

    /**
     * Crea un contenedor JavaFX con el icono vectorial indicado.
     *
     * @param symbol símbolo a renderizar
     * @return nodo contenedor con el SVG escalado y listo para estilizar
     */
    public static StackPane create(Symbol symbol) {
        SVGPath shape = new SVGPath();
        shape.setContent(symbol.path);
        shape.getStyleClass().add("brand-icon-shape");
        shape.setScaleX(.72);
        shape.setScaleY(.72);

        StackPane holder = new StackPane(shape);
        holder.getStyleClass().add("brand-icon");
        holder.setMinSize(18, 18);
        holder.setPrefSize(18, 18);
        holder.setMaxSize(18, 18);
        holder.setMouseTransparent(true);
        return holder;
    }
}
