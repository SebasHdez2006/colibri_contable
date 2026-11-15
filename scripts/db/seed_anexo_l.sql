-- Generado por scripts/anexo_l/generar_seed_sql.py desde anexo_l.json. No editar a mano.
-- Requiere: reset_total.sql + seed_00_referencia.sql ejecutados antes y usuarios en `contador`.
\encoding UTF8
BEGIN;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM evento_contable WHERE clave_unica LIKE 'SEED-ANEXOL-2026-%') THEN
        RAISE EXCEPTION 'El seed del Anexo L ya fue cargado';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM contador WHERE usuario = 'contador') THEN
        RAISE EXCEPTION 'Falta el usuario contador';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM cuenta WHERE codigo = '510401') THEN
        RAISE EXCEPTION 'Falta el catalogo de referencia (ejecute seed_00_referencia.sql)';
    END IF;
END $$;
INSERT INTO empresa (id, razon_social, nombre_comercial, nit, nrc, moneda, zona_horaria, actividad_economica) VALUES ('84736680-c8a1-50bc-95f2-21c911700c7e', 'Soluciones Empresariales Colibrí, S.A. de C.V.', 'Colibrí', 'SIMULADO-NIT', 'SIMULADO-NRC', 'USD', 'America/El_Salvador', 'Programación informática (CAT-019)');
INSERT INTO ejercicio (id, empresa_id, anio, fecha_inicio, fecha_fin, activo, version_catalogo) VALUES ('0ae7578f-80ac-5463-93d5-a9da5d72b6ca', '84736680-c8a1-50bc-95f2-21c911700c7e', 2026, DATE '2026-01-01', DATE '2026-12-31', TRUE, 1);
INSERT INTO periodo (id, ejercicio_id, mes, fecha_inicio, fecha_fin, estado, version) VALUES ('39fed8bc-67ce-5bef-bd8a-6c4a22b8c097', '0ae7578f-80ac-5463-93d5-a9da5d72b6ca', 1, DATE '2026-01-01', DATE '2026-01-31', 'ABIERTO', 1);
INSERT INTO periodo (id, ejercicio_id, mes, fecha_inicio, fecha_fin, estado, version) VALUES ('9e792af3-b1b7-54ce-b5bf-6fa2b2b836c0', '0ae7578f-80ac-5463-93d5-a9da5d72b6ca', 2, DATE '2026-02-01', DATE '2026-02-28', 'ABIERTO', 1);
INSERT INTO periodo (id, ejercicio_id, mes, fecha_inicio, fecha_fin, estado, version) VALUES ('dda3d166-af53-5c1f-b243-b971da74c147', '0ae7578f-80ac-5463-93d5-a9da5d72b6ca', 3, DATE '2026-03-01', DATE '2026-03-31', 'ABIERTO', 1);
INSERT INTO periodo (id, ejercicio_id, mes, fecha_inicio, fecha_fin, estado, version) VALUES ('859d2eb3-4b1b-51ae-be41-f3d1bc73b669', '0ae7578f-80ac-5463-93d5-a9da5d72b6ca', 4, DATE '2026-04-01', DATE '2026-04-30', 'ABIERTO', 1);
INSERT INTO periodo (id, ejercicio_id, mes, fecha_inicio, fecha_fin, estado, version) VALUES ('3d02001b-55a4-5299-912b-3d6470482f37', '0ae7578f-80ac-5463-93d5-a9da5d72b6ca', 5, DATE '2026-05-01', DATE '2026-05-31', 'ABIERTO', 1);
INSERT INTO periodo (id, ejercicio_id, mes, fecha_inicio, fecha_fin, estado, version) VALUES ('06e0ebbb-3d25-57c6-a1ac-a6f8d07075b9', '0ae7578f-80ac-5463-93d5-a9da5d72b6ca', 6, DATE '2026-06-01', DATE '2026-06-30', 'ABIERTO', 1);
INSERT INTO periodo (id, ejercicio_id, mes, fecha_inicio, fecha_fin, estado, version) VALUES ('dc041932-16e6-5452-bf78-c08a473188ba', '0ae7578f-80ac-5463-93d5-a9da5d72b6ca', 7, DATE '2026-07-01', DATE '2026-07-31', 'ABIERTO', 1);
INSERT INTO periodo (id, ejercicio_id, mes, fecha_inicio, fecha_fin, estado, version) VALUES ('54b9f0ca-00bf-5e64-8058-2117d8a2b0ab', '0ae7578f-80ac-5463-93d5-a9da5d72b6ca', 8, DATE '2026-08-01', DATE '2026-08-31', 'ABIERTO', 1);
INSERT INTO periodo (id, ejercicio_id, mes, fecha_inicio, fecha_fin, estado, version) VALUES ('9d9251bd-f986-569f-ab6f-9fd9fccec9ff', '0ae7578f-80ac-5463-93d5-a9da5d72b6ca', 9, DATE '2026-09-01', DATE '2026-09-30', 'ABIERTO', 1);
INSERT INTO periodo (id, ejercicio_id, mes, fecha_inicio, fecha_fin, estado, version) VALUES ('fe24eeb9-6c1d-562b-af34-318e4bd29720', '0ae7578f-80ac-5463-93d5-a9da5d72b6ca', 10, DATE '2026-10-01', DATE '2026-10-31', 'ABIERTO', 1);
INSERT INTO periodo (id, ejercicio_id, mes, fecha_inicio, fecha_fin, estado, version) VALUES ('d78ddace-546f-5102-9d66-e2e8aaee32f2', '0ae7578f-80ac-5463-93d5-a9da5d72b6ca', 11, DATE '2026-11-01', DATE '2026-11-30', 'ABIERTO', 1);
INSERT INTO periodo (id, ejercicio_id, mes, fecha_inicio, fecha_fin, estado, version) VALUES ('bb7070d3-645c-515e-b71d-b83f253eb029', '0ae7578f-80ac-5463-93d5-a9da5d72b6ca', 12, DATE '2026-12-01', DATE '2026-12-31', 'ABIERTO', 1);
UPDATE catalogo_version SET aprobado = TRUE, aprobado_en = CURRENT_TIMESTAMP, aprobado_por = (SELECT id FROM contador WHERE usuario = 'contador') WHERE version = 1;
INSERT INTO tercero (id, nit, razon_social, nombre_comercial, es_cliente, es_proveedor, activo) VALUES ('4d479225-d6b8-5ac3-8c14-983396d88fa7', 'SIM-ANEXOL-DSM', 'Distribuidora San Marcos', 'Distribuidora San Marcos', TRUE, FALSE, TRUE);
INSERT INTO tercero (id, nit, razon_social, nombre_comercial, es_cliente, es_proveedor, activo) VALUES ('1d75e66b-5bfa-5492-a8cc-a79d10e9d062', 'SIM-ANEXOL-CLN', 'Clínica Los Naranjos', 'Clínica Los Naranjos', TRUE, FALSE, TRUE);
INSERT INTO tercero (id, nit, razon_social, nombre_comercial, es_cliente, es_proveedor, activo) VALUES ('1088c0f3-1aba-5cc0-9609-77ebae2e310b', 'SIM-ANEXOL-CDEV', 'Cliente de desarrollo a medida (OS-2026-014)', 'Cliente de desarrollo a medida (OS-2026-014)', TRUE, FALSE, TRUE);
INSERT INTO tercero (id, nit, razon_social, nombre_comercial, es_cliente, es_proveedor, activo) VALUES ('ab8929c3-88d8-53e3-9fa0-a326138c4a46', 'SIM-ANEXOL-CSAAS', 'Cliente de suscripción SaaS', 'Cliente de suscripción SaaS', TRUE, FALSE, TRUE);
INSERT INTO tercero (id, nit, razon_social, nombre_comercial, es_cliente, es_proveedor, activo) VALUES ('abbc1c69-b06b-5967-947e-1a1ba9881a98', 'SIM-ANEXOL-CSOP', 'Cliente de soporte y mantenimiento', 'Cliente de soporte y mantenimiento', TRUE, FALSE, TRUE);
INSERT INTO tercero (id, nit, razon_social, nombre_comercial, es_cliente, es_proveedor, activo) VALUES ('da791299-7dcf-54b1-aeff-eaba70e55337', 'SIM-ANEXOL-NUBE', 'Proveedor de nube', 'Proveedor de nube', FALSE, TRUE, TRUE);
INSERT INTO tercero (id, nit, razon_social, nombre_comercial, es_cliente, es_proveedor, activo) VALUES ('6c136678-82c3-5ced-9552-293af0b27a93', 'SIM-ANEXOL-RENTA', 'Arrendador de oficina', 'Arrendador de oficina', FALSE, TRUE, TRUE);
INSERT INTO documento (id, tipo, emisor_nit, numero_control, codigo_generacion, receptor_nit, tercero_id, fecha_emision, base_gravada, iva, exento, total, iva_incluido, estado, es_simulacion) VALUES ('718cf7c3-c6e0-5b8b-aa0e-516c052dfe7e', 'CCF', 'SIMULADO-NIT', 'SIM-DTE-2026-278', 'DTE-2026-278', 'SIM-ANEXOL-CDEV', '1088c0f3-1aba-5cc0-9609-77ebae2e310b', DATE '2026-09-20', 8400.00, 1092.00, 0, 9492.00, FALSE, 'CONTABILIZADO', TRUE);
INSERT INTO documento (id, tipo, emisor_nit, numero_control, codigo_generacion, receptor_nit, tercero_id, fecha_emision, base_gravada, iva, exento, total, iva_incluido, estado, es_simulacion) VALUES ('5eac78ee-b506-5996-bbc6-125a6e88bec7', 'CCF', 'SIMULADO-NIT', 'SIM-DTE-2026-314', 'DTE-2026-314', 'SIM-ANEXOL-CDEV', '1088c0f3-1aba-5cc0-9609-77ebae2e310b', DATE '2026-10-02', 8400.00, 1092.00, 0, 9492.00, FALSE, 'CONTABILIZADO', TRUE);
INSERT INTO documento (id, tipo, emisor_nit, numero_control, codigo_generacion, receptor_nit, tercero_id, fecha_emision, base_gravada, iva, exento, total, iva_incluido, estado, es_simulacion) VALUES ('9c1d97dd-a438-5048-b90a-061f54486ce2', 'RECIBO', 'SIM-ANEXOL-CDEV', 'SIM-BC-2026-188', 'BC-2026-188', 'SIMULADO-NIT', '1088c0f3-1aba-5cc0-9609-77ebae2e310b', DATE '2026-10-03', 4746.00, 0.00, 0, 4746.00, FALSE, 'CONTABILIZADO', TRUE);
INSERT INTO documento (id, tipo, emisor_nit, numero_control, codigo_generacion, receptor_nit, tercero_id, fecha_emision, base_gravada, iva, exento, total, iva_incluido, estado, es_simulacion) VALUES ('eedc55c1-b6f5-524b-b9fb-833fee167855', 'CCF', 'SIMULADO-NIT', 'SIM-DTE-2026-315', 'DTE-2026-315', 'SIM-ANEXOL-CSAAS', 'ab8929c3-88d8-53e3-9fa0-a326138c4a46', DATE '2026-10-08', 2250.00, 292.50, 0, 2542.50, FALSE, 'CONTABILIZADO', TRUE);
INSERT INTO documento (id, tipo, emisor_nit, numero_control, codigo_generacion, receptor_nit, tercero_id, fecha_emision, base_gravada, iva, exento, total, iva_incluido, estado, es_simulacion) VALUES ('d2c6b2cd-d738-5985-b655-bd517d50dbc3', 'RECIBO', 'SIM-ANEXOL-CSAAS', 'SIM-BC-2026-191', 'BC-2026-191', 'SIMULADO-NIT', 'ab8929c3-88d8-53e3-9fa0-a326138c4a46', DATE '2026-10-08', 2542.50, 0.00, 0, 2542.50, FALSE, 'CONTABILIZADO', TRUE);
INSERT INTO documento (id, tipo, emisor_nit, numero_control, codigo_generacion, receptor_nit, tercero_id, fecha_emision, base_gravada, iva, exento, total, iva_incluido, estado, es_simulacion) VALUES ('9ba42c30-2a2c-5d2b-a517-f252ea141149', 'CCF', 'SIMULADO-NIT', 'SIM-DTE-2026-316', 'DTE-2026-316', 'SIM-ANEXOL-DSM', '4d479225-d6b8-5ac3-8c14-983396d88fa7', DATE '2026-10-15', 2340.00, 304.20, 0, 2644.20, FALSE, 'CONTABILIZADO', TRUE);
INSERT INTO documento (id, tipo, emisor_nit, numero_control, codigo_generacion, receptor_nit, tercero_id, fecha_emision, base_gravada, iva, exento, total, iva_incluido, estado, es_simulacion) VALUES ('3cea1047-4daf-53dc-a624-6c313c4e2df7', 'CCF', 'SIMULADO-NIT', 'SIM-DTE-2026-317', 'DTE-2026-317', 'SIM-ANEXOL-CLN', '1d75e66b-5bfa-5492-a8cc-a79d10e9d062', DATE '2026-10-28', 1560.00, 202.80, 0, 1762.80, FALSE, 'CONTABILIZADO', TRUE);
INSERT INTO documento (id, tipo, emisor_nit, numero_control, codigo_generacion, receptor_nit, tercero_id, fecha_emision, base_gravada, iva, exento, total, iva_incluido, estado, es_simulacion) VALUES ('a1e388ce-65e9-5f7c-b739-fac64df85400', 'CCF', 'SIMULADO-NIT', 'SIM-DTE-2026-318', 'DTE-2026-318', 'SIM-ANEXOL-CSOP', 'abbc1c69-b06b-5967-947e-1a1ba9881a98', DATE '2026-10-30', 3600.00, 468.00, 0, 4068.00, FALSE, 'CONTABILIZADO', TRUE);
INSERT INTO documento (id, tipo, emisor_nit, numero_control, codigo_generacion, receptor_nit, tercero_id, fecha_emision, base_gravada, iva, exento, total, iva_incluido, estado, es_simulacion) VALUES ('5876d0ff-d4c8-5089-b9aa-6b72425d02ea', 'CCF', 'SIM-ANEXOL-NUBE', 'SIM-ANEXOL-COMPRA-NUBE-OCT', 'ANEXOL-COMPRA-NUBE-OCT', 'SIMULADO-NIT', 'da791299-7dcf-54b1-aeff-eaba70e55337', DATE '2026-10-22', 1200.00, 156.00, 0, 1356.00, FALSE, 'CONTABILIZADO', TRUE);
INSERT INTO documento (id, tipo, emisor_nit, numero_control, codigo_generacion, receptor_nit, tercero_id, fecha_emision, base_gravada, iva, exento, total, iva_incluido, estado, es_simulacion) VALUES ('b9fcd66e-ba6a-50bc-96b6-0ac83deb1c6d', 'CCF', 'SIM-ANEXOL-RENTA', 'SIM-ANEXOL-COMPRA-RENTA-OCT', 'ANEXOL-COMPRA-RENTA-OCT', 'SIMULADO-NIT', '6c136678-82c3-5ced-9552-293af0b27a93', DATE '2026-10-24', 1200.00, 156.00, 0, 1356.00, FALSE, 'CONTABILIZADO', TRUE);
INSERT INTO producto_version (id, producto_id, version_label, estado) VALUES ('14212330-a246-5f53-8034-ede07a27ce54', (SELECT id FROM producto WHERE codigo = 'COL-INV'), '2.0', 'LIBERADA');
INSERT INTO evento_contable (id, clave_unica, descripcion) VALUES ('5d0a556c-2888-5c80-b400-8879a7ce6748', 'SEED-ANEXOL-2026-AP-APERTURA', 'Apertura: saldos acumulados al 30 de septiembre de 2026 (derivados del Anexo L)');
INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto, estado, origen, documento_soporte, evento_id, creado_por) VALUES ('08f891f0-4e96-5cd3-931e-4d3e744cb55b', 'AP', 'AP-2026-000001', DATE '2026-09-30', '9d9251bd-f986-569f-ab6f-9fd9fccec9ff', 'Apertura: saldos acumulados al 30 de septiembre de 2026 (derivados del Anexo L)', 'CONTABILIZADA', 'MANUAL', 'ANEXO-L-APERTURA', '5d0a556c-2888-5c80-b400-8879a7ce6748', (SELECT id FROM contador WHERE usuario = 'contador'));
INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber) VALUES
    ('b79566fe-137e-516a-bac2-3f310c9704f6', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 1, '110102', NULL, NULL, 100914.25, 0.00),
    ('b77de4a7-be76-542f-a626-8c15317fe0f7', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 2, '110202', NULL, NULL, 15660.78, 0.00),
    ('c07c7428-da93-5820-8682-77df46162ed0', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 3, '110203', NULL, NULL, 9510.00, 0.00),
    ('3a92725c-4302-5486-8c70-11150c7b2b58', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 4, '110204', NULL, NULL, 8600.00, 0.00),
    ('4d5ed37c-2ec0-5a8e-be19-25306fb6a2c4', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 5, '110301', NULL, NULL, 202.80, 0.00),
    ('0785bbe3-d208-575e-92ec-d2d610c5d45b', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 6, '110302', NULL, NULL, 6230.00, 0.00),
    ('c79fe228-41ff-535d-8058-be7e783f4664', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 7, '110303', NULL, NULL, 2120.00, 0.00),
    ('65aa9e4f-04b0-5905-8bd4-8f8831f03f91', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 8, '110401', NULL, NULL, 5210.00, 0.00),
    ('9ae40a07-dfcd-505e-af88-d7948999d474', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 9, '110501', NULL, NULL, 12257.75, 0.00),
    ('1609314d-884e-5b36-89a1-a78af28d4582', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 10, '110502', NULL, NULL, 900.00, 0.00),
    ('3d2caadf-68fe-572b-90a0-6316c152054e', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 11, '110503', NULL, NULL, 846.00, 0.00),
    ('d498667b-4d8a-50aa-b470-e7fce186dd30', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 12, '110701', NULL, NULL, 1380.00, 0.00),
    ('3000958d-bdfb-5521-acf7-dc165f92ff10', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 13, '110702', NULL, NULL, 1800.00, 0.00),
    ('1fd6227b-4be6-5b00-a01c-d838726213a8', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 14, '120101', NULL, NULL, 24750.00, 0.00),
    ('ce95d718-850e-5b75-90ad-768aa800de4b', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 15, '120102', NULL, NULL, 5700.00, 0.00),
    ('4c782ee4-4956-55b7-85fb-b30f9402141a', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 16, '120103', NULL, NULL, 6000.00, 0.00),
    ('89d5e63c-891f-5f63-b757-0ec31f36596e', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 17, '120201', NULL, NULL, 0.00, 9870.00),
    ('a7f16a9f-7911-5244-aa14-06b4b53e3103', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 18, '120202', NULL, NULL, 0.00, 2550.00),
    ('0901e1bf-8e33-547a-8ae1-8ce684c848bf', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 19, '120203', NULL, NULL, 0.00, 3000.00),
    ('08ca9b57-225b-51ac-974d-53f076eff368', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 20, '120301', NULL, NULL, 7200.00, 0.00),
    ('bce545ef-925d-5afc-96a4-bba8683dcc96', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 21, '120401', NULL, NULL, 0.00, 1800.00),
    ('108d326b-7438-5220-8470-d35132ccb812', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 22, '210101', NULL, NULL, 0.00, 12800.00),
    ('0bcaa83e-0d2c-5268-97a7-88b82d2aa0f1', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 23, '210601', NULL, NULL, 0.00, 1937.50),
    ('2f397019-e0c7-59ba-abe1-a67c7cd2d6f7', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 24, '210602', NULL, NULL, 0.00, 6458.33),
    ('fde98bd0-dc74-53c4-814d-e40ff96f00ec', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 25, '210704', NULL, NULL, 0.00, 4350.00),
    ('a0fef429-bacc-5eb9-ae13-fb59aca6d225', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 26, '210803', NULL, NULL, 0.00, 2000.00),
    ('770ca437-f0bc-5d69-8227-2996c873602f', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 27, '220101', NULL, NULL, 0.00, 9800.00),
    ('3d3e125a-4fa9-57bd-bfd5-4a54713d58d1', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 28, '310101', NULL, NULL, 0.00, 30000.00),
    ('7fa2fee8-44fd-56aa-92b5-5925f887b3d6', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 29, '310102', NULL, NULL, 0.00, 45000.00),
    ('3a3cc494-8069-577c-b227-a55113e3db8e', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 30, '310201', NULL, NULL, 0.00, 2800.00),
    ('7ed9dfe6-1583-56c7-ae27-2d6111aa6294', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 31, '310301', NULL, NULL, 0.00, 29800.00),
    ('18a4fdcd-f42c-58ae-ad88-090912314f37', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 32, '410101', NULL, NULL, 0.00, 206200.00),
    ('ed79cfb2-2b8e-5209-b4da-5e4b345373bc', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 33, '410201', NULL, NULL, 0.00, 33400.00),
    ('dc505380-aa15-521d-83f5-5ff6b495e216', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 34, '410301', NULL, NULL, 0.00, 12800.00),
    ('3a327ef9-6074-510c-aa2c-ea93f06d2a64', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 35, '410402', NULL, NULL, 0.00, 68950.00),
    ('601fc0e1-4cc7-5e3f-bb74-c92c4e444efd', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 36, '410602', NULL, NULL, 0.00, 20100.00),
    ('ceae0744-75f1-5a1e-bc67-3bf2350246f1', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 37, '510101', NULL, NULL, 68616.00, 0.00),
    ('4c3e161e-ce18-5dd2-951d-69741158c918', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 38, '510102', NULL, NULL, 31290.00, 0.00),
    ('675566a3-9983-535d-9d82-0a5cab627546', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 39, '510103', NULL, NULL, 17154.00, 0.00),
    ('e18bb1c9-786f-5419-b393-a97be912df58', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 40, '510201', NULL, NULL, 10800.00, 0.00),
    ('a26e9295-2daf-52b2-bdfb-ac8d2bf91838', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 41, '510202', NULL, NULL, 6064.81, 0.00),
    ('8b317268-a3b3-514b-b68b-6bf936e49542', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 42, '510203', NULL, NULL, 6400.00, 0.00),
    ('2d5746aa-67d9-50cd-9049-4176c3640c80', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 43, '510302', NULL, NULL, 7900.00, 0.00),
    ('cfabde73-6b18-5ba5-90b1-879ce53f3f45', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 44, '510401', NULL, NULL, 23821.94, 0.00),
    ('d6916452-8a2f-5a0d-9f9d-d3f7c42db8b2', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 45, '610101', NULL, NULL, 57569.38, 0.00),
    ('6f30379f-88e6-5332-b716-06e91955e5d5', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 46, '610102', NULL, NULL, 10900.00, 0.00),
    ('9a9a8f4d-b7af-5722-8041-9a2aab8d7301', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 47, '610103', NULL, NULL, 9300.00, 0.00),
    ('14cfd6c3-37c1-5831-b3ec-e7ebf2dbb0b4', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 48, '610104', NULL, NULL, 4970.00, 0.00),
    ('9182cf02-924c-5167-b13e-702a8662df90', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 49, '610105', NULL, NULL, 1800.00, 0.00),
    ('12a9e3b8-1f66-5466-b943-bccf8323d95a', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 50, '610106', NULL, NULL, 6700.00, 0.00),
    ('8d5f4c5f-8743-599b-baa2-733178466c43', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 51, '610201', NULL, NULL, 19448.12, 0.00),
    ('b8e70058-e713-5018-804c-6b488d0735cc', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 52, '610202', NULL, NULL, 4200.00, 0.00),
    ('416628f2-579e-56ed-9b1d-4fd3f3628651', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 53, '610203', NULL, NULL, 2100.00, 0.00),
    ('8237a31c-8850-536b-bf29-9b3c5929efb1', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 54, '610204', NULL, NULL, 1300.00, 0.00),
    ('77d00bd6-b63d-5d1d-a7a2-d7aa35668985', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 55, '610303', NULL, NULL, 2800.00, 0.00),
    ('435bbd7d-36c3-5e9e-9cb5-417088e2cfbc', '08f891f0-4e96-5cd3-931e-4d3e744cb55b', 56, '710102', NULL, NULL, 1200.00, 0.00);
