$action = New-ScheduledTaskAction -Execute "C:\Scripts\restart-jenkins-agent.bat"

$trigger = New-ScheduledTaskTrigger -Daily -At 6:00am

$principal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -RunLevel Highest

Register-ScheduledTask `
-TaskName "Reinicio Jenkins Agent" `
-Action $action `
-Trigger $trigger `
-Principal $principal


//verificar Get-ScheduledTask -TaskName "Reinicio Jenkins Agent"

// ejecutar manual Start-ScheduledTask -TaskName "Reinicio Jenkins Agent"

// eliminar para recrear Unregister-ScheduledTask -TaskName "Reinicio Jenkins Agent" -Confirm:$false