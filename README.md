# Tarefas

Organizador de tarefas com calendário. Tarefas não concluídas até o fim do dia
aparecem como pendentes no dia seguinte (com o aviso "Atrasada desde ...").

## Memória do projeto

- [Histórico, estado validado, pendências e retomada](docs/RETOMADA.md)
- [Decisões de produto e arquitetura, com seus motivos](docs/DECISOES.md)
- [Orientações compartilhadas para agentes](AGENTS.md)

Esses documentos registram o contexto para continuar o trabalho em outra sessão
ou com outro agente. O README reúne as instruções de execução e o contrato da API.

## Estrutura

- `android/` — app Android (Kotlin + Jetpack Compose + Room/SQLite). Funciona
  offline e sincroniza com o backend após conectar uma conta.
- `backend/` — Laravel + MySQL, com front web em Vue e API com Sanctum.
  O navegador usa sessão; o Android usa token por aparelho.
- `shared/fixtures/rules.json` — casos de teste das regras de recorrência e de
  dia de exibição. O app e o backend rodam os mesmos casos, para as regras
  escritas em Kotlin e em PHP não divergirem.

## App Android

1. Abra a pasta `android/` no Android Studio (**File > Open**).
2. Se o Android Studio pedir o JDK do Gradle, use o 21 (o Gradle 8.9 do projeto
   não funciona com o 25).
3. Conecte um celular com depuração USB, ou use um emulador, e clique em **Run ▶**.

Requer minSdk 26 (Android 8.0+). Testes: `gradle :app:testDebugUnitTest`
dentro de `android/`, usando Gradle 8.9 e JDK 21 (ou pelo Android Studio).
O repositório ainda não inclui o executável `gradlew`.

### Conectar o Android ao servidor

No menu **⋮ > Conta e sincronização**, informe o servidor, e-mail e senha da
mesma conta criada no web. Antes de conectar a primeira conta, o app continua
funcionando somente no aparelho. As tarefas locais são vinculadas à primeira
conta conectada; Trabalho, Casa e Faculdade são conciliadas com as tags padrão
já existentes no servidor.

Para testar com o backend local:

- Emulador padrão do Android Studio: `http://10.0.2.2:8000`.
- Celular na mesma rede: use o IP local do computador, por exemplo
  `http://192.168.1.10:8000`, e execute o servidor com
  `php artisan serve --host=0.0.0.0 --port=8000` dentro de `backend/`.
- HTTP só é aceito na versão debug e para endereços locais. A versão release
  exige HTTPS. Use sempre o mesmo endereço para uma conta neste aparelho: a
  identificação do espaço local inclui o servidor e o usuário.

A sincronização acontece após alterações locais, ao abrir/retomar o app, a cada
30 segundos enquanto a lista está aberta e periodicamente em segundo plano.
O Android pode adiar o trabalho em segundo plano para economizar bateria.
**Sincronizar agora** permite solicitar uma atualização manual. Falhas de conexão
mantêm a fila local; uma sessão expirada pede novo login sem apagar os dados.

A sessão também é verificada ao abrir/retomar o app e a cada 30 segundos em
primeiro plano, inclusive na tela de conta. Quando o servidor responde que o
acesso foi revogado, o token é descartado e o app volta à tela de login; tarefas,
widget e lembretes ficam indisponíveis até entrar novamente. Os bancos e as
alterações pendentes são preservados. Sem internet, a troca de senha só pode ser
detectada no próximo contato com o servidor.

Cada conta tem seu próprio banco e cursor. Ao sair, tarefas e alterações pendentes
ficam guardadas para o próximo login daquela conta. Widgets e notificações usam
apenas a conta ativa. A migração 5→6 preserva os registros existentes; não é
necessário desinstalar o app nem limpar seus dados.

O token é protegido pelo Android Keystore, a senha não é salva e a sessão não
entra no backup automático. O vínculo entre contas e bancos é preservado junto
dos dados. Em conta conectada, **Importar backup** adiciona novas cópias, sem
substituir ou apagar tarefas do servidor. Backups exportados de contas também
recebem IDs novos ao serem importados em outro espaço local.

