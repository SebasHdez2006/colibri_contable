# Iniciar-Colibri.ps1 — lanzador de usuario final de Colibrí.
#
# Compila solo cuando falta el build o se fuerza con -Compilar, genera el
# classpath de ejecución una sola vez y arranca la app con javaw.exe, por lo
# que no aparece ninguna consola negra. Los errores fatales se muestran en un
# cuadro de diálogo y quedan en %LOCALAPPDATA%\Colibri\logs.
#
# Uso:
#   .\scripts\Iniciar-Colibri.ps1                 arranque normal
#   .\scripts\Iniciar-Colibri.ps1 -Compilar       recompila antes de abrir
#   .\scripts\Iniciar-Colibri.ps1 -Silencioso     solo escribe en el log
#   .\scripts\Iniciar-Colibri.ps1 -SinJavaFXCheck omite la comprobación de JavaFX

[CmdletBinding()]
param(
    [switch]$Compilar,
    [switch]$Silencioso
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$modulo = Join-Path $root "colibri-desktop"
$classes = Join-Path $modulo "target\classes"
$cpFile = Join-Path $modulo "target\cp-runtime.txt"
$jarDesktop = Join-Path $modulo "target\colibri-desktop-1.0.0-SNAPSHOT.jar"
$mvnw = Join-Path $root "mvnw.cmd"

$logDir = Join-Path $env:LOCALAPPDATA "Colibri\logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$logFile = Join-Path $logDir ("colibri-" + (Get-Date -Format "yyyyMMdd") + ".log")
$buildLog = Join-Path $logDir "build-ultimo.log"

function Log([string]$msg, [string]$nivel = "INFO") {
    $linea = "{0} [{1}] {2}" -f (Get-Date -Format "HH:mm:ss"), $nivel, $msg
    Add-Content -Path $logFile -Value $linea -Encoding UTF8
    if (-not $Silencioso) { Write-Host $linea }
}

function Fallar([string]$msg) {
    Log $msg "ERROR"
    try {
        Add-Type -AssemblyName PresentationFramework -ErrorAction Stop
        [System.Windows.MessageBox]::Show($msg, "Colibrí — error de arranque",
            "OK", "Error") | Out-Null
    } catch { }
    exit 1
}

Log "=== Inicio de Colibrí ==="
Log "Repositorio: $root"

# ---------------------------------------------------------------- Java ----
function Find-Javaw {
    if ($env:JAVA_HOME) {
        $candidato = Join-Path $env:JAVA_HOME "bin\javaw.exe"
        if (Test-Path $candidato) { return $candidato }
    }
    $enPath = Get-Command javaw.exe -ErrorAction SilentlyContinue
    if ($enPath) { return $enPath.Source }
    $comun = Get-ChildItem `
        "C:\Program Files\Java\*\bin\javaw.exe", `
        "C:\Program Files\Eclipse Adoptium\*\bin\javaw.exe", `
        "C:\Program Files\Microsoft\*\bin\javaw.exe" `
        -ErrorAction SilentlyContinue |
        Sort-Object { $_.Directory.Parent.Name } -Descending |
        Select-Object -First 1
    if ($comun) { return $comun.FullName }
    return $null
}

$javaw = Find-Javaw
if (-not $javaw) {
    Fallar "No se encontró javaw.exe. Instale un JDK 25 o superior y configure JAVA_HOME."
}
Log "Java runtime: $javaw"

# ------------------------------------------------------------- Build ------
$ejecutable = Join-Path $classes "sv\edu\ues\colibri\desktop\ColibriApp.class"
# No basta con comprobar que exista una clase: esa condición dejaba iniciar
# una versión antigua después de editar CSS, vistas o cualquiera de los módulos
# dependientes. Un lanzamiento normal recompila cuando el JAR es anterior a una
# fuente, recurso o POM relevante. -Compilar sigue disponible para forzarlo.
$insumosBuild = @()
foreach ($proyecto in @($root, $root + "\colibri-domain", $root + "\colibri-persistence",
        $root + "\colibri-application", $root + "\colibri-reports", $modulo)) {
    $pom = Join-Path $proyecto "pom.xml"
    if (Test-Path $pom) { $insumosBuild += Get-Item $pom }
    $src = Join-Path $proyecto "src"
    if (Test-Path $src) {
        $insumosBuild += Get-ChildItem $src -Recurse -File
    }
}
$insumoMasReciente = $insumosBuild | Sort-Object LastWriteTime -Descending | Select-Object -First 1
$artefactoVencido = -not (Test-Path $jarDesktop) -or
    ($insumoMasReciente -and $insumoMasReciente.LastWriteTime -gt (Get-Item $jarDesktop).LastWriteTime)
if ($Compilar -or -not (Test-Path $ejecutable) -or $artefactoVencido) {
    if (-not (Test-Path $mvnw)) {
        Fallar "No existe mvnw.cmd en la raíz del proyecto."
    }
    $motivoBuild = if ($Compilar) { "solicitada manualmente" } elseif ($artefactoVencido) {
        "porque hay fuentes o recursos más recientes que el JAR"
    } else { "porque no existe el build" }
    Log "Compilando Colibrí ($motivoBuild; puede tardar la primera vez)..."
    Push-Location $root
    $eap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"   # mvnw escribe avisos en stderr
    try {
        & $mvnw -q -pl colibri-desktop -am install "-Dmaven.test.skip=true" `
            *>&1 | Out-File -FilePath $buildLog -Encoding utf8
        $codigo = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $eap
        Pop-Location
    }
    if ($codigo -ne 0) {
        Fallar "La compilación falló (código $codigo). Detalle en $buildLog"
    }
    Log "Compilación completada."
}

# ------------------------------------------------------------ Classpath --
$dirsConPom = @($root) + (Get-ChildItem $root -Directory |
    Where-Object { Test-Path (Join-Path $_.FullName "pom.xml") } |
    ForEach-Object { $_.FullName })
$pomMasNuevo = $dirsConPom |
    ForEach-Object { Get-Item (Join-Path $_ "pom.xml") } |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
$cpViejo = if (Test-Path $cpFile) { (Get-Item $cpFile).LastWriteTime } else { [datetime]::MinValue }
if (-not (Test-Path $cpFile) -or
    ($pomMasNuevo -and $pomMasNuevo.LastWriteTime -gt $cpViejo)) {
    Log "Generando classpath de ejecución..."
    Push-Location $root
    $eap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & $mvnw -q -pl colibri-desktop dependency:build-classpath `
            "-Dmdep.outputFile=target\cp-runtime.txt" *>&1 |
            Out-File -FilePath $buildLog -Encoding utf8 -Append
        $codigo = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $eap
        Pop-Location
    }
    if ($codigo -ne 0 -or -not (Test-Path $cpFile)) {
        Fallar "No se pudo generar el classpath de ejecución."
    }
}

# Separar jars de JavaFX (van a --module-path) del resto (van a -cp).
$jars = (Get-Content $cpFile -Raw).Trim() -split ';' | Where-Object { $_ }
$javafx = @($jars | Where-Object { $_ -match 'javafx-(base|graphics|controls|fxml|swing|web|media|webcam)' })
$resto  = @($jars | Where-Object { $_ -notmatch 'javafx-' })
if ($javafx.Count -eq 0) {
    Fallar "El classpath no contiene jars de JavaFX; revise $cpFile"
}
$modulePath = ($javafx -join ';')
$classpath = "$classes;" + ($resto -join ';')

# ------------------------------------------------------- Base de datos ----
$dbUrl = "jdbc:postgresql://127.0.0.1:5432/colibri"
foreach ($archivo in @(
        (Join-Path $env:USERPROFILE ".colibri\application.local.properties"),
        (Join-Path $root "application.local.properties"))) {
    if (Test-Path $archivo) {
        $linea = Select-String -Path $archivo -Pattern '^\s*db\.url\s*=\s*(.*)$' |
            Select-Object -Last 1
        if ($linea) { $dbUrl = $linea.Matches[0].Groups[1].Value.Trim() }
    }
}
$pgHost = "127.0.0.1"; $pgPort = 5432
if ($dbUrl -match 'jdbc:postgresql://([^/:]+)(?::(\d+))?/') {
    $pgHost = $Matches[1]
    if ($Matches[2]) { $pgPort = [int]$Matches[2] }
}
try {
    $tcp = New-Object System.Net.Sockets.TcpClient
    $conexion = $tcp.BeginConnect($pgHost, $pgPort, $null, $null)
    if (-not $conexion.AsyncWaitHandle.WaitOne(1500) -or -not $tcp.Connected) {
        Log "Aviso: PostgreSQL no responde en ${pgHost}:${pgPort}; la app lo reportará al iniciar sesión." "WARN"
    } else {
        Log "PostgreSQL accesible en ${pgHost}:${pgPort}."
    }
    $tcp.Close()
} catch {
    Log "Aviso: no se pudo comprobar PostgreSQL (${pgHost}:${pgPort})." "WARN"
}

# ------------------------------------------------------------------ Run --
Log "Lanzando Colibrí..."
$argumentos = "--module-path `"$modulePath`" " +
    "--add-modules javafx.base,javafx.graphics,javafx.controls,javafx.fxml " +
    "-cp `"$classpath`" sv.edu.ues.colibri.desktop.ColibriApp"
$proc = Start-Process -FilePath $javaw -ArgumentList $argumentos `
    -WorkingDirectory $root -PassThru
Log "Aplicación iniciada (PID $($proc.Id)). Log: $logFile"
