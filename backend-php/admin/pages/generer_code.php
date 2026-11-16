<?php
declare(strict_types=1);

require_once dirname(__DIR__) . '/includes/auth.php';
require_once dirname(__DIR__) . '/includes/layout.php';

$admin = require_admin();
$pdo   = admin_db();

$preselectedId = (int) ($_GET['atelier_id'] ?? $_POST['atelier_id'] ?? 0);
$generatedResult = null;
$error = null;

$ateliersStmt = $pdo->query(
    'SELECT id, code_atelier, nom_atelier, telephone, date_expiration
     FROM ateliers
     ORDER BY nom_atelier ASC'
);
$ateliersList = $ateliersStmt->fetchAll();

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    verify_csrf();

    $atelierId   = (int) ($_POST['atelier_id'] ?? 0);
    $formule     = strtoupper(trim((string) ($_POST['formule'] ?? '')));
    $modePaiement = trim((string) ($_POST['mode_paiement'] ?? ''));
    $reference   = trim((string) ($_POST['reference_transaction'] ?? ''));

    if ($atelierId <= 0 || !isset(LICENSE_FORMULAS[$formule])) {
        $error = 'Veuillez sélectionner un atelier et une formule valide (M1, M3 ou A1).';
    } elseif (!in_array($modePaiement, PAYMENT_METHODS, true)) {
        $error = 'Mode de paiement invalide.';
    } else {
        $formulaInfo = LICENSE_FORMULAS[$formule];
        $daysToAdd   = (int) $formulaInfo['days'];
        $montantFcfa = (int) $formulaInfo['price_fcfa'];
        $moisAnnee   = gmdate('Y-m');

        try {
            $pdo->beginTransaction();

            $sel = $pdo->prepare('SELECT * FROM ateliers WHERE id = :id FOR UPDATE');
            $sel->execute([':id' => $atelierId]);
            $atelier = $sel->fetch();

            if ($atelier === false) {
                throw new RuntimeException('Atelier introuvable.');
            }

            $codeAtelier = strtoupper(trim((string) ($atelier['code_atelier'] ?? '')));
            if ($codeAtelier === '') {
                $codeAtelier = generate_workshop_code();
            }

            // Calcul unique FORMULE-HMAC6 basé sur HMAC-SHA256(code_atelier-formule-YYYY-MM, CLE_SECRETE)
            $codeGenere = compute_license_code($codeAtelier, $formule, $moisAnnee);

            // Prolongation de date_expiration à partir de max(now, date_expiration)
            $upd = $pdo->prepare(
                'UPDATE ateliers
                 SET code_atelier    = :code_atelier,
                     plan_abonnement = :plan,
                     date_expiration = TIMESTAMPADD(DAY, :jours, GREATEST(COALESCE(date_expiration, UTC_TIMESTAMP()), UTC_TIMESTAMP()))
                 WHERE id = :id'
            );
            $upd->execute([
                ':code_atelier' => $codeAtelier,
                ':plan'         => $formule === 'A1' ? 'atelier_plus' : 'pro',
                ':jours'        => $daysToAdd,
                ':id'           => $atelierId,
            ]);

            $ins = $pdo->prepare(
                'INSERT INTO licences_codes (
                    atelier_id, code_atelier, code_genere, formule, jours_ajoutes,
                    montant_fcfa, mode_paiement, reference_transaction, mois_annee, created_by_admin_id
                 ) VALUES (
                    :atelier_id, :code_atelier, :code_genere, :formule, :jours,
                    :montant, :mode, :ref, :mois, :admin_id
                 )'
            );
            $ins->execute([
                ':atelier_id'   => $atelierId,
                ':code_atelier' => $codeAtelier,
                ':code_genere'  => $codeGenere,
                ':formule'      => $formule,
                ':jours'        => $daysToAdd,
                ':montant'      => $montantFcfa,
                ':mode'         => $modePaiement,
                ':ref'          => $reference,
                ':mois'         => $moisAnnee,
                ':admin_id'     => (int) $admin['id'],
            ]);

            $afterStmt = $pdo->prepare('SELECT date_expiration FROM ateliers WHERE id = :id');
            $afterStmt->execute([':id' => $atelierId]);
            $newExp = (string) $afterStmt->fetchColumn();

            $pdo->commit();

            $waMsg = "Bonjour {$atelier['nom_atelier']},\n"
                   . "Merci pour votre paiement ({$formulaInfo['label']} - " . format_fcfa($montantFcfa) . ").\n"
                   . "Voici votre code d'activation AtelierPro ({$codeAtelier}) :\n\n"
                   . "{$codeGenere}\n\n"
                   . "Nouvelle expiration : " . substr($newExp, 0, 10) . ".\n"
                   . "Saisissez ce code dans l'application (Menu Abonnement).";

            $generatedResult = [
                'atelier_id'      => $atelierId,
                'nom_atelier'     => $atelier['nom_atelier'],
                'telephone'       => $atelier['telephone'],
                'code_atelier'    => $codeAtelier,
                'code_genere'     => $codeGenere,
                'formule_label'   => $formulaInfo['label'],
                'montant_fcfa'    => $montantFcfa,
                'new_expiration'  => substr($newExp, 0, 10),
                'whatsapp_url'    => wa_url((string) $atelier['telephone'], $waMsg),
            ];
        } catch (Throwable $e) {
            if ($pdo->inTransaction()) {
                $pdo->rollBack();
            }
            $error = 'Erreur lors de la génération du code : ' . $e->getMessage();
        }
    }
}

render_admin_header('Générateur de codes & Paiements', 'generer', $admin);
?>

<div class="mb-4">
  <h1 class="h3 fw-bold mb-1">Générateur de codes &amp; Paiements</h1>
  <p class="text-muted small mb-0">Enregistrez un règlement et générez le code HMAC-SHA256 à envoyer sur WhatsApp</p>
