<?php
declare(strict_types=1);

use App\Config\Config;
use App\Controllers\AuthController;
use App\Controllers\SyncController;
use App\Controllers\UploadController;
use App\Http\Response;

define('ATELIERPRO_PUBLIC_DIR', __DIR__);

// Découverte automatique du répertoire cœur (backend-php/ local OU ../../secure/atelierpro-core/ sous Apache)
$coreCandidates = [
    dirname(__DIR__),
    dirname(__DIR__, 2) . '/secure/atelierpro-core',
    dirname(__DIR__, 2) . '/secure',
];
$coreRoot = dirname(__DIR__);
foreach ($coreCandidates as $candidate) {
    if (is_dir($candidate . '/src') && is_dir($candidate . '/config')) {
        $coreRoot = $candidate;
        break;
    }
}
define('ATELIERPRO_CORE_DIR', $coreRoot);

// Autoloader PSR-4 léger (zéro dépendance Composer requise)
spl_autoload_register(static function (string $class): void {
    $prefixes = [
        'App\\Config\\' => ATELIERPRO_CORE_DIR . '/config/',
        'App\\'         => ATELIERPRO_CORE_DIR . '/src/',
    ];
    foreach ($prefixes as $prefix => $baseDir) {
        if (str_starts_with($class, $prefix)) {
            $relative = substr($class, strlen($prefix));
            $file = $baseDir . str_replace('\\', '/', $relative) . '.php';
            if (is_file($file)) {
                require_once $file;
            }
            return;
        }
    }
});

Config::loadAuto(ATELIERPRO_CORE_DIR);
Response::sendCorsHeaders();

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'OPTIONS') {
    http_response_code(204);
    exit;
}

set_exception_handler(static function (Throwable $e): void {
    error_log('[AtelierPro API] ' . $e->getMessage());
    Response::error('Erreur interne du serveur.', 500);
});

$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
$path   = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';

// Supporte aussi un montage dans un sous-répertoire Apache (/api-atelierpro/api/...)
$scriptDir = rtrim(str_replace('\\', '/', dirname((string) ($_SERVER['SCRIPT_NAME'] ?? ''))), '/');
if ($scriptDir !== '' && $scriptDir !== '/' && str_starts_with($path, $scriptDir)) {
    $path = substr($path, strlen($scriptDir)) ?: '/';
}
$path = rtrim($path, '/') ?: '/';

$checkDeviceFile = is_file(__DIR__ . '/admin/api/check_device.php')
    ? __DIR__ . '/admin/api/check_device.php'
    : ATELIERPRO_CORE_DIR . '/admin/api/check_device.php';

match ("$method $path") {
    'POST /api/auth/register' => (new AuthController())->register(),
    'POST /api/auth/login'    => (new AuthController())->login(),
    'POST /api/sync/push'     => (new SyncController())->push(),
    'GET /api/sync/pull'      => (new SyncController())->pull(),
    'POST /api/upload/image'  => (new UploadController())->uploadImage(),
    'POST /api/check_device'  => require $checkDeviceFile,
    default                   => Response::error('Route API introuvable.', 404),
};
