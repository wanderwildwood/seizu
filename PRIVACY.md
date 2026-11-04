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

## The compass and motion sensors, only while Point is on

**Point** turns the chart to the way the phone faces. For that it reads the phone's compass
and motion sensors (the fused rotation vector, and the magnetometer's accuracy so it can say
when the compass is unsure). They need no permission. They are read on the phone only while
Point is on and the app is in front: the listener is registered when Point comes on or the
app comes back, and unregistered when Point goes off, the view is held, or the app goes into
the background. Readings are turned into a direction and drawn; none is stored.

## What is stored

Your chosen latitude and longitude, which way the chart is turned, whether it turns
sideways with the phone, and which layers are switched on. Where Point is looking is not
stored. All in `SharedPreferences`, all visible in `sky/Preferences.kt`.

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

<!-- shared-about privacy section -->
## Who we are

Star Chart is made by wander wildwood, an independent developer: one person, not a company. wander wildwood is the data controller for anything the app itself handles, and that is very little. Star Chart keeps what you put in it on your phone and sends nothing to the developer, so the developer holds no data about you.

## How to contact us

Write to email@wanderthe.dev about your data, this policy or anything else. The same address is in the app, under About.

## Your rights

If you are in the EU, the EEA or the UK, the GDPR gives you the right to:

- see the personal data held about you (access)
- have it corrected (rectification) or deleted (erasure)
- limit how it is used (restriction)
- object to its use (objection)
- get a copy to take elsewhere (portability)
- withdraw your consent at any time, where something rests on it
- complain to a data protection supervisory authority, for example the one where you live

Similar laws elsewhere, such as the CCPA in California, give you the right to know, delete and correct, and to opt out of the sale or sharing of your data. Nothing from Star Chart is sold or shared.

Since Star Chart keeps what it stores only on your phone, you can exercise most of these rights yourself: type a different place in the Where dialog, clear the app's data in Android's settings, or uninstall the app, which deletes all of it. For anything you cannot do yourself, write to the address above.
<!-- /shared-about privacy section -->
