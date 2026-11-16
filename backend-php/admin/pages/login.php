<?php
declare(strict_types=1);

require_once dirname(__DIR__) . '/includes/auth.php';

use App\Security\RateLimiter;

if (current_admin() !== null) {
    header('Location: dashboard.php');
    exit;
}

$error = null;
$email = '';

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    verify_csrf();
    $pdo      = admin_db();
    $ip       = RateLimiter::clientIp();
    $email    = trim((string) ($_POST['email'] ?? ''));
    $password = (string) ($_POST['password'] ?? '');

    $ipLimit   = RateLimiter::consume($pdo, 'admin_login_ip', $ip, 15, 900);
    $acctLimit = $email !== ''
        ? RateLimiter::consume($pdo, 'admin_login_acct', $ip . '|' . strtolower($email), 5, 900)
        : ['allowed' => true, 'retry_after' => 0];

    if (!$ipLimit['allowed'] || !$acctLimit['allowed']) {
        $waitSec = max((int) $ipLimit['retry_after'], (int) $acctLimit['retry_after']);
        $waitMin = max(1, (int) ceil($waitSec / 60));
        http_response_code(429);
        header('Retry-After: ' . $waitSec);
        $error = "Trop de tentatives de connexion. Réessayez dans environ {$waitMin} minute(s).";
    } elseif ($email === '' || $password === '') {
        $error = 'Veuillez saisir votre email et votre mot de passe.';
    } elseif (login_admin($pdo, $email, $password)) {
        RateLimiter::reset($pdo, 'admin_login_acct', $ip . '|' . strtolower($email));
        header('Location: dashboard.php');
        exit;
    } else {
        $error = 'Identifiants invalides ou compte administrateur désactivé.';
    }
}
?>
<!DOCTYPE html>
<html lang="fr">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Connexion Admin — AtelierPro</title>
  <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
  <link href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css" rel="stylesheet">
  <style>
    body { background-color: #FAF9F6; }
    .btn-indigo { background-color: #1E3A5F; border-color: #1E3A5F; color: #ffffff; }
    .btn-indigo:hover { background-color: #162C49; border-color: #162C49; color: #ffffff; }
  </style>
</head>
<body class="d-flex align-items-center justify-content-center min-vh-100 p-3">
  <div class="card border shadow-sm rounded-4 w-100" style="max-width: 420px;">
    <div class="card-body p-4 p-md-5">
      <div class="text-center mb-4">
        <span class="rounded-3 d-inline-flex align-items-center justify-content-center text-white mb-2" style="width:48px;height:48px;background:#1E3A5F;">
          <i class="bi bi-scissors fs-4"></i>
        </span>
        <h1 class="h4 fw-bold mb-1">AtelierPro Admin</h1>
        <p class="text-muted small mb-0">Gestion des licences, appareils &amp; WhatsApp</p>
      </div>

      <?php if ($error !== null): ?>
        <div class="alert alert-danger py-2 small"><?= e($error) ?></div>
      <?php endif; ?>

      <form method="post" autocomplete="on" novalidate>
        <input type="hidden" name="csrf_token" value="<?= e(csrf_token()) ?>">
        <div class="mb-3">
          <label for="email" class="form-label small fw-semibold">Email administrateur</label>
          <input type="email" class="form-control" id="email" name="email" value="<?= e($email) ?>" required autofocus>
        </div>
        <div class="mb-4">
          <label for="password" class="form-label small fw-semibold">Mot de passe</label>
          <input type="password" class="form-control" id="password" name="password" required>
        </div>
        <button type="submit" class="btn btn-indigo w-100 py-2 fw-semibold">
          <i class="bi bi-shield-lock me-1"></i> Se connecter
        </button>
      </form>
    </div>
  </div>
</body>
</html>
