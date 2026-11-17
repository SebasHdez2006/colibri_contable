-- Kárdex de licencias: movimientos cronológicos con saldo por lote.
-- Cada fila es un hecho de inventario (liberación, entrega PEPS, devolución o
-- revocación) y conserva el saldo de unidades y valor resultante en el lote.
-- El saldo refleja las unidades bajo control de la empresa: las entregadas ya
-- no cuentan y las devueltas regresan; la revocación no mueve existencias
-- porque la unidad había salido con su venta.

CREATE TABLE licencia_movimiento (
    id              UUID PRIMARY KEY,
    secuencia       BIGINT GENERATED ALWAYS AS IDENTITY,
    lote_id         UUID NOT NULL REFERENCES lote_licencia(id),
    licencia_id     UUID REFERENCES licencia(id),
    tipo            VARCHAR(20) NOT NULL
                    CHECK (tipo IN ('LIBERACION', 'ENTREGA', 'DEVOLUCION', 'REVOCACION')),
    fecha           DATE NOT NULL,
    unidades        INTEGER NOT NULL,
    costo_unitario  NUMERIC(18,6) NOT NULL DEFAULT 0,
    importe         NUMERIC(18,2) NOT NULL DEFAULT 0,
    saldo_unidades  INTEGER NOT NULL,
    saldo_valor     NUMERIC(18,2) NOT NULL,
    partida_id      UUID REFERENCES partida(id),
    detalle         VARCHAR(300),
    creado_por      UUID REFERENCES contador(id),
    registrado_en   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_licencia_mov_lote ON licencia_movimiento(lote_id, secuencia);
CREATE INDEX ix_licencia_mov_licencia ON licencia_movimiento(licencia_id);
