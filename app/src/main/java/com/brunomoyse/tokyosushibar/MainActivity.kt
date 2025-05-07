package com.brunomoyse.tokyosushibar

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.Bundle
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.brunomoyse.tokyosushibar.ui.printing.PrintHandler
import com.brunomoyse.tokyosushibar.ui.sound.SoundHandler

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private val TAG = "MainActivity"

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        WebView.setWebContentsDebuggingEnabled(true)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                super.onLost(network)
                Log.w(TAG, "Network lost – closing app")
                runOnUiThread { finish() }
            }
        }
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder().build(),
            networkCallback
        )
        // ────────────────────────────────────────────────────────────────────

        val email = BuildConfig.DEFAULT_EMAIL
        val password = BuildConfig.DEFAULT_PASSWORD
        Log.d(TAG, "Email: $email, Password: $password")

        webView = findViewById(R.id.webView)
        webView.settings.apply {
            javaScriptEnabled = true
            cacheMode        = WebSettings.LOAD_NO_CACHE
        }
        webView.clearCache(true)
        webView.webChromeClient = WebChromeClient()

        webView.loadUrl("https://admin.nuagemagique.dev/orders")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Page finished loading: $url")

                if (url.contains("login")) {
                    Log.d(TAG, "Login page detected, injecting script")
                    val script = """
                        (function() {
                            const emailField = document.querySelector('input[name="email"], input[type="email"]');
                            const passwordField = document.querySelector('input[name="password"], input[type="password"]');
                            const submitBtn = document.querySelector('button[type="submit"], button');
                    
                            if (emailField && passwordField && submitBtn) {
                                emailField.value = "$email";
                                passwordField.value = "$password";
                                emailField.dispatchEvent(new Event('input', { bubbles: true }));
                                passwordField.dispatchEvent(new Event('input', { bubbles: true }));
                                setTimeout(() => {
                                    submitBtn.click();
                                    console.log("Login form submitted");
                                }, 0);
                            } else {
                                console.log("Login form elements not found");
                            }
                        })();
                    """.trimIndent()
                    view.evaluateJavascript(script) { result ->
                        Log.d(TAG, "JS result: $result")
                    }
                }
            }
        }

        webView.addJavascriptInterface(PrintHandler(this), "PrintHandler")
        webView.addJavascriptInterface(SoundHandler(this), "SoundHandler")
    }

    override fun onDestroy() {
        super.onDestroy()
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }
}