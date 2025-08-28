WORKSPACE_DIR="$(pwd)"
export ANDROID_SDK_ROOT=$HOME/android-sdk
mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
cd "$ANDROID_SDK_ROOT/cmdline-tools"
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip commandlinetools-linux-*_latest.zip
rm commandlinetools-linux-*_latest.zip
mv cmdline-tools latest
export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"
/bin/bash -c "yes | sdkmanager --licenses"
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
cd $WORKSPACE_DIR
printf "sdk.dir=%s\n" "$ANDROID_SDK_ROOT" > local.properties
./gradlew :composeApp:compileDebugSources
