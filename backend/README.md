# Backend ChaTop

À cette étape, Docker prépare l’environnement ; les fonctionnalités backend décrites ci-dessous sont livrées progressivement par les commits suivants.

Le [README à la racine](../README.md) décrit le démarrage complet avec Docker,
MySQL, Mockoon et Swagger UI, les tests de l'API, les transferts de ports Remote
SSH, le redémarrage automatique et le dépannage.

Cette page décrit l’authentification, la consultation, la création et la modification
des locations, l’envoi de messages et les outils de test de l’API. Les réponses Mockoon
ne prouvent ni une implémentation Spring Boot ni une persistance dans MySQL.

## Authentification implémentée

| Route | Résultat |
| --- | --- |
| `POST /api/auth/register` | HTTP 200 et `{ "token": "..." }` ; utilisateur enregistré dans `USERS` |
| `POST /api/auth/login` | HTTP 200 et JWT après vérification du mot de passe |
| `GET /api/auth/me` | HTTP 200 et profil du propriétaire du JWT, sans mot de passe |

Les deux POST sont publics ; les autres routes métier exigent un JWT Bearer.
Les origines locales Swagger (`http://localhost:8081`, `http://127.0.0.1:8081`)
et Angular (`http://localhost:4200`, `http://127.0.0.1:4200`, y compris via le
proxy) sont autorisées par CORS ; les autres origines sont refusées.
Les chemins de documentation Swagger restent autorisés ; l’interface Swagger
est actuellement servie par le conteneur dédié sur `http://localhost:8081`.

L’email est normalisé (espaces extérieurs retirés, minuscules), le nom doit être
non vide et le mot de passe d’inscription doit contenir au moins 8 caractères,
avec une limite de 72 octets UTF-8 pour BCrypt. Il est haché avec BCrypt avant
son enregistrement. Les champs invalides renvoient 400, un email déjà utilisé
409, et de mauvais identifiants 401 avec un message générique.

Les JWT sont signés en HS256 avec `JWT_SECRET` (clé aléatoire en Base64 d’au
moins 32 octets). Leur durée est configurée par `JWT_TTL` (`PT1H` par défaut).
Le backend vérifie la signature, l’émetteur et l’expiration et refuse le jeton
fictif `jwt`. Aucune clé réelle n’est incluse dans le dépôt ou l’image Docker.

Configurer `.env` à partir de `.env.example`, notamment `JWT_SECRET` et
`API_MODE=backend`, puis suivre les commandes du [README racine](../README.md).
La bascule nécessite une reconstruction du frontend avec `--build`. Les sources
Angular et le mock restent inchangés. Toutes les opérations métier du contrat sont maintenant implémentées.

### Vérification

Depuis `backend/` :

```bash
./mvnw test
```

Les tests HTTP utilisent un serveur réel sur un port aléatoire, une base H2
isolée et une clé JWT générée pour les tests. Ils vérifient inscription,
connexion, profil, hachage, doublons dont inscriptions concurrentes, entrées
invalides et rejet des jetons absents, fictifs, altérés, expirés ou d’un autre émetteur.
Ils n’utilisent ni les identifiants ni les données de la base MySQL locale.

Les essais avec Angular doivent porter sur le backend réel : inscription,
affichage du compte, déconnexion, reconnexion, erreur de mot de passe et doublon.
Vérifier aussi dans MySQL la présence de l’utilisateur et d’un hash BCrypt.
Un succès du mock ne constitue pas une preuve de ces parcours.

## Consultation des locations et du propriétaire

| Route protégée par JWT | Résultat |
| --- | --- |
| `GET /api/rentals` | HTTP 200 et `{ "rentals": [...] }`, trié par identifiant croissant |
| `GET /api/rentals/{id}` | HTTP 200 et détail de la location demandée |
| `GET /api/user/{id}` | HTTP 200 et profil du propriétaire (id, nom, email, dates), sans mot de passe |

Ces routes lisent les tables `RENTALS` et `USERS` de MySQL. Une table `RENTALS`
vide renvoie `{ "rentals": [] }` ; aucune location fictive n’est injectée.
Un identifiant introuvable renvoie 404 avec un message JSON ; un identifiant
non entier, non positif ou hors plage renvoie 400. Sans JWT valide, l’accès est
refusé avec 401. Tout utilisateur authentifié peut consulter les locations et
le profil de leur propriétaire, indépendamment de l’auteur de la location.

Pour préserver le contrat consommé par Angular, `picture` est une chaîne dans
la liste et un tableau contenant l’URL dans le détail (tableau vide si absente).
Les dates du backend utilisent ISO 8601, contrairement aux exemples du mock.
Les champs facultatifs des lignes existantes peuvent être nuls.

