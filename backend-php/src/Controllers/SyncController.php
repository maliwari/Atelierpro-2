<?php
declare(strict_types=1);

namespace App\Controllers;

use App\Config\Database;
use App\Http\Response;
use App\Middleware\AuthMiddleware;
use App\Security\RateLimiter;
use DateTimeImmutable;
use DateTimeZone;
use PDO;
use Throwable;

final class SyncController
{
    private const ALLOWED_STATUSES = ['En attente', 'Coupe', 'Couture', 'Essayage', 'Prêt', 'Livré'];
    private const ALLOWED_PAY_MODES = ['fixe', 'piece', 'commission'];

    /**
     * POST /api/sync/push
     * Transactional batch UPSERT based on (atelier_id, uuid_local) with Last-Write-Wins on updated_at.
     */
    public function push(): never
    {
        $atelier   = AuthMiddleware::requireAtelier();
        $atelierId = (int) $atelier['id'];
        $pdo       = Database::getConnection();
        RateLimiter::enforceApi($pdo, 'api_sync', 'atelier:' . $atelierId, 120, 60);

        $payload   = Response::readJsonBody();

        $clients   = $this->requireArrayField($payload, 'clients');
        $modeles   = $this->requireArrayField($payload, 'modeles');
        $mercerie  = $this->requireArrayField($payload, 'mercerie');
        $employes  = $this->requireArrayField($payload, 'employes');
        $commandes = $this->requireArrayField($payload, 'commandes');
        $depenses  = $this->requireArrayField($payload, 'depenses');
        $recipes   = $this->requireArrayField($payload, 'modele_mercerie');

        $synced = [
            'clients'         => [],
            'modeles'         => [],
            'mercerie'        => [],
            'employes'        => [],
            'commandes'       => [],
            'depenses'        => [],
            'modele_mercerie' => [],
        ];

        try {
            $pdo->beginTransaction();

            $synced['clients']   = $this->upsertClients($pdo, $atelierId, $clients);
            $synced['mercerie']  = $this->upsertMercerie($pdo, $atelierId, $mercerie);
            $synced['modeles']   = $this->upsertModeles($pdo, $atelierId, $modeles);
            $synced['employes']  = $this->upsertEmployes($pdo, $atelierId, $employes);
            $synced['commandes'] = $this->upsertCommandes($pdo, $atelierId, $commandes);
            $synced['depenses']  = $this->upsertDepenses($pdo, $atelierId, $depenses);

            if ($recipes !== []) {
                $synced['modele_mercerie'] = $this->upsertModeleMercerie($pdo, $atelierId, $recipes);
            }

            $licenceSynced = false;
            if (!empty($payload['licence']) && is_array($payload['licence'])) {
                $licenceSynced = $this->upsertLicence($pdo, $atelierId, $payload['licence']);
            }
            $syncedCodes = $this->upsertUsedCodes($pdo, $atelierId, $this->requireArrayField($payload, 'codes_utilises'));

            $pdo->commit();
        } catch (Throwable $e) {
            if ($pdo->inTransaction()) {
                $pdo->rollBack();
            }
            if ($e instanceof \InvalidArgumentException) {
                Response::error($e->getMessage(), 422);
            }
            Response::error('Erreur lors de la synchronisation transactionnelle.', 500);
        }

        Response::json([
            'success'        => true,
            'server_time'    => gmdate('Y-m-d H:i:s'),
            'synced_uuids'   => $synced,
            'licence_synced' => $licenceSynced,
            'synced_codes'   => $syncedCodes,
        ], 200);
    }