Na tela de acesso, **Esqueci minha senha** solicita um link por e-mail ao
servidor informado. Abra o link no navegador, escolha uma nova senha e volte ao
app para entrar. Solicitar o link não conecta uma conta nem altera as tarefas
locais. Após redefinir a senha, os acessos anteriores precisam de novo login;
as alterações offline continuam guardadas para a mesma conta.

Validação no aparelho:

1. Atualize o app mantendo os dados e confira tarefas, tags, recorrências e prazos.
2. Entre na conta do web e confira a importação, sem duplicar as tags padrão.
3. Crie e edite uma tarefa no Android; confira no web. Faça o caminho inverso e
   use **Sincronizar agora** ou aguarde a atualização com a lista aberta.
4. Ative o modo avião, crie/edite/exclua tarefas e reinicie o app. Reconecte e
   confira se as alterações chegam ao web.
5. Conclua uma recorrente e confira que há só uma próxima ocorrência.
6. Saia da conta, entre em outra e confira também o widget. Volte à primeira e
   verifique que seus dados e alterações pendentes continuam disponíveis.

### Testes de integração Android ↔ Laravel

A suíte conecta o `SyncEngine`, o Room e o cliente HTTP do Android à API Laravel
real, usando MySQL em `tarefas_test`. O teste inicia um servidor em uma porta
livre de `127.0.0.1`, cria contas temporárias pela API e remove essas contas e
seus dados ao terminar. Não utiliza as contas do celular nem o servidor da
porta 8000. Não execute outra suíte que altere `tarefas_test` ao mesmo tempo.

Depois de configurar o backend, o banco de teste e o SDK Android, execute:

```sh
cd backend
JAVA_HOME=/caminho/do/jdk-21 \
SYNC_TEST_GRADLE=/caminho/do/gradle-8.9/bin/gradle \
RUN_ANDROID_SYNC_TESTS=1 \
php vendor/bin/phpunit tests/Feature/Api/AndroidSyncIntegrationTest.php
```

`SYNC_TEST_GRADLE` é opcional se `gradle` 8.9 estiver no `PATH`.
Se o ambiente não identificar corretamente o PHP CLI, informe também
`SYNC_TEST_PHP=/caminho/do/php`. A suíte exige `APP_ENV=testing` e o banco
`tarefas_test` configurados em `backend/phpunit.xml`; não apaga nem recria o banco.
O relatório detalhado fica em
`android/app/build/reports/tests/testDebugUnitTest/index.html`.

Os cenários cobrem importação inicial com tags padrão, alterações nas duas
direções, conflitos entre dois clientes, empate de horário, reenvio após perda
da resposta, fila offline preservada ao reabrir o banco, recorrências sem
duplicação, exclusões, lotes grandes, edição durante o envio, troca de conta e
token revogado. Os testes Android rodam no Robolectric: widgets, notificações,
interface e agendamento em segundo plano ainda exigem a validação no aparelho
descrita acima. Nas suítes normais, essa integração é pulada; o comando acima
exige que os cenários sejam executados, sem testes pulados.

## Backend

Requer PHP 8.3+, Composer e MySQL 8.4. Para o front, Node.js 22.12+ e npm.

### Ambiente com Podman

O `compose.yaml` da raiz sobe o backend isolado do restante da máquina, sem
root: `db` (MySQL 8.4 com `tarefas` e `tarefas_test`, num volume próprio) e
`app` (PHP 8.5, Composer e Node 22, imagem de `dev/Containerfile`). O Android
continua no Android Studio da máquina. Requer Podman e `podman-compose`
(`uv tool install podman-compose` ou `sudo dnf install podman-compose`).

| Serviço | Endereço na máquina | Limite de memória |
|---|---|---|
| `db` | `127.0.0.1:3307` (a 3306 fica para outro MySQL) | 512 MB |
| `app` | `0.0.0.0:8000`, acessível pelo celular na rede local | 1 GB |

