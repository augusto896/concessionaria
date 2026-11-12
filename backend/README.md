# API da concessionária (WampServer)

Backend PHP 8.1+ com PDO/MySQL para uso local no WampServer. O Android acessa esta API REST; ele nunca se conecta diretamente ao MySQL.

## Instalação no WampServer

1. Copie a pasta `backend` para `C:\wamp64\www\concessionaria-api` (ou ajuste o caminho do projeto).
2. No phpMyAdmin, importe `backend/database/schema.sql`. O script cria o banco `concessionaria` e as seis tabelas relacionadas.
3. Copie `backend/config.example.php` para `backend/config.php` e informe host, porta, banco e credenciais MySQL locais. Esse arquivo é ignorado pelo Git.
4. Habilite `mod_rewrite` no Apache e confirme que `AllowOverride All` está habilitado para `www`; a regra em `public/.htaccess` encaminha as rotas ao PHP.
5. Aponte o DocumentRoot para `backend/public` (recomendado) ou configure um alias para essa pasta. A URL base local ficará, por exemplo, `http://localhost/concessionaria-api/public/api/v1/`.
6. Crie o primeiro usuário administrador no terminal, a partir de `backend`: `php create_admin.php admin admin@example.com "Administrador"`. A senha é solicitada sem ser gravada em texto puro.

Emulador Android Studio: configure `API_BASE_URL` como `http://10.0.2.2/concessionaria-api/public/api/v1/`. Em aparelho físico, use o endereço IP local do computador e permita o tráfego Apache no firewall. HTTP sem TLS é somente para desenvolvimento local; use HTTPS antes de disponibilizar a API em uma rede não confiável.

## Rotas

Todas as respostas usam `{ "data": ... }` ou `{ "error": { "code": "...", "message": "..." } }`. Exceto login, todas exigem `Authorization: Bearer <token>`.

| Método | Rota | Acesso |
|---|---|---|
| POST | `auth/login` | Público; corpo `{ "usuario": "...", "senha": "..." }` (email também aceito) |
| POST | `auth/logout` | Autenticado |
| GET | `dashboard` | Autenticado |
| GET/POST | `usuarios` | Administrador |
| GET/PUT/DELETE | `usuarios/{id}` | Administrador |
| GET/POST | `clientes`, `marcas`, `veiculos` | Autenticado |
| GET/PUT/DELETE | `clientes/{id}`, `marcas/{id}`, `veiculos/{id}` | Autenticado |
| GET | `vendas`, `vendas/{id}` | Autenticado |
| POST | `vendas` | Autenticado; vendedor autenticado é registrado como responsável |

O `POST /vendas` recebe `cliente_id`, `veiculo_id`, `data_venda` (`YYYY-MM-DD`), `valor_venda` e `forma_pagamento`. Em uma transação, bloqueia o veículo, verifica disponibilidade, grava a venda e marca o veículo como vendido. A chave única em `vendas.veiculo_id` impede uma segunda venda mesmo em requisições concorrentes. Exclusões de clientes, marcas, veículos e funcionários referenciados são recusadas pela integridade referencial do MySQL.

## Modelo de dados

`marcas 1:N veiculos`, `clientes 1:N vendas`, `usuarios 1:N vendas` e `veiculos 1:0..1 vendas`. `api_sessoes` guarda apenas o hash SHA-256 dos tokens aleatórios com expiração; senhas usam `password_hash`/`password_verify`.
