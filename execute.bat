@echo off
setlocal enabledelayedexpansion

set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot"
set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"

set "MAVEN_HOME=C:\Program Files\apache-maven-3.9.6"
set "PATH=%MAVEN_HOME%\bin;%PATH%"

set "APP_DIR=%~dp0"
cd "%APP_DIR%"

if not exist "target\aliexpress-price-updater-1.0-SNAPSHOT-jar-with-dependencies.jar" (
    echo Building application...
    call "%MAVEN_HOME%\bin\mvn" clean package
    if !ERRORLEVEL! neq 0 (
        echo Failed to build application
        pause
        exit /b 1
    )
)

if not exist "logs" mkdir logs

echo Starting application...
"%JAVA_EXE%" -jar target\aliexpress-price-updater-1.0-SNAPSHOT-jar-with-dependencies.jar

if !ERRORLEVEL! neq 0 (
    echo Application exited with error code !ERRORLEVEL!
    pause
)

endlocal
