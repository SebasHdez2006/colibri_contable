# Casos de uso del actor Soporte Técnico

Colibrí es una aplicación de escritorio conectada a PostgreSQL local. Esta especificación refleja las acciones disponibles en las vistas y servicios actuales. El actor técnico administra cuentas y entorno, consulta bitácoras y ejecuta respaldos manuales; no consulta saldos ni registra operaciones contables.

## Usuarios

### CU-ST-01 Crear y administrar cuentas de usuario

- **Actor:** Soporte Técnico.
- **Precondición:** sesión activa con rol técnico.
- **Flujo:** consultar usuarios y conteo de cuentas activas; crear una cuenta indicando usuario, nombre, correo, rol y contraseña inicial; o seleccionar una cuenta existente para actualizar nombre, correo y estado activo/inactivo.
- **Resultado:** la cuenta se guarda y el sistema registra la acción correspondiente. Los mecanismos de recuperación se entregan y resguardan conforme al procedimiento local.
- **Alcance:** la vista permite elegir el rol al crear una cuenta. La edición disponible no cambia usuario, rol ni contraseña.
- **Seguridad:** el mantenimiento de usuarios está protegido para Soporte Técnico.

## Base de datos

### CU-ST-02 Generar respaldo manual

- **Actor:** Soporte Técnico.
- **Precondición:** PostgreSQL está disponible y `pg_dump` de PostgreSQL 18 está instalado en la ruta configurada por el prototipo.
- **Flujo:** consultar la fecha del último respaldo registrado, confirmar la acción y solicitar el respaldo.
- **Resultado:** `pg_dump` crea un archivo de base de datos en `~/.colibri/backups`; la interfaz registra fecha y resultado en el entorno técnico.
- **Límite:** no hay programación, cifrado, retención, respaldo incremental ni restauración desde la aplicación. La restauración debe ejecutarse y comprobarse mediante un procedimiento externo en una base aislada.

## Entorno

### CU-ST-03 Probar y guardar configuración técnica

- **Actor:** Soporte Técnico.
- **Precondición:** sesión activa y valores de conexión válidos para guardar.
- **Entradas:** URL JDBC, usuario y contraseña de PostgreSQL, ruta de reportes, ruta DTE e impresora.
- **Flujo:** revisar los valores almacenados, introducir cambios y seleccionar **Probar conexión / Guardar configuración**.
- **Resultado:** el servicio intenta conectar a PostgreSQL; si la conexión es exitosa, crea la ruta de reportes cuando corresponde, actualiza la configuración técnica y guarda los valores locales aplicables en `~/.colibri/application.local.properties`.
- **Límite:** la ruta DTE y la impresora se guardan como parámetros. Esto no integra con Hacienda ni verifica una impresora de red.

## Monitoreo

### CU-ST-04 Consultar bitácora técnica

- **Actor:** Soporte Técnico.
- **Flujo:** consultar los eventos técnicos ordenados por fecha y filtrar por nivel (`TODOS`, `INFO`, `WARN`, `ERROR`, `FATAL`). Doble clic sobre un evento muestra el detalle o la traza disponible.
- **Resultado:** hasta 200 eventos para la consulta; el panel apoya el diagnóstico local y no expone datos financieros.

## Accesos

### CU-ST-05 Auditar accesos y desbloquear cuentas

- **Actor:** Soporte Técnico.
- **Flujo:** consultar la bitácora de accesos y estados de usuario, seleccionar una cuenta y solicitar desbloqueo con generación de clave temporal.
- **Resultado:** se reinician los intentos fallidos, se libera el bloqueo, la cuenta queda activa y se entrega una clave temporal de un solo uso. La operación conserva trazabilidad.
