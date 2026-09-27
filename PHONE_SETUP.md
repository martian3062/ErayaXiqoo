# Setting up the iQOO 15 for ERAYA

Step by step, starting from a phone fresh out of the box. Plan on **about 45 minutes**, plus model downloads (~1.7 GB).

> OriginOS menu names change between versions. Each step gives the **Settings search word** (🔍), so you can find the option even if the menu path is different.

---

## Before you start

- [ ] Phone charged above 60%, charger nearby
- [ ] Fast Wi-Fi, or a hotspot from your own phone (the venue Wi-Fi may be slow)
- [ ] Optional: laptop with a USB-C cable and `adb`

---

## 1. First boot

1. Finish the setup wizard. A Google account is optional.
2. Connect to Wi-Fi.
3. Check that it's the right device:
   Settings → **About phone** → processor should say **Snapdragon 8 Elite Gen 5**.
   🔍 `about phone`
4. Install pending system updates only if they're small. Skip big OS updates during the event.

## 2. Turn on Developer options

1. Settings → About phone → **Version** (or *Software version*).
   🔍 `software version`
2. Tap **Software version** 7 times and enter your PIN.
   You'll see *"You are now in developer mode"*.

## 3. Developer options settings

🔍 `developer options` (usually under *System* or *Additional settings*)

Turn **on**:

- [ ] **USB debugging**
- [ ] **Install via USB**: lets the laptop install APKs. It may ask you to sign in to a vivo account.
- [ ] **Stay awake**: the screen stays on while charging
- [ ] **Disable child process restrictions**, if the option is there. It stops Android killing the Termux servers.

## 4. Connect the laptop (optional)

On the laptop:

```bash
adb devices
```

1. Tap **Allow** on the phone's *"Allow USB debugging?"* prompt. Tick *Always allow*.
2. `adb devices` should now show the phone as `device`, not `unauthorized`.
3. If there's no *Disable child process restrictions* option in step 3, run this instead:

   ```bash
   adb shell settings put global \
     settings_enable_monitor_phantom_procs false
   ```

**Office Kit:** install it on the laptop from `pc.vivoglobal.com` and connect the phone by USB or QR code. Use it for file transfer and screen mirroring. It also counts toward the device-usage score.

## 5. Install Termux + Termux:API

Both apps must come from **GitHub**, not the Play Store, or they can't work together.

1. Open in Chrome:
   **github.com/termux/termux-app/releases**
   → download `termux-app_v0.118.3+github-debug_arm64-v8a.apk`
2. Open:
   **github.com/termux/termux-api/releases**
   → download `termux-api-app_v0.53.0+github.debug.apk`
3. Open each APK from the download notification.
   - Allow **Install unknown apps** for Chrome when asked.
   - If a security scan warns you, tap **Install anyway**.

## 6. Set up Termux

Open **Termux** and run these one at a time:

```bash
termux-setup-storage
```

Tap **Allow** for file access.

```bash
pkg install -y git
git clone https://github.com/martian3062/ErayaXiqoo
bash ErayaXiqoo/termux/setup.sh
```

`setup.sh` installs the tools and builds llama.cpp and whisper.cpp on the phone. It takes **10–20 minutes**; keep Termux open and the screen on.

## 7. Get the models (~1.7 GB)

**Option A: download on the phone**

```bash
cd ~/models
wget -O ggml-base-q5_1.bin \
 https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin
wget -O qwen3.5-2b-q4_0.gguf \
 https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/main/Qwen3.5-2B-Q4_0.gguf
```

**Option B: copy from the laptop.** This is faster on slow Wi-Fi.

Put both files in the phone's **Download** folder (Office Kit or `adb push file /sdcard/Download/`), then in Termux:

```bash
cp ~/storage/downloads/ggml-base-q5_1.bin ~/models/
cp ~/storage/downloads/Qwen3.5-2B-Q4_0.gguf \
   ~/models/qwen3.5-2b-q4_0.gguf
```

Check:

```bash
ls -lh ~/models
```

## 8. Start the engines

```bash
bash ~/ErayaXiqoo/termux/run_servers.sh
```

- If Android asks to let Termux **ignore battery optimisation**, tap **Allow**.
- Wait until **both** servers print a line with **listening** and `127.0.0.1:8081` / `:8082`.
- Leave this Termux session running. To keep working, swipe from the left edge → **New session**.

## 9. Install ERAYA

Pick one:

- **From GitHub (phone only):** repo → **Actions** → latest green **build-apk** run → **eraya-debug-apk** → unzip → open `app-debug.apk`.
- **From the laptop:**

  ```bash
  adb install -r app-debug.apk
  ```

  If it fails with `INSTALL_FAILED_USER_RESTRICTED`, turn on **Install via USB** (step 3), or tap *Install* on the phone's prompt.

On first launch, allow the **Microphone** and **Notifications**.

## 10. Stop OriginOS killing the apps

Do all of these for **both ERAYA and Termux**:

- [ ] **Battery:** allow high background power use / *No restrictions*
      🔍 `background power consumption` or `battery`
- [ ] **Autostart:** on
      🔍 `autostart`
- [ ] **Lock in recents:** open the recent-apps view, then long-press or pull down on the app card and tap the 🔒 lock
- [ ] **Pause app activity if unused:** off (long-press the app icon → App info)

ERAYA's top-right gear → **Demo tools** → **Allow background activity** opens the right screen.

## 11. Smoke test

1. Open ERAYA. Both lines at the top should show **● ready**:
   - `ASR whisper.cpp·CPU`
   - `LLM llama.cpp·CPU·qwen3.5-2b-q4_0`
2. Tap 🎙️, read the demo script (`demo/script_en_hi.md`) out loud, then tap ⏹.
3. You should get **3 proposals**, each with an evidence quote.
4. ✓ ✓ ✗ → the **Tasks** tab shows 2 items. Open one → *Calendar* opens the calendar pre-filled.

## 12. Airplane-mode test

1. Pull down the shade → **Airplane mode ON**.
2. The top badge should read **OFFLINE · CPU** (or **OFFLINE · NPU** once the NPU engines land).
3. Repeat the smoke test **3 times in a row** with no failure.
4. Also try with the **screen off**: start recording, lock the phone for 60 s, unlock, stop.

## 13. Before going on stage

- [ ] Charged above 80%, and close every other app
- [ ] **Do Not Disturb** on
- [ ] Screen timeout → 10 min (🔍 `screen timeout`)
- [ ] Brightness up, font size normal
- [ ] Termux servers running, ERAYA shows ● ready
- [ ] Backup ready: top-right gear → **Demo tools** → **Use sample recording** works
- [ ] Airplane mode ON, on camera

---

## If something breaks

**`adb devices` shows "unauthorized"**
Unplug, replug, and accept the prompt on the phone. Still stuck? Developer options → *Revoke USB debugging authorizations*, then try again.

**ERAYA shows "✕ llama-server not ready"**
Termux was killed. Tap **Open Termux**, run `run_servers.sh` again, then tap **Retry** in ERAYA. After that, redo step 10.

**Servers die when the screen turns off**
Check step 10 and the child-process setting in step 3. `run_servers.sh` already holds a wake lock; you should see a Termux notification saying so.

**`setup.sh` fails partway**
Run it again. It skips the parts already done.

**Out of storage**
`ls -lh ~/models`, then delete any extra `.gguf` files.

**Live mic fails on stage**
Top-right gear → **Demo tools** → **Use sample recording**. It runs the same pipeline on a saved WAV.