UPDATE evento_contable SET partida_contabilizada_id = '08f891f0-4e96-5cd3-931e-4d3e744cb55b' WHERE id = '5d0a556c-2888-5c80-b400-8879a7ce6748';
INSERT INTO evento_contable (id, clave_unica, descripcion) VALUES ('a7ab82c4-27a1-5337-8cbe-0db5e640418d', 'SEED-ANEXOL-2026-DI-2026-000001', 'Clientes por desarrollo a medida (DTE-2026-314, hito OS-2026-014 aceptado)');
INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto, estado, origen, documento_soporte, evento_id, creado_por) VALUES ('aef0d60a-7ceb-5235-a611-f6111442d02f', 'DI', 'DI-2026-000001', DATE '2026-10-02', 'fe24eeb9-6c1d-562b-af34-318e4bd29720', 'Clientes por desarrollo a medida (DTE-2026-314, hito OS-2026-014 aceptado)', 'CONTABILIZADA', 'AUTOMATICO', 'DTE-2026-314, OS-2026-014', 'a7ab82c4-27a1-5337-8cbe-0db5e640418d', (SELECT id FROM contador WHERE usuario = 'contador'));
INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber) VALUES
    ('56806f31-fa5f-5eac-b675-f45baaa35bfb', 'aef0d60a-7ceb-5235-a611-f6111442d02f', 1, '110202', NULL, NULL, 9492.00, 0.00),
    ('6351eeee-77b8-5ea9-bfa9-967a99f8b555', 'aef0d60a-7ceb-5235-a611-f6111442d02f', 2, '410101', NULL, NULL, 0.00, 8400.00),
    ('f6969cc3-9094-59e8-b057-44d81fb13b9a', 'aef0d60a-7ceb-5235-a611-f6111442d02f', 3, '210201', NULL, NULL, 0.00, 1092.00);
