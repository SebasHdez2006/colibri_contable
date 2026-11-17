import unittest
from decimal import Decimal as D
from pathlib import Path

import extraer_anexo_l as x

DOCX = Path(__file__).resolve().parents[3] / 'Informe_tecnico_Colibri.docx'


class ParsersTest(unittest.TestCase):
    def test_dinero(self):
        self.assertEqual(x.dinero('$1,234.50'), D('1234.50'))
        self.assertEqual(x.dinero('$(900.00)'), D('-900.00'))
        self.assertEqual(x.dinero('−$7,400.00'), D('-7400.00'))
        self.assertIsNone(x.dinero('—'))
        self.assertIsNone(x.dinero(''))

    def test_fecha(self):
        self.assertEqual(x.fecha_iso('02 oct.'), '2026-10-02')

    def test_soporte_con_parentesis_anidados(self):
        nombre, soporte = x.separar_soporte('Depreciación (Cédula; vidas útiles de IX.Q (3, 4 y 5 años))')
        self.assertEqual(nombre, 'Depreciación')
        self.assertEqual(soporte, 'Cédula; vidas útiles de IX.Q (3, 4 y 5 años)')
        self.assertEqual(x.separar_soporte('Bancos'), ('Bancos', None))

    def test_diario_sintetico(self):
        filas = [['02 oct.', 'DI-2026-000001', '', 'Hito aceptado (DTE-1)', '', ''],
                 ['', '', '110202', 'Clientes por desarrollo (OS-1)', '$10.00', ''],
                 ['', '', '410101', 'Ingresos por servicios', '', '$10.00'],
                 ['', '', '', 'SUMAS IGUALES DEL EXTRACTO', '$10.00', '$10.00']]
        partidas, control = x.parsear_diario(filas)
        self.assertEqual(len(partidas), 1)
        self.assertEqual(partidas[0]['concepto'], 'Hito aceptado (DTE-1)')
        self.assertEqual(len(partidas[0]['lineas']), 2)
        self.assertEqual(partidas[0]['lineas'][0]['soporte_texto'], 'OS-1')
        self.assertEqual(partidas[0]['lineas'][0]['cuenta'], '110202')
        self.assertEqual(control, {'debe': D('10.00'), 'haber': D('10.00')})


@unittest.skipUnless(DOCX.exists(), 'No está el .docx del informe')
class DocumentoRealTest(unittest.TestCase):
    def test_validaciones_y_apertura(self):
        datos = x.construir(str(DOCX))
        self.assertEqual(len(datos['diario']), 48)
        self.assertEqual(datos['controles']['apertura_debe'], D('972820.15'))
        self.assertEqual(datos['controles']['apertura_debe'], datos['controles']['apertura_haber'])
        self.assertEqual(datos['controles']['balanza_debe'], D('1072672.90'))
        self.assertEqual(datos['controles']['utilidad_neta'], D('51632.41'))
        self.assertEqual(datos['controles']['catalogo_cuentas'], 181)
        self.assertEqual(len(datos['noviembre']['cobertura']), 32)


if __name__ == '__main__':
    unittest.main()