</div>

<?php if ($error !== null): ?>
  <div class="alert alert-danger"><?= e($error) ?></div>
<?php endif; ?>

<div class="row g-4">
  <div class="col-12 col-lg-6">
    <form method="post" class="card-clean p-4 vstack gap-3">
      <input type="hidden" name="csrf_token" value="<?= e(csrf_token()) ?>">

      <div>
        <label for="atelier_id" class="form-label small fw-semibold">Atelier client *</label>
        <select name="atelier_id" id="atelier_id" class="form-select" required>
          <option value="">-- Sélectionner un atelier --</option>
          <?php foreach ($ateliersList as $a): ?>
            <?php
              $id = (int) $a['id'];
              $labelCode = $a['code_atelier'] ?: 'Sans code';
              $expShort = $a['date_expiration'] ? substr((string) $a['date_expiration'], 0, 10) : 'Expiré';
            ?>
            <option value="<?= $id ?>" <?= $preselectedId === $id ? 'selected' : '' ?>>
              <?= e($a['nom_atelier']) ?> (<?= e($labelCode) ?> · exp. <?= e($expShort) ?>)
            </option>
          <?php endforeach; ?>
        </select>
      </div>

      <div>
        <label class="form-label small fw-semibold">Formule d'abonnement *</label>
        <div class="vstack gap-2">
          <?php foreach (LICENSE_FORMULAS as $key => $f): ?>
            <label class="border rounded-3 p-2 d-flex justify-content-between align-items-center" style="cursor:pointer;">
              <span class="d-flex align-items-center gap-2">
                <input type="radio" name="formule" value="<?= e($key) ?>" class="form-check-input mt-0" <?= $key === 'M1' ? 'checked' : '' ?> required>
                <span class="fw-semibold"><?= e($key) ?> — <?= e($f['label']) ?></span>
              </span>
              <span class="mono fw-bold text-primary"><?= e(format_fcfa($f['price_fcfa'])) ?></span>
            </label>
          <?php endforeach; ?>
        </div>
      </div>

      <div class="row g-2">
        <div class="col-12 col-sm-6">
          <label for="mode_paiement" class="form-label small fw-semibold">Mode de paiement *</label>
          <select name="mode_paiement" id="mode_paiement" class="form-select" required>
            <?php foreach (PAYMENT_METHODS as $m): ?>
              <option value="<?= e($m) ?>"><?= e($m) ?></option>
            <?php endforeach; ?>
          </select>
        </div>
        <div class="col-12 col-sm-6">
          <label for="reference_transaction" class="form-label small fw-semibold">Référence transaction</label>
          <input type="text" name="reference_transaction" id="reference_transaction" class="form-control mono" placeholder="Ex : OM-261116-8421">
        </div>
      </div>

      <button type="submit" class="btn btn-indigo py-2 fw-semibold">
        <i class="bi bi-key-fill me-1"></i> Valider le paiement &amp; Générer le code
      </button>
    </form>
  </div>

  <div class="col-12 col-lg-6">
    <?php if ($generatedResult !== null): ?>
      <div class="card-clean p-4 border-success">
        <div class="d-flex align-items-center gap-2 text-success mb-2">
          <i class="bi bi-check-circle-fill fs-5"></i>
          <h2 class="h5 fw-bold mb-0">Code généré &amp; licence prolongée !</h2>
        </div>
        <p class="small text-muted mb-3">
          Atelier : <strong><?= e($generatedResult['nom_atelier']) ?></strong>
          (<span class="mono"><?= e($generatedResult['code_atelier']) ?></span>) ·
          Nouvelle expiration : <strong class="mono"><?= e($generatedResult['new_expiration']) ?></strong>
        </p>

        <div class="p-3 bg-light border rounded-3 text-center mb-3">
          <div class="small text-muted mb-1">Code d'activation hors-ligne (<?= e($generatedResult['formule_label']) ?>)</div>
          <div id="codeBox" class="mono fs-2 fw-bold text-dark user-select-all"><?= e($generatedResult['code_genere']) ?></div>
        </div>

        <div class="d-grid gap-2">
          <a href="<?= e($generatedResult['whatsapp_url']) ?>" target="_blank" rel="noopener noreferrer" class="btn btn-success py-2 fw-semibold">
            <i class="bi bi-whatsapp me-1"></i> Envoyer le code sur WhatsApp
          </a>
          <button type="button" class="btn btn-outline-secondary btn-sm" onclick="navigator.clipboard?.writeText(document.getElementById('codeBox').innerText)">
            <i class="bi bi-clipboard me-1"></i> Copier le code
          </button>
          <a href="atelier_details.php?id=<?= (int) $generatedResult['atelier_id'] ?>" class="btn btn-link btn-sm text-decoration-none">
            Voir la fiche de l'atelier →
          </a>
        </div>
      </div>
    <?php else: ?>
      <div class="card-clean p-4 text-muted small">
        <h2 class="h6 fw-bold text-dark mb-2">Fonctionnement du générateur HMAC-SHA256</h2>
        <ul class="mb-0 vstack gap-2">
          <li>Le code suit exactement la formule <span class="mono">FORMULE-HMAC6</span> attendue par l'application Android hors-ligne.</li>
          <li>La signature est calculée pour le mois UTC en cours (<span class="mono"><?= e(gmdate('Y-m')) ?></span>) avec le <span class="mono">code_atelier</span>.</li>
          <li>La validation enregistre la transaction dans <span class="mono">licences_codes</span> et prolonge automatiquement <span class="mono">date_expiration</span> dans MySQL.</li>
        </ul>
      </div>
    <?php endif; ?>
  </div>
</div>

<?php render_admin_footer(); ?>
