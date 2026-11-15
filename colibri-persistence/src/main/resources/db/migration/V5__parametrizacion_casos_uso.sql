-- Parametrización, dimensiones y catálogos de soporte (CasosDeUso.md)

ALTER TABLE empresa
    ADD COLUMN IF NOT EXISTS actividad_economica VARCHAR(120),
    ADD COLUMN IF NOT EXISTS direccion_fiscal VARCHAR(300),
    ADD COLUMN IF NOT EXISTS logo_ruta VARCHAR(500),
    ADD COLUMN IF NOT EXISTS representante_legal VARCHAR(160),
    ADD COLUMN IF NOT EXISTS dui_representante VARCHAR(20),
    ADD COLUMN IF NOT EXISTS contador_nombre VARCHAR(160),
    ADD COLUMN IF NOT EXISTS registro_cvpcpa VARCHAR(40);

CREATE TABLE IF NOT EXISTS parametro_sistema (
    clave       VARCHAR(80) PRIMARY KEY,
    valor       VARCHAR(400) NOT NULL,
    descripcion VARCHAR(300),
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO parametro_sistema (clave, valor, descripcion) VALUES
('folio.mascara.in', 'IN-{yyyy}-{######}', 'Máscara de correlativo de ingresos'),
('folio.mascara.eg', 'EG-{yyyy}-{######}', 'Máscara de correlativo de egresos'),
('folio.mascara.di', 'DI-{yyyy}-{######}', 'Máscara de correlativo de diario'),
('folio.mascara.aj', 'AJ-{yyyy}-{######}', 'Máscara de correlativo de ajustes'),
('partida.tolerancia', '0.00', 'Tolerancia de descuadre permitida'),
('partida.inmutabilidad', 'true', 'Partidas contabilizadas inmutables'),
('sesion.inactividad.minutos', '15', 'Cierre de sesión por inactividad')
ON CONFLICT (clave) DO NOTHING;

CREATE TABLE IF NOT EXISTS parametro_fiscal (
    id              UUID PRIMARY KEY,
    iva_tasa        NUMERIC(8,6) NOT NULL DEFAULT 0.13,
    retencion_iva   NUMERIC(8,6) NOT NULL DEFAULT 0.01,
    pago_cuenta_isr NUMERIC(8,6) NOT NULL DEFAULT 0.0175,
    retencion_isr_servicios NUMERIC(8,6) NOT NULL DEFAULT 0.10,
    retencion_isr_intangibles NUMERIC(8,6) NOT NULL DEFAULT 0.05,
    cuenta_iva_credito VARCHAR(6) NOT NULL DEFAULT '110301',
    cuenta_iva_debito  VARCHAR(6) NOT NULL DEFAULT '210201',
    cuenta_iva_por_pagar VARCHAR(6) NOT NULL DEFAULT '210202',
    cuenta_retencion_iva VARCHAR(6) NOT NULL DEFAULT '110304',
    cuenta_pago_cuenta VARCHAR(6) NOT NULL DEFAULT '110302',
    catalogo_mh     VARCHAR(40) NOT NULL DEFAULT 'CAT-019',
    actualizado_en  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO parametro_fiscal (id) VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-000000000001')
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS posting_profile (
    evento          VARCHAR(40) PRIMARY KEY,
    etiqueta        VARCHAR(120) NOT NULL,
    cuenta_debe     VARCHAR(6) NOT NULL,
    cuenta_haber    VARCHAR(6) NOT NULL,
    cuenta_iva      VARCHAR(6),
    actualizado_en  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO posting_profile (evento, etiqueta, cuenta_debe, cuenta_haber, cuenta_iva) VALUES
('VENTA', 'Ventas de licencias o servicios', '110201', '410602', '210201'),
('COMPRA', 'Compras y gastos gravados', '610102', '210101', '110301'),
('COBRO', 'Cobros a clientes', '110102', '110201', NULL),
('PAGO', 'Pagos a proveedores', '210101', '110102', NULL),
('INVENTARIO', 'Inventario perpetuo de licencias', '110702', '110102', NULL),
('COSTO_VENTA', 'Costo de ventas de licencias', '510302', '110702', NULL),
('DEPRECIACION', 'Depreciación lineal mensual', '610104', '120201', NULL),
('PLANILLA', 'Devengo de planilla', '610101', '210301', NULL)
ON CONFLICT (evento) DO NOTHING;

CREATE TABLE IF NOT EXISTS centro_costo (
    codigo      VARCHAR(20) PRIMARY KEY,
    nombre      VARCHAR(120) NOT NULL,
    activo      BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO centro_costo (codigo, nombre) VALUES
('CC-01', 'Administración'),
('CC-02', 'Desarrollo'),
('CC-03', 'Soporte'),
('CC-04', 'Comercial')
ON CONFLICT (codigo) DO NOTHING;

CREATE TABLE IF NOT EXISTS plan_servicio (
    id              UUID PRIMARY KEY,
    codigo          VARCHAR(40) NOT NULL UNIQUE,
    nombre          VARCHAR(160) NOT NULL,
    modalidad       VARCHAR(20) NOT NULL CHECK (modalidad IN ('LICENCIA', 'SAAS', 'PROYECTO')),
    tarifa_base     NUMERIC(18,2) NOT NULL DEFAULT 0,
    tipo_facturacion VARCHAR(40) NOT NULL DEFAULT 'UNICA',
    cuenta_ingreso  VARCHAR(6) NOT NULL,
    activo          BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO plan_servicio (id, codigo, nombre, modalidad, tarifa_base, tipo_facturacion, cuenta_ingreso) VALUES
('ffffffff-0001-0001-0001-000000000001', 'LIC-INV', 'Licencia Colibrí Inventarios', 'LICENCIA', 240.00, 'UNICA', '410602'),
('ffffffff-0001-0001-0001-000000000002', 'SAAS-INV', 'SaaS Colibrí Inventarios (usuario-mes)', 'SAAS', 50.00, 'USUARIO_MES', '410402'),
('ffffffff-0001-0001-0001-000000000003', 'OS-MEDIDA', 'Desarrollo a la medida', 'PROYECTO', 2500.00, 'POR_ENTREGA', '410101')
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO activo_fijo (id, codigo, nombre, cuenta_activo, cuenta_dep_acum, cuenta_gasto, costo, vida_meses, fecha_uso, valor_residual)
SELECT '99999999-0001-0001-0001-000000000001', 'AF-PC-001', 'Equipo de cómputo',
       '120101', '120201', '610104', 1200.00, 36, DATE '2026-01-02', 0
WHERE NOT EXISTS (SELECT 1 FROM activo_fijo WHERE codigo = 'AF-PC-001');
