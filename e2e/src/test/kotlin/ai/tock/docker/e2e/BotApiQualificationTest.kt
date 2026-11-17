package ai.tock.docker.e2e

import ai.tock.docker.e2e.Scenario.CITY
import ai.tock.docker.e2e.Scenario.GREETINGS
import ai.tock.docker.e2e.Scenario.TRAVEL
import ai.tock.docker.e2e.Scenario.greetingVariants
import ai.tock.docker.e2e.Scenario.travelSentences
import com.microsoft.playwright.assertions.LocatorAssertions
import com.microsoft.playwright.assertions.PageAssertions
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.regex.Pattern

/**
 * Scenario: creation of a bot in API mode (webhook) from Tock Studio, dialog,
 * sentence qualification in the Inbox, model rebuild then intent recognition;
 * model update with an entity, checked on a sentence in database and on a close sentence;
 * read-back in the Studio of the written data (intents, dialogs, model builds).
 *
 * Demo bot (tock-bot-demo): intent `greetings` -> "Hello <message>", otherwise "Sorry - not understood".
 */
@DisplayName("Bot API + qualification")
class BotApiQualificationTest : E2eTestBase() {
    private val user = "e2e-user-${System.currentTimeMillis()}"

    @Test
    @Order(1)
    fun `login and application creation with the wizard`() {
        page.login()
        if (E2eConfig.reuseStack) {
            page.waitForURL(Pattern.compile(".*/(configuration/new|dashboard).*"))
            if (!page.url().contains("/configuration/new")) return
        }
        assertThat(page).hasURL(Pattern.compile(".*/configuration/new.*"))

        page.selectNbOption(page.button(exact("Language")), exact("English"))
        page.button(exact("next")).click()

        page.selectNbOption(page.button(exact("Channel")), exact("Web"))
        page.button(exact("next")).click()

        page.button(exact("create")).click()
        assertThat(page).hasURL(
            Pattern.compile(".*/dashboard.*"),
            PageAssertions.HasURLOptions().setTimeout(30_000.0),
        )
    }

    @Test
    @Order(2)
    fun `bot webhook configuration`() {
        page.goTo("/configuration/bot")
        val webhook = page.botWebhookInput()
        webhook.fill(E2eConfig.webhookUrl)
        webhook.press("Enter")
        assertThat(page.getByText(containing("webhook saved"))).isVisible()
    }

    @Test
    @Order(3)
    fun `the bot in API mode answers through the webhook (unknown intent)`() {
        eventually(Duration.ofSeconds(90), Duration.ofSeconds(3)) {
            val answers = api.talk("hello", user)
            assertTrue(answers.any { "Sorry - not understood" in it }, "answers: $answers")
        }
    }

    @Test
    @Order(4)
    fun `sentence qualification in the Inbox`() {
        page.goTo("/language-understanding/inbox")
        val card = page.sentenceCard("hello")
        assertThat(card).isVisible()
        page.createIntentFromSentence(card, GREETINGS)
        card.validateSentence()
        assertThat(page.sentenceCard("hello")).isHidden()
    }

    @Test
    @Order(5)
    fun `addition of qualified variants`() {
        for (variant in greetingVariants) {
            page.addSentence(variant)
            val card = page.sentenceCard(variant)
            page.selectIntent(card, GREETINGS)
            card.validateSentence()
            assertThat(card).isHidden()
        }
    }

    @Test
    @Order(6)
    fun `after the model rebuild, the bot recognizes the intent`() {
        eventually(Duration.ofMinutes(4), Duration.ofSeconds(10)) {
            val answers = api.talk("hello", user)
            assertTrue(answers.any { "Hello hello" in it }, "answers: $answers")
        }
    }

    @Test
    @Order(7)
    fun `Studio Test page`() {
        page.goTo("/test/test")
        page.getByPlaceholder(containing("type some text")).fill("hi")
        page.button(exact("go")).click()
        assertThat(page.getByText("Hello hi")).isVisible(LocatorAssertions.IsVisibleOptions().setTimeout(30_000.0))
    }

    @Test
    @Order(8)
    fun `read-back - validated sentence with the intent`() {
        page.assertSearchedSentence("hello", GREETINGS)
    }

    @Test
    @Order(9)
    fun `addition of three sentences with an entity`() {
        travelSentences.forEachIndexed { index, (sentence, city) ->
            page.addSentence(sentence)
            val card = page.sentenceCard(sentence)
            // the intent created from the card does not allow annotation yet: it must be selected in the autocomplete
            if (index == 0) page.createIntentFromSentence(card, TRAVEL)
            page.selectIntent(card, TRAVEL)
            page.annotateEntity(card, city, CITY)
            card.validateSentence()
            assertThat(card).isHidden()
        }
    }

    @Test
    @Order(10)
    fun `exact sentence - entity read back from the validated sentence in database`() {
        api.assertExactTravelSentence()
    }

    @Test
    @Order(11)
    fun `close sentence - entity detected by the model`() {
        api.assertTravelVariantDetectedByModel(Duration.ofMinutes(4))
    }

    @Test
    @Order(12)
    fun `read-back - sentence annotated with the entity`() {
        val (sentence, city) = travelSentences.first()
        page.assertSearchedSentence(sentence, TRAVEL, annotatedText = city)
    }

    @Test
    @Order(13)
    fun `read-back - intents and entity in the Studio`() {
        page.goTo("/language-understanding/intents")
        assertThat(page.getByText(exact(GREETINGS)).first()).isVisible()
        assertThat(page.getByText(exact(TRAVEL)).first()).isVisible()
        page.goTo("/language-understanding/entities")
        assertThat(page.getByText(containing(CITY)).first()).isVisible()
    }

    @Test
    @Order(14)
    fun `read-back - bot dialog in Analytics`() {
        page.assertDialogContains("Hello hello")
    }

    @Test
    @Order(15)
    fun `read-back - model builds`() {
        // at least two intent builds: greetings alone, then greetings + travel
        eventually(Duration.ofSeconds(30), Duration.ofSeconds(3)) {
            page.goTo("/dashboard")
            page.goTo("/model-quality/model-builds")
            assertThat(page.locator("table tbody tr").first()).isVisible()
            assertTrue(page.locator("table tbody tr").count() >= 2, "builds: ${page.locator("table tbody").innerText()}")
        }
    }
}
