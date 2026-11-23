# Nearly

A cozy little space for two. Capacitor Android app + home-screen widgets.

## Get the APK (no Android Studio needed)
1. Create a new GitHub repo and upload everything in this folder (keep `.github/` and `.gitignore`).
2. Open the repo's **Actions** tab, run **Build Nearly APK** (it also runs on every push).
3. When it turns green, open the run and download **Nearly-debug-apk** under Artifacts. Unzip it and install `app-debug.apk` on your phone.
4. Long-press the home screen > Widgets > "Nearly ·", or open the app > Us > Home-screen widgets.

## Build on your own computer instead
    npm install
    npx cap add android
    python3 scripts/apply-widgets.py
    npx cap sync android
    cd android && ./gradlew assembleDebug

## Layout
- `www/` the app (index.html + nearly-widgets.js)
- `native-widget/` Java + XML for the 11 widget types (see its README.txt)
- `scripts/apply-widgets.py` copies the widgets into the generated android project
- Package ID: `com.benjamin.nearly`

## Notes
- The widget Java has not been compiled yet. If the first build fails, send the error log.
- Two-phone syncing (partner's mood, photos, candle...) needs a small database URL passed to
  `NearlyWidgets.configure({url, code, slot, pslot})`. Not set up yet; see native-widget/README.txt.
