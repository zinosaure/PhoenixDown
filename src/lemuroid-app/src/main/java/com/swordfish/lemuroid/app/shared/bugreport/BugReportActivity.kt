package com.swordfish.lemuroid.app.shared.bugreport

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.swordfish.lemuroid.BuildConfig
import com.swordfish.lemuroid.R

/**
 * Activity que muestra el formulario de reporte de bugs en un WebView.
 * Evita la dependencia del navegador externo y los problemas de redirección de Google.
 */
class BugReportActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorView: TextView
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    
    private val fileChooserLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uris = when {
                result.resultCode != Activity.RESULT_OK -> null
                result.data?.clipData != null -> {
                    // Múltiples archivos seleccionados
                    val clipData = result.data!!.clipData!!
                    Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                }
                result.data?.data != null -> {
                    // Un solo archivo seleccionado
                    arrayOf(result.data!!.data!!)
                }
                else -> null
            }
            fileUploadCallback?.onReceiveValue(uris)
            fileUploadCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            SystemBarStyle.dark(Color.TRANSPARENT),
            SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        
        // Crear layout programáticamente
        val container = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#1a1a2e")) // Match del formulario
        }
        
        // Progress bar
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                8
            ).apply {
                topMargin = (24 * resources.displayMetrics.density).toInt() // ~24dp
            }
            isIndeterminate = false
            max = 100
        }
        
        // Error view
        errorView = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER
            }
            setTextColor(Color.WHITE)
            textSize = 16f
            visibility = View.GONE
            text = getString(R.string.bug_report_error)
        }
        
        // WebView
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        
        container.addView(webView)
        container.addView(progressBar)
        container.addView(errorView)
        
        setContentView(container)
        
        setupWebView()
        loadBugReportForm()
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
            }
            
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                showError()
            }
        }
        
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                progressBar.progress = newProgress
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                }
            }
            
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback
                
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                
                try {
                    fileChooserLauncher.launch(
                        Intent.createChooser(intent, getString(R.string.bug_report_select_images))
                    )
                } catch (e: Exception) {
                    fileUploadCallback?.onReceiveValue(null)
                    fileUploadCallback = null
                    return false
                }
                
                return true
            }
        }
    }
    
    private fun loadBugReportForm() {
        val baseUrl = "https://script.google.com/macros/s/AKfycbxgLtUJcImGpEz0TdPtZZ852DjxdwxzsJ0GT1CjsMHdqErJ-BrNDh1O-RLjHmU5oyhhNg/exec"
        val fullUrl = "$baseUrl?app=PhoenixDown&version=${BuildConfig.VERSION_NAME}"
        webView.loadUrl(fullUrl)
    }
    
    private fun showError() {
        webView.visibility = View.GONE
        errorView.visibility = View.VISIBLE
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
    
    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
