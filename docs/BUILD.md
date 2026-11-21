# Compilación y pruebas
Utiliza Node 20 o 22, JDK 17 o 21, Android SDK 35 y Build Tools 35.0.0. Desde la carpeta del proyecto:

```sh
npm ci
npx tsc --noEmit
npm test -- --runInBand
cd android
./gradlew :app:testReleaseUnitTest :app:assembleRelease
```

La tarea bundleJs integra el paquete de Metro y los recursos en el APK. La compilación release del proyecto queda sin firmar. El instalador distribuido está firmado y puede arrancar sin servidor de desarrollo.
