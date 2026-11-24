# Aegis Browser

A full-featured Android browser built on **Android System WebView**, with one headline feature: the **Aegis Shield**, an on/off switch that stops a page from opening pop-ups or redirecting you to other websites, links or URLs.

Everything runs locally on the device. There is no account, no sync service and no analytics.

## Highlights

### Aegis Shield (the main feature)

The shield button in the top-left corner (or **Menu → Shield**) opens the shield panel:

- **Master switch** — turn all protection on/off instantly, without reloading the page.
- **Block pop-ups** — stops `window.open()`, `target="_blank"` pop-ups and WebView `onCreateWindow` requests.
- **Block redirects** — stops automatic cross-site navigation, including JavaScript redirects and `<meta http-equiv="refresh">`.
- **Block app links** — stops a page from launching `intent:`, `market:`, `tel:`, `mailto:` and similar app schemes without a tap.
- **Per-site switch** — turn protection off for just the site you are on.
- **Allow list** — permanently exempt sites you trust.
- **Counters and recent blocks** — see what was stopped, with an **Allow** action on the block notification.

**User taps still work.** The shield distinguishes a real user gesture from an automatic navigation, and keeps a short "gesture budget" so a link that goes through a search engine, short-link service or OAuth redirect chain still reaches its destination. Same-site navigation is never blocked.

The shield has two layers:

1. A document-start JavaScript shim (WebViews that support it use `WebViewCompat.addDocumentStartJavaScript`) that wraps `window.open`, rewrites `target="_blank"` links and removes meta refresh tags.
2. Native navigation interception in `WebViewClient.shouldOverrideUrlLoading` and `WebChromeClient.onCreateWindow`, so blocked navigations never leave the current page.

If document-start scripts are unavailable, the shim is re-injected on page start as a fallback.

### Normal browser features

- Multiple tabs with live previews, incognito tabs, tab limit and restore after restart
- Address bar with local suggestions from history and bookmarks (nothing is sent to a suggestion service)
- Six search engines, plus a custom homepage
- Back/forward, reload/stop, home, bookmark toggle, share
- Find in page
- Bookmarks, history and downloads (including blob/data URL downloads)
- Context menu on links and images: open in new tab, copy, share, save image, download
- File upload, camera/microphone and location permission prompts
- Fullscreen video
- Desktop mode, adjustable text size, JavaScript/images/cookies toggles, Do Not Track
- Safe Browsing and SSL warning dialogs
- Error page with retry
- Light, dark and system themes
- Share targets: opens `http(s)` links, web search intents and shared text
- Swipe to refresh

## Project layout

```
app/src/main/java/com/aegis/browser/
├── BrowserActivity.kt        main UI, intents, downloads, permissions, fullscreen
├── SettingsActivity.kt       programmatic settings screen
├── AegisApp.kt               application setup
├── core/                     Prefs, URL helpers, JSON stores
├── ui/                       tabs, sheets, adapters, find bar, suggestions
└── web/                      shield, WebView factory, clients, home page, downloads
```

## Building

Requirements:

- JDK 17
- Android SDK Platform 35 and Build Tools
- Android Studio Ladybug or newer (optional)

Command line:

```bash
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Building without a local Android toolchain

This repository includes a GitHub Actions workflow at `.github/workflows/build.yml`.
Push the folder to a new GitHub repository (or use the repository root as the project root), then:

1. Open the **Actions** tab.
2. Run the **Build APK** workflow (it also runs on every push).
3. Download the `AegisBrowser-debug-apk` artifact.
4. Unzip it and install `app-debug.apk` on an Android 7.0+ device.

## Notes

- Minimum Android version is 7.0 (API 24); the build targets Android 15 (API 35).
- The app uses the device's WebView implementation. WebView version is shown in **Settings → About**.
- The shield is a navigation guard, not an ad blocker or a sandbox. It cannot inspect encrypted traffic or stop a page from changing its own content without navigating.
- Releases are unsigned debug builds unless you add your own signing configuration.
