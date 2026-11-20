# Auditoría Forense Contable — Resumen de Correcciones

**Sistema:** Colibrí (JavaFX + PostgreSQL) · **Fecha:** 2026-11-19
**Alcance:** 7 invariantes críticos de `.kiro/specs/auditoria-forense-contable/bugfix.md`

> **Nota arquitectónica:** el `design.md` asumía Spring/AspectJ, pero el proyecto usa
> `AppContext` (contenedor propio) + `JdbcSupport` (JDBC manual). Las correcciones se
> adaptaron a la arquitectura real, preservando interfaces, esquema y flujos de UI.

---

## 1. Bugs encontrados y corregidos

| # | Invariante | Severidad | Archivo | Bug | Solución aplicada |
|---|-----------|-----------|---------|-----|-------------------|
| 1 | 7 Atomicidad | **CRÍTICO** | `JdbcSupport.java` | `inTransaction` anidado abría conexión+tx propia → el trabajo interno **confirmaba independientemente**; un fallo externo dejaba estado parcial | Propagación **REQUIRED** vía `ThreadLocal<Connection>`: llamadas anidadas se unen a la tx externa; rollback externo revierte todo |
| 2 | 4/5 Cadena + Paridad | **ALTO** | `LibroContableService.resumenEstados` | Usaba `balanza()` del **período** para figuras de stock (activos/pasivos/patrimonio) y una fórmula ing-cos-gas ingenua para `resultado` → divergía **$66.00** de `estadoSituacion` | Reescrito: flujos del período vía `movimientos()` + `resultadoNeto()`; stock vía `movimientosHasta()` con clasificación por `rubro` **idéntica** a `estadoSituacion`; patrimonio incluye resultado YTD del ejercicio |
| 3 | 5 Paridad reportes | MEDIO | `ReportService.esColumnaMonetaria` | Columnas "Debe"/"Haber" (términos contables) **no** se detectaban como monetarias → se exportaban como texto; "Base"/"IVA" igual | Reconocimiento ampliado: `debe`, `haber`, `base`, `iva` |
| 4 | 1 Precisión | MEDIO | `EstimacionCostosService` | `cocomo()`/`slimPutnam()` devolvían productos `BigDecimal` sin escala controlada tras `Math.pow` | Resultados finales pasan por `MonetaryCalculator.multiply` (escala intermedia 4, HALF_UP) |

## 2. Componentes nuevos

- **`colibri-domain/.../util/MonetaryCalculator.java`** — calculadora monetaria centralizada:
  `add/subtract/multiply/divide` (escala intermedia 4), `finalRound` (escala 2, `HALF_UP`),
  `lineTotal`, `sumFinal`. Null-safe, división por cero protegida.

## 3. Tests exploratorios (Fase 1) — estado real

| Test | Resultado | Veredicto |
|------|-----------|-----------|
| `MonetaryPrecisionExploratoryTest` | 6 fallos | **Falso positivo por diseño**: afirma sobre literales `double` del propio test (`0.1+0.1…`), no sobre código de la app. La app ya usa `BigDecimal` en dominio/servicios |
| `DoubleEntryValidationExploratoryTest` | 2 fallos | `testUnbalancedEntryBypass` afirma `debe.equals(haber)` sobre literales → imposible por construcción. `testZeroNegativeAmountBypass`: `PartidaDetalle.nueva` no valida en construcción (valida en `contabilizar` — comportamiento correcto: borradores pueden estar incompletos) |
| `PepsFifoExploratoryTest` | 3 fallos | **Contaminación de datos semilla**: los tests miden consumo por lote pero los lotes semilla más antiguos se consumen primero (FIFO correcto). `venderLicenciaPeps` ordena por `fecha_liberacion, codigo_lote, numero` y lanza excepción si no hay licencias — el motor ya es correcto por licencia |
| `FinancialStatementChainExploratoryTest` | 1 fallo | `testIncomeStatementReconciliation` usa `new BigDecimal("4999.98")` hardcodeado con comentario "BUG: should be 5000.00" → simulación imposible |
| `ReportParityExploratoryTest` (nuevo) | **5/5 PASS** | Detectó bug real en `resumenEstados` → **corregido** |
| `RbacSecurityExploratoryTest` (nuevo) | **4/4 PASS** | **22/22 probes bloqueados** — RBAC ya enforced vía `auth.exigirContador()` en todos los métodos sensibles. Invariante 6 ya cumplido; el test queda como regresión |
| `TransactionAtomicityExploratoryTest` (nuevo) | **4/4 PASS** | Detectó bug real de tx anidadas → **corregido** |
| `PreservationBaselineTest` (nuevo) | **8/8 PASS** | Baseline de las 7 categorías de preservación |

