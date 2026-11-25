/*
 * Zhihu-Hyperion - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 * Co-author: eltavine <me@eltavine.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.zly2006.zhihu.account

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.core.app.NotificationManagerCompat
import com.github.zly2006.zhihu.data.AccountData
import com.github.zly2006.zhihu.ui.components.AppLoadingIndicator
import com.github.zly2006.zhihu.ui.components.CustomWebView
import com.github.zly2006.zhihu.ui.components.WebviewComp
import com.github.zly2006.zhihu.ui.components.setupUpWebviewClient
import io.ktor.client.HttpClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.mp.KoinPlatform
import java.util.TimeZone

actual val supportedLoginMethods: List<LoginMethod> = listOf(
    LoginMethod.Phone,
    LoginMethod.Qr,
    LoginMethod.Web,
)

actual val isLoginRiskControlSupported: Boolean = true

internal actual val smsCodeImeOptions: PlatformImeOptions? = null

@Composable
actual fun rememberLoginHttpClient(cookies: MutableMap<String, String>): HttpClient {
    val accountStore = koinInject<ZhihuAccountStore>()
    val httpClient = remember(accountStore) { accountStore.client.temporaryHttpClient(cookies) }
    DisposableEffect(httpClient) {
        onDispose(httpClient::close)
    }
    return httpClient
}

actual val phoneLoginDeviceInfo: ZhihuPhoneLoginDeviceInfo
    get() = KoinPlatform.getKoin().get<Context>().phoneLoginDeviceInfo()

actual fun decodePhoneLoginCaptchaImage(content: String) = runCatching {
    val encoded = content.substringAfter("base64,", content)
    val bytes = Base64.decode(encoded, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()

@Composable
actual fun QrLoginPane(
    onLoginSuccess: (String) -> Unit,
    onUsePhoneLogin: () -> Unit,
) {
    val accountStore = koinInject<ZhihuAccountStore>()
    SharedQrLoginPane(
        onLoginSuccess = { cookies ->
            if (accountStore.login(cookies.toMutableMap())) {
                onLoginSuccess(accountStore.session.username)
                true
            } else {
                false
            }
        },
        onUsePhoneLogin = onUsePhoneLogin,
        initialCookies = AccountData.data.cookies,
    )
}

@Composable
actual fun WebLoginPane(onLoginSuccess: (String) -> Unit) {
    val accountStore = koinInject<ZhihuAccountStore>()
    val scope = rememberCoroutineScope()
    var isVerifying by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<CustomWebView?>(null) }
    DisposableEffect(Unit) {
        onDispose { WebLoginScroll.scrollBy = null }
    }

    fun handleCookies(cookies: Map<String, String>) {
        if (isVerifying) return
        isVerifying = true
        scope.launch {
            try {
                if (accountStore.login(cookies.toMutableMap())) {
                    onLoginSuccess(accountStore.session.username)
                }
            } finally {
                isVerifying = false
            }
        }
    }

    // 扫码成功后网页可能用 pushState 跳到已登录首页，不会触发 onPageFinished；轮询 cookie 兜底。
    LaunchedEffect(webView) {
        if (webView == null) return@LaunchedEffect
        var lastToken: String? = null
        while (isActive) {
            delay(1000)
            val cookies = readWebViewCookies(ZHIHU_HOME_URL)
            val token = cookies["z_c0"]
            if (!token.isNullOrBlank() && token != lastToken) {
                lastToken = token
                handleCookies(cookies)
            }
        }
    }

    WebviewComp(
        modifier = Modifier.fillMaxSize(),
        // 登录页需要固定视口高度，WebView 内部才有可滚动范围；不能让 WebView 按整页高度展开。
        matchContentHeight = false,
        onLoad = { loadedWebView ->
            webView = loadedWebView
            loadedWebView.allowNativeScroll = true
            WebLoginScroll.scrollBy = { dx, dy ->
                loadedWebView.scrollBy(dx, dy)
                true
            }
            configureWebLogin(loadedWebView, ::handleCookies)
        },
    )
}

@Composable
actual fun LoginRiskControlPane(
    url: String,
    cookies: Map<String, String>,
    onCookiesChanged: (Map<String, String>) -> Unit,
) {
    var loading by remember(url) { mutableStateOf(true) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        WebviewComp(
            modifier = Modifier.fillMaxSize(),
            onLoad = { webView ->
                configureRiskControlWebView(
                    webView = webView,
                    url = url,
                    cookies = cookies,
                    onCookiesChanged = onCookiesChanged,
                    onLoadingChanged = { loading = it },
                )
            },
        )
        if (loading) {
            AppLoadingIndicator(Modifier.testTag("qr_risk_control_loading"))
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun configureWebLogin(
    webView: CustomWebView,
    onCookiesReady: (Map<String, String>) -> Unit,
) {
    webView.setupUpWebviewClient()
    webView.settings.javaScriptEnabled = true
    // 二维码登录需要桌面版页面；Android 默认 UA 会拿到移动版，页面没有可扫描的登录二维码。
    webView.settings.userAgentString = ZHIHU_DESKTOP_USER_AGENT
    webView.isFocusable = true
    webView.isFocusableInTouchMode = true
    webView.directionKeyScrollStep = 96
    webView.requestFocus()
    // WebView 默认会把方向键交给表单控件（例如国家区号下拉）；在页面捕获阶段拦截，改成小步滚动。
    val keyScrollScript =
        """
        window.addEventListener('keydown', function (e) {
          var d = 96;
          if (e.key === 'ArrowUp' || e.keyCode === 19) { window.scrollBy(0, -d); e.preventDefault(); e.stopPropagation(); }
          else if (e.key === 'ArrowDown' || e.keyCode === 20) { window.scrollBy(0, d); e.preventDefault(); e.stopPropagation(); }
          else if (e.key === 'ArrowLeft' || e.keyCode === 21) { window.scrollBy(-d, 0); e.preventDefault(); e.stopPropagation(); }
          else if (e.key === 'ArrowRight' || e.keyCode === 22) { window.scrollBy(d, 0); e.preventDefault(); e.stopPropagation(); }
        }, true);
        """.trimIndent()
    webView.settings.useWideViewPort = true
    webView.settings.loadWithOverviewMode = true
    webView.settings.setSupportZoom(true)
    webView.settings.builtInZoomControls = true
    webView.settings.displayZoomControls = false
    webView.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest,
        ): Boolean = request.url?.scheme == "zhihu"

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            // 登录页加载完成后再抢一次焦点，确保方向键进入 WebView 的 setOnKeyListener，而不是 Compose 侧栏。
            webView.post { webView.requestFocus() }
            if (url?.contains("signin") == true) {
                // 桌面登录页在电视的 16:9 可视区里偏高，缩放后二维码才能完整露出。
                webView.evaluateJavascript("document.documentElement.style.zoom='0.7';", null)
                webView.evaluateJavascript(keyScrollScript, null)
            }
            // 扫码成功后知乎会跳到首页的某种变体（可能带 query / 无尾斜杠），不能做精确字符串比较。
            if (isLoggedInHomeUrl(url)) {
                val cookies = readWebViewCookies(url)
                if (!cookies["z_c0"].isNullOrBlank()) onCookiesReady(cookies)
            }
        }
    }
    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
    if (webView.url.isNullOrEmpty()) {
        CookieManager.getInstance().removeAllCookies { webView.loadUrl(ZHIHU_SIGNIN_URL) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun configureRiskControlWebView(
    webView: WebView,
    url: String,
    cookies: Map<String, String>,
    onCookiesChanged: (Map<String, String>) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
) {
    webView.setupUpWebviewClient()
    webView.settings.javaScriptEnabled = true
    webView.settings.userAgentString = ZHIHU_DESKTOP_USER_AGENT
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptThirdPartyCookies(webView, true)
    cookies.forEach { (name, value) ->
        cookieManager.setCookie(ZHIHU_HOME_URL, "$name=$value; Domain=.zhihu.com; Path=/")
    }
    cookieManager.flush()
    webView.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest,
        ): Boolean = request.url?.scheme == "zhihu"

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            onLoadingChanged(true)
        }

        // 验证页的子资源要十几秒才全部加载完（onPageFinished），页面内容早已可见、可以操作。
        override fun onPageCommitVisible(view: WebView?, url: String?) {
            super.onPageCommitVisible(view, url)
            onLoadingChanged(false)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            onLoadingChanged(false)
            onCookiesChanged(readWebViewCookies(url))
        }
    }
    if (webView.url != url) {
        webView.loadUrl(url)
    }
}

private fun isLoggedInHomeUrl(url: String?): Boolean {
    if (url.isNullOrBlank() || url.contains("signin", ignoreCase = true)) return false
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
    return uri.host == "www.zhihu.com"
}

private fun readWebViewCookies(url: String?): Map<String, String> =
    parseCookieAssignments(
        CookieManager.getInstance().getCookie(url ?: ZHIHU_HOME_URL).orEmpty(),
    )

private fun Context.phoneLoginDeviceInfo(): ZhihuPhoneLoginDeviceInfo {
    val runtime = Runtime.getRuntime()
    val storage = StatFs(Environment.getDataDirectory().path)
    val installTime = runCatching {
        packageManager.getPackageInfo(packageName, 0).firstInstallTime
    }.getOrDefault(0L)
    return ZhihuPhoneLoginDeviceInfo(
        timezoneOffsetSeconds = TimeZone.getDefault().rawOffset / 1_000L,
        appInstallTimeMillis = installTime,
        notificationEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled(),
        bluetoothAvailable = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH),
        phoneBrand = Build.BRAND,
        phoneModel = Build.MODEL,
        androidRelease = Build.VERSION.RELEASE,
        cpuType = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
        cpuCount = runtime.availableProcessors(),
        cpuUsage = "0.0",
        totalMemoryMegabytes = (runtime.totalMemory() / 1_048_576L).toInt(),
        freeMemoryMegabytes = (runtime.freeMemory() / 1_048_576L).toInt(),
        totalStorageMegabytes = (storage.totalBytes / 1_048_576L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        freeStorageMegabytes = (storage.availableBytes / 1_048_576L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
    )
}
