-- Catálogo y planes de servicio alineados con el informe técnico actualizado.
-- La tabla XX del informe añade las subcuentas 4107xx (devoluciones y descuentos
-- sobre ventas) y 7104xx (otros ingresos y otros gastos) que faltaban en V2.
-- Los precios de lista vigentes son los del Anexo N y del ejercicio L.K.

INSERT INTO cuenta (id, codigo, nombre, codigo_padre, nivel, naturaleza, rubro, estado, vigencia_desde, admite_movimiento) VALUES
('a0000000-0000-4000-8000-000000004107', '4107', 'Devoluciones y descuentos sobre ventas', '41', 4, 'DEUDORA', 'INGRESOS', 'ACTIVA', DATE '2024-01-01', FALSE),
('a0000000-0000-4000-8000-000000410701', '410701', 'Devoluciones sobre ventas', '4107', 6, 'DEUDORA', 'INGRESOS', 'ACTIVA', DATE '2024-01-01', TRUE),
('a0000000-0000-4000-8000-000000410702', '410702', 'Descuentos sobre ventas', '4107', 6, 'DEUDORA', 'INGRESOS', 'ACTIVA', DATE '2024-01-01', TRUE),
('a0000000-0000-4000-8000-000000007104', '7104', 'Otros ingresos y otros gastos', '71', 4, 'AMBAS', 'OTROS', 'ACTIVA', DATE '2024-01-01', FALSE),
('a0000000-0000-4000-8000-000000710401', '710401', 'Otros ingresos', '7104', 6, 'ACREEDORA', 'OTROS', 'ACTIVA', DATE '2024-01-01', TRUE),
('a0000000-0000-4000-8000-000000710402', '710402', 'Otros gastos', '7104', 6, 'DEUDORA', 'OTROS', 'ACTIVA', DATE '2024-01-01', TRUE)
ON CONFLICT (codigo) DO NOTHING;

-- Planes comerciales que el ejercicio integral necesita como referencia.
-- Se insertan con los mismos identificadores que usaba seed_ejercicio_completo.sql
-- para que las filas saas_usuario_mes y plan_consumo sigan siendo válidas.
INSERT INTO plan_servicio (id, codigo, nombre, modalidad, tarifa_base, tipo_facturacion, cuenta_ingreso, activo) VALUES
('90000000-0000-4000-8000-000000000101', 'LIC-VENTAS-PRO', 'Licencia descargable Colibrí Ventas Pro', 'LICENCIA', 340.00, 'UNICA', '410601', TRUE),
('90000000-0000-4000-8000-000000000102', 'LIC-GEST-TEAM', 'Licencia descargable Colibrí Gestión Team', 'LICENCIA', 0.00, 'UNICA', '410603', TRUE),
('90000000-0000-4000-8000-000000000103', 'SAAS-VENTAS-PLUS', 'SaaS Colibrí Ventas Plus', 'SAAS', 80.00, 'USUARIO_MES', '410401', TRUE),
('90000000-0000-4000-8000-000000000104', 'SAAS-GEST-BUSINESS', 'SaaS Colibrí Gestión Business', 'SAAS', 120.00, 'USUARIO_MES', '410403', TRUE),
('90000000-0000-4000-8000-000000000105', 'CAP-OPERADORES', 'Capacitación de operadores', 'SERVICIO', 480.00, 'POR_ENTREGA', '410501', TRUE),
('90000000-0000-4000-8000-000000000106', 'PROY-ANALITICA', 'Proyecto de analítica a la medida', 'PROYECTO', 12500.00, 'POR_HITO', '410102', TRUE)
ON CONFLICT (codigo) DO NOTHING;

-- Precios de lista vigentes según el informe: licencias $260/$340, SaaS
-- $50/$80/$120 por usuario-mes, plan de soporte $600.00 mensuales y tarifa de
-- presupuesto de desarrollo $31.00/hora (contrato OS2026001 de $2,500.00).
-- LIC-GEST-TEAM queda en 0.00 porque el informe indica que aún no tiene lote
-- liberado ni precio de lista al corte.
UPDATE plan_servicio SET tarifa_base = v.tarifa
  FROM (VALUES
    ('LIC-INV', 260.00),
    ('LIC-VENTAS-PRO', 340.00),
    ('LIC-GEST-TEAM', 0.00),
    ('SAAS-INV', 50.00),
    ('SAAS-VENTAS-PLUS', 80.00),
    ('SAAS-GEST-BUSINESS', 120.00),
    ('SVC-SOPORTE', 600.00),
    ('OS-MEDIDA', 2500.00),
    ('CAP-OPERADORES', 480.00),
    ('PROY-ANALITICA', 12500.00)
  ) AS v(codigo, tarifa)
 WHERE plan_servicio.codigo = v.codigo;
