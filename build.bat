@echo off
setlocal EnableExtensions

set "PROJECT_DIR=%~sdp0"
pushd "%PROJECT_DIR%"
if errorlevel 1 (
    echo Could not access the project directory.
    exit /b 1
)

if /i "%~1"=="clean" (
    if not "%~2"=="" goto :usage
    call :clean_generated_files
    if errorlevel 1 (
        popd
        exit /b 1
    )
    echo Removed generated build files.
    popd
    exit /b 0
)
if not "%~1"=="" goto :usage

rem Use JAVA_HOME, a standard Java 6 install folder, or tools already on PATH.
set "JDK_HOME="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "JDK_HOME=%JAVA_HOME%"
if not defined JDK_HOME (
    for /d %%D in ("%ProgramFiles%\Java\jdk1.6*") do if not defined JDK_HOME if exist "%%~D\bin\javac.exe" set "JDK_HOME=%%~D"
)
if defined JDK_HOME (
    set "JAVAC=%JDK_HOME%\bin\javac.exe"
    set "JAR=%JDK_HOME%\bin\jar.exe"
) else (
    set "JAVAC=javac"
    set "JAR=jar"
)

"%JAVAC%" -version >nul 2>&1
if errorlevel 1 (
    echo Could not find javac. Install JDK 6 and set JAVA_HOME or add its bin directory to PATH.
    goto :failed
)
if defined JDK_HOME if not exist "%JAR%" (
    echo Could not find "%JAR%".
    goto :failed
)

rem WTK_HOME may point to any WTK 2.5.2 installation directory.
if defined WTK_HOME if exist "%WTK_HOME%\bin\preverify.exe" goto :wtk_found
set "WTK_HOME="
for /d %%D in (C:\WTK* "%ProgramFiles%\WTK*" "%ProgramFiles%\Sun\WTK*" "%ProgramFiles%\Java ME Platform SDK*") do (
    if not defined WTK_HOME if exist "%%~D\bin\preverify.exe" set "WTK_HOME=%%~D"
)
if not defined WTK_HOME (
    echo Could not find WTK. Set WTK_HOME to the WTK 2.5.2 installation folder.
    goto :failed
)

:wtk_found
set "PREVERIFY=%WTK_HOME%\bin\preverify.exe"
if not exist "%PREVERIFY%" (
    echo Could not find "%PREVERIFY%".
    goto :failed
)

set "MIDP_CP=%WTK_HOME%\lib\cldcapi11.jar;%WTK_HOME%\lib\midpapi20.jar;%WTK_HOME%\lib\mmapi.jar;%WTK_HOME%\lib\wma20.jar;%WTK_HOME%\lib\jsr75.jar"
for %%F in (cldcapi11.jar midpapi20.jar mmapi.jar wma20.jar jsr75.jar) do (
    if not exist "%WTK_HOME%\lib\%%F" (
        echo Required Java ME library not found: "%WTK_HOME%\lib\%%F"
        goto :failed
    )
)

echo === Cleaning build directories ===
if exist tmpclasses rmdir /s /q tmpclasses
if exist classes rmdir /s /q classes
if not exist tmpclasses mkdir tmpclasses
if not exist classes mkdir classes
if not exist bin mkdir bin

echo === Finding Java sources ===
if exist sources.txt del sources.txt
dir /s /b /a-d src\*.java | findstr /v "\._" > sources.txt
if exist to_unix_paths.vbs cscript //nologo to_unix_paths.vbs sources.txt
if not exist sources.txt (
    echo No Java source files found under src.
    goto :failed
)

echo === Compiling Java sources ===
"%JAVAC%" -encoding UTF-8 -source 1.3 -target 1.3 -bootclasspath "%MIDP_CP%" -d tmpclasses @sources.txt
if errorlevel 1 (
    echo Compilation failed. This build requires JDK 6 with Java 1.3 source support.
    goto :failed
)
del sources.txt

echo === Preverifying classes for CLDC 1.1 ===
"%PREVERIFY%" -classpath "%MIDP_CP%;tmpclasses" -d classes tmpclasses
if errorlevel 1 (
    echo Preverification failed.
    goto :failed
)

echo === Creating MIDlet manifest ===
(
    echo MIDlet-1: WhatsApp, /WhatsApp.png, com.whatsapp.WhatsAppMIDlet
    echo MIDlet-Icon: /WhatsApp.png
    echo MIDlet-Name: WhatsApp
    echo MIDlet-Vendor: lacorte.dev
    echo MIDlet-Version: 2.26.50
    echo MicroEdition-Configuration: CLDC-1.1
    echo MicroEdition-Profile: MIDP-2.0
    echo.
) > bin\MANIFEST.MF

echo === Packaging JAR ===
if exist bin\WhatsApp.jar del bin\WhatsApp.jar
"%JAR%" cvfm bin\WhatsApp.jar bin\MANIFEST.MF -C classes .
if errorlevel 1 (
    echo JAR packaging failed.
    goto :failed
)
if exist res "%JAR%" uvf bin\WhatsApp.jar -C res .
if errorlevel 1 (
    echo Adding resources to the JAR failed.
    goto :failed
)

for %%A in (bin\WhatsApp.jar) do set "JAR_SIZE=%%~zA"
(
    echo MIDlet-1: WhatsApp, /WhatsApp.png, com.whatsapp.WhatsAppMIDlet
    echo MIDlet-Icon: /WhatsApp.png
    echo MIDlet-Jar-Size: %JAR_SIZE%
    echo MIDlet-Jar-URL: WhatsApp.jar
    echo MIDlet-Name: WhatsApp
    echo MIDlet-Vendor: lacorte.dev
    echo MIDlet-Version: 2.26.50
    echo MicroEdition-Configuration: CLDC-1.1
    echo MicroEdition-Profile: MIDP-2.0
) > bin\WhatsApp.jad

call :clean_intermediates
if errorlevel 1 goto :failed
echo ==============================================
echo Built bin\WhatsApp.jar (%JAR_SIZE% bytes) successfully!
echo ==============================================
popd
exit /b 0

:failed
call :clean_intermediates
popd
exit /b 1

:clean_generated_files
call :clean_intermediates
if errorlevel 1 exit /b 1
if exist bin\WhatsApp.jar del /q bin\WhatsApp.jar
if exist bin\WhatsApp.jar (
    echo Could not remove bin\WhatsApp.jar. 1>&2
    exit /b 1
)
if exist bin\WhatsApp.jad del /q bin\WhatsApp.jad
if exist bin\WhatsApp.jad (
    echo Could not remove bin\WhatsApp.jad. 1>&2
    exit /b 1
)
if exist bin\MANIFEST.MF del /q bin\MANIFEST.MF
if exist bin\MANIFEST.MF (
    echo Could not remove bin\MANIFEST.MF. 1>&2
    exit /b 1
)
exit /b 0

:clean_intermediates
if exist tmpclasses rmdir /s /q tmpclasses
if exist tmpclasses (
    echo Could not remove tmpclasses. 1>&2
    exit /b 1
)
if exist classes rmdir /s /q classes
if exist classes (
    echo Could not remove classes. 1>&2
    exit /b 1
)
if exist sources.txt del /q sources.txt
if exist sources.txt (
    echo Could not remove sources.txt. 1>&2
    exit /b 1
)
exit /b 0

:usage
echo Usage: %~nx0 [clean]
popd
exit /b 1
