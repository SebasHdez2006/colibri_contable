-- Las cédulas de cierre son evidencia inmutable: después de crearlas sólo
-- puede completarse una vez su vínculo con la partida contabilizada.
CREATE OR REPLACE FUNCTION proteger_cedula_cierre_inmutable()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF (to_jsonb(NEW) - 'partida_id') IS DISTINCT FROM (to_jsonb(OLD) - 'partida_id') THEN
        RAISE EXCEPTION 'La cédula de cierre es inmutable; sólo puede completarse su partida pendiente';
    END IF;

    IF OLD.partida_id IS NOT NULL AND NEW.partida_id IS DISTINCT FROM OLD.partida_id THEN
        RAISE EXCEPTION 'La partida vinculada a la cédula de cierre no puede sustituirse ni eliminarse';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_depreciacion_cuota_inmutable
    BEFORE UPDATE ON depreciacion_cuota
    FOR EACH ROW EXECUTE FUNCTION proteger_cedula_cierre_inmutable();

CREATE TRIGGER trg_amortizacion_cuota_inmutable
    BEFORE UPDATE ON amortizacion_cuota
    FOR EACH ROW EXECUTE FUNCTION proteger_cedula_cierre_inmutable();

CREATE TRIGGER trg_estimacion_cartera_cuota_inmutable
    BEFORE UPDATE ON estimacion_cartera_cuota
    FOR EACH ROW EXECUTE FUNCTION proteger_cedula_cierre_inmutable();

CREATE TRIGGER trg_deterioro_lote_inmutable
    BEFORE UPDATE ON deterioro_lote_licencia
    FOR EACH ROW EXECUTE FUNCTION proteger_cedula_cierre_inmutable();

-- El asiento inverso debe restituir también los auxiliares que no forman
-- parte del mayor. Se conserva la cédula asociada a la partida revertida
-- como rastro de auditoría.
CREATE OR REPLACE FUNCTION restituir_auxiliares_al_revertir_partida()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.estado = 'CONTABILIZADA' AND NEW.estado = 'REVERTIDA' THEN
        UPDATE activo_fijo a
        SET depreciacion_acum = GREATEST(0::NUMERIC, a.depreciacion_acum - c.importe),
            activo = CASE
                WHEN GREATEST(0::NUMERIC, a.depreciacion_acum - c.importe) < a.costo - a.valor_residual
                    THEN TRUE
                ELSE a.activo
            END
        FROM depreciacion_cuota c
        WHERE c.partida_id = NEW.id AND c.activo_id = a.id;

        UPDATE activo_intangible a
        SET amortizacion_acum = GREATEST(0::NUMERIC, a.amortizacion_acum - c.importe),
            activo = CASE
                WHEN GREATEST(0::NUMERIC, a.amortizacion_acum - c.importe) < a.costo - a.valor_residual
                    THEN TRUE
                ELSE a.activo
            END
        FROM amortizacion_cuota c
        WHERE c.partida_id = NEW.id AND c.activo_id = a.id;

        -- El importe de deterioro se recalcula desde las cédulas contabilizadas;
        -- este campo conserva la última valoración visible del lote.
        UPDATE lote_licencia l
        SET valor_recuperable = (
            SELECT d.valor_recuperable
            FROM deterioro_lote_licencia d
            JOIN partida p ON p.id = d.partida_id
            WHERE d.lote_id = l.id AND p.estado = 'CONTABILIZADA'
            ORDER BY p.fecha_contable DESC, p.creado_en DESC
            LIMIT 1
        )
        WHERE EXISTS (
            SELECT 1 FROM deterioro_lote_licencia d WHERE d.lote_id = l.id AND d.partida_id = NEW.id
        );
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_restituir_auxiliares_al_revertir_partida
    AFTER UPDATE OF estado ON partida
    FOR EACH ROW EXECUTE FUNCTION restituir_auxiliares_al_revertir_partida();
