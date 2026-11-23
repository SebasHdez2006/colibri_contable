-- Ejercicio integral de Colibrí. Se ejecuta después de seed_00_referencia.sql
-- y seed_anexo_l.sql; añade la operación de noviembre sin alterar el caso Anexo L.
-- Todas las entidades usan datos ficticios y los documentos se marcan como simulación.
-- Los folios continúan la secuencia de octubre (DI desde 22, IN desde 6, AJ desde 13).
\encoding UTF8
\set ON_ERROR_STOP on

BEGIN;

DO $$
BEGIN
    IF (SELECT count(*) FROM partida WHERE estado = 'CONTABILIZADA') <> 49 THEN
        RAISE EXCEPTION 'El ejercicio completo requiere primero la semilla intacta del Anexo L';
    END IF;
    IF EXISTS (SELECT 1 FROM evento_contable WHERE clave_unica = 'SEED-COMPLETO-2026-COBERTURA-CATALOGO') THEN
        RAISE EXCEPTION 'El ejercicio completo ya fue cargado';
    END IF;
END $$;

-- Una fotografía realista: octubre se encuentra en revisión y noviembre es el
-- período operativo. Los nueve períodos previos permanecen cerrados.
UPDATE periodo SET estado = 'EN_REVISION'
 WHERE id = 'fe24eeb9-6c1d-562b-af34-318e4bd29720';

INSERT INTO centro_costo (codigo, nombre, activo) VALUES
 ('CC-05', 'Aseguramiento de calidad', TRUE),
 ('CC-06', 'Éxito del cliente', TRUE),
 ('CC-07', 'Plataforma y nube', TRUE)
ON CONFLICT (codigo) DO NOTHING;

-- Los planes comerciales ya existen desde V24 con las tarifas del informe
-- (licencias $260/$340, SaaS $50/$80/$120 por usuario-mes). Se mantienen las
-- filas para que una base migrada desde antes de V24 conserve los mismos ids.
INSERT INTO plan_servicio (id, codigo, nombre, modalidad, tarifa_base, tipo_facturacion, cuenta_ingreso, activo) VALUES
 ('90000000-0000-4000-8000-000000000101', 'LIC-VENTAS-PRO', 'Licencia descargable Colibrí Ventas Pro', 'LICENCIA', 340.00, 'UNICA', '410601', TRUE),
 ('90000000-0000-4000-8000-000000000102', 'LIC-GEST-TEAM', 'Licencia descargable Colibrí Gestión Team', 'LICENCIA', 0.00, 'UNICA', '410603', TRUE),
 ('90000000-0000-4000-8000-000000000103', 'SAAS-VENTAS-PLUS', 'SaaS Colibrí Ventas Plus', 'SAAS', 80.00, 'USUARIO_MES', '410401', TRUE),
 ('90000000-0000-4000-8000-000000000104', 'SAAS-GEST-BUSINESS', 'SaaS Colibrí Gestión Business', 'SAAS', 120.00, 'USUARIO_MES', '410403', TRUE),
 ('90000000-0000-4000-8000-000000000105', 'CAP-OPERADORES', 'Capacitación de operadores', 'SERVICIO', 480.00, 'POR_ENTREGA', '410501', TRUE),
 ('90000000-0000-4000-8000-000000000106', 'PROY-ANALITICA', 'Proyecto de analítica a la medida', 'PROYECTO', 12500.00, 'POR_HITO', '410102', TRUE)
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO producto (id, codigo, nombre, modalidad, activo) VALUES
 ('90000000-0000-4000-8000-000000000201', 'COL-SAAS-VENTAS', 'SaaS Colibrí Ventas', 'SAAS', TRUE),
 ('90000000-0000-4000-8000-000000000202', 'COL-SAAS-GEST', 'SaaS Colibrí Gestión', 'SAAS', TRUE)
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO producto_version (id, producto_id, version_label, estado) VALUES
 ('90000000-0000-4000-8000-000000000211', (SELECT id FROM producto WHERE codigo = 'COL-VENTAS'), '3.1', 'LIBERADA'),
 ('90000000-0000-4000-8000-000000000212', (SELECT id FROM producto WHERE codigo = 'COL-GEST'), '4.0', 'LIBERADA'),
 ('90000000-0000-4000-8000-000000000213', (SELECT id FROM producto WHERE codigo = 'COL-SAAS-VENTAS'), '2026.11', 'EN_PRODUCCION'),
 ('90000000-0000-4000-8000-000000000214', (SELECT id FROM producto WHERE codigo = 'COL-SAAS-GEST'), '2026.11', 'EN_PRODUCCION')
ON CONFLICT (producto_id, version_label) DO NOTHING;

-- Terceros del ejercicio: clientes Alfa, Beta y Gamma; proveedores de QA y de
-- licencias, conforme a la tabla XCII del informe.
INSERT INTO tercero (id, nit, razon_social, nombre_comercial, es_cliente, es_proveedor, email, telefono, activo) VALUES
 ('90000000-0000-4000-8000-000000000301', 'SIM-COMP-ALFA', 'Grupo Comercial Alfa, S.A. de C.V.', 'Comercial Alfa', TRUE, FALSE, 'finanzas@alfa.simulado', '2222-1101', TRUE),
 ('90000000-0000-4000-8000-000000000302', 'SIM-COMP-BETA', 'Transportes Beta, S.A. de C.V.', 'Transportes Beta', TRUE, FALSE, 'admin@beta.simulado', '2222-1102', TRUE),
 ('90000000-0000-4000-8000-000000000303', 'SIM-COMP-GAMMA', 'Red Médica Gamma, S.A. de C.V.', 'Red Médica Gamma', TRUE, FALSE, 'ti@gamma.simulado', '2222-1103', TRUE),
 ('90000000-0000-4000-8000-000000000304', 'SIM-PROV-QA', 'Pruebas y Calidad, S.A. de C.V.', 'QA Externo', FALSE, TRUE, 'cuentas@qa.simulado', '2222-2101', TRUE),
 ('90000000-0000-4000-8000-000000000305', 'SIM-PROV-LIC', 'Licencias Técnicas Centroamericanas, S.A. de C.V.', 'Licencias Técnicas CA', FALSE, TRUE, 'ventas@licencias.simulado', '2222-2102', TRUE)
ON CONFLICT (nit) DO NOTHING;

INSERT INTO contrato (id, tercero_id, codigo, modalidad, descripcion, fecha_inicio, fecha_fin, monto_sin_iva, activo) VALUES
 ('90000000-0000-4000-8000-000000000311', '90000000-0000-4000-8000-000000000301', 'CTR-2026-ALFA-PROY', 'PROYECTO', 'Portal B2B y analítica de ventas por hitos.', DATE '2026-10-03', DATE '2027-03-31', 18500.00, TRUE),
 ('90000000-0000-4000-8000-000000000312', '90000000-0000-4000-8000-000000000302', 'CTR-2026-BETA-SAAS', 'SAAS', '80 accesos SaaS Ventas Plus facturados por usuario-mes.', DATE '2026-11-01', DATE '2027-10-31', 76800.00, TRUE),
 ('90000000-0000-4000-8000-000000000313', '90000000-0000-4000-8000-000000000303', 'CTR-2026-GAMMA-MIX', 'MIXTO', 'SaaS Gestión, capacitación y desarrollo de integración.', DATE '2026-10-15', DATE '2027-06-30', 29400.00, TRUE),
 ('90000000-0000-4000-8000-000000000314', '90000000-0000-4000-8000-000000000301', 'CTR-2026-ALFA-SOP', 'SOPORTE', 'Bolsa de horas de mantenimiento y soporte.', DATE '2026-11-01', DATE '2027-04-30', 4800.00, TRUE)
