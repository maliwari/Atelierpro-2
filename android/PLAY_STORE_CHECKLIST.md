# Checklist Déploiement Production & Google Play Store

## 1. Aligner les secrets avant compilation Release
Avant de générer la version publique, remplacez la clé de développement `LICENSE_SECRET` simultanément dans :
- `/home/<compte>/secure/.env` (`LICENSE_SECRET=...`)
- `android/app/src/main/java/com/atelierpro/couture/license/LicenseManager.kt` (`SECRET_KEY`)
- Vérifiez que `DEFAULT_BASE_URL` dans `SyncPreferences.kt` pointe bien vers `https://api-atelierpro.crfdmali.org`.

## 2. Générer la clé de signature (Keystore)
Exécutez une seule fois dans un dossier sécurisé (à sauvegarder hors du dépôt Git) :
```bash
keytool -genkey -v -keystore android/release-keystore.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias atelierpro-release
```
Puis copiez `android/keystore.properties.example` vers `android/keystore.properties` et renseignez vos mots de passe.

## 3. Exécuter les tests & vérifications avant build
Depuis le dossier `android/` (avec Android Studio / JDK 17+) :
```bash
./gradlew testDebugUnitTest lintDebug
```
Sur émulateur ou appareil connecté (tests Room SQLite instrumentés) :
```bash
./gradlew connectedDebugAndroidTest
```

## 4. Compiler le Android App Bundle (.aab) signé pour Play Store
```bash
./gradlew bundleRelease
```
Fichier généré : `android/app/build/outputs/bundle/release/app-release.aab`
*(APK direct hors Play Store : `./gradlew assembleRelease`.)*

## 5. Déclarations Google Play Console
- **Sécurité des données (Data Safety)** :
  - **Collecte facultative (si Sync Cloud / Licence activée)** : Nom, email et téléphone du propriétaire de l’atelier ; données métier synchronisées (clients, commandes, photos) ; identifiant d’installation (`device_uuid`) pour la gestion du quota multi-appareils.
  - **Chiffrement en transit** : Oui (HTTPS TLS obligatoire sur `https://api-atelierpro.crfdmali.org`).
  - **Données partagées avec des tiers** : **Non**.
- **Politique de confidentialité** : publier `android/PRIVACY_POLICY.md` sur une URL HTTPS publique (ex. `https://api-atelierpro.crfdmali.org/privacy.html` ou GitHub Pages) et renseigner le lien dans Play Console.
- **Accès app / Testeurs** : aucun compte de démonstration requis (l’utilisateur crée son atelier hors-ligne à l’ouverture).
