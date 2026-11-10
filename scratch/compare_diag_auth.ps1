$d28 = Get-Content diagnose_2528.json -Raw | ConvertFrom-Json
$d08 = Get-Content diagnose_4508.json -Raw | ConvertFrom-Json

[xml]$x28 = $d28.AuthSettings
[xml]$x08 = $d08.AuthSettings

$s28 = $x28.DeviceInformationModel.GetValue.Authentication.AuthenticationSettings
$s08 = $x08.DeviceInformationModel.GetValue.Authentication.AuthenticationSettings

Write-Host "--- Comparison of AuthSettings ---"
$props = @(
    'openAccess',
    'functionwiseAuthentication',
    'requireTrustedUser',
    'allowGuestUser',
    'unauthorizedPrintJob',
    'AuthenticationType/authenticationName',
    'AuthenticationType/autoRegistration'
)

foreach ($p in $props) {
    $v28 = [string]($x28.SelectSingleNode("//$p").'#text')
    $v08 = [string]($x08.SelectSingleNode("//$p").'#text')
    Write-Host "$p -> 2528: '$v28' | 4508: '$v08'"
}
