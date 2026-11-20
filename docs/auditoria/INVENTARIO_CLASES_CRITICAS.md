# Inventario de Clases Críticas - Sistema Colibri

**Fecha**: 18 de noviembre de 2026
**Propósito**: Inventario exhaustivo de clases con cálculos monetarios para análisis de impacto de auditoría forense

> **Nota de vigencia**: este inventario describe el estado **previo** a las correcciones de la
> auditoría forense contable (`AUDITORIA_CORRECCIONES_RESUMEN.md`, 19 de noviembre de 2026).
> Con posterioridad se agregó `colibri-domain/.../util/MonetaryCalculator.java` (calculadora
> monetaria centralizada con `BigDecimal`), por lo que los conteos de la sección 7 no incluyen
> esa clase. Para el estado corregido, consultar el resumen de auditoría.

---

## 1. COLIBRI-DOMAIN: Clases de Dominio con Cálculos Monetarios

### 1.1 Clases de Modelo con Campos Monetarios (BigDecimal)

#### PartidaDetalle.java
**Ubicación**: `colibri-domain/src/main/java/sv/edu/ues/colibri/domain/model/PartidaDetalle.java`
**Campos Monetarios**:
- `BigDecimal debe` - Importe al debe (normalizado a 2 decimales)
- `BigDecimal haber` - Importe al haber (normalizado a 2 decimales)

**Cálculos/Validaciones**:
- Normalización de importes a 2 decimales con `RoundingMode.HALF_UP`
- Validación de importes no negativos
- Validación de debe/haber mutuamente exclusivos
- Verificación de subcuentas de movimiento (6 dígitos)

**Criticidad**: ⚠️ **CRÍTICA** - Toda operación contable depende de estos importes

---

#### Documento.java
**Ubicación**: `colibri-domain/src/main/java/sv/edu/ues/colibri/domain/model/Documento.java`
**Campos Monetarios**:
- `BigDecimal baseGravada` - Base imponible gravada
- `BigDecimal iva` - Impuesto calculado
- `BigDecimal exento` - Monto exento
- `BigDecimal total` - Total del documento
- `boolean ivaIncluido` - Indica si IVA está incluido en total

**Cálculos/Validaciones**:
- Relación entre base gravada, IVA y total
- Manejo de documentos con IVA incluido vs excluido

**Criticidad**: ⚠️ **CRÍTICA** - Documentos tributarios y soporte fiscal

---

#### Partida.java
**Ubicación**: `colibri-domain/src/main/java/sv/edu/ues/colibri/domain/model/Partida.java`
**Campos Monetarios**:
- Cálculos derivados de `List<PartidaDetalle> detalles`
- Suma de debe vs suma de haber

**Cálculos/Validaciones**:
- `estaEquilibrada()` - Verifica que debe = haber
- `diferencia()` - Calcula diferencia entre debe y haber
- `aprobar()` - Valida equilibrio antes de aprobar
- Validación de partidas aprobadas/contabilizadas/revertidas

**Criticidad**: ⚠️ **CRÍTICA** - Control de equilibrio contable

---

### 1.2 Clases Calculadoras

#### IvaCalculator.java
**Ubicación**: `colibri-domain/src/main/java/sv/edu/ues/colibri/domain/iva/IvaCalculator.java`
**Constantes**:
- `TASA_DEFAULT = 0.13` (13% IVA)

**Métodos de Cálculo**:
- `desdeNeto(BigDecimal baseNeto, BigDecimal tasa)` - Calcula IVA desde base neta
- `desdeIncluido(BigDecimal totalIncluido, BigDecimal tasa)` - Extrae IVA de precio incluido
- `desdeImportacion(BigDecimal valorEnAduana, BigDecimal dai, BigDecimal tasa)` - Calcula IVA en importaciones (base incluye DAI)

**Retorna**: `Resultado` record con `base`, `iva`, `total`

**Criticidad**: ⚠️ **CRÍTICA** - Cálculo fiscal IVA

---

