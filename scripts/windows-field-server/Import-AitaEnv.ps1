Set-StrictMode -Version Latest

function Import-AitaEnv {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [switch]$PassThru
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "AITA environment file was not found: $Path"
    }

    $resolvedPath = (Resolve-Path -LiteralPath $Path).Path
    $seenNames = @{}
    $loadedValues = [ordered]@{}
    $lineNumber = 0

    foreach ($rawLine in Get-Content -LiteralPath $resolvedPath -Encoding UTF8) {
        $lineNumber += 1
        $line = $rawLine.Trim()

        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) {
            continue
        }

        if ($line.StartsWith('export ', [System.StringComparison]::OrdinalIgnoreCase)) {
            $line = $line.Substring(7).TrimStart()
        }

        $equalsIndex = $line.IndexOf('=')
        if ($equalsIndex -lt 1) {
            throw "Invalid environment entry at ${resolvedPath}:$lineNumber. Expected NAME=value."
        }

        $name = $line.Substring(0, $equalsIndex).Trim()
        $value = $line.Substring($equalsIndex + 1).Trim()

        if ($name -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
            throw "Invalid environment-variable name '$name' at ${resolvedPath}:$lineNumber."
        }

        if ($seenNames.ContainsKey($name)) {
            throw "Duplicate environment variable '$name' at ${resolvedPath}:$lineNumber."
        }
        $seenNames[$name] = $true

        if ($value.Length -gt 0 -and ($value[0] -eq '"' -or $value[0] -eq "'")) {
            $quote = [string]$value[0]
            if ($value.Length -lt 2 -or [string]$value[$value.Length - 1] -ne $quote) {
                throw "Unterminated quoted value for '$name' at ${resolvedPath}:$lineNumber."
            }
            $value = $value.Substring(1, $value.Length - 2)
        }

        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        $loadedValues[$name] = $value
    }

    if ($PassThru) {
        return $loadedValues
    }
}