UPDATE evento_contable SET partida_contabilizada_id = 'aef0d60a-7ceb-5235-a611-f6111442d02f' WHERE id = 'a7ab82c4-27a1-5337-8cbe-0db5e640418d';
INSERT INTO documento_evento (id, documento_id, evento_id, tipo_evento, partida_id) VALUES ('db24b6df-ab62-599b-9152-4a816ce634eb', '5eac78ee-b506-5996-bbc6-125a6e88bec7', 'a7ab82c4-27a1-5337-8cbe-0db5e640418d', 'VENTA', 'aef0d60a-7ceb-5235-a611-f6111442d02f');
INSERT INTO evento_contable (id, clave_unica, descripcion) VALUES ('70ecb2ef-2077-54b7-97b5-61981aa3f423', 'SEED-ANEXOL-2026-IN-2026-000001', 'Bancos (Recibo BC-2026-188, aplicación parcial DTE-2026-278)');
INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto, estado, origen, documento_soporte, evento_id, creado_por) VALUES ('573f1df1-5f2a-5906-84ee-8fd2f1ae3c94', 'IN', 'IN-2026-000001', DATE '2026-10-03', 'fe24eeb9-6c1d-562b-af34-318e4bd29720', 'Bancos (Recibo BC-2026-188, aplicación parcial DTE-2026-278)', 'CONTABILIZADA', 'AUTOMATICO', 'BC-2026-188, DTE-2026-278', '70ecb2ef-2077-54b7-97b5-61981aa3f423', (SELECT id FROM contador WHERE usuario = 'contador'));
INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber) VALUES
    ('c52df53a-7100-51ad-981a-644220650b80', '573f1df1-5f2a-5906-84ee-8fd2f1ae3c94', 1, '110102', NULL, NULL, 4746.00, 0.00),
    ('4775d592-b9a3-5929-a28d-75f63f22a284', '573f1df1-5f2a-5906-84ee-8fd2f1ae3c94', 2, '110202', NULL, NULL, 0.00, 4746.00);
UPDATE evento_contable SET partida_contabilizada_id = '573f1df1-5f2a-5906-84ee-8fd2f1ae3c94' WHERE id = '70ecb2ef-2077-54b7-97b5-61981aa3f423';
INSERT INTO documento_evento (id, documento_id, evento_id, tipo_evento, partida_id) VALUES ('61325471-7703-55f9-9dbf-638f22013450', '9c1d97dd-a438-5048-b90a-061f54486ce2', '70ecb2ef-2077-54b7-97b5-61981aa3f423', 'COBRO', '573f1df1-5f2a-5906-84ee-8fd2f1ae3c94');
INSERT INTO evento_contable (id, clave_unica, descripcion) VALUES ('6ae3d490-d345-5e03-8eeb-94656040691b', 'SEED-ANEXOL-2026-DI-2026-000002', 'Clientes por suscripciones saas (DTE-2026-315, cobertura octubre)');
INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto, estado, origen, documento_soporte, evento_id, creado_por) VALUES ('a119a4a8-5a72-51e9-bf18-5770a98746d9', 'DI', 'DI-2026-000002', DATE '2026-10-08', 'fe24eeb9-6c1d-562b-af34-318e4bd29720', 'Clientes por suscripciones saas (DTE-2026-315, cobertura octubre)', 'CONTABILIZADA', 'AUTOMATICO', 'DTE-2026-315', '6ae3d490-d345-5e03-8eeb-94656040691b', (SELECT id FROM contador WHERE usuario = 'contador'));
INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber) VALUES
    ('31aa5d44-37fc-593a-8aef-d39a25ea695f', 'a119a4a8-5a72-51e9-bf18-5770a98746d9', 1, '110203', NULL, NULL, 2542.50, 0.00),
    ('43e6ef73-4274-54d6-be3f-7f0985bdc78d', 'a119a4a8-5a72-51e9-bf18-5770a98746d9', 2, '410402', NULL, NULL, 0.00, 2250.00),
    ('9552c1ef-9b67-5c79-b79c-f69c0e09b401', 'a119a4a8-5a72-51e9-bf18-5770a98746d9', 3, '210201', NULL, NULL, 0.00, 292.50);
