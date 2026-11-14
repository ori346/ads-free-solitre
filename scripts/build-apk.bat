@echo off
setlocal

for %%I in ("%~dp0..") do set "ROOT_DIR=%%~fI"

:: Parse optional arguments:
::   (default) or --release  -> Build Release APK and copy to AdFree-Solitaire.apk
::   --debug                 -> Build Debug APK and copy to AdFree-Solitaire-debug.apk
::   --clean                 -> Run clean before building
set "BUILD_TYPE=release"
set "DO_CLEAN=0"

:parse_args
if "%~1"=="" goto done_args
if /I "%~1"=="--debug"   ( set "BUILD_TYPE=debug"   & shift & goto parse_args )
if /I "%~1"=="-debug"    ( set "BUILD_TYPE=debug"   & shift & goto parse_args )
if /I "%~1"=="--release" ( set "BUILD_TYPE=release" & shift & goto parse_args )
if /I "%~1"=="-release"  ( set "BUILD_TYPE=release" & shift & goto parse_args )
if /I "%~1"=="--clean"   ( set "DO_CLEAN=1"         & shift & goto parse_args )
if /I "%~1"=="-clean"    ( set "DO_CLEAN=1"         & shift & goto parse_args )
shift
goto parse_args

:done_args

if not defined JAVA_HOME (
    if exist "%USERPROFILE%\android-tools\jdk17\bin\java.exe" (
        set "JAVA_HOME=%USERPROFILE%\android-tools\jdk17"
    )
)

set "GRADLE_TASKS="
if "%DO_CLEAN%"=="1" set "GRADLE_TASKS=clean "

if /I "%BUILD_TYPE%"=="debug" (
    set "BUILD_LABEL=Debug"
    set "GRADLE_TASK=assembleDebug"
    set "BUILT_APK=%ROOT_DIR%\app\build\outputs\apk\debug\app-debug.apk"
    set "OUT_APK=%ROOT_DIR%\AdFree-Solitaire-debug.apk"
) else (
    set "BUILD_LABEL=Release"
    set "GRADLE_TASK=assembleRelease"
    set "BUILT_APK=%ROOT_DIR%\app\build\outputs\apk\release\app-release.apk"
    set "OUT_APK=%ROOT_DIR%\AdFree-Solitaire.apk"
)

echo Building %BUILD_LABEL% APK...
pushd "%ROOT_DIR%" >nul
call "%ROOT_DIR%\gradlew.bat" %GRADLE_TASKS%%GRADLE_TASK%
set "BUILD_EXIT=%ERRORLEVEL%"
popd >nul

if not "%BUILD_EXIT%"=="0" (
    echo.
    echo Build failed!
    exit /b 1
)

if exist "%BUILT_APK%" (
    copy /Y "%BUILT_APK%" "%OUT_APK%" >nul
    echo.
    echo Build succeeded! APK saved to:
    echo   %OUT_APK%
) else (
    echo.
    echo Error: Expected APK not found at "%BUILT_APK%"
    exit /b 1
)

exit /b 0
