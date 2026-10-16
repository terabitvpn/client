param([Parameter(Mandatory)][string]$Version, [string]$Dir = ".")
$files = [ordered]@{}
Get-ChildItem $Dir -File |
  Where-Object { $_.Name -ne "latest.json" -and $_.Extension -ne ".txt" } |
  ForEach-Object {
    $files[$_.Name] = [ordered]@{
      size   = $_.Length
      sha256 = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower()
    }
  }
$json = [ordered]@{ version = $Version; files = $files } | ConvertTo-Json -Depth 5
[IO.File]::WriteAllText((Join-Path $Dir "latest.json"), $json, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Готово: $(Join-Path $Dir 'latest.json')"
