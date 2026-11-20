# Elementos descartados del repositorio

Este repositorio contiene solo lo que participa en algún proceso vigente del sistema:
**compilación** (`mvnw`, POM raíz y POM de los 5 módulos), **ejecución** (`colibri-desktop`
y sus recursos empaquetados), **migración del esquema** (Flyway `V1`–`V28`),
**pruebas** (`src/test` de cada módulo y `scripts/anexo_l/test_extraer_anexo_l.py`),
**preparación de base y datos** (`scripts/setup-postgres.*`, `scripts/run-colibri.ps1`,
`scripts/anexo_l/`, `scripts/db/`) y la **documentación** citada en el `README.md`.

Estado de verificación documental: la versión actual contiene las migraciones `V1` a `V28`.
Los resultados de `./mvnw clean test` y de las pruebas que requieren PostgreSQL dependen del
entorno y deben registrarse para cada ejecución; este archivo no conserva conteos ni resultados
históricos como si fueran evidencia vigente. La validación del esquema debe ejecutarse contra una
base desechable, porque las semillas y los scripts de reinicio pueden reemplazar datos operativos.

Lo siguiente existía en el directorio de trabajo original y **no** se versiona:

| Elemento | Por qué no interviene en ningún proceso actual |
|---|---|
| `**/target/` | Salida de compilación de Maven. Se regenera con `./mvnw package`; ya estaba excluida por `.gitignore`. |
| `src/` (raíz: `model/Cuenta.java`, `view/MainWindow.java`) | Código huérfano de una versión mínima en Swing. El POM raíz tiene `packaging=pom`, por lo que Maven nunca compila `src/` de la raíz, y ninguna clase del sistema importa los paquetes `model` o `view`. |
| `_backup_pre_restore/` | Copia de seguridad de una arquitectura anterior (paquete `ui.nav`, `PlantillasView`, `SocietariosView`, pruebas de integración antiguas) generada por `restore_old_arch.py`. No es módulo del POM ni está en el classpath; la versión vigente es la de `colibri-*/src`. |
| `tmp/` | Espacio de trabajo para redactar el informe (scripts de docx/pdf, renders de páginas, extracciones de texto, borradores `.docx`/`.pdf`). Ningún módulo, script de arranque ni prueba lo usa; ya estaba excluido por `.gitignore`. |
| `img/` (raíz) | Paquete de iconos y splash en múltiples formatos. La aplicación carga su identidad visual exclusivamente desde `colibri-desktop/src/main/resources/sv/edu/ues/colibri/desktop/img/` (`BrandAssets`), que sí se versiona. |
| `scripts/gen_catalogo.py` | Generador de un solo uso de `V2__catalogo.sql`. Esa migración ya existe y está aplicada; regenerarla alteraría su checksum y Flyway rechazaría el arranque. Ningún script ni el README de ejecución lo invocan. |
| `scripts/anexo_l/__pycache__/` | Bytecode de Python generado automáticamente al ejecutar los scripts. |
| `cve-dependencies.txt`, `cve-dependency-tree.txt`, `colibri-*/cve-dependencies.txt` | Salidas puntuales de un análisis de dependencias (vacías o con "none"). No se consumen en el build. |
| `NUL` / `nul` | Archivos vacíos o casi vacíos creados por error al redirigir a `NUL`/`nul` desde una shell no-Windows. Windows reserva el nombre y no se pueden leer por las vías normales; conviene borrarlos con `del \\?\<ruta>\nul` si estorban. |
| `.vs/` | Caché e índices de Visual Studio (sqlite, índices de Copilot). Específicos de la máquina. |
| `.vscode/` | Configuración local del editor; ya estaba excluida por `.gitignore`. |
| `.continue/` | Reglas del asistente de IA Continue; no afectan compilación ni ejecución. |
| `.kiro/` | Especificaciones de la herramienta Kiro (`specs/auditoria-forense-contable/` con `bugfix.md`, `design.md` y `tasks.md`). Documentan el proceso de la auditoría forense; el resultado consolidado sí se versiona en `AUDITORIA_CORRECCIONES_RESUMEN.md`. No afectan compilación ni ejecución. |
| `.github/modernize/` | Hooks de registro de la herramienta de migración de Java de Copilot; su propio `.gitignore` (`**/*`) ya los excluía. No son workflows de GitHub Actions. |
