# Launcher TV

[English](README.en.md) | [Português](README.md)

A modern alternative to the default Android TV launcher, developed in Java. It brings together movie and TV show highlights, apps, per-profile playback history, weather, and cinema listings. This version does not include the Channels section or Home Assistant integration. It does not use Gradle: the build script calls Android SDK tools directly.

## Contents

- [Features](#features)
- [Screenshots](#screenshots)
  - [Home with TMDB configured](#home-with-tmdb-configured)
  - [Home and profiles](#home-and-profiles)
  - [First launch and location](#first-launch-and-location)
- [Requirements](#requirements)
- [External services and API keys](#external-services-and-api-keys)
- [Configuration](#configuration)
- [Build](#build)
- [Prepare the TV and install](#prepare-the-tv-and-install)
  - [Before connecting](#before-connecting)
  - [Build and install](#build-and-install)
  - [Permissions and first launch](#permissions-and-first-launch)
- [Known limitations](#known-limitations)
- [Testing and compatibility](#testing-and-compatibility)
  - [Preserve remote shortcuts and data when updating](#preserve-remote-shortcuts-and-data-when-updating)
- [Disclaimer of warranties and liability](#disclaimer-of-warranties-and-liability)
- [Data and privacy](#data-and-privacy)
- [Project structure](#project-structure)
- [Contributing](#contributing)

## Features

- **Home:** movie and TV show highlights, Continue Watching, frequently used apps, watchlist, seven-day weather forecast, movies in theaters, and cinema reminders.
- **Movies and TV Shows:** TMDB highlights and artwork, linked to streaming services installed on the TV.
- **Apps:** a grid of apps with a launchable activity. Hold OK to open the menu: launch, move (left, right, up, and down), view app info, add to favorites, hide, or uninstall.
- **Profiles:** creates a local **User** profile on first launch; it can be renamed and additional profiles can be added. Each profile has its own avatar, history, and preferences. Choose **Kids profile: Yes or No** for each profile. Kids mode lets you choose an age from 3 to 17 to limit recommendations and apps. Optionally enable **Require PIN to enter Kids**; a PIN of at least four digits is set on the first entry and protects leaving the profile and changing restrictions.
- **Continue Watching:** tracks media sessions from apps and resumes content when the app provides a destination.
- **Search:** unified search for content and apps, with voice input when supported by the TV.
- **Weather:** current conditions and a seven-day forecast from Open-Meteo, using automatic location or a manually entered city. The theme follows local time and does not use a hard-coded city location.
- **Cinema:** movies showing in your city; hold OK to view showtimes and create reminders.
- **Accessibility:** larger text and more visible focus.
- **Remote shortcuts:** physical app buttons (such as Netflix and YouTube) launch their apps from any screen when the accessibility service is enabled.

## Screenshots

These screenshots were captured on a TCL Android TV. Highlights and recommendations depend on cache contents and configured services. The Kids and PIN options shown in the form are a demonstration and were not saved to the TV profile.

### Home with TMDB configured

![Home screen with TMDB highlights and recommendations](images/home-tmdb-tcl.png)

Captured on a TCL with a TMDB key enabled. The top navigation was cropped because this image came from the full launcher version; this public version does not include Channels or Home Assistant.

### Home and profiles

![Launcher TV home screen on a TCL](images/tela-inicial-tcl.png)

![Profile selector with the default User profile](images/perfil-usuario-selecao.png)

![Editing the User profile with Kids mode disabled](images/perfil-edicao-usuario.png)

![Kids options, ages 3 to 17, and PIN enabled in the form](images/perfil-kids-faixa-etaria-pin.png)

### First launch and location

These screens show the Launcher TV explanation and Android's location permission request. They were captured without granting permission.

![Launcher TV location explanation](images/permissao-localizacao-explicacao.png)

![Android TV location permission request](images/permissao-localizacao-android.png)

## Requirements

- Android TV running Android 9 (API 28) or later.
- JDK with `javac` and `keytool`.
- Android SDK with `aapt2`, `d8`, `zipalign`, `apksigner`, `core-lambda-stubs.jar`, and at least one Android platform installed.
- ADB to install on the TV (network or USB debugging enabled).

`build.sh` uses the newest tools it finds in the SDK. Set `ANDROID_HOME` to your SDK path; otherwise, it looks in `/opt/homebrew/share/android-commandlinetools`.

## External services and API keys

The launcher uses the services below. Only TMDB requires an API key for movie and TV show features.

| Service | Purpose | Key | Where to get it |
|---|---|---|---|
| TMDB | Highlights, movies, TV shows, artwork, and content ratings | Required for Movies, TV Shows, and recommendations | Create a free account at <https://www.themoviedb.org> and generate a key under **Settings → API** (<https://www.themoviedb.org/settings/api>). Use the *API Key (v3)* |
| Open-Meteo | Weather forecast | Not required | <https://open-meteo.com> |
| Open Cinema Project | Nearby showtimes, theaters, and ticket links | Public source | Location-based lookup; coverage varies by city |

This product uses the TMDB API but is not endorsed or certified by TMDB.

## Configuration

For the fullest experience, adding a TMDB key is recommended. It enables Movies, TV Shows, recommendations, and movie data for theaters when no showtimes are available. Create `config.properties` in the project root:

```properties
tmdb_key=YOUR_TMDB_API_KEY
```

Without `tmdb_key`, the Movies and TV Shows tabs are hidden, recommendations are not loaded, and the app removes old TMDB cache so content from a previous installation is not shown. The Cinema section remains available: it queries public Open Cinema Project showtimes when location is available and, if no showtimes are returned, uses TMDB to list movies in theaters when a key is configured. Open Cinema Project coverage varies by city. No Ingresso.com partnership needs to be configured. `config.properties` is in `.gitignore`. If the file is absent, the build continues without TMDB integration and does not create empty keys.

> **Warning:** the TMDB key is embedded in the APK during the build and can be extracted from the app. Do not publish or share APKs built with a personal key. Each user should create their own key and build their own APK.

## Build

```sh
./build.sh
```

The output is `LauncherTV.apk`. Each build copies the previous APK to `backups/builds/` along with its SHA-256 hash.

On the first build, the script asks for a password and creates `sala.keystore` to sign the APK. **Keep this file safe**: updates can only be installed over an existing app when signed with the same certificate. It is also in `.gitignore`; the password is not stored in the project.

Anyone installing the app for the first time can build with the keystore created by the first build and run `adb install LauncherTV.apk`. Keep and reuse that same keystore for future updates.

## Prepare the TV and install

Installation uses ADB. You are responsible for confirming the TV model and Android TV version, reviewing permissions, and deciding whether to install this software. ADB can control the device; connect only your own TV and computer.

### Before connecting

On the TV, enable Developer options (usually under **Settings → Device Preferences → About**, press the **Build** item several times). In **Developer options**, enable **USB debugging** or **Network debugging**; names and locations vary by manufacturer and version. Keep the TV and computer on the same network. On the first connection, approve your computer's ADB key on the TV.

To check the Android version, connect, and inspect the current state without changing the TV, run these commands from the `launcher_android` folder:

```sh
adb connect TV_IP_ADDRESS:5555
adb devices -l
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity'
adb shell dumpsys power | grep mWakefulness
adb shell pm path br.casa.sala
```

Use the IP address shown in the TV's network settings. For USB, connect the cable and confirm the device with `adb devices -l`; `adb connect` is not needed. The activity, power, and package-path checks show which app is in the foreground, whether the TV is awake, and whether Launcher TV is installed. If a third-party app is in the foreground, do not close it to install. An update may restart the Launcher TV process if it is active; the installer does not close apps or automatically start Launcher TV.

### Build and install

If you want TMDB features, configure `config.properties` before building. The build also works without a key:

```sh
./build.sh
shasum -a 256 LauncherTV.apk
```

For a first installation, confirm that `adb shell pm path br.casa.sala` returned no path, then run:

```sh
adb install LauncherTV.apk
```

To update an existing installation signed with the same certificate, use the installer included with this project:

```sh
./install-tv.sh --confirm-tv-install
```

The script checks the connection, saves the installed APK and metadata under `backups/tv/<timestamp>/`, and only then runs `adb install -r LauncherTV.apk`. If it cannot create the backup, installation is cancelled. Do not run `adb install -r` manually to bypass this protection. Android cannot update an app with an APK signed by a different certificate; uninstalling first deletes local profiles, history, and preferences.

If the TV already has package `br.casa.sala` signed with another keystore (for example, an earlier Launcher TV build), use the original key to update without deleting data. `build.sh` accepts `SIGNING_KEYSTORE`, `SIGNING_KEY_ALIAS`, `SIGNING_STORE_PASS`, and `SIGNING_KEY_PASS`. When run in a terminal, it prompts for the password without storing it in the project. To select another keystore:

```sh
export SIGNING_KEYSTORE=/path/to/your-keystore.jks
export SIGNING_KEY_ALIAS=sala
./build.sh
```

Without the key used for the existing installation, Android blocks the update. Uninstalling allows a fresh install but deletes local data.

After installation, check what Android registered:

```sh
adb shell dumpsys package br.casa.sala | grep -E 'versionCode=|versionName=|lastUpdateTime='
adb shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity'
adb shell pm path br.casa.sala
adb shell settings get secure enabled_accessibility_services
```

These commands confirm installation and system state; they do not replace visual and remote-control testing.

### Permissions and first launch

1. Open Launcher TV from the TV home screen or select it as the default **Home** app in settings.
2. To use physical app-button shortcuts, enable **Accessibility → Launcher TV: remote shortcuts**. This permission is needed to receive remote key events while another app is open.
3. To let playback history capture information provided by apps, enable **Notification access → Launcher TV: media history**. Without it, history may be incomplete.
4. For location-based weather and cinema, grant location permission or enter a city manually. Open Cinema Project receives the coordinates used to search for nearby showtimes.
5. Review each permission request on the TV and enable only the features you want. Shortcuts, history, or local forecasts may be limited without optional permissions.

A regular update preserves the app's private data when the signing certificate is the same. Uninstalling deletes that data. Never run `am force-stop br.casa.sala`: this can disable the accessibility service for remote shortcuts until it is enabled again in TV settings.

## Known limitations

- **Netflix — Continue Watching:** resuming is still inconsistent. Netflix may report only that the app is playing, without a title, artwork, or direct content link. In that case, the card may be generic and selecting it may open Netflix without resuming the title or exact position. The launcher cannot retrieve data that the service does not expose through Android interfaces.
- **Other streaming apps:** title, artwork, progress, and resume destination depend on what each app publishes; some cards only open the app.
- **Cinema:** Open Cinema Project may have no showtimes for a city, and major theater chains may not be listed.

## Testing and compatibility

The base version was installed on a TCL Android TV; remote shortcuts were mapped for that manufacturer. The public variant without Channels and Home Assistant was also built and installed on a TCL on 2026-10-23. The home screen, location requests, and profile options were visually checked. Full remote-control navigation still needs user validation. On other brands, key codes can differ and shortcuts may not work.

- When the TV is offline, the launcher continues to show the last cached content.

### Preserve remote shortcuts and data when updating

Remote button mappings are in `RemoteShortcutService.java`. Updates should keep the same package (`br.casa.sala`) and use the same keystore/certificate, installing with `./install-tv.sh --confirm-tv-install`. This updates the app without uninstalling it and preserves local data. Do not uninstall the app or clear its data if you want to keep profiles and preferences. After installation, check `adb shell settings get secure enabled_accessibility_services`; if Android disables the shortcut service, enable it again in TV settings.

## Disclaimer of warranties and liability

Launcher TV is provided **"as is"**, for voluntary use, with no warranty of uninterrupted operation, availability, compatibility with every device, or fitness for a particular purpose. To the maximum extent permitted by applicable law, the maintainers are not liable for device damage, loss or corruption of data, service interruptions, or other harm arising from the use of, or inability to use, this project.

Installation, configuration, and use are at the user's own risk. Users are responsible for checking TV compatibility, reviewing permissions, safeguarding their keys, and backing up data before installing or updating. Integrations and information depend on third-party services and apps; they may change, become unavailable, or provide incomplete data, and are outside the maintainers' control. This section does not limit rights that cannot be excluded under applicable law.

## Data and privacy

Profiles, history, playback progress, location, and forecasts are stored in the app's private storage and separated by profile. The Kids profile PIN is stored using salt and PBKDF2, never as plain text. Weather requests location permission; if you deny it, enter a city manually.

## Project structure

```text
AndroidManifest.xml        Manifest, permissions, and components
build.sh                   Build and back up previous APK
install-tv.sh              Back up TV APK and install with confirmation
config.properties          Local keys (ignored by Git)
res/                       Android resources
src/br/casa/sala/
  MainActivity.java        UI, navigation, focus, and pages
  RemoteShortcutService.java  Physical remote button shortcuts
  Tmdb.java                TMDB highlights, providers, and cache
  Search.java              Content and app search
  MediaListener.java       Media session monitoring
  History.java             Per-profile history and progress
  Profiles.java            Local profiles and Kids mode rules
  Watchlist.java           Per-profile watchlist
  Weather.java             Location and forecast
  Cinema.java              Movies in theaters, showtimes, and reminders
  Sun.java                 Time-of-day theme
```

## Contributing

Suggestions and fixes are welcome through issues or pull requests. Before opening a PR, build with `./build.sh` and test navigation with the remote: the app is designed for directional focus, not touch.