    /**
     * GET /api/sync/pull?last_sync=YYYY-MM-DD HH:MM:SS
     * Returns all records modified on the server strictly after last_sync for the authenticated workshop.
     */
    public function pull(): never
    {
        $atelier   = AuthMiddleware::requireAtelier();
        $atelierId = (int) $atelier['id'];
        $pdo       = Database::getConnection();
        RateLimiter::enforceApi($pdo, 'api_sync', 'atelier:' . $atelierId, 120, 60);

        $rawLastSync = trim((string) ($_GET['last_sync'] ?? '1970-01-01 00:00:00'));
        $dt = DateTimeImmutable::createFromFormat('Y-m-d H:i:s', $rawLastSync, new DateTimeZone('UTC'));
        if ($dt === false || $dt->format('Y-m-d H:i:s') !== $rawLastSync) {
            Response::error('Paramètre last_sync invalide. Format attendu : YYYY-MM-DD HH:MM:SS', 422);
        }
        $lastSync = $dt->format('Y-m-d H:i:s');

        $serverTime = gmdate('Y-m-d H:i:s');

        $clients = $this->fetchSince(
            $pdo,
            'SELECT uuid_local, nom, telephone, adresse, notes, updated_at, deleted_at
             FROM clients WHERE atelier_id = :atelier_id AND updated_at > :last_sync ORDER BY updated_at ASC',
            $atelierId,
            $lastSync
        );

        $modeles = $this->fetchSince(
            $pdo,
            'SELECT uuid_local, nom, categorie, photo_url, prix_base, jours_confection, mesures_requises, updated_at, deleted_at
             FROM modeles WHERE atelier_id = :atelier_id AND updated_at > :last_sync ORDER BY updated_at ASC',
            $atelierId,
            $lastSync
        );
        foreach ($modeles as &$m) {
            $m['mesures_requises'] = json_decode((string) $m['mesures_requises'], true) ?: [];
        }
        unset($m);

        $mercerie = $this->fetchSince(
            $pdo,
            'SELECT uuid_local, nom, unite, prix_unitaire, stock_actuel, seuil_alerte, updated_at, deleted_at
             FROM mercerie WHERE atelier_id = :atelier_id AND updated_at > :last_sync ORDER BY updated_at ASC',
            $atelierId,
            $lastSync
        );

        $modeleMercerie = $this->fetchSince(
            $pdo,
            'SELECT modele_uuid, mercerie_uuid, quantite_necessaire, updated_at
             FROM modele_mercerie WHERE atelier_id = :atelier_id AND updated_at > :last_sync ORDER BY updated_at ASC',
            $atelierId,
            $lastSync
        );

        $employes = $this->fetchSince(
            $pdo,
            'SELECT uuid_local, nom, telephone, poste, type_remuneration, montant_remuneration, actif, updated_at, deleted_at
             FROM employes WHERE atelier_id = :atelier_id AND updated_at > :last_sync ORDER BY updated_at ASC',
            $atelierId,
            $lastSync
        );

        $commandes = $this->fetchSince(
            $pdo,
            'SELECT uuid_local, client_uuid, modele_uuid, employe_uuid, photo_tissu_url, mesures_client,
                    date_commande, date_essayage, date_livraison_prevue, prix_total, avance_payee, statut,
                    notes_commande, updated_at, deleted_at
             FROM commandes WHERE atelier_id = :atelier_id AND updated_at > :last_sync ORDER BY updated_at ASC',
            $atelierId,
            $lastSync
        );
        foreach ($commandes as &$c) {
            $c['mesures_client'] = json_decode((string) $c['mesures_client'], true) ?: new \stdClass();
        }
        unset($c);

        $depenses = $this->fetchSince(
            $pdo,
            'SELECT uuid_local, categorie, montant, date_depense, description, justificatif_url, updated_at, deleted_at
             FROM depenses WHERE atelier_id = :atelier_id AND updated_at > :last_sync ORDER BY updated_at ASC',
            $atelierId,
            $lastSync
        );

        $licenceStmt = $pdo->prepare(
            'SELECT code_atelier, plan_abonnement, max_appareils, date_expiration, updated_at FROM ateliers WHERE id = :id LIMIT 1'
        );
        $licenceStmt->execute([':id' => $atelierId]);
        $licenceRow = $licenceStmt->fetch() ?: null;

        $codesStmt = $pdo->prepare(
            'SELECT code, duration_code, days_added, used_at FROM codes_utilises WHERE atelier_id = :atelier_id ORDER BY used_at DESC'
        );
        $codesStmt->execute([':atelier_id' => $atelierId]);
        $codesUtilises = $codesStmt->fetchAll();

        Response::json([
            'success'     => true,
            'server_time' => $serverTime,
            'data'        => [
                'clients'         => $clients,
                'modeles'         => $modeles,
                'mercerie'        => $mercerie,
                'modele_mercerie' => $modeleMercerie,
                'employes'        => $employes,
                'commandes'       => $commandes,
                'depenses'        => $depenses,
                'licence'         => $licenceRow,
                'codes_utilises'  => $codesUtilises,
            ],
        ], 200);
    }

