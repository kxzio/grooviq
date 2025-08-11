package com.example.groviq.backEnd.headerTaker

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun YTMusicWebView(
    onHeadersCaptured: (String) -> Unit
) {

    AndroidView(factory = { context ->
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportMultipleWindows(true)

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    if (request != null) {
                        val urlStr = request.url.toString()
                        if (urlStr.contains("/browse") && request.method == "POST") {
                            val headersMap = request.requestHeaders
                            val cookieManager = CookieManager.getInstance()
                            val cookies = cookieManager.getCookie("https://music.youtube.com")

                            if (!cookies.isNullOrEmpty()) {
                                // Формируем headers в формате, который ждёт ytmusicapi
                                val headersString = buildString {
                                    headersMap.forEach { (key, value) ->
                                        append("$key: $value\n")
                                    }
                                    append("cookie: $cookies\n")
                                    append("user-agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:88.0) Gecko/20100101 Firefox/88.0\n")  // Or use the WebView's settings.userAgentString
                                    append("origin: https://music.youtube.com\n")
                                    append("referer: https://music.youtube.com\n")
                                    append("content-type: application/json\n")
                                    append("accept: */*\n")
                                    append("accept-language: en-US,en;q=0.5\n")
                                }
                                view?.post {
                                    onHeadersCaptured(headersString)
                                }
                            }
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }
            }

            loadUrl("https://music.youtube.com")
        }
    })
}