-- AtelierPro SaaS Multi-Atelier Sync Database (MySQL 8.0+ / MariaDB 10.4+)
-- Moteur : InnoDB | Encodage : utf8mb4_unicode_ci

CREATE DATABASE IF NOT EXISTS `atelierpro_saas`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `atelierpro_saas`;

-- 1. Comptes Propriétaires / Ateliers (Isolation Multi-Tenant & Licences)
CREATE TABLE IF NOT EXISTS `ateliers` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `code_atelier` VARCHAR(20) NULL DEFAULT NULL,
  `nom_atelier` VARCHAR(150) NOT NULL,
  `telephone` VARCHAR(30) NOT NULL,
  `email` VARCHAR(180) NOT NULL,
  `password_hash` VARCHAR(255) NOT NULL,
  `plan_abonnement` ENUM('free', 'pro', 'atelier_plus') NOT NULL DEFAULT 'free',
  `max_appareils` INT UNSIGNED NOT NULL DEFAULT 2,
  `date_expiration` DATETIME NULL DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ateliers_email` (`email`),
  UNIQUE KEY `uq_ateliers_code` (`code_atelier`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 1a. Administrateurs du Backoffice
CREATE TABLE IF NOT EXISTS `admins` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `nom` VARCHAR(120) NOT NULL,
  `email` VARCHAR(180) NOT NULL,
  `password_hash` VARCHAR(255) NOT NULL,
  `actif` TINYINT(1) NOT NULL DEFAULT 1,
  `last_login_at` DATETIME NULL DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_admins_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 1b. Appareils liés par atelier (contrôle quota max_appareils & blocage)
CREATE TABLE IF NOT EXISTS `appareils` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `atelier_id` BIGINT UNSIGNED NOT NULL,
  `device_uuid` VARCHAR(100) NOT NULL,
  `device_name` VARCHAR(150) NOT NULL DEFAULT 'Appareil Android',
  `platform` VARCHAR(40) NOT NULL DEFAULT 'android',
  `statut` ENUM('autorise', 'bloque') NOT NULL DEFAULT 'autorise',
  `last_seen_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_appareils_atelier_uuid` (`atelier_id`, `device_uuid`),
  KEY `idx_appareils_statut` (`atelier_id`, `statut`),
  CONSTRAINT `fk_appareils_atelier` FOREIGN KEY (`atelier_id`) REFERENCES `ateliers` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 1c. Codes de licence générés & journal des paiements WhatsApp (Orange Money / Moov Money / Espèces)
CREATE TABLE IF NOT EXISTS `licences_codes` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `atelier_id` BIGINT UNSIGNED NOT NULL,
  `code_atelier` VARCHAR(20) NOT NULL,
  `code_genere` VARCHAR(32) NOT NULL,
  `formule` ENUM('M1', 'M3', 'A1') NOT NULL,
  `jours_ajoutes` INT UNSIGNED NOT NULL,
  `montant_fcfa` INT UNSIGNED NOT NULL,
  `mode_paiement` ENUM('Orange Money', 'Moov Money', 'Espèces') NOT NULL,
  `reference_transaction` VARCHAR(120) NOT NULL DEFAULT '',
  `mois_annee` CHAR(7) NOT NULL,
  `created_by_admin_id` BIGINT UNSIGNED NULL DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_licences_atelier` (`atelier_id`, `created_at`),
  KEY `idx_licences_mois` (`created_at`),
  CONSTRAINT `fk_licences_atelier` FOREIGN KEY (`atelier_id`) REFERENCES `ateliers` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_licences_admin` FOREIGN KEY (`created_by_admin_id`) REFERENCES `admins` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 1d. Codes d'activation WhatsApp utilisés sur mobile (synchronisation anti-rejeu)
CREATE TABLE IF NOT EXISTS `codes_utilises` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `atelier_id` BIGINT UNSIGNED NOT NULL,
  `workshop_code` VARCHAR(20) NOT NULL,
  `code` VARCHAR(32) NOT NULL,
  `duration_code` VARCHAR(8) NOT NULL,
  `days_added` INT UNSIGNED NOT NULL,
  `used_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_codes_atelier_code` (`atelier_id`, `code`),
  CONSTRAINT `fk_codes_atelier` FOREIGN KEY (`atelier_id`) REFERENCES `ateliers` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Clients & Mensurations mémorisées
CREATE TABLE IF NOT EXISTS `clients` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `uuid_local` VARCHAR(36) NOT NULL,
  `atelier_id` BIGINT UNSIGNED NOT NULL,
  `nom` VARCHAR(150) NOT NULL,
  `telephone` VARCHAR(35) NOT NULL,
  `adresse` VARCHAR(255) NOT NULL DEFAULT '',
  `notes` TEXT NULL,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted_at` DATETIME NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_clients_atelier_uuid` (`atelier_id`, `uuid_local`),
  KEY `idx_clients_sync` (`atelier_id`, `updated_at`),
  CONSTRAINT `fk_clients_atelier` FOREIGN KEY (`atelier_id`) REFERENCES `ateliers` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Modèles & Gabarits de mesures
CREATE TABLE IF NOT EXISTS `modeles` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `uuid_local` VARCHAR(36) NOT NULL,
  `atelier_id` BIGINT UNSIGNED NOT NULL,
  `nom` VARCHAR(160) NOT NULL,
  `categorie` VARCHAR(60) NOT NULL DEFAULT 'Femme',
  `photo_url` VARCHAR(500) NOT NULL DEFAULT '',
  `prix_base` DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  `jours_confection` INT UNSIGNED NOT NULL DEFAULT 5,
  `mesures_requises` JSON NOT NULL,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted_at` DATETIME NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_modeles_atelier_uuid` (`atelier_id`, `uuid_local`),
  KEY `idx_modeles_sync` (`atelier_id`, `updated_at`),
  CONSTRAINT `fk_modeles_atelier` FOREIGN KEY (`atelier_id`) REFERENCES `ateliers` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Mercerie & Stocks
CREATE TABLE IF NOT EXISTS `mercerie` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `uuid_local` VARCHAR(36) NOT NULL,
  `atelier_id` BIGINT UNSIGNED NOT NULL,
  `nom` VARCHAR(160) NOT NULL,
  `unite` VARCHAR(40) NOT NULL DEFAULT 'pièce',
  `prix_unitaire` DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  `stock_actuel` INT NOT NULL DEFAULT 0,
  `seuil_alerte` INT NOT NULL DEFAULT 5,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted_at` DATETIME NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_mercerie_atelier_uuid` (`atelier_id`, `uuid_local`),
  KEY `idx_mercerie_sync` (`atelier_id`, `updated_at`),
  CONSTRAINT `fk_mercerie_atelier` FOREIGN KEY (`atelier_id`) REFERENCES `ateliers` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Recette de mercerie par modèle
CREATE TABLE IF NOT EXISTS `modele_mercerie` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `atelier_id` BIGINT UNSIGNED NOT NULL,
  `modele_uuid` VARCHAR(36) NOT NULL,
  `mercerie_uuid` VARCHAR(36) NOT NULL,
  `quantite_necessaire` INT UNSIGNED NOT NULL DEFAULT 1,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_modele_mercerie_pair` (`atelier_id`, `modele_uuid`, `mercerie_uuid`),
  KEY `idx_mm_sync` (`atelier_id`, `updated_at`),
  CONSTRAINT `fk_mm_atelier` FOREIGN KEY (`atelier_id`) REFERENCES `ateliers` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Employés / Salariés (RH)
