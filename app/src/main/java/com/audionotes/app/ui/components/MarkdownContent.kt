package com.audionotes.app.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject
import java.io.IOException

/**
 * Рендерер Markdown с поддержкой формул LaTeX.
 *
 * Используется WebView с локальными библиотеками marked.js и MathJax
 * (лежат в assets/renderer) — интернет для отображения не требуется.
 */
@Composable
fun MarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isSystemInDarkTheme()
) {
    val context = LocalContext.current
    val assets = remember(context) {
        runCatching {
            readAsset(context, "renderer/marked.min.js") to readAsset(context, "renderer/tex-svg.js")
        }.getOrNull()
    }

    var rendered by remember { mutableStateOf<String?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                @SuppressLint("SetJavaScriptEnabled")
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.defaultTextEncodingName = "UTF-8"
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        val payload = JSONObject.quote(markdown)
                        view.evaluateJavascript("window.renderMarkdown($payload);", null)
                    }
                }
            }
        },
        update = { webView ->
            val key = "$darkTheme|$markdown"
            if (rendered != key) {
                rendered = key
                val html = assets?.let { (marked, mathJax) -> buildHtml(darkTheme, marked, mathJax) }
                    ?: buildFallbackHtml(darkTheme)
                webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        }
    )
}

private fun readAsset(context: Context, path: String): String =
    try {
        context.assets.open(path).bufferedReader().use { it.readText() }
    } catch (e: IOException) {
        ""
    }

private fun buildHtml(darkTheme: Boolean, markedJs: String, mathJaxJs: String): String {
    val textColor = if (darkTheme) "#E4E1E9" else "#1A1B22"
    val mutedColor = if (darkTheme) "#C8C5D0" else "#47464F"
    val codeBg = if (darkTheme) "#232130" else "#F0EFF7"
    return buildString {
        append("<!DOCTYPE html><html><head>")
        append("<meta charset=\"utf-8\">")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        append("<style>")
        append("body{font-family:-apple-system,Roboto,'Segoe UI',sans-serif;")
        append("color:$textColor;background:transparent;font-size:16px;line-height:1.55;")
        append("margin:0;padding:4px 2px;word-wrap:break-word;}")
        append("h1,h2,h3,h4{line-height:1.3;margin:18px 0 10px;}")
        append("h1{font-size:24px;}h2{font-size:20px;}h3{font-size:18px;}")
        append("p{margin:8px 0;}")
        append("ul,ol{margin:8px 0;padding-left:24px;}")
        append("li{margin:4px 0;}")
        append("code{background:$codeBg;padding:2px 5px;border-radius:4px;font-size:14px;}")
        append("pre{background:$codeBg;padding:12px;border-radius:8px;overflow-x:auto;}")
        append("pre code{background:transparent;padding:0;}")
        append("blockquote{border-left:3px solid $mutedColor;margin:10px 0;padding:2px 12px;color:$mutedColor;}")
        append("strong{color:$textColor;}")
        append("hr{border:none;border-top:1px solid $mutedColor;opacity:0.4;}")
        append("mjx-container{overflow-x:auto;overflow-y:hidden;}")
        append("</style>")
        append("<script>window.MathJax={tex:{inlineMath:[['\\$','\\$'],['\\\\(','\\\\)']],displayMath:[['\\$\\$','\\$\\$'],['\\\\[','\\\\]']]},svg:{fontCache:'none'}};</script>")
        append("<script>").append(markedJs).append("</script>")
        append("<script>").append(mathJaxJs).append("</script>")
        append("</head><body><div id=\"content\"></div>")
        append("<script>")
        append("window.renderMarkdown=function(md){")
        append("try{var el=document.getElementById('content');")
        append("el.innerHTML=(typeof marked!=='undefined')?marked.parse(md):md;")
        append("if(window.MathJax&&MathJax.typesetPromise){MathJax.typesetPromise();}")
        append("}catch(e){document.getElementById('content').textContent=md;}")
        append("};")
        append("</script></body></html>")
    }
}

/** Простой запасной вариант, если assets не читаются. */
private fun buildFallbackHtml(darkTheme: Boolean): String {
    val textColor = if (darkTheme) "#E4E1E9" else "#1A1B22"
    return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">" +
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
        "<style>body{color:$textColor;font-size:16px;line-height:1.5;}</style>" +
        "</head><body><pre id=\"content\"></pre>" +
        "<script>window.renderMarkdown=function(md){document.getElementById('content').textContent=md;};</script>" +
        "</body></html>"
}
