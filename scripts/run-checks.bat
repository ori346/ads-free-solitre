@echo off
setlocal

for %%I in ("%~dp0..") do set "ROOT_DIR=%%~fI"

:: Parse optional arguments:
::   (default) -> Run unit tests, Android lint, and release APK build
::   --clean   -> Run clean before running checks
::   --help    -> Show usage information
set "DO_CLEAN=0"

:parse_args
if "%~1"=="" goto done_args
if /I "%~1"=="--clean" ( set "DO_CLEAN=1" & shift & goto parse_args )
if /I "%~1"=="-clean"  ( set "DO_CLEAN=1" & shift & goto parse_args )
if /I "%~1"=="--help"  goto show_help
if /I "%~1"=="-h"      goto show_help
if /I "%~1"=="/?"      goto show_help
echo Unknown option: %~1
echo Usage: scripts\run-checks.bat [--clean]
exit /b 1

:show_help
echo Usage: scripts\run-checks.bat [--clean]
echo.
echo Options:
echo   (default)    Run spotlessCheck, testDebugUnitTest, lintDebug, and assembleRelease
echo   --clean      Run clean before executing checks
exit /b 0

:done_args

if not defined JAVA_HOME (
    if exist "%USERPROFILE%\android-tools\jdk17\bin\java.exe" (
        set "JAVA_HOME=%USERPROFILE%\android-tools\jdk17"
    )
)

set "GRADLE_TASKS="
if "%DO_CLEAN%"=="1" set "GRADLE_TASKS=clean "

echo Running Gradle checks ^(%GRADLE_TASKS%spotlessCheck testDebugUnitTest lintDebug assembleRelease^)...
pushd "%ROOT_DIR%" >nul
call "%ROOT_DIR%\gradlew.bat" %GRADLE_TASKS%spotlessCheck testDebugUnitTest lintDebug assembleRelease
set "CHECK_EXIT=%ERRORLEVEL%"
popd >nul

if not "%CHECK_EXIT%"=="0" (
    echo.
    echo Checks failed!
    exit /b 1
)

set "BUILT_APK=%ROOT_DIR%\app\build\outputs\apk\release\app-release.apk"
set "OUT_APK=%ROOT_DIR%\AdFree-Solitaire.apk"

if exist "%BUILT_APK%" (
    copy /Y "%BUILT_APK%" "%OUT_APK%" >nul
    if errorlevel 1 (
        echo.
        echo Error: Failed to copy "%BUILT_APK%" to "%OUT_APK%"
        exit /b 1
    )
    echo.
    echo All checks passed! Release APK synced to:
    echo   %OUT_APK%
) else (
    echo.
    echo Error: Expected Release APK not found at "%BUILT_APK%"
    exit /b 1
)

exit /b 0
