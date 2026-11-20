# Plan de pruebas — Colibrí

Lista de verificación de todas las pruebas ejecutables sobre el prototipo, organizada por módulo, sección y apartado, replicando la navegación real de la aplicación. Sirve como guion de aceptación, apoyo de la sección de pruebas del informe y base para la demostración.

## Cómo usar este documento

- **Tipos de prueba:** `F` flujo normal · `A` flujo alternativo · `E` error/borde · `NF` no funcional.
- Cada fila indica el resultado esperado. Si el resultado difiere, registrar el caso como defecto.
- **Datos previos:** ejecutar `scripts\run-colibri-complete.ps1 -Reset` para cargar el escenario integral (empresa Colibrí, períodos, catálogo, terceros, documentos y movimientos del anexo L). Sin ese escenario, varias pruebas de reportes y costos no tienen datos.
- Las pruebas marcadas **[Auto]** ya están cubiertas por pruebas unitarias/de integración del repositorio (`mvnw clean test`); se listan para trazabilidad, no necesitan repetirse a mano salvo verificación visual.

## Pruebas automatizadas existentes (referencia)

| Suite | Cubre |
|---|---|
| `PartidaRulesTest` [Auto] | Descuadre de 1 centavo, partida equilibrada, subcuentas de 6 dígitos, Debe y Haber en la misma línea, reversión no reversible |
| `PartidaServiceIntegrationTest` [Auto] | Semilla del catálogo, descuadre no contabiliza, evento duplicado no doble-contabiliza (idempotencia), cuenta de agrupación rechazada, libros balanceados, semillas idempotentes |
| `IvaCalculatorTest` [Auto] | Venta + IVA, precio con IVA incluido, licencia $240, IVA de importación con DAI |
| `AjustesCalculatorTest` [Auto] | Interés diferido y devengos, depreciación sin sobrepasar base, agotamiento/amortización/incobrables, validación de duraciones |
| `CostosCalculatorTest` [Auto] | Elementos del costo, planilla (aportes, factores, vacaciones), INCAF bajo umbral, costo/precio/utilidad de orden, unidades equivalentes por proceso, costos relevantes, bases inválidas |
| `ResultadosCalculatorTest` [Auto] | Cédula analítica ventas/compras/inventario, conciliación de licencia digital, utilidades, inventario final inconsistente |
| `CosteoGerencialServiceTest` [Auto] | Costo directo vs CIF estándar y variación, CIF por actividad (ABC) |
| `CosteoPorProcesosServiceTest` [Auto] | Cédula PEPS por elemento conciliada, WIP inicial inválido |
| `EstimacionCostosServiceTest` [Auto] | Costo/CIF/precio/margen, ecuaciones de estimación, COCOMO y Putnam, promedio/mediana/variabilidad |
| `InventarioCostoServiceCostingTest` [Auto] | Centavos de lote sin residuo, centavos SaaS sin diferencia de conciliación, comparación PEPS/promedio/UEPS con costo unitario exacto, varianza y rentabilidad |
| `KardexLicenciaIntegrationTest` [Auto] | Liberación, entrega, devolución y revocación registradas en kárdex |
| `PlanillaServiceTest` [Auto] | Planilla del caso con cargas/prestaciones, INCAF bajo umbral, política patronal parametrizada, asiento balanceado, deducciones mayores que salario rechazadas |
| `PasswordHasherTest` [Auto] | Hash sin texto claro, longitud del código de recuperación |
| `AppContextTest` [Auto] | Semilla demo deshabilitada sin bandera explícita |
| `JsonSupportTest` [Auto] | Serialización de documento simulado |
| `NavegacionModulosTest` [Auto] | Contador abre todas las secciones, menú acordeón y modo contraído |
| `DisenoTokensTest` / `BreakpointsTest` [Auto] | Tokens CSS sin literales, IDs de navegación estables, breakpoints |
| `ReportServiceTest` [Auto] | Generación y formato de reportes |
| `SnapshotRunner` [Auto] | Capturas visuales de vistas para revisión |

---

## 1. Autenticación y sesión (transversal)

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| AUT-01 | Login con usuario y contraseña válidos (contador) | F | Entra al tablero con el menú completo del contador |
| AUT-02 | Login con usuario soporte | F | Entra con el menú de soporte (Usuarios, Base de datos, Entorno, Monitoreo, Accesos); no ve módulos contables |
| AUT-03 | Contraseña incorrecta | E | Mensaje "Credenciales inválidas", permanece en login |
| AUT-04 | Usuario inexistente | E | Mismo mensaje genérico (no revela si el usuario existe) |
| AUT-05 | 5 intentos fallidos seguidos | E | Cuenta bloqueada 15 minutos; mensaje indica hora de desbloqueo |
| AUT-06 | Reintentar dentro del bloqueo | E | Sigue bloqueada aunque la contraseña sea correcta |
| AUT-07 | Esperar 15 minutos y reintentar | A | La cuenta se desbloquea sola y permite entrar |
| AUT-08 | Recuperar contraseña con código válido | F | Diálogo pide usuario, código, nueva contraseña y confirmación; al aceptar entra o vuelve a login |
| AUT-09 | Recuperación: código inválido o ya usado | E | Error y el diálogo permanece abierto |
| AUT-10 | Recuperación: contraseña <12 o >128 caracteres | E | Error de validación |
| AUT-11 | Recuperación: confirmación distinta | E | Error de validación |
| AUT-12 | Recuperación exitosa | A | Se muestra un **código de recuperación nuevo**; el anterior queda invalidado (probar reusándolo → AUT-09) |
| AUT-13 | Cerrar sesión desde la barra superior | F | Vuelve a la pantalla de acceso; la sesión queda registrada |
| AUT-14 | Inactividad 15 minutos y luego navegar | E | Al abrir una sección muestra "Sesión no activa o expirada" y devuelve al login |
| AUT-15 | Trabajar seguido más de 15 minutos | A | La sesión sigue viva mientras haya actividad |
| AUT-16 | Abrir una segunda instancia de la app | E | El bloqueo de instancia impide la segunda apertura |

## 2. Ventana principal y navegación (transversal)

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| NAV-01 | Contraer menú con el botón de tres líneas | A | El menú pasa a iconos; hover muestra el nombre; grupos abren lista flotante |
| NAV-02 | Ventana muy angosta | A | El menú se contrae automáticamente |
| NAV-03 | Abrir cualquier sección del menú | F | El contenido cambia, la sección queda marcada y el breadcrumb muestra el módulo |
| NAV-04 | Clic en el módulo del breadcrumb | A | Despliega lista flotante para saltar entre secciones |
| NAV-05 | `Ctrl + K` y buscar "diario" | F | La paleta lista resultados y navega a la sección elegida |
| NAV-06 | Zoom: `Ctrl +` repetido | A | La escala sube de 5 % en 5 % hasta 135 %; botón "Aumentar zoom" hace lo mismo |
| NAV-07 | Zoom: `Ctrl -` repetido | A | La escala baja hasta 85 % |
| NAV-08 | `Ctrl + 0` o botón "Restablecer" | A | Vuelve a 100 % |
| NAV-09 | Alternar tema claro/oscuro | A | La interfaz cambia de tema; botones conservan su significado de color |
| NAV-10 | Filtro de tabla en cualquier listado | F | Escribir texto reduce las filas; limpiar restaura |
| NAV-11 | Tabla con muchas filas | A | Scroll horizontal y vertical disponibles; ninguna columna oculta |
| NAV-12 | Diálogo de confirmación | F | "Aceptar" confirma; "Cancelar" no modifica nada |
| NAV-13 | Chip de período con período abierto | F | Muestra "Mes Año · Abierto" |
| NAV-14 | Chip sin período abierto | A | Muestra "Sin período abierto" |
| NAV-15 | Persistencia | NF | Cerrar y reabrir la app conserva usuarios, catálogos, movimientos y configuraciones |

