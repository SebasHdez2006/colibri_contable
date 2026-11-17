# Casos de uso vigentes del prototipo Colibri

Este documento describe las funciones expuestas por la aplicación de escritorio Colibri en su estado actual. El prototipo usa JavaFX y PostgreSQL local; sus operaciones se orientan al registro y análisis contable académico. No genera, firma ni transmite Documentos Tributarios Electrónicos oficiales, ni sustituye la revisión profesional, tributaria o societaria.

## Módulo 1: Configuración de la Entidad y Empresa

### `CU-EMP-01`: Configurar Perfil Corporativo e Información Fiscal

* **Descripción:** Permite al Contador registrar y actualizar datos de empresa, actividad económica, dirección, logo y firmantes que aparecen en comprobantes y reportes internos.


* **Entradas:** Datos generales, datos fiscales que se desean mostrar, imagen de logo y responsables.


* **Resultado:** Perfil almacenado para encabezados de reportes y comprobantes internos. Los datos no acreditan inscripciones, NIT, NRC ni registros profesionales reales, y no activan una integración con Hacienda.



---

## Módulo 2: Parametrización Contable, Fiscal y Operativa

### `CU-PAR-01`: Administrar Catálogo de Cuentas y Subcuentas

* **Descripción:** Muestra el catálogo existente y permite crear subcuentas afectables de seis dígitos bajo una cuenta padre de cuatro dígitos. También permite cambiar la denominación y el estado de una cuenta seleccionada.


* **Entradas:** Código de seis dígitos, código de cuenta padre, nombre, naturaleza y estado.


* **Resultado:** La subcuenta queda disponible para partidas si está activa y admite movimientos. El modelo actual no configura por cuenta el origen manual o automático, exclusividades de asiento ni dimensiones obligatorias; las líneas de partida sólo exponen los campos opcionales `auxiliar` y `centroCosto`.



### `CU-PAR-02`: Configurar Perfiles de Contabilización

* **Descripción:** Permite consultar, crear y actualizar perfiles de contabilización por evento, con cuenta Debe, cuenta Haber y cuenta de IVA opcional.


* **Entradas:** Código y etiqueta del evento, y cuentas afectables de cargo, abono e IVA.


* **Resultado:** Los perfiles quedan validados y pueden ser usados por los flujos que reconocen ese evento, como documentos operativos y anticipos. Crear un evento nuevo no crea por sí mismo una pantalla ni una automatización adicional, y algunos ajustes especializados usan su propia configuración o cuentas del activo registrado.



### `CU-PAR-03`: Gestionar Períodos Contables Existentes

* **Descripción:** Permite consultar los períodos cargados y cambiar su estado a abierto, en revisión o cerrado. El cierre valida las cédulas pendientes y el cierre desde Ajustes puede ejecutar depreciación, amortización y liquidación de IVA antes de bloquear la captura.


* **Entradas:** Período seleccionado y acción de abrir o reabrir, poner en revisión o cerrar.


* **Resultado:** El estado del período controla la captura de operaciones según las reglas de la aplicación. La interfaz actual no crea ni edita ejercicios fiscales nuevos.



### `CU-PAR-04`: Configurar Parámetros Fiscales

* **Descripción:** Permite guardar una configuración fiscal activa con tasas de IVA, retención de IVA, pago a cuenta, retenciones de ISR, cuentas relacionadas y código de catálogo MH.


* **Entradas:** Tasas y cuentas afectables para los conceptos disponibles.


* **Resultado:** Se actualiza un único registro de parámetros fiscales. No existe historial de vigencias ni perfiles distintos por sujeto u operación; el uso de cada cuenta o tasa depende del flujo operativo que la consulte. La configuración no equivale a una declaración ni a una integración con servicios del Ministerio de Hacienda.



### `CU-PAR-05`: Consultar y Editar Valores de Políticas

* **Descripción:** Permite listar y modificar los valores textuales de políticas guardadas para partida doble, devengo, inventarios, CIF y recursos humanos.


* **Entradas:** Política seleccionada y nuevo valor.


* **Resultado:** El valor queda persistido para consulta. En la implementación actual, el formato de folio, el cuadre exacto Debe igual a Haber y la inmutabilidad de partidas contabilizadas se aplican con reglas fijas del motor; cambiar máscaras, tolerancias o flags de esas políticas no reconfigura dinámicamente esas reglas.



### `CU-PAR-06`: Administrar Centros de Costo

* **Descripción:** Permite registrar o actualizar código, nombre y estado de los centros de costo.


