<?php
declare(strict_types=1);

function render_admin_header(string $pageTitle, string $activeNav, array $admin): void
{
    $flash = pop_flash();
    $navItems = [
        ['key' => 'dashboard', 'href' => 'dashboard.php',    'icon' => 'bi-speedometer2', 'label' => 'Tableau de bord'],
        ['key' => 'ateliers',  'href' => 'ateliers.php',     'icon' => 'bi-shop',         'label' => 'Ateliers & Appareils'],
        ['key' => 'generer',   'href' => 'generer_code.php', 'icon' => 'bi-key-fill',     'label' => 'Générer un code'],
    ];
    ?>
<!DOCTYPE html>
<html lang="fr">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title><?= e($pageTitle) ?> — AtelierPro Admin</title>
  <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
  <link href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css" rel="stylesheet">
  <style>
    :root {
      --atelier-indigo: #1E3A5F;
      --atelier-cream: #FAF9F6;
    }
    body { background-color: var(--atelier-cream); color: #1C1917; }
    .sidebar-desktop { width: 256px; min-height: 100vh; background: #ffffff; border-right: 1px solid #E7E5E4; }
    .brand-mark { color: var(--atelier-indigo); font-weight: 700; letter-spacing: -0.02em; }
    .nav-admin .nav-link {
      color: #44403C; border-radius: 10px; padding: 10px 14px; font-weight: 500;
      display: flex; align-items: center; gap: 10px;
    }
    .nav-admin .nav-link:hover { background: #F5F5F4; color: #1C1917; }
    .nav-admin .nav-link.active { background: var(--atelier-indigo); color: #ffffff; }
    .kpi-card { border: 1px solid #E7E5E4; border-radius: 14px; background: #ffffff; }
    .card-clean { border: 1px solid #E7E5E4; border-radius: 14px; background: #ffffff; }
    .btn-indigo { background-color: var(--atelier-indigo); border-color: var(--atelier-indigo); color: #ffffff; }
    .btn-indigo:hover { background-color: #162C49; border-color: #162C49; color: #ffffff; }
    .mono { font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; }
  </style>
</head>
<body>
<div class="d-flex min-vh-100">
  <!-- Barre latérale PC -->
  <aside class="sidebar-desktop d-none d-lg-flex flex-column p-3 sticky-top" style="height: 100vh;">
    <div class="px-2 py-2 mb-3 border-bottom">
      <div class="d-flex align-items-center gap-2">
        <span class="rounded-3 d-inline-flex align-items-center justify-content-center text-white" style="width:36px;height:36px;background:#1E3A5F;">
          <i class="bi bi-scissors"></i>
        </span>
        <div>
          <div class="brand-mark fs-5 lh-1">AtelierPro</div>
          <small class="text-muted">Administration SaaS</small>
        </div>
      </div>
    </div>

    <ul class="nav flex-column gap-1 nav-admin flex-grow-1">
      <?php foreach ($navItems as $item): ?>
        <li class="nav-item">
          <a class="nav-link <?= $activeNav === $item['key'] ? 'active' : '' ?>" href="<?= e($item['href']) ?>">
            <i class="bi <?= e($item['icon']) ?>"></i>
            <span><?= e($item['label']) ?></span>
          </a>
        </li>
      <?php endforeach; ?>
    </ul>

    <div class="border-top pt-3 mt-3">
      <div class="px-2 mb-2">
        <div class="fw-semibold small text-truncate"><?= e($admin['nom']) ?></div>
        <div class="text-muted small text-truncate"><?= e($admin['email']) ?></div>
      </div>
      <form method="post" action="logout.php">
        <input type="hidden" name="csrf_token" value="<?= e(csrf_token()) ?>">
        <button type="submit" class="btn btn-outline-danger btn-sm w-100">
          <i class="bi bi-box-arrow-right me-1"></i> Déconnexion
        </button>
      </form>
    </div>
  </aside>

  <!-- Zone principale -->
  <div class="flex-grow-1 d-flex flex-column min-w-0">
    <!-- Barre supérieure Mobile / Tablette -->
    <header class="d-lg-none navbar bg-white border-bottom sticky-top px-3">
      <button class="btn btn-outline-secondary btn-sm" type="button" data-bs-toggle="offcanvas" data-bs-target="#mobileMenu" aria-label="Menu">
        <i class="bi bi-list fs-5"></i>
      </button>
      <span class="brand-mark fs-5">AtelierPro Admin</span>
      <a href="generer_code.php" class="btn btn-indigo btn-sm"><i class="bi bi-key-fill"></i></a>
    </header>

    <!-- Menu Offcanvas Mobile -->
    <div class="offcanvas offcanvas-start" tabindex="-1" id="mobileMenu">
      <div class="offcanvas-header border-bottom">
        <h5 class="offcanvas-title brand-mark">AtelierPro Admin</h5>
        <button type="button" class="btn-close" data-bs-dismiss="offcanvas" aria-label="Fermer"></button>
      </div>
      <div class="offcanvas-body d-flex flex-column">
        <ul class="nav flex-column gap-1 nav-admin flex-grow-1">
          <?php foreach ($navItems as $item): ?>
            <li class="nav-item">
              <a class="nav-link <?= $activeNav === $item['key'] ? 'active' : '' ?>" href="<?= e($item['href']) ?>">
                <i class="bi <?= e($item['icon']) ?>"></i>
                <span><?= e($item['label']) ?></span>
              </a>
            </li>
          <?php endforeach; ?>
        </ul>
        <div class="border-top pt-3">
          <div class="small fw-semibold mb-2"><?= e($admin['nom']) ?> (<?= e($admin['email']) ?>)</div>
          <form method="post" action="logout.php">
            <input type="hidden" name="csrf_token" value="<?= e(csrf_token()) ?>">
            <button type="submit" class="btn btn-outline-danger btn-sm w-100">Déconnexion</button>
          </form>
        </div>
      </div>
    </div>

    <main class="container-fluid p-3 p-md-4 flex-grow-1" style="max-width: 1320px;">
      <?php if ($flash !== null): ?>
        <div class="alert alert-<?= e($flash['type']) ?> alert-dismissible fade show" role="alert">
          <?= e($flash['message']) ?>
          <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Fermer"></button>
        </div>
      <?php endif; ?>
    <?php
}

function render_admin_footer(): void
{
    ?>
    </main>
  </div>
</div>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
    <?php
}
