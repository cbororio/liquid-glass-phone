# Liquid Glass Phone & Messages

Two Android apps with an iOS 26 "liquid glass" look, built with Jetpack Compose:

- **Phone** (`com.tonny.dial`): a full default dialer app
- **Messages** (`com.tonny.messages`): a full default SMS/MMS app

## Download

Grab the latest `Phone.apk` and `Messages.apk` from the [Releases page](../../releases/latest).

1. Download the APK(s) on your Android phone (Android 10 or newer).
2. Open the file and allow "Install unknown apps" for your browser/file manager if asked.
3. If Play Protect warns about an unknown developer, tap **More details → Install anyway**.
4. Open the app and accept the prompt to make it your default Phone / SMS app.

## Features

### Phone
- Recents grouped by day, with live contact names
- Contacts, search and keypad
- Glass in-call screen, incoming calls over the lock screen
- Decline an incoming call with a quick text reply
- Missed-call notification with Call back / Message actions
- Block and unblock numbers (system blocked-numbers list)
- Dual-SIM "Call with" picker

### Messages
- SMS and MMS: photos, camera, voice notes, documents, group chats
- OTP codes highlighted in notifications with a Copy code button
- M-PESA transaction summary in notifications
- Automatic betting/promo spam filter, silent blocked senders
- Reply and Mark as read from the notification
- Delivery reports, timestamps, resend/copy/select/delete
- Dual-SIM picker

## Note for MIUI / HyperOS (Xiaomi, Redmi, POCO)

To show incoming calls over the lock screen, open **Settings → Apps → Phone → Other permissions** and allow
"Show on lock screen" and "Display pop-up windows while running in the background".

## Building from source

Requires Android Studio (or JDK 17+) and the Android SDK.

```bash
./gradlew :app:assembleRelease :messages:assembleRelease
```

Without a `keystore.properties` file the release builds are signed with the debug key.
