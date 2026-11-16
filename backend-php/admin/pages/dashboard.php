<?php
declare(strict_types=1);

require_once dirname(__DIR__) . '/includes/auth.php';
require_once dirname(__DIR__) . '/includes/layout.php';

$admin = require_admin();
$pdo   = admin_db();

// 1. KPIs globaux via requêtes préparées PDO
$kpiStmt = $pdo->query(
    'SELECT
        COUNT(*) AS total_ateliers,
        SUM(CASE WHEN date_expiration IS NOT NULL AND date_expiration >= UTC_TIMESTAMP() THEN 1 ELSE 0 END) AS actifs,
        SUM(CASE WHEN date_expiration IS NULL OR date_expiration < UTC_TIMESTAMP() THEN 1 ELSE 0 END) AS expires
     FROM ateliers'
);
$kpis = $kpiStmt->fetch() ?: ['total_ateliers' => 0, 'actifs' => 0, 'expires' => 0];

$revStmt = $pdo->prepare(
    'SELECT COALESCE(SUM(montant_fcfa), 0) AS revenus_mois
     FROM licences_codes
     WHERE YEAR(created_at) = :yr AND MONTH(created_at) = :mo'
);
$revStmt->execute([
    ':yr' => (int) gmdate('Y'),
    ':mo' => (int) gmdate('n'),
]);
$revenusMois = (int) ($revStmt->fetchColumn() ?: 0);

// 2. Licences expirant dans les 5 prochains jours
$expiringStmt = $pdo->query(
    'SELECT id, code_atelier, nom_atelier, telephone, plan_abonnement, date_expiration
     FROM ateliers
     WHERE date_expiration IS NOT NULL
       AND date_expiration >= UTC_TIMESTAMP()
       AND date_expiration <= DATE_ADD(UTC_TIMESTAMP(), INTERVAL 5 DAY)
     ORDER BY date_expiration ASC'
);
$expiringSoon = $expiringStmt->fetchAll();

// 3. Derniers paiements / codes générés
$recentStmt = $pdo->query(
    'SELECT lc.code_genere, lc.formule, lc.montant_fcfa, lc.mode_paiement, lc.created_at,
            a.nom_atelier, a.code_atelier, a.id AS atelier_id
     FROM licences_codes lc
     INNER JOIN ateliers a ON a.id = lc.atelier_id
     ORDER BY lc.created_at DESC
     LIMIT 6'
);
$recentCodes = $recentStmt->fetchAll();

render_admin_header('Tableau de bord', 'dashboard', $admin);
?>

<div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-4">
  <div>
    <h1 class="h3 fw-bold mb-1">Tableau de bord</h1>
    <p class="text-muted small mb-0">Suivi des licences, échéances WhatsApp et revenus AtelierPro</p>
  </div>
  <a href="generer_code.php" class="btn btn-indigo">
    <i class="bi bi-key-fill me-1"></i> Générer un code d'activation
  </a>
</div>

<!-- 4 Cartes KPI -->
<div class="row g-3 mb-4">
  <div class="col-6 col-xl-3">
    <div class="kpi-card p-3 h-100">
      <div class="text-muted small mb-1">Total ateliers</div>
      <div class="fs-3 fw-bold"><?= (int) $kpis['total_ateliers'] ?></div>
      <div class="small text-muted">Comptes enregistrés</div>
    </div>
  </div>
  <div class="col-6 col-xl-3">
    <div class="kpi-card p-3 h-100">
      <div class="text-muted small mb-1">Ateliers actifs</div>
      <div class="fs-3 fw-bold text-success"><?= (int) $kpis['actifs'] ?></div>
      <div class="small text-muted">Licence en cours de validité</div>
    </div>
  </div>
  <div class="col-6 col-xl-3">
    <div class="kpi-card p-3 h-100">
      <div class="text-muted small mb-1">Comptes expirés</div>
      <div class="fs-3 fw-bold text-danger"><?= (int) $kpis['expires'] ?></div>
      <div class="small text-muted">À relancer / renouveler</div>
    </div>
  </div>
  <div class="col-6 col-xl-3">
    <div class="kpi-card p-3 h-100">
      <div class="text-muted small mb-1">Revenus du mois</div>
      <div class="fs-3 fw-bold" style="color:#1E3A5F;"><?= e(format_fcfa($revenusMois)) ?></div>
      <div class="small text-muted"><?= e(gmdate('m/Y')) ?> (Orange / Moov / Espèces)</div>
    </div>
  </div>
</div>

