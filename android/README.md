# AtelierPro — Application Android Native (Kotlin / Jetpack Compose)

Projet Android natif conforme au cahier des charges **AtelierPro / CoutureManager** :

- **Plateforme** : Android 8.0+ (API 26 minimum, `compileSdk 35`) — Smartphones et Tablettes.
- **Interface** : **Jetpack Compose + Material 3** avec adaptation automatique `WindowWidthSizeClass` (barre inférieure sur smartphone, `NavigationRail` + affichage maître-détail sur tablette en paysage).
- **Phase 1 — Onboarding & Sécurité** :
  - Premier démarrage guidé (`OnboardingScreen`) : nom de l'atelier, devise (`FCFA`, `€`, `MAD`), code PIN personnel haché (SHA-256 + sel) + option biométrique (`BiometricPrompt`), et chargement optionnel de données d'exemple.
  - Durcissement release : `isMinifyEnabled = true`, `isShrinkResources = true`, `proguard-rules.pro` (protection Room/Gson), `android:allowBackup="false"`, icônes adaptatives et export des schémas Room (`app/schemas`).
- **Phase 2 — Tests, Signature Release & Play Store** :
  - Tests unitaires JUnit (`AtelierCalculationsTest`, `AtelierTypeConvertersTest`, `PinSecurityHelperTest`, `CsvExporterTest`, `BackupSecurityTest`) et tests instrumentés Room (`AtelierDatabaseTest`).
  - Signature Release AAB via `keystore.properties` (`keystore.properties.example`), politique de confidentialité (`PRIVACY_POLICY.md`), checklist Play Store (`PLAY_STORE_CHECKLIST.md`) et CI GitHub Actions (`.github/workflows/android-ci.yml`).
- **Phase 3 — Fiabilisation Opérationnelle & Confort Terrain** :
  - Verrouillage automatique du module Finances/RH lors du passage en arrière-plan (`Lifecycle.Event.ON_STOP`).
  - Sélecteur calendaire natif Material 3 (`DatePickerDialog`) pour les dates d'essayage et de livraison (zéro saisie invalide).
  - Alerte pré-coupe de stock mercerie insuffisant, filtre « Urgentes (<48h / retards) », et bouton « Solder » en 1 clic.
  - Restauration `.zip` atomique avec validation de l'en-tête SQLite et protection Zip Slip (`BackupManager`), assainissement des noms de fichiers PDF, et export comptable CSV UTF-8 BOM pour Excel (`CsvExporter`).
- **Synchronisation Cloud Offline-First (Optionnelle — PHP/MySQL)** :
  - `SyncManager` (`com.atelierpro.couture.sync`) + Room v2 (`uuidLocal`, `updatedAt`, `isSynced`, `deletedAt`, `MIGRATION_1_2`) + `EncryptedSharedPreferences` + `ConnectivityManager` + `WorkManager` retry exponentiel.
  - Upload prioritaire des photos (`multipart/form-data`), Push transactionnel, confirmation/purge locale, Pull incrémental, et widget `SyncStatusIndicator` dans la barre supérieure.

## Compiler et tester

1. Ouvrez le dossier `/android` dans **Android Studio** (Ladybug ou plus récent).
2. Exécutez les tests unitaires : `./gradlew testDebugUnitTest lintDebug`.
3. Pour générer un bundle Play Store (`.aab`) signé : configurez `android/keystore.properties` (voir `PLAY_STORE_CHECKLIST.md`) puis lancez `./gradlew bundleRelease`.
