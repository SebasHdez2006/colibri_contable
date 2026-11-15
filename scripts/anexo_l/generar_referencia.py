#!/usr/bin/env python3
"""Genera scripts/db/seed_00_referencia.sql con pg_dump (solo datos) desde una BD
recién migrada (Flyway V1..V20) y sin datos de negocio.

Uso:
    PGPASSWORD=colibri python generar_referencia.py --db colibri_scratch [--port 5434]
"""
import argparse
import os
import subprocess
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
SALIDA = RAIZ / 'scripts/db/seed_00_referencia.sql'
TABLAS = ['cuenta', 'catalogo_version', 'parametro_sistema', 'parametro_fiscal', 'parametro_iva',
          'posting_profile', 'centro_costo', 'plan_servicio', 'entorno_tecnico', 'producto']
DEFAULT_PG = r'C:\Program Files\PostgreSQL\18\bin\pg_dump.exe'


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--db', required=True)
    ap.add_argument('--host', default='127.0.0.1')
    ap.add_argument('--port', default='5434')
    ap.add_argument('--user', default='colibri')
    ap.add_argument('--pg-dump', default=os.environ.get('PG_DUMP', DEFAULT_PG))
    a = ap.parse_args()
    cmd = [a.pg_dump, '-h', a.host, '-p', a.port, '-U', a.user, '-d', a.db, '--data-only',
           '--column-inserts', '--no-owner', '--no-privileges', '--rows-per-insert=1']
    for t in TABLAS:
        cmd += ['-t', f'public.{t}']
    res = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8')
    if res.returncode != 0:
        print(res.stderr, file=sys.stderr)
        return res.returncode
    lineas = [l for l in res.stdout.splitlines()
              if l.startswith('INSERT INTO ')]
    if not lineas:
        print('pg_dump no devolvió datos', file=sys.stderr)
        return 1
    cab = ['-- Datos de referencia (catálogo, parámetros, posting, centros, planes, productos).',
           '-- Generado con scripts/anexo_l/generar_referencia.py desde una BD recién migrada (V1..V20).',
           '-- Excluye orden_servicio / activo_fijo / producto_version de demostración.',
           '\\encoding UTF8', 'BEGIN;']
    SALIDA.write_text('\n'.join(cab + lineas + ['COMMIT;']) + '\n', encoding='utf-8', newline='\n')
    print(f'OK: {SALIDA} ({len(lineas)} filas)')
    return 0


if __name__ == '__main__':
    sys.exit(main())
