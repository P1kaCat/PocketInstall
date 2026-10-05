# Logo, startup and app language

The approved PocketInstall symbol is a blue pocket with a negative-space downward arrow. The original PNG is retained; vector/adaptive and Android 13 monochrome icons are provided.

Startup uses a 550 ms fade/scale transition. Disabled system animations skip it. Rotation, activity recreation and language changes do not replay a completed introduction.

Fresh installations explicitly choose French, US English, German, Spanish, Japanese or Simplified Chinese. Names appear in their own language. Device language suggests a supported choice; unsupported languages suggest US English. Confirmation is persisted before locale application.

AppCompat persists locales on Android 8–12; Android 13+ uses per-app language settings. Help offers a language picker, disabled during active imports/downloads. App language does not select the downloaded Windows language.

Navigation, main guides and download-consent dialogs are localized. Some Linux/library diagnostics, PC messages and legacy generated commands remain French. Repository documentation is English.

```sh
cd android
bash gradlew :app:testDebugUnitTest :server-core:test :app:assembleDebug :app:lintDebug
```

Manual checks: fresh launch, each locale, persistence after restart, rotation, locale switching, landscape, narrow screens, large fonts and reduced animation. Unit tests check persistence, regional suggestions and formatted resources; they do not replace device UI checks.
