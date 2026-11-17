package ai.tock.docker.e2e

import ai.tock.docker.e2e.Scenario.GREETINGS
import ai.tock.docker.e2e.Scenario.TRAVEL
import ai.tock.docker.e2e.Scenario.travelSentences
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * Exécuté par run-e2e.sh après [BotApiQualificationTest] et le redémarrage de admin_web, nlp_api,
 * build_worker et bot_api : tout ce qui a été écrit doit être relu depuis Mongo (et GridFS pour le modèle).
 * Exclu d'un `mvn test` simple : le lancer avec `-Dtest=AfterRestartTest` sur une stack redémarrée.
 */
@DisplayName("Après redémarrage des services")
class AfterRestartTest : E2eTestBase() {
    private val user = "e2e-user-after-restart-${System.currentTimeMillis()}"

    @Test
    @Order(1)
    fun `connexion au Studio, l'application existe`() {
        page.login()
        page.goTo("/configuration/bot")
        val item = page.locator("nb-accordion-item").first()
        val webhook = item.getByPlaceholder(containing("webhook url"))
        item.locator("nb-accordion-item-header").first().click()
        if (!webhook.isVisible) item.locator("nb-accordion-item-header").first().click()
        assertThat(webhook).hasValue(E2eConfig.webhookUrl)
    }

    @Test
    @Order(2)
    fun `le bot répond via le webhook avec le modèle rechargé`() {
        eventually(Duration.ofSeconds(90), Duration.ofSeconds(3)) {
            val answers = api.talk("hello", user)
            assertTrue(answers.any { "Hello hello" in it }, "réponses : $answers")
        }
    }

    @Test
    @Order(3)
    fun `phrase exacte - entité restituée depuis la base`() {
        api.assertExactTravelSentence()
    }

    @Test
    @Order(4)
    fun `phrase proche - entité détectée par le modèle chargé depuis GridFS`() {
        api.assertTravelVariantDetectedByModel(Duration.ofSeconds(60))
    }

    @Test
    @Order(5)
    fun `relecture des phrases validées et annotées`() {
        page.assertSearchedSentence("hello", GREETINGS)
        val (sentence, city) = travelSentences.first()
        page.assertSearchedSentence(sentence, TRAVEL, annotatedText = city)
    }

    @Test
    @Order(6)
    fun `relecture des dialogues d'avant le redémarrage`() {
        page.assertDialogContains("Sorry - not understood")
    }

    @Test
    @Order(7)
    fun `qualification après redémarrage, reconstruction et rechargement du modèle`() {
        val sentence = "good evening"
        page.addSentence(sentence)
        val card = page.sentenceCard(sentence)
        page.selectIntent(card, GREETINGS)
        card.validateSentence()
        assertThat(card).isHidden()
        // la phrase est relue depuis la base aussitôt validée ; une autre phrase proche passe par le modèle reconstruit
        eventually(Duration.ofMinutes(2), Duration.ofSeconds(5)) {
            val answers = api.talk(sentence, user)
            assertTrue(answers.any { "Hello $sentence" in it }, "réponses : $answers")
        }
        api.assertTravelVariantDetectedByModel(Duration.ofMinutes(2))
    }
}
