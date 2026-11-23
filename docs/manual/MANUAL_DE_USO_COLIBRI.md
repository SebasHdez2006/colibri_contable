# Manual de uso — Colibrí

Colibrí es un sistema contable de escritorio para **Soluciones Empresariales Colibrí, S.A. de C.V.** que corre localmente con JavaFX y PostgreSQL. Este manual describe lo que la aplicación realmente permite hacer hoy: cada flujo, sus alternativas y sus límites. Cuando una función parece existir pero el prototipo no la completa, se indica explícitamente en la sección **Qué no hacer**.

## Contenido

1. [Puesta en marcha y sesión](#1-puesta-en-marcha-y-sesión)
2. [La ventana principal](#2-la-ventana-principal)
3. [Inicio (tablero)](#3-inicio-tablero)
4. [Temas contables](#4-temas-contables)
5. [Empresa](#5-empresa)
6. [Parametrización](#6-parametrización)
7. [Registros](#7-registros)
8. [Inventario y costos](#8-inventario-y-costos)
9. [Reportes](#9-reportes)
10. [Soporte técnico](#10-soporte-técnico)
11. [Errores frecuentes y recuperación](#11-errores-frecuentes-y-recuperación)
12. [Qué no hacer](#12-qué-no-hacer-límites-del-prototipo)

---

## 1. Puesta en marcha y sesión

### 1.1 Abrir la aplicación

Colibrí es una aplicación de escritorio local; no hay sitio web al que ingresar.

1. Ejecute el lanzador de usuario final **`Colibri.vbs`** (sin consola) o **`Colibri.cmd`** (consola visible) desde la raíz del proyecto. Ambos llaman a `scripts\Iniciar-Colibri.ps1`, que compila solo si falta el build y registra errores en `%LOCALAPPDATA%\Colibri\logs`.
2. Espere el splash y la pantalla **Acceso al sistema**.
3. Si el arranque falla (base de datos apagada, credenciales incorrectas), el error se muestra en un cuadro de diálogo y queda registrado en el log del lanzador.

> Solo puede haber una instancia abierta a la vez: el sistema usa un bloqueo de instancia y una segunda apertura se rechaza.

### 1.2 Iniciar sesión

1. Escriba su **Nombre de usuario** y **Contraseña** y pulse **Iniciar sesión** (o `Enter`).
2. Según el rol de la cuenta verá un menú distinto:
   - **Contador:** Inicio, Temas contables, Empresa, Parametrización, Registros, Inventario, Costos y Reportes.
   - **Soporte técnico:** Usuarios, Base de datos, Entorno, Monitoreo y Accesos. Este rol **no** ve saldos, partidas ni estados financieros.

### 1.3 Bloqueo por intentos y sesión por inactividad

- Tras **5 intentos fallidos** la cuenta se bloquea durante **15 minutos**. El mensaje indica la hora hasta la que debe esperar; no hay desbloqueo manual desde la pantalla de login.
- La sesión expira tras **15 minutos de inactividad**. La expiración se detecta al abrir una sección: el sistema muestra *“Sesión no activa o expirada por inactividad”* y lo devuelve a la pantalla de acceso.
- **Antes de ausentarse**, guarde su trabajo: las partidas en edición son borradores recuperables, pero cualquier acción pendiente de confirmar se pierde.

### 1.4 Recuperar contraseña

Use este flujo cuando olvidó su contraseña y tiene el **código de recuperación** (guardado por soporte técnico en `~/.colibri/recovery-code.txt` al crear la cuenta o tras una recuperación anterior).

1. En la pantalla de acceso, pulse el enlace **“Recuperar contraseña con código local”**.
2. Complete **Usuario**, **Código de recuperación**, **Nueva contraseña** y **Confirmar contraseña** (deben coincidir y tener entre 12 y 128 caracteres).
3. Pulse **Aceptar**. Si algún dato falla, el diálogo permanece abierto con el error.
4. Al terminar, el sistema muestra un **código de recuperación nuevo**: guárdelo, porque el anterior queda invalidado y este nuevo es de un solo uso.

> El código de recuperación desbloquea la cuenta y restablece la contraseña. La **clave temporal** que emite soporte técnico (sección 10.5) es distinta: sustituye la contraseña, **vence a las 24 horas** y obliga a definir una clave propia en el primer inicio de sesión.

### 1.5 Cerrar sesión

Pulse **Cerrar sesión** en la barra superior. La acción queda registrada en la bitácora de accesos.

---

## 2. La ventana principal

### 2.1 Barra superior

De izquierda a derecha:

- **Migas de pan:** el nombre del módulo es un enlace que despliega sus secciones en una lista flotante para saltar entre ellas sin usar el menú lateral.
- **Chip de período** (solo rol Contador): muestra el mes y año del período abierto y su estado — `Abierto`, `En revisión` o `Cerrado` — o **“Sin período abierto”**. Es el indicador clave antes de capturar.
- **Buscar** (o `Ctrl + K`): abre la paleta de comandos para ir a cualquier sección escribiendo su nombre.
- **Zoom:** botones **Reducir zoom**, **Restablecer** y **Aumentar zoom**, con indicador porcentual (85 %–135 %). Atajos: `Ctrl +` / `Ctrl -` / `Ctrl 0`.
- **Tema claro / Tema oscuro:** pulse el texto para alternar; ambos temas conservan el significado de los colores.
- **Cerrar sesión.**

### 2.2 Menú lateral

- Pulse un módulo para desplegar sus opciones; la sección activa queda marcada con una línea verde lima.
- El botón de **tres líneas** junto al logo contrae el menú a iconos. Con el menú contraído, sitúe el cursor sobre un icono para leer su nombre o pulse un grupo para abrir su lista flotante. En ventanas muy angostas el menú se contrae automáticamente.

### 2.3 Convenciones visuales

- **Botón verde lima:** acción principal que guarda, contabiliza o confirma un proceso.
- **Botón claro con borde azul/cian:** consulta, previsualización, actualización o regreso; no registra operación por sí solo.
- **Botón rojo o con borde rojo:** operación riesgosa (anular, revocar). Lea la confirmación antes de aceptar.
- **Etiquetas:** ámbar = atención o datos pendientes; verde = operación correcta; rojo = error a corregir.
- **Filtros de tabla:** cada tabla tiene su campo **“Filtro de la tabla”**. Escriba código, nombre, fecha o importe para reducir filas. Use las barras horizontal y vertical cuando el contenido exceda el panel; ninguna columna queda oculta por compresión.
- **Cuadros de diálogo:** `Aceptar` confirma; `Cancelar` no modifica nada. Los diálogos de progreso de exportación no se pueden cerrar hasta terminar.

---

## 3. Inicio (tablero)

La pantalla **Inicio** resume el período sin capturar datos:

- **Ingresos vs. egresos · superávit o déficit por mes:** barras del rango de años elegido con los selectores **«Desde»** y **«hasta»** (años con movimientos contables); al cambiar cualquiera la serie se recarga y, si el rango se invierte, los valores se intercambian. Encima del gráfico una lectura resume el resultado acumulado del rango y cuántos meses cerraron en superávit, déficit o equilibrio; las fichas mensuales se tiñen según el signo del resultado. Si un mes tiene un volumen extraordinario, se separa como “hito” para no aplastar la tendencia; el detalle numérico de cada mes queda en un panel lateral con scroll junto al diagrama, y los meses sin movimiento se muestran en cero para que el eje sea continuo.
- **KPI:** Ingresos del mes con variación vs. mes anterior, Utilidad del período y Cartera (DTEs por cobrar).
- **Clientes principales** del período con participación porcentual, producto más vendido y usuarios SaaS del mes.
- **Órdenes de servicio:** tarjeta dedicada con el presupuesto contra el costo real de cada orden (consumos registrados), la variación porcentual y el estado; el encabezado resume cuántas hay en curso y finalizadas. La búsqueda filtra por código, descripción, estado o montos.
- **Costos y suscripciones:** tarifas vigentes por plan y comparativo SaaS/CIF del período.
- **Dispersión · ingresos vs. egresos por mes:** cada punto es un mes (ingresos en X, egresos en Y). La diagonal marca el equilibrio y la banda sombreada la tolerancia de ±10 %; los puntos verdes cerraron con holgura, los celestes dentro de tolerancia y los rojos con egresos desbordados. La leyenda y el pie interpretan el conteo por zona; al pasar el cursor sobre un punto aparece un aviso con el nombre del mes y sus valores. Comparte la fila con la dispersión de costos y ambas gráficas se repintan al cambiar el tamaño de ventana o el tema.
- **Dispersión · costos: presupuesto vs. costo real:** el selector **«Ver costos de»** alterna entre *Proyectos a la medida*, *Licencias digitales* y *Acceso SaaS*; cada punto es un objeto de la línea (presupuesto en X, costo real en Y). La diagonal marca el costo exacto y la banda sombreada la tolerancia de ±10 %; el pie resume cuántos objetos quedaron bajo presupuesto, en tolerancia o con sobrecosto y la variación media; el cursor sobre un punto muestra el objeto con su presupuesto, costo real y variación.
- **Estructura de costos:** tabla con una fila por proyecto, lote de licencias y plan SaaS: **precio unitario** y costo desagregado por rubro (**materia prima**, **mano de obra** y **CIF**), más el costo total. En proyectos el precio es la venta de la orden y el costo sale de los consumos contabilizados (servicios directos cuentan como materia prima); en licencias el precio es el promedio de venta por unidad según el kárdex y el costo del lote se reconoce íntegro en materia prima, sin desglose MOD/CIF; en accesos el precio es la tarifa por usuario-mes y el costo es el asignado del pool de procesos por usuario-mes (va en CIF por ser indirecto). La búsqueda filtra por código, detalle o tipo.
- **Por hacer:** estado del catálogo, borradores pendientes de revisión, alertas operativas y el botón **Aprobar catálogo inicial** (necesario una sola vez si el catálogo aún no está aprobado para movimientos).
- **Ir a registros:** acceso directo al Libro diario.

> Si el panel no logra calcular sus indicadores, la pantalla muestra valores en cero y el detalle del error queda en la bitácora local en el archivo colibri.log dentro de la carpeta .colibri del perfil de usuario, para diagnóstico.

---

## 4. Temas contables

**Temas contables** es un mapa de los temas de la rúbrica agrupados en cuatro secciones: *Empresa y fundamentos*, *Registros y estados*, *Costos y control* y *Estimación y comercialización*.

1. Despliegue el grupo y elija un tema en la lista.
2. Lea el concepto y cómo lo automatiza Colibrí.
3. Pulse **Abrir \<módulo\>** para saltar al módulo operativo que lo ejecuta.

Es material de referencia; no registra ni modifica datos.

---

## 5. Empresa

Configure el perfil corporativo que encabeza reportes y comprobantes.

1. Abra **Empresa**.
2. En **Datos fiscales** complete razón social, nombre comercial, NIT, NRC, actividad económica (CAT-019), dirección fiscal.
3. En **Logo y firmantes**:
   - Escriba la ruta del logo o pulse **Elegir logo** (PNG o JPG); la vista previa se actualiza al instante.
   - Complete representante legal, DUI, contador firmante y registro CVPCPA.
4. Pulse **Guardar perfil corporativo**. El aviso confirma que el perfil quedó disponible para encabezados y firmas.

> Los períodos **no** se administran aquí: viven en **Parametrización → Períodos contables**.

---

## 6. Parametrización

### 6.1 Catálogo de cuentas

Administre el plan de cuentas (solo cuentas de movimiento pueden usarse en partidas).

**Crear una subcuenta:**

1. Filtre por código o nombre para ubicar el padre.
2. Escriba el **código de 6 dígitos**, la denominación y el **código de cuenta padre de 4 dígitos**; elija la naturaleza (Deudora, Acreedora o Deudora y acreedora).
3. Pulse **Crear subcuenta**. Si el código existe, el padre no es de 4 dígitos o el código no tiene 6 dígitos, aparece una alerta y no se guarda.

**Editar o cambiar estado:**

4. Seleccione la fila: el combo **Nuevo estado** se sincroniza con su estado actual (Activa, Desactivada o Bloqueada); cámbielo si lo necesita y pulse **Cambiar estado**. También puede pulsar **Editar denominación** para cambiar el nombre.

> El botón **Aprobar catálogo inicial** del Inicio habilita el catálogo para movimientos cuando el sistema lo exige.

### 6.2 Mapeo de módulos (perfiles de contabilización)

Define las cuentas por defecto que usan los eventos operativos (ventas, compras, cobros, pagos, inventario, depreciación, planilla) al generar asientos automáticos.

1. En la tabla, seleccione el evento; los combos Debe, Haber e IVA se llenan con sus cuentas actuales. Las columnas Debe, Haber e IVA muestran el código **y el nombre** de cada cuenta para identificarla sin memorizar el plan.
2. Cambie las cuentas que necesite (dejar un combo vacío conserva la cuenta actual).
3. Pulse **Guardar mapeo seleccionado**.
4. Para un evento nuevo: escriba el código (se normaliza a mayúsculas) y la descripción, elija Debe y Haber (obligatorios), IVA opcional, y pulse **Crear evento**.

> Crear un evento nuevo **no** crea una pantalla ni una automatización: solo registra el perfil de cuentas que los flujos existentes pueden consultar.

### 6.3 Períodos contables

Los períodos son meses de un ejercicio contable. Desde esta pantalla puede **crear ejercicios nuevos**, **crear períodos** dentro de un ejercicio y cambiar el estado de los existentes.

#### Crear un ejercicio

1. Escriba el **Año** (2000–2100) y elija el **Mes a abrir**.
2. Pulse **Crear ejercicio (12 meses)**: genera los 12 períodos del año; solo el mes elegido queda **abierto** y los demás nacen cerrados. Si el año ya existe para la empresa, lo rechaza.

#### Crear un período suelto

1. Elija el **Ejercicio** en su combo y el **Mes** (1–12) en el otro.
2. Pulse **Crear período**: agrega ese mes al ejercicio y **nace cerrado**; ábralo con el botón de estado si necesita captura. Si el ejercicio ya tiene ese mes, lo rechaza.

#### Cambiar el estado de un período

1. Abra **Períodos contables** y localice el mes en la tabla (filtro disponible; la columna Ejercicio muestra el año).
2. Según el estado deseado:
   - **Abrir / reabrir:** habilita la captura en ese mes.
   - **Poner en auditoría:** admite ajustes controlados.
   - **Cerrar período:** pide confirmación y verifica que no haya cédulas de ajuste ni partidas en borrador o aprobadas pendientes antes de bloquear la captura.
3. Verifique el chip de la barra superior: muestra el período abierto más antiguo.

#### Activar o desactivar un ejercicio

1. Elija el ejercicio en el combo **Ejercicio** y pulse **Activar / desactivar ejercicio**.
2. Un ejercicio inactivo deja de participar en la captura. **No se puede desactivar** mientras tenga períodos abiertos: cierre o reubique la captura primero.

> Pueden coexistir varios períodos abiertos: cada operación valida el período de **su propia fecha**, no solo el del chip.

### 6.4 Parámetros fiscales

Un único registro activo con las alícuotas y cuentas tributarias.

1. Complete: IVA, retención de IVA, pago a cuenta ISR, retención ISR servicios, retención ISR intangibles y código de catálogo MH.
2. Seleccione las cuentas: IVA crédito, IVA débito, IVA por pagar, retención de IVA (activo) y pago a cuenta.
3. Pulse **Guardar parámetros fiscales**. Si falta una cuenta, el sistema indica cuál.

> No hay historial de vigencias: el cambio aplica de inmediato a las operaciones nuevas.

### 6.5 Políticas y secuenciales

Parámetros del sistema en tabla (clave, valor, descripción): máscaras de folio, tolerancia de cuadre, políticas de inventario, CIF y recurso humano (tasas laborales ISSS y previsional que usa la planilla).

1. Filtre y seleccione la política; su valor actual aparece en el campo.
2. Escriba el nuevo valor y pulse **Actualizar valor**.

> **Atención:** cambiar estos valores no reconfigura el motor. El formato de folio, la igualdad exacta Debe = Haber y la inmutabilidad de partidas contabilizadas son reglas fijas del sistema.

### 6.6 Centros de costo

1. En la tabla, localice el centro con el filtro: muestra **Código, Nombre, Descripción, Uso y Estado** (Activo/Inactivo).
2. **Seleccione la fila** para cargarla en el formulario (código, nombre, descripción, uso y marca Activo).
3. Modifique lo necesario —incluido el estado Activo para desactivarlo o reactivarlo— y pulse **Guardar centro** (actualiza por código). Para crear otro, pulse **Nuevo centro** y limpie los campos.

> En las líneas de partida, auxiliar y centro son campos opcionales: el catálogo se ofrece como ayuda de captura, no como validación obligatoria.

---

## 7. Registros

### 7.1 Documentos, terceros e IVA

Pantalla para terceros, documentos internos y operaciones con IVA. **No emite DTE oficiales**: los documentos son registros internos de demostración.

#### Crear un tercero

1. En la tarjeta **Tercero de la operación**, escriba NIT y razón social.
2. Marque **Cliente**, **Proveedor** o ambos (un tercero puede ser ambos; la tabla muestra su perfil) y deje **Activo** marcado; desmáquelo para darlo de baja sin borrarlo.
3. Pulse **Guardar tercero**.
4. Localícelo en la tabla **Terceros** con su filtro y **selecciónelo**: las operaciones y los anticipios se filtran por esa selección.

#### Editar un tercero

1. Seleccione el tercero en la tabla **Terceros** y pulse **Editar seleccionado**: los campos NIT, razón social y las marcas Cliente/Proveedor se cargan en el formulario y la etiqueta de modo muestra "Editando: …".
2. Modifique lo necesario —incluida la marca **Activo** para habilitarlo o darlo de baja— y pulse **Actualizar tercero** (el botón cambia de texto en modo edición).
3. Para volver a crear, pulse **Nuevo**: limpia el formulario y sale del modo edición.

> El NRC y el nombre comercial no se editan desde esta pantalla; se conservan sus valores actuales. Si cambia el NIT a uno ya registrado, el sistema lo rechaza.

#### Registrar una operación

Requisitos: período abierto, tercero seleccionado y un importe numérico mayor que cero. La fecha por defecto es el inicio del período abierto.

1. Seleccione el tercero en la tabla.
2. En **Operación y documento**, elija la plantilla:

   | Plantilla | Uso | Notas |
   |---|---|---|
   | Venta licencia | Venta de licencia digital | Requiere tercero **cliente** |
   | Venta SaaS ingreso | Ingreso por suscripción | Requiere tercero **cliente** |
   | Compra | Compra a proveedor | Requiere tercero **proveedor** |
   | Cobro | Cobro a cliente | Permite retención de IVA |
   | Pago | Pago a proveedor | — |
   | Anticipo SaaS | Anticipo de suscripción | Genera saldo aplicable a DTE |
   | Aplicar anticipo a DTE | Traslada saldo de anticipo a un DTE | Ver flujo abajo |

3. Indique **Fecha** e **Importe (USD)**; marque **IVA incluido** solo cuando el monto ya contiene IVA (venta y compra).
4. **Cuenta opcional:** texto libre que reemplaza la cuenta parametrizada — solo aplica a ventas y compras; se ignora en cobros, pagos y anticipos.
5. Para **Cobro**, marque **Retención de IVA sufrida** y revise el importe sugerido (importe × tasa de retención fiscal); puede corregirlo.
6. Pulse **Previsualizar asiento**: el diálogo muestra las líneas con Cuenta, Auxiliar, Centro, Debe y Haber, y el total con indicación **(cuadra)** o **(NO cuadra)**.
7. Si es correcto, cierre la vista previa y pulse **Registrar y contabilizar**. El aviso muestra el **folio** creado; el documento aparece en la tabla **Documentos** con su estado.

#### Aplicar un anticipo a un DTE

1. Seleccione un tercero marcado como **cliente**.
2. Elija la plantilla **Aplicar anticipo a DTE**: se habilitan los combos **Anticipo** (con saldo) y **DTE destino** (con saldo), ambos filtrados por ese cliente.
3. Escriba el **Importe aplicación**.
4. Previsualice (muestra el traslado del anticipo al DTE) y pulse **Registrar y contabilizar**.
5. Si el importe supera el saldo disponible, el sistema lo rechaza: corrija el monto.

### 7.2 Libro diario

Pantalla de captura y consulta de partidas. Ciclo de vida: **Borrador → Aprobada → Contabilizada**; desde borrador/aprobada se puede **Anular**; una contabilizada se corrige con **Revertir** (genera una partida de ajuste y marca la original como Revertida). Una partida contabilizada es **inmutable**.

La pantalla se organiza en: lista de partidas con filtros, el **inspector de la partida seleccionada** justo debajo de esa tabla (folio, origen, concepto, cuadre y acciones de documento), y debajo la captura con sus líneas del asiento.

#### Crear y cuadrar una partida manual

Requisitos: período abierto, cuentas que admitan movimiento, al menos una línea al Debe y otra al Haber.

1. Abra **Registros → Libro diario**. Pulse **Nueva** si había una partida en edición (si tiene cambios sin guardar, el sistema pregunta si desea guardarlos antes).
2. En **Partidas contables**: elija **Tipo de partida**, **Fecha contable** (la píldora de período muestra mes, año y estado; advierte si la fecha cae fuera de período), escriba **Concepto** y, si existe, **Documento de soporte** y **Referencia del evento**.
3. En **Líneas del asiento**, complete **Cuenta** (buscador de cuentas de movimiento), **Auxiliar** (terceros, lotes u órdenes), **Centro** y un importe en **Debe** o en **Haber — nunca ambos**. Pulse **Agregar línea** (también `Enter` en el campo Haber). Repita por cada componente.
4. Edición rápida en la tabla: **doble clic**, `F2` o `Enter` sobre una celda para editarla; `Supr` elimina la línea seleccionada; `Esc` cancela la edición. Las líneas con datos inválidos se marcan en rojo.
5. Revise la **barra de cuadre** (Debe / Haber / Diferencia). Si hay diferencia:
   - Pulse **Cuadrar con cuenta**: el diálogo propone el lado que falta (Debe o Haber) y el monto exacto; complete cuenta, auxiliar y centro, y confirme.
   - O agregue la línea manualmente.
6. Pulse **Guardar borrador** (`Ctrl + G`) para seguir después — el borrador puede quedar descuadrado — o **Contabilizar** (`Ctrl + Enter`) para registrar la partida.
7. **Contabilizar** guarda el borrador si hace falta, exige cuadre exacto, pide confirmación (el folio se asigna y la partida queda inmutable) y muestra el folio.

#### Aprobar, anular y revertir

1. **Más → Aprobar:** lleva un borrador cuadrado a aprobada (también se aprueba automáticamente al contabilizar).
2. **Más → Anular:** solo para borrador o aprobada; pide **motivo** y la partida queda anulada sin afectar libros.
3. **Más → Revertir contabilizada:** seleccione primero la partida en la lista de recientes; pide motivo y fecha de reversión; crea una partida de ajuste `AJ` contabilizada y marca la original como **Revertida**.

#### Otras acciones del Libro diario

- **Duplicar partida** (Más): copia las líneas de la partida seleccionada o cargada como borrador nuevo sin guardar; revise y pulse **Guardar borrador**.
- **Importar CSV** (Más): elija un `.csv` de líneas de asiento; genera un **borrador** que se carga en el editor para revisar y guardar. No importa DTE ni XML.
- **Comprobante:** vista de impresión del asiento con membrete de empresa. Desde el diálogo puede **Imprimir** o **Guardar PDF**.
- **Cadena de evidencia:** muestra documento, evento, folio, bitácora y referencia de la partida seleccionada, con sus líneas.
- **Exportar diario filtrado:** con los campos “Diario desde/hasta” genere un Excel (`.xlsx`) del libro diario del rango, con fila de totales. Use los filtros Estado/Tipo/fechas de la lista solo para la vista; la exportación usa el rango de fechas.
- La lista muestra hasta **200 partidas recientes**; para más historia use los filtros o los reportes.

### 7.3 Plantillas de asiento recurrente

Para asientos mensuales repetitivos (depreciación, provisiones, ISSS).

1. Abra **Registros → Plantillas** y pulse **Nueva plantilla**.
2. En el diálogo: el **código** se sugiere solo desde el tipo y el nombre (puede editarse), escriba **Nombre**, **Tipo de partida** y **Concepto**.
3. Agregue líneas con cuenta, Debe y Haber (**Agregar línea** / **Quitar línea**). Reglas: al menos dos líneas, cada una con importe en un solo lado, y el total Debe debe igualar Haber o el diálogo muestra el error.
4. **Ver líneas** muestra el detalle de la plantilla seleccionada.
5. **Generar borrador del mes:** con una plantilla seleccionada, confirma y crea un **borrador** con fecha del fin del período abierto (si no hay período abierto, pide la fecha). El borrador se revisa y contabiliza en el Libro diario; la plantilla **nunca contabiliza sola**.

### 7.4 Planilla y cargas laborales

Devengo agregado de planilla con conciliación previa.

Requisitos: período abierto y tasas laborales configuradas en **Parametrización → Políticas**.

1. Abra **Registros → Planilla**.
2. Complete **Fecha contable** (por defecto, fin del período abierto), **Empleados**, **Salario nominal total**, **Centro de costo** (por defecto GENERAL), **ISR retenido**, **Otras deducciones** y **Otras prestaciones**.
3. Las tasas ISSS y previsional se muestran de **solo lectura**; provienen de Parametrización.
4. Pulse **Previsualizar cálculo y asiento**: el resumen muestra líquido por pagar, cargas patronales, prestaciones provisionadas y costo total empresa; la conciliación muestra las líneas Debe/Haber.
5. Si es correcto, pulse **Contabilizar planilla** y confirme. El sistema registra una partida única, informa el folio y conserva las tasas e importes usados.

### 7.5 Ajustes y cierre de período

Ejecute los ajustes del mes antes de cerrar. Cada botón registra su partida de inmediato (no hay previsualización) e informa el folio.

1. Abra **Registros → Ajustes** y elija la **Fecha contable**: debe caer dentro del período abierto.
2. **Ajustes manuales del mes:** cuatro columnas, cada una con su importe, la explicación de qué registra y el botón debajo del campo:
   - **Suscripciones devengadas** — gasto de suscripciones SaaS devengadas.
   - **Consumo de nube** — servicios en la nube del mes.
   - **Consumo de alquiler** — alquiler devengado.
   - **Gastos pendientes** — gasto devengado pendiente de factura.
3. **Ajustes automáticos y cierre:** cada acción muestra su título, una explicación de lo que hará y el botón al lado:
   - **Depreciar activos fijos** y **Amortizar intangibles:** recorren los activos/intangibles elegibles del período. Son **idempotentes**: no duplican la cuota si ya existe.
   - **Liquidar IVA del período:** cruza débito y crédito fiscal del mes y genera la partida de liquidación.
4. **Cierre del período** (dos alternativas):
   - **Cerrar período con ajustes automáticos:** ejecuta depreciación, amortización y liquidación de IVA del mes y después cierra; muestra un resumen de lo generado.
   - **Cerrar período (sin ajustes):** cierra directamente; úselo solo si los ajustes ya fueron registrados manualmente.
5. Ambos piden confirmación y **bloquean la captura** del mes. El cierre se rechaza si existen cédulas de ajuste pendientes **o partidas en borrador/aprobada sin resolver** en el período.

---

## 8. Inventario y costos

### 8.1 Inventario → Digitales (licencias)

#### Liberar un lote

1. Complete **Fecha**, **Código del lote** (único), **Producto / versión**, **Unidades** y **Costo unitario (USD)**; el panel muestra el **Costo total** calculado en vivo. **Cliente titular** es opcional. El combo de versión sólo ofrece versiones en estado **Liberada** del catálogo (se administra en **Inventario → Productos**); si no selecciona ninguna, el sistema usa la primera versión liberada.
2. Pulse **Liberar lote**. Unidades y costo deben ser mayores que cero.

#### Vender una licencia (PEPS)

1. En **Concesión de licencia**, seleccione el **Cliente** y escriba el **Precio neto (USD)** sin IVA.
2. Pulse **Registrar venta y costo**: el sistema aplica PEPS (toma la licencia disponible más antigua), calcula IVA y costo de ventas, y muestra el folio.

#### Devolver o revocar una licencia entregada

1. En **Licencia entregada**, seleccione la licencia (el combo muestra lote, número, cliente y fecha).
2. Escriba el **Motivo** si lo desea (queda en el kárdex).
3. Elija una de dos operaciones — **no son equivalentes**:
   - **Devolver al inventario:** la unidad vuelve a estar disponible por PEPS. Si su venta tiene partida trazable, el sistema revierte el asiento completo (ingreso, IVA, costo de ventas e inventario) y muestra el folio de la reversa; si no, avisa que el ajuste del ingreso es manual.
   - **Revocar derecho** (rojo): el derecho de uso queda revocado y la unidad **NO** regresa al inventario; el tratamiento del ingreso se hace por separado. Ambos piden confirmación.

La tarjeta **Inventario perpetuo PEPS** resume las cuentas del método según el mapeo de módulos (cuenta por cobrar e ingreso de VENTA, costo de ventas e inventario de COSTO_VENTA e IVA débito fiscal), muestra los lotes con unidades disponibles y costo unitario, y ofrece **Ver kárdex de licencias** para saltar a esa pantalla.

#### Deterioro de inventario (NIC 2 / valor recuperable)

Cuando el valor recuperable de un lote es menor que su costo en libros, registre el ajuste:

1. Seleccione el **Lote** en el combo (muestra código, disponibles y costo unitario), escriba el **Valor recuperable (USD)** del lote completo — no puede exceder su costo bruto — y la **Fecha** del ajuste (tiene su propio selector de fecha).
2. Pulse **Aplicar deterioro**: el sistema compara contra el deterioro ya acumulado y genera la partida de ajuste **en borrador** con folio; revísela, apruébela y contabilícela en el **Libro diario** para que afecte el mayor.
3. La operación es **idempotente por período**: repetirla con el mismo valor recuperable devuelve la partida existente, no duplica la pérdida. Si en un período posterior el valor recuperable **sube**, el sistema revierte automáticamente el deterioro acumulado.

> El ajuste se hace **por lote**, no por licencia individual.

### 8.2 Inventario → Productos y versiones

Catálogo de productos digitales y ciclo de vida de sus versiones. Es la pantalla donde el contador da de alta lo que luego libera como lotes en **Digitales**.

#### Administrar productos

1. La tabla lista los productos (código, nombre, modalidad, estado). Filtre con su búsqueda.
2. Complete el formulario: **Código** (único), **Nombre**, **Modalidad** (Licencia, Suscripción SaaS o Proyecto a la medida) y la marca **Producto activo**.
3. Pulse **Guardar producto**: crea uno nuevo o actualiza el existente con el mismo código. **Seleccione una fila** para cargar el producto y editarlo; **Nuevo** limpia los campos. Un producto inactivo conserva su historial pero no admite versiones nuevas.

#### Crear y liberar versiones

1. En **Versiones de producto**, pulse **Crear versión** con el **Producto** y la **Versión** (ej. 2.0). La versión nace en estado **En producción** y debe ser única dentro del producto.
2. Para habilitarla en la liberación de lotes, **seleccione la fila** de la versión, elija **Liberada** en **Estado destino** y pulse **Cambiar estado**.
3. El ciclo es **En producción → Liberada → Descontinuada**: una versión en producción puede liberarse o descontinuarse; una liberada sólo puede descontinuarse; descontinuada es terminal. Descontinuar una versión **no** afecta los lotes ya liberados con ella: siguen vendibles por PEPS.

### 8.3 Inventario → Kárdex

1. Seleccione el **Lote** en el combo (se carga el primero disponible): la tabla se llena sola, no hay botón de actualización.
2. Acote la consulta con **Desde/Hasta** (fechas) y **Tipo** de movimiento (Liberación, Salida PEPS, Devolución, Revocación); la línea de totales muestra movimientos, entradas, salidas e importe neto del filtro.
3. La tabla muestra los movimientos cronológicos: fecha, tipo, licencia, entrada/salida, costo unitario, importe y saldos de unidades y valor. Use el **Filtro de la tabla** para buscar dentro de lo cargado y desplace horizontalmente para ver saldos y valores.

### 8.4 Inventario → Servicios / Accesos

#### Administrar planes de servicio

1. La tabla lista los planes (código, nombre, modalidad, tarifa). Filtre con su búsqueda.
2. Complete el formulario: **Código**, **Nombre**, **Modalidad** (Licencia, Suscripción SaaS, Proyecto a la medida, Servicio), **Facturación** (Por usuario y mes, Por plan, Por hito, Por entrega, Pago único), **Tarifa**, **Cuenta de ingreso** (el combo sólo ofrece **cuentas de ingreso activas**; por defecto 410402) y la marca **Plan activo**.
3. Pulse **Guardar plan**: guarda uno nuevo o actualiza el existente con el mismo código (incluido su estado activo). **Seleccione una fila** para cargar el plan en el formulario y editarlo; **Nuevo plan** limpia los campos. Si la cuenta de ingreso deseada no existe, pulse **Ir al catálogo de cuentas** para crear la subcuenta.

#### Acumular costos por plan facturado

1. En **Costos agregados por plan facturado**, seleccione el **Plan de servicio** (solo planes de modalidad SERVICIO), el **Elemento** (MOD, Servicio/recurso directo o CIF), **Descripción**, **Cantidad** y **Tarifa**.
2. Pulse **Acumular costo del plan**. El panel confirma MOD, directos, CIF y total acumulado del plan.

> Estos costos son apoyo gerencial: no generan por sí solos un asiento de reparto ni ingreso/margen por plan.

### 8.5 Costos → A la medida (órdenes de servicio)

La pantalla tiene dos tarjetas: **Ciclo de vida** (gestión de la orden) y **Costeo** (acumulación y reconocimiento).

#### Ciclo de vida de la orden

1. **Crear orden:** complete **Código** (único), **Descripción**, **Presupuesto**, **Precio de venta** y **Cliente** (opcional, solo terceros marcados como cliente) y pulse **Crear orden**. La orden nace en estado **Abierta**.
2. La tabla lista todas las órdenes con su estado, presupuesto, precio, costo acumulado, margen y **margen %**; use su filtro para buscar.
3. **Seleccione una fila** para cargar su código en el panel de costeo y habilitar el cambio de estado.
4. **Editar orden:** con una fila seleccionada, pulse **Editar seleccionada** para cargar descripción, presupuesto, precio y cliente en el formulario; ajuste y pulse **Guardar cambios**. Sólo aplica a órdenes **Abierta** o **En proceso**; cada edición queda en bitácora con los valores anteriores.
5. **Cambiar estado:** elija el **Estado destino** (el combo solo ofrece transiciones válidas) y pulse **Cambiar estado**. El ciclo es `ABIERTA → EN_PROCESO → ENTREGADA → CERRADA`; `CANCELADA` solo desde Abierta o En proceso. Cada cambio queda en bitácora.

#### Costeo de la orden

1. El panel superior muestra la orden cargada: costo acumulado, precio, margen y alerta si el margen es menor a 35 %.
2. **Registrar MOD:** escriba **Horas** y **Tarifa** y pulse el botón para acumular mano de obra directa en la orden indicada.
3. **Reconocer costo de la orden:** con el código cargado y la fecha, pulsa el botón para descargar los componentes acumulados como costo; el aviso muestra el folio.

> El reconocimiento **no verifica una entrega previa**: la transición a Entregada es operativa y no bloquea el costeo.

### 8.6 Costos → Licencias descargables (comparación de métodos)

1. La tarjeta lista los lotes y unidades disponibles.
2. Escriba **Unidades para análisis** y pulse **Comparar PEPS / promedio / UEPS**.
3. La tabla muestra, para cada método, el costo de salida hipotética y el saldo posterior.

> Solo **PEPS** origina salidas contables reales; promedio y UEPS son referencia gerencial y no generan asientos.

### 8.7 Costos → Suscripción SaaS

Requisitos: al menos un plan activo con modalidad **SaaS**.

1. Seleccione el **Plan SaaS** y la **Corte** (fecha del mes).
2. Escriba los **Usuarios SaaS del mes** y pulse **Guardar usuarios-mes** (se guarda por mes).
3. Pulse **Calcular costo por plan**: el resultado muestra usuarios del plan sobre el total, alojamiento, soporte, infraestructura compartida, total asignado y **costo por usuario-mes**. Esos componentes se leen de los saldos de las cuentas CIF de alojamiento, soporte e infraestructura del período: si no hay gastos registrados ahí, salen en $0.00 y el aviso indica registrar primero la partida de costos indirectos.

> El cálculo no persiste una distribución contable ni genera asiento de reparto.

### 8.8 Costos → Procesos y CIF

#### Cédula de costo por procesos

1. Complete **Fecha contable** y **Código de proceso** (por defecto SAAS-01).
2. Si ya existe una cédula del período, pulse **Cargar cédula del período** para traerla al formulario.
3. Registre el **balance físico** (inicial + iniciadas − final = terminadas; el sistema valida la conciliación), los avances de **Material** y **Conversión** (proporciones de 0 a 1) y los costos iniciales y del período.
4. Pulse **Guardar cédula y calcular**: la salida muestra unidades equivalentes, costo unitario y totales por **PEPS** (método aplicado), promedio y UEPS (solo comparación).

#### Análisis CIF (costos indirectos de fabricación)

1. Complete **Centro de costo**, **CIF estimado**, **Base estimada**, **Base real** y **CIF real**; pulse **Guardar análisis CIF**. El resultado muestra la tasa predeterminada, el CIF aplicado, el real y la variación (equilibrado / subaplicado / sobreaplicado).
2. **Aplicar CIF a orden:** escriba el código de orden, la base aplicada y la descripción; pulse el botón para registrar la aplicación con partida trazable (muestra folio).
3. **Cerrar variación CIF:** requiere seleccionar la **Cuenta CIF real** (solo cuentas 5201xx) y confirmar; lleva la sub o sobreaplicación a costo de proyectos con partida y folio.

### 8.9 Costos → Estimación y políticas

#### Estimación de costos de software

1. Elija el **Método**: juicio de expertos, analogía, puntos de función, COSMIC, COCOMO, desgaste de trabajo, líneas de código, tres puntos o SLIM/Putnam. Los campos de parámetros cambian según el método.
2. Complete tarifa/hora, CIF % y margen %.
3. Pulse **Calcular costo y precio unitario**: muestra esfuerzo en horas, costo directo, CIF, costo total, precio unitario, margen y una recomendación de uso.

#### Comparaciones gerenciales (misma pantalla)

- **Comparar métodos de costeo:** materiales, MOD, CIF estándar, costo real, unidades y actividades ABC (calidad, infraestructura) → costo directo, estándar con variación y ABC.
- **Comparar estimado y real:** indique el código de orden → presupuesto vs. costo real, variación, índice de ejecución, ahorro/sobrecosto, utilidades y diagnóstico.
- **Estimación promedio vs. históricos:** tarifa/hora, CIF % y margen % compartidos, más un apartado por cada uno de los 9 métodos con sus propios insumos en la unidad que su técnica requiere (horas, puntos de función, CFP, KLOC o tamaño) → cada tarjeta muestra su costo total estimado; estadísticos del promedio (mediana, rango, desviación, variabilidad, varianza), detalle por método y tabla estimado vs. costos reales por rubro.
- **Políticas de costos y ventas:** texto de referencia (inventario/PEPS, MOD/CIF, acumulación por tipo de servicio, documentación y trazabilidad).
- **Políticas, procesos y procedimientos:** catálogo de 122 instrumentos institucionales con filtro por texto; la columna **Instrumento** muestra el nombre completo (envuelto si es largo) y el botón **Ver** de cada fila despliega la descripción íntegra en un panel bajo la tabla (**Ocultar descripción** lo repliega); es referencia documental, no movimientos.

> Todos los cálculos de esta pantalla son apoyo gerencial: no generan partidas ni sustituyen cotizaciones o contratos.

---

## 9. Reportes

Pantallas de **solo lectura** con filtros de formato, alcance, cuenta (solo Cuenta T), fechas y botones de generación/exportación. Los reportes usan el membrete del perfil de Empresa.

### 9.1 Reportes → Obligatorios

Formatos disponibles:

- **Libros:** Libro diario, Libro mayor, Balanza de comprobación, Cuenta T (requiere elegir la cuenta), Hoja de trabajo.
- **Estados financieros:** Situación financiera, Estado de resultados, Cambios en el patrimonio y Flujos de efectivo (los cuatro son seleccionables en el formato).
- **Libros de IVA:** compras, ventas CCF y ventas consumidor final.

Flujo:

1. Elija **Formato**, **Alcance** (*Libro completo* o *Caso D del informe*), **Cuenta** si es Cuenta T, y el rango **Desde/Hasta** (por defecto, el período abierto).
2. Pulse **Generar reporte**: la tabla muestra las filas y el total ("N filas · período").
3. Con el reporte generado, pulse **Exportar Excel** o **Exportar PDF** para guardar el archivo.

> El libro de ventas consumidor final excluye las `CCF` (pertenecen al libro de ventas CCF) y los documentos anulados; la separación es por tipo de documento.

### 9.2 Reportes → Auxiliares

Mismos filtros y exportaciones, con formatos: **Auxiliar de clientes, proveedores, inventario digital, órdenes de servicio y gastos por centro de costo**.

Cada auxiliar incluye la **dimensión** del movimiento (cliente/proveedor, lote, orden o centro de costo) y el **saldo acumulado** por dimensión: el saldo inicial del rango se calcula desde el historial y cada fila muestra el saldo corrido después de su movimiento.

> **Ojo con el saldo por dimensión:** cuando la misma dimensión aparece en varias cuentas (por ejemplo, el NIT de un cliente en 110201 y 110202), el saldo acumulado las **combina**. Si filtra el auxiliar a una sola cuenta, el saldo corrido puede parecer mayor que el de esa cuenta en particular; use el Libro mayor o la Balanza para saldos por cuenta exactos.

### 9.3 Reportes → Societarios

Vista informativa sobre actas, accionistas y capital social. **No registra ni emite libros societarios**; remite a los reportes obligatorios.

### 9.4 Exportaciones

- **Paquete ZIP:** con el rango de fechas elegido, genera un `.zip` con los **8 libros** (Diario, Mayor, Balanza, Hoja de trabajo, Situación financiera, Resultados, Cambios en el patrimonio y Flujos de efectivo) en **PDF y XLSX** cada uno, organizado por reporte y período. Los reportes anchos salen en PDF A3 apaisado. No se eligen formatos individuales dentro del ZIP.
- **Exportar libros × períodos:** abre un diálogo para marcar **libros** (la Cuenta T queda fuera porque requiere cuenta específica) y **períodos** de los ejercicios disponibles, con rango de fechas para filtrarlos y botones Todos/Ninguno. Elija la carpeta destino y el sistema genera una carpeta `Exportacion_Del_<rango>_<fecha>` con una subcarpeta por libro y dentro un Excel y un PDF por período. **No** es un ZIP.
- **Exportar diario filtrado** (desde el Libro diario): Excel del diario del rango con totales.

---

## 10. Soporte técnico

Rol separado que administra usuarios, base de datos, entorno y monitoreo. No accede a información contable.

### 10.1 Usuarios

1. La tabla lista usuario, nombre, correo, rol y estado, con el contador de usuarios activos.
2. **Nuevo usuario:** complete usuario (3–64 caracteres: letras, números, punto, guion o guion bajo), nombre completo, correo opcional, rol y **contraseña inicial** (12–128 caracteres). Al crearlo, el sistema devuelve un **código de recuperación de un solo uso**: entréguelo a la persona por un canal seguro.
3. Seleccione un usuario para editar nombre, correo y estado activo (**Guardar cambios**). El rol y la contraseña no se editan aquí.
4. No puede desactivar la cuenta que está usando ni dejar el sistema sin al menos un usuario activo.

### 10.2 Base de datos

- El panel muestra la fecha del **último respaldo**.
- **Generar respaldo** pide confirmación y crea una copia con `pg_dump` (ruta fija `C:\Program Files\PostgreSQL\18\bin\pg_dump.exe`); el aviso muestra la ruta del archivo.

> Es un respaldo **manual**: no hay programación, cifrado, incremental ni restauración desde la interfaz.

### 10.3 Entorno

1. Revise/edite **URL JDBC**, **usuario de base de datos** y **contraseña** (vacío = conservar la actual).
2. Configure **Ruta de reportes**, **Ruta de DTE** e **Impresora**.
3. Pulse **Probar conexión / Guardar configuración**: prueba la conexión y guarda los valores; el mensaje indica el resultado.

### 10.4 Monitoreo

1. Filtre la bitácora técnica por nivel (Todos, Información, Aviso, Error, Crítico).
2. La tabla muestra fecha, nivel, módulo y mensaje; **doble clic** en una fila muestra la traza completa.

> La bitácora consulta la tabla `log_tecnico` (sembrada con registros de demostración si está vacía); no es el archivo de log de la aplicación.

### 10.5 Accesos

1. La tabla de auditoría muestra fecha, acción y detalle de inicios de sesión, fallos, cierres, creación de usuarios, claves temporales y recuperaciones.
2. La tabla de usuarios muestra estado **Bloqueado / Activo / Inactivo**.
3. Seleccione la cuenta y pulse **Desbloquear cuenta / Generar clave temporal**: el sistema emite una clave que **sustituye la contraseña** del usuario; entréguela por canal seguro. La clave **vence a las 24 horas**.

> Al iniciar sesión con la clave temporal, el sistema **obliga a definir una clave propia** (12–128 caracteres) antes de entrar: la temporal queda invalidada en ese momento. La clave nueva **debe ser distinta de la temporal** (el sistema la compara contra la contraseña actual y la rechaza si es idéntica). Si el usuario ya entró y desea cambiar su clave por su cuenta, use la recuperación con código (1.4).

---

## 11. Errores frecuentes y recuperación

| Situación | Qué hacer |
|---|---|
| “Sin período abierto” en el chip | Vaya a **Parametrización → Períodos contables**: si el mes existe, pulse **Abrir / reabrir**; si falta, cree el ejercicio o el período con los formularios de la misma pantalla. |
| “Cuenta bloqueada hasta …” | Espere los 15 minutos o pida a soporte técnico una clave temporal (10.5); vence a las 24 h y exige clave propia al entrar. |
| “Sesión no activa o expirada por inactividad” | Inicie sesión de nuevo; el trabajo en borradores se conserva. |
| El asiento no cuadra | Use **Cuadrar con cuenta** o revise la barra de cuadre; cada línea lleva importe solo en Debe o solo en Haber. |
| “Período cerrado o en revisión” | No se puede capturar/contabilizar en ese mes; reábralo en Parametrización si corresponde. |
| “No se puede cerrar el período mientras existan cédulas de ajuste pendientes” | Resuelva las cédulas pendientes antes de cerrar. |
| Tabla vacía | Borre el filtro de la tabla y lea el mensaje vacío: indica dónde se crea el dato requerido. |
| Error de importes | Use solo números (se admite formato `1,000.00`); las fechas deben caer en el período abierto. |
| Exportación larga | No cierre la ventana; el diálogo de progreso no se puede cancelar hasta terminar. |
| Duda sobre un folio o partida | Use **Comprobante** y **Cadena de evidencia** en el Libro diario, o los reportes del módulo Reportes. |

---

## 12. Qué no hacer (límites del prototipo)

Colibrí es un **prototipo académico de escritorio**. Estas son sus fronteras reales:

### Alcance funcional

- **No es web ni multiusuario:** una instalación local, una sesión a la vez, una sola instancia abierta.
- **No emite ni transmite DTE oficiales:** los documentos son internos (incluso los marcados CCF/FACTURA son simulados); no hay firma electrónica ni envío a Hacienda.
- **Moneda única (USD)** y parámetros fiscales de demostración: revisan con un profesional antes de cualquier uso real.
- **No hay banca, comercio electrónico ni portal de licencias:** no ejecuta pagos ni entrega licencias externamente.

### Datos y parametrización

- **La creación de períodos tiene reglas:** un ejercicio genera sus 12 meses de una vez (solo el mes elegido queda abierto); un período suelto es un mes faltante que **nace cerrado**; el año de ejercicio no puede repetirse.
- **Las políticas de Parametrización no reconfiguran el motor:** folio, cuadre exacto e inmutabilidad son reglas fijas.
- **El catálogo no define dimensiones por cuenta:** auxiliar y centro de costo son campos libres opcionales en las líneas; el catálogo de centros es ayuda de captura, no validación obligatoria.
- **Mapeo de módulos:** crear un evento nuevo solo registra cuentas por defecto; no genera una pantalla ni automatización adicional.

### Operaciones contables

- **Una partida contabilizada es inmutable:** no se edita ni se borra. Para corregirla use **Revertir contabilizada** (crea la partida de ajuste) dentro de un período abierto; **Anular** solo aplica a borrador o aprobada y exige motivo.
- **No omita el motivo** en anulaciones y reversiones: la interfaz lo exige y queda en bitácora.
- **No descuadre a propósito:** el borrador admite descuadre temporal, pero aprobar/contabilizar exige Debe = Haber exacto.
- **No espere que la plantilla de asiento contabilice sola:** siempre genera un borrador revisable.
- **No use UEPS ni promedio como política:** en licencias solo PEPS genera salidas reales; los demás métodos son comparación.
- **El costo del lote se captura manual:** la liberación sí elige producto y versión LIBERADA del catálogo, pero no consolida automáticamente costos capitalizables de producción.
- **Órdenes a la medida con límites:** hay creación, edición (solo en Abierta/En proceso), estados y rentabilidad, pero **no hay entrega que bloquee el costeo**; reconocer costo no verifica entrega.
- **Cálculos de costos son gerenciales:** SaaS por usuario-mes, costos por plan, estimaciones y ABC no dejan distribución contable persistida ni asientos de reparto.
- **Deterioro por lote, no por licencia:** la pantalla ajusta el lote completo contra su valor recuperable y deja la partida en borrador para aprobarla en el Libro diario; la reversa es automática cuando el valor recuperable sube, no hay botón de reversión manual.
- **Auxiliares por dimensión única:** cada auxiliar usa una sola dimensión (cliente, proveedor, lote, orden o centro de costo); no se puede combinar dimensiones ni filtrar por una dimensión específica.

### Sesión y soporte

- **No comparta su sesión ni la deje abierta:** expira a los 15 minutos y la estación es compartida.
- **Guarde el código de recuperación nuevo** que muestra el flujo de recuperación: el anterior queda invalidado.
- **La clave temporal de soporte vence a las 24 horas** y obliga a definir una clave propia al entrar; entréguela por canal seguro y no la comparta.
- **El respaldo es manual:** depende de `pg_dump` en ruta fija; no hay restauración desde la interfaz, ni programación, ni cifrado. Los scripts `-Reset` de `scripts/` reemplazan datos operativos: úselos solo sobre bases de demostración.
- **Monitoreo no es el log de la app:** lee la tabla `log_tecnico`, que se siembra con datos de demostración si está vacía.

---

*Documento alineado con el estado del código en `colibri-desktop`, `colibri-application`, `colibri-domain`, `colibri-persistence` y `colibri-reports`. Para el detalle de casos de uso y límites académicos, consulte `docs/casos-de-uso/CasosDeUso.md`, `docs/casos-de-uso/CasosDeUso_SoporteTecnico.md` y `README.md`.*