package com.example.aidiarycheomsak.parent.ui

import android.app.Activity
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aidiarycheomsak.parent.data.DiaryReport
import com.example.aidiarycheomsak.parent.data.PreferenceHelper
import com.example.aidiarycheomsak.parent.data.GeminiService
import com.example.aidiarycheomsak.parent.data.BillingHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.kakao.sdk.user.UserApiClient
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Popup
import androidx.compose.material.icons.filled.Home
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentHomeScreen(
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val preferenceHelper = remember { PreferenceHelper(context) }
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val scope = rememberCoroutineScope()
    var isAuthChecking by remember { mutableStateOf(true) }
    var isUserLoggedIn by remember { mutableStateOf(auth.currentUser != null && !auth.currentUser!!.isAnonymous) }
    var userEmail by remember { mutableStateOf(auth.currentUser?.email ?: "") }
    var reviewerName by remember { mutableStateOf(preferenceHelper.reviewerName) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            isUserLoggedIn = user != null && !user.isAnonymous
            userEmail = user?.email ?: ""
            reviewerName = preferenceHelper.reviewerName
            if (user == null || user.isAnonymous) {
                isAuthChecking = false
            }
        }
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    // State variables
    var childrenList by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var selectedChildId by remember { mutableStateOf("") }
    var diariesList by remember { mutableStateOf<List<DiaryReport>>(emptyList()) }
    var isLoadingChildren by remember { mutableStateOf(false) }
    var isLoadingDiaries by remember { mutableStateOf(false) }

    // Dialog state
    var showPairingDialog by remember { mutableStateOf(false) }
    var showPurchaseDialog by remember { mutableStateOf(false) }
    var pairingCodeInput by remember { mutableStateOf("") }
    var pairingNicknameInput by remember { mutableStateOf("") }
    var isPairingInProgress by remember { mutableStateOf(false) }

    val activity = remember(context) { context as? Activity }
    val serverUrl = remember { preferenceHelper.serverUrl.ifBlank { "https://www.ai-gochi.com" } }
    val billingHelper = remember {
        BillingHelper(context, serverUrl) { productId, creditsAdded ->
            // Trigger UI reload or Firestore sync if needed. 
            // Real-time snapshot listener on Firebase children collection will automatically update the credits UI!
        }
    }

    // Initial setup auth monitor
    val uid = remember(isUserLoggedIn) { auth.currentUser?.uid ?: "" }

    // Listen for reviewer document changes in real-time
    DisposableEffect(uid) {
        if (uid.isNotEmpty()) {
            val listenerRegistration = db.collection("reviewers").document(uid)
                .addSnapshotListener { snapshot, e ->
                    if (snapshot != null && snapshot.exists()) {
                        val name = snapshot.getString("name") ?: ""
                        preferenceHelper.reviewerName = name
                        reviewerName = name
                    }
                    isAuthChecking = false
                }
            onDispose {
                listenerRegistration.remove()
            }
        } else {
            if (!isUserLoggedIn && auth.currentUser == null) {
                isAuthChecking = false
            }
            reviewerName = ""
            onDispose {}
        }
    }

    // 1. Listen for paired children in real-time
    LaunchedEffect(uid, reviewerName) {
        if (uid.isNotEmpty() && reviewerName.isNotEmpty()) {
            isLoadingChildren = true
            val reviewerMap = mapOf("uid" to uid, "name" to reviewerName)
            val registration = db.collection("children")
                .whereArrayContains("pairedReviewers", reviewerMap)
                .addSnapshotListener { snapshot, e ->
                    if (snapshot != null) {
                        val list = mutableListOf<Map<String, Any>>()
                        for (doc in snapshot.documents) {
                            val data = doc.data ?: continue
                            list.add(data)
                        }
                        childrenList = list
                        
                        // Automatically re-subscribe to FCM topics for all paired children
                        list.forEach { child ->
                            val childId = child["childId"] as? String ?: ""
                            if (childId.isNotEmpty()) {
                                FirebaseMessaging.getInstance().subscribeToTopic("child_$childId")
                            }
                        }
                        
                        // Default select first child if current selection is empty or not in the list
                        if (selectedChildId.isEmpty() && list.isNotEmpty()) {
                            selectedChildId = list[0]["childId"] as? String ?: ""
                        }
                    }
                    isLoadingChildren = false
                }
        }
    }

    // 1.5. Ensure subscription to all paired children's FCM topics
    LaunchedEffect(childrenList) {
        childrenList.forEach { child ->
            val childId = child["childId"] as? String ?: ""
            if (childId.isNotEmpty()) {
                FirebaseMessaging.getInstance().subscribeToTopic("child_$childId")
            }
        }
    }

    // 2. Listen for selected child's diaries in real-time
    LaunchedEffect(selectedChildId) {
        if (selectedChildId.isNotEmpty()) {
            isLoadingDiaries = true
            val registration = db.collection("children")
                .document(selectedChildId)
                .collection("diaries")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, e ->
                    if (snapshot != null) {
                        val list = mutableListOf<DiaryReport>()
                        for (doc in snapshot.documents) {
                            val report = DiaryReport(
                                id = doc.getString("diaryId") ?: "",
                                name = doc.getString("name") ?: "",
                                originalContent = doc.getString("originalContent") ?: "",
                                originalFeedback = doc.getString("originalFeedback") ?: "",
                                rewrittenContent = doc.getString("rewrittenContent") ?: "",
                                firstSpellingScore = doc.getLong("firstSpellingScore")?.toInt() ?: 0,
                                firstExpressionScore = doc.getLong("firstExpressionScore")?.toInt() ?: 0,
                                secondSpellingScore = doc.getLong("secondSpellingScore")?.toInt() ?: 0,
                                secondExpressionScore = doc.getLong("secondExpressionScore")?.toInt() ?: 0,
                                stamp = doc.getString("stamp") ?: "",
                                improved = doc.getBoolean("improved") ?: false,
                                typingSpeed = doc.getLong("wpm")?.toInt() ?: 0,
                                timestamp = doc.getLong("timestamp") ?: 0L,
                                originalLength = doc.getLong("originalLength")?.toInt() ?: 0,
                                rewrittenLength = doc.getLong("rewrittenLength")?.toInt() ?: 0
                            )
                            list.add(report)
                        }
                        diariesList = list
                    }
                    isLoadingDiaries = false
                }
        } else {
            diariesList = emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("👩‍👦 AI고치 보호자용 대시보드", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ai-gochi.com"))
                        context.startActivity(intent)
                    }) {
                        Icon(imageVector = Icons.Default.Home, contentDescription = "홈페이지 바로가기")
                    }
                    if (reviewerName.isNotEmpty()) {
                        var showTooltip by remember { mutableStateOf(true) }
                        
                        Box(contentAlignment = Alignment.Center) {
                            IconButton(onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ai-gochi.com"))
                                context.startActivity(intent)
                            }) {
                                Icon(imageVector = Icons.Default.Home, contentDescription = "홈페이지 바로가기")
                            }
                            
                            if (showTooltip) {
                                Popup(
                                    alignment = Alignment.BottomCenter,
                                    onDismissRequest = { showTooltip = false }
                                ) {
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3182CE)),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                        modifier = Modifier
                                            .padding(top = 48.dp)
                                            .width(180.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "홈페이지 설명서보기",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ai-gochi.com"))
                                                        context.startActivity(intent)
                                                        showTooltip = false
                                                    }
                                            )
                                            IconButton(
                                                onClick = { showTooltip = false },
                                                modifier = Modifier.size(16.dp)
                                            ) {
                                                Text(text = "✕", color = Color.White, fontSize = 9.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        IconButton(onClick = { showPairingDialog = true }) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "자녀 추가")
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "설정")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF7FAFC))
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF7FAFC)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🔒 아이용 앱에는 광고가 없습니다.",
                    color = Color(0xFF718096),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(Color(0xFFE2E8F0))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📢 [광고] AI고치 추천 교재 및 도서 배너 광고",
                        color = Color(0xFF4A5568),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        if (isAuthChecking) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF7FAFC))
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF3182CE))
            }
        } else if (!isUserLoggedIn) {
            var isAuthLoading by remember { mutableStateOf(false) }
            var isAgreed by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF7FAFC))
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "👩‍👦 AI고치 보호자 로그인",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2D3748)
                        )

                        Text(
                            text = "아이의 일기 분석 결과를 실시간으로 확인하고 마법이슬을 관리하기 위해 로그인해 주세요.\n첫 로그인 시 자동으로 회원가입이 진행됩니다.",
                            fontSize = 14.sp,
                            color = Color(0xFF718096),
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Terms & Privacy Agreement Checkbox Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        ) {
                            Checkbox(
                                checked = isAgreed,
                                onCheckedChange = { isAgreed = it },
                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF3182CE))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "이용약관",
                                        color = Color(0xFF3182CE),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.clickable {
                                            val serverUrl = preferenceHelper.serverUrl.ifBlank { "https://www.ai-gochi.com" }
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("${serverUrl.trim().removeSuffix("/")}/terms"))
                                            context.startActivity(intent)
                                        }
                                    )
                                    Text(text = " 및 ", color = Color(0xFF718096), fontSize = 12.sp)
                                    Text(
                                        text = "개인정보 처리방침",
                                        color = Color(0xFF3182CE),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.clickable {
                                            val serverUrl = preferenceHelper.serverUrl.ifBlank { "https://www.ai-gochi.com" }
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("${serverUrl.trim().removeSuffix("/")}/privacy"))
                                            context.startActivity(intent)
                                        }
                                    )
                                    Text(text = " 동의 (필수)", color = Color(0xFF718096), fontSize = 12.sp)
                                }
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "만 14세 미만 자녀 정보 수집·이용",
                                        color = Color(0xFF3182CE),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.clickable {
                                            val serverUrl = preferenceHelper.serverUrl.ifBlank { "https://www.ai-gochi.com" }
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("${serverUrl.trim().removeSuffix("/")}/child-privacy"))
                                            context.startActivity(intent)
                                        }
                                    )
                                    Text(text = " 동의 (필수)", color = Color(0xFF718096), fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "[오픈소스 라이선스 고지]",
                            color = Color(0xFF718096),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier
                                .clickable {
                                    val serverUrl = preferenceHelper.serverUrl.ifBlank { "https://www.ai-gochi.com" }
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("${serverUrl.trim().removeSuffix("/")}/licenses"))
                                    context.startActivity(intent)
                                }
                                .padding(vertical = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        if (isAuthLoading) {
                            CircularProgressIndicator(color = Color(0xFFFEE500))
                        } else {
                            Button(
                                onClick = {
                                    isAuthLoading = true
                                    val callback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
                                        if (error != null) {
                                            isAuthLoading = false
                                            Toast.makeText(context, "카카오 로그인 실패: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                                        } else if (token != null) {
                                            val accessToken = token.accessToken
                                            
                                            // Send token to backend server
                                            scope.launch {
                                                try {
                                                    val serverUrl = preferenceHelper.serverUrl.ifBlank { "http://10.0.2.2:8000" }
                                                    val customToken = GeminiService.getFirebaseCustomToken(serverUrl, "kakao", accessToken)
                                                    
                                                    auth.signInWithCustomToken(customToken)
                                                        .addOnSuccessListener {
                                                            isAuthLoading = false
                                                            Toast.makeText(context, "카카오 로그인 성공! 🎉", Toast.LENGTH_SHORT).show()
                                                        }
                                                        .addOnFailureListener { e ->
                                                            isAuthLoading = false
                                                            Toast.makeText(context, "Firebase 로그인 실패: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                        }
                                                } catch (e: Exception) {
                                                    isAuthLoading = false
                                                    Toast.makeText(context, "로그인 처리 실패: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    }

                                    if (UserApiClient.instance.isKakaoTalkLoginAvailable(context)) {
                                        UserApiClient.instance.loginWithKakaoTalk(context) { token, error ->
                                            if (error != null) {
                                                if (error is ClientError && error.reason == ClientErrorCause.Cancelled) {
                                                    isAuthLoading = false
                                                    return@loginWithKakaoTalk
                                                }
                                                UserApiClient.instance.loginWithKakaoAccount(context, callback = callback)
                                            } else {
                                                callback(token, null)
                                            }
                                        }
                                    } else {
                                        UserApiClient.instance.loginWithKakaoAccount(context, callback = callback)
                                    }
                                },
                                enabled = !isAuthLoading && isAgreed,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFEE500),
                                    disabledContainerColor = Color(0xFFE2E8F0),
                                    disabledContentColor = Color(0xFFA0AEC0)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Text(
                                    text = "Kakao 아이디로 로그인",
                                    color = if (isAgreed) Color(0xFF191919) else Color(0xFFA0AEC0),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        } else if (reviewerName.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF7FAFC))
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "👋 호칭 설정",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2D3748)
                        )

                        Text(
                            text = "아이에게 칭찬 및 피드백 시 표시될 보호자님의 호칭을 입력해 주세요.\n(예: 엄마, 아빠, 선생님, 이모, 삼촌)",
                            fontSize = 14.sp,
                            color = Color(0xFF718096),
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )

                        OutlinedTextField(
                            value = pairingNicknameInput,
                            onValueChange = { pairingNicknameInput = it },
                            label = { Text("호칭 입력") },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.Black),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                val cleanNickname = pairingNicknameInput.trim()
                                if (cleanNickname.isEmpty()) {
                                    Toast.makeText(context, "호칭을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isPairingInProgress = true
                                val reviewerData = mapOf(
                                    "reviewerUid" to uid,
                                    "name" to cleanNickname,
                                    "pairedChildren" to emptyList<String>(),
                                    "freePromotionUsed" to false
                                )
                                db.collection("reviewers").document(uid)
                                    .set(reviewerData, SetOptions.merge())
                                    .addOnSuccessListener {
                                        preferenceHelper.reviewerName = cleanNickname
                                        reviewerName = cleanNickname
                                        isPairingInProgress = false
                                        Toast.makeText(context, "호칭이 설정되었습니다! 🎉", Toast.LENGTH_SHORT).show()
                                    }
                                    .addOnFailureListener { e ->
                                        isPairingInProgress = false
                                        Toast.makeText(context, "호칭 설정 실패: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                            },
                            enabled = !isPairingInProgress,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3182CE)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            if (isPairingInProgress) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Text("설정 완료", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            // Main Dashboard Screen (Paired layout)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF7FAFC))
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Connected children list selectors
                Text(
                    text = "👥 연결된 자녀/학생 목록",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4A5568)
                )

                if (isLoadingChildren) {
                    Box(modifier = Modifier.fillMaxWidth().height(50.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else if (childrenList.isEmpty()) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "👦 연결된 자녀가 없습니다",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2D3748)
                            )
                            Text(
                                text = "자녀의 앱 화면에 표시된 6자리 페어링 코드를 등록하여 연동을 시작하세요.\n연동 완료 시 아이의 일기 분석 내역을 실시간으로 확인하실 수 있습니다.",
                                fontSize = 13.sp,
                                color = Color(0xFF718096),
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                            Button(
                                onClick = { showPairingDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3182CE)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text("자녀 연결하기", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        childrenList.forEach { child ->
                            val id = child["childId"] as? String ?: ""
                            val name = child["childName"] as? String ?: "무명"
                            val isSelected = id == selectedChildId

                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedChildId = id },
                                label = { Text(text = "👦 $name") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFEBF8FF),
                                    selectedLabelColor = Color(0xFF2B6CB0)
                                )
                            )
                        }
                    }
                }

                // 🪙 자녀 마법이슬 현황 및 충전 카드
                if (selectedChildId.isNotEmpty()) {
                    val selectedChild = childrenList.find { (it["childId"] as? String) == selectedChildId }
                    if (selectedChild != null) {
                        val credits = (selectedChild["credits"] as? Long) ?: 3L
                        val totalCredits = (selectedChild["totalCreditsGranted"] as? Long) ?: 3L
                        val childName = (selectedChild["childName"] as? String) ?: "아이"
                        
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF5)),
                            border = BorderStroke(1.dp, Color(0xFFFBD38D)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(text = "🪙", fontSize = 22.sp)
                                        Column {
                                            Text(
                                                text = "${childName}의 남은 마법이슬: ${credits}개",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = Color(0xFFB7791F)
                                            )
                                            Text(
                                                text = "사용 가능 마법이슬: ${credits} / ${totalCredits}개",
                                                fontSize = 11.sp,
                                                color = Color(0xFF744210)
                                            )
                                        }
                                    }

                                    val primaryParentId = selectedChild["primaryParentId"] as? String ?: ""
                                    val isPrimary = primaryParentId == uid

                                    Button(
                                        onClick = {
                                            showPurchaseDialog = true
                                        },
                                        enabled = isPrimary,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isPrimary) Color(0xFFD69E2E) else Color(0xFFCBD5E0)
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "마법이슬 충전",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (isPrimary) Color.White else Color(0xFF718096)
                                        )
                                    }
                                }

                                val primaryParentId = selectedChild["primaryParentId"] as? String ?: ""
                                if (primaryParentId.isNotEmpty() && primaryParentId != uid) {
                                    Text(
                                        text = "⚠️ 다른 보호자가 대표(결제 권한자)로 지정되어 있습니다.",
                                        fontSize = 11.sp,
                                        color = Color(0xFFE53E3E),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                HorizontalDivider(color = Color(0xFFFBD38D))

                                var isUnpairingInProgress by remember { mutableStateOf(false) }

                                TextButton(
                                    onClick = {
                                        isUnpairingInProgress = true
                                        val reviewerMap = mapOf("uid" to uid, "name" to reviewerName)

                                        // 1. Remove parent from child's pairedReviewers
                                        db.collection("children").document(selectedChildId)
                                            .update("pairedReviewers", FieldValue.arrayRemove(reviewerMap))
                                            .addOnSuccessListener {
                                                // 2. Remove child from parent's pairedChildren
                                                db.collection("reviewers").document(uid)
                                                    .update("pairedChildren", FieldValue.arrayRemove(selectedChildId))
                                                    .addOnSuccessListener {
                                                        // 3. Unsubscribe from topic
                                                        FirebaseMessaging.getInstance().unsubscribeFromTopic("child_$selectedChildId")
                                                        Toast.makeText(context, "${childName} 연결이 해제되었습니다. 👋", Toast.LENGTH_SHORT).show()

                                                        selectedChildId = ""
                                                        isUnpairingInProgress = false
                                                    }
                                                    .addOnFailureListener { e ->
                                                        isUnpairingInProgress = false
                                                        Toast.makeText(context, "연결 해제 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                    }
                                            }
                                            .addOnFailureListener { e ->
                                                isUnpairingInProgress = false
                                                Toast.makeText(context, "연결 해제 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            }
                                    },
                                    enabled = !isUnpairingInProgress,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE53E3E))
                                ) {
                                    if (isUnpairingInProgress) {
                                        CircularProgressIndicator(color = Color(0xFFE53E3E), modifier = Modifier.size(20.dp))
                                    } else {
                                        Text(
                                            text = "❌ 이 자녀 연결 해제하기 (내 목록에서 삭제)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFE2E8F0))

                // Selected Child's Diaries List
                Text(
                    text = "✍️ 일기 첨삭 보고서 목록",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2D3748)
                )

                if (isLoadingDiaries) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (diariesList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (selectedChildId.isEmpty()) "자녀를 먼저 선택해 주세요." else "아직 등록된 일기가 없습니다.\n아이가 일기 쓰기를 완료하면 실시간으로 여기에 노출됩니다!",
                            color = Color(0xFF718096),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(diariesList, key = { it.id }) { report ->
                            val dateStr = remember(report.timestamp) {
                                SimpleDateFormat("yyyy년 MM월 dd일 HH:mm", Locale.getDefault()).format(Date(report.timestamp))
                            }

                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToDetail("$selectedChildId/${report.id}") } // Combined ID format
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "👦 ${report.name} 어린이의 일기 보고서",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Color(0xFF2D3748)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = dateStr,
                                            fontSize = 12.sp,
                                            color = Color(0xFF718096)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            val spellingText = if (report.secondSpellingScore == 0) {
                                                "맞춤법: ${report.firstSpellingScore}"
                                            } else {
                                                "맞춤법: ${report.firstSpellingScore} ➡️ ${report.secondSpellingScore}"
                                            }
                                            val expressionText = if (report.secondExpressionScore == 0) {
                                                "표현력: ${report.firstExpressionScore}"
                                            } else {
                                                "표현력: ${report.firstExpressionScore} ➡️ ${report.secondExpressionScore}"
                                            }
                                            Text(
                                                text = spellingText,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF3182CE)
                                            )
                                            Text(
                                                text = expressionText,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF48BB78)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            db.collection("children").document(selectedChildId)
                                                .collection("diaries").document(report.id)
                                                .delete()
                                                .addOnSuccessListener {
                                                    Toast.makeText(context, "보고서가 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                                                }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "삭제",
                                            tint = Color(0xFFE53E3E)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dynamic Add Pairing Dialog
    if (showPairingDialog) {
        AlertDialog(
            onDismissRequest = { showPairingDialog = false },
            title = { Text("새로운 자녀/교사 추가 연결", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("추가할 아이 스마트폰 화면의 6자리 인증 코드를 입력해주세요.")
                    OutlinedTextField(
                        value = pairingCodeInput,
                        onValueChange = { pairingCodeInput = it },
                        placeholder = { Text("6자리 코드 입력") },
                        textStyle = TextStyle(color = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pairingCodeInput.trim().length != 6) {
                            Toast.makeText(context, "6자리 코드를 입력해주세요.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isPairingInProgress = true
                        val cleanCode = pairingCodeInput.trim()

                        db.collection("children")
                            .whereEqualTo("pairingCode", cleanCode)
                            .get()
                            .addOnSuccessListener { querySnapshot ->
                                val doc = querySnapshot.documents.firstOrNull()
                                if (doc != null && doc.exists()) {
                                    val expires = doc.getLong("pairingCodeExpires") ?: 0L
                                    if (expires < System.currentTimeMillis() / 1000) {
                                        isPairingInProgress = false
                                        Toast.makeText(context, "만료된 코드입니다.", Toast.LENGTH_LONG).show()
                                        return@addOnSuccessListener
                                    }

                                    val childId = doc.getString("childId") ?: ""
                                    val childName = doc.getString("childName") ?: ""

                                    val reviewerMap = mapOf("uid" to uid, "name" to reviewerName)
                                    val currentReviewers = doc.get("pairedReviewers") as? List<*> ?: emptyList<Any>()
                                    val isFirstParent = currentReviewers.isEmpty()
                                    val updateMap = mutableMapOf<String, Any>(
                                        "pairedReviewers" to FieldValue.arrayUnion(reviewerMap)
                                    )
                                    if (isFirstParent) {
                                        updateMap["primaryParentId"] = uid
                                    }
                                    
                                    db.collection("children").document(childId)
                                        .update(updateMap)
                                        .addOnSuccessListener {
                                            db.collection("reviewers").document(uid).get()
                                                .addOnSuccessListener { reviewerDoc ->
                                                    val freePromotionUsed = reviewerDoc.getBoolean("freePromotionUsed") ?: false
                                                    
                                                    if (!freePromotionUsed) {
                                                        db.collection("children").document(childId)
                                                            .update("credits", 3, "totalCreditsGranted", 3)
                                                            .addOnSuccessListener {
                                                                val reviewerData = mapOf(
                                                                     "reviewerUid" to uid,
                                                                     "name" to reviewerName,
                                                                     "pairedChildren" to FieldValue.arrayUnion(childId),
                                                                     "freePromotionUsed" to true
                                                                 )
                                                                 db.collection("reviewers").document(uid)
                                                                     .set(reviewerData, SetOptions.merge())
                                                                    .addOnSuccessListener {
                                                                        selectedChildId = childId
                                                                        showPairingDialog = false
                                                                        pairingCodeInput = ""
                                                                        isPairingInProgress = false
                                                                        FirebaseMessaging.getInstance().subscribeToTopic("child_$childId")
                                                                        Toast.makeText(context, "${childName} 연결 성공! 신규 가입 무료 마법이슬 3개가 지급되었습니다! 🎁", Toast.LENGTH_LONG).show()
                                                                    }
                                                                    .addOnFailureListener { e ->
                                                                        isPairingInProgress = false
                                                                        Toast.makeText(context, "보호자 정보 등록 실패: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                                    }
                                                            }
                                                            .addOnFailureListener { e ->
                                                                isPairingInProgress = false
                                                                Toast.makeText(context, "자녀 마법이슬 지급 실패: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                            }
                                                    } else {
                                                        val reviewerData = mapOf(
                                                             "reviewerUid" to uid,
                                                             "name" to reviewerName,
                                                             "pairedChildren" to FieldValue.arrayUnion(childId)
                                                         )
                                                         db.collection("reviewers").document(uid)
                                                             .set(reviewerData, SetOptions.merge())
                                                            .addOnSuccessListener {
                                                                selectedChildId = childId
                                                                showPairingDialog = false
                                                                pairingCodeInput = ""
                                                                isPairingInProgress = false
                                                                FirebaseMessaging.getInstance().subscribeToTopic("child_$childId")
                                                                Toast.makeText(context, "${childName} 연결 성공! (기존에 무료 프로모션을 이미 사용하여 마법이슬이 추가되지 않았습니다.) 🔗", Toast.LENGTH_LONG).show()
                                                            }
                                                            .addOnFailureListener { e ->
                                                                isPairingInProgress = false
                                                                Toast.makeText(context, "보호자 자녀 목록 업데이트 실패: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                            }
                                                    }
                                                }
                                                .addOnFailureListener { e ->
                                                    isPairingInProgress = false
                                                    Toast.makeText(context, "보호자 정보 확인 실패: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                }
                                        }
                                        .addOnFailureListener { e ->
                                            isPairingInProgress = false
                                            Toast.makeText(context, "자녀 연결 정보 업데이트 실패: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                        }
                                } else {
                                    isPairingInProgress = false
                                    Toast.makeText(context, "코드를 찾을 수 없습니다.", Toast.LENGTH_LONG).show()
                                }
                            }
                            .addOnFailureListener {
                                isPairingInProgress = false
                                Toast.makeText(context, "연결 실패: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                    },
                    enabled = !isPairingInProgress
                ) {
                    if (isPairingInProgress) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Text("연결하기")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showPairingDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    if (showPurchaseDialog && selectedChildId.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showPurchaseDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🪙 마법이슬 충전소", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "자녀의 첨삭 지도를 위한 마법이슬을 구매합니다. 결제 완료 즉시 자녀의 계정에 마법이슬이 지급됩니다.",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))

                    val products = listOf(
                        Triple("magical_dew_1", "마법이슬 1개", "1,000원"),
                        Triple("magical_dew_10", "마법이슬 10개", "9,900원"),
                        Triple("magical_dew_30", "마법이슬 30개", "27,000원"),
                        Triple("magical_dew_subscription", "정기 구독 (월 100개)", "39,000원 / 월")
                    )

                    products.forEach { (productId, productName, price) ->
                        val isSub = productId == "magical_dew_subscription"
                        
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSub) Color(0xFFFEF3C7) else Color(0xFFF3F4F6)
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSub) Color(0xFFF59E0B) else Color(0xFFD1D5DB)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    if (activity != null) {
                                        billingHelper.launchBillingFlow(activity, productId, isSub, selectedChildId)
                                        showPurchaseDialog = false
                                    } else {
                                        Toast.makeText(context, "결제 창을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = productName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (isSub) Color(0xFFB45309) else Color(0xFF1F2937)
                                    )
                                    Text(
                                        text = if (isSub) "매달 마법이슬 100개 정기 충전" else "${productName.replace("마법이슬 ", "")} 즉시 충전",
                                        fontSize = 11.sp,
                                        color = if (isSub) Color(0xFFD97706) else Color(0xFF6B7280)
                                    )
                                }
                                Text(
                                    text = price,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = if (isSub) Color(0xFFB45309) else Color(0xFF2563EB)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPurchaseDialog = false }) {
                    Text("닫기", color = Color(0xFF4B5563))
                }
            }
        )
    }
}