## 3. Inicio (tablero)

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| INI-01 | Abrir Inicio con datos del escenario | F | KPIs muestran ingresos del mes, utilidad y cartera con variación vs. mes anterior |
| INI-02 | Gráfica de ingresos vs egresos | F | Serie por meses; un mes con volumen extremo se separa como hito sin ocultar los demás |
| INI-03 | Clientes principales, producto más vendido y usuarios SaaS | F | Tablas con datos del período |
| INI-04 | Panel de costos y suscripciones | F | Tarifas por plan, usuarios-mes SaaS y comparativo CIF (las órdenes ya no viven aquí) |
| INI-05 | Bloque "Por hacer" | F | Muestra estado del catálogo, borradores pendientes y alertas operativas |
| INI-06 | Botón "Aprobar catálogo inicial" si el catálogo no está aprobado | A | Habilita el catálogo para movimientos y el aviso desaparece |
| INI-07 | Botón "Ir a registros" | A | Navega al Libro diario |
| INI-08 | Lectura analítica sobre la gráfica de barras | F | Muestra resultado acumulado del rango y conteo de meses superávit/déficit/equilibrio; las fichas mensuales se tiñen según el signo |
| INI-09 | Tarjeta "Órdenes de servicio" dedicada | F | Lista presupuesto vs. costo real, variación % y estado; encabezado resume en curso vs. finalizadas; "—" cuando el presupuesto es 0; búsqueda filtra; estado vacío con pista de dónde crear órdenes |
| INI-10 | Gráfica de dispersión con diagonal y tolerancia | F | Un punto por mes (ingresos X, egresos Y); diagonal de equilibrio, banda ±10 % sombreada, puntos por zona (superávit/tolerancia/déficit), leyenda y pie con conteo; se repinta al redimensionar y al cambiar tema; ambas tarjetas de gráfica van centradas con ancho máximo en vez de estirarse a todo lo ancho |

## 4. Temas contables

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| TEM-01 | Desplegar los 4 grupos de temas | F | Empresa y fundamentos, Registros y estados, Costos y control, Estimación y comercialización |
| TEM-02 | Seleccionar un tema | F | Muestra el concepto y cómo lo automatiza Colibrí |
| TEM-03 | Botón "Abrir \<módulo\>" | A | Navega al módulo operativo que ejecuta ese tema |

## 5. Empresa

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| EMP-01 | Completar razón social, NIT, NRC, actividad CAT-019 y dirección | F | "Guardar perfil corporativo" confirma y los datos persisten tras reabrir |
| EMP-02 | Verificar membrete en un reporte | A | El encabezado del reporte usa los datos guardados |
| EMP-03 | "Elegir logo" con PNG/JPG válido | F | Vista previa actualiza al instante |
| EMP-04 | Elegir archivo no imagen o ruta inválida | E | Error o la vista previa no cambia; no guarda |
| EMP-05 | Firmantes: representante legal, DUI, contador, CVPCPA | F | Se guardan y aparecen en comprobantes |
| EMP-06 | Guardar dos veces seguidas | A | No duplica ni falla |

## 6. Parametrización

### 6.1 Catálogo de cuentas

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| CAT-01 | Crear subcuenta: código 6 dígitos, padre 4 dígitos, denominación, naturaleza | F | Aparece en la tabla como Activa |
| CAT-02 | Código con ≠6 dígitos | E | Alerta y no se guarda |
| CAT-03 | Padre inexistente o sin 4 dígitos | E | Alerta y no se guarda |
| CAT-04 | Código de subcuenta ya existente | E | Error de duplicado |
| CAT-05 | Filtro por código o nombre | A | Reduce la tabla |
| CAT-06 | "Editar denominación" sobre una subcuenta | A | Cambia el nombre y persiste |
| CAT-07 | "Cambiar estado" a Desactivada y luego Bloqueada | A | El estado persiste; la cuenta deja de ofrecerse en combos de captura |
| CAT-08 | Intentar contabilizar en cuenta de agrupación (no movimiento) | E | El sistema rechaza la partida **[Auto: cuentaAgrupadoraNoRecibeMovimiento]** |
| CAT-09 | Cuenta bloqueada en partida nueva | E | No aparece en el combo de cuentas o es rechazada al contabilizar |
| CAT-10 | "Aprobar catálogo inicial" desde Inicio | A | El catálogo queda habilitado para movimientos |
| CAT-11 | Seleccionar una cuenta en la tabla | A | El combo "Nuevo estado" se sincroniza con su estado actual (Activa/Desactivada/Bloqueada) |

### 6.2 Mapeo de módulos

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| MAP-01 | Seleccionar evento VENTA en la tabla | F | Los combos Debe/Haber/IVA muestran sus cuentas actuales |
| MAP-02 | Cambiar la cuenta Debe y "Guardar mapeo seleccionado" | F | Persiste; una venta nueva usa la cuenta nueva en su asiento |
| MAP-03 | Dejar un combo vacío al guardar | A | Conserva la cuenta anterior (no la borra) |
| MAP-04 | "Crear evento": código en minúsculas + Debe y Haber | F | Se crea normalizado a mayúsculas con la descripción |
| MAP-05 | Crear evento sin Debe o sin Haber | E | Error de validación |
| MAP-06 | Crear evento y buscar una pantalla/automatización nueva | A | No aparece: el evento solo registra cuentas por defecto (límite conocido) |
| MAP-07 | Columnas Debe, Haber e IVA de la tabla | A | Cada una muestra el código y el nombre de la cuenta asignada |