#### AjustesCalculator.java
**Ubicación**: `colibri-domain/src/main/java/sv/edu/ues/colibri/domain/calculation/AjustesCalculator.java`
**Métodos de Cálculo**:
- `interesAcumulado(capital, tasaAnual, meses)` - Intereses devengados
- `gastoDiferidoConsumido(importePagado, mesesCubiertos, mesesTranscurridos)` - Amortización de diferidos
- `depreciacionAnual(costo, valorResidual, vidaUtil)` - Depreciación anual activos
- `depreciacionPeriodica(anual, periodos)` - Depreciación mensual/trimestral
- `depreciacionSegura(costo, valorResidual, vidaMeses, acumuladaActual, mesesTranscurridos)` - Depreciación con límite de base depreciable
- `tasaAgotamiento(costoYacimiento, unidadesEstimadas)` - Tasa agotamiento recursos naturales
- `cargoAgotamiento(unidadesExtraidas, tasa)` - Cargo por agotamiento
- `amortizacionIntangible(costo, vidaUtil)` - Amortización de intangibles
- `incobrableEstimado(saldoClientes, porcentajeEstimado)` - Provisión cuentas incobrables
- `ingresoAcumulado(ingresoTotal, periodosTotales, periodosTranscurridos)` - Reconocimiento proporcional ingreso

**Criticidad**: ⚠️ **CRÍTICA** - Ajustes contables de cierre

---

#### CostosCalculator.java
**Ubicación**: `colibri-domain/src/main/java/sv/edu/ues/colibri/domain/calculation/CostosCalculator.java`
**Constantes de Nómina**:
- `TASA_ISSS_LABORAL = 0.03` (3% ISSS empleado)
- `TASA_ISSS_PATRONAL = 0.075` (7.5% ISSS empleador)
- `TASA_AFP_LABORAL = 0.0725` (7.25% AFP empleado)
- `TASA_AFP_PATRONAL = 0.0775` (7.75% AFP empleador)
- `TASA_INCAF = 0.01` (1% INCAF, aplica si ≥10 empleados)

**Métodos de Cálculo**:
- `costoProducto(materialesDirectos, manoObraDirecta, cifAplicado)` - Componentes del costo de producto
- `calcularPlanilla(salarioMinimo, sumaSalarios, numEmpleados, horasExtras, bonificaciones, vales)` - Cálculo completo de planilla con retenciones y aportes patronales
- `cifTasa(cifBudget, baseAsignacion)` - Tasa CIF
- `cifAplicado(produccionUnidades, tasaCif)` - CIF aplicado a producción
- `analizarCif(cifBudget, cifAplicado)` - Análisis variación CIF (subaplicado/sobreaplicado)
- `analizarOrden(costoTotal, precioVenta)` - Análisis costo/precio/utilidad por orden
- `puntosEquilibrio(costosVariablesUnitarios, precioVenta, costosFijosPeriodo)` - Punto de equilibrio unidades y monetario
- `margenContribucion(precioVenta, costosVariablesUnitarios)` - Margen de contribución unitario
- `costoPorUnidadEquivalente(costosTotales, unidadesEquivalentes)` - Costeo por procesos

**Criticidad**: ⚠️ **CRÍTICA** - Costeo de productos y análisis gerencial

---

#### ResultadosCalculator.java
**Ubicación**: `colibri-domain/src/main/java/sv/edu/ues/colibri/domain/calculation/ResultadosCalculator.java`
**Métodos de Cálculo**:
- `ventasNetas(ventas, devoluciones, descuentos)` - Ventas netas
- `comprasTotales(compras, gastosSobreCompras)` - Compras totales
- `comprasNetas(comprasTotales, devoluciones, descuentos)` - Compras netas
- `costoVentas(mercanciasDisponibles, inventarioFinal)` - Costo de ventas
- `utilidadBruta(ventasNetas, costoVentas)` - Utilidad bruta
- `utilidadOperacion(utilidadBruta, gastosOperativos)` - Utilidad operativa
- `utilidadAntesImpuestos(utilidadOperacion, ingresosNoOperativos, gastosNoOperativos, gastosFinancieros)` - UAI
- `utilidadNeta(utilidadAntesImpuestos, impuestoRenta)` - Utilidad neta después de impuestos
- `calcularResultadoMercantil(...)` - Cálculo completo del estado de resultados

**Criticidad**: ⚠️ **CRÍTICA** - Estados financieros y resultados

---

## 2. COLIBRI-APPLICATION: Servicios con Operaciones Contables

### 2.1 Servicios de Operaciones Contables

