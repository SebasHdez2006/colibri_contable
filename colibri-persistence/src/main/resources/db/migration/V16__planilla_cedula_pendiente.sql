-- La cédula se conserva antes de contabilizar la partida. Si el proceso se
-- interrumpe después del asiento, el reintento enlaza esta misma fotografía en
-- lugar de recalcular importes o tasas con datos nuevos.
ALTER TABLE planilla_periodo
    ALTER COLUMN partida_id DROP NOT NULL,
    ADD COLUMN centro_costo VARCHAR(20) NOT NULL DEFAULT 'CC-01';

ALTER TABLE planilla_periodo
    ADD CONSTRAINT fk_planilla_periodo_centro
        FOREIGN KEY (centro_costo) REFERENCES centro_costo(codigo);

COMMENT ON COLUMN planilla_periodo.partida_id IS
    'Partida del devengo. NULL sólo mientras la cédula inmutable está pendiente de contabilización o recuperación.';
COMMENT ON COLUMN planilla_periodo.centro_costo IS
    'Centro de costo usado en el asiento de devengo de la planilla.';