Les colonnes `surface` et `price` sont désormais des `DECIMAL(12,2)` : les dix
chiffres entiers du schéma initial sont conservés et deux décimales sont disponibles.

Les tests HTTP vérifient aussi les réponses vides, le contenu et l’ordre de la
liste, le détail, les formats de `picture`, l’absence de mot de passe dans les
profils, ainsi que les erreurs 400, 401 et 404. Les essais Angular portent sur
l’affichage de la liste, le passage au détail et l’affichage du nom du propriétaire.

## Création, modification et messages

| Route protégée par JWT | Résultat |
| --- | --- |
| `POST /api/rentals` | Multipart avec image ; HTTP 200, `{ "message": "Rental created !" }` |
| `PUT /api/rentals/{id}` | Multipart sans nouvelle image ; HTTP 200, `{ "message": "Rental updated !" }` |
| `POST /api/messages` | JSON ; HTTP 200, `{ "message": "Message send with success" }` |

Les locations acceptent `name` (1 à 255 caractères), `description` (1 à 2000),
`surface` strictement positive et `price` positif ou nul. Les valeurs numériques
acceptent dix chiffres entiers et deux décimales au maximum. La création exige
`picture`. Le propriétaire vient du JWT ; un champ `owner_id` client ne le remplace pas.
La modification est réservée au propriétaire et conserve l’image ainsi que
l’auteur et la date de création. Les valeurs incorrectes renvoient 400, un autre
propriétaire 403, et une location absente 404.

Les messages exigent `rental_id`, `user_id` et un texte non vide de 2000 caractères
maximum. L’auteur enregistré provient du JWT : une usurpation de `user_id` est
refusée avec 403. Une location absente renvoie 404. Le succès signifie que le
message est enregistré dans `MESSAGES` ; aucun email n’est envoyé.

### Images

Les images reçues doivent être des PNG ou JPEG valides, de 5 Mio maximum et de
4096 pixels maximum sur chaque dimension. La requête multipart est limitée à
6 Mio ; un dépassement renvoie 413. Les formats invalides ou incohérents avec
leur type MIME sont refusés avec 400. Le serveur décode puis réencode l’image
en PNG, avec un nom UUID indépendant du nom de fichier transmis par le client.

Les ressources `/images/{filename}` sont publiques, hors API métier, pour que
les balises `img` Angular puissent les afficher sans en-tête Authorization.
Elles exposent uniquement les PNG générés, avec `Content-Type: image/png` et
`nosniff`. Les noms invalides, fichiers absents et liens symboliques sont refusés.
Le proxy Docker transmet `/images/**` au backend sans changer le frontend fourni.

Dans Docker, le volume nommé `rental_images` est monté sur `/app/uploads` et
conserve les images après recréation du backend. Hors Docker, `UPLOAD_DIR`
peut définir le répertoire ; sa valeur par défaut est `./uploads` depuis le
répertoire de lancement, ignoré par Git lorsqu’il se trouve dans `backend/`.
Le répertoire doit être accessible en écriture à l’utilisateur du processus.

Si la transaction SQL échoue après le stockage d’une image, celle-ci est
supprimée au rollback. Une panne du processus entre l’écriture du fichier et
la fin de la transaction peut toutefois laisser un fichier orphelin ; le disque
et MySQL ne constituent pas une transaction unique.

### Consulter les photos dans Docker

Exécuter ces commandes depuis la racine du projet, avec le backend démarré.

Lister les fichiers et leur taille :

```bash
sudo docker compose --project-directory . --env-file backend/.env -f docker/compose.yaml exec backend ls -lh /app/uploads
```

Ouvrir un terminal dans le conteneur :

```bash
sudo docker compose --project-directory . --env-file backend/.env -f docker/compose.yaml exec backend sh
```

Puis, dans ce terminal :

```sh
cd /app/uploads
ls -lah
exit
```

Pour afficher une photo dans le navigateur, remplacer `nom-du-fichier.png` par
un nom relevé dans la liste :

```text
http://localhost:8080/images/nom-du-fichier.png
```

### Création du schéma

Le script [schema.sql](schema.sql) est un export `mysqldump --no-data` de la
structure locale, sans données. Il crée les tables `USERS`, `RENTALS` et
`MESSAGES`, leurs clés étrangères et l’index unique des emails. Les colonnes
`surface` et `price` sont directement définies en `DECIMAL(12,2)`.
Le SQL reproduit la structure des tables métier de la base locale : dates en
`TIMESTAMP`, colonnes nullables selon le schéma existant et présence de
`MESSAGES.updated_at`. Le moteur est InnoDB, avec le jeu de caractères
`utf8mb4` et la collation `utf8mb4_0900_ai_ci`.
Les validations des DTO imposent les champs obligatoires des requêtes API.
Les dates sont renseignées par le backend lors de l’enregistrement.
La colonne historique `MESSAGES.updated_at` n’est pas utilisée par le backend.

