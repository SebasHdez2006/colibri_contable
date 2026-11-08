# Flujo de arranque local: compila desde cero, migra la base a la versión más
# reciente y ejecuta la aplicación JavaFX con la semilla del Anexo L activa.
# Evita mezclar la interfaz recién compilada con JARs antiguos de ~/.m2.
#
# Uso:
#   .\scripts\run-colibri.ps1              compila, migra y ejecuta
#   .\scripts\run-colibri.ps1 -Reset       borra TODOS los datos (conserva los
#                                          usuarios contador y soporte) y vuelve
#                                          a poblar referencia + Anexo L vía SQL
#   .\scripts\run-colibri.ps1 -SinSemilla  ejecuta sin activar la semilla
#   .\scripts\run-colibri.ps1 -SoloDatos   compila y prepara la base sin abrir la
#                                        interfaz (útil junto con -Reset)
#
# La semilla del Anexo L es opt-in (COLIBRI_SEED_ANEXO_L). Si la base contiene
# partidas ajenas al anexo, el seed rechaza el arranque; en ese caso ejecute
# de nuevo con -Reset.

[CmdletBinding()]
param(
    [switch]$Reset,
    [switch]$SinSemilla,
    [switch]$SoloDatos
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$mvnw = Join-Path $root "mvnw.cmd"

function Get-ConfigValue {
    param([string]$Key, [string]$EnvName)
    $value = $null
    # Misma precedencia que DatabaseConfig.load(): ~/.colibri, luego la raíz
    # del proyecto, luego variables de entorno.
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
    $candidato = Get-ChildItem "C:\Program Files\PostgreSQL\*\bin\psql.exe" -ErrorAction SilentlyContinue |
        Where-Object { $_.Directory.Parent.Name -match '^\d+(\.\d+)*$' } |
        Sort-Object { [version]($_.Directory.Parent.Name + ".0") } -Descending |
        Select-Object -First 1
    if ($candidato) { return $candidato.FullName }
    return $null
}

function Invoke-PsqlScalar {
    param([string]$Sql)
    $out = & $script:PsqlPath -h $script:PgHost -p $script:PgPort -U $script:PgUser `
        -d $script:PgDb -tA -v ON_ERROR_STOP=1 -c $Sql 2>$null
    if ($LASTEXITCODE -ne 0) { return $null }
    return ("$out").Trim()
}

function Invoke-PsqlFile {
    param([string]$File, [string[]]$Vars = @())
    & $script:PsqlPath -h $script:PgHost -p $script:PgPort -U $script:PgUser `
        -d $script:PgDb -v ON_ERROR_STOP=1 @Vars -f $File
    if ($LASTEXITCODE -ne 0) { throw "Falló $(Split-Path $File -Leaf) (código $LASTEXITCODE)." }
}

Push-Location $root
$pgPassAnterior = $env:PGPASSWORD
$pgEncAnterior = $env:PGCLIENTENCODING
$semillaAnterior = $env:COLIBRI_SEED_ANEXO_L
try {
    Write-Host "Compilando la versión más reciente de Colibrí..."
    # No compilar fuentes de prueba durante el arranque; el script debe usar
    # los JAR actuales y reservar la validación de pruebas para el pipeline.
    & $mvnw -pl colibri-desktop -am clean install "-Dmaven.test.skip=true"
    if ($LASTEXITCODE -ne 0) {
        throw "La compilación falló (código $LASTEXITCODE)."
    }

    Write-Host "Migrando la base de datos a la versión más reciente..."
    & $mvnw -q -pl colibri-persistence org.codehaus.mojo:exec-maven-plugin:3.5.0:java `
        "-Dexec.mainClass=sv.edu.ues.colibri.persistence.config.FlywayMigrator" `
        "-Dexec.cleanupDaemonThreads=false"
    if ($LASTEXITCODE -ne 0) {
        throw "La migración de la base falló (código $LASTEXITCODE)."
    }

    # Parámetros de conexión con la misma precedencia que la aplicación.
    $dbUrl = Get-ConfigValue -Key "db.url" -EnvName "COLIBRI_DB_URL"
    if (-not $dbUrl) { $dbUrl = "jdbc:postgresql://127.0.0.1:5432/colibri" }
    $script:PgUser = Get-ConfigValue -Key "db.migrate.user" -EnvName "COLIBRI_DB_MIGRATE_USER"
    if (-not $script:PgUser) {
        $script:PgUser = Get-ConfigValue -Key "db.user" -EnvName "COLIBRI_DB_USER"
    }
    if (-not $script:PgUser) { $script:PgUser = "colibri" }
    $script:PgPass = Get-ConfigValue -Key "db.migrate.password" -EnvName "COLIBRI_DB_MIGRATE_PASSWORD"
    if (-not $script:PgPass) {
        $script:PgPass = Get-ConfigValue -Key "db.password" -EnvName "COLIBRI_DB_PASSWORD"
    }
    if (-not $script:PgPass) { $script:PgPass = "colibri" }
    $script:PgHost = "127.0.0.1"; $script:PgPort = 5432; $script:PgDb = "colibri"
    if ($dbUrl -match 'jdbc:postgresql://([^/:]+)(?::(\d+))?/([^?]+)') {
        $script:PgHost = $Matches[1]
        if ($Matches[2]) { $script:PgPort = [int]$Matches[2] }
        $script:PgDb = $Matches[3]
    }

    $script:PsqlPath = Find-Psql
    $env:PGPASSWORD = $script:PgPass
    # Los .sql son UTF-8; en consolas cp1252 psql los leería mal sin esto.
    $env:PGCLIENTENCODING = "UTF8"
    $partidas = $null
    $anexoL = $null
    if ($script:PsqlPath) {
        $partidas = Invoke-PsqlScalar "SELECT count(*) FROM partida"
        $anexoL = Invoke-PsqlScalar `
            "SELECT count(*) FROM evento_contable WHERE clave_unica LIKE 'SEED-ANEXOL-2026-%'"
    }

    if ($Reset) {
        if (-not $script:PsqlPath) {
            throw "-Reset requiere psql en PATH o bajo C:\Program Files\PostgreSQL."
        }
        $usuarios = Invoke-PsqlScalar `
            "SELECT count(*) FROM contador WHERE usuario IN ('contador', 'soporte')"
        if ($usuarios -ne "2") {
            Write-Host "Creando las cuentas iniciales contador y soporte..."
            & $mvnw -q -pl colibri-application `
                org.codehaus.mojo:exec-maven-plugin:3.5.0:java `
                "-Dexec.mainClass=sv.edu.ues.colibri.application.bootstrap.BootstrapService" `
                "-Dexec.cleanupDaemonThreads=false"
            if ($LASTEXITCODE -ne 0) {
                throw "No se pudieron crear las cuentas iniciales (código $LASTEXITCODE)."
            }
        }
        Write-Host "Borrando los datos (se conservan contador y soporte) y resembrando el Anexo L..."
        Invoke-PsqlFile (Join-Path $root "scripts\db\reset_total.sql") @("-v", "confirmar=SI")
        Invoke-PsqlFile (Join-Path $root "scripts\db\seed_00_referencia.sql")
        Invoke-PsqlFile (Join-Path $root "scripts\db\seed_anexo_l.sql")
        Invoke-PsqlFile (Join-Path $root "scripts\db\verificar_anexo_l.sql")
    } elseif (-not $SinSemilla -and $null -ne $partidas -and [int]$partidas -gt 0 `
            -and -not ([int]$anexoL -gt 0)) {
        throw "La base contiene $partidas partidas ajenas al Anexo L y la semilla no puede " +
              "mezclarse con ellas. Ejecute '.\scripts\run-colibri.ps1 -Reset' para " +
              "reemplazarlas, o use -SinSemilla para arrancar con los datos existentes."
    }

    if ($SoloDatos) {
        Write-Host "Base preparada; -SoloDatos evita abrir la interfaz."
        return
    }

    if (-not $SinSemilla) {
        $env:COLIBRI_SEED_ANEXO_L = "true"
    }
    Write-Host "Iniciando Colibrí..."
    & $mvnw -pl colibri-desktop javafx:run
    if ($LASTEXITCODE -ne 0) {
        throw "La aplicación terminó con código $LASTEXITCODE."
    }
} finally {
    $env:PGPASSWORD = $pgPassAnterior
    $env:PGCLIENTENCODING = $pgEncAnterior
    $env:COLIBRI_SEED_ANEXO_L = $semillaAnterior
    Pop-Location
}
