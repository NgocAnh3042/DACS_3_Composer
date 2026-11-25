package com.example.dacs_3_composer.feature.customer.ai

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.data.repository.AiAddressOption
import com.example.dacs_3_composer.data.repository.AiOrderQuote
import com.example.dacs_3_composer.data.repository.AiUpsellSuggestion
import com.example.dacs_3_composer.data.repository.MealAdvice
import com.example.dacs_3_composer.data.repository.MealAdvisorRepository
import com.example.dacs_3_composer.data.repository.MealChatContext
import com.example.dacs_3_composer.data.repository.OrderingAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class MealTurn(
    val question: String,
    val budget: Long?,
    val includeDelivery: Boolean,
    val answer: MealAdvice? = null,
    val error: String? = null,
    val action: OrderingAction? = null
)

class MealAdvisorViewModel(
    private val savedState: SavedStateHandle,
    private val ask: suspend (String, Long?, Boolean, List<MealChatContext>) -> MealAdvice,
    private val actOnDraft: (suspend (String, OrderingAction, Long?, Boolean, List<MealChatContext>) -> MealAdvice)?
) : ViewModel() {

    constructor(
        savedState: SavedStateHandle,
        ask: suspend (String, Long?, Boolean, List<MealChatContext>) -> MealAdvice
    ) : this(savedState, ask, null)

    constructor(savedState: SavedStateHandle) : this(
        savedState = savedState,
        deps = MealAdvisorRepository(),
        unused = true
    )

    private constructor(savedState: SavedStateHandle, deps: MealAdvisorRepository, unused: Boolean) : this(
        savedState = savedState,
        ask = deps::suggest,
        actOnDraft = deps::act
    )

    val message = savedState.getStateFlow("chatDraft", "")
    val budget = savedState.getStateFlow("chatBudget", "")
    val includeDelivery = savedState.getStateFlow("includeDelivery", false)
    private val mutableTurns = MutableStateFlow<List<MealTurn>>(emptyList())
    val turns = mutableTurns.asStateFlow()

    // Stable idempotency key per quoteId so retries or double-taps NEVER create duplicate orders.
    private val idempotencyByQuote = ConcurrentHashMap<String, String>()

    internal fun idempotencyKeyForQuote(quoteId: String): String =
        idempotencyByQuote.getOrPut(quoteId) { "idem-$quoteId-${UUID.randomUUID()}" }

    fun setMessage(value: String) { savedState["chatDraft"] = value.take(500) }
    fun setBudget(value: String, delivery: Boolean) {
        savedState["chatBudget"] = value
        savedState["includeDelivery"] = delivery
    }

    fun send(text: String = message.value) {
        val question = text.trim()
        if (question.isEmpty() || question.length > 500 || isPending()) return
        val turn = MealTurn(question, budget.value.toLongOrNull(), includeDelivery.value)
        setMessage("")
        submit(mutableTurns.value, turn)
    }

    fun dispatchOrderingAction(displayText: String, action: OrderingAction) {
        if (isPending()) return
        val turn = MealTurn(
            question = displayText.take(300),
            budget = budget.value.toLongOrNull(),
            includeDelivery = includeDelivery.value,
            action = action
        )
        submit(mutableTurns.value, turn)
    }

    fun quickOrderDish(dishId: String, dishName: String, quantity: Int = 1) {
        dispatchOrderingAction(
            displayText = "Chọn đặt ${quantity}x $dishName",
            action = OrderingAction(type = "add_item", dishId = dishId, quantity = quantity)
        )
    }

    fun replaceStoreWithDish(dishId: String, dishName: String, quantity: Int = 1) {
        dispatchOrderingAction(
            displayText = "Đổi sang quán mới và chọn ${quantity}x $dishName",
            action = OrderingAction(type = "replace_store_with_item", dishId = dishId, quantity = quantity)
        )
    }

    fun updateDraftItemQuantity(lineId: String, dishName: String, newQty: Int) {
        val label = if (newQty <= 0) "Bỏ món $dishName khỏi đơn nháp" else "Chỉnh $dishName thành $newQty phần"
        dispatchOrderingAction(
            displayText = label,
            action = OrderingAction(type = "update_quantity", lineId = lineId, quantity = newQty)
        )
    }

    fun selectDraftAddress(address: AiAddressOption) {
        dispatchOrderingAction(
            displayText = "Giao tới ${address.label}: ${address.addressLine}",
            action = OrderingAction(type = "select_address", addressId = address.id)
        )
    }

    fun selectDraftVoucher(code: String?, autoBest: Boolean = false, label: String) {
        dispatchOrderingAction(
            displayText = label,
            action = OrderingAction(type = "select_voucher", voucherCode = code, autoBest = autoBest)
        )
    }

    fun selectDraftPayment(method: String, label: String) {
        dispatchOrderingAction(
            displayText = "Phương thức thanh toán: $label",
            action = OrderingAction(type = "select_payment", paymentMethod = method)
        )
    }

    fun reorderLastOrder(dateHint: String? = null) {
        val label = if (dateHint == "yesterday") "Đặt lại y hệt đơn hôm qua" else "Đặt lại đơn cũ gần nhất"
        dispatchOrderingAction(
            displayText = label,
            action = OrderingAction(type = "reorder_last", dateHint = dateHint)
        )
    }

    fun acceptUpsell(upsell: AiUpsellSuggestion) {
        dispatchOrderingAction(
            displayText = "Đồng ý thêm 1x ${upsell.dishName} & áp mã ${upsell.targetVoucherCode}",
            action = OrderingAction(
                type = "accept_upsell",
                dishId = upsell.dishId,
                voucherCode = upsell.targetVoucherCode
            )
        )
    }

    fun selectScheduleSlot(scheduledForIso: String?, label: String) {
        val text = if (scheduledForIso.isNullOrBlank()) "Đổi về Giao ngay" else "Hẹn giờ giao: $label"
        dispatchOrderingAction(
            displayText = text,
            action = OrderingAction(type = "set_schedule", scheduledFor = scheduledForIso)
        )
    }

    fun refreshDraftQuote() {
        dispatchOrderingAction(
            displayText = "Cập nhật lại báo giá đơn nháp",
            action = OrderingAction(type = "refresh_quote")
        )
    }

    fun cancelActiveDraft() {
        dispatchOrderingAction(
            displayText = "Hủy đơn nháp hiện tại",
            action = OrderingAction(type = "cancel_draft")
        )
    }

    fun confirmOrder(quote: AiOrderQuote) {
        val qId = quote.quoteId ?: return
        val token = quote.confirmationToken ?: return
        if (!quote.canConfirmNow || isPending()) return
        val idemKey = idempotencyKeyForQuote(qId)
        dispatchOrderingAction(
            displayText = "Xác nhận đặt hàng (${quote.total}đ)",
            action = OrderingAction(
                type = "confirm_order",
                draftId = quote.draftId,
                quoteId = qId,
                confirmationToken = token,
                idempotencyKey = idemKey,
                expectedTotalVnd = quote.total
            )
        )
    }

    fun retry() {
        val last = mutableTurns.value.lastOrNull() ?: return
        if (last.error != null) submit(mutableTurns.value.dropLast(1), last.copy(error = null))
    }

    private fun isPending() = mutableTurns.value.lastOrNull()?.let { it.answer == null && it.error == null } == true

    private fun submit(previous: List<MealTurn>, turn: MealTurn) {
        mutableTurns.value = previous + turn
        val recent = previous.filter { it.answer != null }.takeLast(4)
        val cursor = previous.asReversed().firstNotNullOfOrNull { it.answer?.menuCursor }
        val context = recent.mapIndexed { index, item ->
            val answer = requireNotNull(item.answer)
            MealChatContext(item.question, answer.recommendations.map { it.dish.id },
                answer.message.take(700), if (index == recent.lastIndex) cursor else null)
        }
        viewModelScope.launch {
            val result = try {
                val advice = if (turn.action != null && actOnDraft != null) {
                    actOnDraft.invoke(turn.question, turn.action, turn.budget, turn.includeDelivery, context)
                } else {
                    ask(turn.question, turn.budget, turn.includeDelivery, context)
                }
                turn.copy(answer = advice)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                val safeMsg = if (turn.action?.type == "confirm_order") {
                    "Chưa xác nhận được kết quả tạo đơn do kết nối mạng. Bạn bấm Gửi lại (hệ thống có khóa chống trùng đơn) hoặc kiểm tra mục Đơn hàng nhé."
                } else {
                    error.message ?: "Kiểm tra kết nối mạng rồi thử lại."
                }
                turn.copy(error = safeMsg)
            } catch (_: Exception) {
                turn.copy(error = "Chưa đọc được câu trả lời. Bạn thử gửi lại nhé.")
            }
            mutableTurns.value = previous + result
        }
    }
}
