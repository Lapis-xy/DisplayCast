@echo off
setlocal EnableDelayedExpansion

rem Firma: Lapis-xy

rem Host: compila e installa Sender in %APPDATA%\DisplayCatch\sender
rem Cartella sorgente (dove si trovano i .java)
set "SRC_FOLDER=%~dp0"
rem Rimuovi trailing backslash
if "%SRC_FOLDER:~-1%"=="\" set "SRC_FOLDER=%SRC_FOLDER:~0,-1%"

rem Cartella di destinazione
set "DEST_FOLDER=%APPDATA%\DisplayCatch\sender"

rem --- Trova javaw.exe e javac.exe ---
set "JAVAW="
set "JAVAC="

if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\javaw.exe" set "JAVAW=%JAVA_HOME%\bin\javaw.exe"
    if exist "%JAVA_HOME%\bin\javac.exe" set "JAVAC=%JAVA_HOME%\bin\javac.exe"
)
if not defined JAVAW (
    for %%i in (javaw.exe) do if not "%%~$PATH:i"=="" set "JAVAW=%%~$PATH:i"
)
if not defined JAVAC (
    for %%i in (javac.exe) do if not "%%~$PATH:i"=="" set "JAVAC=%%~$PATH:i"
)
if not defined JAVAW (
    echo ERRORE: javaw.exe non trovato. Installa Java o imposta JAVA_HOME.
    pause
    exit /b 1
)
if not defined JAVAC (
    echo ERRORE: javac.exe non trovato. Installa il JDK o imposta JAVA_HOME.
    pause
    exit /b 1
)

rem --- Crea cartella destinazione ---
if not exist "%DEST_FOLDER%" mkdir "%DEST_FOLDER%"

rem --- Flag: se esiste il file %APPDATA%\DisplayCatch\norebuild.flag salta la compilazione
set "APPBRODCAST=%APPDATA%\DisplayCatch"
set "SKIP_COMPILE=0"
if exist "%APPBRODCAST%\norebuild.flag" set "SKIP_COMPILE=1"
rem --- supporto anche a parametro della riga di comando per compatibilita' (norebuild/skip)
if /I "%~1"=="norebuild" set "SKIP_COMPILE=1"
if /I "%~1"=="/norebuild" set "SKIP_COMPILE=1"
if /I "%~1"=="skip" set "SKIP_COMPILE=1"

pushd "%SRC_FOLDER%"

if "%SKIP_COMPILE%"=="1" (
    echo Salto la compilazione perche' richiesto dal flag.
    echo Verifico la presenza dei .class in %DEST_FOLDER%...
    if not exist "%DEST_FOLDER%\Sender.class" (
        echo Attenzione: Sender.class non trovato in %DEST_FOLDER%. Potrebbe non essere possibile avviare il Sender.
    )
) else (
    rem --- Compilazione ---
    echo Compilazione Java in corso...
    "%JAVAC%" -encoding UTF-8 -g -d . Server.java Sender.java 2>javac_errors.txt
    if errorlevel 1 (
        type javac_errors.txt
        echo ERRORE: compilazione fallita.
        del /f /q javac_errors.txt 2>nul
        popd
        pause
        exit /b 1
    )
    del /f /q javac_errors.txt 2>nul

    rem --- Sposta i .class nella cartella destinazione ---
    echo Spostamento file compilati in %DEST_FOLDER%...
    for %%f in (Server*.class Sender*.class) do (
        if exist "%%f" move /y "%%f" "%DEST_FOLDER%\" >nul 2>&1
    )

    rem --- Pulisci eventuali .class rimasti nella cartella sorgente ---
    del /f /q Server*.class Sender*.class 2>nul
    rem --- Rimuovi i sorgenti .java dalla cartella host (richiesta di pulizia)
    echo Rimuovo i file sorgente .java dalla cartella del progetto Host...
    del /f /q "%SRC_FOLDER%\*.java" 2>nul

    rem --- Crea flag per indicare che i .class sono stati installati in %APPDATA% ---
    if not exist "%APPBRODCAST%" mkdir "%APPBRODCAST%"
    (echo installed)>"%APPBRODCAST%\norebuild.flag"
)

popd

rem --- Avvia Sender con javaw (nessuna finestra console, GUI visibile) ---
echo Avvio Sender...
rem --- Copia questo batch nella cartella di destinazione in %APPDATA% ---
if exist "%DEST_FOLDER%" (
    copy /y "%~f0" "%DEST_FOLDER%\Host.bat" >nul 2>&1
)

rem --- Se presente, copia anche l'icona personalizzata (app.ico) nella cartella destinazione ---
if exist "%SRC_FOLDER%\app.ico" (
    copy /y "%SRC_FOLDER%\app.ico" "%DEST_FOLDER%\app.ico" >nul 2>&1
)

rem --- Crea un collegamento sul Desktop che punta alla copia nel %APPDATA% ---
powershell -NoProfile -ExecutionPolicy Bypass -Command "$s=New-Object -ComObject WScript.Shell; $desk=[Environment]::GetFolderPath('Desktop'); $lnk=$s.CreateShortcut($desk + '\\DisplayCatch.lnk'); $target=Join-Path $env:APPDATA 'DisplayCatch\\sender\\Host.bat'; $lnk.TargetPath=$target; $lnk.WorkingDirectory=Join-Path $env:APPDATA 'DisplayCatch\\sender'; $ico=Join-Path $env:APPDATA 'DisplayCatch\\sender\\app.ico'; if (Test-Path $ico) { $lnk.IconLocation = $ico + ',0' } else { $lnk.IconLocation = $ico }; $lnk.Save();"

rem --- Avvia Sender con javaw (nessuna finestra console, GUI visibile) ---
start "" "%JAVAW%" -cp "%DEST_FOLDER%" Sender

endlocal
exit /b 0
