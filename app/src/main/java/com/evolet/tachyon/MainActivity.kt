package com.evolet.tachyon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.evolet.tachyon.ui.TachyonRoot
import com.evolet.tachyon.ui.theme.TachyonTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TachyonTheme { TachyonRoot(container) }
        }
    }
}
