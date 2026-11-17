package ai.tock.docker.e2e

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.microsoft.playwright.APIRequestContext
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import com.microsoft.playwright.options.AriaRole
import com.microsoft.playwright.options.RequestOptions
import java.time.Duration
import java.util.regex.Pattern

/*
 * Tock Studio helpers: the fragile selectors (Nebular, icon-only buttons) are grouped here.
 */

private fun env(name: String, default: String): String = System.getenv(name)?.takeIf { it.isNotBlank() } ?: default

object E2eConfig {
    val studioUrl = env("STUDIO_URL", "http://localhost")
    val botApiUrl = env("BOT_API_URL", "http://localhost:8080")
    val nlpApiUrl = env("NLP_API_URL", "http://localhost:8888")
    val adminEmail = env("TOCK_ADMIN_EMAIL", "admin@app.com")
    val adminPassword = env("TOCK_ADMIN_PASSWORD", "password")
    val webhookUrl = env("DEMO_WEBHOOK_URL", "http://demo_api_webhook:8887")
    val headless = env("HEADLESS", "true").toBoolean()

    /** To iterate on an already initialized stack: the creation wizard is skipped if the application exists. */
    val reuseStack = env("E2E_REUSE_STACK", "false").toBoolean()
    const val APP_NAME = "new_assistant"
    const val NAMESPACE = "app"
    const val WEB_CONNECTOR_PATH = "/io/app/$APP_NAME/web"
}

/** JavaScript-compatible escaping: Playwright passes the Patterns to the browser (no \\Q...\\E). */
fun escapeRegex(text: String): String = text.replace(Regex("[.*+?^\${}()|\\[\\]\\\\/-]")) { "\\" + it.value }

fun exact(text: String): Pattern = Pattern.compile("^\\s*${escapeRegex(text)}\\s*$", Pattern.CASE_INSENSITIVE)

fun containing(text: String): Pattern = Pattern.compile(escapeRegex(text), Pattern.CASE_INSENSITIVE)

fun Page.button(name: Pattern): Locator = getByRole(AriaRole.BUTTON, Page.GetByRoleOptions().setName(name))

fun Locator.button(name: Pattern): Locator = getByRole(AriaRole.BUTTON, Locator.GetByRoleOptions().setName(name))

fun Page.login() {
    navigate("${E2eConfig.studioUrl}/login")
    locator("#input-email").fill(E2eConfig.adminEmail)
    locator("#input-password").fill(E2eConfig.adminPassword)
    button(containing("login")).click()
    assertThat(this).not().hasURL(Pattern.compile(".*/login.*"))
}

/**
 * Navigation inside the SPA (Angular router): a full reload makes the Studio lose its session.
 */
fun Page.goTo(path: String) {
    evaluate("p => { history.pushState({}, '', p); dispatchEvent(new PopStateEvent('popstate', { state: {} })); }", path)
    assertThat(this).hasURL(Pattern.compile(".*${escapeRegex(path)}.*"))
}

/**
 * Webhook url input of the first bot configuration (Settings > Configurations), its accordion item expanded.
 * The item is toggled only from its collapsed state: its content is rendered with an animation,
 * so the visibility of the input right after a click does not tell whether the item is expanded.
 */
fun Page.botWebhookInput(): Locator {
    val item = locator("nb-accordion-item").first()
    val expanded = Pattern.compile("(^|\\s)expanded(\\s|$)")
    assertThat(item).isVisible()
    if (!expanded.matcher(item.getAttribute("class").orEmpty()).find()) {
        item.locator("nb-accordion-item-header").first().click()
    }
    assertThat(item).hasClass(expanded)
    val webhook = item.getByPlaceholder(containing("webhook url"))
    assertThat(webhook).isEditable()
    return webhook
}

/** Opens a nb-select and picks the matching option (the list is rendered in an overlay). */
fun Page.selectNbOption(select: Locator, option: Pattern) {
    select.click()
    locator("nb-option-list nb-option", Page.LocatorOptions().setHasText(option)).first().click()
}

/** Card of a sentence in a tock-sentence-training list. */
fun Page.sentenceCard(text: String): Locator =
    locator("tock-sentence-training-entry")
        .filter(
            Locator.FilterOptions().setHas(
                locator(".tokens-container", Page.LocatorOptions().setHasText(exact(text))),
            ),
        ).first()

/** Creates an intent from the card (compass button "Create new Intent"); it is associated with the sentence. */
fun Page.createIntentFromSentence(card: Locator, intentName: String) {
    card.locator("button.lineFirstButtonRetract:has(nb-icon[icon=\"compass\"])").click()
    val dialog = locator("nb-dialog-container")
    dialog.locator("input[name=\"label\"]").fill(intentName)
    dialog.locator("input[name=\"name\"]").fill(intentName)
    dialog.button(exact("create")).click()
    assertThat(dialog).isHidden()
}

