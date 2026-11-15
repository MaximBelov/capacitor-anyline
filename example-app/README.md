# Example app

A test page for a phone: it initialises the Anyline SDK with a licence key typed into the page, asks for the camera,
opens the scanner with an editable scan view config (a barcode scan by default) and reads the process's memory. Every
call and its result is written on the page.

```bash
npm install
npm run build
npx cap sync           # or: npx cap sync android / ios
npx cap run android    # or open ios/App/App.xcodeproj in Xcode
```

The SDK needs a licence key for the app id `com.example.plugin`; type it into the page. No key is stored in the app.

The app is wired the way the plugin's README asks an app to be:

- Android: Anyline's Maven repository in `android/build.gradle`, and `anyline-assets.gradle` applied from
  `android/app/build.gradle` with `anylineRetainAssetsPattern = 'module_barcode'` in `android/variables.gradle`, so the
  APK carries the barcode module only.
- iOS: `NSCameraUsageDescription` in `Info.plist`, and a Run Script build phase after the frameworks are embedded that
  runs `scripts/ios/remove-unneeded-assets.sh` with the build setting `ANYLINE_RETAIN_ASSETS_PATTERN = module_barcode`.
