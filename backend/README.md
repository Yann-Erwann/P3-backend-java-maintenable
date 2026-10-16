# Backend ChaTop

API REST de gestion de locations, développée avec Java 25, Spring Boot 4.1.1
et MySQL 8.4. Le projet comprend un frontend Angular et un environnement Docker
pour lancer l’application complète en développement local.

**Documentation de l’API : [Swagger UI](http://localhost:8081)**, accessible après
le démarrage décrit ci-dessous. Le contrat est disponible dans [swagger.yml](swagger.yml).

Sauf indication contraire, exécuter toutes les commandes depuis la **racine du
projet**, le dossier contenant `backend/`, `frontend/` et `docker/`.

## Prérequis

- Git.
- Docker Engine ou Docker Desktop, démarré, avec Docker Compose v2 ou supérieur.
- OpenSSL pour générer la clé JWT.
- Java 25 uniquement pour lancer le backend hors Docker ; Maven est fourni par
  le wrapper `backend/mvnw`. Docker fournit Java et Node.js pour le lancement complet.

Vérifier Docker et Compose :

```bash
docker --version
docker compose version
```

Si l’accès au socket Docker est refusé, préfixer les commandes `docker` par `sudo`.

## Installation et configuration

Cloner le dépôt, puis créer le fichier de configuration locale :

```bash
git clone https://github.com/Yann-Erwann/P3-backend-java-maintenable.git
cd P3-backend-java-maintenable
cp -n backend/.env.example backend/.env
```

Si le projet est déjà cloné, se placer à sa racine. `cp -n` conserve un fichier
`.env` existant. Ce fichier est ignoré par Git et contient les secrets locaux.
Entourer les mots de passe de quotes simples s’ils contiennent des caractères
spéciaux, afin de pouvoir charger ce fichier aussi dans Bash.

### Configurer la base de données

Éditer `backend/.env` avec les valeurs suivantes :

| Variable | Valeur par défaut / configuration attendue |
| --- | --- |
| `MYSQL_HOST` | `127.0.0.1` pour Java sur l’hôte ; Compose utilise automatiquement `mysql` pour le backend conteneurisé. |
| `MYSQL_PORT` | `3306`, port MySQL exposé sur l’hôte ; choisir un autre port s’il est occupé. |
| `MYSQL_DATABASE` | `chatop`, nom de la base créée au premier démarrage. |
| `MYSQL_USER` | `chatop`, compte applicatif créé au premier démarrage. |
| `MYSQL_PASSWORD` | Remplacer la valeur d’exemple par le mot de passe du compte applicatif. |
| `MYSQL_ROOT_PASSWORD` | Choisir un mot de passe distinct pour l’administrateur MySQL du conteneur. |
| `JWT_SECRET` | Renseigner la clé générée ci-dessous. |
| `JWT_TTL` | `PT1H`, durée de validité des jetons JWT : une heure. |
| `API_MODE` | Mettre `backend` pour utiliser l’API réelle depuis le frontend. |
| `UPLOAD_DIR` | `./uploads` pour Java sur l’hôte ; Compose utilise `/app/uploads`. |

Générer une clé aléatoire de 32 octets encodée en Base64 :

```bash
openssl rand -base64 32
```

Copier le résultat dans la valeur de `JWT_SECRET` et régler le mode de l’API
dans `backend/.env` :

```dotenv
API_MODE=backend
```

Sur une base déjà initialisée, conserver ses identifiants : modifier `.env` ne
change ni les comptes ni les mots de passe enregistrés dans MySQL. Les variables
de création et le script SQL s’appliquent uniquement à un volume vide.

### Création du schéma

Le script [schema.sql](schema.sql) crée les tables `USERS`, `RENTALS` et
`MESSAGES`, leurs clés étrangères et l’index unique des emails, dans la base
sélectionnée par la connexion. Les colonnes `surface` et `price` utilisent
`DECIMAL(12,2)`. Il ne contient ni nom de base imposé ni suppression de table.

Docker Compose crée la base et l’utilisateur définis par `MYSQL_DATABASE` et
`MYSQL_USER`, puis exécute le SQL sur cette base uniquement sur un volume vide.
Hibernate conserve `ddl-auto=none` ; un volume existant n’est pas modifié.

Pour créer la base sans Docker, utiliser un serveur MySQL 8.4 installé et démarré.
Se connecter avec un compte administrateur autorisé à créer une base, puis
exécuter ceci en adaptant son nom :

```sql
CREATE DATABASE chatop CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE chatop;
SOURCE backend/schema.sql;
```

La commande `SOURCE` est à lancer depuis le client MySQL ouvert à la racine du
projet. Le compte applicatif doit disposer des droits sur la base sélectionnée.
Le nom renseigné dans `MYSQL_DATABASE` doit être le même. Pour un serveur MySQL
sur l’hôte, créer si nécessaire le compte applicatif avec les droits de lecture
et d’écriture sur cette base, puis reporter ses identifiants dans `.env`. Les
créations de tables se font avec le compte administrateur ; le backend n’a pas
besoin de droits de création de tables.

Ne pas réexécuter le script sur une base contenant déjà ces tables : les
`CREATE TABLE` échoueraient. Aucune table n’est supprimée ou remplacée par ce SQL.
Pour une base existante issue d’une ancienne version, suivre la procédure de
maintenance ci-dessous plutôt que réimporter le schéma.

## Lancer le projet avec Docker

Une fois `backend/.env` renseigné, vérifier la configuration puis construire et
démarrer les services :

```bash
docker compose --project-directory . --env-file backend/.env -f docker/compose.yaml config --quiet
docker compose --project-directory . --env-file backend/.env -f docker/compose.yaml up -d --build --wait
docker compose --project-directory . --env-file backend/.env -f docker/compose.yaml ps
```

Le premier lancement télécharge les images et les dépendances. MySQL et le
frontend doivent devenir `healthy`. Attendre également la fin du démarrage de
Spring Boot avant d’appeler l’API.

| Service | Adresse locale |
| --- | --- |
| Application Angular | [http://localhost:4200](http://localhost:4200) |
| Backend REST | `http://localhost:8080/api` |
| Swagger UI | [http://localhost:8081](http://localhost:8081) |
| MySQL | `127.0.0.1:3306` ou le port choisi dans `MYSQL_PORT` |
| Mockoon, serveur de démonstration | `http://localhost:3001/api` |

Créer un compte dans l’application avec **Register**, puis se connecter avec
**Login**. Aucun compte de démonstration n’est créé dans MySQL et la liste des
locations est initialement vide. Un mot de passe d’inscription doit contenir
au moins 8 caractères et au plus 72 octets UTF-8.

Pour contrôler les tables depuis le conteneur MySQL :

```bash
docker compose --project-directory . --env-file backend/.env -f docker/compose.yaml exec mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u "$MYSQL_USER" "$MYSQL_DATABASE" -e "SHOW TABLES;"'
```

Le résultat doit contenir `USERS`, `RENTALS` et `MESSAGES`.

### Utiliser Mockoon à la place du backend

Le réglage `API_MODE=mock` utilise les réponses fictives du
[mock fourni](../frontend/ressources/mockoon/rental-oc.json), sans écriture dans MySQL.
Les identifiants de démonstration sont `test@test.com` / `test!31`.

Après chaque changement de `API_MODE` dans `.env`, reconstruire le frontend :

```bash
docker compose --project-directory . --env-file backend/.env -f docker/compose.yaml up -d --build frontend
```

Se déconnecter puis se reconnecter après la bascule : les comptes et les jetons
du mock sont distincts de ceux du backend.

## Swagger : consulter et tester l’API

Ouvrir **[http://localhost:8081](http://localhost:8081)**. Swagger UI est servi
par un conteneur dédié qui charge [swagger.yml](swagger.yml).

1. Sélectionner le serveur **`http://localhost:8080/api`** pour tester le backend réel.
2. Déplier `POST /auth/register`, cliquer sur **Try it out**, renseigner `name`,
   `email` et `password`, puis cliquer sur **Execute**. Pour un compte existant,
   utiliser `POST /auth/login` avec `email` et `password`.
3. Copier la valeur `token` de la réponse, cliquer sur **Authorize** et coller
   uniquement le jeton, sans le préfixe `Bearer`.
4. Tester par exemple `GET /auth/me` ou `GET /rentals`.

L’inscription et la connexion sont publiques ; les autres routes métier
nécessitent un JWT valide. Le contrat décrit les neuf opérations métier :
authentification, profils, consultation et écriture des locations, et envoi de messages.

Pour tester le mock, sélectionner `http://localhost:3001/api`, utiliser ses
identifiants de démonstration puis son jeton fictif `jwt`. Ce jeton est refusé
par le backend réel.

Avec VS Code Remote SSH, transférer les ports **4200**, **8080** et **8081** vers
la machine locale ; ajouter **3001** pour le mock et **3306** (ou `MYSQL_PORT`)
pour un client MySQL local. Le navigateur doit pouvoir joindre l’API sélectionnée.

## Lancer le backend Java sur l’hôte

Installer Java 25 et vérifier `java -version`. Cette variante conserve MySQL et
Swagger UI dans Docker, puis exécute Spring Boot dans un terminal dédié.

Depuis la racine, arrêter le backend Docker s’il utilise déjà le port 8080 et
lancer les services nécessaires :

```bash
docker compose --project-directory . --env-file backend/.env -f docker/compose.yaml stop backend
docker compose --project-directory . --env-file backend/.env -f docker/compose.yaml up -d --wait mysql swagger-ui
```

Dans un terminal **Bash** ouvert à la racine du projet, exporter les variables
avant le lancement :

```bash
set -a
source backend/.env
set +a
cd backend
./mvnw spring-boot:run
```

Spring Boot ne charge pas automatiquement `backend/.env`. Dans un IDE, définir
les mêmes variables dans la configuration de lancement. Le backend est accessible
sur `http://localhost:8080/api` et peut être testé dans Swagger UI.

Pour utiliser un serveur MySQL déjà installé, créer son schéma comme décrit dans
la section « Configurer la base de données », adapter `MYSQL_HOST`, `MYSQL_PORT`
et les identifiants dans `.env`, puis ne lancer que le service Docker `swagger-ui`.

## Images et conservation des données

Les images acceptées sont des PNG ou JPEG valides de **5 Mio maximum**, avec
au plus 4096 pixels par dimension. Le backend les réencode en PNG et les sert
publiquement sous `/images/{filename}` pour l’affichage dans Angular. Les lectures
GET et HEAD depuis les origines locales Swagger et Angular sont autorisées par
CORS ; les origines non configurées et les prévols d’écriture sont refusés.

Les données MySQL sont conservées dans le volume `mysql_data`, et les images
dans `rental_images`, monté sur `/app/uploads`. Pour Java sur l’hôte, le répertoire
`UPLOAD_DIR` doit être accessible en écriture.

Pour arrêter les services depuis la racine sans supprimer leurs volumes :

```bash
docker compose --project-directory . --env-file backend/.env -f docker/compose.yaml down
```

Pour les relancer, reprendre la commande `up -d --build --wait`. Éviter `down -v`
si les données doivent être conservées : cette option supprime les volumes.

### Consulter les photos dans Docker

Depuis la racine, lister les fichiers du volume d’images :

```bash
docker compose --project-directory . --env-file backend/.env -f docker/compose.yaml exec backend ls -lh /app/uploads
```

Pour ouvrir un terminal dans le conteneur :

```bash
docker compose --project-directory . --env-file backend/.env -f docker/compose.yaml exec backend sh
```

Puis, dans ce terminal :

```sh
cd /app/uploads
ls -lah
exit
```

Une photo peut être ouverte dans le navigateur à l’adresse
`http://localhost:8080/images/nom-du-fichier.png`, en reprenant son nom exact.

## Vérifications et dépannage

Depuis `backend/`, lancer les tests HTTP avec leur base H2 isolée :

```bash
./mvnw test
```

Le profil `test` conserve les mappings de l’application et remplace uniquement
les réglages nécessaires aux tests, dont la datasource H2. Les tests HTTP
n’utilisent pas les données MySQL locales. Deux tests supplémentaires valident
les exemples Swagger contre les DTO. Les identifiants JSON fractionnaires sont
refusés avec HTTP 400 avant toute écriture.

Le test de schéma MySQL est désactivé sans configuration explicite. Sa procédure
est décrite ci-dessous ; il contrôle aussi les contraintes obligatoires des
entités JPA. Les avertissements SQL des tests de doublon et de rollback sont
attendus et ne représentent pas un échec de la suite.

| Problème | Vérification |
| --- | --- |
| Erreur d’accès au socket Docker | Préfixer les commandes Docker par `sudo`. |
| Port MySQL occupé | Modifier `MYSQL_PORT` dans `.env`, puis relancer Compose avec `up -d`. |
| `Access denied` MySQL | Vérifier les identifiants de la base existante ; changer `.env` ne réinitialise pas ses comptes. |
| Swagger affiche `Failed to fetch` | Vérifier l’adresse du serveur sélectionné, le démarrage du backend et les ports transférés en SSH. |
| Réponse 401 sur une route protégée | Se connecter au serveur sélectionné et renseigner son jeton dans **Authorize**. |

### Vérifications automatiques avec GitHub Actions

Le workflow [Backend CI](../.github/workflows/backend.yml) exécute `./mvnw -B verify`
dans `backend/` à chaque push sur une branche et pour les pull requests. Il
installe Java 25 avec `actions/setup-java` et met en cache les dépendances Maven.
Une erreur de compilation ou un échec de test fait échouer le job **Maven
verification**. Les tests utilisent H2 ; le test MySQL optionnel conserve sa
configuration explicite décrite ci-dessous.

## Maintenance d’une base existante et tests MySQL

### Aligner une ancienne base sans effacer les données

Le schéma de référence est `schema.sql`. Une base initialisée avec l’ancien SQL
peut encore accepter `NULL` dans des champs obligatoires des tables `USERS` et
`MESSAGES`. La création du schéma n’est pas rejouée sur un volume existant.

Avant toute modification, arrêter les écritures applicatives et sauvegarder la
base. Depuis la racine, avec Bash et le backend arrêté pendant l’intervention :

```bash
sudo docker compose --project-directory . --env-file backend/.env -f docker/compose.yaml stop backend
mkdir -p backend/backups
umask 077
sudo docker compose --project-directory . --env-file backend/.env -f docker/compose.yaml exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump --single-transaction --no-tablespaces --set-gtid-purged=OFF -u "$MYSQL_USER" --databases "$MYSQL_DATABASE"' > backend/backups/before-constraints.sql
sudo docker compose --project-directory . --env-file backend/.env -f docker/compose.yaml exec mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u "$MYSQL_USER" "$MYSQL_DATABASE"'
```

Vérifier que la sauvegarde est non vide avant de continuer. `backend/backups/`
est ignoré par Git ; ses fichiers contiennent les données de la base.
Dans le client MySQL, ces deux compteurs doivent être à zéro :

```sql
SELECT COUNT(*) AS incomplete_users FROM USERS
WHERE email IS NULL OR name IS NULL OR password IS NULL
   OR created_at IS NULL OR updated_at IS NULL;
SELECT COUNT(*) AS incomplete_messages FROM MESSAGES
WHERE rental_id IS NULL OR user_id IS NULL OR message IS NULL OR created_at IS NULL;
```

Si un compteur est non nul, corriger les lignes avec des valeurs justifiées avant
de poursuivre ; ne pas supprimer ni inventer des données pour passer le contrôle.
Sinon, appliquer les mêmes contraintes que pour une nouvelle installation :

```sql
ALTER TABLE `USERS`
  MODIFY `email` VARCHAR(255) NOT NULL,
  MODIFY `name` VARCHAR(255) NOT NULL,
  MODIFY `password` VARCHAR(255) NOT NULL,
  MODIFY `created_at` TIMESTAMP NOT NULL,
  MODIFY `updated_at` TIMESTAMP NOT NULL;

ALTER TABLE `MESSAGES`
  MODIFY `rental_id` INT NOT NULL,
  MODIFY `user_id` INT NOT NULL,
  MODIFY `message` VARCHAR(2000) NOT NULL,
  MODIFY `created_at` TIMESTAMP NOT NULL;

SHOW CREATE TABLE USERS;
SHOW CREATE TABLE RENTALS;
SHOW CREATE TABLE MESSAGES;
```

Ces deux `ALTER TABLE` ne suppriment ni les lignes ni les clés étrangères.
MySQL valide chaque DDL séparément : ne pas compter sur `ROLLBACK` pour les annuler.
Comparer les définitions avec le schéma de référence, puis quitter le client et
relancer le backend avec la commande Compose `start backend`.

### Vérifier le SQL sur MySQL

Le test `MySqlSchemaTests` vérifie le script, les clés et les décimales dans deux
bases aux noms distincts, créées puis supprimées par le test. Il est désactivé
sans `MYSQL_TEST_URL`. Utiliser uniquement un serveur MySQL 8.4 réservé aux tests,
avec un compte autorisé à créer et supprimer ses bases temporaires. Depuis un
terminal Bash ouvert à la racine du dépôt :

```bash
export MYSQL_TEST_URL='jdbc:mysql://127.0.0.1:3307/mysql?sslMode=DISABLED&allowPublicKeyRetrieval=true'
export MYSQL_TEST_USER=root
read -rsp 'Mot de passe MySQL de test : ' MYSQL_TEST_PASSWORD
export MYSQL_TEST_PASSWORD
cd backend
./mvnw -Dtest=MySqlSchemaTests test
unset MYSQL_TEST_URL MYSQL_TEST_USER MYSQL_TEST_PASSWORD
```

Ces variables concernent uniquement ce test JDBC ; les tests HTTP ordinaires
continuent d’utiliser leur H2 isolée. Ne pas utiliser la base locale de travail
pour ces vérifications.