### 6.3 Períodos contables

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| PER-01 | Abrir la tabla de períodos | F | Lista los meses de los ejercicios con su estado |
| PER-02 | "Abrir / reabrir" en un período cerrado | A | El chip superior cambia a "Mes · Abierto" y la captura se habilita |
| PER-03 | "Poner en auditoría" | A | Estado "En revisión" |
| PER-04 | "Cerrar período" con confirmación | F | Estado "Cerrado"; el chip lo refleja |
| PER-05 | Cerrar período con cédulas de ajuste pendientes | E | Error "No se puede cerrar el período mientras existan cédulas de ajuste pendientes" |
| PER-06 | Crear ejercicio: año nuevo + mes a abrir | F | Genera los 12 meses; solo el mes elegido queda abierto, los demás cerrados; la tabla muestra la columna Ejercicio |
| PER-07 | Crear ejercicio con año ya existente | E | Rechazado: el año ya existe para la empresa |
| PER-08 | Crear ejercicio con año fuera de 2000-2100 o mes inválido | E | Validación |
| PER-09 | Crear período suelto: ejercicio + mes faltante | F | El mes se agrega al ejercicio y nace cerrado; debe abrirse para capturar |
| PER-10 | Crear período de un mes que el ejercicio ya tiene | E | Rechazado |
| PER-11 | Cerrar período con partida en BORRADOR o APROBADA sin resolver | E | Rechazado por la guardia de cierre (además de la de cédulas) |
| PER-12 | "Activar / desactivar ejercicio" | A | El estado activo persiste; no se puede desactivar un ejercicio con períodos abiertos |

### 6.4 Parámetros fiscales

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| FIS-01 | Completar IVA, retenciones, pago a cuenta y cuentas fiscales | F | "Guardar parámetros fiscales" confirma |
| FIS-02 | Guardar sin seleccionar una cuenta requerida | E | Error que indica qué cuenta falta |
| FIS-03 | Cambiar la tasa de IVA y registrar una venta con IVA incluido | A | El cálculo usa la tasa nueva (sin historial de vigencias) |
| FIS-04 | Guardar valores no numéricos en tasas | E | Validación de formato |

### 6.5 Políticas y secuenciales

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| POL-01 | Seleccionar política (p. ej. tasa ISSS) y "Actualizar valor" | F | Persiste; la planilla siguiente usa el valor nuevo **[Auto: aplicaLaPoliticaPatronalParametrizada]** |
| POL-02 | Escribir valor no numérico donde aplica | E | Validación o rechazo |
| POL-03 | Cambiar máscara de folio y contabilizar | A | Verificar si el folio cambia (límite conocido: el motor usa reglas fijas) |

### 6.6 Centros de costo

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| CC-01 | "Guardar centro" con código y nombre nuevos | F | Aparece en la tabla y en el combo Centro de las líneas de partida |
| CC-02 | Guardar un código existente | A | Actualiza el registro (upsert por código) |
| CC-03 | Desactivar un centro | A | Deja de ofrecerse en la captura de líneas |
| CC-04 | Usar un centro en una línea de partida | A | El centro queda en el comprobante/cadena de evidencia |
| CC-05 | Tabla de centros de costo | F | Columnas Código, Nombre, Descripción, Uso y Estado visibles |
| CC-06 | Seleccionar un centro existente | A | Carga código, nombre, descripción, uso y marca Activo en el formulario |
| CC-07 | Editar descripción/uso y desmarcar Activo, luego "Guardar centro" | A | Persiste los cambios; el centro queda inactivo |
| CC-08 | "Nuevo centro" tras editar | A | Limpia el formulario para crear otro centro |

## 7. Registros

### 7.1 Documentos — terceros

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| TER-01 | Crear tercero con NIT y razón social, marcar Cliente | F | Aparece en la tabla con perfil "Cliente" |
| TER-02 | Marcar Proveedor o ambos | A | La tabla muestra el perfil correcto; ambos perfiles permitidos |
| TER-03 | Guardar sin marcar ningún perfil | A | Perfil "Sin perfil"; las operaciones de venta/compra lo rechazan después |
| TER-04 | Crear tercero con NIT duplicado | E | Error "(¿NIT duplicado?)"; no se inserta |
| TER-05 | Filtro de la tabla de terceros | A | Localiza por NIT o nombre |
| TER-06 | "Editar seleccionado" sobre un tercero de la tabla | F | Carga NIT, razón social y perfiles en el formulario; botón pasa a "Actualizar tercero" y la etiqueta muestra modo edición |
| TER-07 | Modificar razón social/perfiles y "Actualizar tercero" | A | Persiste; NRC y nombre comercial conservan su valor |
| TER-08 | "Nuevo" tras editar | A | Limpia el formulario y vuelve a modo creación |
| TER-09 | Actualizar un tercero cambiando su NIT a uno ya registrado | E | Rechazado por unicidad |
| TER-10 | Desmarcar "Activo" al crear o editar un tercero | A | La marca persiste en la ficha del tercero |

### 7.2 Documentos — operaciones con IVA

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| DOC-01 | Seleccionar cliente + plantilla "Venta licencia" + importe | F | "Previsualizar asiento" muestra líneas que cuadran (por cobrar, ingreso, IVA) |
| DOC-02 | Venta con "IVA incluido" marcado | A | El asiento desglosa base e IVA correctamente del total **[Auto: IvaCalculatorTest]** |
| DOC-03 | Venta con "Cuenta opcional" escrita | A | La línea usa la cuenta indicada en vez de la parametrizada |
| DOC-04 | Venta a tercero sin perfil Cliente | E | Error "El tercero no está marcado como cliente" |
| DOC-05 | "Venta SaaS ingreso" | A | Usa la cuenta de ingreso por defecto del perfil SaaS |
| DOC-06 | Compra a proveedor | F | Asiento: gasto/activo + IVA crédito + cuenta por pagar |
| DOC-07 | Compra con "Cuenta opcional" | A | Reemplaza la cuenta de gasto parametrizada |
| DOC-08 | Compra a tercero sin perfil Proveedor | E | Error "El tercero no está marcado como proveedor" |
| DOC-09 | Cobro con "Retención de IVA sufrida" | A | Sugiere retención = importe × tasa fiscal; editable antes de registrar |
| DOC-10 | Cobro sin retención | F | Asiento de cobro simple |
| DOC-11 | Pago a proveedor | F | Asiento de pago con su cuenta parametrizada |
| DOC-12 | Anticipo SaaS | F | Genera documento y partida; el anticipo queda con saldo aplicable |
| DOC-13 | "Aplicar anticipo a DTE": cliente con anticipo y DTE con saldo | F | Combos filtrados por cliente; importe ≤ saldo → traslado anticipo→DTE contabilizado |
| DOC-14 | Aplicar anticipo: cliente sin anticipos o sin DTEs | E | Combos vacíos; no permite registrar |
| DOC-15 | Aplicar anticipo: importe mayor al saldo | E | El sistema rechaza el importe |
| DOC-16 | Fecha fuera del período abierto | E | Error de período |
| DOC-17 | Importe 0, negativo o no numérico | E | Validación antes de previsualizar |
| DOC-18 | Registrar sin haber previsualizado | A | Funciona igual (la vista previa es opcional) |
| DOC-19 | Vista previa descuadrada ("(NO cuadra)") | E | La marca es visible; no se debe registrar así |
| DOC-20 | Tras registrar | F | Aviso con folio; el documento aparece en la tabla con su estado |

