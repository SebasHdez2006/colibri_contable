-- La cédula de deterioro conserva cada movimiento y distingue reversiones.
-- El saldo por lote se obtiene como deterioros menos reversiones, por lo que
-- una nueva estimación no vuelve a castigar el mismo costo ya corregido.
ALTER TABLE deterioro_lote_licencia
    ADD COLUMN IF NOT EXISTS es_reversion BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN deterioro_lote_licencia.es_reversion IS
    'TRUE cuando el movimiento revierte parcialmente un deterioro previo; el importe siempre se guarda positivo.';
