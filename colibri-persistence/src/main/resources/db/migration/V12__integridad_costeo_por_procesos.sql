-- Endurece la trazabilidad añadida en V10 sin reescribir una migración ya aplicada.
-- GENERAL es el centro explícito que utiliza el análisis CIF cuando no se
-- especifica uno; por ello puede participar también en la integridad referencial.
INSERT INTO centro_costo (codigo, nombre) VALUES
('GENERAL', 'Centro de costo general')
ON CONFLICT (codigo) DO NOTHING;

ALTER TABLE cif_presupuesto_periodo
    ALTER COLUMN centro_codigo TYPE VARCHAR(20),
    ADD CONSTRAINT fk_cif_presupuesto_centro
        FOREIGN KEY (centro_codigo) REFERENCES centro_costo(codigo);

-- La cédula física se conserva conciliada. Además, el modelo PEPS simplificado
-- sólo admite entradas donde el WIP inicial se terminó durante el período; si
-- quedara WIP inicial, se requieren capas de unidades explícitas.
ALTER TABLE proceso_costo_periodo
    ADD CONSTRAINT ck_proceso_unidades_disponibles
        CHECK (unidades_finales <= unidades_iniciales + unidades_iniciadas),
    ADD CONSTRAINT ck_proceso_balance_fisico
        CHECK (unidades_terminadas = unidades_iniciales + unidades_iniciadas - unidades_finales),
    ADD CONSTRAINT ck_proceso_fifo_inicial_terminado
        CHECK (unidades_terminadas >= unidades_iniciales);
