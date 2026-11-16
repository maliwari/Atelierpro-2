<?php
declare(strict_types=1);

namespace App\Config;

final class Config
{
    private static array $env = [];
    private static bool $loaded = false;

    public static function load(string $envFile): void
    {
        if (self::$loaded) {
            return;
        }
        $resolved = is_file($envFile) ? $envFile : self::resolveEnvFile(dirname($envFile));
        if ($resolved !== null && is_file($resolved)) {
            $lines = file($resolved, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES) ?: [];
            foreach ($lines as $line) {
                $trimmed = trim($line);
                if ($trimmed === '' || str_starts_with($trimmed, '#')) {
                    continue;
                }
                [$key, $val] = array_pad(explode('=', $trimmed, 2), 2, '');
                self::$env[trim($key)] = trim($val, " \t\n\r\0\x0B\"'");
            }
        }
        self::$loaded = true;
    }

    /**
     * Charge automatiquement le fichier .env, en priorisant le dossier `secure/`
     * situé au même niveau que `public_html/` sous Apache.
     */
    public static function loadAuto(?string $anchorDir = null): void
    {
        if (self::$loaded) {
            return;
        }
        $envPath = self::resolveEnvFile($anchorDir ?? dirname(__DIR__));
        self::load($envPath ?? dirname(__DIR__) . '/.env');
    }

    public static function resolveEnvFile(string $anchorDir): ?string
    {
        $fromServer = getenv('ATELIERPRO_ENV_FILE') ?: ($_SERVER['ATELIERPRO_ENV_FILE'] ?? '');
        if ($fromServer !== '' && is_file($fromServer)) {
            return $fromServer;
        }

        $candidates = [
            $anchorDir . '/.env',
            dirname($anchorDir) . '/.env',
            dirname($anchorDir) . '/secure/.env',
            dirname($anchorDir, 2) . '/secure/.env',
            dirname($anchorDir, 3) . '/secure/.env',
            dirname($anchorDir, 4) . '/secure/.env',
        ];

        $docRoot = (string) ($_SERVER['DOCUMENT_ROOT'] ?? '');
        if ($docRoot !== '' && str_contains($docRoot, '/public_html')) {
            $accountRoot = preg_replace('#/public_html.*$#', '', rtrim($docRoot, '/'));
            if (is_string($accountRoot) && $accountRoot !== '') {
                array_unshift($candidates, $accountRoot . '/secure/.env');
            }
        }

        foreach ($candidates as $path) {
            if (is_file($path)) {
                return $path;
            }
        }
        return null;
    }

    public static function get(string $key, string $default = ''): string
    {
        $fromEnv = getenv($key);
        if ($fromEnv !== false && $fromEnv !== '') {
            return $fromEnv;
        }
        return self::$env[$key] ?? $default;
    }
}
