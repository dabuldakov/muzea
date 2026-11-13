package com.example.muzea.ui.chat

/**
 * Воспроизведение аудио/голосовых прямо в чате: один плеер на экран,
 * переключение между сообщениями, пауза/продолжение и сброс состояния иконок.
 */
class ChatAudioController(
    private val adapter: MessageAdapter,
    private val createPlayer: () -> ChatAudioPlayer,
    private val onFinished: () -> Unit,
    private val onError: () -> Unit
) {

    private var player: ChatAudioPlayer? = null

    var playingUuid: String? = null
        private set

    fun toggle(messageUuid: String, url: String) {
        val current = player ?: createPlayer().also { player = it }
        if (playingUuid == messageUuid) {
            val willPlay = !current.isPlaying
            if (willPlay) current.resume() else current.pause()
            adapter.setAudioState(messageUuid, willPlay)
            return
        }
        playingUuid = messageUuid
        adapter.setAudioState(messageUuid, playing = true)
        current.play(url)
    }

    fun reset() {
        playingUuid = null
        adapter.setAudioState(null, playing = false)
    }

    fun finish() {
        reset()
        onFinished()
    }

    fun fail() {
        reset()
        onError()
    }

    fun release() {
        player?.release()
        player = null
        playingUuid = null
    }
}
