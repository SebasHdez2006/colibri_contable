-- La planilla debe conservar las políticas patronales efectivas al devengar,
-- no sólo los importes resultantes. Las filas anteriores a esta migración se
-- reconstruyen con las tasas fijas que usaba V11.
ALTER TABLE planilla_periodo
    ADD COLUMN IF NOT EXISTS tasa_isss_patronal NUMERIC(8,6) NOT NULL DEFAULT 0.075
        CHECK (tasa_isss_patronal BETWEEN 0 AND 1),
    ADD COLUMN IF NOT EXISTS tasa_previsional_patronal NUMERIC(8,6) NOT NULL DEFAULT 0.0875
        CHECK (tasa_previsional_patronal BETWEEN 0 AND 1),
    ADD COLUMN IF NOT EXISTS tasa_incaf NUMERIC(8,6) NOT NULL DEFAULT 0.01
        CHECK (tasa_incaf BETWEEN 0 AND 1),
    ADD COLUMN IF NOT EXISTS umbral_incaf_empleados INTEGER NOT NULL DEFAULT 10
        CHECK (umbral_incaf_empleados > 0);

COMMENT ON COLUMN planilla_periodo.tasa_isss_patronal IS
    'Tasa patronal ISSS vigente al contabilizar la planilla.';
COMMENT ON COLUMN planilla_periodo.tasa_previsional_patronal IS
    'Tasa previsional patronal vigente al contabilizar la planilla.';
COMMENT ON COLUMN planilla_periodo.tasa_incaf IS
    'Tasa INCAF vigente al contabilizar la planilla.';
COMMENT ON COLUMN planilla_periodo.umbral_incaf_empleados IS
    'Número mínimo de empleados que activa INCAF según la política vigente.';
