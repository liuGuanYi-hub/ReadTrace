package com.example.readtrace

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.readtrace.data.BookDatabaseHelper
import com.example.readtrace.model.Book
import com.example.readtrace.model.BookStatus
import com.example.readtrace.model.MediaType
import com.example.readtrace.util.DoubanClient
import com.example.readtrace.util.HapticFeedbackEngine
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * 📷 离线极速 ISBN 条码扫描 (IsbnScannerEngine)
 *
 * P11 极简心流 Phase 4：
 * - ML Kit Barcode Scanning 纯本地离线模型，零网络延迟、零权限滥用（仅相机）；
 * - 只识别 EAN-13 / EAN-8（978/979 前缀即 ISBN-13）；
 * - 命中后走豆瓣 ISBN 直取管线补齐书名/封面/作者，自动以「想读」状态落库。
 */
class IsbnScannerActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var hintText: TextView
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private var handled = false

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else {
                Toast.makeText(this, "需要相机权限才能扫码", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_isbn_scanner)

        previewView = findViewById(R.id.scannerPreview)
        hintText = findViewById(R.id.scannerHint)
        findViewById<TextView>(R.id.scannerClose).setOnClickListener { finish() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            runCatching {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val options = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8)
                    .build()
                val scanner = BarcodeScanning.getClient(options)
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analyzer ->
                        analyzer.setAnalyzer(analysisExecutor) { imageProxy ->
                            processFrame(scanner, imageProxy)
                        }
                    }

                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }.onFailure {
                Toast.makeText(this, "相机启动失败: ${it.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /** 逐帧识别：本地模型 0.2s 内出结果，命中即锁定 */
    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processFrame(scanner: com.google.mlkit.vision.barcode.BarcodeScanner, imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (handled || mediaImage == null) {
            imageProxy.close()
            return
        }
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val raw = barcodes.firstOrNull()?.rawValue ?: return@addOnSuccessListener
                if (handled) return@addOnSuccessListener
                val isbn = normalizeIsbn(raw) ?: return@addOnSuccessListener
                handled = true
                runOnUiThread { HapticFeedbackEngine.cartridgeSnap(this) }
                fetchAndInsert(isbn)
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    /** EAN-8 不为 ISBN；EAN-13 以 978/979 开头的才是 ISBN-13 */
    private fun normalizeIsbn(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.length == 13 && (digits.startsWith("978") || digits.startsWith("979")) -> digits
            else -> null
        }
    }

    private fun fetchAndInsert(isbn: String) {
        runOnUiThread { hintText.text = "✅ 识别成功 ISBN $isbn\n正在补齐书名与封面…" }
        DoubanClient.getBookByIsbn(isbn) { subject ->
            if (subject != null && subject.displayTitle.isNotBlank()) {
                val databaseHelper = BookDatabaseHelper.getInstance(this)
                val newId = databaseHelper.insertBook(
                    Book(
                        title = subject.displayTitle,
                        author = subject.creator,
                        coverUrl = subject.coverUrl,
                        category = subject.tags.firstOrNull(),
                        tags = subject.tags,
                        status = BookStatus.WISHLIST,
                        mediaType = MediaType.BOOK,
                        startDate = null,
                        finishDate = null,
                        sourceType = DoubanClient.SOURCE_DOUBAN,
                        sourceId = subject.id.toString(),
                        remoteRating = subject.ratingScore,
                        description = subject.summary,
                    ),
                )
                Toast.makeText(
                    this,
                    if (newId > 0) "⚡ 已收录《${subject.displayTitle}》至想读" else "收录失败，请重试",
                    Toast.LENGTH_SHORT,
                ).show()
                finish()
            } else {
                handled = false
                runOnUiThread {
                    hintText.text = "未匹配到该 ISBN 的公开元数据\n继续对准条码，或换用搜索录入"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        analysisExecutor.shutdown()
    }
}
