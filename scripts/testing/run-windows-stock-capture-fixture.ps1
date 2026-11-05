# A synthetic local stock window for manual recording acceptance. No real account/data.
param([switch]$Verify)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Add-Type -AssemblyName System.Drawing,System.Windows.Forms,System.Web.Extensions,WindowsBase,UIAutomationClient,UIAutomationTypes
$references = @('System.dll','System.Core.dll',
    [System.Drawing.Bitmap].Assembly.Location, [System.Windows.Forms.Form].Assembly.Location,
    [System.Web.Script.Serialization.JavaScriptSerializer].Assembly.Location,
    [System.Windows.Rect].Assembly.Location, [System.Windows.Automation.AutomationElement].Assembly.Location,
    [System.Windows.Automation.ControlType].Assembly.Location) | Select-Object -Unique
Add-Type -Path @(
    (Join-Path $repo 'composeApp\src\jvmMain\resources\stock-capture\stock-capture-helper.cs'),
    (Join-Path $PSScriptRoot 'windows-stock-capture-fixture.cs')
) -ReferencedAssemblies $references
$result = [Aita.StockCapture.SyntheticFixture]::Run($Verify.IsPresent)
exit $result
