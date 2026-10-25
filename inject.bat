@echo off
setlocal EnableDelayedExpansion
title utilitymod injector
cd /d "%~dp0"
set "SYS=%SystemRoot%\System32"

echo.
echo   ==========================================
echo      utilitymod injector
echo   ==========================================
echo      Run THIS .bat - never double-click the .jar.
echo      (double-clicking the jar opens the Java
echo       installer and throws a "switches" error)
echo   ==========================================
echo.

rem ---------------------------------------------------------------------------
rem 1) find the mod jar next to this script (newest utilitymod*.jar, not -sources)
rem ---------------------------------------------------------------------------
set "JAR="
for /f "delims=" %%F in ('dir /b /o-d "utilitymod*.jar" 2^>nul ^| "%SYS%\findstr.exe" /v /i "sources"') do (
    if not defined JAR set "JAR=%~dp0%%F"
)
if not defined JAR (
    echo   [X] No utilitymod .jar found next to this script.
    echo       Put inject.bat in the same folder as the utilitymod jar and run it again.
    goto :fail
)
echo   [OK] Mod: !JAR!

rem ---------------------------------------------------------------------------
rem 2) find a JDK 21 (needs the attach module, a plain JRE will not work)
rem ---------------------------------------------------------------------------
call :findjdk
if not defined JAVA (
    echo.
    echo   [!] Java 21 JDK not found. It is needed to inject the mod.
    echo.
    "%SYS%\choice.exe" /c YN /m "      Install it now (Oracle JDK 21, official, about 170 MB)"
    if errorlevel 2 goto :nojava
    where winget >nul 2>nul
    if errorlevel 1 goto :nowinget
    echo.
    echo   Installing Java 21... accept any Windows prompt that pops up.
    winget install --id Oracle.JDK.21 -e --accept-package-agreements --accept-source-agreements
    call :findjdk
    if not defined JAVA (
        echo   [X] Java was installed but could not be found. Restart your PC and run this again.
        goto :fail
    )
)
echo   [OK] Java: !JAVA!

rem ---------------------------------------------------------------------------
rem 3) wait for Minecraft
rem ---------------------------------------------------------------------------
echo.
echo   Start Minecraft 1.21.11 (Fabric) and get to the title screen or a world.
echo   Waiting for Minecraft...
:waitmc
rem the game window title starts with "Minecraft" (other java programs don't count)
"%SYS%\tasklist.exe" /fi "windowtitle eq Minecraft*" 2>nul | "%SYS%\find.exe" /i "java" >nul
if not errorlevel 1 goto :mcfound
"%SYS%\timeout.exe" /t 2 /nobreak >nul
goto :waitmc

:mcfound
echo   [OK] Minecraft is running. Giving it a few seconds to finish loading...
"%SYS%\timeout.exe" /t 8 /nobreak >nul

rem ---------------------------------------------------------------------------
rem 4) inject
rem ---------------------------------------------------------------------------
echo.
echo   Injecting...
"!JAVA!" -jar "!JAR!"
if errorlevel 3 (
    echo.
    echo   [X] Your launcher runs Minecraft on a Java that can't load mods while running.
    echo       Prism: right-click the instance ^> Edit ^> Settings ^> Java ^> tick "Java installation"
    echo              ^> Auto-detect ^> pick the Oracle "jdk-21" one ^> OK.
    echo       Then restart Minecraft and run this script again.
    goto :fail
)
if errorlevel 1 (
    echo.
    echo   [X] Injection failed. Make sure:
    echo       - Minecraft is 1.21.11 with Fabric and is fully loaded
    echo       - the utilitymod jar is NOT also in your mods folder
    echo       - you restarted Minecraft since the last injection
    goto :fail
)
echo.
echo   [OK] Done! In game: press RIGHT SHIFT to open the menu.
echo        After joining a world a self-test appears in chat - all green = everything works.
echo.
echo   Tip: if double-clicking a .jar opens the Java installer on this PC, the .jar
echo        file association is wrong. You can fix it (so jars run with Java 21):
"%SYS%\choice.exe" /c YN /m "        Fix the .jar association now (needs admin)"
if not errorlevel 2 call :fixassoc
echo.
pause
exit /b 0

rem ===========================================================================
:fixassoc
set "JDIR=%JAVA:\bin\java.exe=%"
set "FIXBAT=%TEMP%\utilitymod_jarfix.cmd"
echo   Repointing .jar at "%JDIR%\bin\javaw.exe" (an admin prompt will pop up)...
>"%FIXBAT%" echo @echo off
>>"%FIXBAT%" echo ftype jarfile="%JDIR%\bin\javaw.exe" -jar "%%1" %%*
>>"%FIXBAT%" echo assoc .jar=jarfile
>>"%FIXBAT%" echo echo Done - jars now run with Java 21.
>>"%FIXBAT%" echo pause
"%SYS%\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -Command "Start-Process -Verb RunAs -FilePath '%FIXBAT%'"
if errorlevel 1 (
    echo   [!] Could not elevate (admin prompt declined). Skipped.
    echo       You can still run jars anytime with inject.bat.
) else (
    echo   [OK] Fix launched. Approve the admin prompt; jars will then run with Java 21.
)
exit /b 0

rem ===========================================================================
:findjdk
set "JAVA="
for %%R in ("%ProgramFiles%\Eclipse Adoptium" "%ProgramFiles%\Java" "%ProgramFiles%\Microsoft" "%ProgramFiles%\Zulu" "%ProgramFiles%\BellSoft" "%USERPROFILE%\.jdks") do (
    if exist "%%~R" (
        for /f "delims=" %%D in ('dir /b /ad /o-n "%%~R" 2^>nul ^| "%SYS%\findstr.exe" /i "21"') do (
            if not defined JAVA if exist "%%~R\%%D\bin\attach.dll" if exist "%%~R\%%D\bin\java.exe" set "JAVA=%%~R\%%D\bin\java.exe"
        )
    )
)
if not defined JAVA if defined JAVA_HOME if exist "%JAVA_HOME%\bin\attach.dll" set "JAVA=%JAVA_HOME%\bin\java.exe"
exit /b 0

:nowinget
echo.
echo   [X] winget is not available on this PC. Opening the Java download page...
echo       Download the Windows x64 Installer for JDK 21, install it, then run this script again.
start "" "https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html"
goto :fail

:nojava
echo.
echo   Java 21 JDK is required. Install Oracle JDK 21 from https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html and run this again.
goto :fail

:fail
echo.
pause
exit /b 1
