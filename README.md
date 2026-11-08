# Colibrí — Sistema Contable (escritorio Java)

<!--
Documento principal de referencia del repositorio.
Resume el alcance funcional, la arquitectura, el estado del prototipo y las
limitaciones vigentes para mantener una documentación homogénea con el resto
de archivos del proyecto.
-->

Prototipo funcional para **Soluciones Empresariales Colibrí, S.A. de C.V.**  
Sistema de escritorio **local y monousuario** (JavaFX + PostgreSQL). No emite DTE oficiales ni se integra con bancos, servicios web o multimoneda.

Actores:
- **Contador** — operación contable: Empresa, Parametrización, Registros, Documentos, Inventario, Costos, Reportes.
- **Soporte técnico** — administración: Usuarios, Base de datos, Entorno, Monitoreo, Accesos. No consulta saldos ni partidas.

---

## 1. Estado y límites actuales del sistema

| Área | Estado |
|---|---|
| Arquitectura por capas (5 módulos Maven) | Implementada |
| Autenticación, roles, bloqueo y bitácora | Implementados |
| Catálogo de cuentas a 6 dígitos y períodos contables | Implementados |
| Partida doble, inmutabilidad y validaciones | Implementadas |
| Documentos internos e IVA neto/incluido | Implementados con tasas y cuentas parametrizables |
| Inventario y costos | PEPS automático para licencias descargables; órdenes para software a la medida; SaaS por usuario-mes y plan; servicios por plan; costeo por procesos y CIF aplicado/cerrado |
| Ajustes y cierre | IVA acumulado, consumo y devengo, depreciación/amortización, provisión de incobrables, deterioro/reversión y protección de cierre |
| Planilla | Cálculo configurable de cargas laborales, cédula por período y asiento de devengo |
| Libros y estados | Diario, Mayor, Balanza, Cuenta T, Hoja de trabajo, Situación financiera, Resultados, libros de IVA y auxiliares disponibles en el selector; Cambios en el patrimonio y Flujos de efectivo se generan en el ZIP |
| Flujo de efectivo | Método directo limitado a Caja chica y Bancos (grupo 1101); requiere conciliación documental antes de uso definitivo |
| Respaldo | Ejecución manual de `pg_dump`; no incluye cifrado, programación, incremental ni restauración desde la interfaz |
| Exportación | PDF, XLSX y ZIP del rango seleccionado; el ZIP reúne Diario, Mayor, Balanza, Hoja de trabajo y los cuatro estados financieros disponibles |
| Soporte técnico y entorno | Gestión de usuarios, bitácoras, parámetros de entorno y respaldo manual |
| Migraciones Flyway `V1`–`V19` | Aplicadas por el arranque |
| Identidad visual | Integrada |

Fuera de alcance: emisión o transmisión oficial de DTE, conexiones bancarias o cloud, portal de descargas, web/multiusuario, multimoneda, cálculo legal integral de nómina con topes y reglas vigentes, prorrata automática de IVA mixto, respaldo programado/cifrado/incremental y restauración desde la interfaz. La planilla implementada calcula cargas configurables y genera su devengo; requiere validación profesional para uso laboral real. Las políticas fiscales también deben revisarse antes de aplicarlas a operaciones reales.

### Formatos contables emitidos

- **Libro Diario:** Fecha, PDA, código/referencia, cuenta y concepto, Debe y Haber, agrupados por partida y con totales.
- **Libro Mayor y Cuenta T:** código, cuenta, acumulados Debe/Haber, saldos deudor/acreedor y desglose de cargos, abonos y totales por cuenta.
- **Balanza de comprobación:** código, cuenta, Debe, Haber y fila de comprobación final.
- **Hoja de trabajo:** catálogo; balanza; ajustes; balanza ajustada; resultados; capital; y situación financiera, con Debe/Haber en cada bloque.
- **Estados:** situación financiera con clasificación de activo, pasivo y patrimonio; y resultados con ventas netas, costo de ventas, utilidad bruta, gastos de operación, resultado financiero/otros, impuesto y utilidad neta.

Para licencias descargables, el sistema asigna automáticamente la unidad disponible del lote liberado más antiguo mediante PEPS. La pantalla presenta PEPS, promedio ponderado y UEPS sólo como comparación analítica; promedio y UEPS no generan asientos ni cambian la política contable seleccionada.

---

## 2. Stack y librerías

