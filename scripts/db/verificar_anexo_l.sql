-- Generado por generar_seed_sql.py. Falla (RAISE EXCEPTION) si el seed no coincide con el Anexo L.
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
    ('110102', 92335.00),
    ('110201', 4407.00),
    ('110202', 20406.78),
    ('110203', 9510.00),
    ('110204', 8600.00),
    ('110401', 5210.00),
    ('110501', 15614.25),
    ('110702', 1680.00),
    ('120101', 24750.00),
    ('120102', 5700.00),
    ('120103', 6000.00),
    ('120201', -10557.50),
    ('120202', -2668.75),
    ('120203', -3100.00),
    ('120301', 7200.00),
    ('120401', -1800.00),
    ('210101', -12800.00),
    ('210202', -1844.70),
    ('210204', -3783.88),
    ('210205', -800.00),
    ('210601', -1937.50),
    ('210602', -6458.33),
    ('210704', -7950.00),
    ('210803', -2000.00),
    ('220101', -9800.00),
    ('310101', -30000.00),
    ('310102', -45000.00),
    ('310201', -2800.00),
    ('310301', -29800.00),
    ('410101', -214600.00),
    ('410201', -33400.00),
    ('410301', -12800.00),
    ('410402', -71200.00),
    ('410602', -24000.00),
    ('510101', 72000.00),
    ('510102', 32190.00),
    ('510103', 18000.00),
    ('510201', 12000.00),
    ('510202', 6705.78),
    ('510203', 6400.00),
    ('510302', 9400.00),
    ('510401', 26339.22),
    ('520103', 118.75),
    ('610101', 63650.00),
    ('610102', 12100.00),
    ('610103', 9300.00),
    ('610104', 5757.50),
    ('610105', 1800.00),
    ('610106', 6700.00),
    ('610201', 21492.50),
    ('610202', 4200.00),
    ('610203', 2100.00),
    ('610204', 1300.00),
    ('610303', 2800.00),
    ('710102', 1200.00),
    ('710201', 12133.88)),
        real AS (SELECT pd.codigo_cuenta AS codigo, SUM(pd.debe - pd.haber) AS saldo
                   FROM partida_detalle pd JOIN partida p ON p.id = pd.partida_id
                  WHERE p.estado = 'CONTABILIZADA' GROUP BY pd.codigo_cuenta)
        SELECT COALESCE(e.codigo, r.codigo) FROM esperado e FULL JOIN real r ON r.codigo = e.codigo
         WHERE COALESCE(e.saldo,0) <> COALESCE(r.saldo,0)
    ) x;
    IF n <> 0 THEN RAISE EXCEPTION 'La balanza difiere del Anexo L en % cuentas', n; END IF;

    SELECT SUM(pd.debe) INTO v FROM partida_detalle pd JOIN partida p ON p.id = pd.partida_id
     WHERE p.tipo <> 'AP' AND p.fecha_contable >= DATE '2026-10-01';
    IF v <> 97517.13 THEN RAISE EXCEPTION 'Sumas de octubre % <> 97517.13', v; END IF;

    SELECT SUM(pd.debe - pd.haber) INTO v FROM partida_detalle pd JOIN partida p ON p.id = pd.partida_id
     WHERE pd.codigo_cuenta = '110102' AND p.fecha_contable < DATE '2026-10-01';
    IF v <> 100914.25 THEN RAISE EXCEPTION 'Saldo inicial de bancos %', v; END IF;

    SELECT count(*), COALESCE(SUM(costo_unitario),0) INTO n, v FROM licencia WHERE estado = 'DISPONIBLE';
    IF n <> 15 OR v <> 1680.00 THEN
        RAISE EXCEPTION 'Existencia de licencias % uds / %', n, v; END IF;

    SELECT count(*) INTO n FROM partida WHERE estado = 'CONTABILIZADA';
    IF n <> 19 THEN RAISE EXCEPTION 'Partidas contabilizadas: %', n; END IF;
    SELECT count(*) INTO n FROM periodo WHERE estado = 'CERRADO';
    IF n <> 9 THEN RAISE EXCEPTION 'Periodos cerrados: %', n; END IF;
    SELECT count(*) INTO n FROM contador WHERE usuario IN ('contador','soporte');
    IF n <> 2 THEN RAISE EXCEPTION 'Usuarios conservados: %', n; END IF;
    SELECT count(*) INTO n FROM planilla_periodo WHERE partida_id IS NOT NULL;
    IF n <> 1 THEN RAISE EXCEPTION 'Planilla de octubre no enlazada'; END IF;
    RAISE NOTICE 'Seed del Anexo L verificado';
END $$;
