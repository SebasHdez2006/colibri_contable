# Colibrí Sistema Contable

Prototipo funcional de escritorio para **Soluciones Empresariales Colibrí, S.A. de C.V.**. Se ejecuta localmente con JavaFX y PostgreSQL y está orientado a la captura, procesamiento y consulta de información contable de demostración.

Este documento describe el comportamiento del código disponible en el repositorio. Cuando una política, un instrumento académico o un caso de uso propone una capacidad no implementada, se indica como límite del prototipo y no como funcionalidad operativa.

## Alcance actual

Colibrí cuenta con dos roles:

- **Contador:** Empresa, Parametrización, Registros, Inventario, Costos y Reportes.
- **Soporte técnico:** Usuarios, Base de datos, Entorno, Monitoreo y Accesos. No puede consultar saldos, partidas ni estados financieros desde la interfaz de ese rol.

El prototipo incluye:

- autenticación local, bloqueo por intentos fallidos, sesión con inactividad, código de recuperación de un solo uso y separación de roles;
- empresa, catálogo de cuentas de seis dígitos, períodos, partidas de diario, reversión, documentos internos, terceros, anticipos y plantillas recurrentes;
- parametrización de cuentas, perfiles de contabilización, tasas y centros de costo, con las limitaciones indicadas más adelante;
- lotes de licencias, asignación PEPS, kárdex de licencias, servicios y cálculos de costos para las líneas de negocio modeladas;
- planilla, ajustes y cierre: liquidación de IVA, devengos, depreciación, amortización de intangibles, estimación de incobrables, deterioro y reversión de inventario, CIF y costeo por procesos;
- Libro Diario, Mayor, Balanza, Cuenta T, Hoja de trabajo, Situación financiera, Resultados, libros de IVA, auxiliares y exportación a PDF/XLSX/ZIP;
- respaldo manual mediante `pg_dump`, y un panel de soporte para usuarios, configuración, respaldos y consulta de registros técnicos.

No es una aplicación web, multiusuario, bancaria, de comercio electrónico ni multimoneda. No transmite DTE oficiales, no entrega licencias por portal ni ejecuta pagos. La nómina y los parámetros fiscales son de demostración y requieren revisión profesional antes de cualquier uso real.

## Límites conocidos del comportamiento actual

- Las reglas de obligatoriedad, origen y exclusividad de dimensiones no se aplican por cuenta. El detalle de una partida admite los campos libres de auxiliar y centro de costo, pero no existe un motor de dimensiones configurables.
- Los parámetros de políticas y secuenciales se almacenan, pero el folio y la igualdad Debe/Haber se resuelven con reglas fijas del motor.
- La liberación de un lote de licencias recibe un costo total manual y usa la versión de producto actualmente fijada en el servicio; no implementa una consolidación automática de costos por producto y versión.
- Las órdenes de software a la medida tienen creación, ciclo de estados (ABIERTA → EN_PROCESO → ENTREGADA → CERRADA / CANCELADA) y rentabilidad por costo acumulado, con edición de órdenes en estado ABIERTA/EN_PROCESO y bloqueo de costeo por entrega no implementado. Los cálculos de SaaS, planes y órdenes son apoyo gerencial, no una trazabilidad integral de rentabilidad por contrato.
- Los libros de IVA separan CCF de consumidor final de forma excluyente; los auxiliares muestran una dimensión por tipo (cliente, proveedor, lote, orden o centro de costo) y el saldo acumulado por dimensión, sin combinación ni filtro por dimensión específica.
- La exportación por varios períodos existe y genera archivos por combinación seleccionada en una carpeta. El ZIP se genera para un rango y contiene un conjunto fijo de reportes.
- El monitoreo técnico consulta la tabla `log_tecnico`, que se inicializa con registros de demostración si está vacía; no consume automáticamente el archivo de log de la aplicación. `AppLog` escribe en `%USERPROFILE%\.colibri\colibri.log`, mientras el lanzador de usuario escribe en `%LOCALAPPDATA%\Colibri\logs`.
- La clave temporal emitida por Soporte Técnico desbloquea la cuenta y sustituye la contraseña con vencimiento de 24 horas; al entrar con ella, el sistema obliga a definir una clave propia que la invalida.
- El respaldo es manual, depende de `C:\Program Files\PostgreSQL\18\bin\pg_dump.exe` y no incluye programación, cifrado, incremental ni restauración desde la interfaz.

## Arquitectura

El proyecto está organizado en cinco módulos Maven con dependencias orientadas desde la interfaz hacia los servicios y el núcleo:

```text
colibri-desktop       JavaFX, navegación y vistas
        ↓
colibri-application   servicios de aplicación y casos de uso
        ↓
colibri-domain        reglas y modelos sin JavaFX ni JDBC
colibri-persistence   JDBC, HikariCP, repositorios y Flyway
colibri-reports       composición y exportación PDF/XLSX/ZIP
```

`AppContext` es la raíz de composición. Al iniciar, carga la configuración de base de datos, ejecuta Flyway, crea los servicios y asegura los datos de arranque. Las operaciones de escritura se coordinan mediante transacciones JDBC.

Los módulos principales son:

- **`colibri-domain`**: modelos contables, reglas de partida doble, IVA, seguridad y cálculos puros.
- **`colibri-persistence`**: configuración, conexión Hikari, migraciones y repositorios JDBC.
- **`colibri-application`**: autenticación, catálogo, períodos, partidas, documentos, inventario, costos, cierre, planilla, reportes, panel, plantillas, respaldo y soporte.
- **`colibri-reports`**: generación de estructuras para PDF, Excel y ZIP.
- **`colibri-desktop`**: aplicación JavaFX, navegación por rol, vistas operativas, tema e identidad visual.

