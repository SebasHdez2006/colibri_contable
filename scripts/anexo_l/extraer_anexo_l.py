#!/usr/bin/env python3
"""Extrae el Anexo L del informe técnico (.docx) a un JSON estructural.

Solo usa la biblioteca estándar. Falla con código distinto de 0 si alguna
validación contable no cuadra.

Uso:
    python extraer_anexo_l.py --docx Informe_tecnico_Colibri.docx [--out anexo_l.json]
"""
import argparse
import hashlib
import json
import re
import sys
import zipfile
import xml.etree.ElementTree as ET
from collections import defaultdict
from decimal import Decimal as D
from pathlib import Path

W = '{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'
NS = {'w': W[1:-1]}
CERO = D('0.00')
MESES = {'ene': 1, 'feb': 2, 'mar': 3, 'abr': 4, 'may': 5, 'jun': 6,
         'jul': 7, 'ago': 8, 'sep': 9, 'oct': 10, 'nov': 11, 'dic': 12}
ANIO = 2026
RAIZ = Path(__file__).resolve().parents[2]
SALIDA_DEFECTO = RAIZ / 'colibri-application/src/main/resources/seed/anexo_l.json'
PREFIJO_EVENTO = 'SEED-ANEXOL-2026-'


class ErrorAnexo(Exception):
    pass


# ---------------------------------------------------------------- lectura docx
def cargar_cuerpo(ruta):
    with zipfile.ZipFile(ruta) as z:
        raiz = ET.fromstring(z.read('word/document.xml'))
    return list(raiz.find('w:body', NS))


def texto_parrafo(p):
    return ''.join(t.text or '' for t in p.iter(W + 't'))


def texto_celda(tc):
    partes = [texto_parrafo(p).strip() for p in tc.findall('w:p', NS)]
    return '\n'.join(x for x in partes if x)


def estilo(p):
    st = p.find('w:pPr/w:pStyle', NS)
    return st.get(W + 'val') if st is not None else ''


def filas_tabla(tbl):
    return [[texto_celda(c) for c in r.findall('w:tc', NS)] for r in tbl.findall('w:tr', NS)]


def localizar_anexo(cuerpo):
    inicios = [i for i, el in enumerate(cuerpo)
               if el.tag == W + 'p' and estilo(el) == 'Ttulo1'
               and re.match(r'^ANEXO L\b', texto_parrafo(el).strip())]
    if len(inicios) != 1:
        raise ErrorAnexo(f'Se esperaba un único encabezado "ANEXO L"; hay {len(inicios)}')
    ini = inicios[0]
    fin = len(cuerpo)
    for i in range(ini + 1, len(cuerpo)):
        if cuerpo[i].tag == W + 'p' and estilo(cuerpo[i]) == 'Ttulo1':
            fin = i
            break
    return ini, fin


def recorrer(cuerpo, ini, fin):
    """Devuelve (tablas, notas) preservando sección y leyenda de cada tabla."""
    tablas, notas = {}, []
    seccion, leyenda = '', None
    for i in range(ini + 1, fin):
        el = cuerpo[i]
        if el.tag == W + 'p':
            st, t = estilo(el), texto_parrafo(el).strip()
            if not t:
                continue
            if st == 'Ttulo2':
                seccion = t
            elif st == 'Descripcin':
                leyenda = t
            elif st not in ('Equation', 'EquationLong'):
                notas.append({'elemento': i, 'seccion': seccion, 'texto': t})
        elif el.tag == W + 'tbl':
            filas = filas_tabla(el)
            m = re.match(r'^TABLA\s+([IVXLC]+)\s+(.*)$', leyenda or '')
            tid, titulo = (m.group(1), m.group(2)) if m else (f'T{i}', leyenda or '')
            tablas[tid] = {'id': tid, 'titulo': titulo, 'seccion': seccion, 'elemento': i,
                           'encabezados': filas[0], 'filas': filas[1:]}
            leyenda = None
    return tablas, notas


