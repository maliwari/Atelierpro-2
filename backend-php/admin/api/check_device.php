<?php
declare(strict_types=1);

/**
 * Endpoint JSON : POST /admin/api/check_device.php (aussi routé sur POST /api/check_device)
 * Enregistre le device_uuid lors de la synchronisation Android, vérifie le quota max_appareils
 * et le statut (autorise / bloque), puis renvoie le statut de licence à jour.
 */

require_once dirname(__DIR__) . '/config/db.php';
require_once ATELIERPRO_CORE_DIR . '/src/Http/Response.php';
require_once ATELIERPRO_CORE_DIR . '/src/Security/JwtService.php';
require_once ATELIERPRO_CORE_DIR . '/src/Security/RateLimiter.php';

use App\Http\Response;
use App\Security\JwtService;
use App\Security\RateLimiter;

Response::sendCorsHeaders();

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'OPTIONS') {
    http_response_code(204);
    exit;
}

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') !== 'POST') {
    Response::error('Méthode non autorisée. Utilisez POST.', 405);
}

$pdo = admin_db();
RateLimiter::enforceApi($pdo, 'api_check_device', RateLimiter::clientIp(), 60, 60);

$input = Response::readJsonBody();
$deviceUuid = trim((string) ($input['device_uuid'] ?? ''));
$deviceName = trim((string) ($input['device_name'] ?? 'Appareil Android'));
$platform   = trim((string) ($input['platform'] ?? 'android'));
$codeAtelier = strtoupper(trim((string) ($input['code_atelier'] ?? $input['workshop_code'] ?? '')));

if ($deviceUuid === '' || strlen($deviceUuid) > 100) {
    Response::error('Le champ device_uuid est requis (max 100 caractères).', 422);
}

$atelierId = null;

// 1) Authentification prioritaire via Bearer Token JWT
$authHeader = $_SERVER['HTTP_AUTHORIZATION'] ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? '';
if (preg_match('/^Bearer\s+(.+)$/i', trim((string) $authHeader), $m)) {
    $claims = JwtService::verifyToken($m[1]);
    if ($claims !== null) {
        $atelierId = (int) $claims['sub'];
    }
}

try {
    $pdo->beginTransaction();

    if ($atelierId !== null) {
        $stmt = $pdo->prepare(
            'SELECT id, code_atelier, nom_atelier, plan_abonnement, max_appareils, date_expiration
             FROM ateliers WHERE id = :id FOR UPDATE'
        );
        $stmt->execute([':id' => $atelierId]);
    } elseif ($codeAtelier !== '') {
        $stmt = $pdo->prepare(
            'SELECT id, code_atelier, nom_atelier, plan_abonnement, max_appareils, date_expiration
             FROM ateliers WHERE code_atelier = :code FOR UPDATE'
        );
        $stmt->execute([':code' => $codeAtelier]);
    } else {
        $pdo->rollBack();
        Response::error('Authorization Bearer Token ou code_atelier requis.', 401);
    }

    $atelier = $stmt->fetch();
    if ($atelier === false) {
        $pdo->rollBack();
        Response::error('Atelier introuvable.', 404);
    }

    $aid = (int) $atelier['id'];
    $maxAppareils = max(1, (int) $atelier['max_appareils']);
    $stInfo = atelier_status($atelier['date_expiration']);
    $statutLicence = $stInfo['label'] === 'Expiré' ? 'expire' : 'actif';

    // Vérifier si cet appareil existe déjà
    $devStmt = $pdo->prepare(
        'SELECT id, statut FROM appareils WHERE atelier_id = :aid AND device_uuid = :uuid LIMIT 1 FOR UPDATE'
    );
    $devStmt->execute([':aid' => $aid, ':uuid' => $deviceUuid]);
    $device = $devStmt->fetch();

    if ($device !== false) {
        if ($device['statut'] === 'bloque') {
            $pdo->commit();
            Response::json([
                'success'         => false,
                'authorized'      => false,
                'reason'          => 'device_blocked',
                'message'         => 'Cet appareil a été bloqué par l’administrateur.',
                'statut'          => $statutLicence,
                'date_expiration' => $atelier['date_expiration'],
            ], 403);
        }

        $updDev = $pdo->prepare(
            'UPDATE appareils SET last_seen_at = UTC_TIMESTAMP(), device_name = :name, platform = :plat WHERE id = :id'
        );
        $updDev->execute([
            ':name' => mb_substr($deviceName !== '' ? $deviceName : 'Appareil Android', 0, 150),
            ':plat' => mb_substr($platform !== '' ? $platform : 'android', 0, 40),
            ':id'   => (int) $device['id'],
        ]);
    } else {
        $cntStmt = $pdo->prepare('SELECT COUNT(*) FROM appareils WHERE atelier_id = :aid');
        $cntStmt->execute([':aid' => $aid]);
        $currentCount = (int) $cntStmt->fetchColumn();

        if ($currentCount >= $maxAppareils) {
            $pdo->commit();
            Response::json([
                'success'         => false,
                'authorized'      => false,
                'reason'          => 'device_quota_exceeded',
                'message'         => "Quota d’appareils atteint ({$currentCount}/{$maxAppareils}). Supprimez un ancien téléphone depuis l’administration.",
                'devices_used'    => $currentCount,
                'max_appareils'   => $maxAppareils,
                'statut'          => $statutLicence,
                'date_expiration' => $atelier['date_expiration'],
            ], 403);
        }

        $insDev = $pdo->prepare(
            'INSERT INTO appareils (atelier_id, device_uuid, device_name, platform, statut, last_seen_at)
             VALUES (:aid, :uuid, :name, :plat, \'autorise\', UTC_TIMESTAMP())'
        );
        $insDev->execute([
            ':aid'  => $aid,
            ':uuid' => $deviceUuid,
            ':name' => mb_substr($deviceName !== '' ? $deviceName : 'Appareil Android', 0, 150),
            ':plat' => mb_substr($platform !== '' ? $platform : 'android', 0, 40),
        ]);
    }

    $cntAfterStmt = $pdo->prepare('SELECT COUNT(*) FROM appareils WHERE atelier_id = :aid AND statut = \'autorise\'');
    $cntAfterStmt->execute([':aid' => $aid]);
    $devicesUsed = (int) $cntAfterStmt->fetchColumn();

    $pdo->commit();

    Response::json([
        'success'         => true,
        'authorized'      => true,
        'code_atelier'    => $atelier['code_atelier'],
        'plan_abonnement' => $atelier['plan_abonnement'],
        'statut'          => $statutLicence,
        'date_expiration' => $atelier['date_expiration'],
        'devices_used'    => $devicesUsed,
        'max_appareils'   => $maxAppareils,
    ], 200);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    error_log('[AtelierPro check_device] ' . $e->getMessage());
    Response::error('Erreur serveur lors de la vérification appareil.', 500);
}
