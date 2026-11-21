package com.hwanseong.hskstudy

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import java.time.LocalDate

enum class WordStatus(val code: Int, val label: String) {
    NEW(0, "새 단어"),
    UNKNOWN(1, "모름"),
    UNSURE(2, "애매"),
    KNOWN(3, "앎");

    companion object {
        fun of(code: Int) = entries.firstOrNull { it.code == code } ?: NEW
    }
}

/**
 * 단어별 학습 상태, 즐겨찾기, 연속 학습일을 기기에 저장한다.
 * 1단계 임시 저장소(SharedPreferences). 간격 반복을 넣는 2단계에서 Room DB로 옮긴다.
 */
class ProgressStore(context: Context) {
    private val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
    private val status = mutableStateMapOf<Int, WordStatus>()
    private val favorites = mutableStateMapOf<Int, Boolean>()
    var streak by mutableIntStateOf(0)
        private set

    init {
        prefs.all.forEach { (k, v) ->
            when {
                k.startsWith("s_") && v is Int -> status[k.removePrefix("s_").toInt()] = WordStatus.of(v)
                k.startsWith("f_") && v == true -> favorites[k.removePrefix("f_").toInt()] = true
            }
        }
        val last = prefs.getString("lastStudy", null)?.let { LocalDate.parse(it) }
        val today = LocalDate.now()
        streak = if (last == today || last == today.minusDays(1)) prefs.getInt("streak", 0) else 0
    }

    fun statusOf(id: Int): WordStatus = status[id] ?: WordStatus.NEW

    fun setStatus(id: Int, s: WordStatus) {
        status[id] = s
        prefs.edit().putInt("s_$id", s.code).apply()
        markStudiedToday()
        val key = "d_${LocalDate.now()}"
        daily.edit().putInt(key, daily.getInt(key, 0) + 1).apply()
    }

    /** 날짜별 답한 횟수 (통계용). 키 "d_2026-11-21" */
    private val daily = context.getSharedPreferences("daily", Context.MODE_PRIVATE)

    fun answeredOn(date: LocalDate): Int = daily.getInt("d_$date", 0)

    fun isFavorite(id: Int): Boolean = favorites[id] == true

    fun toggleFavorite(id: Int) {
        if (isFavorite(id)) {
            favorites.remove(id)
            prefs.edit().remove("f_$id").apply()
        } else {
            favorites[id] = true
            prefs.edit().putBoolean("f_$id", true).apply()
        }
    }

    private fun markStudiedToday() {
        val today = LocalDate.now()
        val last = prefs.getString("lastStudy", null)?.let { LocalDate.parse(it) }
        if (last == today) return
        streak = if (last == today.minusDays(1)) prefs.getInt("streak", 0) + 1 else 1
        prefs.edit().putString("lastStudy", today.toString()).putInt("streak", streak).apply()
    }
}
