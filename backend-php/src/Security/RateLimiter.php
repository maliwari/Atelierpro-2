<?php
declare(strict_types=1);

namespace App\Security;

use App\Config\Config;
use App\Http\Response;
use PDO;
use Throwable;

/**
 * Limitation de débit (Rate Limiting) persistante via MySQL/PDO.
 * Adaptée aux hébergements Apache mutualisés (aucune dépendance Redis/Nginx requise).
 *
 * Seuils recommandés :
 * - Login API (/api/auth/login)     : 10 essais / 15 min par (IP + email), 30 / 15 min par IP
 * - Register API (/api/auth/register): 5 créations / 1 heure par IP
 * - Login Admin (/admin/pages/login): 5 essais / 15 min par (IP + email), 15 / 15 min par IP
 * - Check Device (/api/check_device): 60 req / min par IP
 * - Sync & Upload authentifiés      : 120 req / min par atelier_id
 */
final class RateLimiter
{
    public static function clientIp(): string
    {
        $trustedProxy = strtolower(Config::get('TRUSTED_PROXY', 'false')) === 'true';
        if ($trustedProxy) {
            foreach (['HTTP_CF_CONNECTING_IP', 'HTTP_X_FORWARDED_FOR', 'HTTP_X_REAL_IP'] as $header) {
                $val = trim((string) ($_SERVER[$header] ?? ''));
                if ($val !== '') {
                    $first = trim(explode(',', $val)[0]);
                    if (filter_var($first, FILTER_VALIDATE_IP)) {
                        return $first;
                    }
                }
            }
        }

        $remote = trim((string) ($_SERVER['REMOTE_ADDR'] ?? '0.0.0.0'));
        return filter_var($remote, FILTER_VALIDATE_IP) ? $remote : '0.0.0.0';
    }

    /**
     * Incrémente le compteur atomiquement et retourne ['allowed' => bool, 'remaining' => int, 'retry_after' => int].
     */
    public static function consume(PDO $pdo, string $scope, string $identifier, int $maxHits, int $windowSeconds): array
    {
        $bucketKey = $scope . ':' . hash('sha256', $identifier);

        try {
            $upsert = $pdo->prepare(
                'INSERT INTO rate_limits (bucket_key, hits, window_reset_at)
                 VALUES (:key, 1, TIMESTAMPADD(SECOND, :win1, UTC_TIMESTAMP()))
                 ON DUPLICATE KEY UPDATE
                   hits = IF(window_reset_at <= UTC_TIMESTAMP(), 1, hits + 1),
                   window_reset_at = IF(window_reset_at <= UTC_TIMESTAMP(), TIMESTAMPADD(SECOND, :win2, UTC_TIMESTAMP()), window_reset_at)'
            );
            $upsert->execute([
                ':key'  => $bucketKey,
                ':win1' => $windowSeconds,
                ':win2' => $windowSeconds,
            ]);

            $sel = $pdo->prepare(
                'SELECT hits, GREATEST(1, TIMESTAMPDIFF(SECOND, UTC_TIMESTAMP(), window_reset_at)) AS retry_after
                 FROM rate_limits
                 WHERE bucket_key = :key
                 LIMIT 1'
            );
            $sel->execute([':key' => $bucketKey]);
            $row = $sel->fetch();

            // Nettoyage périodique (1 % des requêtes) pour garder la table compacte
            if (random_int(1, 100) === 1) {
                $pdo->exec('DELETE FROM rate_limits WHERE window_reset_at < UTC_TIMESTAMP() LIMIT 500');
            }

            $hits = (int) ($row['hits'] ?? 1);
            $retryAfter = (int) ($row['retry_after'] ?? $windowSeconds);
            $allowed = $hits <= $maxHits;

            return [
                'allowed'     => $allowed,
                'limit'       => $maxHits,
                'remaining'   => max(0, $maxHits - $hits),
                'retry_after' => $allowed ? 0 : $retryAfter,
            ];
        } catch (Throwable $e) {
            // En cas de table absente avant migration, journaliser sans casser l'application
            error_log('[AtelierPro RateLimiter] ' . $e->getMessage());
            return [
                'allowed'     => true,
                'limit'       => $maxHits,
                'remaining'   => $maxHits,
                'retry_after' => 0,
            ];
        }
    }

    /**
     * Applique le rate limit sur un endpoint JSON et renvoie HTTP 429 en cas de dépassement.
     */
    public static function enforceApi(PDO $pdo, string $scope, string $identifier, int $maxHits, int $windowSeconds): void
    {
        $res = self::consume($pdo, $scope, $identifier, $maxHits, $windowSeconds);
        header('X-RateLimit-Limit: ' . $res['limit']);
        header('X-RateLimit-Remaining: ' . $res['remaining']);

        if (!$res['allowed']) {
            header('Retry-After: ' . $res['retry_after']);
            Response::error(
                'Trop de tentatives. Veuillez patienter avant de réessayer.',
                429,
                ['retry_after_seconds' => $res['retry_after']]
            );
        }
    }

    public static function reset(PDO $pdo, string $scope, string $identifier): void
    {
        try {
            $bucketKey = $scope . ':' . hash('sha256', $identifier);
            $stmt = $pdo->prepare('DELETE FROM rate_limits WHERE bucket_key = :key');
            $stmt->execute([':key' => $bucketKey]);
        } catch (Throwable $e) {
            error_log('[AtelierPro RateLimiter reset] ' . $e->getMessage());
        }
    }
}
