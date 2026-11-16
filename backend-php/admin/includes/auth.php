<?php
declare(strict_types=1);

require_once dirname(__DIR__) . '/config/db.php';

function admin_start_session(): void
{
    if (session_status() === PHP_SESSION_ACTIVE) {
        return;
    }
    $isHttps = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off')
        || (($_SERVER['SERVER_PORT'] ?? '') === '443');

    session_set_cookie_params([
        'lifetime' => 0,
        'path'     => '/',
        'secure'   => $isHttps,
        'httponly' => true,
        'samesite' => 'Lax',
    ]);
    session_start();
}

function current_admin(): ?array
{
    admin_start_session();
    return $_SESSION['admin'] ?? null;
}

function require_admin(): array
{
    $admin = current_admin();
    if ($admin === null) {
        header('Location: login.php');
        exit;
    }
    return $admin;
}

function login_admin(PDO $pdo, string $email, string $password): bool
{
    admin_start_session();
    $stmt = $pdo->prepare(
        'SELECT id, nom, email, password_hash, actif FROM admins WHERE email = :email LIMIT 1'
    );
    $stmt->execute([':email' => strtolower(trim($email))]);
    $admin = $stmt->fetch();

    if ($admin === false || (int) $admin['actif'] !== 1) {
        return false;
    }
    if (!password_verify($password, (string) $admin['password_hash'])) {
        return false;
    }

    session_regenerate_id(true);
    $_SESSION['admin'] = [
        'id'    => (int) $admin['id'],
        'nom'   => (string) $admin['nom'],
        'email' => (string) $admin['email'],
    ];

    $upd = $pdo->prepare('UPDATE admins SET last_login_at = UTC_TIMESTAMP() WHERE id = :id');
    $upd->execute([':id' => (int) $admin['id']]);
    return true;
}

function logout_admin(): void
{
    admin_start_session();
    $_SESSION = [];
    if (ini_get('session.use_cookies')) {
        $p = session_get_cookie_params();
        setcookie(session_name(), '', time() - 42000, $p['path'], $p['domain'], $p['secure'], $p['httponly']);
    }
    session_destroy();
}

function csrf_token(): string
{
    admin_start_session();
    if (empty($_SESSION['csrf_token'])) {
        $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
    }
    return (string) $_SESSION['csrf_token'];
}

function verify_csrf(): void
{
    admin_start_session();
    $token = (string) ($_POST['csrf_token'] ?? '');
    $sessionToken = (string) ($_SESSION['csrf_token'] ?? '');
    if ($token === '' || $sessionToken === '' || !hash_equals($sessionToken, $token)) {
        http_response_code(403);
        exit('Jeton CSRF invalide ou expiré. Rechargez la page.');
    }
}

function set_flash(string $type, string $message): void
{
    admin_start_session();
    $_SESSION['flash'] = ['type' => $type, 'message' => $message];
}

function pop_flash(): ?array
{
    admin_start_session();
    $flash = $_SESSION['flash'] ?? null;
    unset($_SESSION['flash']);
    return $flash;
}
