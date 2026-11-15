package sv.edu.ues.colibri.desktop.ui.views;

import java.util.List;

/**
 * Catálogo de referencia de las políticas, procesos y procedimientos de
 * Soluciones Empresariales Colibrí definidos en el informe técnico
 * (Anexo M). Datos documentales estáticos: no provienen de la base de
 * datos porque describen el marco de la empresa, no movimientos.
 */
public final class InstrumentosInstitucionales {

    /** Un instrumento del Anexo M: número, tipo, nombre y sección del informe. */
    public record Instrumento(int numero, String tipo, String nombre, String seccion) {
    }

    private static final String DATOS = """
            1|Política|Política de reconocimiento y medición (devengo, partida doble, moneda funcional, consistencia y materialidad)|IX.A.1
            2|Política|Política de ingresos y anticipos de clientes|IX.A.2; Anexo L.C (tabla LXXVIII)
            3|Política|Política de cuentas por cobrar y por pagar (incluye deterioro de cartera)|IX.A.3
            4|Política|Política de propiedad, equipo y software de uso interno (depreciación y amortización)|IX.A.4; IX.Q
            5|Política|Política de costos del servicio y gastos del período|IX.A.5
            6|Política|Política de beneficios a empleados e impuesto a las ganancias|IX.A.6
            7|Política|Política de inventario digital (sistema perpetuo y PEPS por lote de versión liberada)|VI.F.2; X.G; X.J; Anexo I.F; Anexo L.C
            8|Política|Política de crédito y cobranza (plazo normal de 15 a 30 días)|Anexo L.C (tabla LXXVIII)
            9|Política|Política de planilla (separación de salarios, descuentos, aportes patronales y prestaciones)|Anexo L.C; VII.D.4
            10|Política|Política de IVA e ISR|Anexo L.C; Anexo H.3
            11|Política|Política de cierre y conciliación mensual|Anexo L.C; IX.I
            12|Política|Políticas ampliadas para presentación y control de estados financieros|IX.O
            13|Política|Política de parametrización y secuencia de registro (comprobantes IN, EG, DI, AJ, AP y CI)|IX.P; Anexo H.2 (H02.01, H02.06)
            14|Política|Política de parámetros de ajustes y liquidación (vidas útiles, liquidación de IVA)|IX.Q; Anexo H.4
            15|Política|Política de cierre y apertura de ejercicio|IX.R; Anexo H.11
            16|Política|Política de autorización de compras y compromisos (umbrales de $2,000.00 y $5,000.00)|VI.K; Anexo L.B
            17|Política|Política de pagos al gerente, al administrador y a partes relacionadas|VI.K; Anexo A
            18|Política|Política de delegación y límites de aprobación (Gerencia y Administrador Único)|VI.H.2; VI.H.3; VI.K
            19|Política|Política de segregación de funciones y control interno|VII.A.4; Anexo A (tabla XXXIII)
            20|Política|Política de independencia de auditoría y asesorías externas|VI.H.4; VII.D.3
            21|Política|Política de conservación de documentos (mínimo de diez años para el expediente tributario)|VII.D.2
            22|Política|Política de liquidez (reserva de dos meses de egresos y flujo móvil de trece semanas)|Anexo C; VI.L; XIII
            23|Política|Política de capacidad operativa (trabajo simultáneo limitado a la capacidad aprobada)|VI.L
            24|Política|Política de margen mínimo y metas internas anuales (margen bruto de 35 %, 90 % de hitos, cobro pactado, restauración trimestral)|VI.E; X.C; Anexo H.6 (H06.08)
            25|Política|Política de costeo por órdenes (tarifa estándar de mano de obra y tasa de CIF)|X.A; X.B; X.D
            26|Política|Política de costeo por línea de venta (órdenes, PEPS, procesos, ABC y plan facturado)|X.J; Anexo I.E
            27|Política|Política de estimación de costos y precio unitario|X.I; Anexo I.G; Anexo I.H
            28|Política|Política de recursos humanos y control de horas (contrato, jornada, aprobación y segregación)|Anexo I.C; X.A; X.G
            29|Política|Política de costos indirectos de fabricación tecnológicos|Anexo I.D; Anexo H.5 (H05.05)
            30|Política|Política de distribución comercial y reconocimiento de ingresos|X.J; Anexo I.I
            31|Política|Política de control de cambios de alcance (nueva orden antes de ejecutar)|VI.F.3; VI.I.2; X.C
            32|Política|Política de clasificación de compras (activo, pago anticipado, costo de orden o gasto)|VI.I.4
            33|Política|Política de control de activos fijos (ficha, serie, responsable y depreciación)|Anexo L.B (tabla LXXVII); VI.I.4
            34|Política|Política de adquisición de equipo solicitado por clientes (compra contra orden aprobada, sin stock de hardware)|Anexo L.B (tabla LXXVII)
            35|Política|Política de control de accesos del cliente (por función y retiro al terminar la necesidad)|VI.I.2
            36|Política|Política de calidad y paso a producción (dictamen QA previo y sin autoaprobación del programador)|VI.I.1
            37|Política|Política de protección de datos personales y confidencialidad|VII.D.5; VII.B.4; Anexo I.C
            38|Política|Política de contratación de tecnología y licencias de terceros|VII.D.5
            39|Política|Política de cumplimiento normativo (calendario con fundamento, responsable, revisor y constancia)|VII.D.6; Anexo B (tabla XXXIV)
            40|Política|Política de cumplimiento laboral y de seguridad ocupacional (reglamento interno, comité de seguridad y modalidad híbrida)|VII.D.4; Anexo B
            41|Política|Política de seguridad y continuidad (respaldos y metas de recuperación)|IX.G; Anexo H.8 (H08.09); XIII
            42|Política|Política de autenticación y control de acceso al sistema (roles, bloqueo e inactividad)|Anexo H.8 (H08.01, H08.02); IX.G
            43|Política|Política de bitácora y trazabilidad|Anexo H.8 (H08.07); IX.B
            44|Política|Política de no eliminación de registros (desactivar, anular o revertir)|Anexo H.8 (H08.08)
            45|Política|Política de corrección por reversión o ajuste (sin editar asientos contabilizados)|IX.B; Anexo H.2 (H02.05); Anexo L.C
            46|Política|Política de administración del catálogo de cuentas|IX.D; Anexo H.1 (H01.08, H01.10)
            47|Política|Política de prevención de duplicidad de documentos y terceros|Anexo H.8 (H08.06); Anexo H.2 (H02.07)
            48|Política|Política de documentos tributarios electrónicos (DTE)|IX.F; Anexo H.3 (H03.09)
            49|Política|Política de tratamiento de IVA (tasa, crédito, débito, notas y retenciones)|Anexo H.3
            50|Política|Política de ajustes contables al cierre|Anexo H.4
            51|Política|Política de inventarios digitales y tratamientos especiales|Anexo H.5
            52|Política|Política de criterios de aceptación del prototipo y errores críticos|XI.D; Anexo H.10 (H10.04, H10.05)
            53|Proceso|Venta y contratación|VI.J (tabla VI)
            54|Proceso|Construcción y pruebas|VI.J (tabla VI)
            55|Proceso|Implementación|VI.J (tabla VI)
            56|Proceso|Soporte y renovación|VI.J (tabla VI)
            57|Proceso|Facturación y cobranza|VI.J (tabla VI)
            58|Proceso|Compra y pago|VI.J (tabla VI)
            59|Proceso|Planilla y personal|VI.J (tabla VI)
            60|Proceso|Registro y cierre|VI.J (tabla VI)
            61|Proceso|Ciclo de una orden de servicio (oportunidad, contrato, ejecución, QA, aceptación, facturación y reconocimiento)|VI.J
            62|Proceso|Embudo comercial y contratación B2B|VI.F.1
            63|Proceso|Gestión de inventario digital (liberación de versiones, lotes y control de unidades licenciables)|VI.F.2; X.G
            64|Proceso|Comercialización y entrega de licencias descargables|VI.F.3
            65|Proceso|Prestación del servicio SaaS|VI.F.3
            66|Proceso|Desarrollo y entrega de proyectos a medida|VI.F.3
            67|Proceso|Atención de incidentes y soporte técnico|VI.I.2
            68|Proceso|Gestión de cartera de clientes y renovaciones|VI.G
            69|Proceso|Autorización, registro y custodia de operaciones|VI.K; Anexo A
            70|Proceso|Gobierno societario (Junta General, Administrador Único y Gerencia)|VI.H
            71|Proceso|Planificación de capacidad y liquidez|VI.L; Anexo C
            72|Proceso|Gestión del cumplimiento normativo|VII.D.6; Anexo B
            73|Proceso|Proceso documental y control contable|VIII.D
            74|Proceso|Flujo de información: documento fuente, diario, mayor, balanza, ajustes, hoja de trabajo, estados y cierre|IX.B
            75|Proceso|Acumulación de costos por orden|X.D; Anexo L.J
            76|Proceso|Registro y venta de una licencia (CU-INV-01 y CU-REG-01)|XI.A.4
            77|Proceso|Contabilización transaccional y estados de la partida|XI.B.3; Anexo H.2 (H02.09)
            78|Proceso|Cierre mensual|IX.I; Anexo H.11
            79|Proceso|Cierre anual y apertura del ejercicio|IX.I; IX.R; Anexo H.11 (H11.07, H11.08)
            80|Proceso|Reapertura de períodos cerrados|IX.R; Anexo H.11 (H11.06)
            81|Proceso|Pruebas y aceptación del prototipo|XI.D; Anexo G
            82|Procedimiento|Procedimiento de cálculo y validación de operaciones (captura, base, IVA, partida, auxiliares, estados)|IX.C
            83|Procedimiento|Procedimiento de cálculo de IVA (precio neto o con IVA incluido)|IX.C; Anexo H.3 (H03.04)
            84|Procedimiento|Procedimiento de liquidación mensual de IVA|IX.Q; Anexo H.3 (H03.07); Anexo H.11 (H11.11)
            85|Procedimiento|Procedimiento de registro de anticipos y liquidación de proyectos|IX.C.2
            86|Procedimiento|Procedimiento de aplicación de retenciones (IVA e ISR)|IX.C.3
            87|Procedimiento|Procedimiento de cálculo de planilla y cargas patronales (ISSS, previsional e INCAF)|IX.C.4
            88|Procedimiento|Procedimiento de anticipo de renta y liquidación anual de ISR|IX.C.5; Anexo H.11 (H11.14)
            89|Procedimiento|Procedimiento de depreciación y amortización|IX.Q; Anexo H.4 (H04.03)
            90|Procedimiento|Procedimiento de registro de deterioro de inventario digital|Anexo H.4 (H04.06)
            91|Procedimiento|Procedimiento de salida de inventario por PEPS|VI.F.2; XI.A.4; Anexo L.D
            92|Procedimiento|Procedimiento de conteo y conciliación mensual de derechos digitales|X.G; IX.I
            93|Procedimiento|Procedimiento de devoluciones, revocaciones y bajas de licencias|IX.A.5; Anexo H.5 (H05.09)
            94|Procedimiento|Procedimiento de cálculo del costo de una orden y su margen|IX.C; X.C
            95|Procedimiento|Procedimiento de aplicación y liquidación de CIF|X.D; Anexo H.5 (H05.05)
            96|Procedimiento|Procedimiento de estimación de costos (desglose del trabajo y tres puntos PERT)|X.I; Anexo I.G
            97|Procedimiento|Procedimiento de aprobación de horas directas|X.A; Anexo I.C
            98|Procedimiento|Procedimiento de autorización de ventas bajo el margen mínimo|X.C; Anexo H.6 (H06.08)
            99|Procedimiento|Procedimiento de compras (requisición, cotizaciones, recepción y pago)|VI.G; VI.I.4; VI.K
            100|Procedimiento|Procedimiento de elevación de compromisos fuera de presupuesto al Administrador Único|VI.H.4; VI.K
            101|Procedimiento|Procedimiento de entrega de licencia descargable (pedido, DTE, enlace temporal y bitácora)|VI.F.3
            102|Procedimiento|Procedimiento de aprobación y pago de planilla|VII.D.4; Anexo A
            103|Procedimiento|Procedimiento de cobro y aplicación a documentos concretos|VI.J; Anexo A; Anexo L.B
            104|Procedimiento|Procedimiento de conciliaciones al cierre (bancos, cartera, órdenes, inventario, impuestos, planilla y activos)|IX.E; IX.I; Anexo L.I
            105|Procedimiento|Procedimiento de preparación, revisión y autorización del expediente de cierre|Anexo L.I
            106|Procedimiento|Procedimiento de validaciones previas al cierre|Anexo H.11 (H11.04)
            107|Procedimiento|Procedimiento de cancelación de cuentas temporales y traslado del resultado|IX.R; Anexo H.11 (H11.07, H11.08)
            108|Procedimiento|Procedimiento de reapertura de un período (autorización, motivo, respaldo y nuevo cierre)|IX.R; Anexo H.11 (H11.06)
            109|Procedimiento|Procedimiento de corrección por reversión|IX.B; Anexo H.2 (H02.05)
            110|Procedimiento|Procedimiento de alta, edición, desactivación y bloqueo de cuentas|IX.D; Anexo H.1 (H01.08)
            111|Procedimiento|Procedimiento de aprobación del catálogo inicial|Anexo H.1 (H01.10)
            112|Procedimiento|Procedimiento de actualización de la tasa fiscal|Anexo H.1 (H01.07); tabla XXXII (XI.D)
            113|Procedimiento|Procedimiento de importación de asientos desde CSV a borrador|IX.H; Anexo H.9 (H09.06)
            114|Procedimiento|Procedimiento de recuperación de contraseña|Anexo H.8 (H08.02)
            115|Procedimiento|Procedimiento de respaldo de la base de datos (pg_dump)|IX.G; Anexo H.8 (H08.09)
            116|Procedimiento|Procedimiento de restauración de respaldos en entorno aislado (externo al prototipo)|IX.G; Anexo H.8 (H08.09); XIII
            117|Procedimiento|Procedimiento de exportación de reportes (PDF, Excel y ZIP)|IX.H; Anexo H.7 (H07.07); Anexo J.G
            118|Procedimiento|Procedimiento de control de duplicidad de DTE, facturas, partidas y terceros|Anexo H.8 (H08.06)
            119|Procedimiento|Procedimiento de conservación e invalidación de DTE|IX.F; Anexo H.3 (H03.09)
            120|Procedimiento|Procedimiento de evaluación anual de la obligación de auditor fiscal|VII.D.3; Anexo B
            121|Procedimiento|Procedimiento de entrega del encargo al cambiar el profesional responsable|VI.K
            122|Procedimiento|Procedimiento de documentación de pruebas y evidencia|XI.D; Anexo G
            """;

    private InstrumentosInstitucionales() {
    }

    /**
     * Los 122 instrumentos del Anexo M en el orden del informe:
     * 52 políticas, 29 procesos y 41 procedimientos.
     *
     * @return lista inmutable de instrumentos
     */
    public static List<Instrumento> anexoM() {
        return DATOS.lines()
                .map(String::trim)
                .filter(linea -> !linea.isEmpty())
                .map(linea -> {
                    String[] partes = linea.split("\\|", 4);
                    return new Instrumento(Integer.parseInt(partes[0]), partes[1],
                            partes[2], partes[3]);
                })
                .toList();
    }
}
