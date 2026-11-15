-- Perfil requerido para los anticipos de clientes gravados con IVA.
INSERT INTO posting_profile (evento, etiqueta, cuenta_debe, cuenta_haber, cuenta_iva)
VALUES ('ANTICIPO', 'Anticipos de clientes SaaS', '110102', '210702', '210201')
ON CONFLICT (evento) DO NOTHING;
