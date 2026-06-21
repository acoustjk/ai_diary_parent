package com.example.aidiarycheomsak.parent.ui

import android.widget.Toast
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aidiarycheomsak.parent.data.PreferenceHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onRoleChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val preferenceHelper = remember { PreferenceHelper(context) }
    val auth = remember { FirebaseAuth.getInstance() }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var isSavingName by remember { mutableStateOf(false) }
    var nicknameInput by remember { mutableStateOf(preferenceHelper.reviewerName) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF7FAFC)
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF7FAFC))
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 👤 보호자 계정 정보 카드
            val currentUser = auth.currentUser
            val email = currentUser?.email ?: "이메일 로그인 없음 (소셜)"
            val reviewerName = preferenceHelper.reviewerName.ifBlank { "보호자" }

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "👤 보호자 계정 정보",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2D3748),
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "호칭: $reviewerName 님",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF4A5568)
                        )
                        Text(
                            text = "로그인 계정: $email",
                            fontSize = 13.sp,
                            color = Color(0xFF718096)
                        )
                    }

                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    // ✏️ 호칭 변경 섹션
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "✏️ 호칭 변경",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2D3748)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = nicknameInput,
                                onValueChange = { nicknameInput = it },
                                placeholder = { Text("변경할 호칭 입력") },
                                singleLine = true,
                                enabled = !isSavingName && !isProcessing,
                                textStyle = TextStyle(color = Color.Black),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val oldName = preferenceHelper.reviewerName
                                    val newName = nicknameInput.trim()
                                    if (newName.isEmpty()) {
                                        Toast.makeText(context, "호칭을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isSavingName = true
                                    val db = FirebaseFirestore.getInstance()
                                    val uid = currentUser?.uid ?: ""
                                    if (uid.isNotEmpty()) {
                                        db.collection("reviewers").document(uid).get()
                                            .addOnSuccessListener { doc ->
                                                val pairedChildren = doc.get("pairedChildren") as? List<String> ?: emptyList()
                                                val batch = db.batch()
                                                val oldReviewerMap = mapOf("uid" to uid, "name" to oldName)
                                                val newReviewerMap = mapOf("uid" to uid, "name" to newName)

                                                for (childId in pairedChildren) {
                                                    val childRef = db.collection("children").document(childId)
                                                    batch.update(childRef, "pairedReviewers", FieldValue.arrayRemove(oldReviewerMap))
                                                    batch.update(childRef, "pairedReviewers", FieldValue.arrayUnion(newReviewerMap))
                                                }

                                                batch.update(db.collection("reviewers").document(uid), "name", newName)

                                                batch.commit().addOnSuccessListener {
                                                    preferenceHelper.reviewerName = newName
                                                    isSavingName = false
                                                    Toast.makeText(context, "호칭이 변경되었습니다! 🎉", Toast.LENGTH_SHORT).show()
                                                }.addOnFailureListener { e ->
                                                    isSavingName = false
                                                    Toast.makeText(context, "호칭 변경 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            .addOnFailureListener { e ->
                                                db.collection("reviewers").document(uid)
                                                    .update("name", newName)
                                                    .addOnSuccessListener {
                                                        preferenceHelper.reviewerName = newName
                                                        isSavingName = false
                                                        Toast.makeText(context, "호칭이 변경되었습니다! 🎉", Toast.LENGTH_SHORT).show()
                                                    }
                                                    .addOnFailureListener { err ->
                                                        isSavingName = false
                                                        Toast.makeText(context, "호칭 변경 실패: ${err.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                    }
                                            }
                                    } else {
                                        isSavingName = false
                                    }
                                },
                                enabled = !isSavingName && !isProcessing && nicknameInput.trim().isNotEmpty() && nicknameInput.trim() != reviewerName,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3182CE)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                if (isSavingName) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                                } else {
                                    Text("저장", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    if (isProcessing) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFFE53E3E))
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    auth.signOut()
                                    preferenceHelper.reviewerName = ""
                                    Toast.makeText(context, "로그아웃되었습니다. 초기 화면으로 이동합니다.", Toast.LENGTH_SHORT).show()
                                    onRoleChanged()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A5568)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("로그아웃", color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { showDeleteConfirmDialog = true },
                                border = BorderStroke(1.dp, Color(0xFFE53E3E)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53E3E)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("회원탈퇴 (계정 삭제)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
            }

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "📄 약관 및 라이선스",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2D3748)
                    )

                    HorizontalDivider(color = Color(0xFFE2E8F0))

                    Text(
                        text = "🏠 AI고치 홈페이지 바로가기",
                        color = Color(0xFF3182CE),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ai-gochi.com"))
                                context.startActivity(intent)
                            }
                            .padding(vertical = 8.dp)
                    )

                    Text(
                        text = "이용약관",
                        color = Color(0xFF3182CE),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val serverUrl = preferenceHelper.serverUrl.ifBlank { "https://ai-gochi.com" }
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("${serverUrl.trim().removeSuffix("/")}/terms"))
                                context.startActivity(intent)
                            }
                            .padding(vertical = 8.dp)
                    )

                    Text(
                        text = "개인정보 처리방침",
                        color = Color(0xFF3182CE),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val serverUrl = preferenceHelper.serverUrl.ifBlank { "https://ai-gochi.com" }
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("${serverUrl.trim().removeSuffix("/")}/privacy"))
                                context.startActivity(intent)
                            }
                            .padding(vertical = 8.dp)
                    )

                    Text(
                        text = "오픈소스 라이선스 고지",
                        color = Color(0xFF3182CE),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val serverUrl = preferenceHelper.serverUrl.ifBlank { "https://ai-gochi.com" }
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("${serverUrl.trim().removeSuffix("/")}/licenses"))
                                context.startActivity(intent)
                            }
                            .padding(vertical = 8.dp)
                    )
                }
            }
        }
    }

        // ⚠️ 회원탈퇴 확인 다이얼로그
        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text("⚠️ 회원 탈퇴 확인", fontWeight = FontWeight.Bold) },
                text = { Text("정말로 탈퇴하시겠습니까?\n탈퇴 시 자녀와의 연결 관계 및 모든 설정 정보가 즉시 파기되며 복구할 수 없습니다.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirmDialog = false
                            val currentUser = auth.currentUser
                            if (currentUser != null) {
                                isProcessing = true
                                val db = FirebaseFirestore.getInstance()
                                val uid = currentUser.uid
                                val name = preferenceHelper.reviewerName

                                // 1. Fetch reviewer details to get paired children list
                                db.collection("reviewers").document(uid).get()
                                    .addOnSuccessListener { doc ->
                                        val pairedChildren = doc.get("pairedChildren") as? List<String> ?: emptyList()
                                        val batch = db.batch()
                                        val reviewerMap = mapOf("uid" to uid, "name" to name)

                                        // 2. Unpair from each child
                                        for (childId in pairedChildren) {
                                            val childRef = db.collection("children").document(childId)
                                            batch.update(childRef, "pairedReviewers", FieldValue.arrayRemove(reviewerMap))
                                        }

                                        // 3. Delete reviewer document
                                        batch.delete(db.collection("reviewers").document(uid))

                                        // 4. Commit Firestore updates and delete auth user
                                        batch.commit().addOnCompleteListener { commitTask ->
                                            currentUser.delete().addOnCompleteListener { authTask ->
                                                isProcessing = false
                                                auth.signOut()
                                                preferenceHelper.reviewerName = ""
                                                Toast.makeText(context, "회원탈퇴가 완료되었습니다. 이용해 주셔서 감사합니다.", Toast.LENGTH_LONG).show()
                                                onRoleChanged()
                                            }
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        // Fallback if document fetch fails
                                        currentUser.delete().addOnCompleteListener {
                                            isProcessing = false
                                            auth.signOut()
                                            preferenceHelper.reviewerName = ""
                                            Toast.makeText(context, "회원탈퇴 처리가 완료되었습니다.", Toast.LENGTH_LONG).show()
                                            onRoleChanged()
                                        }
                                    }
                            } else {
                                Toast.makeText(context, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53E3E))
                    ) {
                        Text("탈퇴하기", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("취소")
                    }
                }
            )
        }
    }
}
