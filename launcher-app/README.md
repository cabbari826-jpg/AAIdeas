# Şüşə — glass launcher (prototype)

Android home screen where every app icon sits on glass tinted with the app's own colour.
Working name; the final name and icon come later. Lives here temporarily, like the
crossword; it moves to its own repository later.

- Pages: page one = clock, weather and calendar cards, four favourite apps; the next pages
  hold all other apps (4 x 5), alphabetically. No app drawer: everything is on the home screen.
- Dock (phone, messages, browser, camera) and search stay at the bottom on every page.
- Long press an app: shortcuts, glass colour, app info, hide, uninstall.
  Long press empty space: settings (glass, wallpaper, text colour, hidden apps).
- Glass = the launcher's own wallpaper, blurred once at quarter size and drawn behind each
  element at its window position (works on every Android version, cheap on old phones).
- Icons: the app's monochrome (themed) icon in white on Android 13+; apps without one keep
  their coloured logo on a lighter tint.
- Weather: Open-Meteo, prototype only (its free tier is non-commercial).

Build: `./gradlew assembleDebug`. Screenshots: `./gradlew testDebugUnitTest -Proborazzi.test.record=true`
(written to `app/screenshots/`). CI publishes pre-releases `steklo-N` (asset `steklo.apk`);
newest: https://github.com/cabbari826-jpg/AAIdeas/releases?q=steklo

## 0.2
- Icons keep the original colour, picture icons get their white drawing pulled out.
- Apps open by growing from the icon; swipe down opens the notification shade, swipe up opens search.
- Weather card tap asks for location or refreshes.
- "Жидкое стекло" switch (Android 13+).
- Widgets page left of the home screen.

## 0.3
- Sturdier icon processing (bad monochrome layers ignored, plates peeled, readable glass).
- Page swipes no longer stolen by the shade swipe.
- "Отправить значки для проверки" in settings (prototype).
