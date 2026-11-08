-- Etapa 2: terceros, contratos, documentos, IVA, aplicaciones de pago

CREATE TABLE tercero (
    id              UUID PRIMARY KEY,
    nit             VARCHAR(30) NOT NULL,
    nrc             VARCHAR(30),
    razon_social    VARCHAR(200) NOT NULL,
    nombre_comercial VARCHAR(200),
    es_cliente      BOOLEAN NOT NULL DEFAULT FALSE,
    es_proveedor    BOOLEAN NOT NULL DEFAULT FALSE,
    email           VARCHAR(120),
    telefono        VARCHAR(40),
    activo          BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tercero_nit UNIQUE (nit)
);

CREATE TABLE contrato (
    id              UUID PRIMARY KEY,
    tercero_id      UUID NOT NULL REFERENCES tercero(id),
    codigo          VARCHAR(40) NOT NULL UNIQUE,
    modalidad       VARCHAR(30) NOT NULL
                    CHECK (modalidad IN ('LICENCIA', 'SAAS', 'PROYECTO', 'SOPORTE', 'MIXTO')),
    descripcion     VARCHAR(500),
    fecha_inicio    DATE NOT NULL,
    fecha_fin       DATE,
    monto_sin_iva   NUMERIC(18,2) NOT NULL DEFAULT 0,
    activo          BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE documento (
    id                  UUID PRIMARY KEY,
    tipo                VARCHAR(30) NOT NULL
                        CHECK (tipo IN ('CCF', 'FACTURA', 'NC', 'ND', 'RECIBO', 'COMPROBANTE', 'OTRO')),
    emisor_nit          VARCHAR(30) NOT NULL,
    numero_control      VARCHAR(60),
    codigo_generacion   VARCHAR(80) NOT NULL,
    receptor_nit        VARCHAR(30),
    tercero_id          UUID REFERENCES tercero(id),
    contrato_id         UUID REFERENCES contrato(id),
    fecha_emision       DATE NOT NULL,
    base_gravada        NUMERIC(18,2) NOT NULL DEFAULT 0,
    iva                 NUMERIC(18,2) NOT NULL DEFAULT 0,
    exento              NUMERIC(18,2) NOT NULL DEFAULT 0,
    total               NUMERIC(18,2) NOT NULL DEFAULT 0,
    iva_incluido        BOOLEAN NOT NULL DEFAULT FALSE,
    estado              VARCHAR(20) NOT NULL DEFAULT 'REGISTRADO'
                        CHECK (estado IN ('REGISTRADO', 'ANULADO', 'CONTABILIZADO')),
    archivo_ruta        VARCHAR(500),
    archivo_huella      VARCHAR(128),
    es_simulacion       BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_documento_emisor_codigo UNIQUE (emisor_nit, codigo_generacion)
);

CREATE TABLE documento_evento (
    id              UUID PRIMARY KEY,
    documento_id    UUID NOT NULL REFERENCES documento(id),
    evento_id       UUID NOT NULL REFERENCES evento_contable(id),
    tipo_evento     VARCHAR(40) NOT NULL
                    CHECK (tipo_evento IN ('VENTA', 'COMPRA', 'COBRO', 'PAGO', 'ANTICIPO', 'AJUSTE', 'REVERSION')),
    partida_id      UUID REFERENCES partida(id),
    CONSTRAINT uq_documento_evento UNIQUE (documento_id, tipo_evento)
);

CREATE TABLE parametro_iva (
    id              UUID PRIMARY KEY,
    tasa            NUMERIC(8,6) NOT NULL DEFAULT 0.13,
    vigencia_desde  DATE NOT NULL,
    vigencia_hasta  DATE,
    cuenta_credito  VARCHAR(6) NOT NULL DEFAULT '110301',
    cuenta_debito   VARCHAR(6) NOT NULL DEFAULT '210201',
    cuenta_por_pagar VARCHAR(6) NOT NULL DEFAULT '210202',
    activo          BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO parametro_iva (id, tasa, vigencia_desde, activo)
VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-000000000001', 0.13, DATE '2024-01-01', TRUE);

CREATE TABLE aplicacion_pago (
    id                  UUID PRIMARY KEY,
    documento_origen_id UUID NOT NULL REFERENCES documento(id),
    documento_pago_id   UUID REFERENCES documento(id),
    partida_id          UUID REFERENCES partida(id),
    importe             NUMERIC(18,2) NOT NULL CHECK (importe > 0),
    fecha               DATE NOT NULL,
    concepto            VARCHAR(300)
);

CREATE INDEX idx_documento_fecha ON documento(fecha_emision);
CREATE INDEX idx_documento_tercero ON documento(tercero_id);
CREATE INDEX idx_contrato_tercero ON contrato(tercero_id);
