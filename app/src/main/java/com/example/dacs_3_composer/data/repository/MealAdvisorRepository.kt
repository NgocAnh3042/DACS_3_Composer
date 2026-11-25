package com.example.dacs_3_composer.data.repository

import com.example.dacs_3_composer.core.model.Dish
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.google.gson.JsonParser
import java.io.IOException

data class MealRecommendation(
    val dish: Dish,
    val restaurantName: String,
    val reason: String,
    val estimatedTotal: Double
)

data class AiSelectedOption(
    val optionId: String = "",
    val groupId: String = "",
    val groupName: String = "",
    val name: String = "",
    val extraPriceVnd: Long = 0L
)

data class AiDraftItem(
    val lineId: String = "",
    val dishId: String = "",
    val restaurantId: String = "",
    val restaurantName: String = "",
    val name: String = "",
    val imageUrl: String = "",
    val category: String = "",
    val quantity: Int = 1,
    val unitPriceVnd: Long = 0L,
    val selectedOptions: List<AiSelectedOption> = emptyList(),
    val optionsExtraVnd: Long = 0L,
    val lineTotalVnd: Long = 0L,
    val note: String = ""
)

data class AiOrderDraft(
    val draftId: String = "",
    val conversationId: String = "",
    val storeId: String? = null,
    val state: String = "IDLE",
    val items: List<AiDraftItem> = emptyList(),
    val addressId: String? = null,
    val voucherCode: String? = null,
    val voucherAuto: Boolean = true,
    val paymentMethod: String? = "cash",
    val customerNote: String = "",
    val scheduledFor: String? = null,
    val reorderedFromOrderId: String? = null,
    val version: Int = 1,
    val activeQuoteId: String? = null,
    val submittedOrderId: String? = null,
    val expiresAt: String = ""
)

data class AiAddressOption(
    val id: String = "",
    val label: String = "Địa chỉ",
    val receiverName: String = "",
    val receiverPhone: String = "",
    val addressLine: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isDefault: Boolean = false
)

data class AiVoucherOption(
    val id: String = "",
    val code: String = "",
    val title: String = "",
    val description: String = "",
    val discountType: String = "",
    val discountValue: Double = 0.0,
    val maxDiscountVnd: Long? = null,
    val minOrderVnd: Long = 0L,
    val foodDiscountVnd: Long = 0L,
    val deliveryDiscountVnd: Long = 0L,
    val actualDiscountVnd: Long = 0L,
    val eligible: Boolean = false,
    val ineligibleReason: String? = null
)

data class AiEtaEstimate(
    val available: Boolean = false,
    val min: Int? = null,
    val max: Int? = null,
    val prepMinutes: Int? = null,
    val pickupMinutes: Int? = null,
    val travelMinutes: Int? = null,
    val distanceKm: Double? = null,
    val displayText: String? = null,
    val note: String? = null
)

data class AiScheduleSlot(
    val iso: String = "",
    val label: String = ""
)

data class AiUpsellSuggestion(
    val dishId: String = "",
    val dishName: String = "",
    val dishPriceVnd: Long = 0L,
    val dishImageUrl: String = "",
    val category: String = "",
    val isSideOrDrink: Boolean = false,
    val targetVoucherCode: String = "",
    val targetVoucherTitle: String = "",
    val minOrderVnd: Long = 0L,
    val missingAmountVnd: Long = 0L,
    val unlockedDiscountVnd: Long = 0L,
    val currentTotalVnd: Long = 0L,
    val projectedTotalVnd: Long = 0L,
    val netDeltaVnd: Long = 0L,
    val savingsVsCurrentVnd: Long = 0L,
    val isStrictlyCheaper: Boolean = false,
    val headline: String = "",
    val reason: String = ""
)

data class AiOrderQuote(
    val quoteId: String? = null,
    val draftId: String = "",
    val draftVersion: Int = 1,
    val currency: String = "VND",
    val restaurantId: String = "",
    val restaurantName: String = "",
    val restaurantAddress: String = "",
    val storeOpenHours: String? = null,
    val items: List<AiDraftItem> = emptyList(),
    val subtotal: Long = 0L,
    val deliveryFee: Long = 0L,
    val discount: Long = 0L,
    val foodDiscount: Long = 0L,
    val deliveryDiscount: Long = 0L,
    val serviceFee: Long = 0L,
    val tax: Long = 0L,
    val total: Long = 0L,
    val appliedVoucher: AiVoucherOption? = null,
    val address: AiAddressOption? = null,
    val paymentMethod: String = "COD",
    val customerNote: String = "",
    val scheduledFor: String? = null,
    val scheduledForDisplay: String? = null,
    val availableScheduleSlots: List<AiScheduleSlot> = emptyList(),
    val upsellSuggestion: AiUpsellSuggestion? = null,
    val reorderedFromOrderId: String? = null,
    val estimatedDeliveryMinutes: AiEtaEstimate? = null,
    val expiresAt: String? = null,
    val expiresInSeconds: Int? = null,
    val confirmationToken: String? = null,
    val warnings: List<String> = emptyList(),
    val requiresConfirmation: Boolean = true,
    val canConfirmNow: Boolean = false
)

