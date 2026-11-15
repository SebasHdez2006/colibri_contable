#!/usr/bin/env python3
"""Genera scripts/db/seed_anexo_l.sql a partir de anexo_l.json (bloque "seed").

Los UUID son deterministas (uuid5) para que la salida sea reproducible.
"""
import argparse
import json
import sys
import uuid
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
JSON_DEFECTO = RAIZ / 'colibri-application/src/main/resources/seed/anexo_l.json'
SQL_DEFECTO = RAIZ / 'scripts/db/seed_anexo_l.sql'
NS = uuid.UUID('6f1c1b0e-3d7a-4c3e-9a55-0c0b1b100026')
CONTADOR = "(SELECT id FROM contador WHERE usuario = 'contador')"


def uid(*partes):
    return str(uuid.uuid5(NS, ':'.join(partes)))


def q(v):
    if v is None:
        return 'NULL'
    if isinstance(v, bool):
        return 'TRUE' if v else 'FALSE'
    if isinstance(v, (int, float)):
        return str(v)
    return "'" + str(v).replace("'", "''") + "'"


def num(v):
    return str(v)


def fecha(v):
    return 'NULL' if v is None else f"DATE '{v}'"


def periodo_ref(anio, mes):
    return f"'{uid('periodo', str(anio), str(mes))}'"


def periodo_de(f):
    return int(f.split('-')[1])