UPDATE evento_contable SET partida_contabilizada_id = 'a119a4a8-5a72-51e9-bf18-5770a98746d9' WHERE id = '6ae3d490-d345-5e03-8eeb-94656040691b';
INSERT INTO documento_evento (id, documento_id, evento_id, tipo_evento, partida_id) VALUES ('d47e7e25-a65b-5a4b-b743-2b3bfc5f2e31', 'eedc55c1-b6f5-524b-b9fb-833fee167855', '6ae3d490-d345-5e03-8eeb-94656040691b', 'VENTA', 'a119a4a8-5a72-51e9-bf18-5770a98746d9');
INSERT INTO evento_contable (id, clave_unica, descripcion) VALUES ('973e727a-2349-5339-b099-1119821cde15', 'SEED-ANEXOL-2026-IN-2026-000002', 'Bancos (Comprobante bancario BC-2026-191)');
INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto, estado, origen, documento_soporte, evento_id, creado_por) VALUES ('106e5da1-e7bb-5cc6-95f0-58b726716a62', 'IN', 'IN-2026-000002', DATE '2026-10-08', 'fe24eeb9-6c1d-562b-af34-318e4bd29720', 'Bancos (Comprobante bancario BC-2026-191)', 'CONTABILIZADA', 'AUTOMATICO', 'BC-2026-191', '973e727a-2349-5339-b099-1119821cde15', (SELECT id FROM contador WHERE usuario = 'contador'));
INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber) VALUES
    ('acf83ff8-8c12-5749-8b58-8c09fb8229f6', '106e5da1-e7bb-5cc6-95f0-58b726716a62', 1, '110102', NULL, NULL, 2542.50, 0.00),
    ('50951883-f737-5918-b8af-edacfe52abbc', '106e5da1-e7bb-5cc6-95f0-58b726716a62', 2, '110203', NULL, NULL, 0.00, 2542.50);
UPDATE evento_contable SET partida_contabilizada_id = '106e5da1-e7bb-5cc6-95f0-58b726716a62' WHERE id = '973e727a-2349-5339-b099-1119821cde15';
INSERT INTO documento_evento (id, documento_id, evento_id, tipo_evento, partida_id) VALUES ('5b8e262d-403c-576a-852d-fe0f1aa7b4c9', 'd2c6b2cd-d738-5985-b655-bd517d50dbc3', '973e727a-2349-5339-b099-1119821cde15', 'COBRO', '106e5da1-e7bb-5cc6-95f0-58b726716a62');
INSERT INTO evento_contable (id, clave_unica, descripcion) VALUES ('d7b7ca60-970f-5bfa-9ce3-c17e7c43e811', 'SEED-ANEXOL-2026-DI-2026-000003', 'Licencias de versiones liberadas (Acta LI-2026-04 y hoja de costos)');
INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto, estado, origen, documento_soporte, evento_id, creado_por) VALUES ('663156c4-afca-5ed8-9548-0b157c2b251b', 'DI', 'DI-2026-000003', DATE '2026-10-10', 'fe24eeb9-6c1d-562b-af34-318e4bd29720', 'Licencias de versiones liberadas (Acta LI-2026-04 y hoja de costos)', 'CONTABILIZADA', 'AUTOMATICO', 'LI-2026-04', 'd7b7ca60-970f-5bfa-9ce3-c17e7c43e811', (SELECT id FROM contador WHERE usuario = 'contador'));
INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber) VALUES
    ('750f6fff-5c76-5d78-8fdf-4960453d4c8c', '663156c4-afca-5ed8-9548-0b157c2b251b', 1, '110702', NULL, NULL, 1380.00, 0.00),
    ('62814a78-a320-5d66-a770-1bc92168183a', '663156c4-afca-5ed8-9548-0b157c2b251b', 2, '110701', NULL, NULL, 0.00, 1380.00);
UPDATE evento_contable SET partida_contabilizada_id = '663156c4-afca-5ed8-9548-0b157c2b251b' WHERE id = 'd7b7ca60-970f-5bfa-9ce3-c17e7c43e811';
INSERT INTO evento_contable (id, clave_unica, descripcion) VALUES ('65447d80-8a92-55c2-a070-55984f8644ab', 'SEED-ANEXOL-2026-DI-2026-000004', 'Clientes por licencias descargables (DTE-2026-316, activaciones LI-2026-03)');
INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto, estado, origen, documento_soporte, evento_id, creado_por) VALUES ('a2095cf8-5c86-54e9-86c2-db25e89e33b6', 'DI', 'DI-2026-000004', DATE '2026-10-15', 'fe24eeb9-6c1d-562b-af34-318e4bd29720', 'Clientes por licencias descargables (DTE-2026-316, activaciones LI-2026-03)', 'CONTABILIZADA', 'AUTOMATICO', 'DTE-2026-316, LI-2026-03', '65447d80-8a92-55c2-a070-55984f8644ab', (SELECT id FROM contador WHERE usuario = 'contador'));
INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber) VALUES
    ('3d93ece9-9f78-519d-a629-68de5352978a', 'a2095cf8-5c86-54e9-86c2-db25e89e33b6', 1, '110201', NULL, NULL, 2644.20, 0.00),
    ('62291c4b-b197-54a0-b3fb-aa4260697f94', 'a2095cf8-5c86-54e9-86c2-db25e89e33b6', 2, '410602', NULL, NULL, 0.00, 2340.00),
    ('d0a7b548-9292-5d0d-8b9b-914ecb8ade3a', 'a2095cf8-5c86-54e9-86c2-db25e89e33b6', 3, '210201', NULL, NULL, 0.00, 304.20),
    ('f12b292e-a77b-5fcb-905d-1f8e09e741fc', 'a2095cf8-5c86-54e9-86c2-db25e89e33b6', 4, '510302', NULL, NULL, 900.00, 0.00),
    ('125fe8e2-6bdd-5aaf-ad06-0b29b5740f61', 'a2095cf8-5c86-54e9-86c2-db25e89e33b6', 5, '110702', NULL, NULL, 0.00, 900.00);
UPDATE evento_contable SET partida_contabilizada_id = 'a2095cf8-5c86-54e9-86c2-db25e89e33b6' WHERE id = '65447d80-8a92-55c2-a070-55984f8644ab';
INSERT INTO documento_evento (id, documento_id, evento_id, tipo_evento, partida_id) VALUES ('466a393f-7081-5d0a-a78f-bb73cc24b04f', '9ba42c30-2a2c-5d2b-a517-f252ea141149', '65447d80-8a92-55c2-a070-55984f8644ab', 'VENTA', 'a2095cf8-5c86-54e9-86c2-db25e89e33b6');
INSERT INTO evento_contable (id, clave_unica, descripcion) VALUES ('5a05e997-e777-5da8-b591-ef9513894c37', 'SEED-ANEXOL-2026-DI-2026-000005', 'Servicios en proceso (Planilla de octubre de Proyectos y Desarrollo, horas y centros de costo aprobados; ISSS con base máxima de $1,000.00 por trabajador)');
INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto, estado, origen, documento_soporte, evento_id, creado_por) VALUES ('98e7f27d-8639-5347-8de8-ccee064f6bb3', 'DI', 'DI-2026-000005', DATE '2026-10-17', 'fe24eeb9-6c1d-562b-af34-318e4bd29720', 'Servicios en proceso (Planilla de octubre de Proyectos y Desarrollo, horas y centros de costo aprobados; ISSS con base máxima de $1,000.00 por trabajador)', 'CONTABILIZADA', 'AUTOMATICO', 'Planilla de octubre de Proyectos y Desarrollo, horas y centros de costo aprobados; ISSS con base máxima de $1,000.00 por trabajador', '5a05e997-e777-5da8-b591-ef9513894c37', (SELECT id FROM contador WHERE usuario = 'contador'));
INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber) VALUES
    ('e0fd711f-0c74-5b1b-ac16-88d2e5093d39', '98e7f27d-8639-5347-8de8-ccee064f6bb3', 1, '110501', NULL, NULL, 6740.50, 0.00),
    ('f86ba5c4-e7c4-564f-a277-633ce532afc4', '98e7f27d-8639-5347-8de8-ccee064f6bb3', 2, '510202', NULL, NULL, 640.97, 0.00),
    ('9f484d6b-8595-516d-9638-3f3fd1a96614', '98e7f27d-8639-5347-8de8-ccee064f6bb3', 3, '510401', NULL, NULL, 2517.28, 0.00),
    ('50df0dc6-6ec0-5f52-8f33-329edb4817c2', '98e7f27d-8639-5347-8de8-ccee064f6bb3', 4, '610201', NULL, NULL, 2044.38, 0.00),
    ('0456294c-43ca-5758-a055-a450016e0604', '98e7f27d-8639-5347-8de8-ccee064f6bb3', 5, '610101', NULL, NULL, 6080.62, 0.00),
    ('eb0541cf-6f4a-5600-8bce-e8836d5c7aa0', '98e7f27d-8639-5347-8de8-ccee064f6bb3', 6, '210301', NULL, NULL, 0.00, 13171.25),
    ('4161d540-ce9a-599c-8c19-5a4c3fe58401', '98e7f27d-8639-5347-8de8-ccee064f6bb3', 7, '210401', NULL, NULL, 0.00, 405.00),
    ('cc6b6d98-859b-59c3-bc07-ddd322816c74', '98e7f27d-8639-5347-8de8-ccee064f6bb3', 8, '210402', NULL, NULL, 0.00, 1012.50),
    ('10879053-091b-577d-9c3e-e8c194ef1053', '98e7f27d-8639-5347-8de8-ccee064f6bb3', 9, '210501', NULL, NULL, 0.00, 1123.75),
    ('c69b7937-5330-5636-bf48-52675b47e542', '98e7f27d-8639-5347-8de8-ccee064f6bb3', 10, '210502', NULL, NULL, 0.00, 1356.25),
    ('d623f868-927b-5dbc-a5ec-ffe9bd0ee6a4', '98e7f27d-8639-5347-8de8-ccee064f6bb3', 11, '210801', NULL, NULL, 0.00, 155.00),
    ('9efdacb5-77e9-53a3-9a41-07cbac2bdd8f', '98e7f27d-8639-5347-8de8-ccee064f6bb3', 12, '210205', NULL, NULL, 0.00, 800.00);
