package org.example.spotifydownloader

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.File

class MainActivity : AppCompatActivity() {
    private var selectedDownloadFolder: Uri? = null
    private lateinit var txtSelectedFolder: TextView
    private lateinit var txtCurrentAccount: TextView
    private var autoScrollEnabled = true
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLogger.install(this)
        setContentView(R.layout.activity_main)
        
        // Initialize notifications
        NotificationHelper.createNotificationChannel(this)
        
        UiBridge.log("App started\n")

        // Request storage/media permissions on first launch (best effort)
        val launcher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
            val granted = res.entries.any { it.value }
            UiBridge.log("[perm] Requested media/storage permissions: grantedAny=$granted\n")
        }
        
        // Request notification permission for Android 13+
        val notificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            UiBridge.log("[perm] Notification permission: $granted\n")
            if (!granted) {
                UiBridge.log("⚠️ Notifications disabled - you won't see download progress when app is minimized\n")
            }
        }
        
        // Request battery optimization exemption
        val batteryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            UiBridge.log("[perm] Battery optimization result: ${result.resultCode}\n")
        }
        try {
            val perms = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= 33) {
                perms += Manifest.permission.READ_MEDIA_AUDIO
                perms += Manifest.permission.READ_MEDIA_IMAGES
            } else {
                perms += Manifest.permission.READ_EXTERNAL_STORAGE
                if (Build.VERSION.SDK_INT <= 32) {
                    perms += Manifest.permission.WRITE_EXTERNAL_STORAGE
                }
            }
            if (Build.VERSION.SDK_INT >= 30) {
                perms += Manifest.permission.MANAGE_EXTERNAL_STORAGE
            }
            if (perms.isNotEmpty()) launcher.launch(perms.toTypedArray())
            
            // Request notification permission on Android 13+
            if (Build.VERSION.SDK_INT >= 33) {
                notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            
            // Request battery optimization exemption for background downloads
            try {
                val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = android.net.Uri.parse("package:$packageName")
                    batteryLauncher.launch(intent)
                    UiBridge.log("⚡ Requesting battery optimization exemption for background downloads\n")
                }
            } catch (e: Exception) {
                UiBridge.log("⚠️ Could not request battery optimization exemption: ${e.message}\n")
            }
        } catch (_: Throwable) {}

        val btn = findViewById<Button>(R.id.btnStart)
        val btnSelectFolder = findViewById<Button>(R.id.btnSelectFolder)
        val btnAccounts = findViewById<Button>(R.id.btnAccounts)
        val btnServerConfig = findViewById<Button>(R.id.btnServerConfig)
        val btnExportLogs = findViewById<Button>(R.id.btnExportLogs)
        val prog = findViewById<ProgressBar>(R.id.progress)
        val logView = findViewById<TextView>(R.id.txtLog)
        val logScrollView = findViewById<android.widget.ScrollView>(R.id.logScrollView)
        txtSelectedFolder = findViewById<TextView>(R.id.txtSelectedFolder)
        txtCurrentAccount = findViewById<TextView>(R.id.txtCurrentAccount)
        UiBridge.attach(this, logView)
        
        // Update current account display
        updateAccountDisplay()
        
        // Add scroll lock functionality
        logScrollView.setOnTouchListener { _, _ ->
            // When user manually scrolls, disable auto-scroll temporarily
            autoScrollEnabled = false
            // Re-enable auto-scroll after 3 seconds of no interaction
            logScrollView.postDelayed({
                autoScrollEnabled = true
            }, 3000)
            false
        }
        try {
            val crashFile = File(getExternalFilesDir(null), "last_crash.txt")
            if (crashFile.exists()) {
                UiBridge.log("Found last crash:\n" + crashFile.readText() + "\n")
            }
        } catch (_: Exception) {}

        // Folder selection launcher
        val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                selectedDownloadFolder = uri
                // Persist permission for this URI
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                
                // Update UI to show selected folder
                val docFile = DocumentFile.fromTreeUri(this, uri)
                val folderName = docFile?.name ?: "Selected Folder"
                txtSelectedFolder.text = "📁 Download folder: $folderName"
                UiBridge.log("📁 Selected download folder: $folderName\n")
            }
        }

        // Folder selection button
        btnSelectFolder.setOnClickListener {
            folderPickerLauncher.launch(null)
        }
        
        // Account management button
        btnAccounts.setOnClickListener {
            showAccountManagerDialog()
        }

        // Server configuration button
        btnServerConfig.setOnClickListener {
            showServerConfigDialog()
        }
        
        // Test notification (long press on server config button)
        btnServerConfig.setOnLongClickListener {
            UiBridge.log("🔔 Testing notification...\n")
            NotificationHelper.showDownloadNotification(
                this,
                "🧪 Test Notification",
                "This is a test notification to check if notifications work!"
            )
            
            // Also show background service info
            val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            val batteryOptimized = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                !powerManager.isIgnoringBatteryOptimizations(packageName)
            } else false
            
            UiBridge.log("🔋 Battery optimized: $batteryOptimized\n")
            if (batteryOptimized) {
                UiBridge.log("⚠️ App is battery optimized - background downloads may fail\n")
                UiBridge.log("💡 Go to Settings → Battery → Battery Optimization → SpotifyDownloader → Don't optimize\n")
            } else {
                UiBridge.log("✅ Battery optimization disabled - background downloads should work\n")
            }
            true
        }

        btn.setOnClickListener {
            btn.isEnabled = false
            prog.visibility = ProgressBar.VISIBLE
            
            // Start background service to keep app running
            DownloadService.start(this)
            
            // Show initial notification
            NotificationHelper.showDownloadNotification(
                this,
                "🚀 Starting Download",
                "Initializing Spotify Downloader..."
            )
            
            // Warn about background limitations
            UiBridge.log("📱 IMPORTANT: For best results, keep the app open during downloads\n")
            UiBridge.log("⚠️ Background downloads may fail due to Android restrictions\n")
            UiBridge.log("💡 If downloads stop when minimized, return to the app\n")
            
            Thread {
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(this))
                }
                val py = Python.getInstance()
                try {
                    // Using cloud server for audio conversion - no local FFmpeg needed
                    UiBridge.log("🌐 Using cloud server for audio conversion\n")
                    UiBridge.log("🔄 Checking server availability (Render wake-up)...\n")
                    
                    // Wake up Render server first
                    val healthChecker = py.getModule("server_health_checker")
                    val serverReady = healthChecker.callAttr("ensure_server_ready")
                    
                    if (serverReady.toBoolean()) {
                        UiBridge.log("✅ Server is ready!\n")
                    } else {
                        UiBridge.log("⚠️ Server may be slow to respond (continuing anyway)\n")
                    }
                    
                    val mod = py.getModule("android_entry")
                    val baseDir = getExternalFilesDir(null)?.absolutePath
                    
                    // Pass selected download folder to Python
                    val downloadFolderPath = selectedDownloadFolder?.let { uri ->
                        val docFile = DocumentFile.fromTreeUri(this, uri)
                        docFile?.uri?.toString()
                    }
                    
                    // No FFmpeg path needed - using cloud server
                    val result = mod.callAttr("run", baseDir, null, null, downloadFolderPath)
                    Log.d("APP", "Result: $result")
                } catch (e: Exception) {
                    Log.e("APP", "Python error", e)
                    UiBridge.log("\n[ERROR] ${e.message}\n")
                } finally {
                    runOnUiThread {
                        prog.visibility = ProgressBar.GONE
                        btn.isEnabled = true
                        // Stop background service and hide notifications
                        DownloadService.stop(this@MainActivity)
                        NotificationHelper.hideNotification(this@MainActivity)
                    }
                }
            }.start()
        }

        // Export logs button functionality
        btnExportLogs.setOnClickListener {
            Thread {
                try {
                    val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                    val logContent = logView.text.toString()
                    
                    // Export to external files directory (accessible via file manager)
                    val externalDir = getExternalFilesDir(null)
                    val logFile = File(externalDir, "exported_logs_$timestamp.txt")
                    logFile.writeText(logContent)
                    
                    // Also try to export to a shared location
                    val sharedDir = File("/storage/emulated/0/Download")
                    if (sharedDir.exists()) {
                        val sharedLogFile = File(sharedDir, "SpotifyDownloader_logs_$timestamp.txt")
                        try {
                            sharedLogFile.writeText(logContent)
                            runOnUiThread {
                                UiBridge.log("[LOG EXPORT] Saved to Downloads: ${sharedLogFile.name}\n")
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                UiBridge.log("[LOG EXPORT] Downloads failed: ${e.message}\n")
                            }
                        }
                    }
                    
                    runOnUiThread {
                        UiBridge.log("[LOG EXPORT] Saved to app files: ${logFile.name}\n")
                        UiBridge.log("[LOG EXPORT] Path: ${logFile.absolutePath}\n")
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        UiBridge.log("[LOG EXPORT ERROR] ${e.message}\n")
                    }
                }
            }.start()
        }

        // Auto-start if requested via intent extra (for ADB automation)
        val autoStart = intent?.getBooleanExtra("autoStart", false) ?: false
        if (autoStart) {
            btn.post { btn.performClick() }
        }
    }

    private fun showServerConfigDialog() {
        val dialogView = layoutInflater.inflate(R.layout.server_config_dialog, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.serverRadioGroup)
        val radioCloudServer = dialogView.findViewById<RadioButton>(R.id.radioCloudServer)
        val radioLocalServer = dialogView.findViewById<RadioButton>(R.id.radioLocalServer)
        val editCustomUrl = dialogView.findViewById<EditText>(R.id.editCustomUrl)
        
        // Load current preferences
        val prefs = getSharedPreferences("server_config", MODE_PRIVATE)
        val useCloudServer = prefs.getBoolean("use_cloud_server", true)
        val customUrl = prefs.getString("custom_url", "")
        
        radioCloudServer.isChecked = useCloudServer
        radioLocalServer.isChecked = !useCloudServer
        editCustomUrl.setText(customUrl)
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }
        
        dialogView.findViewById<Button>(R.id.btnSave).setOnClickListener {
            val useCloud = radioCloudServer.isChecked
            val customUrlText = editCustomUrl.text.toString().trim()
            
            // Save preferences
            prefs.edit()
                .putBoolean("use_cloud_server", useCloud)
                .putString("custom_url", customUrlText)
                .apply()
            
            // Update Python configuration
            updatePythonServerConfig(useCloud, customUrlText)
            
            val serverType = if (useCloud) "☁️ Cloud Server" else "🖥️ Local Server"
            UiBridge.log("⚙️ Server updated: $serverType\n")
            if (customUrlText.isNotEmpty()) {
                UiBridge.log("🔗 Custom URL: $customUrlText\n")
            }
            
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun updatePythonServerConfig(useCloudServer: Boolean, customUrl: String) {
        try {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(this))
            }
            val py = Python.getInstance()
            val configModule = py.getModule("converter_config")
            
            // Update the configuration
            configModule.put("USE_CLOUD_SERVER", useCloudServer)
            if (customUrl.isNotEmpty()) {
                if (useCloudServer) {
                    configModule.put("CLOUD_SERVER", customUrl)
                } else {
                    configModule.put("LOCAL_SERVER", customUrl)
                }
            }
            
            UiBridge.log("✅ Python server configuration updated\n")
        } catch (e: Exception) {
            UiBridge.log("❌ Failed to update server config: ${e.message}\n")
        }
    }
    
    private fun updateAccountDisplay() {
        txtCurrentAccount.text = SpotifyAccountManager.getCurrentAccountInfo(this)
    }
    
    private fun showAccountManagerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.account_manager_dialog, null)
        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.accountRecyclerView)
        val editDisplayName = dialogView.findViewById<android.widget.EditText>(R.id.editDisplayName)
        val editClientId = dialogView.findViewById<android.widget.EditText>(R.id.editClientId)
        val editClientSecret = dialogView.findViewById<android.widget.EditText>(R.id.editClientSecret)
        val btnAddAccount = dialogView.findViewById<Button>(R.id.btnAddAccount)
        val btnHelp = dialogView.findViewById<Button>(R.id.btnHelp)
        val btnClose = dialogView.findViewById<Button>(R.id.btnClose)
        
        // Setup RecyclerView
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        val accounts = SpotifyAccountManager.getAccounts(this).toMutableList()
        val activeAccountIndex = SpotifyAccountManager.getAccounts(this).indexOfFirst { 
            it == SpotifyAccountManager.getActiveAccount(this) 
        }
        
        lateinit var adapter: SpotifyAccountAdapter
        adapter = SpotifyAccountAdapter(
            accounts,
            activeAccountIndex,
            onAccountSelected = { index ->
                SpotifyAccountManager.setActiveAccount(this@MainActivity, index)
                updateAccountDisplay()
                adapter.updateAccounts(SpotifyAccountManager.getAccounts(this@MainActivity), index)
            },
            onAccountRemoved = { index ->
                SpotifyAccountManager.removeAccount(this@MainActivity, index)
                updateAccountDisplay()
                val newAccounts = SpotifyAccountManager.getAccounts(this@MainActivity)
                val newActiveIndex = SpotifyAccountManager.getAccounts(this@MainActivity).indexOfFirst { 
                    it == SpotifyAccountManager.getActiveAccount(this@MainActivity) 
                }
                adapter.updateAccounts(newAccounts, newActiveIndex)
            }
        )
        recyclerView.adapter = adapter
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        btnAddAccount.setOnClickListener {
            val displayName = editDisplayName.text.toString().trim()
            val clientId = editClientId.text.toString().trim()
            val clientSecret = editClientSecret.text.toString().trim()
            
            if (displayName.isEmpty() || clientId.isEmpty() || clientSecret.isEmpty()) {
                UiBridge.log("⚠️ Please fill in all account fields\n")
                return@setOnClickListener
            }
            
            val newAccount = SpotifyAccount(
                displayName = displayName,
                clientId = clientId,
                clientSecret = clientSecret
            )
            
            val newIndex = SpotifyAccountManager.addAccount(this, newAccount)
            updateAccountDisplay()
            
            // Clear form
            editDisplayName.text.clear()
            editClientId.text.clear()
            editClientSecret.text.clear()
            
            // Update adapter
            val updatedAccounts = SpotifyAccountManager.getAccounts(this@MainActivity)
            val updatedActiveIndex = SpotifyAccountManager.getAccounts(this@MainActivity).indexOfFirst { 
                it == SpotifyAccountManager.getActiveAccount(this@MainActivity) 
            }
            adapter.updateAccounts(updatedAccounts, updatedActiveIndex)
        }
        
        btnHelp.setOnClickListener {
            showSpotifySetupHelp()
        }
        
        btnClose.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun showSpotifySetupHelp() {
        val helpMessage = """
            🎵 How to Get Spotify API Credentials:
            
            1. Go to https://developer.spotify.com/dashboard
            2. Log in with your Spotify account
            3. Click "Create App"
            4. Fill in:
               • App Name: "My Spotify Downloader"
               • App Description: "Personal music downloader"
               • Redirect URI: http://localhost:8080
            5. Copy your Client ID and Client Secret
            6. Paste them into the form above
            
            💡 Tips:
            • Each person needs their own Spotify Developer account
            • Free Spotify accounts work fine
            • Keep your Client Secret private
            • You can create multiple apps for different users
            
            🔒 Privacy:
            • Credentials are stored locally on this device
            • No data is shared between accounts
            • Each account downloads from their own playlists
        """.trimIndent()
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("📚 Spotify API Setup Help")
            .setMessage(helpMessage)
            .setPositiveButton("✅ Got it") { dialog, _ -> dialog.dismiss() }
            .setNeutralButton("🌐 Open Developer Site") { _, _ ->
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                intent.data = android.net.Uri.parse("https://developer.spotify.com/dashboard")
                startActivity(intent)
            }
            .show()
    }
}
