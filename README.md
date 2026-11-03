# Argument Cat 😼

A small native Android game: pick a claim and try to change a stubborn cat's mind before you run out of turns. Built with Kotlin, Jetpack Compose and [QVAC SDK](https://github.com/tetherto/qvac/releases/tag/sdk-v0.23.0).

The cat says you can't cut metal with scissors. Point out that scissors cut aluminum foil, and see if it concedes. Other claims are less grounded—this is a game, not a fact checker.

## Play

1. Choose a claim and a limit of 3, 5 or 10 turns.
2. Tap **Start argument**. The first round downloads and loads the chat model.
3. Type an argument, or tap **Speak**, record it and edit the transcript. Tap **Send** when ready.
4. Change the cat's mind to win. If it holds its position until the turn limit, the cat wins.

The cat speaks its replies and reacts with an animated 3D head. **Voice off** keeps replies as text; **Hear reply** replays the latest response. Speech models download on first use.

## Powered by QVAC

All model inference runs locally through the native Kotlin client in **QVAC SDK 0.23.0**:

- **Conversation:** Qwen3.5 2B, Q4_K_M. Generates the cat's reply and a structured concession signal used by the game.
- **Speech to text:** Whisper Tiny, Q8. Transcribes microphone recordings into editable arguments.
- **Text to speech:** Parler Mini v1 English, Q8, with a soft female Laura voice description. Generates the cat's spoken replies.

Android handles microphone capture and audio playback. QVAC handles model downloads, loading and inference. The app switches between chat and speech models rather than keeping every runtime loaded at once.

There is no app backend or account to set up. Internet access is required to fetch the SDK and model weights; the weights are cached on the device. Allow several gigabytes of free storage for the runtime and models.

## Build

Requirements:

- JDK 21.
- Android SDK Platform 36, Build Tools 36.0.0 and Platform Tools.
- Python 3 and `curl` for the SDK download helper.

```sh
git clone https://github.com/NamelsKing/argument-cat.git
cd argument-cat
python3 tools/fetch_sdk.py
./gradlew :app:assembleDebug
```

The helper downloads the public QVAC Maven archive into `vendor/qvac` and verifies its pinned SHA256. It does not download model weights or require a GitHub token. The Gradle wrapper is included.

Set `ANDROID_HOME` to your Android SDK directory, or add its path to an untracked `local.properties` file:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

If you already have the extracted QVAC Maven repository, use it directly:

```sh
./gradlew -PqvacMavenDir=/absolute/path/to/maven-repository :app:assembleDebug
```

## Run

Use an **arm64 Android 10+** device or an arm64 emulator. Connect it with USB debugging enabled, or start the emulator, then install and launch:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.namelsking.argumentcat/.MainActivity
```

You can also open the project in Android Studio and run the `app` configuration. Model downloads begin when a capability is first used; simply opening the app does not start inference. Microphone permission is requested when you tap **Speak**, and typing remains available if permission is denied.

Local text inference and speech synthesis have been exercised on an Android 16 arm64 emulator configured with 6 GiB RAM. Microphone transcription, physical-phone performance and fully offline operation still need device testing.

## Development

```sh
./gradlew :core:test :app:lintDebug
```

- `core/`: game rules, prompts, structured reply validation and unit tests.
- `app/`: Compose UI, QVAC integration, recording, playback and the OpenGL cat renderer.
- `tools/fetch_sdk.py`: pinned public SDK download and checksum verification.

## License

[MIT](LICENSE). Model weights retain their upstream licenses and are not included in this repository. Argument Cat is an independent personal demo, not an official QVAC application.
