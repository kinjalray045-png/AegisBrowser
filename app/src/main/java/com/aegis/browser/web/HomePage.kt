package com.aegis.browser.web

import com.aegis.browser.core.SearchEngine
import com.aegis.browser.core.Urls

/**
 * The native new-tab page. It is generated in Kotlin so quick links can come
 * from the user's own history and bookmarks, and it never makes a network
 * request of its own.
 */
object HomePage {

    private val fallbackLinks = listOf(
        "https://en.wikipedia.org/" to "Wikipedia",
        "https://www.youtube.com/" to "YouTube",
        "https://github.com/" to "GitHub",
        "https://www.reddit.com/" to "Reddit",
        "https://news.ycombinator.com/" to "Hacker News",
        "https://www.bbc.com/news" to "BBC News",
        "https://www.google.com/maps" to "Maps",
        "https://www.spotify.com/" to "Spotify"
    )

    fun render(
        engine: SearchEngine,
        links: List<Pair<String, String>>,
        shieldOn: Boolean
    ): String {
        val source = links.take(8).toMutableList()
        if (source.size < 8) {
            val seen = source.map { it.first }.toMutableSet()
            fallbackLinks.forEach { link ->
                if (source.size < 8 && seen.add(link.first)) source.add(link)
            }
        }
        val tiles = source.joinToString("") { (url, title) ->
            val name = esc(title.trim().ifEmpty { Urls.displayHost(url) })
            val letter = esc(Urls.letterFor(title, url))
            val href = esc(url)
            """<a class="tile" href="$href"><span class="letter">$letter</span><span class="name">$name</span></a>"""
        }

        val shieldClass = if (shieldOn) "on" else "off"
        val shieldLabel = if (shieldOn) {
            "Shield on &middot; pop-ups and redirects blocked"
        } else {
            "Shield off &middot; pop-ups and redirects allowed"
        }

        return """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light dark">
<title>New tab</title>
<style>
  :root {
    color-scheme: light dark;
    --bg: #f7f9fb;
    --bg2: #eef7f8;
    --fg: #0f1720;
    --muted: #5b6b78;
    --card: #ffffff;
    --line: #dbe4ea;
    --brand: #0e7490;
    --brand-soft: #cffafe;
    --brand-fg: #083344;
    --shadow: 0 10px 30px rgba(15, 23, 32, .08);
  }
  @media (prefers-color-scheme: dark) {
    :root {
      --bg: #0b0f13;
      --bg2: #0d1a1e;
      --fg: #e5eaf0;
      --muted: #9fb0bd;
      --card: #151c22;
      --line: #26313a;
      --brand: #22d3ee;
      --brand-soft: #123c45;
      --brand-fg: #cffafe;
      --shadow: 0 10px 30px rgba(0, 0, 0, .45);
    }
  }
  * { box-sizing: border-box; }
  html, body { height: 100%; }
  body {
    margin: 0;
    background: radial-gradient(120% 80% at 50% 0%, var(--bg2), var(--bg) 60%);
    color: var(--fg);
    font-family: -apple-system, "Segoe UI", Roboto, system-ui, sans-serif;
    display: flex;
    align-items: flex-start;
    justify-content: center;
    padding: 12vh 20px 40px;
  }
  .wrap { width: 100%; max-width: 560px; }
  .brand { display: flex; flex-direction: column; align-items: center; margin-bottom: 26px; }
  .mark {
    width: 58px; height: 58px; border-radius: 18px;
    background: var(--brand); display: grid; place-items: center;
    box-shadow: var(--shadow);
  }
  .mark svg { width: 34px; height: 34px; display: block; }
  h1 { margin: 14px 0 2px; font-size: 30px; letter-spacing: -0.03em; font-weight: 650; }
  .tag { margin: 0; color: var(--muted); font-size: 13px; letter-spacing: .02em; }

  form {
    display: flex; align-items: center; gap: 10px;
    background: var(--card);
    border: 1px solid var(--line);
    border-radius: 999px;
    padding: 4px 8px 4px 16px;
    box-shadow: var(--shadow);
  }
  form svg { width: 18px; height: 18px; flex: 0 0 auto; color: var(--muted); }
  input {
    flex: 1; min-width: 0; border: 0; outline: 0; background: transparent;
    color: var(--fg); font-size: 15px; padding: 12px 0;
  }
  input::placeholder { color: var(--muted); }
  button {
    border: 0; border-radius: 999px; background: var(--brand); color: var(--brand-fg);
    font-weight: 600; font-size: 13px; padding: 9px 16px; cursor: pointer;
  }

  .tiles {
    margin-top: 26px;
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 10px;
  }
  .tile {
    display: flex; flex-direction: column; align-items: center; gap: 7px;
    padding: 12px 4px;
    border-radius: 16px;
    text-decoration: none; color: var(--fg);
    background: var(--card);
    border: 1px solid var(--line);
  }
  .tile:active { transform: scale(.97); }
  .letter {
    width: 38px; height: 38px; border-radius: 12px;
    background: var(--brand-soft); color: var(--brand-fg);
    display: grid; place-items: center; font-weight: 700; font-size: 16px;
  }
  .name {
    font-size: 11px; color: var(--muted); max-width: 100%;
    overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  }

  .shield {
    margin-top: 30px; display: flex; align-items: center; gap: 9px;
    justify-content: center; text-decoration: none;
    font-size: 12px; color: var(--muted);
  }
  .shield svg { width: 16px; height: 16px; }
  .shield.on svg { color: var(--brand); }
  .shield.off svg { color: var(--muted); }
  .dot { width: 4px; height: 4px; border-radius: 50%; background: var(--muted); }
  .foot { margin-top: 8px; text-align: center; font-size: 11px; color: var(--muted); opacity: .8; }
  @media (max-width: 380px) {
    .tiles { grid-template-columns: repeat(3, 1fr); }
    h1 { font-size: 26px; }
  }
</style>
</head>
<body>
  <div class="wrap">
    <div class="brand">
      <div class="mark">
        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M12 2.2 20 5.6v5.9c0 4.9-3.4 9.5-8 10.6-4.6-1.1-8-5.7-8-10.6V5.6L12 2.2Z" fill="#ffffff"/>
          <path d="m8.4 11.9 2.4 2.4 4.9-5 1.5 1.5-6.4 6.4-3.9-3.8 1.5-1.5Z" fill="#0e7490"/>
        </svg>
      </div>
      <h1>Aegis</h1>
      <p class="tag">Private. Fast. Yours.</p>
    </div>

    <form id="searchForm" autocomplete="off">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
        <circle cx="11" cy="11" r="7"></circle>
        <path d="m20 20-3.2-3.2"></path>
      </svg>
      <input id="q" type="text" enterkeyhint="go" placeholder="Search with ${esc(engine.label)} or type a URL" aria-label="Search or type a URL">
      <button type="submit">Go</button>
    </form>

    <div class="tiles">
      $tiles
    </div>

    <a class="shield $shieldClass" href="https://aegis.home/shield"
       onclick="if(window.AegisHome&amp;&amp;AegisHome.shield){AegisHome.shield();return false;}">
      <svg viewBox="0 0 24 24" fill="currentColor" xmlns="http://www.w3.org/2000/svg">
        <path d="M12 1 3 5v6c0 5.6 3.8 10.7 9 12 5.2-1.3 9-6.4 9-12V5l-9-4Z"/>
      </svg>
      <span>$shieldLabel</span>
    </a>
    <div class="foot">Aegis Browser &middot; Android System WebView</div>
  </div>
<script>
  (function () {
    var form = document.getElementById('searchForm');
    var input = document.getElementById('q');
    form.addEventListener('submit', function (event) {
      event.preventDefault();
      var value = input.value.trim();
      if (!value) return;
      if (window.AegisHome && AegisHome.search) {
        AegisHome.search(value);
      } else {
        window.location.href = 'https://aegis.home/search?q=' + encodeURIComponent(value);
      }
    });
  })();
</script>
</body>
</html>
        """.trimIndent()
    }

    private fun esc(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
