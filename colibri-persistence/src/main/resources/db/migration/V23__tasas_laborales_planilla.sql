-- Tasas laborales de planilla administradas como parámetros del sistema.
-- La vista de planilla las muestra de sólo lectura; se editan en
-- Parametrización → Políticas junto con las tasas patronales.

INSERT INTO parametro_sistema (clave, valor, descripcion) VALUES
('rrhh.tasa.isss.laboral', '0.03', 'Tasa laboral de ISSS (seguro social); verificar tope y vigencia antes de liquidar.'),
('rrhh.tasa.previsional.laboral', '0.0725', 'Tasa laboral de cotización previsional; verificar normativa y topes vigentes.')
ON CONFLICT (clave) DO NOTHING;
