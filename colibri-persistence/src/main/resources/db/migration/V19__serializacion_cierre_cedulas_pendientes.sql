-- Serializa la creación de cédulas con el cierre del período. El bloqueo de
-- la fila período evita que una cédula se inserte entre la comprobación y el
-- cambio a CERRADO; los enlaces pendientes sí pueden completarse después.
CREATE OR REPLACE FUNCTION exigir_periodo_capturable_para_cedula()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    estado_periodo VARCHAR(20);
BEGIN
    SELECT estado INTO estado_periodo
    FROM periodo
    WHERE id = NEW.periodo_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'No existe el período de la cédula';
    END IF;
    IF estado_periodo NOT IN ('ABIERTO', 'EN_REVISION') THEN
        RAISE EXCEPTION 'No se puede crear una cédula en un período %', estado_periodo;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_depreciacion_cuota_periodo_capturable
    BEFORE INSERT ON depreciacion_cuota
    FOR EACH ROW EXECUTE FUNCTION exigir_periodo_capturable_para_cedula();

CREATE TRIGGER trg_amortizacion_cuota_periodo_capturable
    BEFORE INSERT ON amortizacion_cuota
    FOR EACH ROW EXECUTE FUNCTION exigir_periodo_capturable_para_cedula();

CREATE TRIGGER trg_estimacion_cartera_periodo_capturable
    BEFORE INSERT ON estimacion_cartera_cuota
    FOR EACH ROW EXECUTE FUNCTION exigir_periodo_capturable_para_cedula();

CREATE TRIGGER trg_deterioro_lote_periodo_capturable
    BEFORE INSERT ON deterioro_lote_licencia
    FOR EACH ROW EXECUTE FUNCTION exigir_periodo_capturable_para_cedula();

CREATE TRIGGER trg_planilla_periodo_capturable
    BEFORE INSERT ON planilla_periodo
    FOR EACH ROW EXECUTE FUNCTION exigir_periodo_capturable_para_cedula();

CREATE OR REPLACE FUNCTION impedir_cierre_con_cedulas_pendientes()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.estado = 'CERRADO' AND OLD.estado IS DISTINCT FROM NEW.estado
       AND EXISTS (
            SELECT 1 FROM depreciacion_cuota WHERE periodo_id = NEW.id AND partida_id IS NULL
            UNION ALL
            SELECT 1 FROM amortizacion_cuota WHERE periodo_id = NEW.id AND partida_id IS NULL
            UNION ALL
            SELECT 1 FROM estimacion_cartera_cuota WHERE periodo_id = NEW.id AND partida_id IS NULL
            UNION ALL
            SELECT 1 FROM deterioro_lote_licencia WHERE periodo_id = NEW.id AND partida_id IS NULL
            UNION ALL
            SELECT 1 FROM planilla_periodo WHERE periodo_id = NEW.id AND partida_id IS NULL
       ) THEN
        RAISE EXCEPTION 'No se puede cerrar el período mientras existan cédulas pendientes de vincular';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_impedir_cierre_con_cedulas_pendientes
    BEFORE UPDATE OF estado ON periodo
    FOR EACH ROW EXECUTE FUNCTION impedir_cierre_con_cedulas_pendientes();