def generar(d):
    s = d['seed']
    anio = d['ejercicio']['anio']
    emp, ej = uid('empresa'), uid('ejercicio', str(anio))
    pref = d['meta']['prefijo_evento']
    out = []
    w = out.append
    w('-- Generado por scripts/anexo_l/generar_seed_sql.py desde anexo_l.json. No editar a mano.')
    w('-- Requiere: reset_total.sql + seed_00_referencia.sql ejecutados antes y usuarios en `contador`.')
    w('\\encoding UTF8')
    w('BEGIN;')
    w(f"""DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM evento_contable WHERE clave_unica LIKE '{pref}%') THEN
        RAISE EXCEPTION 'El seed del Anexo L ya fue cargado';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM contador WHERE usuario = 'contador') THEN
        RAISE EXCEPTION 'Falta el usuario contador';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM cuenta WHERE codigo = '510401') THEN
        RAISE EXCEPTION 'Falta el catalogo de referencia (ejecute seed_00_referencia.sql)';
    END IF;
END $$;""")
    e = d['empresa']
    w(f"INSERT INTO empresa (id, razon_social, nombre_comercial, nit, nrc, moneda, zona_horaria, actividad_economica) "
      f"VALUES ('{emp}', {q(e['razon_social'])}, {q(e['nombre_comercial'])}, {q(e['nit'])}, {q(e['nrc'])}, "
      f"{q(e['moneda'])}, {q(e['zona_horaria'])}, {q(e['actividad_economica'])});")
    w(f"INSERT INTO ejercicio (id, empresa_id, anio, fecha_inicio, fecha_fin, activo, version_catalogo) "
      f"VALUES ('{ej}', '{emp}', {anio}, DATE '{anio}-01-01', DATE '{anio}-12-31', TRUE, 1);")
    import calendar
    for mes in range(1, 13):
        ult = calendar.monthrange(anio, mes)[1]
        w(f"INSERT INTO periodo (id, ejercicio_id, mes, fecha_inicio, fecha_fin, estado, version) VALUES "
          f"({periodo_ref(anio, mes)}, '{ej}', {mes}, DATE '{anio}-{mes:02d}-01', DATE '{anio}-{mes:02d}-{ult:02d}', 'ABIERTO', 1);")
    w(f"UPDATE catalogo_version SET aprobado = TRUE, aprobado_en = CURRENT_TIMESTAMP, aprobado_por = {CONTADOR} "
      f"WHERE version = 1;")

    t_id = {t['nit']: uid('tercero', t['nit']) for t in s['terceros']}
    for t in s['terceros']:
        w(f"INSERT INTO tercero (id, nit, razon_social, nombre_comercial, es_cliente, es_proveedor, activo) VALUES "
          f"('{t_id[t['nit']]}', {q(t['nit'])}, {q(t['razon_social'])}, {q(t['razon_social'])}, "
          f"{q(t['es_cliente'])}, {q(t['es_proveedor'])}, TRUE);")
    d_id = {x['codigo']: uid('documento', x['codigo']) for x in s['documentos']}
    for x in s['documentos']:
        w(f"INSERT INTO documento (id, tipo, emisor_nit, numero_control, codigo_generacion, receptor_nit, tercero_id, "
          f"fecha_emision, base_gravada, iva, exento, total, iva_incluido, estado, es_simulacion) VALUES "
          f"('{d_id[x['codigo']]}', {q(x['tipo'])}, {q(x['emisor_nit'])}, {q('SIM-' + x['codigo'])}, {q(x['codigo'])}, "
          f"{q(x['receptor_nit'])}, '{t_id[x['tercero_nit']]}', {fecha(x['fecha'])}, {num(x['base_gravada'])}, "
          f"{num(x['iva'])}, 0, {num(x['total'])}, FALSE, 'CONTABILIZADO', TRUE);")
    for v in s['producto_versiones']:
        w(f"INSERT INTO producto_version (id, producto_id, version_label, estado) VALUES "
          f"('{uid('pv', v['producto'], v['version'])}', (SELECT id FROM producto WHERE codigo = {q(v['producto'])}), "
          f"{q(v['version'])}, {q(v['estado'])});")

    p_id, ev_id = {}, {}
    for p in s['partidas']:
        pid, eid = uid('partida', p['folio']), uid('evento', p['folio'])
        p_id[p['folio']], ev_id[p['folio']] = pid, eid
        w(f"INSERT INTO evento_contable (id, clave_unica, descripcion) VALUES "
          f"('{eid}', {q(pref + p['clave'])}, {q(p['concepto'][:500])});")
        w(f"INSERT INTO partida (id, tipo, folio, fecha_contable, periodo_id, concepto, estado, origen, "
          f"documento_soporte, evento_id, creado_por) VALUES ('{pid}', {q(p['tipo'])}, {q(p['folio'])}, "
          f"{fecha(p['fecha'])}, {periodo_ref(anio, periodo_de(p['fecha']))}, {q(p['concepto'][:500])}, "
          f"'CONTABILIZADA', {q(p['origen'])}, {q(p['soporte'])}, '{eid}', {CONTADOR});")
        filas = ',\n    '.join(
            f"('{uid('detalle', p['folio'], str(i))}', '{pid}', {i}, {q(l['cuenta'])}, {q(l.get('auxiliar'))}, NULL, "
            f"{num(l['debe'])}, {num(l['haber'])})" for i, l in enumerate(p['lineas'], 1))
        w(f"INSERT INTO partida_detalle (id, partida_id, linea, codigo_cuenta, auxiliar, centro_costo, debe, haber) "
          f"VALUES\n    {filas};")
        w(f"UPDATE evento_contable SET partida_contabilizada_id = '{pid}' WHERE id = '{eid}';")
        if p['documento']:
            w(f"INSERT INTO documento_evento (id, documento_id, evento_id, tipo_evento, partida_id) VALUES "
              f"('{uid('docev', p['folio'])}', '{d_id[p['documento']]}', '{eid}', {q(p['tipo_evento'])}, '{pid}');")
    for a in s['aplicaciones_pago']:
        w(f"INSERT INTO aplicacion_pago (id, documento_origen_id, documento_pago_id, partida_id, importe, fecha, concepto) "
          f"VALUES ('{uid('apago', a['pago'])}', '{d_id[a['origen']]}', '{d_id[a['pago']]}', '{p_id[a['folio']]}', "
          f"{num(a['importe'])}, {fecha(a['fecha'])}, {q(a['concepto'])});")
    for f in s['folio_secuencia']:
        w(f"INSERT INTO folio_secuencia (anio, tipo, ultimo) VALUES ({f['anio']}, {q(f['tipo'])}, {f['ultimo']});")

    for l in s['lotes']:
        lid = uid('lote', l['codigo_lote'])
        w(f"INSERT INTO lote_licencia (id, version_id, codigo_lote, unidades_estimadas, costo_total, costo_unitario, "
          f"unidades_disponibles, fecha_liberacion, partida_id) VALUES ('{lid}', "
          f"(SELECT pv.id FROM producto_version pv JOIN producto p ON p.id = pv.producto_id "
          f"WHERE p.codigo = 'COL-INV' AND pv.version_label = {q(l['version'])}), {q(l['codigo_lote'])}, "
          f"{l['unidades_estimadas']}, {num(l['costo_total'])}, {num(l['costo_unitario'])}, "
          f"{l['unidades_disponibles']}, {fecha(l['fecha_liberacion'])}, "
          f"{q(p_id[l['folio']]) if l['folio'] else 'NULL'});")
        filas = []
        for x in l['licencias']:
            ent = x['estado'] == 'ENTREGADA'
            filas.append(f"('{uid('licencia', l['codigo_lote'], str(x['numero']))}', '{lid}', {x['numero']}, "
                         f"{q(x['estado'])}, {q(t_id[x['cliente']]) if ent else 'NULL'}, NULL, "
                         f"{q(d_id[x['documento']]) if ent else 'NULL'}, {fecha(x.get('fecha'))}, "
                         f"{fecha(x.get('fecha'))}, {num(x['costo_unitario'])})")
        w("INSERT INTO licencia (id, lote_id, numero, estado, cliente_id, contrato_id, documento_id, fecha_entrega, "
          "fecha_activacion, costo_unitario) VALUES\n    " + ',\n    '.join(filas) + ';')

    for o in s['ordenes']:
        oid = uid('orden', o['codigo'])
        w(f"INSERT INTO orden_servicio (id, codigo, cliente_id, descripcion, presupuesto, precio_venta, estado) VALUES "
          f"('{oid}', {q(o['codigo'])}, '{t_id[o['cliente']]}', {q(o['descripcion'])}, {num(o['presupuesto'])}, "
          f"{num(o['precio_venta'])}, {q(o['estado'])});")
        pf = o['folio_consumos']
        fch = next(p['fecha'] for p in s['partidas'] if p['folio'] == pf)
        for i, c in enumerate(o['consumos'], 1):
            w(f"INSERT INTO orden_consumo (id, orden_id, tipo, descripcion, cantidad, tarifa, importe, periodo_id, fecha, partida_id) "
              f"VALUES ('{uid('consumo', o['codigo'], str(i))}', '{oid}', {q(c['tipo'])}, {q(c['descripcion'])}, "
              f"{num(c['cantidad'])}, {num(c['tarifa'])}, {num(c['importe'])}, {periodo_ref(anio, periodo_de(fch))}, "
              f"{fecha(fch)}, '{p_id[pf]}');")

    for a in s['activos_fijos']:
        w(f"INSERT INTO activo_fijo (id, codigo, nombre, cuenta_activo, cuenta_dep_acum, cuenta_gasto, costo, vida_meses, "
          f"fecha_uso, valor_residual, depreciacion_acum, activo) VALUES ('{uid('af', a['codigo'])}', {q(a['codigo'])}, "
          f"{q(a['nombre'])}, {q(a['cuenta_activo'])}, {q(a['cuenta_dep_acum'])}, {q(a['cuenta_gasto'])}, "
          f"{num(a['costo'])}, {a['vida_meses']}, {fecha(a['fecha_uso'])}, {num(a['valor_residual'])}, "
          f"{num(a['depreciacion_acum'])}, TRUE);")
    for c in s['cuotas_depreciacion']:
        w(f"INSERT INTO depreciacion_cuota (id, activo_id, periodo_id, importe, partida_id) VALUES "
          f"('{uid('dep', c['activo'], str(c['periodo_mes']))}', '{uid('af', c['activo'])}', "
          f"{periodo_ref(anio, c['periodo_mes'])}, {num(c['importe'])}, '{p_id[c['folio']]}');")
    for a in s['activos_intangibles']:
        w(f"INSERT INTO activo_intangible (id, codigo, nombre, cuenta_activo, cuenta_amort_acum, cuenta_gasto, costo, "
          f"vida_meses, fecha_uso, valor_residual, amortizacion_acum, activo) VALUES ('{uid('ai', a['codigo'])}', "
          f"{q(a['codigo'])}, {q(a['nombre'])}, {q(a['cuenta_activo'])}, {q(a['cuenta_amort_acum'])}, "
          f"{q(a['cuenta_gasto'])}, {num(a['costo'])}, {a['vida_meses']}, {fecha(a['fecha_uso'])}, "
          f"{num(a['valor_residual'])}, {num(a['amortizacion_acum'])}, TRUE);")
    for pl in s['planillas']:
        cols = ['empleados', 'salario_nominal', 'tasa_isss_laboral', 'tasa_previsional_laboral', 'isr_retenido',
                'otras_deducciones', 'isss_laboral', 'isss_patronal', 'previsional_laboral', 'previsional_patronal',
                'incaf', 'liquido_por_pagar', 'costo_patronal_total', 'tasa_isss_patronal',
                'tasa_previsional_patronal', 'tasa_incaf', 'umbral_incaf_empleados']
        vals = ', '.join(num(pl[c]) for c in cols)
        w(f"INSERT INTO planilla_periodo (id, periodo_id, fecha_contable, {', '.join(cols)}, centro_costo, clave_evento, "
          f"partida_id) VALUES ('{uid('planilla', str(pl['periodo_mes']))}', {periodo_ref(anio, pl['periodo_mes'])}, "
          f"{fecha(pl['fecha_contable'])}, {vals}, {q(pl['centro_costo'])}, {q(pref + pl['clave_evento'])}, "
          f"'{p_id[pl['folio']]}');")

    cerrados = ', '.join(str(m) for m in d['ejercicio']['periodos_cerrados'])
    w(f"UPDATE periodo SET estado = 'CERRADO' WHERE ejercicio_id = '{ej}' AND mes IN ({cerrados});")
    w('COMMIT;')
    return '\n'.join(out) + '\n'


