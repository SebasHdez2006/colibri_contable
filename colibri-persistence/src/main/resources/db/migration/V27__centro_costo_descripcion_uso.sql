-- Centros de costo: descripción del centro y ámbito de uso.
-- Ambas columnas son texto libre administrado desde Parametrización.

ALTER TABLE centro_costo
    ADD COLUMN IF NOT EXISTS descripcion VARCHAR(200),
    ADD COLUMN IF NOT EXISTS uso        VARCHAR(200);

UPDATE centro_costo
SET descripcion = nombre
WHERE descripcion IS NULL;
