# Revisión UX por módulo — Colibrí

**Fecha:** 18 de noviembre de 2026  
**Estado:** actualizado tras las correcciones UX de esta sesión.  
**Método:** renderizado JavaFX con `colibri_test`; revisión de Contador y Soporte a 1360, 1080 y 660 px, más verificación posterior de las correcciones a 1360 y 1080 px.

## Resultado

La interfaz queda **apta para demostración técnica** dentro del alcance actual del sistema. Los módulos construyen sin errores, mantienen una jerarquía y acciones coherentes, y la cabecera ya no rompe sus etiquetas en anchos medio o compacto.

La revisión no sustituye los 380 casos manuales de `PLAN_DE_PRUEBAS.md`: no hubo interacción humana con diálogos, selectores de archivos ni operaciones irreversibles en esta sesión.

## Mejoras aplicadas y verificadas

### UX-01 — Cabecera adaptable

**Estado: corregido.**

- En ancho medio el menú lateral se contrae automáticamente, liberando espacio para el perfil, zoom, tema y cierre de sesión sin partir etiquetas.
- En ancho compacto los controles de sesión se agrupan en el botón textual **Opciones**. Conserva reducir/restablecer/aumentar zoom, cambiar tema y cerrar sesión; los atajos de teclado no cambian.
- Se ajustaron espaciados y tamaños de la cabecera media, manteniendo botones con texto completo y foco accesible.

Evidencia: a 1080 px la barra se conserva en una fila y a 660 px muestra `Opciones`, sin texto vertical ni una franja superior desproporcionada.

### UX-02 — Estado activo de navegación

**Estado: sin defecto de producto confirmado.**

La primera captura tomaba un fotograma intermedio de la transición de 140–220 ms: el contenido y el breadcrumb ya habían cambiado, mientras el resaltado lateral aún estaba animándose. La navegación lógica y el acordeón ya estaban cubiertos por pruebas automatizadas.

Se actualizó `SnapshotRunner` para esperar el estado final antes de capturar; así la evidencia futura no convierte una transición en un falso hallazgo.

### UX-03 — Legibilidad de ayudas y campos

**Estado: corregido en los casos observados.**

- Los formularios ahora reservan 200 px mínimos por campo y refluyen antes de comprimir etiquetas o prompts.
- Las ayudas de Parametrización, Ajustes y Procesos/CIF se expanden al ancho disponible y envuelven el texto.
- El campo JDBC de Entorno tiene una preferencia de ancho adecuada para la cadena de conexión.

Los valores excepcionalmente largos de tablas conservan desplazamiento horizontal, decisión intencional para no ocultar columnas contables.

### UX-04 — Densidad desigual de páginas informativas

**Estado: observación menor pendiente.**

Base de datos y Libros societarios siguen teniendo bastante espacio libre cuando no existen registros. No bloquea la operación; una mejora posterior podría añadir actividad reciente, requisitos o siguientes pasos.

## Revisión por módulo y apartado

### Shell común

- Navegación lateral, breadcrumb, período, búsqueda `Ctrl+K`, tema, zoom y sesión son descubribles y accesibles.
- La respuesta por ancho es consistente: barra expandida en escritorio, rail de iconos en ancho medio y utilidades agrupadas en compacto.

### Inicio

- **Aprobado.** El resumen del período, gráfica, KPIs y tarjetas ponen el estado financiero antes del detalle.

### Temas contables

- **Aprobado.** Los cuatro apartados conservan el patrón maestro–detalle, acción contextual y enlace al módulo operativo relacionado.

### Empresa

- **Perfil corporativo: aprobado.** Los datos fiscales, firmantes y guardado mantienen agrupación clara.

### Parametrización

- **Catálogo de cuentas: aprobado.** Filtro, tabla y alta/edición/cambio de estado son distinguibles; los campos refluyen para conservar etiquetas.
- **Mapeo de módulos: aprobado.** La relación evento–Debe–Haber es visible y las descripciones ya no obligan a comprimir el formulario.
- **Períodos contables: aprobado.** Abrir, auditoría y cierre se presentan como acciones distintas y comprensibles.
- **Parámetros fiscales: aprobado.** Alícuotas y cuentas asociadas se agrupan de forma operable.
- **Políticas y secuenciales: aprobado.** Búsqueda y tabla permiten localizar parámetros sin romper la jerarquía visual.
- **Centros de costo: aprobado.** Código, nombre, uso y estado siguen un modelo uniforme.

### Registros

- **Documentos, terceros e IVA; Libro diario; Plantillas; Planilla; Ajustes y cierre: aprobados.** El orden de datos, revisión y acción principal es coherente; los textos de ayuda relevantes envuelven en lugar de truncarse.

### Inventario

- **Digitales; Kárdex; Servicios / Accesos: aprobados.** Las acciones muestran sus consecuencias y las tablas anchas mantienen scroll horizontal para auditoría.

### Costos

- **A la medida; Licencias descargables; Suscripción SaaS; Procesos y CIF; Estimación y políticas: aprobados.** La secuencia de costos es visible; Procesos/CIF conserva sus fórmulas y ayudas legibles tras el ajuste.

### Reportes

- **Obligatorios y Auxiliares: aprobados.** Formato, alcance, filtros y exportación siguen el mismo patrón.
- **Societarios: aprobado con observación UX-04.** La información es clara; queda margen de mejora en densidad cuando la pantalla está vacía.

### Soporte técnico

- **Usuarios; Base de datos; Entorno; Monitoreo; Accesos: aprobados.** Roles, respaldo, configuración, registros y recuperación de acceso son distinguibles. Entorno dispone de mayor ancho para el JDBC.

## Evidencia y validación

- Cobertura inicial: 99 capturas (28 apartados de Contador y 5 de Soporte, cada uno a 1360, 1080 y 660 px).
- Evidencia de la corrección: `colibri-desktop/target/ux-snapshots-20261118-corrected` y `colibri-desktop/target/ux-snapshots-20261118-final`.
- `SnapshotRunner` es una utilidad de prueba y no forma parte de la distribución. Ahora fija el final de la entrada visual y espera la transición de navegación antes de generar un PNG.
- Compilación posterior: `mvnw.cmd -pl colibri-desktop -am -DskipTests compile` aprobada.
- Regresión posterior: `mvnw.cmd test` aprobada con **77 pruebas, 0 fallos, 0 errores y 0 omitidas**.

## Límite de aceptación

No se declara una validación manual total. Para cerrarla hacen falta las evidencias interactivas descritas en `PLAN_DE_PRUEBAS.md`, especialmente diálogos, selección de archivos, persistencia tras reinicio y operaciones sensibles. La advertencia previa de Log4j sin proveedor también continúa como hallazgo técnico no bloqueante para producción.