Primeira vez, a partir da raiz do repositório:

```sh
podman compose build
podman compose up -d db
cp backend/.env.example backend/.env       # já aponta para 127.0.0.1:3307
podman compose run --rm -T app composer install
podman compose run --rm -T app npm ci
podman compose run --rm -T app php artisan key:generate
podman compose run --rm -T app php artisan migrate
podman compose run --rm -T app npm run build
podman compose up -d                       # web e API em http://localhost:8000
```

No dia a dia: `podman compose up -d` para iniciar, `podman compose down` para
parar (os dados ficam no volume `tarefas_tarefas-mysql`; `down -v` os apagaria).
Comandos rodam com `podman compose run --rm -T app <comando>`, por exemplo
`php vendor/bin/phpunit`, `npm test` ou `php vendor/bin/pint --dirty`.
Logs do servidor: `podman logs -f tarefas_app_1`.

Dentro do container, `DB_HOST=db` e `DB_PORT=3306` vêm do compose e têm
precedência sobre o `.env`. Por isso o servidor usa `php -S` com o router do
Laravel: `php artisan serve` descarta as variáveis que também existem no
`.env` e tentaria `127.0.0.1:3307`. `vendor/` e `node_modules/` ficam na pasta
do projeto, para a IDE enxergá-los. A integração Android ↔ Laravel roda com o
PHP e o Gradle da máquina e usa o MySQL do container pelo `.env`.

### Instalação direta

```sh
cd backend
composer install
cp .env.example .env        # ajuste DB_PORT, DB_USERNAME e DB_PASSWORD
php artisan key:generate
php artisan migrate
npm ci
npm run build
php artisan serve           # Web em http://127.0.0.1:8000; API em /api/v1
```

O web permite criar a conta, navegar pelo calendário, buscar em todas as datas,
filtrar por tag e criar, editar, concluir ou excluir tarefas. Temas claro e escuro
ficam salvos no navegador. Tarefas recorrentes usam as mesmas regras do app.
Em desenvolvimento, rode também `npm run dev` em outro terminal para atualizar
o front automaticamente. Atalhos: `N` cria uma tarefa; `/` abre a busca.

Os testes usam o banco `tarefas_test`, que precisa existir no MySQL:

```sh
php artisan test
npm test
```

### Recuperação de senha

O web e o Android permitem solicitar a recuperação pelo e-mail da conta.
O link abre o formulário web, vale por 60 minutos e só pode ser usado uma vez.
Um novo envio após o intervalo de 60 segundos invalida o link anterior.
Solicitações repetidas nesse intervalo e e-mails não cadastrados recebem a
mesma mensagem de confirmação. Os endpoints também limitam tentativas por IP.

Ao salvar a nova senha, o servidor revoga os tokens dos aparelhos e as sessões
web da conta. Tarefas, tags, vínculos e cursores de sincronização são preservados.
O usuário entra novamente com o mesmo e-mail e a nova senha.

Para testar localmente sem enviar e-mails reais, configure em `backend/.env`:

```dotenv
APP_URL=http://192.168.1.10:8000
MAIL_MAILER=log
```

Substitua o IP pelo endereço do computador acessível pelo celular. `APP_URL`
é a base dos links enviados, mesmo quando a solicitação chega por outro endereço.
Para testar apenas no computador, também pode usar `http://127.0.0.1:8000`.
Após alterar a configuração, execute `php artisan config:clear`.

1. No web ou em **Conta e sincronização** do Android, escolha **Esqueci minha senha**.
2. Informe o e-mail de uma conta existente e solicite o link.
3. Com `MAIL_MAILER=log`, abra o link da mensagem registrada em
   `backend/storage/logs/laravel.log`; nenhum e-mail é enviado por esse driver.
4. Defina e confirme a nova senha. Entre novamente no web e no app e confira
   as tarefas, inclusive alterações que estavam pendentes no celular.
5. Confira que a senha anterior e a reutilização do link são recusadas.

