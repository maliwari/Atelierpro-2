<?php
declare(strict_types=1);

require_once __DIR__ . '/includes/auth.php';

if (current_admin() !== null) {
    header('Location: pages/dashboard.php');
} else {
    header('Location: pages/login.php');
}
exit;
