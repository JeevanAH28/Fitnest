# FitNest

FitNest is a private, offline-first Android home-fitness companion. It provides equipment-based home workouts, animated 3D form demonstrations, water and sleep tracking, weekly private progress photos, calorie logging, food and recipe guidance, a basic fitness Q&A assistant, skin-care habits, and optional Health Connect step syncing.

## Open and run

1. Install Android Studio with JDK 17 and Android SDK Platform 36.
2. Open `fitness-companion` as an Android Studio project.
3. Allow Gradle to sync, then run the `app` configuration on a physical device or emulator (Android 8+).

## Build an APK in the cloud

This project contains `.github/workflows/build-apk.yml`, which builds an installable debug APK in GitHub Actions without Android Studio on your computer.

1. Create an empty private GitHub repository named `fitnest`.
2. Upload the **contents** of this `fitness-companion` folder to that repository. Ensure `app`, `.github`, and the Gradle files are at the repository root.
3. Open the repository’s **Actions** tab, enable workflows if GitHub asks, select **Build FitNest APK**, then choose **Run workflow**.
4. When the workflow has a green check, open it and download the `FitNest-debug-apk` artifact.
5. Transfer `app-debug.apk` to your phone, open it, allow installs from the app you used to open it when Android asks, then tap **Install**.

The debug APK is appropriate for private personal use. Before publishing to Google Play, create a signed release build and an Android App Bundle (`.aab`) using a protected signing key.

Important: the cloud workflow currently produces a debug APK for the first installation. To install future updates over the existing app without uninstalling it and losing local data, configure a persistent release signing key in GitHub Actions before distributing updates.

## Privacy and current boundaries

- Water, sleep, workout completion, selected equipment, food log, and photo paths are stored on the phone in private app storage.
- Weekly fitness photos are copied into the app's private files directory; they are not uploaded.
- Health Connect is optional and asks only to read today’s step total.
- Food photos currently support a local logging flow. A photo alone cannot yield exact calories; exact values need ingredients, cooked weight, preparation details, and usually a nutrition database or vision service. Do not ship an AI food-analysis provider until it has an explicit privacy policy, user consent, and a secure server-side API key.
- The in-app chat and food suggestions are general wellness information, not medical, dermatology, or supplement advice.
- The included 3D models are downloaded once with the app and run locally. They are form demonstrations, not personal medical or training clearance.
- The 3D viewer adds about 17 MB of bundled exercise models before normal Android packaging overhead. The app currently includes push-up, jumping-jack, and bear-crawl demonstrations; add only properly licensed `.glb` models for future exercises.
