# Manual de uso — Colibrí

## Antes de comenzar

Requisitos para operar el sistema:

1. Inicie Colibrí y entre con un usuario autorizado.
2. Verifique que el chip de la esquina superior derecha indique un período **Abierto**. Si muestra “Sin período abierto”, cree o abra uno en **Parametrización → Períodos** antes de intentar registrar movimientos.
3. Configure el catálogo, los centros de costo y los perfiles de contabilización necesarios. El sistema mostrará una alerta cuando falte una selección o un importe no sea válido.
4. Use el control con icono de sol/luna, situado al final de los controles del pie izquierdo, para cambiar entre tema claro y oscuro. Ambos temas mantienen el mismo significado de los colores.

### Convenciones visuales

- El botón verde lima es la acción principal que guarda, contabiliza o confirma un proceso.
- El botón claro con borde azul/cian sirve para consultar, previsualizar, actualizar o regresar; no registra una operación por sí solo.
- El botón rojo o con borde rojo corresponde a una operación riesgosa, como anular o revocar. Lea la confirmación antes de aceptarla.
- Las etiquetas ámbar indican atención o datos pendientes; las verdes, una operación correcta; las rojas, un error que debe corregirse.
- Cada tabla tiene su campo **“Filtro de la tabla”**. Escriba código, nombre, fecha o importe para reducir sus filas. Use sus barras horizontal y vertical cuando el contenido sea más ancho o alto que el panel; ninguna columna queda ocultada por compresión.

## Navegación y búsqueda

### Abrir una sección

1. En la barra lateral navy, pulse el nombre de un módulo, por ejemplo **Registros**, **Inventario** o **Costos**.
2. Si el módulo tiene una flecha, púlselo para desplegar sus opciones.
3. Seleccione la opción deseada. La opción activa queda marcada con una línea verde lima.
4. Si necesita más espacio de trabajo, pulse el botón de menú de tres líneas junto al logo. El menú se contrae a iconos; sitúe el cursor sobre un icono para leer su nombre o pulse un grupo para abrir su lista flotante.

### Encontrar una función rápidamente

1. Pulse el botón claro **Buscar** de la barra superior, o use `Ctrl + K`.
2. Escriba el nombre de la pantalla o función.
3. Elija el resultado con el mouse o teclado. La aplicación abre la sección correspondiente.

## Configuración inicial

### Crear o revisar un período contable

Requisito: permiso de configuración.

1. Abra **Parametrización → Períodos**.
2. Complete año, mes, fechas y estado de acuerdo con el ejercicio contable.
3. Pulse el botón verde lima para guardar o abrir el período.
4. Confirme que el chip superior indique el mes y “Abierto”. Si el sistema muestra un aviso de fechas o de traslape, corrija el intervalo antes de continuar.

### Administrar el catálogo de cuentas

1. Abra **Parametrización → Catálogo** o **Catálogo de cuentas**.
2. Escriba parte del código o nombre en el campo de filtro para localizar la cuenta.
3. Seleccione una fila de la tabla.
4. Para cambiar el nombre, pulse **Editar denominación**, escriba el nuevo valor y confirme.
5. Para cambiar su estado, seleccione “Nuevo estado” y pulse **Cambiar estado**. Si no eligió una cuenta o un estado, complete ambos antes de continuar.

### Configurar centros, planes y perfiles

1. En **Parametrización**, abra la tarjeta correspondiente: centros de costo, planes, parámetros o perfiles de contabilización.
2. Ingrese todos los campos obligatorios; los campos con selector permiten buscar por código o nombre.
3. Pulse **Guardar**. Si un código está repetido, una cuenta no admite movimientos o falta un dato, aparecerá una alerta de error y el registro no se guardará.

## Registrar documentos, terceros e IVA

### Crear un tercero

1. Vaya a **Registros → Documentos, terceros e IVA**.
2. En la tarjeta “Tercero de la operación”, escriba NIT y razón social.
3. Marque **Cliente**, **Proveedor** o ambos según corresponda.
4. Pulse el botón verde lima **Guardar tercero**.
5. Localícelo en la tabla “Terceros” mediante su filtro y selecciónelo antes de registrar un documento.

### Previsualizar y contabilizar una venta, compra, cobro o pago

Requisitos: período abierto, tercero seleccionado y un importe numérico mayor que cero.

1. Seleccione el tercero en la tabla “Terceros”.
2. En “Operación y documento”, elija la plantilla: Venta licencia, Venta SaaS ingreso, Compra, Cobro, Pago o Anticipo SaaS.
3. Indique fecha e importe; marque **IVA incluido** únicamente cuando el monto ya lo contiene.
4. Para un cobro con retención, marque **Retención de IVA sufrida** y revise el importe sugerido.
5. Pulse el botón claro **Previsualizar asiento**. Revise las líneas, Debe y Haber; use el filtro de esa tabla si hay muchas líneas.
6. Si el asiento es correcto, cierre la vista previa y pulse el botón verde lima **Registrar y contabilizar**. El aviso final muestra el folio creado.

### Aplicar un anticipo a un DTE

1. Seleccione un tercero que sea cliente.
2. Elija la plantilla **Aplicar anticipo a DTE**.
3. Seleccione el anticipo con saldo, el DTE destino y el importe de aplicación.
4. Previsualice el asiento y pulse **Registrar y contabilizar**.
5. Si el monto supera el saldo, el sistema lo rechazará y debe corregirlo.

## Libro diario

La pantalla está organizada de izquierda a derecha: consulta de partidas, captura y líneas del asiento, luego el inspector de la partida elegida. Esta secuencia evita confundir la lista de asientos existentes con las líneas del asiento que se está editando.

