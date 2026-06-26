# 🛠️ AI고치 보호자용 앱 개발 진행 상황 및 가이드 (DEVELOPMENT_LOG)

이 문서는 다른 컴퓨터에서 작업을 이어받거나 개발 히스토리를 한눈에 확인하여 다음 작업을 즉시 이어나갈 수 있도록 정리한 로그 파일입니다.

---

## 📌 1. 현재까지 완료된 주요 작업 내역

### ① 보호자용 앱 패키지명(Application ID) 변경
* **내용**: 구글 플레이 콘솔의 패키지명 중복 이슈 해결을 위해 식별 ID를 변경했습니다.
* **적용 파일**: 
  - `app/build.gradle.kts`: `applicationId = "com.aigochi.parent"`로 변경
  - `GeminiService.kt`: 구글 플레이 영수증 검증 API 호출 패키지명을 `"com.aigochi.parent"`로 디폴트 파라미터 변경
  - `google-services.json`: 임시 로컬 빌드가 가능하도록 패키지명 필드를 `"com.aigochi.parent"`로 임시 수정

### ② 구글 플레이 심사 통과를 위한 데모 로그인 기능 탑재
* **내용**: 구글 심사관들이 카카오 로그인 인증 절차 없이 즉시 앱 내부를 볼 수 있도록 우회 로그인 버튼을 추가했습니다.
* **적용 화면**: 보호자 로그인 페이지 하단에 **`데모 계정으로 로그인 (구글 심사용)`** 버튼 추가.
* **동작 방식**: 클릭 시 Firebase Auth의 `tester@aigochi.com` / `test1234` 계정으로 즉시 로그인을 시도하고, 로그인 성공 시 Firestore `reviewers` 컬렉션에 구글 심사관 정보를 자동 생성/연동하여 보호자 대시보드로 진입시킵니다.

### ③ AdMob 배너 광고 연동
* **내용**: 보호자용 대시보드 하단 영역에 실물 배너 광고를 탑재 완료했습니다.
* **설정 값**:
  - AdMob App ID: `ca-app-pub-5254974097452914~8945949666`
  - 배너 광고 단위 ID: `ca-app-pub-5254974097452914/8945949666`
* **적용 파일**: `MainActivity.kt` (초기화), `ParentHomeScreen.kt` (배너 UI 렌더링), `AndroidManifest.xml` (메타데이터 설정)

### ④ 카카오 로그인 정식 상용 키 이관 및 오류 수정
* **내용**: 정식 카카오 키 이관 및 가입 연동 오류를 해결했습니다.
* **적용 파일**:
  - `ParentApplication.kt` / `AndroidManifest.xml`: 정식 네이티브 앱 키(`6a6d42054298ea6c7b9d62bf919a148a`) 및 scheme 적용
  - 웹 백엔드(`main.py`): 정식 REST API 키(`1c784d641fd28c3fb2cb7d67d22980e2`) 설정 완료
  - **EMAIL_EXISTS 오류 수정**: 중복 가입을 시도할 때 에러를 발생시키는 대신 Firebase UID와 카카오 연동을 동기화 처리하도록 수정.
  - **어드민 목록 연동**: 카카오 일반 로그인 사용자도 어드민 회원 목록에 즉시 동기화되도록 연동 추가 및 닉네임 검색 필터 고도화.

---

## ⚡ 2. 다른 컴퓨터에서 작업을 이어서 시작할 때 (준비 단계)

1. **저장소 복제 및 코드 최신화**:
   ```bash
   git clone https://github.com/acoustjk/ai_diary_parent.git
   # 또는 이미 복제되어 있다면
   git pull origin main
   ```
2. **빌드 도구 환경 설정**:
   - Android Studio가 필요하며, 빌드 실행 시 Gradle 데몬이 올바른 JDK를 바라보도록 설정해주어야 합니다.
   - 예시 (Windows 환경 변수 설정 스크립트):
     ```powershell
     $env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
     .\gradlew.bat assembleDebug
     ```

---

## 🚀 3. 다음으로 진행해야 할 작업 목록 (TODO)

### ⬜ [1] Firebase 콘솔에 패키지명 등록 및 json 덮어쓰기 (필수)
현재 코드는 빌드 테스트용 임시 `google-services.json`을 사용하고 있으므로, Firebase 기능이 실기기에서 정상 동작하려면 새 앱을 Firebase 프로젝트에 등록해야 합니다.
1. [Firebase 콘솔](https://console.firebase.google.com/) ➡️ **[프로젝트 설정]** ➡️ **[일반]** 탭 진입.
2. 하단의 내 앱 영역에서 **[앱 추가]** ➡️ **[Android]** 선택.
3. 패키지 이름에 **`com.aigochi.parent`** 입력 후 앱 등록.
4. 새로 발급되는 **`google-services.json`** 파일을 다운로드하여 프로젝트 내 `app/` 폴더에 덮어씌웁니다.

### ⬜ [2] Firebase Auth에 데모 계정 수동 등록 (필수)
심사용 우회 버튼이 정상 작동하도록 Firebase DB에 계정을 활성화해주어야 합니다.
1. Firebase 콘솔 ➡️ **[Authentication]** ➡️ **[Users]** 탭 진입.
2. **[사용자 추가]** 버튼 클릭.
3. 이메일: **`tester@aigochi.com`** / 비밀번호: **`test1234`**로 추가.
4. *참고: 만약 이메일 로그인 기능이 비활성화 상태라면 [Sign-in method] 탭에서 '이메일/비밀번호'를 '사용 설정됨(Enabled)' 상태로 변경해주세요.*

### ⬜ [3] 카카오 디벨로퍼스 Android 플랫폼 패키지명 변경 (필수)
1. [카카오 디벨로퍼스 콘솔](https://developers.kakao.com/) ➡️ 내 애플리케이션 ➡️ **[플랫폼]** ➡️ **[Android]** 이동.
2. 패키지명을 기존 `com.example.aidiarycheomsak.parent`에서 **`com.aigochi.parent`**로 변경하여 저장합니다.

### ⬜ [4] 구글 플레이 콘솔에 업로드 및 내부 테스트 개시
1. 빌드 완료된 APK 파일(`parent-debug.apk`)을 구글 플레이 콘솔의 **[내부 테스트]** 트랙에 업로드합니다.
2. 테스터 목록에 본인 구글 이메일을 등록한 후, 발급된 다운로드 링크를 통해 실기기에서 다운로드하여 인앱 결제 및 로그인을 테스트합니다.