| Componente | Versión / artefacto | Uso en el sistema |
|---|---|---|
| Java | 25 LTS (`maven.compiler.release=25`) | Lenguaje y runtime |
| JavaFX | 25 (`base`, `controls`, `fxml`, `graphics`) | Interfaz de escritorio (MVVM ligero) |
| PostgreSQL | 18 (puerto **5434** en el entorno documentado) + driver JDBC `42.7.12` | Persistencia relacional |
| Maven | 3.9.11 (Wrapper `mvnw`) | Build multmódulo y empaquetado |
| HikariCP | 6.3.0 | Pool de conexiones |
| Flyway | 11.10.0 (`flyway-core`, `flyway-database-postgresql`) | Migraciones versionadas del esquema |
| JasperReports | 7.0.7 (+ `jasperreports-pdf`) | Reportes PDF |
| Apache POI | 5.4.1 (`poi`, `poi-ooxml`) | Exportación Excel (XLSX) |
| Jackson | 2.22.3 (`databind`, `jsr310`) | Serialización JSON de estructuras internas |
| SLF4J | 2.0.17 (`slf4j-api`, `slf4j-simple`) | Logging de la aplicación |
| JUnit 5 | 5.13.4 | Pruebas unitarias/integración |
| AssertJ | 3.27.7 | Aserciones fluidas |
| Testcontainers | 1.21.3 (`postgresql`) | PostgreSQL efímero para pruebas |
| TestFX | 4.0.18 (+ `openjfx-monocle`) | Pruebas de UI |

---

## 3. Arquitectura

Arquitectura **en capas** con dependencias en un solo sentido (puertos y adaptadores): el núcleo contable no conoce la base de datos ni la interfaz.

```
            colibri-desktop  (adaptador de ENTRADA: JavaFX)
                   │  depende de ↓
            colibri-application  (servicios de aplicación / casos de uso)
                   │  depende de ↓
   colibri-domain ──── colibri-persistence  (adaptador de SALIDA: JDBC + Flyway)
   (núcleo: reglas)      (repositorios, conexión)
                   ↑
            colibri-reports  (exportación PDF/Excel)

   Regla de dependencia: desktop → application → { domain, persistence, reports }
                         persistence → domain
                         domain → (sin dependencias de framework)
```

- **Domain** — entidades y reglas puras (partida doble, IVA, estados). Sin JavaFX ni JDBC.
- **Persistence** — repositorios (mapeo ResultSet↔dominio), pool Hikari, migraciones Flyway.
- **Application** — orquesta los casos de uso y expone servicios vía `AppContext`; las operaciones de escritura se ejecutan en transacciones JDBC.
- **Reports** — API mínima de composición/exportación (Jasper/POI).
- **Desktop** — JavaFX: shell, navegación, vistas por módulo e identidad visual.

### Punto de composición
`AppContext` es la **raíz de composición**: construye el pool, aplica migraciones, inicializa los servicios y habilita la semilla de demostración sólo por configuración explícita (inyección manual por constructor). Las vistas reciben únicamente el `AppContext`.

---

## 4. Módulos y paquetes

```
Colibri/
├─ pom.xml                         Parent POM (dependencyManagement centralizado)
├─ mvnw / mvnw.cmd                 Maven Wrapper
├─ application.local.properties.example
├─ colibri-domain/                 Núcleo (sin frameworks)
├─ colibri-persistence/            Persistencia
├─ colibri-application/            Servicios / casos de uso
├─ colibri-reports/                Exportación de reportes
├─ colibri-desktop/                Interfaz JavaFX
├─ scripts/                        setup-postgres.ps1/.sql, run-colibri.ps1, anexo_l/, db/
└─ CasosDeUso.md · CasosDeUso_SoporteTecnico.md · Analisis_Sistema_Colibri.txt · DESCARTE.md
```

### colibri-domain — `sv.edu.ues.colibri.domain`
| Paquete | Contenido |
|---|---|
| `model` | `Partida`, `PartidaDetalle`, `Cuenta`, `Documento`, `Tercero`, `Empresa`, `Ejercicio`, `Periodo`, `Contador`, `BitacoraEntrada`, `EventoContable`, y enums `EstadoPartida`/`EstadoCuenta`/`EstadoPeriodo`/`NaturalezaCuenta`/`TipoPartida`/`OrigenPartida`/`RolUsuario` |
| `iva` | `IvaCalculator` (+ `Resultado`) — cálculo desde neto o desde total incluido con tasa suministrada |
| `security` | `PasswordHasher` — PBKDF2-HMAC-SHA256 con sal; los parámetros se gestionan en el código de seguridad |
| `exception` | `DomainException` |

