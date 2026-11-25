# Portfolio

Portfolio personnel sous forme de **livre à feuilleter**. Chaque page se compose librement dans un éditeur
glisser-déposer façon Canva ; le serveur enregistre chaque consultation (IP selon le mode choisi, page, date,
appareil, provenance) et l'administration affiche les statistiques.

Le rendu s'inspire du modèle FlipHTML5 « portfolio d'ingénierie » : livre centré sur une photo d'arrière-plan
floutée, double page sur grand écran, barre du bas (zoom, compteur « 2-3/7 », curseur de progression, vignettes,
table des matières, plein écran), palette orange et noir. Ni le code ni les images du modèle ne sont repris :
les pages de démonstration et leurs illustrations sont originales (générées au premier démarrage).

- `backend/` : API REST Spring Boot 3.5 (Java 21, **Gradle**, **Spring Modulith**, **PostgreSQL 16**, Flyway)
- `frontend/` : deux applications React 18 + TypeScript strict + Vite + Tailwind, comme un journal :
  - `apps/book` : le **livre public**, en lecture seule (servi sur `/`) ;
  - `apps/admin` : la **rédaction** : page d'accueil (vitrine), connexion, éditeur, pages, médias, statistiques,
    réglages (servie sur `/admin/`) ;
  - `packages/shared` : ce qu'elles partagent, dont le moteur de rendu unique `PageRenderer`.

## Structure du livre

Le livre suit toujours le même plan :

| Ordre | Partie | Règle |
|---|---|---|
| 1 | Couverture | page fixe |
| 2 | Sommaire | page fixe ; son bloc « Sommaire » liste les projets avec leur **numéro de première page, calculé automatiquement** |
| 3… | Projets | chaque projet a un titre et autant de **feuilles** que voulu ; projets et feuilles se réordonnent par glisser-déposer |
| fin | Contact | page fixe, toujours la dernière |

Les numéros sont calculés (la couverture est la page 1) : ajouter ou supprimer une feuille, ou déplacer un projet,
met à jour le sommaire sans rien retoucher ; les lecteurs voient ces numéros à la prochaine publication du site.
Dans le livre, chaque ligne du sommaire ouvre le projet. Les pages fixes ne peuvent être ni
supprimées ni dupliquées ; s'il en manque une, elle est recréée en brouillon au démarrage.

Le bloc « Sommaire » est un élément de l'éditeur (palette → Sommaire) : on le place, le redimensionne et choisit
police, taille, couleurs, interligne et pointillés ; seules les lignes sont calculées.

## Médias : images et vidéos

La médiathèque accepte :

- **images** PNG, JPEG, WebP, GIF, 5 Mo maximum : type vérifié sur le contenu, nom remplacé par un UUID,
  métadonnées (EXIF, XMP, commentaires) retirées ;
- **vidéos** MP4 (H.264 conseillé) ou WebM, `MEDIA_MAX_VIDEO_MB` maximum (100 Mo par défaut) : type vérifié sur le
  conteneur (marque `ftyp` pour MP4, `DocType` EBML pour WebM ; les `.mov` QuickTime et `.mkv` sont refusés),
  enregistrées en flux sur le disque. Le navigateur lit les dimensions avant l'envoi.

Dans l'éditeur, une vidéo de la médiathèque se place comme une image (clic ou glisser-déposer) ; elle se lit dans le
livre avec les commandes du navigateur, et la lecture peut sauter n'importe où (réponses partielles HTTP 206). Les
vidéos YouTube et Vimeo restent possibles par leur adresse.

## Fonctionnement « journal »

L'éditeur enregistre automatiquement la **version de travail** du livre (pages, projets, feuilles, ordre) ; les
lecteurs, eux, ne voient que la **dernière édition publiée**. Un **seul bouton, « Publier le site »**, présent dans
l'en-tête de l'administration et dans l'éditeur, met à jour toute la page publique d'un coup : il fige le livre
entier (pages, ordre, sommaire) dans une nouvelle édition (table `book_edition`, les 20 dernières sont gardées).
À côté, un indicateur affiche « Site public à jour » ou « Modifications non publiées ». Dans l'éditeur, le bouton
enregistre d'abord la page ouverte. Un lecteur déjà sur le site reçoit la nouvelle édition sans recharger
(vérification toutes les 30 s et au retour sur l'onglet ; tant que rien n'a changé, le serveur répond 304 grâce à
l'ETag). Une feuille supprimée reste en ligne jusqu'à la publication suivante. Les réglages du site s'appliquent
immédiatement.

## Accueil de l'administration et connexion

**https://portfolio-admin** ouvre une **page d'accueil** visible sans être connecté : nom, titre (« Ingénieur logiciel
& systèmes d'information »), présentation, expériences, compétences en orbite et projets du livre publié (chacun
ouvre sa page sur le site public). Les textes et la photo se modifient dans **Réglages → Page d'accueil (vitrine)** ;
les valeurs de départ sont génériques, à remplacer. Le bouton « Se connecter » mène au formulaire.

Le **formulaire de connexion** (carte translucide sur fond sombre et halo orange) propose :

