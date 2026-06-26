package com.example.aidiarycheomsak.parent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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
import com.google.android.gms.ads.MobileAds

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Initialize Google AdMob SDK
    MobileAds.initialize(this) {}
    
    enableEdgeToEdge()
    
    // Request notification permission for Android 13+ (API 33+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
      }
    }
    
    // Create FCM notification channel
    createNotificationChannel()
    
    setContent {
      AiDiaryCheomsakTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation(initialReportId = androidx.compose.runtime.remember { mutableStateOf(null) })
        }
      }
    }
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val name = "\uc77c\uae30\u0020\uc131\uc7a5\u0020\ubcf4\uace0\uc11c" // "일기 성장 보고서"
      val descriptionText = "자녀의 일기 최종 완료 및 마법이슬 알림을 수신합니다."
      val importance = NotificationManager.IMPORTANCE_HIGH
      val channel = NotificationChannel("diary_notification_channel", name, importance).apply {
        description = descriptionText
      }
      val notificationManager: NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      notificationManager.createNotificationChannel(channel)
    }
  }
}