<!-- Licences expirant dans les 5 prochains jours -->
<div class="card-clean mb-4">
  <div class="p-3 border-bottom d-flex flex-wrap justify-content-between align-items-center gap-2">
    <div>
      <h2 class="h6 fw-bold mb-0">
        <i class="bi bi-exclamation-triangle-fill text-warning me-1"></i>
        Licences expirant dans les 5 prochains jours
      </h2>
    </div>
    <span class="badge text-bg-warning"><?= count($expiringSoon) ?> atelier(s)</span>
  </div>
  <div class="table-responsive">
    <table class="table table-hover align-middle mb-0">
      <thead class="table-light small text-muted">
        <tr>
          <th>Atelier</th>
          <th>Code Atelier</th>
          <th>Téléphone</th>
          <th>Expiration (UTC)</th>
          <th>Reste</th>
          <th class="text-end">Actions</th>
        </tr>
      </thead>
      <tbody>
        <?php if ($expiringSoon === []): ?>
          <tr>
            <td colspan="6" class="text-center text-muted py-4">
              Aucune licence n'expire dans les 5 prochains jours.
            </td>
          </tr>
        <?php else: ?>
          <?php foreach ($expiringSoon as $row): ?>
            <?php
              $st = atelier_status($row['date_expiration']);
              $codeAt = $row['code_atelier'] ?: 'ATEL-' . $row['id'];
              $msg = "Bonjour {$row['nom_atelier']}, votre licence AtelierPro ({$codeAt}) expire le "
                   . substr((string) $row['date_expiration'], 0, 10)
                   . " (dans {$st['days_left']} jour(s)). Souhaitez-vous renouveler pour 30 jours (3 500 FCFA) ou 1 an (35 000 FCFA) ?";
              $waHref = wa_url((string) $row['telephone'], $msg);
            ?>
            <tr>
              <td class="fw-semibold">
                <a href="atelier_details.php?id=<?= (int) $row['id'] ?>" class="text-decoration-none text-dark">
                  <?= e($row['nom_atelier']) ?>
                </a>
              </td>
              <td class="mono small"><?= e($codeAt) ?></td>
              <td class="mono small"><?= e($row['telephone']) ?></td>
              <td class="mono small"><?= e(substr((string) $row['date_expiration'], 0, 16)) ?></td>
              <td><span class="badge text-bg-warning"><?= $st['days_left'] ?> j</span></td>
              <td class="text-end text-nowrap">
                <a href="<?= e($waHref) ?>" target="_blank" rel="noopener noreferrer" class="btn btn-success btn-sm">
                  <i class="bi bi-whatsapp me-1"></i> Relancer
                </a>
                <a href="generer_code.php?atelier_id=<?= (int) $row['id'] ?>" class="btn btn-outline-primary btn-sm ms-1">
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

<!-- Derniers codes & paiements -->
<div class="card-clean">
  <div class="p-3 border-bottom d-flex justify-content-between align-items-center">
    <h2 class="h6 fw-bold mb-0">Dernières activations enregistrées</h2>
    <a href="ateliers.php" class="small text-decoration-none">Voir tous les ateliers →</a>
  </div>
  <div class="table-responsive">
    <table class="table table-hover align-middle mb-0">
      <thead class="table-light small text-muted">
        <tr>
          <th>Date</th>
          <th>Atelier</th>
          <th>Code généré</th>
          <th>Formule</th>
          <th>Paiement</th>
          <th class="text-end">Montant</th>
        </tr>
      </thead>
      <tbody>
        <?php if ($recentCodes === []): ?>
          <tr>
            <td colspan="6" class="text-center text-muted py-4">Aucun code généré pour le moment.</td>
          </tr>
        <?php else: ?>
          <?php foreach ($recentCodes as $c): ?>
            <tr>
              <td class="mono small"><?= e(substr((string) $c['created_at'], 0, 16)) ?></td>
              <td>
                <a href="atelier_details.php?id=<?= (int) $c['atelier_id'] ?>" class="text-decoration-none fw-semibold text-dark">
                  <?= e($c['nom_atelier']) ?>
                </a>
                <span class="mono small text-muted ms-1">(<?= e($c['code_atelier']) ?>)</span>
              </td>
              <td class="mono fw-bold text-primary"><?= e($c['code_genere']) ?></td>
              <td><span class="badge text-bg-light border"><?= e($c['formule']) ?></span></td>
              <td class="small"><?= e($c['mode_paiement']) ?></td>
              <td class="text-end mono fw-semibold"><?= e(format_fcfa($c['montant_fcfa'])) ?></td>
            </tr>
          <?php endforeach; ?>
        <?php endif; ?>
      </tbody>
    </table>
  </div>
</div>

<?php render_admin_footer(); ?>
