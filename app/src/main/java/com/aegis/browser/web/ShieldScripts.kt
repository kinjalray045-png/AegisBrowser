package com.aegis.browser.web

import org.json.JSONArray
import org.json.JSONObject

/**
 * JavaScript injected at document start into every page.
 *
 * The script is deliberately defensive: every step is wrapped in try/catch so a
 * broken page can never break the browser, and the behaviour is controlled by a
 * single runtime flag so the user can toggle the shield without reloading.
 */
object ShieldScripts {

    fun documentStart(
        enabled: Boolean,
        allowlist: Set<String>,
        siteLock: Boolean,
        lockedSite: String
    ): String {
        val allowJson = JSONArray(allowlist.toList()).toString()
        val lockJson = JSONObject.quote(lockedSite)
        return """
(function () {
  try {
    var ALLOW = $allowJson;
    var ON = $enabled;
    var LOCK = $siteLock;
    window.__aegisAllow = ALLOW;
    window.__aegisSiteLock = LOCK;
    window.__aegisLockKey = $lockJson;

    function hostAllowed() {
      try {
        var h = (location.hostname || '').toLowerCase();
        if (!h) return false;
        var list = window.__aegisAllow || ALLOW;
        for (var i = 0; i < list.length; i++) {
          var a = list[i];
          if (!a) continue;
          if (h === a || h.slice(-(a.length + 1)) === '.' + a) return true;
        }
      } catch (e) {}
      return false;
    }
    window.__aegisHostAllowed = hostAllowed;
    window.__aegisShieldOn = !!ON && !hostAllowed();

    // Site Lock: the registrable domain the user pinned this tab to. A host
    // belongs to the site when it is the site itself or a subdomain of it.
    function hostInSite(host, site) {
      try {
        host = (host || '').toLowerCase().replace(/^www\./, '');
        site = (site || '').toLowerCase().replace(/^www\./, '');
        if (!site || !host) return true;
        return host === site || host.slice(-(site.length + 1)) === '.' + site;
      } catch (e) {
        return true;
      }
    }
    window.__aegisHostInSite = hostInSite;

    function sameSiteHref(href) {
      if (!href) return true;
      var raw = String(href).trim();
      if (!raw || raw.charAt(0) === '#') return true;
      if (/^javascript:/i.test(raw)) return true;
      var url;
      try { url = new URL(raw, location.href); } catch (e) { return true; }
      // blob:/data:/about: stay inside this tab and this origin.
      if (url.protocol === 'blob:' || url.protocol === 'data:' || url.protocol === 'about:') return true;
      if (url.protocol !== 'http:' && url.protocol !== 'https:') return false;
      return hostInSite(url.hostname, window.__aegisLockKey);
    }
    window.__aegisSameSiteHref = sameSiteHref;

    if (window.__aegisShieldInstalled) return;
    window.__aegisShieldInstalled = true;

    var realOpen = window.open;
    var guardedOpen = function (url) {
      if (window.__aegisSiteLock) {
        var target = '';
        try {
          target = url ? new URL(String(url), location.href).href : '';
        } catch (e) {
          target = String(url || '');
        }
        // Same-site pop-ups open in this tab; cross-site ones are dropped.
        if (target && sameSiteHref(target)) {
          try { location.href = target; } catch (e) {}
          return null;
        }
        try { if (window.AegisLock && target) window.AegisLock.blocked(target); } catch (e) {}
        try { console.log('Aegis Site Lock: pop-up blocked'); } catch (e) {}
        return null;
      }
      if (window.__aegisShieldOn) {
        try { console.log('Aegis: pop-up blocked'); } catch (e) {}
        return null;
      }
      try { return realOpen.apply(window, arguments); } catch (e) { return null; }
    };
    try {
      Object.defineProperty(window, 'open', {
        value: guardedOpen,
        writable: false,
        configurable: false
      });
    } catch (e) {
      try { window.open = guardedOpen; } catch (e2) {}
    }

    function killRefresh() {
      if (!window.__aegisShieldOn && !window.__aegisSiteLock) return;
      try {
        var metas = document.querySelectorAll('meta[http-equiv]');
        for (var i = 0; i < metas.length; i++) {
          var v = (metas[i].getAttribute('http-equiv') || '').toLowerCase();
          if (v === 'refresh') {
            metas[i].setAttribute('content', '999999');
            if (metas[i].parentNode) metas[i].parentNode.removeChild(metas[i]);
          }
        }
      } catch (e) {}
    }

    var pending = false;
    function scheduleKill() {
      if (pending) return;
      pending = true;
      setTimeout(function () { pending = false; killRefresh(); }, 150);
    }

    // Site Lock runs first and independently of the shield: a tap that lands on
    // a hidden ad overlay is cancelled before the page can navigate. Then the
    // shield keeps target=_blank taps in this tab.
    document.addEventListener('click', function (e) {
      var el = e.target;
      while (el && (!el.tagName || el.tagName.toUpperCase() !== 'A')) {
        el = el.parentElement;
      }
      if (!el) return;

      if (window.__aegisSiteLock) {
        var href = el.getAttribute('href') || '';
        if (href && !sameSiteHref(href)) {
          e.preventDefault();
          e.stopPropagation();
          try {
            var abs = new URL(href, location.href).href;
            if (window.AegisLock) window.AegisLock.blocked(abs);
          } catch (err) {}
          try { console.log('Aegis Site Lock: blocked link to another site'); } catch (err) {}
          return;
        }
        var lockTarget = (el.getAttribute('target') || '').toLowerCase();
        if (lockTarget && lockTarget !== '_self' &&
            lockTarget !== '_top' && lockTarget !== '_parent') {
          el.setAttribute('target', '_self');
        }
      }

      if (!window.__aegisShieldOn) return;
      var t = (el.getAttribute('target') || '').toLowerCase();
      if (t && t !== '_self' && t !== '_top' && t !== '_parent') {
        el.setAttribute('target', '_self');
      }
    }, true);

    killRefresh();
    document.addEventListener('DOMContentLoaded', killRefresh);
    try {
      var head = document.head || document.documentElement;
      if (head) {
        new MutationObserver(scheduleKill).observe(head, { childList: true, subtree: true });
      }
    } catch (e) {}
  } catch (e) {}
})();
        """.trimIndent()
    }

