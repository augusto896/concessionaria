<?php
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');

function respond(int $status, array $body): never
{
    http_response_code($status);
    echo json_encode($body, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

function ok(mixed $data, int $status = 200): never
{
    respond($status, ['data' => $data]);
}

function fail(int $status, string $code, string $message): never
{
    respond($status, ['error' => ['code' => $code, 'message' => $message]]);
}

function input(): array
{
    $raw = file_get_contents('php://input');
    $data = json_decode($raw === false ? '' : $raw, true);
    if (!is_array($data)) {
        fail(400, 'invalid_json', 'O corpo deve ser um objeto JSON válido.');
    }
    return $data;
}

function database(): PDO
{
    static $pdo = null;
    if ($pdo instanceof PDO) {
        return $pdo;
    }

    $configFile = dirname(__DIR__) . '/config.php';
    if (!is_file($configFile)) {
        fail(500, 'server_not_configured', 'Configure backend/config.php antes de iniciar a API.');
    }
    $config = require $configFile;
    $db = $config['database'] ?? [];
    $dsn = sprintf(
        'mysql:host=%s;port=%d;dbname=%s;charset=utf8mb4',
        $db['host'] ?? '127.0.0.1',
        $db['port'] ?? 3306,
        $db['name'] ?? 'concessionaria'
    );
    $pdo = new PDO($dsn, $db['user'] ?? '', $db['password'] ?? '', [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES => false,
    ]);
    return $pdo;
}

function requestBodyFields(array $body, array $allowed, array $required = []): array
{
    $fields = [];
    foreach ($allowed as $field) {
        if (array_key_exists($field, $body)) {
            $fields[$field] = $body[$field];
        }
    }
    foreach ($required as $field) {
        if (!isset($fields[$field]) || $fields[$field] === '') {
            fail(422, 'validation_error', "O campo {$field} é obrigatório.");
        }
    }
    return $fields;
}

function authenticate(PDO $pdo): array
{
    $header = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
    if (!preg_match('/^Bearer\s+([a-f0-9]{64})$/i', $header, $match)) {
        fail(401, 'unauthorized', 'Faça login para continuar.');
    }
    $query = $pdo->prepare(
        'SELECT u.id, u.nome, u.usuario, u.email, u.cargo
         FROM api_sessoes s JOIN usuarios u ON u.id = s.usuario_id
         WHERE s.token_hash = ? AND s.expira_em > NOW()'
    );
    $query->execute([hash('sha256', $match[1])]);
    $user = $query->fetch();
    if (!$user) {
        fail(401, 'unauthorized', 'A sessão expirou. Entre novamente.');
    }
    return $user;
}

function requireAdmin(array $user): void
{
    if ($user['cargo'] !== 'admin') {
        fail(403, 'forbidden', 'Esta operação exige permissão de administrador.');
    }
}

function sendRow(array $row): array
{
    unset($row['senha_hash']);
    return $row;
}

function resourceConfig(string $resource): array
{
    $resources = [
        'usuarios' => [
            'table' => 'usuarios',
            'fields' => ['nome', 'usuario', 'email', 'cargo'],
            'required' => ['nome', 'usuario', 'email', 'cargo'],
            'admin' => true,
        ],
        'clientes' => [
            'table' => 'clientes',
            'fields' => ['nome', 'cpf', 'telefone', 'email', 'endereco'],
            'required' => ['nome', 'cpf', 'telefone', 'endereco'],
        ],
        'marcas' => [
            'table' => 'marcas',
            'fields' => ['nome', 'pais_origem'],
            'required' => ['nome', 'pais_origem'],
        ],
        'veiculos' => [
            'table' => 'veiculos',
            'fields' => ['marca_id', 'modelo', 'ano', 'cor', 'quilometragem', 'preco', 'descricao', 'imagem_url'],
            'required' => ['marca_id', 'modelo', 'ano', 'cor', 'quilometragem', 'preco'],
        ],
    ];
    if (!isset($resources[$resource])) {
        fail(404, 'not_found', 'Recurso não encontrado.');
    }
    return $resources[$resource];
}

function requirePositiveId(string $value): int
{
    if (!ctype_digit($value) || (int)$value < 1) {
        fail(400, 'invalid_id', 'O identificador deve ser um número positivo.');
    }
    return (int)$value;
}

try {
    $pdo = database();
    $path = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';
    $path = preg_replace('#^.*?/api/v1/?#', '', $path);
    $parts = array_values(array_filter(explode('/', trim($path, '/')), 'strlen'));
    $method = strtoupper($_SERVER['REQUEST_METHOD'] ?? 'GET');

    if (($parts[0] ?? '') === 'auth' && ($parts[1] ?? '') === 'login' && $method === 'POST') {
        $body = input();
        $identity = trim((string)($body['usuario'] ?? $body['email'] ?? ''));
        $password = (string)($body['senha'] ?? '');
        if ($identity === '' || $password === '') {
            fail(422, 'validation_error', 'Informe o usuário ou email e a senha.');
        }
        $query = $pdo->prepare(
            'SELECT id, nome, usuario, senha_hash, email, cargo FROM usuarios WHERE usuario = ? OR email = ? LIMIT 1'
        );
        $query->execute([$identity, $identity]);
        $user = $query->fetch();
        if (!$user || !password_verify($password, $user['senha_hash'])) {
            fail(401, 'invalid_credentials', 'Usuário ou senha inválidos.');
        }
        $token = bin2hex(random_bytes(32));
        $insert = $pdo->prepare(
            'INSERT INTO api_sessoes (usuario_id, token_hash, expira_em) VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 30 DAY))'
        );
        $insert->execute([$user['id'], hash('sha256', $token)]);
        unset($user['senha_hash']);
        ok(['token' => $token, 'usuario' => $user]);
    }

    if (($parts[0] ?? '') === 'auth' && ($parts[1] ?? '') === 'logout' && $method === 'POST') {
        $header = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
        if (preg_match('/^Bearer\s+([a-f0-9]{64})$/i', $header, $match)) {
            $delete = $pdo->prepare('DELETE FROM api_sessoes WHERE token_hash = ?');
            $delete->execute([hash('sha256', $match[1])]);
        }
        ok(['encerrada' => true]);
    }

    if (($parts[0] ?? '') === 'dashboard' && $method === 'GET') {
        authenticate($pdo);
        $counts = $pdo->query(
            "SELECT
                (SELECT COUNT(*) FROM veiculos) AS total_veiculos,
                (SELECT COUNT(*) FROM veiculos WHERE status = 'disponivel') AS veiculos_disponiveis,
                (SELECT COUNT(*) FROM veiculos WHERE status = 'vendido') AS veiculos_vendidos,
                (SELECT COUNT(*) FROM clientes) AS total_clientes,
                (SELECT COUNT(*) FROM vendas) AS total_vendas"
        )->fetch();
        ok($counts);
    }

    $resource = $parts[0] ?? '';
    if ($resource === 'vendas') {
        $user = authenticate($pdo);
        if ($method === 'GET' && count($parts) <= 2) {
            $sql = 'SELECT v.id, v.cliente_id, v.veiculo_id, v.usuario_id, v.data_venda, v.valor_venda,
                           v.forma_pagamento, c.nome AS cliente_nome, m.nome AS marca_nome,
                           car.modelo AS veiculo_modelo, u.nome AS vendedor_nome
                    FROM vendas v
                    JOIN clientes c ON c.id = v.cliente_id
                    JOIN veiculos car ON car.id = v.veiculo_id
                    JOIN marcas m ON m.id = car.marca_id
                    JOIN usuarios u ON u.id = v.usuario_id';
            if (isset($parts[1])) {
                $query = $pdo->prepare($sql . ' WHERE v.id = ?');
                $query->execute([requirePositiveId($parts[1])]);
                $row = $query->fetch();
                $row ? ok($row) : fail(404, 'not_found', 'Venda não encontrada.');
            }
            ok($pdo->query($sql . ' ORDER BY v.id DESC')->fetchAll());
        }
        if ($method === 'POST' && count($parts) === 1) {
            $body = input();
            $fields = requestBodyFields(
                $body,
                ['cliente_id', 'veiculo_id', 'data_venda', 'valor_venda', 'forma_pagamento', 'usuario_id'],
                ['cliente_id', 'veiculo_id', 'data_venda', 'valor_venda', 'forma_pagamento']
            );
            $sellerId = $user['cargo'] === 'admin'
                ? (int)($fields['usuario_id'] ?? $user['id'])
                : (int)$user['id'];
            $pdo->beginTransaction();
            $vehicleQuery = $pdo->prepare('SELECT status FROM veiculos WHERE id = ? FOR UPDATE');
            $vehicleQuery->execute([(int)$fields['veiculo_id']]);
            $vehicle = $vehicleQuery->fetch();
            if (!$vehicle) {
                $pdo->rollBack();
                fail(422, 'invalid_vehicle', 'O veículo informado não existe.');
            }
            if ($vehicle['status'] !== 'disponivel') {
                $pdo->rollBack();
                fail(409, 'vehicle_already_sold', 'Este veículo não está disponível para venda.');
            }
            $insert = $pdo->prepare(
                'INSERT INTO vendas (cliente_id, veiculo_id, usuario_id, data_venda, valor_venda, forma_pagamento)
                 VALUES (?, ?, ?, ?, ?, ?)'
            );
            $insert->execute([
                (int)$fields['cliente_id'], (int)$fields['veiculo_id'], $sellerId,
                $fields['data_venda'], $fields['valor_venda'], $fields['forma_pagamento'],
            ]);
            $saleId = (int)$pdo->lastInsertId();
            $update = $pdo->prepare("UPDATE veiculos SET status = 'vendido' WHERE id = ?");
            $update->execute([(int)$fields['veiculo_id']]);
            $pdo->commit();
            ok(['id' => $saleId], 201);
        }
        fail(405, 'method_not_allowed', 'Método não permitido para vendas.');
    }

    $config = resourceConfig($resource);
    $user = authenticate($pdo);
    if (($config['admin'] ?? false) === true) {
        requireAdmin($user);
    }
    $table = $config['table'];
    $id = isset($parts[1]) ? requirePositiveId($parts[1]) : null;

    if ($method === 'GET' && count($parts) <= 2) {
        $columns = $resource === 'usuarios' ? 'id, nome, usuario, email, cargo, criado_em' : '*';
        $from = $resource === 'veiculos'
            ? 'veiculos v JOIN marcas m ON m.id = v.marca_id'
            : $table;
        if ($resource === 'veiculos') {
            $columns = 'v.*, m.nome AS marca_nome';
        }
        if ($id !== null) {
            $idColumn = $resource === 'veiculos' ? 'v.id' : 'id';
            $query = $pdo->prepare("SELECT {$columns} FROM {$from} WHERE {$idColumn} = ?");
            $query->execute([$id]);
            $row = $query->fetch();
            $row ? ok(sendRow($row)) : fail(404, 'not_found', 'Registro não encontrado.');
        }
        $orderColumn = $resource === 'veiculos' ? 'v.id' : 'id';
        ok(array_map('sendRow', $pdo->query("SELECT {$columns} FROM {$from} ORDER BY {$orderColumn} DESC")->fetchAll()));
    }

    if ($method === 'POST' && $id === null && count($parts) === 1) {
        $body = input();
        $fields = requestBodyFields($body, $config['fields'], $config['required']);
        if ($resource === 'usuarios') {
            $password = (string)($body['senha'] ?? '');
            if (strlen($password) < 8) {
                fail(422, 'validation_error', 'A senha deve ter pelo menos 8 caracteres.');
            }
            if (!in_array($fields['cargo'], ['admin', 'vendedor'], true)) {
                fail(422, 'validation_error', 'Cargo inválido.');
            }
            $fields['senha_hash'] = password_hash($password, PASSWORD_DEFAULT);
        }
        if ($resource === 'clientes' && !preg_match('/^\d{11}$/', (string)$fields['cpf'])) {
            fail(422, 'validation_error', 'O CPF deve conter 11 dígitos.');
        }
        $columns = array_keys($fields);
        $sql = sprintf(
            'INSERT INTO %s (%s) VALUES (%s)',
            $table,
            implode(', ', $columns),
            implode(', ', array_fill(0, count($columns), '?'))
        );
        $insert = $pdo->prepare($sql);
        $insert->execute(array_values($fields));
        ok(['id' => (int)$pdo->lastInsertId()], 201);
    }

    if (in_array($method, ['PUT', 'PATCH'], true) && $id !== null && count($parts) === 2) {
        $body = input();
        $fields = requestBodyFields($body, $config['fields']);
        if ($resource === 'usuarios' && isset($body['senha']) && $body['senha'] !== '') {
            if (strlen((string)$body['senha']) < 8) {
                fail(422, 'validation_error', 'A senha deve ter pelo menos 8 caracteres.');
            }
            $fields['senha_hash'] = password_hash((string)$body['senha'], PASSWORD_DEFAULT);
        }
        if ($resource === 'usuarios' && isset($fields['cargo']) && !in_array($fields['cargo'], ['admin', 'vendedor'], true)) {
            fail(422, 'validation_error', 'Cargo inválido.');
        }
        if (!$fields) {
            fail(422, 'validation_error', 'Informe ao menos um campo permitido para atualizar.');
        }
        $assignments = array_map(static fn(string $field): string => "{$field} = ?", array_keys($fields));
        $statement = $pdo->prepare("UPDATE {$table} SET " . implode(', ', $assignments) . ' WHERE id = ?');
        $statement->execute([...array_values($fields), $id]);
        if ($statement->rowCount() === 0) {
            $exists = $pdo->prepare("SELECT 1 FROM {$table} WHERE id = ?");
            $exists->execute([$id]);
            if (!$exists->fetchColumn()) {
                fail(404, 'not_found', 'Registro não encontrado.');
            }
        }
        ok(['atualizado' => true]);
    }

    if ($method === 'DELETE' && $id !== null && count($parts) === 2) {
        if ($resource === 'usuarios' && $id === (int)$user['id']) {
            fail(409, 'cannot_delete_self', 'Não é possível excluir o usuário autenticado.');
        }
        $delete = $pdo->prepare("DELETE FROM {$table} WHERE id = ?");
        $delete->execute([$id]);
        if ($delete->rowCount() === 0) {
            fail(404, 'not_found', 'Registro não encontrado.');
        }
        ok(['excluido' => true]);
    }

    fail(405, 'method_not_allowed', 'Método não permitido para este recurso.');
} catch (PDOException $exception) {
    if (isset($pdo) && $pdo->inTransaction()) {
        $pdo->rollBack();
    }
    if ($exception->getCode() === '23000') {
        fail(409, 'relationship_conflict', 'O registro possui dados relacionados ou viola uma regra de unicidade.');
    }
    error_log($exception->getMessage());
    fail(500, 'database_error', 'Não foi possível concluir a operação no banco de dados.');
} catch (Throwable $exception) {
    error_log($exception->getMessage());
    fail(500, 'server_error', 'Ocorreu um erro interno.');
}