Para entrega real de e-mails, configure `MAIL_MAILER=smtp`, os campos `MAIL_*`
do `.env.example` e o remetente do serviço de e-mail. Na publicação, `APP_URL`
deve apontar para o domínio HTTPS do produto. Os testes automatizados usam
notificações falsas e não enviam e-mails.

Validação: `php vendor/bin/phpunit tests/Feature/Api/PasswordRecoveryTest.php`,
`npm test` e os testes Android de `SyncApiTest`.

### Confirmação de e-mail

Novos cadastros recebem um link de confirmação por e-mail. Ele usa `APP_URL`,
é assinado e vale por 60 minutos. Pode ser aberto no navegador sem login,
inclusive em outro aparelho; confirma apenas o endereço da conta indicada no
link e não troca a conta conectada no navegador. Links alterados, expirados ou
referentes a um endereço antigo são recusados. Reabrir um link válido já usado
mantém a confirmação existente.

O site e **Conta e sincronização** no Android mostram se o e-mail está confirmado
e permitem solicitar outro link, com intervalo mínimo de um minuto por conta,
compartilhado entre os aparelhos. Contas existentes continuam disponíveis e
podem confirmar o endereço pelo mesmo fluxo. Nesta etapa, a confirmação pendente
não bloqueia tarefas nem sincronização. Confirmar não altera tokens, bancos,
cursores ou alterações offline. Uma falha no envio também não desfaz o cadastro;
o usuário pode entrar e solicitar o link novamente.

Teste local, usando a mesma configuração de e-mail da recuperação de senha:

1. Cadastre uma conta de teste no site ou entre em uma conta ainda não confirmada.
2. Use **Enviar link de confirmação** no site ou no Android.
3. Com `MAIL_MAILER=log`, abra o link da mensagem **Confirme seu e-mail — Tarefas**
   em `backend/storage/logs/laravel.log`. Nenhum e-mail real é enviado nesse modo.
4. Confira a página **E-mail confirmado** e volte ao site ou ao app. O site
   consulta o status ao recuperar o foco; o Android usa a verificação de sessão
   ao retomar e a cada 30 segundos em primeiro plano. **Já confirmei** também
   atualiza o status manualmente. Sem rede, o último status fica guardado.
5. Confira que tarefas e alterações pendentes continuam disponíveis.

Validação: `php vendor/bin/phpunit tests/Feature/Api/EmailVerificationTest.php`,
`npm test` e os testes Android de `SyncApiTest` e `SessionValidatorTest`.

### Rotas da API (`/api/v1`)

| Método | Rota | O que faz |
|---|---|---|
| POST | `register`, `login` | Cria a conta ou entra; devolve um token por aparelho |
| POST | `forgot-password` | Recebe `email` e solicita o link, sem precisar de login |
| POST | `reset-password` | Recebe `email`, `token`, `password` e `password_confirmation`; redefine a senha |
| POST | `logout` | Encerra a sessão deste aparelho |
| GET | `user` | Usuário logado |
| POST | `email/verification-notification` | Solicita confirmação para o e-mail da conta autenticada |
| POST | `sync` | Envia alterações offline e recebe tarefas/tags alteradas desde um cursor |
| GET | `tasks?date=&today=` | Tarefas que aparecem no dia (`today` é o dia no aparelho) |
| GET | `tasks?search=` | Busca por título e descrição em todas as datas |
| GET | `tasks?start=&end=&today=&completed=0` | Pendentes no intervalo de dias exibidos |
| GET | `tasks?completed=1` | Concluídas em todas as datas |
| GET | `tasks/pending-counts?start=&end=&today=` | Pendentes por dia, para o calendário |
| POST, GET, PATCH, DELETE | `tasks`, `tasks/{id}` | Criar, ver, editar e excluir |
| POST | `tasks/{id}/toggle` | Concluir ou desmarcar; recorrentes geram a próxima ocorrência |
| POST, GET, PATCH, DELETE | `tags`, `tags/{id}` | Tags do usuário |

