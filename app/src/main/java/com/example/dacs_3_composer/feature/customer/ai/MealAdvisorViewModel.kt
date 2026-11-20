package com.example.dacs_3_composer.feature.customer.ai

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dacs_3_composer.data.repository.MealAdvice
import com.example.dacs_3_composer.data.repository.MealAdvisorRepository
import com.example.dacs_3_composer.data.repository.MealChatContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

data class MealTurn(
    val question: String,
    val budget: Long?,
    val includeDelivery: Boolean,
    val answer: MealAdvice? = null,
    val error: String? = null
)

class MealAdvisorViewModel(
    private val savedState: SavedStateHandle,
    private val ask: suspend (String, Long?, Boolean, List<MealChatContext>) -> MealAdvice
) : ViewModel() {
    constructor(savedState: SavedStateHandle) : this(savedState, MealAdvisorRepository()::suggest)

    val message = savedState.getStateFlow("chatDraft", "")
    val budget = savedState.getStateFlow("chatBudget", "")
    val includeDelivery = savedState.getStateFlow("includeDelivery", false)
    private val mutableTurns = MutableStateFlow<List<MealTurn>>(emptyList())
    val turns = mutableTurns.asStateFlow()

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
                turn.copy(answer = ask(turn.question, turn.budget, turn.includeDelivery, context))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                turn.copy(error = error.message ?: "Kiểm tra kết nối mạng rồi thử lại.")
            } catch (_: Exception) {
                turn.copy(error = "Chưa đọc được câu trả lời. Bạn thử gửi lại nhé.")
            }
            mutableTurns.value = previous + result
        }
    }
}