* **Entradas:** Código, nombre y bandera activo.


* **Resultado:** El catálogo se ofrece en varias pantallas de costos, planilla y CIF. Las líneas manuales de partida no validan de forma obligatoria que un código escrito en `centroCosto` exista o esté activo en este catálogo.



---

## Módulo 3: Registros y Transacciones Contables (Motor Transaccional)

### `CU-REG-01`: Registrar, Aprobar y Contabilizar Partidas de Libro Diario

* **Descripción:** Permite crear partidas de ingreso, egreso, diario, ajuste, apertura o cierre; agregar líneas, guardarlas como borrador, aprobarlas y contabilizarlas.


* **Entradas:** Fecha, tipo, concepto, referencia de soporte y líneas con cuenta, Debe, Haber, auxiliar y centro de costo opcionales.


* **Resultado:** La partida pasa por los estados `BORRADOR`, `APROBADA` y `CONTABILIZADA`. El motor exige cuentas activas y afectables, por lo menos dos líneas y cuadre exacto. Una partida contabilizada queda inmutable y alimenta los libros generados por el prototipo.



### `CU-REG-02`: Anular o Revertir Partidas

* **Descripción:** Permite anular una partida o revertir una partida contabilizada mediante una partida de ajuste con importes compensatorios.


* **Entradas:** Partida seleccionada, motivo solicitado por la interfaz y fecha de reversión cuando aplica.


* **Resultado:** La anulación cambia el estado a `ANULADA`; la reversión genera una partida `AJ`, marca la original como `REVERTIDA` y registra la relación en la bitácora. La pantalla exige motivo, aunque la capa de servicio no lo vuelve una validación independiente; tampoco se ofrece un enlace hipertextual bidireccional entre ambos comprobantes.



### `CU-REG-03`: Liquidar IVA del Período

* **Descripción:** Ejecuta el cruce de IVA débito e IVA crédito del período seleccionado desde la vista de ajustes y cierre.


* **Entradas:** Fecha del período abierto.


* **Resultado:** Se genera una partida de liquidación con el saldo correspondiente. Los libros de IVA sirven como apoyo interno y requieren conciliación y procedimientos tributarios externos.



### `CU-REG-04`: Ejecutar Depreciación y Amortización

* **Descripción:** Ejecuta depreciación lineal de activos fijos y amortización de intangibles registrados para la fecha seleccionada.


* **Entradas:** Fecha contable dentro del período abierto y activos o intangibles elegibles.


* **Resultado:** Se registran las partidas de depreciación o amortización del período. Los procesos son idempotentes para evitar duplicar la cuota de un mismo período.



### `CU-REG-05`: Importar Borradores de Asientos CSV

* **Descripción:** Permite cargar un archivo CSV de líneas de asiento y convertirlo en un borrador contable para revisión posterior.


* **Entradas:** Archivo CSV y fecha contable.


* **Resultado:** Partida creada en estado `BORRADOR`. No importa JSON, XML ni DTE oficiales.


### `CU-REG-06`: Registrar Terceros, Documentos y Operaciones con IVA

* **Descripción:** Permite registrar terceros como clientes o proveedores y contabilizar operaciones de venta de licencia, venta SaaS, compra, cobro, pago y anticipo SaaS. Antes de contabilizar se puede previsualizar el asiento. También permite aplicar un anticipo con saldo a un DTE del mismo cliente.


* **Entradas:** Tercero seleccionado, plantilla de operación, fecha, importe, opción de IVA incluido, cuenta alternativa opcional y, para cobros, retención de IVA opcional.


* **Resultado:** Se registra el documento interno y su partida contable con el perfil disponible o con la cuenta alternativa indicada. La función no genera ni transmite DTE oficiales.


### `CU-REG-07`: Administrar Plantillas de Asiento Recurrente

* **Descripción:** Permite crear plantillas con código, nombre, tipo de partida, concepto y líneas contables balanceadas, y generar desde una plantilla el borrador del mes.


* **Entradas:** Datos de la plantilla y al menos dos líneas con Debe y Haber equilibrados.


* **Resultado:** Plantilla almacenada y borrador generado para revisión y contabilización posterior; no contabiliza automáticamente una recurrencia sin intervención del usuario.


### `CU-REG-08`: Calcular y Contabilizar Planilla Agregada

* **Descripción:** Calcula una planilla agregada del período con empleados, salario nominal, tasas laborales configuradas, ISR, otras deducciones, prestaciones y centro de costo.


* **Entradas:** Fecha, cantidad de empleados, importes agregados y centro de costo.


