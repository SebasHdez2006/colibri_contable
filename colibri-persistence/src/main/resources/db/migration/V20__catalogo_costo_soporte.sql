-- Anexo L usa 510401 (costo de soporte, mantenimiento e implementación) bajo 5104,
-- cuenta que el catálogo inicial no incluía.
INSERT INTO cuenta (id, codigo, nombre, codigo_padre, nivel, naturaleza, rubro, estado, vigencia_desde, admite_movimiento) VALUES
('a0000000-0000-4000-8000-000000005104', '5104', 'Costo de soporte, mantenimiento e implementación', '51', 4, 'DEUDORA', 'COSTOS', 'ACTIVA', DATE '2024-01-01', FALSE),
('a0000000-0000-4000-8000-000000510401', '510401', 'Costo de soporte, mantenimiento e implementación', '5104', 6, 'DEUDORA', 'COSTOS', 'ACTIVA', DATE '2024-01-01', TRUE)
ON CONFLICT (codigo) DO NOTHING;
