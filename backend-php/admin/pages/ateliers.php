<?php
declare(strict_types=1);

require_once dirname(__DIR__) . '/includes/auth.php';
require_once dirname(__DIR__) . '/includes/layout.php';

$admin = require_admin();
$pdo   = admin_db();

// Création manuelle d'un atelier (pour clients WhatsApp 100% hors-ligne)
if ($_SERVER['REQUEST_METHOD'] === 'POST' && ($_POST['action'] ?? '') === 'create_atelier') {
    verify_csrf();
    $nom         = trim((string) ($_POST['nom_atelier'] ?? ''));
    $telephone   = trim((string) ($_POST['telephone'] ?? ''));
    $email       = strtolower(trim((string) ($_POST['email'] ?? '')));
    $codeAtelier = strtoupper(trim((string) ($_POST['code_atelier'] ?? '')));
    $maxApp      = max(1, min(20, (int) ($_POST['max_appareils'] ?? 2)));

    if ($codeAtelier === '') {
        $codeAtelier = generate_workshop_code();
    }
    if ($email === '') {
        $email = strtolower($codeAtelier) . '@offline.atelierpro.local';
    }

    if (mb_strlen($nom) < 2 || $telephone === '') {
        set_flash('danger', 'Le nom de l’atelier et le numéro WhatsApp sont obligatoires.');
    } else {
        try {
            $stmt = $pdo->prepare(
                'INSERT INTO ateliers (code_atelier, nom_atelier, telephone, email, password_hash, plan_abonnement, max_appareils, date_expiration)
                 VALUES (:code, :nom, :tel, :email, :hash, :plan, :max_app, DATE_ADD(UTC_TIMESTAMP(), INTERVAL 14 DAY))'
            );
            $stmt->execute([
                ':code'    => $codeAtelier,
                ':nom'     => $nom,
                ':tel'     => $telephone,
                ':email'   => $email,
                ':hash'    => password_hash(bin2hex(random_bytes(16)), PASSWORD_BCRYPT),
                ':plan'    => 'free',
                ':max_app' => $maxApp,
            ]);
            set_flash('success', "Atelier « {$nom} » créé avec le code {$codeAtelier} (14 jours d'essai).");
            header('Location: atelier_details.php?id=' . (int) $pdo->lastInsertId());
            exit;
        } catch (Throwable $e) {
            set_flash('danger', 'Impossible de créer cet atelier (email ou code_atelier déjà existant).');
        }
    }
}

$search = trim((string) ($_GET['q'] ?? ''));
$statusFilter = (string) ($_GET['statut'] ?? 'tous');

$where  = [];
$params = [];

if ($search !== '') {
    $where[] = '(a.nom_atelier LIKE :q1 OR a.telephone LIKE :q2 OR COALESCE(a.code_atelier, \'\') LIKE :q3 OR a.email LIKE :q4)';
    $like = '%' . $search . '%';
    $params[':q1'] = $like;
    $params[':q2'] = $like;
    $params[':q3'] = $like;
    $params[':q4'] = $like;
}

if ($statusFilter === 'actifs') {
    $where[] = 'a.date_expiration IS NOT NULL AND a.date_expiration >= UTC_TIMESTAMP()';
} elseif ($statusFilter === 'bientot') {
    $where[] = 'a.date_expiration IS NOT NULL AND a.date_expiration >= UTC_TIMESTAMP() AND a.date_expiration <= DATE_ADD(UTC_TIMESTAMP(), INTERVAL 5 DAY)';
} elseif ($statusFilter === 'expires') {
    $where[] = '(a.date_expiration IS NULL OR a.date_expiration < UTC_TIMESTAMP())';
}

