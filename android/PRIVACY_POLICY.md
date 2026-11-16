# Politique de Confidentialité — AtelierPro / CoutureManager

*Dernière mise à jour : Novembre 2026*

**AtelierPro** est une application mobile de gestion d’atelier de couture conçue en mode **Offline-First**, avec une option facultative de synchronisation Cloud multi-appareils via `https://api-atelierpro.crfdmali.org`.

## 1. Données enregistrées localement sur l’appareil
Toutes les informations saisies fonctionnent d’abord dans la mémoire interne privée de votre appareil (base SQLite Room et dossiers privés `filesDir/photos/` et `filesDir/pdfs/`) :
- Coordonnées des clients (nom, téléphone, adresse, mensurations).
- Commandes, photos de tissus, modèles de couture et stocks de mercerie.
- Données internes d’atelier (dépenses, justificatifs, fiches employés et rémunérations).

## 2. Synchronisation Cloud et gestion des licences (optionnelle / compte atelier)
Si vous activez la synchronisation Cloud ou la vérification de licence :
- **Données transmises de manière chiffrée (HTTPS)** : informations de compte atelier (nom d’atelier, email, téléphone), données métier de l’atelier (clients, commandes, modèles, mercerie, employés, dépenses et photos associées), ainsi que l’identifiant technique d’installation (`device_uuid` et nom de l’appareil) pour contrôler le quota d’appareils autorisés et prévenir la fraude.
- **Cloisonnement strict** : chaque atelier dispose d’un espace isolé (`atelier_id`) inaccessible aux autres ateliers.
- **Aucun partage commercial** : l’éditeur ne vend ni ne cède vos données à des tiers publicitaires.

## 3. Partage volontaire et permissions Android
- **Partage volontaire (WhatsApp / PDF / ZIP)** : le partage de reçus PDF, fiches de coupe ou demandes d’activation WhatsApp s’effectue uniquement à votre initiative.
- **Appareil photo (`CAMERA`)** : prise de photo de tissu, modèle, logo ou justificatif de dépense.
- **Biométrie (`BiometricPrompt`)** : déverrouillage local des finances & RH via Android (aucune donnée biométrique lue ou transmise).

## 4. Suppression et portabilité des données
Vous pouvez exporter vos données au format `.zip` / `.csv` ou réinitialiser l’atelier depuis **Finances ➔ Réglages**. La suppression d’un compte Cloud et des fichiers associés peut être demandée via l’administrateur (`crfd.mali@gmail.com`). La désinstallation supprime les données locales (`android:allowBackup="false"`).
