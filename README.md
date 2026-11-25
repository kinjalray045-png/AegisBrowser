# Aegis Browser

A full-featured Android browser built on **Android System WebView**, with two headline features: the **Aegis Shield**, which stops a page from opening pop-ups or redirecting you to other websites, and **Site Lock**, a separate strict switch that keeps a tab on its current site no matter what.

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

**User taps still work.** The shield distinguishes a real user gesture from an automatic navigation, and keeps a short "gesture budget" so a link that goes through a search engine, short-link service or OAuth redirect chain still reaches its destination. Same-site navigation is never blocked. If you want taps to be stopped too, use Site Lock below.

### Site Lock (strict "never leave this site")

Site Lock is a second, independent switch for a stricter promise: **this tab will not leave the site it is on, no matter what** — even when the navigation comes from a real tap.

It exists for pages that hide transparent ad overlays on top of their controls. When you try to pause a video or change a player setting, the tap lands on an invisible ad link instead. With Site Lock on, that tap is cancelled.

- Turn it on from **Menu → Site Lock**, the switch at the top of the shield panel, or **Settings → Privacy and security**.
- It locks the tab to the site it is on at that moment (registrable domain, so `m.example.com` and `www.example.com` count as the same site).
- While locked, cross-site links, address-bar and bookmark navigation to another site, pop-ups, redirects, meta refreshes, app links and `window.open` are all refused.
- Same-site navigation, Back/Forward and reload still work. Back/Forward moves the lock to the page you land on.
- Every block shows **Site Lock kept you on example.com** with an **Unlock** action. Unlock does not follow the blocked link.
- Going to the Home page clears the lock target so the next site you open becomes the new target.
- Site Lock works independently of the shield: either one can be on without the other.

The strict mode has three layers, in order:

1. A capture-phase click handler in the document-start script cancels taps on links that point to another site, before the page's own handlers can react.
2. Native interception in `WebViewClient.shouldOverrideUrlLoading` cancels cross-site main-frame navigations (including those started by JavaScript or a server redirect) and app schemes.
3. A page-start backstop stops a cross-site load that somehow slipped through, and restores the last page on the locked site.

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
- The shield is a navigation guard, not an ad blocker or a sandbox. It cannot inspect encrypted traffic or stop a page from changing its own content without navigating. Site Lock likewise stops *leaving* a site; it does not remove an overlay that stays on the same site.
- Releases are unsigned debug builds unless you add your own signing configuration.