UPDATE evento_contable SET partida_contabilizada_id = '98e7f27d-8639-5347-8de8-ccee064f6bb3' WHERE id = '5a05e997-e777-5da8-b591-ef9513894c37';
INSERT INTO evento_contable (id, clave_unica, descripcion) VALUES ('b92005d0-2420-53de-b153-19a820d2ffeb', 'SEED-ANEXOL-2026-EG-2026-000001', 'Remuneraciones netas por pagar (Transferencia de planilla TP-2026-10)');
INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto, estado, origen, documento_soporte, evento_id, creado_por) VALUES ('eb94a275-52f8-5ddf-b7f0-5674578e86ed', 'EG', 'EG-2026-000001', DATE '2026-10-20', 'fe24eeb9-6c1d-562b-af34-318e4bd29720', 'Remuneraciones netas por pagar (Transferencia de planilla TP-2026-10)', 'CONTABILIZADA', 'AUTOMATICO', 'TP-2026-10', 'b92005d0-2420-53de-b153-19a820d2ffeb', (SELECT id FROM contador WHERE usuario = 'contador'));
INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber) VALUES
    ('4104cbd6-a9bd-5bb7-911d-f2bc99b7a0d0', 'eb94a275-52f8-5ddf-b7f0-5674578e86ed', 1, '210301', NULL, NULL, 13171.25, 0.00),
    ('ca74a1c7-9b88-5200-954e-8b0d21fb2a38', 'eb94a275-52f8-5ddf-b7f0-5674578e86ed', 2, '110102', NULL, NULL, 0.00, 13171.25);
UPDATE evento_contable SET partida_contabilizada_id = 'eb94a275-52f8-5ddf-b7f0-5674578e86ed' WHERE id = 'b92005d0-2420-53de-b153-19a820d2ffeb';
INSERT INTO evento_contable (id, clave_unica, descripcion) VALUES ('44105efb-6632-567b-8181-0968b90a11df', 'SEED-ANEXOL-2026-EG-2026-000002', 'ISSS laboral retenido (Planillas y recibos de entero)');
INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto, estado, origen, documento_soporte, evento_id, creado_por) VALUES ('9a0626e4-4791-5cee-8ff7-468308df2bea', 'EG', 'EG-2026-000002', DATE '2026-10-21', 'fe24eeb9-6c1d-562b-af34-318e4bd29720', 'ISSS laboral retenido (Planillas y recibos de entero)', 'CONTABILIZADA', 'AUTOMATICO', 'Planillas y recibos de entero', '44105efb-6632-567b-8181-0968b90a11df', (SELECT id FROM contador WHERE usuario = 'contador'));
INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber) VALUES
    ('5a7690f1-3ea1-5346-a63e-bd2a9038cd3f', '9a0626e4-4791-5cee-8ff7-468308df2bea', 1, '210401', NULL, NULL, 405.00, 0.00),
    ('cd8a607b-d06e-50cc-a770-f260ab0abb64', '9a0626e4-4791-5cee-8ff7-468308df2bea', 2, '210402', NULL, NULL, 1012.50, 0.00),
    ('a5dd7861-0794-5594-bb9f-8119a1c1db7f', '9a0626e4-4791-5cee-8ff7-468308df2bea', 3, '210501', NULL, NULL, 1123.75, 0.00),
    ('c9cf3e92-e9f9-5ad1-aca7-6619a7020a17', '9a0626e4-4791-5cee-8ff7-468308df2bea', 4, '210502', NULL, NULL, 1356.25, 0.00),
    ('0e748245-06af-533d-b7eb-81ec81814b03', '9a0626e4-4791-5cee-8ff7-468308df2bea', 5, '210801', NULL, NULL, 155.00, 0.00),
    ('ce769542-1e86-5c39-82f7-50a84ee447c7', '9a0626e4-4791-5cee-8ff7-468308df2bea', 6, '110102', NULL, NULL, 0.00, 4052.50);
UPDATE evento_contable SET partida_contabilizada_id = '9a0626e4-4791-5cee-8ff7-468308df2bea' WHERE id = '44105efb-6632-567b-8181-0968b90a11df';
INSERT INTO evento_contable (id, clave_unica, descripcion) VALUES ('d5f0b273-9dca-535b-aa52-c87690f93088', 'SEED-ANEXOL-2026-EG-2026-000003', 'Alojamiento de aplicaciones saas (DTE de nube y comprobante bancario)');
INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto, estado, origen, documento_soporte, evento_id, creado_por) VALUES ('72e2873c-a4e1-58d3-bebe-3c5d30d078af', 'EG', 'EG-2026-000003', DATE '2026-10-22', 'fe24eeb9-6c1d-562b-af34-318e4bd29720', 'Alojamiento de aplicaciones saas (DTE de nube y comprobante bancario)', 'CONTABILIZADA', 'AUTOMATICO', 'DTE de nube y comprobante bancario', 'd5f0b273-9dca-535b-aa52-c87690f93088', (SELECT id FROM contador WHERE usuario = 'contador'));
INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber) VALUES
    ('f0fd3440-e96c-5620-906e-b576e97fe136', '72e2873c-a4e1-58d3-bebe-3c5d30d078af', 1, '510201', NULL, NULL, 1200.00, 0.00),
    ('633df968-03ca-54e4-8267-d782d022c192', '72e2873c-a4e1-58d3-bebe-3c5d30d078af', 2, '110301', NULL, NULL, 156.00, 0.00),
    ('141dc4a0-e7dd-53b4-a898-bfffaf8bd5fb', '72e2873c-a4e1-58d3-bebe-3c5d30d078af', 3, '110102', NULL, NULL, 0.00, 1356.00);
UPDATE evento_contable SET partida_contabilizada_id = '72e2873c-a4e1-58d3-bebe-3c5d30d078af' WHERE id = 'd5f0b273-9dca-535b-aa52-c87690f93088';
INSERT INTO documento_evento (id, documento_id, evento_id, tipo_evento, partida_id) VALUES ('3a92abd1-dd08-5656-bb0b-4f2ac4e88f9e', '5876d0ff-d4c8-5089-b9aa-6b72425d02ea', 'd5f0b273-9dca-535b-aa52-c87690f93088', 'COMPRA', '72e2873c-a4e1-58d3-bebe-3c5d30d078af');
INSERT INTO evento_contable (id, clave_unica, descripcion) VALUES ('28bcb4c8-ff5c-5b27-8b91-8d760fd2e2e1', 'SEED-ANEXOL-2026-EG-2026-000004', 'Alquiler y servicios administrativos (CCF de arrendamiento y pago)');
INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto, estado, origen, documento_soporte, evento_id, creado_por) VALUES ('09415941-f77e-5bc0-92d3-ee436f58c2fc', 'EG', 'EG-2026-000004', DATE '2026-10-24', 'fe24eeb9-6c1d-562b-af34-318e4bd29720', 'Alquiler y servicios administrativos (CCF de arrendamiento y pago)', 'CONTABILIZADA', 'AUTOMATICO', 'CCF de arrendamiento y pago', '28bcb4c8-ff5c-5b27-8b91-8d760fd2e2e1', (SELECT id FROM contador WHERE usuario = 'contador'));
INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber) VALUES
    ('edb08ea1-f49e-5c7c-b726-5abe158998a7', '09415941-f77e-5bc0-92d3-ee436f58c2fc', 1, '610102', NULL, NULL, 1200.00, 0.00),
    ('cffd5e39-259f-5cb1-8c8a-3654415ecc66', '09415941-f77e-5bc0-92d3-ee436f58c2fc', 2, '110301', NULL, NULL, 156.00, 0.00),
    ('01965e33-41aa-5b8d-ab69-d435eeba54b3', '09415941-f77e-5bc0-92d3-ee436f58c2fc', 3, '110102', NULL, NULL, 0.00, 1356.00);
UPDATE evento_contable SET partida_contabilizada_id = '09415941-f77e-5bc0-92d3-ee436f58c2fc' WHERE id = '28bcb4c8-ff5c-5b27-8b91-8d760fd2e2e1';
INSERT INTO documento_evento (id, documento_id, evento_id, tipo_evento, partida_id) VALUES ('3eee3e7d-d8ad-54b8-9c95-01aa11e78b90', 'b9fcd66e-ba6a-50bc-96b6-0ac83deb1c6d', '28bcb4c8-ff5c-5b27-8b91-8d760fd2e2e1', 'COMPRA', '09415941-f77e-5bc0-92d3-ee436f58c2fc');
INSERT INTO evento_contable (id, clave_unica, descripcion) VALUES ('ffb4b54a-f8f8-5b8b-a4f6-6c1d6330319c', 'SEED-ANEXOL-2026-DI-2026-000006', 'Clientes por licencias descargables (DTE-2026-317, activaciones LI-2026-03)');
INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto, estado, origen, documento_soporte, evento_id, creado_por) VALUES ('94d1102c-d14d-59bd-834c-b31071c97465', 'DI', 'DI-2026-000006', DATE '2026-10-28', 'fe24eeb9-6c1d-562b-af34-318e4bd29720', 'Clientes por licencias descargables (DTE-2026-317, activaciones LI-2026-03)', 'CONTABILIZADA', 'AUTOMATICO', 'DTE-2026-317, LI-2026-03', 'ffb4b54a-f8f8-5b8b-a4f6-6c1d6330319c', (SELECT id FROM contador WHERE usuario = 'contador'));
INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber) VALUES
    ('bfce23be-f44d-500a-86a7-ec4d1962fd3a', '94d1102c-d14d-59bd-834c-b31071c97465', 1, '110201', NULL, NULL, 1762.80, 0.00),
    ('654d180a-61e8-5da1-acb8-91c8471d227e', '94d1102c-d14d-59bd-834c-b31071c97465', 2, '410602', NULL, NULL, 0.00, 1560.00),
    ('16c9fd83-ed86-5de1-8878-bdeadc1af273', '94d1102c-d14d-59bd-834c-b31071c97465', 3, '210201', NULL, NULL, 0.00, 202.80),
    ('740e2330-fa1b-57d3-96b8-09ed074558bc', '94d1102c-d14d-59bd-834c-b31071c97465', 4, '510302', NULL, NULL, 600.00, 0.00),
    ('948a2e03-5ce4-598e-bb14-b9984d6db8f0', '94d1102c-d14d-59bd-834c-b31071c97465', 5, '110702', NULL, NULL, 0.00, 600.00);
