package com.example.aidiarycheomsak.parent

import android.app.Application
import com.kakao.sdk.common.KakaoSdk

class ParentApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Kakao SDK with user's Native App Key
        KakaoSdk.init(this, "8455b01fa3249350b309e01a54d8c47b")
    }
}
