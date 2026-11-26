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

function columnExists(PDO $pdo, string $table, string $column): bool
{
    $query = $pdo->prepare(
        'SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?'
    );
    $query->execute([$table, $column]);
    return (bool)$query->fetchColumn();
}

if (!columnExists($pdo, 'veiculos', 'quantidade_estoque')) {
    $pdo->exec(
        'ALTER TABLE veiculos
         ADD COLUMN quantidade_estoque INT UNSIGNED NOT NULL DEFAULT 1'
    );
    $pdo->exec("UPDATE veiculos SET quantidade_estoque = 0 WHERE status = 'vendido'");
    fwrite(STDOUT, "Coluna veiculos.quantidade_estoque criada.\n");
}

if (!columnExists($pdo, 'vendas', 'quantidade')) {
    $pdo->exec(
        'ALTER TABLE vendas
         ADD COLUMN quantidade INT UNSIGNED NOT NULL DEFAULT 1'
    );
    fwrite(STDOUT, "Coluna vendas.quantidade criada.\n");
}

$indexQuery = $pdo->prepare(
    'SELECT 1 FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
       AND INDEX_NAME = ? AND NON_UNIQUE = 1'
);
$indexQuery->execute(['vendas', 'idx_vendas_veiculo_id']);
if (!$indexQuery->fetchColumn()) {
    $pdo->exec('ALTER TABLE vendas ADD INDEX idx_vendas_veiculo_id (veiculo_id)');
    fwrite(STDOUT, "Índice de vendas atualizado.\n");
}

$uniqueIndexes = $pdo->query(
    "SELECT INDEX_NAME
     FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'vendas' AND NON_UNIQUE = 0
     GROUP BY INDEX_NAME
     HAVING COUNT(*) = 1 AND MAX(COLUMN_NAME = 'veiculo_id') = 1"
)->fetchAll(PDO::FETCH_COLUMN);
foreach ($uniqueIndexes as $indexName) {
    $quotedIndex = '`' . str_replace('`', '``', (string)$indexName) . '`';
    $pdo->exec("ALTER TABLE vendas DROP INDEX {$quotedIndex}");
    fwrite(STDOUT, "Índice exclusivo {$indexName} removido.\n");
}

foreach ([
    'veiculos' => 'preco',
    'vendas' => 'valor_venda',
] as $table => $column) {
    $query = $pdo->prepare(
        'SELECT NUMERIC_PRECISION, NUMERIC_SCALE
         FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?'
    );
    $query->execute([$table, $column]);
    $type = $query->fetch(PDO::FETCH_ASSOC);
    if (!$type) {
        throw new RuntimeException("Coluna {$table}.{$column} não encontrada.");
    }
    if ((int)$type['NUMERIC_PRECISION'] < 65 || (int)$type['NUMERIC_SCALE'] < 2) {
        $pdo->exec("ALTER TABLE {$table} MODIFY {$column} DECIMAL(65, 2) NOT NULL");
        fwrite(STDOUT, "Coluna {$table}.{$column} ampliada.\n");
    }
}

fwrite(STDOUT, "Migração de estoque concluída. É seguro executar novamente.\n");