    private function upsertClients(PDO $pdo, int $atelierId, array $rows): array
    {
        if ($rows === []) {
            return [];
        }
        $sql = 'INSERT INTO clients (uuid_local, atelier_id, nom, telephone, adresse, notes, updated_at, deleted_at)
                VALUES (:uuid_local, :atelier_id, :nom, :telephone, :adresse, :notes, :updated_at, :deleted_at)
                ON DUPLICATE KEY UPDATE
                    nom        = IF(VALUES(updated_at) >= updated_at, VALUES(nom), nom),
                    telephone  = IF(VALUES(updated_at) >= updated_at, VALUES(telephone), telephone),
                    adresse    = IF(VALUES(updated_at) >= updated_at, VALUES(adresse), adresse),
                    notes      = IF(VALUES(updated_at) >= updated_at, VALUES(notes), notes),
                    deleted_at = IF(VALUES(updated_at) >= updated_at, VALUES(deleted_at), deleted_at),
                    updated_at = GREATEST(updated_at, VALUES(updated_at))';
        $stmt = $pdo->prepare($sql);
        $uuids = [];

        foreach ($rows as $row) {
            $uuid = $this->validUuid($row['uuid_local'] ?? null, 'clients.uuid_local');
            $stmt->execute([
                ':uuid_local' => $uuid,
                ':atelier_id' => $atelierId,
                ':nom'        => $this->nonEmptyString($row['nom'] ?? null, 'clients.nom'),
                ':telephone'  => trim((string) ($row['telephone'] ?? '')),
                ':adresse'    => trim((string) ($row['adresse'] ?? '')),
                ':notes'      => $row['notes'] ?? null,
                ':updated_at' => $this->normalizeDatetime($row['updated_at'] ?? null),
                ':deleted_at' => $this->normalizeNullableDatetime($row['deleted_at'] ?? null),
            ]);
            $uuids[] = $uuid;
        }
        return $uuids;
    }

