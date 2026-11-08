-- Plantillas de asiento recurrente: el contador define una vez la estructura
-- de un asiento mensual (depreciación, provisiones, cargos fijos) y lo genera
-- como borrador cada período sin recapturar las cuentas.

CREATE TABLE plantilla_partida (
    id          UUID PRIMARY KEY,
    codigo      VARCHAR(40) NOT NULL UNIQUE,
    nombre      VARCHAR(160) NOT NULL,
    tipo        VARCHAR(2) NOT NULL CHECK (tipo IN ('IN', 'EG', 'DI', 'AJ', 'AP', 'CI')),
    concepto    VARCHAR(500) NOT NULL,
    activo      BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE plantilla_detalle (
    id            UUID PRIMARY KEY,
    plantilla_id  UUID NOT NULL REFERENCES plantilla_partida(id) ON DELETE CASCADE,
    linea         INTEGER NOT NULL,
    codigo_cuenta VARCHAR(6) NOT NULL REFERENCES cuenta(codigo),
    auxiliar      VARCHAR(64),
    centro_costo  VARCHAR(64),
    debe          NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (debe >= 0),
    haber         NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (haber >= 0),
    UNIQUE (plantilla_id, linea),
    CONSTRAINT ck_plantilla_subcuenta CHECK (length(codigo_cuenta) = 6)
);

CREATE INDEX idx_plantilla_detalle_plantilla ON plantilla_detalle(plantilla_id);