### 7.3 Libro diario — captura de líneas

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| LD-01 | "Nueva" con partida limpia en edición | F | Editor vacío listo para captura |
| LD-02 | Cargar otra partida teniendo cambios sin guardar | A | Pregunta si desea guardarlos antes |
| LD-03 | Partida mínima válida (2 líneas, una Debe y una Haber) | F | "Guardar borrador" la crea aunque esté descuadrada |
| LD-04 | Línea con importe en Debe **y** Haber | E | Línea inválida marcada en rojo **[Auto: ambosLadosEnMismaLineaEsInvalido]** |
| LD-05 | Importe negativo o no numérico | E | Línea marcada como error |
| LD-06 | Cuenta de agrupación en una línea | E | Rechazada al aprobar/contabilizar **[Auto]** |
| LD-07 | Cuenta desactivada/bloqueada | E | No aparece en el combo o es rechazada |
| LD-08 | Fecha contable fuera de todo período | E | La píldora de período lo advierte; aprobar/contabilizar rechaza |
| LD-09 | Editar celda: doble clic / `F2` / `Enter` | A | La celda entra en edición; `Esc` cancela; `Supr` elimina la línea |
| LD-10 | `Enter` en campo Haber | A | Agrega una línea nueva |
| LD-11 | "Cuadrar con cuenta" con diferencia | A | El diálogo propone el lado y monto faltante; al confirmar la partida cuadra |
| LD-12 | "Agregar línea" / "Quitar línea" | F | Las líneas se agregan y eliminan correctamente |
| LD-13 | Barra de cuadre Debe/Haber/Diferencia | F | Totales correctos en vivo; "cuadra" cuando Diferencia = 0 |
| LD-14 | Auxiliar (tercero/lote/orden) y Centro en líneas | A | Se capturan y aparecen en el comprobante |

### 7.4 Libro diario — ciclo de vida de la partida

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| LD-20 | Guardar borrador | F | Estado Borrador; aparece en la lista y persiste tras reabrir |
| LD-21 | Editar un borrador guardado | A | Los cambios se guardan sobre la misma partida |
| LD-22 | "Más → Aprobar" sobre borrador cuadrado | F | Pasa a Aprobada |
| LD-23 | Aprobar un borrador descuadrado | E | La vista exige cuadre antes de aprobar |
| LD-24 | Editar una partida aprobada | A | Vuelve a Borrador (regla del modelo) |
| LD-25 | "Contabilizar" (`Ctrl+Enter`) sobre borrador | F | Auto-aprueba, exige cuadre, pide confirmación y asigna folio → Contabilizada |
| LD-26 | Contabilizar una descuadrada | E | Error de cuadre; no registra **[Auto: descuadreDeUnCentavoNoContabiliza]** |
| LD-27 | Contabilizar en período cerrado o en revisión | E | "Período cerrado o en revisión: no se puede contabilizar" |
| LD-28 | Contabilizar dos veces el mismo evento | E | Idempotente: no crea duplicado **[Auto: eventoDuplicadoNoDobleContabiliza]** |
| LD-29 | Intentar editar/anular una contabilizada | E | Inmutable: no se edita ni se anula |
| LD-30 | "Más → Anular" en borrador o aprobada con motivo | F | Estado Anulada con motivo registrado |
| LD-31 | Anular sin motivo | E | La interfaz exige el motivo |
| LD-32 | Anular una contabilizada | E | "Solo se pueden anular partidas en borrador o aprobadas" |
| LD-33 | "Más → Revertir contabilizada" con fecha y motivo | F | Crea partida AJ de reversión contabilizada; la original queda Revertida; ambas cuadran |
| LD-34 | Revertir una no contabilizada | E | "Solo se revierten partidas contabilizadas" |
| LD-35 | Revertir una ya revertida | E | No se puede revertir dos veces **[Auto]** |
| LD-36 | Revertir con fecha fuera de período abierto | E | Error de período |

### 7.5 Libro diario — acciones complementarias

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| LD-40 | "Más → Duplicar partida" | A | Carga las líneas como borrador nuevo sin guardar; "Guardar borrador" lo persiste |
| LD-41 | "Más → Importar CSV" con archivo válido | A | Genera un borrador cargado en el editor para revisar y guardar |
| LD-42 | Importar CSV malformado o con cuentas inválidas | E | Error o líneas marcadas; no contabiliza solo |
| LD-43 | "Comprobante" | F | Diálogo con el asiento y membrete; opciones Imprimir y Guardar PDF |
| LD-44 | Guardar comprobante en PDF | A | Genera el archivo y se abre/visualiza |
| LD-45 | "Cadena de evidencia" | F | Muestra documento, evento, folio, bitácora, referencia y líneas de la partida |
| LD-46 | "Exportar diario filtrado" con rango Desde/Hasta | F | Genera `.xlsx` con el diario del rango y fila de totales |
| LD-47 | Doble clic en una partida reciente | A | La carga en el editor (solo editable si es borrador/aprobada) |
| LD-48 | Lista de recientes y filtros Estado/Tipo/Desde/Hasta | A | Filtran correctamente; máximo 200 partidas |
| LD-49 | Búsqueda dentro de la tabla de partidas | A | Reduce filas por texto |
| LD-50 | Inspector de partida seleccionada | A | Aparece junto a la tabla de partidas (debajo de ella), no al final del formulario |

### 7.6 Plantillas de asiento recurrente

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| PLT-01 | "Nueva plantilla" con ≥2 líneas cuadradas | F | Se guarda; el código se sugiere desde tipo+nombre (editable) |
| PLT-02 | Plantilla descuadrada | E | El diálogo muestra el error de cuadre |
| PLT-03 | Línea con Debe y Haber a la vez | E | Rechazada en el diálogo |
| PLT-04 | "Ver líneas" sobre plantilla seleccionada | A | Muestra el detalle de sus líneas |
| PLT-05 | "Generar borrador del mes" con período abierto | F | Crea un BORRADOR con fecha del fin del período; **no** contabiliza |
| PLT-06 | Generar borrador sin período abierto | A | Pide la fecha manualmente |
| PLT-07 | Verificar en Libro diario que el borrador de plantilla está pendiente | A | Estado Borrador, editable y contabilizable manualmente |
| PLT-08 | Código de plantilla duplicado | E | Rechazo por unicidad del código |

### 7.7 Planilla y cargas laborales

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| PLN-01 | Completar fecha, empleados, salario, deducciones y prestaciones | F | "Previsualizar cálculo y asiento" muestra resumen (líquido, cargas patronales, prestaciones, costo total) y asiento cuadrado **[Auto: PlanillaServiceTest]** |
| PLN-02 | Tasas ISSS/previsional | A | Se muestran de solo lectura desde Parametrización |
| PLN-03 | Deducciones mayores que el salario | E | Rechazado **[Auto: impideDeduccionesMayoresQueElSalario]** |
| PLN-04 | INCAF bajo el umbral de trabajadores | A | No se aplica **[Auto]** |
| PLN-05 | Cambiar política patronal en Parametrización y recalcular | A | La planilla usa la política nueva **[Auto]** |
| PLN-06 | "Contabilizar planilla" y confirmar | F | Registra una partida AJ única con folio; conserva tasas e importes usados |
| PLN-07 | Previsualizar sin contabilizar | A | No modifica libros (comparar balanza antes/después) |
| PLN-08 | Empleados o salario en 0 / negativos | E | Validación |
| PLN-09 | Fecha fuera del período abierto | E | Error de período |

