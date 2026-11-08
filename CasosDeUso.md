## Módulo 1: Configuración de la Entidad y Empresa

### `CU-EMP-01`: Configurar Perfil Corporativo e Información Fiscal

* **Descripción:** Permite al Contador registrar y actualizar los datos internos de empresa, actividad económica, dirección, logo y responsables de los reportes. En el caso académico, estos valores son simulados; no se atribuyen a Colibrí inscripciones, NIT, NRC ni registros profesionales reales.


* **Entradas:** Formulario de datos generales, archivo de imagen de logo, credenciales profesionales.


* **Resultado:** Datos centralizados para los encabezados de reportes y documentos internos. La aplicación no genera, firma ni transmite DTE oficiales.



---

## Módulo 2: Parametrización Contable, Fiscal y Operativa

### `CU-PAR-01`: Administrar Catálogo de Cuentas y Subcuentas

* **Descripción:** Permite estructurar la jerarquía a 4 niveles (Clase, Grupo, Cuenta Agrupadora y Subcuenta a 6 dígitos). Permite configurar nombre, naturaleza (Deudora/Acreedora), si es afectable, origen/exclusividad de asiento (Manual/Automático) y activación de dimensiones analíticas obligatorias.


* **Entradas:** Código contable, atributos de configuración, flags de dimensiones analíticas.


* **Resultado:** Subcuenta parametrizada y lista para recibir asientos directos con validaciones activas en el motor transaccional.



### `CU-PAR-02`: Configurar Mapeo Modular de Integración (Posting Profiles)

* **Descripción:** Asigna las subcuentas por defecto que utilizará el sistema al procesar ventas, compras, cobros, pagos, anticipos, licencias, depreciaciones, inventario y liquidación de IVA.


* **Entradas:** Selección de subcuentas por cada evento operativo del ERP.


* **Resultado:** Plantillas de integración listas para la generación de asientos automáticos sin intervención de digitación manual.



### `CU-PAR-03`: Gestionar Períodos Contables y Ejercicios Fiscales

* **Descripción:** Permite al Contador abrir, poner en revisión, cerrar o reabrir períodos, bloqueando la captura de nuevas partidas en rangos no abiertos.


* **Entradas:** Selección de período, acción de bloqueo/cierre o reapertura autorizada.


* **Resultado:** Período actualizado en estado `ABIERTO`, `EN_REVISION` o `CERRADO`, con bloqueo de captura cuando corresponda.



### `CU-PAR-04`: Configurar Parámetros Fiscales Salvadoreños

* **Descripción:** Configura tasas y cuentas asociadas para IVA, retención de IVA, pago a cuenta y retenciones de ISR, además del código de catálogo fiscal almacenado por el sistema. Las tasas aplicables dependen de la vigencia, del sujeto y de la operación.


* **Entradas:** Tasas aprobadas, subcuentas de débito/crédito fiscal, código de actividad y datos de parametrización.


* **Resultado:** Las operaciones compatibles usan las tasas y cuentas configuradas. La configuración debe ser validada profesionalmente y no equivale a una integración con servicios del Ministerio de Hacienda.



### `CU-PAR-05`: Configurar Políticas y Secuenciales del Sistema

* **Descripción:** Establece los formatos autogenerados de correlativos (`DI-YYYY-######`, `AJ-YYYY-######`), el margen de tolerancia de descuadre ($0.00 exacto) y la inmutabilidad estricta de partidas.


* **Entradas:** Máscaras de texto, opciones de inmutabilidad y exigencia de campos obligatorios.


* **Resultado:** Reglas de auditoría y control interno activas en toda la aplicación.



### `CU-PAR-06`: Administrar Catálogo de Centros de Costo

* **Descripción:** Administra el listado de las áreas funcionales de la empresa (Administración, Desarrollo, Soporte, Comercial) que alimentan las validaciones de costos y gastos.


* **Entradas:** Código de centro de costo, nombre del área, estado activo/inactivo.


* **Resultado:** Entidades maestras disponibles para la selección de dimensiones analíticas en asientos contables.



---

## Módulo 3: Registros y Transacciones Contables (Motor Transaccional)

### `CU-REG-01`: Registrar y Aprobar Asientos en Libro Diario

* **Descripción:** Permite capturar e ingresar partidas manuales (Ingreso `IN`, Egreso `EG`, Diario `DI`) exigiendo que el débito y crédito cuadren a $0.00 y que se completen los atributos requeridos según las dimensiones de cada subcuenta.


