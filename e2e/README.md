# e2e scenario: bot in API mode + qualification

Playwright tests (Kotlin, JUnit 5) on the `docker-compose-bot-webhook.yml` stack.

`BotApiQualificationTest`:

1. login to the Studio, creation of the `new_assistant` application (wizard, Web connector);
2. input of the demo bot webhook URL (`tock/bot_api_webhook`, API mode);
3. dialog through the bot_api web connector: "hello" → `Sorry - not understood`;
4. qualification of "hello" in the Inbox (creation of the `greetings` intent) and of a few variants;
5. after the model is rebuilt by build_worker: "hello" → `Hello hello`;
6. Studio Test page: "hi" → `Hello hi`;
7. read-back of the validated sentence in the search page;
8. model update: three sentences of the `travel` intent with a city annotated as a `city` entity
   (text selection in the sentence card);
9. NLP API (`nlp_api`, `/rest/nlp/parse`) on the exact sentence: intent and entity read back from the validated sentence
   in database (probabilities of 1);
10. NLP API on a close sentence that is not in the database (`book a train to Paris`): intent and entity detected by
    the rebuilt model (entity probability < 1);
11. read-back in the Studio: annotated sentence (highlighted entity), intents and entity, bot dialog in
    Analytics > Dialogs, model builds in Model Quality > Model Builds.

Then `run-e2e.sh` restarts admin_web, nlp_api, build_worker and bot_api, and runs `AfterRestartTest`: everything must
be read back from Mongo (and GridFS for the model):

1. bot configuration (webhook URL) in the Studio;
2. bot answer through the webhook with the reloaded model;
3. exact sentence and close sentence through the NLP API;
4. validated and annotated sentences, dialogs from before the restart;
5. new qualification, model rebuild and reload by the restarted services.

Finally, the script prints a non-blocking report of the errors found in each service's logs (`logs/errors.txt`).

## Prerequisites

Docker (compose v2), JDK 21+, Maven. Free ports: 80, 8080, 8887, 8888, 27017-27019.

## Run

```sh
# published images (validates the scenario itself)
TAG=26.9.1 ./e2e/run-e2e.sh

# builds ../tock, then the SNAPSHOT images (bot_admin, nlp_api, build_worker, bot_api), and runs the tests
./e2e/run-e2e.sh --build

# keep the stack running at the end
./e2e/run-e2e.sh --keep
```

`TAG` defaults to the tock-docker pom version, i.e. the version of the images built by `--build`.
`duckling`, `kotlin_compiler` and the demo bot stay on `STABLE_TAG` (26.9.1 by default).

Against an already running stack: `mvn -f e2e/pom.xml test` (`HEADLESS=false` to see the browser);
`AfterRestartTest` is excluded by default: `mvn -f e2e/pom.xml test -Dtest=AfterRestartTest`.
If the application already exists (rerun on a stack kept with `--keep`), add `E2E_REUSE_STACK=true` to skip the wizard.

## On failure

- Playwright screenshots and trace: `e2e/target/playwright/<test class>/` (open `trace.zip` on
  https://trace.playwright.dev);
- docker logs: `e2e/logs/compose.log`, error summary: `e2e/logs/errors.txt`.

Duration: about 3 min excluding the build (stack startup included); the model rebuild is almost immediate
on this small set of sentences, the test waits for it up to 4 min.

## Things to know

- The webhook URL entered is `http://demo_api_webhook:8887`: bot_api appends `/webhook` itself.
- The Studio loses its session on a full page reload: the tests navigate through the Angular router (`Page.goTo`).
- An intent created from a sentence card does not enable entity annotation: the test then selects it in
  the autocomplete.
- With three examples, OpenNLP widens the entity of `I want to go to London` to `go to London`: the close sentence
  follows the structure of `book a train to London`, where only the city is detected.
- Expected errors in the log report: `MongoWriteException` code 66 at startup ("Fail to save built-in
  entity type", fixed in kmongo-dsl 5.14.1, not released yet) and `IllegalArgumentException` on
  `XMLConstants` during model builds.
- Java `Pattern`s are passed as is to the browser (JavaScript regex): do not use `Pattern.quote`, use `escapeRegex` instead.
