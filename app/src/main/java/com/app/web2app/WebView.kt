package com.app.web2app

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Context.DOWNLOAD_SERVICE
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.view.ViewGroup
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewSection() {

    fun Context.getActivityOrNull(): Activity? {
        var context = this
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return null
    }

    fun handleSpecialLinks(context: Context, url: String): Boolean {
        return when {
            url.startsWith("tel:") || url.startsWith("sms:") || url.startsWith("mailto:") -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
                true
            }
            url.startsWith("intent://") -> {
                val fallbackUrl = Uri.decode(url).substringAfter("intent://").substringBefore("#")
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://$fallbackUrl"))
                context.startActivity(intent)
                true
            }
            else -> false
        }
    }


    val context = LocalContext.current

    val mUrl = context.getString(R.string.start_url)
    //val mUrl = "https://google.com"

    var loading by rememberSaveable {
        mutableStateOf(true)
    }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var webView: WebView? by remember {
        mutableStateOf(null)
    }
    val state = rememberPullToRefreshState()
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = {
        scope.launch {
            isRefreshing = true
            webView?.reload()
            delay(1000L)
            isRefreshing = false
        }
    },
        state = state
    ) {

        Column(
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            AndroidView(factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.loadsImagesAutomatically = true

                    webViewClient = object : WebViewClient() {

                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            loading = true
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            loading = false
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?, request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url.toString()
                            return handleSpecialLinks(context, url)
                        }
                    }
                    webChromeClient = WebChromeClient()

                    settings.setSupportZoom(true);
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    loadUrl(mUrl)
                }.also {
                    webView = it
                    it.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                        val request = DownloadManager.Request(Uri.parse(url))
                        val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)
                        request.setTitle(filename)
                        request.addRequestHeader("User-Agent", userAgent);
                        request.setDescription("Downloading file...")
                        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        request.setDestinationInExternalFilesDir(
                            context,
                            Environment.DIRECTORY_DOWNLOADS,
                            filename
                        )

                        val dm = context.getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                        dm.enqueue(request)
                        Toast.makeText(context, "Downloading...", Toast.LENGTH_LONG).show()
                    }

                }
            }, modifier = Modifier.fillMaxSize(), update = {
                webView = it
            })
        }
    }

    if (loading) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }

    BackHandler {
        if (webView?.canGoBack() == true) {
            webView!!.goBack()
        } else {
            context.getActivityOrNull()?.finish()
        }
    }
}