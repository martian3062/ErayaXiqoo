package com.evolet.tachyon.ui

import android.graphics.ImageDecoder
import android.media.ThumbnailUtils
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.evolet.tachyon.twin.ReplicaCapturePlan
import com.evolet.tachyon.twin.ReplicaGenerationStage
import com.evolet.tachyon.twin.ReplicaMeshGenerator
import com.evolet.tachyon.twin.ReplicaPose
import com.evolet.tachyon.twin.ReplicaStore
import com.evolet.tachyon.ui.components.ErayaCoolGradient
import com.evolet.tachyon.ui.components.ErayaSwitch
import com.evolet.tachyon.ui.components.GlassCard
import com.evolet.tachyon.ui.components.GradientAction
import com.evolet.tachyon.ui.components.PageIntro
import com.evolet.tachyon.ui.components.ReplicaPortraitPreview
import com.evolet.tachyon.ui.components.StatusPill
import com.evolet.tachyon.ui.components.TachyonIcons
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Private guided capture plus an animated portrait for the ERAYA Room.
 */
@Composable
fun ReplicaStudioScreen(store: ReplicaStore, generator: ReplicaMeshGenerator) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val snapshot by store.state.collectAsState()
    val scope = rememberCoroutineScope()
    val poses = ReplicaCapturePlan.poses
    val pager = rememberPagerState { poses.size }
    var pendingPhoto by remember { mutableStateOf<Pair<ReplicaPose, File>?>(null) }
    var pendingVideo by remember { mutableStateOf<File?>(null) }
    var sealing by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var generationStage by remember { mutableStateOf(ReplicaGenerationStage.PREPARING) }
    var generationError by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val pending = pendingPhoto
        if (saved && pending != null) runCatching { store.commitPhoto(pending.first, pending.second) }
        else store.discard(pending?.second)
        pendingPhoto = null
    }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { saved ->
        val pending = pendingVideo
        if (saved && pending != null) runCatching { store.commitVideo(pending) }
        else store.discard(pending)
        pendingVideo = null
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !importing) scope.launch {
            importing = true
            importError = null
            runCatching { withContext(Dispatchers.IO) { store.importGenerated(uri) } }
                .onSuccess { Toast.makeText(context, "Laptop replica imported", Toast.LENGTH_SHORT).show() }
                .onFailure { importError = it.message ?: "Replica import failed" }
            importing = false
        }
    }

    fun capturePhoto(pose: ReplicaPose) {
        if (!snapshot.consented) return
        val file = store.newPendingPhoto(pose)
        pendingPhoto = pose to file
        photoLauncher.launch(store.uri(file))
    }

    fun captureVideo() {
        if (!snapshot.consented) return
        val file = store.newPendingVideo()
        pendingVideo = file
        videoLauncher.launch(store.uri(file))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete replica capture?") },
            text = { Text("All ten private photos, motion video and the local replica profile will be permanently removed from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    store.wipe()
                    confirmDelete = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PageIntro(
                eyebrow = "Portrait Studio · private beta",
                title = "Build a visual self",
                supporting = "Capture ten guided angles and one short motion reference, or import an animated portrait generated on your laptop. Everything remains inside ERAYA.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            ConsentPanel(
                checked = snapshot.consented,
                onChecked = store::setConsent,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            LaptopImportPanel(
                consented = snapshot.consented,
                importing = importing,
                error = importError,
                onImport = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            CaptureProgress(
                count = snapshot.capturedCount,
                total = poses.size,
                video = snapshot.hasMotionVideo,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            HorizontalPager(
                state = pager,
                contentPadding = PaddingValues(horizontal = 20.dp),
                pageSpacing = 12.dp,
                modifier = Modifier.fillMaxWidth().height(405.dp),
            ) { page ->
                val pose = poses[page]
                ReplicaPoseCard(
                    pose = pose,
                    index = page,
                    total = poses.size,
                    file = store.photoFile(pose).takeIf(File::exists),
                    enabled = snapshot.consented,
                    onCapture = { capturePhoto(pose) },
                )
            }
        }
        item {
            val pose = poses[pager.currentPage]
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IconButton(
                    enabled = pager.currentPage > 0,
                    onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                ) { Icon(TachyonIcons.ChevronLeft, contentDescription = "Previous angle") }
                GradientAction(
                    label = if (snapshot.capturedPoseIds.contains(pose.id)) "Retake ${pose.label}" else "Capture ${pose.label}",
                    onClick = { capturePhoto(pose) },
                    enabled = snapshot.consented,
                    modifier = Modifier.weight(1f),
                    leading = {
                        Icon(TachyonIcons.Camera, contentDescription = null, tint = Color.White, modifier = Modifier.padding(end = 8.dp))
                    },
                )
                IconButton(
                    enabled = pager.currentPage < poses.lastIndex,
                    onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                ) { Icon(TachyonIcons.ChevronRight, contentDescription = "Next angle") }
            }
        }
        item {
            PoseDots(
                selected = pager.currentPage,
                completed = snapshot.capturedPoseIds,
                poses = poses,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            MotionCaptureCard(
                file = store.motionFile().takeIf(File::exists),
                enabled = snapshot.consented,
                onCapture = ::captureVideo,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            GenerationPanel(
                complete = snapshot.captureComplete,
                generated = snapshot.portraitReady,
                sealing = sealing,
                generationStage = generationStage,
                generationError = generationError,
                onGenerate = {
                    if (!sealing) scope.launch {
                        sealing = true
                        generationError = null
                        runCatching { generator.generate { generationStage = it } }
                            .onFailure { generationError = it.message ?: "Portrait generation failed" }
                        sealing = false
                    }
                },
                portrait = store.portraitFile().takeIf(File::isFile),
                talkingPortrait = store.talkingPortraitFile(),
                blinkPortrait = store.blinkPortraitFile(),
                lookLeftPortrait = store.lookLeftPortraitFile(),
                lookRightPortrait = store.lookRightPortraitFile(),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (snapshot.capturedCount > 0 || snapshot.hasMotionVideo) {
            item {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Icon(TachyonIcons.Delete, contentDescription = null)
                    Text("  Delete replica capture")
                }
            }
        }
    }
}

@Composable
private fun LaptopImportPanel(
    consented: Boolean,
    importing: Boolean,
    error: String?,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(ErayaCoolGradient), contentAlignment = Alignment.Center) {
                    Icon(TachyonIcons.Upload, contentDescription = null, tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text("Bring in your anime portrait", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (consented) "Select ERAYA-anime-avatar.zip; it is validated before replacing your current portrait."
                        else "Confirm consent above before importing your identity portrait.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            OutlinedButton(
                onClick = onImport,
                enabled = consented && !importing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (importing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(TachyonIcons.Upload, contentDescription = null)
                Text(if (importing) "  Validating package…" else "  Import portrait package")
            }
        }
    }
}

@Composable
private fun ConsentPanel(checked: Boolean, onChecked: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(ErayaCoolGradient),
                contentAlignment = Alignment.Center,
            ) {
                Icon(TachyonIcons.Shield, contentDescription = null, tint = Color.White)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Consent and ownership", style = MaterialTheme.typography.titleMedium)
                Text(
                    "I am capturing myself, or I have the person's clear consent. Media stays in private app storage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ErayaSwitch(checked = checked, onCheckedChange = onChecked)
        }
    }
}

@Composable
private fun CaptureProgress(count: Int, total: Int, video: Boolean, modifier: Modifier = Modifier) {
    GlassCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Capture set", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusPill("$count / $total views", count == total)
                Spacer(Modifier.size(6.dp))
                StatusPill(if (video) "Motion ready" else "Motion needed", video)
            }
            LinearProgressIndicator(
                progress = { (count + if (video) 1f else 0f) / (total + 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFE73877),
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            )
            Text("Use even light, remove hats or glasses, and keep the phone at eye level.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ReplicaPoseCard(
    pose: ReplicaPose,
    index: Int,
    total: Int,
    file: File?,
    enabled: Boolean,
    onCapture: () -> Unit,
) {
    val bitmap = rememberPreview(file)
    GlassCard(Modifier.fillMaxSize(), onClick = onCapture.takeIf { enabled }) {
        Box(Modifier.fillMaxSize()) {
            if (bitmap != null) {
                Image(bitmap, contentDescription = pose.label, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                PoseGuide(Modifier.fillMaxSize())
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color(0xD9110914))),
                ),
            )
            StatusPill(
                label = if (bitmap == null) "VIEW ${index + 1} OF $total" else "CAPTURED · ${index + 1} OF $total",
                active = bitmap != null,
                modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(pose.label, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text(pose.instruction, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.82f))
            }
        }
    }
}

@Composable
private fun PoseGuide(modifier: Modifier = Modifier) {
    Canvas(modifier.background(ErayaCoolGradient)) {
        val center = Offset(size.width / 2, size.height * 0.46f)
        drawCircle(Color.White.copy(alpha = 0.11f), radius = size.minDimension * 0.34f, center = center)
        drawCircle(Color.White.copy(alpha = 0.84f), radius = size.minDimension * 0.17f, center = center, style = Stroke(3.dp.toPx()))
        drawArc(
            color = Color.White.copy(alpha = 0.84f),
            startAngle = 205f,
            sweepAngle = 130f,
            useCenter = false,
            topLeft = Offset(center.x - size.minDimension * 0.28f, center.y + size.minDimension * 0.13f),
            size = androidx.compose.ui.geometry.Size(size.minDimension * 0.56f, size.minDimension * 0.42f),
            style = Stroke(3.dp.toPx()),
        )
        drawCircle(Color.White.copy(alpha = 0.45f), radius = size.minDimension * 0.30f, center = center, style = Stroke(1.dp.toPx()))
        drawLine(Color.White.copy(alpha = 0.22f), Offset(center.x, center.y - size.height * 0.30f), Offset(center.x, center.y + size.height * 0.34f), 1.dp.toPx())
        drawLine(Color.White.copy(alpha = 0.22f), Offset(center.x - size.width * 0.34f, center.y), Offset(center.x + size.width * 0.34f, center.y), 1.dp.toPx())
    }
}

@Composable
private fun PoseDots(selected: Int, completed: Set<String>, poses: List<ReplicaPose>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        poses.forEachIndexed { index, pose ->
            val color = when {
                index == selected -> MaterialTheme.colorScheme.primary
                completed.contains(pose.id) -> Color(0xFF2FB681)
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Box(Modifier.padding(3.dp).size(if (index == selected) 10.dp else 7.dp).background(color, CircleShape))
        }
    }
}

@Composable
private fun MotionCaptureCard(file: File?, enabled: Boolean, onCapture: () -> Unit, modifier: Modifier = Modifier) {
    val thumbnail = rememberVideoPreview(file)
    GlassCard(modifier) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(92.dp).clip(RoundedCornerShape(22.dp)).background(ErayaCoolGradient),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnail != null) Image(thumbnail, contentDescription = "Motion reference", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(TachyonIcons.Video, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Motion reference", style = MaterialTheme.typography.titleMedium)
                Text("Slowly turn left, centre, then right for 6–10 seconds.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onCapture, enabled = enabled, contentPadding = PaddingValues(0.dp)) {
                    Text(if (file == null) "Record video" else "Retake video")
                }
            }
            StatusPill(if (file == null) "Needed" else "Ready", file != null)
        }
    }
}

@Composable
private fun GenerationPanel(
    complete: Boolean,
    generated: Boolean,
    sealing: Boolean,
    generationStage: ReplicaGenerationStage,
    generationError: String?,
    onGenerate: () -> Unit,
    portrait: File?,
    talkingPortrait: File,
    blinkPortrait: File,
    lookLeftPortrait: File,
    lookRightPortrait: File,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(ErayaCoolGradient), contentAlignment = Alignment.Center) {
                    Icon(TachyonIcons.Sparkles, contentDescription = null, tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(if (generated) "Animated portrait ready" else "Create private animated portrait", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            generated -> "Your portrait, eye motion and lip-sync are ready for the ERAYA Room."
                            complete -> "Your capture set is complete."
                            else -> "Complete all ten views and the motion reference, or import a laptop portrait package."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedContent(targetState = generated, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "replica-ready") { ready ->
                if (ready && portrait != null) ReplicaReadyPreview(
                    portrait = portrait,
                    talkingPortrait = talkingPortrait,
                    blinkPortrait = blinkPortrait,
                    lookLeftPortrait = lookLeftPortrait,
                    lookRightPortrait = lookRightPortrait,
                ) else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AnimatedVisibility(sealing) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(generationStage.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    generationError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    GradientAction(
                        label = if (sealing) "Building portrait locally…" else "Build animated portrait on this phone",
                        onClick = onGenerate,
                        enabled = complete && !sealing,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Text(
                "The Room displays only this portrait, with private eye movement, blinking, breathing and voice-driven lip motion.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReplicaReadyPreview(
    portrait: File,
    talkingPortrait: File,
    blinkPortrait: File,
    lookLeftPortrait: File,
    lookRightPortrait: File,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.fillMaxWidth().height(330.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF0E0314)).border(1.dp, Color(0x66E73877), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            ReplicaPortraitPreview(
                portraitFile = portrait,
                talkingPortraitFile = talkingPortrait,
                blinkPortraitFile = blinkPortrait,
                lookLeftPortraitFile = lookLeftPortrait,
                lookRightPortraitFile = lookRightPortrait,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusPill("portrait only", true)
            StatusPill("eyes + lip-sync", true)
            StatusPill("offline", true)
        }
    }
}

@Composable
private fun rememberPreview(file: File?): ImageBitmap? {
    val key = file?.let { "${it.path}:${it.lastModified()}:${it.length()}" }
    val value by produceState<ImageBitmap?>(initialValue = null, key1 = key) {
        value = withContext(Dispatchers.IO) {
            file?.takeIf { it.exists() && it.length() > 0L }?.let {
                runCatching {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(it)) { decoder, info, _ ->
                        val scale = (1_200f / maxOf(info.size.width, info.size.height)).coerceAtMost(1f)
                        decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    return value
}

@Composable
private fun rememberVideoPreview(file: File?): ImageBitmap? {
    val key = file?.let { "${it.path}:${it.lastModified()}:${it.length()}" }
    val value by produceState<ImageBitmap?>(initialValue = null, key1 = key) {
        value = withContext(Dispatchers.IO) {
            file?.takeIf { it.exists() && it.length() > 0L }?.let {
                runCatching { ThumbnailUtils.createVideoThumbnail(it, Size(720, 720), null).asImageBitmap() }.getOrNull()
            }
        }
    }
    return value
}
