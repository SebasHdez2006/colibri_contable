@echo off
rem Colibri.bat — lanzador de un clic para Colibrí.
rem Doble clic en este archivo: compila solo si hace falta, verifica la base
rem de datos y abre la aplicación sin consola. La primera ejecución puede
rem tardar mientras descarga dependencias y compila.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Iniciar-Colibri.ps1" %*