UPDATE evento_contable SET partida_contabilizada_id = '94d1102c-d14d-59bd-834c-b31071c97465' WHERE id = 'ffb4b54a-f8f8-5b8b-a4f6-6c1d6330319c';
INSERT INTO documento_evento (id, documento_id, evento_id, tipo_evento, partida_id) VALUES ('f4c8ff5c-c054-5a9f-b883-fb127fa3c471', '3cea1047-4daf-53dc-a624-6c313c4e2df7', 'ffb4b54a-f8f8-5b8b-a4f6-6c1d6330319c', 'VENTA', '94d1102c-d14d-59bd-834c-b31071c97465');
INSERT INTO evento_contable (id, clave_unica, descripcion) VALUES ('2cbc6ebb-f9ca-50c5-a2b9-37af56461d06', 'SEED-ANEXOL-2026-IN-2026-000003', 'Bancos (DTE-2026-318, cobertura nov. 2026 a abr. 2027)');
INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto, estado, origen, documento_soporte, evento_id, creado_por) VALUES ('24ee1525-6452-5eed-ad00-a19737f72500', 'IN', 'IN-2026-000003', DATE '2026-10-30', 'fe24eeb9-6c1d-562b-af34-318e4bd29720', 'Bancos (DTE-2026-318, cobertura nov. 2026 a abr. 2027)', 'CONTABILIZADA', 'AUTOMATICO', 'DTE-2026-318', '2cbc6ebb-f9ca-50c5-a2b9-37af56461d06', (SELECT id FROM contador WHERE usuario = 'contador'));
INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber) VALUES
    ('8ca70223-688f-5949-826e-ddfba1c3596c', '24ee1525-6452-5eed-ad00-a19737f72500', 1, '110102', NULL, NULL, 4068.00, 0.00),
    ('ccfdf50c-5540-53e3-b73e-69d68314faf5', '24ee1525-6452-5eed-ad00-a19737f72500', 2, '210704', NULL, NULL, 0.00, 3600.00),
    ('dc48310b-6434-5613-92d8-362e3f13dffb', '24ee1525-6452-5eed-ad00-a19737f72500', 3, '210201', NULL, NULL, 0.00, 468.00);
UPDATE evento_contable SET partida_contabilizada_id = '24ee1525-6452-5eed-ad00-a19737f72500' WHERE id = '2cbc6ebb-f9ca-50c5-a2b9-37af56461d06';
INSERT INTO documento_evento (id, documento_id, evento_id, tipo_evento, partida_id) VALUES ('bcf94cad-f886-5b1a-9c06-1dff1c9a422f', 'a1e388ce-65e9-5f7c-b739-fac64df85400', '2cbc6ebb-f9ca-50c5-a2b9-37af56461d06', 'ANTICIPO', '24ee1525-6452-5eed-ad00-a19737f72500');
INSERT INTO evento_contable (id, clave_unica, descripcion) VALUES ('cd3044fa-35e4-5123-8cd5-7e9128078519', 'SEED-ANEXOL-2026-DI-2026-000007', 'Mano de obra directa de proyectos (hoja de costo OS-2026-014 y acta de aceptación)');
INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto, estado, origen, documento_soporte, evento_id, creado_por) VALUES ('adeff2dc-37b4-5b8d-b789-bdd70a157c55', 'DI', 'DI-2026-000007', DATE '2026-10-31', 'fe24eeb9-6c1d-562b-af34-318e4bd29720', 'Mano de obra directa de proyectos (hoja de costo OS-2026-014 y acta de aceptación)', 'CONTABILIZADA', 'AUTOMATICO', 'OS-2026-014', 'cd3044fa-35e4-5123-8cd5-7e9128078519', (SELECT id FROM contador WHERE usuario = 'contador'));
INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber) VALUES
    ('b78a7317-9c81-5a11-a981-8b78a24d8d52', 'adeff2dc-37b4-5b8d-b789-bdd70a157c55', 1, '510101', 'OS-2026-014', NULL, 3384.00, 0.00),
    ('8f47adb4-0579-51f5-b7ef-d30f5e830613', 'adeff2dc-37b4-5b8d-b789-bdd70a157c55', 2, '510102', 'OS-2026-014', NULL, 900.00, 0.00),
    ('2df5b34c-2a1f-594a-85c7-433a247d180d', 'adeff2dc-37b4-5b8d-b789-bdd70a157c55', 3, '510103', 'OS-2026-014', NULL, 846.00, 0.00),
    ('a528e15b-2a94-5a9f-957d-17c5ddb82253', 'adeff2dc-37b4-5b8d-b789-bdd70a157c55', 4, '110501', 'OS-2026-014', NULL, 0.00, 3384.00),
    ('2a7013ea-2828-5b64-9bfb-3393dda414dc', 'adeff2dc-37b4-5b8d-b789-bdd70a157c55', 5, '110502', 'OS-2026-014', NULL, 0.00, 900.00),
    ('16df6999-8e1f-56a0-a96d-b0ee73aba208', 'adeff2dc-37b4-5b8d-b789-bdd70a157c55', 6, '110503', 'OS-2026-014', NULL, 0.00, 846.00);
UPDATE evento_contable SET partida_contabilizada_id = 'adeff2dc-37b4-5b8d-b789-bdd70a157c55' WHERE id = 'cd3044fa-35e4-5123-8cd5-7e9128078519';
INSERT INTO evento_contable (id, clave_unica, descripcion) VALUES ('a90ecc59-149c-51b5-a0bb-305651e6bbe3', 'SEED-ANEXOL-2026-AJ-2026-000001', 'Depreciación administrativa (Cédula de activos fijos de octubre; vidas útiles de IX.Q (3, 4 y 5 años))');
INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto, estado, origen, documento_soporte, evento_id, creado_por) VALUES ('c9cd511a-6b0b-5ed0-99fe-e6dbc6d29332', 'AJ', 'AJ-2026-000001', DATE '2026-10-31', 'fe24eeb9-6c1d-562b-af34-318e4bd29720', 'Depreciación administrativa (Cédula de activos fijos de octubre; vidas útiles de IX.Q (3, 4 y 5 años))', 'CONTABILIZADA', 'MANUAL', 'Cédula de activos fijos de octubre; vidas útiles de IX.Q (3, 4 y 5 años)', 'a90ecc59-149c-51b5-a0bb-305651e6bbe3', (SELECT id FROM contador WHERE usuario = 'contador'));
INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber) VALUES
    ('fe1249cf-fde7-5217-80f3-aedc8f927971', 'c9cd511a-6b0b-5ed0-99fe-e6dbc6d29332', 1, '610104', NULL, NULL, 787.50, 0.00),
    ('df985cdf-b370-5b88-8b78-4e666425a0ce', 'c9cd511a-6b0b-5ed0-99fe-e6dbc6d29332', 2, '520103', NULL, NULL, 118.75, 0.00),
    ('bd84bc89-d281-5b27-a03c-80ed8591a008', 'c9cd511a-6b0b-5ed0-99fe-e6dbc6d29332', 3, '120201', NULL, NULL, 0.00, 687.50),
    ('10d6c0e0-8814-5dac-a806-90a60b559509', 'c9cd511a-6b0b-5ed0-99fe-e6dbc6d29332', 4, '120202', NULL, NULL, 0.00, 118.75),
    ('d712414b-1fac-5c9a-b828-686a1f28d6d6', 'c9cd511a-6b0b-5ed0-99fe-e6dbc6d29332', 5, '120203', NULL, NULL, 0.00, 100.00);
UPDATE evento_contable SET partida_contabilizada_id = 'c9cd511a-6b0b-5ed0-99fe-e6dbc6d29332' WHERE id = 'a90ecc59-149c-51b5-a0bb-305651e6bbe3';
INSERT INTO evento_contable (id, clave_unica, descripcion) VALUES ('3a0bbb86-db17-5f86-87cf-f7e2fc6335d5', 'SEED-ANEXOL-2026-AJ-2026-000002', 'IVA débito fiscal (Libro de IVA; crédito aplicado de $514.80 = $312.00 de octubre + $202.80 de períodos anteriores)');
INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto, estado, origen, documento_soporte, evento_id, creado_por) VALUES ('115591aa-b1df-5ab0-ac23-c8b9831b9402', 'AJ', 'AJ-2026-000002', DATE '2026-10-31', 'fe24eeb9-6c1d-562b-af34-318e4bd29720', 'IVA débito fiscal (Libro de IVA; crédito aplicado de $514.80 = $312.00 de octubre + $202.80 de períodos anteriores)', 'CONTABILIZADA', 'MANUAL', 'Libro de IVA; crédito aplicado de $514.80 = $312.00 de octubre + $202.80 de períodos anteriores', '3a0bbb86-db17-5f86-87cf-f7e2fc6335d5', (SELECT id FROM contador WHERE usuario = 'contador'));
INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber) VALUES
    ('0b31617f-2bca-5c2f-9920-4b25ddeb8b94', '115591aa-b1df-5ab0-ac23-c8b9831b9402', 1, '210201', NULL, NULL, 2359.50, 0.00),
    ('360c8922-ddb3-56ca-8d22-786ea70f3294', '115591aa-b1df-5ab0-ac23-c8b9831b9402', 2, '110301', NULL, NULL, 0.00, 514.80),
    ('66f7600c-31c2-5841-ae34-f9d10dc940e5', '115591aa-b1df-5ab0-ac23-c8b9831b9402', 3, '210202', NULL, NULL, 0.00, 1844.70);
UPDATE evento_contable SET partida_contabilizada_id = '115591aa-b1df-5ab0-ac23-c8b9831b9402' WHERE id = '3a0bbb86-db17-5f86-87cf-f7e2fc6335d5';
INSERT INTO evento_contable (id, clave_unica, descripcion) VALUES ('7db76bc2-9af1-5238-bc74-02ff899d2b36', 'SEED-ANEXOL-2026-AJ-2026-000003', 'Gasto por ISR corriente (Conciliación fiscal interna acumulada)');
INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto, estado, origen, documento_soporte, evento_id, creado_por) VALUES ('72c86396-21a4-5a5e-aa19-449555c80f0d', 'AJ', 'AJ-2026-000003', DATE '2026-10-31', 'fe24eeb9-6c1d-562b-af34-318e4bd29720', 'Gasto por ISR corriente (Conciliación fiscal interna acumulada)', 'CONTABILIZADA', 'MANUAL', 'Conciliación fiscal interna acumulada', '7db76bc2-9af1-5238-bc74-02ff899d2b36', (SELECT id FROM contador WHERE usuario = 'contador'));
INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber) VALUES
    ('f8807145-fad3-51de-beed-dc32b95720f1', '72c86396-21a4-5a5e-aa19-449555c80f0d', 1, '710201', NULL, NULL, 12133.88, 0.00),
    ('3ffded73-1852-5d04-b369-9b14f0a03609', '72c86396-21a4-5a5e-aa19-449555c80f0d', 2, '210204', NULL, NULL, 0.00, 12133.88);
