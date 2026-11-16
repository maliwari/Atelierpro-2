<?php
declare(strict_types=1);

namespace App\Security;

use App\Config\Config;

/**
 * Pure PHP 8+ HS256 JWT implementation (no external Composer dependencies).
 */
final class JwtService
{
    public static function issueToken(int $atelierId, string $email): string
    {
        $secret = self::secret();
        $now = time();
        $ttl = (int) Config::get('JWT_TTL_SECONDS', '2592000'); // 30 days default

        $header = self::base64UrlEncode((string) json_encode(['alg' => 'HS256', 'typ' => 'JWT']));
        $payload = self::base64UrlEncode((string) json_encode([
            'sub'   => $atelierId,
            'email' => $email,
            'iat'   => $now,
            'exp'   => $now + $ttl,
        ]));

        $signature = hash_hmac('sha256', "$header.$payload", $secret, true);
        return "$header.$payload." . self::base64UrlEncode($signature);
    }

    public static function verifyToken(string $token): ?array
    {
        $parts = explode('.', $token);
        if (count($parts) !== 3) {
            return null;
        }

        [$headerB64, $payloadB64, $sigB64] = $parts;
        $expectedSig = self::base64UrlEncode(
            hash_hmac('sha256', "$headerB64.$payloadB64", self::secret(), true)
        );

        if (!hash_equals($expectedSig, $sigB64)) {
            return null;
        }

        $payloadJson = self::base64UrlDecode($payloadB64);
        $payload = json_decode($payloadJson, true);
        if (!is_array($payload) || !isset($payload['sub'], $payload['exp'])) {
            return null;
        }

        if ((int) $payload['exp'] < time()) {
            return null;
        }

        return $payload;
    }

    private static function secret(): string
    {
        $secret = Config::get('JWT_SECRET', '');
        if (strlen($secret) < 16) {
            throw new \RuntimeException('JWT_SECRET non configuré ou trop court.');
        }
        return $secret;
    }

    private static function base64UrlEncode(string $data): string
    {
        return rtrim(strtr(base64_encode($data), '+/', '-_'), '=');
    }

    private static function base64UrlDecode(string $data): string
    {
        $remainder = strlen($data) % 4;
        if ($remainder > 0) {
            $data .= str_repeat('=', 4 - $remainder);
        }
        return base64_decode(strtr($data, '-_', '+/')) ?: '';
    }
}
