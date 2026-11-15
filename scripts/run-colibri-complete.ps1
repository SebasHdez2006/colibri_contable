# Carga y ejecuta el ejercicio contable integral de Colibrí.
#
# Uso:
#   .\scripts\run-colibri-complete.ps1 -Reset             # carga el caso integral y abre la aplicación
#   .\scripts\run-colibri-complete.ps1 -Reset -SoloDatos  # sólo prepara la base
#   .\scripts\run-colibri-complete.ps1 -SoloDatos         # valida los datos integrales ya cargados
#
# -Reset borra los datos operativos existentes (conserva los usuarios contador
# y soporte), por lo que está pensado para una base de demostración.

[CmdletBinding()]
param(
    [switch]$Reset,
    [switch]$SoloDatos
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$mvnw = Join-Path $root "mvnw.cmd"

function Get-ConfigValue {
    param([string]$Key, [string]$EnvName)
    $value = $null
    foreach ($file in @(
            (Join-Path $HOME ".colibri\application.local.properties"),
            (Join-Path $root "application.local.properties"))) {
        if (Test-Path $file) {
            $line = Select-String -Path $file -Pattern "^\s*$([regex]::Escape($Key))\s*=\s*(.*)$" |
                Select-Object -Last 1
            if ($line) { $value = $line.Matches[0].Groups[1].Value.Trim() }
        }
    }
    $envValue = [Environment]::GetEnvironmentVariable($EnvName)
    if (-not [string]::IsNullOrWhiteSpace($envValue)) { $value = $envValue.Trim() }
    return $value
}

function Find-Psql {
    $cmd = Get-Command psql -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidate = Get-ChildItem "C:\Program Files\PostgreSQL\*\bin\psql.exe" -ErrorAction SilentlyContinue |
        Where-Object { $_.Directory.Parent.Name -match '^\d+(\.\d+)*$' } |
        Sort-Object { [version]($_.Directory.Parent.Name + ".0") } -Descending |
        Select-Object -First 1
    if ($candidate) { return $candidate.FullName }
    return $null
}

function Invoke-PsqlFile {
    param([string]$File, [string[]]$Vars = @())
    & $script:PsqlPath -h $script:PgHost -p $script:PgPort -U $script:PgUser `
        -d $script:PgDb -v ON_ERROR_STOP=1 @Vars -f $File
    if ($LASTEXITCODE -ne 0) { throw "Falló $(Split-Path $File -Leaf) (código $LASTEXITCODE)." }
}

function Invoke-PsqlScalar {
    param([string]$Sql)
    $out = & $script:PsqlPath -h $script:PgHost -p $script:PgPort -U $script:PgUser `
        -d $script:PgDb -tA -v ON_ERROR_STOP=1 -c $Sql 2>$null
    if ($LASTEXITCODE -ne 0) { return $null }
    return ("$out").Trim()
}

Push-Location $root
$previousPgPassword = $env:PGPASSWORD
$previousPgEncoding = $env:PGCLIENTENCODING
try {
    Write-Host "Compilando la versión más reciente de Colibrí..."
    & $mvnw -pl colibri-desktop -am clean install "-Dmaven.test.skip=true"
    if ($LASTEXITCODE -ne 0) { throw "La compilación falló (código $LASTEXITCODE)." }

    Write-Host "Migrando la base de datos a la versión más reciente..."
    & $mvnw -q -pl colibri-persistence org.codehaus.mojo:exec-maven-plugin:3.5.0:java `
        "-Dexec.mainClass=sv.edu.ues.colibri.persistence.config.FlywayMigrator" `
        "-Dexec.cleanupDaemonThreads=false"
    if ($LASTEXITCODE -ne 0) { throw "La migración de la base falló (código $LASTEXITCODE)." }

    $dbUrl = Get-ConfigValue -Key "db.url" -EnvName "COLIBRI_DB_URL"
    if (-not $dbUrl) { $dbUrl = "jdbc:postgresql://127.0.0.1:5432/colibri" }
    $script:PgUser = Get-ConfigValue -Key "db.migrate.user" -EnvName "COLIBRI_DB_MIGRATE_USER"
    if (-not $script:PgUser) { $script:PgUser = Get-ConfigValue -Key "db.user" -EnvName "COLIBRI_DB_USER" }
    if (-not $script:PgUser) { $script:PgUser = "colibri" }
    $pgPassword = Get-ConfigValue -Key "db.migrate.password" -EnvName "COLIBRI_DB_MIGRATE_PASSWORD"
    if (-not $pgPassword) { $pgPassword = Get-ConfigValue -Key "db.password" -EnvName "COLIBRI_DB_PASSWORD" }
    if (-not $pgPassword) { $pgPassword = "colibri" }
    $script:PgHost = "127.0.0.1"; $script:PgPort = 5432; $script:PgDb = "colibri"
    if ($dbUrl -match 'jdbc:postgresql://([^/:]+)(?::(\d+))?/([^?]+)') {
        $script:PgHost = $Matches[1]
        if ($Matches[2]) { $script:PgPort = [int]$Matches[2] }
        $script:PgDb = $Matches[3]
    }
    $script:PsqlPath = Find-Psql
    if (-not $script:PsqlPath) { throw "No se encontró psql en PATH ni bajo C:\Program Files\PostgreSQL." }
    $env:PGPASSWORD = $pgPassword
    $env:PGCLIENTENCODING = "UTF8"

    if ($Reset) {
        $users = Invoke-PsqlScalar "SELECT count(*) FROM contador WHERE usuario IN ('contador', 'soporte')"
        if ($users -ne "2") {
            Write-Host "Creando las cuentas iniciales contador y soporte..."
            & $mvnw -q -pl colibri-application org.codehaus.mojo:exec-maven-plugin:3.5.0:java `
                "-Dexec.mainClass=sv.edu.ues.colibri.application.bootstrap.BootstrapService" `
                "-Dexec.cleanupDaemonThreads=false"
            if ($LASTEXITCODE -ne 0) { throw "No se pudieron crear las cuentas iniciales (código $LASTEXITCODE)." }
        }
        Write-Host "Reemplazando los datos operativos por el ejercicio contable integral..."
        Invoke-PsqlFile (Join-Path $root "scripts\db\reset_total.sql") @("-v", "confirmar=SI")
        Invoke-PsqlFile (Join-Path $root "scripts\db\seed_00_referencia.sql")
        Invoke-PsqlFile (Join-Path $root "scripts\db\seed_anexo_l.sql")
        Invoke-PsqlFile (Join-Path $root "scripts\db\seed_ejercicio_completo.sql")
    }

    $loaded = Invoke-PsqlScalar "SELECT count(*) FROM evento_contable WHERE clave_unica = 'SEED-COMPLETO-2026-COBERTURA-CATALOGO'"
    if ($loaded -ne "1") {
        throw "No está cargado el ejercicio integral. Ejecute '.\scripts\run-colibri-complete.ps1 -Reset'."
    }
    Invoke-PsqlFile (Join-Path $root "scripts\db\verificar_ejercicio_completo.sql")

    if ($SoloDatos) {
        Write-Host "Ejercicio integral preparado y verificado; -SoloDatos evita abrir la interfaz."
        return
    }
    Write-Host "Iniciando Colibrí con el ejercicio contable integral..."
    & $mvnw -pl colibri-desktop javafx:run
    if ($LASTEXITCODE -ne 0) { throw "La aplicación terminó con código $LASTEXITCODE." }
} finally {
    $env:PGPASSWORD = $previousPgPassword
    $env:PGCLIENTENCODING = $previousPgEncoding
    Pop-Location
}