#### OperacionContableService.java
**Ubicación**: `colibri-application/src/main/java/sv/edu/ues/colibri/application/operacion/OperacionContableService.java`
**Funciones**:
- Registra operaciones de ventas, compras, cobros, pagos y anticipos
- Genera partidas contables automáticas
- Calcula IVA y retenciones
- Maneja operaciones con documentos tributarios

**Cálculos Monetarios**:
- Cálculo de IVA en ventas/compras
- Retenciones fiscales
- Generación automática de asientos contables

**Criticidad**: ⚠️ **CRÍTICA** - Todas las operaciones diarias del negocio

---

#### PartidaService.java
**Ubicación**: `colibri-application/src/main/java/sv/edu/ues/colibri/application/partida/PartidaService.java`
**Funciones**:
- Registro, aprobación, contabilización y reversión de partidas
- Validación de equilibrio contable
- Gestión de cadena de evidencia (documento + evento + folio)
- Control de estados de partida

**Cálculos Monetarios**:
- Validación de equilibrio debe = haber
- Cálculo de diferencias
- Verificación de saldos antes de contabilizar

**Criticidad**: ⚠️ **CRÍTICA** - Core del sistema contable

---

### 2.2 Servicios de Costos

#### InventarioCostoService.java
**Ubicación**: `colibri-application/src/main/java/sv/edu/ues/colibri/application/inventario/InventarioCostoService.java`
**Funciones**:
- Inventario de licencias digitales
- Costeo por órdenes de producción
- Método PEPS (First-In-First-Out)
- Costeo de procesos SaaS

**Cálculos Monetarios**:
- Costo unitario de productos
- Valuación de inventarios
- Distribución de centavos en redondeo

**Criticidad**: ⚠️ **CRÍTICA** - Valuación de inventarios

---

#### CifService.java
**Ubicación**: `colibri-application/src/main/java/sv/edu/ues/colibri/application/costos/CifService.java`
**Funciones**:
- Análisis de Costos Indirectos de Fabricación (CIF)
- Cálculo de CIF aplicado vs real
- Detección de subaplicación/sobreaplicación

**Cálculos Monetarios**:
- Tasa CIF
- CIF aplicado a producción
- Variaciones de CIF

**Criticidad**: ⚠️ **ALTA** - Costeo de manufactura

---

#### CosteoGerencialService.java
**Ubicación**: `colibri-application/src/main/java/sv/edu/ues/colibri/application/costos/CosteoGerencialService.java`
**Funciones**:
- Costeo directo
- Costeo estándar
- Costeo ABC (Activity-Based Costing)
- Análisis de variaciones

**Cálculos Monetarios**:
- Costos directos e indirectos
- Variaciones de precio y eficiencia
- Costos por actividad

**Criticidad**: ⚠️ **ALTA** - Decisiones gerenciales

---

#### CosteoPorProcesosService.java
**Ubicación**: `colibri-application/src/main/java/sv/edu/ues/colibri/application/costos/CosteoPorProcesosService.java`
**Funciones**:
- Costeo por procesos productivos continuos
- Unidades equivalentes
- Costos departamentales

**Cálculos Monetarios**:
- Costo por unidad equivalente
- Distribución de costos entre departamentos

**Criticidad**: ⚠️ **ALTA** - Producción continua

---

#### EstimacionCostosService.java
**Ubicación**: `colibri-application/src/main/java/sv/edu/ues/colibri/application/costos/EstimacionCostosService.java`
**Funciones**:
- Estimación de costos futuros
- Presupuestos de producción

**Cálculos Monetarios**:
- Proyecciones de costos
- Presupuestos

**Criticidad**: ⚠️ **MEDIA** - Planeación

---

### 2.3 Servicios de Nómina

#### PlanillaService.java
**Ubicación**: `colibri-application/src/main/java/sv/edu/ues/colibri/application/nomina/PlanillaService.java`
**Funciones**:
- Cálculo de planilla de sueldos
- Retenciones laborales (ISSS, AFP)
- Aportes patronales
- Generación de asientos contables de nómina

**Cálculos Monetarios**:
- Salarios brutos y netos
- ISSS laboral (3%) y patronal (7.5%)
- AFP laboral (7.25%) y patronal (7.75%)
- INCAF (1% si ≥10 empleados)
- Horas extras, bonificaciones, vales

