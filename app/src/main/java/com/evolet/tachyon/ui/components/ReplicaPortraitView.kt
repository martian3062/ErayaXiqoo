package com.evolet.tachyon.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** High-resolution portrait with breathing, gaze, blink, and audio-driven speaking motion. */
@Composable
fun ReplicaPortraitPreview(
    portraitFile: File,
    talkingPortraitFile: File,
    blinkPortraitFile: File,
    lookLeftPortraitFile: File,
    lookRightPortraitFile: File,
    modifier: Modifier = Modifier,
    talking: Boolean = false,
    listening: Boolean = false,
    audioLevel: Float = 0f,
) {
    val key = "${portraitFile.path}:${portraitFile.lastModified()}:${portraitFile.length()}"
    val portrait by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, key1 = key) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(portraitFile.path)?.asImageBitmap()
        }
    }
    val talkingKey = "${talkingPortraitFile.path}:${talkingPortraitFile.lastModified()}:${talkingPortraitFile.length()}"
    val talkingPortrait by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, key1 = talkingKey) {
        value = withContext(Dispatchers.IO) {
            talkingPortraitFile.takeIf(File::isFile)?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }
        }
    }
    val blinkKey = "${blinkPortraitFile.path}:${blinkPortraitFile.lastModified()}:${blinkPortraitFile.length()}"
    val blinkPortrait by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, key1 = blinkKey) {
        value = withContext(Dispatchers.IO) {
            blinkPortraitFile.takeIf(File::isFile)?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }
        }
    }
    val lookLeftKey = "${lookLeftPortraitFile.path}:${lookLeftPortraitFile.lastModified()}:${lookLeftPortraitFile.length()}"
    val lookLeftPortrait by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, key1 = lookLeftKey) {
        value = withContext(Dispatchers.IO) {
            lookLeftPortraitFile.takeIf(File::isFile)?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }
        }
    }
    val lookRightKey = "${lookRightPortraitFile.path}:${lookRightPortraitFile.lastModified()}:${lookRightPortraitFile.length()}"
    val lookRightPortrait by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, key1 = lookRightKey) {
        value = withContext(Dispatchers.IO) {
            lookRightPortraitFile.takeIf(File::isFile)?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }
        }
    }
    val transition = rememberInfiniteTransition(label = "replica portrait motion")
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (talking || listening) 1_450 else 3_600), RepeatMode.Reverse),
        label = "portrait breathing",
    )
    val sway by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_600), RepeatMode.Reverse),
        label = "portrait head sway",
    )
    val nod by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 7_200
                0f at 0
                0f at 1_900
                1f at 2_180
                -0.35f at 2_620
                0f at 3_050
                0f at 7_200
            },
        ),
        label = "portrait head nod",
    )
    val mouthPulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 620
                0.08f at 0
                0.88f at 95
                0.28f at 205
                0.96f at 330
                0.42f at 455
                0.10f at 620
            },
        ),
        label = "portrait syllable movement",
    )
    val speechEnvelope by animateFloatAsState(
        targetValue = if (talking) audioLevel.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 70),
        label = "speech audio envelope",
    )
    val blink by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 4_600
                0f at 0
                0f at 4_040
                1f at 4_120
                1f at 4_205
                0f at 4_310
                0f at 4_600
            },
        ),
        label = "portrait blink",
    )
    val gaze by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 11_400
                0f at 0
                0f at 1_500
                -1f at 1_720
                -1f at 2_450
                0f at 2_700
                0f at 6_100
                1f at 6_320
                1f at 7_040
                0f at 7_300
            },
        ),
        label = "portrait eye gaze",
    )

    Box(modifier, contentAlignment = Alignment.Center) {
        val image = portrait
        if (image == null) {
            CircularProgressIndicator()
        } else {
            val mouthOpen = PortraitMotion.lipAmount(talking, speechEnvelope, mouthPulse)
            val activity = when {
                talking -> 1f
                listening -> 0.45f + audioLevel.coerceIn(0f, 1f) * 0.55f
                else -> 0f
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val pulse = 0.009f + activity * 0.009f
                        scaleX = 1.015f + breath * pulse
                        scaleY = 1.015f + breath * pulse
                        translationX = sway * (1.4f + activity * 2.4f) + gaze * (0.7f + activity)
                        translationY = -2.5f * breath - activity * 1.5f + nod * (1.2f + activity * 1.5f)
                        rotationX = nod * (0.25f + activity * 0.35f)
                        rotationZ = sway * (0.18f + activity * 0.30f)
                    },
            ) {
                Image(
                    bitmap = image,
                    contentDescription = "Your animated high-definition anime ERAYA portrait",
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                )
                // A single portrait is sufficient: gently deform only the mouth/jaw band from
                // the neutral image. Optional authored expression frames can refine this, but
                // lip motion never depends on a 3D model or a second image.
                if (talking) {
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        alpha = (0.55f + mouthOpen * 0.45f).coerceIn(0f, 1f),
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(0.5f, 0.69f)
                                scaleY = 1f + mouthOpen * 0.055f
                                translationY = mouthOpen * 2.6f
                            }
                            .mouthBandOnly(),
                    )
                }
                talkingPortrait?.let { frame ->
                    Image(
                        bitmap = frame,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        alpha = mouthOpen * 0.88f,
                        modifier = Modifier.fillMaxSize().mouthBandOnly(),
                    )
                }
                lookLeftPortrait?.let { frame ->
                    Image(
                        bitmap = frame,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        alpha = (-gaze).coerceIn(0f, 1f) * (1f - blink),
                        modifier = Modifier.fillMaxSize().eyeBandOnly(),
                    )
                }
                lookRightPortrait?.let { frame ->
                    Image(
                        bitmap = frame,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        alpha = gaze.coerceIn(0f, 1f) * (1f - blink),
                        modifier = Modifier.fillMaxSize().eyeBandOnly(),
                    )
                }
                blinkPortrait?.let { frame ->
                    Image(
                        bitmap = frame,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        alpha = blink,
                        modifier = Modifier.fillMaxSize().eyeBandOnly(),
                    )
                }
            }
        }
    }
}

/** Clips expression frames to the eyes so gaze/blinks can combine with a speaking mouth. */
private fun Modifier.eyeBandOnly(): Modifier = drawWithContent {
    clipRect(top = size.height * 0.29f, bottom = size.height * 0.59f) {
        this@drawWithContent.drawContent()
    }
}

/** Keeps speech deformation local to the lower face instead of stretching the whole portrait. */
private fun Modifier.mouthBandOnly(): Modifier = drawWithContent {
    clipRect(top = size.height * 0.56f, bottom = size.height * 0.82f) {
        this@drawWithContent.drawContent()
    }
}

/** Pure motion mapping kept separate so lip-sync behaviour can be regression-tested. */
internal object PortraitMotion {
    fun lipAmount(talking: Boolean, speechLevel: Float, syllablePulse: Float): Float {
        if (!talking) return 0f
        return (
            0.08f +
                speechLevel.coerceIn(0f, 1f) * 0.68f +
                syllablePulse.coerceIn(0f, 1f) * 0.24f
            ).coerceIn(0.08f, 1f)
    }
}
