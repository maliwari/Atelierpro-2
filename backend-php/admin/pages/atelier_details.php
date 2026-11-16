<?php
declare(strict_types=1);

require_once dirname(__DIR__) . '/includes/auth.php';
require_once dirname(__DIR__) . '/includes/layout.php';

$admin = require_admin();
$pdo   = admin_db();

$atelierId = (int) ($_GET['id'] ?? $_POST['atelier_id'] ?? 0);
if ($atelierId <= 0) {
    set_flash('danger', 'Atelier introuvable.');
    header('Location: ateliers.php');
    exit;
}

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    verify_csrf();
    $action = (string) ($_POST['action'] ?? '');

    if ($action === 'update_quota') {
        $maxApp = max(1, min(50, (int) ($_POST['max_appareils'] ?? 2)));
        $codeAt = strtoupper(trim((string) ($_POST['code_atelier'] ?? '')));
        $stmt = $pdo->prepare(
            'UPDATE ateliers SET max_appareils = :max_app, code_atelier = COALESCE(NULLIF(:code, \'\'), code_atelier) WHERE id = :id'
        );
        $stmt->execute([
            ':max_app' => $maxApp,
            ':code'    => $codeAt,
            ':id'      => $atelierId,
        ]);
        set_flash('success', "Quota mis à jour ({$maxApp} appareil(s) max).");
    } elseif ($action === 'toggle_device') {
        $deviceId = (int) ($_POST['device_id'] ?? 0);
        $nextStatus = ($_POST['next_status'] ?? 'bloque') === 'autorise' ? 'autorise' : 'bloque';
        $stmt = $pdo->prepare('UPDATE appareils SET statut = :statut WHERE id = :did AND atelier_id = :aid');
        $stmt->execute([':statut' => $nextStatus, ':did' => $deviceId, ':aid' => $atelierId]);
        set_flash('success', $nextStatus === 'autorise' ? 'Appareil autorisé.' : 'Appareil bloqué.');
    } elseif ($action === 'delete_device') {
        $deviceId = (int) ($_POST['device_id'] ?? 0);
        $stmt = $pdo->prepare('DELETE FROM appareils WHERE id = :did AND atelier_id = :aid');
        $stmt->execute([':did' => $deviceId, ':aid' => $atelierId]);
        set_flash('success', 'Appareil supprimé (emplacement libéré).');
    }

    header('Location: atelier_details.php?id=' . $atelierId);
    exit;
}

$stmt = $pdo->prepare('SELECT * FROM ateliers WHERE id = :id LIMIT 1');
$stmt->execute([':id' => $atelierId]);
$atelier = $stmt->fetch();

if ($atelier === false) {
    set_flash('danger', 'Atelier inexistant.');
    header('Location: ateliers.php');
    exit;
}

$devStmt = $pdo->prepare('SELECT * FROM appareils WHERE atelier_id = :id ORDER BY last_seen_at DESC');
$devStmt->execute([':id' => $atelierId]);
$appareils = $devStmt->fetchAll();

$histStmt = $pdo->prepare(
    'SELECT lc.*, adm.nom AS admin_nom
     FROM licences_codes lc
     LEFT JOIN admins adm ON adm.id = lc.created_by_admin_id
     WHERE lc.atelier_id = :id
     ORDER BY lc.created_at DESC'
);
$histStmt->execute([':id' => $atelierId]);
$historique = $histStmt->fetchAll();

$st = atelier_status($atelier['date_expiration']);

render_admin_header('Fiche Atelier — ' . $atelier['nom_atelier'], 'ateliers', $admin);
?>

<div class="mb-3">
  <a href="ateliers.php" class="text-decoration-none small text-muted">← Retour à la liste des ateliers</a>
</div>

