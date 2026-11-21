package com.hwanseong.hskstudy

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** 듣기 모드 설정. 화면과 재생 서비스가 같은 값을 읽도록 SharedPreferences("listen")에 둔다. */
data class ListenSettings(
    val level: Int = 1,              // 0 = 전체
    val target: String = "all",      // all | weak(모름·애매) | fav
    val speed: Float = 1.0f,
    val repeat: Int = 1,
    val koMeaning: Boolean = true,
    val shuffle: Boolean = false,
    val gapMs: Int = 1000,           // 500 | 1000 | 2000
    val endAction: String = "stop",  // stop | restart | reshuffle
) {
    fun save(context: Context) {
        context.getSharedPreferences("listen", Context.MODE_PRIVATE).edit()
            .putInt("level", level).putString("target", target).putFloat("speed", speed)
            .putInt("repeat", repeat).putBoolean("koMeaning", koMeaning).putBoolean("shuffle", shuffle)
            .putInt("gapMs", gapMs).putString("endAction", endAction).apply()
    }

    companion object {
        fun load(context: Context): ListenSettings {
            val p = context.getSharedPreferences("listen", Context.MODE_PRIVATE)
            val d = ListenSettings()
            return ListenSettings(
                p.getInt("level", d.level), p.getString("target", d.target) ?: d.target,
                p.getFloat("speed", d.speed), p.getInt("repeat", d.repeat),
                p.getBoolean("koMeaning", d.koMeaning), p.getBoolean("shuffle", d.shuffle),
                p.getInt("gapMs", d.gapMs), p.getString("endAction", d.endAction) ?: d.endAction,
            )
        }
    }
}

/** 듣기 범위에 들어가는 단어. 학습 상태는 ProgressStore와 같은 저장소("progress")에서 읽는다. */
fun listenWords(context: Context, data: VocabData, s: ListenSettings): List<Word> {
    val p = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
    return data.words.filter { w ->
        (s.level == 0 || w.level == s.level) && when (s.target) {
            "weak" -> p.getInt("s_${w.id}", 0).let { it == WordStatus.UNKNOWN.code || it == WordStatus.UNSURE.code }
            "fav" -> p.getBoolean("f_${w.id}", false)
            else -> true
        }
    }
}

const val CMD_START = "listen.start"     // 설정대로 재생 목록을 만든다 (마지막 단어부터). args "play": 바로 재생할지
const val CMD_SLEEP = "listen.sleep"     // args "minutes": 0이면 해제

/**
 * 듣기 모드 재생 서비스. 화면을 끄거나 다른 앱으로 가도 계속 재생되고,
 * 잠금 화면·알림·이어폰 버튼으로 조작할 수 있다(Media3 MediaSession).
 * 단어 → (한국어 뜻) → 예문 1 → 예문 2 순서를 단어마다 이어 붙이고, 항목 사이에 무음을 넣는다.
 */
class ListenService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var data: VocabData
    private val handler = Handler(Looper.getMainLooper())
    private val sleepRunnable = Runnable {
        player.pause()
        prefs().edit().remove("sleepUntil").apply()
    }

    private fun prefs() = getSharedPreferences("listen", Context.MODE_PRIVATE)

    override fun onCreate() {
        super.onCreate()
        data = VocabData(this)
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                /* handleAudioFocus = */ true,  // 전화가 오면 멈추고 끝나면 다시 재생
            )
            .setHandleAudioBecomingNoisy(true)  // 이어폰이 빠지면 멈춤
            .build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaMetadata?.extras?.getInt("wordId", 0) ?: 0
                if (id > 0) prefs().edit().putInt("lastWordId", id).apply()
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    val s = ListenSettings.load(this@ListenService)
                    prefs().edit().remove("lastWordId").apply()
                    if (s.endAction == "reshuffle") start(s.copy(shuffle = true), resume = false)
                }
            }
        })
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).putExtra("tab", "LISTEN"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(CMD_START, Bundle.EMPTY))
                        .add(SessionCommand(CMD_SLEEP, Bundle.EMPTY))
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(commands)
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        CMD_START -> start(ListenSettings.load(this@ListenService), resume = true, autoPlay = args.getBoolean("play", true))
                        CMD_SLEEP -> setSleep(args.getInt("minutes", 0))
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(sleepRunnable)
        prefs().edit().remove("sleepUntil").apply()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private fun setSleep(minutes: Int) {
        handler.removeCallbacks(sleepRunnable)
        if (minutes <= 0) {
            prefs().edit().remove("sleepUntil").apply()
        } else {
            val ms = minutes * 60_000L
            handler.postDelayed(sleepRunnable, ms)
            prefs().edit().putLong("sleepUntil", System.currentTimeMillis() + ms).apply()
        }
    }

    private fun start(s: ListenSettings, resume: Boolean, autoPlay: Boolean = true) {
        var words = listenWords(this, data, s)
        if (words.isEmpty()) {
            player.clearMediaItems()
            return
        }
        if (s.shuffle) words = words.shuffled()
        val gap = "audio/silence/gap_${s.gapMs}.ogg"
        val items = ArrayList<MediaItem>(words.size * 8 * s.repeat)
        var startIndex = 0
        val lastId = if (resume) prefs().getInt("lastWordId", 0) else 0
        words.forEachIndexed { i, w ->
            if (w.id == lastId) startIndex = items.size
            val ex = data.examplesOf(w.id)
            repeat(s.repeat) {
                items += item(w, i, words.size, "word", w.audioZh, null)
                items += item(w, i, words.size, "gap", gap, null)
                if (s.koMeaning) {
                    items += item(w, i, words.size, "meaning", w.audioKo, null)
                    items += item(w, i, words.size, "gap", gap, null)
                }
                ex.forEachIndexed { n, sen ->
                    items += item(w, i, words.size, "s${n + 1}", sen.audio, sen)
                    items += item(w, i, words.size, "gap", gap, sen)
                }
            }
        }
        player.setMediaItems(items, startIndex, 0L)
        player.repeatMode = if (s.endAction == "restart") Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        player.playbackParameters = PlaybackParameters(s.speed)
        player.prepare()
        if (autoPlay) player.play()
    }

    private fun item(w: Word, index: Int, total: Int, kind: String, path: String, sen: Sentence?): MediaItem {
        val extras = Bundle().apply {
            putInt("wordId", w.id)
            putInt("wordPos", index + 1)
            putInt("wordTotal", total)
            putString("kind", kind)
            putString("pinyin", w.pinyin)
            putString("meaning", w.meaning)
            putInt("level", w.level)
            if (sen != null) {
                putString("zh", sen.zh)
                putString("zhPinyin", sen.pinyin)
                putString("ko", sen.ko)
            }
        }
        val meta = MediaMetadata.Builder()
            .setTitle(if (sen != null) sen.zh else w.hanzi)
            .setArtist(if (sen != null) sen.ko else "${w.pinyin} · ${w.meaning}")
            .setAlbumTitle("HSK ${w.level}급 · ${w.hanzi}")
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId("$kind:${w.id}:${itemCounter++}")
            .setUri("asset:///$path")
            .setMediaMetadata(meta)
            .build()
    }

    private var itemCounter = 0
}
