<?php
declare(strict_types=1);

$coreCandidates = [
    dirname(__DIR__, 2),
    dirname(__DIR__, 3) . '/secure/atelierpro-core',
    dirname(__DIR__, 3) . '/secure',
    dirname(__DIR__, 4) . '/secure/atelierpro-core',
    dirname(__DIR__, 4) . '/secure',
];

$coreRoot = dirname(__DIR__, 2);
foreach ($coreCandidates as $candidate) {
    if (is_file($candidate . '/config/Config.php') && is_file($candidate . '/config/Database.php')) {
        $coreRoot = $candidate;
        break;
    }
}

if (!defined('ATELIERPRO_CORE_DIR')) {
    define('ATELIERPRO_CORE_DIR', $coreRoot);
}

require_once ATELIERPRO_CORE_DIR . '/config/Config.php';
require_once ATELIERPRO_CORE_DIR . '/config/Database.php';
require_once ATELIERPRO_CORE_DIR . '/src/Http/Response.php';
require_once ATELIERPRO_CORE_DIR . '/src/Security/RateLimiter.php';

use App\Config\Config;
use App\Config\Database;

Config::loadAuto(ATELIERPRO_CORE_DIR);

if (!defined('CLE_SECRETE')) {
    define('CLE_SECRETE', Config::get('LICENSE_SECRET', 'AtelierPro_M@li_2026!HMAC#Key_v1'));
}

const LICENSE_FORMULAS = [
    'M1' => ['label' => 'Mensuel (30 jours)',     'days' => 30,  'price_fcfa' => 3500],
    'M3' => ['label' => 'Trimestriel (90 jours)', 'days' => 90,  'price_fcfa' => 10000],
    'A1' => ['label' => 'Annuel (365 jours)',     'days' => 365, 'price_fcfa' => 35000],
];

const PAYMENT_METHODS = ['Orange Money', 'Moov Money', 'Espèces'];

function admin_db(): PDO
{
    return Database::getConnection();
}

function e(mixed $val): string
{
    return htmlspecialchars((string) ($val ?? ''), ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

function compute_license_code(string $codeAtelier, string $formule, ?string $yearMonth = null): string
{
    $ym = $yearMonth ?? gmdate('Y-m');
    $codeUpper = strtoupper(trim($codeAtelier));
    $formUpper = strtoupper(trim($formule));
    $payload = "{$codeUpper}-{$formUpper}-{$ym}";
    $hmacHex = hash_hmac('sha256', $payload, CLE_SECRETE);
    $sig6 = strtoupper(substr($hmacHex, 0, 6));
    return "{$formUpper}-{$sig6}";
}

function generate_workshop_code(): string
{
    $alphabet = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
    $suffix = '';
    for ($i = 0; $i < 4; $i++) {
        $suffix .= $alphabet[random_int(0, strlen($alphabet) - 1)];
    }
    return "ATEL-{$suffix}";
}

function format_fcfa(int|float|string $amount): string
{
    return number_format((float) $amount, 0, ',', ' ') . ' FCFA';
}

function wa_url(string $phone, string $message): string
{
    $digits = preg_replace('/\D+/', '', $phone) ?? '';
    return 'https://wa.me/' . $digits . '?text=' . rawurlencode($message);
}

function atelier_status(?string $expiration): array
{
    if (empty($expiration)) {
        return ['label' => 'Expiré', 'badge' => 'danger', 'days_left' => 0];
    }
    $expTs = strtotime($expiration . ' UTC');
    $nowTs = time();
    if ($expTs === false || $expTs < $nowTs) {
        return ['label' => 'Expiré', 'badge' => 'danger', 'days_left' => 0];
    }
    $daysLeft = (int) ceil(($expTs - $nowTs) / 86400);
    if ($daysLeft <= 5) {
        return ['label' => 'Expire bientôt', 'badge' => 'warning', 'days_left' => $daysLeft];
    }
    return ['label' => 'Actif', 'badge' => 'success', 'days_left' => $daysLeft];
}