## Tecnologías

| Componente | Versión configurada | Uso |
|---|---:|---|
| Java | 25 | Lenguaje y runtime |
| JavaFX | 25 | Interfaz de escritorio |
| PostgreSQL | 18 en el entorno documentado | Persistencia |
| Maven Wrapper | Maven 3.9.11 | Compilación multmódulo |
| HikariCP | 6.3.0 | Pool JDBC |
| Flyway | 11.10.0 | Migraciones de esquema |
| JasperReports | 7.0.7 | Reportes PDF |
| Apache POI | 5.4.1 | Exportación XLSX |
| Jackson | 2.22.3 | JSON interno |
| JUnit, Testcontainers y TestFX | configurados en el POM | Pruebas |

Flyway aplica las migraciones **V1 a V28** al arrancar. `V24` incorpora cuentas y planes de referencia, `V25` crea el kárdex de movimientos de licencias, `V26` agrega el vencimiento de la clave temporal, `V27` la descripción y el uso del centro de costo, y `V28` reconstruye el historial del kárdex.

## Requisitos y configuración local

1. Instalar JDK 25 y PostgreSQL 18.
2. Copiar `application.local.properties.example` a `%USERPROFILE%\.colibri\application.local.properties` y ajustar las credenciales.
3. El archivo de ejemplo y `scripts\setup-postgres.ps1` usan el puerto 5434. Si no existe una configuración local, `DatabaseConfig` y los lanzadores usan 5432 como valor de respaldo.

Para preparar el entorno de demostración en el puerto configurado:

```powershell
$env:PGPASSWORD = 'CONTRASENA_DEL_SUPERUSUARIO'
.\scripts\setup-postgres.ps1
```

El script crea o actualiza el rol y las bases `colibri` y `colibri_test`. Las credenciales iniciales que genera el bootstrap (`contador` y `soporte`) son sólo para desarrollo y deben reemplazarse antes de usar una base no desechable.

## Compilar y ejecutar

Desde la raíz del repositorio:

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd -pl colibri-desktop -am install -DskipTests
.\mvnw.cmd -pl colibri-desktop javafx:run
```

También están disponibles estos flujos:

- `scripts\run-colibri.ps1`: compila, migra y abre la aplicación. `-Reset` reemplaza los datos operativos por la semilla del Anexo L; úselo sólo sobre una base de demostración.
- `scripts\run-colibri-complete.ps1 -Reset`: carga y verifica el ejercicio contable integral mediante los scripts SQL; también elimina datos operativos existentes.
- `scripts\Iniciar-Colibri.ps1`: lanzador para usuario final. `Colibri.vbs` lo ejecuta sin consola y `Colibri.cmd` conserva la consola visible.

Las pruebas pueden usar la base separada configurando:

```powershell
$env:COLIBRI_TEST_DB_URL = 'jdbc:postgresql://127.0.0.1:5434/colibri_test'
$env:COLIBRI_TEST_DB_USER = 'colibri'
$env:COLIBRI_TEST_DB_PASSWORD = 'colibri'
.\mvnw.cmd test
```

## Datos de demostración

Los scripts de `scripts\db` preparan referencias, el Anexo L y el ejercicio integral. Son datos de práctica y las operaciones `-Reset`, `reset_total.sql` y los seeds correspondientes pueden reemplazar datos operativos. Antes de ejecutarlos, confirme que la base seleccionada sea desechable o esté respaldada.

## Documentación vigente

| Documento | Propósito |
|---|---|
| `Analisis_Sistema_Colibri.txt` | Análisis del sistema: frontera, entradas, procesos, salidas, actores y límites. |
| `CasosDeUso.md` | Casos de uso reales del rol Contador, con límites explícitos del prototipo. |
| `CasosDeUso_SoporteTecnico.md` | Casos de uso reales del rol Soporte Técnico. |
| `MANUAL_DE_USO_COLIBRI.md` | Manual operativo por pantalla: flujos, alternativas y límites. |
| `ESCENARIO_EJERCICIO_CONTABLE_COMPLETO.md` | Escenario de demostración y precauciones de carga. |
| `rubrica_de_evaluacion_del_proyecto.md` | Matriz académica y notas de evidencia. |
| `sintesis-formulas-contables.md` | Material académico de referencia; no todas las fórmulas son automatismos del software. |
| `DESCARTE.md` | Elementos descartados y decisiones de alcance. |
| `PLAN_DE_PRUEBAS.md` | Lista de verificación de aceptación por módulo y sección. |
| `INFORME_DE_PRUEBAS_PARANOICO_2026-11-18.md` | Evidencia de la ejecución de pruebas automatizadas y pendientes manuales. |
| `REVISION_UX_2026-11-18.md` | Revisión de usabilidad por módulo y correcciones aplicadas. |
| `AUDITORIA_CORRECCIONES_RESUMEN.md` | Resumen de las correcciones de la auditoría forense contable. |
| `INVENTARIO_CLASES_CRITICAS.md` | Inventario de clases con cálculos monetarios previo a la auditoría. |

Los informes con fecha en el nombre (`*_AAAA-MM-DD.md`) son evidencias de una ejecución concreta; describen el estado del sistema en esa fecha y no se reescriben cuando el código avanza. La especificación de la auditoría forense se conserva en `.kiro/specs/auditoria-forense-contable/`.

Los instrumentos, políticas y especificaciones que se encuentran en el directorio superior del proyecto describen el marco organizativo y académico. Deben leerse junto con sus leyendas de estado: una política documentada no implica por sí sola que exista un control automático en Colibrí.
