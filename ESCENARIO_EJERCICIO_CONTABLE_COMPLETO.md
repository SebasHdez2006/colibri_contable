# Ejercicio contable integral de Colibrí

## Propósito y carga

Este ejercicio representa a **Soluciones Empresariales Colibrí, S.A. de C.V.** al 8 de noviembre de 2026. Todos los NIT, clientes, DTE, comprobantes y rutas de archivos son ficticios; sirven para recorrer el sistema completo sin mezclar datos de una empresa real.

Para reemplazar los datos operativos por este escenario ejecute:

```powershell
.\scripts\run-colibri-complete.ps1 -Reset
```

`-Reset` conserva las cuentas `contador` y `soporte`, pero borra la operación, los documentos y los catálogos de la base. Para cargar y verificar sin abrir JavaFX use `-SoloDatos`. El cargador compila, migra, incorpora el catálogo de referencia, el Anexo L y la ampliación integral; por último ejecuta las verificaciones SQL incluidas para el escenario. Esa carga es destructiva para los datos operativos y no sustituye la validación contable o fiscal de una empresa real.

## Empresa y momento contable

El ejercicio conserva el historial del Anexo L hasta octubre. Enero a septiembre están cerrados, octubre está en revisión y noviembre está abierto para operar. Esto permite revisar períodos ya bloqueados, consultar ajustes en revisión y capturar operaciones actuales sin tener que cambiar fechas.

La empresa vende software de tres maneras:

- Licencias descargables: Colibrí Ventas Pro, Inventarios y Gestión Team.
- Accesos SaaS: Inventarios, Ventas Plus y Gestión Business por usuario-mes.
- Servicios: proyectos a la medida, implementación, capacitación, consultoría, mantenimiento y bolsas de horas.

## Datos que quedan disponibles

| Área | Datos del escenario | Qué se puede demostrar |
|---|---|---|
| Catálogo y parámetros | Todas las subcuentas de movimiento reciben al menos un apunte; se mantienen cuentas de agrupación sin movimientos por diseño contable. | Catálogo, mayor, balanza, cuentas T y mapeo contable. |
| Terceros y documentos | Clientes Alfa, Beta y Gamma; proveedores QA y de licencias. Hay CCF, factura, recibo, comprobante, nota de crédito, nota de débito y otro documento. | Registro documental, IVA, anexos simulados, documentos pendientes y anulados. |
| Proyectos | OS-2026-014 existente, OS-2026-015 en proceso, OS-2026-016 entregada y OS-2026-017 abierta. | Consultas y cálculos de costos a la medida, presupuestos vs. reales, horas, servicios y CIF. El prototipo no implementa el ciclo completo de creación, entrega y liquidación de una orden. |
| Descargables | Lotes LI-2026-03 a LI-2026-06, con licencias entregadas, reservadas, disponibles y devueltas. | Inventario digital, PEPS, costo de ventas y auxiliar de inventario. |
| SaaS | Planes de Inventarios, Ventas Plus y Gestión Business; 45, 80 y 30 usuario-mes en noviembre. | Accesos, ventas SaaS, costo por plan y consumo de recursos. |
| Costos y cierre | Dos procesos de costo, presupuestos CIF de Desarrollo y Plataforma, aplicación y cierre CIF; amortización, estimación de cartera y deterioro. | Paneles de procesos/CIF, cédulas y ajustes de cierre. |
| Libro diario | Partidas contabilizadas, una en borrador, una aprobada, una anulada y una revertida con su asiento inverso. | Flujo de revisión, estados y reversión. No representa una auditoría completa de antes/después para cada edición. |
| Plantillas y soporte | Plantillas de nube, renta y honorarios; entorno técnico y tres registros de monitoreo de demostración. | Borradores recurrentes y pantallas del rol de soporte. El panel de monitoreo no ingiere automáticamente el archivo de log de la aplicación. |

La partida `AJ-2026-000099` se llama **Cobertura técnica de subcuentas aún sin movimiento**. No representa una venta ni una política financiera: completa de forma balanceada las subcuentas que las operaciones realistas no alcanzan a tocar. Así el ejercicio prueba el 100 % del catálogo que admite movimientos. Las cuentas de cabecera no aparecen en el diario porque el modelo impide correctamente contabilizar en ellas.

## Recorrido recomendado

1. Inicie sesión como contador y revise **Inicio** con el período de noviembre.
2. Abra **Documentos** para ver documentos internos con tipos fiscales simulados, recibos y notas pendientes. No son DTE oficiales ni se transmiten a una autoridad fiscal.
3. En **Costos > A la medida**, compare los datos de OS-2026-015 con OS-2026-016; después consulte **Procesos y CIF**. Las órdenes sirven como datos de consulta y cálculo, no como un flujo operativo integral.
4. En **Inventario > Digitales** y **Costos > Licencias descargables**, revise lotes, entregas y devoluciones.
5. En **Inventario > Servicios / Accesos** y **Costos > Suscripción SaaS**, contraste usuarios-mes con los consumos de cada plan.
6. En **Registros**, consulte los estados de partida, genere un borrador desde Plantillas y revise las cédulas en Ajustes.
7. Exporte Diario, Mayor, Balanza, IVA, auxiliares y estados financieros desde **Reportes**.
8. Inicie sesión como soporte para revisar Entorno, Monitoreo, Accesos y el respaldo manual. El respaldo requiere `pg_dump` de PostgreSQL 18 y Monitoreo muestra los registros de demostración almacenados en la base.

## Crecimiento necesario desde 15 empleados

La empresa actual tiene 15 empleados. El escenario requiere capacidad simultánea para cuatro proyectos (uno entregado, uno en ejecución, uno por iniciar y el histórico), tres líneas SaaS, liberaciones de software descargable y soporte recurrente. Una estructura mínima creíble es de **31 empleados**: crecer en **16 personas**, un **106.7 %** sobre la plantilla actual.

| Función | Actual estimado | Meta | Aumento | Motivo operativo |
|---|---:|---:|---:|---|
| Dirección, administración y finanzas | 3 | 4 | 1 | Contratos, cobros, IVA, cierre y control de proveedores. |
| Desarrollo e ingeniería | 6 | 12 | 6 | Dos equipos de proyecto, producto descargable y mantenimiento de SaaS. |
| QA, DevOps y plataforma | 1 | 4 | 3 | Calidad, despliegues, seguridad, nube y continuidad de tres servicios SaaS. |
| Soporte, implementación y éxito del cliente | 3 | 6 | 3 | Onboarding, bolsas de horas, capacitación y atención de accesos. |
| Comercial y producto | 2 | 5 | 3 | Venta consultiva, renovaciones, canales de licencias y gestión de producto. |
| **Total** | **15** | **31** | **16** | **Capacidad para el escenario integral.** |

La meta de 31 es una capacidad mínima, no una proyección de ventas. Conviene hacerla por etapas: primero 4 desarrolladores, 2 de soporte/éxito y 1 DevOps/QA; después completar QA, comercial y administración cuando los contratos SaaS y los proyectos en cartera sostengan esa carga. El escenario supone que los 80 accesos de Ventas, 45 de Inventarios y 30 de Gestión ya tienen operación recurrente, por lo que no es recomendable sustituir soporte y plataforma con horas ocasionales.

## Límites del ejercicio

No se deben interpretar sus cifras como estados financieros de la empresa real ni enviar sus documentos simulados como DTE. Es un conjunto de datos de prueba relacionado para que las pantallas y reportes tengan información consistente; no acredita una auditoría, una transmisión fiscal ni una restauración de respaldo.
