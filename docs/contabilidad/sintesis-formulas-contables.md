# Síntesis de Fórmulas Contables y de Costos

> **Alcance documental.** Este archivo es material académico de referencia, no un inventario de automatismos de Colibrí. El prototipo calcula o usa directamente sólo una parte de estas fórmulas (por ejemplo IVA, partida doble, ajustes, depreciación, amortización, planilla, PEPS, procesos, CIF y estimaciones). Las fórmulas restantes orientan el análisis, la parametrización manual o la validación profesional; no deben presentarse como resultados generados automáticamente por el software.

<!--
Documento de apoyo técnico y académico.
Agrupa fórmulas, ecuaciones y criterios de vigencia para mantener una lectura
uniforme con la documentación general del proyecto.
-->

Compilación integral de las fórmulas, ecuaciones y modelos matemáticos extraídos de los materiales de estudio de la asignatura **Sistemas Contables y de Costos**.

## Nota de vigencia regional

<!-- Esta sección separa la referencia académica de los parámetros normativos que deben verificarse antes de usar el material en operación real. -->

Las fórmulas tributarias y laborales se usan como apoyo didáctico para el caso Colibrí y deben parametrizarse por período. Para El Salvador, el IVA general es 13 %; el aporte patronal al ISSS es 7.5 % sobre la base cotizable, respetando el tope vigente; la cotización previsional patronal es 8.75 %; y la contribución al INCAF es 1 % de la planilla mensual cuando el empleador tiene diez o más trabajadores. Antes de liquidar una planilla real deben verificarse topes, exclusiones, tablas de ISR y vigencia normativa. Fuentes de consulta: [Ministerio de Hacienda](https://www.mh.gob.sv/wp-content/uploads/2021/06/700-UC-IF-2019-21250.pdf), [ISSS](https://www.isss.gob.sv/servicios/actualizacion-de-datos-patronales/), [Ley Integral del Sistema de Pensiones](https://ssf.gob.sv/wp-content/uploads/2023/02/Ley-Integral-del-Sistema-de-Pensiones.pdf) y [Ley de Creación del INCAF](https://www.asamblea.gob.sv/sites/default/files/2024-12/TOMO%201%20ANUARIO%202024.pdf). Consultado: 8 nov. 2026.

---

## 1. Ecuación Contable y Estructura del Balance General

### 1. Ecuación Contable Fundamental (Dualidad Económica)
* **Fórmula:**
  $$\text{Activo} = \text{Pasivo} + \text{Capital}$$
  *(o conceptualmente: $\text{Recursos o Usos} = \text{Fuentes o Participaciones}$)*
* **Caso de uso:** Representa el equilibrio financiero básico donde los recursos totales de la entidad equivalen a las fuentes de financiamiento externas (acreedores) e internas (propietarios o socios).

### 2. Ecuación Contable por Exigibilidad
* **Fórmula:**
  $$\text{Activo} = \text{Pasivos Exigibles} + \text{Pasivos No Exigibles}$$
* **Caso de uso:** Diferencia las obligaciones legales a favor de terceros respecto al patrimonio neto aportado o ganado.

### 3. Balance General en Forma de Reporte (Presentación Vertical)
* **Fórmula:**
  $$\text{Activo} - \text{Pasivo} = \text{Capital}$$
* **Caso de uso:** Estructura vertical del balance para presentar de forma deductiva el patrimonio neto resultante tras restar todas las deudas a los activos.

### 4. Balance General en Forma de Cuenta (Presentación Horizontal)
* **Fórmula:**
  $$\text{Activo} = \text{Pasivo} + \text{Capital}$$
* **Caso de uso:** Esquema visual a dos columnas (activos a la izquierda y pasivo + capital a la derecha) para comprobar visualmente la igualdad numérica de la dualidad económica.

---

## 2. Transacciones Mercantiles y Estado de Resultados (Método Analítico y Perpetuo)

### 5. Ventas Netas
* **Fórmula:**
  $$\text{Ventas Netas} = \text{Ventas Totales} - (\text{Devoluciones sobre Venta} + \text{Rebajas/Descuentos sobre Venta})$$
* **Caso de uso:** Determinación de los ingresos reales netos devengados por ventas al cierre del ejercicio tras deducir las devoluciones y rebajas concedidas a clientes.

### 6. Compras Totales
* **Fórmula:**
  $$\text{Compras Totales} = \text{Compras} + \text{Gastos sobre Compras}$$
* **Caso de uso:** Cuantifica el importe total bruto desembolsado por adquisición de mercadería, incluyendo fletes y seguros de traslado.

### 7. Compras Netas
* **Fórmula:**
  $$\text{Compras Netas} = \text{Compras Totales} - (\text{Devoluciones sobre Compras} + \text{Rebajas/Descuentos sobre Compras})$$
* **Caso de uso:** Refleja el costo efectivo de adquisición de inventario tras restar las mercaderías devueltas a proveedores y bonificaciones obtenidas.

### 8. Mercancías Disponibles para la Venta
* **Fórmula:**
  $$\text{Mercancías Disponibles} = \text{Inventario Inicial} + \text{Compras Netas}$$
* **Caso de uso:** Calcula el volumen total valorado de productos listos para la comercialización durante el período operativo.

### 9. Costo de Ventas (Costo de lo Vendido en Método Analítico)
* **Fórmula:**
  $$\text{Costo de Ventas} = \text{Mercancías Disponibles} - \text{Inventario Final}$$
* **Caso de uso:** Determina el costo del inventario efectivamente vendido al cierre del período mediante la toma física del inventario final.

### 10. Utilidad (o Pérdida) Bruta
* **Fórmula:**
  $$\text{Utilidad Bruta} = \text{Ventas Netas} - \text{Costo de Ventas}$$
* **Caso de uso:** Medición del margen bruto obtenido por comercialización; si el costo de ventas supera a las ventas netas, el resultado representa una pérdida bruta.

### 11. Utilidad de Operación
* **Fórmula:**
  $$\text{Utilidad de Operación} = \text{Utilidad Bruta} - \text{Gastos de Operación}$$
* **Caso de uso:** Evalúa la ganancia operativa restando gastos administrativos, sueldos de ventas, alquileres y servicios generales.

### 12. Utilidad Neta del Ejercicio
* **Fórmula:**
  $$\text{Utilidad Neta} = \text{Utilidad antes de Impuestos} - \text{Impuestos Directos}$$
* **Caso de uso:** Calcula el resultado definitivo final atribuible a los propietarios tras cubrir costos operativos, financieros y tributarios.

---

## 3. Impuesto al Valor Agregado (IVA - Marco Tributario de El Salvador)

### 13. Cálculo de IVA sobre Precio Neto (Precio Más IVA)
* **Fórmula:**
  $$\text{IVA} = \text{Valor Neto} \times 13\% \quad (0.13)$$
* **Caso de uso:** Aplicado al registrar compras o ventas cuando la tasa impositiva legal del 13% debe sumarse a la base imponible acordada.

### 14. Importe Total Facturado (Más IVA)
* **Fórmula:**
  $$\text{Total} = \text{Valor Neto} + \text{IVA} = \text{Valor Neto} \times 1.13$$
* **Caso de uso:** Emisión de comprobantes fiscales y determinación de cobros o pagos totales brutos.

### 15. Desglose de Base Imponible (Precio con IVA Incluido)
* **Fórmula:**
  $$\text{Base Imponible} = \frac{\text{Total con IVA}}{1.13}$$
* **Caso de uso:** Ventas o compras documentadas con factura a consumidor final donde el impuesto ya está incorporado en el precio de venta.

### 16. IVA Desglosado a partir del Total Incluido
* **Fórmula:**
  $$\text{IVA} = \text{Total con IVA} - \text{Base Imponible}$$
* **Caso de uso:** Separar el débito o crédito fiscal del valor del ingreso o inventario cuando el comprobante no discrimina el impuesto.

### 17. IVA en Importaciones
* **Fórmula:**
  $$\text{IVA Importación} = (\text{Valor en Aduanas / Base Imponible} + \text{DAI}) \times 13\%$$
* **Caso de uso:** Liquidación aduanera sobre bienes importados gravados con Derechos Arancelarios a la Importación (DAI).

### 18. Liquidación Mensual de IVA
* **Fórmula:**
  $$\text{IVA Débito Fiscal} - \text{IVA Crédito Fiscal} = \text{IVA por Pagar} \quad (\text{si Débito} > \text{Crédito})$$
* **Caso de uso:** Cierre tributario mensual; si el Crédito Fiscal excede al Débito, la diferencia constituye un remanente a favor que se traslada a los siguientes períodos.

---

## 4. Asientos de Ajuste al Cierre de Período

### 19. Depreciación Anual por Método de Línea Recta
* **Fórmula:**
  $$\text{Cargo Anual de Depreciación} = \frac{P - L}{N} = \frac{\text{Valor Inicial} - \text{Valor Final (Desecho)}}{\text{Vida Útil}}$$
* **Caso de uso:** Distribución sistemática del costo de un activo fijo tangible (maquinaria, edificios, equipo de transporte) a lo largo de su vida útil estimada.

### 20. Depreciación Periódica Fraccionada (Meses o Bimestres)
* **Fórmula:**
  $$\text{Depreciación Periódica} = \frac{\text{Depreciación Anual}}{\text{Número de Períodos del Año}}$$
* **Caso de uso:** Ajuste de gasto contable por depreciación cuando los cierres contables son mensuales (dividir entre 12) o bimestrales (dividir entre 6).

### 21. Factor de Agotamiento Unitario de Recursos Naturales
* **Fórmula:**
  $$\text{Tasa de Agotamiento} = \frac{\text{Monto Original del Activo}}{\text{Cantidad Total Estimada de Recursos}}$$
* **Caso de uso:** Aplicado a activos de recursos no renovables (minas, pozos petroleros, canteras) para prorratear el costo de extracción unitario.

### 22. Cargo por Agotamiento del Período
* **Fórmula:**
  $$\text{Cargo por Agotamiento} = \text{Recursos Extraídos en el Período} \times \text{Tasa de Agotamiento}$$
* **Caso de uso:** Registrar el costo por desmejora o declinación patrimonial del recurso natural conforme avanza su explotación.

### 23. Amortización de Intangibles / Gastos Diferidos
* **Fórmula:**
  $$\text{Amortización} = \frac{\text{Valor del Activo}}{\text{Tiempo de Duración Estimado}} = \frac{P}{N}$$
* **Caso de uso:** Distribución proporcional del gasto o inversión en remodelaciones, licencias o activos intangibles con vida útil finita.

### 24. Estimación de Cuentas Incobrables
* **Fórmula:**
  $$\text{Gasto por Cuentas Incobrables} = \text{Saldo de Cuentas por Cobrar (o Ventas al Crédito)} \times \% \text{ Estimado}$$
* **Caso de uso:** Reconocer como gasto del período la pérdida potencial por ventas a crédito que difícilmente podrán ser recuperadas.

---

## 5. Fundamentos de Costos y Relaciones entre Elementos Productivos

### 25. Productividad
* **Fórmula:**
  $$\text{Productividad} = \frac{\text{Unidades de Producción}}{\text{Unidades de Insumos}}$$
* **Caso de uso:** Medición del aprovechamiento o eficiencia de los recursos invertidos en relación con los artículos generados.

### 26. Costo de Producción (Costo del Producto)
* **Fórmula:**
  $$\text{Costo de Producción} = \text{MPD} + \text{MOD} + \text{CIF}$$
* **Caso de uso:** Agrupa los tres componentes primarios de manufactura: Materiales Primos Directos (MPD), Mano de Obra Directa (MOD) y Costos Indirectos de Fabricación (CIF).

### 27. Costo Primo
* **Fórmula:**
  $$\text{Costo Primo} = \text{MPD} + \text{MOD}$$
* **Caso de uso:** Cuantifica los costos que son directamente atribuibles y rastreables a la fabricación física de las unidades.

### 28. Costo de Conversión
* **Fórmula:**
  $$\text{Costo de Conversión} = \text{MOD} + \text{CIF}$$
* **Caso de uso:** Agrupa los recursos requeridos para transformar las materias primas en productos terminados.

---

## 6. Asignación de Costos Indirectos de Fabricación (CIF)

### 29. Tasa Predeterminada de CIF
* **Fórmula:**
  $$\text{Tasa Predeterminada de CIF} = \frac{\text{Total de CIF Estimados}}{\text{Base de Aplicación Estimada}}$$
* **Caso de uso:** Estimación previa de costos indirectos para distribuirlos equitativamente a la producción según bases de actividad (horas hombre, horas máquina o costo de mano de obra).

### 30. CIF Aplicados a la Producción / Orden
* **Fórmula:**
  $$\text{CIF Aplicados} = \text{Base Real Consumida} \times \text{Tasa Predeterminada}$$
  *(o en porcentaje directo: $\% \text{ CIF} \times \text{Costo MOD}$, ej. $150\%$ de la MOD)*
* **Caso de uso:** Asignar la cuota proporcional de costos indirectos a cada lote o pedido de trabajo conforme se fabrica.

### 31. Variación de Costos Indirectos (Sobre / Subaplicación)
* **Fórmula:**
  $$\text{Variación CIF} = \text{CIF Reales Incurridos} - \text{CIF Aplicados}$$
* **Caso de uso:** Comparar los costos indirectos reales al final del ciclo productivo contra las estimaciones aplicadas para detectar discrepancias y ajustar resultados.

---

## 7. Control, Salarios y Factor de Recargo de Mano de Obra (MOD)

### 32. Costo Real Integral de la Mano de Obra
* **Fórmula:**
  $$\text{Costo Real MOD} = \text{Salario Nominal} + \text{Prestaciones de Ley} + \text{Aportes Patronales} + \text{Prestaciones Contractuales / Preferenciales}$$
* **Caso de uso:** Determinar el costo laboral verdadero para la empresa, integrando sueldos pactados, séptimos, aguinaldos, vacaciones e indemnizaciones.

### 33. Salario Real Devengado Diario (Sector Privado)
* **Fórmula:**
  $$\text{Salario Real Diario} = \frac{\text{Salario Semanal}}{5.5 \text{ Días Laborados}}$$
* **Caso de uso:** Convertir la remuneración semanal ordinaria a costo efectivo diario en empresas privadas con jornada habitual de 5.5 días.

### 34. Salario Real Devengado Diario (Sector Público)
* **Fórmula:**
  $$\text{Salario Real Diario} = \frac{\text{Salario Semanal}}{5 \text{ Días Laborados}}$$
* **Caso de uso:** Determinación del costo por día laborado en regímenes de jornada completa de 5 días hábiles a la semana.

### 35. Factor de Recargo Laboral
* **Fórmula:**
  $$\text{Factor de Recargo} = \frac{\text{Salario Calculado Integral}}{\text{Salario Nominal}}$$
* **Caso de uso:** Establece el coeficiente porcentual adicional que la empresa debe provisionar por encima de la remuneración contractual directa.

### 36. Factor de Recargo por Eficiencia
* **Fórmula:**
  $$\text{Factor de Recargo por Eficiencia} = \frac{\text{Salario Calculado Integral} \times \% \text{ de Eficiencia}}{\text{Salario Nominal}}$$
* **Caso de uso:** Ajustar el factor laboral de acuerdo al grado real de aprovechamiento productivo de la mano de obra del trabajador.

### 37. Aporte Patronal de Salud (ISSS)
* **Fórmula:**
$$\text{Aporte Patronal ISSS} = \min(\text{Salario Nominal}, \text{Tope Cotizable Vigente}) \times 7.5\%$$
* **Caso de uso:** Obligación patronal para cobertura del seguro médico de los trabajadores. El tope debe conservarse con su vigencia; en el caso integrado del informe se aplica $1,000.00 por trabajador.

### 38. Aporte Patronal Previsional
* **Fórmula:**
$$\text{Aporte Patronal Previsional} = \text{Base Cotizable} \times 8.75\%$$
* **Caso de uso:** Contribución patronal previsional calculada sobre la base cotizable vigente. La cédula separa esta carga de la cotización laboral y conserva la tasa, base y vigencia aplicadas.

### 39. Aporte para Capacitación INCAF
* **Fórmula:**
  $$\text{Aporte INCAF} = \text{Planilla Mensual Total} \times 1\%$$
* **Caso de uso:** Cuota patronal exigible cuando la entidad cuenta con 10 o más trabajadores. Para costear una orden o trabajador puede asignarse proporcionalmente el importe ya calculado sobre la planilla; la obligación se calcula una sola vez sobre el total mensual.

### 40. Remuneración de Vacaciones Laborales
* **Fórmula:**
  $$\text{Pago de Vacaciones} = (15 \text{ Días de Salario Base}) + (30\% \times \text{Salario Base de esos 15 Días})$$
* **Caso de uso:** Cuantificar el monto por descanso anual remunerado con el recargo legal obligatorio del 30%.

---

## 8. Sistema de Costos por Órdenes de Fabricación

### 41. Costo Unitario por Orden de Fabricación
* **Fórmula:**
  $$\text{Costo Unitario} = \frac{\text{Costo Total Acumulado de la Orden}}{\text{Unidades Producidas en la Orden}}$$
* **Caso de uso:** Liquidar la orden y asignar el valor unitario a cada bien terminado cuando los lotes se elaboran bajo requerimientos y especificaciones singulares del cliente.

### 42. Precio de Venta Sugerido según Margen sobre Costo
* **Fórmula:**
  $$\text{Precio de Venta} = \text{Costo Total de la Orden} \times \% \text{ Factor de Precio} \quad (\text{ej. } 160\%)$$
* **Caso de uso:** Fijación de precios en órdenes bajo política contractual de margen sobre costo absorbido.

### 43. Utilidad Real Obtenida en una Orden
* **Fórmula:**
  $$\text{Utilidad Real} = \text{Precio de Venta Facturado} - \text{Costo Total Real Incurrido}$$
* **Caso de uso:** Conocer la ganancia definitiva de una orden liquidada una vez ajustados los costos indirectos reales frente a los presupuestados.

---

## 9. Sistema de Costos por Procesos y Cédulas de Unidades Equivalentes

### 44. Cédula de Balance de Unidades Físicas
* **Fórmula:**
  $$\text{Unidades Iniciadas} + \text{Inventario Inicial en Proceso} - \text{Inventario Final en Proceso} = \text{Unidades Transferidas}$$
* **Caso de uso:** Garantiza la conciliación física de flujos de producción: todo lo que entra al departamento o centro productivo debe coincidir con lo que sale transferido más lo que queda en curso.

### 45. Unidad Equivalente Simple
* **Fórmula:**
  $$\text{Unidades Equivalentes} = \text{Unidades Físicas en Proceso} \times \text{Grado de Avance (\% Recibido)}$$
* **Caso de uso:** Expresar productos semiterminados como el equivalente a unidades completamente acabadas según los recursos consumidos.

### 46. Método de Unidades Terminadas (Cédula de Unidades Equivalentes)
* **Fórmula:**
  $$\text{Unidades Equivalentes Producidas} = \text{Unidades Terminadas} + (\text{Inv. Final PP} \times \text{Grado Avance Final}) - (\text{Inv. Inicial PP} \times \text{Grado Avance Inicial})$$
* **Caso de uso:** Cálculo de la producción equivalente neta realizada durante el período cuando se deduce el trabajo ya incorporado del inventario previo.

### 47. Método de Unidades Iniciadas y Terminadas
* **Fórmula:**
  $$\text{Unidades Equivalentes Producidas} = \text{Unidades Iniciadas y Terminadas} + [\text{Inv. Inicial PP} \times (1 - \text{Grado Avance Inicial})] + (\text{Inv. Final PP} \times \text{Grado Avance Final})$$
* **Caso de uso:** Cuantifica las unidades equivalentes sumando el remanente por terminar del inventario inicial, las unidades procesadas íntegramente en el ciclo y el avance del inventario final.

### 48. Costo Unitario por Proceso
* **Fórmula:**
  $$\text{Costo Unitario Departamental} = \frac{\text{Costos Totales Acumulados del Departamento}}{\text{Unidades Equivalentes Producidas}}$$
* **Caso de uso:** Base de costeo unitario calculada por separado para materia prima y costos de conversión debido a sus distintos avances.

### 49. Asignación de Costo de Inventario Final bajo Método PEPS
* **Fórmula:**
  $$\text{Costo Inv. Final (PEPS)} = \left(\frac{\text{Costos de Unidades Equivalentes Producidas}}{\text{Unidades Equivalentes Producidas}}\right) \times \text{Unidades de Inventario Final}$$
* **Caso de uso:** Valuación del inventario final en proceso tomando como base los costos más recientes generados en el período actual.

### 50. Asignación de Costo de Unidades Transferidas bajo Método UEPS
* **Fórmula:**
  $$\text{Costo Unidades Transferidas (UEPS)} = \left(\frac{\text{Costos de Unidades Equivalentes Producidas}}{\text{Unidades Equivalentes Producidas}}\right) \times \text{Unidades Transferidas}$$
* **Caso de uso:** Valuación de la producción saliente al departamento subsiguiente utilizando los costos de las últimas unidades generadas.

### 51. Asignación de Costo bajo Método Costo Promedio (CP)
* **Fórmula:**
  $$\text{Costo Inv. Final (CP)} = \left(\frac{\text{Costo Total de Unidades Equivalentes}}{\text{Total de Unidades}}\right) \times \text{Unidades de Inventario Final}$$
* **Caso de uso:** Valuación ponderada homogénea combinando costos anteriores y del presente período sin distinción de antigüedad.

---

## 10. Costos Relevantes para la Toma de Decisiones

### 52. Enfoque Incremental / Costo Diferencial
* **Fórmula:**
  $$\text{Costo Diferencial} = \text{Costos Pertinentes (Opción A)} - \text{Costos Pertinentes (Opción B)}$$
* **Caso de uso:** Comparación económica para elegir entre alternativas (por ejemplo, comprar equipo nuevo versus reparar el usado, o fabricar versus tercerizar partes) analizando únicamente los costos futuros modificables.

### 53. Evaluación de Pérdida en Venta Inmediata vs. Uso (Tratamiento de Costos Hundidos)
* **Fórmula:**
  $$\text{Pérdida Neta} = \text{Ingreso / Ahorro Generado} - \text{Depreciación Total o Inversión Histórica}$$
* **Caso de uso:** Decidir si mantener o enajenar un activo existente reconociendo que el valor de compra original es irrecuperable e invariable ante decisiones futuras.
