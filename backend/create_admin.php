<?php
declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

$configFile = __DIR__ . '/config.php';
if (!is_file($configFile)) {
    fwrite(STDERR, "Configure backend/config.php primeiro.\n");
    exit(1);
}
$config = require $configFile;
$db = $config['database'];
$dsn = sprintf(
    'mysql:host=%s;port=%d;dbname=%s;charset=utf8mb4',
    $db['host'],
    $db['port'],
    $db['name']
);
$pdo = new PDO($dsn, $db['user'], $db['password'], [
    PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
]);

$username = trim($argv[1] ?? '');
$email = trim($argv[2] ?? '');
$name = trim($argv[3] ?? '');
if ($username === '' || $email === '' || $name === '') {
    fwrite(STDERR, "Uso: php create_admin.php <usuario> <email> <nome>\n");
    exit(1);
}
fwrite(STDOUT, "Senha (mínimo 8 caracteres): ");
$password = trim((string)fgets(STDIN));
if (strlen($password) < 8) {
    fwrite(STDERR, "A senha deve ter pelo menos 8 caracteres.\n");
    exit(1);
}
$statement = $pdo->prepare(
    'INSERT INTO usuarios (nome, usuario, senha_hash, email, cargo) VALUES (?, ?, ?, ?, ?)'
);
$statement->execute([$name, $username, password_hash($password, PASSWORD_DEFAULT), $email, 'admin']);
fwrite(STDOUT, "Administrador criado com sucesso.\n");
