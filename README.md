# Waze audio patches

Patches for use with Morphe that add a music app of your choice (default: myspoti) to the
Waze audio player, without the app needing the Waze SDK.

<!-- PATCHES_START EXPANDED -->
> **[v1.0.0-dev.1](https://github.com/pomee4/waze-audio-patches/releases/tag/v1.0.0-dev.1)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;1 patches total
<details open>
<summary>📦 Waze&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**Package name:** `com.waze`

**🎯 Supported versions:**

| 5.26.5.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Add custom audio app](#add-custom-audio-app) | Adds a music app of your choice (default: myspoti) to the Waze audio player. Waze controls it through its standard Android media service, without the app needing the Waze SDK. | • Audio app package name<br>• Audio app name |

</details>

<!-- PATCHES_END -->

## How it works

Waze's audio player supports partner apps through the Waze audio SDK. For those apps, Waze:

1. downloads a list of allowed partner package names from its server,
2. checks the app is "installed" by looking for a `com.waze.sdk.audio.ACTION_INIT` broadcast receiver,
3. sends that broadcast and waits up to 15 s for the app to bind to `com.waze.sdk.SdkService` and register,
4. controls playback through the app's standard `MediaBrowserService` (the same one Android Auto uses).

The **Add custom audio app** patch:

- adds the app to the partner list with only the audio scope, so no navigation data is shared and no
  consent dialog is shown,
- makes the "installed" check pass for the app,
- on connect, registers the app directly (as Waze already does for Spotify) instead of waiting for an SDK callback,
- adds the app to the manifest `<queries>` so Waze can see it on Android 11+.

Options: `audioAppPackageName` (default `app.myspoti.stable`) and `audioAppName` (default `myspoti`).
The app must have a `MediaBrowserService`.

### GmsCore support

A patched Waze is re-signed, so Google Play Services rejects it and Google sign-in fails. The **GmsCore support**
patch redirects Waze's Play Services calls (services, permissions, content providers, push messaging) to
[GmsCore](https://github.com/MorpheApp/MicroG-RE) (`app.revanced.android.gms`, the same one used by patched YouTube),
and adds manifest metadata so GmsCore presents the app to Google as the original Waze (package `com.waze`, original
signature). Unlike patched YouTube, Waze keeps its package name.

Requirements: GmsCore installed, signed in to your Google account in GmsCore, and battery optimization disabled
for GmsCore (for push notifications). Location still comes from Android's own location providers.

Developed against Waze **5.26.5.0**.

## Building

- Make changes on the `dev` branch with semantic commit messages (`feat:`, `fix:`, `chore:`).
- Pushing to `dev` builds a pre-release with GitHub Actions; the `.mpp` file is attached to the release.
- Local build: `./gradlew buildAndroid` (needs a GitHub token with `read:packages` as `gpr.user`/`gpr.key`
  in `~/.gradle/gradle.properties`). Output: `patches/build/libs/patches-*.mpp`.

## License

GPLv3, see [LICENSE](LICENSE).
