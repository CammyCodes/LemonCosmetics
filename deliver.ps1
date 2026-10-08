# Installs the built LemonCosmetics jar into both CurseForge instances.
# Build it first:  ./gradlew build
#
# Guards, per instance (the same ones Nethermine's and Nylah's deliveries use):
#   1. Never copy into a RUNNING game. A jar replaced under a running game can
#      corrupt that session, so a running instance (found by the --gameDir on its
#      java command line) is waited for.
#   2. Never downgrade: if a newer lemoncosmetics jar is installed, refuse.
#   3. Never leave two lemoncosmetics jars (a duplicate mod id stops Fabric booting).
#   4. Verify by hash after copying.
#
# Waits up to four hours for a running instance to close, retrying every 15s.
# Usage:  powershell -File deliver.ps1

$ErrorActionPreference = 'Stop'

$repo = $PSScriptRoot
$instances = @(
    "C:\Users\Cammy\curseforge\minecraft\Instances\Fabulously Optimized (1)\mods",
    "C:\Users\Cammy\curseforge\minecraft\Instances\Fabulously Optimized 2\mods"
)

$version = (Get-Content (Join-Path $repo 'gradle.properties') |
    Where-Object { $_ -match '^mod_version=' }) -replace '^mod_version=', ''
if (-not $version) { Write-Host 'Could not read mod_version'; exit 1 }

$name = "lemoncosmetics-$version.jar"
$build = Join-Path $repo "build\libs\$name"
if (-not (Test-Path $build)) { Write-Host "No $name in build\libs: run ./gradlew build first"; exit 1 }
$hash = (Get-FileHash $build -Algorithm SHA256).Hash
$pattern = '^lemoncosmetics-\d+\.\d+\.\d+\.jar$'

function Test-InstanceRunning([string]$mods) {
    $instanceDir = Split-Path -Parent $mods
    try {
        $procs = Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'" -ErrorAction Stop
    } catch {
        return $false
    }
    foreach ($p in $procs) {
        $cl = $p.CommandLine
        if ($cl -and $cl -match '--gameDir' -and $cl.IndexOf($instanceDir, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
            return $true
        }
    }
    return $false
}

function Get-Ver([string]$n) {
    if ($n -match 'lemoncosmetics-(\d+)\.(\d+)\.(\d+)') {
        return [version]("{0}.{1}.{2}" -f $Matches[1], $Matches[2], $Matches[3])
    }
    return [version]'0.0.0'
}
$mine = [version]$version

# 0 = delivered, 2 = waiting, 3 = refused
$state = @{}
foreach ($mods in $instances) { $state[$mods] = 2 }
$said = @{}

for ($pass = 0; $pass -lt 960; $pass++) {
    foreach ($mods in $instances) {
        if ($state[$mods] -ne 2) { continue }
        if (-not (Test-Path $mods)) { Write-Host "No mods folder: $mods"; $state[$mods] = 3; continue }
        $installed = @(Get-ChildItem $mods -Filter '*.jar' | Where-Object { $_.Name -match $pattern })
        $newer = $installed | Where-Object { (Get-Ver $_.Name) -gt $mine }
        if ($newer) {
            Write-Host "REFUSING ${mods}: a newer LemonCosmetics is installed ($($newer.Name -join ', '))"
            $state[$mods] = 3
            continue
        }
        $same = $installed | Where-Object { $_.Name -eq $name }
        if ($same -and (Get-FileHash $same.FullName -Algorithm SHA256).Hash -eq $hash -and $installed.Count -eq 1) {
            Write-Host "Already delivered: $mods\$name"
            $state[$mods] = 0
            continue
        }
        if (Test-InstanceRunning $mods) {
            if (-not $said[$mods]) { Write-Host "Waiting: Minecraft is running from $(Split-Path -Parent $mods)"; $said[$mods] = $true }
            continue
        }
        try {
            $installed | ForEach-Object { Remove-Item $_.FullName -Force -ErrorAction Stop }
            $target = Join-Path $mods $name
            Copy-Item $build $target -Force -ErrorAction Stop
            if ((Get-FileHash $target -Algorithm SHA256).Hash -ne $hash) { throw "hash mismatch after copy" }
            Write-Host "Delivered $name -> $mods"
            $state[$mods] = 0
        } catch {
            # Locked or half-done: try again next pass.
        }
    }
    if (-not ($state.Values -contains 2)) { break }
    Start-Sleep -Seconds 15
}

$bad = $state.GetEnumerator() | Where-Object { $_.Value -ne 0 }
if ($bad) { exit 2 }
exit 0
