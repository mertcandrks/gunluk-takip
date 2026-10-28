param([Parameter(Mandatory = $true)][string]$Device, [string]$BridgeConfig)

$ErrorActionPreference = 'Stop'
$binary = Join-Path $PSScriptRoot 'gunluk-takip-mcp.exe'
if (-not (Test-Path -LiteralPath $binary)) { throw 'Build gunluk-takip-mcp.exe first.' }
$codexCommand = Get-Command codex -ErrorAction Stop
$bridgeArguments = @()
if ($BridgeConfig) { $bridgeArguments = @('--config', $BridgeConfig) }
$found = $false
try {
    $existingJson = & $codexCommand mcp get gunluk_takip --json 2>$null
    $found = $LASTEXITCODE -eq 0
} catch { $found = $false }
if ($found) {
    $existing = ($existingJson -join "`n") | ConvertFrom-Json
    $existingCommand = $existing.transport.command
    if (-not $existingCommand) { $existingCommand = $existing.command }
    if (-not [string]::Equals($existingCommand, $binary, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'A different gunluk_takip server already exists. It was not overwritten.'
    }
    $existingArguments = $existing.transport.args
    if (-not $existingArguments) { $existingArguments = $existing.args }
    if ((@($existingArguments) -join "`0") -cne ($bridgeArguments -join "`0")) {
        throw 'The existing server uses another credential profile. It was not overwritten.'
    }
}

& $binary --pair --device $Device @bridgeArguments
if ($LASTEXITCODE -ne 0) { throw 'Pairing failed. No Codex setting was changed.' }
if ($found) { Write-Output 'Pairing updated; existing MCP registration preserved.'; return }

$codexDirectory = $env:CODEX_HOME
if (-not $codexDirectory) { $codexDirectory = Join-Path $env:USERPROFILE '.codex' }
$configFile = Join-Path $codexDirectory 'config.toml'
if (Test-Path -LiteralPath $configFile) {
    $backupDirectory = Join-Path $codexDirectory 'backups'
    New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
    $backupFile = Join-Path $backupDirectory ('config-before-gunluk-mcp-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '.toml')
    Copy-Item -LiteralPath $configFile -Destination $backupFile
}
& $codexCommand mcp add gunluk_takip -- $binary @bridgeArguments
if ($LASTEXITCODE -ne 0) { throw 'Codex registration failed; the existing configuration backup was retained.' }
Write-Output 'Gunluk Takip MCP registered. Start a new Codex session to load its tools.'
