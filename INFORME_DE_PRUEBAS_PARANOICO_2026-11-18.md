# Informe de pruebas paranoico — Colibrí

**Fecha:** 18 de noviembre de 2026
**Versión evaluada:** estado actual del árbol de trabajo
**Resultado general:** APROBADO CON RESERVAS DE COBERTURA MANUAL

Este informe deja evidencia de lo que se ejecutó en esta sesión. No convierte en aprobado un flujo que solo fue leído en código o listado en el plan de pruebas.

## Resumen ejecutivo

- Se ejecutó `mvnw.cmd test` contra PostgreSQL local y la base aislada `colibri_test`.
- Resultado: **77 pruebas ejecutadas, 77 aprobadas, 0 fallos, 0 errores y 0 omitidas**.
- Las pruebas de integración de partidas, kárdex y navegación JavaFX también pasaron; verifican que la base de pruebas responde y que las vistas se pueden construir y navegar.
- No se alteró la base operativa `colibri`. Los tests de integración usan por defecto `jdbc:postgresql://127.0.0.1:5434/colibri_test`.
- Se intentó abrir una instancia visual aislada con un perfil de usuario temporal y `colibri_test`. La sesión de automatización de escritorio no recibió una ventana objetivo, por lo cual no se realizaron clics manuales ni se adjudicó aprobación visual ficticia.

## Ambiente y evidencia

- Sistema: Windows 11, JDK 25.0.3, Maven Wrapper 3.9.11, PostgreSQL 18 en `127.0.0.1:5434`.
- Base de pruebas: `colibri_test`, usuario `colibri`.
- Comando ejecutado: `./mvnw.cmd test`.
- Conectividad verificada mediante una consulta de solo lectura a PostgreSQL.
- Evidencia de resultados: archivos `TEST-*.xml` bajo los directorios `target/surefire-reports` de los cinco módulos.
- Advertencia no bloqueante observada: Jasper/Log4j informa que no encuentra un proveedor de logging. No produjo errores ni fallos de prueba, pero deja el registro de Jasper incompleto y debe corregirse antes de producción.

## Flujos y casos ejecutados

### Arranque, configuración y seguridad

- `AppContextTest` (2): la semilla demo permanece desactivada salvo que se habilite explícitamente; una bandera ambigua no activa datos demo.
- `PasswordHasherTest` (2): la contraseña no se almacena en texto claro, se verifica correctamente y el código de recuperación tiene una longitud razonable.
- `PartidaServiceIntegrationTest.usuariosPersonalesPersistenBloqueoYPermitenDesactivacion`: se crea un usuario de prueba, se bloquea tras cinco intentos inválidos y permanece sin acceso cuando soporte lo desactiva.
- `JsonSupportTest` (1): se serializa y deserializa el documento simulado.

### Motor contable, catálogo y períodos

- `PartidaRulesTest` (5): una diferencia de un centavo impide aprobar; una partida equilibrada sí aprueba; el detalle exige subcuentas de seis dígitos; una línea no puede tener Debe y Haber simultáneamente; y una reversión no puede revertirse otra vez.
- `PartidaServiceIntegrationTest.catalogoSemillaCargaTablaVIII`: el catálogo semilla carga al menos 170 cuentas.
- `PartidaServiceIntegrationTest.descuadreDeUnCentavoNoContabiliza`: el borrador descuadrado se rechaza al contabilizar.
- `PartidaServiceIntegrationTest.eventoDuplicadoNoDobleContabiliza`: una clave de evento duplicada no genera doble contabilización y el reintento conserva el folio.
- `PartidaServiceIntegrationTest.cuentaAgrupadoraNoRecibeMovimiento`: se bloquean movimientos directos sobre una cuenta de agrupación.
- `PartidaServiceIntegrationTest.semillasCoincidenConCasoDYSonIdempotentes`: la semilla del Caso D conserva saldos esperados, costo/margen de la orden y 17 eventos sin duplicación.

### IVA, resultados y ajustes de cierre

- `IvaCalculatorTest` (4): IVA de venta, precio con IVA incluido, licencia e importación con DAI en la base.
- `ResultadosCalculatorTest` (4): cédula analítica de ventas/compras/inventario, conciliación de licencia digital, utilidad operativa/neta y rechazo de inventario final o deducciones inconsistentes.
- `AjustesCalculatorTest` (4): intereses diferidos y devengos; depreciación sin exceder la base; agotamiento, amortización e incobrables; validación de plazos y límites.

### Costos, inventario y planilla

- `CostosCalculatorTest` (7): elementos del costo, productividad y CIF; aportes/planilla/vacaciones; umbral INCAF; costo, precio y utilidad de orden; unidades equivalentes y costeo por procesos; costos relevantes; rechazo de bases o proporciones inválidas.
- `InventarioCostoServiceCostingTest` (4): distribución exacta de centavos del lote; prorrateo SaaS por planes; comparación de métodos con costo unitario exacto; varianza y rentabilidad desde registros.
- `KardexLicenciaIntegrationTest`: flujo completo de lote de licencias: liberar → vender por PEPS → devolver → revender → revocar, con saldos, costo, folios y bloqueos de dobles devoluciones/revocaciones inválidas.
- `PlanillaServiceTest` (5): cargas y prestaciones separadas; no aplicación de INCAF bajo el umbral; política patronal parametrizada; asiento preliminar balanceado; rechazo de deducciones mayores que el salario.
- `CosteoPorProcesosServiceTest` (2): cédula PEPS por elemento y conciliación del costo; rechazo de WIP inicial que permanece al final bajo el modelo PEPS simplificado.
- `CosteoGerencialServiceTest` (2): separación entre costo directo y CIF estándar con variación; acumulación ABC por actividad.
- `EstimacionCostosServiceTest` (5): tres puntos, puntos de función, líneas de código, desgaste de trabajo, COCOMO y SLIM/Putnam; costo/CIF/precio/margen; promedio, mediana, rango, varianza, desviación y variabilidad cero con una sola muestra.

