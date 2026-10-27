package com.renata.claude.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The MiniMax rules that decide how a request is built.
 *
 * The behaviour differs per model family, so these tests keep the table from
 * the MiniMax documentation in one place.
 */
class MiniMaxSupportTest {

    private fun openAi(
        model: String,
        thinking: Boolean = false,
        effort: String = MiniMaxSupport.EFFORT_AUTO,
    ) = AppSettings(
        model = model,
        apiFormat = Providers.FORMAT_OPENAI,
        thinking = thinking,
        reasoningEffort = effort,
    )

    private fun anthropic(
        model: String,
        thinking: Boolean = false,
        effort: String = MiniMaxSupport.EFFORT_AUTO,
    ) = AppSettings(
        model = model,
        apiFormat = Providers.FORMAT_ANTHROPIC,
        thinking = thinking,
        reasoningEffort = effort,
    )

    // --- The thinking depth ------------------------------------------------

    @Test
    fun automaticDepthIsNotSent() {
        assertNull(MiniMaxSupport.effortValue("auto"))
        assertNull(MiniMaxSupport.effortValue(""))
        assertNull(MiniMaxSupport.effortValue("  "))
    }

    @Test
    fun onlyKnownDepthsAreSent() {
        assertEquals("high", MiniMaxSupport.effortValue("HIGH"))
        assertEquals("xhigh", MiniMaxSupport.effortValue(" xhigh "))
        assertNull(MiniMaxSupport.effortValue("none"))
        assertNull(MiniMaxSupport.effortValue("sangat-dalam"))
    }

    @Test
    fun onlyTheM31FamilyReadsTheDepth() {
        assertTrue(MiniMaxSupport.supportsEffort("MiniMax-M3.1-Flash"))
        assertFalse(MiniMaxSupport.supportsEffort("MiniMax-M3"))
        assertFalse(MiniMaxSupport.supportsEffort("MiniMax-M2.7"))
    }

    // --- The thinking parameter -------------------------------------------

    @Test
    fun m31AlwaysUsesAdaptiveThinking() {
        assertEquals("adaptive", MiniMaxSupport.thinkingParam(openAi("MiniMax-M3.1-Flash"))?.getString("type"))
        assertEquals("adaptive", MiniMaxSupport.thinkingParam(anthropic("MiniMax-M3.1-Flash"))?.getString("type"))
    }

    @Test
    fun m2AlwaysUsesAdaptiveThinking() {
        assertEquals("adaptive", MiniMaxSupport.thinkingParam(openAi("MiniMax-M2.7"))?.getString("type"))
        assertEquals("adaptive", MiniMaxSupport.thinkingParam(anthropic("MiniMax-M2.5"))?.getString("type"))
    }

    @Test
    fun m3InTheAnthropicFormatThinksOnlyWhenAsked() {
        assertNull(MiniMaxSupport.thinkingParam(anthropic("MiniMax-M3", thinking = false)))
        assertEquals(
            "adaptive",
            MiniMaxSupport.thinkingParam(anthropic("MiniMax-M3", thinking = true))?.getString("type"),
        )
    }

    @Test
    fun m3InTheOpenAiFormatIsSwitchedOffExplicitly() {
        assertEquals(
            "disabled",
            MiniMaxSupport.thinkingParam(openAi("MiniMax-M3", thinking = false))?.getString("type"),
        )
        assertEquals(
            "adaptive",
            MiniMaxSupport.thinkingParam(openAi("MiniMax-M3", thinking = true))?.getString("type"),
        )
    }

    @Test
    fun otherProvidersNeverGetTheMiniMaxThinkingParameter() {
        assertNull(MiniMaxSupport.thinkingParam(anthropic("claude-sonnet-4-5", thinking = true)))
    }

    // --- The served models --------------------------------------------------

    @Test
    fun theNewestModelComesFirst() {
        assertEquals(MiniMaxSupport.MODEL_M31_FLASH, MiniMaxSupport.availableModels.first())
        assertTrue(MiniMaxSupport.availableModels.contains(MiniMaxSupport.MODEL_M3))
    }

