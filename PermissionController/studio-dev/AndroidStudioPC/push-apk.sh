# Script to install permission controller apk in system partition
scriptDir="$(cd $(dirname $0) && pwd)"
androidBuildTop="$(cd "${scriptDir}/../../../../../../"; pwd)"

APK_FILE="$androidBuildTop/out/gradle/build/AndroidStudioPC/PermissionController/build/outputs/apk/debug/PermissionController-debug.apk"

echo "APK path set to $APK_FILE"

if [ ! -f $APK_FILE ]; then
    echo "Compiled APK not found $APK_FILE" > /dev/stderr
    exit 1
fi

echo "Compiled APK found at $APK_FILE"

adb install $APK_FILE

echo "Install complete"
