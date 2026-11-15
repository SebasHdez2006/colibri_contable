@echo off
rem Colibri.cmd — lanzador con consola visible (útil para ver el progreso
rem de compilación). La app se abre sin consola una vez compilada.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Iniciar-Colibri.ps1" %*