**Criticidad**: ⚠️ **CRÍTICA** - Obligaciones laborales

---

### 2.4 Servicios de Libros y Reportes

#### LibroContableService.java
**Ubicación**: `colibri-application/src/main/java/sv/edu/ues/colibri/application/libros/LibroContableService.java`
**Funciones**:
- Generación de libros contables oficiales
- Libro Diario
- Libro Mayor
- Balance de Comprobación
- Estados Financieros

**Cálculos Monetarios**:
- Saldos de cuentas
- Sumas de debe y haber
- Balance de resultados

**Criticidad**: ⚠️ **CRÍTICA** - Libros legales obligatorios

---

#### ReporteFinancieroService.java
**Ubicación**: `colibri-application/src/main/java/sv/edu/ues/colibri/application/libros/ReporteFinancieroService.java`
**Funciones**:
- Balance General
- Estado de Resultados
- Flujo de Efectivo
- Estado de Cambios en el Patrimonio

**Cálculos Monetarios**:
- Activos, Pasivos, Patrimonio
- Ingresos, Gastos, Utilidades
- Flujos de efectivo operativos/inversión/financiamiento

**Criticidad**: ⚠️ **CRÍTICA** - Reportes financieros oficiales

---

### 2.5 Servicios de Cierre

#### CierreService.java
**Ubicación**: `colibri-application/src/main/java/sv/edu/ues/colibri/application/cierre/CierreService.java`
**Funciones**:
- Cierre mensual y anual
- Ajustes de cierre
- Cálculo de devengos (ej: ingresos SaaS diferidos)
- Reversión de cierres

**Cálculos Monetarios**:
- Ajustes de depreciación/amortización
- Reconocimiento proporcional de ingresos
- Provisiones y reservas
- Cierre de cuentas de resultados

**Criticidad**: ⚠️ **CRÍTICA** - Cierre contable oficial

---

### 2.6 Servicios de Configuración

#### ParametrizacionService.java
**Ubicación**: `colibri-application/src/main/java/sv/edu/ues/colibri/application/configuracion/ParametrizacionService.java`
**Funciones**:
- Configuración de políticas contables
- Parámetros fiscales (tasas IVA, retenciones)
- Perfiles de contabilización

**Parámetros Monetarios**:
- Tasas de IVA
- Porcentajes de retención
- Tasas de depreciación

**Criticidad**: ⚠️ **ALTA** - Afecta todos los cálculos

---

### 2.7 Otros Servicios

#### CatalogoService.java
**Ubicación**: `colibri-application/src/main/java/sv/edu/ues/colibri/application/catalogo/CatalogoService.java`
**Funciones**:
- Gestión del catálogo de cuentas contables
- Aprobación de versiones del catálogo

**Criticidad**: ⚠️ **ALTA** - Estructura contable

---

#### PeriodoAppService.java
**Ubicación**: `colibri-application/src/main/java/sv/edu/ues/colibri/application/periodo/PeriodoAppService.java`
**Funciones**:
- Gestión de ejercicios y períodos contables
- Apertura y cierre de períodos

**Criticidad**: ⚠️ **ALTA** - Control temporal de operaciones

---

## 3. COLIBRI-PERSISTENCE: Repositorios de Datos Financieros

### 3.1 Repositorios de Partidas y Transacciones

#### PartidaRepository.java
**Ubicación**: `colibri-persistence/src/main/java/sv/edu/ues/colibri/persistence/repo/PartidaRepository.java`
**Funciones**:
- Persistencia de cabeceras de partidas
- Persistencia de detalles de partidas (con importes debe/haber)
- Consultas de partidas por período, cuenta, estado

**Datos Monetarios**:
- Importes debe/haber en `partida_detalle`
- Sumas de partidas

**Criticidad**: ⚠️ **CRÍTICA** - Almacena todos los movimientos contables

---

#### CuentaRepository.java
**Ubicación**: `colibri-persistence/src/main/java/sv/edu/ues/colibri/persistence/repo/CuentaRepository.java`
**Funciones**:
- Persistencia del catálogo de cuentas
- Cálculo de saldos de cuentas
- Consultas jerárquicas del plan contable

**Datos Monetarios**:
- Saldos de cuentas (calculados desde partida_detalle)
- Saldos iniciales

