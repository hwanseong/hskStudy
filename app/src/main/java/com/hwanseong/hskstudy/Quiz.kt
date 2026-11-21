package com.hwanseong.hskstudy

import android.content.Context
import kotlin.random.Random

/** 한 문제. 보기형은 options/correct, 어순 맞추기는 blocks/answer를 쓴다. */
data class Question(
    val type: String,
    val word: Word,
    val sentence: Sentence?,
    val options: List<String> = emptyList(),
    val correct: Int = -1,
    val blocks: List<String> = emptyList(),   // 섞인 블록
    val answer: List<String> = emptyList(),   // 정답 순서
    val tail: String = "",                    // 문장 끝 문장부호
) {
    /** 오답 노트 키: 단어 w_<id>, 예문 s_<id> */
    val key: String get() = if (sentence != null) "s_${sentence.id}" else "w_${word.id}"
    val audio: String get() = sentence?.audio ?: word.audioZh
}

private const val PUNCT = "。，？！、：；“”\"…—（）《》,.?!:;"

/**
 * 게임 설정대로 문제를 만든다.
 * - 문제 순서·보기 순서·정답 위치는 매번 랜덤
 * - 오답 보기는 문제를 낼 때마다 같은 급수·같은 품사에서 새로 뽑는다
 * - 정답 위치는 0~3을 고르게 섞은 묶음에서 하나씩 꺼내 라운드 전체에서 고르게 나오게 한다
 */
class QuizMaker(private val data: VocabData, private val s: GameSettings, wrongKeys: Set<String>, private val rnd: Random = Random.Default) {
    private val byId = data.words.associateBy { it.id }
    private val vocab = data.words.map { it.hanzi }.toSet()
    private val pool: List<String>
    private val queue = ArrayDeque<String>()
    private val positions = ArrayDeque<Int>()
    val total: Int  // 0 = 무제한

    init {
        val keys = if (s.mode == "word") {
            data.words.filter { s.level == 0 || it.level == s.level }.map { "w_${it.id}" }
        } else {
            data.sentences.filter { s.level == 0 || byId.getValue(it.wordId).level == s.level }.map { "s_${it.id}" }
        }
        pool = if (s.wrongOnly) keys.filter { it in wrongKeys }.ifEmpty { keys } else keys
        total = if (s.count == 0) 0 else minOf(s.count, pool.size)
    }

    fun next(): Question {
        if (queue.isEmpty()) queue.addAll(pool.shuffled(rnd))
        val key = queue.removeFirst()
        return if (key.startsWith("w_")) wordQuestion(byId.getValue(key.removePrefix("w_").toInt()))
        else sentenceQuestion(data.sentences.first { it.id == key.removePrefix("s_") })
    }

    private fun position(): Int {
        if (positions.isEmpty()) positions.addAll((0..3).shuffled(rnd))
        return positions.removeFirst()
    }

    private fun place(correct: String, wrong: List<String>): Pair<List<String>, Int> {
        val pos = position()
        val list = wrong.shuffled(rnd).toMutableList()
        list.add(pos.coerceAtMost(list.size), correct)
        return list to list.indexOf(correct)
    }

    /** 같은 급수·같은 품사 → 같은 급수 → 전체 순으로 후보를 넓혀 서로 다른 값 n개를 뽑는다. */
    private fun distractors(w: Word, n: Int, value: (Word) -> String, exclude: (Word) -> Boolean = { false }): List<String> {
        val target = value(w)
        val tiers = listOf(
            data.words.filter { it.level == w.level && it.pos.firstOrNull() == w.pos.firstOrNull() },
            data.words.filter { it.level == w.level },
            data.words,
        )
        val picked = LinkedHashSet<String>()
        for (tier in tiers) {
            for (c in tier.shuffled(rnd)) {
                if (picked.size >= n) break
                if (c.id == w.id || c.hanzi == w.hanzi || exclude(c)) continue
                val v = value(c)
                if (v == target || v.isBlank()) continue
                picked += v
            }
            if (picked.size >= n) break
        }
        return picked.toList()
    }

    private fun wordQuestion(w: Word): Question {
        val type = s.wordTypes.toList().shuffled(rnd).first()
        return when (type) {
            "hanzi", "listen" -> {
                val (opts, c) = place(w.hanzi, distractors(w, 3, { it.hanzi }))
                Question(type, w, null, opts, c)
            }
            "pinyin" -> {
                val syl = w.pinyin.count { it == ' ' }
                val (opts, c) = place(w.pinyin, distractors(w, 3, { it.pinyin }) { it.hanzi.length != w.hanzi.length || it.pinyin.count { ch -> ch == ' ' } != syl })
                Question(type, w, null, opts, c)
            }
            else -> {
                val (opts, c) = place(w.meaning, distractors(w, 3, { it.meaning }))
                Question("meaning", w, null, opts, c)
            }
        }
    }