<div class="d-flex flex-wrap justify-content-between align-items-start gap-3 mb-4">
  <div>
    <div class="d-flex align-items-center gap-2 flex-wrap">
      <h1 class="h3 fw-bold mb-0"><?= e($atelier['nom_atelier']) ?></h1>
      <span class="badge text-bg-<?= e($st['badge']) ?>"><?= e($st['label']) ?></span>
    </div>
    <div class="text-muted small mt-1">
      <span class="mono fw-semibold text-primary"><?= e($atelier['code_atelier'] ?: 'Non défini') ?></span> ·
      <span><?= e($atelier['telephone']) ?></span> ·
      <span><?= e($atelier['email']) ?></span>
    </div>
  </div>
  <div class="d-flex gap-2">
    <a href="generer_code.php?atelier_id=<?= (int) $atelier['id'] ?>" class="btn btn-indigo">
      <i class="bi bi-key-fill me-1"></i> Générer un code / Prolonger
    </a>
  </div>
</div>

<div class="row g-3 mb-4">
  <!-- Informations licence & quota appareils -->
  <div class="col-12 col-lg-5">
    <div class="card-clean p-4 h-100">
      <h2 class="h6 fw-bold mb-3">Licence &amp; Quota d'appareils</h2>
      <dl class="row small mb-3">
        <dt class="col-5 text-muted">Plan actuel</dt>
        <dd class="col-7 fw-semibold text-uppercase"><?= e($atelier['plan_abonnement']) ?></dd>

        <dt class="col-5 text-muted">Date d'expiration</dt>
        <dd class="col-7 mono fw-semibold">
          <?= $atelier['date_expiration'] ? e(substr((string) $atelier['date_expiration'], 0, 16)) : 'Expiré / Aucune' ?>
          <?php if ($st['days_left'] > 0): ?>
            <span class="text-muted">(<?= $st['days_left'] ?> j)</span>
          <?php endif; ?>
        </dd>

        <dt class="col-5 text-muted">Appareils liés</dt>
        <dd class="col-7 mono"><?= count($appareils) ?> / <?= (int) $atelier['max_appareils'] ?></dd>
      </dl>

      <form method="post" class="border-top pt-3 vstack gap-2">
        <input type="hidden" name="csrf_token" value="<?= e(csrf_token()) ?>">
        <input type="hidden" name="atelier_id" value="<?= (int) $atelier['id'] ?>">
        <input type="hidden" name="action" value="update_quota">

        <div class="row g-2">
          <div class="col-6">
            <label class="form-label small fw-semibold mb-1">Code Atelier</label>
            <input type="text" name="code_atelier" class="form-control form-control-sm mono text-uppercase" value="<?= e($atelier['code_atelier'] ?? '') ?>" placeholder="ATEL-XXXX">
          </div>
          <div class="col-6">
            <label class="form-label small fw-semibold mb-1">Quota max_appareils</label>
            <input type="number" name="max_appareils" class="form-control form-control-sm" min="1" max="50" value="<?= (int) $atelier['max_appareils'] ?>" required>
          </div>
        </div>
        <button type="submit" class="btn btn-outline-dark btn-sm mt-1">
          <i class="bi bi-save me-1"></i> Modifier le quota d'appareils
        </button>
      </form>
    </div>
  </div>

  <!-- Appareils enregistrés -->
  <div class="col-12 col-lg-7">
    <div class="card-clean h-100">
      <div class="p-3 border-bottom d-flex justify-content-between align-items-center">
        <h2 class="h6 fw-bold mb-0">Appareils liés (<?= count($appareils) ?> / <?= (int) $atelier['max_appareils'] ?>)</h2>
        <span class="small text-muted">Changement de téléphone : supprimez l'ancien</span>
      </div>
      <div class="table-responsive">
        <table class="table table-hover align-middle mb-0">
          <thead class="table-light small text-muted">
            <tr>
              <th>Appareil</th>
              <th>UUID</th>
              <th>Dernière synchro</th>
              <th>Statut</th>
              <th class="text-end">Actions</th>
            </tr>
          </thead>
          <tbody>
            <?php if ($appareils === []): ?>
              <tr>
                <td colspan="5" class="text-center text-muted py-4">Aucun appareil encore enregistré via l'API.</td>
              </tr>
            <?php else: ?>
              <?php foreach ($appareils as $dev): ?>
                <?php $isAuth = $dev['statut'] === 'autorise'; ?>
                <tr>
                  <td>
                    <div class="fw-semibold small"><?= e($dev['device_name']) ?></div>
                    <div class="text-muted" style="font-size:11px;"><?= e($dev['platform']) ?></div>
                  </td>
                  <td class="mono small text-muted"><?= e(substr((string) $dev['device_uuid'], 0, 14)) ?>…</td>
                  <td class="mono small"><?= e(substr((string) $dev['last_seen_at'], 0, 16)) ?></td>
                  <td>
                    <span class="badge text-bg-<?= $isAuth ? 'success' : 'danger' ?>">
                      <?= $isAuth ? 'Autorisé' : 'Bloqué' ?>
                    </span>
                  </td>
                  <td class="text-end text-nowrap">
                    <form method="post" class="d-inline">
                      <input type="hidden" name="csrf_token" value="<?= e(csrf_token()) ?>">
                      <input type="hidden" name="atelier_id" value="<?= (int) $atelier['id'] ?>">
                      <input type="hidden" name="action" value="toggle_device">
                      <input type="hidden" name="device_id" value="<?= (int) $dev['id'] ?>">
                      <input type="hidden" name="next_status" value="<?= $isAuth ? 'bloque' : 'autorise' ?>">
                      <button type="submit" class="btn btn-sm btn-outline-<?= $isAuth ? 'warning' : 'success' ?>">
                        <?= $isAuth ? 'Bloquer' : 'Autoriser' ?>
                      </button>
                    </form>
                    <form method="post" class="d-inline ms-1" onsubmit="return confirm('Supprimer cet appareil ?');">
                      <input type="hidden" name="csrf_token" value="<?= e(csrf_token()) ?>">
                      <input type="hidden" name="atelier_id" value="<?= (int) $atelier['id'] ?>">
                      <input type="hidden" name="action" value="delete_device">
                      <input type="hidden" name="device_id" value="<?= (int) $dev['id'] ?>">
                      <button type="submit" class="btn btn-sm btn-outline-danger" title="Supprimer l'appareil">
                        <i class="bi bi-trash"></i>
                      </button>
                    </form>
                  </td>
                </tr>
              <?php endforeach; ?>
            <?php endif; ?>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</div>