data class AiCreatedOrder(
    val id: String = "",
    val code: String = "",
    val status: String = "PENDING",
    val paymentMethod: String = "COD",
    val paymentStatus: String = "PENDING",
    val subtotalVnd: Long = 0L,
    val deliveryFeeVnd: Long = 0L,
    val discountVnd: Long = 0L,
    val totalVnd: Long = 0L,
    val restaurantId: String = "",
    val restaurantName: String = "",
    val deliveryAddress: String = "",
    val receiverName: String = "",
    val receiverPhone: String = "",
    val scheduledFor: String? = null,
    val scheduledForDisplay: String? = null,
    val requiresOnlinePayment: Boolean = false
)

data class AiStoreConflict(
    val currentStoreName: String = "",
    val targetStoreName: String = "",
    val targetDishId: String = "",
    val quantity: Int = 1
)

data class OrderingAction(
    val type: String,
    val dishId: String? = null,
    val lineId: String? = null,
    val quantity: Int? = null,
    val note: String? = null,
    val optionNames: List<String>? = null,
    val addressId: String? = null,
    val voucherCode: String? = null,
    val autoBest: Boolean? = null,
    val paymentMethod: String? = null,
    val scheduledFor: String? = null,
    val dateHint: String? = null,
    val draftId: String? = null,
    val quoteId: String? = null,
    val confirmationToken: String? = null,
    val idempotencyKey: String? = null,
    val expectedTotalVnd: Long? = null
)

data class MealAdvice(
    val message: String,
    val preferences: String,
    val budget: Double,
    val budgetScope: String,
    val deliveryFee: Double,
    val catalogLimited: Boolean,
    val recommendations: List<MealRecommendation>,
    val source: String? = null,
    val menuCursor: MenuCursor? = null,
    val hasMore: Boolean = false,
    val kind: String? = null,
    val orderDraft: AiOrderDraft? = null,
    val orderQuote: AiOrderQuote? = null,
    val availableAddresses: List<AiAddressOption> = emptyList(),
    val availableVouchers: List<AiVoucherOption> = emptyList(),
    val bestVoucher: AiVoucherOption? = null,
    val readyToConfirm: Boolean = false,
    val blockingReason: String? = null,
    val createdOrder: AiCreatedOrder? = null,
    val storeConflict: AiStoreConflict? = null
)

data class MenuCursor(val afterId: String, val storeId: String?, val ceiling: Double)
data class MealChatContext(val question: String, val dishIds: List<String>, val answer: String? = null, val menuCursor: MenuCursor? = null)

class MealAdvisorRepository {
    val conversationId: String = java.util.UUID.randomUUID().toString()

    suspend fun suggest(message: String, budget: Long?, includeDelivery: Boolean, history: List<MealChatContext>): MealAdvice {
        return sendInternal(message, null, budget, includeDelivery, history)
    }

    suspend fun act(
        displayMessage: String,
        action: OrderingAction,
        budget: Long?,
        includeDelivery: Boolean,
        history: List<MealChatContext>
    ): MealAdvice {
        return sendInternal(displayMessage, action, budget, includeDelivery, history)
    }

    private suspend fun sendInternal(
        message: String,
        orderingAction: OrderingAction?,
        budget: Long?,
        includeDelivery: Boolean,
        history: List<MealChatContext>
    ): MealAdvice {
        val payload = com.google.gson.JsonObject().apply {
            addProperty("conversationId", conversationId)
            addProperty("message", message)
            addProperty("budget", budget ?: 10_000_000L)
            add("history", FoodHubClient.gson.toJsonTree(history))
            addProperty("budgetScope", if (includeDelivery) "total" else "food")
            if (orderingAction != null) {
                add("orderingAction", FoodHubClient.gson.toJsonTree(orderingAction))
            }
        }
        val response = FoodHubClient.api.create("ai/meal-advisor", payload)
        if (!response.isSuccessful) {
            val error = runCatching {
                JsonParser.parseString(response.errorBody()?.string().orEmpty()).asJsonObject["error"].asString
            }.getOrNull()
            throw IOException(error ?: "Chưa thể xử lý yêu cầu. Vui lòng thử lại.")
        }
        val data = response.body()?.getAsJsonObject("data") ?: throw IOException("Máy chủ chưa trả phản hồi.")
        return FoodHubClient.gson.fromJson(data, MealAdvice::class.java)
    }
}
