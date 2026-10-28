param(
    [Parameter(Mandatory)][string]$Serial,
    [Parameter(Mandatory)][string]$Label,
    [string]$Package = 'com.uykutakip.app'
)
$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$local = Join-Path $env:TEMP ('daily-tracker-ui-' + [Guid]::NewGuid().ToString('N') + '.xml')
try {
    & $adb -s $Serial shell uiautomator dump /sdcard/daily-tracker-ui.xml | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'UI dump failed' }
    & $adb -s $Serial pull /sdcard/daily-tracker-ui.xml $local 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'UI pull failed' }
    [xml]$tree = Get-Content -LiteralPath $local -Raw
    $matches = @($tree.SelectNodes('//node') | Where-Object {
        $_.package -eq $Package -and ($_.text -eq $Label -or $_.'content-desc' -eq $Label)
    })
    if ($matches.Count -ne 1) { throw "Expected one control '$Label', found $($matches.Count); no tap performed" }
    $node = $matches[0]
    while ($node.clickable -ne 'true' -and $node.ParentNode.Name -eq 'node') { $node = $node.ParentNode }
    if ($node.clickable -ne 'true' -or $node.enabled -ne 'true') { throw 'Control is not enabled and clickable' }
    if ($node.bounds -notmatch '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$') { throw 'Invalid bounds' }
    $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
    $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
    & $adb -s $Serial shell input tap $x $y
    if ($LASTEXITCODE -ne 0) { throw 'Tap failed' }
    Write-Output "Tapped: $Label"
} finally {
    if (Test-Path -LiteralPath $local) { Remove-Item -LiteralPath $local }
}
