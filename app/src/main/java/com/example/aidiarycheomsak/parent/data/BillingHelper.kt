package com.example.aidiarycheomsak.parent.data

import android.app.Activity
import android.content.Context
import android.widget.Toast
import com.android.billingclient.api.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BillingHelper(
    private val context: Context,
    private val serverUrl: String,
    private val onPurchaseSuccess: (String, Int) -> Unit
) : PurchasesUpdatedListener {

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    private val auth = FirebaseAuth.getInstance()
    private val scope = CoroutineScope(Dispatchers.Main)
    private var currentChildId: String? = null

    init {
        startConnection()
    }

    fun startConnection(onReady: (() -> Unit)? = null) {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    onReady?.invoke()
                } else {
                    logError("Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                // Try reconnecting in production as needed
            }
        })
    }

    fun launchBillingFlow(activity: Activity, productId: String, isSubscription: Boolean, childId: String? = null) {
        currentChildId = childId
        val productType = if (isSubscription) BillingClient.ProductType.SUBS else BillingClient.ProductType.INAPP
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(productType)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                val productDetails = productDetailsList[0]
                val flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(productDetails)
                                .build()
                        )
                    )
                    .build()

                billingClient.launchBillingFlow(activity, flowParams)
            } else {
                // Fallback simulation mode if Google Play Store is not set up (essential for local testing/dev)
                scope.launch {
                    val uid = auth.currentUser?.uid ?: ""
                    if (uid.isNotEmpty()) {
                        simulateMockBillingPurchase(productId, uid)
                    } else {
                        Toast.makeText(context, "로그인 후 결제가 가능합니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    handlePurchase(purchase)
                }
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Toast.makeText(context, "결제가 취소되었습니다.", Toast.LENGTH_SHORT).show()
        } else {
            logError("Purchase error: ${billingResult.debugMessage}")
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        val parentUid = auth.currentUser?.uid ?: ""
        if (parentUid.isEmpty()) {
            Toast.makeText(context, "결제 승인 오류: 로그인 계정을 확인할 수 없습니다.", Toast.LENGTH_LONG).show()
            return
        }

        scope.launch {
            try {
                val productId = purchase.products.firstOrNull() ?: ""
                val purchaseToken = purchase.purchaseToken

                val verified = withContext(Dispatchers.IO) {
                    GeminiService.verifyGooglePlayPurchase(
                        serverUrl = serverUrl,
                        purchaseToken = purchaseToken,
                        productId = productId,
                        parentUid = parentUid,
                        childId = currentChildId
                    )
                }

                if (verified) {
                    val isSubscription = productId == "magical_dew_subscription"
                    if (isSubscription) {
                        acknowledgeSubscription(purchase)
                    } else {
                        consumeConsumable(purchase)
                    }
                    
                    val creditsAdded = getCreditsForProduct(productId)
                    onPurchaseSuccess(productId, creditsAdded)
                } else {
                    Toast.makeText(context, "결제 영수증 검증에 실패했습니다. 고객센터로 문의해 주세요.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "결제 처리 중 서버 통신 오류가 발생했습니다: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun consumeConsumable(purchase: Purchase) {
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.consumeAsync(consumeParams) { billingResult, _ ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                logError("Failed to consume purchase: ${billingResult.debugMessage}")
            }
        }
    }

    private fun acknowledgeSubscription(purchase: Purchase) {
        val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(acknowledgeParams) { billingResult ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                logError("Failed to acknowledge subscription: ${billingResult.debugMessage}")
            }
        }
    }

    private suspend fun simulateMockBillingPurchase(productId: String, parentUid: String) {
        Toast.makeText(context, "[시뮬레이션] Google Play가 로드되지 않아 가상 결제로 진행합니다.", Toast.LENGTH_SHORT).show()
        val mockToken = "mock_google_token_" + System.currentTimeMillis()
        try {
            val verified = withContext(Dispatchers.IO) {
                GeminiService.verifyGooglePlayPurchase(
                    serverUrl = serverUrl,
                    purchaseToken = mockToken,
                    productId = productId,
                    parentUid = parentUid,
                    childId = currentChildId
                )
            }
            if (verified) {
                val creditsAdded = getCreditsForProduct(productId)
                onPurchaseSuccess(productId, creditsAdded)
            } else {
                Toast.makeText(context, "가상 결제 검증에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "가상 결제 통신 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getCreditsForProduct(productId: String): Int {
        return when (productId) {
            "magical_dew_1" -> 1
            "magical_dew_10" -> 10
            "magical_dew_30" -> 30
            "magical_dew_subscription" -> 100
            else -> 0
        }
    }

    private fun logError(message: String) {
        Toast.makeText(context, "결제 오류: $message", Toast.LENGTH_LONG).show()
    }
}
