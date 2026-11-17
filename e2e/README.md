# Scénario e2e : bot en mode API + qualification

Tests Playwright (Kotlin, JUnit 5) sur la stack `docker-compose-bot-webhook.yml`.

`BotApiQualificationTest` :

1. connexion au Studio, création de l'application `new_assistant` (assistant, connecteur Web) ;
2. saisie de l'URL du webhook du bot démo (`tock/bot_api_webhook`, mode API) ;
3. dialogue via le connecteur web de bot_api : « hello » → `Sorry - not understood` ;
4. qualification de « hello » dans l'Inbox (création de l'intent `greetings`) et de quelques variantes ;
5. après reconstruction du modèle par build_worker : « hello » → `Hello hello` ;
6. page Test du Studio : « hi » → `Hello hi` ;
7. relecture de la phrase validée dans la recherche ;
8. modification du modèle : trois phrases de l'intent `travel` avec une ville annotée comme entité `city`
   (sélection du texte dans la carte de la phrase) ;
9. API NLP (`nlp_api`, `/rest/nlp/parse`) sur la phrase exacte : intent et entité relus depuis la phrase validée
   en base (probabilités de 1) ;
10. API NLP sur une phrase proche absente de la base (`book a train to Paris`) : intent et entité détectés par
    le modèle reconstruit (probabilité d'entité < 1) ;
11. relecture dans le Studio : phrase annotée (entité surlignée), intents et entité, dialogue du bot dans
    Analytics > Dialogs, builds du modèle dans Model Quality > Model Builds.

Puis `run-e2e.sh` redémarre admin_web, nlp_api, build_worker et bot_api, et lance `AfterRestartTest` : tout doit
être relu depuis Mongo (et GridFS pour le modèle) :

1. configuration du bot (URL du webhook) dans le Studio ;
2. réponse du bot via le webhook avec le modèle rechargé ;
3. phrase exacte et phrase proche via l'API NLP ;
4. phrases validées et annotées, dialogues d'avant le redémarrage ;
5. nouvelle qualification, reconstruction et rechargement du modèle par les services redémarrés.

Enfin, le script affiche un rapport non bloquant des erreurs des logs de chaque service (`logs/errors.txt`).

## Prérequis

Docker (compose v2), JDK 21+, Maven. Ports libres : 80, 8080, 8887, 8888, 27017-27019.

## Lancer

```sh
# images publiées (valide le scénario lui-même)
TAG=26.9.1 ./e2e/run-e2e.sh

# construit ../tock puis les images SNAPSHOT (bot_admin, nlp_api, build_worker, bot_api) et teste
./e2e/run-e2e.sh --build

# garder la stack à la fin
./e2e/run-e2e.sh --keep
```

`TAG` vaut par défaut la version du pom tock-docker, celle des images construites par `--build`.
`duckling`, `kotlin_compiler` et le bot démo restent sur `STABLE_TAG` (26.9.1 par défaut).

Contre une stack déjà démarrée : `mvn -f e2e/pom.xml test` (`HEADLESS=false` pour voir le navigateur) ;
`AfterRestartTest` est exclu par défaut : `mvn -f e2e/pom.xml test -Dtest=AfterRestartTest`.
Si l'application existe déjà (relance sur une stack conservée avec `--keep`), ajouter `E2E_REUSE_STACK=true` pour sauter l'assistant.

## En cas d'échec

- captures et trace Playwright : `e2e/target/playwright/<classe de test>/` (ouvrir `trace.zip` sur
  https://trace.playwright.dev) ;
- logs docker : `e2e/logs/compose.log`, résumé des erreurs : `e2e/logs/errors.txt`.

Durée : environ 3 min hors build (démarrage de la stack compris) ; la reconstruction du modèle est quasi immédiate
sur ce petit jeu de phrases, le test l'attend jusqu'à 4 min.

## Points à connaître

- L'URL de webhook saisie est `http://demo_api_webhook:8887` : bot_api ajoute lui-même `/webhook`.
- Le Studio perd sa session sur un rechargement complet : les tests naviguent via le routeur Angular (`Page.goTo`).
- L'intent créé depuis la carte d'une phrase ne débloque pas l'annotation d'entité : le test le choisit ensuite dans
  l'autocomplete.
- Avec trois exemples, OpenNLP élargit l'entité de `I want to go to London` à `go to London` : la phrase proche
  reprend la structure de `book a train to London`, où la ville seule est détectée.
- Erreurs attendues dans le rapport des logs : `MongoWriteException` code 66 au démarrage (« Fail to save built-in
  entity type », corrigée dans kmongo-dsl 5.14.1, pas encore publiée) et `IllegalArgumentException` sur
  `XMLConstants` pendant les builds du modèle.
- Les `Pattern` Java sont transmis tels quels au navigateur (regex JavaScript) : ne pas utiliser `Pattern.quote`, mais `escapeRegex`.
