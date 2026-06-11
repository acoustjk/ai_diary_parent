package com.example.aidiarycheomsak.parent.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
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
    val currentUser = auth.currentUser

    // State variables
    var reviewerName by remember { mutableStateOf(preferenceHelper.reviewerName) }
    var childrenList by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var selectedChildId by remember { mutableStateOf("") }
    var diariesList by remember { mutableStateOf<List<DiaryReport>>(emptyList()) }
    var isLoadingChildren by remember { mutableStateOf(false) }
    var isLoadingDiaries by remember { mutableStateOf(false) }

    // Dialog state
    var showPairingDialog by remember { mutableStateOf(false) }
    var pairingCodeInput by remember { mutableStateOf("") }
    var pairingNicknameInput by remember { mutableStateOf("") }
    var isPairingInProgress by remember { mutableStateOf(false) }

    // Initial setup auth monitor
    val uid = currentUser?.uid ?: ""

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
                        
                        // Default select first child if current selection is empty or not in the list
                        if (selectedChildId.isEmpty() && list.isNotEmpty()) {
                            selectedChildId = list[0]["childId"] as? String ?: ""
                        }
                    }
                    isLoadingChildren = false
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
                                timestamp = doc.getLong("timestamp") ?: 0L
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
                title = { Text("👩‍👦 AI고치 부모용 대시보드", fontWeight = FontWeight.Bold) },
                actions = {
                    if (reviewerName.isNotEmpty()) {
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
            // Requirement 2: AdMob Banner
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
        },
        modifier = modifier
    ) { innerPadding ->
        // Onboarding / Setup Profile Screen (If parent nickname is empty)
        if (reviewerName.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF7FAFC))
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "👋 환영합니다! 보호자 연결",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2D3748)
                        )

                        Text(
                            text = "아이의 AI고치 분석 결과를 실시간으로 확인하기 위해, 호칭과 페어링 코드를 입력해 주세요.",
                            fontSize = 13.sp,
                            color = Color(0xFF718096),
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )

                        OutlinedTextField(
                            value = pairingNicknameInput,
                            onValueChange = { pairingNicknameInput = it },
                            label = { Text("보호자 호칭 (예: 엄마, 아빠, 선생님)") },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.Black),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = pairingCodeInput,
                            onValueChange = { pairingCodeInput = it },
                            label = { Text("아이 앱의 6자리 페어링 코드") },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.Black),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                if (pairingNicknameInput.trim().isEmpty() || pairingCodeInput.trim().length != 6) {
                                    Toast.makeText(context, "올바른 호칭과 6자리 코드를 입력해주세요.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isPairingInProgress = true
                                val cleanCode = pairingCodeInput.trim()
                                val cleanNickname = pairingNicknameInput.trim()

                                // Query child by code
                                db.collection("children")
                                    .whereEqualTo("pairingCode", cleanCode)
                                    .get()
                                    .addOnSuccessListener { querySnapshot ->
                                        val doc = querySnapshot.documents.firstOrNull()
                                        if (doc != null && doc.exists()) {
                                            val expires = doc.getLong("pairingCodeExpires") ?: 0L
                                            if (expires < System.currentTimeMillis() / 1000) {
                                                isPairingInProgress = false
                                                Toast.makeText(context, "만료된 코드입니다. 아이 앱에서 다시 발급받아 주세요.", Toast.LENGTH_LONG).show()
                                                return@addOnSuccessListener
                                            }

                                            val childId = doc.getString("childId") ?: ""
                                            val childName = doc.getString("childName") ?: ""

                                            // Update relations
                                            val reviewerMap = mapOf("uid" to uid, "name" to cleanNickname)
                                            
                                            // 1. Update Child Doc with Reviewer info
                                            db.collection("children").document(childId)
                                                .update("pairedReviewers", FieldValue.arrayUnion(reviewerMap))
                                                .addOnSuccessListener {
                                                    // 2. Update/Create Reviewer Doc with Child info
                                                    val reviewerData = mapOf(
                                                        "reviewerUid" to uid,
                                                        "name" to cleanNickname,
                                                        "pairedChildren" to FieldValue.arrayUnion(childId)
                                                    )
                                                    db.collection("reviewers").document(uid)
                                                        .set(reviewerData, SetOptions.merge())
                                                        .addOnSuccessListener {
                                                            preferenceHelper.reviewerName = cleanNickname
                                                            reviewerName = cleanNickname
                                                            selectedChildId = childId
                                                            isPairingInProgress = false
                                                            FirebaseMessaging.getInstance().subscribeToTopic("child_$childId")
                                                            Toast.makeText(context, "${childName} 어린이 연결 성공! 🎉", Toast.LENGTH_SHORT).show()
                                                        }
                                                }
                                        } else {
                                            isPairingInProgress = false
                                            Toast.makeText(context, "일치하는 코드를 찾을 수 없습니다. 코드를 다시 확인해 주세요.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                    .addOnFailureListener {
                                        isPairingInProgress = false
                                        Toast.makeText(context, "연결 실패: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                            },
                            enabled = !isPairingInProgress,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3182CE)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            if (isPairingInProgress) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Text("연결하기", fontWeight = FontWeight.Bold)
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
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5F5)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "연결된 자녀가 없습니다.\n우측 상단의 '+' 버튼을 눌러 연동해 주세요.",
                                fontSize = 12.sp,
                                color = Color(0xFFC53030)
                            )
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
                                    
                                    db.collection("children").document(childId)
                                        .update("pairedReviewers", FieldValue.arrayUnion(reviewerMap))
                                        .addOnSuccessListener {
                                            db.collection("reviewers").document(uid)
                                                .update("pairedChildren", FieldValue.arrayUnion(childId))
                                                .addOnSuccessListener {
                                                    selectedChildId = childId
                                                    showPairingDialog = false
                                                    pairingCodeInput = ""
                                                    isPairingInProgress = false
                                                    FirebaseMessaging.getInstance().subscribeToTopic("child_$childId")
                                                    Toast.makeText(context, "${childName} 어린이 추가 연결 성공! 🎉", Toast.LENGTH_SHORT).show()
                                                }
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
}
