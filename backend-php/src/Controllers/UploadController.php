<?php
declare(strict_types=1);

namespace App\Controllers;

use App\Config\Config;
use App\Config\Database;
use App\Http\Response;
use App\Middleware\AuthMiddleware;
use App\Security\RateLimiter;

final class UploadController
{
    private const MAX_FILE_BYTES = 5 * 1024 * 1024; // 5 Mo (les photos sont déjà compressées à 70 % sur mobile)
    private const ALLOWED_MIMES = [
        'image/webp' => 'webp',
        'image/jpeg' => 'jpg',
        'image/png'  => 'png',
    ];

    /**
     * POST /api/upload/image (multipart/form-data, champ "image")
     * Stocke l'image dans /public/uploads/{atelier_id}/ et renvoie l'URL publique.
     */
    public function uploadImage(): never
    {
        $atelier   = AuthMiddleware::requireAtelier();
        $atelierId = (int) $atelier['id'];
        RateLimiter::enforceApi(Database::getConnection(), 'api_upload', 'atelier:' . $atelierId, 120, 60);

        if (!isset($_FILES['image']) || !is_array($_FILES['image'])) {
            Response::error('Fichier "image" manquant dans la requête multipart/form-data.', 422);
        }

        $file = $_FILES['image'];
        if (($file['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK) {
            Response::error('Échec du téléversement du fichier.', 422);
        }

        $size = (int) ($file['size'] ?? 0);
        if ($size <= 0 || $size > self::MAX_FILE_BYTES) {
            Response::error('Taille d’image invalide (maximum 5 Mo).', 422);
        }

        $tmpPath = (string) ($file['tmp_name'] ?? '');
        if ($tmpPath === '' || !is_uploaded_file($tmpPath)) {
            Response::error('Fichier temporaire invalide.', 422);
        }

        // Vérification stricte du type MIME réel (ne jamais faire confiance à l'extension client)
        $finfo = new \finfo(FILEINFO_MIME_TYPE);
        $mime  = (string) $finfo->file($tmpPath);
        if (!isset(self::ALLOWED_MIMES[$mime])) {
            Response::error('Format non supporté. Formats autorisés : WebP, JPEG, PNG.', 422);
        }
        $ext = self::ALLOWED_MIMES[$mime];

        // Sous-dossier isolé par atelier_id dans le répertoire public web
        $publicBase  = defined('ATELIERPRO_PUBLIC_DIR') ? ATELIERPRO_PUBLIC_DIR : dirname(__DIR__, 2) . '/public';
        $uploadsRoot = rtrim($publicBase, '/') . '/uploads/' . $atelierId;
        if (!is_dir($uploadsRoot) && !mkdir($uploadsRoot, 0755, true) && !is_dir($uploadsRoot)) {
            Response::error('Impossible de créer le dossier de stockage sécurisé.', 500);
        }

        $filename   = sprintf('%s_%s.%s', gmdate('Ymd_His'), bin2hex(random_bytes(8)), $ext);
        $targetPath = $uploadsRoot . '/' . $filename;

        if (!move_uploaded_file($tmpPath, $targetPath)) {
            Response::error('Impossible d’enregistrer l’image sur le serveur.', 500);
        }

        $baseUrl      = rtrim(Config::get('APP_BASE_URL', ''), '/');
        $relativePath = "/uploads/$atelierId/$filename";
        $publicUrl    = $baseUrl !== '' ? $baseUrl . $relativePath : $relativePath;

        Response::json([
            'success'       => true,
            'url'           => $publicUrl,
            'relative_path' => $relativePath,
            'mime_type'     => $mime,
            'size_bytes'    => $size,
        ], 201);
    }
}
