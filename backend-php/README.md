# AtelierPro — API SaaS Offline-First & Backoffice Admin (PHP 8+ / MySQL)

## 1. Structure
- `database/schema.sql` : Schéma MySQL InnoDB (`utf8mb4_unicode_ci`) multi-atelier + tables `admins`, `appareils`, `licences_codes`, `codes_utilises`.
- `public/index.php` : API REST JSON (`POST /api/auth/register`, `POST /api/auth/login`, `POST /api/sync/push`, `GET /api/sync/pull`, `POST /api/upload/image`, `POST /api/check_device`).
- `admin/` : Backoffice Bootstrap 5 de gestion des licences, appareils et activations WhatsApp :
  - `admin/pages/login.php` & `logout.php` : Authentification par session (`password_verify`) + jetons CSRF.
  - `admin/pages/dashboard.php` : 4 KPIs (Total ateliers, Actifs, Expirés, Revenus du mois FCFA) + relance WhatsApp à J-5.
  - `admin/pages/ateliers.php` & `atelier_details.php` : Recherche/filtres, gestion des appareils (`Autoriser/Bloquer`, `Supprimer`) et modification du quota `max_appareils`.
  - `admin/pages/generer_code.php` : Paiements (`Orange Money`, `Moov Money`, `Espèces`), calcul `FORMULE-HMAC6`, prolongation automatique et lien WhatsApp pré-rempli.
  - `admin/api/check_device.php` : Endpoint JSON appelé par Android à chaque synchronisation (`SyncManager.kt`).
- `scripts/create_admin.php` & `scripts/generate_license_code.php` : Outils CLI.

## 2. Déploiement rapide
1. Importer `database/schema.sql` dans MySQL 8.0+ / MariaDB 10.4+.
2. Copier `.env.example` vers `.env` et renseigner `DB_*`, `JWT_SECRET`, `LICENSE_SECRET`.
3. Créer le premier compte administrateur en CLI :
   ```bash
   php scripts/create_admin.php "Admin AtelierPro" "admin@atelierpro.ml" "VotreMotDePasseFort"
   ```
4. Configurer deux VirtualHosts (ou sous-domaines) :
   - `api.atelierpro.example.com` -> pointe vers `/backend-php/public`
   - `admin.atelierpro.example.com` -> pointe vers `/backend-php/admin` (HTTPS obligatoire en production)