    private function upsertModeles(PDO $pdo, int $atelierId, array $rows): array
    {
        if ($rows === []) {
            return [];
        }
        $sql = 'INSERT INTO modeles (uuid_local, atelier_id, nom, categorie, photo_url, prix_base, jours_confection, mesures_requises, updated_at, deleted_at)
                VALUES (:uuid_local, :atelier_id, :nom, :categorie, :photo_url, :prix_base, :jours_confection, :mesures_requises, :updated_at, :deleted_at)
                ON DUPLICATE KEY UPDATE
                    nom              = IF(VALUES(updated_at) >= updated_at, VALUES(nom), nom),
                    categorie        = IF(VALUES(updated_at) >= updated_at, VALUES(categorie), categorie),
                    photo_url        = IF(VALUES(updated_at) >= updated_at, VALUES(photo_url), photo_url),
                    prix_base        = IF(VALUES(updated_at) >= updated_at, VALUES(prix_base), prix_base),
                    jours_confection = IF(VALUES(updated_at) >= updated_at, VALUES(jours_confection), jours_confection),
                    mesures_requises = IF(VALUES(updated_at) >= updated_at, VALUES(mesures_requises), mesures_requises),
                    deleted_at       = IF(VALUES(updated_at) >= updated_at, VALUES(deleted_at), deleted_at),
                    updated_at       = GREATEST(updated_at, VALUES(updated_at))';
        $stmt = $pdo->prepare($sql);

        $delRecipeStmt = $pdo->prepare('DELETE FROM modele_mercerie WHERE atelier_id = :atelier_id AND modele_uuid = :modele_uuid');
        $insRecipeStmt = $pdo->prepare(
            'INSERT INTO modele_mercerie (atelier_id, modele_uuid, mercerie_uuid, quantite_necessaire)
             VALUES (:atelier_id, :modele_uuid, :mercerie_uuid, :qty)
             ON DUPLICATE KEY UPDATE quantite_necessaire = VALUES(quantite_necessaire)'
        );

        $uuids = [];
        foreach ($rows as $row) {
            $uuid = $this->validUuid($row['uuid_local'] ?? null, 'modeles.uuid_local');
            $mesures = $row['mesures_requises'] ?? [];
            $mesuresJson = is_string($mesures)
                ? $mesures
                : json_encode($mesures, JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR);

            $stmt->execute([
                ':uuid_local'       => $uuid,
                ':atelier_id'       => $atelierId,
                ':nom'              => $this->nonEmptyString($row['nom'] ?? null, 'modeles.nom'),
                ':categorie'        => trim((string) ($row['categorie'] ?? 'Femme')),
                ':photo_url'        => trim((string) ($row['photo_url'] ?? '')),
                ':prix_base'        => (float) ($row['prix_base'] ?? 0),
                ':jours_confection' => max(1, (int) ($row['jours_confection'] ?? 5)),
                ':mesures_requises' => $mesuresJson,
                ':updated_at'       => $this->normalizeDatetime($row['updated_at'] ?? null),
                ':deleted_at'       => $this->normalizeNullableDatetime($row['deleted_at'] ?? null),
            ]);

            if (isset($row['recette']) && is_array($row['recette'])) {
                $delRecipeStmt->execute([':atelier_id' => $atelierId, ':modele_uuid' => $uuid]);
                foreach ($row['recette'] as $rec) {
                    if (!is_array($rec)) {
                        continue;
                    }
                    $mercUuid = $this->validUuid($rec['mercerie_uuid'] ?? null, 'modeles.recette.mercerie_uuid');
                    $insRecipeStmt->execute([
                        ':atelier_id'    => $atelierId,
                        ':modele_uuid'   => $uuid,
                        ':mercerie_uuid' => $mercUuid,
                        ':qty'           => max(1, (int) ($rec['quantite_necessaire'] ?? 1)),
                    ]);
                }
            }
            $uuids[] = $uuid;
        }
        return $uuids;
    }

    private function upsertMercerie(PDO $pdo, int $atelierId, array $rows): array
    {
        if ($rows === []) {
            return [];
        }
        $sql = 'INSERT INTO mercerie (uuid_local, atelier_id, nom, unite, prix_unitaire, stock_actuel, seuil_alerte, updated_at, deleted_at)
                VALUES (:uuid_local, :atelier_id, :nom, :unite, :prix_unitaire, :stock_actuel, :seuil_alerte, :updated_at, :deleted_at)
                ON DUPLICATE KEY UPDATE
                    nom           = IF(VALUES(updated_at) >= updated_at, VALUES(nom), nom),
                    unite         = IF(VALUES(updated_at) >= updated_at, VALUES(unite), unite),
                    prix_unitaire = IF(VALUES(updated_at) >= updated_at, VALUES(prix_unitaire), prix_unitaire),
                    stock_actuel  = IF(VALUES(updated_at) >= updated_at, VALUES(stock_actuel), stock_actuel),
                    seuil_alerte  = IF(VALUES(updated_at) >= updated_at, VALUES(seuil_alerte), seuil_alerte),
                    deleted_at    = IF(VALUES(updated_at) >= updated_at, VALUES(deleted_at), deleted_at),
                    updated_at    = GREATEST(updated_at, VALUES(updated_at))';
        $stmt = $pdo->prepare($sql);
        $uuids = [];

        foreach ($rows as $row) {
            $uuid = $this->validUuid($row['uuid_local'] ?? null, 'mercerie.uuid_local');
            $stmt->execute([
                ':uuid_local'    => $uuid,
                ':atelier_id'    => $atelierId,
                ':nom'           => $this->nonEmptyString($row['nom'] ?? null, 'mercerie.nom'),
                ':unite'         => trim((string) ($row['unite'] ?? 'pièce')),
                ':prix_unitaire' => (float) ($row['prix_unitaire'] ?? 0),
                ':stock_actuel'  => (int) ($row['stock_actuel'] ?? 0),
                ':seuil_alerte'  => (int) ($row['seuil_alerte'] ?? 5),
                ':updated_at'    => $this->normalizeDatetime($row['updated_at'] ?? null),
                ':deleted_at'    => $this->normalizeNullableDatetime($row['deleted_at'] ?? null),
            ]);
            $uuids[] = $uuid;
        }
        return $uuids;
    }