### colibri-persistence — `sv.edu.ues.colibri.persistence`
| Paquete | Contenido |
|---|---|
| `config` | `DatabaseConfig` (carga credenciales), `DataSourceFactory` (Hikari), `FlywayMigrator` |
| `jdbc` | `JdbcSupport` (transacciones), `PersistenceException` |
| `repo` | `ContadorRepository`, `CuentaRepository`, `PartidaRepository`, `DocumentoRepository`, `TerceroRepository`, `EmpresaRepository`, `PeriodoRepository`, `EventoRepository`, `BitacoraRepository`, `CatalogoVersionRepository` |
| recursos | `db/migration/V1__nucleo.sql` … `V19__serializacion_cierre_cedulas_pendientes.sql`, `application.properties` |

### colibri-application — `sv.edu.ues.colibri.application`
| Paquete | Contenido |
|---|---|
| (raíz) | `AppContext` — raíz de composición y fachada de servicios |
| `auth` | `AuthService` — login, sesión, roles, intentos, recuperación |
| `bootstrap` | `BootstrapService`, `CasoContableSeedService` — inicialización segura y semilla de demostración optativa |
| `catalogo` | `CatalogoService` |
| `periodo` | `PeriodoAppService` |
| `partida` | `PartidaService` — captura y contabilización |
| `operacion` | `OperacionContableService` (+ `PlantillaDraft`) — asientos automáticos |
| `inventario` | `InventarioCostoService` (+ `LoteInfo`, `OrdenInfo`, `ReservaLicencia`) |
| `costos` | `EstimacionCostosService`, `CosteoGerencialService` — estimación, precio unitario, costo directo, estándar y ABC |
| `libros` | `LibroContableService` — Diario, Mayor, Balanza, Estados |
| `cierre` | `CierreService` — IVA, devengos, depreciación/amortización, estimaciones de cartera, deterioro y cierre de período |
| `costos` | `CifService`, `CosteoPorProcesosService` — aplicación/cierre de CIF y cédulas PEPS, promedio y UEPS |
| `nomina` | `PlanillaService` — cálculo parametrizado de cargas y contabilización del devengo |
| `configuracion` | `ParametrizacionService` (+ `Parametro`, `ParametroFiscal`, `CentroCosto`, `PlanServicio`, `PostingProfile`) |
| `respaldo` | `RespaldoService` — `pg_dump` |
| `soporte` | `SoporteTecnicoService` (+ `Entorno`, `LogTecnico`) |
| `support` | `JsonSupport` — serialización JSON interna |

### colibri-reports — `sv.edu.ues.colibri.reports`
- `ReportService` — composición y exportación PDF/Excel y paquete ZIP.

### colibri-desktop — `sv.edu.ues.colibri.desktop`
| Paquete | Contenido |
|---|---|
| (raíz) | `ColibriApp` (entrypoint), `InstanceLock`, `AppLog` |
| `ui` | `MainShell` (BorderPane: menú lateral + barra superior + una página), `ResponsiveLayouts`, `UiTheme`, `UiDialogs`, `BrandAssets`, `BrandLogo`, `BrandIcon`, `LoginView` |
| `ui.nav` | `NavItem`, `NavRegistry` (árbol de navegación por rol, fuente única), `SideNav` (acordeón), `SectionHost`, `Navegador` |
| `ui.layout` | `Page` (scroll/fill), `Breakpoints` (pseudo-clases `:compact/:medium/:wide`), `SegmentedNav`, `MasterDetail` |
| `ui.views` | `InicioView`, `EmpresaPeriodosView`, `ParametrizacionView` (una sección por ítem del menú), `TemasContablesView`, `CatalogoView`, `PartidasView`, `DocumentosView`, `PlanillaView`, `InventarioCostosView`, `ProcesosCifView`, `EstimacionCostosView`, `SocietariosView`, `ReportesView`, `AjustesCierreView`, `RespaldoView`, `SoporteTecnicoView`, `UsuariosView` |
| recursos | `colibri.css`, `img/` (marca, imagotipo, splash e iconos multiresolución 16–512) |

---

## 5. Patrones de diseño

