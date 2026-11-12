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

1. Importe `backend/database/schema.sql` no phpMyAdmin do WampServer.
2. Crie `backend/config.php` a partir de `backend/config.example.php` e configure a conexão MySQL. Não versione esse arquivo.
3. Configure Apache para servir `backend/public` (ou o alias descrito em `backend/README.md`), habilite `mod_rewrite` e crie o primeiro usuário com `php backend/create_admin.php admin admin@example.com "Administrador"`.
4. Inicie o WampServer e configure o endereço da API no Gradle. Para o emulador Android padrão, o valor inicial é `http://10.0.2.2/concessionaria-api/public/api/v1/`. Para um telefone físico, use o IP local do computador, por exemplo `gradlew.bat assembleDebug -PAPI_BASE_URL=http://192.168.1.20/concessionaria-api/public/api/v1/`.
5. Abra o projeto no Android Studio e execute a variante `debug`. HTTP sem TLS é permitido somente nessa variante para desenvolvimento local; implantações reais devem usar HTTPS.

O dashboard e os módulos consultam a API. Veículos, clientes, marcas e usuários têm listagem, criação, edição e exclusão com confirmação. Vendas são consultáveis e criadas pelo app; a API faz a reserva do veículo e a atualização de situação na mesma transação. As permissões de funcionários são verificadas no servidor, independentemente dos controles visuais do app.

## Verificação

Execute `gradlew.bat testDebugUnitTest assembleDebug` no Windows para compilar e executar os testes locais. A API também pode ser verificada com `php -l backend/public/index.php` e `php -l backend/create_admin.php`. Os testes de integração com MySQL exigem WampServer ativo e banco importado.
