# Arc Comic Post Generator — Android App

Lightweight Android wrapper for the Arc Comic HTML post builder.

## What Got Fixed
- **Blocks no longer vanish** when you add new blocks or switch fields. Every keystroke live-updates the data model. No more "Save" buttons per block.
- **Files actually save to your phone**. Uses Android's Storage Access Framework (file picker) so you choose exactly where HTML and project files go.
- **Auto-save** writes `autosave.json` to your chosen folder every 4 seconds. Crash or close the app accidentally? Reopen and your work is back.
- **Sidebar shows real files** from your chosen folder — tap any `.html` or `.json` file to load it instantly.

## Build APK without PC (GitHub Actions)
1. Create a new **private** repo on GitHub
2. Upload **all files in this folder** to that repo (drag & drop on GitHub website)
3. Go to **Actions** tab → **Build APK** → wait ~3 minutes
4. Download the APK from the workflow artifacts

### Or push from Termux (on your phone)
```bash
pkg install git
gh auth login   # or use token
cd arc-comic-android
git init
git add .
git commit -m "initial"
git remote add origin https://github.com/YOURNAME/arc-comic-android.git
git push -u origin main
```

## Install on Android 11 (Redmi Note 9 Pro)
1. Download APK from GitHub Actions artifact
2. Tap APK → Install → allow "Install unknown apps" if asked
3. Open app → it asks you to pick a folder (choose `Documents/ArcComic` or similar)
4. Done. Every HTML/JSON you generate lands in that folder, visible in any file manager.

## App Permissions
- **Storage** — so it can write HTML/JSON to your chosen folder
- **Internet** — only for previewing remote images; the app works fully offline

## Notes
- The app is a WebView wrapper around the post generator UI. All generation logic runs locally on your phone.
- If you ever want to change the save folder, tap the purple banner at the top of the builder and pick a new one.
