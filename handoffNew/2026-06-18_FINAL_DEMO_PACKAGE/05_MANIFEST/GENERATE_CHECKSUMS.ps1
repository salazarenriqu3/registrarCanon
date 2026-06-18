$package = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$output = Join-Path $PSScriptRoot "SHA256SUMS.txt"
Get-ChildItem $package -Recurse -File |
    Where-Object { $_.FullName -ne $output } |
    Sort-Object FullName |
    ForEach-Object {
        $relative = $_.FullName.Substring($package.Length + 1).Replace('\', '/')
        $hash = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        "$hash  $relative"
    } | Set-Content $output -Encoding ASCII
Write-Host "Wrote $output"
