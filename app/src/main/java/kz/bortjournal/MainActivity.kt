package kz.bortjournal

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingFileText: String? = null

    private val pickFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        fileCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        fileCallback = null
    }

    private val createFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val text = pendingFileText
        pendingFileText = null
        if (uri != null && text != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                Toast.makeText(this, "Файл сохранён", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Не удалось сохранить файл", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private var pendingAction: String? = null
    private var pageReady = false

    private val pickBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            AutoBackup.setUri(this, uri)
            AutoBackup.schedule(this, Storage.load(this), 0)
            Toast.makeText(this, "Автокопия включена", Toast.LENGTH_SHORT).show()
            web.postDelayed({ web.evaluateJavascript("window.__autoBackupChanged && window.__autoBackupChanged()", null) }, 1200)
        }
    }

    private fun runPendingAction() {
        val a = pendingAction ?: return
        if (!pageReady) return
        pendingAction = null
        web.evaluateJavascript("window.__action && window.__action(" + org.json.JSONObject.quote(a) + ")", null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("action")?.let { pendingAction = it; runPendingAction() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannel(this)

        web = WebView(this)
        web.setBackgroundColor(0xFF0E1828.toInt())
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = true
        web.settings.textZoom = 100
        web.addJavascriptInterface(Bridge(), "Android")

        pendingAction = intent?.getStringExtra("action")
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                pageReady = true
                view.postDelayed({ runPendingAction() }, 300)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.scheme == "file") return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, url))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this@MainActivity, "Нет приложения, чтобы открыть ссылку", Toast.LENGTH_SHORT).show()
                }
                return true
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                try {
                    pickFile.launch("*/*")
                } catch (e: Exception) {
                    fileCallback = null
                    return false
                }
                return true
            }
        }

        setContentView(web)
        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else web.loadUrl("file:///android_asset/index.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("(window.__back && window.__back()) ? 1 : 0") { r ->
                    if (r != "1") finish()
                }
            }
        })

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    inner class Bridge {
        @JavascriptInterface
        fun load(): String = Storage.load(this@MainActivity)

        @JavascriptInterface
        fun save(json: String) {
            Storage.save(this@MainActivity, json)
            AutoBackup.schedule(this@MainActivity, json)
        }

        @JavascriptInterface
        fun autoBackupInfo(): String = AutoBackup.info(this@MainActivity)

        @JavascriptInterface
        fun setupAutoBackup() {
            runOnUiThread {
                try {
                    pickBackup.launch("bortjournal-autobackup.json")
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Не удалось открыть выбор места", Toast.LENGTH_LONG).show()
                }
            }
        }

        @JavascriptInterface
        fun disableAutoBackup() = AutoBackup.disable(this@MainActivity)

        @JavascriptInterface
        fun autoBackupNow(json: String) = AutoBackup.schedule(this@MainActivity, json, 0)

        @JavascriptInterface
        fun schedule(json: String) = Scheduler.replaceAll(this@MainActivity, json)

        @JavascriptInterface
        fun saveFile(name: String, text: String) {
            runOnUiThread {
                pendingFileText = text
                try {
                    createFile.launch(name)
                } catch (e: Exception) {
                    pendingFileText = null
                    Toast.makeText(this@MainActivity, "Не удалось открыть сохранение", Toast.LENGTH_LONG).show()
                }
            }
        }

        @JavascriptInterface
        fun testNotify() {
            Notifier.show(this@MainActivity, 999_999, "Бортжурнал", "Уведомления работают")
        }
    }
}
