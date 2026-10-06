package io.github.hebadenys.fitnesshub.core.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class AiMealRequestTest {

    private val key = "sk-super-secret-value"

    private fun settings(provider: AiProvider, enabled: Boolean = true) =
        AiSettings(enabled = enabled, provider = provider)

    @Test
    @DisplayName("a disabled configuration builds no payload at all")
    fun disabledBuildsNothing() {
        for (provider in AiProvider.entries) {
            assertNull(
                AiMealRequest.build(settings(provider, enabled = false), key, "3 eggs"),
                "disabled ${provider.id} must not produce a request"
            )
        }
    }

    @Test
    @DisplayName("an enabled configuration without a stored key builds no payload")
    fun enabledWithoutKeyBuildsNothing() {
        assertNull(AiMealRequest.build(settings(AiProvider.OPENAI), "", "3 eggs"))
        assertNull(AiMealRequest.build(settings(AiProvider.OPENAI), "   ", "3 eggs"))
    }

    @Test
    @DisplayName("no provider selected builds no payload")
    fun noProviderBuildsNothing() {
        assertNull(AiMealRequest.build(AiSettings(enabled = true), key, "3 eggs"))
    }

    @Test
    @DisplayName("blank user input builds no payload")
    fun blankInputBuildsNothing() {
        assertNull(AiMealRequest.build(settings(AiProvider.OPENAI), key, "   "))
    }

    @ParameterizedTest
    @EnumSource(AiProvider::class)
    @DisplayName("the API key never appears in the request body")
    fun keyNeverAppearsInBody(provider: AiProvider) {
        val payload = AiMealRequest.build(settings(provider), key, "3 eggs, 50g oats")
        assertNotNull(payload)
        assertFalse(
            payload!!.body.contains(key),
            "${provider.id} leaked the API key into the body"
        )
    }

    @ParameterizedTest
    @EnumSource(AiProvider::class)
    @DisplayName("the preview masks the credential")
    fun previewMasksCredential(provider: AiProvider) {
        val payload = AiMealRequest.build(settings(provider), key, "3 eggs")!!
        val preview = payload.preview()
        assertFalse(preview.contains(key), "${provider.id} leaked the API key into the preview")
        assertTrue(preview.contains(AiRequestPayload.MASK))
        assertTrue(preview.contains(payload.url))
    }

    @Test
    @DisplayName("the preview shows the exact body that would be sent")
    fun previewContainsExactBody() {
        val payload = AiMealRequest.build(settings(AiProvider.OPENAI), key, "3 eggs")!!
        assertTrue(payload.preview().contains(payload.body))
    }

    @Test
    @DisplayName("only opted-in context dates are attached")
    fun contextDatesRequireOptIn() {
        val withoutOptIn = AiMealRequest.build(
            settings(AiProvider.OPENAI), key, "3 eggs", includeRecentDays = false,
            recentDays = listOf("2026-04-01")
        )!!
        assertFalse(withoutOptIn.body.contains("2026-04-01"))

        val withOptIn = AiMealRequest.build(
            settings(AiProvider.OPENAI), key, "3 eggs", includeRecentDays = true,
            recentDays = listOf("2026-04-01")
        )!!
        assertTrue(withOptIn.body.contains("2026-04-01"))
    }

    @Test
    @DisplayName("an empty context list attaches nothing even when opted in")
    fun emptyContextStaysEmpty() {
        val payload = AiMealRequest.build(
            settings(AiProvider.OPENAI), key, "3 eggs", includeRecentDays = true
        )!!
        assertFalse(payload.body.contains("contextDates"))
    }

    @Test
    @DisplayName("the user's own text is the only free-form content in the body")
    fun onlyUserTextIsIncluded() {
        val payload = AiMealRequest.build(settings(AiProvider.OPENAI), key, "3 eggs, 50g oats")!!
        assertTrue(payload.body.contains("3 eggs, 50g oats"))
    }

    @ParameterizedTest
    @EnumSource(AiProvider::class)
    @DisplayName("every provider posts to its own documented HTTPS endpoint")
    fun endpointsAreProviderSpecific(provider: AiProvider) {
        val payload = AiMealRequest.build(settings(provider), key, "3 eggs")!!
        assertTrue(payload.url.startsWith("https://"), "must not use plaintext transport")
        assertTrue(payload.url.startsWith(provider.endpoint))
    }

    @Test
    @DisplayName("Gemini embeds the model in the path, the others do not")
    fun geminiEmbedsModelInPath() {
        val gemini = AiMealRequest.build(
            settings(AiProvider.GEMINI), key, "3 eggs"
        )!!
        assertTrue(gemini.url.endsWith(":generateContent"))
        assertTrue(gemini.url.contains(AiProvider.GEMINI.defaultModel))

        val openAi = AiMealRequest.build(
            settings(AiProvider.OPENAI), key, "3 eggs"
        )!!
        assertFalse(openAi.url.contains(AiProvider.OPENAI.defaultModel))
    }

    @Test
    @DisplayName("an explicit model overrides the provider default")
    fun explicitModelWins() {
        val payload = AiMealRequest.build(
            AiSettings(enabled = true, provider = AiProvider.OPENAI, model = "gpt-4o"),
            key,
            "3 eggs"
        )!!
        assertEquals("gpt-4o", payload.model)
        assertTrue(payload.body.contains("gpt-4o"))
    }

    @Test
    @DisplayName("a blank model falls back to the provider default")
    fun blankModelFallsBack() {
        val payload = AiMealRequest.build(
            AiSettings(enabled = true, provider = AiProvider.OPENAI, model = "  "),
            key,
            "3 eggs"
        )!!
        assertEquals(AiProvider.OPENAI.defaultModel, payload.model)
    }

    @Test
    @DisplayName("the prompt forbids inventing values the user did not state")
    fun promptForbidsFabrication() {
        val payload = AiMealRequest.build(settings(AiProvider.OPENAI), key, "3 eggs")!!
        assertTrue(payload.body.contains("Never invent a number"))
    }

    @Test
    @DisplayName("provider authorization values follow each provider's scheme")
    fun authorizationSchemes() {
        assertEquals("Bearer abc", AiProvider.OPENAI.authorizationValue("abc"))
        assertEquals("abc", AiProvider.GEMINI.authorizationValue("abc"))
        assertEquals("abc", AiProvider.ANTHROPIC.authorizationValue("abc"))
    }

    @Test
    @DisplayName("canRequest requires all three preconditions together")
    fun canRequestNeedsAllPreconditions() {
        val key = "k"
        assertFalse(AiSettings(enabled = false, provider = AiProvider.OPENAI).canRequest(true))
        assertFalse(AiSettings(enabled = true, provider = null).canRequest(true))
        assertFalse(AiSettings(enabled = true, provider = AiProvider.OPENAI).canRequest(false))
        assertTrue(AiSettings(enabled = true, provider = AiProvider.OPENAI).canRequest(true))
    }
}