* **Resultado:** Se previsualiza la conciliación y se puede registrar una única partida de planilla. El prototipo no administra expedientes individuales de empleados ni sustituye cálculos laborales o previsionales oficiales.


### `CU-REG-09`: Registrar Ajustes y Cerrar el Período

* **Descripción:** Permite registrar devengos de suscripciones, consumo de nube, alquiler y gasto devengado; ejecutar depreciación, amortización y liquidación de IVA; y cerrar el período con o sin los ajustes automáticos.


* **Entradas:** Fecha contable e importes de los ajustes manuales, o confirmación de cierre.


* **Resultado:** Cada ajuste crea una partida. El cierre bloquea la captura del período conforme a los controles de la aplicación.



---

## Módulo 4: Gestión de Inventario Digital y Servicios

### `CU-INV-01`: Liberar y Mover Lotes de Licencias Digitales

* **Descripción:** Permite liberar un lote de licencias con código, unidades, costo total y cliente. También permite registrar venta y costo, devolución al inventario y revocación de derecho, y consultar el kárdex del lote.


* **Entradas:** Código de lote, unidades, costo total, cliente y movimiento solicitado.


* **Resultado:** La salida toma automáticamente la unidad disponible más antigua con PEPS y actualiza el kárdex. La vista muestra comparaciones con promedio y UEPS, pero sólo PEPS origina la salida contable. La liberación actual no permite elegir producto o versión: el servicio asigna una versión fija del prototipo y el costo se captura manualmente.



### `CU-INV-02`: Registrar Catálogo de Planes de Servicio

* **Descripción:** Permite registrar planes con código, nombre, modalidad (`LICENCIA`, `SAAS`, `PROYECTO` o `SERVICIO`), tarifa, tipo de facturación, cuenta de ingreso y estado.


* **Entradas:** Datos del plan y cuenta de ingreso afectable.


* **Resultado:** El plan queda disponible en la vista de servicios y accesos; guardar un código existente actualiza su registro en la base de datos. La interfaz no ofrece un flujo completo de selección, edición y baja de todos los planes existentes.



### `CU-INV-03`: Deterioro de Inventario Digital

* **Descripción:** El servicio de cierre contiene una capacidad para registrar deterioro de inventario, pero la aplicación de escritorio actual no ofrece una pantalla ni acción de usuario para ejecutarla.


* **Resultado:** No existe un caso de uso operativo accesible para registrar deterioro desde la interfaz actual.



---

## Módulo 5: Gestión y Acumulación de Costos

### `CU-COS-01`: Acumular Costos de una Orden de Servicio de Ejemplo

* **Descripción:** La vista permite cargar costos de ejemplo, registrar mano de obra directa y reconocer el costo de una orden identificada en la interfaz.


* **Entradas:** Código de orden, importe o base de costo y datos solicitados por el formulario.


* **Resultado:** Se acumulan o reconocen costos para la orden seleccionada. No existe en la aplicación actual un ciclo completo para crear, editar, entregar y controlar el estado de órdenes de servicio; el reconocimiento no verifica una entrega previa.



### `CU-COS-02`: Liberar Licencias con Costo Manual

* **Descripción:** Al liberar un lote, el usuario proporciona el costo total y las unidades; el sistema calcula el costo unitario del lote para sus posteriores salidas PEPS.


* **Entradas:** Código de lote, unidades, costo total y cliente.


* **Resultado:** El costo unitario se guarda en el lote liberado. El prototipo no consolida automáticamente costos capitalizables por producto o versión y la versión no se selecciona desde la interfaz.



### `CU-COS-03`: Calcular Costo por Usuario Mes para SaaS

* **Descripción:** Permite registrar usuarios-mes por plan y calcular una distribución gerencial de costos SaaS a partir de las cuentas de costo definidas por el servicio.


* **Entradas:** Planes, usuarios-mes y costos contabilizados del período.


* **Resultado:** Se muestra el costo por usuario-mes y la distribución calculada. El cálculo no deja una distribución contable persistida ni genera por sí mismo un asiento de reparto entre planes.



### `CU-COS-04`: Acumular Costos por Plan Facturado

* **Descripción:** Permite registrar mano de obra directa, recursos directos y CIF para un plan de servicio seleccionado.


* **Entradas:** Plan, tipo de costo, importe, descripción y centro de costo cuando aplica.


* **Resultado:** Se consulta el costo acumulado del plan. El modelo actual no relaciona el consumo con una orden opcional ni calcula ingreso o margen por plan.