    private function upsertModeleMercerie(PDO $pdo, int $atelierId, array $rows): array
    {
        $sql = 'INSERT INTO modele_mercerie (atelier_id, modele_uuid, mercerie_uuid, quantite_necessaire)
                VALUES (:atelier_id, :modele_uuid, :mercerie_uuid, :qty)
                ON DUPLICATE KEY UPDATE quantite_necessaire = VALUES(quantite_necessaire)';
        $stmt = $pdo->prepare($sql);
        $keys = [];
        foreach ($rows as $row) {
            $mUuid = $this->validUuid($row['modele_uuid'] ?? null, 'modele_mercerie.modele_uuid');
            $iUuid = $this->validUuid($row['mercerie_uuid'] ?? null, 'modele_mercerie.mercerie_uuid');
            $stmt->execute([
                ':atelier_id'    => $atelierId,
                ':modele_uuid'   => $mUuid,
                ':mercerie_uuid' => $iUuid,
                ':qty'           => max(1, (int) ($row['quantite_necessaire'] ?? 1)),
            ]);
            $keys[] = "$mUuid:$iUuid";
        }
        return $keys;
    }

    private function upsertEmployes(PDO $pdo, int $atelierId, array $rows): array
    {
        if ($rows === []) {
            return [];
        }
        $sql = 'INSERT INTO employes (uuid_local, atelier_id, nom, telephone, poste, type_remuneration, montant_remuneration, actif, updated_at, deleted_at)
                VALUES (:uuid_local, :atelier_id, :nom, :telephone, :poste, :type_remuneration, :montant_remuneration, :actif, :updated_at, :deleted_at)
                ON DUPLICATE KEY UPDATE
                    nom                  = IF(VALUES(updated_at) >= updated_at, VALUES(nom), nom),
                    telephone            = IF(VALUES(updated_at) >= updated_at, VALUES(telephone), telephone),
                    poste                = IF(VALUES(updated_at) >= updated_at, VALUES(poste), poste),
                    type_remuneration    = IF(VALUES(updated_at) >= updated_at, VALUES(type_remuneration), type_remuneration),
                    montant_remuneration = IF(VALUES(updated_at) >= updated_at, VALUES(montant_remuneration), montant_remuneration),
                    actif                = IF(VALUES(updated_at) >= updated_at, VALUES(actif), actif),
                    deleted_at           = IF(VALUES(updated_at) >= updated_at, VALUES(deleted_at), deleted_at),
                    updated_at           = GREATEST(updated_at, VALUES(updated_at))';
        $stmt = $pdo->prepare($sql);
        $uuids = [];

        foreach ($rows as $row) {
            $uuid = $this->validUuid($row['uuid_local'] ?? null, 'employes.uuid_local');
            $payMode = (string) ($row['type_remuneration'] ?? 'piece');
            if (!in_array($payMode, self::ALLOWED_PAY_MODES, true)) {
                throw new \InvalidArgumentException("type_remuneration invalide : $payMode");
            }

            $stmt->execute([
                ':uuid_local'           => $uuid,
                ':atelier_id'           => $atelierId,
                ':nom'                  => $this->nonEmptyString($row['nom'] ?? null, 'employes.nom'),
                ':telephone'            => trim((string) ($row['telephone'] ?? '')),
                ':poste'                => trim((string) ($row['poste'] ?? 'Tailleur')),
                ':type_remuneration'    => $payMode,
                ':montant_remuneration' => (float) ($row['montant_remuneration'] ?? 0),
                ':actif'                => !empty($row['actif']) ? 1 : 0,
                ':updated_at'           => $this->normalizeDatetime($row['updated_at'] ?? null),
                ':deleted_at'           => $this->normalizeNullableDatetime($row['deleted_at'] ?? null),
            ]);
            $uuids[] = $uuid;
        }
        return $uuids;
    }

