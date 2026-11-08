-- Planilla agregada por período. Conserva tasas, importes y la partida que
-- documenta el devengo para que los cambios normativos no alteren historia.
CREATE TABLE planilla_periodo (
    id                          UUID PRIMARY KEY,
    periodo_id                  UUID NOT NULL REFERENCES periodo(id),
    fecha_contable              DATE NOT NULL,
    empleados                   INTEGER NOT NULL CHECK (empleados > 0),
    salario_nominal             NUMERIC(18,2) NOT NULL CHECK (salario_nominal > 0),
    tasa_isss_laboral           NUMERIC(8,6) NOT NULL CHECK (tasa_isss_laboral BETWEEN 0 AND 1),
    tasa_previsional_laboral    NUMERIC(8,6) NOT NULL CHECK (tasa_previsional_laboral BETWEEN 0 AND 1),
    isr_retenido                NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (isr_retenido >= 0),
    otras_deducciones           NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (otras_deducciones >= 0),
    isss_laboral                NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (isss_laboral >= 0),
    isss_patronal               NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (isss_patronal >= 0),
    previsional_laboral         NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (previsional_laboral >= 0),
    previsional_patronal        NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (previsional_patronal >= 0),
    incaf                       NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (incaf >= 0),
    aguinaldo_provisionado      NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (aguinaldo_provisionado >= 0),
    vacaciones_provisionadas    NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (vacaciones_provisionadas >= 0),
    otras_prestaciones          NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (otras_prestaciones >= 0),
    liquido_por_pagar           NUMERIC(18,2) NOT NULL CHECK (liquido_por_pagar >= 0),
    costo_patronal_total        NUMERIC(18,2) NOT NULL CHECK (costo_patronal_total > 0),
    clave_evento                VARCHAR(200) NOT NULL UNIQUE,
    partida_id                  UUID NOT NULL UNIQUE REFERENCES partida(id),
    creado_en                   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_planilla_periodo UNIQUE (periodo_id)
);

CREATE INDEX idx_planilla_fecha ON planilla_periodo (fecha_contable);

INSERT INTO parametro_sistema (clave, valor, descripcion) VALUES
('rrhh.tasa.isss.patronal', '0.075', 'Tasa patronal de ISSS; verificar tope y vigencia antes de liquidar.'),
('rrhh.tasa.previsional.patronal', '0.0875', 'Tasa patronal previsional; verificar normativa y topes vigentes.'),
('rrhh.tasa.incaf', '0.01', 'Aporte patronal INCAF cuando corresponda por número de trabajadores.'),
('rrhh.umbral.incaf.empleados', '10', 'Cantidad mínima de empleados para aplicar INCAF en el caso didáctico.')
ON CONFLICT (clave) DO NOTHING;
