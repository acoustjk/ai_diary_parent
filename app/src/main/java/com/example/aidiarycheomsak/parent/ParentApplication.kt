package com.example.aidiarycheomsak.parent

import android.app.Application
import com.kakao.sdk.common.KakaoSdk

class ParentApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Kakao SDK with user's Native App Key
        KakaoSdk.init(this, "6a6d42054298ea6c7b9d62bf919a148a")
    }
}
