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
 * Vérifications partagées par les étapes, avant et après le redémarrage des services.
 */

/** Phrase exacte : intent et entité relus depuis la phrase validée en base (probabilités de 1). */
fun APIRequestContext.assertExactTravelSentence() {
    val (sentence, city) = travelSentences.first()
    val result = parse(sentence)
    assertEquals(TRAVEL, result.path("intent").asText(), "résultat : $result")
    assertEquals(1.0, result.path("intentProbability").asDouble(), "résultat : $result")
    assertEquals(
        listOf(DetectedEntity(cityEntityType, city, 1.0)),
        result.detectedEntities(sentence),
        "résultat : $result",
    )
}

/** Phrase proche, absente de la base : intent et entité détectés par le modèle (probabilité d'entité < 1). */
fun APIRequestContext.assertTravelVariantDetectedByModel(timeout: Duration) {
    eventually(timeout, Duration.ofSeconds(10)) {
        val result = parse(TRAVEL_VARIANT)
        assertEquals(TRAVEL, result.path("intent").asText(), "résultat : $result")
        val entities = result.detectedEntities(TRAVEL_VARIANT)
        assertEquals(listOf(cityEntityType to TRAVEL_VARIANT_CITY), entities.map { it.type to it.text }, "résultat : $result")
        assertTrue(entities.single().probability < 1.0, "résultat : $result")
    }
}

/** Saisit une phrase dans « New sentence » et attend sa carte. */
fun Page.addSentence(sentence: String) {
    goTo("/language-understanding/try")
    val input = getByPlaceholder(containing("cancel my subscription"))
    input.fill(sentence)
    input.press("Enter")
    assertThat(sentenceCard(sentence)).isVisible()
}

/** Recherche une phrase validée : intent associé et, le cas échéant, texte annoté comme entité. */
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

/** Analytics > Dialogs : un dialogue contient le message du bot. */
fun Page.assertDialogContains(botMessage: String) {
    goTo("/analytics/dialogs")
    assertThat(
        locator("nb-card[id^=\"dialog-wrapper-\"]", Page.LocatorOptions().setHasText(containing(botMessage))).first(),
    ).isVisible()
}
