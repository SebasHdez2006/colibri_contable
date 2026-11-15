-- Núcleo etapa 1 — Colibrí (H.1, H.2, H.8, H.9)

CREATE TABLE empresa (
    id              UUID PRIMARY KEY,
    razon_social    VARCHAR(200) NOT NULL,
    nombre_comercial VARCHAR(200),
    nit             VARCHAR(30),
    nrc             VARCHAR(30),
    moneda          VARCHAR(3) NOT NULL DEFAULT 'USD',
    zona_horaria    VARCHAR(64) NOT NULL DEFAULT 'America/El_Salvador',
    actualizado_en  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ejercicio (
    id               UUID PRIMARY KEY,
    empresa_id       UUID NOT NULL REFERENCES empresa(id),
    anio             INTEGER NOT NULL,
    fecha_inicio     DATE NOT NULL,
    fecha_fin        DATE NOT NULL,
    activo           BOOLEAN NOT NULL DEFAULT TRUE,
    version_catalogo INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT uq_ejercicio_empresa_anio UNIQUE (empresa_id, anio),
    CONSTRAINT ck_ejercicio_fechas CHECK (fecha_inicio < fecha_fin)
);

CREATE TABLE periodo (
    id           UUID PRIMARY KEY,
    ejercicio_id UUID NOT NULL REFERENCES ejercicio(id),
    mes          INTEGER NOT NULL CHECK (mes BETWEEN 1 AND 12),
    fecha_inicio DATE NOT NULL,
    fecha_fin    DATE NOT NULL,
    estado       VARCHAR(20) NOT NULL CHECK (estado IN ('ABIERTO', 'EN_REVISION', 'CERRADO')),
    version      INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT uq_periodo_ejercicio_mes UNIQUE (ejercicio_id, mes),
    CONSTRAINT ck_periodo_fechas CHECK (fecha_inicio <= fecha_fin)
);

CREATE TABLE cuenta (
    id                UUID PRIMARY KEY,
    codigo            VARCHAR(6) NOT NULL UNIQUE,
    nombre            VARCHAR(200) NOT NULL,
    codigo_padre      VARCHAR(6),
    nivel             INTEGER NOT NULL CHECK (nivel IN (1, 2, 4, 6)),
    naturaleza        VARCHAR(20) NOT NULL CHECK (naturaleza IN ('DEUDORA', 'ACREEDORA', 'AMBAS')),
    rubro             VARCHAR(80),
    estado            VARCHAR(20) NOT NULL DEFAULT 'ACTIVA'
                      CHECK (estado IN ('ACTIVA', 'DESACTIVADA', 'BLOQUEADA')),
    vigencia_desde    DATE,
    vigencia_hasta    DATE,
    admite_movimiento BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_cuenta_movimiento CHECK (
        (admite_movimiento = TRUE AND length(codigo) = 6)
        OR (admite_movimiento = FALSE)
    )
);

CREATE INDEX idx_cuenta_padre ON cuenta(codigo_padre);
CREATE INDEX idx_cuenta_estado ON cuenta(estado);

CREATE TABLE contador (
    id                        UUID PRIMARY KEY,
    usuario                   VARCHAR(64) NOT NULL UNIQUE,
    nombre                    VARCHAR(120) NOT NULL,
    password_hash             BYTEA NOT NULL,
    password_salt             BYTEA NOT NULL,
    intentos_fallidos         INTEGER NOT NULL DEFAULT 0,
    bloqueado_hasta           TIMESTAMP,
    codigo_recuperacion_hash  BYTEA,
    codigo_recuperacion_salt  BYTEA,
    activo                    BOOLEAN NOT NULL DEFAULT TRUE,
    ultimo_acceso             TIMESTAMP
);

CREATE TABLE evento_contable (
    id                       UUID PRIMARY KEY,
    clave_unica              VARCHAR(200) NOT NULL UNIQUE,
    descripcion              VARCHAR(500),
    partida_contabilizada_id UUID,
    creado_en                TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE partida (
    id                   UUID PRIMARY KEY,
    tipo                 VARCHAR(2) NOT NULL CHECK (tipo IN ('IN', 'EG', 'DI', 'AJ', 'AP', 'CI')),
    folio                VARCHAR(20),
    fecha_contable       DATE NOT NULL,
    periodo_id           UUID NOT NULL REFERENCES periodo(id),
    concepto             VARCHAR(500) NOT NULL,
    estado               VARCHAR(20) NOT NULL
                         CHECK (estado IN ('BORRADOR', 'APROBADA', 'CONTABILIZADA', 'ANULADA', 'REVERTIDA')),
    origen               VARCHAR(20) NOT NULL CHECK (origen IN ('MANUAL', 'AUTOMATICO')),
    documento_soporte    VARCHAR(300),
    evento_id            UUID REFERENCES evento_contable(id),
    partida_revertida_id UUID REFERENCES partida(id),
    motivo_anulacion     VARCHAR(500),
    creado_por           UUID REFERENCES contador(id),
    creado_en            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_partida_folio UNIQUE (folio)
);

CREATE INDEX idx_partida_periodo ON partida(periodo_id);
CREATE INDEX idx_partida_estado ON partida(estado);
CREATE INDEX idx_partida_fecha ON partida(fecha_contable);

ALTER TABLE evento_contable
    ADD CONSTRAINT fk_evento_partida
    FOREIGN KEY (partida_contabilizada_id) REFERENCES partida(id);

CREATE TABLE partida_detalle (
    id            UUID PRIMARY KEY,
    partida_id    UUID NOT NULL REFERENCES partida(id) ON DELETE CASCADE,
    linea         INTEGER NOT NULL,
    codigo_cuenta VARCHAR(6) NOT NULL REFERENCES cuenta(codigo),
    auxiliar      VARCHAR(64),
    centro_costo  VARCHAR(64),
    debe          NUMERIC(18, 2) NOT NULL DEFAULT 0 CHECK (debe >= 0),
    haber         NUMERIC(18, 2) NOT NULL DEFAULT 0 CHECK (haber >= 0),
    CONSTRAINT uq_partida_linea UNIQUE (partida_id, linea),
    CONSTRAINT ck_detalle_un_lado CHECK (
        (debe > 0 AND haber = 0) OR (haber > 0 AND debe = 0) OR (debe = 0 AND haber = 0)
    ),
    CONSTRAINT ck_detalle_subcuenta CHECK (length(codigo_cuenta) = 6)
);

CREATE INDEX idx_detalle_cuenta ON partida_detalle(codigo_cuenta);

CREATE TABLE folio_secuencia (
    anio      INTEGER NOT NULL,
    tipo      VARCHAR(2) NOT NULL,
    ultimo    BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (anio, tipo)
);

CREATE TABLE bitacora (
    id             UUID PRIMARY KEY,
    contador_id    UUID REFERENCES contador(id),
    fecha_hora     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accion         VARCHAR(80) NOT NULL,
    entidad        VARCHAR(80) NOT NULL,
    entidad_id     VARCHAR(80),
    valor_anterior TEXT,
    valor_nuevo    TEXT,
    motivo         VARCHAR(500),
    referencia     VARCHAR(200)
);

CREATE INDEX idx_bitacora_fecha ON bitacora(fecha_hora);

CREATE TABLE sesion_lock (
    id          INTEGER PRIMARY KEY CHECK (id = 1),
    hostname    VARCHAR(200) NOT NULL,
    pid         INTEGER,
    adquirido_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE catalogo_version (
    id            UUID PRIMARY KEY,
    version       INTEGER NOT NULL UNIQUE,
    aprobado      BOOLEAN NOT NULL DEFAULT FALSE,
    aprobado_en   TIMESTAMP,
    aprobado_por  UUID REFERENCES contador(id),
    descripcion   VARCHAR(300)
);

-- Impide UPDATE de partidas ya contabilizadas o revertidas (inmutabilidad H02.05)
-- Solo se permite la transición CONTABILIZADA -> REVERTIDA.
CREATE OR REPLACE FUNCTION impedir_update_partida_definitiva()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.estado = 'REVERTIDA' THEN
        RAISE EXCEPTION 'Partida revertida inmutable: %', OLD.id;
    END IF;
    IF OLD.estado = 'CONTABILIZADA' AND NEW.estado IS DISTINCT FROM 'REVERTIDA' THEN
        RAISE EXCEPTION 'Partida contabilizada inmutable: %', OLD.id;
    END IF;
    IF OLD.estado = 'CONTABILIZADA' AND NEW.estado = 'REVERTIDA' THEN
        IF NEW.folio IS DISTINCT FROM OLD.folio
           OR NEW.fecha_contable IS DISTINCT FROM OLD.fecha_contable
           OR NEW.periodo_id IS DISTINCT FROM OLD.periodo_id
           OR NEW.concepto IS DISTINCT FROM OLD.concepto
           OR NEW.tipo IS DISTINCT FROM OLD.tipo THEN
            RAISE EXCEPTION 'Reversión solo puede cambiar el estado: %', OLD.id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_partida_inmutable
    BEFORE UPDATE ON partida
    FOR EACH ROW
    EXECUTE PROCEDURE impedir_update_partida_definitiva();

CREATE OR REPLACE FUNCTION impedir_update_detalle_definitivo()
RETURNS TRIGGER AS $$
DECLARE
    est VARCHAR(20);
BEGIN
    SELECT estado INTO est FROM partida WHERE id = COALESCE(OLD.partida_id, NEW.partida_id);
    IF est IN ('CONTABILIZADA', 'REVERTIDA', 'ANULADA') THEN
        RAISE EXCEPTION 'No se pueden modificar detalles de partida en estado %', est;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_detalle_inmutable
    BEFORE UPDATE OR DELETE ON partida_detalle
    FOR EACH ROW
    EXECUTE PROCEDURE impedir_update_detalle_definitivo();
