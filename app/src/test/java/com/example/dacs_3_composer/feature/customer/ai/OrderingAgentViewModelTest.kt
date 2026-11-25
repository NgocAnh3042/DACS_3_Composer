package com.example.dacs_3_composer.feature.customer.ai

import androidx.lifecycle.SavedStateHandle
import com.example.dacs_3_composer.data.remote.FoodHubClient
import com.example.dacs_3_composer.data.repository.AiCreatedOrder
import com.example.dacs_3_composer.data.repository.AiOrderQuote
import com.example.dacs_3_composer.data.repository.MealAdvice
import com.example.dacs_3_composer.data.repository.OrderingAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderingAgentViewModelTest {

    @Test
    fun `idempotencyKeyForQuote returns identical key across retries for same quoteId and new key for new quoteId`() {
        val vm = MealAdvisorViewModel(
            savedState = SavedStateHandle(),
            ask = { _, _, _, _ ->
                MealAdvice("Xin chào", "", 100000.0, "food", 15000.0, false, emptyList())
            }
        )

        val firstKey = vm.idempotencyKeyForQuote("qt_abc123")
        val retryKey = vm.idempotencyKeyForQuote("qt_abc123")
        val differentQuoteKey = vm.idempotencyKeyForQuote("qt_def456")

        assertTrue(firstKey.startsWith("idem-qt_abc123-"))
        assertEquals(firstKey, retryKey)
        assertNotEquals(firstKey, differentQuoteKey)
    }

    @Test
    fun `confirmOrder refuses to add turn when quote lacks confirmationToken or canConfirmNow is false`() {
        val vm = MealAdvisorViewModel(
            savedState = SavedStateHandle(),
            ask = { _, _, _, _ -> error("unexpected") },
            actOnDraft = { _, _, _, _, _ -> error("unexpected") }
        )

        val incompleteQuote = AiOrderQuote(
            quoteId = null,
            draftId = "dr_1",
            total = 50000L,
            confirmationToken = null,
            canConfirmNow = false
        )
        vm.confirmOrder(incompleteQuote)
        assertTrue(vm.turns.value.isEmpty())
    }

    @Test
    fun `Gson deserializes ordering_quote and order_created payloads accurately into MealAdvice`() {
        val rawQuoteJson = """
            {
              "message": "Dạ tổng đơn là 45.000đ",
              "preferences": "",
              "budget": 10000000,
              "budgetScope": "food",
              "deliveryFee": 15000,
              "catalogLimited": false,
              "recommendations": [],
              "kind": "ordering_quote",
              "source": "ordering_agent",
              "readyToConfirm": true,
              "orderQuote": {
                "quoteId": "qt_999",
                "draftId": "dr_888",
                "draftVersion": 3,
                "restaurantName": "Cơm Tấm Sài Gòn Xưa",
                "subtotal": 45000,
                "deliveryFee": 15000,
                "discount": 15000,
                "total": 45000,
                "paymentMethod": "PAYOS",
                "confirmationToken": "ct_hash_999",
                "canConfirmNow": true
              }
            }
        """.trimIndent()

        val parsedQuote = FoodHubClient.gson.fromJson(rawQuoteJson, MealAdvice::class.java)
        assertEquals("ordering_quote", parsedQuote.kind)
        assertTrue(parsedQuote.readyToConfirm)
        assertEquals("qt_999", parsedQuote.orderQuote?.quoteId)
        assertEquals(45000L, parsedQuote.orderQuote?.total)
        assertEquals("PAYOS", parsedQuote.orderQuote?.paymentMethod)
        assertTrue(parsedQuote.orderQuote?.canConfirmNow == true)

        val rawCreatedJson = """
            {
              "message": "Đặt hàng thành công!",
              "preferences": "",
              "budget": 10000000,
              "budgetScope": "food",
              "deliveryFee": 15000,
              "catalogLimited": false,
              "recommendations": [],
              "kind": "order_created",
              "source": "ordering_agent",
              "createdOrder": {
                "id": "501",
                "code": "FH261125ABCD",
                "status": "PENDING_PAYMENT",
                "paymentMethod": "PAYOS",
                "totalVnd": 45000,
                "requiresOnlinePayment": true
              }
            }
        """.trimIndent()

        val parsedCreated = FoodHubClient.gson.fromJson(rawCreatedJson, MealAdvice::class.java)
        assertEquals("FH261125ABCD", parsedCreated.createdOrder?.code)
        assertTrue(parsedCreated.createdOrder?.requiresOnlinePayment == true)

        // Verify OrderingAction round-trip serialization
        val act = OrderingAction(type = "confirm_order", draftId = "dr_888", quoteId = "qt_999", confirmationToken = "ct_hash_999", idempotencyKey = "idem-1", expectedTotalVnd = 45000L)
        val actTree = FoodHubClient.gson.toJsonTree(act).asJsonObject
        assertEquals("confirm_order", actTree["type"].asString)
        assertEquals(45000L, actTree["expectedTotalVnd"].asLong)
    }

    @Test
    fun `Gson deserializes upsellSuggestion and scheduledFor slots and ViewModel serializes reorder, upsell, and schedule actions`() {
        val rawV2QuoteJson = """
            {
              "message": "Đã nạp lại đơn cũ",
              "preferences": "",
              "budget": 10000000,
              "budgetScope": "food",
              "deliveryFee": 15000,
              "catalogLimited": false,
              "recommendations": [],
              "kind": "ordering_quote",
              "source": "ordering_agent",
              "readyToConfirm": true,
              "orderQuote": {
                "quoteId": "qt_v2_1",
                "draftId": "dr_v2_1",
                "draftVersion": 2,
                "restaurantName": "Cơm Tấm Sài Gòn Xưa",
                "storeOpenHours": "07:30–21:30",
                "subtotal": 52000,
                "deliveryFee": 15000,
                "discount": 15000,
                "total": 52000,
                "paymentMethod": "COD",
                "scheduledFor": "2026-11-25 18:30:00",
                "scheduledForDisplay": "Hôm nay 18:30",
                "reorderedFromOrderId": "301",
                "availableScheduleSlots": [
                  { "iso": "2026-11-25 18:30:00", "label": "Hôm nay 18:30" },
                  { "iso": "2026-11-25 19:00:00", "label": "Hôm nay 19:00" }
                ],
                "upsellSuggestion": {
                  "dishId": "19",
                  "dishName": "Trà tắc mật ong",
                  "dishPriceVnd": 12000,
                  "targetVoucherCode": "DEAL30K",
                  "missingAmountVnd": 8000,
                  "currentTotalVnd": 52000,
                  "projectedTotalVnd": 49000,
                  "savingsVsCurrentVnd": 3000,
                  "isStrictlyCheaper": true,
                  "headline": "Thêm 1x Trà tắc mật ong — Tổng đơn rẻ hơn 3.000đ!",
                  "reason": "Đơn thiếu 8.000đ mở mã DEAL30K"
                },
                "confirmationToken": "ct_v2_hash",
                "canConfirmNow": true
              }
            }
        """.trimIndent()

        val parsed = FoodHubClient.gson.fromJson(rawV2QuoteJson, MealAdvice::class.java)
        val q = requireNotNull(parsed.orderQuote)
        assertEquals("2026-11-25 18:30:00", q.scheduledFor)
        assertEquals("Hôm nay 18:30", q.scheduledForDisplay)
        assertEquals("301", q.reorderedFromOrderId)
        assertEquals(2, q.availableScheduleSlots.size)
        val up = requireNotNull(q.upsellSuggestion)
        assertEquals("19", up.dishId)
        assertEquals("DEAL30K", up.targetVoucherCode)
        assertTrue(up.isStrictlyCheaper)
        assertEquals(49000L, up.projectedTotalVnd)

        val schedAct = OrderingAction(type = "set_schedule", scheduledFor = "2026-11-25 18:30:00")
        val schedJson = FoodHubClient.gson.toJsonTree(schedAct).asJsonObject
        assertEquals("set_schedule", schedJson["type"].asString)
        assertEquals("2026-11-25 18:30:00", schedJson["scheduledFor"].asString)

        val reorderAct = OrderingAction(type = "reorder_last", dateHint = "yesterday")
        val reorderJson = FoodHubClient.gson.toJsonTree(reorderAct).asJsonObject
        assertEquals("reorder_last", reorderJson["type"].asString)
        assertEquals("yesterday", reorderJson["dateHint"].asString)
    }
}
