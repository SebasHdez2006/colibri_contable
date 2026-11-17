-- Generado por generar_seed_sql.py. Falla (RAISE EXCEPTION) si el seed no coincide con el informe.
\encoding UTF8
\set ON_ERROR_STOP on
DO $$
DECLARE
    n integer;
    v numeric;
BEGIN
    SELECT COALESCE(SUM(pd.debe),0) - COALESCE(SUM(pd.haber),0) INTO v
      FROM partida_detalle pd JOIN partida p ON p.id = pd.partida_id WHERE p.estado = 'CONTABILIZADA';
    IF v <> 0 THEN RAISE EXCEPTION 'Diario global descuadrado: %', v; END IF;

    SELECT count(*) INTO n FROM (
        WITH esperado(codigo, saldo) AS (VALUES
    ('110102', 229356.54),
    ('110201', 4698.54),
    ('110202', 51930.56),
    ('110203', 10844.05),
    ('110204', 27250.52),
    ('110401', 6400.00),
    ('110501', 8400.00),
    ('110502', 3773.10),
    ('110503', 2100.00),
    ('110601', -2841.71),
    ('110702', 7260.00),
    ('120101', 46850.00),
    ('120102', 13700.00),
    ('120103', 12400.00),
    ('120201', -13426.95),
    ('120202', -3835.44),
    ('120203', -3593.34),
    ('120301', 12000.00),
    ('120401', -2666.65),
    ('210101', -3616.00),
    ('210102', -9203.68),
    ('210202', -6323.68),
    ('210203', -230.00),
    ('210204', -11603.33),
    ('210205', -2693.80),
    ('210401', -888.00),
    ('210402', -2220.00),
    ('210501', -2537.50),
    ('210502', -3062.50),
    ('210601', -3145.00),
    ('210602', -10483.32),
    ('210701', -11000.00),
    ('210702', -2640.00),
    ('210704', -21570.00),
    ('210801', -350.00),
    ('220101', -69800.00),
    ('310101', -30000.00),
    ('310102', -135000.00),
    ('310201', -2800.00),
    ('310301', -29800.00),
    ('410101', -336815.00),
    ('410201', -92055.00),
    ('410202', -16245.00),
    ('410301', -48700.00),
    ('410302', -19300.00),
    ('410401', -41520.00),
    ('410402', -20850.00),
    ('410403', -20400.00),
    ('410501', -17500.00),
    ('410601', -12240.00),
    ('410602', -25150.00),
    ('510101', 130788.00),
    ('510102', 50162.25),
    ('510103', 32697.00),
    ('510201', 20900.00),
    ('510202', 18825.87),
    ('510203', 8800.00),
    ('510301', 4920.00),
    ('510302', 10000.00),
    ('510401', 50937.10),
    ('520101', 29200.00),
    ('520102', 7000.00),
    ('520103', 2354.19),
    ('520104', 9239.98),
    ('520201', -33597.00),
    ('520202', -2970.00),
    ('610101', 50775.34),
    ('610102', 39700.00),
    ('610103', 20400.00),
    ('610104', 11237.79),
    ('610105', 2666.65),
    ('610106', 16600.00),
    ('610201', 29635.53),
    ('610202', 33500.00),
    ('610203', 11400.00),
    ('610204', 6350.00),
    ('610301', 2841.71),
    ('610303', 7850.00),
    ('710102', 4800.00),
    ('710201', 22128.18)),
        real AS (SELECT pd.codigo_cuenta AS codigo, SUM(pd.debe - pd.haber) AS saldo
                   FROM partida_detalle pd JOIN partida p ON p.id = pd.partida_id
                  WHERE p.estado = 'CONTABILIZADA' GROUP BY pd.codigo_cuenta)
        SELECT COALESCE(e.codigo, r.codigo) FROM esperado e FULL JOIN real r ON r.codigo = e.codigo
         WHERE COALESCE(e.saldo,0) <> COALESCE(r.saldo,0)
    ) x;
    IF n <> 0 THEN RAISE EXCEPTION 'La balanza difiere del informe en % cuentas', n; END IF;

    SELECT SUM(pd.debe) INTO v FROM partida_detalle pd JOIN partida p ON p.id = pd.partida_id
     WHERE p.tipo <> 'AP' AND p.fecha_contable >= DATE '2026-10-01' AND p.fecha_contable < DATE '2026-11-01';
    IF v <> 403869.85 THEN RAISE EXCEPTION 'Sumas de octubre % <> 403869.85', v; END IF;

    SELECT SUM(pd.debe - pd.haber) INTO v FROM partida_detalle pd JOIN partida p ON p.id = pd.partida_id
     WHERE pd.codigo_cuenta = '110102' AND p.fecha_contable < DATE '2026-10-01';
    IF v <> 197113.68 THEN RAISE EXCEPTION 'Saldo inicial de bancos %', v; END IF;

    SELECT count(*), COALESCE(SUM(costo_unitario),0) INTO n, v FROM licencia WHERE estado = 'DISPONIBLE';
    IF n <> 64 OR v <> 7260.00 THEN
        RAISE EXCEPTION 'Existencia de licencias % uds / %', n, v; END IF;

    SELECT count(*) INTO n FROM partida WHERE estado = 'CONTABILIZADA';
    IF n <> 49 THEN RAISE EXCEPTION 'Partidas contabilizadas: %', n; END IF;
    SELECT count(*) INTO n FROM periodo WHERE estado = 'CERRADO';
    IF n <> 9 THEN RAISE EXCEPTION 'Periodos cerrados: %', n; END IF;
    SELECT count(*) INTO n FROM periodo WHERE estado = 'EN_REVISION' AND mes = 10;
    IF n <> 1 THEN RAISE EXCEPTION 'Octubre no esta EN_REVISION'; END IF;
    SELECT count(*) INTO n FROM contador WHERE usuario IN ('contador','soporte');
    IF n <> 2 THEN RAISE EXCEPTION 'Usuarios conservados: %', n; END IF;
    SELECT count(*) INTO n FROM planilla_periodo WHERE partida_id IS NOT NULL;
    IF n <> 1 THEN RAISE EXCEPTION 'Planilla de octubre no enlazada'; END IF;
    SELECT count(*) INTO n FROM amortizacion_cuota;
    IF n <> 2 THEN RAISE EXCEPTION 'Cuotas de amortizacion: %', n; END IF;
    SELECT count(*) INTO n FROM estimacion_cartera_cuota;
    IF n <> 1 THEN RAISE EXCEPTION 'Estimacion de cartera: %', n; END IF;
    SELECT count(*) INTO n FROM saas_usuario_mes;
    IF n <> 3 THEN RAISE EXCEPTION 'Consumos SaaS de octubre: %', n; END IF;
    SELECT count(*) INTO n FROM orden_servicio;
    IF n <> 4 THEN RAISE EXCEPTION 'Ordenes de servicio: %', n; END IF;
    SELECT count(*) INTO n FROM orden_consumo;
    IF n <> 9 THEN RAISE EXCEPTION 'Consumos de ordenes: %', n; END IF;
    RAISE NOTICE 'Seed del Anexo L verificado';
END $$;