/** Associates an existing intent through the card autocomplete. */
fun Page.selectIntent(card: Locator, intentName: String) {
    val input = card.locator("input.intent-input")
    input.click()
    input.fill(intentName)
    locator("nb-option-list nb-option", Page.LocatorOptions().setHasText(exact(intentName))).first().click()
}

/**
 * Annotates [text] of the sentence with the [entityType] entity: the Studio reacts to a text selection (window.getSelection)
 * followed by a mouseup on the sentence component. The entity is created if it is not offered yet.
 */
fun Page.annotateEntity(
    card: Locator,
    text: String,
    entityType: String,
) {
    val sentence = card.locator("tock-sentence-training-sentence")
    sentence.locator(".tokens-container").evaluate(
        """(container, text) => {
            const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT);
            let node;
            while ((node = walker.nextNode())) {
                const index = node.textContent.indexOf(text);
                if (index >= 0) {
                    const range = document.createRange();
                    range.setStart(node, index);
                    range.setEnd(node, index + text.length);
                    const selection = window.getSelection();
                    selection.removeAllRanges();
                    selection.addRange(range);
                    node.parentElement.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
                    return;
                }
            }
            throw new Error('text not found: ' + text);
        }""",
        text,
    )
    val existing = card.locator(".token-selector", Locator.LocatorOptions().setHasText(exact(entityType)))
    if (existing.count() > 0) {
        existing.first().click()
    } else {
        card.button(containing("add entity")).click()
        val dialog = locator("nb-dialog-container")
        dialog.getByPlaceholder(exact("Entity Type")).fill(entityType)
        dialog.button(exact("add")).click()
        assertThat(dialog).isHidden()
    }
    assertThat(sentence.locator("span.token", Locator.LocatorOptions().setHasText(exact(text)))).isVisible()
}

fun Locator.validateSentence() {
    locator("button[status=\"success\"]:has(nb-icon[icon=\"check-circle-fill\"])").click()
}

private val mapper = jacksonObjectMapper()

/** Sends a message to the bot through the bot_api web connector and returns the answer texts. */
fun APIRequestContext.talk(query: String, userId: String): List<String> {
    val response =
        post(
            "${E2eConfig.botApiUrl}${E2eConfig.WEB_CONNECTOR_PATH}",
            RequestOptions.create()
                .setHeader("Content-Type", "application/json")
                .setData(mapOf("query" to query, "userId" to userId, "locale" to "en"))
                .setFailOnStatusCode(false)
                .setTimeout(60_000.0),
        )
    if (!response.ok()) return listOf("HTTP ${response.status()}")
    val body: JsonNode = mapper.readTree(response.text())
    return body.path("responses").map { it.path("text").asText(it.toString()) }
}

/** NLP parsing of a sentence through nlp_api, without saving it in the Inbox (`test = true`). */
fun APIRequestContext.parse(query: String): JsonNode {
    val response =
        post(
            "${E2eConfig.nlpApiUrl}/rest/nlp/parse",
            RequestOptions.create()
                .setHeader("Content-Type", "application/json")
                .setData(
                    mapOf(
                        "queries" to listOf(query),
                        "namespace" to E2eConfig.NAMESPACE,
                        "applicationName" to E2eConfig.APP_NAME,
                        "context" to mapOf("language" to "en", "test" to true),
                    ),
                ).setFailOnStatusCode(false)
                .setTimeout(60_000.0),
        )
    if (!response.ok()) throw AssertionError("parse \"$query\": HTTP ${response.status()} ${response.text()}")
    return mapper.readTree(response.text())
}

/** Detected entities: "type name" -> (recognized text, probability). */
data class DetectedEntity(val type: String, val text: String, val probability: Double)

fun JsonNode.detectedEntities(query: String): List<DetectedEntity> =
    path("entities").map {
        DetectedEntity(
            type = it.path("entity").path("entityType").path("name").asText(),
            text = query.substring(it.path("start").asInt(), it.path("end").asInt()),
            probability = it.path("probability").asDouble(),
        )
    }

/** Retries [block] until it succeeds or [timeout] has elapsed. */
fun <T> eventually(
    timeout: Duration,
    interval: Duration = Duration.ofSeconds(5),
    block: () -> T,
): T {
    val deadline = System.nanoTime() + timeout.toNanos()
    while (true) {
        try {
            return block()
        } catch (e: AssertionError) {
            if (System.nanoTime() > deadline) throw e
        }
        Thread.sleep(interval.toMillis())
    }
}
