@echo off
setlocal EnableDelayedExpansion

rem Firma: Lapis-xy
rem Agente: avvia Listener da %APPDATA%\DisplayCatch\Listener

rem Cartella dove risiedono i .class
set "DEST=%APPDATA%\DisplayCatch\Listener"
if not exist "%DEST%" (
    echo Cartella %DEST% non esiste. Assicurati che i .class siano stati installati.
    pause
    endlocal
    exit /b 1
)

rem Trova javaw o java
set "JAVACMD="
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\javaw.exe" set "JAVACMD=%JAVA_HOME%\bin\javaw.exe"
    if not defined JAVACMD if exist "%JAVA_HOME%\bin\java.exe" set "JAVACMD=%JAVA_HOME%\bin\java.exe"
)
if not defined JAVACMD (
    for %%x in (javaw.exe java.exe) do (
        for /f "delims=" %%p in ('where %%x 2^>nul') do if not defined JAVACMD set "JAVACMD=%%p"
    )
)

if not defined JAVACMD (
    echo ERRORE: java/javaw non trovato. Installa Java o imposta JAVA_HOME.
    pause
    endlocal
    exit /b 1
)

rem Avvia la classe Listener dalla cartella DEST
echo Avvio Listener da %DEST%...
start "" "%JAVACMD%" -cp "%DEST%" Listener
echo Listener avviato.

endlocal
exit /b 0

