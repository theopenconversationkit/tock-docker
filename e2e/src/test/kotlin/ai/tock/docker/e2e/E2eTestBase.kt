package ai.tock.docker.e2e

import com.microsoft.playwright.APIRequestContext
import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.Tracing
import com.microsoft.playwright.assertions.PlaywrightAssertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.extension.TestWatcher
import java.nio.file.Path
import java.nio.file.Paths

/** Données partagées par les étapes, avant et après le redémarrage des services. */
object Scenario {
    const val GREETINGS = "greetings"
    val greetingVariants = listOf("hi", "good morning", "hey there")
    const val TRAVEL = "travel"
    const val CITY = "city"

    /** Phrases d'entraînement de l'intent travel, avec la ville annotée comme entité city. */
    val travelSentences =
        listOf(
            "I want to go to Paris" to "Paris",
            "book a train to London" to "London",
            "I would like to travel to Berlin" to "Berlin",
        )

    /** Même structure que "book a train to London", avec une autre ville : la phrase n'existe pas en base. */
    const val TRAVEL_VARIANT = "book a train to Paris"
    const val TRAVEL_VARIANT_CITY = "Paris"

    val cityEntityType = "${E2eConfig.NAMESPACE}:$CITY"
}

/**
 * Cycle de vie Playwright commun : un navigateur et une page partagés par les étapes d'une classe.
 * Les étapes partagent l'état de la stack : elles s'exécutent dans l'ordre et
 * les suivantes sont ignorées dès qu'une étape échoue (capture d'écran de l'étape en échec, trace de la classe).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
abstract class E2eTestBase {
    private val outputDir: Path = Paths.get("target", "playwright", javaClass.simpleName)

    private lateinit var playwright: Playwright
    private lateinit var browser: Browser
    private lateinit var context: BrowserContext
    protected lateinit var page: Page
    protected lateinit var api: APIRequestContext

    private var failed = false

    @RegisterExtension
    val watcher =
        object : TestWatcher {
            override fun testFailed(
                extensionContext: ExtensionContext,
                cause: Throwable?,
            ) {
                failed = true
                runCatching {
                    page.screenshot(
                        Page.ScreenshotOptions()
                            .setPath(outputDir.resolve("${extensionContext.requiredTestMethod.name}.png"))
                            .setFullPage(true),
                    )
                }
            }
        }

    @BeforeAll
    fun start() {
        PlaywrightAssertions.setDefaultAssertionTimeout(15_000.0)
        playwright = Playwright.create()
        browser = playwright.chromium().launch(BrowserType.LaunchOptions().setHeadless(E2eConfig.headless))
        context = browser.newContext(Browser.NewContextOptions().setLocale("en-US").setViewportSize(1600, 1000))
        context.tracing().start(Tracing.StartOptions().setScreenshots(true).setSnapshots(true))
        page = context.newPage()
        api = playwright.request().newContext()
    }

    @AfterAll
    fun stop() {
        if (::context.isInitialized) {
            context.tracing().stop(Tracing.StopOptions().setPath(outputDir.resolve("trace.zip")))
        }
        if (::playwright.isInitialized) playwright.close()
    }

    @BeforeEach
    fun skipAfterFailure() {
        assumeFalse(failed, "une étape précédente a échoué")
    }
}