ON CONFLICT (codigo) DO NOTHING;

-- Documentos de todos los tipos permitidos: CCF, factura, recibo, comprobante,
-- nota de crédito, nota de débito y otro documento. Los importes de las ventas
-- SaaS siguen las tarifas del informe (45/80/30 usuarios-mes).
INSERT INTO documento (id, tipo, emisor_nit, numero_control, codigo_generacion, receptor_nit, tercero_id, contrato_id, fecha_emision, base_gravada, iva, exento, total, iva_incluido, estado, archivo_ruta, archivo_huella, es_simulacion) VALUES
 ('90000000-0000-4000-8000-000000000401', 'CCF', 'SIMULADO-NIT', 'SIM-DTE-2026-401', 'DTE-COMP-401', 'SIM-COMP-BETA', '90000000-0000-4000-8000-000000000302', '90000000-0000-4000-8000-000000000312', DATE '2026-11-03', 6400.00, 832.00, 0, 7232.00, FALSE, 'CONTABILIZADO', 'simulado/dte-401.json', 'sha256-sim-401', TRUE),
 ('90000000-0000-4000-8000-000000000402', 'CCF', 'SIMULADO-NIT', 'SIM-DTE-2026-402', 'DTE-COMP-402', 'SIM-COMP-GAMMA', '90000000-0000-4000-8000-000000000303', '90000000-0000-4000-8000-000000000313', DATE '2026-11-05', 6200.00, 806.00, 0, 7006.00, FALSE, 'CONTABILIZADO', 'simulado/dte-402.json', 'sha256-sim-402', TRUE),
 ('90000000-0000-4000-8000-000000000403', 'FACTURA', 'SIMULADO-NIT', 'SIM-FCF-2026-403', 'FCF-COMP-403', 'SIM-COMP-ALFA', '90000000-0000-4000-8000-000000000301', '90000000-0000-4000-8000-000000000314', DATE '2026-11-06', 2080.00, 270.40, 0, 2350.40, FALSE, 'CONTABILIZADO', 'simulado/fcf-403.json', 'sha256-sim-403', TRUE),
 ('90000000-0000-4000-8000-000000000404', 'RECIBO', 'SIM-COMP-BETA', 'SIM-RC-2026-404', 'RC-COMP-404', 'SIMULADO-NIT', '90000000-0000-4000-8000-000000000302', '90000000-0000-4000-8000-000000000312', DATE '2026-11-07', 2938.00, 0, 0, 2938.00, FALSE, 'CONTABILIZADO', 'simulado/recibo-404.pdf', 'sha256-sim-404', TRUE),
 ('90000000-0000-4000-8000-000000000405', 'COMPROBANTE', 'SIMULADO-NIT', 'SIM-BC-2026-405', 'BC-COMP-405', 'SIM-COMP-ALFA', '90000000-0000-4000-8000-000000000301', '90000000-0000-4000-8000-000000000311', DATE '2026-11-08', 2500.00, 0, 0, 2500.00, FALSE, 'CONTABILIZADO', 'simulado/anticipo-405.pdf', 'sha256-sim-405', TRUE),
 ('90000000-0000-4000-8000-000000000406', 'CCF', 'SIM-PROV-LIC', 'SIM-CCF-2026-406', 'CCF-COMP-406', 'SIMULADO-NIT', '90000000-0000-4000-8000-000000000305', NULL, DATE '2026-11-09', 900.00, 117.00, 0, 1017.00, FALSE, 'CONTABILIZADO', 'simulado/ccf-406.json', 'sha256-sim-406', TRUE),
 ('90000000-0000-4000-8000-000000000407', 'NC', 'SIMULADO-NIT', 'SIM-NC-2026-407', 'NC-COMP-407', 'SIM-COMP-BETA', '90000000-0000-4000-8000-000000000302', '90000000-0000-4000-8000-000000000312', DATE '2026-11-10', 130.00, 16.90, 0, 146.90, FALSE, 'REGISTRADO', 'simulado/nc-407.json', 'sha256-sim-407', TRUE),
 ('90000000-0000-4000-8000-000000000408', 'ND', 'SIMULADO-NIT', 'SIM-ND-2026-408', 'ND-COMP-408', 'SIM-COMP-GAMMA', '90000000-0000-4000-8000-000000000303', '90000000-0000-4000-8000-000000000313', DATE '2026-11-10', 75.00, 9.75, 0, 84.75, FALSE, 'REGISTRADO', 'simulado/nd-408.json', 'sha256-sim-408', TRUE),
 ('90000000-0000-4000-8000-000000000409', 'OTRO', 'SIM-PROV-QA', 'SIM-OTRO-2026-409', 'OTRO-COMP-409', 'SIMULADO-NIT', '90000000-0000-4000-8000-000000000304', NULL, DATE '2026-11-11', 0, 0, 450.00, 450.00, FALSE, 'ANULADO', 'simulado/acta-qa-409.pdf', 'sha256-sim-409', TRUE),
 ('90000000-0000-4000-8000-000000000410', 'CCF', 'SIMULADO-NIT', 'SIM-DTE-2026-410', 'DTE-COMP-410', 'SIM-COMP-GAMMA', '90000000-0000-4000-8000-000000000303', '90000000-0000-4000-8000-000000000313', DATE '2026-11-04', 3600.00, 468.00, 0, 4068.00, FALSE, 'CONTABILIZADO', 'simulado/dte-410.json', 'sha256-sim-410', TRUE),
 ('90000000-0000-4000-8000-000000000411', 'CCF', 'SIMULADO-NIT', 'SIM-DTE-2026-411', 'DTE-COMP-411', 'SIM-COMP-ALFA', '90000000-0000-4000-8000-000000000301', NULL, DATE '2026-11-04', 2250.00, 292.50, 0, 2542.50, FALSE, 'CONTABILIZADO', 'simulado/dte-411.json', 'sha256-sim-411', TRUE)
ON CONFLICT (emisor_nit, codigo_generacion) DO NOTHING;

-- Las órdenes OS-2026-014 a OS-2026-017 ya existen desde la semilla del Anexo L
-- con sus clientes reales (Textiles, Ferretería, Logística y Alimentos). Al 8 de
-- noviembre cubren los cuatro estados del ejercicio: OS-014 se cierra, 015 sigue
-- en proceso, 016 está entregada y 017 permanece abierta.
UPDATE orden_servicio SET estado = 'CERRADA' WHERE codigo = 'OS-2026-014';

-- Partidas de operación de noviembre: proyectos, SaaS, licencias y anticipos.
INSERT INTO evento_contable (id, clave_unica, descripcion) VALUES
 ('90000000-0000-4000-8000-000000000601', 'SEED-COMPLETO-2026-DI-022', 'Costos directos acumulados del proyecto OS-2026-015'),
 ('90000000-0000-4000-8000-000000000602', 'SEED-COMPLETO-2026-DI-023', 'Hito entregado del proyecto OS-2026-016'),
 ('90000000-0000-4000-8000-000000000603', 'SEED-COMPLETO-2026-DI-024', 'Costo del hito entregado OS-2026-016'),
 ('90000000-0000-4000-8000-000000000604', 'SEED-COMPLETO-2026-IN-006', 'Venta de accesos SaaS Ventas Plus'),
 ('90000000-0000-4000-8000-000000000605', 'SEED-COMPLETO-2026-IN-007', 'Venta de accesos SaaS Gestión Business'),
 ('90000000-0000-4000-8000-000000000606', 'SEED-COMPLETO-2026-DI-025', 'Licencias descargables Colibrí Inventarios'),
 ('90000000-0000-4000-8000-000000000607', 'SEED-COMPLETO-2026-DI-026', 'Costo PEPS de licencias descargables entregadas'),
 ('90000000-0000-4000-8000-000000000608', 'SEED-COMPLETO-2026-IN-009', 'Anticipo de proyecto a la medida'),
 ('90000000-0000-4000-8000-000000000609', 'SEED-COMPLETO-2026-IN-008', 'Venta de accesos SaaS Inventarios')
