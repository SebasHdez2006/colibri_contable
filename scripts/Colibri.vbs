' Colibri.vbs — lanzador silencioso de Colibrí.
' Ejecuta Iniciar-Colibri.ps1 sin mostrar ninguna ventana de consola.
' Los errores fatales aparecen en un cuadro de diálogo y quedan en
' %LOCALAPPDATA%\Colibri\logs\colibri-AAAAMMDD.log

Set shell = CreateObject("Wscript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
scriptDir = fso.GetParentFolderName(WScript.ScriptFullName)
cmd = "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden " & _
      "-File """ & scriptDir & "\Iniciar-Colibri.ps1"" -Silencioso"
shell.Run cmd, 0, False
