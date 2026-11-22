package com.example.dacs_3_composer.feature.customer.ai

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dacs_3_composer.core.model.Dish
import com.example.dacs_3_composer.data.repository.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MealAdvisorScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun feedbackKeepsOriginalOrderQuestionAndAnswer() {
        val calls = mutableListOf<Pair<String, List<MealChatContext>>>()
        val model = MealAdvisorViewModel(SavedStateHandle()) { question, _, _, history ->
            calls.add(question to history)
            MealAdvice(if (history.isEmpty()) "Đơn #21 đang được chuẩn bị." else "Đã ghi nhận góp ý #7.",
                "", 100000.0, "food", 15000.0, false, emptyList(), source = "support", kind = "support")
        }
        compose.setContent { MaterialTheme { MealAdvisorScreen({}, {}, model) } }
        compose.onNodeWithText("Đơn vừa đặt").performClick()
        compose.onNodeWithText("Chưa giải quyết").performClick()
        compose.onNodeWithText("Đã ghi nhận góp ý #7.").assertExists()
        compose.runOnIdle {
            assertEquals("Đánh giá: Chưa giải quyết", calls.last().first)
            assertEquals("Đơn vừa đặt", calls.last().second.single().question)
            assertEquals("Đơn #21 đang được chuẩn bị.", calls.last().second.single().answer)
        }
    }

    @Test fun nextPageSendsTheBrowseCursor() {
        val cursor = MenuCursor("18", "3", 100000.0)
        val calls = mutableListOf<List<MealChatContext>>()
        val model = MealAdvisorViewModel(SavedStateHandle()) { _, _, _, history ->
            calls.add(history)
            MealAdvice(if (calls.size == 1) "Trang đầu" else "Trang cuối", "", 100000.0,
                "food", 0.0, false, emptyList(), menuCursor = cursor, hasMore = calls.size == 1, kind = "page")
        }
        compose.setContent { MaterialTheme { MealAdvisorScreen({}, {}, model) } }
        compose.onNodeWithText("Xem thực đơn").performClick()
        compose.onNodeWithText("Xem tiếp menu").performClick()
        compose.onNodeWithText("Trang cuối").assertExists()
        compose.runOnIdle { assertEquals(cursor, calls.last().last().menuCursor) }
    }

    @Test fun opensChatAndSendsFollowUpWithPreviousDishIds() {
        val contexts = mutableListOf<List<MealChatContext>>()
        val dish = Dish(id = "8", name = "Bánh xèo", price = 60000.0, restaurantId = "3")
        var opened = ""
        val model = MealAdvisorViewModel(SavedStateHandle()) { _, _, _, context ->
            contexts.add(context)
            MealAdvice(if (context.isEmpty()) "Mình tìm thấy bánh xèo." else "Món đầu tiên giá 60.000đ.",
                "", 10000000.0, "food", 0.0, false, listOf(MealRecommendation(dish, "Quán thử", "", 60000.0)))
        }
        compose.setContent { MaterialTheme { MealAdvisorScreen({}, { opened = it }, model) } }
        compose.onNodeWithText("FoodHub AI").assertIsDisplayed()
        compose.onNodeWithText("Xem thực đơn").performClick()
        compose.onNodeWithText("Mình tìm thấy bánh xèo.").assertExists()
        compose.onNodeWithText("Bánh xèo").performClick()
        compose.runOnIdle { assertEquals("3", opened) }
        compose.onNodeWithTag("meal_chat_input").performTextInput("Món đầu tiên giá bao nhiêu?")
        compose.onNodeWithContentDescription("Gửi tin nhắn").performClick()
        compose.onNodeWithText("Món đầu tiên giá 60.000đ.").assertExists()
        compose.runOnIdle {
            assertEquals(listOf(MealChatContext("Xem thực đơn", listOf("8"), "Mình tìm thấy bánh xèo.")), contexts.last())
            assertEquals(2, model.turns.value.size)
        }
    }
}
