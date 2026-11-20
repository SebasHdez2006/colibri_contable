-- Vencimiento de la clave temporal emitida por soporte técnico.
-- La marca indica que la contraseña actual es temporal y debe reemplazarse.

ALTER TABLE contador
    ADD COLUMN IF NOT EXISTS clave_temporal_expira TIMESTAMP;