$sql = 'SELECT a.id, a.code_atelier, a.nom_atelier, a.telephone, a.email, a.plan_abonnement,
               a.max_appareils, a.date_expiration,
               (SELECT COUNT(*) FROM appareils ap WHERE ap.atelier_id = a.id AND ap.statut = \'autorise\') AS devices_actifs
        FROM ateliers a';
if ($where !== []) {
    $sql .= ' WHERE ' . implode(' AND ', $where);
}
$sql .= ' ORDER BY a.created_at DESC';

$stmt = $pdo->prepare($sql);
$stmt->execute($params);
$ateliers = $stmt->fetchAll();

render_admin_header('Ateliers & Appareils', 'ateliers', $admin);
?>

<div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-4">
  <div>
    <h1 class="h3 fw-bold mb-1">Ateliers &amp; Appareils</h1>
    <p class="text-muted small mb-0"><?= count($ateliers) ?> atelier(s) trouvé(s)</p>
  </div>
  <button class="btn btn-indigo" type="button" data-bs-toggle="modal" data-bs-target="#newAtelierModal">
    <i class="bi bi-plus-lg me-1"></i> Ajouter un atelier
  </button>
</div>

<!-- Recherche & Filtres -->
<form method="get" class="card-clean p-3 mb-4">
  <div class="row g-2 align-items-end">
    <div class="col-12 col-md-6">
      <label for="q" class="form-label small fw-semibold mb-1">Rechercher (nom, téléphone, code_atelier)</label>
      <input type="text" class="form-control" id="q" name="q" value="<?= e($search) ?>" placeholder="Ex : ATEL-7K4M, +22376..., Couture">
    </div>
    <div class="col-12 col-md-4">
      <label for="statut" class="form-label small fw-semibold mb-1">Statut licence</label>
      <select class="form-select" id="statut" name="statut">
        <option value="tous" <?= $statusFilter === 'tous' ? 'selected' : '' ?>>Tous les statuts</option>
        <option value="actifs" <?= $statusFilter === 'actifs' ? 'selected' : '' ?>>Actifs uniquement</option>
        <option value="bientot" <?= $statusFilter === 'bientot' ? 'selected' : '' ?>>Expire dans ≤ 5 jours</option>
        <option value="expires" <?= $statusFilter === 'expires' ? 'selected' : '' ?>>Expirés uniquement</option>
      </select>
    </div>
    <div class="col-12 col-md-2 d-flex gap-2">
      <button type="submit" class="btn btn-indigo w-100">Filtrer</button>
      <?php if ($search !== '' || $statusFilter !== 'tous'): ?>
        <a href="ateliers.php" class="btn btn-outline-secondary" title="Réinitialiser"><i class="bi bi-x-lg"></i></a>
      <?php endif; ?>
    </div>
  </div>
</form>

<!-- Liste des ateliers -->
<div class="card-clean">
  <div class="table-responsive">
    <table class="table table-hover align-middle mb-0">
      <thead class="table-light small text-muted">
        <tr>
          <th>Code Atelier</th>
          <th>Nom de l'atelier</th>
          <th>Téléphone</th>
          <th>Appareils</th>
          <th>Expiration</th>
          <th>Statut</th>
          <th class="text-end">Actions</th>
        </tr>
      </thead>
      <tbody>
        <?php if ($ateliers === []): ?>
          <tr>
            <td colspan="7" class="text-center text-muted py-4">Aucun atelier ne correspond à votre recherche.</td>
          </tr>
        <?php else: ?>
          <?php foreach ($ateliers as $a): ?>
            <?php $st = atelier_status($a['date_expiration']); ?>
            <tr>
              <td class="mono fw-bold text-primary"><?= e($a['code_atelier'] ?: '—') ?></td>
              <td>
                <div class="fw-semibold"><?= e($a['nom_atelier']) ?></div>
                <div class="small text-muted"><?= e($a['email']) ?></div>
              </td>
              <td class="mono small"><?= e($a['telephone']) ?></td>
              <td class="mono small">
                <span class="fw-semibold"><?= (int) $a['devices_actifs'] ?></span> / <?= (int) $a['max_appareils'] ?>
              </td>
              <td class="mono small">
                <?= $a['date_expiration'] ? e(substr((string) $a['date_expiration'], 0, 10)) : '—' ?>
              </td>
              <td><span class="badge text-bg-<?= e($st['badge']) ?>"><?= e($st['label']) ?></span></td>
              <td class="text-end text-nowrap">
                <a href="atelier_details.php?id=<?= (int) $a['id'] ?>" class="btn btn-outline-dark btn-sm">
                  <i class="bi bi-laptop me-1"></i> Gérer
                </a>
                <a href="generer_code.php?atelier_id=<?= (int) $a['id'] ?>" class="btn btn-indigo btn-sm ms-1">
                  <i class="bi bi-key"></i>
                </a>
              </td>
            </tr>
          <?php endforeach; ?>
        <?php endif; ?>
      </tbody>
    </table>
  </div>
</div>

<!-- Modal Nouvel Atelier -->
<div class="modal fade" id="newAtelierModal" tabindex="-1" aria-hidden="true">
  <div class="modal-dialog">
    <form method="post" class="modal-content">
      <input type="hidden" name="csrf_token" value="<?= e(csrf_token()) ?>">
      <input type="hidden" name="action" value="create_atelier">
      <div class="modal-header">
        <h5 class="modal-title fw-bold">Enregistrer un atelier</h5>
        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Fermer"></button>
      </div>
      <div class="modal-body vstack gap-3">
        <div>
          <label class="form-label small fw-semibold">Nom de l'atelier *</label>
          <input type="text" name="nom_atelier" class="form-control" required placeholder="Ex : Atelier Étoile Bamako">
        </div>
        <div>
          <label class="form-label small fw-semibold">Téléphone WhatsApp *</label>
          <input type="text" name="telephone" class="form-control" required placeholder="Ex : +22376112233">
        </div>
        <div class="row g-2">
          <div class="col-7">
            <label class="form-label small fw-semibold">Code Atelier (ATEL-XXXX)</label>
            <input type="text" name="code_atelier" class="form-control mono text-uppercase" placeholder="Auto-généré si vide">
          </div>
          <div class="col-5">
            <label class="form-label small fw-semibold">Quota appareils</label>
            <input type="number" name="max_appareils" class="form-control" min="1" max="20" value="2">
          </div>
        </div>
        <div>
          <label class="form-label small fw-semibold">Email (optionnel si hors-ligne)</label>
          <input type="email" name="email" class="form-control" placeholder="client@atelier.com">
        </div>
      </div>
      <div class="modal-footer">
        <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Annuler</button>
        <button type="submit" class="btn btn-indigo">Créer l'atelier</button>
      </div>
    </form>
  </div>
</div>

<?php render_admin_footer(); ?>