### 7.8 Ajustes y cierre de período

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| AJU-01 | "Devengo de suscripciones" con importe | F | Partida registrada con folio |
| AJU-02 | "Consumo de nube", "Consumo de alquiler" y "Gasto devengado" | F | Cada uno registra su partida |
| AJU-03 | Importe 0 o negativo en un devengo | E | Validación |
| AJU-04 | "Depreciar activos fijos" | F | Genera cuota línea recta del mes; ejecutarla dos veces no duplica (idempotente) |
| AJU-05 | "Amortizar intangibles" | F | Genera amortización; idempotente igual que depreciación |
| AJU-06 | "Liquidar IVA del período" | F | Cruza débito y crédito fiscal del mes; genera partida de liquidación con remanente arrastrable |
| AJU-07 | Liquidar IVA dos veces el mismo rango | A | Idempotente por clave `AJ-A5-desde-hasta`: devuelve la partida existente |
| AJU-08 | Liquidar IVA sin débito pendiente | E | Error "No hay IVA débito pendiente para liquidar" (con aviso de crédito remanente si aplica) |
| AJU-09 | "Cerrar período con ajustes automáticos" | F | Ejecuta depreciación + amortización + IVA y luego cierra; muestra resumen |
| AJU-10 | "Cerrar período (sin ajustes)" | A | Cierra directamente tras confirmación |
| AJU-11 | Cerrar con cédulas pendientes | E | Rechazado por cédulas de ajuste pendientes |
| AJU-12 | Intentar capturar en el período ya cerrado | E | Todas las capturas/contabilizaciones se rechazan; chip muestra Cerrado |
| AJU-13 | "Abrir / reabrir" el período cerrado | A | Reactiva la captura |
| AJU-14 | Fecha contable fuera del período abierto | E | Error de período |
| AJU-15 | Cerrar período con partida en BORRADOR sin resolver | E | Rechazado: resolver (contabilizar o anular) la partida primero |
| AJU-16 | Cerrar período con partida APROBADA sin contabilizar | E | Rechazado igual que borrador |
| AJU-17 | Ajustes manuales del mes | A | Seis controles en rejilla de 4 columnas; cada botón queda debajo de su propio campo de importe y cada acción muestra texto explicativo |
| AJU-18 | Ajustes automáticos y cierre | A | Cada acción (depreciar, amortizar, liquidar IVA, cierres) muestra título, explicación y botón accesible |

## 8. Inventario y costos

### 8.1 Inventario → Digitales

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| DIG-01 | Liberar lote: fecha, código, unidades, costo unitario | F | Lote creado; "Costo total" calculado en vivo = unidades × costo |
| DIG-02 | Unidades o costo ≤ 0 | E | Validación |
| DIG-03 | Código de lote duplicado | E | Rechazo por unicidad |
| DIG-04 | Vender licencia PEPS: cliente + precio neto | F | Toma la licencia disponible más antigua, calcula IVA y costo de ventas, muestra folio |
| DIG-05 | Venta con varios lotes disponibles | A | Consume primero el lote más antiguo (verificar en kárdex) |
| DIG-06 | Vender sin unidades disponibles | E | Error de sin saldo |
| DIG-07 | Vender sin cliente seleccionado | E | Error/validación |
| DIG-08 | "Devolver al inventario" una licencia entregada | F | La unidad reingresa disponible por PEPS; si la venta era trazable, revierte el asiento y muestra folio de reversa |
| DIG-09 | Devolver licencia sin partida trazable | A | Aviso de que el ajuste del ingreso es manual |
| DIG-10 | "Revocar derecho" | A | El derecho queda revocado y la unidad **NO** reingresa (confirmación roja) |
| DIG-11 | Tarjeta de existencias | F | Lista lotes con unidades disponibles y costo unitario correctos |
| DIG-12 | Motivo opcional en devolución/revocación | A | Queda registrado en el kárdex |
| DIG-13 | Aplicar deterioro: lote + valor recuperable menor al costo + fecha | F | Genera partida de ajuste en borrador con folio; mostrar en Libro diario |
| DIG-14 | Aprobar y contabilizar la partida de deterioro | A | Afecta el mayor; la pérdida = costo bruto − valor recuperable menos deterioro previo |
| DIG-15 | Aplicar deterioro dos veces con el mismo valor | A | Idempotente por período: devuelve la partida existente, no duplica |
| DIG-16 | Valor recuperable mayor al costo bruto del lote | E | Rechazado |
| DIG-17 | Valor recuperable negativo o no numérico | E | Validación |
| DIG-18 | Subir el valor recuperable en un período posterior | A | El sistema revierte automáticamente el deterioro acumulado |
| DIG-19 | Tarjeta "Inventario perpetuo PEPS" | F | Muestra las cuentas del mapeo (código + nombre) y el botón "Ver kárdex de licencias" navega a Kárdex |
| DIG-20 | Fecha del deterioro | F | El selector de fecha del deterioro es independiente y visible (regresión: antes desaparecía por nodo compartido) |

### 8.2 Inventario → Kárdex

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| KAR-01 | Seleccionar un lote | F | Carga sus movimientos cronológicos: fecha, tipo, licencia, entrada/salida, costo unitario, importe, saldos de unidades y valor |
| KAR-02 | Ejecutar liberación/venta/devolución/revocación y refrescar | A | Los 4 tipos de movimiento aparecen en orden **[Auto: KardexLicenciaIntegrationTest]** |
| KAR-03 | Filtro de la tabla | A | Busca por fecha, tipo, licencia o folio |
| KAR-04 | Conciliar saldo final del kárdex con existencias | A | Unidades y valor cuadran |
| KAR-05 | Scroll horizontal del kárdex | A | Columnas de saldos y valores accesibles |
| KAR-06 | Seleccionar un lote con movimientos | F | La tabla se carga sola al elegirlo (ya no hay botón "Actualizar") |
| KAR-07 | Filtros Desde/Hasta y Tipo de movimiento | A | Reducen los movimientos mostrados; la línea de totales cuadra con lo filtrado |
| KAR-08 | Lote sin movimientos en licencia_movimiento | A | Tabla vacía manejada sin error; el mensaje/estado vacío es claro |

