param(
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Matrix = Get-Content (Join-Path $Root 'versions.json') -Raw | ConvertFrom-Json
$Dist = Join-Path $Root 'dist'
$Mvn = if ($env:MAVEN_CMD) { $env:MAVEN_CMD } else { 'mvn' }
$Gradle = if ($env:GRADLE_CMD) { $env:GRADLE_CMD } else { 'gradle' }

function Invoke-Build($Directory, [hashtable]$Properties) {
    $arguments = @()
    foreach ($key in $Properties.Keys) {
        $arguments += "-P$key=$($Properties[$key])"
    }
    Write-Host "  gradle $($arguments -join ' ')"
    if (-not $DryRun) {
        Push-Location $Directory
        try {
            & $Gradle @arguments
            if ($LASTEXITCODE -ne 0) { throw "Gradle failed in $Directory" }
        } finally {
            Pop-Location
        }
    }
}

function Copy-LoaderJar($Directory, $Loader, $Family) {
    if ($DryRun) { return }
    $target = Join-Path $Dist (Join-Path $Loader $Family)
    New-Item -ItemType Directory -Force -Path $target | Out-Null
    Get-ChildItem -Path (Join-Path $Directory 'build/libs') -Filter '*.jar' |
        Where-Object { $_.Name -notlike '*sources*' } |
        ForEach-Object { Copy-Item $_.FullName $target -Force }
}

Write-Host "Minecraft server report build matrix"
Write-Host "Paper: $($Matrix.paper.compile) - $($Matrix.paper.range)"
if (-not $DryRun) {
    Push-Location $Root
    try {
        & $Mvn -q -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw 'Maven build failed' }
    } finally {
        Pop-Location
    }
    $paperTarget = Join-Path $Root 'dist/paper/universal'
    New-Item -ItemType Directory -Force -Path $paperTarget | Out-Null
    Copy-Item (Join-Path $Root 'paper/target/minecraft-server-report-paper.jar') $paperTarget -Force
}

foreach ($entry in $Matrix.fabric) {
    Write-Host "Fabric $($entry.family): compile=$($entry.compile) range=$($entry.range)"
    if ($entry.available -eq $false) { Write-Host '  skipped (not released)'; continue }
    if ($entry.compile -eq 'latest-1.21' -or $entry.yarn -eq 'auto') {
        Write-Host '  skipped until exact loader/Yarn/API versions are resolved'
        continue
    }
    if ($entry.yarn -eq 'none') {
        Write-Host '  skipped: 26.x needs the official-name adapter source'
        continue
    }
    Invoke-Build (Join-Path $Root 'fabric') @{
        minecraft_family = $entry.family
        minecraft_version = $entry.compile
        minecraft_range = $entry.range
        yarn_mappings = $entry.yarn
        loader_version = $entry.loader
        fabric_api_version = $entry.api
        loom_version = $entry.loom
        java_version = $entry.java
    }
    Copy-LoaderJar (Join-Path $Root 'fabric') 'fabric' $entry.family
}

foreach ($entry in $Matrix.forge) {
    Write-Host "Forge $($entry.family): compile=$($entry.compile) range=$($entry.range)"
    if ($entry.available -eq $false) { Write-Host '  skipped (not released)'; continue }
    Invoke-Build (Join-Path $Root 'forge') @{
        minecraft_family = $entry.family
        minecraft_version = $entry.compile
        minecraft_range = $entry.range
        forge_version = $entry.forge
        forge_loader_range = $entry.loaderRange
        forge_range = $entry.forgeRange
        java_version = $entry.java
    }
    Copy-LoaderJar (Join-Path $Root 'forge') 'forge' $entry.family
}

foreach ($entry in $Matrix.neoforge) {
    Write-Host "NeoForge $($entry.family): compile=$($entry.compile) range=$($entry.range)"
    if ($entry.available -eq $false) { Write-Host '  skipped (not released)'; continue }
    if ($entry.neoforge -eq 'auto') {
        Write-Host '  skipped until NeoForge version is resolved'
        continue
    }
    Invoke-Build (Join-Path $Root 'neoforge') @{
        minecraft_family = $entry.family
        minecraft_version = $entry.compile
        minecraft_range = $entry.range
        neoforge_version = $entry.neoforge
        neoforge_loader_range = $entry.loaderRange
        neoforge_range = $entry.neoforgeRange
        java_version = $entry.java
    }
    Copy-LoaderJar (Join-Path $Root 'neoforge') 'neoforge' $entry.family
}

if (-not $DryRun) {
    Get-ChildItem -Path $Dist -Recurse -Filter '*.jar' |
        Get-FileHash -Algorithm SHA256 |
        ForEach-Object { "$($_.Hash)  $($_.Path)" } |
        Set-Content (Join-Path $Dist 'checksums.txt')
    Write-Host "Artifacts: $Dist"
}