<!-- Historique des codes générés -->
<div class="card-clean">
  <div class="p-3 border-bottom">
    <h2 class="h6 fw-bold mb-0">Historique des codes &amp; paiements de cet atelier</h2>
  </div>
  <div class="table-responsive">
    <table class="table table-hover align-middle mb-0">
      <thead class="table-light small text-muted">
        <tr>
          <th>Date</th>
          <th>Code généré</th>
          <th>Formule</th>
          <th>Mode paiement</th>
          <th>Référence</th>
          <th class="text-end">Montant</th>
        </tr>
      </thead>
      <tbody>
        <?php if ($historique === []): ?>
          <tr>
            <td colspan="6" class="text-center text-muted py-4">Aucun paiement enregistré pour cet atelier.</td>
          </tr>
        <?php else: ?>
          <?php foreach ($historique as $h): ?>
            <tr>
              <td class="mono small"><?= e(substr((string) $h['created_at'], 0, 16)) ?></td>
              <td class="mono fw-bold text-primary"><?= e($h['code_genere']) ?></td>
              <td><span class="badge text-bg-light border"><?= e($h['formule']) ?> (+<?= (int) $h['jours_ajoutes'] ?> j)</span></td>
              <td class="small"><?= e($h['mode_paiement']) ?></td>
              <td class="mono small"><?= e($h['reference_transaction'] ?: '—') ?></td>
              <td class="text-end mono fw-semibold"><?= e(format_fcfa($h['montant_fcfa'])) ?></td>
            </tr>
          <?php endforeach; ?>
        <?php endif; ?>
      </tbody>
    </table>
  </div>
</div>

<?php render_admin_footer(); ?>
