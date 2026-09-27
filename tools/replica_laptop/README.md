# ERAYA laptop portrait tooling

These scripts prepare private portrait assets on a laptop for import into ERAYA on Android. The Android product displays a portrait-only experience with gaze, blink, breathing, head movement, and voice-driven lip motion. It does not expose a 3D viewer or claim a photorealistic head/body scan.

Use only images of the replica owner with their explicit consent. Source photographs, intermediate geometry, and generated packages remain local unless the owner deliberately transfers the final ZIP to the phone.

## Environment

Create an isolated Python environment and install the dependencies required by the selected script. Keep the environment, input photographs, and output directory outside the repository.

Example on Windows:

```powershell
python -m venv .venv-eraya-replica
.\.venv-eraya-replica\Scripts\python.exe -m pip install --upgrade pip
```

## Create a portrait package from a front photo

Use one evenly lit, front-facing image containing only the owner:

```powershell
.\.venv-eraya-replica\Scripts\python.exe `
  tools\replica_laptop\generate_eraya_replica.py `
  --image C:\private\front-photo.jpg
```

The script produces an `ERAYA-replica.zip` package plus local intermediate assets. The fixed facial topology is based on the 468-landmark MediaPipe canonical face model.

## Package a stylized anime portrait

If an owner-approved anime portrait already exists:

```powershell
.\.venv-eraya-replica\Scripts\python.exe `
  tools\replica_laptop\generate_anime_avatar.py `
  --image C:\private\anime-portrait.png `
  --out C:\private\eraya-anime-avatar
```

This creates `ERAYA-anime-avatar.zip` and supporting local assets. Any rear-head, hair, beard, neck, or shoulder geometry is procedural and stylized, not a complete scan.

## Import on Android

1. Transfer only the generated ZIP to the phone.
2. Open **ERAYA -> You -> Portrait Studio**.
3. Confirm identity-photo consent.
4. Tap **Import portrait package** and select the ZIP.
5. ERAYA validates file names, dimensions, sizes, and package structure before replacing the active portrait.

Delete the transferred ZIP and local source photos when they are no longer required.