UPDATE evento_contable SET partida_contabilizada_id = '72c86396-21a4-5a5e-aa19-449555c80f0d' WHERE id = '7db76bc2-9af1-5238-bc74-02ff899d2b36';
INSERT INTO evento_contable (id, clave_unica, descripcion) VALUES ('9f21c6f0-97d2-5c04-97f5-824f5f4f7eb0', 'SEED-ANEXOL-2026-AJ-2026-000004', 'ISR corriente por pagar (Aplicación parcial de créditos de ISR con constancia; ver nota de ISR)');
INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto, estado, origen, documento_soporte, evento_id, creado_por) VALUES ('93cd5a53-c45a-5161-8de9-2e6d4e9e124e', 'AJ', 'AJ-2026-000004', DATE '2026-10-31', 'fe24eeb9-6c1d-562b-af34-318e4bd29720', 'ISR corriente por pagar (Aplicación parcial de créditos de ISR con constancia; ver nota de ISR)', 'CONTABILIZADA', 'MANUAL', 'Aplicación parcial de créditos de ISR con constancia; ver nota de ISR', '9f21c6f0-97d2-5c04-97f5-824f5f4f7eb0', (SELECT id FROM contador WHERE usuario = 'contador'));
INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber) VALUES
    ('8515bc6b-3c93-5d46-95a1-5432d6eb3d64', '93cd5a53-c45a-5161-8de9-2e6d4e9e124e', 1, '210204', NULL, NULL, 8350.00, 0.00),
    ('d3dd2b24-f27c-5ad5-a8c0-94a6febb1816', '93cd5a53-c45a-5161-8de9-2e6d4e9e124e', 2, '110302', NULL, NULL, 0.00, 6230.00),
    ('62a40672-69c1-5de1-bd90-5e32047e2f40', '93cd5a53-c45a-5161-8de9-2e6d4e9e124e', 3, '110303', NULL, NULL, 0.00, 2120.00);
UPDATE evento_contable SET partida_contabilizada_id = '93cd5a53-c45a-5161-8de9-2e6d4e9e124e' WHERE id = '9f21c6f0-97d2-5c04-97f5-824f5f4f7eb0';
INSERT INTO aplicacion_pago (id, documento_origen_id, documento_pago_id, partida_id, importe, fecha, concepto) VALUES ('9bf85ac4-3836-51f7-aecc-bc12153faed7', '718cf7c3-c6e0-5b8b-aa0e-516c052dfe7e', '9c1d97dd-a438-5048-b90a-061f54486ce2', '573f1df1-5f2a-5906-84ee-8fd2f1ae3c94', 4746.00, DATE '2026-10-03', 'Aplicación parcial de BC-2026-188 a DTE-2026-278');
INSERT INTO aplicacion_pago (id, documento_origen_id, documento_pago_id, partida_id, importe, fecha, concepto) VALUES ('1038148d-28f9-52fb-bc7b-b04aa9327bf0', 'eedc55c1-b6f5-524b-b9fb-833fee167855', 'd2c6b2cd-d738-5985-b655-bd517d50dbc3', '106e5da1-e7bb-5cc6-95f0-58b726716a62', 2542.50, DATE '2026-10-08', 'Cobro de DTE-2026-315 con BC-2026-191');
INSERT INTO folio_secuencia (anio, tipo, ultimo) VALUES (2026, 'AJ', 4);
INSERT INTO folio_secuencia (anio, tipo, ultimo) VALUES (2026, 'AP', 1);
INSERT INTO folio_secuencia (anio, tipo, ultimo) VALUES (2026, 'DI', 7);
INSERT INTO folio_secuencia (anio, tipo, ultimo) VALUES (2026, 'EG', 4);
INSERT INTO folio_secuencia (anio, tipo, ultimo) VALUES (2026, 'IN', 3);
INSERT INTO lote_licencia (id, version_id, codigo_lote, unidades_estimadas, costo_total, costo_unitario, unidades_disponibles, fecha_liberacion, partida_id) VALUES ('48b315ec-b4fe-5215-843b-466793c19e61', (SELECT pv.id FROM producto_version pv JOIN producto p ON p.id = pv.producto_id WHERE p.codigo = 'COL-INV' AND pv.version_label = '2.0'), 'LI-2026-03', 18, 1800.00, 100.00, 3, DATE '2026-09-15', NULL);
INSERT INTO licencia (id, lote_id, numero, estado, cliente_id, contrato_id, documento_id, fecha_entrega, fecha_activacion, costo_unitario) VALUES
    ('1751ff20-98ff-55a1-81f8-2e6aacf5885a', '48b315ec-b4fe-5215-843b-466793c19e61', 1, 'ENTREGADA', '4d479225-d6b8-5ac3-8c14-983396d88fa7', NULL, '9ba42c30-2a2c-5d2b-a517-f252ea141149', DATE '2026-10-15', DATE '2026-10-15', 100.00),
    ('205b6b1f-c018-5480-bbe1-715e7105887c', '48b315ec-b4fe-5215-843b-466793c19e61', 2, 'ENTREGADA', '4d479225-d6b8-5ac3-8c14-983396d88fa7', NULL, '9ba42c30-2a2c-5d2b-a517-f252ea141149', DATE '2026-10-15', DATE '2026-10-15', 100.00),
    ('a82472da-6fde-5a10-9955-ead216d9d3b3', '48b315ec-b4fe-5215-843b-466793c19e61', 3, 'ENTREGADA', '4d479225-d6b8-5ac3-8c14-983396d88fa7', NULL, '9ba42c30-2a2c-5d2b-a517-f252ea141149', DATE '2026-10-15', DATE '2026-10-15', 100.00),
    ('4027e5eb-3f76-5049-b47f-d7762a3811ff', '48b315ec-b4fe-5215-843b-466793c19e61', 4, 'ENTREGADA', '4d479225-d6b8-5ac3-8c14-983396d88fa7', NULL, '9ba42c30-2a2c-5d2b-a517-f252ea141149', DATE '2026-10-15', DATE '2026-10-15', 100.00),
    ('6a682074-f434-50a1-b937-782437ff59a3', '48b315ec-b4fe-5215-843b-466793c19e61', 5, 'ENTREGADA', '4d479225-d6b8-5ac3-8c14-983396d88fa7', NULL, '9ba42c30-2a2c-5d2b-a517-f252ea141149', DATE '2026-10-15', DATE '2026-10-15', 100.00),
    ('de26e399-4ff9-5b00-8d9b-7b567d8ea019', '48b315ec-b4fe-5215-843b-466793c19e61', 6, 'ENTREGADA', '4d479225-d6b8-5ac3-8c14-983396d88fa7', NULL, '9ba42c30-2a2c-5d2b-a517-f252ea141149', DATE '2026-10-15', DATE '2026-10-15', 100.00),
    ('0b6f082b-ed5a-5ab6-8f03-fcb6066a9b4d', '48b315ec-b4fe-5215-843b-466793c19e61', 7, 'ENTREGADA', '4d479225-d6b8-5ac3-8c14-983396d88fa7', NULL, '9ba42c30-2a2c-5d2b-a517-f252ea141149', DATE '2026-10-15', DATE '2026-10-15', 100.00),
    ('556d3078-7d0c-5a9b-b861-6aadb3c6161d', '48b315ec-b4fe-5215-843b-466793c19e61', 8, 'ENTREGADA', '4d479225-d6b8-5ac3-8c14-983396d88fa7', NULL, '9ba42c30-2a2c-5d2b-a517-f252ea141149', DATE '2026-10-15', DATE '2026-10-15', 100.00),
    ('bb5ffdf8-b7c9-587e-8b3d-789560436caa', '48b315ec-b4fe-5215-843b-466793c19e61', 9, 'ENTREGADA', '4d479225-d6b8-5ac3-8c14-983396d88fa7', NULL, '9ba42c30-2a2c-5d2b-a517-f252ea141149', DATE '2026-10-15', DATE '2026-10-15', 100.00),
    ('8575dca4-1d84-58a9-afcc-91f40d353460', '48b315ec-b4fe-5215-843b-466793c19e61', 10, 'ENTREGADA', '1d75e66b-5bfa-5492-a8cc-a79d10e9d062', NULL, '3cea1047-4daf-53dc-a624-6c313c4e2df7', DATE '2026-10-28', DATE '2026-10-28', 100.00),
    ('33359e34-5632-5d84-900b-768067b7ca23', '48b315ec-b4fe-5215-843b-466793c19e61', 11, 'ENTREGADA', '1d75e66b-5bfa-5492-a8cc-a79d10e9d062', NULL, '3cea1047-4daf-53dc-a624-6c313c4e2df7', DATE '2026-10-28', DATE '2026-10-28', 100.00),
    ('381ba0c8-4eaf-53c3-9a9a-62fbf22a8072', '48b315ec-b4fe-5215-843b-466793c19e61', 12, 'ENTREGADA', '1d75e66b-5bfa-5492-a8cc-a79d10e9d062', NULL, '3cea1047-4daf-53dc-a624-6c313c4e2df7', DATE '2026-10-28', DATE '2026-10-28', 100.00),
    ('ab6f22d6-57a0-5aa5-9d69-07d54c8cb0e4', '48b315ec-b4fe-5215-843b-466793c19e61', 13, 'ENTREGADA', '1d75e66b-5bfa-5492-a8cc-a79d10e9d062', NULL, '3cea1047-4daf-53dc-a624-6c313c4e2df7', DATE '2026-10-28', DATE '2026-10-28', 100.00),
    ('c3dff5b0-a155-5e73-8ced-8a076dea7f89', '48b315ec-b4fe-5215-843b-466793c19e61', 14, 'ENTREGADA', '1d75e66b-5bfa-5492-a8cc-a79d10e9d062', NULL, '3cea1047-4daf-53dc-a624-6c313c4e2df7', DATE '2026-10-28', DATE '2026-10-28', 100.00),
    ('c5692577-9e8f-5b66-9605-a3b7473b7573', '48b315ec-b4fe-5215-843b-466793c19e61', 15, 'ENTREGADA', '1d75e66b-5bfa-5492-a8cc-a79d10e9d062', NULL, '3cea1047-4daf-53dc-a624-6c313c4e2df7', DATE '2026-10-28', DATE '2026-10-28', 100.00),
    ('ce6bb641-41b6-5a3e-afe7-25eca296b566', '48b315ec-b4fe-5215-843b-466793c19e61', 16, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 100.00),
    ('f786d513-d5e1-5199-a449-b5e55ec1f6ed', '48b315ec-b4fe-5215-843b-466793c19e61', 17, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 100.00),
    ('27218240-ebb3-50c7-8347-a16247fd3551', '48b315ec-b4fe-5215-843b-466793c19e61', 18, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 100.00);
