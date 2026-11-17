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
 * Run by run-e2e.sh after [BotApiQualificationTest] and the restart of admin_web, nlp_api,
 * build_worker and bot_api: everything that was written must be read back from Mongo (and GridFS for the model).
 * Excluded from a plain `mvn test`: run it with `-Dtest=AfterRestartTest` on a restarted stack.
 */
@DisplayName("After services restart")
class AfterRestartTest : E2eTestBase() {
    private val user = "e2e-user-after-restart-${System.currentTimeMillis()}"

    @Test
    @Order(1)
    fun `login to the Studio, the application exists`() {
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
    fun `the bot answers through the webhook with the reloaded model`() {
        eventually(Duration.ofSeconds(90), Duration.ofSeconds(3)) {
            val answers = api.talk("hello", user)
            assertTrue(answers.any { "Hello hello" in it }, "answers: $answers")
        }
    }

    @Test
    @Order(3)
    fun `exact sentence - entity read back from the database`() {
        api.assertExactTravelSentence()
    }

    @Test
    @Order(4)
    fun `close sentence - entity detected by the model loaded from GridFS`() {
        api.assertTravelVariantDetectedByModel(Duration.ofSeconds(60))
    }

    @Test
    @Order(5)
    fun `read-back of the validated and annotated sentences`() {
        page.assertSearchedSentence("hello", GREETINGS)
        val (sentence, city) = travelSentences.first()
        page.assertSearchedSentence(sentence, TRAVEL, annotatedText = city)
    }

    @Test
    @Order(6)
    fun `read-back of the dialogs from before the restart`() {
        page.assertDialogContains("Sorry - not understood")
    }

    @Test
    @Order(7)
    fun `qualification after restart, model rebuild and reload`() {
        val sentence = "good evening"
        page.addSentence(sentence)
        val card = page.sentenceCard(sentence)
        page.selectIntent(card, GREETINGS)
        card.validateSentence()
        assertThat(card).isHidden()
        // the sentence is read back from the database as soon as it is validated; another close sentence goes through the rebuilt model
        eventually(Duration.ofMinutes(2), Duration.ofSeconds(5)) {
            val answers = api.talk(sentence, user)
            assertTrue(answers.any { "Hello $sentence" in it }, "answers: $answers")
        }
        api.assertTravelVariantDetectedByModel(Duration.ofMinutes(2))
    }
}
