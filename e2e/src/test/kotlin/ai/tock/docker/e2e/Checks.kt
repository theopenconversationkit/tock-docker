package ai.tock.docker.e2e

import ai.tock.docker.e2e.Scenario.TRAVEL
import ai.tock.docker.e2e.Scenario.TRAVEL_VARIANT
import ai.tock.docker.e2e.Scenario.TRAVEL_VARIANT_CITY
import ai.tock.docker.e2e.Scenario.cityEntityType
import ai.tock.docker.e2e.Scenario.travelSentences
import com.microsoft.playwright.APIRequestContext
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.Duration

/*
 * Checks shared by the steps, before and after the services restart.
 */

/** Exact sentence: intent and entity read back from the validated sentence in database (probabilities of 1). */
fun APIRequestContext.assertExactTravelSentence() {
    val (sentence, city) = travelSentences.first()
    val result = parse(sentence)
    assertEquals(TRAVEL, result.path("intent").asText(), "result: $result")
    assertEquals(1.0, result.path("intentProbability").asDouble(), "result: $result")
    assertEquals(
        listOf(DetectedEntity(cityEntityType, city, 1.0)),
        result.detectedEntities(sentence),
        "result: $result",
    )
}

/** Close sentence, not in the database: intent and entity detected by the model (entity probability < 1). */
fun APIRequestContext.assertTravelVariantDetectedByModel(timeout: Duration) {
    eventually(timeout, Duration.ofSeconds(10)) {
        val result = parse(TRAVEL_VARIANT)
        assertEquals(TRAVEL, result.path("intent").asText(), "result: $result")
        val entities = result.detectedEntities(TRAVEL_VARIANT)
        assertEquals(listOf(cityEntityType to TRAVEL_VARIANT_CITY), entities.map { it.type to it.text }, "result: $result")
        assertTrue(entities.single().probability < 1.0, "result: $result")
    }
}

/** Types a sentence in "New sentence" and waits for its card. */
fun Page.addSentence(sentence: String) {
    goTo("/language-understanding/try")
    val input = getByPlaceholder(containing("cancel my subscription"))
    input.fill(sentence)
    input.press("Enter")
    assertThat(sentenceCard(sentence)).isVisible()
}

/** Searches a validated sentence: associated intent and, if any, text annotated as an entity. */
fun Page.assertSearchedSentence(
    sentence: String,
    intent: String,
    annotatedText: String? = null,
) {
    goTo("/language-understanding/search")
    locator("input[formcontrolname=\"search\"]").fill(sentence)
    val card = sentenceCard(sentence)
    assertThat(card).isVisible()
    assertThat(card.locator("input.intent-input")).hasAttribute("placeholder", intent)
    if (annotatedText != null) {
        assertThat(
            card.locator("tock-sentence-training-sentence span.token", Locator.LocatorOptions().setHasText(exact(annotatedText))),
        ).isVisible()
    }
}

/** Analytics > Dialogs: a dialog contains the bot message. */
fun Page.assertDialogContains(botMessage: String) {
    goTo("/analytics/dialogs")
    assertThat(
        locator("nb-card[id^=\"dialog-wrapper-\"]", Page.LocatorOptions().setHasText(containing(botMessage))).first(),
    ).isVisible()
}