INSERT INTO lote_licencia (id, version_id, codigo_lote, unidades_estimadas, costo_total, costo_unitario, unidades_disponibles, fecha_liberacion, partida_id) VALUES ('fa4dedb7-f060-540e-95c5-e97813adc27f', (SELECT pv.id FROM producto_version pv JOIN producto p ON p.id = pv.producto_id WHERE p.codigo = 'COL-INV' AND pv.version_label = '2.0'), 'LI-2026-04', 12, 1380.00, 115.00, 12, DATE '2026-10-10', '663156c4-afca-5ed8-9548-0b157c2b251b');
INSERT INTO licencia (id, lote_id, numero, estado, cliente_id, contrato_id, documento_id, fecha_entrega, fecha_activacion, costo_unitario) VALUES
    ('f1e819e4-6bfe-568e-9755-5e6958863725', 'fa4dedb7-f060-540e-95c5-e97813adc27f', 1, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 115.00),
    ('5b71dd94-70f2-55f6-997c-f8a465b1ef9c', 'fa4dedb7-f060-540e-95c5-e97813adc27f', 2, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 115.00),
    ('1e898f37-e7cf-5c9b-8ffe-fa67aa1a7e08', 'fa4dedb7-f060-540e-95c5-e97813adc27f', 3, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 115.00),
    ('7150015b-7560-598f-b8fa-8c1d4047ffc9', 'fa4dedb7-f060-540e-95c5-e97813adc27f', 4, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 115.00),
    ('2191b980-54d6-5bf3-8fc3-9cce631af51a', 'fa4dedb7-f060-540e-95c5-e97813adc27f', 5, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 115.00),
    ('930f8c80-9539-5087-b58b-25141f1b48e8', 'fa4dedb7-f060-540e-95c5-e97813adc27f', 6, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 115.00),
    ('62c13fc4-0760-5088-b92d-7ad668d8b51c', 'fa4dedb7-f060-540e-95c5-e97813adc27f', 7, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 115.00),
    ('edb87149-53f7-59b7-ba90-b3a194640990', 'fa4dedb7-f060-540e-95c5-e97813adc27f', 8, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 115.00),
    ('46eecfe0-d51c-57b8-ad8e-052117dfbf4a', 'fa4dedb7-f060-540e-95c5-e97813adc27f', 9, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 115.00),
    ('5cfa946c-f9fc-5974-9b6d-af07bfb8e6f0', 'fa4dedb7-f060-540e-95c5-e97813adc27f', 10, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 115.00),
    ('802756e5-262a-5d33-9e35-7cbb74abf98d', 'fa4dedb7-f060-540e-95c5-e97813adc27f', 11, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 115.00),
    ('94a08849-dfd2-5d36-a8ff-f583eeca5bc7', 'fa4dedb7-f060-540e-95c5-e97813adc27f', 12, 'DISPONIBLE', NULL, NULL, NULL, NULL, NULL, 115.00);
INSERT INTO orden_servicio (id, codigo, cliente_id, descripcion, presupuesto, precio_venta, estado) VALUES ('95178e2b-c67e-5ca0-a9c3-681470c0a3c2', 'OS-2026-014', '1088c0f3-1aba-5cc0-9609-77ebae2e310b', 'Desarrollo a medida; hito aceptado en octubre de 2026', 5130.00, 8400.00, 'ENTREGADA');
INSERT INTO orden_consumo (id, orden_id, tipo, descripcion, cantidad, tarifa, importe, periodo_id, fecha, partida_id) VALUES ('382b35da-1bb9-5f47-9439-66a9c31dd2ab', '95178e2b-c67e-5ca0-a9c3-681470c0a3c2', 'MOD', 'Mano de obra directa: 282 horas', 282.00, 12.00, 3384.00, 'fe24eeb9-6c1d-562b-af34-318e4bd29720', DATE '2026-10-31', 'adeff2dc-37b4-5b8d-b789-bdd70a157c55');
INSERT INTO orden_consumo (id, orden_id, tipo, descripcion, cantidad, tarifa, importe, periodo_id, fecha, partida_id) VALUES ('76b35784-9149-557f-a9b9-8e110b465b53', '95178e2b-c67e-5ca0-a9c3-681470c0a3c2', 'SERVICIO', 'Servicios directos del proyecto', 1.00, 900.00, 900.00, 'fe24eeb9-6c1d-562b-af34-318e4bd29720', DATE '2026-10-31', 'adeff2dc-37b4-5b8d-b789-bdd70a157c55');
INSERT INTO orden_consumo (id, orden_id, tipo, descripcion, cantidad, tarifa, importe, periodo_id, fecha, partida_id) VALUES ('5c1e3744-d07d-549e-849e-fd8a7e3da8b6', '95178e2b-c67e-5ca0-a9c3-681470c0a3c2', 'CIF', 'CIF aplicado a $3.00 por hora', 282.00, 3.00, 846.00, 'fe24eeb9-6c1d-562b-af34-318e4bd29720', DATE '2026-10-31', 'adeff2dc-37b4-5b8d-b789-bdd70a157c55');
INSERT INTO activo_fijo (id, codigo, nombre, cuenta_activo, cuenta_dep_acum, cuenta_gasto, costo, vida_meses, fecha_uso, valor_residual, depreciacion_acum, activo) VALUES ('119e1057-ae94-56e9-aacd-368278034a94', 'AF-COMPUTADORAS', 'Computadoras y periféricos', '120101', '120201', '610104', 24750.00, 36, DATE '2024-03-01', 0.00, 10557.50, TRUE);
INSERT INTO activo_fijo (id, codigo, nombre, cuenta_activo, cuenta_dep_acum, cuenta_gasto, costo, vida_meses, fecha_uso, valor_residual, depreciacion_acum, activo) VALUES ('9b1ee7b4-8fde-5330-a356-8cbd437700f8', 'AF-SERVIDORES', 'Servidores y equipos de red', '120102', '120202', '520103', 5700.00, 48, DATE '2024-03-01', 0.00, 2668.75, TRUE);
INSERT INTO activo_fijo (id, codigo, nombre, cuenta_activo, cuenta_dep_acum, cuenta_gasto, costo, vida_meses, fecha_uso, valor_residual, depreciacion_acum, activo) VALUES ('ce4de4de-fe53-5057-9ee1-2d9f52970cec', 'AF-MOBILIARIO', 'Mobiliario y equipo de oficina', '120103', '120203', '610104', 6000.00, 60, DATE '2024-03-01', 0.00, 3100.00, TRUE);
INSERT INTO depreciacion_cuota (id, activo_id, periodo_id, importe, partida_id) VALUES ('bc3f19fe-1290-5fb8-986e-d6ab3ac80f51', '119e1057-ae94-56e9-aacd-368278034a94', 'fe24eeb9-6c1d-562b-af34-318e4bd29720', 687.50, 'c9cd511a-6b0b-5ed0-99fe-e6dbc6d29332');
INSERT INTO depreciacion_cuota (id, activo_id, periodo_id, importe, partida_id) VALUES ('555eaaa2-1c84-505f-881a-0ab9cb0a4583', '9b1ee7b4-8fde-5330-a356-8cbd437700f8', 'fe24eeb9-6c1d-562b-af34-318e4bd29720', 118.75, 'c9cd511a-6b0b-5ed0-99fe-e6dbc6d29332');
INSERT INTO depreciacion_cuota (id, activo_id, periodo_id, importe, partida_id) VALUES ('ced095d5-d99e-59c8-a9f3-9d216c648af1', 'ce4de4de-fe53-5057-9ee1-2d9f52970cec', 'fe24eeb9-6c1d-562b-af34-318e4bd29720', 100.00, 'c9cd511a-6b0b-5ed0-99fe-e6dbc6d29332');
INSERT INTO activo_intangible (id, codigo, nombre, cuenta_activo, cuenta_amort_acum, cuenta_gasto, costo, vida_meses, fecha_uso, valor_residual, amortizacion_acum, activo) VALUES ('ea1f6dfd-84dd-536e-b143-7bf8f8e84fab', 'AI-SOFTWARE', 'Software adquirido de uso interno', '120301', '120401', '610105', 7200.00, 36, DATE '2026-02-01', 0.00, 1800.00, TRUE);
INSERT INTO planilla_periodo (id, periodo_id, fecha_contable, empleados, salario_nominal, tasa_isss_laboral, tasa_previsional_laboral, isr_retenido, otras_deducciones, isss_laboral, isss_patronal, previsional_laboral, previsional_patronal, incaf, liquido_por_pagar, costo_patronal_total, tasa_isss_patronal, tasa_previsional_patronal, tasa_incaf, umbral_incaf_empleados, centro_costo, clave_evento, partida_id) VALUES ('2aceff68-faeb-509e-bc95-387380c42a9b', 'fe24eeb9-6c1d-562b-af34-318e4bd29720', DATE '2026-10-17', 15, 15500.00, 0.03, 0.07, 800.00, 0.00, 405.00, 1012.50, 1123.75, 1356.25, 155.00, 13171.25, 18023.75, 0.08, 0.09, 0.01, 10, 'CC-01', 'SEED-ANEXOL-2026-PLANILLA-2026-10', '98e7f27d-8639-5347-8de8-ccee064f6bb3');
UPDATE periodo SET estado = 'CERRADO' WHERE ejercicio_id = '0ae7578f-80ac-5463-93d5-a9da5d72b6ca' AND mes IN (1, 2, 3, 4, 5, 6, 7, 8, 9);
COMMIT;