def generar_verificacion(d):
    saldos = {}
    for b in d['balanza']:
        c = '110501' if b['codigo'] == '1105' else b['codigo']
        saldos[c] = saldos.get(c, 0) + (float(b['debe']) - float(b['haber']))
    sep = ',' + chr(10) + '    '
    valores = sep.join(f"('{c}', {v:.2f})" for c, v in sorted(saldos.items()))
    ctl = d['controles']
    return f"""-- Generado por generar_seed_sql.py. Falla (RAISE EXCEPTION) si el seed no coincide con el Anexo L.
\\encoding UTF8
\\set ON_ERROR_STOP on
DO $$
DECLARE
    n integer;
    v numeric;
BEGIN
    SELECT COALESCE(SUM(pd.debe),0) - COALESCE(SUM(pd.haber),0) INTO v
      FROM partida_detalle pd JOIN partida p ON p.id = pd.partida_id WHERE p.estado = 'CONTABILIZADA';
    IF v <> 0 THEN RAISE EXCEPTION 'Diario global descuadrado: %', v; END IF;

    SELECT count(*) INTO n FROM (
        WITH esperado(codigo, saldo) AS (VALUES
    {valores}),
        real AS (SELECT pd.codigo_cuenta AS codigo, SUM(pd.debe - pd.haber) AS saldo
                   FROM partida_detalle pd JOIN partida p ON p.id = pd.partida_id
                  WHERE p.estado = 'CONTABILIZADA' GROUP BY pd.codigo_cuenta)
        SELECT COALESCE(e.codigo, r.codigo) FROM esperado e FULL JOIN real r ON r.codigo = e.codigo
         WHERE COALESCE(e.saldo,0) <> COALESCE(r.saldo,0)
    ) x;
    IF n <> 0 THEN RAISE EXCEPTION 'La balanza difiere del Anexo L en % cuentas', n; END IF;

    SELECT SUM(pd.debe) INTO v FROM partida_detalle pd JOIN partida p ON p.id = pd.partida_id
     WHERE p.tipo <> 'AP' AND p.fecha_contable >= DATE '2026-10-01';
    IF v <> {ctl['diario_debe']} THEN RAISE EXCEPTION 'Sumas de octubre % <> {ctl['diario_debe']}', v; END IF;

    SELECT SUM(pd.debe - pd.haber) INTO v FROM partida_detalle pd JOIN partida p ON p.id = pd.partida_id
     WHERE pd.codigo_cuenta = '110102' AND p.fecha_contable < DATE '2026-10-01';
    IF v <> 100914.25 THEN RAISE EXCEPTION 'Saldo inicial de bancos %', v; END IF;

    SELECT count(*), COALESCE(SUM(costo_unitario),0) INTO n, v FROM licencia WHERE estado = 'DISPONIBLE';
    IF n <> {ctl['licencias_disponibles']} OR v <> {ctl['inventario_licencias']} THEN
        RAISE EXCEPTION 'Existencia de licencias % uds / %', n, v; END IF;

    SELECT count(*) INTO n FROM partida WHERE estado = 'CONTABILIZADA';
    IF n <> {len(d['seed']['partidas'])} THEN RAISE EXCEPTION 'Partidas contabilizadas: %', n; END IF;
    SELECT count(*) INTO n FROM periodo WHERE estado = 'CERRADO';
    IF n <> {len(d['ejercicio']['periodos_cerrados'])} THEN RAISE EXCEPTION 'Periodos cerrados: %', n; END IF;
    SELECT count(*) INTO n FROM contador WHERE usuario IN ('contador','soporte');
    IF n <> 2 THEN RAISE EXCEPTION 'Usuarios conservados: %', n; END IF;
    SELECT count(*) INTO n FROM planilla_periodo WHERE partida_id IS NOT NULL;
    IF n <> 1 THEN RAISE EXCEPTION 'Planilla de octubre no enlazada'; END IF;
    RAISE NOTICE 'Seed del Anexo L verificado';
END $$;
"""


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--json', default=str(JSON_DEFECTO))
    ap.add_argument('--out', default=str(SQL_DEFECTO))
    a = ap.parse_args(argv)
    datos = json.loads(Path(a.json).read_text(encoding='utf-8'))
    sql = generar(datos)
    Path(a.out).parent.mkdir(parents=True, exist_ok=True)
    Path(a.out).write_text(sql, encoding='utf-8', newline='\n')
    ver = Path(a.out).with_name('verificar_anexo_l.sql')
    ver.write_text(generar_verificacion(datos), encoding='utf-8', newline=chr(10))
    print(f'OK: {a.out} ({sql.count(chr(10))} líneas) y {ver.name}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