| Patrón | Dónde se aplica |
|---|---|
| **Capas / Puertos y Adaptadores** | Separación domain ↔ application ↔ adaptadores (persistence, desktop) |
| **Repository** | `repo/*` encapsula el acceso a datos por agregado |
| **Data Mapper** | Repositorios traducen `ResultSet` ↔ entidades de dominio |
| **Facade / Application Service** | `AppContext` y los `*Service` como fachadas de casos de uso |
| **Composition Root + DI por constructor** | `AppContext` crea e inyecta todas las dependencias |
| **Unit of Work / Transaction Script** | `JdbcSupport.inTransaction` / `runInTransaction` |
| **Factory** | `DataSourceFactory` (crea pools Hikari) |
| **Singleton (proceso)** | `InstanceLock` garantiza una sola instancia; pool único de Hikari |
| **DTO / Record** | `LoteInfo`, `OrdenInfo`, `Parametro`, `PostingProfile`, `LogTecnico`, etc. |
| **Value Object / Enum** | `NaturalezaCuenta`, `TipoPartida`, `EstadoPartida`, `RolUsuario` |
| **Strategy / cálculo aislado** | `IvaCalculator` desacopla el cálculo fiscal |
| **Bootstrap / semilla controlada** | `BootstrapService`, `CasoContableSeedService` |
| **Esquema versionado (migraciones)** | Flyway `V1`–`V19` |
| **MVC/MVVM ligero (JavaFX)** | `MainShell` + `views` + binding a `Property` |
| **Observer** | Listeners de JavaFX y `textProperty` en `UiTheme` |
| **Decorator** | `UiTheme.decorate` enriquece controles con iconos/estilos |
| **Lock / Failsafe de arranque** | `InstanceLock` + `Arranque` en `ColibriApp` |

---

## 6. Decisiones de uso

- **Local y monocapa de escritorio.** Sin red, sin web y sin multiusuario; el sistema es un prototipo funcional de un solo equipo.
- **Separación de roles (menor privilegio).** El Contador no administra usuarios ni base de datos; el Soporte Técnico no accede a saldos ni partidas (`exigirContador` / `exigirSoporteTecnico`).
- **Credenciales fuera del código.** Se leen de `%USERPROFILE%\.colibri\application.local.properties` (ver `application.local.properties.example`). Nunca se versionan secretos.
- **Puerto 5434.** Se usa 5434 (no 5432) para no chocar con instalaciones locales de PostgreSQL.
- **Seguridad de contraseñas.** Hash PBKDF2-HMAC-SHA256 con sal (`PasswordHasher`); nunca texto claro.
- **Política de acceso.** Bloqueo tras 5 intentos fallidos (15 min) y expiración de sesión por inactividad (15 min).
- **Recuperación local.** Código de un solo uso escrito en `%USERPROFILE%\.colibri\recovery-code.txt`; se rota al usarse. Soporte Técnico puede emitir clave temporal.
- **Inicialización conservadora.** `BootstrapService` crea empresa, ejercicio, períodos y cuentas iniciales sólo si no existen; nunca restablece credenciales, roles, estados ni datos ya administrados. La semilla de demostración se habilita únicamente con `-Dcolibri.seed.demo=true` o `COLIBRI_SEED_DEMO=true`.
- **Integridad contable.** Partida doble con descuadre exacto ($0.00; 0.01 en pruebas); partidas contabilizadas inmutables y reversión solo vía ajustes (`AJ`).
- **Períodos.** La captura se bloquea en fechas cerradas; la aprobación del catálogo habilita movimientos.
- **Esquema gestionado.** Flyway aplica migraciones versionadas; el pool Hikari se crea solo tras migrar.
- **Continuidad.** El soporte técnico puede ejecutar un respaldo manual con `pg_dump` en `~/.colibri/backups`; la restauración, el cifrado y la programación son procedimientos externos al prototipo. Se conservan bitácoras de acceso y técnicas.
- **Identidad visual empaquetada.** Logo, imagotipo, splash e iconos de barra de tareas se cargan desde recursos (`BrandAssets`), no desde rutas externas.
- **Simplicidad de despliegue.** Build reproducible con Maven Wrapper; sin contenedores en producción local.

---

## 7. Requisitos previos

1. JDK 25 (LTS) en `PATH`; Maven Wrapper usa `maven.compiler.release=25`.
2. PostgreSQL 18. En esta máquina el servicio escucha en **puerto 5434** (no 5432).
3. Crear rol/bases:

```powershell
# Si pg_hba tiene trust en 127.0.0.1, basta:
.\scripts\setup-postgres.ps1

# Si exige password del superusuario:
$env:PGPASSWORD = 'TU_PASSWORD_DE_POSTGRES'
.\scripts\setup-postgres.ps1
```

Crea el rol `colibri`/`colibri`, las bases `colibri` y `colibri_test`, y
`%USERPROFILE%\.colibri\application.local.properties`. Al primer arranque, Flyway
aplica las migraciones y, sólo si no existen cuentas, el bootstrap crea empresa,
períodos y los usuarios iniciales `contador` / `Colibri2026!` y `soporte` /
`Soporte2026!`. Cambie esas credenciales antes de operar datos reales.

4. Si cambió `pg_hba.conf` (p. ej. `trust` antes de `scram-sha-256` en localhost), reinicie como administrador:

```powershell
Restart-Service postgresql-x64-18
```

---

## 8. Compilar y ejecutar

```bash
./mvnw -v
./mvnw clean test
./mvnw -pl colibri-desktop -am install -DskipTests
./mvnw -pl colibri-desktop javafx:run
```

En Windows PowerShell:

```powershell
.\mvnw.cmd clean test
.\scripts\run-colibri.ps1
```

El comando `install` recompila e instala en el repositorio Maven local los cinco JAR de módulos y sus dependencias; el JAR de `colibri-desktop` es modular por capas y se ejecuta con JavaFX mediante Maven, no como `java -jar` autónomo. `run-colibri.ps1` realiza la instalación antes de abrir la aplicación. El arranque carga la configuración local, valida/construye las 19 migraciones Flyway y abre la pantalla de acceso.

`run-colibri.ps1` instala primero los módulos hermanos en `~/.m2` y después ejecuta JavaFX. Así evita mezclar la interfaz recién compilada con JARs antiguos de `application`, `domain` o `persistence`.

### Credenciales iniciales

- **Contador:** `contador` / `Colibri2026!` — Empresa, Parametrización, Registros, Documentos, Inventario, Costos y Reportes.
- **Soporte técnico:** `soporte` / `Soporte2026!` — Usuarios, Base de datos, Entorno, Monitoreo y Accesos.
- Cuando el bootstrap crea por primera vez la cuenta `contador`, genera un **código de recuperación de un solo uso** en `%USERPROFILE%\.colibri\recovery-code.txt`.

---

## 9. Migraciones y componentes implementados

- `V1__nucleo.sql` — autenticación, contador, catálogo, períodos, partidas, bitácora.
- `V2__catalogo.sql` — catálogo contable (tabla VIII).
- `V3__etapa2_documentos.sql` — terceros, documentos e IVA.
- `V4__etapa3_inventario_costos.sql` — inventario, lotes y órdenes de servicio.
- `V5__parametrizacion_casos_uso.sql` — parametrización, dimensiones y catálogos.
- `V6__roles_soporte_tecnico.sql` — rol de Soporte Técnico, correo y entorno técnico.
- `V7__perfil_posting_anticipo.sql` — perfil de contabilización para anticipos.
- `V8__costeo_por_plan_y_peps_exacto.sql` — modalidad de servicio, consumo por plan, usuarios-mes SaaS y PEPS exacto.
- `V9__politicas_contables_costos_y_rrhh.sql` — políticas de devengo, partida doble, consistencia, inventarios, CIF, distribución y control de horas.
- `V10`–`V14` — ajustes, estimaciones, amortización, deterioro, costos por proceso, integridad de cédulas, aplicación y cierre de CIF.
- `V15`–`V17` — tasas históricas y recuperación/inmutabilidad de cédulas de planilla.
- `V18`–`V19` — inmutabilidad de cédulas de cierre, restitución de auxiliares al revertir, serialización de captura y bloqueo de cierre con cédulas pendientes.

**Núcleo:** autenticación, catálogo, períodos, partida doble, bitácora y bloqueo de instancia.  
**Documentos e IVA:** terceros, documentos internos y plantillas con tasas parametrizadas.  
**Inventario y costos:** PEPS automático de licencias descargables, órdenes de servicio, acumulación por plan facturado y SaaS por usuarios-mes; estimación, precio unitario y comparación de costos directo, estándar y ABC.  
**Ecuaciones de estimación:** `EstimacionCostosService` aplica ecuaciones parametrizadas para juicio experto (promedio ponderado), analogía por tamaño, puntos de función, COSMIC, COCOMO II simplificado, corrección por desgaste/retrabajo, LOC, PERT de tres puntos y Putnam/SLIM. La interfaz muestra los insumos propios de cada técnica y los envía a las variantes parametrizadas del servicio.