    private fun sentenceQuestion(sen: Sentence): Question {
        val w = byId.getValue(sen.wordId)
        val order = segment(sen.zh)
        val valid = s.sentenceTypes.filter {
            when (it) {
                "blank" -> sen.blank != null
                "order" -> order.first.size in 3..9
                else -> true
            }
        }.ifEmpty { listOf("listenKo") }
        return when (valid.shuffled(rnd).first()) {
            "blank" -> {
                val (opts, c) = place(w.hanzi, distractors(w, 3, { it.hanzi }) { sen.zh.contains(it.hanzi) })
                Question("blank", w, sen, opts, c)
            }
            "koToZh" -> {
                val (opts, c) = place(sen.zh, otherSentences(sen, 3) { it.zh })
                Question("koToZh", w, sen, opts, c)
            }
            "order" -> {
                var shuffled = order.first.shuffled(rnd)
                if (shuffled == order.first) shuffled = order.first.reversed()
                Question("order", w, sen, blocks = shuffled, answer = order.first, tail = order.second)
            }
            else -> {
                val (opts, c) = place(sen.ko, otherSentences(sen, 3) { it.ko })
                Question("listenKo", w, sen, opts, c)
            }
        }
    }

    private fun otherSentences(sen: Sentence, n: Int, value: (Sentence) -> String): List<String> {
        val level = byId.getValue(sen.wordId).level
        val picked = LinkedHashSet<String>()
        for (tier in listOf(data.sentences.filter { byId.getValue(it.wordId).level == level }, data.sentences)) {
            for (c in tier.shuffled(rnd)) {
                if (picked.size >= n) break
                if (c.wordId == sen.wordId || value(c) == value(sen)) continue
                picked += value(c)
            }
            if (picked.size >= n) break
        }
        return picked.toList()
    }

    /** 어순 맞추기용: 1~3급 단어 목록으로 가장 긴 단어부터 잘라 블록을 만든다. 끝 문장부호는 따로 둔다. */
    fun segment(zh: String): Pair<List<String>, String> {
        var body = zh.trim()
        var tail = ""
        while (body.isNotEmpty() && body.last() in PUNCT) {
            tail = body.last() + tail
            body = body.dropLast(1)
        }
        val blocks = mutableListOf<String>()
        var i = 0
        while (i < body.length) {
            if (body[i] in PUNCT) {  // 문장 가운데 쉼표 등은 앞 블록에 붙인다
                if (blocks.isNotEmpty()) blocks[blocks.size - 1] = blocks.last() + body[i] else blocks += body[i].toString()
                i++
                continue
            }
            var len = minOf(4, body.length - i)
            while (len > 1 && body.substring(i, i + len) !in vocab) len--
            blocks += body.substring(i, i + len)
            i += len
        }
        return blocks to tail
    }
}

/** 오답 노트: SharedPreferences("wrong"), 키 → 틀린 횟수. 다시 맞히면 빠진다. */
object WrongNote {
    private fun prefs(c: Context) = c.getSharedPreferences("wrong", Context.MODE_PRIVATE)
    fun keys(c: Context): Set<String> = prefs(c).all.keys
    fun add(c: Context, key: String) {
        val p = prefs(c)
        p.edit().putInt(key, p.getInt(key, 0) + 1).apply()
    }
    fun remove(c: Context, key: String) = prefs(c).edit().remove(key).apply()
}

/** 한 판 결과. 통계 화면(3단계)에서 쓰도록 기록도 남긴다. */
data class GameResult(
    val settings: GameSettings,
    val answered: Int,
    val correct: Int,
    val score: Int,
    val maxCombo: Int,
    val hearts: Int,
    val seconds: Int,
    val wrong: List<Question>,
) {
    fun saveHistory(c: Context) {
        val p = c.getSharedPreferences("history", Context.MODE_PRIVATE)
        val line = listOf(System.currentTimeMillis(), settings.mode, settings.level, answered, correct, score, maxCombo, seconds).joinToString(",")
        val lines = (p.getString("games", "") ?: "").lines().filter { it.isNotBlank() }.takeLast(199) + line
        p.edit().putString("games", lines.joinToString("\n")).apply()
    }
}
