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

    fun documentStart(enabled: Boolean, allowlist: Set<String>): String {
        val allowJson = JSONArray(allowlist.toList()).toString()
        return """
(function () {
  try {
    var ALLOW = $allowJson;
    var ON = $enabled;
    function hostAllowed() {
      try {
        var h = (location.hostname || '').toLowerCase();
        if (!h) return false;
        for (var i = 0; i < ALLOW.length; i++) {
          var a = ALLOW[i];
          if (!a) continue;
          if (h === a || h.slice(-(a.length + 1)) === '.' + a) return true;
        }
      } catch (e) {}
      return false;
    }
    window.__aegisShieldOn = !!ON && !hostAllowed();
    if (window.__aegisShieldInstalled) return;
    window.__aegisShieldInstalled = true;

    var realOpen = window.open;
    var guardedOpen = function () {
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
      if (!window.__aegisShieldOn) return;
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

    // A user tap on a target=_blank link should stay in this tab instead of
    // becoming a pop-up window.
    document.addEventListener('click', function (e) {
      if (!window.__aegisShieldOn) return;
      var el = e.target;
      while (el && el.tagName !== 'A') el = el.parentElement;
      if (!el) return;
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

    fun runtimeFlag(enabled: Boolean): String = "window.__aegisShieldOn = $enabled;"

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
