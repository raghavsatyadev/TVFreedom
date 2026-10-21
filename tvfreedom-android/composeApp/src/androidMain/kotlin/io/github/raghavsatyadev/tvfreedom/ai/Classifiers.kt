package io.github.raghavsatyadev.tvfreedom.ai

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import io.github.raghavsatyadev.tvfreedom.core.AskItem
import io.github.raghavsatyadev.tvfreedom.core.Classifier
import io.github.raghavsatyadev.tvfreedom.core.Example
import io.github.raghavsatyadev.tvfreedom.core.Prompt
import io.github.raghavsatyadev.tvfreedom.core.TvInfo
import io.github.raghavsatyadev.tvfreedom.core.Verdict
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withContext

/** Ask in batches; a failed batch is logged and its packages stay (no answer = keep). */
internal suspend fun inBatches(items: List<AskItem>, size: Int, log: (String) -> Unit, name: String,
                              ask: suspend (List<AskItem>) -> String): Map<String, Verdict> {
    val answers = linkedMapOf<String, Verdict>()
    items.chunked(size).forEachIndexed { i, chunk ->
        log("$name: ${chunk.size} packages (${i * size + chunk.size}/${items.size})")
        val text = runCatching { ask(chunk) }.getOrElse { log("$name failed: ${it.message ?: it.javaClass.simpleName}"); "" }
        answers += Prompt.parse(text, chunk.map { it.pkg }.toSet())
    }
    return answers
}

/** Gemini Nano through ML Kit's Prompt API: on the phone, free, offline. Small context, so small batches. */
/** [waitOpen] returns once TVFreedom is on screen: AICore refuses Gemini Nano calls from an app in the background. */
class NanoClassifier(private val waitOpen: suspend () -> Unit = {}) : Classifier {
    override val name = "Gemini Nano"
    private val model = Generation.getClient()

    /** "ready", or why not. Downloads the model first when the phone can. */
    suspend fun prepare(log: (String) -> Unit): String {
        when (model.checkStatus()) {
            FeatureStatus.AVAILABLE -> return READY
            FeatureStatus.UNAVAILABLE -> return "Gemini Nano is not available on this phone"
            else -> Unit // DOWNLOADABLE or DOWNLOADING
        }
        log("downloading Gemini Nano (once)")
        var result = "download did not finish"
        model.download().takeWhile { s ->
            when (s) {
                is DownloadStatus.DownloadCompleted -> { result = READY; false }
                is DownloadStatus.DownloadFailed -> { result = "download failed: ${s.e.message}"; false }
                else -> true
            }
        }.collect {}
        return result
    }

    override suspend fun classify(info: TvInfo, items: List<AskItem>, examples: Map<String, Example>,
                                  keep: Set<String>, log: (String) -> Unit): Map<String, Verdict> {
        val status = prepare(log)
        if (status != READY) {
            log("$status: every package the lists don't cover is kept")
            return emptyMap()
        }
        return inBatches(items, 10, log, name) { chunk ->
            val req = generateContentRequest(TextPart(Prompt.build(info, chunk, examples, keep, withExamples = false))) {
                temperature = 0f
                maxOutputTokens = 1024
            }
            waitOpen()
            model.generateContent(req).candidates.firstOrNull()?.text ?: ""
        }
    }

    override fun close() = model.close()

    companion object {
        const val READY = "ready"
    }
}

/** Claude through the Anthropic Java SDK, with the user's own API key. Sees the other TVs' lists as examples. */
class ClaudeClassifier(private val apiKey: String, private val model: String) : Classifier {
    override val name = "Claude"

    override suspend fun classify(info: TvInfo, items: List<AskItem>, examples: Map<String, Example>,
                                  keep: Set<String>, log: (String) -> Unit): Map<String, Verdict> = withContext(Dispatchers.IO) {
        val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
        try {
            inBatches(items, 60, log, name) { chunk ->
                val params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(16000L)
                    .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                    .addUserMessage(Prompt.build(info, chunk, examples, keep, withExamples = true))
                    .build()
                val msg = client.messages().create(params)
                msg.content().mapNotNull { b -> b.text().map { it.text() }.orElse(null) }.joinToString("")
                    .also { if (it.isEmpty()) log("Claude gave no text (stop: ${msg.stopReason().orElse(null)})") }
            }
        } finally {
            client.close()
        }
    }
}
