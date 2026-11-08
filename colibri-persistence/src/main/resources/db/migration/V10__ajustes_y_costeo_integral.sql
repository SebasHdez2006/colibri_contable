-- Etapa 4: trazabilidad de ajustes, costos indirectos y costeo por procesos.
-- Las tablas guardan la base de cálculo usada en cada período para que un
-- asiento histórico no cambie si después se modifican las políticas.

CREATE TABLE activo_intangible (
    id                    UUID PRIMARY KEY,
    codigo                VARCHAR(40) NOT NULL UNIQUE,
    nombre                VARCHAR(200) NOT NULL,
    cuenta_activo         VARCHAR(6) NOT NULL DEFAULT '120301',
    cuenta_amort_acum     VARCHAR(6) NOT NULL DEFAULT '120401',
    cuenta_gasto          VARCHAR(6) NOT NULL DEFAULT '610105',
    costo                 NUMERIC(18,2) NOT NULL CHECK (costo > 0),
    vida_meses            INTEGER NOT NULL CHECK (vida_meses > 0),
    fecha_uso             DATE NOT NULL,
    valor_residual        NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (valor_residual >= 0),
    amortizacion_acum     NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (amortizacion_acum >= 0),
    activo                BOOLEAN NOT NULL DEFAULT TRUE,
    CHECK (valor_residual < costo),
    CHECK (amortizacion_acum <= costo - valor_residual)
);

CREATE TABLE amortizacion_cuota (
    id                    UUID PRIMARY KEY,
    activo_id             UUID NOT NULL REFERENCES activo_intangible(id),
    periodo_id            UUID NOT NULL REFERENCES periodo(id),
    importe               NUMERIC(18,2) NOT NULL CHECK (importe > 0),
    partida_id            UUID REFERENCES partida(id),
    UNIQUE (activo_id, periodo_id)
);

CREATE TABLE estimacion_cartera_cuota (
    id                    UUID PRIMARY KEY,
    periodo_id            UUID NOT NULL REFERENCES periodo(id),
    base_estimacion       NUMERIC(18,2) NOT NULL CHECK (base_estimacion >= 0),
    porcentaje            NUMERIC(10,8) NOT NULL CHECK (porcentaje >= 0 AND porcentaje <= 1),
    importe               NUMERIC(18,2) NOT NULL CHECK (importe >= 0),
    partida_id            UUID REFERENCES partida(id),
    UNIQUE (periodo_id)
);

ALTER TABLE lote_licencia
    ADD COLUMN IF NOT EXISTS valor_recuperable NUMERIC(18,2);

CREATE TABLE deterioro_lote_licencia (
    id                    UUID PRIMARY KEY,
    lote_id               UUID NOT NULL REFERENCES lote_licencia(id),
    periodo_id            UUID NOT NULL REFERENCES periodo(id),
    costo_en_libros       NUMERIC(18,2) NOT NULL CHECK (costo_en_libros >= 0),
    valor_recuperable     NUMERIC(18,2) NOT NULL CHECK (valor_recuperable >= 0),
    importe               NUMERIC(18,2) NOT NULL CHECK (importe >= 0),
    partida_id            UUID REFERENCES partida(id),
    UNIQUE (lote_id, periodo_id)
);

CREATE TABLE cif_presupuesto_periodo (
    id                    UUID PRIMARY KEY,
    periodo_id            UUID NOT NULL REFERENCES periodo(id),
    centro_codigo         VARCHAR(40) NOT NULL DEFAULT 'GENERAL',
    cif_estimado          NUMERIC(18,2) NOT NULL CHECK (cif_estimado >= 0),
    base_estimada         NUMERIC(18,6) NOT NULL CHECK (base_estimada > 0),
    cif_real              NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (cif_real >= 0),
    base_real             NUMERIC(18,6) NOT NULL DEFAULT 0 CHECK (base_real >= 0),
    UNIQUE (periodo_id, centro_codigo)
);

CREATE TABLE proceso_costo_periodo (
    id                    UUID PRIMARY KEY,
    codigo_proceso        VARCHAR(40) NOT NULL,
    periodo_id            UUID NOT NULL REFERENCES periodo(id),
    unidades_iniciales    NUMERIC(18,6) NOT NULL DEFAULT 0 CHECK (unidades_iniciales >= 0),
    avance_inicial_material NUMERIC(10,8) NOT NULL DEFAULT 0 CHECK (avance_inicial_material BETWEEN 0 AND 1),
    avance_inicial_conversion NUMERIC(10,8) NOT NULL DEFAULT 0 CHECK (avance_inicial_conversion BETWEEN 0 AND 1),
    costo_inicial_material NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (costo_inicial_material >= 0),
    costo_inicial_conversion NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (costo_inicial_conversion >= 0),
    unidades_iniciadas    NUMERIC(18,6) NOT NULL DEFAULT 0 CHECK (unidades_iniciadas >= 0),
    unidades_terminadas   NUMERIC(18,6) NOT NULL DEFAULT 0 CHECK (unidades_terminadas >= 0),
    unidades_finales      NUMERIC(18,6) NOT NULL DEFAULT 0 CHECK (unidades_finales >= 0),
    avance_final_material NUMERIC(10,8) NOT NULL DEFAULT 0 CHECK (avance_final_material BETWEEN 0 AND 1),
    avance_final_conversion NUMERIC(10,8) NOT NULL DEFAULT 0 CHECK (avance_final_conversion BETWEEN 0 AND 1),
    costo_periodo_material NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (costo_periodo_material >= 0),
    costo_periodo_conversion NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (costo_periodo_conversion >= 0),
    UNIQUE (codigo_proceso, periodo_id)
);

INSERT INTO posting_profile (evento, etiqueta, cuenta_debe, cuenta_haber, cuenta_iva) VALUES
('AMORTIZACION', 'Amortización de intangibles', '610105', '120401', NULL),
('INCOBRABLES', 'Estimación de deterioro de cartera', '610301', '110601', NULL),
('INTERES_DEVENGADO', 'Interés acumulado por pagar', '710102', '210804', NULL),
('INGRESO_DEVENGADO', 'Ingreso devengado no facturado', '110205', '410301', NULL),
('DETERIORO_INVENTARIO', 'Deterioro de inventario digital', '610302', '110801', NULL)
ON CONFLICT (evento) DO NOTHING;

INSERT INTO parametro_sistema (clave, valor, descripcion) VALUES
('costos.cif.redondeo', 'HALF_UP_6',
 'Tasas de CIF a seis decimales y valores monetarios a dos decimales.'),
('costos.procesos.metodo', 'PEPS',
 'Método por defecto para unidades equivalentes; promedio y UEPS se conservan para análisis.'),
('cierre.inventario.menor_costo_vnr', 'true',
 'Cada lote digital se compara con su valor recuperable antes de reconocer deterioro.'),
('rrhh.incaf.umbral_trabajadores', '10',
 'La contribución INCAF se calcula cuando la planilla tiene diez o más trabajadores.')
ON CONFLICT (clave) DO NOTHING;