### Libros, estados y exportaciones

- `PartidaServiceIntegrationTest.librosPresentanCuentasTHojaDeTrabajoYEstadosBalanceados`: Cuenta T incluye el folio contabilizado; la hoja de trabajo incorpora cuentas afectadas; Situación financiera cuadra; Resultados presenta utilidad bruta y utilidad/pérdida neta.
- `ReportServiceTest` (14): disponibilidad de Jasper y POI; Excel con título, datos, membrete y ajuste de texto; PDF legible, texto largo conservado y hoja de trabajo ancha; nombres de archivo para períodos y cortes; exportación de varios libros en carpetas descriptivas con progreso por archivo.

### Interfaz, navegación y diseño

- `NavegacionModulosTest.contadorPuedeAbrirTodasLasSeccionesSinPestanas`: carga las rutas del contador sin errores de módulo y verifica el orden del menú, Inicio, Empresa, Temas contables, Parametrización, Registros, Inventario, Costos y Reportes.
- La prueba anterior construye y visita las 25 secciones hijas: 4 de Temas, 6 de Parametrización, 5 de Registros, 3 de Inventario, 5 de Costos y 3 de Reportes; además recorre los 28 temas contables disponibles.
- `NavegacionModulosTest.elMenuEsUnAcordeonYSeContraeAIconos`: el menú mantiene un único grupo expandido y el breadcrumb/flyout navega aun con el menú contraído.
- `BreakpointsTest` (2): los anchos seleccionan el nivel responsive esperado y los identificadores de navegación son estables.
- `DisenoTokensTest` (4): paleta semántica, ausencia de referencias CSS rotas, aliases resueltos y ausencia de estilos en línea en las vistas.

## Casos negativos y de borde comprobados

- Descuadre de exactamente $0.01.
- Clave de evento contable duplicada e idempotencia del reintento.
- Cuenta agrupadora y código de cuenta no imputable.
- Partida de reversión reversible por segunda vez.
- Deducción salarial mayor que el salario.
- Base/proporción inválida en estimación.
- WIP PEPS simplificado no permitido.
- Devolución o revocación repetida/no aplicable de licencia.
- Inventario final y deducciones contradictorias.
- Bloqueo de credenciales por fuerza bruta y cuenta desactivada.
- Semilla demostrativa no solicitada y resembrado idempotente.

## Cobertura manual pendiente (no falsificada)

`PLAN_DE_PRUEBAS.md` contiene el checklist de aceptación de 380 verificaciones. En esta sesión no se pudo completar la parte que exige interacción visual real porque la ventana JavaFX aislada no fue expuesta como superficie controlable, aunque la prueba JavaFX de navegación pasó.

Quedan pendientes de ejecutar manualmente, con capturas de evidencia en una estación donde la ventana sea visible:

- Login, recuperación de clave, expiración de sesión, bloqueo y separación de roles.
- Altas, ediciones, aprobaciones, anulaciones y reversos en todos los formularios.
- Ciclo completo de documentos/IVA, anticipos, plantillas, planilla, ajustes y cierre de período.
- Flujos visuales de inventario, órdenes, planes SaaS, procesos/CIF y estimación.
- Todos los formatos de reportes, filtros, diálogos, impresión y selección real de carpetas de exportación.
- Soporte técnico: usuarios, entorno, monitoreo, respaldo manual y claves temporales.
- Persistencia al cerrar/reabrir y pruebas de rendimiento/ergonomía con tablas extensas.

Esta lista no es un defecto del producto: es una frontera explícita de la evidencia de esta ejecución. Debe usarse `PLAN_DE_PRUEBAS.md` para registrar cada caso manual como Aprobado, Fallido o Bloqueado junto con capturas y datos de entrada.

## Hallazgos y riesgos

### Hallazgo QA-001 — registro de Jasper incompleto

**Severidad:** baja.
**Evidencia:** durante `ReportServiceTest` apareció `Log4j API could not find a logging provider`.
**Impacto:** los reportes se generaron y las 14 pruebas pasaron, pero diagnósticos de Jasper/Log4j podrían perderse.
**Recomendación:** añadir y configurar un proveedor compatible de Log4j/SLF4J, o excluir la API no configurada, y comprobar el log en un arranque normal.

### Riesgo QA-002 — verificación visual aún no observable

**Severidad:** media para aceptación de UX, no para la lógica ya cubierta.
**Evidencia:** la instancia JavaFX aislada inició desde el proceso, pero no se publicó una ventana a la superficie de automatización; además el perfil normal ya tenía un candado de instancia activo.
**Impacto:** no se puede certificar mediante esta sesión el aspecto de diálogos, campos, atajos, selecciones de archivos y mensajes en pantalla.
**Recomendación:** ejecutar la sección manual de `PLAN_DE_PRUEBAS.md` en una sesión de escritorio visible, con `colibri_test`, y adjuntar capturas por caso.

## Criterio de salida

La lógica de negocio, persistencia de prueba, generación de reportes y construcción/navegación de módulos alcanzaron un resultado limpio. La aplicación no debe declararse “validada completamente en interfaz” hasta cerrar los 380 casos manuales con evidencia visual. Para una entrega académica o demostración, el estado recomendado es: **apta para demo técnica; aceptación visual/manual pendiente**.
