param([Parameter(ValueFromRemainingArguments=$true)][string[]]$Arguments)
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '../..')
if (Get-Command py -ErrorAction SilentlyContinue) { & py -3 scripts/guided-tests/run.py @Arguments }
elseif (Get-Command python -ErrorAction SilentlyContinue) { & python scripts/guided-tests/run.py @Arguments }
else { throw 'Install Python 3, then rerun this command.' }
exit $LASTEXITCODE
