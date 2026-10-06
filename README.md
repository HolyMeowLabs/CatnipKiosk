# CatnipKiosk

Catnip Kiosk keeps your TV or tablet screen hooked on one web page.

CatnipKiosk is an Android app for Android TV, Google TV and tablets. It shows a single web page full screen, such as a dashboard, a menu board or a status page, and keeps viewers on it.

## Features

- **One page, full screen.** System bars are hidden and the screen stays on.
- **Navigation you control.** Allow only the start page, or the whole site (optionally with subdomains and a few extra sites such as a sign-in page). Anything else is blocked, and no other app is ever opened.
- **Recovers on its own.** If the page or the network fails, it shows "Reconnecting…" and retries. It can also reload on a schedule.
- **Admin settings behind a PIN.** On TV, press Up Up Down Down Left Right Left Right on the remote. On a tablet, tap the top-left corner five times.
- **Remote-control cursor** for pages that need pointing on a TV.
- **Two levels of lockdown:**
  - **Soft:** make CatnipKiosk the Home app, so Home returns to the kiosk.
  - **Hard (tablets):** device-owner mode blocks Home, Recents and notifications.

## Install

CatnipKiosk will be available on Google Play. The link will be added here once it is live.

## Hard lockdown

Hard lockdown makes CatnipKiosk the device owner. It needs a device with **no accounts**, which usually means a factory reset where you skip adding accounts, plus USB debugging and a computer with [adb](https://developer.android.com/tools/adb).

1. Install CatnipKiosk and finish its setup.
2. On the computer, run:

   ```
   adb shell dpm set-device-owner com.holymeowlabs.catnipkiosk/.lockdown.KioskDeviceAdminReceiver
   ```

3. Reopen CatnipKiosk. It locks itself in.
4. Turn USB debugging off again. Left on, anyone with a cable and a computer could take the device out of the kiosk.

To undo it, open settings with the secret gesture and your PIN, then choose **Remove hard lockdown**. After that the app can be uninstalled normally.

> **If you forget the PIN while hard lockdown is on, the only way out is a factory reset, which erases the device.**

## Privacy

CatnipKiosk collects nothing. See the [privacy policy](docs/privacy.md).