### `CU-COS-05`: Registrar Costeo por Procesos y CIF

* **Descripción:** Permite guardar una cédula de proceso para un período, calcular costos por PEPS y promedio, y usar UEPS sólo como comparación. También permite guardar análisis CIF, aplicar CIF a una orden y cerrar la variación CIF.


* **Entradas:** Balance físico, avances, costos de material y conversión, centro de costo, presupuesto CIF, base y costos reales.


* **Resultado:** Cédula y análisis disponibles para consulta, y partidas trazables para aplicación y cierre de variación CIF. Los valores constituyen evidencia de cálculo dentro del prototipo.


### `CU-COS-06`: Estimar Costos y Precios Unitarios

* **Descripción:** Ofrece técnicas de estimación de esfuerzo, costo total y precio unitario, comparación de métodos de costeo y comparación entre estimado y costos históricos.


* **Entradas:** Variables de estimación, costos, márgenes y datos de comparación solicitados por cada instrumento.


* **Resultado:** Resultados de análisis para apoyo gerencial. Los cálculos no generan por sí solos una partida ni sustituyen la validación de una cotización o contrato.



---

## Módulo 6: Reportes, Estados Financieros y Exportación Avanzada

### `CU-REP-01`: Consultar Libros Contables y Estados Financieros

* **Descripción:** La navegación principal permite consultar Libro Diario, Libro Mayor, Cuenta T, Balanza de Comprobación, Hoja de Trabajo, Situación Financiera y Resultados. La vista es de sólo lectura y admite el rango o período requerido por cada reporte.


* **Entradas:** Tipo de reporte, fechas y alcance disponible.


* **Resultado:** Reporte visible para revisión. El paquete ZIP también incorpora Estado de Cambios en el Patrimonio y Estado de Flujos de Efectivo; estos dos no aparecen como selección individual en la navegación principal. El estado de flujos usa el método directo y se limita a movimientos del grupo de Caja chica y Bancos configurado por el prototipo.



### `CU-REP-02`: Consultar Libros Fiscales de IVA

* **Descripción:** Permite consultar libros de compras, ventas CCF y ventas consumidor final por rango de fechas.


* **Entradas:** Tipo de libro y rango de fechas.


* **Resultado:** Auxiliar fiscal interno sujeto a revisión tributaria. En la consulta actual, el libro de ventas a consumidor final incluye documentos `FACTURA` y `CCF`, por lo que puede solaparse con el libro de ventas CCF; no genera declaraciones ni registros oficiales.



### `CU-REP-03`: Consultar Libros Auxiliares

* **Descripción:** Permite consultar auxiliares de clientes, proveedores, inventario digital, órdenes de servicio y gastos por centro de costo mediante tipo y rango de fechas.


* **Entradas:** Tipo de auxiliar y rango de fechas.


* **Resultado:** Tabla de movimientos y totales Debe y Haber. La interfaz actual no recibe una dimensión o código específico para filtrar un auxiliar y no presenta un saldo acumulado por movimiento.



### `CU-REP-04`: Exportar Paquete ZIP de Reportes

* **Descripción:** Genera un archivo ZIP para un rango de fechas y el alcance `Libro completo` o `Caso D`. El paquete incluye siempre ocho formatos: Diario, Mayor, Balanza, Hoja de Trabajo, Situación Financiera, Resultados, Cambios en el Patrimonio y Flujos de Efectivo, en PDF y XLSX.


* **Entradas:** Fecha inicial, fecha final y alcance.


* **Resultado:** Archivo con nombre `Reportes_<periodo>_<marca>.zip`, organizado por reporte y período. El nombre de carpeta de período puede representar un mes y año o un rango. Los reportes anchos se exportan en PDF A3 apaisado. No se seleccionan individualmente los formatos dentro del ZIP.


### `CU-REP-05`: Exportar Libros para Varios Períodos

* **Descripción:** Permite seleccionar varios períodos de los ejercicios disponibles y exportar los libros elegidos para cada combinación.


* **Entradas:** Períodos marcados y tipos de libro disponibles.


* **Resultado:** Se crean archivos PDF y XLSX por período en una carpeta de salida. Esta exportación multiperíodo no se empaqueta en un único ZIP.


### `CU-REP-06`: Consultar la Sección de Libros Societarios

* **Descripción:** Muestra una vista informativa sobre actas, accionistas y capital social.


* **Resultado:** La sección remite a los reportes obligatorios para capital y estados financieros. No registra ni emite libros societarios en la versión actual.
