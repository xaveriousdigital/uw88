package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.imageLoader
import coil.load
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

data class WallpaperItem(
    val id: Int,
    val title: String,
    val subtitle: String,
    @DrawableRes val imageResId: Int,
    val imageUrl: String? = null
)

class MainActivity : AppCompatActivity() {

    companion object {
        // Ganti URL ini dengan alamat situs update wallpaper terbaru uw88 Anda (HTTPS)
        const val LATEST_WALLPAPER_URL = "https://xaverious.digital/uw88/"
    }

    private lateinit var btnInfo: MaterialButton
    private lateinit var btnShareApp: MaterialButton
    private lateinit var btnMasuk: MaterialButton
    private lateinit var rvWallpapers: RecyclerView
    private lateinit var webView: WebView
    private lateinit var webProgressBar: ProgressBar
    private lateinit var webErrorLayout: View
    private lateinit var btnRetryWeb: MaterialButton
    private lateinit var btnBackToGallery: MaterialButton

    private var isWebViewVisible = false
    private var hasWebLoadError = false
    private var lastDownloadId: Long = -1L
    private var pendingDownloadWallpaper: WallpaperItem? = null

    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            val wallpaper = pendingDownloadWallpaper
            pendingDownloadWallpaper = null
            if (isGranted && wallpaper != null) {
                startDownload(wallpaper)
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.toast_storage_permission_required),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val completedId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
            if (completedId != -1L && completedId == lastDownloadId) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.toast_download_complete),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private val wallpapers = listOf(
        WallpaperItem(
            id = 1,
            title = "Mahjong Ways",
            subtitle = "PG Soft • Emas & Giok",
            imageResId = R.drawable.img_wp_mahjong_ways_1795176136781
        ),
        WallpaperItem(
            id = 2,
            title = "Fortune Tiger",
            subtitle = "PG Soft • Penjaga Bulan",
            imageResId = R.drawable.img_wp_fortune_tiger_1795176137203
        ),
        WallpaperItem(
            id = 3,
            title = "Treasures of Aztec",
            subtitle = "PG Soft • Kuil Emas",
            imageResId = R.drawable.img_wp_aztec_treasure_1795176137058
        ),
        WallpaperItem(
            id = 4,
            title = "Wild Bandito",
            subtitle = "PG Soft • Mariachi Emas",
            imageResId = R.drawable.img_wp_wild_bandito_1795176137539
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val rootLayout = findViewById<View>(R.id.rootLayout)
        val headerLayout = findViewById<View>(R.id.headerLayout)
        btnInfo = findViewById(R.id.btnInfo)
        btnShareApp = findViewById(R.id.btnShareApp)
        btnMasuk = findViewById(R.id.btnMasuk)
        rvWallpapers = findViewById(R.id.rvWallpapers)
        webView = findViewById(R.id.webView)
        webProgressBar = findViewById(R.id.webProgressBar)
        webErrorLayout = findViewById(R.id.webErrorLayout)
        btnRetryWeb = findViewById(R.id.btnRetryWeb)
        btnBackToGallery = findViewById(R.id.btnBackToGallery)

        applyEdgeToEdgeInsets(rootLayout, headerLayout)
        setupRecyclerView()
        setupWebView()
        setupHeaderActions()
        setupBackNavigation()

        ContextCompat.registerReceiver(
            this,
            downloadCompleteReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        unregisterReceiver(downloadCompleteReceiver)
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    private fun applyEdgeToEdgeInsets(rootLayout: View, headerLayout: View) {
        val headerBaseTop = headerLayout.paddingTop
        val rvBaseBottom = rvWallpapers.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            headerLayout.updatePadding(top = headerBaseTop + insets.top)
            rvWallpapers.updatePadding(bottom = rvBaseBottom + insets.bottom)
            webView.updatePadding(bottom = insets.bottom)
            webErrorLayout.updatePadding(bottom = insets.bottom)
            windowInsets
        }
    }

    private fun setupRecyclerView() {
        rvWallpapers.layoutManager = GridLayoutManager(this, 2)
        rvWallpapers.adapter = WallpaperAdapter(
            items = wallpapers,
            onPreviewClick = { wallpaper -> showWallpaperPreviewDialog(wallpaper) },
            onDownloadClick = { wallpaper -> handleDownloadWallpaper(wallpaper) },
            onApplyClick = { wallpaper -> showWallpaperTargetDialog(wallpaper) }
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url ?: return false
                val scheme = url.scheme?.lowercase(Locale.ROOT)
                return if (scheme == "https") {
                    false
                } else {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, url))
                    } catch (_: Exception) {
                    }
                    true
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                hasWebLoadError = false
                if (isWebViewVisible) {
                    webErrorLayout.visibility = View.GONE
                    webView.visibility = View.VISIBLE
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                webProgressBar.visibility = View.GONE
                if (isWebViewVisible && !hasWebLoadError) {
                    webErrorLayout.visibility = View.GONE
                    webView.visibility = View.VISIBLE
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true && isWebViewVisible) {
                    hasWebLoadError = true
                    webProgressBar.visibility = View.GONE
                    webView.visibility = View.GONE
                    webErrorLayout.visibility = View.VISIBLE
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (isWebViewVisible) {
                    if (newProgress < 100) {
                        webProgressBar.visibility = View.VISIBLE
                        webProgressBar.progress = newProgress
                    } else {
                        webProgressBar.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun setupHeaderActions() {
        btnInfo.setOnClickListener { showAppInfoDialog() }
        btnShareApp.setOnClickListener { shareApplication() }
        btnMasuk.setOnClickListener {
            if (isWebViewVisible) {
                showGallery()
            } else {
                showWebView()
            }
        }
        btnRetryWeb.setOnClickListener {
            hasWebLoadError = false
            webErrorLayout.visibility = View.GONE
            webView.visibility = View.VISIBLE
            webProgressBar.visibility = View.VISIBLE
            webView.reload()
        }
        btnBackToGallery.setOnClickListener { showGallery() }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    isWebViewVisible && !hasWebLoadError && webView.canGoBack() -> webView.goBack()
                    isWebViewVisible -> showGallery()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    private fun showWebView() {
        isWebViewVisible = true
        hasWebLoadError = false
        rvWallpapers.visibility = View.GONE
        webErrorLayout.visibility = View.GONE
        webView.visibility = View.VISIBLE
        webProgressBar.visibility = View.VISIBLE
        webProgressBar.progress = 15
        webView.loadUrl(LATEST_WALLPAPER_URL)

        btnMasuk.text = getString(R.string.btn_kembali_galeri)
        btnMasuk.setIconResource(R.drawable.ic_arrow_back)
        val goldLight = ContextCompat.getColor(this, R.color.accent_gold_light)
        btnMasuk.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.card_dark)
        )
        btnMasuk.setTextColor(goldLight)
        btnMasuk.iconTint = ColorStateList.valueOf(goldLight)
        btnMasuk.strokeWidth = resources.displayMetrics.density.toInt().coerceAtLeast(1)
        btnMasuk.strokeColor = ColorStateList.valueOf(goldLight)
    }

    private fun showGallery() {
        isWebViewVisible = false
        hasWebLoadError = false
        webView.stopLoading()
        webView.visibility = View.GONE
        webErrorLayout.visibility = View.GONE
        webProgressBar.visibility = View.GONE
        rvWallpapers.visibility = View.VISIBLE

        btnMasuk.text = getString(R.string.btn_masuk)
        btnMasuk.setIconResource(R.drawable.ic_globe)
        val onGold = ContextCompat.getColor(this, R.color.on_gold)
        btnMasuk.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.accent_gold)
        )
        btnMasuk.setTextColor(onGold)
        btnMasuk.iconTint = ColorStateList.valueOf(onGold)
        btnMasuk.strokeWidth = 0
    }

    private fun showAppInfoDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.info_dialog_title, BuildConfig.VERSION_NAME))
            .setMessage(getString(R.string.info_dialog_message))
            .setPositiveButton(getString(R.string.btn_tutup), null)
            .show()
    }

    private fun shareApplication() {
        val shareText = getString(R.string.share_app_message, packageName)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_app_chooser_title)))
    }

    private fun showWallpaperPreviewDialog(wallpaper: WallpaperItem) {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_wallpaper_preview, null)
        val ivPreview = dialogView.findViewById<ImageView>(R.id.ivPreviewImage)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvPreviewTitle)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvPreviewSubtitle)
        val btnShare = dialogView.findViewById<MaterialButton>(R.id.btnPreviewShare)
        val btnUnduh = dialogView.findViewById<MaterialButton>(R.id.btnPreviewUnduh)
        val btnPasang = dialogView.findViewById<MaterialButton>(R.id.btnPreviewPasang)

        tvTitle.text = wallpaper.title
        tvSubtitle.text = wallpaper.subtitle
        ivPreview.load(wallpaper.imageUrl ?: wallpaper.imageResId) {
            crossfade(true)
            placeholder(R.drawable.placeholder_wallpaper)
            error(R.drawable.placeholder_wallpaper)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnShare.setOnClickListener { shareWallpaperImage(wallpaper) }
        btnUnduh.setOnClickListener { handleDownloadWallpaper(wallpaper) }
        btnPasang.setOnClickListener {
            dialog.dismiss()
            showWallpaperTargetDialog(wallpaper)
        }

        dialog.show()
    }

    private fun shareWallpaperImage(wallpaper: WallpaperItem) {
        val imageData: Any = wallpaper.imageUrl ?: wallpaper.imageResId
        lifecycleScope.launch {
            try {
                val uri = withContext(Dispatchers.IO) {
                    val request = ImageRequest.Builder(this@MainActivity)
                        .data(imageData)
                        .allowHardware(false)
                        .build()
                    val result = imageLoader.execute(request)
                    val bitmap = (result as? SuccessResult)?.drawable?.toBitmap()
                        ?: throw IllegalStateException("Gagal memuat gambar")

                    val shareDir = File(cacheDir, "shared_wallpapers")
                    if (!shareDir.exists()) shareDir.mkdirs()
                    val file = File(shareDir, "uw88_share_${wallpaper.id}.jpg")
                    FileOutputStream(file).use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    FileProvider.getUriForFile(
                        this@MainActivity,
                        "${packageName}.fileprovider",
                        file
                    )
                }

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(
                        Intent.EXTRA_TEXT,
                        getString(R.string.share_wallpaper_message, wallpaper.title, wallpaper.subtitle)
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(
                    Intent.createChooser(
                        shareIntent,
                        getString(R.string.share_wallpaper_chooser_title, wallpaper.title)
                    )
                )
            } catch (_: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.toast_share_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showWallpaperTargetDialog(wallpaper: WallpaperItem) {
        val options = arrayOf(
            getString(R.string.wallpaper_target_home),
            getString(R.string.wallpaper_target_lock),
            getString(R.string.wallpaper_target_both)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dialog_wallpaper_target_title))
            .setItems(options) { _, which ->
                val flags = when (which) {
                    0 -> WallpaperManager.FLAG_SYSTEM
                    1 -> WallpaperManager.FLAG_LOCK
                    else -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                }
                applyWallpaperWithCoil(wallpaper, flags)
            }
            .show()
    }

    private fun handleDownloadWallpaper(wallpaper: WallpaperItem) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val permissionState = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            if (permissionState != PackageManager.PERMISSION_GRANTED) {
                pendingDownloadWallpaper = wallpaper
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        startDownload(wallpaper)
    }

    private fun startDownload(wallpaper: WallpaperItem) {
        val sanitizedTitle = wallpaper.title
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        val fileName = "uw88_${sanitizedTitle}_${wallpaper.id}.jpg"

        val remoteUrl = wallpaper.imageUrl
        if (remoteUrl != null) {
            try {
                val request = DownloadManager.Request(Uri.parse(remoteUrl)).apply {
                    setTitle(wallpaper.title)
                    setDescription(getString(R.string.downloading_wallpaper_desc))
                    setMimeType("image/jpeg")
                    setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(true)
                    setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        "uw88/$fileName"
                    )
                }

                val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                lastDownloadId = downloadManager.enqueue(request)

                Toast.makeText(
                    this,
                    getString(R.string.toast_downloading, wallpaper.title),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    getString(R.string.toast_download_failed, e.localizedMessage ?: "Error"),
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }

        Toast.makeText(
            this,
            getString(R.string.toast_downloading, wallpaper.title),
            Toast.LENGTH_SHORT
        ).show()

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    saveBundledWallpaperToDownloads(wallpaper.imageResId, fileName)
                }
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.toast_download_complete),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.toast_download_failed, e.localizedMessage ?: "Error"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun saveBundledWallpaperToDownloads(@DrawableRes resId: Int, fileName: String) {
        val bitmap = BitmapFactory.decodeResource(resources, resId)
            ?: throw IllegalStateException("Gagal membaca gambar wallpaper")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "image/jpeg")
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/uw88"
                    )
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Gagal membuat file di Downloads/uw88")

                try {
                    contentResolver.openOutputStream(uri)?.use { output ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
                    } ?: throw IllegalStateException("Gagal membuka stream output")

                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                } catch (e: Exception) {
                    contentResolver.delete(uri, null, null)
                    throw e
                }
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "uw88"
                )
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val targetFile = File(downloadsDir, fileName)
                FileOutputStream(targetFile).use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
                }
                MediaScannerConnection.scanFile(
                    this,
                    arrayOf(targetFile.absolutePath),
                    arrayOf("image/jpeg"),
                    null
                )
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun applyWallpaperWithCoil(wallpaper: WallpaperItem, whichFlags: Int) {
        Toast.makeText(
            this,
            getString(R.string.toast_setting_wallpaper, wallpaper.title),
            Toast.LENGTH_SHORT
        ).show()

        val imageData: Any = wallpaper.imageUrl ?: wallpaper.imageResId
        lifecycleScope.launch {
            try {
                val request = ImageRequest.Builder(this@MainActivity)
                    .data(imageData)
                    .allowHardware(false)
                    .build()

                val result = imageLoader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = result.drawable.toBitmap()
                    withContext(Dispatchers.IO) {
                        val wallpaperManager = WallpaperManager.getInstance(this@MainActivity)
                        wallpaperManager.setBitmap(bitmap, null, true, whichFlags)
                    }
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.toast_wallpaper_success, wallpaper.title),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.toast_wallpaper_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (_: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.toast_wallpaper_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private class WallpaperAdapter(
        private val items: List<WallpaperItem>,
        private val onPreviewClick: (WallpaperItem) -> Unit,
        private val onDownloadClick: (WallpaperItem) -> Unit,
        private val onApplyClick: (WallpaperItem) -> Unit
    ) : RecyclerView.Adapter<WallpaperAdapter.WallpaperViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WallpaperViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_wallpaper, parent, false)
            return WallpaperViewHolder(view)
        }

        override fun onBindViewHolder(holder: WallpaperViewHolder, position: Int) {
            holder.bind(items[position], onPreviewClick, onDownloadClick, onApplyClick)
        }

        override fun getItemCount(): Int = items.size

        class WallpaperViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val ivWallpaper: ImageView = itemView.findViewById(R.id.ivWallpaper)
            private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
            private val tvSubtitle: TextView = itemView.findViewById(R.id.tvSubtitle)
            private val btnUnduh: MaterialButton = itemView.findViewById(R.id.btnUnduh)
            private val btnPasang: MaterialButton = itemView.findViewById(R.id.btnPasang)

            fun bind(
                item: WallpaperItem,
                onPreviewClick: (WallpaperItem) -> Unit,
                onDownloadClick: (WallpaperItem) -> Unit,
                onApplyClick: (WallpaperItem) -> Unit
            ) {
                tvTitle.text = item.title
                tvSubtitle.text = item.subtitle
                ivWallpaper.load(item.imageUrl ?: item.imageResId) {
                    crossfade(true)
                    placeholder(R.drawable.placeholder_wallpaper)
                    error(R.drawable.placeholder_wallpaper)
                }
                ivWallpaper.setOnClickListener { onPreviewClick(item) }
                btnUnduh.setOnClickListener { onDownloadClick(item) }
                btnPasang.setOnClickListener { onApplyClick(item) }
            }
        }
    }
}
