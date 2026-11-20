-- V28: rellena licencia_movimiento con el historial reconstruible de datos
-- anteriores a V25 (la tabla nacio vacia y solo registra operaciones nuevas).
--
-- Reglas:
--  * Solo rellena lotes que aun no tienen movimientos; un lote con historial
--    real conserva sus saldos y no se mezcla con datos sinteticos.
--  * LIBERACION: una por lote, en su fecha_liberacion, con su partida_id.
--  * ENTREGA: una por licencia ENTREGADA (unidades -1, importe -costo).
--  * DEVOLUCION: una por licencia DEVUELTA (+1); su fecha no quedo
--    registrada, se usa la fecha de esta migracion.
--  * REVOCACION: una por licencia REVOCADA (0 unidades, 0 importe).
--  * Las entregas sin fecha_entrega se reconstruyen con la fecha de
--    liberacion del lote (nunca anterior a ella).
--  * Los saldos son sumas corridas por lote en orden cronologico; la
--    liberacion se ordena primero dentro de una misma fecha.
--  * Idempotente: la guarda NOT EXISTS por lote evita duplicar al re-ejecutar.
--  * Los movimientos historicos se distinguen con "registro historico".

WITH fuente AS (
    SELECT l.id AS lote_id,
           NULL::uuid AS licencia_id,
           NULL::integer AS licencia_numero,
           'LIBERACION'::varchar(20) AS tipo,
           1 AS tipo_orden,
           COALESCE(l.fecha_liberacion, CURRENT_DATE)::date AS fecha,
           COALESCE(
               NULLIF((SELECT COUNT(*) FROM licencia x WHERE x.lote_id = l.id), 0),
               l.unidades_estimadas)::integer AS unidades,
           l.costo_unitario,
           l.costo_total AS importe,
           l.partida_id,
           NULL::varchar(300) AS detalle
    FROM lote_licencia l
    UNION ALL
    SELECT l.id, li.id, li.numero, 'ENTREGA', 2,
           COALESCE(GREATEST(COALESCE(li.fecha_entrega, l.fecha_liberacion),
                             l.fecha_liberacion),
                    CURRENT_DATE::date),
           -1, li.costo_unitario, -ROUND(li.costo_unitario, 2), NULL::uuid,
           'Entrega PEPS · licencia #' || li.numero || ' · registro histórico'
    FROM licencia li
    JOIN lote_licencia l ON l.id = li.lote_id
    WHERE li.estado = 'ENTREGADA'
    UNION ALL
    SELECT l.id, li.id, li.numero, 'DEVOLUCION', 3,
           GREATEST(CURRENT_DATE::date, l.fecha_liberacion),
           1, li.costo_unitario, ROUND(li.costo_unitario, 2), NULL::uuid,
           'Devolución al inventario · licencia #' || li.numero || ' · registro histórico'
    FROM licencia li
    JOIN lote_licencia l ON l.id = li.lote_id
    WHERE li.estado = 'DEVUELTA'
    UNION ALL
    SELECT l.id, li.id, li.numero, 'REVOCACION', 4,
           GREATEST(CURRENT_DATE::date, l.fecha_liberacion),
           0, li.costo_unitario, 0::numeric, NULL::uuid,
           'Revocación de derecho · licencia #' || li.numero || ' · registro histórico'
    FROM licencia li
    JOIN lote_licencia l ON l.id = li.lote_id
    WHERE li.estado = 'REVOCADA'
),
con_saldos AS (
    SELECT f.*,
           SUM(f.unidades) OVER w AS saldo_unidades,
           ROUND(SUM(f.importe) OVER w, 2) AS saldo_valor
    FROM fuente f
    WHERE NOT EXISTS (
        SELECT 1 FROM licencia_movimiento lm WHERE lm.lote_id = f.lote_id
    )
    WINDOW w AS (
        PARTITION BY f.lote_id
        ORDER BY f.fecha, f.tipo_orden, f.licencia_numero NULLS LAST
    )
)
INSERT INTO licencia_movimiento (
    id, lote_id, licencia_id, tipo, fecha, unidades,
    costo_unitario, importe, saldo_unidades, saldo_valor,
    partida_id, detalle, creado_por)
SELECT gen_random_uuid(),
       lote_id, licencia_id, tipo, fecha, unidades,
       ROUND(costo_unitario, 6), ROUND(importe, 2),
       saldo_unidades::integer, saldo_valor,
       partida_id,
       CASE WHEN tipo = 'LIBERACION'
            THEN 'Liberación de ' || unidades || ' licencias · registro histórico'
            ELSE detalle END,
       NULL
FROM con_saldos
ORDER BY lote_id, fecha, tipo_orden, licencia_numero NULLS LAST;
