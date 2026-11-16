<?php
declare(strict_types=1);

/**
 * Script CLI pour créer ou réinitialiser un compte administrateur du Backoffice AtelierPro.
 * Fonctionne autant avec l'arborescence standard qu'avec la séparation secure/ + public_html/.
 *
 * Usage :
 *   php scripts/create_admin.php "Administrateur AtelierPro" admin@crfdmali.org MotDePasseFort123!
 */

if (PHP_SAPI !== 'cli') {
    fwrite(STDERR, "Exécution uniquement autorisée en CLI.\n");
    exit(1);
}

$coreRoot = dirname(__DIR__);
require_once $coreRoot . '/config/Config.php';
require_once $coreRoot . '/config/Database.php';

use App\Config\Config;
use App\Config\Database;

Config::loadAuto($coreRoot);

$nom      = trim($argv[1] ?? '');
$email    = strtolower(trim($argv[2] ?? ''));
$password = (string) ($argv[3] ?? '');

if ($nom === '' || !filter_var($email, FILTER_VALIDATE_EMAIL) || strlen($password) < 8) {
    fwrite(STDOUT, "Usage : php scripts/create_admin.php \"Nom\" email@domaine.com MotDePasseMin8Car\n");
    exit(1);
}

$pdo  = Database::getConnection();
$hash = password_hash($password, PASSWORD_BCRYPT, ['cost' => 12]);

$stmt = $pdo->prepare(
    'INSERT INTO admins (nom, email, password_hash, actif)
     VALUES (:nom, :email, :hash, 1)
     ON DUPLICATE KEY UPDATE nom = VALUES(nom), password_hash = VALUES(password_hash), actif = 1'
);
$stmt->execute([
    ':nom'   => $nom,
    ':email' => $email,
    ':hash'  => $hash,
]);

fwrite(STDOUT, "Administrateur enregistré : {$email}\n");