Envie `Authorization: Bearer <token>` nas rotas autenticadas.
No web, `POST /login`, `/register` e `/logout` usam a sessão com proteção CSRF;
as operações de tarefas e tags usam a mesma API por cookies, sem guardar tokens
no navegador.

### Sincronização (`POST /api/v1/sync`)

O Android usa este endpoint com o token por aparelho recebido no login. Para receber o estado inicial:

```json
{"cursor": 0, "tasks": [], "tags": []}
```

`cursor` é obrigatório e pertence à conta. `tasks` e `tags` são opcionais, com no
máximo 100 registros de cada tipo por chamada. Envie estados completos, não
comandos como “alternar conclusão” nem apenas os campos editados:

| Registro | Campos enviados |
|---|---|
| Tarefa | `id`, `title`, `description`, `date`, `original_date`, `deadline`, `priority`, `recurrence`, `series_id`, `completed`, `created_at`, `updated_at`, `deleted_at`, `tag_ids` |
| Tag | `id`, `name`, `color_hex`, `updated_at`, `deleted_at`; `created_at` é opcional |

Os IDs são UUIDs gerados no aparelho. `priority` usa `LOW`, `MEDIUM` ou `HIGH`;
`recurrence` aceita `DAILY` ou `WEEKLY:1,3,5` (dias ISO, de 1 a 7).
Datas de calendário continuam como `YYYY-MM-DD`, sem conversão de fuso.
`deadline`, `recurrence` e `series_id` devem estar presentes, podendo ser `null`.
`description` pode ser vazia; `tag_ids` pode ser `[]`.

Nesta rota, `created_at`, `updated_at` e `deleted_at` são números inteiros em
**milissegundos desde Unix epoch**, como no Room. `deleted_at: null` significa
registro ativo; excluir envia o registro completo com `deleted_at` preenchido e
`updated_at` atualizado. Os horários de criação e exclusão não podem superar
`updated_at`. Horários negativos ou mais de cinco minutos à frente do servidor
são recusados; o aparelho deve usar relógio automático. A criação de um registro
existente é preservada. Não envie `user_id` ou `sync_version`: são do servidor.

A resposta tem o formato:

```json
{
  "data": {
    "cursor": 12,
    "server_time": 1735689600000,
    "tasks": [],
    "tags": []
  }
}
```

As listas contêm os estados completos alterados após o cursor recebido,
incluindo excluídos, e o estado vencedor de **todo ID enviado**, mesmo se uma
edição antiga foi ignorada. Cada registro também traz `sync_version`; os horários
da resposta usam os mesmos milissegundos da entrada. Com `cursor: 0`, retorna
toda a conta. Nesta primeira versão, a resposta incremental não tem paginação.

- Conflitos comparam `updated_at` por registro: vence a alteração mais recente.
  Em empate, mantém o estado do servidor. Um UUID já excluído não é restaurado
  por uma atualização posterior, evitando o retorno de ocorrências excluídas.
- Um lote é atômico. Reenvios idênticos não duplicam dados nem avançam a versão.
  Dados inválidos, cursor à frente do servidor e IDs de outra conta retornam
  `422`, sem gravar parte do lote. Sem autenticação, retorna `401`.
- Tags podem ser criadas junto com as tarefas que as usam. Referências a tags
  próprias já excluídas são removidas; tags desconhecidas ou de outra conta são
  recusadas. Excluir uma tag também envia as tarefas das quais ela saiu.
- Recorrências chegam como estado: o Android envia a tarefa concluída e a próxima
  ocorrência no mesmo lote, usando o UUID determinístico da série/data. A rota
  não executa novamente a conclusão nem cria ocorrências adicionais.
- O Android deve aplicar a resposta e salvar o novo cursor na mesma transação
  local, sem sobrescrever edições feitas enquanto a chamada estava em andamento.
  Só então confirma os envios correspondentes. Em falha de rede, repete o lote
  com o cursor anterior. O cursor e a fila de envio devem ser separados por conta.

A importação inicial mantém IDs locais; tags de nomes iguais não são mescladas
automaticamente. O Android concilia as tags padrão antes do primeiro envio.