# ------------------------------------------------------------------ normalizar
def dinero(s):
    s = (s or '').strip()
    if s in ('', '—', '-', '–'):
        return None
    neg = '(' in s or '−' in s or s.startswith('-')
    num = re.sub(r'[^0-9.]', '', s)
    if not num:
        return None
    v = D(num)
    return -v if neg else v


def d2(v):
    return str((v if v is not None else CERO).quantize(D('0.01')))


def fecha_iso(texto):
    m = re.match(r'^(\d{1,2})\s+([a-záéíóú]{3})', texto.strip().lower())
    if not m:
        raise ErrorAnexo(f'Fecha no reconocida: {texto!r}')
    return f'{ANIO}-{MESES[m.group(2)]:02d}-{int(m.group(1)):02d}'


def separar_soporte(texto):
    """Separa 'Nombre (soporte)' usando balance de paréntesis."""
    ini = texto.find('(')
    if ini < 0:
        return texto.strip(), None
    nivel = 0
    for i in range(ini, len(texto)):
        if texto[i] == '(':
            nivel += 1
        elif texto[i] == ')':
            nivel -= 1
            if nivel == 0:
                if texto[i + 1:].strip():
                    return texto.strip(), None
                return texto[:ini].strip(), texto[ini + 1:i].strip()
    raise ErrorAnexo(f'Paréntesis sin cerrar en: {texto!r}')


# ---------------------------------------------------------------------- parsers
def parsear_diario(filas):
    partidas, control, actual = [], None, None
    for f in filas:
        fecha, folio, cuenta, nombre, debe, haber = (f + [''] * 6)[:6]
        if not cuenta and 'SUMAS' in nombre.upper():
            control = {'debe': dinero(debe), 'haber': dinero(haber)}
            continue
        if folio:
            nom, soporte = separar_soporte(nombre)
            actual = {'fecha': fecha_iso(fecha), 'folio': folio, 'tipo': folio[:2],
                      'concepto': nombre[:500], 'soporte_texto': soporte, 'lineas': []}
            partidas.append(actual)
            linea_nombre = nom
        else:
            linea_nombre = separar_soporte(nombre)[0]
        if actual is None:
            raise ErrorAnexo('Línea de diario sin partida')
        actual['lineas'].append({'cuenta': cuenta, 'nombre': linea_nombre,
                                 'debe': dinero(debe) or CERO, 'haber': dinero(haber) or CERO})
    return partidas, control


def parsear_balanza(filas):
    saldos, control = [], None
    for f in filas:
        if not f[0]:
            control = {'debe': dinero(f[2]), 'haber': dinero(f[3])}
            continue
        saldos.append({'codigo': f[0], 'nombre': f[1],
                       'debe': dinero(f[2]) or CERO, 'haber': dinero(f[3]) or CERO})
    return saldos, control


def parsear_saldos_iniciales_mayor(filas):
    si = {}
    for f in filas:
        m = re.search(r'SI D ([\d,]+\.\d{2})', f[1])
        if m:
            si[f[0].split()[0]] = dinero(m.group(1))
    return si


def neto(d, h):
    return d - h


def a_lineas(netos):
    return [{'cuenta': c, 'debe': v if v > 0 else CERO, 'haber': -v if v < 0 else CERO}
            for c, v in sorted(netos.items()) if v != 0]


# --------------------------------------------------------------------- derivar
def derivar(partidas, balanza, advertencias, supuestos):
    mov = defaultdict(lambda: CERO)
    for p in partidas:
        for l in p['lineas']:
            mov[l['cuenta']] += l['debe'] - l['haber']
    final = defaultdict(lambda: CERO)
    for b in balanza:
        final[b['codigo']] += b['debe'] - b['haber']
    # 1105 es cuenta de control en la balanza; el diario usa 110501..110503.
    control_1105 = final.pop('1105', CERO)
    final['110501'] += control_1105
    supuestos.append('Control 1105 de la balanza asignado íntegramente a 110501 al 31-oct '
                     '(110502 y 110503 se consumen en DI-2026-000007).')
    cuentas = set(final) | set(mov)
    apertura = {c: final[c] - mov[c] for c in cuentas}
    apertura = {c: v for c, v in apertura.items() if v != 0}
    td = sum(v for v in apertura.values() if v > 0)
    th = -sum(v for v in apertura.values() if v < 0)
    return apertura, td, th, mov, final