Flyway et son ancienne table technique `flyway_schema_history` ont été retirés.
La base locale contient uniquement les trois tables métier définies par ce script.

Docker Compose monte ce fichier dans le répertoire d’initialisation MySQL.
Il est exécuté uniquement lors de la création d’une base sur un volume vide.
Pour une installation MySQL hors Docker, exécuter ce SQL dans la base configurée
avant de lancer le backend. Hibernate conserve `ddl-auto=none` ; aucun outil de
migration n’est utilisé.

Docker n’exécute pas ce script sur un volume déjà initialisé. L’export contient
des instructions `DROP TABLE IF EXISTS` : ne pas le réimporter dans une base
contenant des données à conserver, car il supprimerait les tables concernées.
La base locale
possède déjà les colonnes décimales attendues. Toute autre base existante doit
être adaptée explicitement avant utilisation.

Les tests HTTP utilisent un schéma H2 isolé généré à partir des entités ; ils ne
valident pas à eux seuls le script de création MySQL.
Le script a également été exécuté dans une base MySQL temporaire : les trois
tables et les types décimaux ont été vérifiés, puis cette base a été supprimée.

## Mockoon avec Docker

Le mock utilise le fichier [rental-oc.json](../frontend/ressources/mockoon/rental-oc.json).

Pour démarrer le même serveur avec Docker, depuis la racine du dépôt :

```bash
sudo docker compose --project-directory . --env-file backend/.env -f docker/compose.yaml up -d mockoon
sudo docker compose --project-directory . --env-file backend/.env -f docker/compose.yaml logs mockoon
```

L'API est accessible sur `http://localhost:3001/api`. Pour arrêter uniquement le
mock : `sudo docker compose --project-directory . --env-file backend/.env -f docker/compose.yaml stop mockoon`.

## Swagger / OpenAPI

Le fichier [`swagger.yml`](swagger.yml), dans ce dossier, décrit les
9 opérations métier du contrat et la ressource statique des images. Le serveur
backend et le mock restent sélectionnables séparément dans Swagger UI.

Pour tester le serveur existant :

1. Depuis la racine du dépôt, lancer `sudo docker compose --project-directory . --env-file backend/.env -f docker/compose.yaml up -d mockoon swagger-ui`.
   Il est aussi possible de démarrer le JSON dans l'application Mockoon sur le
   port `3001`, avec le préfixe `/api`, à la place du service Docker `mockoon`.
2. Ouvrir [Swagger UI](http://localhost:8081), qui charge automatiquement `swagger.yml`,
   puis utiliser **Try it out** sur une route.
   Sélectionner `http://localhost:3001/api` pour les exemples Mockoon ci-dessous.
   Pour le backend, sélectionner `http://localhost:8080/api`, créer son compte
   puis utiliser le JWT renvoyé dans **Authorize**.
3. Appeler `POST /auth/login` avec `email: test@test.com` et `password: test!31`.
4. Cliquer sur **Authorize**, saisir `jwt` (sans le préfixe `Bearer`), puis tester
   par exemple `GET /auth/me`, `GET /rentals` et `GET /rentals/1`.

Exemple de contrôle direct dans un terminal :

```bash
curl http://localhost:3001/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"test@test.com","password":"test!31"}'
curl http://localhost:3001/api/rentals -H 'Authorization: Bearer jwt'
```

Le navigateur qui affiche Swagger doit pouvoir joindre le serveur local ; avec
Remote SSH, transférer les ports `8081` et `3001` vers la machine locale. Une page Swagger
servie en HTTP local évite les blocages possibles d'accès HTTP depuis une page
HTTPS.

Il est aussi possible de créer un environnement Mockoon depuis ce fichier via
**New local environment from OpenAPI/Swagger**. L'import reproduit les routes et
les exemples, mais pas les règles conditionnelles du JSON : utiliser le fichier
[`rental-oc.json`](../frontend/ressources/mockoon/rental-oc.json) pour tester les scénarios d'authentification et d'erreur existants.
Voir les [limites de compatibilité OpenAPI de Mockoon](https://mockoon.com/docs/latest/openapi/openapi-specification-compatibility/).

Deux particularités du mock sont conservées : `picture` est une chaîne dans la
liste des locations et un tableau dans le détail ; la règle d'erreur du login
teste `login` au lieu de `email`, ce qui peut produire un 404 pour certains échecs.
