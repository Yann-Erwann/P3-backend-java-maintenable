# Contribuer à ChaTop

ChaTop comprend un backend Spring Boot avec Java 25 et MySQL 8.4, ainsi qu’un
frontend Angular 20 exécuté avec Node.js 22. L’installation, la configuration
et le lancement sont décrits dans le [README principal](README.md) et le
[README du backend](backend/README.md).

Ces conventions s’appliquent aux prochaines contributions. Elles ne demandent
aucune réécriture de l’historique existant et n’imposent aucun hook Git.

## Périmètre des changements

Le frontend et le mock Mockoon fournis doivent être conservés : ne pas modifier
`frontend/`, son lockfile ou `frontend/ressources/mockoon/rental-oc.json` sans
autorisation explicite. Les fichiers Docker sont regroupés dans `docker/` et
la configuration locale se trouve dans `backend/.env`.

Conserver les données et les volumes MySQL et images existants. Ne pas créer de
scripts dans le dépôt. Documenter les commandes nécessaires dans les README.

## Messages de commit

Utiliser uniquement un titre en anglais, sans corps de description, de la forme :

```text
type(scope): describe the resulting change
```

Choisir le type selon la modification :

| Type       | Usage                                                             |
| ---------- | ----------------------------------------------------------------- |
| `feat`     | Nouvelle fonctionnalité.                                          |
| `fix`      | Correction d'un défaut.                                           |
| `refactor` | Restructuration sans changement du comportement attendu.          |
| `perf`     | Optimisation dont le bénéfice est mesuré.                         |
| `test`     | Rétablissement du socle de tests ou couverture transversale.      |
| `ci`       | Workflows GitHub Actions, vérifications et publication automatisée. |
| `docs`     | Documentation.                                                    |
| `build`    | Dépendances, migrations et configuration de build ou d'outillage. |
| `style`    | Modification de présentation du code sans effet fonctionnel.      |
| `chore`    | Maintenance qui ne relève pas des types précédents.               |

Le scope désigne une responsabilité, pas le type de changement. Utiliser ceux
qui correspondent à ChaTop :

| Scope | Responsabilité |
| --- | --- |
| `project` | Organisation, installation et documentation générale. |
| `git` | Conventions de contribution et configuration Git. |
| `testing` | Infrastructure de tests et vérification continue. |
| `http` | Contrat REST, DTO, statuts et gestion des erreurs. |
| `security` | Inscription, connexion, JWT et autorisations. |
| `database` | Schéma MySQL, mappings JPA et intégrité des données. |
| `rentals` | Consultation, création et modification des locations. |
| `messages` | Envoi et validation des messages. |
| `images` | Validation, stockage et lecture des images. |
| `docker` | Environnement local et construction des images Docker. |

Un scope supplémentaire doit correspondre à une responsabilité identifiable
et être expliqué dans la PR.

Exemples adaptés au projet :

```text
feat(security): issue a JWT after login
test(database): verify rental transaction rollbacks on MySQL
ci(testing): verify the backend on pushes and pull requests
docs(project): document installation and Swagger access
```

La description de PR explique le problème, le résultat, les éventuels changements de contrat,
les prérequis techniques, les validations réellement exécutées et les limites
pertinentes. Quelques lignes suffisent pour une modification simple.

Lorsqu'un transfert vers une autre branche est envisagé, préciser au besoin dans la PR :

```text
Requires: Describe actual technical prerequisites and verified prerequisite commits.
Cherry-pick checks: Describe target contracts, conflicts and required validation.
Validation: Record commands actually run, their results and relevant limitations.
```

`Requires:` et `Cherry-pick checks:` sont du texte explicatif, pas des trailers
Git standardisés. Ne pas annoncer de validation réussie sans l'avoir exécutée.

## Commits atomiques

Un commit couvre une responsabilité cohérente et laisse le projet compilable.
Inclure ensemble l'implémentation, les tests pertinents et les adaptations de
configuration ou de documentation nécessaires à ce changement.

Séparer les responsabilités indépendantes : nettoyage, typage, migration de
dépendances et correction de navigation ne forment pas un commit unique.
Conserver ensemble un modèle et ses consommateurs, ou un changement de contrat
et les tests qui le vérifient. Réserver les commits de tests séparés au socle
de tests ou à un parcours transversal.

Éviter les reformattages globaux et les suppressions sans justification.
Examiner le diff réel avant de suivre un découpage prévu dans une issue.
Sélectionner les fichiers ou les hunks concernés et contrôler le diff de l'index.

## Traçabilité issue, branche et PR

Les identifiants `I01`, `I02`, etc. du plan local ne sont pas des numéros d'issues
GitHub. Ne pas utiliser leur partie numérique comme numéro d'issue GitHub.

Lorsqu'une issue GitHub existe, utiliser son vrai numéro dans le nom de branche
et la PR. Convention de branche : `<type>/<issue-number>-<short-description>`,
sans issue distante, utiliser `<type>/<short-description>`.

La PR décrit le problème, le comportement obtenu, le périmètre, les validations
et les critères encore ouverts. Lier l'issue réelle, utiliser `Refs #<issue-number>`
pour une contribution partielle. Préférer `Closes #<issue-number>` dans la PR
qui satisfait tous ses critères. Remplacer ces placeholders avant publication,
n'ajouter aucune référence fictive ni promesse de clôture pour un travail partiel.

Indiquer les dépendances entre les contributions. Un ordre de travail ne prouve
pas une dépendance technique, et un hash de prérequis doit correspondre à un
commit réellement vérifié.

## Validation avant commit et publication

Avant chaque commit :

1. Définir une responsabilité cohérente et vérifier ses dépendances. Garder
   ensemble l’implémentation, les tests et les adaptations indispensables.
