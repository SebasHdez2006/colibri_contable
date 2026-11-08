-- Políticas visibles y configurables que respaldan los controles automáticos.
-- Se mantienen como parámetros para que el contador pueda documentar la
-- política aprobada sin alterar el código ni las partidas históricas.

INSERT INTO parametro_sistema (clave, valor, descripcion) VALUES
('contabilidad.base_devengo', 'true',
 'Principio de devengo: ingresos y gastos se reconocen cuando se generan.'),
('contabilidad.partida_doble', 'estricta',
 'Debe y Haber deben cuadrar exactamente antes de contabilizar.'),
('contabilidad.consistencia', 'catalogo_versionado',
 'Consistencia: catálogo, cuentas automáticas y políticas quedan versionados y trazables.'),
('contabilidad.empresa_en_marcha', 'true',
 'Los estados se preparan bajo supuesto de continuidad de la empresa.'),
('contabilidad.materialidad', 'revision_contador',
 'Las estimaciones y ajustes materiales requieren revisión del contador firmante.'),
('inventario.metodo_valoracion', 'PEPS',
 'Método contable de inventario para licencias; UEPS y promedio son comparativos gerenciales.'),
('costos.base_cif_proyectos', 'horas_mod_y_centro_costo',
 'Base de aplicación de CIF para proyectos: horas de mano de obra directa y centro de costo.'),
('costos.cif_software', 'infraestructura_soporte_herramientas_calidad',
 'CIF de software: nube/servidores, soporte, licencias de herramientas y aseguramiento de calidad.'),
('rrhh.registro_horas', 'obligatorio_por_orden_o_plan',
 'La mano de obra debe registrarse por horas, tarifa, orden/proyecto o plan facturado.'),
('rrhh.aprobacion_horas', 'responsable_proyecto',
 'Las horas y cambios de tarifa se validan por el responsable de proyecto antes de costear.'),
('ventas.distribucion_software', 'plan_canal_precio_neto_iva',
 'Cada venta identifica línea, plan/canal, precio neto, IVA y costo reconocido.')
ON CONFLICT (clave) DO NOTHING;