**Criticidad**: ⚠️ **CRÍTICA** - Catálogo contable y saldos

---

#### DocumentoRepository.java
**Ubicación**: `colibri-persistence/src/main/java/sv/edu/ues/colibri/persistence/repo/DocumentoRepository.java`
**Funciones**:
- Persistencia de documentos tributarios
- Almacena base gravada, IVA, exento, total

**Datos Monetarios**:
- `base_gravada`, `iva`, `exento`, `total`
- Flag `iva_incluido`

**Criticidad**: ⚠️ **CRÍTICA** - Soporte tributario

---

### 3.2 Otros Repositorios

#### PeriodoRepository.java
**Ubicación**: `colibri-persistence/src/main/java/sv/edu/ues/colibri/persistence/repo/PeriodoRepository.java`
**Funciones**:
- Persistencia de ejercicios y períodos contables

**Criticidad**: ⚠️ **ALTA** - Control temporal

---

#### EmpresaRepository.java
**Ubicación**: `colibri-persistence/src/main/java/sv/edu/ues/colibri/persistence/repo/EmpresaRepository.java`
**Funciones**:
- Datos de la empresa (NIT, NRC, razón social)

**Criticidad**: ⚠️ **ALTA** - Identificación fiscal

---

#### TerceroRepository.java
**Ubicación**: `colibri-persistence/src/main/java/sv/edu/ues/colibri/persistence/repo/TerceroRepository.java`
**Funciones**:
- Clientes y proveedores

**Criticidad**: ⚠️ **MEDIA** - Relaciones comerciales

---

#### EventoRepository.java
**Ubicación**: `colibri-persistence/src/main/java/sv/edu/ues/colibri/persistence/repo/EventoRepository.java`
**Funciones**:
- Eventos contables para idempotencia

**Criticidad**: ⚠️ **ALTA** - Previene duplicados

---

#### PlantillaPartidaRepository.java
**Ubicación**: `colibri-persistence/src/main/java/sv/edu/ues/colibri/persistence/repo/PlantillaPartidaRepository.java`
**Funciones**:
- Plantillas de partidas recurrentes

**Criticidad**: ⚠️ **MEDIA** - Automatización

---

#### ContadorRepository.java
**Ubicación**: `colibri-persistence/src/main/java/sv/edu/ues/colibri/persistence/repo/ContadorRepository.java`
**Funciones**:
- Usuarios y credenciales

**Criticidad**: ⚠️ **ALTA** - Seguridad y auditoría

---

#### BitacoraRepository.java
**Ubicación**: `colibri-persistence/src/main/java/sv/edu/ues/colibri/persistence/repo/BitacoraRepository.java`
**Funciones**:
- Log de auditoría operativa

**Criticidad**: ⚠️ **ALTA** - Trazabilidad

---

#### CatalogoVersionRepository.java
**Ubicación**: `colibri-persistence/src/main/java/sv/edu/ues/colibri/persistence/repo/CatalogoVersionRepository.java`
**Funciones**:
- Control de versiones del catálogo

**Criticidad**: ⚠️ **MEDIA** - Versionado

---

## 4. COLIBRI-REPORTS: Generadores de Reportes

### 4.1 Servicio Principal de Reportes

#### ReportService.java
**Ubicación**: `colibri-reports/src/main/java/sv/edu/ues/colibri/reports/ReportService.java`
**Tecnologías**:
- **JasperReports** - Generación de PDFs
- **Apache POI (XSSF)** - Generación de Excel

**Funciones**:
- Exportación de libros contables a PDF/Excel
- Generación de estados financieros
- Reportes con membrete institucional
- Formateo de importes monetarios

**Datos Monetarios**:
- Formateo de `BigDecimal` en reportes
- Presentación de saldos, totales, subtotales
- Columnas de debe/haber en libro diario/mayor

**Criticidad**: ⚠️ **ALTA** - Presentación formal de información financiera

---

## 5. RESUMEN POR CRITICIDAD

### ⚠️ CLASES CRÍTICAS (Impacto Directo en Integridad Financiera)

**Domain**:
1. `PartidaDetalle` - Importes debe/haber
2. `Documento` - Datos tributarios
3. `Partida` - Equilibrio contable
4. `IvaCalculator` - Cálculo fiscal
5. `AjustesCalculator` - Ajustes contables
6. `CostosCalculator` - Costos y planilla
7. `ResultadosCalculator` - Estados financieros

