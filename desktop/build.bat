@echo off
REM Build script for FileCrypt desktop app
REM Requires Java 17+

cd /d "%~dp0"

where jar >nul 2>&1
if errorlevel 1 (
    for /d %%D in ("C:\Program Files\Java\jdk*") do (
        if exist "%%D\bin\jar.exe" set "PATH=%%D\bin;%PATH%"
    )
)
where jar >nul 2>&1
if errorlevel 1 (
    if exist "C:\Program Files\Java\latest\bin\jar.exe" (
        set "PATH=C:\Program Files\Java\latest\bin;%PATH%"
    )
)

echo Compiling Java sources...
if not exist build mkdir build
javac -d build -source 17 -target 17 src\com\filecrypt\*.java
if errorlevel 1 (
    echo Compilation failed.
    exit /b 1
)

echo Creating JAR...
if not exist dist mkdir dist
jar cfe dist\FileCrypt.jar com.filecrypt.Main -C build .
if errorlevel 1 (
    echo JAR creation failed.
    exit /b 1
)

echo Build complete: dist\FileCrypt.jar

REM Compute SHA-256 checksum
certutil -hashfile dist\FileCrypt.jar SHA256