-- Roles, correo y entorno técnico (actor Soporte Técnico)

ALTER TABLE contador
    ADD COLUMN IF NOT EXISTS rol VARCHAR(20) NOT NULL DEFAULT 'CONTADOR',
    ADD COLUMN IF NOT EXISTS email VARCHAR(160);

ALTER TABLE contador
    DROP CONSTRAINT IF EXISTS ck_contador_rol;
ALTER TABLE contador
    ADD CONSTRAINT ck_contador_rol CHECK (rol IN ('CONTADOR', 'SOPORTE_TECNICO'));

UPDATE contador SET rol = 'CONTADOR' WHERE rol IS NULL OR rol = '';

CREATE TABLE IF NOT EXISTS entorno_tecnico (
    id              UUID PRIMARY KEY,
    jdbc_url        VARCHAR(300) NOT NULL,
    db_user         VARCHAR(80) NOT NULL,
    ruta_reportes   VARCHAR(500),
    ruta_dte        VARCHAR(500),
    impresora       VARCHAR(300),
    ultimo_respaldo TIMESTAMP,
    actualizado_en  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO entorno_tecnico (id, jdbc_url, db_user, ruta_reportes, ruta_dte, impresora)
SELECT '12121212-1212-1212-1212-121212121212',
       'jdbc:postgresql://127.0.0.1:5434/colibri',
       'colibri',
       COALESCE(current_setting('colibri.ruta_reportes', true), ''),
       '',
       ''
WHERE NOT EXISTS (SELECT 1 FROM entorno_tecnico);

CREATE TABLE IF NOT EXISTS log_tecnico (
    id          UUID PRIMARY KEY,
    fecha_hora  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    nivel       VARCHAR(10) NOT NULL CHECK (nivel IN ('INFO', 'WARN', 'ERROR', 'FATAL')),
    modulo      VARCHAR(80) NOT NULL,
    mensaje     VARCHAR(500) NOT NULL,
    traza       TEXT
);
