# Conventions de contribution

Ces conventions s'appliquent aux prochaines contributions. Elles ne demandent
aucune réécriture de l'historique existant et n'imposent aucun hook Git.

## Nommage des classes CSS

Utiliser BEM : `bloc`, `bloc__element` et `bloc--variante` ou
`bloc__element--variante`. Écrire les noms composés en kebab-case et conserver
la classe de base lorsqu'un modificateur est ajouté.

Exemples : `brand__banner`, `chart-card__title`, `chart-card--history`,
`country-picker__select--long`. Chaque composant Angular porte sa classe de
bloc sur son hôte quand ses éléments ou variantes en dépendent. Les classes
utilitaires transversales, comme `visually-hidden`, restent indépendantes.

Renommer ensemble les templates, les styles, les bindings Angular, le shell
HTML initial et les sélecteurs des tests. Cibler les éléments par leur classe
BEM plutôt que par leur balise ou leur position lorsque leur rôle est connu.

## Messages de commit

Écrire le titre et le corps en anglais avec un titre de la forme :

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
| `docs`     | Documentation.                                                    |
| `build`    | Dépendances, migrations et configuration de build ou d'outillage. |
| `style`    | Modification de présentation du code sans effet fonctionnel.      |
| `chore`    | Maintenance qui ne relève pas des types précédents.               |

Le scope désigne une responsabilité, pas le type de changement. Utiliser les
scopes prévus pour le projet : `project`, `git`, `testing`, `migration/angular`,
`olympics`, `http`, `security`, `state`, `routing`, `charts`, `ui`, `a11y`,
`performance`, `assets`, `docker`. Conserver notamment `migration/angular` pour
les migrations Angular. Un scope supplémentaire doit correspondre à une
responsabilité identifiable et être expliqué dans la PR.

Exemples adaptés au projet :

```text
refactor(olympics): remove debug logs and redundant operations
test(olympics): cover statistics and existing page rendering
docs(git): document scopes and atomic commit conventions
```

Le corps explique le problème, le résultat, les éventuels changements de contrat,
les prérequis techniques, les validations réellement exécutées et les limites
pertinentes. Quelques lignes suffisent pour une modification simple.

Lorsqu'un transfert vers une autre branche est envisagé, préciser au besoin :

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

Contrôler `git status`, le diff de travail, puis les fichiers sélectionnés avec
`git diff --cached` et `git diff --cached --check`. Vérifier que le commit ne
contient pas de changement étranger à son périmètre.

Pour une modification applicative, exécuter les contrôles adaptés :

```bash
pnpm run lint
pnpm exec ng test --watch=false
pnpm run build
```

Utiliser des tests ciblés lorsque cela suffit à vérifier le changement. Les tests
unitaires Vitest s’exécutent dans Node.js avec jsdom ; Chrome ou Chromium reste
nécessaire uniquement aux tests E2E. Pour une
modification documentaire seule, relire les exemples, vérifier les liens locaux
et contrôler le diff, il n'est pas nécessaire de relancer les tests applicatifs.
Consigner les échecs ou les contrôles indisponibles avec leur cause.

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
