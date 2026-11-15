-- Ejercicio integral de Colibrí. Se ejecuta después de seed_00_referencia.sql
-- y seed_anexo_l.sql; añade una operación amplia sin alterar el caso Anexo L.
-- Todas las entidades usan datos ficticios y los documentos se marcan como simulación.
\encoding UTF8
\set ON_ERROR_STOP on

BEGIN;

DO $$
BEGIN
    IF (SELECT count(*) FROM partida WHERE estado = 'CONTABILIZADA') <> 19 THEN
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

INSERT INTO plan_servicio (id, codigo, nombre, modalidad, tarifa_base, tipo_facturacion, cuenta_ingreso, activo) VALUES
 ('90000000-0000-4000-8000-000000000101', 'LIC-VENTAS-PRO', 'Licencia descargable Colibrí Ventas Pro', 'LICENCIA', 420.00, 'UNICA', '410601', TRUE),
 ('90000000-0000-4000-8000-000000000102', 'LIC-GEST-TEAM', 'Licencia descargable Colibrí Gestión Team', 'LICENCIA', 380.00, 'UNICA', '410603', TRUE),
 ('90000000-0000-4000-8000-000000000103', 'SAAS-VENTAS-PLUS', 'SaaS Colibrí Ventas Plus', 'SAAS', 65.00, 'USUARIO_MES', '410401', TRUE),
 ('90000000-0000-4000-8000-000000000104', 'SAAS-GEST-BUSINESS', 'SaaS Colibrí Gestión Business', 'SAAS', 72.00, 'USUARIO_MES', '410403', TRUE),
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

INSERT INTO tercero (id, nit, razon_social, nombre_comercial, es_cliente, es_proveedor, email, telefono, activo) VALUES
 ('90000000-0000-4000-8000-000000000301', 'SIM-COMP-ALFA', 'Grupo Comercial Alfa, S.A. de C.V.', 'Comercial Alfa', TRUE, FALSE, 'finanzas@alfa.simulado', '2222-1101', TRUE),
 ('90000000-0000-4000-8000-000000000302', 'SIM-COMP-BETA', 'Transportes Beta, S.A. de C.V.', 'Transportes Beta', TRUE, FALSE, 'admin@beta.simulado', '2222-1102', TRUE),
 ('90000000-0000-4000-8000-000000000303', 'SIM-COMP-GAMMA', 'Red Médica Gamma, S.A. de C.V.', 'Red Médica Gamma', TRUE, FALSE, 'ti@gamma.simulado', '2222-1103', TRUE),
 ('90000000-0000-4000-8000-000000000304', 'SIM-PROV-QA', 'Pruebas y Calidad, S.A. de C.V.', 'QA Externo', FALSE, TRUE, 'cuentas@qa.simulado', '2222-2101', TRUE),
 ('90000000-0000-4000-8000-000000000305', 'SIM-PROV-LIC', 'Licencias Técnicas Centroamericanas, S.A. de C.V.', 'Licencias Técnicas CA', FALSE, TRUE, 'ventas@licencias.simulado', '2222-2102', TRUE)
ON CONFLICT (nit) DO NOTHING;

INSERT INTO contrato (id, tercero_id, codigo, modalidad, descripcion, fecha_inicio, fecha_fin, monto_sin_iva, activo) VALUES
 ('90000000-0000-4000-8000-000000000311', '90000000-0000-4000-8000-000000000301', 'CTR-2026-ALFA-PROY', 'PROYECTO', 'Portal B2B y analítica de ventas por hitos.', DATE '2026-10-03', DATE '2027-03-31', 18500.00, TRUE),
 ('90000000-0000-4000-8000-000000000312', '90000000-0000-4000-8000-000000000302', 'CTR-2026-BETA-SAAS', 'SAAS', '80 accesos SaaS Ventas Plus facturados por usuario-mes.', DATE '2026-11-01', DATE '2027-10-31', 62400.00, TRUE),
 ('90000000-0000-4000-8000-000000000313', '90000000-0000-4000-8000-000000000303', 'CTR-2026-GAMMA-MIX', 'MIXTO', 'SaaS Gestión, capacitación y desarrollo de integración.', DATE '2026-10-15', DATE '2027-06-30', 29400.00, TRUE),
 ('90000000-0000-4000-8000-000000000314', '90000000-0000-4000-8000-000000000301', 'CTR-2026-ALFA-SOP', 'SOPORTE', 'Bolsa de horas de mantenimiento y soporte.', DATE '2026-11-01', DATE '2027-04-30', 4800.00, TRUE)
ON CONFLICT (codigo) DO NOTHING;

