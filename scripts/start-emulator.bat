@echo off
setlocal

for %%I in ("%~dp0..") do set "ROOT_DIR=%%~fI"

:: Parse command-line arguments:
::   Default (no flag)                        -> Keep current emulator locale (fast Quickboot, no reboot)
::   --hebrew / -hebrew / --he / -he / --rtl  -> Hebrew (he-IL) & RTL
::   --english / -english / --en / -en / --ltr -> English (en-US) & LTR
::   [avd_name]                               -> Optional AVD name
set "AVD_NAME="
set "LOCALE_MODE=keep"

:parse_args
if "%~1"=="" goto done_args
if /I "%~1"=="--hebrew"  ( set "LOCALE_MODE=hebrew"  & shift & goto parse_args )
if /I "%~1"=="-hebrew"   ( set "LOCALE_MODE=hebrew"  & shift & goto parse_args )
if /I "%~1"=="/hebrew"   ( set "LOCALE_MODE=hebrew"  & shift & goto parse_args )
if /I "%~1"=="--he"      ( set "LOCALE_MODE=hebrew"  & shift & goto parse_args )
if /I "%~1"=="-he"       ( set "LOCALE_MODE=hebrew"  & shift & goto parse_args )
if /I "%~1"=="hebrew"    ( set "LOCALE_MODE=hebrew"  & shift & goto parse_args )
if /I "%~1"=="--rtl"     ( set "LOCALE_MODE=hebrew"  & shift & goto parse_args )
if /I "%~1"=="-rtl"      ( set "LOCALE_MODE=hebrew"  & shift & goto parse_args )
if /I "%~1"=="--english" ( set "LOCALE_MODE=english" & shift & goto parse_args )
if /I "%~1"=="-english"  ( set "LOCALE_MODE=english" & shift & goto parse_args )
if /I "%~1"=="/english"  ( set "LOCALE_MODE=english" & shift & goto parse_args )
if /I "%~1"=="--en"      ( set "LOCALE_MODE=english" & shift & goto parse_args )
if /I "%~1"=="-en"       ( set "LOCALE_MODE=english" & shift & goto parse_args )
if /I "%~1"=="english"   ( set "LOCALE_MODE=english" & shift & goto parse_args )
if /I "%~1"=="--ltr"     ( set "LOCALE_MODE=english" & shift & goto parse_args )
if /I "%~1"=="-ltr"      ( set "LOCALE_MODE=english" & shift & goto parse_args )
if /I "%~1"=="--help"    goto show_help
if /I "%~1"=="-h"        goto show_help
if /I "%~1"=="/?"        goto show_help
if not defined AVD_NAME set "AVD_NAME=%~1"
shift
goto parse_args

:show_help
echo Usage: scripts\start-emulator.bat [--hebrew ^| --english] [avd_name]
echo.
echo Options:
echo   (default)                Keep current emulator locale ^(fast Quickboot, no UI restart^)
echo   --hebrew, -he, --rtl     Configure emulator for Hebrew ^(he-IL^) with RTL layout enabled
echo   --english, -en, --ltr    Configure emulator for English ^(en-US^) with LTR layout
echo   avd_name                 Optional Android Virtual Device name
exit /b 0

:done_args

:: Resolve Android SDK path from local.properties, ANDROID_HOME, or default locations
set "SDK_DIR="
if exist "%ROOT_DIR%\local.properties" (
    for /f "tokens=1,* delims==" %%A in ('findstr /b "sdk.dir=" "%ROOT_DIR%\local.properties"') do (
        set "SDK_DIR=%%B"
    )
)

if not defined SDK_DIR goto resolve_sdk_fallback
set "SDK_DIR=%SDK_DIR:\:=:%"
set "SDK_DIR=%SDK_DIR:\\=\%"
for %%P in ("%SDK_DIR%") do set "SDK_DIR=%%~fP"
goto sdk_resolved

:resolve_sdk_fallback
if defined ANDROID_HOME (
    set "SDK_DIR=%ANDROID_HOME%"
) else if defined ANDROID_SDK_ROOT (
    set "SDK_DIR=%ANDROID_SDK_ROOT%"
) else if exist "%USERPROFILE%\android-tools\sdk\emulator\emulator.exe" (
    set "SDK_DIR=%USERPROFILE%\android-tools\sdk"
) else (
    set "SDK_DIR=%LOCALAPPDATA%\Android\Sdk"
)

:sdk_resolved
set "EMULATOR=%SDK_DIR%\emulator\emulator.exe"
set "ADB=%SDK_DIR%\platform-tools\adb.exe"
set "PATH=%SDK_DIR%\platform-tools;%SDK_DIR%\emulator;%PATH%"

if not exist "%EMULATOR%" (
    echo Error: emulator.exe not found at "%EMULATOR%"
    echo Check sdk.dir in local.properties or set ANDROID_HOME.
    pause
    exit /b 1
)

:: Pick the first available AVD if none was specified
if "%AVD_NAME%"=="" (
    for /f "delims=" %%I in ('emulator.exe -list-avds') do (
        if not defined AVD_NAME set "AVD_NAME=%%I"
    )
)

