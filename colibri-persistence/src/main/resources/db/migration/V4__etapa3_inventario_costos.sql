-- Etapa 3: inventario digital, órdenes, costos, activos

CREATE TABLE producto (
    id              UUID PRIMARY KEY,
    codigo          VARCHAR(40) NOT NULL UNIQUE,
    nombre          VARCHAR(200) NOT NULL,
    modalidad       VARCHAR(20) NOT NULL
                    CHECK (modalidad IN ('LICENCIA', 'SAAS', 'PROYECTO')),
    activo          BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE producto_version (
    id              UUID PRIMARY KEY,
    producto_id     UUID NOT NULL REFERENCES producto(id),
    version_label   VARCHAR(40) NOT NULL,
    estado          VARCHAR(20) NOT NULL DEFAULT 'EN_PRODUCCION'
                    CHECK (estado IN ('EN_PRODUCCION', 'LIBERADA', 'DESCONTINUADA')),
    UNIQUE (producto_id, version_label)
);

CREATE TABLE lote_licencia (
    id              UUID PRIMARY KEY,
    version_id      UUID NOT NULL REFERENCES producto_version(id),
    codigo_lote     VARCHAR(40) NOT NULL UNIQUE,
    unidades_estimadas INTEGER NOT NULL CHECK (unidades_estimadas > 0),
    costo_total     NUMERIC(18,2) NOT NULL DEFAULT 0,
    costo_unitario  NUMERIC(18,6) NOT NULL DEFAULT 0,
    unidades_disponibles INTEGER NOT NULL DEFAULT 0,
    fecha_liberacion DATE,
    partida_id      UUID REFERENCES partida(id)
);

CREATE TABLE licencia (
    id              UUID PRIMARY KEY,
    lote_id         UUID NOT NULL REFERENCES lote_licencia(id),
    numero          INTEGER NOT NULL,
    estado          VARCHAR(20) NOT NULL DEFAULT 'DISPONIBLE'
                    CHECK (estado IN ('DISPONIBLE', 'RESERVADA', 'ENTREGADA', 'REVOCADA', 'DEVUELTA')),
    cliente_id      UUID REFERENCES tercero(id),
    contrato_id     UUID REFERENCES contrato(id),
    documento_id    UUID REFERENCES documento(id),
    fecha_entrega   DATE,
    fecha_activacion DATE,
    costo_unitario  NUMERIC(18,6) NOT NULL DEFAULT 0,
    UNIQUE (lote_id, numero)
);

CREATE TABLE orden_servicio (
    id              UUID PRIMARY KEY,
    codigo          VARCHAR(40) NOT NULL UNIQUE,
    contrato_id     UUID REFERENCES contrato(id),
    cliente_id      UUID REFERENCES tercero(id),
    descripcion     VARCHAR(500) NOT NULL,
    presupuesto     NUMERIC(18,2) NOT NULL DEFAULT 0,
    precio_venta    NUMERIC(18,2) NOT NULL DEFAULT 0,
    estado          VARCHAR(20) NOT NULL DEFAULT 'ABIERTA'
                    CHECK (estado IN ('ABIERTA', 'EN_PROCESO', 'ENTREGADA', 'CERRADA', 'CANCELADA')),
    margen_minimo   NUMERIC(8,4) NOT NULL DEFAULT 0.35,
    creado_en       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE orden_consumo (
    id              UUID PRIMARY KEY,
    orden_id        UUID NOT NULL REFERENCES orden_servicio(id),
    tipo            VARCHAR(20) NOT NULL
                    CHECK (tipo IN ('MOD', 'SERVICIO', 'CIF', 'OTRO')),
    descripcion     VARCHAR(300) NOT NULL,
    cantidad        NUMERIC(18,6) NOT NULL DEFAULT 0,
    tarifa          NUMERIC(18,6) NOT NULL DEFAULT 0,
    importe         NUMERIC(18,2) NOT NULL DEFAULT 0,
    periodo_id      UUID REFERENCES periodo(id),
    fecha           DATE NOT NULL,
    partida_id      UUID REFERENCES partida(id)
);

CREATE TABLE activo_fijo (
    id              UUID PRIMARY KEY,
    codigo          VARCHAR(40) NOT NULL UNIQUE,
    nombre          VARCHAR(200) NOT NULL,
    cuenta_activo   VARCHAR(6) NOT NULL,
    cuenta_dep_acum VARCHAR(6) NOT NULL,
    cuenta_gasto    VARCHAR(6) NOT NULL DEFAULT '610104',
    costo           NUMERIC(18,2) NOT NULL,
    vida_meses      INTEGER NOT NULL CHECK (vida_meses > 0),
    fecha_uso       DATE NOT NULL,
    valor_residual  NUMERIC(18,2) NOT NULL DEFAULT 0,
    depreciacion_acum NUMERIC(18,2) NOT NULL DEFAULT 0,
    activo          BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE depreciacion_cuota (
    id              UUID PRIMARY KEY,
    activo_id       UUID NOT NULL REFERENCES activo_fijo(id),
    periodo_id      UUID NOT NULL REFERENCES periodo(id),
    importe         NUMERIC(18,2) NOT NULL,
    partida_id      UUID REFERENCES partida(id),
    UNIQUE (activo_id, periodo_id)
);

INSERT INTO producto (id, codigo, nombre, modalidad) VALUES
('cccccccc-0001-0001-0001-000000000001', 'COL-VENTAS', 'Colibrí Ventas', 'LICENCIA'),
('cccccccc-0001-0001-0001-000000000002', 'COL-INV', 'Colibrí Inventarios', 'LICENCIA'),
('cccccccc-0001-0001-0001-000000000003', 'COL-GEST', 'Colibrí Gestión', 'LICENCIA'),
('cccccccc-0001-0001-0001-000000000004', 'COL-SAAS-INV', 'SaaS Colibrí Inventarios', 'SAAS');

INSERT INTO producto_version (id, producto_id, version_label, estado) VALUES
('dddddddd-0001-0001-0001-000000000001', 'cccccccc-0001-0001-0001-000000000002', '1.0', 'LIBERADA');

INSERT INTO orden_servicio (id, codigo, descripcion, presupuesto, precio_venta, estado) VALUES
('eeeeeeee-0001-0001-0001-000000000001', 'OS2026001',
 'Aplicación de inventarios a medida', 1800.00, 2500.00, 'ABIERTA');
