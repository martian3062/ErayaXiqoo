package com.evolet.tachyon.ui

import android.graphics.ImageDecoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.evolet.tachyon.eyes.EyesController
import com.evolet.tachyon.eyes.EyesStage
import com.evolet.tachyon.ui.components.GlassCard
import com.evolet.tachyon.ui.components.PageIntro
import com.evolet.tachyon.ui.components.StatusPill
import com.evolet.tachyon.ui.components.TachyonIcons
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun EyesScreen(controller: EyesController, onPropose: (String) -> Unit) {
    val ui by controller.ui.collectAsState()
    var pending by remember { mutableStateOf<Pair<File, android.net.Uri>?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val capture = pending
        if (saved && capture != null) controller.scan(capture.first) else controller.discard()
        pending = null
    }
    val preview = rememberEyesPreview(ui.imageFile)

    fun capture() {
        val target = controller.newCapture()
        pending = target
        camera.launch(target.second)
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageIntro(
                eyebrow = "F11 · on-device vision",
                title = "Eyes",
                supporting = "Photograph a whiteboard, note or checklist. ERAYA reads it locally and asks before creating any proposal.",
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(TachyonIcons.Camera, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Private camera evidence", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        StatusPill("EN + हिन्दी · OFFLINE", true)
                    }
                    Text(
                        "The photo stays in ERAYA's private storage and is deleted after you submit or discard it. Extracted text remains editable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (preview != null) {
            item {
                Box(
                    Modifier.fillMaxWidth().height(250.dp).clip(MaterialTheme.shapes.large),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = preview,
                        contentDescription = "Private note captured for offline text recognition",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (ui.stage == EyesStage.SCANNING) {
                        GlassCard(Modifier.padding(24.dp)) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                Text("Reading text on device…")
                            }
                        }
                    }
                }
            }
        }

        when (ui.stage) {
            EyesStage.READY -> item {
                Button(onClick = ::capture, modifier = Modifier.fillMaxWidth()) {
                    Icon(TachyonIcons.Camera, contentDescription = null)
                    Text("  Photograph notes")
                }
            }
            EyesStage.SCANNING -> Unit
            EyesStage.ERROR -> {
                item {
                    Text(ui.error.orEmpty(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = controller::discard, modifier = Modifier.weight(1f)) { Text("Discard") }
                        Button(onClick = ::capture, modifier = Modifier.weight(1f)) { Text("Retake") }
                    }
                }
            }
            EyesStage.REVIEW -> {
                item {
                    Text("Review ${ui.detectedLines} detected lines", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Correct names or dates before continuing. ERAYA will still propose—never automatically commit—any actions it finds.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    OutlinedTextField(
                        value = ui.text,
                        onValueChange = controller::updateText,
                        label = { Text("Recognized text") },
                        minLines = 7,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ui.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = controller::discard, modifier = Modifier.weight(1f)) { Text("Discard") }
                        Button(
                            onClick = { controller.consumeForProposal()?.let(onPropose) },
                            enabled = ui.text.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        ) { Text("Propose commitments") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun rememberEyesPreview(file: File?): ImageBitmap? {
    val key = file?.let { "${it.path}:${it.lastModified()}:${it.length()}" }
    val bitmap by produceState<ImageBitmap?>(null, key1 = key) {
        value = withContext(Dispatchers.IO) {
            file?.takeIf { it.isFile && it.length() > 0L }?.let {
                runCatching {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(it)) { decoder, info, _ ->
                        val scale = (1_400f / maxOf(info.size.width, info.size.height)).coerceAtMost(1f)
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt().coerceAtLeast(1),
                            (info.size.height * scale).toInt().coerceAtLeast(1),
                        )
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    return bitmap
}