CREATE TABLE IF NOT EXISTS `employes` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `uuid_local` VARCHAR(36) NOT NULL,
  `atelier_id` BIGINT UNSIGNED NOT NULL,
  `nom` VARCHAR(150) NOT NULL,
  `telephone` VARCHAR(35) NOT NULL DEFAULT '',
  `poste` VARCHAR(60) NOT NULL DEFAULT 'Tailleur',
  `type_remuneration` ENUM('fixe', 'piece', 'commission') NOT NULL DEFAULT 'piece',
  `montant_remuneration` DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  `actif` TINYINT(1) NOT NULL DEFAULT 1,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted_at` DATETIME NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_employes_atelier_uuid` (`atelier_id`, `uuid_local`),
  KEY `idx_employes_sync` (`atelier_id`, `updated_at`),
  CONSTRAINT `fk_employes_atelier` FOREIGN KEY (`atelier_id`) REFERENCES `ateliers` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Commandes & Workflow
CREATE TABLE IF NOT EXISTS `commandes` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `uuid_local` VARCHAR(36) NOT NULL,
  `atelier_id` BIGINT UNSIGNED NOT NULL,
  `client_uuid` VARCHAR(36) NOT NULL,
  `modele_uuid` VARCHAR(36) NOT NULL,
  `employe_uuid` VARCHAR(36) NULL DEFAULT NULL,
  `photo_tissu_url` VARCHAR(500) NOT NULL DEFAULT '',
  `mesures_client` JSON NOT NULL,
  `date_commande` DATE NOT NULL,
  `date_essayage` DATE NULL DEFAULT NULL,
  `date_livraison_prevue` DATE NOT NULL,
  `prix_total` DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  `avance_payee` DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  `statut` ENUM('En attente', 'Coupe', 'Couture', 'Essayage', 'Prêt', 'Livré') NOT NULL DEFAULT 'En attente',
  `notes_commande` TEXT NULL,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted_at` DATETIME NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_commandes_atelier_uuid` (`atelier_id`, `uuid_local`),
  KEY `idx_commandes_sync` (`atelier_id`, `updated_at`),
  KEY `idx_commandes_client` (`atelier_id`, `client_uuid`),
  CONSTRAINT `fk_commandes_atelier` FOREIGN KEY (`atelier_id`) REFERENCES `ateliers` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. Dépenses & Justificatifs
CREATE TABLE IF NOT EXISTS `depenses` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `uuid_local` VARCHAR(36) NOT NULL,
  `atelier_id` BIGINT UNSIGNED NOT NULL,
  `categorie` VARCHAR(80) NOT NULL,
  `montant` DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
  `date_depense` DATE NOT NULL,
  `description` VARCHAR(255) NOT NULL DEFAULT '',
  `justificatif_url` VARCHAR(500) NOT NULL DEFAULT '',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted_at` DATETIME NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_depenses_atelier_uuid` (`atelier_id`, `uuid_local`),
  KEY `idx_depenses_sync` (`atelier_id`, `updated_at`),
  CONSTRAINT `fk_depenses_atelier` FOREIGN KEY (`atelier_id`) REFERENCES `ateliers` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. Limitation de débit (Rate Limiting API & Admin sur hébergement Apache mutualisé)
CREATE TABLE IF NOT EXISTS `rate_limits` (
  `bucket_key` VARCHAR(128) NOT NULL,
  `hits` INT UNSIGNED NOT NULL DEFAULT 1,
  `window_reset_at` DATETIME NOT NULL,
  PRIMARY KEY (`bucket_key`),
  KEY `idx_rate_limits_reset` (`window_reset_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

