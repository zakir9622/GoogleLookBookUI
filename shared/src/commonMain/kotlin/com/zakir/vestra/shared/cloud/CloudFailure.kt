package com.zakir.vestra.shared.cloud

/**
 * Typed cloud failure — classified once at the client boundary, never re-matched by substring downstream.
 */
sealed interface CloudFailure {
    val retryable: Boolean
    val advanceModel: Boolean
    val retryVariants: Boolean

    data object Offline : CloudFailure {
        override val retryable = false
        override val advanceModel = false
        override val retryVariants = false
    }

    data class QuotaExhausted(val scope: Scope) : CloudFailure {
        enum class Scope { ACCOUNT, MODEL }

        override val retryable = false
        /** Always try another route — ACCOUNT skips Spaces in GenerativeCloudService. */
        override val advanceModel = true
        override val retryVariants = false
    }

    data object CreditsExhausted : CloudFailure {
        override val retryable = false
        override val advanceModel = true
        override val retryVariants = false
    }

    /** Space / host returned 404 or is unreachable at the API path — try the next model. */
    data object HostUnavailable : CloudFailure {
        override val retryable = true
        override val advanceModel = true
        override val retryVariants = false
    }

    data object AuthRejected : CloudFailure {
        override val retryable = false
        override val advanceModel = true
        override val retryVariants = false
    }

    data object RouteUnsupported : CloudFailure {
        override val retryable = false
        override val advanceModel = true
        override val retryVariants = false
    }

    data object SchemaRejected : CloudFailure {
        override val retryable = false
        override val advanceModel = true
        override val retryVariants = false
    }

    data object Busy : CloudFailure {
        override val retryable = true
        override val advanceModel = true
        override val retryVariants = false
    }

    data object Waking : CloudFailure {
        override val retryable = true
        override val advanceModel = true
        override val retryVariants = false
    }

    data object Timeout : CloudFailure {
        override val retryable = true
        override val advanceModel = true
        override val retryVariants = false
    }

    data object SafetyBlocked : CloudFailure {
        override val retryable = true
        override val advanceModel = false
        override val retryVariants = true
    }

    data object BadOutput : CloudFailure {
        override val retryable = true
        override val advanceModel = false
        override val retryVariants = true
    }

    data class Unknown(val raw: String) : CloudFailure {
        override val retryable = true
        override val advanceModel = true
        override val retryVariants = false
    }
}

class CloudFailureException(val failure: CloudFailure) : Exception(failure.toUserHint())

/** Strip hostnames / URLs from user-facing error fragments. */
fun sanitizeHostnames(raw: String): String {
    var s = raw
    // https://host/... or http://host
    s = HOST_URL_REGEX.replace(s, "[host]")
    // bare foo.hf.space / api.example.com (keep short tokens like 404)
    s = BARE_HOST_REGEX.replace(s) { m ->
        val host = m.value
        if (host.contains('.') && host.any { it.isLetter() }) "[host]" else host
    }
    return s
}

private val HOST_URL_REGEX = Regex(
    """https?://[^\s"'<>]+""",
    RegexOption.IGNORE_CASE,
)
private val BARE_HOST_REGEX = Regex(
    """\b(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+(?:hf\.space|huggingface\.co|groq\.com|openrouter\.ai|[a-z]{2,})\b""",
    RegexOption.IGNORE_CASE,
)

fun CloudFailure.toUserHint(): String = when (this) {
    CloudFailure.Offline -> "No internet connection"
    is CloudFailure.QuotaExhausted -> when (scope) {
        CloudFailure.QuotaExhausted.Scope.ACCOUNT -> "ZeroGPU quota exhausted"
        CloudFailure.QuotaExhausted.Scope.MODEL -> "Model quota exhausted"
    }
    CloudFailure.CreditsExhausted -> "Inference Providers monthly credits used up"
    CloudFailure.HostUnavailable -> "Model host looks offline (404)"
    CloudFailure.AuthRejected -> "API key rejected"
    CloudFailure.RouteUnsupported -> "Model not supported by provider"
    CloudFailure.SchemaRejected -> "Space schema rejected the request"
    CloudFailure.Busy -> "Service busy"
    CloudFailure.Waking -> "Space waking up"
    CloudFailure.Timeout -> "Request timed out"
    CloudFailure.SafetyBlocked -> "Content blocked by safety filter"
    CloudFailure.BadOutput -> "Invalid output received"
    is CloudFailure.Unknown -> sanitizeHostnames(raw).take(220)
}

object CloudFailureClassifier {
    fun from(throwable: Throwable): CloudFailure {
        if (throwable is CloudFailureException) return throwable.failure
        return fromMessage(throwable.message.orEmpty())
    }

    fun fromMessage(msg: String): CloudFailure {
        val lower = msg.lowercase()
        return when {
            lower.contains("no internet") ||
                lower.contains("unable to resolve host") ||
                lower.contains("unknownhostexception") ||
                lower.contains("network is unreachable") -> CloudFailure.Offline

            // Mid-transfer / peer refused — Space or DNS blip, not "phone has no internet".
            lower.contains("connection abort") ||
                lower.contains("connection reset") ||
                lower.contains("broken pipe") ||
                lower.contains("econnreset") ||
                lower.contains("econnaborted") ||
                lower.contains("software caused connection") ||
                lower.contains("failed to connect") ||
                lower.contains("connection refused") ||
                lower.contains("connectexception") -> CloudFailure.Timeout

            lower.contains("402") ||
                lower.contains("depleted your monthly") ||
                lower.contains("inference providers monthly credits") ||
                lower.contains("monthly credits are used up") -> CloudFailure.CreditsExhausted

            // Gradio dialect probes already consume both prefixes; a surviving 404 means
            // the Space API path is gone — advance to the next candidate.
            lower.contains("looks offline (404)") ||
                (lower.contains("404") &&
                    (lower.contains("space") || lower.contains("gradio") || lower.contains("hf.space") ||
                        lower.contains("not found"))) -> CloudFailure.HostUnavailable

            lower.contains("quota exceeded") ||
                lower.contains("zerogpu quota") ||
                lower.contains("exceeded your free zerogpu") ||
                lower.contains("0s left") -> CloudFailure.QuotaExhausted(CloudFailure.QuotaExhausted.Scope.ACCOUNT)

            lower.contains("401") ||
                lower.contains("unauthorized") ||
                lower.contains("token rejected") -> CloudFailure.AuthRejected

            lower.contains("model not supported by provider") -> CloudFailure.RouteUnsupported

            lower.contains("event: error") && lower.contains("null") ||
                lower.contains("empty error") ||
                lower.contains("pydantic") ||
                lower.contains("schema") && lower.contains("reject") -> CloudFailure.SchemaRejected

            lower.contains("nsfw") ||
                lower.contains("safety") ||
                lower.contains("content policy") ||
                lower.contains("blocked") && !lower.contains("inference") -> CloudFailure.SafetyBlocked

            lower.contains("timeout") || lower.contains("timed out") -> CloudFailure.Timeout

            lower.contains("queue is full") ||
                lower.contains("503") ||
                lower.contains("service unavailable") -> CloudFailure.Busy

            lower.contains("waking") || lower.contains("restarting") -> CloudFailure.Waking

            lower.contains("too small") ||
                lower.contains("not a recognizable image") ||
                lower.contains("downloaded file is empty") ||
                lower.contains("looks blank") ||
                lower.contains("low variance") -> CloudFailure.BadOutput

            msg.isBlank() -> CloudFailure.Unknown("Generation failed")

            else -> CloudFailure.Unknown(sanitizeHostnames(msg).take(220))
        }
    }
}
