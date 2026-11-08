CREATE INDEX IF NOT EXISTS idx_aplicacion_pago_origen
    ON aplicacion_pago(documento_origen_id, fecha);

CREATE INDEX IF NOT EXISTS idx_aplicacion_pago_partida
    ON aplicacion_pago(partida_id);
