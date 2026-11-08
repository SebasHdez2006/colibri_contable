-- Objetos de costo por plan: SaaS por usuarios-mes y servicios facturados por plan.
-- La modalidad SERVICIO representa implementación, capacitación, soporte y otros
-- servicios que no deben confundirse con una orden de desarrollo a la medida.

ALTER TABLE plan_servicio
    DROP CONSTRAINT IF EXISTS plan_servicio_modalidad_check;

ALTER TABLE plan_servicio
    ADD CONSTRAINT plan_servicio_modalidad_check
    CHECK (modalidad IN ('LICENCIA', 'SAAS', 'PROYECTO', 'SERVICIO'));

CREATE TABLE IF NOT EXISTS saas_usuario_mes (
    plan_id       UUID NOT NULL REFERENCES plan_servicio(id),
    periodo       DATE NOT NULL,
    usuarios_mes  INTEGER NOT NULL CHECK (usuarios_mes > 0),
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (plan_id, periodo),
    CHECK (EXTRACT(DAY FROM periodo) = 1)
);

CREATE INDEX IF NOT EXISTS idx_saas_usuario_mes_periodo
    ON saas_usuario_mes (periodo, plan_id);

CREATE TABLE IF NOT EXISTS plan_consumo (
    id           UUID PRIMARY KEY,
    plan_id      UUID NOT NULL REFERENCES plan_servicio(id),
    tipo         VARCHAR(20) NOT NULL CHECK (tipo IN ('MOD', 'SERVICIO', 'CIF')),
    descripcion  VARCHAR(300) NOT NULL,
    cantidad     NUMERIC(18,6) NOT NULL CHECK (cantidad > 0),
    tarifa       NUMERIC(18,6) NOT NULL CHECK (tarifa >= 0),
    importe      NUMERIC(18,2) NOT NULL CHECK (importe >= 0),
    fecha        DATE NOT NULL,
    partida_id   UUID NOT NULL REFERENCES partida(id)
);

CREATE INDEX IF NOT EXISTS idx_plan_consumo_plan_fecha
    ON plan_consumo (plan_id, fecha);

INSERT INTO plan_servicio (id, codigo, nombre, modalidad, tarifa_base, tipo_facturacion, cuenta_ingreso)
VALUES ('ffffffff-0001-0001-0001-000000000004', 'SVC-SOPORTE',
        'Soporte e implementación por plan', 'SERVICIO', 0.00, 'POR_PLAN', '410302')
ON CONFLICT (codigo) DO NOTHING;
