# Privacy

Star Chart works entirely on the phone. It has no network permission, so nothing it knows
can leave.

That is the whole policy. The rest of this page is the evidence for it.

## One permission, and only if you ask for it

`app/src/main/AndroidManifest.xml` declares exactly one:

```
android.permission.ACCESS_COARSE_LOCATION
```

It is requested only when you press **Ask the GPS** in the Where dialog. Typing a latitude
and longitude works without granting anything at all — and typing is the only way to chart
somewhere you are not standing, which is half of what a planisphere is for.

Coarse is deliberate. A star chart needs to know which hillside you are on, not which end
of it, so fine location would be asking for more precision than the app can use.

There is **no `INTERNET` permission**. Without it Android will not let the app open a
network connection. The star catalogue ships inside the APK and every position is computed
on the phone, so there is nothing to fetch and no way to send.

There is no background location permission, and no service.

## What is stored

Your chosen latitude and longitude, which way the chart is turned, and which layers are
switched on. All in `SharedPreferences`, all visible in `sky/Preferences.kt`.

No history, no log of where you have been, no record of what you looked at.

## No analytics

No crash reporting, no telemetry, no advertising identifier, no third-party SDK. The
dependency list in `app/build.gradle.kts` is AndroidX, Jetpack Compose and Mudita's MMD
component library, and nothing else.

## Checking for yourself

```
aapt2 dump badging app-release.apk | grep uses-permission
```

That prints every permission the built app actually carries. It prints two lines:

```
uses-permission: name='android.permission.ACCESS_COARSE_LOCATION'
uses-permission: name='com.wanderwildwood.seizu.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
```

The first is the one described above. The second is not mine: AndroidX defines it
automatically for every app, it is signature-level and scoped to this package so only this
app can hold it, and it exists so a runtime-registered broadcast receiver is not exported
to other apps. It grants access to nothing.

There is no `INTERNET` in that list, which is the claim above without having to trust me.