* **Entradas:** Fecha, tipo de asiento, concepto, líneas con subcuenta, cargos/abonos y dimensiones disponibles (tercero, referencia documental, proyecto, centro de costo y lote).


* **Resultado:** La partida pasa de `BORRADOR` a `APROBADA` y, al confirmarse, a `CONTABILIZADA`. En ese último estado es inmutable y afecta el Libro Mayor, auxiliares y estados financieros.



### `CU-REG-02`: Revertir y Corregir Partidas Contabilizadas (Asientos AJ)

* **Descripción:** Permite anular o corregir un asiento contabilizado mediante la emisión de una partida de Ajuste (`AJ`) con montos invertidos, exigiendo la captura obligatoria del motivo de corrección.


* **Entradas:** Selección de la partida original, motivo de reversión por escrito.


* **Resultado:** Partida original marcada como `REVERTIDA`, creación del asiento reverso `AJ` y vinculación hipertextual bidireccional entre ambos registros.



### `CU-REG-03`: Ejecutar Liquidación Mensual de IVA

* **Descripción:** Asistente automático que compensa el saldo de `IVA Débito Fiscal` contra `IVA Crédito Fiscal` al final de cada mes, determinando el `IVA por Pagar` o el `Crédito Fiscal Remanente`.


* **Entradas:** Selección del mes a liquidar.


* **Resultado:** Asiento automático de liquidación de IVA y saldos de apoyo para revisión tributaria. Los libros de IVA y la declaración oficial requieren conciliación y procedimientos externos al prototipo.



### `CU-REG-04`: Ejecutar Proceso de Depreciación y Amortización

* **Descripción:** Calcula y contabiliza la cuota mensual lineal de depreciación para activos fijos activos registrados. El cálculo usa costo, valor residual y vida útil en meses; no calcula amortización de intangibles.


* **Entradas:** Período de cálculo.


* **Resultado:** Generación e impacto automático de partidas de gasto/costo por depreciación contra depreciación acumulada.



### `CU-REG-05`: Importar Borradores de Asientos CSV

* **Descripción:** Permite cargar un archivo CSV de líneas de asiento para crear un borrador contable sujeto a revisión. No interpreta JSON, XML ni DTE.


* **Entradas:** Archivo CSV con las líneas del asiento y fecha contable.


* **Resultado:** Partidas pre-cargadas en estado Borrador para revisión y aprobación final del Contador.



---

## Módulo 4: Gestión de Inventario Digital y Servicios

### `CU-INV-01`: Administrar Lotes de Licencias Digitales Liberadas

* **Descripción:** Registro y control de lotes de licencias de software listos para distribución. Al confirmar la venta, el sistema reserva la unidad disponible del lote liberado más antiguo conforme a PEPS, contabiliza la operación y confirma la entrega; el contador no selecciona manualmente el lote de salida.


* **Entradas:** Producto, número de versión, número de lote, unidades liberadas, costo unitario.


* **Resultado:** Auxiliar de inventario digital y costo de venta actualizados por PEPS automático. La vista muestra PEPS, promedio ponderado y UEPS como comparación analítica; sólo PEPS puede originar la salida y el asiento de costo de ventas.



### `CU-INV-02`: Administrar Catálogo de Servicios y Planes SaaS

* **Descripción:** CRUD de las ofertas comercializables de desarrollo a la medida, suscripciones SaaS por usuario-mes y servicios facturados por plan, paquete o hito.


* **Entradas:** Nombre del plan/servicio, tarifa base, modalidad de facturación, cuenta de ingreso asociada y, cuando aplica, unidad de consumo por usuario-mes.


* **Resultado:** Catálogo de productos/servicios disponible para integración con facturación y acumulación de costos por plan.



### `CU-INV-03`: Registrar Estimación por Deterioro de Inventario Digital

* **Descripción:** Registra un asiento de ajuste por deterioro con referencia de lote. La estimación del importe y la evaluación de obsolescencia son procedimientos de revisión externa al prototipo.


* **Entradas:** Versión/Lote a evaluar, monto de deterioro calculado.


* **Resultado:** Partida de ajuste contabilizada con referencia de lote; su razonabilidad requiere documentación de soporte y revisión profesional.



---

## Módulo 5: Gestión y Acumulación de Costos

### `CU-COS-01`: Acumular y Liquidar Costos por Órdenes de Servicio (A la Medida)

* **Descripción:** Asigna y acumula los costos de mano de obra directa, insumos técnicos y Costos Indirectos de Fabricación (CIF) aplicados a cada Orden de Trabajo (OS) de desarrollo a la medida.


* **Entradas:** Código de OS, horas reportadas, insumos utilizados, tasa de CIF.


