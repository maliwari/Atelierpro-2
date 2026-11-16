<?php
declare(strict_types=1);

namespace App\Controllers;

use App\Config\Database;
use App\Http\Response;
use App\Security\JwtService;
use App\Security\RateLimiter;

final class AuthController
{
    public function register(): never
    {
        $pdo = Database::getConnection();
        $ip  = RateLimiter::clientIp();
        RateLimiter::enforceApi($pdo, 'api_register_ip', $ip, 5, 3600);

        $input = Response::readJsonBody();

        $nomAtelier = trim((string) ($input['nom_atelier'] ?? ''));
        $telephone  = trim((string) ($input['telephone'] ?? ''));
        $email      = strtolower(trim((string) ($input['email'] ?? '')));
        $password   = (string) ($input['password'] ?? '');

        $errors = [];
        if (mb_strlen($nomAtelier) < 2) {
            $errors['nom_atelier'] = 'Le nom de l’atelier est requis (min. 2 caractères).';
        }
        if ($telephone === '') {
            $errors['telephone'] = 'Le numéro de téléphone est requis.';
        }
        if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
            $errors['email'] = 'Adresse email invalide.';
        }
        if (strlen($password) < 8) {
            $errors['password'] = 'Le mot de passe doit contenir au moins 8 caractères.';
        }
        if ($errors !== []) {
            Response::error('Données d’inscription invalides.', 422, $errors);
        }

        $check = $pdo->prepare('SELECT id FROM ateliers WHERE email = :email LIMIT 1');
        $check->execute([':email' => $email]);
        if ($check->fetch() !== false) {
            Response::error('Un atelier est déjà enregistré avec cet email.', 422, ['email' => 'Email déjà utilisé.']);
        }

        $passwordHash = password_hash($password, PASSWORD_BCRYPT, ['cost' => 12]);
        $expiration = gmdate('Y-m-d H:i:s', strtotime('+30 days'));

        $stmt = $pdo->prepare(
            'INSERT INTO ateliers (nom_atelier, telephone, email, password_hash, plan_abonnement, date_expiration)
             VALUES (:nom_atelier, :telephone, :email, :password_hash, :plan, :expiration)'
        );
        $stmt->execute([
            ':nom_atelier'   => $nomAtelier,
            ':telephone'     => $telephone,
            ':email'         => $email,
            ':password_hash' => $passwordHash,
            ':plan'          => 'free',
            ':expiration'    => $expiration,
        ]);

        $atelierId = (int) $pdo->lastInsertId();
        $token = JwtService::issueToken($atelierId, $email);

        Response::json([
            'success' => true,
            'token'   => $token,
            'atelier' => [
                'id'              => $atelierId,
                'nom_atelier'     => $nomAtelier,
                'telephone'       => $telephone,
                'email'           => $email,
                'plan_abonnement' => 'free',
                'date_expiration' => $expiration,
            ],
        ], 201);
    }

    public function login(): never
    {
        $pdo   = Database::getConnection();
        $ip    = RateLimiter::clientIp();
        $input = Response::readJsonBody();

        $email    = strtolower(trim((string) ($input['email'] ?? '')));
        $password = (string) ($input['password'] ?? '');

        RateLimiter::enforceApi($pdo, 'api_login_ip', $ip, 30, 900);
        if ($email !== '') {
            RateLimiter::enforceApi($pdo, 'api_login_acct', $ip . '|' . $email, 10, 900);
        }

        if (!filter_var($email, FILTER_VALIDATE_EMAIL) || $password === '') {
            Response::error('Email et mot de passe requis.', 422);
        }

        $stmt = $pdo->prepare(
            'SELECT id, nom_atelier, telephone, email, password_hash, plan_abonnement, date_expiration
             FROM ateliers WHERE email = :email LIMIT 1'
        );
        $stmt->execute([':email' => $email]);
        $atelier = $stmt->fetch();

        if ($atelier === false || !password_verify($password, (string) $atelier['password_hash'])) {
            Response::error('Identifiants incorrects.', 401);
        }

        if (!empty($atelier['date_expiration']) && strtotime((string) $atelier['date_expiration']) < time()) {
            Response::error('Abonnement expiré. Veuillez renouveler votre offre.', 403);
        }

        RateLimiter::reset($pdo, 'api_login_acct', $ip . '|' . $email);

        $atelierId = (int) $atelier['id'];
        $token = JwtService::issueToken($atelierId, (string) $atelier['email']);

        Response::json([
            'success' => true,
            'token'   => $token,
            'atelier' => [
                'id'              => $atelierId,
                'nom_atelier'     => $atelier['nom_atelier'],
                'telephone'       => $atelier['telephone'],
                'email'           => $atelier['email'],
                'plan_abonnement' => $atelier['plan_abonnement'],
                'date_expiration' => $atelier['date_expiration'],
            ],
        ], 200);
    }
}
