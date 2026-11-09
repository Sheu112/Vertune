package app.vitune.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Shows Discord's own login page (so passwords, 2FA and captchas work as usual) and, once the
 * user is logged in, reads the account token from the page and returns it as the result.
 */
class DiscordLoginActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var webView: WebView
    private var finished = false

    private val pollToken = object : Runnable {
        override fun run() {
            if (finished) return
            webView.evaluateJavascript(READ_TOKEN_JS) { raw ->
                val token = raw.orEmpty().trim().removeSurrounding("\"").removeSurrounding("\\\"").trim()
                if (token.length > 20 && token != "null") onToken(token)
            }
            handler.postDelayed(this, 1000)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start from a clean slate so an old, rejected session isn't reused.
        clearWebData()

        webView = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#313338"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            @Suppress("DEPRECATION")
            settings.databaseEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            // Discord's site misbehaves for the embedded-browser user agent, so look like
            // regular Chrome on Android instead.
            settings.userAgentString = settings.userAgentString
                .replace("; wv", "")
                .replace(Regex("""Version/\S+\s"""), "")
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {}
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    Log.d(TAG, "${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                    return true
                }
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(webView) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setContentView(webView)
        webView.loadUrl(LOGIN_URL)
        handler.postDelayed(pollToken, 1500)
    }

    private fun onToken(token: String) {
        if (finished) return
        finished = true
        setResult(RESULT_OK, Intent().putExtra(EXTRA_TOKEN, token))
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        webView.destroy()
        // Don't leave the Discord web session lying around in the app's WebView storage.
        clearWebData()
        super.onDestroy()
    }

    private fun clearWebData() {
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
    }

    /** Launch with `launcher.launch(Unit)`; the result is the token, or `null` if cancelled. */
    class Contract : ActivityResultContract<Unit, String?>() {
        override fun createIntent(context: Context, input: Unit) =
            Intent(context, DiscordLoginActivity::class.java)

        override fun parseResult(resultCode: Int, intent: Intent?) =
            intent?.getStringExtra(EXTRA_TOKEN)?.takeIf { resultCode == RESULT_OK }
    }

    private companion object {
        const val TAG = "DiscordLogin"
        const val EXTRA_TOKEN = "token"
        const val LOGIN_URL = "https://discord.com/login"

        // Discord hides window.localStorage from page scripts, but a fresh iframe still
        // exposes it. The token is stored as a JSON string, so it comes back quoted.
        const val READ_TOKEN_JS = """
(function() {
  try {
    var token = null;
    try { token = window.localStorage && window.localStorage.getItem('token'); } catch (e) {}
    if (!token && document.body) {
      var frame = document.createElement('iframe');
      frame.style.display = 'none';
      document.body.appendChild(frame);
      token = frame.contentWindow.localStorage.getItem('token');
      frame.remove();
    }
    return token || '';
  } catch (e) { return ''; }
})();
"""
    }
}
