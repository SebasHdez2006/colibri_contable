-- V29__correcciones_auditoria_segunda_ronda.sql
-- Ampliación de CHECK de origen en partida, corrección del perfil INVENTARIO y restricciones de activo fijo.

-- 1. Ampliar CHECK de origen en tabla partida para aceptar los orígenes del dominio
ALTER TABLE partida DROP CONSTRAINT IF EXISTS partida_origen_check;
ALTER TABLE partida ADD CONSTRAINT partida_origen_check
    CHECK (origen IN ('MANUAL', 'AUTOMATICO', 'VENTA', 'PLANILLA', 'CIF', 'CIERRE', 'SEED'));

-- 2. Corregir perfil posting INVENTARIO: la salida al ingresar producto terminado proviene de WIP (110701), no de Bancos (110102)
UPDATE posting_profile
SET cuenta_haber = '110701'
WHERE evento = 'INVENTARIO' AND cuenta_haber = '110102';

-- 3. Restricciones de integridad en activo fijo
ALTER TABLE activo_fijo DROP CONSTRAINT IF EXISTS chk_activo_fijo_costo;
ALTER TABLE activo_fijo ADD CONSTRAINT chk_activo_fijo_costo CHECK (costo > 0);

ALTER TABLE activo_fijo DROP CONSTRAINT IF EXISTS chk_activo_fijo_residual;
ALTER TABLE activo_fijo ADD CONSTRAINT chk_activo_fijo_residual CHECK (valor_residual >= 0 AND valor_residual <= costo);