def lineas_de(partidas, folio):
    return next(p for p in partidas if p['folio'] == folio)['lineas']


def monto(partidas, folio, cuenta, lado):
    return sum((l[lado] for l in lineas_de(partidas, folio) if l['cuenta'] == cuenta), CERO)


def dec_json(o):
    if isinstance(o, D):
        return d2(o)
    raise TypeError(type(o))


def construir(docx):
    cuerpo = cargar_cuerpo(docx)
    ini, fin = localizar_anexo(cuerpo)
    tablas, notas = recorrer(cuerpo, ini, fin)
    errores, advertencias, supuestos = [], [], []

    def req(tid):
        if tid not in tablas:
            raise ErrorAnexo(f'Falta la tabla {tid} en el Anexo L')
        return tablas[tid]

    partidas, ctl_diario = parsear_diario(req('LXXX')['filas'])
    balanza, ctl_balanza = parsear_balanza(req('LXXXIII')['filas'])
    si_mayor = parsear_saldos_iniciales_mayor(req('LXXXII')['filas'])

    # ------------------------------------------------------------ validaciones
    def chequear(cond, msg):
        if not cond:
            errores.append(msg)

    for p in partidas:
        d = sum(l['debe'] for l in p['lineas'])
        h = sum(l['haber'] for l in p['lineas'])
        chequear(d == h, f'Partida {p["folio"]} no cuadra: {d} vs {h}')
    td = sum(l['debe'] for p in partidas for l in p['lineas'])
    th = sum(l['haber'] for p in partidas for l in p['lineas'])
    chequear(ctl_diario and td == ctl_diario['debe'] and th == ctl_diario['haber'],
             f'Sumas del diario {td}/{th} difieren del control {ctl_diario}')
    chequear(len(partidas) == 18, f'Se esperaban 18 partidas y hay {len(partidas)}')
    bd = sum(b['debe'] for b in balanza)
    bh = sum(b['haber'] for b in balanza)
    chequear(ctl_balanza and bd == ctl_balanza['debe'] and bh == ctl_balanza['haber'] and bd == bh,
             f'Balanza descuadrada {bd}/{bh}')

    er = req('LXXXIV')['filas']
    util = next(dinero(f[-1]) for f in er if f[0].startswith('UTILIDAD NETA'))
    chequear(util == D('28312.37'), f'Utilidad neta {util}')
    esf = req('LXXXV')['filas']
    tot = next(f for f in esf if f[0].startswith('TOTAL ACTIVOS'))
    chequear(dinero(tot[1]) == dinero(tot[3]) == D('183286.78'), f'ESF descuadrado {tot}')

    apertura, ad, ah, mov, final = derivar(partidas, balanza, advertencias, supuestos)
    chequear(ad == ah, f'Apertura descuadrada {ad}/{ah}')
    chequear(ad == D('503615.83'), f'Apertura esperada 503,615.83 y es {ad}')
    # Cruce con saldos iniciales del mayor
    def suma(pref):
        return sum((v for c, v in apertura.items() if c.startswith(pref)), CERO)
    chequear(apertura.get('110102') == si_mayor.get('110102'), 'SI 110102 difiere del mayor')
    chequear(suma('1102') == si_mayor.get('1102'), f'SI 1102 {suma("1102")} difiere del mayor')
    chequear(suma('1105') == si_mayor.get('1105'), f'SI 1105 {suma("1105")} difiere del mayor')
    chequear(apertura.get('110702') == si_mayor.get('110702'), 'SI 110702 difiere del mayor')
    # Saldo final recalculado = balanza
    for c in set(final) | set(mov):
        chequear(apertura.get(c, CERO) + mov[c] == final[c], f'Saldo final {c} no cuadra')

    kardex = req('LXXIX')['filas']
    fin_k = next(f for f in kardex if f[1].startswith('Existencia final'))
    chequear(fin_k[2] == '15' and dinero(fin_k[4]) == D('1680.00') == final['110702'],
             'Kárdex final no coincide con 110702')

    # ------------------------------------------------------------- derivados
    chequear(all(len(c) == 6 for c in apertura), 'Código de cuenta de 6 dígitos esperado')
    di5 = 'DI-2026-000005'
    prev_lab = monto(partidas, di5, '210501', 'haber')
    nominal = (prev_lab / D('0.0725')).quantize(D('0.01'))
    isss_lab = monto(partidas, di5, '210401', 'haber')
    isr = monto(partidas, di5, '210205', 'haber')
    liquido = monto(partidas, di5, '210301', 'haber')
    isss_pat = monto(partidas, di5, '210402', 'haber')
    prev_pat = monto(partidas, di5, '210502', 'haber')
    incaf = monto(partidas, di5, '210801', 'haber')
    costo_patronal = sum(l['debe'] for l in lineas_de(partidas, di5))
    chequear(nominal - isss_lab - prev_lab - isr == liquido, 'Planilla: líquido no cuadra')
    chequear(nominal + isss_pat + prev_pat + incaf == costo_patronal, 'Planilla: costo patronal no cuadra')
    chequear(nominal == D('15500.00'), f'Salario nominal derivado {nominal}')
    planilla = {'folio': di5, 'periodo_mes': 10, 'fecha_contable': '2026-10-17', 'empleados': 15,
                'salario_nominal': nominal, 'tasa_isss_laboral': D('0.03'),
                'tasa_previsional_laboral': D('0.0725'), 'isr_retenido': isr,
                'otras_deducciones': CERO, 'isss_laboral': isss_lab, 'isss_patronal': isss_pat,
                'previsional_laboral': prev_lab, 'previsional_patronal': prev_pat, 'incaf': incaf,
                'liquido_por_pagar': liquido, 'costo_patronal_total': costo_patronal,
                'tasa_isss_patronal': D('0.075'), 'tasa_previsional_patronal': D('0.0875'),
                'tasa_incaf': D('0.01'), 'umbral_incaf_empleados': 10, 'centro_costo': 'CC-01',
                'clave_evento': 'PLANILLA-2026-10', 'supuesto': True,
                'fuente': 'Derivado de DI-2026-000005 (base previsional 7.25 %)'}

    aj1 = 'AJ-2026-000001'
    activos_def = [('AF-COMPUTADORAS', 'Computadoras y periféricos', '120101', '120201', '610104', 36),
                   ('AF-SERVIDORES', 'Servidores y equipos de red', '120102', '120202', '520103', 48),
                   ('AF-MOBILIARIO', 'Mobiliario y equipo de oficina', '120103', '120203', '610104', 60)]
    activos, cuotas = [], []
    for cod, nom, ca, cd, cg, vida in activos_def:
        costo = final[ca]
        cuota = monto(partidas, aj1, cd, 'haber')
        chequear((costo / vida).quantize(D('0.01')) == cuota, f'Cuota de {cod} no coincide con costo/vida')
        activos.append({'codigo': cod, 'nombre': nom, 'cuenta_activo': ca, 'cuenta_dep_acum': cd,
                        'cuenta_gasto': cg, 'costo': costo, 'vida_meses': vida,
                        'fecha_uso': '2024-03-01', 'valor_residual': CERO,
                        'depreciacion_acum': -final[cd], 'supuesto_fecha_uso': True})
        cuotas.append({'activo': cod, 'periodo_mes': 10, 'importe': cuota, 'folio': aj1})
    sw = final['120301']
    chequear(sw == D('7200.00') and -final['120401'] == D('1800.00'), 'Software adquirido inesperado')
    intangibles = [{'codigo': 'AI-SOFTWARE', 'nombre': 'Software adquirido de uso interno',
                    'cuenta_activo': '120301', 'cuenta_amort_acum': '120401', 'cuenta_gasto': '610105',
                    'costo': sw, 'vida_meses': 36, 'fecha_uso': '2026-02-01',
                    'valor_residual': CERO, 'amortizacion_acum': -final['120401'],
                    'supuesto_fecha_uso': True}]

    # ------------------------------------------------------------ auxiliares
    ev = lambda f: PREFIJO_EVENTO + f
    nit_empresa = 'SIMULADO-NIT'
    clientes = [
        ('SIM-ANEXOL-DSM', 'Distribuidora San Marcos', False),
        ('SIM-ANEXOL-CLN', 'Clínica Los Naranjos', False),
        ('SIM-ANEXOL-CDEV', 'Cliente de desarrollo a medida (OS-2026-014)', True),
        ('SIM-ANEXOL-CSAAS', 'Cliente de suscripción SaaS', True),
        ('SIM-ANEXOL-CSOP', 'Cliente de soporte y mantenimiento', True),
    ]
    proveedores = [('SIM-ANEXOL-NUBE', 'Proveedor de nube', True),
                   ('SIM-ANEXOL-RENTA', 'Arrendador de oficina', True)]
    terceros = ([{'nit': n, 'razon_social': r, 'es_cliente': True, 'es_proveedor': False, 'supuesto': s}
                 for n, r, s in clientes]
                + [{'nit': n, 'razon_social': r, 'es_cliente': False, 'es_proveedor': True, 'supuesto': s}
                   for n, r, s in proveedores])

    def doc(codigo, tipo, emisor, receptor, tercero, fecha, base, iva, total, folio, evento, supuesto=False):
        chequear(base + iva == total, f'Documento {codigo}: base + IVA ≠ total')
        return {'codigo': codigo, 'tipo': tipo, 'emisor_nit': emisor, 'receptor_nit': receptor,
                'tercero_nit': tercero, 'fecha': fecha, 'base_gravada': base, 'iva': iva,
                'total': total, 'folio': folio, 'tipo_evento': evento, 'supuesto': supuesto}

    m = lambda f, c, l: monto(partidas, f, c, l)
    documentos = [
        doc('DTE-2026-278', 'CCF', nit_empresa, 'SIM-ANEXOL-CDEV', 'SIM-ANEXOL-CDEV', '2026-09-20',
            D('8400.00'), D('1092.00'), D('9492.00'), None, None, True),
        doc('DTE-2026-314', 'CCF', nit_empresa, 'SIM-ANEXOL-CDEV', 'SIM-ANEXOL-CDEV', '2026-10-02',
            m('DI-2026-000001', '410101', 'haber'), m('DI-2026-000001', '210201', 'haber'),
            m('DI-2026-000001', '110202', 'debe'), 'DI-2026-000001', 'VENTA'),
        doc('BC-2026-188', 'RECIBO', 'SIM-ANEXOL-CDEV', nit_empresa, 'SIM-ANEXOL-CDEV', '2026-10-03',
            m('IN-2026-000001', '110102', 'debe'), CERO, m('IN-2026-000001', '110102', 'debe'), 'IN-2026-000001', 'COBRO'),
        doc('DTE-2026-315', 'CCF', nit_empresa, 'SIM-ANEXOL-CSAAS', 'SIM-ANEXOL-CSAAS', '2026-10-08',
            m('DI-2026-000002', '410402', 'haber'), m('DI-2026-000002', '210201', 'haber'),
            m('DI-2026-000002', '110203', 'debe'), 'DI-2026-000002', 'VENTA'),
        doc('BC-2026-191', 'RECIBO', 'SIM-ANEXOL-CSAAS', nit_empresa, 'SIM-ANEXOL-CSAAS', '2026-10-08',
            m('IN-2026-000002', '110102', 'debe'), CERO, m('IN-2026-000002', '110102', 'debe'), 'IN-2026-000002', 'COBRO'),
        doc('DTE-2026-316', 'CCF', nit_empresa, 'SIM-ANEXOL-DSM', 'SIM-ANEXOL-DSM', '2026-10-15',
            m('DI-2026-000004', '410602', 'haber'), m('DI-2026-000004', '210201', 'haber'),
            m('DI-2026-000004', '110201', 'debe'), 'DI-2026-000004', 'VENTA'),
        doc('DTE-2026-317', 'CCF', nit_empresa, 'SIM-ANEXOL-CLN', 'SIM-ANEXOL-CLN', '2026-10-28',
            m('DI-2026-000006', '410602', 'haber'), m('DI-2026-000006', '210201', 'haber'),
            m('DI-2026-000006', '110201', 'debe'), 'DI-2026-000006', 'VENTA'),
        doc('DTE-2026-318', 'CCF', nit_empresa, 'SIM-ANEXOL-CSOP', 'SIM-ANEXOL-CSOP', '2026-10-30',
            m('IN-2026-000003', '210704', 'haber'), m('IN-2026-000003', '210201', 'haber'),
            m('IN-2026-000003', '110102', 'debe'), 'IN-2026-000003', 'ANTICIPO'),
        doc('ANEXOL-COMPRA-NUBE-OCT', 'CCF', 'SIM-ANEXOL-NUBE', nit_empresa, 'SIM-ANEXOL-NUBE', '2026-10-22',
            m('EG-2026-000003', '510201', 'debe'), m('EG-2026-000003', '110301', 'debe'),
            m('EG-2026-000003', '110102', 'haber'), 'EG-2026-000003', 'COMPRA', True),
        doc('ANEXOL-COMPRA-RENTA-OCT', 'CCF', 'SIM-ANEXOL-RENTA', nit_empresa, 'SIM-ANEXOL-RENTA', '2026-10-24',
            m('EG-2026-000004', '610102', 'debe'), m('EG-2026-000004', '110301', 'debe'),
            m('EG-2026-000004', '110102', 'haber'), 'EG-2026-000004', 'COMPRA', True),
    ]
    aplicaciones = [
        {'origen': 'DTE-2026-278', 'pago': 'BC-2026-188', 'folio': 'IN-2026-000001',
         'importe': m('IN-2026-000001', '110202', 'haber'), 'fecha': '2026-10-03',
         'concepto': 'Aplicación parcial de BC-2026-188 a DTE-2026-278'},
        {'origen': 'DTE-2026-315', 'pago': 'BC-2026-191', 'folio': 'IN-2026-000002',
         'importe': m('IN-2026-000002', '110203', 'haber'), 'fecha': '2026-10-08',
         'concepto': 'Cobro de DTE-2026-315 con BC-2026-191'}]

    # Kárdex: lotes y licencias
    lic = []
    for n in range(1, 19):
        base = {'numero': n, 'costo_unitario': D('100.00')}
        if n <= 9:
            base.update(estado='ENTREGADA', cliente='SIM-ANEXOL-DSM', documento='DTE-2026-316',
                        fecha='2026-10-15')
        elif n <= 15:
            base.update(estado='ENTREGADA', cliente='SIM-ANEXOL-CLN', documento='DTE-2026-317',
                        fecha='2026-10-28')
        else:
            base.update(estado='DISPONIBLE')
        lic.append(base)
    lic2 = [{'numero': n, 'costo_unitario': D('115.00'), 'estado': 'DISPONIBLE'} for n in range(1, 13)]
    chequear(sum(1 for l in lic if l['estado'] == 'DISPONIBLE') + len(lic2) == 15, 'Licencias disponibles ≠ 15')
    lotes = [
        {'codigo_lote': 'LI-2026-03', 'version': '2.0', 'unidades_estimadas': 18,
         'costo_total': D('1800.00'), 'costo_unitario': D('100.00'), 'unidades_disponibles': 3,
         'fecha_liberacion': '2026-09-15', 'folio': None, 'licencias': lic},
        {'codigo_lote': 'LI-2026-04', 'version': '2.0', 'unidades_estimadas': 12,
         'costo_total': D('1380.00'), 'costo_unitario': D('115.00'), 'unidades_disponibles': 12,
         'fecha_liberacion': '2026-10-10', 'folio': 'DI-2026-000003', 'licencias': lic2}]

    di7 = 'DI-2026-000007'
    mod, srv, cif = (m(di7, '110501', 'haber'), m(di7, '110502', 'haber'), m(di7, '110503', 'haber'))
    chequear((mod / 12) == (mod / 12).to_integral_value() and cif == (mod / 12 * 3), 'Horas OS-2026-014 no cuadran')
    orden = {'codigo': 'OS-2026-014', 'descripcion': 'Desarrollo a medida; hito aceptado en octubre de 2026',
             'cliente': 'SIM-ANEXOL-CDEV', 'presupuesto': mod + srv + cif,
             'precio_venta': m('DI-2026-000001', '410101', 'haber'), 'estado': 'ENTREGADA',
             'folio_consumos': di7, 'supuesto_presupuesto': True,
             'consumos': [
                 {'tipo': 'MOD', 'descripcion': 'Mano de obra directa: %s horas' % (mod / 12).to_integral_value(),
                  'cantidad': mod / 12, 'tarifa': D('12.00'), 'importe': mod},
                 {'tipo': 'SERVICIO', 'descripcion': 'Servicios directos del proyecto',
                  'cantidad': D('1'), 'tarifa': srv, 'importe': srv},
                 {'tipo': 'CIF', 'descripcion': 'CIF aplicado a $3.00 por hora',
                  'cantidad': mod / 12, 'tarifa': D('3.00'), 'importe': cif}]}

    # Partidas del seed
    seed_partidas = [{
        'clave': 'AP-APERTURA', 'folio': 'AP-2026-000001', 'tipo': 'AP', 'fecha': '2026-09-30',
        'concepto': 'Apertura: saldos acumulados al 30 de septiembre de 2026 (derivados del Anexo L)',
        'soporte': 'ANEXO-L-APERTURA', 'origen': 'MANUAL', 'documento': None, 'tipo_evento': None,
        'lineas': a_lineas(apertura)}]
    for p in partidas:
        refs = re.findall(r'\b(?:DTE|BC|TP|LI|OS)-\d{4}-\d+\b', p['soporte_texto'] or '')
        seed_partidas.append({
            'clave': p['folio'], 'folio': p['folio'], 'tipo': p['tipo'], 'fecha': p['fecha'],
            'concepto': p['concepto'],
            'soporte': (', '.join(dict.fromkeys(refs)) or (p['soporte_texto'] or p['folio']))[:300],
            'origen': 'MANUAL' if p['tipo'] == 'AJ' else 'AUTOMATICO',
            'documento': next((d['codigo'] for d in documentos if d['folio'] == p['folio']), None),
            'tipo_evento': next((d['tipo_evento'] for d in documentos if d['folio'] == p['folio']), None),
            'lineas': [{'cuenta': l['cuenta'], 'debe': l['debe'], 'haber': l['haber'],
                        'auxiliar': 'OS-2026-014' if p['folio'] == di7 else None} for l in p['lineas']]})
    maxfolio = {}
    for p in seed_partidas:
        k = p['tipo']
        maxfolio[k] = max(maxfolio.get(k, 0), int(p['folio'].split('-')[-1]))

    # Advertencias del documento
    xci = req('XCI')['filas']
    infra = next(f for f in xci if f[0].startswith('Infraestructura'))
    if dinero(infra[3]) != final['510203']:
        advertencias.append(f'Tabla XCI muestra infraestructura saas {dinero(infra[3])} pero la balanza (510203) '
                            f'y el total usan {final["510203"]}; se siembra {final["510203"]}.')
    for tid in ('LXXXIII',):
        faltan = [b['codigo'] for b in balanza if b['codigo'] == '510401']
    if faltan:
        advertencias.append('Cuenta 510401 no existe en el catálogo V2; se agrega en V20__catalogo_costo_soporte.sql.')

    if errores:
        raise ErrorAnexo('Validaciones fallidas:\n - ' + '\n - '.join(errores))

    sha = hashlib.sha256(Path(docx).read_bytes()).hexdigest()
    return {
        'meta': {'docx': Path(docx).name, 'docx_sha256': sha, 'corte': '2026-10-31', 'moneda': 'USD',
                 'version_esquema': 1, 'prefijo_evento': PREFIJO_EVENTO},
        'empresa': {'razon_social': 'Soluciones Empresariales Colibrí, S.A. de C.V.',
                    'nombre_comercial': 'Colibrí', 'nit': nit_empresa, 'nrc': 'SIMULADO-NRC',
                    'moneda': 'USD', 'zona_horaria': 'America/El_Salvador',
                    'actividad_economica': 'Programación informática (CAT-019)'},
        'ejercicio': {'anio': ANIO, 'periodos_cerrados': list(range(1, 10))},
        'controles': {
            'diario_debe': ctl_diario['debe'], 'diario_haber': ctl_diario['haber'],
            'balanza_debe': ctl_balanza['debe'], 'balanza_haber': ctl_balanza['haber'],
            'apertura_debe': ad, 'apertura_haber': ah, 'utilidad_neta': util,
            'activo_total': D('183286.78'), 'licencias_disponibles': 15, 'inventario_licencias': D('1680.00')},
        'balanza': balanza,
        'saldos_iniciales_mayor': si_mayor,
        'diario': [{k: v for k, v in p.items()} for p in partidas],
        'derivados': {'apertura': a_lineas(apertura), 'planilla_octubre': planilla,
                      'supuestos': supuestos},
        'seed': {
            'terceros': terceros, 'documentos': documentos, 'aplicaciones_pago': aplicaciones,
            'partidas': seed_partidas, 'folio_secuencia': [
                {'anio': ANIO, 'tipo': k, 'ultimo': v} for k, v in sorted(maxfolio.items())],
            'producto_versiones': [{'producto': 'COL-INV', 'version': '2.0', 'estado': 'LIBERADA'}],
            'lotes': lotes, 'ordenes': [orden], 'activos_fijos': activos,
            'cuotas_depreciacion': cuotas, 'activos_intangibles': intangibles,
            'planillas': [planilla]},
        'tablas': tablas,
        'advertencias': advertencias,
        'notas': notas,
    }


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--docx', required=True)
    ap.add_argument('--out', default=str(SALIDA_DEFECTO))
    args = ap.parse_args(argv)
    try:
        datos = construir(args.docx)
    except ErrorAnexo as e:
        print(f'ERROR: {e}', file=sys.stderr)
        return 1
    salida = Path(args.out)
    salida.parent.mkdir(parents=True, exist_ok=True)
    salida.write_text(json.dumps(datos, ensure_ascii=False, indent=1, default=dec_json) + '\n',
                      encoding='utf-8')
    print(f'OK: {salida} ({len(datos["seed"]["partidas"])} partidas, '
          f'{len(datos["tablas"])} tablas, {len(datos["advertencias"])} advertencias)')
    for a in datos['advertencias']:
        print('ADVERTENCIA:', a)
    return 0


if __name__ == '__main__':
    sys.exit(main())
