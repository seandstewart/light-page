set shell := ["bash", "-eu", "-o", "pipefail", "-c"]
set default-list := true

default_avd := "light-phone-iii-api34"
light_page_apk := "light-page/build/outputs/apk/debug/light-page-debug.apk"
emulator_apk := "sdk/emulator/build/outputs/apk/debug/emulator-debug.apk"
light_page_id := "com.thelightphone.lightpage"
serial := env("ANDROID_SERIAL", "")
adb := if serial == "" { "adb" } else { "adb -s " + serial }

# Interactive CLI bootstrap wizard
[group('setup')]
bootstrap:
  ./scripts/bootstrap-dev-env-wizard.sh

# Run all checks expected in CI
[group('check')]
check:
  ./gradlew check --stacktrace

# Lint light-page module
[group('check')]
lint:
  ./gradlew :light-page:lint --stacktrace

# Run light-page module tests
[group('check')]
test:
  ./gradlew :light-page:test --stacktrace

# Build Light Page APK
[group('build')]
build-light-page:
  ./gradlew :light-page:assembleDebug --stacktrace

# Build LightOS emulator APK
[group('build')]
build-emulator:
  ./gradlew :sdk:emulator:assembleDebug --stacktrace

# Start emulator with writable system
[group('emulator')]
start-emulator avd=default_avd:
  emulator -avd {{avd}} -writable-system

# Start emulator in background with log file
[group('emulator')]
start-emulator-bg avd=default_avd:
  nohup emulator -avd {{avd}} -writable-system >/tmp/light-emulator.log 2>&1 &
  echo "emulator log: /tmp/light-emulator.log"

# Wait for configured adb target
[group('emulator')]
wait-device:
  {{adb}} wait-for-device

# Prepare writable system partition
[group('emulator')]
prepare-system:
  {{adb}} root
  {{adb}} remount || (echo "remount failed: try 'adb disable-verity && adb reboot' then rerun" && false)

# Install Light Page APK on emulator/device
[group('light-page')]
install-light-page apk=light_page_apk:
  {{adb}} install -r {{apk}}

# Launch Light Page from launcher category
[group('light-page')]
launch-light-page tool=light_page_id:
  {{adb}} shell monkey -p {{tool}} -c android.intent.category.LAUNCHER 1

# Install LightOS emulator as privileged system app
[group('emulator')]
install-system-emulator apk=emulator_apk:
  {{adb}} shell mkdir -p /system/priv-app/LightOSEmulator
  {{adb}} push {{apk}} /system/priv-app/LightOSEmulator/LightOSEmulator.apk
  {{adb}} reboot

# Rebuild and reinstall emulator app without full priv-app flow
[group('emulator')]
reinstall-emulator:
  ./gradlew :sdk:emulator:assembleDebug --stacktrace
  {{adb}} install -r {{emulator_apk}}

# Verify privileged install and test-keys image
[group('emulator')]
verify-system-emulator:
  {{adb}} shell pm path com.thelightphone.sdk.emulator
  {{adb}} shell dumpsys package com.thelightphone.sdk.emulator | grep "uid=1000"
  {{adb}} shell getprop ro.build.description | grep "test-keys"

# Set LightOS emulator as launcher
[group('emulator')]
set-launcher:
  {{adb}} shell cmd package set-home-activity com.thelightphone.sdk.emulator/.MainActivity

# Disable Android transition animations
[group('emulator')]
disable-animations:
  {{adb}} shell settings put global window_animation_scale 0
  {{adb}} shell settings put global transition_animation_scale 0
  {{adb}} shell settings put global animator_duration_scale 0

# Bring emulator up and remounted
[group('flow')]
setup-emulator avd=default_avd: (start-emulator-bg avd) wait-device prepare-system

# Fast local quality gate
[group('flow')]
dev-check: lint test build-light-page

# Serve M2 fixture pages over HTTP for visual QA
[group('qa')]
serve-fixtures port='8000':
  python3 -m http.server {{port}} --directory fixtures/m2