-- Documentos de todos los tipos permitidos, con tres ventas, anticipos y un
-- proveedor de licencias. Los documentos sin asiento son evidencia para probar
-- captura, anulación y notas de crédito/débito.
INSERT INTO documento (id, tipo, emisor_nit, numero_control, codigo_generacion, receptor_nit, tercero_id, contrato_id, fecha_emision, base_gravada, iva, exento, total, iva_incluido, estado, archivo_ruta, archivo_huella, es_simulacion) VALUES
 ('90000000-0000-4000-8000-000000000401', 'CCF', 'SIMULADO-NIT', 'SIM-DTE-2026-401', 'DTE-COMP-401', 'SIM-COMP-BETA', '90000000-0000-4000-8000-000000000302', '90000000-0000-4000-8000-000000000312', DATE '2026-11-03', 5200.00, 676.00, 0, 5876.00, FALSE, 'CONTABILIZADO', 'simulado/dte-401.json', 'sha256-sim-401', TRUE),
 ('90000000-0000-4000-8000-000000000402', 'CCF', 'SIMULADO-NIT', 'SIM-DTE-2026-402', 'DTE-COMP-402', 'SIM-COMP-GAMMA', '90000000-0000-4000-8000-000000000303', '90000000-0000-4000-8000-000000000313', DATE '2026-11-05', 6200.00, 806.00, 0, 7006.00, FALSE, 'CONTABILIZADO', 'simulado/dte-402.json', 'sha256-sim-402', TRUE),
 ('90000000-0000-4000-8000-000000000403', 'FACTURA', 'SIMULADO-NIT', 'SIM-FCF-2026-403', 'FCF-COMP-403', 'SIM-COMP-ALFA', '90000000-0000-4000-8000-000000000301', '90000000-0000-4000-8000-000000000314', DATE '2026-11-06', 840.00, 109.20, 0, 949.20, FALSE, 'CONTABILIZADO', 'simulado/fcf-403.json', 'sha256-sim-403', TRUE),
 ('90000000-0000-4000-8000-000000000404', 'RECIBO', 'SIM-COMP-BETA', 'SIM-RC-2026-404', 'RC-COMP-404', 'SIMULADO-NIT', '90000000-0000-4000-8000-000000000302', '90000000-0000-4000-8000-000000000312', DATE '2026-11-07', 2938.00, 0, 0, 2938.00, FALSE, 'CONTABILIZADO', 'simulado/recibo-404.pdf', 'sha256-sim-404', TRUE),
 ('90000000-0000-4000-8000-000000000405', 'COMPROBANTE', 'SIMULADO-NIT', 'SIM-BC-2026-405', 'BC-COMP-405', 'SIM-COMP-ALFA', '90000000-0000-4000-8000-000000000301', '90000000-0000-4000-8000-000000000311', DATE '2026-11-08', 2500.00, 0, 0, 2500.00, FALSE, 'CONTABILIZADO', 'simulado/anticipo-405.pdf', 'sha256-sim-405', TRUE),
 ('90000000-0000-4000-8000-000000000406', 'CCF', 'SIM-PROV-LIC', 'SIM-CCF-2026-406', 'CCF-COMP-406', 'SIMULADO-NIT', '90000000-0000-4000-8000-000000000305', NULL, DATE '2026-11-09', 900.00, 117.00, 0, 1017.00, FALSE, 'CONTABILIZADO', 'simulado/ccf-406.json', 'sha256-sim-406', TRUE),
 ('90000000-0000-4000-8000-000000000407', 'NC', 'SIMULADO-NIT', 'SIM-NC-2026-407', 'NC-COMP-407', 'SIM-COMP-BETA', '90000000-0000-4000-8000-000000000302', '90000000-0000-4000-8000-000000000312', DATE '2026-11-10', 130.00, 16.90, 0, 146.90, FALSE, 'REGISTRADO', 'simulado/nc-407.json', 'sha256-sim-407', TRUE),
 ('90000000-0000-4000-8000-000000000408', 'ND', 'SIMULADO-NIT', 'SIM-ND-2026-408', 'ND-COMP-408', 'SIM-COMP-GAMMA', '90000000-0000-4000-8000-000000000303', '90000000-0000-4000-8000-000000000313', DATE '2026-11-10', 75.00, 9.75, 0, 84.75, FALSE, 'REGISTRADO', 'simulado/nd-408.json', 'sha256-sim-408', TRUE),
 ('90000000-0000-4000-8000-000000000409', 'OTRO', 'SIM-PROV-QA', 'SIM-OTRO-2026-409', 'OTRO-COMP-409', 'SIMULADO-NIT', '90000000-0000-4000-8000-000000000304', NULL, DATE '2026-11-11', 0, 0, 450.00, 450.00, FALSE, 'ANULADO', 'simulado/acta-qa-409.pdf', 'sha256-sim-409', TRUE)
ON CONFLICT (emisor_nit, codigo_generacion) DO NOTHING;

-- Proyectos a medida en estados distintos: uno entregado, uno en proceso y
-- uno abierto. Los consumos respaldan los paneles de costos y estimación.
INSERT INTO orden_servicio (id, codigo, contrato_id, cliente_id, descripcion, presupuesto, precio_venta, estado, margen_minimo) VALUES
 ('90000000-0000-4000-8000-000000000501', 'OS-2026-015', '90000000-0000-4000-8000-000000000311', '90000000-0000-4000-8000-000000000301', 'Portal B2B: primera fase de desarrollo e integración.', 8900.00, 12500.00, 'EN_PROCESO', 0.35),
 ('90000000-0000-4000-8000-000000000502', 'OS-2026-016', '90000000-0000-4000-8000-000000000313', '90000000-0000-4000-8000-000000000303', 'Integración de expedientes y tablero clínico; hito entregado.', 4600.00, 6200.00, 'ENTREGADA', 0.30),
 ('90000000-0000-4000-0000-000000000503', 'OS-2026-017', '90000000-0000-4000-8000-000000000311', '90000000-0000-4000-8000-000000000301', 'Módulo de conciliación bancaria solicitado para la fase dos.', 3900.00, 6000.00, 'ABIERTA', 0.32)
ON CONFLICT (codigo) DO NOTHING;

-- Partidas de operación de noviembre: proyectos, SaaS, licencias y anticipos.
INSERT INTO evento_contable (id, clave_unica, descripcion) VALUES
 ('90000000-0000-4000-8000-000000000601', 'SEED-COMPLETO-2026-DI-008', 'Costos directos acumulados del proyecto OS-2026-015'),
 ('90000000-0000-4000-8000-000000000602', 'SEED-COMPLETO-2026-DI-009', 'Hito entregado del proyecto OS-2026-016'),
 ('90000000-0000-4000-8000-000000000603', 'SEED-COMPLETO-2026-DI-010', 'Costo del hito entregado OS-2026-016'),
 ('90000000-0000-4000-8000-000000000604', 'SEED-COMPLETO-2026-IN-004', 'Venta de accesos SaaS Ventas Plus'),
 ('90000000-0000-4000-8000-000000000605', 'SEED-COMPLETO-2026-IN-005', 'Venta de accesos SaaS Gestión Business'),
 ('90000000-0000-4000-8000-000000000606', 'SEED-COMPLETO-2026-DI-011', 'Licencias descargables Ventas Pro y Gestión Team'),
 ('90000000-0000-4000-8000-000000000607', 'SEED-COMPLETO-2026-DI-012', 'Costo PEPS de licencias descargables entregadas'),
 ('90000000-0000-4000-8000-000000000608', 'SEED-COMPLETO-2026-IN-006', 'Anticipo de proyecto a la medida')
ON CONFLICT (clave_unica) DO NOTHING;

INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto, estado, origen, documento_soporte, evento_id, creado_por) VALUES
 ('90000000-0000-4000-8000-000000000611', 'DI', 'DI-2026-000008', DATE '2026-11-02', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Costos directos acumulados del proyecto OS-2026-015', 'CONTABILIZADA', 'AUTOMATICO', 'OS-2026-015 / hoja de horas y QA', '90000000-0000-4000-8000-000000000601', (SELECT id FROM contador WHERE usuario = 'contador')),
 ('90000000-0000-4000-8000-000000000612', 'DI', 'DI-2026-000009', DATE '2026-11-05', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Hito entregado del proyecto OS-2026-016', 'CONTABILIZADA', 'AUTOMATICO', 'DTE-COMP-402 / OS-2026-016', '90000000-0000-4000-8000-000000000602', (SELECT id FROM contador WHERE usuario = 'contador')),
 ('90000000-0000-4000-8000-000000000613', 'DI', 'DI-2026-000010', DATE '2026-11-05', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Costo del hito entregado OS-2026-016', 'CONTABILIZADA', 'AUTOMATICO', 'OS-2026-016 / costeo directo', '90000000-0000-4000-8000-000000000603', (SELECT id FROM contador WHERE usuario = 'contador')),
 ('90000000-0000-4000-8000-000000000614', 'IN', 'IN-2026-000004', DATE '2026-11-03', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Venta de accesos SaaS Ventas Plus', 'CONTABILIZADA', 'AUTOMATICO', 'DTE-COMP-401', '90000000-0000-4000-8000-000000000604', (SELECT id FROM contador WHERE usuario = 'contador')),
 ('90000000-0000-4000-8000-000000000615', 'IN', 'IN-2026-000005', DATE '2026-11-04', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Venta de accesos SaaS Gestión Business', 'CONTABILIZADA', 'AUTOMATICO', 'Contrato CTR-2026-GAMMA-MIX', '90000000-0000-4000-8000-000000000605', (SELECT id FROM contador WHERE usuario = 'contador')),
 ('90000000-0000-4000-8000-000000000616', 'DI', 'DI-2026-000011', DATE '2026-11-06', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Licencias descargables Ventas Pro y Gestión Team', 'CONTABILIZADA', 'AUTOMATICO', 'FCF-COMP-403', '90000000-0000-4000-8000-000000000606', (SELECT id FROM contador WHERE usuario = 'contador')),
 ('90000000-0000-4000-8000-000000000617', 'DI', 'DI-2026-000012', DATE '2026-11-06', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Costo PEPS de licencias descargables entregadas', 'CONTABILIZADA', 'AUTOMATICO', 'LI-2026-05 / LI-2026-06', '90000000-0000-4000-8000-000000000607', (SELECT id FROM contador WHERE usuario = 'contador')),
 ('90000000-0000-4000-8000-000000000618', 'IN', 'IN-2026-000006', DATE '2026-11-08', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Anticipo de proyecto a la medida', 'CONTABILIZADA', 'AUTOMATICO', 'BC-COMP-405', '90000000-0000-4000-8000-000000000608', (SELECT id FROM contador WHERE usuario = 'contador'));

INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber) VALUES
 ('90000000-0000-4000-8000-000000000621', '90000000-0000-4000-8000-000000000611', 1, '110501', 'OS-2026-015', 'CC-02', 2800.00, 0),
 ('90000000-0000-4000-8000-000000000622', '90000000-0000-4000-8000-000000000611', 2, '110502', 'OS-2026-015', 'CC-05', 600.00, 0),
 ('90000000-0000-4000-8000-000000000623', '90000000-0000-4000-8000-000000000611', 3, '110102', NULL, 'CC-02', 0, 3400.00),
 ('90000000-0000-4000-8000-000000000624', '90000000-0000-4000-8000-000000000612', 1, '110202', 'OS-2026-016', 'CC-02', 7006.00, 0),
 ('90000000-0000-4000-8000-000000000625', '90000000-0000-4000-8000-000000000612', 2, '410102', 'OS-2026-016', 'CC-02', 0, 6200.00),
 ('90000000-0000-4000-8000-000000000626', '90000000-0000-4000-8000-000000000612', 3, '210201', NULL, 'CC-02', 0, 806.00),
 ('90000000-0000-4000-8000-000000000627', '90000000-0000-4000-8000-000000000613', 1, '510101', 'OS-2026-016', 'CC-02', 2300.00, 0),
 ('90000000-0000-4000-8000-000000000628', '90000000-0000-4000-8000-000000000613', 2, '510102', 'OS-2026-016', 'CC-05', 500.00, 0),
 ('90000000-0000-4000-8000-000000000629', '90000000-0000-4000-8000-000000000613', 3, '510103', 'OS-2026-016', 'CC-07', 300.00, 0),
 ('90000000-0000-4000-8000-000000000630', '90000000-0000-4000-8000-000000000613', 4, '110501', 'OS-2026-016', 'CC-02', 0, 2300.00),
 ('90000000-0000-4000-8000-000000000631', '90000000-0000-4000-8000-000000000613', 5, '110502', 'OS-2026-016', 'CC-05', 0, 500.00),
 ('90000000-0000-4000-8000-000000000632', '90000000-0000-4000-8000-000000000613', 6, '110503', 'OS-2026-016', 'CC-07', 0, 300.00),
 ('90000000-0000-4000-8000-000000000633', '90000000-0000-4000-8000-000000000614', 1, '110203', 'CTR-2026-BETA-SAAS', 'CC-04', 5876.00, 0),
 ('90000000-0000-4000-8000-000000000634', '90000000-0000-4000-8000-000000000614', 2, '410401', 'SAAS-VENTAS-PLUS', 'CC-04', 0, 5200.00),
 ('90000000-0000-4000-8000-000000000635', '90000000-0000-4000-8000-000000000614', 3, '210201', NULL, 'CC-04', 0, 676.00),
 ('90000000-0000-4000-8000-000000000636', '90000000-0000-4000-8000-000000000615', 1, '110203', 'CTR-2026-GAMMA-MIX', 'CC-04', 2440.80, 0),
 ('90000000-0000-4000-8000-000000000637', '90000000-0000-4000-8000-000000000615', 2, '410403', 'SAAS-GEST-BUSINESS', 'CC-04', 0, 2160.00),
 ('90000000-0000-4000-8000-000000000638', '90000000-0000-4000-8000-000000000615', 3, '210201', NULL, 'CC-04', 0, 280.80),
 ('90000000-0000-4000-8000-000000000639', '90000000-0000-4000-8000-000000000616', 1, '110201', 'LIC-DESC-NOV', 'CC-04', 949.20, 0),
 ('90000000-0000-4000-8000-000000000640', '90000000-0000-4000-8000-000000000616', 2, '410201', 'SVC-SOPORTE', 'CC-06', 0, 300.00),
 ('90000000-0000-4000-8000-000000000641', '90000000-0000-4000-8000-000000000616', 3, '410601', 'LIC-VENTAS-PRO', 'CC-04', 0, 300.00),
 ('90000000-0000-4000-8000-000000000642', '90000000-0000-4000-8000-000000000616', 4, '410603', 'LIC-GEST-TEAM', 'CC-04', 0, 240.00),
 ('90000000-0000-4000-8000-000000000643', '90000000-0000-4000-8000-000000000616', 5, '210201', NULL, 'CC-04', 0, 109.20),
 ('90000000-0000-4000-8000-000000000644', '90000000-0000-4000-8000-000000000617', 1, '510301', 'LI-2026-05', 'CC-04', 180.00, 0),
 ('90000000-0000-4000-8000-000000000645', '90000000-0000-4000-8000-000000000617', 2, '510303', 'LI-2026-06', 'CC-04', 140.00, 0),
 ('90000000-0000-4000-8000-000000000646', '90000000-0000-4000-8000-000000000617', 3, '110702', 'LI-2026-05/06', 'CC-04', 0, 320.00),
 ('90000000-0000-4000-8000-000000000647', '90000000-0000-4000-8000-000000000618', 1, '110102', 'CTR-2026-ALFA-PROY', 'CC-04', 2500.00, 0),
 ('90000000-0000-4000-8000-000000000648', '90000000-0000-4000-8000-000000000618', 2, '210701', 'CTR-2026-ALFA-PROY', 'CC-04', 0, 2500.00);

UPDATE evento_contable e SET partida_contabilizada_id = p.id
  FROM partida p
 WHERE p.evento_id = e.id AND e.id BETWEEN '90000000-0000-4000-8000-000000000601' AND '90000000-0000-4000-8000-000000000608';

INSERT INTO documento_evento (id, documento_id, evento_id, tipo_evento, partida_id) VALUES
 ('90000000-0000-4000-8000-000000000651', '90000000-0000-4000-8000-000000000401', '90000000-0000-4000-8000-000000000604', 'VENTA', '90000000-0000-4000-8000-000000000614'),
 ('90000000-0000-4000-8000-000000000652', '90000000-0000-4000-8000-000000000402', '90000000-0000-4000-8000-000000000602', 'VENTA', '90000000-0000-4000-8000-000000000612'),
 ('90000000-0000-4000-8000-000000000653', '90000000-0000-4000-8000-000000000403', '90000000-0000-4000-8000-000000000606', 'VENTA', '90000000-0000-4000-8000-000000000616'),
 ('90000000-0000-4000-8000-000000000654', '90000000-0000-4000-8000-000000000405', '90000000-0000-4000-8000-000000000608', 'ANTICIPO', '90000000-0000-4000-8000-000000000618')
ON CONFLICT (documento_id, tipo_evento) DO NOTHING;

INSERT INTO aplicacion_pago (id, documento_origen_id, documento_pago_id, partida_id, importe, fecha, concepto) VALUES
 ('90000000-0000-4000-8000-000000000661', '90000000-0000-4000-8000-000000000401', '90000000-0000-4000-8000-000000000404', '90000000-0000-4000-8000-000000000614', 2938.00, DATE '2026-11-07', 'Cobro parcial del acceso SaaS de Transportes Beta')
ON CONFLICT (id) DO NOTHING;

INSERT INTO lote_licencia (id, version_id, codigo_lote, unidades_estimadas, costo_total, costo_unitario, unidades_disponibles, fecha_liberacion, partida_id, valor_recuperable) VALUES
 ('90000000-0000-4000-8000-000000000701', '90000000-0000-4000-8000-000000000211', 'LI-2026-05', 10, 900.00, 90.00, 8, DATE '2026-11-01', '90000000-0000-4000-8000-000000000617', 720.00),
 ('90000000-0000-4000-8000-000000000702', '90000000-0000-4000-8000-000000000212', 'LI-2026-06', 8, 560.00, 70.00, 6, DATE '2026-11-01', '90000000-0000-4000-8000-000000000617', 420.00)
ON CONFLICT (codigo_lote) DO NOTHING;

INSERT INTO licencia (id, lote_id, numero, estado, cliente_id, contrato_id, documento_id, fecha_entrega, fecha_activacion, costo_unitario) VALUES
 ('90000000-0000-4000-8000-000000000711', '90000000-0000-4000-8000-000000000701', 1, 'ENTREGADA', '90000000-0000-4000-8000-000000000301', '90000000-0000-4000-8000-000000000314', '90000000-0000-4000-8000-000000000403', DATE '2026-11-06', DATE '2026-11-06', 90.00),
 ('90000000-0000-4000-8000-000000000712', '90000000-0000-4000-8000-000000000701', 2, 'ENTREGADA', '90000000-0000-4000-8000-000000000301', '90000000-0000-4000-8000-000000000314', '90000000-0000-4000-8000-000000000403', DATE '2026-11-06', DATE '2026-11-06', 90.00),
 ('90000000-0000-4000-8000-000000000713', '90000000-0000-4000-8000-000000000701', 3, 'RESERVADA', '90000000-0000-4000-8000-000000000302', '90000000-0000-4000-8000-000000000312', NULL, NULL, NULL, 90.00),
 ('90000000-0000-4000-8000-000000000714', '90000000-0000-4000-8000-000000000702', 1, 'ENTREGADA', '90000000-0000-4000-8000-000000000303', '90000000-0000-4000-8000-000000000313', '90000000-0000-4000-8000-000000000403', DATE '2026-11-06', DATE '2026-11-06', 70.00),
 ('90000000-0000-4000-8000-000000000715', '90000000-0000-4000-8000-000000000702', 2, 'ENTREGADA', '90000000-0000-4000-8000-000000000303', '90000000-0000-4000-8000-000000000313', '90000000-0000-4000-8000-000000000403', DATE '2026-11-06', DATE '2026-11-06', 70.00),
 ('90000000-0000-4000-8000-000000000716', '90000000-0000-4000-8000-000000000702', 3, 'DEVUELTA', '90000000-0000-4000-8000-000000000303', '90000000-0000-4000-8000-000000000313', '90000000-0000-4000-8000-000000000403', DATE '2026-11-06', DATE '2026-11-07', 70.00)
ON CONFLICT (lote_id, numero) DO NOTHING;

INSERT INTO orden_consumo (id, orden_id, tipo, descripcion, cantidad, tarifa, importe, periodo_id, fecha, partida_id) VALUES
 ('90000000-0000-4000-8000-000000000721', '90000000-0000-4000-8000-000000000501', 'MOD', '200 horas de desarrollo de integración', 200, 14.00, 2800.00, 'd78ddace-546f-5102-9d66-e2e8aaee32f2', DATE '2026-11-02', '90000000-0000-4000-8000-000000000611'),
 ('90000000-0000-4000-8000-000000000722', '90000000-0000-4000-8000-000000000501', 'SERVICIO', 'Pruebas de seguridad por tercero', 1, 600.00, 600.00, 'd78ddace-546f-5102-9d66-e2e8aaee32f2', DATE '2026-11-02', '90000000-0000-4000-8000-000000000611'),
 ('90000000-0000-4000-8000-000000000723', '90000000-0000-4000-8000-000000000502', 'MOD', 'Horas del hito de integración', 164.285714, 14.00, 2300.00, 'd78ddace-546f-5102-9d66-e2e8aaee32f2', DATE '2026-11-05', '90000000-0000-4000-8000-000000000613'),
 ('90000000-0000-4000-8000-000000000724', '90000000-0000-4000-8000-000000000502', 'SERVICIO', 'QA clínico especializado', 1, 500.00, 500.00, 'd78ddace-546f-5102-9d66-e2e8aaee32f2', DATE '2026-11-05', '90000000-0000-4000-8000-000000000613'),
 ('90000000-0000-4000-8000-000000000725', '90000000-0000-4000-8000-000000000502', 'CIF', 'CIF aplicado de plataforma', 100, 3.00, 300.00, 'd78ddace-546f-5102-9d66-e2e8aaee32f2', DATE '2026-11-05', '90000000-0000-4000-8000-000000000613');

INSERT INTO saas_usuario_mes (plan_id, periodo, usuarios_mes) VALUES
 ('90000000-0000-4000-8000-000000000103', DATE '2026-11-01', 80),
 ('90000000-0000-4000-8000-000000000104', DATE '2026-11-01', 30),
 ('ffffffff-0001-0001-0001-000000000002', DATE '2026-11-01', 45)
ON CONFLICT (plan_id, periodo) DO NOTHING;

INSERT INTO plan_consumo (id, plan_id, tipo, descripcion, cantidad, tarifa, importe, fecha, partida_id) VALUES
 ('90000000-0000-4000-8000-000000000731', '90000000-0000-4000-8000-000000000103', 'MOD', 'Operación y monitoreo de 80 accesos Ventas', 48, 16.00, 768.00, DATE '2026-11-03', '90000000-0000-4000-8000-000000000614'),
 ('90000000-0000-4000-8000-000000000732', '90000000-0000-4000-8000-000000000103', 'CIF', 'Nube compartida de Ventas', 80, 3.50, 280.00, DATE '2026-11-03', '90000000-0000-4000-8000-000000000614'),
 ('90000000-0000-4000-8000-000000000733', '90000000-0000-4000-8000-000000000104', 'MOD', 'Onboarding de usuarios Gestión', 24, 16.00, 384.00, DATE '2026-11-04', '90000000-0000-4000-8000-000000000615'),
 ('90000000-0000-4000-8000-000000000734', 'ffffffff-0001-0001-0001-000000000004', 'SERVICIO', 'Bolsa de soporte usada por Comercial Alfa', 12, 25.00, 300.00, DATE '2026-11-06', '90000000-0000-4000-8000-000000000616');

-- Cédulas de cierre, CIF y proceso: presentan los datos que necesita cada
-- panel para calcular, comparar y auditar, sin dejar períodos cerrados.
INSERT INTO cif_presupuesto_periodo (id, periodo_id, centro_codigo, cif_estimado, base_estimada, cif_real, base_real) VALUES
 ('90000000-0000-4000-8000-000000000741', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'CC-02', 4200.00, 1400.000000, 4380.00, 1460.000000),
 ('90000000-0000-4000-8000-000000000742', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'CC-07', 3100.00, 900.000000, 2980.00, 880.000000);

INSERT INTO proceso_costo_periodo (id, codigo_proceso, periodo_id, unidades_iniciales, avance_inicial_material, avance_inicial_conversion, costo_inicial_material, costo_inicial_conversion, unidades_iniciadas, unidades_terminadas, unidades_finales, avance_final_material, avance_final_conversion, costo_periodo_material, costo_periodo_conversion) VALUES
 ('90000000-0000-4000-8000-000000000751', 'PROC-VENTAS-3.1', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 20, 1.0, 0.60, 900.00, 540.00, 180, 160, 40, 1.0, 0.45, 6300.00, 4860.00),
 ('90000000-0000-4000-8000-000000000752', 'PROC-GEST-4.0', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 12, 1.0, 0.50, 600.00, 320.00, 88, 75, 25, 1.0, 0.40, 3500.00, 2900.00);

-- Ajustes y cierre: amortización, cartera, deterioro y ciclo CIF cuentan con
-- sus cédulas enlazadas a partidas para conservar la evidencia histórica.
INSERT INTO evento_contable (id, clave_unica, descripcion) VALUES
 ('90000000-0000-4000-8000-000000000831', 'SEED-COMPLETO-2026-AJ-AMORT', 'Amortización mensual del software interno'),
 ('90000000-0000-4000-8000-000000000832', 'SEED-COMPLETO-2026-AJ-CARTERA', 'Estimación de deterioro de cartera de noviembre'),
 ('90000000-0000-4000-8000-000000000833', 'SEED-COMPLETO-2026-AJ-INVENTARIO', 'Deterioro de lote de licencias a valor recuperable'),
 ('90000000-0000-4000-8000-000000000834', 'SEED-COMPLETO-2026-CIF-APLICACION', 'Aplicación de CIF a OS-2026-015'),
 ('90000000-0000-4000-8000-000000000835', 'SEED-COMPLETO-2026-CIF-CIERRE', 'Cierre de variación CIF de desarrollo');

INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto, estado, origen, documento_soporte, evento_id, creado_por) VALUES
 ('90000000-0000-4000-8000-000000000841', 'AJ', 'AJ-2026-000005', DATE '2026-11-30', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Amortización mensual del software interno', 'CONTABILIZADA', 'AUTOMATICO', 'Cédula AI-SOFTWARE', '90000000-0000-4000-8000-000000000831', (SELECT id FROM contador WHERE usuario = 'contador')),
 ('90000000-0000-4000-8000-000000000842', 'AJ', 'AJ-2026-000006', DATE '2026-11-30', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Estimación de deterioro de cartera de noviembre', 'CONTABILIZADA', 'AUTOMATICO', 'Cédula cartera noviembre', '90000000-0000-4000-8000-000000000832', (SELECT id FROM contador WHERE usuario = 'contador')),
 ('90000000-0000-4000-8000-000000000843', 'AJ', 'AJ-2026-000007', DATE '2026-11-30', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Deterioro de lote de licencias a valor recuperable', 'CONTABILIZADA', 'AUTOMATICO', 'Cédula LI-2026-05', '90000000-0000-4000-8000-000000000833', (SELECT id FROM contador WHERE usuario = 'contador')),
 ('90000000-0000-4000-8000-000000000844', 'DI', 'DI-2026-000013', DATE '2026-11-30', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Aplicación de CIF a OS-2026-015', 'CONTABILIZADA', 'AUTOMATICO', 'CIF CC-02 / OS-2026-015', '90000000-0000-4000-8000-000000000834', (SELECT id FROM contador WHERE usuario = 'contador')),
 ('90000000-0000-4000-8000-000000000845', 'AJ', 'AJ-2026-000008', DATE '2026-11-30', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Cierre de variación CIF de desarrollo', 'CONTABILIZADA', 'AUTOMATICO', 'CIF CC-02 noviembre', '90000000-0000-4000-8000-000000000835', (SELECT id FROM contador WHERE usuario = 'contador'));

INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber) VALUES
 ('90000000-0000-4000-8000-000000000851', '90000000-0000-4000-8000-000000000841', 1, '610105', 'AI-SOFTWARE', 'CC-01', 200.00, 0),
 ('90000000-0000-4000-8000-000000000852', '90000000-0000-4000-8000-000000000841', 2, '120401', 'AI-SOFTWARE', 'CC-01', 0, 200.00),
 ('90000000-0000-4000-8000-000000000853', '90000000-0000-4000-8000-000000000842', 1, '610301', 'CARTERA-NOV', 'CC-01', 180.00, 0),
 ('90000000-0000-4000-8000-000000000854', '90000000-0000-4000-8000-000000000842', 2, '110601', 'CARTERA-NOV', 'CC-01', 0, 180.00),
 ('90000000-0000-4000-8000-000000000855', '90000000-0000-4000-8000-000000000843', 1, '610302', 'LI-2026-05', 'CC-04', 120.00, 0),
 ('90000000-0000-4000-8000-000000000856', '90000000-0000-4000-8000-000000000843', 2, '110801', 'LI-2026-05', 'CC-04', 0, 120.00),
 ('90000000-0000-4000-8000-000000000857', '90000000-0000-4000-8000-000000000844', 1, '110503', 'OS-2026-015', 'CC-02', 600.00, 0),
 ('90000000-0000-4000-8000-000000000858', '90000000-0000-4000-8000-000000000844', 2, '520201', 'OS-2026-015', 'CC-02', 0, 600.00),
 ('90000000-0000-4000-8000-000000000859', '90000000-0000-4000-8000-000000000845', 1, '520201', 'CIF-CC-02', 'CC-02', 180.00, 0),
 ('90000000-0000-4000-8000-000000000860', '90000000-0000-4000-8000-000000000845', 2, '520101', 'CIF-CC-02', 'CC-02', 0, 180.00);

UPDATE evento_contable e SET partida_contabilizada_id = p.id
  FROM partida p
 WHERE p.evento_id = e.id AND e.id BETWEEN '90000000-0000-4000-8000-000000000831' AND '90000000-0000-4000-8000-000000000835';

INSERT INTO amortizacion_cuota (id, activo_id, periodo_id, importe, partida_id) VALUES
 ('90000000-0000-4000-8000-000000000861', 'ea1f6dfd-84dd-536e-b143-7bf8f8e84fab', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 200.00, '90000000-0000-4000-8000-000000000841');
INSERT INTO estimacion_cartera_cuota (id, periodo_id, base_estimacion, porcentaje, importe, partida_id) VALUES
 ('90000000-0000-4000-8000-000000000862', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 18000.00, 0.01000000, 180.00, '90000000-0000-4000-8000-000000000842');
INSERT INTO deterioro_lote_licencia (id, lote_id, periodo_id, costo_en_libros, valor_recuperable, importe, partida_id, es_reversion) VALUES
 ('90000000-0000-4000-8000-000000000863', '90000000-0000-4000-8000-000000000701', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 720.00, 600.00, 120.00, '90000000-0000-4000-8000-000000000843', FALSE);
UPDATE lote_licencia SET valor_recuperable = 600.00 WHERE id = '90000000-0000-4000-8000-000000000701';
INSERT INTO cif_aplicacion_orden (id, periodo_id, centro_codigo, orden_id, base_aplicada, tasa_predeterminada, importe, fecha_aplicacion, descripcion, partida_id, clave_evento) VALUES
 ('90000000-0000-4000-8000-000000000864', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'CC-02', '90000000-0000-4000-8000-000000000501', 200.000000, 3.00000000, 600.00, DATE '2026-11-30', 'CIF aplicado sobre las horas directas de OS-2026-015', '90000000-0000-4000-8000-000000000844', 'SEED-COMPLETO-2026-CIF-APLICACION');
INSERT INTO cif_cierre_periodo (id, periodo_id, centro_codigo, cuenta_cif_real, cif_real, cif_aplicado, variacion, fecha_cierre, partida_id, clave_evento) VALUES
 ('90000000-0000-4000-8000-000000000865', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'CC-02', '520101', 4380.00, 4200.00, 180.00, DATE '2026-11-30', '90000000-0000-4000-8000-000000000845', 'SEED-COMPLETO-2026-CIF-CIERRE');

-- Plantillas recurrentes disponibles para generar borradores de diciembre.
INSERT INTO plantilla_partida (id, codigo, nombre, tipo, concepto, activo) VALUES
 ('90000000-0000-4000-8000-000000000761', 'PLT-NUBE', 'Devengo mensual de nube', 'AJ', 'Devengo mensual de infraestructura y nube', TRUE),
 ('90000000-0000-4000-8000-000000000762', 'PLT-RENT', 'Consumo mensual de alquiler', 'AJ', 'Reconocimiento de alquiler pagado por anticipado', TRUE),
 ('90000000-0000-4000-8000-000000000763', 'PLT-HON', 'Provisión de honorarios', 'AJ', 'Provisión mensual de honorarios profesionales', TRUE)
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO plantilla_detalle (id, plantilla_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber) VALUES
 ('90000000-0000-4000-8000-000000000771', '90000000-0000-4000-8000-000000000761', 1, '510201', 'NUBE', 'CC-07', 1200.00, 0),
 ('90000000-0000-4000-8000-000000000772', '90000000-0000-4000-8000-000000000761', 2, '110401', 'NUBE', 'CC-07', 0, 1200.00),
 ('90000000-0000-4000-8000-000000000773', '90000000-0000-4000-8000-000000000762', 1, '610102', 'RENTA', 'CC-01', 900.00, 0),
 ('90000000-0000-4000-8000-000000000774', '90000000-0000-4000-8000-000000000762', 2, '110402', 'RENTA', 'CC-01', 0, 900.00),
 ('90000000-0000-4000-8000-000000000775', '90000000-0000-4000-8000-000000000763', 1, '610103', 'HONORARIOS', 'CC-01', 750.00, 0),
 ('90000000-0000-4000-8000-000000000776', '90000000-0000-4000-8000-000000000763', 2, '210803', 'HONORARIOS', 'CC-01', 0, 750.00);

-- Estados del libro diario para demostrar flujo borrador -> aprobado,
-- anulación y reversión, dejando sólo el asiento inverso en los saldos.
INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto, estado, origen, documento_soporte, partida_revertida_id, creado_por) VALUES
 ('90000000-0000-4000-8000-000000000781', 'DI', 'DI-2026-000090', DATE '2026-11-12', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Borrador de demostración para revisión del contador', 'BORRADOR', 'MANUAL', 'BORRADOR-DEMO', NULL, (SELECT id FROM contador WHERE usuario = 'contador')),
 ('90000000-0000-4000-8000-000000000782', 'DI', 'DI-2026-000091', DATE '2026-11-12', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Asiento aprobado pendiente de contabilización', 'APROBADA', 'MANUAL', 'APROBADA-DEMO', NULL, (SELECT id FROM contador WHERE usuario = 'contador')),
 ('90000000-0000-4000-8000-000000000783', 'AJ', 'AJ-2026-000090', DATE '2026-11-12', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Asiento anulado de demostración', 'ANULADA', 'MANUAL', 'ANULADA-DEMO', NULL, (SELECT id FROM contador WHERE usuario = 'contador')),
 ('90000000-0000-4000-8000-000000000784', 'AJ', 'AJ-2026-000091', DATE '2026-11-12', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Asiento original revertido de demostración', 'REVERTIDA', 'MANUAL', 'REVERSION-DEMO', NULL, (SELECT id FROM contador WHERE usuario = 'contador')),
 ('90000000-0000-4000-8000-000000000785', 'AJ', 'AJ-2026-000092', DATE '2026-11-12', 'd78ddace-546f-5102-9d66-e2e8aaee32f2', 'Reversión del ajuste de demostración', 'CONTABILIZADA', 'MANUAL', 'REVERSION-DEMO', '90000000-0000-4000-8000-000000000784', (SELECT id FROM contador WHERE usuario = 'contador'));

INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber) VALUES
 ('90000000-0000-4000-8000-000000000791', '90000000-0000-4000-8000-000000000781', 1, '610106', 'BORRADOR', 'CC-01', 100.00, 0), ('90000000-0000-4000-8000-000000000792', '90000000-0000-4000-8000-000000000781', 2, '110102', 'BORRADOR', 'CC-01', 0, 100.00),
 ('90000000-0000-4000-8000-000000000793', '90000000-0000-4000-8000-000000000782', 1, '610106', 'APROBADA', 'CC-01', 100.00, 0), ('90000000-0000-4000-8000-000000000794', '90000000-0000-4000-8000-000000000782', 2, '110102', 'APROBADA', 'CC-01', 0, 100.00),
 ('90000000-0000-4000-8000-000000000795', '90000000-0000-4000-8000-000000000783', 1, '610106', 'ANULADA', 'CC-01', 100.00, 0), ('90000000-0000-4000-8000-000000000796', '90000000-0000-4000-8000-000000000783', 2, '110102', 'ANULADA', 'CC-01', 0, 100.00),
 ('90000000-0000-4000-8000-000000000797', '90000000-0000-4000-8000-000000000784', 1, '610106', 'REVERTIR', 'CC-01', 100.00, 0), ('90000000-0000-4000-8000-000000000798', '90000000-0000-4000-8000-000000000784', 2, '110102', 'REVERTIR', 'CC-01', 0, 100.00),
 ('90000000-0000-4000-8000-000000000799', '90000000-0000-4000-8000-000000000785', 1, '110102', 'REVERSION', 'CC-01', 100.00, 0), ('90000000-0000-4000-8000-000000000800', '90000000-0000-4000-8000-000000000785', 2, '610106', 'REVERSION', 'CC-01', 0, 100.00);

-- Soporte técnico: estado de entorno y bitácora de carga visibles en el rol de soporte.
INSERT INTO entorno_tecnico (id, jdbc_url, db_user, ruta_reportes, ruta_dte, impresora, ultimo_respaldo) VALUES
 ('90000000-0000-4000-8000-000000000811', 'jdbc:postgresql://127.0.0.1:5432/colibri', 'colibri', 'simulado/reportes', 'simulado/dte', 'PDF virtual', TIMESTAMP '2026-11-08 07:30:00')
ON CONFLICT (id) DO NOTHING;
INSERT INTO log_tecnico (id, fecha_hora, nivel, modulo, mensaje, traza) VALUES
 ('90000000-0000-4000-8000-000000000821', TIMESTAMP '2026-11-08 07:25:00', 'INFO', 'SEMILLA', 'Ejercicio integral cargado para demostración.', NULL),
 ('90000000-0000-4000-8000-000000000822', TIMESTAMP '2026-11-08 07:27:00', 'WARN', 'DTE', 'Nota de crédito de prueba pendiente de contabilización.', 'Documento NC-COMP-407 permanece registrado.'),
 ('90000000-0000-4000-8000-000000000823', TIMESTAMP '2026-11-08 07:30:00', 'INFO', 'RESPALDO', 'Respaldo simulado registrado.', NULL)
ON CONFLICT (id) DO NOTHING;

-- Cobertura del catálogo: por diseño las cuentas de agrupación no admiten
-- movimientos. Cada subcuenta aún no usada recibe una contrapartida técnica
-- documentada; así se prueba el mayor y la balanza con el 100% del catálogo
-- transaccional sin registrar importes en cuentas de cabecera.
DO $$
DECLARE
    cuenta_pendiente record;
    linea integer := 1;
    importe numeric(18,2);
    partida_cobertura uuid := '90000000-0000-4000-8000-000000000901';
    evento_cobertura uuid := '90000000-0000-4000-8000-000000000902';
BEGIN
    INSERT INTO evento_contable (id, clave_unica, descripcion)
    VALUES (evento_cobertura, 'SEED-COMPLETO-2026-COBERTURA-CATALOGO', 'Cobertura de todas las subcuentas transaccionales para el ejercicio integral');
    INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto, estado, origen, documento_soporte, evento_id, creado_por)
    VALUES (partida_cobertura, 'AJ', 'AJ-2026-000099', DATE '2026-11-13', 'd78ddace-546f-5102-9d66-e2e8aaee32f2',
            'Cobertura técnica de subcuentas aún sin movimiento', 'CONTABILIZADA', 'MANUAL', 'MATRIZ-COBERTURA-CATALOGO', evento_cobertura,
            (SELECT id FROM contador WHERE usuario = 'contador'));
    FOR cuenta_pendiente IN
        SELECT c.codigo, c.naturaleza
          FROM cuenta c
         WHERE c.admite_movimiento
           AND NOT EXISTS (SELECT 1 FROM partida_detalle d WHERE d.codigo_cuenta = c.codigo)
         ORDER BY c.codigo
    LOOP
        importe := 100.00 + linea;
        IF cuenta_pendiente.naturaleza = 'DEUDORA' THEN
            INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber)
            VALUES ((substr(md5('COB-D-' || cuenta_pendiente.codigo),1,8)||'-'||substr(md5('COB-D-' || cuenta_pendiente.codigo),9,4)||'-4'||substr(md5('COB-D-' || cuenta_pendiente.codigo),14,3)||'-8'||substr(md5('COB-D-' || cuenta_pendiente.codigo),18,3)||'-'||substr(md5('COB-D-' || cuenta_pendiente.codigo),21,12))::uuid, partida_cobertura, linea, cuenta_pendiente.codigo, 'COBERTURA', 'GENERAL', importe, 0);
            linea := linea + 1;
            INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber)
            VALUES ((substr(md5('COB-H-' || cuenta_pendiente.codigo),1,8)||'-'||substr(md5('COB-H-' || cuenta_pendiente.codigo),9,4)||'-4'||substr(md5('COB-H-' || cuenta_pendiente.codigo),14,3)||'-8'||substr(md5('COB-H-' || cuenta_pendiente.codigo),18,3)||'-'||substr(md5('COB-H-' || cuenta_pendiente.codigo),21,12))::uuid, partida_cobertura, linea, CASE WHEN cuenta_pendiente.codigo = '310102' THEN '110102' ELSE '310102' END, 'COBERTURA', 'GENERAL', 0, importe);
        ELSE
            INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber)
            VALUES ((substr(md5('COB-D-' || cuenta_pendiente.codigo),1,8)||'-'||substr(md5('COB-D-' || cuenta_pendiente.codigo),9,4)||'-4'||substr(md5('COB-D-' || cuenta_pendiente.codigo),14,3)||'-8'||substr(md5('COB-D-' || cuenta_pendiente.codigo),18,3)||'-'||substr(md5('COB-D-' || cuenta_pendiente.codigo),21,12))::uuid, partida_cobertura, linea, CASE WHEN cuenta_pendiente.codigo = '110102' THEN '310102' ELSE '110102' END, 'COBERTURA', 'GENERAL', importe, 0);
            linea := linea + 1;
            INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber)
            VALUES ((substr(md5('COB-H-' || cuenta_pendiente.codigo),1,8)||'-'||substr(md5('COB-H-' || cuenta_pendiente.codigo),9,4)||'-4'||substr(md5('COB-H-' || cuenta_pendiente.codigo),14,3)||'-8'||substr(md5('COB-H-' || cuenta_pendiente.codigo),18,3)||'-'||substr(md5('COB-H-' || cuenta_pendiente.codigo),21,12))::uuid, partida_cobertura, linea, cuenta_pendiente.codigo, 'COBERTURA', 'GENERAL', 0, importe);
        END IF;
        linea := linea + 1;
    END LOOP;
    UPDATE evento_contable SET partida_contabilizada_id = partida_cobertura WHERE id = evento_cobertura;
END $$;

INSERT INTO folio_secuencia (anio, tipo, ultimo) VALUES
 (2026, 'DI', 99), (2026, 'IN', 6), (2026, 'AJ', 99)
ON CONFLICT (anio, tipo) DO UPDATE SET ultimo = GREATEST(folio_secuencia.ultimo, EXCLUDED.ultimo);

COMMIT;