    private function upsertCommandes(PDO $pdo, int $atelierId, array $rows): array
    {
        if ($rows === []) {
            return [];
        }
        $sql = 'INSERT INTO commandes (
                    uuid_local, atelier_id, client_uuid, modele_uuid, employe_uuid,
                    photo_tissu_url, mesures_client, date_commande, date_essayage, date_livraison_prevue,
                    prix_total, avance_payee, statut, notes_commande, updated_at, deleted_at
                ) VALUES (
                    :uuid_local, :atelier_id, :client_uuid, :modele_uuid, :employe_uuid,
                    :photo_tissu_url, :mesures_client, :date_commande, :date_essayage, :date_livraison_prevue,
                    :prix_total, :avance_payee, :statut, :notes_commande, :updated_at, :deleted_at
                )
                ON DUPLICATE KEY UPDATE
                    client_uuid           = IF(VALUES(updated_at) >= updated_at, VALUES(client_uuid), client_uuid),
                    modele_uuid           = IF(VALUES(updated_at) >= updated_at, VALUES(modele_uuid), modele_uuid),
                    employe_uuid          = IF(VALUES(updated_at) >= updated_at, VALUES(employe_uuid), employe_uuid),
                    photo_tissu_url       = IF(VALUES(updated_at) >= updated_at, VALUES(photo_tissu_url), photo_tissu_url),
                    mesures_client        = IF(VALUES(updated_at) >= updated_at, VALUES(mesures_client), mesures_client),
                    date_commande         = IF(VALUES(updated_at) >= updated_at, VALUES(date_commande), date_commande),
                    date_essayage         = IF(VALUES(updated_at) >= updated_at, VALUES(date_essayage), date_essayage),
                    date_livraison_prevue = IF(VALUES(updated_at) >= updated_at, VALUES(date_livraison_prevue), date_livraison_prevue),
                    prix_total            = IF(VALUES(updated_at) >= updated_at, VALUES(prix_total), prix_total),
                    avance_payee          = IF(VALUES(updated_at) >= updated_at, VALUES(avance_payee), avance_payee),
                    statut                = IF(VALUES(updated_at) >= updated_at, VALUES(statut), statut),
                    notes_commande        = IF(VALUES(updated_at) >= updated_at, VALUES(notes_commande), notes_commande),
                    deleted_at            = IF(VALUES(updated_at) >= updated_at, VALUES(deleted_at), deleted_at),
                    updated_at            = GREATEST(updated_at, VALUES(updated_at))';
        $stmt = $pdo->prepare($sql);
        $uuids = [];

