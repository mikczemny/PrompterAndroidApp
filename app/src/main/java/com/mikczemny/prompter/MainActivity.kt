package com.mikczemny.prompter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mikczemny.prompter.ui.HomeScreen
import com.mikczemny.prompter.ui.TeleprompterScreen
import com.mikczemny.prompter.ui.theme.PrompterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PrompterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    PrompterApp()
                }
            }
        }
    }
}

@Composable
private fun PrompterApp() {
    var script by remember { mutableStateOf<String?>(null) }

    val current = script
    if (current == null) {
        HomeScreen(onStart = { script = it })
    } else {
        TeleprompterScreen(
            script = current,
            onBack = { script = null },
        )
    }
}
