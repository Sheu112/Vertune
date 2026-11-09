package app.vitune.android.ui.screens.settings

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.vitune.android.R
import app.vitune.android.ui.components.themed.SecondaryTextButton
import app.vitune.android.utils.semiBold
import app.vitune.core.ui.LocalAppearance
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val LOGIN_URL = "https://discord.com/login"

// Discord hides window.localStorage from page scripts, but a fresh iframe still exposes it.
// The token is stored as a JSON string, so it comes back wrapped in quotes.
private const val READ_TOKEN_JS = """
(function() {
  try {
    var frame = document.createElement('iframe');
    frame.style.display = 'none';
    document.body.appendChild(frame);
    var token = frame.contentWindow.localStorage.getItem('token');
    frame.remove();
    return token || '';
  } catch (e) { return ''; }
})();
"""

/**
 * Shows Discord's own login page (so passwords, 2FA and captchas all work as usual) and, once
 * the user is logged in, reads the account token from the page and hands it to [onToken].
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DiscordLoginDialog(
    onDismiss: () -> Unit,
    onToken: (String) -> Unit
) = Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
) {
    val [colorPalette, typography] = LocalAppearance.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageLoaded by remember { mutableStateOf(false) }

    // Poll the page for the token; Discord sets it right after a successful login.
    LaunchedEffect(webView, pageLoaded) {
        val view = webView ?: return@LaunchedEffect
        if (!pageLoaded) return@LaunchedEffect
        while (true) {
            val raw = suspendCancellableCoroutine { cont ->
                view.evaluateJavascript(READ_TOKEN_JS) { cont.resume(it.orEmpty()) }
            }
            val token = raw.trim('"').removeSurrounding("\\\"").trim()
            if (token.length > 20 && token != "null") {
                onToken(token)
                return@LaunchedEffect
            }
            delay(1000)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Don't leave the Discord web session lying around in the app's WebView storage.
            webView?.destroy()
            CookieManager.getInstance().removeAllCookies(null)
            WebStorage.getInstance().deleteAllData()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorPalette.background0)
            .systemBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            BasicText(
                text = stringResource(R.string.discord_login),
                style = typography.m.semiBold,
                modifier = Modifier.weight(1f)
            )
            SecondaryTextButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss
            )
        }

        AndroidView(
            factory = { context ->
                // Start from a clean slate so an old, rejected session isn't reused.
                CookieManager.getInstance().removeAllCookies(null)
                WebStorage.getInstance().deleteAllData()

                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            pageLoaded = false
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            pageLoaded = true
                        }
                    }
                    loadUrl(LOGIN_URL)
                    webView = this
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}
