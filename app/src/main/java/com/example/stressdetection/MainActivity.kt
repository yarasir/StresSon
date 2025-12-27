@file:OptIn(
    androidx.camera.core.ExperimentalGetImage::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.example.stressdetection

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.stressdetection.ui.screens.MainMenuScreen
import com.example.stressdetection.ui.screens.StressDetectionScreen
import com.example.stressdetection.ui.screens.VideoAnalysisScreen
import com.example.stressdetection.ui.theme.StressDetectionTheme
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// =======================
// MAIN ACTIVITY
// =======================
class MainActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private var onVideoSelected: ((Uri) -> Unit)? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            android.util.Log.d("MainActivity", "✅ Kamera izni verildi")
        } else {
            android.util.Log.w("MainActivity", "❌ Kamera izni reddedildi")
        }
    }

    private val videoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { videoUri ->
                onVideoSelected?.invoke(videoUri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
            StressDetectionTheme {
                var showVideoAnalysis by remember { mutableStateOf(false) }
                var showCameraScreen by remember { mutableStateOf(false) }
                var currentVideoUri by remember { mutableStateOf<Uri?>(null) }

                LaunchedEffect(Unit) {
                    onVideoSelected = { uri ->
                        currentVideoUri = uri
                        showVideoAnalysis = true
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when {
                        showVideoAnalysis && currentVideoUri != null -> {
                            VideoAnalysisScreen(
                                videoUri = currentVideoUri!!,
                                onBack = {
                                    showVideoAnalysis = false
                                    currentVideoUri = null
                                }
                            )
                        }
                        showCameraScreen -> {
                            StressDetectionScreen(
                                requestPermission = { permission ->
                                    requestPermissionLauncher.launch(permission)
                                },
                                onBack = { showCameraScreen = false }
                            )
                        }
                        else -> {
                            MainMenuScreen(
                                onVideoSelect = {
                                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                        type = "video/*"
                                        addCategory(Intent.CATEGORY_OPENABLE)
                                    }
                                    videoPickerLauncher.launch(intent)
                                },
                                onCameraSelect = {
                                    if (ContextCompat.checkSelfPermission(
                                            this@MainActivity,
                                            Manifest.permission.CAMERA
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        showCameraScreen = true
                                    } else {
                                        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

