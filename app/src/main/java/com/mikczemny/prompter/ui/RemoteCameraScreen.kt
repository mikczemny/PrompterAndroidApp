package com.mikczemny.prompter.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mikczemny.prompter.R
import com.mikczemny.prompter.remote.RemoteSecureClient
import com.mikczemny.prompter.remote.RemoteSecureServer
import com.mikczemny.prompter.remote.RemoteCameraSession
import com.mikczemny.prompter.remote.RemoteServiceAdvertiser
import com.mikczemny.prompter.remote.RemoteServiceBrowser
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

enum class RemoteRole { CAMERA, PROMPTER }

@Composable
fun RemoteRoleScreen(onSelect: (RemoteRole) -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.remote_role_title), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.remote_role_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button({ onSelect(RemoteRole.CAMERA) }, Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.remote_role_camera))
        }
        Spacer(Modifier.height(12.dp))
        Button({ onSelect(RemoteRole.PROMPTER) }, Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.remote_role_prompter))
        }
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back_to_menu)) }
    }
}

@Composable
fun RemoteCameraHostScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // The camera phone is commonly left untouched on a tripod. Without this,
    // its normal display timeout stops CameraX and freezes the tablet preview.
    KeepScreenBright(1f)
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var showDisclosure by remember { mutableStateOf(!permissionGranted) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }
    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text(stringResource(R.string.camera_disclosure_title)) },
            text = { Text(stringResource(R.string.remote_camera_disclosure)) },
            confirmButton = {
                TextButton(onClick = {
                    showDisclosure = false
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }) { Text(stringResource(R.string.continue_action)) }
            },
            dismissButton = { TextButton(onClick = onBack) { Text(stringResource(R.string.not_now)) } },
        )
    }
    if (!permissionGranted) {
        MessageWithBack(stringResource(R.string.remote_camera_permission_needed), onBack)
        return
    }

    val pairingCode = remember { RemoteSecureClient.newPairingCode() }
    val server = remember(pairingCode) { RemoteSecureServer(pairingCode) }
    val advertiser = remember { RemoteServiceAdvertiser(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val lastFrameAt = remember { AtomicLong(0) }
    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            setImageAnalysisTargetSize(CameraController.OutputSize(Size(640, 480)))
            setImageAnalysisBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        }
    }
    DisposableEffect(controller, server) {
        server.start()
        advertiser.start()
        controller.setImageAnalysisAnalyzer(executor) { image ->
            try {
                val now = System.currentTimeMillis()
                if (now - lastFrameAt.get() >= FRAME_INTERVAL_MS && lastFrameAt.getAndSet(now) != now) {
                    val source = image.toBitmap()
                    val bitmap = source.rotated(image.imageInfo.rotationDegrees)
                    val output = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
                    server.publishFrame(output.toByteArray())
                    bitmap.recycle()
                    if (source !== bitmap) source.recycle()
                }
            } finally {
                image.close()
            }
        }
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            server.close()
            advertiser.close()
            executor.shutdownNow()
        }
    }

    Box(Modifier.fillMaxSize()) {
        CameraPreview(controller, Modifier.fillMaxSize())
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.remote_camera_ready), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.remote_waiting_for_tablet), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.remote_pairing_code_label), style = MaterialTheme.typography.labelLarge)
                    Text(
                        pairingCode.chunked(3).joinToString("  "),
                        fontSize = 42.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Text(stringResource(R.string.remote_camera_address, localIpv4Address()), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back_to_menu)) }
        }
    }
}

@Composable
fun RemotePrompterClientScreen(
    session: RemoteCameraSession,
    onBack: () -> Unit,
) {
    var host by remember { mutableStateOf("") }
    var cameraName by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    val context = LocalContext.current
    val browser = remember {
        RemoteServiceBrowser(context) { camera ->
            cameraName = camera.name
            host = camera.host
        }
    }
    DisposableEffect(Unit) {
        browser.start()
        onDispose { browser.close() }
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.remote_connect_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            cameraName?.let { stringResource(R.string.remote_camera_found, it) }
                ?: stringResource(R.string.remote_camera_searching),
            color = MaterialTheme.colorScheme.primary,
        )
        OutlinedTextField(
            host, { host = it.filter { char -> char.isDigit() || char == '.' } },
            label = { Text(stringResource(R.string.remote_ip_address_optional)) }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            code, { code = it.filter(Char::isDigit).take(6) },
            label = { Text(stringResource(R.string.remote_pairing_code_label)) }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = host.isNotBlank() && code.length == 6 && !session.connected,
                onClick = { session.connect(host, code) },
            ) { Text(stringResource(R.string.remote_connect)) }
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back_to_menu)) }
        }
        Text(stringResource(if (session.connected) R.string.remote_connected else R.string.remote_not_connected))
        session.frame?.let { bitmap ->
            Image(
                bitmap.asImageBitmap(), contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun MessageWithBack(message: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(message)
        OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back_to_menu)) }
    }
}

private fun Bitmap.rotated(degrees: Int): Bitmap = if (degrees == 0) this else {
    Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
}

private fun localIpv4Address(): String = runCatching {
    NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
        .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }?.hostAddress
}.getOrNull() ?: "-"

private const val FRAME_INTERVAL_MS = 125L
private const val JPEG_QUALITY = 65
