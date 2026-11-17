# Concessionária

Aplicativo Android de gestão comercial para estoque, clientes, marcas, funcionários e vendas. O app usa Kotlin, Jetpack Compose/Material 3, MVVM com `StateFlow`, Navigation Compose, Retrofit e DataStore. A API PHP/MySQL localiza-se em `backend/` e foi preparada para WampServer.

## Arquitetura

```text
app/src/main/java/com/example/concessionaria/
  data/remote/       Contratos Retrofit e envelopes JSON
  data/repository/   Retrofit, autenticação e acesso à API
  domain/            Modelos de domínio
  preferences/       Sessão e preferências persistidas com DataStore
  ui/theme/          Tema Material 3
  viewmodel/         Estado e operações assíncronas com StateFlow
  MainActivity.kt    Login, dashboard, navegação e módulos Compose
backend/
  database/schema.sql  Modelo MySQL e chaves estrangeiras
  public/index.php     API REST
  create_admin.php     Criação segura do primeiro administrador (CLI)
```

O MySQL só é acessado pelo backend. `backend/README.md` contém o procedimento completo de configuração do WampServer, as rotas, os campos JSON e as regras das vendas.

## Executar

### 1) Preparar o banco no WampServer

1. Instale e inicie o WampServer.
2. Abra o phpMyAdmin em `http://localhost/phpmyadmin`.
3. Crie o banco `concessionaria` (ou importe o arquivo `backend/database/schema.sql` diretamente; o script já contém `CREATE DATABASE IF NOT EXISTS concessionaria`).
4. Importante: o banco deve ficar dentro do MySQL do WampServer, não na pasta do projeto Android. O endereço do banco será localmente `127.0.0.1` ou `localhost` na porta `3306`.
5. O script `schema.sql` cria as tabelas: `usuarios`, `clientes`, `marcas`, `veiculos`, `vendas` e `api_sessoes`.

### 2) Colocar a API dentro das pastas do computador

1. Copie a pasta do backend para dentro do diretório público do WampServer, por exemplo:
   - `C:\wamp64\www\concessionaria-api\`
2. A estrutura final da API deve ficar assim:
   - `C:\wamp64\www\concessionaria-api\public\index.php`
   - `C:\wamp64\www\concessionaria-api\config.php`
   - `C:\wamp64\www\concessionaria-api\database\schema.sql`
3. A raiz do projeto Android fica separada, normalmente em alguma pasta do usuário, por exemplo:
   - `C:\Users\SeuUsuario\Documents\concessionaria\`
4. A pasta do backend nunca deve ser colada dentro da pasta do Android Studio; o projeto Android apenas acessa a API via HTTP.

### 3) Configurar a conexão com o banco

1. Copie `backend/config.example.php` para `backend/config.php`.
2. Edite esse arquivo com as credenciais locais do WampServer:

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

3. O arquivo `config.php` é local e NÃO deve ser versionado no Git.
4. Se o navegador não encontrar a API, confirme que o Apache do Wamp está ativo e que o alias/document root aponta para `C:\wamp64\www\concessionaria-api`.

### 4) Iniciar a API e criar o administrador

1. Ative o WampServer e confirme que o Apache está rodando.
2. Abra o terminal no diretório do backend e execute:
   - `php create_admin.php admin admin@example.com "Administrador"`
3. O script solicita a senha e grava apenas o hash seguro no banco.
4. A API fica disponível em uma URL como:
   - `http://localhost/concessionaria-api/public/api/v1/`

### 5) Configurar o Android

1. No projeto Android, ajuste `API_BASE_URL` para a URL local do Wamp.
2. Para emulador Android, use:
   - `http://10.0.2.2/concessionaria-api/public/api/v1/`
3. Para aparelho físico na mesma rede, use o IP do computador, por exemplo:
   - `http://192.168.1.20/concessionaria-api/public/api/v1/`
4. Abra o projeto no Android Studio e execute a variante `debug`.

O dashboard e os módulos consultam a API. Veículos, clientes, marcas e usuários têm listagem, criação, edição e exclusão com confirmação. Vendas são consultáveis e criadas pelo app; a API faz a reserva do veículo e a atualização de situação na mesma transação. As permissões de funcionários são verificadas no servidor, independentemente dos controles visuais do app.

## Verificação

Execute `gradlew.bat testDebugUnitTest assembleDebug` no Windows para compilar e executar os testes locais. A API também pode ser verificada com `php -l backend/public/index.php` e `php -l backend/create_admin.php`. Os testes de integração com MySQL exigem WampServer ativo e banco importado.