if "%AVD_NAME%"=="" (
    echo Error: No Android Virtual Devices ^(AVDs^) found.
    pause
    exit /b 1
)

:: Check if an emulator is already running
set "RUNNING_EMU="
for /f "tokens=1" %%D in ('adb.exe devices ^| findstr /r "^emulator-[0-9]*[ 	]*device$"') do (
    set "RUNNING_EMU=%%D"
)

:: NOTE: Do NOT pass -change-locale to emulator.exe here, because -change-locale
:: forces the Android Emulator to ignore Quickboot snapshots and perform a full
:: cold boot + framework restart every time.
if not defined RUNNING_EMU (
    echo Starting Android Emulator ^(%AVD_NAME%^)...
    start "" "%EMULATOR%" -avd "%AVD_NAME%"
) else (
    echo Emulator ^(%RUNNING_EMU%^) is already running.
)

echo Waiting for emulator to be ready...
"%ADB%" wait-for-device
:wait_boot
set "BOOTED="
for /f "delims=" %%B in ('adb.exe shell getprop sys.boot_completed 2^>nul') do set "BOOTED=%%B"
if not "%BOOTED%"=="1" (
    ping -n 2 127.0.0.1 >nul
    goto wait_boot
)
"%ADB%" shell pm path android >nul 2>&1
if errorlevel 1 (
    ping -n 2 127.0.0.1 >nul
    goto wait_boot
)

:: Apply Hebrew/RTL or English/LTR settings only when requested and not already active
if /I "%LOCALE_MODE%"=="hebrew" goto set_locale_hebrew
if /I "%LOCALE_MODE%"=="english" goto set_locale_english
goto build_and_install

:set_locale_hebrew
"%ADB%" shell settings put global debug.force_rtl 1 >nul 2>&1
set "CUR_LOCALE="
for /f "delims=" %%L in ('adb.exe shell getprop persist.sys.locale 2^>nul') do set "CUR_LOCALE=%%L"
if /I not "%CUR_LOCALE%"=="he-IL" (
    echo Switching emulator locale to Hebrew ^(he-IL^) and Right-to-Left ^(RTL^)...
    "%ADB%" root >nul 2>&1
    "%ADB%" wait-for-device
    "%ADB%" shell "setprop persist.sys.locale he-IL; settings put system system_locales he-IL; settings put global debug.force_rtl 1; setprop ctl.restart zygote" >nul 2>&1
    echo Waiting for Android UI to reload in Hebrew...
    ping -n 4 127.0.0.1 >nul
    goto wait_after_locale
)
echo Emulator is already in Hebrew ^(he-IL^) with RTL enabled.
goto build_and_install

:set_locale_english
"%ADB%" shell settings put global debug.force_rtl 0 >nul 2>&1
set "CUR_LOCALE="
for /f "delims=" %%L in ('adb.exe shell getprop persist.sys.locale 2^>nul') do set "CUR_LOCALE=%%L"
if defined CUR_LOCALE if /I not "%CUR_LOCALE%"=="en-US" (
    echo Switching emulator locale to English ^(en-US^) and Left-to-Right ^(LTR^)...
    "%ADB%" root >nul 2>&1
    "%ADB%" wait-for-device
    "%ADB%" shell "setprop persist.sys.locale en-US; settings put system system_locales en-US; settings put global debug.force_rtl 0; setprop ctl.restart zygote" >nul 2>&1
    echo Waiting for Android UI to reload in English...
    ping -n 4 127.0.0.1 >nul
    goto wait_after_locale
)
echo Emulator is already in English ^(en-US^) with LTR enabled.
goto build_and_install

:wait_after_locale
set "BOOTED="
for /f "delims=" %%B in ('adb.exe shell getprop sys.boot_completed 2^>nul') do set "BOOTED=%%B"
if not "%BOOTED%"=="1" (
    ping -n 2 127.0.0.1 >nul
    goto wait_after_locale
)
"%ADB%" shell pm path android >nul 2>&1
if errorlevel 1 (
    ping -n 2 127.0.0.1 >nul
    goto wait_after_locale
)

:build_and_install
:: Build and install the latest debug APK
if not defined JAVA_HOME (
    if exist "%USERPROFILE%\android-tools\jdk17\bin\java.exe" (
        set "JAVA_HOME=%USERPROFILE%\android-tools\jdk17"
    )
)

echo Building debug APK...
pushd "%ROOT_DIR%" >nul
call "%ROOT_DIR%\gradlew.bat" assembleDebug
popd >nul
set "APK=%ROOT_DIR%\app\build\outputs\apk\debug\app-debug.apk"
if not exist "%APK%" set "APK=%ROOT_DIR%\app\build\outputs\apk\release\app-release.apk"

if exist "%APK%" (
    echo Installing Solitaire APK...
    "%ADB%" install -r "%APK%"
    echo Launching Solitaire...
    "%ADB%" shell am force-stop com.adfree.solitaire
    "%ADB%" shell am start -n com.adfree.solitaire/.MainActivity
) else (
    echo Warning: Could not find or build APK to install.
    pause
)