### 8.3 Inventario → Servicios / Accesos

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| SRV-01 | Crear plan: código, nombre, modalidad, facturación, tarifa, cuenta ingreso | F | "Guardar plan" lo registra |
| SRV-02 | Guardar plan con código existente | A | Actualiza el registro |
| SRV-03 | Modalidades: Licencia, Suscripción SaaS, Proyecto a la medida, Servicio | A | Las 4 disponibles en el combo |
| SRV-04 | Facturaciones: Por usuario y mes, Por plan, Por hito, Por entrega, Pago único | A | Las 5 disponibles |
| SRV-05 | Cuenta de ingreso por defecto 410402 | A | Se precarga; editable |
| SRV-06 | Acumular costo a plan de modalidad SERVICIO | F | Panel muestra MOD, directos, CIF y total acumulado |
| SRV-07 | Plan que no es SERVICIO en el selector de costos | E | No aparece o no permite acumular |
| SRV-08 | Cantidad o tarifa inválida | E | Validación |
| SRV-09 | Combo "Cuenta de ingreso" del plan | A | Solo lista cuentas de ingreso activas que admiten movimiento |
| SRV-10 | Botón "Ir al catálogo de cuentas" | A | Navega a Parametrización → Catálogo de cuentas |
| SRV-11 | Editar plan: seleccionar fila → "Editar seleccionada" → ajustar → "Guardar plan" | A | Persiste cambios (incluido "Plan activo") con bitácora; "Nuevo plan" limpia el formulario |
| SRV-12 | Columna Cuenta/Estado en tabla de planes | A | Muestra código + nombre de la cuenta de ingreso y el estado Activo/Inactivo |

## 9. Costos

### 9.1 Costos → A la medida

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| MED-01 | Panel de orden principal | F | Muestra costo acumulado, precio, margen % y alerta si margen < 35 % |
| MED-02 | "Cargar costos de ejemplo ($1,800)" sobre OS2026001 | A | Carga el escenario demo |
| MED-03 | "Cargar costos de ejemplo" sobre otra orden | E | Rechazado (solo funciona para OS2026001) |
| MED-04 | "Registrar MOD" con horas y tarifa | F | Acumula MOD en la orden indicada |
| MED-05 | Horas o tarifa inválidas | E | Validación |
| MED-06 | "Reconocer costo de la orden" | F | Descarga los componentes acumulados como costo y muestra el folio |
| MED-07 | Reconocer con código de orden inexistente | E | Error |
| MED-08 | Reconocer dos veces la misma orden | A | Verificar comportamiento de duplicidad |
| MED-09 | Crear orden: código, descripción, presupuesto, precio y cliente | F | Aparece en la tabla en estado Abierta |
| MED-10 | Crear orden con código duplicado | E | Rechazado por unicidad |
| MED-11 | Crear orden con importes negativos o texto no numérico | E | Validación |
| MED-12 | Crear orden con un tercero que no es cliente | E | Rechazado (el combo solo lista clientes, pero el servicio valida) |
| MED-13 | Seleccionar una orden en la tabla | A | Su código se carga en el panel de costeo; Estado destino solo ofrece transiciones válidas |
| MED-14 | Ciclo ABIERTA → EN_PROCESO → ENTREGADA → CERRADA | F | Cada transición persiste y queda en bitácora |
| MED-15 | Transición inválida (ABIERTA → CERRADA, CANCELADA desde CERRADA o ENTREGADA) | E | Rechazado con el ciclo explicado; el combo no ofrece CANCELADA en Entregada |
| MED-16 | Tabla de rentabilidad | F | Columnas: código, descripción, estado, presupuesto, precio, costo, margen y margen % correctos |
| MED-17 | Reconocer costo NO exige entrega previa | A | Límite conocido: la entrega es operativa, no bloquea el costeo |
| MED-18 | "Editar seleccionada" sobre orden ABIERTA o EN_PROCESO → ajustar → "Guardar cambios" | F | Persiste descripción/presupuesto/precio/cliente; queda en bitácora con valores anteriores |
| MED-19 | Intentar editar orden ENTREGADA/CERRADA/CANCELADA | E | No permitido por el servicio; el botón no aplica |

### 9.2 Costos → Licencias descargables

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| LIC-01 | Lista de existencias de lotes | F | Muestra lotes y unidades disponibles |
| LIC-02 | "Comparar PEPS / promedio / UEPS" con N unidades | F | Tabla con costo de salida y saldo posterior para los 3 métodos **[Auto: InventarioCostoServiceCostingTest]** |
| LIC-03 | Verificar que solo PEPS genera salida real | A | Tras una venta real, el costo de ventas usa PEPS; promedio/UEPS no crean asientos |
| LIC-04 | Unidades para análisis inválidas (0, negativo, no numérico) | E | Validación |

### 9.3 Costos → Suscripción SaaS

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| SaaS-01 | Selector de plan | F | Solo lista planes con modalidad SaaS; sin plan activo muestra mensaje |
| SaaS-02 | "Guardar usuarios-mes" con fecha de corte | F | Persiste los usuarios por mes |
| SaaS-03 | "Calcular costo por plan" | F | Desglose: usuarios del plan/total, alojamiento, soporte, infraestructura, total asignado y costo por usuario-mes |
| SaaS-04 | Calcular sin usuarios-mes guardados | A | Verificar comportamiento (ceros o mensaje) |
| SaaS-05 | Verificar que el cálculo no genera asiento | A | Límite conocido: es apoyo gerencial, no distribución contable persistida |
| SaaS-06 | Calcular sin gastos registrados en cuentas CIF (510201-510203) | A | Alojamiento/soporte/infraestructura salen $0.00 y el aviso indica el origen de los datos |

### 9.4 Costos → Procesos y CIF

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| CIF-01 | "Cargar cédula del período" con cédula existente | A | Rellena el formulario con sus datos |
| CIF-02 | Balance físico: inicial + iniciadas − final = terminadas | F | La conciliación cuadra; si no, error **[Auto: CosteoPorProcesosServiceTest]** |
| CIF-03 | WIP inicial que queda al final (PEPS simplificado) | E | Rechazado **[Auto]** |
| CIF-04 | Avances de Material/Conversión fuera de 0–1 | E | Validación |
| CIF-05 | "Guardar cédula y calcular" | F | Muestra unidades equivalentes, costo unitario y totales PEPS (aplicado), promedio y UEPS (comparación) |
| CIF-06 | "Guardar análisis CIF" con estimado/base real | F | Muestra tasa, CIF aplicado, real y variación (equilibrado/subaplicado/sobreaplicado) |
| CIF-07 | "Aplicar CIF a orden" con código, base y descripción | F | Registra la aplicación con partida trazable y folio |
| CIF-08 | "Cerrar variación CIF" sin cuenta 5201xx | E | Requiere cuenta CIF real válida y confirmación |
| CIF-09 | Cerrar variación con cuenta 5201xx válida | F | Llevada a costo de proyectos con partida y folio |

