#!/usr/bin/env python3
"""Extrae el informe técnico (.docx) actualizado a un JSON estructural (esquema v2).

Solo usa la biblioteca estándar. Falla con código distinto de 0 si alguna
validación contable no cuadra.

Fuentes del documento (todas se indexan por su leyenda "TABLA <romano><título>"):
  XX    catálogo base de cuentas (181 cuentas)
  LXXIX kárdex de licencias Colibrí Inventarios
  LXXX  lotes de licencias al 31 de octubre
  LXXXI libro diario de octubre (48 partidas)
  LXXXIV balanza de comprobación ajustada
  LXXXV/LXXXVI estado de resultados y situación financiera
  CIII  costo y precio de los hitos aceptados en octubre
  CXII  asiento de apertura AP-2026-000001
  CXIV  partida técnica AJ-2026-000099 (cobertura de catálogo, noviembre)
  XCVIII estado de lotes LI-2026-03 a LI-2026-06 al 8 de noviembre

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

# Cuentas de control de la balanza y su desagregación en el mayor (documentada
# en la nota de trazabilidad de la tabla CXII).
CONTROL_1105 = {'110501': D('9840.00'), '110502': D('3813.00'), '110503': D('2460.00')}

# Subcuentas de movimiento sin operación realista al corte: la partida técnica
# AJ-2026-000099 del 8 de noviembre las cubre con $1.00 en su naturaleza
# (tabla CXIV). El balance de la partida cuadra con $1.00 de Bancos.
COBERTURA_TEORICA = [
    ('110101', 'D'), ('110205', 'D'), ('110303', 'D'), ('110402', 'D'),
    ('110403', 'D'), ('110404', 'D'), ('120302', 'D'), ('120501', 'D'),
    ('310302', 'D'), ('410701', 'D'), ('410702', 'D'), ('510303', 'D'),
    ('610302', 'D'), ('710301', 'D'), ('710402', 'D'),
    ('110801', 'H'), ('120402', 'H'), ('210103', 'H'), ('210603', 'H'),
    ('210604', 'H'), ('210703', 'H'), ('210802', 'H'), ('210803', 'H'),
    ('210804', 'H'), ('220201', 'H'), ('310401', 'H'), ('410102', 'H'),
    ('410603', 'H'), ('710101', 'H'), ('710302', 'H'), ('710401', 'H'),
]


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


_ROMANO = re.compile(r'^M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})$')


def _valor_romano(s):
    valores = {'I': 1, 'V': 5, 'X': 10, 'L': 50, 'C': 100, 'D': 500, 'M': 1000}
    total, prev = 0, 0
    for ch in reversed(s):
        v = valores[ch]
        total += -v if v < prev else v
        prev = max(prev, v)
    return total


def parsear_leyenda(leyenda):
    """'TABLA LXXXIEXTRACTO...' -> ('LXXXI', 'EXTRACTO...')."""
    m = re.match(r'^TABLA\s+(.+)$', (leyenda or '').strip())
    if not m:
        return None, ''
    resto = m.group(1)
    for n in range(min(8, len(resto)), 0, -1):
        pref = resto[:n]
        if _ROMANO.match(pref):
            try:
                v = _valor_romano(pref)
            except KeyError:
                continue
            if 1 <= v <= 130:
                return pref, resto[n:].strip()
    return None, resto


def recorrer_global(cuerpo):
    """Indexa todas las tablas del documento por su leyenda 'TABLA <romano>'."""
    tablas, seccion, leyenda = {}, '', None
    for i, el in enumerate(cuerpo):
        if el.tag == W + 'p':
            st, t = estilo(el), texto_parrafo(el).strip()
            if not t:
                continue
            if st == 'Ttulo2':
                seccion = t
            elif st == 'Descripcin':
                leyenda = t
        elif el.tag == W + 'tbl':
            filas = filas_tabla(el)
            tid, titulo = parsear_leyenda(leyenda)
            if tid is None:
                tid, titulo = f'T{i}', leyenda or ''
            tablas[tid] = {'id': tid, 'titulo': titulo, 'seccion': seccion,
                           'elemento': i, 'encabezados': filas[0], 'filas': filas[1:]}
            leyenda = None
    return tablas


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
    """Libro diario LXXXI: Fecha | N.º partida | Código/Ref. | Cuenta y concepto | Debe | Haber."""
    partidas, control, actual = [], None, None
    for f in filas:
        f = (f + [''] * 6)[:6]
        fecha, folio, codigo, nombre, debe, haber = (x.strip() for x in f)
        if 'SUMAS' in nombre.upper():
            control = {'debe': dinero(debe), 'haber': dinero(haber)}
            continue
        if folio:
            actual = {'fecha': fecha_iso(fecha), 'folio': folio, 'tipo': folio[:2],
                      'concepto': nombre[:500], 'lineas': []}
            partidas.append(actual)
            continue
        if actual is None:
            raise ErrorAnexo(f'Línea de diario sin partida: {f}')
        cuenta = codigo if re.match(r'^\d{5,6}$', codigo) else ''
        if not cuenta:
            raise ErrorAnexo(f'Cuenta no reconocida en diario {actual["folio"]}: {f}')
        nom, soporte = separar_soporte(nombre)
        actual['lineas'].append({'cuenta': cuenta, 'nombre': nom,
                                 'soporte_texto': soporte,
                                 'debe': dinero(debe) or CERO,
                                 'haber': dinero(haber) or CERO})
    return partidas, control


def parsear_saldos(filas):
    """Balanza LXXXIV y apertura CXII: Código | Cuenta | Debe | Haber."""
    saldos, control = [], None
    for f in filas:
        if not f[0]:
            control = {'debe': dinero(f[2]), 'haber': dinero(f[3])}
            continue
        saldos.append({'codigo': f[0], 'nombre': f[1],
                       'debe': dinero(f[2]) or CERO, 'haber': dinero(f[3]) or CERO})
    return saldos, control


def parsear_lotes(filas):
    """Tabla LXXX: Lote | Producto | Liberación | Unid. | Costo unit. | Costo | Entregadas | Disponibles | Costo disp."""
    lotes = []
    for f in filas:
        f = (f + [''] * 9)[:9]
        if not f[0] or f[0].lower().startswith('total'):
            continue
        lib = f[2].strip()
        m = re.match(r'^(\d{1,2})\s+([a-záéíóú]{3})\.?$', lib.lower())
        lotes.append({'codigo_lote': f[0].strip(), 'producto': f[1].strip(),
                      'fecha_liberacion': f'{ANIO}-{MESES[m.group(2)]:02d}-{int(m.group(1)):02d}' if m else None,
                      'unidades': int(f[3]), 'costo_unitario': dinero(f[4]),
                      'costo_total': dinero(f[5]), 'entregadas': int(f[6]),
                      'disponibles': int(f[7]), 'costo_disponible': dinero(f[8])})
    return lotes


def parsear_catalogo(filas):
    return [{'codigo': f[0].strip(), 'nombre': f[1].strip()} for f in filas if f[0].strip()]


def a_lineas(netos):
    return [{'cuenta': c, 'debe': v if v > 0 else CERO, 'haber': -v if v < 0 else CERO}
            for c, v in sorted(netos.items()) if v != 0]


def dec_json(o):
    if isinstance(o, D):
        return d2(o)
    raise TypeError(type(o))


def licencias_lote(n, entregadas, costo_unit, asignaciones=None):
    """Genera las filas de licencia de un lote. `asignaciones` = lista de
    (hasta, estado, cliente, documento, fecha) aplicada en orden de número."""
    out = []
    for i in range(1, n + 1):
        estado, cli, doc, fec = 'DISPONIBLE', None, None, None
        for hasta, est, c, d, fch in (asignaciones or []):
            if i <= hasta:
                estado, cli, doc, fec = est, c, d, fch
                break
        out.append({'numero': i, 'costo_unitario': costo_unit, 'estado': estado,
                    'cliente': cli, 'documento': doc, 'fecha_entrega': fec})
    return out


# --------------------------------------------------------------------- derivar
def construir(docx):
    cuerpo = cargar_cuerpo(docx)
    tablas = recorrer_global(cuerpo)
    errores, advertencias, supuestos = [], [], []

    def req(tid):
        if tid not in tablas:
            raise ErrorAnexo(f'Falta la tabla {tid} en el informe')
        return tablas[tid]

    def chequear(cond, msg):
        if not cond:
            errores.append(msg)

    partidas, ctl_diario = parsear_diario(req('LXXXI')['filas'])
    balanza, ctl_balanza = parsear_saldos(req('LXXXIV')['filas'])
    apertura_rows, ctl_apertura = parsear_saldos(req('CXII')['filas'])
    lotes_tabla = parsear_lotes(req('LXXX')['filas'])
    catalogo = parsear_catalogo(req('XX')['filas'])

    # ------------------------------------------------------------ validaciones
    for p in partidas:
        d = sum(l['debe'] for l in p['lineas'])
        h = sum(l['haber'] for l in p['lineas'])
        chequear(d == h, f'Partida {p["folio"]} no cuadra: {d} vs {h}')
    td = sum(l['debe'] for p in partidas for l in p['lineas'])
    th = sum(l['haber'] for p in partidas for l in p['lineas'])
    chequear(ctl_diario and td == ctl_diario['debe'] and th == ctl_diario['haber'],
             f'Sumas del diario {td}/{th} difieren del control {ctl_diario}')
    chequear(len(partidas) == 48, f'Se esperaban 48 partidas y hay {len(partidas)}')
    chequear(td == D('403869.85'), f'Total del diario esperado $403,869.85 y es {td}')
    bd = sum(b['debe'] for b in balanza)
    bh = sum(b['haber'] for b in balanza)
    chequear(ctl_balanza and bd == ctl_balanza['debe'] and bh == ctl_balanza['haber'] and bd == bh,
             f'Balanza descuadrada {bd}/{bh}')
    chequear(bd == D('1072672.90'), f'Balanza esperada $1,072,672.90 y es {bd}')
    ad = sum(a['debe'] for a in apertura_rows)
    ah = sum(a['haber'] for a in apertura_rows)
    chequear(ctl_apertura and ad == ah == D('972820.15'),
             f'Apertura descuadrada {ad}/{ah} (esperada $972,820.15)')
    chequear(len(apertura_rows) == 77, f'Apertura: {len(apertura_rows)} filas (esperadas 77)')

    # Estados financieros (controles del informe)
    er = req('LXXXV')['filas']
    def buscar(filas, prefijo, col=-1):
        return next((dinero(f[col]) for f in filas
                     if f and f[0].strip().upper().startswith(prefijo)), None)
    util_neta = buscar(er, 'UTILIDAD NETA DEL PERÍODO')
    ventas_netas = buscar(er, 'VENTAS NETAS', 3)
    util_bruta = buscar(er, 'UTILIDAD BRUTA')
    util_operacion = buscar(er, 'UTILIDAD DE OPERACIÓN')
    util_antes_isr = buscar(er, 'UTILIDAD ANTES DE ISR')
    chequear(util_neta == D('51632.41'), f'Utilidad neta {util_neta}')
    chequear(ventas_netas == D('650775.00') and util_bruta == D('311517.61')
             and util_operacion == D('78560.59') and util_antes_isr == D('73760.59'),
             f'ER: ventas {ventas_netas}, bruta {util_bruta}, operación {util_operacion}, antes ISR {util_antes_isr}')
    esf = req('LXXXVI')['filas']
    tot_act = next(f for f in esf if f[0].strip().upper().startswith('TOTAL ACTIVOS'))
    chequear(dinero(tot_act[1]) == dinero(tot_act[3]) == D('410599.22'), f'ESF descuadrado {tot_act}')

    # Apertura por cuenta de 6 dígitos (se desagrega el control 1105)
    apertura = {}
    for a in apertura_rows:
        c = a['codigo']
        neto = a['debe'] - a['haber']
        if c == '1105':
            chequear(neto == sum(CONTROL_1105.values()), f'Control 1105 apertura {neto}')
            for sub, v in CONTROL_1105.items():
                apertura[sub] = v
        else:
            apertura[c] = neto
    apertura = {c: v for c, v in apertura.items() if v != 0}
    nombres_apertura = {a['codigo']: a['nombre'] for a in apertura_rows}
    nombres_apertura.update({
        '110501': 'Mano de obra directa de órdenes',
        '110502': 'Servicios directos de órdenes',
        '110503': 'Indirectos aplicados a órdenes'})

    def nombre_apertura(c):
        return nombres_apertura.get(c, '')

    mov = defaultdict(lambda: CERO)
    for p in partidas:
        for l in p['lineas']:
            mov[l['cuenta']] += l['debe'] - l['haber']
    final = defaultdict(lambda: CERO)
    for b in balanza:
        final[b['codigo']] += b['debe'] - b['haber']
    # La balanza muestra el control 1105; el mayor y el diario usan 110501..110503.
    # Se verifica el grupo y se guardan los saldos finales de cada subcuenta.
    control_1105 = final.pop('1105', CERO)
    for c in set(final) | set(mov) | set(apertura):
        if c in CONTROL_1105:
            continue
        chequear(apertura.get(c, CERO) + mov[c] == final.get(c, CERO),
                 f'Saldo final {c}: apertura {apertura.get(c, CERO)} + mov {mov[c]} != {final.get(c, CERO)}')
    for s in CONTROL_1105:
        final[s] = apertura.get(s, CERO) + mov[s]
    chequear(control_1105 == sum(final[s] for s in CONTROL_1105),
             f'Control 1105 balanza {control_1105} != suma subcuentas {sum(final[s] for s in CONTROL_1105)}')

    # Kárdex y lotes
    chequear(len(lotes_tabla) == 7, f'Lotes esperados 7 y hay {len(lotes_tabla)}')
    unid = sum(l['unidades'] for l in lotes_tabla)
    ent = sum(l['entregadas'] for l in lotes_tabla)
    disp = sum(l['disponibles'] for l in lotes_tabla)
    cdisp = sum(l['costo_disponible'] for l in lotes_tabla)
    chequear(unid == 200 and ent == 136 and disp == 64 and cdisp == D('7260.00'),
             f'Lotes: {unid}u/{ent}e/{disp}d/${cdisp}')
    chequear(final['110702'] == D('7260.00'), f'110702 esperado $7,260.00 y es {final["110702"]}')
    kardex = req('LXXIX')['filas']
    fin_k = next((f for f in kardex if 'Existencia final' in f[1]), None)
    chequear(fin_k and dinero(fin_k[4]) == D('4980.00'), f'Kárdex final {fin_k}')

    # Catálogo
    codigos = [c['codigo'] for c in catalogo]
    chequear(len(catalogo) == 181, f'Catálogo esperado 181 cuentas y hay {len(catalogo)}')
    chequear(len(set(codigos)) == len(codigos), 'Catálogo con códigos duplicados')

    # Cobertura técnica (tabla CXIV)
    cobertura_tabla = req('CXIV')['filas']
    cobertura = []
    for f in cobertura_tabla:
        f = (f + [''] * 4)[:4]
        if not f[0] or 'SUMAS' in f[1].upper():
            continue
        lado = 'D' if dinero(f[2]) else 'H'
        cobertura.append({'codigo': f[0].strip(), 'lado': lado,
                          'debe': dinero(f[2]) or CERO, 'haber': dinero(f[3]) or CERO})
    chequear(len(cobertura) == 32, f'Cobertura CXIV: {len(cobertura)} líneas (esperadas 32)')
    chequear(sum(c['debe'] for c in cobertura) == sum(c['haber'] for c in cobertura) == D('16.00'),
             'Cobertura CXIV no cuadra $16.00/$16.00')
    bancos_cov = next(c for c in cobertura if c['codigo'] == '110102')
    chequear(bancos_cov['debe'] == D('1.00'), 'Contrapartida Bancos de cobertura')

    if errores:
        raise ErrorAnexo('Validaciones fallidas:\n - ' + '\n - '.join(errores))

    # ------------------------------------------------------------- derivados
    m = lambda f, c, l: sum((x[l] for x in next(p for p in partidas if p['folio'] == f)['lineas']
                             if x['cuenta'] == c), CERO)
    lineas = lambda f: next(p for p in partidas if p['folio'] == f)['lineas']

    # Terceros (nombres del informe; NITs ficticios simulados)
    nit_empresa = 'SIMULADO-NIT'
    terceros = [
        ('SIM-2026-TSR', 'Textiles Santa Rita, S.A. de C.V.', True),
        ('SIM-2026-FSM', 'Ferretería Industrial San Miguel, S.A. de C.V.', True),
        ('SIM-2026-LOG', 'Logística Cuscatlán, S.A. de C.V.', True),
        ('SIM-2026-ROB', 'Comercial El Roble, S.A. de C.V.', True),
        ('SIM-2026-DSM', 'Distribuidora San Marcos, S.A. de C.V.', True),
        ('SIM-2026-CLN', 'Clínica Los Naranjos, S.A. de C.V.', True),
        ('SIM-2026-CSC', 'Servicios Contables Cuscatlán, S.A. de C.V.', True),
        ('SIM-2026-AGP', 'Agroservicios Los Pinos, S.A. de C.V.', True),
        ('SIM-2026-ALV', 'Alimentos del Valle, S.A. de C.V.', True),
        ('SIM-2026-VAR', 'Clientes varios (SaaS, soporte y otros hitos de octubre)', True),
        ('SIM-2026-NA', 'Nube Andina Servicios, S.A. de C.V.', False),
        ('SIM-2026-ITV', 'Inmobiliaria Torres del Valle, S.A. de C.V.', False),
        ('SIM-2026-TEC', 'Tecnoequipos Escalón, S.A. de C.V.', False),
        ('SIM-2026-PEX', 'Profesionales externos (auditoría, certificación fiscal y asesoría legal)', False),
        ('SIM-2026-MKT', 'Proveedores de mercadeo, publicidad y eventos', False),
        ('SIM-2026-INF', 'Proveedores de infraestructura, nube y QA', False),
        ('SIM-2026-PLA', 'Proveedores de servicios de producción de licencias', False),
        ('SIM-2026-PSP', 'Proveedores de servicios de proyectos', False),
        ('SIM-2026-ADM', 'Proveedores de alquiler y servicios administrativos', False),
    ]
    terceros_json = [{'nit': n, 'razon_social': r,
                      'es_cliente': cli, 'es_proveedor': not cli, 'supuesto': False}
                     for n, r, cli in terceros]

    def doc(codigo, tipo, emisor, receptor, tercero, fecha, base, iva, total, folio, evento, supuesto=False):
        chequear(base + iva == total, f'Documento {codigo}: base + IVA ≠ total')
        return {'codigo': codigo, 'tipo': tipo, 'emisor_nit': emisor, 'receptor_nit': receptor,
                'tercero_nit': tercero, 'fecha': fecha, 'base_gravada': base, 'iva': iva,
                'total': total, 'folio': folio, 'tipo_evento': evento, 'supuesto': supuesto}

    documentos = [
        doc('DTE-2026-278', 'CCF', nit_empresa, 'SIM-2026-TSR', 'SIM-2026-TSR', '2026-09-20',
            D('8400.00'), D('1092.00'), D('9492.00'), None, None, True),
        doc('DTE-2026-311', 'CCF', nit_empresa, 'SIM-2026-VAR', 'SIM-2026-VAR', '2026-10-07',
            m('DI-2026-000005', '410401', 'haber'), D('769.60'), D('6689.60'),
            'DI-2026-000005', 'VENTA', True),
        doc('DTE-2026-312', 'CCF', nit_empresa, 'SIM-2026-VAR', 'SIM-2026-VAR', '2026-10-07',
            m('DI-2026-000005', '410403', 'haber'), D('405.60'), D('3525.60'),
            'DI-2026-000005', 'VENTA', True),
        doc('DTE-2026-313', 'CCF', nit_empresa, 'SIM-2026-VAR', 'SIM-2026-VAR', '2026-10-08',
            m('DI-2026-000007', '410201', 'haber') + m('DI-2026-000007', '410202', 'haber')
            + m('DI-2026-000007', '410301', 'haber') + m('DI-2026-000007', '410501', 'haber')
            + m('DI-2026-000007', '410302', 'haber'),
            m('DI-2026-000007', '210201', 'haber'),
            m('DI-2026-000007', '110204', 'debe'), 'DI-2026-000007', 'VENTA', True),
        doc('DTE-2026-314', 'CCF', nit_empresa, 'SIM-2026-TSR', 'SIM-2026-TSR', '2026-10-02',
            m('DI-2026-000001', '410101', 'haber'), m('DI-2026-000001', '210201', 'haber'),
            m('DI-2026-000001', '110202', 'debe'), 'DI-2026-000001', 'VENTA'),
        doc('DTE-2026-315', 'CCF', nit_empresa, 'SIM-2026-ROB', 'SIM-2026-ROB', '2026-10-07',
            m('DI-2026-000004', '410402', 'haber'), m('DI-2026-000004', '210201', 'haber'),
            m('DI-2026-000004', '110203', 'debe'), 'DI-2026-000004', 'VENTA'),
        doc('DTE-2026-316', 'CCF', nit_empresa, 'SIM-2026-DSM', 'SIM-2026-DSM', '2026-10-15',
            m('DI-2026-000011', '410602', 'haber'), m('DI-2026-000011', '210201', 'haber'),
            m('DI-2026-000011', '110201', 'debe'), 'DI-2026-000011', 'VENTA'),
        doc('DTE-2026-317', 'CCF', nit_empresa, 'SIM-2026-AGP', 'SIM-2026-AGP', '2026-10-12',
            m('IN-2026-000003', '210704', 'haber'), m('IN-2026-000003', '210201', 'haber'),
            m('IN-2026-000003', '110102', 'debe'), 'IN-2026-000003', 'ANTICIPO'),
        doc('DTE-2026-318', 'CCF', nit_empresa, 'SIM-2026-CSC', 'SIM-2026-CSC', '2026-10-20',
            m('DI-2026-000014', '410601', 'haber'), m('DI-2026-000014', '210201', 'haber'),
            m('DI-2026-000014', '110201', 'debe'), 'DI-2026-000014', 'VENTA'),
        doc('DTE-2026-319', 'CCF', nit_empresa, 'SIM-2026-FSM', 'SIM-2026-FSM', '2026-10-05',
            m('DI-2026-000003', '110202', 'debe') - m('DI-2026-000003', '210201', 'haber'),
            m('DI-2026-000003', '210201', 'haber'), m('DI-2026-000003', '110202', 'debe'),
            'DI-2026-000003', 'VENTA'),
        doc('DTE-2026-320', 'CCF', nit_empresa, 'SIM-2026-CLN', 'SIM-2026-CLN', '2026-10-28',
            m('DI-2026-000015', '410602', 'haber'), m('DI-2026-000015', '210201', 'haber'),
            m('DI-2026-000015', '110201', 'debe'), 'DI-2026-000015', 'VENTA'),
        doc('DTE-2026-321', 'CCF', nit_empresa, 'SIM-2026-LOG', 'SIM-2026-LOG', '2026-10-08',
            m('DI-2026-000006', '410101', 'haber'), m('DI-2026-000006', '210201', 'haber'),
            m('DI-2026-000006', '110202', 'debe'), 'DI-2026-000006', 'VENTA'),
        doc('DTE-2026-322', 'CCF', nit_empresa, 'SIM-2026-VAR', 'SIM-2026-VAR', '2026-10-11',
            D('800.00'), D('104.00'), D('904.00'), 'DI-2026-000010', 'VENTA', True),
        doc('DTE-2026-323', 'CCF', nit_empresa, 'SIM-2026-VAR', 'SIM-2026-VAR', '2026-10-11',
            D('800.00'), D('104.00'), D('904.00'), 'DI-2026-000010', 'VENTA', True),
        doc('DTE-2026-324', 'CCF', nit_empresa, 'SIM-2026-VAR', 'SIM-2026-VAR', '2026-10-11',
            D('800.00'), D('104.00'), D('904.00'), 'DI-2026-000010', 'VENTA', True),
        doc('DTE-2026-325', 'CCF', nit_empresa, 'SIM-2026-VAR', 'SIM-2026-VAR', '2026-10-11',
            D('800.00'), D('104.00'), D('904.00'), 'DI-2026-000010', 'VENTA', True),
        doc('DTE-2026-326', 'CCF', nit_empresa, 'SIM-2026-VAR', 'SIM-2026-VAR', '2026-10-11',
            D('800.00'), D('104.00'), D('904.00'), 'DI-2026-000010', 'VENTA', True),
        doc('DTE-2026-327', 'CCF', nit_empresa, 'SIM-2026-VAR', 'SIM-2026-VAR', '2026-10-11',
            D('800.00'), D('104.00'), D('904.00'), 'DI-2026-000010', 'VENTA', True),
        doc('DTE-2026-328', 'CCF', nit_empresa, 'SIM-2026-VAR', 'SIM-2026-VAR', '2026-10-11',
            D('800.00'), D('104.00'), D('904.00'), 'DI-2026-000010', 'VENTA', True),
        doc('DTE-2026-329', 'CCF', nit_empresa, 'SIM-2026-VAR', 'SIM-2026-VAR', '2026-10-11',
            D('665.00'), D('86.45'), D('751.45'), 'DI-2026-000010', 'VENTA', True),
        doc('DTE-2026-330', 'CCF', nit_empresa, 'SIM-2026-VAR', 'SIM-2026-VAR', '2026-10-11',
            D('400.00'), D('52.00'), D('452.00'), 'DI-2026-000010', 'VENTA', True),
        doc('BC-2026-201', 'RECIBO', 'SIM-2026-TSR', nit_empresa, 'SIM-2026-TSR', '2026-10-05',
            D('4746.00'), CERO, D('4746.00'), 'IN-2026-000001', 'COBRO'),
        doc('BC-2026-202', 'RECIBO', 'SIM-2026-ROB', nit_empresa, 'SIM-2026-ROB', '2026-10-07',
            D('2542.50'), CERO, D('2542.50'), 'IN-2026-000002', 'COBRO'),
        doc('BC-2026-203', 'RECIBO', 'SIM-2026-AGP', nit_empresa, 'SIM-2026-AGP', '2026-10-12',
            D('4068.00'), CERO, D('4068.00'), 'IN-2026-000003', 'COBRO'),
        doc('BC-2026-204', 'RECIBO', 'SIM-2026-ALV', nit_empresa, 'SIM-2026-ALV', '2026-10-28',
            D('6780.00'), CERO, D('6780.00'), 'IN-2026-000004', 'ANTICIPO'),
        doc('BC-2026-205', 'RECIBO', 'SIM-2026-VAR', nit_empresa, 'SIM-2026-VAR', '2026-10-30',
            m('IN-2026-000005', '110102', 'debe'), CERO, m('IN-2026-000005', '110102', 'debe'),
            'IN-2026-000005', 'COBRO', True),
        doc('CCF-PROV-TEC-001', 'CCF', 'SIM-2026-TEC', nit_empresa, 'SIM-2026-TEC', '2026-10-04',
            m('DI-2026-000002', '120101', 'debe') + m('DI-2026-000002', '120103', 'debe'),
            m('DI-2026-000002', '110301', 'debe'), m('DI-2026-000002', '210101', 'haber'),
            'DI-2026-000002', 'COMPRA'),
        doc('CCF-PROV-INF-001', 'CCF', 'SIM-2026-INF', nit_empresa, 'SIM-2026-INF', '2026-10-03',
            m('EG-2026-000001', '510201', 'debe') + m('EG-2026-000001', '510203', 'debe')
            + m('EG-2026-000001', '520101', 'debe') + m('EG-2026-000001', '520102', 'debe'),
            m('EG-2026-000001', '110301', 'debe'), m('EG-2026-000001', '110102', 'haber'),
            'EG-2026-000001', 'COMPRA', True),
        doc('CCF-PROV-ADM-001', 'CCF', 'SIM-2026-ADM', nit_empresa, 'SIM-2026-ADM', '2026-10-05',
            m('EG-2026-000002', '610102', 'debe') + m('EG-2026-000002', '610106', 'debe')
            + m('EG-2026-000002', '610303', 'debe'),
            m('EG-2026-000002', '110301', 'debe'), m('EG-2026-000002', '110102', 'haber'),
            'EG-2026-000002', 'COMPRA', True),
        doc('CCF-PROV-MKT-001', 'CCF', 'SIM-2026-MKT', nit_empresa, 'SIM-2026-MKT', '2026-10-14',
            m('EG-2026-000007', '610202', 'debe') + m('EG-2026-000007', '610203', 'debe')
            + m('EG-2026-000007', '610204', 'debe'),
            m('EG-2026-000007', '110301', 'debe'), m('EG-2026-000007', '110102', 'haber'),
            'EG-2026-000007', 'COMPRA', True),
        doc('CCF-PROV-PEX-001', 'CCF', 'SIM-2026-PEX', nit_empresa, 'SIM-2026-PEX', '2026-10-20',
            m('EG-2026-000008', '610103', 'debe'), m('EG-2026-000008', '110301', 'debe'),
            m('EG-2026-000008', '210203', 'haber') + m('EG-2026-000008', '110102', 'haber'),
            'EG-2026-000008', 'COMPRA'),
        doc('CCF-PROV-PSP-001', 'CCF', 'SIM-2026-PSP', nit_empresa, 'SIM-2026-PSP', '2026-10-31',
            m('DI-2026-000020', '110502', 'debe'), m('DI-2026-000020', '110301', 'debe'),
            m('DI-2026-000020', '210102', 'haber'), 'DI-2026-000020', 'COMPRA'),
        doc('CCF-PROV-PLA-04', 'CCF', 'SIM-2026-PLA', nit_empresa, 'SIM-2026-PLA', '2026-10-10',
            m('DI-2026-000008', '210102', 'haber') - m('DI-2026-000008', '110301', 'debe'),
            m('DI-2026-000008', '110301', 'debe'), m('DI-2026-000008', '210102', 'haber'),
            'DI-2026-000008', 'COMPRA', True),
        doc('CCF-PROV-PLA-02', 'CCF', 'SIM-2026-PLA', nit_empresa, 'SIM-2026-PLA', '2026-10-20',
            m('DI-2026-000012', '210102', 'haber') - m('DI-2026-000012', '110301', 'debe'),
            m('DI-2026-000012', '110301', 'debe'), m('DI-2026-000012', '210102', 'haber'),
            'DI-2026-000012', 'COMPRA', True),
        doc('CCF-PROV-PLA-05', 'CCF', 'SIM-2026-PLA', nit_empresa, 'SIM-2026-PLA', '2026-10-29',
            m('DI-2026-000016', '210102', 'haber') - m('DI-2026-000016', '110301', 'debe'),
            m('DI-2026-000016', '110301', 'debe'), m('DI-2026-000016', '210102', 'haber'),
            'DI-2026-000016', 'COMPRA', True),
    ]
    # Los nueve DTE-2026-322 a 330 deben sumar el hito "otros" de DI-2026-000010
    docs_otros = [d for d in documentos if re.fullmatch(r'DTE-2026-(32[2-9]|330)', d['codigo'])
                  and d['folio'] == 'DI-2026-000010']
    chequear(len(docs_otros) == 9
             and sum(d['base_gravada'] for d in docs_otros) == m('DI-2026-000010', '410101', 'haber')
             and sum(d['iva'] for d in docs_otros) == m('DI-2026-000010', '210201', 'haber')
             and sum(d['total'] for d in docs_otros) == m('DI-2026-000010', '110202', 'debe'),
             'DTE-2026-322 a 330 no suman el total de DI-2026-000010')
    aplicaciones = [
        {'origen': 'DTE-2026-278', 'pago': 'BC-2026-201', 'folio': 'IN-2026-000001',
         'importe': m('IN-2026-000001', '110202', 'haber'), 'fecha': '2026-10-05',
         'concepto': 'Aplicación parcial de BC-2026-201 a DTE-2026-278'},
        {'origen': 'DTE-2026-315', 'pago': 'BC-2026-202', 'folio': 'IN-2026-000002',
         'importe': m('IN-2026-000002', '110203', 'haber'), 'fecha': '2026-10-07',
         'concepto': 'Cobro de DTE-2026-315 con BC-2026-202'},
        {'origen': 'DTE-2026-317', 'pago': 'BC-2026-203', 'folio': 'IN-2026-000003',
         'importe': m('IN-2026-000003', '110102', 'debe'), 'fecha': '2026-10-12',
         'concepto': 'Cobro de DTE-2026-317 con BC-2026-203'},
    ]

    # Lotes y licencias (asignaciones según kárdex LXXIX y notas de LXXX)
    por_lote = {l['codigo_lote']: l for l in lotes_tabla}
    lotes_json = [
        {'codigo_lote': 'LI-2026-01', 'producto': 'COL-INV', 'version': '2.0',
         'fecha_liberacion': '2026-01-12', 'unidades_estimadas': 45,
         'costo_total': por_lote['LI-2026-01']['costo_total'],
         'costo_unitario': por_lote['LI-2026-01']['costo_unitario'],
         'unidades_disponibles': 0, 'folio': None,
         'licencias': licencias_lote(45, 45, D('100.00'),
                                      [(45, 'ENTREGADA', None, None, None)])},
        {'codigo_lote': 'LV-2026-01', 'producto': 'COL-VENTAS', 'version': '3.1',
         'fecha_liberacion': '2026-04-05', 'unidades_estimadas': 30,
         'costo_total': por_lote['LV-2026-01']['costo_total'],
         'costo_unitario': por_lote['LV-2026-01']['costo_unitario'],
         'unidades_disponibles': 0, 'folio': None,
         'licencias': licencias_lote(30, 30, D('140.00'),
                                      [(30, 'ENTREGADA', None, None, None)])},
        {'codigo_lote': 'LI-2026-02', 'producto': 'COL-INV', 'version': '2.0',
         'fecha_liberacion': '2026-05-20', 'unidades_estimadas': 40,
         'costo_total': por_lote['LI-2026-02']['costo_total'],
         'costo_unitario': por_lote['LI-2026-02']['costo_unitario'],
         'unidades_disponibles': 0, 'folio': None,
         'licencias': licencias_lote(40, 40, D('100.00'),
                                      [(40, 'ENTREGADA', None, None, None)])},
        {'codigo_lote': 'LI-2026-03', 'producto': 'COL-INV', 'version': '2.0',
         'fecha_liberacion': '2026-09-15', 'unidades_estimadas': 18,
         'costo_total': por_lote['LI-2026-03']['costo_total'],
         'costo_unitario': por_lote['LI-2026-03']['costo_unitario'],
         'unidades_disponibles': 3, 'folio': None,
         'licencias': licencias_lote(18, 15, D('100.00'), [
             (9, 'ENTREGADA', 'SIM-2026-DSM', 'DTE-2026-316', '2026-10-15'),
             (15, 'ENTREGADA', 'SIM-2026-CLN', 'DTE-2026-320', '2026-10-28')])},
        {'codigo_lote': 'LI-2026-04', 'producto': 'COL-INV', 'version': '2.0',
         'fecha_liberacion': '2026-10-10', 'unidades_estimadas': 12,
         'costo_total': por_lote['LI-2026-04']['costo_total'],
         'costo_unitario': por_lote['LI-2026-04']['costo_unitario'],
         'unidades_disponibles': 12, 'folio': 'DI-2026-000009',
         'licencias': licencias_lote(12, 0, D('115.00'))},
        {'codigo_lote': 'LV-2026-02', 'producto': 'COL-VENTAS', 'version': '3.1',
         'fecha_liberacion': '2026-10-20', 'unidades_estimadas': 25,
         'costo_total': por_lote['LV-2026-02']['costo_total'],
         'costo_unitario': por_lote['LV-2026-02']['costo_unitario'],
         'unidades_disponibles': 19, 'folio': 'DI-2026-000013',
         'licencias': licencias_lote(25, 6, D('120.00'), [
             (6, 'ENTREGADA', 'SIM-2026-CSC', 'DTE-2026-318', '2026-10-20')])},
        {'codigo_lote': 'LI-2026-05', 'producto': 'COL-INV', 'version': '2.0',
         'fecha_liberacion': '2026-10-29', 'unidades_estimadas': 30,
         'costo_total': por_lote['LI-2026-05']['costo_total'],
         'costo_unitario': por_lote['LI-2026-05']['costo_unitario'],
         'unidades_disponibles': 30, 'folio': 'DI-2026-000017',
         'licencias': licencias_lote(30, 0, D('110.00'))},
    ]
    advertencias.append('Las 115 licencias de los lotes LI-2026-01/02 y LV-2026-01 quedaron '
                        'ENTREGADA en meses previos; el informe no detalla cliente ni documento '
                        'por unidad, por lo que esos campos quedan en NULL.')

    # Costos por hito (tabla CIII) -> consumos de las órdenes
    hitos = {}
    for f in req('CIII')['filas']:
        f = (f + [''] * 10)[:10]
        if f[0].startswith('OS-2026-'):
            codigo = f[0].split()[0]
            hitos[codigo] = {'horas': D(f[1].replace(',', '')),
                             'mod': dinero(f[2]), 'mpd': dinero(f[3]), 'cif': dinero(f[4]),
                             'costo': dinero(f[5]), 'precio': dinero(f[6])}
    chequear(set(hitos) == {'OS-2026-014', 'OS-2026-015', 'OS-2026-016'},
             f'Hitos CIII inesperados: {sorted(hitos)}')

    ordenes_json = [
        {'codigo': 'OS-2026-014', 'cliente': 'SIM-2026-TSR',
         'descripcion': 'Desarrollo a medida; hito aceptado en octubre (DTE-2026-314)',
         'presupuesto': hitos['OS-2026-014']['costo'], 'precio_venta': hitos['OS-2026-014']['precio'],
         'estado': 'ENTREGADA', 'folio_consumos': 'DI-2026-000021',
         'hito': hitos['OS-2026-014'],
         'consumos': [
             {'tipo': 'MOD', 'descripcion': f"Mano de obra directa: {hitos['OS-2026-014']['horas']} horas",
              'cantidad': hitos['OS-2026-014']['horas'], 'tarifa': D('12.00'), 'importe': hitos['OS-2026-014']['mod']},
             {'tipo': 'SERVICIO', 'descripcion': 'Servicios directos del proyecto',
              'cantidad': D('1'), 'tarifa': hitos['OS-2026-014']['mpd'], 'importe': hitos['OS-2026-014']['mpd']},
             {'tipo': 'CIF', 'descripcion': 'CIF aplicado a $3.00 por hora',
              'cantidad': hitos['OS-2026-014']['horas'], 'tarifa': D('3.00'), 'importe': hitos['OS-2026-014']['cif']}]},
        {'codigo': 'OS-2026-015', 'cliente': 'SIM-2026-LOG',
         'descripcion': 'Desarrollo a medida; hito aceptado en octubre (DTE-2026-321)',
         'presupuesto': hitos['OS-2026-015']['costo'], 'precio_venta': hitos['OS-2026-015']['precio'],
         'estado': 'EN_PROCESO', 'folio_consumos': 'DI-2026-000021',
         'hito': hitos['OS-2026-015'],
         'consumos': [
             {'tipo': 'MOD', 'descripcion': f"Mano de obra directa: {hitos['OS-2026-015']['horas']} horas",
              'cantidad': hitos['OS-2026-015']['horas'], 'tarifa': D('12.00'), 'importe': hitos['OS-2026-015']['mod']},
             {'tipo': 'SERVICIO', 'descripcion': 'Servicios directos del proyecto',
              'cantidad': D('1'), 'tarifa': hitos['OS-2026-015']['mpd'], 'importe': hitos['OS-2026-015']['mpd']},
             {'tipo': 'CIF', 'descripcion': 'CIF aplicado a $3.00 por hora',
              'cantidad': hitos['OS-2026-015']['horas'], 'tarifa': D('3.00'), 'importe': hitos['OS-2026-015']['cif']}]},
        {'codigo': 'OS-2026-016', 'cliente': 'SIM-2026-FSM',
         'descripcion': 'Desarrollo a medida; hito final aceptado en octubre (DTE-2026-319)',
         'presupuesto': hitos['OS-2026-016']['costo'], 'precio_venta': hitos['OS-2026-016']['precio'],
         'estado': 'ENTREGADA', 'folio_consumos': 'DI-2026-000021',
         'hito': hitos['OS-2026-016'],
         'consumos': [
             {'tipo': 'MOD', 'descripcion': f"Mano de obra directa: {hitos['OS-2026-016']['horas']} horas",
              'cantidad': hitos['OS-2026-016']['horas'], 'tarifa': D('12.00'), 'importe': hitos['OS-2026-016']['mod']},
             {'tipo': 'SERVICIO', 'descripcion': 'Servicios directos del proyecto',
              'cantidad': D('1'), 'tarifa': hitos['OS-2026-016']['mpd'], 'importe': hitos['OS-2026-016']['mpd']},
             {'tipo': 'CIF', 'descripcion': 'CIF aplicado a $3.00 por hora',
              'cantidad': hitos['OS-2026-016']['horas'], 'tarifa': D('3.00'), 'importe': hitos['OS-2026-016']['cif']}]},
        {'codigo': 'OS-2026-017', 'cliente': 'SIM-2026-ALV',
         'descripcion': 'Desarrollo a medida; anticipo recibido (IN-2026-000004), orden abierta',
         'presupuesto': CERO, 'precio_venta': D('6000.00'), 'estado': 'ABIERTA',
         'folio_consumos': None, 'hito': None, 'consumos': []},
    ]
    advertencias.append('OS-2026-016 y OS-2026-014 muestran el valor del hito aceptado en '
                        'octubre como precio_venta; el contrato puede incluir hitos previos '
                        'no detallados en el informe.')

    # Activos fijos (agregados por cuenta, según trazabilidad de LXXVII)
    aj2 = 'AJ-2026-000002'
    activos = [
        {'codigo': 'AF-COMPUTADORAS', 'nombre': 'Computadoras, estaciones de prueba y laptops de reserva',
         'cuenta_activo': '120101', 'cuenta_dep_acum': '120201', 'cuenta_gasto': '610104',
         'costo': final['120101'], 'vida_meses': 36, 'fecha_uso': '2024-03-01',
         'valor_residual': CERO, 'depreciacion_acum': -final['120201'], 'supuesto_fecha_uso': True},
        {'codigo': 'AF-SERVIDORES', 'nombre': 'Servidores y equipos de red',
         'cuenta_activo': '120102', 'cuenta_dep_acum': '120202', 'cuenta_gasto': '520103',
         'costo': final['120102'], 'vida_meses': 48, 'fecha_uso': '2024-03-01',
         'valor_residual': CERO, 'depreciacion_acum': -final['120202'], 'supuesto_fecha_uso': True},
        {'codigo': 'AF-MOBILIARIO', 'nombre': 'Mobiliario y equipo de oficina',
         'cuenta_activo': '120103', 'cuenta_dep_acum': '120203', 'cuenta_gasto': '610104',
         'costo': final['120103'], 'vida_meses': 60, 'fecha_uso': '2024-03-01',
         'valor_residual': CERO, 'depreciacion_acum': -final['120203'], 'supuesto_fecha_uso': True},
    ]
    chequear(final['120101'] == D('46850.00') and final['120102'] == D('13700.00')
             and final['120103'] == D('12400.00'), 'Costos de activos no coinciden con LXXVII')
    cuotas_dep = [
        {'activo': 'AF-COMPUTADORAS', 'periodo_mes': 10, 'importe': m(aj2, '120201', 'haber'), 'folio': aj2},
        {'activo': 'AF-SERVIDORES', 'periodo_mes': 10, 'importe': m(aj2, '120202', 'haber'), 'folio': aj2},
        {'activo': 'AF-MOBILIARIO', 'periodo_mes': 10, 'importe': m(aj2, '120203', 'haber'), 'folio': aj2},
    ]
    # Software adquirido en dos tramos (enero $7,200 y junio $4,800), vida 36 meses
    intangibles = [
        {'codigo': 'AI-SOFTWARE-ENE', 'nombre': 'Software adquirido de uso interno (enero 2026)',
         'cuenta_activo': '120301', 'cuenta_amort_acum': '120401', 'cuenta_gasto': '610105',
         'costo': D('7200.00'), 'vida_meses': 36, 'fecha_uso': '2026-01-01',
         'valor_residual': CERO, 'amortizacion_acum': D('2000.00'), 'supuesto_fecha_uso': True},
        {'codigo': 'AI-SOFTWARE-JUN', 'nombre': 'Software adquirido de uso interno (junio 2026)',
         'cuenta_activo': '120301', 'cuenta_amort_acum': '120401', 'cuenta_gasto': '610105',
         'costo': D('4800.00'), 'vida_meses': 36, 'fecha_uso': '2026-06-01',
         'valor_residual': CERO, 'amortizacion_acum': D('666.65'), 'supuesto_fecha_uso': True},
    ]
    chequear(final['120301'] == D('12000.00') and -final['120401'] == D('2666.65'),
             f'Software: {final["120301"]}/{final["120401"]}')
    cuotas_amort = [
        {'activo': 'AI-SOFTWARE-ENE', 'periodo_mes': 10, 'importe': D('200.00'), 'folio': aj2},
        {'activo': 'AI-SOFTWARE-JUN', 'periodo_mes': 10, 'importe': D('133.33'), 'folio': aj2},
    ]
    chequear(sum(c['importe'] for c in cuotas_amort) == m(aj2, '120401', 'haber'),
             'Cuotas de amortización no suman la línea de AJ-2')

    # Planilla de octubre (DI-18)
    di18 = 'DI-2026-000018'
    prev_lab = m(di18, '210501', 'haber')
    nominal = (prev_lab / D('0.0725')).quantize(D('0.01'))
    isss_lab = m(di18, '210401', 'haber')
    isr = m(di18, '210205', 'haber')
    liquido = m(di18, '210301', 'haber')
    isss_pat = m(di18, '210402', 'haber')
    prev_pat = m(di18, '210502', 'haber')
    incaf = m(di18, '210801', 'haber')
    costo_patronal = sum(l['debe'] for l in lineas(di18))
    chequear(nominal == D('35000.00') and liquido == D('28880.70') and costo_patronal == D('40632.50'),
             f'Planilla: {nominal}/{liquido}/{costo_patronal}')
    planilla = {'folio': di18, 'periodo_mes': 10, 'fecha_contable': '2026-10-31', 'empleados': 31,
                'salario_nominal': nominal, 'tasa_isss_laboral': D('0.03'),
                'tasa_previsional_laboral': D('0.0725'), 'isr_retenido': isr,
                'otras_deducciones': CERO, 'isss_laboral': isss_lab, 'isss_patronal': isss_pat,
                'previsional_laboral': prev_lab, 'previsional_patronal': prev_pat, 'incaf': incaf,
                'liquido_por_pagar': liquido, 'costo_patronal_total': costo_patronal,
                'tasa_isss_patronal': D('0.075'), 'tasa_previsional_patronal': D('0.0875'),
                'tasa_incaf': D('0.01'), 'umbral_incaf_empleados': 10, 'centro_costo': 'GENERAL',
                'clave_evento': 'PLANILLA-2026-10', 'supuesto': False,
                'fuente': 'DI-2026-000018 del libro diario (31 trabajadores)'}

    # Estimación de cartera de octubre (AJ-6)
    aj6 = 'AJ-2026-000006'
    base_cartera = final['110201'] + final['110202'] + final['110203'] + final['110204']
    chequear(base_cartera == D('94723.67'), f'Cartera base {base_cartera}')
    estimacion = {'folio': aj6, 'periodo_mes': 10, 'fecha_contable': '2026-10-31',
                  'base_estimacion': base_cartera, 'porcentaje': D('0.03'),
                  'importe': m(aj6, '610301', 'debe'),
                  'acumulado_esperado': D('2841.71'), 'supuesto': False,
                  'fuente': 'AJ-2026-000006: 3.00 % de la cartera de clientes'}
    chequear(estimacion['importe'] == D('541.71'), f'Estimación {estimacion["importe"]}')

    # Usuarios-mes SaaS de octubre (diario + devengo AJ-5)
    saas_oct = [
        {'plan': 'SAAS-INV', 'usuarios_mes': 45, 'periodo': '2026-10-01'},
        {'plan': 'SAAS-VENTAS-PLUS', 'usuarios_mes': 74, 'periodo': '2026-10-01'},
        {'plan': 'SAAS-GEST-BUSINESS', 'usuarios_mes': 26, 'periodo': '2026-10-01'},
    ]
    chequear(D('2250') + D('5920') + D('3120') == m('DI-2026-000004', '410402', 'haber')
             + m('DI-2026-000005', '410401', 'haber') + m('DI-2026-000005', '410403', 'haber'),
             'Usuarios-mes SaaS no coinciden con DI-4/DI-5')

    # Partidas del seed
    seed_partidas = [{
        'clave': 'AP-APERTURA', 'folio': 'AP-2026-000001', 'tipo': 'AP', 'fecha': '2026-09-30',
        'concepto': 'Apertura: saldos acumulados al 30 de septiembre de 2026 (asiento AP-2026-000001 del Anexo O)',
        'soporte': 'ANEXO-O-APERTURA', 'origen': 'MANUAL', 'documento': None, 'tipo_evento': None,
        'lineas': a_lineas(apertura)}]
    aux_map = {'DI-2026-000001': 'OS-2026-014', 'DI-2026-000003': 'OS-2026-016',
               'DI-2026-000006': 'OS-2026-015', 'DI-2026-000021': 'OS-2026-014/015/016'}
    for p in partidas:
        refs = re.findall(r'\b(?:DTE|BC|OS)-\d{4}-\d+\b', p['concepto'])
        refs += re.findall(r'\b(?:LI|LV)-\d{4}-\d+\b', p['concepto'])
        seed_partidas.append({
            'clave': p['folio'], 'folio': p['folio'], 'tipo': p['tipo'], 'fecha': p['fecha'],
            'concepto': p['concepto'],
            'soporte': (', '.join(dict.fromkeys(refs)) or p['folio'])[:300],
            'origen': 'MANUAL' if p['tipo'] in ('AJ', 'AP') else 'AUTOMATICO',
            'documento': next((d['codigo'] for d in documentos if d['folio'] == p['folio']), None),
            'tipo_evento': next((d['tipo_evento'] for d in documentos if d['folio'] == p['folio']), None),
            'lineas': [{'cuenta': l['cuenta'], 'debe': l['debe'], 'haber': l['haber'],
                        'auxiliar': aux_map.get(p['folio']) if l['cuenta'].startswith('11') else None}
                       for l in p['lineas']]})
    maxfolio = {}
    for p in seed_partidas:
        maxfolio[p['tipo']] = max(maxfolio.get(p['tipo'], 0), int(p['folio'].split('-')[-1]))

    # Datos del ejercicio integral de noviembre (tablas XCII/XCVIII/CXIV)
    noviembre = {
        'periodos': {'cerrados': list(range(1, 10)), 'en_revision': 10, 'abierto': 11},
        'ordenes_estado': {'OS-2026-014': 'CERRADA', 'OS-2026-015': 'EN_PROCESO',
                           'OS-2026-016': 'ENTREGADA', 'OS-2026-017': 'ABIERTA'},
        'lotes_estado': {
            'LI-2026-03': {'unidades': 18, 'entregadas': 19, 'devueltas': 1, 'reservadas': 0, 'disponibles': 0},
            'LI-2026-04': {'unidades': 12, 'entregadas': 5, 'devueltas': 0, 'reservadas': 6, 'disponibles': 1},
            'LI-2026-05': {'unidades': 30, 'entregadas': 0, 'devueltas': 0, 'reservadas': 0, 'disponibles': 30},
            'LI-2026-06': {'unidades': 20, 'entregadas': 0, 'devueltas': 0, 'reservadas': 0, 'disponibles': 20,
                           'fecha_liberacion': '2026-11-03'},
        },
        'saas_usuarios_mes': {'SAAS-INV': 45, 'SAAS-VENTAS-PLUS': 80, 'SAAS-GEST-BUSINESS': 30},
        'saas_ingreso': D('12250.00'),
        'cobertura': cobertura,
        'partida_cobertura': {'folio': 'AJ-2026-000099', 'fecha': '2026-11-08',
                              'concepto': 'Cobertura técnica de subcuentas aún sin movimiento',
                              'importe_unitario': D('1.00')},
    }

    if errores:
        raise ErrorAnexo('Validaciones derivadas fallidas:\n - ' + '\n - '.join(errores))

    sha = hashlib.sha256(Path(docx).read_bytes()).hexdigest()
    return {
        'meta': {'docx': Path(docx).name, 'docx_sha256': sha, 'corte': '2026-10-31',
                 'moneda': 'USD', 'version_esquema': 2, 'prefijo_evento': PREFIJO_EVENTO},
        'empresa': {'razon_social': 'Soluciones Empresariales Colibrí, S.A. de C.V.',
                    'nombre_comercial': 'Colibrí', 'nit': nit_empresa, 'nrc': 'SIMULADO-NRC',
                    'moneda': 'USD', 'zona_horaria': 'America/El_Salvador',
                    'actividad_economica': 'Programación informática (CAT-019)'},
        'ejercicio': {'anio': ANIO, 'periodos_cerrados': list(range(1, 10))},
        'controles': {
            'diario_debe': ctl_diario['debe'], 'diario_haber': ctl_diario['haber'],
            'balanza_debe': ctl_balanza['debe'], 'balanza_haber': ctl_balanza['haber'],
            'apertura_debe': ad, 'apertura_haber': ah, 'utilidad_neta': util_neta,
            'ventas_netas': ventas_netas, 'activo_total': D('410599.22'),
            'licencias_disponibles': disp, 'inventario_licencias': cdisp,
            'catalogo_cuentas': len(catalogo), 'partidas_octubre': len(partidas),
            'utilidad_bruta': util_bruta, 'utilidad_operacion': util_operacion,
            'utilidad_antes_isr': util_antes_isr,
            'saas_usuarios_mes_octubre': 145, 'saas_ingreso_octubre': D('11530.00')},
        'balanza': balanza,
        'apertura': [{'codigo': c, 'nombre': nombre_apertura(c), 'neto': v}
                     for c, v in sorted(apertura.items())],
        'diario': partidas,
        'catalogo': catalogo,
        'noviembre': noviembre,
        'advertencias': advertencias,
        'supuestos': supuestos,
        'seed': {
            'terceros': terceros_json, 'documentos': documentos, 'aplicaciones_pago': aplicaciones,
            'partidas': seed_partidas, 'folio_secuencia': [
                {'anio': ANIO, 'tipo': k, 'ultimo': v} for k, v in sorted(maxfolio.items())],
            'producto_versiones': [{'producto': 'COL-INV', 'version': '2.0', 'estado': 'LIBERADA'},
                                   {'producto': 'COL-VENTAS', 'version': '3.1', 'estado': 'LIBERADA'}],
            'lotes': lotes_json, 'ordenes': ordenes_json, 'activos_fijos': activos,
            'cuotas_depreciacion': cuotas_dep, 'activos_intangibles': intangibles,
            'cuotas_amortizacion': cuotas_amort, 'planillas': [planilla],
            'estimaciones_cartera': [estimacion], 'saas_usuario_mes': saas_oct},
        'tablas': tablas,
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