En forma compacta: `Eexperto = Σ(wᵢ·Eᵢ)/Σwᵢ`; `Eanalogía = Eref·(Tobjetivo/Tref)·F`; `EFP = FP·h/FP`; `ECOSMIC = CFP·h/CFP`; `PMCOCOMO = 2.94·KLOC^(0.91+0.01·ΣSF)·ΠEM` y `h = PM·h/mes`; `Edesgaste = hproductivas/(1−desgaste)`; `ELOC = KLOC/productividad·160`; `EPERT = (O+4M+P)/6`; `ESLIM = [S/(Ck·t^(4/3))]^3·2080`. Después se calculan CIF, costo total y precio con margen mediante `precio = costoTotal/(1−margen)`.
**Temas y trazabilidad:** el menú Temas contables presenta una subpestaña por tema de la rúbrica y enlaza con el módulo operativo correspondiente.  
**Libros, cierre y respaldo:** Diario, Mayor, Balanza, Cuenta T, Hoja de trabajo, estados disponibles, Excel/PDF/ZIP, liquidación de IVA, devengos y provisiones de cierre, planilla configurable, CIF y costeo por procesos, y respaldo manual con `pg_dump`.

---

## 10. Pruebas

- Unitarias: equilibrio $0.01, códigos de 6 dígitos, hash y cálculo de IVA.
- Integración (si existe `colibri_test` en puerto 5434): catálogo, idempotencia de evento.
- UI: TestFX + Monocle.

```powershell
$env:COLIBRI_TEST_DB_URL="jdbc:postgresql://127.0.0.1:5434/colibri_test"
$env:COLIBRI_TEST_DB_USER="colibri"
$env:COLIBRI_TEST_DB_PASSWORD="colibri"
.\mvnw.cmd test
```

### Ejercicio histórico del Anexo L (seed)

El Anexo L del informe técnico se extrae a JSON y se carga como datos de ejemplo.

```powershell
# 1) Extraer el Anexo L (valida diario, balanza, estados y apertura derivada)
python scripts/anexo_l/extraer_anexo_l.py --docx ..\Informe_tecnico_Colibri.docx
# 2) Regenerar el SQL de seed y de verificación desde el JSON
python scripts/anexo_l/generar_seed_sql.py
# 3) Reset total (conserva solo `contador` y `flyway_schema_history`) + referencia + seed + verificación
psql -h 127.0.0.1 -p 5434 -U colibri -d colibri -v confirmar=SI -f scripts/db/reset_total.sql
psql -h 127.0.0.1 -p 5434 -U colibri -d colibri -f scripts/db/seed_00_referencia.sql
psql -h 127.0.0.1 -p 5434 -U colibri -d colibri -f scripts/db/seed_anexo_l.sql
psql -h 127.0.0.1 -p 5434 -U colibri -d colibri -f scripts/db/verificar_anexo_l.sql
```

Alternativa Java: con la base sin partidas, arrancar con `-Dcolibri.seed.anexol=true` o `COLIBRI_SEED_ANEXO_L=true` (`AnexoLSeedService`, excluyente con `colibri.seed.demo`). `seed_00_referencia.sql` se regenera con `scripts/anexo_l/generar_referencia.py` desde una base recién migrada cuando cambien los datos de referencia de Flyway.

---

## 11. Documentación del repositorio

| Archivo | Contenido |
|---|---|
| `CasosDeUso.md` | Casos de uso del actor Contador |
| `CasosDeUso_SoporteTecnico.md` | Casos de uso del actor Soporte Técnico |
| `Analisis_Sistema_Colibri.txt` | Entradas, procesos, salidas, controles y frontera del sistema |
| `rubrica_de_evaluacion_del_proyecto.md` | Criterios académicos del proyecto |
| `sintesis-formulas-contables.md` | Fórmulas contables y de costos, con notas de vigencia para El Salvador |
| `DESCARTE.md` | Archivos del directorio de trabajo excluidos del repositorio y su justificación |