- adresse e-mail et mot de passe, **« Se souvenir de moi »** (session de 30 jours au lieu de 8 heures) ;
- **« Continuer avec Google »**, affiché seulement si `GOOGLE_CLIENT_ID` et `GOOGLE_CLIENT_SECRET` sont renseignés.
  Seule l'adresse du compte administrateur (`ADMIN_EMAIL`) est acceptée ; Google ne crée jamais de compte ;
- la **double authentification** (application TOTP : Google Authenticator, Microsoft Authenticator, Authy,
  1Password…). Elle s'active dans **Réglages → Sécurité** : QR code, premier code, puis 10 codes de secours à usage
  unique affichés une seule fois. Une fois active, le code est demandé après le mot de passe **et** après Google.

### Configurer Google

1. Google Cloud console → *APIs & Services* → *Credentials* → *Create credentials* → *OAuth client ID*, type
   *Web application* (l'écran de consentement doit exister, en mode *Testing* avec votre adresse comme testeur).
2. *Authorized redirect URI* : la valeur de `GOOGLE_REDIRECT_URI`. Google n'accepte que `localhost` ou un vrai
   domaine public : en local, gardez **`https://localhost/api/auth/google/callback`** ; le navigateur revient
   ensuite tout seul sur `ADMIN_SITE_URL` (https://portfolio-admin). En production :
   `https://votre-domaine-admin/api/auth/google/callback`.
3. Copiez l'identifiant et le secret dans `.env` (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`), puis
   `docker compose up -d`.

Le parcours suit OpenID Connect (code d'autorisation + PKCE, `state`, `nonce`) ; le jeton d'identité est vérifié
(signature, émetteur, audience, expiration, adresse vérifiée). Un cookie lie la connexion au navigateur qui l'a
commencée, et le ticket de retour ne sert qu'une fois.

Téléphone perdu sans code de secours : désactivez la double authentification depuis le serveur avec
`docker compose exec postgres psql -U portfolio -d portfolio -c "UPDATE admin_user SET totp_enabled = false,
totp_secret = NULL"`. Changer `JWT_SECRET` désactive aussi les QR codes déjà scannés (les secrets sont chiffrés
avec une clé qui en dérive) : il faut alors refaire la configuration.

## CV à télécharger

Dans **Réglages → CV**, l'administrateur envoie son CV (PDF uniquement, 10 Mo au maximum), le consulte, le
remplace ou le retire. Le nom du fichier envoyé (nettoyé) est celui que reçoivent les visiteurs. Dès qu'un CV est en
ligne, un bouton **« CV »** apparaît dans la barre du livre public et un bouton **« Télécharger mon CV »** sur la
page d'accueil de l'administration ; sans CV, ces boutons sont masqués. Aucune publication n'est nécessaire.

Chaque téléchargement est **enregistré comme une visite** (table `download`) : date et heure, IP selon le mode IP
des réglages, appareil, robot ou non, provenance, nom du fichier et point de départ (livre, page d'accueil ou lien
direct). Les téléchargements de l'administrateur connecté ne sont pas comptés, et les lignes sont purgées après
13 mois avec les visites. Dans **Statistiques**, une carte « Téléchargements du CV » donne le total de la période
et le tableau « Téléchargements du CV » liste chaque téléchargement.

Routes : `GET /api/public/cv` (le CV existe-t-il ?), `GET /api/public/cv/download?source=book|home` (fichier en
pièce jointe, jamais mis en cache, téléchargement enregistré), `POST`/`DELETE /api/admin/cv`,
`GET /api/admin/cv/file` (aperçu non compté), `GET /api/admin/stats/downloads`.

## Thème du livre en un clic

Le bouton **« Thèmes »** (page « Pages du livre », et barre de l'éditeur, qui enregistre d'abord la page ouverte)
ouvre une galerie de 8 thèmes : Classique, Nuit, Papier ancien, Océan, Forêt, Pastel, Terminal, Minimal. Chaque thème
est montré sur la couverture du livre. Un clic, puis une confirmation, change **toutes les pages** (couverture,
sommaire, fiches des projets, contact) et **le fond autour du livre** :

- fond des pages ; textes de 28 px et plus → couleur et police des titres, les autres → texte courant ; liens et
  textes vifs → couleur d'accent ;
- formes et croquis : une couleur vive devient l'accent, une couleur claire la « surface », une sombre l'encre
  (transparence et opacité conservées) ; boutons → accent ; sommaire → police et couleurs du thème ;
- **inchangés** : mise en page, tailles, images, vidéos, image de fond (seule sa couleur de secours change), gras,
  italique, soulignement.

Les miniatures sont refaites dans le navigateur, puis tout le livre est enregistré **en une seule fois** : si une
page a été modifiée, ajoutée ou supprimée pendant la préparation, rien ne change et il suffit de réessayer.

Avant d'appliquer un thème, le serveur garde une copie du livre tel qu'il était (contenu et miniature de chaque page,
fond du site ; table `theme_snapshot`, les 10 dernières). **« Revenir en arrière »** (sous le titre de « Pages du
livre » et dans la galerie) remet cette copie ; recliquer remonte au thème d'avant. Si des pages ont été retouchées
depuis le thème, leurs titres sont affichés et un second clic est demandé, car ces retouches seront aussi annulées.
Comme toute modification, rien n'est visible des lecteurs avant **« Publier le site »**.

Les règles sont dans `packages/shared/src/themes/bookThemes.ts` (un thème = 7 couleurs et 2 polices) ; l'aperçu de la
galerie et le résultat utilisent donc le même code. Routes : `GET /api/admin/book/theme` (dernier thème annulable),
`POST /api/admin/book/theme` (toutes les pages et la couleur du fond), `POST /api/admin/book/theme/{id}/revert`.

## Avancement

| Étape | Contenu | État |
|---|---|---|
| 1 | Socle : monorepo, PostgreSQL de dev, squelettes, migration `V1__init.sql` | ✅ |
| 2 | Authentification : admin au démarrage, JWT en cookie HttpOnly, CSRF, 5 essais / minute | ✅ |
| 3 | Pages : CRUD, duplication, publication du site, ordre, validation du JSON, verrou optimiste, liste glisser-déposer | ✅ |
| 4 | Médias : type vérifié par Tika, 5 Mo, nom UUID, EXIF retiré, `FileStorage`, cache long | ✅ |
| 5 | Moteur de rendu : types TS, `PageRenderer`, `elementRegistry`, 6 types d'éléments | ✅ |
| 6 | Éditeur à canevas : store, palette, react-moveable, guides, propriétés, plans, annuler/rétablir, raccourcis, sauvegarde auto, conflit | ✅ |
| 7 | Croquis (crayon, gomme), images (dépôt, collage, médias), bandeau de pages, miniatures | ✅ |
| 8 | Portail public : react-pageflip, double / simple page, clavier, tactile, sommaire, plein écran, `/p/:slug` | ✅ |
| 9 | Suivi des visites : IP derrière proxy de confiance, 3 modes, appareils et robots, déduplication, purge 13 mois | ✅ |
| 10 | Tableau de bord : cartes, courbe, barres, répartition, tableau filtrable, export CSV | ✅ |
| 11 | Finitions : en-têtes de sécurité et CSP, Docker Compose de production, tests de bout en bout | ✅ |
| + | Deux applications (lecture / rédaction) et publication par éditions | ✅ |
| + | Page d'accueil de l'administration, connexion Google, double authentification, « Se souvenir de moi » | ✅ |
| + | Polices web embarquées, CV à télécharger, journal des téléchargements | ✅ |
| + | Thème de tout le livre en un clic, avec retour en arrière | ✅ |

## Démarrer en développement

Prérequis : Docker, Node.js 20+. Java 21 n'a pas besoin d'être installé : la *toolchain* Gradle le télécharge.

```bash
cp .env.example .env
```

Remplissez `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `JWT_SECRET` (`openssl rand -base64 48`) et `TRACKING_SALT`
(`openssl rand -hex 32`). En développement, ajoutez `OPENAPI_ENABLED=true` pour la documentation de l'API.

```bash
docker compose -f docker-compose.dev.yml up -d
```

```bash
cd backend && ./gradlew bootRun
```

```bash
cd frontend && npm install && npm run dev
```

`npm run dev` lance les deux applications : le livre sur le port 5173, qui renvoie aussi `/admin` vers
l'administration (port 5174). Tout le site s'ouvre donc à une seule adresse, comme en production :

- Livre public : http://localhost:5173
- Administration : http://localhost:5173/admin (compte `ADMIN_EMAIL` / `ADMIN_PASSWORD`)
- Documentation de l'API (si `OPENAPI_ENABLED=true`) : http://localhost:8080/swagger-ui.html

Au premier démarrage, le livre de démonstration (couverture, sommaire, contact) est créé et publié, avec une photo
d'arrière-plan. Remplacez ces pages dans l'éditeur ; elles ne
reviennent jamais, même si vous supprimez toutes les pages (`DEMO_CONTENT=false` pour ne pas les créer).

## En local sur deux domaines, en HTTPS : `portfolio-public` et `portfolio-admin`

Comme en production, les lecteurs et la rédaction ont chacun leur adresse, en HTTPS via Traefik :

| Adresse | Contenu |
|---|---|
| https://portfolio-public | le livre (aucune page ni API d'administration : 404) |
| https://portfolio-admin | la rédaction (`/` mène à `/admin/`) ; le livre n'y est pas servi |

Les adresses en `http://` redirigent vers `https://`.

1. Déclarez les deux noms sur le poste (une seule fois, mot de passe administrateur demandé) :

   ```bash
   sudo sh -c 'printf "127.0.0.1 portfolio-public\n127.0.0.1 portfolio-admin\n" >> /etc/hosts'
   ```

   Sous Windows, ajoutez les deux mêmes lignes à `C:\Windows\System32\drivers\etc\hosts` (éditeur lancé en
   administrateur).

2. Créez le certificat local (une seule fois). Avec [mkcert](https://github.com/FiloSottile/mkcert), le navigateur
   l'accepte sans avertissement ; lancez d'abord `mkcert -install` si l'autorité locale de mkcert n'est pas encore
   installée sur le poste. Sans mkcert, le script crée un certificat auto-signé (avertissement du navigateur).

   ```bash
   ./traefik/generate-local-certs.sh
   ```

   Le certificat et sa clé privée sont écrits dans `traefik/certs/`, ignoré par git.

3. Dans `.env`, en plus des secrets :

   ```
   HTTP_PORT=80
   HTTPS_PORT=443
   PUBLIC_HOST=portfolio-public
   ADMIN_HOST=portfolio-admin
   PUBLIC_SITE_URL=https://portfolio-public
   APP_ORIGIN=https://portfolio-public,https://portfolio-admin
   COOKIE_SECURE=true
   ```

4. Démarrez la pile :

   ```bash
   docker compose up -d --build
   ```

En développement (`npm run dev`), les deux noms sont aussi acceptés, en HTTP : http://portfolio-public:5173 et
http://portfolio-public:5173/admin.

## Production

```bash
docker compose up -d --build
```

Quatre services :

| Service | Rôle | Publié |
|---|---|---|
| `traefik` | HTTPS (TLS 1.2+), redirection HTTP → HTTPS, HSTS, routage par nom de domaine vers Nginx | `HTTP_PORT`, `HTTPS_PORT` |
| `frontend` | Nginx : le livre sur `PUBLIC_HOST`, l'administration sur `ADMIN_HOST`, `/api` et `/media` vers le backend | non |
| `backend` | API Spring Boot (image multi-étapes Gradle → JRE 21), volume `portfolio-uploads` | non |
| `postgres` | PostgreSQL 16, volume `portfolio-db` | non |

Traefik est configuré par fichiers (`traefik/traefik.yml`, `traefik/dynamic/routes.yml`), **sans accès au socket
Docker**. Certificats :

- **en local** : ceux de `traefik/certs/` (script `generate-local-certs.sh`) ;
- **sur un vrai nom de domaine public** : renseignez `ACME_EMAIL` (et `PUBLIC_HOST`, `ADMIN_HOST`,
  `PUBLIC_SITE_URL`, `APP_ORIGIN` avec vos domaines) : Traefik obtient et renouvelle les certificats Let's Encrypt
  (défi HTTP sur le port 80, stockés dans le volume `portfolio-acme`).

Les deux applications ont chacune leur politique de sécurité : le livre a une CSP stricte (aucun script en ligne,
aucun bloc `<style>` hors les deux de la bibliothèque de livre, autorisés par empreinte) ; l'administration, qui
en a besoin pour l'éditeur, autorise les styles en ligne et n'est pas indexée. Pour réserver l'administration à
certaines adresses (`/admin/`, `/api/admin/`, `/api/auth/`), réglez par exemple
`ADMIN_ALLOW_FROM=203.0.113.7,10.8.0.0/24` : les autres reçoivent 403, le livre reste public.

Traefik transmet l'adresse du visiteur dans `X-Forwarded-For` ; Nginx et le backend ne la lisent que depuis un proxy
de confiance (`TRUSTED_PROXIES`, plages privées par défaut dans Compose). `APP_ORIGIN` doit lister toutes les
adresses publiques du site : derrière Traefik, l'API reçoit du HTTP et ne reconnaît pas seule l'origine `https://`.

## Sauvegardes

Base de données (remplacez les noms si vous avez changé `POSTGRES_USER` / `POSTGRES_DB`) :

```bash
docker compose exec -T postgres pg_dump -U portfolio -d portfolio --format=custom > portfolio-$(date +%F).dump
```

Fichiers envoyés (volume `portfolio-uploads`) :

```bash
docker run --rm -v portfolio_portfolio-uploads:/data -v "$PWD":/backup alpine tar czf /backup/uploads-$(date +%F).tar.gz -C /data .
```

Restauration : `pg_restore --clean -U portfolio -d portfolio` avec le fichier `.dump`, puis extraction de l'archive
dans le volume (`tar xzf … -C /data`).

## Vérifications

```bash
cd backend && ./gradlew check
```

```bash
cd frontend && npm test && npm run lint && npm run format:check && npm run build
```

```bash
cd frontend && npx playwright install chromium && npm run e2e
```

Sur les deux domaines (Chromium les résout même sans le fichier `hosts`) :

```bash
cd frontend && E2E_BASE_URL=https://portfolio-admin E2E_ADMIN_URL=https://portfolio-admin E2E_PUBLIC_URL=https://portfolio-public npm run e2e
```

- `./gradlew check` : 158 tests (dont `ModularityTest` et des tests d'intégration sur un PostgreSQL Testcontainers),
  Checkstyle, et une règle JaCoCo qui exige 80 % de lignes couvertes sur `AuthService`, `JwtService`,
  `PageService`, `PageContentValidator`, `VisitRecorder` et `IpAnonymizer` (rapport :
  `backend/build/reports/jacoco/test/html`). Documentation des modules : `backend/build/spring-modulith-docs`.
- `npm test` : 67 tests Vitest répartis entre `packages/shared`, `apps/book` et `apps/admin` (rendu, livre,
  nouvelle édition reçue sans recharger, store de l'éditeur, opérations, croquis, authentification, double
  authentification, Google, page d'accueil, réglages, CV, statistiques, thèmes).
- `npm run e2e` : 5 parcours Playwright (texte ajouté, déplacé, redimensionné puis rechargé ; croquis ; parcours
  « connexion, création d'une page, publication, visite publique, statistique visible » ; brouillons invisibles ;
  une modification reste privée jusqu'à « Publier le site », puis le lecteur la reçoit sans recharger).
  Ils utilisent le compte de `.env` et visent `E2E_BASE_URL` (http://localhost:5173 par défaut, ou
  http://localhost:8000 pour la pile de production).

## Architecture

Le backend est un **monolithe modulaire (Spring Modulith)**. Chaque sous-paquet direct de `com.portfolio` est un
module ; son API publique est dans son paquet racine, son implémentation dans `internal`.

| Module | Rôle | Dépend de |
|---|---|---|
| `config` | Propriétés, horloge, tâches planifiées | — |
| `common` | Partagé : erreurs `ProblemDetail`, `ClientIpResolver`, format de page | — |
| `auth` | Compte admin, connexion (mot de passe, Google), double authentification, JWT, CSRF, limitation | config, common |
| `media` | Envoi, `FileStorage`, service des fichiers, bibliothèque | config, common |
| `settings` | Réglages du site (ligne unique) | media |
| `page` | Pages, validation du JSON, livre public, contenu de démonstration | media, settings |
| `visit` | Collecte des visites et des téléchargements (`DownloadLog`), IP, appareils, purge | page, settings, auth |
| `stats` | Agrégations, liste paginée, export CSV, journal des téléchargements | page |
| `profile` | Page d'accueil de l'administration : nom, titre, expériences, compétences, photo, projets publiés ; CV | media, page, visit |

Le module `media` ne connaît pas les pages : `page`, `settings` et `profile` implémentent l'interface `MediaUsage`, et un
média encore utilisé ne peut pas être supprimé (409). Les miniatures remplacées sont supprimées après validation
de la transaction (événement `PageMediaReleased`).

Côté client, le dossier `frontend/` est un monorepo npm (workspaces) :

| Paquet | Contenu | Servi sur |
|---|---|---|
| `packages/shared` | `PageRenderer` et registre des éléments, types du JSON, client HTTP (CSRF), textes communs, composants d'interface, lecteur de livre (`BookViewer`) réutilisé par l'aperçu | — |
| `apps/book` | Livre public : `/`, `/p/:slug` | `/` |
| `apps/admin` | Connexion, pages, éditeur, médias (images et vidéos), statistiques, réglages, aperçu ; ses propres textes | `/admin/` |

Une règle ESLint interdit au livre d'importer du code d'administration et au paquet partagé d'importer une
application : le code et les textes de la rédaction ne sont jamais envoyés aux lecteurs.

Un seul composant **`PageRenderer`** transforme le JSON d'une page en éléments positionnés en absolu
dans un cadre de 800 × 1000 unités mis à l'échelle par CSS : l'éditeur, le livre public et les miniatures
l'utilisent tous, donc l'éditeur montre exactement ce que voit le visiteur. Les types d'éléments sont déclarés
dans `elementRegistry.ts`. L'éditeur passe chaque modification par `applyChange` (historique de 100 actions) ; les
coordonnées enregistrées sont toujours logiques (conversion écran → logique dans `geometry.ts`).

### Raccourcis de l'éditeur

| Raccourci | Action |
|---|---|
| Ctrl+S | Enregistrer (et régénérer la miniature) |
| Ctrl+Z / Ctrl+Maj+Z | Annuler / rétablir |
| Ctrl+C / Ctrl+V / Ctrl+D | Copier / coller (y compris entre pages) / dupliquer |
| Ctrl+A | Tout sélectionner |
| Suppr | Supprimer la sélection |
| Flèches (Maj) | Déplacer de 1 (10) unité(s) |
| Ctrl+] / Ctrl+[ | Avancer / reculer d'un plan |
| Échap | Désélectionner, quitter l'outil actif |
| T, R, E, P | Texte, rectangle, ellipse, crayon |
| Maj pendant un redimensionnement / une rotation | Garder les proportions / crans de 15° |
| Ctrl+molette | Zoom |

## Sécurité et vie privée

- Mot de passe BCrypt (coût 12) ; JWT HS256 de 8 heures (30 jours avec « Se souvenir de moi ») dans un cookie
  `HttpOnly`, `Secure`, `SameSite=Strict` ;
  CSRF par `CookieCsrfTokenRepository` (en-tête `X-XSRF-TOKEN`) ; 5 tentatives de connexion par minute et par IP,
  même message pour un e-mail inconnu et un mauvais mot de passe.
- Double authentification TOTP (RFC 6238, 6 chiffres, 30 s, une période de décalage tolérée, un code ne sert
  qu'une fois) ; secret chiffré en AES-GCM ; codes de secours stockés hachés (SHA-256) ; entre les deux étapes, un
  jeton de 5 minutes qui n'ouvre aucune session, 5 essais par étape.
- Google : OpenID Connect avec PKCE, `state` et `nonce` à usage unique (10 minutes), jeton d'identité vérifié,
  adresse vérifiée obligatoire, uniquement le compte administrateur existant.
- JSON des pages validé par le serveur (types connus, propriétés autorisées seulement, nombres bornés, 300 éléments,
  1 Mo, liens `http`/`https`/`mailto`, vidéos YouTube / Vimeo par identifiant) ; textes affichés en texte brut.
- Images : PNG, JPEG, WebP, GIF, 5 Mo, type détecté sur le contenu, nom UUID, métadonnées EXIF/XMP/commentaires
  retirées sans réencodage (une photo orientée par EXIF est redressée).
- CV : PDF reconnu à sa signature `%PDF-`, 10 Mo, stocké sous un nom UUID (jamais servi par `/media/`), nom de
  téléchargement réduit aux lettres, chiffres, espaces et `._()-` ; envoyé en pièce jointe avec `nosniff` et
  `Cache-Control: no-store`.
- En-têtes : CSP stricte (scripts du site seulement, cadres YouTube nocookie et Vimeo seulement),
  `X-Content-Type-Options`, `Referrer-Policy`, `X-Frame-Options`, HSTS. Actuator limité à `/actuator/health`,
  non exposé par Nginx ; aucune trace d'erreur dans les réponses.
- IP masquées par défaut, conservation 13 mois (purge chaque nuit), visites de l'administrateur jamais
  enregistrées, aucun cookie de suivi (le `sessionId` reste dans `sessionStorage`), provenance réduite à
  « schéma + domaine + chemin ». Lien « Mentions et confidentialité » en pied de livre, texte modifiable dans les
  réglages. Aucun bandeau de consentement n'est prévu : vérifiez ce point selon la loi applicable à vos visiteurs.

## Décisions

Écarts au cahier des charges et choix pris là où il ne tranchait pas.

**Accueil, Google et double authentification**
- **Connexion Google sans `spring-boot-starter-oauth2-client`** : le parcours OpenID Connect est écrit à la main
  (`GoogleSignIn`, `HttpGoogleIdentityClient`) avec `spring-security-oauth2-jose`, déjà présent, pour vérifier le
  jeton. Raison : l'API reste sans session (pas de `HttpSession` pour la requête d'autorisation), le bouton
  disparaît proprement quand les variables sont vides, et l'adresse de retour peut être `localhost` (seule adresse
  locale acceptée par Google) alors que l'administration vit sur `portfolio-admin`.
- **TOTP sans bibliothèque** : l'algorithme (RFC 6238) tient en une classe testée sur les vecteurs officiels.
- **`qrcode`** (frontend, administration seulement) : dessine le QR code de configuration en SVG, sans canvas ni
  service externe ; le secret ne quitte jamais le navigateur de l'administrateur.
- **« Mot de passe oublié ? »** : aucun envoi d'e-mail n'existe ; le lien explique d'utiliser Google ou de
  réinitialiser depuis le serveur. **« Inscription »** n'est pas proposée : un seul compte administrateur.
- Migration `V8__two_factor_and_profile.sql` : colonnes TOTP de `admin_user`, table `admin_recovery_code`, table
  `profile` (ligne unique) avec des textes de départ génériques.
- Migration `V9__cv_and_downloads.sql` : table `cv_document` (ligne unique) et table `download` (journal des
  téléchargements, mêmes règles d'IP et de purge que `visit`). Le CV n'est pas un média de la bibliothèque : il
  n'apparaît pas dans les pages et un seul existe à la fois.
- Migration `V10__theme_snapshots.sql` : table `theme_snapshot` (copie du livre avant chaque thème, 10 gardées).
  Les médias qu'elle cite (miniatures remplacées, images) restent « utilisés » tant que la copie existe, pour que
  « Revenir en arrière » retrouve tout ; ils sont libérés quand la copie est utilisée ou abandonnée.

**Choix du propriétaire**
- **Deux applications frontend** (lecture et rédaction) au lieu du « client unique à deux zones » du cahier des
  charges, avec un paquet partagé pour garder un seul moteur de rendu. Gains : CSP stricte pour le livre, code
  d'administration jamais servi aux lecteurs, accès à `/admin/` restreignable dans Nginx.
- **Publication par éditions du livre entier** : un seul bouton « Publier le site » (`POST /api/admin/book/publish`)
  enregistre un instantané JSON du livre dans `book_edition` (migration `V7__book_editions.sql`, qui remplace les
  colonnes de publication par page de `V4`). Le public lit la dernière édition ; l'autosauvegarde ne la touche pas.
- **PostgreSQL 16** partout : développement, production et tests (Testcontainers).
- **Gradle au lieu de Maven** : `./gradlew test` remplace `./mvnw test`, `./gradlew bootRun` remplace
  `./mvnw spring-boot:run`. Wrapper Gradle 8.14.3.
- **Spring Modulith 1.4** (`spring-modulith-starter-core`, `-starter-test`) : architecture en modules vérifiée par
  un test. Les tâches planifiées vivent dans les modules concernés ; `config` porte seulement les réglages transverses.
- **Habillage inspiré du modèle FlipHTML5 et pages de démonstration** : image de fond du site
  (`site_settings.background_media_id`, en plus de `background_color`), barre du bas, vignettes, zoom, couverture
  centrée ; contenu de démonstration créé une seule fois (`site_settings.demo_seeded`, `DEMO_CONTENT`).
- **Aucun commit** fait par l'outil : le propriétaire commite chaque étape.

**Techniques**
- **Spring Boot 3.5.16** : dernière version stable de la branche 3.x exigée.
- **Toolchain Java 21** via `foojay-resolver-convention` : télécharge un JDK 21 si besoin.
- **Port 5433** pour le PostgreSQL de développement (5432 est souvent déjà pris) ; variables `DB_HOST` / `DB_PORT`
  ajoutées pour l'URL JDBC.
- **`spring.config.import` de `../.env`** en développement : `./gradlew bootRun` lit le même `.env` que Compose.
- **JWT avec `spring-security-oauth2-jose`** (Nimbus, géré par Spring Boot) plutôt qu'une bibliothèque tierce ;
  le jeton est lu par un filtre maison sur le cookie : un jeton expiré n'empêche pas de visiter le site public,
  il est simplement effacé.
- **`com.bucket4j:bucket4j_jdk17-core`** : limitation des tentatives de connexion (exigé).
- **`org.apache.tika:tika-core`** : type réel des fichiers (exigé). Les dimensions et la suppression des
  métadonnées sont lues directement dans les en-têtes PNG/JPEG/WebP/GIF, sans bibliothèque d'images.
- **`com.github.ua-parser:uap-java`** : détection des robots ; tablette / mobile / ordinateur par motifs simples.
- **`springdoc-openapi-starter-webmvc-ui`** : documentation OpenAPI, désactivée sauf `OPENAPI_ENABLED=true`.
- **`server.forward-headers-strategy=none` au lieu de `framework`** : le filtre de Spring ferait confiance à
  n'importe quel `X-Forwarded-For`. `ClientIpResolver` ne le lit que depuis `TRUSTED_PROXIES` (adresses ou CIDR) et
  parcourt la chaîne de droite à gauche ; le cookie `Secure` vient de la configuration (`COOKIE_SECURE`).
- **Statistiques en lecture SQL directe** (`JdbcTemplate`) sur la table `visit` depuis le module `stats`
  (côté lecture) ; les titres des pages viennent de `PageApi`.
- **Migrations** : `V2__media_kind.sql` (les miniatures sont des médias de type `THUMBNAIL`, cachés de la
  bibliothèque) et `V3__demo_flag.sql`.
- **Miniatures** : rendues côté client par le même `PageRenderer` (copie hors écran) avec **`html-to-image`**,
  envoyées comme média et jointes à la sauvegarde, au plus toutes les 15 s (et à chaque Ctrl+S ou changement de page).
- **Croquis** : un trait = un élément `drawing` (contour `perfect-freehand` en chemin SVG rempli) ; la gomme
  supprime les croquis touchés.
- **Coller** : Ctrl+C dépose un marqueur dans le presse-papiers système ; Ctrl+V colle les éléments copiés dans
  l'éditeur (même entre pages, via `sessionStorage`) ou envoie l'image collée depuis ailleurs.
- **Réordonner les pages ne change pas leur `version`** (mise à jour en masse) : un éditeur ouvert ne reçoit pas
  de faux conflit 409.
- **Livre** : `react-pageflip` en taille fixe recalculée à chaque redimensionnement ; une page seule (couverture)
  est centrée ; avec `prefers-reduced-motion`, un composant de fondu remplace l'animation. Si le lien demandé est
  déjà visible dans la double page, l'adresse n'est pas réécrite. Clic sur le bord : zones étroites aux bords du
  livre (le clic sur la page elle-même ne tourne pas la page, pour laisser les liens cliquables).
- **Mise à jour du livre ouvert** : TanStack Query revérifie le livre toutes les 30 s et au retour sur l'onglet ;
  `ShallowEtagHeaderFilter` (Spring) répond 304 tant qu'aucune édition n'a changé. Les feuilles du livre lisent leur
  contenu dans un contexte React (`SheetContext`), car `react-pageflip` garde les éléments de son premier rendu.
- **Feuille de style de `page-flip`** livrée comme fichier CSS (la bibliothèque l'injecte aussi en `<style>`,
  autorisé par empreinte dans la CSP du livre ; à mettre à jour si la bibliothèque change).
- **Structure du livre** (migration `V5__book_structure.sql`) : table `project`, colonnes `page.kind`
  (`COVER`, `TABLE_OF_CONTENTS`, `SUMMARY`, `PROJECT_SHEET`, `CONTACT`) et `page.project_id`. À la migration, les
  pages de démonstration `couverture`, `sommaire`, `projets` et `contact` deviennent les pages fixes ; toute autre
  page devient un projet d'une feuille, dans l'ordre existant. L'ordre du livre est calculé (`BookOrder`) ;
  `page.position` est désormais la position d'une feuille dans son projet. API : `GET /api/admin/book`,
  `/api/admin/projects` (création avec une première feuille, renommage, suppression, ordre),
  `/api/admin/projects/{id}/sheets` (ajout, ordre). Le livre public renvoie aussi `toc`.
- **« Synthèse des projets » retirée** (migration `V6__remove_summary_page.sql`) : le livre est Couverture,
  Sommaire, projets, Contact ; les visites de cette page sont conservées sans lien.
- **Vidéos importées** : élément `video` avec `provider: "file"` et `mediaId` (en plus de YouTube / Vimeo). Le
  serveur vérifie qu'un élément vidéo utilise une vidéo et qu'une image utilise une image. Tika (`tika-core`) ne
  distingue que les conteneurs ; `VideoTypes` lit la marque MP4 et le DocType WebM. Les métadonnées des vidéos ne
  sont **pas** retirées (il faudrait réencoder avec ffmpeg) : exportez vos vidéos sans géolocalisation. Limites
  d'envoi : `MEDIA_MAX_VIDEO_MB` côté Spring (fichiers temporaires sur disque au-delà de 512 Ko) et
  `MEDIA_MAX_BODY` côté Nginx (domaine d'administration seulement ; 1 Mo sur le domaine public).
- **Sommaire calculé côté serveur** : nouvel élément `toc` dont seul le style est enregistré ; les lignes viennent
  de `toc` (livre public, aperçu, éditeur) via un contexte React. Ainsi le sommaire reste juste même pour une
  édition publiée plus tôt.
- **Traefik pour le HTTPS** (`traefik:v3.7.19`) : fournisseur de fichiers plutôt que labels Docker, pour ne pas
  donner au proxy l'accès au socket Docker ; certificats locaux mkcert, ou Let's Encrypt avec `ACME_EMAIL`. Nginx
  produit des redirections relatives (`absolute_redirect off`) puisqu'il ne voit pas le HTTPS.
- **`APP_ORIGIN` accepte une liste** d'origines (CORS) : derrière un proxy TLS, chaque domaine public doit y figurer.
- **Deux domaines** (`PUBLIC_HOST`, `ADMIN_HOST`) : un modèle Nginx (`frontend/docker/templates`) rempli au
  démarrage du conteneur. Sur le domaine public, `/admin`, `/api/admin/` et `/api/auth/` répondent 404 ; tout le
  domaine d'administration peut être restreint par `ADMIN_ALLOW_FROM`. Le lien « Voir le site » de
  l'administration vient de `PUBLIC_SITE_URL` (variable de construction `VITE_PUBLIC_SITE_URL`).
- **Limite connue avec deux domaines** : le cookie de session appartient au domaine d'administration ; quand vous
  lisez le livre sur le domaine public, vous êtes compté comme un visiteur. En production, deux sous-domaines du
  même nom (`www.exemple.fr`, `admin.exemple.fr`) permettraient de partager le cookie si besoin.
- **UUID sans HTTPS** : `crypto.randomUUID()` n'existe que sur HTTPS ou `localhost` ; `@shared/util/uuid` se rabat
  sur `crypto.getRandomValues` pour fonctionner aussi sur un domaine local servi en HTTP (développement).
- **Tests de bout en bout** : une seule connexion par exécution (`e2e/global-setup.ts`, session sauvegardée), car la
  connexion est limitée à 5 essais par minute et par IP ; les visiteurs sont des contextes anonymes.
- **`npm run dev`** lance les deux serveurs Vite par un petit script Node (`scripts/dev.mjs`), sans dépendance.
- **Code splitting** dans l'administration (éditeur, graphiques chargés à la demande) ; le livre public pèse
  environ 105 Ko compressés.
- **Police Inter auto-hébergée** avec `@fontsource-variable/inter` (pas d'appel à Google Fonts, compatible CSP).
- **Polices web embarquées pour l'éditeur** : Poppins, Montserrat, Roboto, Open Sans, Playfair Display, Lora,
  Merriweather et Fira Code (paquets `@fontsource`, déclarés dans `packages/shared/src/fonts.css`), en plus des
  8 polices système. Chacune a sa graisse (100 à 900 selon la police) et son italique ; un fichier n'est téléchargé
  que si une page l'utilise. La liste est la même dans `FONT_FAMILIES` (front) et `PageContentValidator.FONTS`.
- **Tailwind CSS 4** via `@tailwindcss/vite` ; couleurs du thème dans `src/index.css`.
- **TypeScript 5.9** : TypeScript 7 n'est pas encore pris en charge par `typescript-eslint`.
- **`react-is`** : dépendance requise par Recharts.
- **Linter** : `typescript-eslint` strict, sauf `no-non-null-assertion` (incompatible en pratique avec
  `noUncheckedIndexedAccess`). **Checkstyle** plutôt que Spotless : il impose l'accolade ouvrante seule sur sa
  ligne sans reformater. **JaCoCo** (intégré à Gradle) pour la règle de couverture.
- **Tests** : `jsdom`, `@testing-library/jest-dom`, `@testing-library/dom` (Vitest) ; `@playwright/test`.
- **Textes de l'interface** dans `src/shared/i18n/fr.ts`, sans bibliothèque (une seule langue). Les messages
  d'erreur de l'API restent en anglais ; le client affiche ses propres textes selon le code HTTP.
- **Proxy de confiance par défaut en production** : plages privées. Une machine du même réseau privé pourrait donc
  forger `X-Forwarded-For` ; restreignez `TRUSTED_PROXIES` si ce réseau n'est pas maîtrisé.
