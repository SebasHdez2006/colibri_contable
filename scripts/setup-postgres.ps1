# Prepara el entorno local: crea o actualiza el rol colibri, sus bases y el archivo de configuración.
# Configura rol y bases Colibrí en PostgreSQL 18 (puerto 5434 en esta máquina).
# Uso:
#   $env:PGPASSWORD = 'TU_PASSWORD_DE_POSTGRES'
#   .\scripts\setup-postgres.ps1

$ErrorActionPreference = "Stop"
$psql = "C:\Program Files\PostgreSQL\18\bin\psql.exe"
$port = if ($env:COLIBRI_PG_PORT) { $env:COLIBRI_PG_PORT } else { "5434" }
$user = if ($env:COLIBRI_PG_ADMIN) { $env:COLIBRI_PG_ADMIN } else { "postgres" }

Write-Host "Conectando a 127.0.0.1:$port como $user ..."
$probe = & $psql -U $user -h 127.0.0.1 -p $port -d postgres -tAc "SELECT 1" 2>&1
if ($LASTEXITCODE -ne 0) {
    if (-not $env:PGPASSWORD) {
        Write-Error "No se pudo conectar. Defina PGPASSWORD con la contraseña de $user, o habilite trust local en pg_hba.conf y reinicie postgresql-x64-18."
    }
    Write-Host "Reintentando con PGPASSWORD ..."
    $probe = & $psql -U $user -h 127.0.0.1 -p $port -d postgres -tAc "SELECT 1" 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Conexión fallida: $probe"
    }
}

& $psql -U $user -h 127.0.0.1 -p $port -d postgres -v ON_ERROR_STOP=1 -c @"
DO `$`$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'colibri') THEN
    CREATE ROLE colibri LOGIN PASSWORD 'colibri' NOSUPERUSER NOCREATEDB;
  ELSE
    ALTER ROLE colibri WITH LOGIN PASSWORD 'colibri' NOSUPERUSER NOCREATEDB;
  END IF;
END
`$`$;
"@

$dbs = @("colibri", "colibri_test")
foreach ($db in $dbs) {
    $exists = (& $psql -U $user -h 127.0.0.1 -p $port -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$db'").Trim()
    if ($exists -ne "1") {
        & $psql -U $user -h 127.0.0.1 -p $port -d postgres -c "CREATE DATABASE $db OWNER colibri"
        Write-Host "Creada base $db"
    } else {
        Write-Host "Base $db ya existe"
    }
}

$configDir = Join-Path $env:USERPROFILE ".colibri"
New-Item -ItemType Directory -Force -Path $configDir | Out-Null
$config = @"
db.url=jdbc:postgresql://127.0.0.1:${port}/colibri
db.user=colibri
db.password=colibri
db.migrate.user=colibri
db.migrate.password=colibri
"@
Set-Content -Path (Join-Path $configDir "application.local.properties") -Value $config -Encoding UTF8
Write-Host "Config escrita en $configDir\application.local.properties"
Write-Host "Listo. Ejecute: .\scripts\run-colibri.ps1"
