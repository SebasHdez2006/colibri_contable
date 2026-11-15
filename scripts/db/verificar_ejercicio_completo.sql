-- Comprobaciones del ejercicio integral. No valida saldos fijos: valida que la
-- semilla sea completa, consistente y útil aun si se ajusta su volumen futuro.
\encoding UTF8
\set ON_ERROR_STOP on
DO $$
DECLARE
    faltantes integer;
    descuadre numeric;
BEGIN
    SELECT COALESCE(SUM(d.debe - d.haber), 0) INTO descuadre
      FROM partida_detalle d JOIN partida p ON p.id = d.partida_id
     WHERE p.estado = 'CONTABILIZADA';
    IF descuadre <> 0 THEN RAISE EXCEPTION 'Diario global descuadrado: %', descuadre; END IF;

    SELECT count(*) INTO faltantes
      FROM cuenta c
     WHERE c.admite_movimiento
       AND NOT EXISTS (SELECT 1 FROM partida_detalle d WHERE d.codigo_cuenta = c.codigo);
    IF faltantes <> 0 THEN RAISE EXCEPTION 'Faltan % subcuentas de movimiento por usar', faltantes; END IF;

    IF (SELECT count(*) FROM orden_servicio WHERE codigo LIKE 'OS-2026-%') < 4 THEN
        RAISE EXCEPTION 'Faltan proyectos a la medida';
    END IF;
    IF (SELECT count(*) FROM licencia WHERE lote_id IN (SELECT id FROM lote_licencia WHERE codigo_lote IN ('LI-2026-05','LI-2026-06'))) < 6 THEN
        RAISE EXCEPTION 'Faltan licencias descargables del ejercicio';
    END IF;
    IF (SELECT count(*) FROM saas_usuario_mes WHERE periodo = DATE '2026-11-01') < 3 THEN
        RAISE EXCEPTION 'Faltan accesos SaaS de noviembre';
    END IF;
    IF (SELECT count(*) FROM plantilla_partida) < 3 OR (SELECT count(*) FROM proceso_costo_periodo) < 2
       OR (SELECT count(*) FROM cif_presupuesto_periodo) < 2 THEN
        RAISE EXCEPTION 'Faltan datos de plantillas, procesos o CIF';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM partida WHERE estado = 'BORRADOR')
       OR NOT EXISTS (SELECT 1 FROM partida WHERE estado = 'APROBADA')
       OR NOT EXISTS (SELECT 1 FROM partida WHERE estado = 'ANULADA')
       OR NOT EXISTS (SELECT 1 FROM partida WHERE estado = 'REVERTIDA') THEN
        RAISE EXCEPTION 'No están representados todos los estados del libro diario';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM evento_contable WHERE clave_unica = 'SEED-COMPLETO-2026-COBERTURA-CATALOGO') THEN
        RAISE EXCEPTION 'No se encontró la cobertura de catálogo';
    END IF;
    RAISE NOTICE 'Ejercicio integral verificado: diario cuadrado y cobertura completa.';
END $$;
