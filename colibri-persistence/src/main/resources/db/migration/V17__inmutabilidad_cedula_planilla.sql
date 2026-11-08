-- La cédula es la evidencia reproducible del cálculo que sustenta el asiento.
-- Tras crearla sólo se puede completar, una única vez, el enlace pendiente a
-- su partida; ni importes, tasas, centro ni clave de evento pueden alterarse.
CREATE OR REPLACE FUNCTION proteger_cedula_planilla_inmutable()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF (to_jsonb(NEW) - 'partida_id') IS DISTINCT FROM (to_jsonb(OLD) - 'partida_id') THEN
        RAISE EXCEPTION 'La cédula de planilla es inmutable; sólo puede completarse su partida pendiente';
    END IF;

    IF OLD.partida_id IS NOT NULL AND NEW.partida_id IS DISTINCT FROM OLD.partida_id THEN
        RAISE EXCEPTION 'La partida enlazada a la cédula de planilla no puede sustituirse ni eliminarse';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_cedula_planilla_inmutable
    BEFORE UPDATE ON planilla_periodo
    FOR EACH ROW
    EXECUTE FUNCTION proteger_cedula_planilla_inmutable();
