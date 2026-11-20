# Rúbrica y Criterios de Evaluación del Proyecto

<!--
Documento de evaluación académica.
Describe los criterios del curso y su relación con el prototipo, sin confundirse
con la evidencia ejecutable del sistema.
-->

Los criterios de este documento expresan el alcance académico esperado y no certifican por sí mismos que cada función esté implementada. Para el estado ejecutable del prototipo, consultar la sección **Estado actual del prototipo** y el README.

## 1. Documento (12 %)

| Criterio / Sección | Valor | Puntaje |
| :--- | :---: | :---: |
| Formato y presentación del documento técnico | 8 | |
| Introducción, objetivos, justificación, alcances y limitaciones | 10 | |
| Caracterización de la empresa | 10 | |
| Marco referencial: teórico y legal | 8 | |
| Análisis del sistema contable: problema, usuarios, procesos y requerimientos | 15 | |
| Diseño del sistema contable: estructura, catálogo y manual de cuentas | 18 | |
| Contabilidad de costos: selección, recursos, método, costo total y unitario | 16 | |
| Modelado del sistema / prototipo funcional | 5 | |
| Conclusiones, recomendaciones, referencias IEEE, glosario y anexos | 5 | |
| Avances y consistencia integral del proyecto | 5 | |
| **TOTAL** | **100** | |

---

## 2. Prototipo funcional (13 %)

| Criterio / Módulo | Valor | Puntaje |
| :--- | :---: | :---: |
| Catálogo de cuentas y parametrización | 15 | |
| Registro y procesamiento de operaciones contables | 20 | |
| Tratamiento de IVA | 15 | |
| Procesos de ajustes contables | 15 | |
| Inventarios, si aplica, o tratamiento especial equivalente según el giro | 10 | |
| Generación de estados financieros | 20 | |
| Usabilidad, controles y consistencia de la información | 5 | |
| **TOTAL** | **100** | |

---

## 3. Exposición y defensa (5 %)

| Criterio | I1 | I2 | I3 | Valor |
| :--- | :---: | :---: | :---: | :---: |
| Puntualidad y organización | | | | 20 |
| Dominio técnico y contable | | | | 20 |
| Demostración del prototipo | | | | 20 |
| Participación y respuesta a preguntas | | | | 20 |
| Asistencia y participación de todos los integrantes | | | | 20 |
| **TOTAL** | | | | **100** |

---

## Estructura Consolidada para Prompts de Generación

### A. Especificaciones del Documento Técnico
- **Formato general:** Presentación y formato de documento técnico.
- **Sección 1:** Introducción, objetivos, justificación, alcances y limitaciones.
- **Sección 2:** Caracterización de la empresa.
- **Sección 3:** Marco referencial (marco teórico y marco legal).
- **Sección 4:** Análisis del sistema contable (definición del problema, tipos de usuarios, procesos de negocio y requerimientos).
- **Sección 5:** Diseño del sistema contable (estructura, catálogo de cuentas y manual de cuentas).
- **Sección 6:** Contabilidad de costos (selección del sistema, recursos, método, costo total y costo unitario).
- **Sección 7:** Modelado del sistema y prototipo funcional.
- **Sección 8:** Conclusiones, recomendaciones, referencias en norma IEEE, glosario y anexos.
- **Criterio de Calidad:** Consistencia integral y seguimiento de avances del proyecto.

### B. Especificaciones del Prototipo Funcional
- **Módulo 1:** Catálogo de cuentas y parametrización del sistema.
- **Módulo 2:** Registro y procesamiento de operaciones contables (asientos contables, libro diario, mayor, etc.).
- **Módulo 3:** Tratamiento y cálculo de IVA (débito y crédito fiscal).
- **Módulo 4:** Procesos de cierres y ajustes contables.
- **Módulo 5:** Control de inventarios o tratamiento especial equivalente según el giro de la empresa.
- **Módulo 6:** Generación automatizada de estados financieros (Estado de Resultados, Balance General, etc.).
- **Criterio de Calidad del Software:** Usabilidad, controles de validación y consistencia de la información.

## Estado actual del prototipo

<!-- Esta sección resume el estado observado del sistema para separar la rúbrica académica del inventario real de funcionalidades. -->

Estado de referencia: 19 de noviembre de 2026. Esta síntesis separa los criterios del curso de las capacidades observables en la versión presentada. Los límites que siguen deben exponerse junto con cualquier demostración.

| Área | Estado implementado y límites |
|---|---|
| Catálogo, períodos, parámetros y seguridad | Catálogo jerárquico, períodos, perfiles contables, centros de costo, roles y bitácora de accesos. Las dimensiones por cuenta, la auditoría completa de cambios y el uso efectivo de todos los parámetros no están implementados de forma integral. |
| Registro contable e IVA | Partidas de doble partida, documentos internos, IVA neto/incluido y liquidación mensual parametrizada. Importación de asientos CSV a borrador. Sin emisión ni transmisión oficial de DTE. |
| Ajustes y cierres | Depreciación lineal, amortización de intangibles, provisión de incobrables, deterioro/reversión de inventario, liquidación de IVA y cierre/bloqueo de período. No hay cierre fiscal anual completo. |
| Inventario y costos | Licencias descargables con salida PEPS, devolución, revocación y kárdex; promedio y UEPS son comparativos sin asientos. Hay datos y cálculos para órdenes a medida (con ciclo de estados, edición y rentabilidad, pero sin bloqueo de costeo por entrega), SaaS por usuarios-mes/plan y servicios por plan facturado; no hay consolidación automática de costo por versión. |
| Libros y estados | Diario, Mayor, Balanza, Cuenta T, Hoja de trabajo, Situación financiera, Resultados, Cambios en el patrimonio y Flujos de efectivo seleccionables; libros de IVA con separación excluyente CCF/consumidor final; auxiliares con saldo acumulado por dimensión pero sin filtro por dimensión específica. |
| Exportación y respaldo | PDF, XLSX y ZIP de un rango. También hay exportación por combinaciones de varios períodos a una carpeta, no a un ZIP multiperíodo. El respaldo es manual con `pg_dump`; no hay programación, cifrado ni restauración desde la interfaz. |

