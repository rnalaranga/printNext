$ip = "192.168.1.10"
# Query SNMP standard OIDs using SNMP if Windows SNMP feature is installed, or using .NET UDP socket
# hrDeviceStatus: 1.3.6.1.2.1.25.3.2.1.5.1
# Let's see if snmpwalk or Get-CimInstance or similar exists
$cmd = Get-Command *snmp* -ErrorAction SilentlyContinue
Write-Output "SNMP commands: $($cmd.Name)"
