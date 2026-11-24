# API da concessionária (WampServer)

Backend PHP 8.1+ com PDO/MySQL para uso local no WampServer. O Android acessa esta API REST; ele nunca se conecta diretamente ao MySQL.

## Instalação no WampServer

### Passo a passo completo

1. Instale o WampServer e inicie o serviço Apache e MySQL.
2. Crie a pasta da API dentro do diretório público do Wamp:
   - `C:\wamp64\www\concessionaria-api`
3. Copie os arquivos do backend para essa pasta, mantendo a estrutura:
   - `C:\wamp64\www\concessionaria-api\public\`
   - `C:\wamp64\www\concessionaria-api\database\`
   - `C:\wamp64\www\concessionaria-api\config.php`
   - `C:\wamp64\www\concessionaria-api\create_admin.php`
4. Abra o phpMyAdmin em `http://localhost/phpmyadmin`.
5. No phpMyAdmin, importe o arquivo `database/schema.sql`.
6. O script faz automaticamente:
   - `CREATE DATABASE IF NOT EXISTS concessionaria;`
   - `USE concessionaria;`
   - criação das tabelas `usuarios`, `clientes`, `marcas`, `veiculos`, `vendas` e `api_sessoes`
7. O banco fica no MySQL do WampServer, em `localhost:3306`, e não dentro do projeto Android.
8. Copie `config.example.php` para `config.php` e configure:

```php
<?php
return [
    'database' => [
        'host' => '127.0.0.1',
        'port' => 3306,
        'name' => 'concessionaria',
        'user' => 'root',
        'password' => '',
    ],
];
```

9. Esse `config.php` é local e não deve ser enviado ao repositório.
10. Habilite `mod_rewrite` no Apache e confirme que `AllowOverride All` está permitido para a pasta `www`; isso permite que as rotas do `public/.htaccess` funcionem e que o cabeçalho de autenticação chegue ao PHP.
11. Aponte o DocumentRoot para `C:\wamp64\www\concessionaria-api\public` ou configure um alias para essa pasta.
12. A URL base local da API geralmente será:
   - `http://localhost/concessionaria-api/public/api/v1/`
13. Crie o primeiro usuário administrador a partir da pasta do backend:
   - `php create_admin.php admin admin@example.com "Administrador"`
14. O sistema pedirá a senha e salva apenas o hash seguro no banco.

### Onde guardar cada parte no computador

- Projeto Android (separado do backend):
  - `C:\Users\SeuUsuario\Documents\concessionaria\`
- API PHP no Wamp:
  - `C:\wamp64\www\concessionaria-api\`
- Banco de dados:
  - dentro do MySQL do WampServer, acessível pelo phpMyAdmin em `localhost`

### Configuração no app Android

- Emulador Android Studio: `http://10.0.2.2/concessionaria-api/public/api/v1/`
- Celular físico na mesma rede: `http://192.168.1.20/concessionaria-api/public/api/v1/`
- Use somente HTTP em desenvolvimento local; em produção use HTTPS.

Emulador Android Studio: configure `API_BASE_URL` como `http://10.0.2.2/concessionaria-api/public/api/v1/`. Em aparelho físico, use o endereço IP local do computador e permita o tráfego Apache no firewall. HTTP sem TLS é somente para desenvolvimento local; use HTTPS antes de disponibilizar a API em uma rede não confiável.

## Rotas

Todas as respostas usam `{ "data": ... }` ou `{ "error": { "code": "...", "message": "..." } }`. Exceto login, todas exigem `Authorization: Bearer <token>`.

| Método | Rota | Acesso |
|---|---|---|
| POST | `auth/login` | Público; corpo `{ "usuario": "...", "senha": "..." }` (email também aceito) |
| POST | `auth/logout` | Autenticado |
| GET | `dashboard` | Autenticado |
| GET/POST | `usuarios` | Administrador; ao criar, informe nome, email, cargo e senha — o identificador interno é gerado automaticamente |
| GET/PUT/DELETE | `usuarios/{id}` | Administrador |
| GET/POST | `clientes`, `marcas`, `veiculos` | Autenticado |
| GET/PUT/DELETE | `clientes/{id}`, `marcas/{id}`, `veiculos/{id}` | Autenticado |
| GET | `vendas`, `vendas/{id}` | Autenticado |
| POST | `vendas` | Autenticado; vendedor autenticado é registrado como responsável |

O `POST /veiculos` gera o ID do veículo automaticamente ao salvar. Se não houver marcas cadastradas, o app envia `marca_nome`; a API cria a marca (sem país de origem informado) e o veículo em uma única transação, sem exigir inserção manual no phpMyAdmin. O `POST /vendas` recebe `cliente_id`, `veiculo_id`, `data_venda` (`YYYY-MM-DD`), `valor_venda` e `forma_pagamento`. Em uma transação, bloqueia o veículo, verifica disponibilidade, grava a venda e marca o veículo como vendido. A chave única em `vendas.veiculo_id` impede uma segunda venda mesmo em requisições concorrentes. Funcionários podem entrar usando o email e a senha; o identificador interno `usuario` é gerado automaticamente ao criá-los pelo app. Exclusões de clientes, marcas, veículos e funcionários referenciados são recusadas pela integridade referencial do MySQL.

## Modelo de dados

`marcas 1:N veiculos`, `clientes 1:N vendas`, `usuarios 1:N vendas` e `veiculos 1:0..1 vendas`. `api_sessoes` guarda apenas o hash SHA-256 dos tokens aleatórios com expiração; senhas usam `password_hash`/`password_verify`.
