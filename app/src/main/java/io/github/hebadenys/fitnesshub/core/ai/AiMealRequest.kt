package io.github.hebadenys.fitnesshub.core.ai

import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds the request that asks a provider to turn free text into a food or
 * exercise log.
 *
 * The builder is deliberately pure: it produces a payload for the user to inspect
 * and does not perform any I/O. Nothing leaves the device in this class, which is
 * what makes the "show the exact payload before sending" guarantee checkable
 * rather than aspirational.
 *
 * Only text the user typed and context they explicitly opted into are included.
 * The rest of the health database is never attached automatically.
 */
object AiMealRequest {

    private const val SYSTEM_PROMPT =
        "Extract foods and exercises from the user's description. Reply with JSON only: " +
            "{\"items\":[{\"name\":String,\"grams\":Number|null,\"quantity\":Number|null," +
            "\"unit\":String|null,\"calories\":Number|null,\"proteinG\":Number|null," +
            "\"carbsG\":Number|null,\"fatG\":Number|null,\"confidence\":Number}]}. " +
            "Use null for any value the user did not state. Never invent a number."

    /**
     * Assembles the payload, or returns null when the feature is not fully
     * configured. Returning null rather than throwing keeps the disabled path
     * inert: a caller cannot accidentally proceed with a half-configured setup.
     */
    fun build(
        settings: AiSettings,
        apiKey: String,
        userText: String,
        includeRecentDays: Boolean = false,
        recentDays: List<String> = emptyList()
    ): AiRequestPayload? {
        val provider = settings.provider ?: return null
        val text = userText.trim()
        if (!settings.canRequest(apiKey.isNotBlank()) || text.isEmpty()) return null

        val model = settings.effectiveModel
        val userContent = JSONObject().apply {
            put("text", text)
            if (includeRecentDays && recentDays.isNotEmpty()) {
                put("contextDates", JSONArray(recentDays))
            }
        }

        val body = when (provider) {
            AiProvider.OPENAI -> openAiBody(model, SYSTEM_PROMPT, userContent)
            AiProvider.GEMINI -> geminiBody(SYSTEM_PROMPT, userContent)
            AiProvider.ANTHROPIC -> anthropicBody(model, SYSTEM_PROMPT, userContent)
        }

        return AiRequestPayload(
            provider = provider,
            url = provider.requestUrl(model),
            model = model,
            body = body,
            apiKey = apiKey.trim()
        )
    }

    private fun openAiBody(model: String, system: String, user: JSONObject): String =
        JSONObject().apply {
            put("model", model)
            put(
                "messages",
                JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", system))
                    put(JSONObject().put("role", "user").put("content", user.toString()))
                }
            )
            put("temperature", 0)
        }.toString()

    private fun geminiBody(system: String, user: JSONObject): String =
        JSONObject().apply {
            put(
                "contents",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("role", "user")
                            put(
                                "parts",
                                JSONArray().put(
                                    JSONObject().put(
                                        "text",
                                        system + "\n\n" + user.getString("text")
                                    )
                                )
                            )
                        }
                    )
                }
            )
            put(
                "generationConfig",
                JSONObject().put("temperature", 0).put("responseMimeType", "application/json")
            )
        }.toString()

    private fun anthropicBody(model: String, system: String, user: JSONObject): String =
        JSONObject().apply {
            put("model", model)
            put("max_tokens", MAX_TOKENS)
            put("temperature", 0)
            put("system", system)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().put("role", "user").put("content", user.getString("text"))
                )
            )
        }.toString()

    private const val MAX_TOKENS = 1024
}
