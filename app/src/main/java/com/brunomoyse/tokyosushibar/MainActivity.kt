package com.brunomoyse.tokyosushibar

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

    override fun onCreate(savedInstanceState: Bundle?) {
        WebView.setWebContentsDebuggingEnabled(true)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val email = BuildConfig.DEFAULT_EMAIL
        val password = BuildConfig.DEFAULT_PASSWORD

        Log.d(TAG, "Email: $email, Password: $password")

        webView = findViewById(R.id.webView)
        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true

        webSettings.cacheMode = WebSettings.LOAD_NO_CACHE
        webView.clearCache(true)

        webView.webChromeClient = WebChromeClient()

        // Load your dashboard URL
        webView.loadUrl("https://admin.nuagemagique.dev/orders")

        // Autofill and simulate login after login page loads
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
                    
                                emailField.focus();
                                passwordField.focus();
                                
                                // Small timeout before clicking the button to let the form react
                                setTimeout(() => {
                                    submitBtn.click();
                                    console.log("✅ Login form submitted");
                                }, 0);
                    
                                submitBtn.click();
                                console.log("✅ Form filled and submitted immediately.");
                            } else {
                                console.log("❌ One or more elements not found. Script ran without retry.");
                            }
                    
                            return "🚀 Login script executed once";
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
}