    @Test
    fun theRetiredModelsAreNotOffered() {
        assertFalse(MiniMaxSupport.isSupportedModel("MiniMax-M2.7"))
        assertFalse(MiniMaxSupport.isSupportedModel("MiniMax-M2.5"))
        assertTrue(MiniMaxSupport.isSupportedModel("MiniMax-M3.1-Flash"))
        assertTrue(MiniMaxSupport.isSupportedModel("minimax-m3"))
    }

    @Test
    fun everyMiniMaxPresetOffersTheNewestModelFirst() {
        listOf("minimax-anthropic", "minimax-openai").forEach { id ->
            val models = Providers.modelsFor(id)
            assertEquals(MiniMaxSupport.MODEL_M31_FLASH, models.first())
        }
    }

    // --- The split between thinking and answer ------------------------------

    @Test
    fun theThinkingIsSplitFromTheAnswerInTheOpenAiFormat() {
        assertTrue(MiniMaxSupport.reasoningSplit(openAi("MiniMax-M3.1-Flash")))
        assertTrue(MiniMaxSupport.reasoningSplit(openAi("MiniMax-M3")))
    }

    @Test
    fun theAnthropicFormatNeedsNoSplit() {
        assertFalse(MiniMaxSupport.reasoningSplit(anthropic("MiniMax-M3.1-Flash")))
        assertFalse(MiniMaxSupport.reasoningSplit(anthropic("MiniMax-M3")))
    }

    @Test
    fun otherProvidersAreNeverSplit() {
        assertFalse(MiniMaxSupport.reasoningSplit(openAi("gpt-4.1")))
    }

    // --- The token budget --------------------------------------------------

    @Test
    fun thinkingTokensGetRoomInTheBudget() {
        val settings = openAi("MiniMax-M3.1-Flash", thinking = true).copy(maxTokens = 1024)
        assertTrue(MiniMaxSupport.effectiveMaxTokens(settings) >= 8192)
    }

    @Test
    fun aModelThatAlwaysThinksKeepsRoomEvenWhenTheToggleIsOff() {
        val settings = openAi("MiniMax-M3.1-Flash", thinking = false).copy(maxTokens = 1024)
        assertTrue(MiniMaxSupport.effectiveMaxTokens(settings) >= 8192)
    }

    @Test
    fun aDeepDepthGetsEvenMoreRoom() {
        val settings = openAi("MiniMax-M3.1-Flash", thinking = true, effort = "max").copy(maxTokens = 1024)
        assertTrue(MiniMaxSupport.effectiveMaxTokens(settings) >= 16384)
    }

    @Test
    fun aSmallBudgetIsKeptWhenTheModelDoesNotThink() {
        val settings = anthropic("MiniMax-M3", thinking = false).copy(maxTokens = 1024)
        assertEquals(1024, MiniMaxSupport.effectiveMaxTokens(settings))
    }

    // --- The system prompt -------------------------------------------------

    @Test
    fun aCustomPromptWins() {
        val settings = anthropic("MiniMax-M3").copy(systemPrompt = "prompt saya")
        assertEquals("prompt saya", MiniMaxSupport.systemPromptFor(settings))
    }

    @Test
    fun miniMaxGetsTheProfessionalPrompt() {
        val prompt = MiniMaxSupport.systemPromptFor(openAi("MiniMax-M3.1-Flash"))
        assertTrue(prompt.contains("asisten AI profesional"))
    }

    @Test
    fun theToolInstructionsAreOnlyAddedWhenTheToolsAreOn() {
        val withTools = MiniMaxSupport.systemPromptFor(openAi("MiniMax-M3.1-Flash"), toolsEnabled = true)
        assertTrue(withTools.contains(ToolRegistry.NAME_CALCULATE))
        assertTrue(withTools.contains(ToolRegistry.NAME_DATETIME))
        assertTrue(withTools.contains(ToolRegistry.NAME_FETCH_URL))
        assertFalse(
            MiniMaxSupport.systemPromptFor(openAi("MiniMax-M3.1-Flash"))
                .contains(ToolRegistry.NAME_CALCULATE)
        )
    }

    @Test
    fun otherProvidersKeepTheirOwnBehaviour() {
        assertEquals("", MiniMaxSupport.systemPromptFor(AppSettings(model = "gpt-4.1")))
    }
}
