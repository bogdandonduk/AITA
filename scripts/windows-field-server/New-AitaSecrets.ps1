[CmdletBinding()]
param([int]$Bytes=64)
Set-StrictMode -Version Latest
$rng=[System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $buffer=New-Object byte[] $Bytes
    $rng.GetBytes($buffer)
    ([Convert]::ToHexString($buffer)).ToLowerInvariant()
} finally { $rng.Dispose() }
