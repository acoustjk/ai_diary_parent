package com.example.aidiarycheomsak.parent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.aidiarycheomsak.parent.data.CompressionHelper
import com.example.aidiarycheomsak.parent.data.DiaryReport
import com.example.aidiarycheomsak.parent.data.PreferenceHelper
import com.example.aidiarycheomsak.parent.theme.AiDiaryCheomsakTheme
import kotlinx.serialization.json.Json
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    // Request notification permission for Android 13+ (API 33+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
      }
    }
    
    // Anonymous Sign In to Firebase on startup
    FirebaseAuth.getInstance().signInAnonymously()
      .addOnFailureListener {
        Toast.makeText(this, "Firebase 연결 실패: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
      }
      
    setContent {
      AiDiaryCheomsakTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation(initialReportId = androidx.compose.runtime.remember { mutableStateOf(null) })
        }
      }
    }
  }
}
