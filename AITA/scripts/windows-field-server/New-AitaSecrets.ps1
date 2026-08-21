[CmdletBinding()]
param(
    [ValidateRange(32, 1024)]
    [int]$Bytes = 64,

    [ValidateRange(1, 32)]
    [int]$Count = 1
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    for ($secretIndex = 0; $secretIndex -lt $Count; $secretIndex += 1) {
        $buffer = New-Object byte[] $Bytes
        $rng.GetBytes($buffer)

        $builder = New-Object System.Text.StringBuilder ($Bytes * 2)
        foreach ($value in $buffer) {
            [void]$builder.Append($value.ToString('x2'))
        }
        $builder.ToString()
    }
} finally {
    $rng.Dispose()
}
