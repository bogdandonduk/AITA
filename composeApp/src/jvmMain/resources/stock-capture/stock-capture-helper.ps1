param(
    [ValidateSet('List','Record','Verify')][string]$Mode = 'List',
    [ValidatePattern('^[0-9]{1,20}$')][string]$WindowId = '0',
    [ValidatePattern('^[a-zA-Z-]{2,16}$')][string]$Language = 'en',
    [int]$OwnerPid = 0,
    [int]$ExpectedPid = 0
)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
[Console]::InputEncoding = New-Object System.Text.UTF8Encoding($false)
try {
    Add-Type -AssemblyName System.Drawing,System.Windows.Forms,System.Web.Extensions,WindowsBase,UIAutomationClient,UIAutomationTypes
    $references = @(
        'System.dll','System.Core.dll',
        [System.Drawing.Bitmap].Assembly.Location,
        [System.Windows.Forms.Form].Assembly.Location,
        [System.Web.Script.Serialization.JavaScriptSerializer].Assembly.Location,
        [System.Windows.Rect].Assembly.Location,
        [System.Windows.Automation.AutomationElement].Assembly.Location,
        [System.Windows.Automation.ControlType].Assembly.Location
    ) | Select-Object -Unique
    Add-Type -Path (Join-Path $PSScriptRoot 'stock-capture-helper.cs') -ReferencedAssemblies $references -ErrorAction Stop
    [Aita.StockCapture.Helper]::Run($Mode, $WindowId, $Language, $OwnerPid, $ExpectedPid)
} catch {
    # Never disclose provider errors: they can contain captured text or private window titles.
    [Console]::WriteLine('{"type":"state","status":"ERROR","code":"helper_unavailable"}')
    exit 1
}
