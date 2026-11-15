-- Reset total de Colibrí: TRUNCATE ... CASCADE de TODAS las tablas excepto
--   * contador               (se conservan los usuarios `contador` y `soporte`, con sus credenciales)
--   * flyway_schema_history  (historial de migraciones)
--
-- Uso (cierre la aplicación antes; ACCESS EXCLUSIVE sobre todas las tablas):
--   psql -h 127.0.0.1 -p 5434 -U colibri -d colibri -v confirmar=SI -f scripts/db/reset_total.sql
--   psql ... -f scripts/db/seed_00_referencia.sql
--   psql ... -f scripts/db/seed_anexo_l.sql
--   psql ... -f scripts/db/verificar_anexo_l.sql
\encoding UTF8
\if :{?confirmar}
\else
    \echo 'Falta -v confirmar=SI: operación destructiva cancelada.'
    \quit
\endif
SELECT :'confirmar' = 'SI' AS confirmado \gset
\if :confirmado
\else
    \echo 'confirmar debe ser SI: operación destructiva cancelada.'
    \quit
\endif

BEGIN;

DO $$
DECLARE
    clasificadas text[] := ARRAY[
        'empresa','ejercicio','periodo','cuenta','evento_contable','partida','partida_detalle',
        'folio_secuencia','bitacora','sesion_lock','catalogo_version','tercero','contrato','documento',
        'documento_evento','parametro_iva','aplicacion_pago','producto','producto_version','lote_licencia',
        'licencia','orden_servicio','orden_consumo','activo_fijo','depreciacion_cuota','parametro_sistema',
        'parametro_fiscal','posting_profile','centro_costo','plan_servicio','saas_usuario_mes','plan_consumo',
        'entorno_tecnico','log_tecnico','activo_intangible','amortizacion_cuota','estimacion_cartera_cuota',
        'deterioro_lote_licencia','cif_presupuesto_periodo','proceso_costo_periodo','planilla_periodo',
        'cif_aplicacion_orden','cif_cierre_periodo','plantilla_partida','plantilla_detalle',
        'contador','flyway_schema_history'];
    sin_clasificar text;
BEGIN
    IF current_database() NOT IN ('colibri', 'colibri_test', 'colibri_scratch') THEN
        RAISE EXCEPTION 'Base de datos no permitida para el reset: %', current_database();
    END IF;
    IF (SELECT count(*) FROM contador WHERE usuario IN ('contador', 'soporte')) <> 2 THEN
        RAISE EXCEPTION 'Deben existir los usuarios contador y soporte antes del reset';
    END IF;
    IF (SELECT count(*) FROM pg_stat_activity
         WHERE datname = current_database() AND pid <> pg_backend_pid() AND state <> 'idle') > 0 THEN
        RAISE EXCEPTION 'Hay otras sesiones activas en la base; cierre la aplicación';
    END IF;
    SELECT string_agg(table_name, ', ') INTO sin_clasificar
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_type = 'BASE TABLE' AND table_name <> ALL (clasificadas);
    IF sin_clasificar IS NOT NULL THEN
        RAISE EXCEPTION 'Tablas nuevas sin clasificar en reset_total.sql: %', sin_clasificar;
    END IF;
END $$;

TRUNCATE TABLE
    empresa, ejercicio, periodo, cuenta, evento_contable, partida, partida_detalle,
    folio_secuencia, bitacora, sesion_lock, catalogo_version, tercero, contrato, documento,
    documento_evento, parametro_iva, aplicacion_pago, producto, producto_version, lote_licencia,
    licencia, orden_servicio, orden_consumo, activo_fijo, depreciacion_cuota, parametro_sistema,
    parametro_fiscal, posting_profile, centro_costo, plan_servicio, saas_usuario_mes, plan_consumo,
    entorno_tecnico, log_tecnico, activo_intangible, amortizacion_cuota, estimacion_cartera_cuota,
    deterioro_lote_licencia, cif_presupuesto_periodo, proceso_costo_periodo, planilla_periodo,
    cif_aplicacion_orden, cif_cierre_periodo, plantilla_partida, plantilla_detalle
    RESTART IDENTITY CASCADE;

DO $$
BEGIN
    IF (SELECT count(*) FROM contador WHERE usuario IN ('contador', 'soporte')) <> 2 THEN
        RAISE EXCEPTION 'El reset afectó a contador; se revierte';
    END IF;
END $$;

COMMIT;