    /**
     * Pushes shield and Site Lock settings into an already-loaded page. The
     * document-start script reads the window.__aegis* globals dynamically, so
     * no re-registration is needed when the user toggles either feature.
     */
    fun runtimeConfig(
        enabled: Boolean,
        allowlist: Set<String>,
        siteLock: Boolean,
        lockedSite: String
    ): String {
        val allowJson = JSONArray(allowlist.toList()).toString()
        val lockJson = JSONObject.quote(lockedSite)
        return """
(function () {
  try {
    window.__aegisAllow = $allowJson;
    window.__aegisSiteLock = $siteLock;
    window.__aegisLockKey = $lockJson;
    var hostAllowed = window.__aegisHostAllowed;
    window.__aegisShieldOn = $enabled && !(hostAllowed ? hostAllowed() : false);
  } catch (e) {}
})();
        """.trimIndent()
    }

    fun doNotTrack(enabled: Boolean): String = """
(function () {
  try {
    window.__aegisDntOn = $enabled;
    if (!window.__aegisDntInstalled) {
      window.__aegisDntInstalled = true;
      Object.defineProperty(navigator, 'doNotTrack', {
        get: function () { return window.__aegisDntOn ? '1' : null; },
        configurable: true
      });
      Object.defineProperty(navigator, 'globalPrivacyControl', {
        get: function () { return !!window.__aegisDntOn; },
        configurable: true
      });
    }
  } catch (e) {}
})();
    """.trimIndent()

    fun dntRuntime(enabled: Boolean): String = "window.__aegisDntOn = $enabled;"

    fun blobFetcher(blobUrl: String, token: String, name: String): String = """
(function () {
  try {
    fetch(${JSONObject.quote(blobUrl)}).then(function (r) { return r.blob(); }).then(function (b) {
      var fr = new FileReader();
      fr.onload = function () {
        try { AegisBlob.save(${JSONObject.quote(token)}, fr.result, b.type || 'application/octet-stream', ${JSONObject.quote(name)}); } catch (e) {}
      };
      fr.readAsDataURL(b);
    }).catch(function () {});
  } catch (e) {}
})();
    """.trimIndent()
}
