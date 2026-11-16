<?php
declare(strict_types=1);

/**
 * Outil CLI Administrateur AtelierPro : Génération de codes d'activation WhatsApp hors-ligne.
 * Lit automatiquement LICENSE_SECRET depuis secure/.env ou .env.
 *
 * Usage :
 *   php scripts/generate_license_code.php ATEL-8K2M M1
 *   php scripts/generate_license_code.php ATEL-8K2M A1 2026-11
 */

if (PHP_SAPI !== 'cli') {
    fwrite(STDERR, "Ce script doit être exécuté en ligne de commande (CLI).\n");
    exit(1);
}

$coreRoot = dirname(__DIR__);
require_once $coreRoot . '/config/Config.php';

use App\Config\Config;

Config::loadAuto($coreRoot);
$secret = Config::get('LICENSE_SECRET', 'AtelierPro_M@li_2026!HMAC#Key_v1');

$workshopId = strtoupper(trim($argv[1] ?? ''));
$duration   = strtoupper(trim($argv[2] ?? 'M1'));
$yearMonth  = trim($argv[3] ?? gmdate('Y-m'));

if ($workshopId === '' || !in_array($duration, ['M1', 'M3', 'A1'], true)) {
    fwrite(STDOUT, "Usage : php scripts/generate_license_code.php <ID_ATELIER> <M1|M3|A1> [YYYY-MM]\n");
    fwrite(STDOUT, "Exemple : php scripts/generate_license_code.php ATEL-7K4M M3\n");
    exit(1);
}

$payload = "{$workshopId}-{$duration}-{$yearMonth}";
$hmacHex = hash_hmac('sha256', $payload, $secret);
$sig6    = strtoupper(substr($hmacHex, 0, 6));
$code    = "{$duration}-{$sig6}";
$days    = match ($duration) {
    'A1'    => 365,
    'M3'    => 90,
    default => 30,
};

fwrite(STDOUT, "Atelier  : {$workshopId}\n");
fwrite(STDOUT, "Mois     : {$yearMonth}\n");
fwrite(STDOUT, "Durée    : {$days} jours ({$duration})\n");
fwrite(STDOUT, "Code     : {$code}\n");
