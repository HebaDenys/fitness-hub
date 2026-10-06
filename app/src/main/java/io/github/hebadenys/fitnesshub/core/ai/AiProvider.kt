package io.github.hebadenys.fitnesshub.core.ai

/**
 * Supported LLM providers.
 *
 * Every provider is bring-your-own-key: there is no Fitness Hub account, no proxy
 * and no server-side relay. The API key is supplied by the user and travels only
 * to the provider named here.
 */
enum class AiProvider(
    val id: String,
    val endpoint: String,
    val headerName: String,
    val defaultModel: String
) {
    OPENAI(
        id = "openai",
        endpoint = "https://api.openai.com/v1/chat/completions",
        headerName = "Authorization",
        defaultModel = "gpt-4o-mini"
    ),
    GEMINI(
        id = "gemini",
        endpoint = "https://generativelanguage.googleapis.com/v1beta/models",
        headerName = "x-goog-api-key",
        defaultModel = "gemini-2.0-flash"
    ),
    ANTHROPIC(
        id = "anthropic",
        endpoint = "https://api.anthropic.com/v1/messages",
        headerName = "x-api-key",
        defaultModel = "claude-3-5-haiku-latest"
    );

    /** Formats the Authorization value for this provider. */
    fun authorizationValue(apiKey: String): String = when (this) {
        OPENAI -> "Bearer $apiKey"
        GEMINI, ANTHROPIC -> apiKey
    }

    /** Resolves the concrete URL, which for Gemini embeds the model in the path. */
    fun requestUrl(model: String): String = when (this) {
        GEMINI -> "$endpoint/$model:generateContent"
        else -> endpoint
    }
}

/**
 * AI configuration. `enabled` defaults to false: the feature is off unless the
 * user actively turns it on, and turning it on still does nothing until a key is
 * stored and a payload is approved.
 */
data class AiSettings(
    val enabled: Boolean = false,
    val provider: AiProvider? = null,
    val model: String? = null
) {
    /**
     * A request may only be built when the feature is on, a provider is chosen
     * and a key exists. Keeping this as a single predicate means no call site can
     * accidentally skip one of the three preconditions.
     */
    fun canRequest(hasStoredKey: Boolean): Boolean = enabled && provider != null && hasStoredKey

    val effectiveModel: String get() = model?.takeIf { it.isNotBlank() } ?: provider?.defaultModel.orEmpty()
}

/**
 * The exact bytes that would leave the device.
 *
 * Nothing is sent implicitly: a payload is constructed, shown to the user, and
 * only then dispatched. [preview] is what the confirmation dialog renders, and it
 * masks the credential so a screenshot of the confirmation screen cannot leak the
 * key the user pasted.
 */
data class AiRequestPayload(
    val provider: AiProvider,
    val url: String,
    val model: String,
    val body: String,
    val apiKey: String
) {
    /** Header names that would be sent, with the credential value masked. */
    fun preview(): String = buildString {
        appendLine("POST $url")
        appendLine("Content-Type: application/json")
        appendLine("${provider.headerName}: ${MASK}")
        appendLine()
        appendLine("Body:")
        append(body)
    }

    companion object {
        const val MASK = "<your key>"
    }
}
