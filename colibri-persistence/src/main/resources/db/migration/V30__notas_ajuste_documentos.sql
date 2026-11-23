-- V30: notas de crédito/débito referenciadas al documento origen.
-- La columna permite trazar qué documento fue ajustado sin alterar el
-- historial: el documento origen permanece CONTABILIZADO y la nota netea
-- su efecto en el Libro de IVA.
ALTER TABLE documento
    ADD COLUMN IF NOT EXISTS documento_origen_id UUID REFERENCES documento(id);

CREATE INDEX IF NOT EXISTS idx_documento_origen
    ON documento(documento_origen_id);
