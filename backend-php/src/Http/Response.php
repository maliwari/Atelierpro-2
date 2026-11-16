<?php
declare(strict_types=1);

namespace App\Http;

use App\Config\Config;

final class Response
{
    public static function sendCorsHeaders(): void
    {
        $origin = Config::get('CORS_ALLOWED_ORIGIN', '*');
        header('Access-Control-Allow-Origin: ' . $origin);
        header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
        header('Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With');
        header('Access-Control-Max-Age: 86400');
    }

    public static function json(array $payload, int $statusCode = 200): never
    {
        http_response_code($statusCode);
        header('Content-Type: application/json; charset=utf-8');
        echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
        exit;
    }

    public static function error(string $message, int $statusCode, array $details = []): never
    {
        $body = ['success' => false, 'error' => $message];
        if ($details !== []) {
            $body['details'] = $details;
        }
        self::json($body, $statusCode);
    }

    public static function readJsonBody(): array
    {
        $raw = file_get_contents('php://input');
        if ($raw === false || trim($raw) === '') {
            return [];
        }
        $decoded = json_decode($raw, true);
        if (!is_array($decoded)) {
            self::error('Payload JSON invalide.', 422);
        }
        return $decoded;
    }
}