### Crear y cuadrar una partida manual

Requisitos: período abierto, cuentas que admitan movimiento y al menos una línea al Debe y otra al Haber.

1. Abra **Registros → Libro diario** y pulse **Nueva** si había una partida en edición.
2. En “Partidas contables”, seleccione tipo, fecha y escriba el concepto. Añada soporte o referencia de evento cuando exista.
3. En “Líneas del asiento”, busque la cuenta, auxiliar y centro; escriba un importe en **Debe** o en **Haber**, nunca ambos.
4. Pulse **Agregar línea**. Repita por cada componente del asiento. Puede filtrar las líneas agregadas con “Filtro de la tabla”.
5. Revise la barra de cuadre. Si hay diferencia, pulse el botón claro **Cuadrar con cuenta**, seleccione la cuenta de contrapartida y confirme la línea propuesta.
6. Pulse **Guardar borrador** para seguir después, o el botón verde lima **Contabilizar** para registrar la partida.
7. Cuando una línea tenga un dato inválido se marca en rojo y la alerta indica qué número de línea corregir.

### Consultar, aprobar, anular o revertir

1. Use los filtros de Estado, Tipo y rango de fechas en la columna izquierda, además del filtro de tabla si desea una búsqueda textual.
2. Seleccione una partida para ver su resumen en el inspector inferior; haga doble clic para cargarla en edición si procede.
3. Use **Más → Aprobar** para una partida en borrador.
4. Use **Más → Anular** para invalidar una partida permitida, o **Más → Revertir contabilizada** para crear la reversa. Lea la confirmación antes de aceptar.
5. Pulse **Comprobante** o **Cadena de evidencia** para consultar sus respaldos y líneas.

## Inventario, licencias y costos

### Liberar y vender licencias digitales

1. Abra **Inventario → Digitales**.
2. En “Liberación de licencias”, escriba código único de lote, fecha, unidades y costo unitario. El panel muestra el costo total calculado.
3. Pulse el botón verde lima **Liberar lote**.
4. Para una venta, seleccione cliente, escriba precio neto y pulse **Registrar venta y costo**. El sistema aplica PEPS, IVA y costo de venta.
5. Para deshacer una entrega, seleccione la licencia y pulse **Devolver al inventario**; para eliminar el derecho sin reingreso, pulse el botón rojo **Revocar derecho**. Ambos requieren confirmación.

### Consultar el kárdex

1. Abra **Inventario → Kárdex de licencias**.
2. Elija un lote y pulse el botón claro **Actualizar**.
3. Use el campo “Filtro de la tabla” para buscar fecha, movimiento, licencia o folio.
4. Desplácese horizontalmente para ver saldos y valores, y verticalmente para recorrer los movimientos.

### Costear un proyecto a la medida

1. Abra **Costos → A la medida**.
2. Indique la orden, fecha, horas y tarifa; pulse **Registrar MOD** para acumular mano de obra.
3. Use **Cargar costos de ejemplo** solo para la orden demostrativa indicada.
4. Cuando la entrega esté lista, pulse **Reconocer costo de la orden** y revise el mensaje con el folio.

### Registrar y calcular una suscripción SaaS

Requisitos: un plan activo con modalidad SaaS y usuarios-mes positivos.

1. Abra **Costos → Suscripción SaaS**.
2. Seleccione el plan, el corte y escriba los usuarios SaaS del mes.
3. Pulse **Guardar usuarios-mes**.
4. Pulse el botón claro **Calcular costo por plan**. El resultado muestra usuarios del plan y total, alojamiento, soporte, infraestructura, total asignado y costo por usuario-mes.
5. Si no hay plan seleccionado o el número no es válido, corrija el selector o el campo señalado por la alerta.

### Administrar planes y costos por servicio

1. Abra **Inventario → Servicios / Accesos**.
2. Consulte los planes mediante su filtro de tabla; use la barra horizontal si necesita ver todos los datos.
3. Complete código, nombre, modalidad, facturación, tarifa y cuenta de ingreso, y pulse **Guardar plan**.
4. En “Costos agregados por plan facturado”, seleccione el plan, elemento (MOD, servicio o CIF), cantidad y tarifa.
5. Pulse **Acumular costo del plan**. El panel confirma los costos directos, CIF y total acumulado.

## Planilla y conciliación previa

Requisitos: período abierto, tasas configuradas y centro de costo válido.

1. Abra **Registros → Planilla y cargas laborales**.
2. Ingrese fecha, empleados, salario nominal, centro, ISR, otras deducciones y prestaciones.
3. Revise las tasas laborales, que son de solo lectura y provienen de Parametrización.
4. Pulse el botón claro **Previsualizar cálculo y asiento**. La “Conciliación previa” tiene espacio adicional para leer todas las líneas y sus totales.
5. Si los valores son correctos, pulse el botón verde lima **Contabilizar planilla** y confirme. El sistema informa el folio y conserva las tasas e importes usados.

## Alertas y recuperación ante errores

1. Lea el texto de las alertas rojas: explican el campo o regla que impide continuar.
2. Corrija el dato sin cerrar la pantalla y repita la acción. Los importes deben ser numéricos y las fechas deben pertenecer al período abierto.
3. Cuando aparezca una confirmación para anular, revertir, devolver o contabilizar, pulse **Cancelar** si necesita revisar antes; nada se modifica hasta confirmar.
4. Si una tabla no tiene resultados, use su filtro para borrarlo y revise el mensaje vacío: normalmente indica el módulo donde se crea el dato requerido.