* **Resultado:** Acumulación por orden de mano de obra directa, recursos directos y CIF; actualización de `Servicios en Proceso` y liquidación hacia `Costo de Ventas` al entregar el proyecto.



### `CU-COS-02`: Determinar Costo de Licencias Descargables

* **Descripción:** Consolida los costos capitalizables de desarrollo de una versión genérica para calcular el costo unitario por licencia al momento de la liberación del lote.


* **Entradas:** Inversión acumulada en la versión, total de licencias generadas.


* **Resultado:** Costo unitario grabado en el lote de inventario digital y salida posterior por PEPS automático según fecha de liberación, código y número de lote.



### `CU-COS-03`: Determinar Costo por Procesos SaaS

* **Descripción:** Acumula los costos operativos del servidor, infraestructura en la nube y soporte técnico asignados al servicio SaaS para calcular el costo por usuario-mes.


* **Entradas:** Costos de alojamiento, soporte e infraestructura del período; usuarios-mes registrados por plan.


* **Resultado:** Costo unitario SaaS por usuario-mes y distribución conciliable de los costos entre planes suscritos, incluso cuando el reparto requiere asignar centavos residuales.



### `CU-COS-04`: Acumular Costos de Otros Servicios por Plan Facturado

* **Descripción:** Registra y concentra los recursos, horas y CIF de implementación, migración, capacitación, soporte u otros servicios dentro del plan, paquete o hito que se facture.


* **Entradas:** Plan facturado, orden opcional, recursos directos, horas y CIF aplicable.


* **Resultado:** Costo total y margen identificables por plan, sin mezclarlo con el inventario de licencias ni con el costo mensual del SaaS.



---

## Módulo 6: Reportes, Estados Financieros y Exportación Avanzada

### `CU-REP-01`: Generar Libros Contables Obligatorios y Estados Financieros

* **Descripción:** Permite consultar y emitir Libro Diario, Libro Mayor, Cuenta T, Balanza de Comprobación, Hoja de trabajo, Situación financiera y Resultados. Cambios en el patrimonio y Flujos de efectivo se construyen para el ZIP, pero no aparecen en el selector manual de la vista principal. Diario contiene fecha, PDA, código/referencia, cuenta/concepto, Debe y Haber; Mayor concentra acumulados y saldos; Balanza totaliza Debe/Haber; y Hoja enlaza catálogo, balanza, ajustes, balanza ajustada, resultados, capital y situación financiera.


* **Entradas:** Selección del libro/estado, período o rango de fechas.


* **Resultado:** Vista de sólo lectura para revisión. Situación financiera presenta activos, pasivos y patrimonio clasificados; Resultados presenta ventas netas, costo de ventas, utilidad bruta, gastos de operación, resultado financiero/otros, impuesto y utilidad neta. El Estado de Flujos de Efectivo utiliza método directo y se limita a movimientos de Caja chica y Bancos (grupo 1101); debe conciliarse antes de su uso final.



### `CU-REP-02`: Generar Libros Fiscales de IVA

* **Descripción:** Genera auxiliares de compras y ventas (CCF y consumidor final) para apoyar la preparación y revisión tributaria.


* **Entradas:** Mes y año fiscal a consultar.


* **Resultado:** Reporte fiscal interno sujeto a revisión del responsable tributario; no presenta declaraciones ni sustituye los registros exigibles.



### `CU-REP-03`: Consultar Libros Auxiliares

* **Descripción:** Muestra movimientos para Auxiliar de Clientes, Auxiliar de Proveedores, Auxiliar de Inventario Digital, Auxiliar de Órdenes de Servicio y Gastos por Centro de Costo.


* **Entradas:** Tipo de auxiliar, dimensión seleccionada, rango de fechas.


* **Resultado:** Tabla detallada de movimientos y saldo acumulado filtrado por el parámetro elegido.



### `CU-REP-04`: Exportar Paquete de Reportes del Rango Seleccionado (ZIP)

* **Descripción:** Genera un paquete `.zip` para un único rango de fechas, organizado en una carpeta del año inicial. Incluye Libro Diario, Libro Mayor, Balanza, Hoja de trabajo y los cuatro estados financieros disponibles, en PDF y XLSX. Los reportes de más de diez columnas se exportan en PDF apaisado A3 para preservar la legibilidad.


* **Entradas:** Fecha inicial, fecha final y alcance de información seleccionado.


* **Resultado:** Archivo `Reportes_Contables_Colibri.zip` del rango elegido. La selección múltiple de períodos o años no está implementada.