**Application**:
1. `OperacionContableService` - Operaciones diarias
2. `PartidaService` - Core contable
3. `InventarioCostoService` - Valuación inventarios
4. `PlanillaService` - Nómina
5. `LibroContableService` - Libros oficiales
6. `ReporteFinancieroService` - Estados financieros
7. `CierreService` - Cierre contable

**Persistence**:
1. `PartidaRepository` - Movimientos contables
2. `CuentaRepository` - Saldos
3. `DocumentoRepository` - Soporte tributario

**Reports**:
1. `ReportService` - Presentación formal

---

### ⚠️ CLASES DE ALTA CRITICIDAD (Afectan Cálculos Indirectamente)

**Application**:
1. `CifService` - CIF manufactura
2. `CosteoGerencialService` - Decisiones gerenciales
3. `CosteoPorProcesosService` - Producción continua
4. `ParametrizacionService` - Configuración que afecta cálculos
5. `CatalogoService` - Estructura contable
6. `PeriodoAppService` - Control temporal

**Persistence**:
1. `ContadorRepository` - Auditoría de usuarios
2. `BitacoraRepository` - Trazabilidad
3. `EventoRepository` - Idempotencia
4. `PeriodoRepository` - Períodos
5. `EmpresaRepository` - Identificación fiscal

---

### ⚠️ CLASES DE MEDIA CRITICIDAD (Soporte y Automatización)

**Application**:
1. `EstimacionCostosService` - Presupuestos

**Persistence**:
1. `TerceroRepository` - Clientes/proveedores
2. `PlantillaPartidaRepository` - Automatización
3. `CatalogoVersionRepository` - Versionado

---

## 6. CONSIDERACIONES PARA AUDITORÍA FORENSE

### Áreas de Mayor Riesgo:
1. **Cálculos de IVA** (`IvaCalculator`) - Error fiscal crítico
2. **Equilibrio contable** (`Partida`, `PartidaDetalle`) - Integridad de partida doble
3. **Redondeo y normalización** - Acumulación de centavos
4. **Planilla y retenciones** (`PlanillaService`, `CostosCalculator`) - Obligaciones laborales
5. **Valuación de inventarios** (`InventarioCostoService`) - Impacto en resultados
6. **Cierre contable** (`CierreService`) - Ajustes de cierre
7. **Estados financieros** (`ReporteFinancieroService`) - Información oficial

### Puntos de Control Recomendados:
- **Validación de rangos** en todos los `BigDecimal`
- **Precisión decimal** consistente (2 decimales vs 8 decimales internos)
- **Logs de auditoría** en operaciones monetarias críticas
- **Pruebas de regresión** en calculadoras
- **Validación de sumas** antes de aprobar partidas
- **Trazabilidad completa** de modificaciones a importes

---

## 7. ESTADÍSTICAS DEL INVENTARIO

### Conteo de Clases:

**colibri-domain**: 25 clases
- Modelos con campos monetarios: 3 (PartidaDetalle, Documento, Partida)
- Calculadoras: 4 (IvaCalculator, AjustesCalculator, CostosCalculator, ResultadosCalculator)

**colibri-application**: 22 servicios
- Servicios con cálculos financieros críticos: 13
- Servicios de soporte: 9

**colibri-persistence**: 11 repositorios
- Repositorios con datos monetarios: 3 (PartidaRepository, CuentaRepository, DocumentoRepository)
- Repositorios de soporte: 8

**colibri-reports**: 1 servicio
- ReportService (generación PDF/Excel)

**Total de clases inventariadas**: 59 clases

---

## 8. PRÓXIMOS PASOS RECOMENDADOS

1. **Análisis detallado de flujos de redondeo** en todas las calculadoras
2. **Revisión de precisión decimal** (RoundingMode, escalas)
3. **Mapeo de dependencias** entre servicios y calculadoras
4. **Identificación de puntos de entrada** de datos monetarios externos
5. **Revisión de validaciones** en límites monetarios
6. **Análisis de casos extremos** (valores muy grandes, muy pequeños, cero, negativos)
7. **Pruebas de acumulación** de errores de redondeo

---

**Fin del Inventario**
