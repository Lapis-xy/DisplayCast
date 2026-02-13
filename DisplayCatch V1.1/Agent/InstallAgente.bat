 @echo off
setlocal EnableDelayedExpansion

rem Firma: Lapis-xy
rem InstallAgente: compila e installa Listener in %APPDATA%\DisplayCatch\Listener

rem Directory script (Agent)
set "AGENT_DIR=%~dp0"
if "%AGENT_DIR:~-1%"=="\" set "AGENT_DIR=%AGENT_DIR:~0,-1%"

set "DEST=%APPDATA%\DisplayCatch\Listener"

pushd "%AGENT_DIR%"
echo === InstallAgente: lavoro nella cartella: %AGENT_DIR% ===

rem Verifica presenza di sorgenti .java
dir /b *.java >nul 2>&1
if errorlevel 1 (
    echo Nessun file .java trovato nella cartella Agent. Esco.
    popd
    endlocal
    exit /b 0
)

rem Trova javac
set "JAVAC_CMD=javac"
where "%JAVAC_CMD%" >nul 2>&1
if errorlevel 1 (
    if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "JAVAC_CMD=%JAVA_HOME%\bin\javac.exe"
)
where "%JAVAC_CMD%" >nul 2>&1
if errorlevel 1 (
    echo ERRORE: "javac" non trovato. Installa il JDK o imposta JAVA_HOME.
    popd
    pause
    endlocal
    exit /b 1
)

rem Compila i sorgenti nella cartella Agent
echo Compilazione dei .java in %AGENT_DIR% ...
"%JAVAC_CMD%" -encoding UTF-8 -g -d . *.java 2>javac_errors.txt
if errorlevel 1 (
    echo ERRORE: compilazione fallita. Vedi gli errori:
    type javac_errors.txt
    del /f /q javac_errors.txt 2>nul
    popd
    pause
    endlocal
    exit /b 1
)
del /f /q javac_errors.txt 2>nul
echo Compilazione completata con successo.

rem Crea cartella destinazione e sposta i .class
if not exist "%DEST%" mkdir "%DEST%"
echo Sposto i .class in %DEST%...
for %%F in (*.class) do (
    move /Y "%%F" "%DEST%\" >nul 2>&1
)
echo .class spostati in %DEST%.

rem --- Elimina i file .java nella cartella Agent (richiesta utente)
echo Eliminazione dei file .java nella cartella %AGENT_DIR%...
if exist "%AGENT_DIR%\*.java" (
    for %%J in ("%AGENT_DIR%\*.java") do (
        echo Eliminazione: %%~fJ
        del /f /q "%%~fJ" >nul 2>&1
        if errorlevel 1 echo Attenzione: impossibile eliminare %%~fJ
    )
    echo Eliminazione .java completata.
) else (
    echo Nessun file .java trovato in %AGENT_DIR%.
)

rem Sposta Agente.bat nella cartella Startup
set "STARTUP=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
if not exist "%STARTUP%" mkdir "%STARTUP%"
echo Sposto Agente.bat in %STARTUP% per l'avvio automatico...
move /Y "%AGENT_DIR%\Agente.bat" "%STARTUP%\" >nul 2>&1
if errorlevel 1 (
    echo ERRORE: impossibile spostare Agente.bat in Startup.
    popd
    pause
    endlocal
    exit /b 1
)
echo Agente.bat spostato in Startup.

rem Avvia la copia presente in Startup in una nuova shell separata (non bloccante)
echo Avvio Agente da Startup (non bloccante)...
start "" cmd /c "%STARTUP%\Agente.bat"

popd
endlocal
exit /b 0