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
 * Scénario : création d'un bot en mode API (webhook) depuis Tock Studio, dialogue,
 * qualification de phrases dans l'Inbox, reconstruction du modèle puis reconnaissance de l'intent ;
 * modification du modèle avec une entité, vérifiée sur une phrase en base et sur une phrase proche ;
 * relecture dans le Studio des données écrites (intents, dialogues, builds du modèle).
 *
 * Bot démo (tock-bot-demo) : intent `greetings` -> "Hello <message>", sinon "Sorry - not understood".
 */
@DisplayName("Bot API + qualification")
class BotApiQualificationTest : E2eTestBase() {
    private val user = "e2e-user-${System.currentTimeMillis()}"

    @Test
    @Order(1)
    fun `connexion et création de l'application via l'assistant`() {
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
    fun `configuration du webhook du bot`() {
        page.goTo("/configuration/bot")
        val item = page.locator("nb-accordion-item").first()
        val webhook = item.getByPlaceholder(containing("webhook url"))
        item.locator("nb-accordion-item-header").first().click()
        if (!webhook.isVisible) item.locator("nb-accordion-item-header").first().click()
        webhook.fill(E2eConfig.webhookUrl)
        webhook.press("Enter")
        assertThat(page.getByText(containing("webhook saved"))).isVisible()
    }

    @Test
    @Order(3)
    fun `le bot en mode API répond via le webhook (intent inconnu)`() {
        eventually(Duration.ofSeconds(90), Duration.ofSeconds(3)) {
            val answers = api.talk("hello", user)
            assertTrue(answers.any { "Sorry - not understood" in it }, "réponses : $answers")
        }
    }

    @Test
    @Order(4)
    fun `qualification de la phrase dans l'Inbox`() {
        page.goTo("/language-understanding/inbox")
        val card = page.sentenceCard("hello")
        assertThat(card).isVisible()
        page.createIntentFromSentence(card, GREETINGS)
        card.validateSentence()
        assertThat(page.sentenceCard("hello")).isHidden()
    }

    @Test
    @Order(5)
    fun `ajout de variantes qualifiées`() {
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
    fun `après reconstruction du modèle, le bot reconnaît l'intent`() {
        eventually(Duration.ofMinutes(4), Duration.ofSeconds(10)) {
            val answers = api.talk("hello", user)
            assertTrue(answers.any { "Hello hello" in it }, "réponses : $answers")
        }
    }

    @Test
    @Order(7)
    fun `page Test du Studio`() {
        page.goTo("/test/test")
        page.getByPlaceholder(containing("type some text")).fill("hi")
        page.button(exact("go")).click()
        assertThat(page.getByText("Hello hi")).isVisible(LocatorAssertions.IsVisibleOptions().setTimeout(30_000.0))
    }

    @Test
    @Order(8)
    fun `relecture - phrase validée avec l'intent`() {
        page.assertSearchedSentence("hello", GREETINGS)
    }

    @Test
    @Order(9)
    fun `ajout de trois phrases avec une entité`() {
        travelSentences.forEachIndexed { index, (sentence, city) ->
            page.addSentence(sentence)
            val card = page.sentenceCard(sentence)
            // l'intent créé depuis la carte n'autorise pas encore l'annotation : il faut le choisir dans l'autocomplete
            if (index == 0) page.createIntentFromSentence(card, TRAVEL)
            page.selectIntent(card, TRAVEL)
            page.annotateEntity(card, city, CITY)
            card.validateSentence()
            assertThat(card).isHidden()
        }
    }

    @Test
    @Order(10)
    fun `phrase exacte - entité restituée depuis la phrase validée en base`() {
        api.assertExactTravelSentence()
    }

    @Test
    @Order(11)
    fun `phrase proche - entité détectée par le modèle`() {
        api.assertTravelVariantDetectedByModel(Duration.ofMinutes(4))
    }

    @Test
    @Order(12)
    fun `relecture - phrase annotée avec l'entité`() {
        val (sentence, city) = travelSentences.first()
        page.assertSearchedSentence(sentence, TRAVEL, annotatedText = city)
    }

    @Test
    @Order(13)
    fun `relecture - intents et entité dans le Studio`() {
        page.goTo("/language-understanding/intents")
        assertThat(page.getByText(exact(GREETINGS)).first()).isVisible()
        assertThat(page.getByText(exact(TRAVEL)).first()).isVisible()
        page.goTo("/language-understanding/entities")
        assertThat(page.getByText(containing(CITY)).first()).isVisible()
    }

    @Test
    @Order(14)
    fun `relecture - dialogue du bot dans Analytics`() {
        page.assertDialogContains("Hello hello")
    }

    @Test
    @Order(15)
    fun `relecture - builds du modèle`() {
        // au moins deux builds d'intents : greetings seul, puis greetings + travel
        eventually(Duration.ofSeconds(30), Duration.ofSeconds(3)) {
            page.goTo("/dashboard")
            page.goTo("/model-quality/model-builds")
            assertThat(page.locator("table tbody tr").first()).isVisible()
            assertTrue(page.locator("table tbody tr").count() >= 2, "builds : ${page.locator("table tbody").innerText()}")
        }
    }
}
