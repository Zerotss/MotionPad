# Build and test

Use Node 20 or 22, JDK 17 or 21, Android SDK 35 and Build Tools 35.0.0. From the project directory:

```sh
npm ci
npx tsc --noEmit
npm test -- --runInBand
cd android
./gradlew :app:testReleaseUnitTest :app:assembleRelease
```

The `bundleJs` task includes the Metro bundle and assets in the APK. The project's release build is unsigned. The distributed installer is signed and can run without a development server.
