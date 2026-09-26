package com.evolet.tachyon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.evolet.tachyon.EngineState.IDLE
import com.evolet.tachyon.EngineState.LOADING
import com.evolet.tachyon.ui.TachyonRoot
import com.evolet.tachyon.ui.components.BrandLaunchScreen
import com.evolet.tachyon.ui.theme.TachyonTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        splash.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .scaleX(1.06f)
                .scaleY(1.06f)
                .setDuration(220L)
                .withEndAction(provider::remove)
                .start()
        }
        setContent {
            TachyonTheme {
                var ready by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(850L)
                    withTimeoutOrNull(1_400L) {
                        container.engines.status.first { status ->
                            status.asr.state !in setOf(IDLE, LOADING) && status.llm.state !in setOf(IDLE, LOADING)
                        }
                    }
                    ready = true
                }
                AnimatedContent(
                    targetState = ready,
                    transitionSpec = {
                        (fadeIn() + scaleIn(initialScale = 0.985f)) togetherWith
                            (fadeOut() + scaleOut(targetScale = 1.025f))
                    },
                    label = "launch-to-app",
                ) { showApp ->
                    if (showApp) TachyonRoot(container) else BrandLaunchScreen()
                }
            }
        }
    }
}