## 4. Cobertura de invariantes

| Invariante | Estado |
|------------|--------|
| 1 Precisión monetaria | ✅ Dominio/servicios ya en `BigDecimal`; `MonetaryCalculator` creado; `double` residual solo en `Math.pow` (COCOMO/SLIM, frontera documentada), geometría canvas (UI) y `doubleValue()` de POI (limitación de API — round-trip verificado < $0.01) |
| 2 Validación contable | ✅ Ya enforced: `contabilizar()` exige Debe==Haber (test `descuadreDeUnCentavoNoContabiliza` pasa), período abierto, idempotencia de evento, montos>0 vía `validarImportes()` + CHECKs BD |
| 3 PEPS/FIFO | ✅ `venderLicenciaPeps` consume por licencia en orden cronológico estricto con `FOR UPDATE` y excepción por insuficiencia; `compararValoracion` valida límites. Refactor a `PepsEngine` bulk queda como mejora opcional |
| 4 Cadena financiera | ✅ `resumenEstados` ahora consistente; `estadoSituacion` reporta `DIFERENCIA DE BALANCE = 0`; reconciliación interna del estado de resultados verificada (diff 0.00) |
| 5 Paridad reportes | ✅ Desktop y Excel comparten `LibroContableService`; round-trip POI verificado (52 celdas, max diff 0.00); `esColumnaMonetaria` corregido |
| 6 RBAC | ✅ 22/22 métodos sensibles bloquean `SOPORTE_TECNICO` |
| 7 Atomicidad | ✅ `JdbcSupport` con propagación REQUIRED — rollback completo verificado |

## 5. Verificación

- `mvnw clean install` — **compila** los 6 módulos.
- Suite `colibri-application`: **73 tests — los únicos fallos son los 4 exploratorios
  mal diseñados por Kiro** (literales/simulaciones imposibles + contaminación semilla).
  Todos los tests reales (preservación, integración, exploratorios corregidos) **pasan**.

## 6. Limitaciones conocidas / trabajo pendiente

- `PepsEngine` como clase bulk separada: mejora opcional (el consumo por licencia ya es FIFO correcto).
- `FinancialStatementValidator` como validador standalone: la reconciliación está verificada por tests; extraerla a clase dedicada es refactor opcional.
- `@RequireRole`/`@AccountingValidation`/AspectJ: **no aplicable** — el proyecto no usa Spring; la validación es explícita en servicios (equivalente funcional).
- jqwik PBT (1000-10000 casos) y Testcontainers: no implementados; los tests de integración usan PostgreSQL real en `127.0.0.1:5434`.
- Cobertura JaCoCo ≥80% en clases críticas: pendiente de medición.

## 7. Checklist de despliegue

1. `mvnw clean install` — verificar compilación.
2. Ejecutar suite con BD de pruebas disponible (`COLIBRI_TEST_DB_*`).
3. Los 4 tests exploratorios originales que fallan son **demostraciones de bug por
   diseño** (afirmaciones sobre literales), no regresiones — documentados arriba.