Los porcentajes y temas de la rúbrica siguen siendo criterios de evaluación del proyecto. No constituyen un inventario de funciones ya entregadas ni una conclusión de cumplimiento normativo.

## Notas y decisiones tomadas al momento
- Se deben agregar ejemplos de cada uno de los apartados y políticas del negocio. Por ejemplo, del proceso de estimación de costos. Según te parezca elige un método de costeo para cada línea de venta, en la carte comercial definitiva: Software a la medida, Licencias de software descargable, Acceso al SaaS en la Nube. Todos los demás servicio, considéralos agregados según el plan que se facture.
- Se deben aplicar todos los temas siguientes: La contabilidad y empresa, Introducción al concepto de partida doble, Principios contables Generales aceptados, Diseño de un sistema contable, Costos para la toma de decisiones, La cuenta T, Estados Financieros y sus elementos, Métodos de registros mercantiles, Asientos de ajuste y hoja de trabajo, Generalidades de la contabilidad de costos, Elementos del costo de un producto, Clasificación de las empresas según actividad económica, Representación de cómo se acumulan los costos en cada tipo de empresa, Contabilización y control de los materiales, Manejo de inventarios (UEPS, PEPS y Costo Promedio), Contabilización y control de la mano de obra, Políticas de trabajo y manejo de recurso humano, Contabilización y control de los costos indirectos de fabricación, CIF específicos de los sistemas informáticos, Métodos de estimación de costos (Juicio de expertos, por analogía, por puntos de función, COSMIC, COCOMO, por desgaste de trabajo, por líneas de código, tres puntos, SLIM/Putnam), Cálculos del precio unitario, Planeación de distribución y venta de software, Sistemas de costos por órdenes de trabajo, Sistemas de costos por proceso, Sistemas de costos Estándar y Directos, Basados en actividades.
- FORMATOS DE SALIDA PARA REPORTES Y LIBROS CONTABLES:
	1. LIBRO DIARIO. Formato columnar para el registro cronológico de transacciones.
  	- Campos / Columnas: Fecha, N° de Partida, Código/Ref, Cuenta y Concepto, Debe, Haber.
  	- Estructura: Agrupa los movimientos por números de partida (PDA) e incluye filas finales de resumen con el total de los débitos y créditos.
	2. LIBRO MAYOR. Formato de concentración de saldos por cuenta.
  	- Campos / Columnas: Código, Cuenta, Debe acumulado, Haber acumulado, Saldo deudor, Saldo acreedor.
  	- Representación: Presentación esquemática a través de esquemas de Mayor (Cuentas "T") para el desglose de cargos, abonos y totales por cuenta.
	3. BALANCE DE COMPROBACIÓN. Formato de verificación de saldos.
  	- Campos / Columnas: Código, Cuenta, Debe, Haber.
  	- Estructura: Lista secuencial de cuentas con suma total al final para verificar la igualdad entre débitos y créditos.
	4. HOJA DE TRABAJO. Matriz multicolumnar de integración contable que conecta los saldos iniciales con los estados finales.
  	- Secciones / Columnas continuas: Catálogo de cuentas, Balance de Comprobación, Ajustes, Balance de Comprobación Ajustado, Estado de Resultados, Estado de Capital, Balance General.
	5. BALANCE GENERAL Estado financiero que muestra la situación financiera a una fecha determinada 6.
  	- Encabezado: Nombre de la Empresa, Identificación del reporte ("Balance General") y Fecha de corte.
  	- Estructura de Cuentas (según A = P + C o A - P = C):
  		* Activos: Divididos en Activos corrientes (Disponibles, Realizables), Activos no corrientes (Fijos tangibles, Fijos intangibles) y Otros activos, finalizando en el Total Activos.
  		* Participaciones (Pasivo + Patrimonio): Divididas en Pasivos corrientes, Pasivos no corrientes, Diferidos y Patrimonio (Capital social, Reservas), finalizando en el Total Participaciones.
  	6. ESTADO DE RESULTADOS (PÉRDIDAS Y GANANCIAS). Reporte de desempeño financiero acumulado para un periodo 7.
  	- Encabezado: Datos de la empresa, nombre del estado financiero y período reportado.
	- Estructura de Columnas: Concepto/Cuenta, Detalles, Parciales, Subtotales, Totales.
  	- Desglose de Secciones:
		1. Ventas Netas: Ventas Totales menos Devoluciones y Descuentos sobre ventas.
  		2. Costo de Ventas: Inventario inicial más Compras Netas (Compras + Gastos de compra - Devoluciones/Descuentos s/compra) menos Inventario final.
  		3. Utilidad Bruta: Ventas Netas menos Costo de Ventas.
  		4. Gastos de Operación: Sumatoria de Gastos de Venta y Gastos de Administración para obtener la Utilidad de Operación.
  		5. Resultado Financiero Netos u Otros Ingresos/Gastos: Productos/Gastos financieros y otros rubros para determinar la Utilidad Neta antes de Impuestos.
  		6. Resultado Final: Deducción de Impuestos para calcular la Utilidad Neta.
- La presentación de documentos extensos como el manual del catálogo de cuentas o las políticas de la empresa se deben agregar como anexos bien identificados y con formato legible. El propósito de estos anexos es que aclaren el perfil de la empresa no que sea solo texto extra, buscamos calidad no cantidad.