        foreach ($rows as $row) {
            $uuid = $this->validUuid($row['uuid_local'] ?? null, 'commandes.uuid_local');
            $statut = (string) ($row['statut'] ?? 'En attente');
            if (!in_array($statut, self::ALLOWED_STATUSES, true)) {
                throw new \InvalidArgumentException("Statut commande invalide : $statut");
            }

            $mesures = $row['mesures_client'] ?? new \stdClass();
            $mesuresJson = is_string($mesures)
                ? $mesures
                : json_encode($mesures, JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR);

            $employeUuid = !empty($row['employe_uuid'])
                ? $this->validUuid($row['employe_uuid'], 'commandes.employe_uuid')
                : null;

            $stmt->execute([
                ':uuid_local'            => $uuid,
                ':atelier_id'            => $atelierId,
                ':client_uuid'           => $this->validUuid($row['client_uuid'] ?? null, 'commandes.client_uuid'),
                ':modele_uuid'           => $this->validUuid($row['modele_uuid'] ?? null, 'commandes.modele_uuid'),
                ':employe_uuid'          => $employeUuid,
                ':photo_tissu_url'       => trim((string) ($row['photo_tissu_url'] ?? '')),
                ':mesures_client'        => $mesuresJson,
                ':date_commande'         => $this->validDate($row['date_commande'] ?? null, 'commandes.date_commande'),
                ':date_essayage'         => $this->validNullableDate($row['date_essayage'] ?? null),
                ':date_livraison_prevue' => $this->validDate($row['date_livraison_prevue'] ?? null, 'commandes.date_livraison_prevue'),
                ':prix_total'            => (float) ($row['prix_total'] ?? 0),
                ':avance_payee'          => (float) ($row['avance_payee'] ?? 0),
                ':statut'                => $statut,
                ':notes_commande'        => $row['notes_commande'] ?? null,
                ':updated_at'            => $this->normalizeDatetime($row['updated_at'] ?? null),
                ':deleted_at'            => $this->normalizeNullableDatetime($row['deleted_at'] ?? null),
            ]);
            $uuids[] = $uuid;
        }
        return $uuids;
    }

    private function upsertDepenses(PDO $pdo, int $atelierId, array $rows): array
    {
        if ($rows === []) {
            return [];
        }
        $sql = 'INSERT INTO depenses (uuid_local, atelier_id, categorie, montant, date_depense, description, justificatif_url, updated_at, deleted_at)
                VALUES (:uuid_local, :atelier_id, :categorie, :montant, :date_depense, :description, :justificatif_url, :updated_at, :deleted_at)
                ON DUPLICATE KEY UPDATE
                    categorie        = IF(VALUES(updated_at) >= updated_at, VALUES(categorie), categorie),
                    montant          = IF(VALUES(updated_at) >= updated_at, VALUES(montant), montant),
                    date_depense     = IF(VALUES(updated_at) >= updated_at, VALUES(date_depense), date_depense),
                    description      = IF(VALUES(updated_at) >= updated_at, VALUES(description), description),
                    justificatif_url = IF(VALUES(updated_at) >= updated_at, VALUES(justificatif_url), justificatif_url),
                    deleted_at       = IF(VALUES(updated_at) >= updated_at, VALUES(deleted_at), deleted_at),
                    updated_at       = GREATEST(updated_at, VALUES(updated_at))';
        $stmt = $pdo->prepare($sql);
        $uuids = [];

        foreach ($rows as $row) {
            $uuid = $this->validUuid($row['uuid_local'] ?? null, 'depenses.uuid_local');
            $stmt->execute([
                ':uuid_local'       => $uuid,
                ':atelier_id'       => $atelierId,
                ':categorie'        => $this->nonEmptyString($row['categorie'] ?? null, 'depenses.categorie'),
                ':montant'          => (float) ($row['montant'] ?? 0),
                ':date_depense'     => $this->validDate($row['date_depense'] ?? null, 'depenses.date_depense'),
                ':description'      => trim((string) ($row['description'] ?? '')),
                ':justificatif_url' => trim((string) ($row['justificatif_url'] ?? '')),
                ':updated_at'       => $this->normalizeDatetime($row['updated_at'] ?? null),
                ':deleted_at'       => $this->normalizeNullableDatetime($row['deleted_at'] ?? null),
            ]);
            $uuids[] = $uuid;
        }
        return $uuids;
    }

    private function upsertLicence(PDO $pdo, int $atelierId, array $licence): bool
    {
        $codeAtelier = strtoupper(trim((string) ($licence['code_atelier'] ?? $licence['workshop_code'] ?? '')));
        $plan = (string) ($licence['plan_abonnement'] ?? 'free');
        if (!in_array($plan, ['free', 'pro', 'atelier_plus'], true)) {
            $plan = 'free';
        }
        $expRaw = trim((string) ($licence['date_expiration'] ?? ''));
        if ($expRaw === '') {
            return false;
        }
        $expDt = $this->normalizeDatetime($expRaw . (strlen($expRaw) === 10 ? ' 23:59:59' : ''));

        $stmt = $pdo->prepare(
            'UPDATE ateliers
             SET code_atelier    = COALESCE(NULLIF(:code_atelier, \'\'), code_atelier),
                 plan_abonnement = CASE WHEN date_expiration IS NULL OR :date_expiration >= date_expiration THEN :plan ELSE plan_abonnement END,
                 date_expiration = GREATEST(COALESCE(date_expiration, \'1970-01-01 00:00:00\'), :date_expiration_cmp)
             WHERE id = :id'
        );
        $stmt->execute([
            ':code_atelier'        => $codeAtelier,
            ':plan'                => $plan,
            ':date_expiration'     => $expDt,
            ':date_expiration_cmp' => $expDt,
            ':id'                  => $atelierId,
        ]);
        return true;
    }

    private function upsertUsedCodes(PDO $pdo, int $atelierId, array $rows): array
    {
        if ($rows === []) {
            return [];
        }
        $stmt = $pdo->prepare(
            'INSERT IGNORE INTO codes_utilises (atelier_id, workshop_code, code, duration_code, days_added, used_at)
             VALUES (:atelier_id, :workshop_code, :code, :duration_code, :days_added, :used_at)'
        );
        $codes = [];
        foreach ($rows as $row) {
            $code = strtoupper(trim((string) ($row['code'] ?? '')));
            if ($code === '') {
                continue;
            }
            $stmt->execute([
                ':atelier_id'    => $atelierId,
                ':workshop_code' => strtoupper(trim((string) ($row['workshop_code'] ?? ''))),
                ':code'          => $code,
                ':duration_code' => strtoupper(trim((string) ($row['duration_code'] ?? 'M1'))),
                ':days_added'    => max(1, (int) ($row['days_added'] ?? 30)),
                ':used_at'       => $this->normalizeDatetime($row['used_at'] ?? null),
            ]);
            $codes[] = $code;
        }
        return $codes;
    }

    private function fetchSince(PDO $pdo, string $sql, int $atelierId, string $lastSync): array
    {
        $stmt = $pdo->prepare($sql);
        $stmt->execute([
            ':atelier_id' => $atelierId,
            ':last_sync'  => $lastSync,
        ]);
        return $stmt->fetchAll();
    }

    private function requireArrayField(array $payload, string $key): array
    {
        if (!array_key_exists($key, $payload)) {
            return [];
        }
        if (!is_array($payload[$key])) {
            Response::error("Le champ '$key' doit être un tableau JSON.", 422);
        }
        return $payload[$key];
    }

    private function validUuid(mixed $val, string $field): string
    {
        $str = trim((string) $val);
        if (!preg_match('/^[a-zA-Z0-9_-]{8,36}$/', $str)) {
            throw new \InvalidArgumentException("Identifiant '$field' invalide (attendu UUID/clé 8..36 caractères).");
        }
        return $str;
    }

    private function nonEmptyString(mixed $val, string $field): string
    {
        $str = trim((string) $val);
        if ($str === '') {
            throw new \InvalidArgumentException("Le champ '$field' est obligatoire.");
        }
        return $str;
    }

    private function validDate(mixed $val, string $field): string
    {
        $str = trim((string) $val);
        $dt = DateTimeImmutable::createFromFormat('Y-m-d', $str);
        if ($dt === false || $dt->format('Y-m-d') !== $str) {
            throw new \InvalidArgumentException("Date '$field' invalide (attendu YYYY-MM-DD).");
        }
        return $str;
    }

    private function validNullableDate(mixed $val): ?string
    {
        if ($val === null || trim((string) $val) === '') {
            return null;
        }
        return $this->validDate($val, 'date_essayage');
    }

    private function normalizeDatetime(mixed $val): string
    {
        if ($val === null || trim((string) $val) === '') {
            return gmdate('Y-m-d H:i:s');
        }
        $ts = strtotime((string) $val);
        if ($ts === false) {
            throw new \InvalidArgumentException("Horodatage updated_at invalide : $val");
        }
        return gmdate('Y-m-d H:i:s', $ts);
    }

    private function normalizeNullableDatetime(mixed $val): ?string
    {
        if ($val === null || trim((string) $val) === '') {
            return null;
        }
        return $this->normalizeDatetime($val);
    }
}
