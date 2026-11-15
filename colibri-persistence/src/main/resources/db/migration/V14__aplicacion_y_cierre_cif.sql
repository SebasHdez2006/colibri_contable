-- Aplicación trazable de CIF a órdenes y cierre de la variación del período.
-- La cédula de V10 conserva el presupuesto; estas tablas vinculan la tasa
-- usada, el objeto de costo y la partida que afecta los libros.

CREATE TABLE cif_aplicacion_orden (
    id                    UUID PRIMARY KEY,
    periodo_id            UUID NOT NULL REFERENCES periodo(id),
    centro_codigo         VARCHAR(20) NOT NULL REFERENCES centro_costo(codigo),
    orden_id              UUID NOT NULL REFERENCES orden_servicio(id),
    base_aplicada         NUMERIC(18,6) NOT NULL CHECK (base_aplicada > 0),
    tasa_predeterminada   NUMERIC(18,8) NOT NULL CHECK (tasa_predeterminada >= 0),
    importe               NUMERIC(18,2) NOT NULL CHECK (importe > 0),
    fecha_aplicacion      DATE NOT NULL,
    descripcion           VARCHAR(300) NOT NULL,
    partida_id            UUID NOT NULL UNIQUE REFERENCES partida(id),
    clave_evento          VARCHAR(200) NOT NULL UNIQUE,
    creado_en             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (periodo_id, centro_codigo, orden_id)
);

CREATE INDEX idx_cif_aplicacion_orden_periodo
    ON cif_aplicacion_orden (periodo_id, centro_codigo);

CREATE TABLE cif_cierre_periodo (
    id                    UUID PRIMARY KEY,
    periodo_id            UUID NOT NULL REFERENCES periodo(id),
    centro_codigo         VARCHAR(20) NOT NULL REFERENCES centro_costo(codigo),
    cuenta_cif_real       VARCHAR(6) NOT NULL REFERENCES cuenta(codigo),
    cif_real              NUMERIC(18,2) NOT NULL CHECK (cif_real >= 0),
    cif_aplicado          NUMERIC(18,2) NOT NULL CHECK (cif_aplicado >= 0),
    variacion             NUMERIC(18,2) NOT NULL,
    fecha_cierre          DATE NOT NULL,
    partida_id            UUID NOT NULL UNIQUE REFERENCES partida(id),
    clave_evento          VARCHAR(200) NOT NULL UNIQUE,
    creado_en             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (periodo_id, centro_codigo)
);
