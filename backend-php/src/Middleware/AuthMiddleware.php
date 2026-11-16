<?php
declare(strict_types=1);

namespace App\Middleware;

use App\Config\Database;
use App\Http\Response;
use App\Security\JwtService;

final class AuthMiddleware
{
    /**
     * Validates Bearer JWT and checks that the tenant workshop exists and is active.
     * @return array<string, mixed> Authenticated atelier row
     */
    public static function requireAtelier(): array
    {
        $authHeader = $_SERVER['HTTP_AUTHORIZATION']
            ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION']
            ?? '';

        if (!preg_match('/^Bearer\s+(.+)$/i', trim($authHeader), $matches)) {
            Response::error('Token Bearer manquant ou invalide.', 401);
        }

        $claims = JwtService::verifyToken(trim($matches[1]));
        if ($claims === null) {
            Response::error('Session expirée ou token invalide.', 401);
        }

        $atelierId = (int) $claims['sub'];
        $pdo = Database::getConnection();
        $stmt = $pdo->prepare(
            'SELECT id, nom_atelier, telephone, email, plan_abonnement, date_expiration
             FROM ateliers WHERE id = :id LIMIT 1'
        );
        $stmt->execute([':id' => $atelierId]);
        $atelier = $stmt->fetch();

        if ($atelier === false) {
            Response::error('Compte atelier introuvable.', 401);
        }

        if (!empty($atelier['date_expiration']) && strtotime((string) $atelier['date_expiration']) < time()) {
            Response::error('Abonnement expiré. Veuillez renouveler votre offre.', 403);
        }

        return $atelier;
    }
}
