# Casos de uso vigentes del actor Soporte Técnico

Colibrí es una aplicación de escritorio conectada a PostgreSQL local. Esta especificación describe las acciones disponibles en las vistas y servicios actuales. El actor técnico administra cuentas y entorno, consulta registros técnicos y ejecuta respaldos manuales; no consulta saldos ni registra operaciones contables.

## Usuarios

### CU-ST-01 Crear y administrar cuentas de usuario

- **Actor:** Soporte Técnico.
- **Precondición:** Sesión activa con rol de soporte técnico.
- **Flujo:** Consultar usuarios y cantidad de cuentas activas; crear una cuenta con usuario, nombre, correo, rol y contraseña inicial; o seleccionar una cuenta existente para cambiar nombre, correo y estado activo o inactivo.
- **Resultado:** La creación registra una entrada de bitácora y devuelve un código de recuperación. La actualización realizada por la pantalla se guarda, pero esa operación no crea por sí misma una entrada de bitácora ni usa las protecciones del método especializado para evitar desactivar la propia cuenta o la última cuenta activa.
- **Alcance:** La vista permite escoger el rol sólo al crear. La edición disponible no cambia usuario, rol ni contraseña.
- **Seguridad:** Los servicios de mantenimiento exigen rol de soporte técnico.

## Base de datos

### CU-ST-02 Generar respaldo manual

- **Actor:** Soporte Técnico.
- **Precondición:** PostgreSQL disponible y `pg_dump` de PostgreSQL 18 instalado en `C:\Program Files\PostgreSQL\18\bin\pg_dump.exe`.
- **Flujo:** Consultar la fecha del último respaldo registrado, confirmar la acción y solicitar el respaldo.
- **Resultado:** `pg_dump` crea un archivo de respaldo en `~/.colibri/backups`; al completar el flujo se actualiza la fecha del último respaldo en el entorno técnico.
- **Límite:** La ruta del ejecutable no es configurable. No hay programación, cifrado, retención, respaldo incremental, historial visible de resultados ni restauración desde la aplicación. La restauración debe ejecutarse y comprobarse externamente en una base aislada.

## Entorno

### CU-ST-03 Probar y guardar configuración técnica

- **Actor:** Soporte Técnico.
- **Precondición:** Sesión activa y valores de conexión válidos para guardar.
- **Entradas:** URL JDBC, usuario y contraseña de PostgreSQL, ruta de reportes, ruta DTE e impresora.
- **Flujo:** Revisar los valores almacenados, introducir cambios y seleccionar **Probar conexión / Guardar configuración**. La prueba abre una conexión con los valores introducidos y la aplicación intenta crear la ruta de reportes indicada.
- **Resultado:** Se actualiza el registro de entorno técnico y se intenta persistir localmente URL, usuario y contraseña de base de datos, ruta de reportes e impresora en `~/.colibri/application.local.properties`. La ruta DTE queda en el registro de entorno, pero no se escribe en ese archivo local.
- **Límites:** Los errores al persistir el archivo local se silencian y la configuración activa de la aplicación se crea al iniciar, por lo que una nueva conexión requiere reiniciar para ser usada por los servicios en ejecución. Una contraseña vacía se prueba como conexión sin contraseña, no como reutilización garantizada de la contraseña ya guardada. No hay integración con Hacienda ni verificación de impresoras de red.

## Monitoreo

### CU-ST-04 Consultar registros técnicos

- **Actor:** Soporte Técnico.
- **Flujo:** Consultar hasta 200 filas de `log_tecnico` ordenadas por fecha y filtrar por `TODOS`, `INFO`, `WARN`, `ERROR` o `FATAL`. Un doble clic muestra el detalle o la traza disponible.
- **Resultado:** Panel de consulta de la tabla de registros técnicos. Cuando la tabla está vacía, el inicio de la aplicación inserta registros de demostración.
- **Límite:** El log operativo de la aplicación se escribe en `~/.colibri/colibri.log` y no alimenta automáticamente esta tabla; por tanto el panel no representa necesariamente los eventos técnicos reales de la ejecución.

## Accesos

### CU-ST-05 Consultar accesos y desbloquear cuentas

- **Actor:** Soporte Técnico.
- **Flujo:** Consultar la bitácora de accesos y estados de usuario, seleccionar una cuenta y solicitar desbloqueo con generación de clave temporal.
- **Resultado:** Se reinician los intentos fallidos, se libera el bloqueo, la cuenta queda activa, se reemplaza su hash de contraseña y se registra una entrada `CLAVE_TEMPORAL` en bitácora.
- **Límite:** La clave emitida sustituye la contraseña con vencimiento de 24 horas; al iniciar sesión con ella, el sistema obliga a definir una clave propia que la invalida. Si vence sin usarse, soporte debe emitir otra. El código de recuperación es un mecanismo distinto y sí rota al utilizarse.
