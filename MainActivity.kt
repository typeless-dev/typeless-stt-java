package com.example.testtypelessstttypeless

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.testtypelessstttypeless.ui.theme.TestTypelessSttTypelessTheme

class MainActivity : ComponentActivity() {
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    // Initialize the websocketManager lazily to ensure context and other components are ready.
    private val websocketManager: WebsocketManager by lazy {
        WebsocketManager(
            "wss://speech.typeless.ch/real-time-transcription",
            "fr",
            arrayOf("typeless"),
            true,
            "gynécologie",
            "12345",
            object : WebsocketManager.MessageHandler {
                override fun handleMessage(message: String) {
                    Log.d("WebSocket", "Received message: $message")
                }
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Permission is granted. Start the websocket connection.
                websocketManager.start()
            } else {
                // Permission is denied. Explain why the feature is unavailable.
            }
        }

        setContent {
            TestTypelessSttTypelessTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Greeting("Android")
                        RequestPermissionButton()
                        StopStreamingButton()
                    }
                }
            }
        }
    }

    @Composable
    fun RequestPermissionButton() {
        val context = LocalContext.current
        Button(onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                // Permission has already been granted, start the websocket connection.
                websocketManager.start()
            } else {
                // Not yet granted, request permission.
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }) {
            Text("Start Streaming")
        }
    }

    @Composable
    fun StopStreamingButton() {
        Button(onClick = {
            // Stop the audio streaming.
            websocketManager.stop()
        }) {
            Text("Stop Streaming")
        }
    }
}


@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    TestTypelessSttTypelessTheme {
        Greeting("Android")
    }
}