### 9.5 Costos → Estimación y políticas

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| EST-01 | Seleccionar cada uno de los 9 métodos | A | Los campos de parámetros se adaptan al método (juicio de expertos, analogía, puntos de función, COSMIC, COCOMO, desgaste de trabajo, líneas de código, tres puntos, SLIM/Putnam) |
| EST-02 | "Calcular costo y precio unitario" | F | Muestra esfuerzo en horas, costo directo, CIF, costo total, precio unitario, margen y recomendación **[Auto: EstimacionCostosServiceTest]** |
| EST-03 | Margen por debajo del objetivo | A | La recomendación/alerta de margen se muestra |
| EST-04 | "Comparar métodos de costeo" | F | Directo, estándar con variación y ABC (calidad, infraestructura) **[Auto: CosteoGerencialServiceTest]** |
| EST-05 | "Comparar estimado y real" con código de orden | F | Presupuesto vs. real, variación, índice de ejecución, ahorro/sobrecosto, rentabilidad y diagnóstico |
| EST-06 | "Comparar estimación promedio con históricos" | F | Estadísticos (promedio, mediana, rango, desviación, variabilidad, varianza) y tabla estimado vs. histórico por rubro |
| EST-07 | Una sola estimación en la comparación | A | Variabilidad cero **[Auto]** |
| EST-08 | Bases/proporciones inválidas en estimación | E | Rechazadas **[Auto: rechazaBasesYProporcionesInvalidas]** |
| EST-09 | Panel "Políticas de costos y ventas" | A | Texto de referencia de solo lectura |
| EST-10 | Catálogo Anexo M con filtro | A | Filtra los 122 instrumentos por texto; son referencia documental |
| EST-11 | Verificar que nada de esta pantalla genera partidas | A | Límite conocido: cálculos gerenciales sin asiento |
| EST-12 | Tabla de políticas/procesos: columna "Breve descripción" | A | Los textos largos se muestran con saltos de línea, no en una sola línea interminable |

## 10. Reportes

### 10.1 Reportes → Obligatorios

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| REP-01 | Generar Libro diario en el rango del período | F | Tabla cronológica con filas por partida y totales Debe/Haber; "N filas · período" |
| REP-02 | Libro mayor | F | Saldos por cuenta con movimientos |
| REP-03 | Balanza de comprobación | F | Debe total = Haber total **[Auto: librosPresentanCuentasTHojaDeTrabajoYEstadosBalanceados]** |
| REP-04 | Cuenta T | F | Requiere seleccionar cuenta; muestra cargos, abonos y saldo |
| REP-05 | Cuenta T sin cuenta seleccionada | E | Validación/pide cuenta |
| REP-06 | Hoja de trabajo | F | Conecta saldos iniciales con estados finales; todos los pares cuadran **[Auto]** |
| REP-07 | Situación financiera | F | Activos, pasivos y patrimonio balanceados (A = P + C) |
| REP-08 | Resultados | F | Ventas netas → costo de ventas → utilidad bruta → gastos → utilidad neta |
| REP-09 | Libro IVA compras | F | Movimientos de IVA crédito fiscal del rango |
| REP-10 | Libro IVA ventas CCF | F | Ventas con CCF del rango |
| REP-11 | Libro IVA ventas consumidor final | A | Excluye CCF y documentos ANULADO; sin solapamiento con el libro CCF |
| REP-12 | Cambios en el patrimonio y Flujos de efectivo | F | Seleccionables en el combo Formato; generan su estado con el rango elegido |
| REP-13 | Alcance "Libro completo" vs "Caso D del informe" | A | Resultados distintos según el alcance elegido |
| REP-14 | Rango Desde/Hasta personalizado | A | El reporte cubre exactamente ese rango |
| REP-15 | Exportar Excel | F | `.xlsx` descargado coincide con la tabla generada |
| REP-16 | Exportar PDF | F | PDF con membrete de Empresa y formato correcto |
| REP-17 | Reporte ancho (diario/mayor) en PDF | A | Sale en A3 apaisado |
| REP-18 | Reporte sin datos en el rango | E | Mensaje de tabla vacía / 0 filas |

### 10.2 Reportes → Auxiliares

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| AUX-01 | Auxiliar de clientes | F | Movimientos con dimensión (cliente) y saldo acumulado por dimensión; totales Debe/Haber |
| AUX-02 | Auxiliar de proveedores | F | Ídem |
| AUX-03 | Auxiliar de inventario digital | F | Movimientos de licencias/lotes |
| AUX-04 | Auxiliar de órdenes de servicio | F | Movimientos de órdenes |
| AUX-05 | Auxiliar de gastos por centro de costo | F | Movimientos agrupados por centro |
| AUX-06 | Exportar auxiliar a Excel/PDF | A | Funciona igual que obligatorios |
| AUX-07 | Saldo acumulado del auxiliar | A | El saldo inicial del rango se calcula desde el historial; cada fila muestra el saldo corrido por dimensión |
| AUX-08 | Verificar límites conocidos | A | Una sola dimensión por auxiliar; no hay selector ni combinación de dimensiones |
| AUX-09 | Misma dimensión en varias cuentas (p. ej. NIT en 110201 y 110202) | A | El saldo acumulado combina ambas cuentas; contrastar con Libro mayor/Balanza si se filtra una sola cuenta |

### 10.3 Reportes → Societarios

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| SOC-01 | Abrir la sección | F | Vista informativa de actas, accionistas y capital |
| SOC-02 | Verificar ausencia de acciones de registro/emisión | A | Límite conocido: no emite libros societarios |

### 10.4 Exportaciones avanzadas

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| EXP-01 | "Exportar paquete ZIP" con rango de fechas | F | `.zip` con los 8 libros (Diario, Mayor, Balanza, Hoja de trabajo, Situación financiera, Resultados, Cambios en el patrimonio, Flujos de efectivo) en PDF y XLSX |
| EXP-02 | Estructura interna del ZIP | A | Archivos organizados por reporte y período |
| EXP-03 | "Exportar libros × períodos": marcar libros y períodos | F | Diálogo permite checkboxes, rango de fechas para filtrar períodos y botones Todos/Ninguno |
| EXP-04 | Cuenta T en exportación múltiple | A | No se ofrece (requiere cuenta específica) |
| EXP-05 | Elegir carpeta destino | F | Genera `Exportacion_Del_<rango>_<fecha>` con subcarpeta por libro y Excel+PDF por período (no es ZIP) |
| EXP-06 | Exportación larga en curso | A | El diálogo de progreso no se puede cerrar hasta terminar |
| EXP-07 | Carpeta destino sin permisos o ruta inválida | E | Error comprensible |
| EXP-08 | "Exportar diario filtrado" desde Libro diario | A | Excel del diario con totales (distinto del paquete) |

## 11. Soporte técnico

### 11.1 Usuarios

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| USR-01 | "Nuevo usuario" con datos válidos | F | Se crea; se muestra un código de recuperación de un solo uso para entregar |
| USR-02 | Usuario <3 o >64 caracteres o con caracteres inválidos | E | Validación |
| USR-03 | Contraseña inicial <12 o >128 | E | Validación |
| USR-04 | Asignar rol Contador y rol Soporte | A | Cada uno entra a su menú correspondiente |
| USR-05 | Editar nombre, correo y estado activo | A | "Guardar cambios" persiste |
| USR-06 | Intentar desactivar la cuenta en uso | E | Rechazado |
| USR-07 | Intentar dejar 0 usuarios activos | E | Rechazado |
| USR-08 | Verificar que rol y contraseña no se editan en el formulario | A | Solo nombre/correo/activo son editables |
| USR-09 | Login del usuario nuevo con su contraseña inicial | F | Entra con el rol asignado |