2. Examiner `git status --short` et `git diff`. Lire aussi le contenu des fichiers
   non suivis retenus pour le commit. Indexer uniquement les fichiers ou hunks
   concernés par cette responsabilité.
3. Exécuter les contrôles adaptés ci-dessous. Indiquer la commande exacte, son
   résultat et les éventuelles limites. Une compilation seule ne valide pas
   les transactions MySQL ni un parcours Angular.
4. Examiner `git diff --cached` et exécuter `git diff --cached --check`. Vérifier
   l’absence de changements étrangers au périmètre, de secrets, de sauvegardes,
   d’images déposées et de fichiers générés tels que `backend/target/`.
5. Vérifier l’auteur et le committer effectifs avant de créer le commit :

   ```bash
   git var GIT_AUTHOR_IDENT
   git var GIT_COMMITTER_IDENT
   ```

   Les commits de ce dépôt utilisent uniquement l’identité
   `Yann-Erwann Le Calvez <flashhy@hotmail.com>`. Ne pas ajouter de co-auteur ni
   remplacer cette identité par celle d’un bot.
6. Rédiger un message en anglais conforme aux conventions ci-dessus. Pour une
   publication, contrôler ensuite la branche cible et les résultats GitHub Actions.

### Backend : Maven et Java 25

Depuis `backend/`, utiliser le Maven Wrapper fourni :

```bash
./mvnw -B test
```

Cette commande lance les tests HTTP sur H2 et les tests de documentation Swagger.
Pour cibler une fonctionnalité pendant le développement :

```bash
./mvnw -B -Dtest=AuthenticationTests test
```

Avant de livrer une modification applicative du backend, vérifier la suite
complète et le JAR :

```bash
./mvnw -B verify
```

`verify` inclut les tests rapides puis les tests JPA `MySqlJpaIT` avec MySQL 8.4
via Testcontainers. Docker doit être démarré et accessible au compte qui exécute
Maven. Le conteneur de test utilise `schema.sql` et des données temporaires.
Les volumes Compose du projet sont conservés. Il n’est pas nécessaire de lancer
`test` séparément si `verify` a déjà été exécuté sur les mêmes changements.

Après une suppression ou un déplacement de classes, éliminer les anciennes
classes compilées :

```bash
./mvnw -B clean verify
```

Le JAR exécutable est généré dans `backend/target/` et reste hors du suivi Git.

### Frontend fourni : npm et Node.js 22

Les commandes sont celles de [frontend/package.json](frontend/package.json) :

| Commande npm | Usage |
| --- | --- |
| `npm ci` | Installation à partir du lockfile, effectuée lors du build Docker. |
| `npm start` | Lancement du serveur Angular. |
| `npm run lint` | Analyse du code avec Angular ESLint. |
| `npm test -- --ci --runInBand` | Tests Jest, sans mode interactif. |
| `npm run build` | Compilation Angular de production. |

Ces commandes s’exécutent dans `/app` dans le conteneur `frontend`, avec sa copie
des dépendances. Le [Dockerfile frontend](docker/Dockerfile.frontend) utilise
`npm ci` et adapte uniquement la copie du lockfile dans l’image au registre
public. Le lancement complet est décrit dans le [README du backend](backend/README.md).

Après le démarrage du projet, lancer les vérifications depuis la racine :

```bash
docker compose --project-directory . --env-file backend/.env -f docker/compose.yaml exec -T frontend npm run lint
docker compose --project-directory . --env-file backend/.env -f docker/compose.yaml exec -T frontend npm test -- --ci --runInBand
docker compose --project-directory . --env-file backend/.env -f docker/compose.yaml exec -T frontend npm run build
```

Les tests utilisent Jest avec `jest-preset-angular`, configuré dans
`frontend/jest.config.ts`. Les commandes de vérification ne donnent pas
d’autorisation de modifier les fichiers du frontend fourni.

### Docker, contrat API et documentation

Pour une modification de l’environnement Docker, vérifier la configuration
depuis la racine, avec `backend/.env` renseigné :

```bash
docker compose --project-directory . --env-file backend/.env -f docker/compose.yaml config --quiet
```

Tester ensuite le service modifié avec les commandes du README du backend.
Pour une route, vérifier le contrat `backend/swagger.yml`, les statuts HTTP,
l’authentification, les autorisations et le parcours Angular concerné, selon
le périmètre modifié. Swagger UI est accessible sur `http://localhost:8081`
après le démarrage du projet.

Pour une modification documentaire seule, relire les commandes et leurs dossiers
d’exécution, vérifier les liens locaux et contrôler le diff. Il n’est pas
nécessaire de relancer les tests applicatifs. Garder cohérents le
[README principal](README.md), le [README du backend](backend/README.md) et ce
guide, en vérifiant les commandes dans `backend/pom.xml`, `frontend/package.json`
et les workflows GitHub Actions.

## Historique et cherry-pick

Aucun amend, rebase, force-push ou autre réécriture automatique de l'historique
n'est prévu. Corriger une contribution déjà commitée par un nouveau commit,
ces conventions ne justifient pas une réorganisation des anciens commits.

Le cherry-pick n'est pas automatique. Avant un transfert demandé, examiner le
commit complet, ses prérequis et leurs éventuels équivalents sur la branche cible.
Vérifier les chevauchements avec les refactorings, les contrats, les providers,
les configurations et les lockfiles. Des commits atomiques peuvent dépendre
les uns des autres et ne sont pas nécessairement transférables isolément.

Après résolution des conflits, inspecter le résultat et exécuter les validations
adaptées sur la cible. Une mesure de performance documentée pour une révision
ne constitue pas une référence pour une autre révision.
