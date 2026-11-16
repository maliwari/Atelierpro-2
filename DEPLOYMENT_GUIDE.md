# Guide de Déploiement Production — AtelierPro (`api-atelierpro.crfdmali.org`)

Architecture cible : **Apache (cPanel / mutualisé ou VPS)** avec séparation stricte entre le dossier public (`public_html/api-atelierpro`) et les secrets hors accès Web (`secure/`).

---

## 1. Arborescence recommandée sur votre serveur Apache

Placez les secrets et le cœur applicatif **en dehors de `public_html`** :

```text
/home/<votre_compte>/
├── secure/                                    # HORS public_html (aucun accès HTTP)
│   ├── .env                                   # Secrets DB, JWT_SECRET, LICENSE_SECRET
│   └── atelierpro-core/                       # Copie de backend-php/{config,database,scripts,src}
│       ├── config/
│       ├── database/
│       ├── scripts/
│       └── src/
└── public_html/
    └── api-atelierpro/                        # DocumentRoot de https://api-atelierpro.crfdmali.org
        ├── .htaccess                          # Copie de backend-php/public/.htaccess
        ├── index.php                          # Copie de backend-php/public/index.php
        ├── uploads/                           # Copie de backend-php/public/uploads/ (avec .htaccess)
        └── admin/                             # Copie complète de backend-php/admin/
            ├── index.php
            ├── api/check_device.php
            ├── config/ (.htaccess + db.php)
            ├── includes/ (.htaccess + auth.php + layout.php)
            └── pages/ (login.php, dashboard.php, ateliers.php, atelier_details.php, generer_code.php, logout.php)
```

> **Important :** Le sous-domaine **`api-atelierpro.crfdmali.org`** doit pointer vers `/home/<votre_compte>/public_html/api-atelierpro` avec un certificat **SSL/HTTPS actif** (Let's Encrypt / AutoSSL cPanel).
> Le code PHP (`public/index.php`, `admin/config/db.php`, `Config.php`) détecte automatiquement `/secure/.env` et `/secure/atelierpro-core/`.

---

## 2. Configuration de la base de données MySQL

1. Créez une base MySQL (ex. `crfdmali_atelierpro`) et un utilisateur dédié avec tous les privilèges sur cette base.
2. Importez `backend-php/database/schema.sql` via phpMyAdmin ou en SSH :
   ```bash
   mysql -u <db_user> -p <db_name> < /home/<votre_compte>/secure/atelierpro-core/database/schema.sql
   ```
   *(Note : si votre hébergeur mutualisé préfixe le nom de la base et interdit `CREATE DATABASE`, supprimez les lignes `CREATE DATABASE` et `USE` en haut de `schema.sql` avant import dans phpMyAdmin).*

---

## 3. Création des secrets hors `public_html` (`/home/<votre_compte>/secure/.env`)

Créez `/home/<votre_compte>/secure/.env` (permissions `chmod 600`) :

```dotenv
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=crfdmali_atelierpro
DB_USER=crfdmali_atelierpro_usr
DB_PASS=MOT_DE_PASSE_MYSQL_FORT

# Générer via : openssl rand -hex 32
JWT_SECRET=REMPLACER_PAR_CLE_ALEATOIRE_64_CARACTERES_HEX
JWT_TTL_SECONDS=2592000

# IMPORTANT : Doit être STRICTEMENT identique à SECRET_KEY dans LicenseManager.kt !
LICENSE_SECRET=REMPLACER_PAR_VOTRE_CLE_HMAC_PRODUCTION

APP_BASE_URL=https://api-atelierpro.crfdmali.org
CORS_ALLOWED_ORIGIN=*
TRUSTED_PROXY=false
```
*(Si votre hébergement utilise Cloudflare ou un reverse proxy devant Apache, passez `TRUSTED_PROXY=true` pour le rate limiting par IP réelle.)*

---

## 4. Permissions & Création du premier compte Administrateur

En SSH sur votre serveur :

```bash
chmod 750 /home/<votre_compte>/secure
chmod 600 /home/<votre_compte>/secure/.env
chmod 755 /home/<votre_compte>/public_html/api-atelierpro/uploads

# Créer le premier administrateur du Backoffice
php /home/<votre_compte>/secure/atelierpro-core/scripts/create_admin.php \
  "Admin AtelierPro" \
  "crfd.mali@gmail.com" \
  "VotreMotDePasseAdminFort!"
```

Vérifiez immédiatement l'accès au backoffice sur :
`https://api-atelierpro.crfdmali.org/admin/`

Et testez le routage API :
```bash
curl -i -X POST https://api-atelierpro.crfdmali.org/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@invalid.ml","password":"bad"}'
```
Vous devez recevoir un JSON `401` (et `X-RateLimit-Limit: 30` dans les en-têtes).

---

## 5. Seuils de Rate Limiting activés (MySQL `rate_limits`)

| Endpoint | Limite | Fenêtre |
| :--- | :--- | :--- |
| `POST /api/auth/login` | **10** essais / `(IP + email)` et **30** / `IP` | 15 min |
| `POST /api/auth/register` | **5** inscriptions / `IP` | 1 heure |
| `POST /admin/pages/login.php` | **5** essais / `(IP + email)` et **15** / `IP` | 15 min |
| `POST /api/check_device` | **60** requêtes / `IP` | 1 min |
| `POST /api/sync/push`, `GET /api/sync/pull`, `POST /api/upload/image` | **120** requêtes / `atelier_id` | 1 min |

---

## 6. Build & Publication de l'application Android (Play Store)

1. **Synchroniser `LICENSE_SECRET`** dans `android/app/src/main/java/com/atelierpro/couture/license/LicenseManager.kt` (`SECRET_KEY`) avec la valeur de `/secure/.env`.
2. Vérifier `SyncPreferences.DEFAULT_BASE_URL` = `"https://api-atelierpro.crfdmali.org"`.
3. **Générer la clé de signature** (une seule fois, à sauvegarder hors Git) :
   ```bash
   keytool -genkey -v -keystore android/release-keystore.jks \
     -keyalg RSA -keysize 2048 -validity 10000 -alias atelierpro-release
   cp android/keystore.properties.example android/keystore.properties
   ```
4. **Exécuter les tests unitaires (y compris `SyncManagerTest`) et générer le `.aab`** :
   ```bash
   cd android
   ./gradlew testDebugUnitTest lintDebug bundleRelease
   ```
5. Téléverser `android/app/build/outputs/bundle/release/app-release.aab` sur Google Play Console avec la politique de confidentialité (`android/PRIVACY_POLICY.md`) publiée en HTTPS.