### 11.2 Base de datos

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| BD-01 | Ver "Último respaldo" | F | Muestra fecha del respaldo previo (o vacío si nunca se hizo) |
| BD-02 | "Generar respaldo" con confirmación | F | Crea copia con `pg_dump`; aviso muestra la ruta del archivo |
| BD-03 | Generar respaldo sin confirmar | A | No hace nada |
| BD-04 | `pg_dump` no disponible o ruta incorrecta | E | Error visible (la ruta esperada es `C:\Program Files\PostgreSQL\18\bin\pg_dump.exe`) |
| BD-05 | Verificar que es respaldo manual | A | No hay programación, cifrado, incremental ni restauración desde la interfaz (límite conocido) |

### 11.3 Entorno

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| ENT-01 | Revisar URL JDBC, usuario y rutas de reportes/DTE | F | Muestra la configuración actual |
| ENT-02 | "Probar conexión / Guardar configuración" con datos válidos | F | Prueba la conexión y guarda |
| ENT-03 | Contraseña vacía | A | Conserva la contraseña actual |
| ENT-04 | URL JDBC inválida o base apagada | E | Error de conexión; no guarda (verificar) |
| ENT-05 | Configurar ruta de reportes, ruta DTE e impresora | A | Persiste para exportaciones e impresión |

### 11.4 Monitoreo

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| MON-01 | Filtro por nivel: Todos, Información, Aviso, Error, Crítico | F | La tabla filtra por nivel |
| MON-02 | Tabla de registros | F | Fecha, nivel, módulo y mensaje de `log_tecnico` |
| MON-03 | Doble clic en una fila | A | Muestra la traza completa |
| MON-04 | Base sin registros técnicos | A | Se siembran datos de demostración (límite conocido: no lee el archivo de log real) |

### 11.5 Accesos

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| ACC-01 | Tabla de auditoría | F | Registra inicios de sesión, fallos, cierres, creación de usuarios, claves temporales y recuperaciones |
| ACC-02 | Estados de cuenta: Bloqueado / Activo / Inactivo | A | Coinciden con el estado real tras AUT-05 y USR-06 |
| ACC-03 | "Desbloquear cuenta / Generar clave temporal" | F | Emite una clave que sustituye la contraseña y vence a las 24 h; queda auditada |
| ACC-04 | Entrar con clave temporal vigente | F | El sistema obliga a definir clave propia (12-128, confirmación) antes de continuar; la temporal queda invalidada |
| ACC-05 | Entrar con clave temporal vencida (>24 h) | E | Rechazado con mensaje de vencimiento; soporte debe emitir otra |
| ACC-06 | Cambiar clave por recuperación con código tras clave temporal | A | La marca temporal se limpia; el código anterior queda invalidado |
| ACC-07 | Definir como clave nueva la misma clave temporal | E | Rechazado: "La nueva contraseña debe ser distinta de la actual" |
| ACC-07 | La acción de soporte queda auditada | A | Aparece en la tabla de accesos |
| ACC-08 | Soporte intenta ver partidas o saldos | E | No tiene acceso (rol separado) |

## 12. No funcionales y transversales

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| NF-01 | Persistencia completa | NF | Cerrar app y reabrir: usuarios, catálogos, períodos, movimientos y configuración intactos |
| NF-02 | Sesión compartida | NF | La estación es de un solo usuario; la sesión expira por inactividad |
| NF-03 | Listas largas | NF | La tabla de 200 partidas recientes no congela la UI |
| NF-04 | Mensajes de error | NF | Todos en español y comprensibles; los de dominio vienen de `DomainException` |
| NF-05 | Fechas y zona horaria | NF | `LocalDate`/`LocalDateTime` consistentes; verificar que no haya corrimientos de día |
| NF-06 | Precisión monetaria | NF | `BigDecimal`/`NUMERIC(18,2)`; un centavo descuadra y no contabiliza **[Auto]** |
| NF-07 | Impresión | NF | Comprobante y reportes imprimen con membrete; PDF legible |
| NF-08 | Arranque en frío | NF | `Iniciar-Colibri.ps1` compila si falta build; errores fatales en diálogo y `%LOCALAPPDATA%\Colibri\logs` |
| NF-09 | Scripts de escenario | A | `run-colibri-complete.ps1 -Reset` carga el anexo L de forma idempotente **[Auto: semillasCoincidenConCasoDYSonIdempotentes]** |
| NF-10 | Datos demo vs reales | A | La semilla demo solo se activa con bandera explícita **[Auto: AppContextTest]** |

## 13. Casos de borde combinados (para la defensa)

| ID | Prueba | Tipo | Resultado esperado |
|---|---|---|---|
| CB-01 | Cerrar período y luego intentar Documentos/Planilla/Ajustes en ese mes | E | Todas las operaciones rechazadas por período |
| CB-02 | Revertir una partida del mes cerrado usando fecha del mes abierto | A | La reversión se contabiliza en el período abierto |
| CB-03 | Venta con anticipo aplicado parcialmente y luego otro cobro | A | Los saldos de anticipo/DTE se actualizan correctamente |
| CB-04 | Liberar lote → vender → devolver → vender otra vez | A | El kárdex y existencias reflejan cada paso; PEPS toma la unidad devuelta según su fecha |
| CB-05 | Planilla + depreciación + amortización + IVA en el mismo mes antes de cerrar | F | Los 4 ajustes conviven sin duplicarse y el cierre los incluye en el resumen |
| CB-06 | Exportar ZIP de un rango que incluye períodos cerrados y abiertos | F | El paquete refleja solo los movimientos del rango |
| CB-07 | Cuenta nueva creada después de generados los reportes | A | Los reportes siguientes la incluyen; los anteriores no cambian (inmutabilidad) |
| CB-08 | Soporte desbloquea al contador mientras este tiene sesión activa | A | La clave temporal sustituye la contraseña en el próximo login |
| CB-09 | Dos partidas con el mismo concepto y fecha | A | Folios distintos por secuencial; ambas válidas |
| CB-10 | Escenario completo `run-colibri-complete.ps1 -Reset` dos veces | A | La segunda carga no duplica datos (idempotente) |

---

## Resumen de cobertura

| Módulo | Pruebas |
|---|---|
| Autenticación y sesión | 16 |
| Ventana principal y navegación | 15 |
| Inicio | 10 |
| Temas contables | 3 |
| Empresa | 6 |
| Parametrización | 45 |
| Registros | 106 |
| Inventario | 40 |
| Costos | 50 |
| Reportes | 37 |
| Soporte técnico | 32 |
| No funcionales | 10 |
| Casos de borde combinados | 10 |
| **Total** | **380** |

De estas, **21 suites automatizadas** (≈60 pruebas unitarias/de integración) ya corren con `mvnw clean test`; el resto son verificaciones manuales de UI y de extremo a extremo que alimentan la sección de pruebas del informe y el guion de demostración.
