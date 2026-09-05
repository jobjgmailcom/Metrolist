package com.metrolist.music.utils

import com.metrolist.innertube.models.response.PlayerResponse
import com.metrolist.music.utils.InnerTubeXPlayer.PlaybackData
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NewPipeRequest
import org.schabi.newpipe.extractor.downloader.Response as NewPipeResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.StreamInfo
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Independent fallback inspired by SimpMusic's BravePipe tier.
 * It is called only after InnerTubeX cannot resolve a playable stream.
 */
object SimpMusicFallbackPlayer {
    private const val TAG = "SimpMusicFallback"
    private const val CLIENT_NAME = "BravePipe"
    private const val DEFAULT_EXPIRY_SECONDS = 300

    private val initialized = AtomicBoolean(false)
    private val downloader = BravePipeDownloader()

    fun initialize() {
        if (initialized.compareAndSet(false, true)) {
            NewPipe.init(downloader)
        }
    }

    suspend fun playerResponseForPlayback(videoId: String): Result<PlaybackData> =
        runCatching {
            val info =
                StreamInfo.getInfo(
                    ServiceList.YouTube,
                    "https://music.youtube.com/watch?v=$videoId",
                )
            val stream =
                info.audioStreams
                    .asSequence()
                    .filter { !it.content.isNullOrBlank() }
                    .maxByOrNull { it.itagItem?.id ?: 0 }
                    ?: error("BravePipe returned no audio stream")
            val url = stream.content
            require(!url.isNullOrBlank()) { "BravePipe returned an empty stream URL" }
            Timber.tag(TAG).i("Fallback stream resolved: video=%s itag=%s", videoId, stream.itagItem?.id)
            PlaybackData(
                audioConfig = null,
                videoDetails = null,
                playbackTracking = null,
                format =
                    PlayerResponse.StreamingData.Format(
                        itag = stream.itagItem?.id ?: 0,
                        url = url,
                        mimeType = "audio/webm",
                        bitrate = 0,
                        width = null,
                        height = null,
                        contentLength = null,
                        quality = "",
                        fps = null,
                        qualityLabel = null,
                        averageBitrate = null,
                        audioQuality = null,
                        approxDurationMs = null,
                        audioSampleRate = null,
                        audioChannels = null,
                        loudnessDb = null,
                        lastModified = null,
                        signatureCipher = null,
                        cipher = null,
                        audioTrack = null,
                    ),
                streamUrl = url,
                streamExpiresInSeconds = DEFAULT_EXPIRY_SECONDS,
                streamClient = CLIENT_NAME,
                streamHeaders = emptyMap(),
                requireBoundedRange = false,
                rangeChunkSizeBytes = 0L,
                useRangeChunks = false,
            )
        }.onFailure {
            Timber.tag(TAG).w(it, "BravePipe fallback failed for video=%s", videoId)
        }

    private class BravePipeDownloader : Downloader() {
        private val client = OkHttpClient()

        @Throws(IOException::class, ReCaptchaException::class)
        override fun execute(request: NewPipeRequest): NewPipeResponse {
            val body = request.dataToSend()?.toRequestBody()
            val builder =
                Request.Builder()
                    .url(request.url())
                    .method(request.httpMethod(), body)
                    .header("User-Agent", USER_AGENT)
            request.headers().forEach { (name, values) ->
                builder.removeHeader(name)
                values.forEach { value -> builder.addHeader(name, value) }
            }
            client.newCall(builder.build()).execute().use { response ->
                if (response.code == 429) throw ReCaptchaException("YouTube requested a challenge", request.url())
                return NewPipeResponse(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),
                    response.body?.string(),
                    response.request.url.toString(),
                )
            }
        }

        private companion object {
            const val USER_AGENT =
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36"
        }
    }
}
