# Work ShortCut 🚀

**Work ShortCut** is a productivity powerhouse Android application engineered for ultra-fast workflows:

- 🫧 **Messenger-Style Floating Overlay Bubble**: Draggable anywhere on the screen with quick tactile controls.
- ⚡ **Super-Fast 2FA Authenticator**: Auto-detects 2FA secret keys from clipboard, generates 6-digit TOTP codes with 1 tap, and instantly copies the code back to clipboard.
- 🧹 **Facebook Lite & Multi-App One-Click Clear Data**: Automatically navigates to Storage, checks `Accounts and settings`, confirms the popup with `OK`, and executes full `CLEAR` data via Accessibility.
- 📋 **Multi-Column Sheet Quick Clipboard**: Direct Column A, B, C row pastings with instant duplicate detection.
- 👤 **Instant Fake Name Generator**: Quick localized names for BD, US, UK, and IN.
- 🛡️ **Super Proxy Switcher**: Fast SOCKS5/HTTP profile switcher with VPN routing.

---

## 🤖 Automated CI/CD Pipeline (GitHub Actions)

This repository includes a pre-configured GitHub Actions CI/CD workflow (`.github/workflows/android-build.yml`) that automatically compiles the Android app and generates ready-to-install APKs.

### How to Deploy to your GitHub Repository `Work ShortCut`:

1. **Create the Repository on GitHub**:
   - Go to [GitHub](https://github.com/new) and create a new repository named `Work ShortCut` (or `work-shortcut`).

2. **Push the Code**:
   ```bash
   git init
   git add .
   git commit -m "Initial commit: Work ShortCut with automated CI/CD pipeline"
   git branch -M main
   git remote add origin https://github.com/YOUR_USERNAME/Work-ShortCut.git
   git push -u origin main
   ```

3. **Automatic Build & Download**:
   - As soon as you push your code, GitHub Actions triggers the **"Build Work ShortCut APK"** workflow.
   - Go to your repository's **"Actions"** tab.
   - Select the running workflow.
   - Once completed (marked with a green checkmark ✅), scroll down to the **Artifacts** section.
   - Click on **`WorkShortCut-Debug-APK`** to download the compiled `.apk` file directly!

### Creating a Release Tag:
Pushing a version tag automatically creates a GitHub Release with the APK attached:
```bash
git tag v1.0.0
git push origin v1.0.0
```
Visit the **"Releases"** section on your GitHub repository to download `app-debug.apk` directly.