ON CONFLICT (clave_unica) DO NOTHING;

INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto, estado, origen, documento_soporte, evento_id, creado_por) VALUES
 ('90000000-0000-4000-8000-000000000611', 'DI', 'DI-2026-000022', DATE '2026-11-02', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Costos directos acumulados del proyecto OS-2026-015', 'CONTABILIZADA', 'AUTOMATICO', 'OS-2026-015 / hoja de horas y QA', '90000000-0000-4000-8000-000000000601', (SELECT id FROM contador WHERE usuario = 'contador')),
 ('90000000-0000-4000-8000-000000000612', 'DI', 'DI-2026-000023', DATE '2026-11-05', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Hito entregado del proyecto OS-2026-016', 'CONTABILIZADA', 'AUTOMATICO', 'DTE-COMP-402 / OS-2026-016', '90000000-0000-4000-8000-000000000602', (SELECT id FROM contador WHERE usuario = 'contador')),
 ('90000000-0000-4000-8000-000000000613', 'DI', 'DI-2026-000024', DATE '2026-11-05', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Costo del hito entregado OS-2026-016', 'CONTABILIZADA', 'AUTOMATICO', 'OS-2026-016 / costeo directo', '90000000-0000-4000-8000-000000000603', (SELECT id FROM contador WHERE usuario = 'contador')),
 ('90000000-0000-4000-8000-000000000614', 'IN', 'IN-2026-000006', DATE '2026-11-03', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Venta de 80 accesos SaaS Ventas Plus a $80.00 por usuario-mes', 'CONTABILIZADA', 'AUTOMATICO', 'DTE-COMP-401', '90000000-0000-4000-8000-000000000604', (SELECT id FROM contador WHERE usuario = 'contador')),
 ('90000000-0000-4000-8000-000000000615', 'IN', 'IN-2026-000007', DATE '2026-11-04', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Venta de 30 accesos SaaS Gestión Business a $120.00 por usuario-mes', 'CONTABILIZADA', 'AUTOMATICO', 'DTE-COMP-410', '90000000-0000-4000-8000-000000000605', (SELECT id FROM contador WHERE usuario = 'contador')),
 ('90000000-0000-4000-8000-000000000616', 'DI', 'DI-2026-000025', DATE '2026-11-06', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Venta de 8 licencias Colibrí Inventarios a $260.00 a Comercial Alfa', 'CONTABILIZADA', 'AUTOMATICO', 'FCF-COMP-403', '90000000-0000-4000-8000-000000000606', (SELECT id FROM contador WHERE usuario = 'contador')),
 ('90000000-0000-4000-8000-000000000617', 'DI', 'DI-2026-000026', DATE '2026-11-06', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Costo PEPS de licencias descargables entregadas en noviembre', 'CONTABILIZADA', 'AUTOMATICO', 'LI-2026-03 / LI-2026-04', '90000000-0000-4000-8000-000000000607', (SELECT id FROM contador WHERE usuario = 'contador')),
 ('90000000-0000-4000-8000-000000000618', 'IN', 'IN-2026-000009', DATE '2026-11-08', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Anticipo de proyecto a la medida', 'CONTABILIZADA', 'AUTOMATICO', 'BC-COMP-405', '90000000-0000-4000-8000-000000000608', (SELECT id FROM contador WHERE usuario = 'contador')),
 ('90000000-0000-4000-8000-000000000619', 'IN', 'IN-2026-000008', DATE '2026-11-04', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Venta de 45 accesos SaaS Inventarios a $50.00 por usuario-mes', 'CONTABILIZADA', 'AUTOMATICO', 'DTE-COMP-411', '90000000-0000-4000-8000-000000000609', (SELECT id FROM contador WHERE usuario = 'contador'));

INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber) VALUES
 ('90000000-0000-4000-8000-000000000621', '90000000-0000-4000-8000-000000000611', 1, '110501', 'OS-2026-015', 'CC-02', 2800.00, 0),
 ('90000000-0000-4000-8000-000000000622', '90000000-0000-4000-8000-000000000611', 2, '110502', 'OS-2026-015', 'CC-05', 600.00, 0),
 ('90000000-0000-4000-8000-000000000623', '90000000-0000-4000-8000-000000000611', 3, '110102', NULL, 'CC-02', 0, 3400.00),
 ('90000000-0000-4000-8000-000000000624', '90000000-0000-4000-8000-000000000612', 1, '110202', 'OS-2026-016', 'CC-02', 7006.00, 0),
 ('90000000-0000-4000-8000-000000000625', '90000000-0000-4000-8000-000000000612', 2, '410101', 'OS-2026-016', 'CC-02', 0, 6200.00),
 ('90000000-0000-4000-8000-000000000626', '90000000-0000-4000-8000-000000000612', 3, '210201', NULL, 'CC-02', 0, 806.00),
 ('90000000-0000-4000-8000-000000000627', '90000000-0000-4000-8000-000000000613', 1, '510101', 'OS-2026-016', 'CC-02', 2300.00, 0),
 ('90000000-0000-4000-8000-000000000628', '90000000-0000-4000-8000-000000000613', 2, '510102', 'OS-2026-016', 'CC-05', 500.00, 0),
 ('90000000-0000-4000-8000-000000000629', '90000000-0000-4000-8000-000000000613', 3, '510103', 'OS-2026-016', 'CC-07', 300.00, 0),
 ('90000000-0000-4000-8000-000000000630', '90000000-0000-4000-8000-000000000613', 4, '110501', 'OS-2026-016', 'CC-02', 0, 2300.00),
 ('90000000-0000-4000-8000-000000000631', '90000000-0000-4000-8000-000000000613', 5, '110502', 'OS-2026-016', 'CC-05', 0, 500.00),
 ('90000000-0000-4000-8000-000000000632', '90000000-0000-4000-8000-000000000613', 6, '110503', 'OS-2026-016', 'CC-07', 0, 300.00),
 ('90000000-0000-4000-8000-000000000633', '90000000-0000-4000-8000-000000000614', 1, '110203', 'CTR-2026-BETA-SAAS', 'CC-04', 7232.00, 0),
 ('90000000-0000-4000-8000-000000000634', '90000000-0000-4000-8000-000000000614', 2, '410401', 'SAAS-VENTAS-PLUS', 'CC-04', 0, 6400.00),
 ('90000000-0000-4000-8000-000000000635', '90000000-0000-4000-8000-000000000614', 3, '210201', NULL, 'CC-04', 0, 832.00),
 ('90000000-0000-4000-8000-000000000636', '90000000-0000-4000-8000-000000000615', 1, '110203', 'CTR-2026-GAMMA-MIX', 'CC-04', 4068.00, 0),
 ('90000000-0000-4000-8000-000000000637', '90000000-0000-4000-8000-000000000615', 2, '410403', 'SAAS-GEST-BUSINESS', 'CC-04', 0, 3600.00),
 ('90000000-0000-4000-8000-000000000638', '90000000-0000-4000-8000-000000000615', 3, '210201', NULL, 'CC-04', 0, 468.00),
 ('90000000-0000-4000-8000-000000000639', '90000000-0000-4000-8000-000000000616', 1, '110201', 'LIC-DESC-NOV', 'CC-04', 2350.40, 0),
 ('90000000-0000-4000-8000-000000000640', '90000000-0000-4000-8000-000000000616', 2, '410602', 'LIC-INV', 'CC-04', 0, 2080.00),
 ('90000000-0000-4000-8000-000000000641', '90000000-0000-4000-8000-000000000616', 3, '210201', NULL, 'CC-04', 0, 270.40),
 ('90000000-0000-4000-8000-000000000642', '90000000-0000-4000-8000-000000000617', 1, '510302', 'LI-2026-03/04', 'CC-04', 875.00, 0),
 ('90000000-0000-4000-8000-000000000643', '90000000-0000-4000-8000-000000000617', 2, '110702', 'LI-2026-03/04', 'CC-04', 0, 875.00),
 ('90000000-0000-4000-8000-000000000644', '90000000-0000-4000-8000-000000000618', 1, '110102', 'CTR-2026-ALFA-PROY', 'CC-04', 2500.00, 0),
 ('90000000-0000-4000-8000-000000000645', '90000000-0000-4000-8000-000000000618', 2, '210701', 'CTR-2026-ALFA-PROY', 'CC-04', 0, 2500.00),
 ('90000000-0000-4000-8000-000000000646', '90000000-0000-4000-8000-000000000619', 1, '110203', 'CTR-2026-ALFA-PROY', 'CC-04', 2542.50, 0),
 ('90000000-0000-4000-8000-000000000647', '90000000-0000-4000-8000-000000000619', 2, '410402', 'SAAS-INV', 'CC-04', 0, 2250.00),
 ('90000000-0000-4000-8000-000000000648', '90000000-0000-4000-8000-000000000619', 3, '210201', NULL, 'CC-04', 0, 292.50);

UPDATE evento_contable e SET partida_contabilizada_id = p.id
  FROM partida p
 WHERE p.evento_id = e.id AND e.id BETWEEN '90000000-0000-4000-8000-000000000601' AND '90000000-0000-4000-8000-000000000609';

INSERT INTO documento_evento (id, documento_id, evento_id, tipo_evento, partida_id) VALUES
 ('90000000-0000-4000-8000-000000000651', '90000000-0000-4000-8000-000000000401', '90000000-0000-4000-8000-000000000604', 'VENTA', '90000000-0000-4000-8000-000000000614'),
 ('90000000-0000-4000-8000-000000000652', '90000000-0000-4000-8000-000000000402', '90000000-0000-4000-8000-000000000602', 'VENTA', '90000000-0000-4000-8000-000000000612'),
 ('90000000-0000-4000-8000-000000000653', '90000000-0000-4000-8000-000000000403', '90000000-0000-4000-8000-000000000606', 'VENTA', '90000000-0000-4000-8000-000000000616'),
 ('90000000-0000-4000-8000-000000000654', '90000000-0000-4000-8000-000000000405', '90000000-0000-4000-8000-000000000608', 'ANTICIPO', '90000000-0000-4000-8000-000000000618'),
 ('90000000-0000-4000-8000-000000000655', '90000000-0000-4000-8000-000000000410', '90000000-0000-4000-8000-000000000605', 'VENTA', '90000000-0000-4000-8000-000000000615'),
 ('90000000-0000-4000-8000-000000000656', '90000000-0000-4000-8000-000000000411', '90000000-0000-4000-8000-000000000609', 'VENTA', '90000000-0000-4000-8000-000000000619')
ON CONFLICT (documento_id, tipo_evento) DO NOTHING;

INSERT INTO aplicacion_pago (id, documento_origen_id, documento_pago_id, partida_id, importe, fecha, concepto) VALUES
 ('90000000-0000-4000-8000-000000000661', '90000000-0000-4000-8000-000000000401', '90000000-0000-4000-8000-000000000404', '90000000-0000-4000-8000-000000000614', 2938.00, DATE '2026-11-07', 'Cobro parcial del acceso SaaS de Transportes Beta')
ON CONFLICT (id) DO NOTHING;

-- Lote nuevo de noviembre: LI-2026-06 se libera el 3 de noviembre con 20
-- unidades de Colibrí Inventarios. El costo unitario ($110.00) sigue el patrón
-- de los lotes recientes del informe (LI-2026-05).
INSERT INTO lote_licencia (id, version_id, codigo_lote, unidades_estimadas, costo_total, costo_unitario, unidades_disponibles, fecha_liberacion, partida_id, valor_recuperable) VALUES
 ('90000000-0000-4000-8000-000000000702', (SELECT pv.id FROM producto_version pv JOIN producto p ON p.id = pv.producto_id WHERE p.codigo = 'COL-INV' AND pv.version_label = '2.0'), 'LI-2026-06', 20, 2200.00, 110.00, 20, DATE '2026-11-03', NULL, NULL)
ON CONFLICT (codigo_lote) DO NOTHING;

INSERT INTO licencia (id, lote_id, numero, estado, cliente_id, contrato_id, documento_id, fecha_entrega, fecha_activacion, costo_unitario) VALUES
 ('90000000-0000-4000-8000-000000000721', '90000000-0000-4000-8000-000000000702', 1, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 110.00),
 ('90000000-0000-4000-8000-000000000722', '90000000-0000-4000-8000-000000000702', 2, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 110.00),
 ('90000000-0000-4000-8000-000000000723', '90000000-0000-4000-8000-000000000702', 3, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 110.00),
 ('90000000-0000-4000-8000-000000000724', '90000000-0000-4000-8000-000000000702', 4, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 110.00),
 ('90000000-0000-4000-8000-000000000725', '90000000-0000-4000-8000-000000000702', 5, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 110.00),
 ('90000000-0000-4000-8000-000000000726', '90000000-0000-4000-8000-000000000702', 6, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 110.00),
 ('90000000-0000-4000-8000-000000000727', '90000000-0000-4000-8000-000000000702', 7, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 110.00),
 ('90000000-0000-4000-8000-000000000728', '90000000-0000-4000-8000-000000000702', 8, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 110.00),
 ('90000000-0000-4000-8000-000000000729', '90000000-0000-4000-8000-000000000702', 9, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 110.00),
 ('90000000-0000-4000-8000-000000000730', '90000000-0000-4000-8000-000000000702', 10, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 110.00),
 ('90000000-0000-4000-8000-000000000731', '90000000-0000-4000-8000-000000000702', 11, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 110.00),
 ('90000000-0000-4000-8000-000000000732', '90000000-0000-4000-8000-000000000702', 12, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 110.00),
 ('90000000-0000-4000-8000-000000000733', '90000000-0000-4000-8000-000000000702', 13, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 110.00),
 ('90000000-0000-4000-8000-000000000734', '90000000-0000-4000-8000-000000000702', 14, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 110.00),
 ('90000000-0000-4000-8000-000000000735', '90000000-0000-4000-8000-000000000702', 15, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 110.00),
 ('90000000-0000-4000-8000-000000000736', '90000000-0000-4000-8000-000000000702', 16, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 110.00),
 ('90000000-0000-4000-8000-000000000737', '90000000-0000-4000-8000-000000000702', 17, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 110.00),
 ('90000000-0000-4000-8000-000000000738', '90000000-0000-4000-8000-000000000702', 18, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 110.00),
 ('90000000-0000-4000-8000-000000000739', '90000000-0000-4000-8000-000000000702', 19, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 110.00),
 ('90000000-0000-4000-8000-000000000740', '90000000-0000-4000-8000-000000000702', 20, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 110.00)
ON CONFLICT (lote_id, numero) DO NOTHING;

-- Movimientos PEPS del 1 al 8 de noviembre (tabla XCVIII): las 8 unidades
-- comprometidas salen de LI-2026-03 (3) y LI-2026-04 (5); LI-2026-04 aparta 6
-- reservas y conserva 1 disponible. La devolución del 4 de noviembre reingresó
-- una unidad a LI-2026-03 y fue reentregada, por lo que las 18 quedan ENTREGADA.
UPDATE licencia SET estado = 'ENTREGADA', cliente_id = '90000000-0000-4000-8000-000000000301',
       documento_id = '90000000-0000-4000-8000-000000000403', fecha_entrega = DATE '2026-11-05',
       fecha_activacion = DATE '2026-11-05'
 WHERE lote_id = (SELECT id FROM lote_licencia WHERE codigo_lote = 'LI-2026-03')
   AND numero BETWEEN 16 AND 18;
UPDATE licencia SET estado = 'ENTREGADA', cliente_id = '90000000-0000-4000-8000-000000000301',
       documento_id = '90000000-0000-4000-8000-000000000403', fecha_entrega = DATE '2026-11-05',
       fecha_activacion = DATE '2026-11-05'
 WHERE lote_id = (SELECT id FROM lote_licencia WHERE codigo_lote = 'LI-2026-04')
   AND numero BETWEEN 1 AND 5;
UPDATE licencia SET estado = 'RESERVADA'
 WHERE lote_id = (SELECT id FROM lote_licencia WHERE codigo_lote = 'LI-2026-04')
   AND numero BETWEEN 6 AND 11;
UPDATE lote_licencia SET unidades_disponibles = 0
 WHERE codigo_lote = 'LI-2026-03';
UPDATE lote_licencia SET unidades_disponibles = 1
 WHERE codigo_lote = 'LI-2026-04';

-- Kárdex de noviembre: liberación de LI-2026-06 y las ocho entregas PEPS del
-- 5 de noviembre. El saldo de LI-2026-03 termina en 0/0 y el de LI-2026-04 en
-- 7/805.00, que equivale a las unidades aún bajo control de la empresa
-- (1 disponible + 6 reservadas). Las ventas son partidas agregadas por lote,
-- por lo que las entregas no llevan partida propia.
INSERT INTO licencia_movimiento (id, lote_id, licencia_id, tipo, fecha, unidades,
    costo_unitario, importe, saldo_unidades, saldo_valor, partida_id, detalle, creado_por)
VALUES ('90000000-0000-4000-8000-000000000800',
        '90000000-0000-4000-8000-000000000702', NULL, 'LIBERACION', DATE '2026-11-03',
        20, 110.00, 2200.00, 20, 2200.00, NULL, 'Liberación de lote',
        (SELECT id FROM contador WHERE usuario = 'contador'))
ON CONFLICT (id) DO NOTHING;

INSERT INTO licencia_movimiento (id, lote_id, licencia_id, tipo, fecha, unidades,
    costo_unitario, importe, saldo_unidades, saldo_valor, partida_id, detalle, creado_por)
SELECT ('90000000-0000-4000-8000-0000000008' || lpad(v.orden::text, 2, '0'))::uuid,
       lo.id, lic.id, 'ENTREGA', DATE '2026-11-05', -1, v.costo, -v.costo,
       v.saldo_u, v.saldo_v, NULL, NULL,
       (SELECT id FROM contador WHERE usuario = 'contador')
FROM (VALUES
    (1, 'LI-2026-03', 16, 100.00, 2, 200.00),
    (2, 'LI-2026-03', 17, 100.00, 1, 100.00),
    (3, 'LI-2026-03', 18, 100.00, 0, 0.00),
    (4, 'LI-2026-04', 1, 115.00, 11, 1265.00),
    (5, 'LI-2026-04', 2, 115.00, 10, 1150.00),
    (6, 'LI-2026-04', 3, 115.00, 9, 1035.00),
    (7, 'LI-2026-04', 4, 115.00, 8, 920.00),
    (8, 'LI-2026-04', 5, 115.00, 7, 805.00)
) AS v(orden, lote, numero, costo, saldo_u, saldo_v)
JOIN lote_licencia lo ON lo.codigo_lote = v.lote
JOIN licencia lic ON lic.lote_id = lo.id AND lic.numero = v.numero
ORDER BY v.orden
ON CONFLICT (id) DO NOTHING;

-- Consumos de las órdenes del Anexo L en noviembre (paneles de costos y horas).
INSERT INTO orden_consumo (id, orden_id, tipo, descripcion, cantidad, tarifa, importe, periodo_id, fecha, partida_id) VALUES
 ('90000000-0000-4000-8000-000000000741', (SELECT id FROM orden_servicio WHERE codigo = 'OS-2026-015'), 'MOD', '200 horas de desarrollo de integración', 200, 14.00, 2800.00, 'd78ddace-546f-5102-9d66-e2e8aaee32f2', DATE '2026-11-02', '90000000-0000-4000-8000-000000000611'),
 ('90000000-0000-4000-8000-000000000742', (SELECT id FROM orden_servicio WHERE codigo = 'OS-2026-015'), 'SERVICIO', 'Pruebas de seguridad por tercero', 1, 600.00, 600.00, 'd78ddace-546f-5102-9d66-e2e8aaee32f2', DATE '2026-11-02', '90000000-0000-4000-8000-000000000611'),
 ('90000000-0000-4000-8000-000000000743', (SELECT id FROM orden_servicio WHERE codigo = 'OS-2026-016'), 'MOD', 'Horas del hito de integración', 164.285714, 14.00, 2300.00, 'd78ddace-546f-5102-9d66-e2e8aaee32f2', DATE '2026-11-05', '90000000-0000-4000-8000-000000000613'),
 ('90000000-0000-4000-8000-000000000744', (SELECT id FROM orden_servicio WHERE codigo = 'OS-2026-016'), 'SERVICIO', 'QA clínico especializado', 1, 500.00, 500.00, 'd78ddace-546f-5102-9d66-e2e8aaee32f2', DATE '2026-11-05', '90000000-0000-4000-8000-000000000613'),
 ('90000000-0000-4000-8000-000000000745', (SELECT id FROM orden_servicio WHERE codigo = 'OS-2026-016'), 'CIF', 'CIF aplicado de plataforma', 100, 3.00, 300.00, 'd78ddace-546f-5102-9d66-e2e8aaee32f2', DATE '2026-11-05', '90000000-0000-4000-8000-000000000613');

-- Usuarios-mes SaaS de noviembre: 45 + 80 + 30 = 155 (tabla XCII).
INSERT INTO saas_usuario_mes (plan_id, periodo, usuarios_mes) VALUES
 ('90000000-0000-4000-8000-000000000103', DATE '2026-11-01', 80),
 ('90000000-0000-4000-8000-000000000104', DATE '2026-11-01', 30),
 ('ffffffff-0001-0001-0001-000000000002', DATE '2026-11-01', 45)
ON CONFLICT (plan_id, periodo) DO NOTHING;

INSERT INTO plan_consumo (id, plan_id, tipo, descripcion, cantidad, tarifa, importe, fecha, partida_id) VALUES
 ('90000000-0000-4000-8000-000000000751', '90000000-0000-4000-8000-000000000103', 'MOD', 'Operación y monitoreo de 80 accesos Ventas', 48, 16.00, 768.00, DATE '2026-11-03', '90000000-0000-4000-8000-000000000614'),
 ('90000000-0000-4000-8000-000000000752', '90000000-0000-4000-8000-000000000103', 'CIF', 'Nube compartida de Ventas', 80, 3.50, 280.00, DATE '2026-11-03', '90000000-0000-4000-8000-000000000614'),
 ('90000000-0000-4000-8000-000000000753', '90000000-0000-4000-8000-000000000104', 'MOD', 'Onboarding de usuarios Gestión', 24, 16.00, 384.00, DATE '2026-11-04', '90000000-0000-4000-8000-000000000615'),
 ('90000000-0000-4000-8000-000000000754', 'ffffffff-0001-0001-0001-000000000004', 'SERVICIO', 'Bolsa de soporte usada por Comercial Alfa', 12, 25.00, 300.00, DATE '2026-11-06', '90000000-0000-4000-8000-000000000616');

-- Cédulas de cierre, CIF y proceso: presentan los datos que necesita cada
-- panel para calcular, comparar y auditar, sin dejar períodos cerrados.
INSERT INTO cif_presupuesto_periodo (id, periodo_id, centro_codigo, cif_estimado, base_estimada, cif_real, base_real) VALUES
 ('90000000-0000-4000-8000-000000000761', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'CC-02', 4200.00, 1400.000000, 4380.00, 1460.000000),
 ('90000000-0000-4000-8000-000000000762', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'CC-07', 3100.00, 900.000000, 2980.00, 880.000000);

INSERT INTO proceso_costo_periodo (id, codigo_proceso, periodo_id, unidades_iniciales, avance_inicial_material, avance_inicial_conversion, costo_inicial_material, costo_inicial_conversion, unidades_iniciadas, unidades_terminadas, unidades_finales, avance_final_material, avance_final_conversion, costo_periodo_material, costo_periodo_conversion) VALUES
 ('90000000-0000-4000-8000-000000000771', 'PROC-VENTAS-3.1', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 20, 1.0, 0.60, 900.00, 540.00, 180, 160, 40, 1.0, 0.45, 6300.00, 4860.00),
 ('90000000-0000-4000-8000-000000000772', 'PROC-GEST-4.0', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 12, 1.0, 0.50, 600.00, 320.00, 88, 75, 25, 1.0, 0.40, 3500.00, 2900.00);

-- Ajustes y cierre de noviembre: amortización de los dos tramos de software
-- ($200.00 + $133.33 = $333.33), estimación de cartera, aplicación y cierre CIF.
-- No hay deterioro de inventario: la tabla CXIV del informe exige que 610302 y
-- 110801 permanezcan cubiertas sólo por la partida técnica AJ-2026-000099.
INSERT INTO evento_contable (id, clave_unica, descripcion) VALUES
 ('90000000-0000-4000-8000-000000000831', 'SEED-COMPLETO-2026-AJ-AMORT', 'Amortización mensual del software interno'),
 ('90000000-0000-4000-8000-000000000832', 'SEED-COMPLETO-2026-AJ-CARTERA', 'Estimación de deterioro de cartera de noviembre'),
 ('90000000-0000-4000-8000-000000000833', 'SEED-COMPLETO-2026-CIF-APLICACION', 'Aplicación de CIF a OS-2026-015'),
 ('90000000-0000-4000-8000-000000000834', 'SEED-COMPLETO-2026-CIF-CIERRE', 'Cierre de variación CIF de desarrollo');

INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto, estado, origen, documento_soporte, evento_id, creado_por) VALUES
 ('90000000-0000-4000-8000-000000000841', 'AJ', 'AJ-2026-000013', DATE '2026-11-30', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Amortización mensual del software interno', 'CONTABILIZADA', 'AUTOMATICO', 'Cédula AI-SOFTWARE', '90000000-0000-4000-8000-000000000831', (SELECT id FROM contador WHERE usuario = 'contador')),
 ('90000000-0000-4000-8000-000000000842', 'AJ', 'AJ-2026-000014', DATE '2026-11-30', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Estimación de deterioro de cartera de noviembre', 'CONTABILIZADA', 'AUTOMATICO', 'Cédula cartera noviembre', '90000000-0000-4000-8000-000000000832', (SELECT id FROM contador WHERE usuario = 'contador')),
 ('90000000-0000-4000-8000-000000000843', 'DI', 'DI-2026-000027', DATE '2026-11-30', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Aplicación de CIF a OS-2026-015', 'CONTABILIZADA', 'AUTOMATICO', 'CIF CC-02 / OS-2026-015', '90000000-0000-4000-8000-000000000833', (SELECT id FROM contador WHERE usuario = 'contador')),
 ('90000000-0000-4000-8000-000000000844', 'AJ', 'AJ-2026-000015', DATE '2026-11-30', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Cierre de variación CIF de desarrollo', 'CONTABILIZADA', 'AUTOMATICO', 'CIF CC-02 noviembre', '90000000-0000-4000-8000-000000000834', (SELECT id FROM contador WHERE usuario = 'contador'));

INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber) VALUES
 ('90000000-0000-4000-8000-000000000851', '90000000-0000-4000-8000-000000000841', 1, '610105', 'AI-SOFTWARE', 'CC-01', 333.33, 0),
 ('90000000-0000-4000-8000-000000000852', '90000000-0000-4000-8000-000000000841', 2, '120401', 'AI-SOFTWARE', 'CC-01', 0, 333.33),
 ('90000000-0000-4000-8000-000000000853', '90000000-0000-4000-8000-000000000842', 1, '610301', 'CARTERA-NOV', 'CC-01', 180.00, 0),
 ('90000000-0000-4000-8000-000000000854', '90000000-0000-4000-8000-000000000842', 2, '110601', 'CARTERA-NOV', 'CC-01', 0, 180.00),
 ('90000000-0000-4000-8000-000000000855', '90000000-0000-4000-8000-000000000843', 1, '110503', 'OS-2026-015', 'CC-02', 600.00, 0),
 ('90000000-0000-4000-8000-000000000856', '90000000-0000-4000-8000-000000000843', 2, '520201', 'OS-2026-015', 'CC-02', 0, 600.00),
 ('90000000-0000-4000-8000-000000000857', '90000000-0000-4000-8000-000000000844', 1, '520201', 'CIF-CC-02', 'CC-02', 180.00, 0),
 ('90000000-0000-4000-8000-000000000858', '90000000-0000-4000-8000-000000000844', 2, '520101', 'CIF-CC-02', 'CC-02', 0, 180.00);

UPDATE evento_contable e SET partida_contabilizada_id = p.id
  FROM partida p
 WHERE p.evento_id = e.id AND e.id BETWEEN '90000000-0000-4000-8000-000000000831' AND '90000000-0000-4000-8000-000000000834';

-- Cuotas de amortización de noviembre: un registro por tramo de software
-- (enero $7,200.00 y junio $4,800.00, vida 36 meses), como en octubre.
INSERT INTO amortizacion_cuota (id, activo_id, periodo_id, importe, partida_id) VALUES
 ('90000000-0000-4000-8000-000000000861', (SELECT id FROM activo_intangible WHERE codigo = 'AI-SOFTWARE-ENE'), 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 200.00, '90000000-0000-4000-8000-000000000841'),
 ('90000000-0000-4000-8000-000000000862', (SELECT id FROM activo_intangible WHERE codigo = 'AI-SOFTWARE-JUN'), 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 133.33, '90000000-0000-4000-8000-000000000841');
INSERT INTO estimacion_cartera_cuota (id, periodo_id, base_estimacion, porcentaje, importe, partida_id) VALUES
 ('90000000-0000-4000-8000-000000000863', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 18000.00, 0.01000000, 180.00, '90000000-0000-4000-8000-000000000842');
INSERT INTO cif_aplicacion_orden (id, periodo_id, centro_codigo, orden_id, base_aplicada, tasa_predeterminada, importe, fecha_aplicacion, descripcion, partida_id, clave_evento) VALUES
 ('90000000-0000-4000-8000-000000000864', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'CC-02', (SELECT id FROM orden_servicio WHERE codigo = 'OS-2026-015'), 200.000000, 3.00000000, 600.00, DATE '2026-11-30', 'CIF aplicado sobre las horas directas de OS-2026-015', '90000000-0000-4000-8000-000000000843', 'SEED-COMPLETO-2026-CIF-APLICACION');
INSERT INTO cif_cierre_periodo (id, periodo_id, centro_codigo, cuenta_cif_real, cif_real, cif_aplicado, variacion, fecha_cierre, partida_id, clave_evento) VALUES
 ('90000000-0000-4000-8000-000000000865', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'CC-02', '520101', 4380.00, 4200.00, 180.00, DATE '2026-11-30', '90000000-0000-4000-8000-000000000844', 'SEED-COMPLETO-2026-CIF-CIERRE');

-- Plantillas recurrentes disponibles para generar borradores de diciembre.
INSERT INTO plantilla_partida (id, codigo, nombre, tipo, concepto, activo) VALUES
 ('90000000-0000-4000-8000-000000000881', 'PLT-NUBE', 'Devengo mensual de nube', 'AJ', 'Devengo mensual de infraestructura y nube', TRUE),
 ('90000000-0000-4000-8000-000000000882', 'PLT-RENT', 'Consumo mensual de alquiler', 'AJ', 'Reconocimiento de alquiler pagado por anticipado', TRUE),
 ('90000000-0000-4000-8000-000000000883', 'PLT-HON', 'Provisión de honorarios', 'AJ', 'Provisión mensual de honorarios profesionales', TRUE)
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO plantilla_detalle (id, plantilla_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber) VALUES
 ('90000000-0000-4000-8000-000000000891', '90000000-0000-4000-8000-000000000881', 1, '510201', 'NUBE', 'CC-07', 1200.00, 0),
 ('90000000-0000-4000-8000-000000000892', '90000000-0000-4000-8000-000000000881', 2, '110401', 'NUBE', 'CC-07', 0, 1200.00),
 ('90000000-0000-4000-8000-000000000893', '90000000-0000-4000-8000-000000000882', 1, '610102', 'RENTA', 'CC-01', 900.00, 0),
 ('90000000-0000-4000-8000-000000000894', '90000000-0000-4000-8000-000000000882', 2, '110402', 'RENTA', 'CC-01', 0, 900.00),
 ('90000000-0000-4000-8000-000000000895', '90000000-0000-4000-8000-000000000883', 1, '610103', 'HONORARIOS', 'CC-01', 750.00, 0),
 ('90000000-0000-4000-8000-000000000896', '90000000-0000-4000-8000-000000000883', 2, '210803', 'HONORARIOS', 'CC-01', 0, 750.00);

-- Estados del libro diario para demostrar flujo borrador -> aprobado,
-- anulación y reversión, dejando sólo el asiento inverso en los saldos.
INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto, estado, origen, documento_soporte, partida_revertida_id, creado_por) VALUES
 ('90000000-0000-4000-8000-000000000901', 'DI', 'DI-2026-000090', DATE '2026-11-12', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Borrador de demostración para revisión del contador', 'BORRADOR', 'MANUAL', 'BORRADOR-DEMO', NULL, (SELECT id FROM contador WHERE usuario = 'contador')),
 ('90000000-0000-4000-8000-000000000902', 'DI', 'DI-2026-000091', DATE '2026-11-12', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Asiento aprobado pendiente de contabilización', 'APROBADA', 'MANUAL', 'APROBADA-DEMO', NULL, (SELECT id FROM contador WHERE usuario = 'contador')),
 ('90000000-0000-4000-8000-000000000903', 'AJ', 'AJ-2026-000090', DATE '2026-11-12', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Asiento anulado de demostración', 'ANULADA', 'MANUAL', 'ANULADA-DEMO', NULL, (SELECT id FROM contador WHERE usuario = 'contador')),
 ('90000000-0000-4000-8000-000000000904', 'AJ', 'AJ-2026-000091', DATE '2026-11-12', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Asiento original revertido de demostración', 'REVERTIDA', 'MANUAL', 'REVERSION-DEMO', NULL, (SELECT id FROM contador WHERE usuario = 'contador')),
 ('90000000-0000-4000-8000-000000000905', 'AJ', 'AJ-2026-000092', DATE '2026-11-12', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Reversión del ajuste de demostración', 'CONTABILIZADA', 'MANUAL', 'REVERSION-DEMO', '90000000-0000-4000-8000-000000000904', (SELECT id FROM contador WHERE usuario = 'contador'));

INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber) VALUES
 ('90000000-0000-4000-8000-000000000911', '90000000-0000-4000-8000-000000000901', 1, '610106', 'BORRADOR', 'CC-01', 100.00, 0), ('90000000-0000-4000-8000-000000000912', '90000000-0000-4000-8000-000000000901', 2, '110102', 'BORRADOR', 'CC-01', 0, 100.00),
 ('90000000-0000-4000-8000-000000000913', '90000000-0000-4000-8000-000000000902', 1, '610106', 'APROBADA', 'CC-01', 100.00, 0), ('90000000-0000-4000-8000-000000000914', '90000000-0000-4000-8000-000000000902', 2, '110102', 'APROBADA', 'CC-01', 0, 100.00),
 ('90000000-0000-4000-8000-000000000915', '90000000-0000-4000-8000-000000000903', 1, '610106', 'ANULADA', 'CC-01', 100.00, 0), ('90000000-0000-4000-8000-000000000916', '90000000-0000-4000-8000-000000000903', 2, '110102', 'ANULADA', 'CC-01', 0, 100.00),
 ('90000000-0000-4000-8000-000000000917', '90000000-0000-4000-8000-000000000904', 1, '610106', 'REVERTIR', 'CC-01', 100.00, 0), ('90000000-0000-4000-8000-000000000918', '90000000-0000-4000-8000-000000000904', 2, '110102', 'REVERTIR', 'CC-01', 0, 100.00),
 ('90000000-0000-4000-8000-000000000919', '90000000-0000-4000-8000-000000000905', 1, '110102', 'REVERSION', 'CC-01', 100.00, 0), ('90000000-0000-4000-8000-000000000920', '90000000-0000-4000-8000-000000000905', 2, '610106', 'REVERSION', 'CC-01', 0, 100.00);

-- Soporte técnico: se actualiza el entorno único con las rutas simuladas y se
-- registra la bitácora de carga visible en el rol de soporte.
UPDATE entorno_tecnico SET ruta_reportes = 'simulado/reportes', ruta_dte = 'simulado/dte',
       impresora = 'PDF virtual', ultimo_respaldo = TIMESTAMP '2026-11-08 07:30:00';
INSERT INTO log_tecnico (id, fecha_hora, nivel, modulo, mensaje, traza) VALUES
 ('90000000-0000-4000-8000-000000000931', TIMESTAMP '2026-11-08 07:25:00', 'INFO', 'SEMILLA', 'Ejercicio integral cargado para demostración.', NULL),
 ('90000000-0000-4000-8000-000000000932', TIMESTAMP '2026-11-08 07:27:00', 'WARN', 'DTE', 'Nota de crédito de prueba pendiente de contabilización.', 'Documento NC-COMP-407 permanece registrado.'),
 ('90000000-0000-4000-8000-000000000933', TIMESTAMP '2026-11-08 07:30:00', 'INFO', 'RESPALDO', 'Respaldo simulado registrado.', NULL)
ON CONFLICT (id) DO NOTHING;

-- Cobertura del catálogo según la tabla CXIV del informe: las 31 subcuentas de
-- movimiento que las operaciones realistas no alcanzan a tocar reciben $1.00
-- en su naturaleza, más $1.00 de Bancos como contrapartida de equilibrio.
-- La partida es del 8 de noviembre, fecha del corte del ejercicio integral.
INSERT INTO evento_contable (id, clave_unica, descripcion) VALUES
 ('90000000-0000-4000-8000-000000000941', 'SEED-COMPLETO-2026-COBERTURA-CATALOGO', 'Cobertura técnica de subcuentas aún sin movimiento (tabla CXIV)');
INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto, estado, origen, documento_soporte, evento_id, creado_por) VALUES
 ('90000000-0000-4000-8000-000000000942', 'AJ', 'AJ-2026-000099', DATE '2026-11-08', 'd78ddace-546f-5102-9d66-e2e8aaee32f2',
  'Cobertura técnica de subcuentas aún sin movimiento', 'CONTABILIZADA', 'MANUAL', 'MATRIZ-COBERTURA-CATALOGO', '90000000-0000-4000-8000-000000000941',
  (SELECT id FROM contador WHERE usuario = 'contador'));
INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber) VALUES
 ('90000000-0000-4000-8000-000000000951', '90000000-0000-4000-8000-000000000942', 1, '110101', 'COBERTURA', 'GENERAL', 1.00, 0),
 ('90000000-0000-4000-8000-000000000952', '90000000-0000-4000-8000-000000000942', 2, '110205', 'COBERTURA', 'GENERAL', 1.00, 0),
 ('90000000-0000-4000-8000-000000000953', '90000000-0000-4000-8000-000000000942', 3, '110303', 'COBERTURA', 'GENERAL', 1.00, 0),
 ('90000000-0000-4000-8000-000000000954', '90000000-0000-4000-8000-000000000942', 4, '110402', 'COBERTURA', 'GENERAL', 1.00, 0),
 ('90000000-0000-4000-8000-000000000955', '90000000-0000-4000-8000-000000000942', 5, '110403', 'COBERTURA', 'GENERAL', 1.00, 0),
 ('90000000-0000-4000-8000-000000000956', '90000000-0000-4000-8000-000000000942', 6, '110404', 'COBERTURA', 'GENERAL', 1.00, 0),
 ('90000000-0000-4000-8000-000000000957', '90000000-0000-4000-8000-000000000942', 7, '120302', 'COBERTURA', 'GENERAL', 1.00, 0),
 ('90000000-0000-4000-8000-000000000958', '90000000-0000-4000-8000-000000000942', 8, '120501', 'COBERTURA', 'GENERAL', 1.00, 0),
 ('90000000-0000-4000-8000-000000000959', '90000000-0000-4000-8000-000000000942', 9, '310302', 'COBERTURA', 'GENERAL', 1.00, 0),
 ('90000000-0000-4000-8000-000000000960', '90000000-0000-4000-8000-000000000942', 10, '410701', 'COBERTURA', 'GENERAL', 1.00, 0),
 ('90000000-0000-4000-8000-000000000961', '90000000-0000-4000-8000-000000000942', 11, '410702', 'COBERTURA', 'GENERAL', 1.00, 0),
 ('90000000-0000-4000-8000-000000000962', '90000000-0000-4000-8000-000000000942', 12, '510303', 'COBERTURA', 'GENERAL', 1.00, 0),
 ('90000000-0000-4000-8000-000000000963', '90000000-0000-4000-8000-000000000942', 13, '610302', 'COBERTURA', 'GENERAL', 1.00, 0),
 ('90000000-0000-4000-8000-000000000964', '90000000-0000-4000-8000-000000000942', 14, '710301', 'COBERTURA', 'GENERAL', 1.00, 0),
 ('90000000-0000-4000-8000-000000000965', '90000000-0000-4000-8000-000000000942', 15, '710402', 'COBERTURA', 'GENERAL', 1.00, 0),
 ('90000000-0000-4000-8000-000000000966', '90000000-0000-4000-8000-000000000942', 16, '110102', 'COBERTURA', 'GENERAL', 1.00, 0),
 ('90000000-0000-4000-8000-000000000967', '90000000-0000-4000-8000-000000000942', 17, '110801', 'COBERTURA', 'GENERAL', 0, 1.00),
 ('90000000-0000-4000-8000-000000000968', '90000000-0000-4000-8000-000000000942', 18, '120402', 'COBERTURA', 'GENERAL', 0, 1.00),
 ('90000000-0000-4000-8000-000000000969', '90000000-0000-4000-8000-000000000942', 19, '210103', 'COBERTURA', 'GENERAL', 0, 1.00),
 ('90000000-0000-4000-8000-000000000970', '90000000-0000-4000-8000-000000000942', 20, '210603', 'COBERTURA', 'GENERAL', 0, 1.00),
 ('90000000-0000-4000-8000-000000000971', '90000000-0000-4000-8000-000000000942', 21, '210604', 'COBERTURA', 'GENERAL', 0, 1.00),
 ('90000000-0000-4000-8000-000000000972', '90000000-0000-4000-8000-000000000942', 22, '210703', 'COBERTURA', 'GENERAL', 0, 1.00),
 ('90000000-0000-4000-8000-000000000973', '90000000-0000-4000-8000-000000000942', 23, '210802', 'COBERTURA', 'GENERAL', 0, 1.00),
 ('90000000-0000-4000-8000-000000000974', '90000000-0000-4000-8000-000000000942', 24, '210803', 'COBERTURA', 'GENERAL', 0, 1.00),
 ('90000000-0000-4000-8000-000000000975', '90000000-0000-4000-8000-000000000942', 25, '210804', 'COBERTURA', 'GENERAL', 0, 1.00),
 ('90000000-0000-4000-8000-000000000976', '90000000-0000-4000-8000-000000000942', 26, '220201', 'COBERTURA', 'GENERAL', 0, 1.00),
 ('90000000-0000-4000-8000-000000000977', '90000000-0000-4000-8000-000000000942', 27, '310401', 'COBERTURA', 'GENERAL', 0, 1.00),
 ('90000000-0000-4000-8000-000000000978', '90000000-0000-4000-8000-000000000942', 28, '410102', 'COBERTURA', 'GENERAL', 0, 1.00),
 ('90000000-0000-4000-8000-000000000979', '90000000-0000-4000-8000-000000000942', 29, '410603', 'COBERTURA', 'GENERAL', 0, 1.00),
 ('90000000-0000-4000-8000-000000000980', '90000000-0000-4000-8000-000000000942', 30, '710101', 'COBERTURA', 'GENERAL', 0, 1.00),
 ('90000000-0000-4000-8000-000000000981', '90000000-0000-4000-8000-000000000942', 31, '710302', 'COBERTURA', 'GENERAL', 0, 1.00),
 ('90000000-0000-4000-8000-000000000982', '90000000-0000-4000-8000-000000000942', 32, '710401', 'COBERTURA', 'GENERAL', 0, 1.00);
UPDATE evento_contable SET partida_contabilizada_id = '90000000-0000-4000-8000-000000000942' WHERE id = '90000000-0000-4000-8000-000000000941';

INSERT INTO folio_secuencia (anio, tipo, ultimo) VALUES
 (2026, 'DI', 91), (2026, 'IN', 9), (2026, 'AJ', 99)
ON CONFLICT (anio, tipo) DO UPDATE SET ultimo = GREATEST(folio_secuencia.ultimo, EXCLUDED.ultimo);

COMMIT;
