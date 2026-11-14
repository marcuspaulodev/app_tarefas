# Decisões de produto e arquitetura

Registro consolidado em **08/11/2026**, com a implementação até o
commit `c8a42bb232127448c43ea610febd0e2511899f79` da `main` (merge do PR #9).
O histórico de entregas e o backlog estão em [RETOMADA.md](RETOMADA.md).
Atualizar este registro quando uma decisão mudar; identificar o que foi
substituído em vez de tratar propostas antigas como requisitos atuais.

## D01 — Produto e escopo

O **Tarefas** é um organizador de tarefas com calendário. A descrição de uma
tarefa atende ao texto livre; não existe um módulo separado de notas.

O usuário definiu uso pessoal inicialmente, com intenção de vender no futuro.
Por isso, contas e isolamento de dados já fazem parte da base, enquanto planos
e cobrança ficam para uma etapa posterior. iPhone não está no escopo atual.
Equipes e tarefas compartilhadas não têm implementação nem etapa ativa.

Subtarefas foram entregues como checklist dentro da tarefa (D14), depois de a
base de sincronização ficar pronta. Não confundir descrição de tarefa com checklist.

## D02 — Stack e organização

| Área | Decisão implementada | Motivo |
|---|---|---|
| Backend | Laravel + MySQL, um serviço com Actions de negócio | Preferência explícita do usuário, que já conhece essa stack; operação inicial simples |
| Web | SPA em Vue, dentro de `backend/resources/js`, construída pelo Vite | Compartilha backend e API com o Android; aprovada pelo usuário |
| Android | Kotlin + Jetpack Compose + Room/SQLite | Preserva funcionamento offline, widget e lembretes nativos |
| Repositório | `android/`, `backend/` e `shared/` no mesmo Git | Evoluir contrato e regras dos dois clientes juntos |
| Regras compartilhadas | Casos JSON consumidos pelos testes PHP e Kotlin | Detectar divergências sem trocar a stack do backend |

O web depende de conexão. O Android usa o banco local para a interface e
sincroniza quando a conta e a rede estão disponíveis. Não há microsserviços,
Kubernetes ou arquitetura de filas distribuídas implantados.

A sugestão inicial de Spring/PostgreSQL/React foi substituída pela escolha
Laravel/MySQL/Vue. Kotlin permanece no Android. Login Google/Socialite e
OpenAPI/Scramble foram ideias iniciais, não entregas existentes nem dependências
autorizadas automaticamente para a próxima etapa.

Fontes: [Composer](../backend/composer.json), [pacotes web](../backend/package.json),
[build Android](../android/app/build.gradle.kts),
[casos compartilhados](../shared/fixtures/rules.json).

## D03 — Dia exibido, atraso e prazo

O atraso é calculado na leitura. Uma tarefa pendente com data anterior a hoje
aparece em hoje, sem atualizar a linha só porque o dia passou. Isso evita que a
virada do dia se torne uma edição capaz de sobrescrever mudanças de outro cliente.

`date` guarda o dia da tarefa e `originalDate`/`original_date` sustenta o aviso
“Atrasada desde”. Manter o dia exibido ao editar preserva a origem do atraso;
escolher outro dia redefine as duas datas. Concluir registra o dia em que a
tarefa aparecia. Datas de calendário são `YYYY-MM-DD`, sem conversão para UTC;
“hoje” vem do cliente para as operações que precisam dele.

Prazo é um campo separado da data no calendário, com indicação visual e lembrete
no Android. Ele não é uma segunda regra de reagendamento automático da tarefa.

Fontes: [Task.kt](../android/app/src/main/java/com/marcuspaulo/tarefas/data/Task.kt),
[TaskDao.kt](../android/app/src/main/java/com/marcuspaulo/tarefas/data/TaskDao.kt),
[Task.php](../backend/app/Models/Task.php),
[UpdateTask](../backend/app/Actions/Tasks/UpdateTask.php).

## D04 — IDs, recorrências e exclusões

Tarefas e tags usam UUIDs gerados no cliente; novos IDs usam UUID v7. Isso permite
criar registros offline sem colidir com IDs de outros aparelhos.

A próxima ocorrência recorrente nasce somente ao concluir a atual. O modelo
usa `seriesId`/`series_id` e um UUID determinístico derivado da série e da data.
Concluir, desmarcar e concluir novamente não pode duplicar a próxima ocorrência.
Uma ocorrência excluída também não pode reaparecer por essa operação.

As regras atuais são diária e semanal por dias ISO, de 1 a 7. A próxima
ocorrência usa a data de conclusão/exibição como base, preserva título,
descrição, prioridade, tags e regra; o prazo não é copiado automaticamente.
Do checklist, a próxima ocorrência recebe só os itens pendentes (D14).

Exclusões usam `deletedAt`/`deleted_at`. A linha permanece como registro de
exclusão para avisar os demais clientes. Não trocar por exclusão física sem
projetar a propagação e a retenção dos registros excluídos.

Fontes: [Ids.kt](../android/app/src/main/java/com/marcuspaulo/tarefas/data/Ids.kt),
[OccurrenceId.php](../backend/app/Support/OccurrenceId.php),
[ToggleTaskCompletion](../backend/app/Actions/Tasks/ToggleTaskCompletion.php).

## D05 — Migrations e backups locais

O Room está na versão **7**, com schemas 4 a 7 versionados. A migration 4→5
converteu IDs, acrescentou série e timestamps de sincronização; a 5→6 acrescentou
o estado de sincronização e a marca de alterações pendentes; a 6→7 acrescentou o
checklist, vazio nas tarefas existentes e sem marcá-las para reenvio. A preservação dos
dados é coberta por testes.

O fallback destrutivo geral foi removido. Continua existindo uma exceção legada
somente para versões 1, 2 e 3, que não tinham migrations. A partir da versão 4,
uma migration ausente deve falhar preservando o banco, em vez de apagá-lo.

O backup JSON está no formato 2 e lê o formato antigo com IDs numéricos.
No espaço apenas local, a restauração substitui os dados após confirmação.
Em conta conectada, a importação acrescenta cópias com IDs novos, sem substituir
o servidor. Um backup portátil exportado de uma conta também recebe IDs novos
ao ser importado em outro espaço; isso evita reutilizar identidades de outra conta.

Fontes: [Migrations.kt](../android/app/src/main/java/com/marcuspaulo/tarefas/data/Migrations.kt),
[AppDatabase.kt](../android/app/src/main/java/com/marcuspaulo/tarefas/data/AppDatabase.kt),
[BackupManager.kt](../android/app/src/main/java/com/marcuspaulo/tarefas/backup/BackupManager.kt).

## D06 — Versões e conflitos de sincronização

`updated_at` decide conflitos, mas não é cursor de sincronização. O servidor
reserva um `sync_version` monotônico por usuário, com trava na linha do usuário
dentro da transação. As escritas do web e as recebidas do Android participam
desse mesmo fluxo, evitando perder alterações por empate de horário.

No conflito de um registro, vence o `updated_at` mais recente; em empate, o
servidor mantém seu estado. Um ID já excluído não volta por uma atualização
posterior. Edições feitas no servidor recebem timestamp posterior ao estado
editado, mesmo se o relógio que o originou estava um pouco adiantado.

O `/api/v1/sync` recebe estados completos, não comandos como “alternar conclusão”.
Um lote é atômico, aceita até 100 tarefas e 100 tags e confirma o estado vencedor
de cada ID enviado. Reenvios idênticos não duplicam registros nem avançam a
versão sem necessidade. A resposta também contém as mudanças posteriores ao
cursor. A primeira versão não pagina a resposta incremental.

Timestamps de sync são inteiros em milissegundos desde Unix epoch. Há validação
de relógio: criação/exclusão no futuro além da tolerância de cinco minutos é
recusada. Os detalhes do contrato estão no [README](../README.md).

Fontes: [Synchronize.php](../backend/app/Actions/Sync/Synchronize.php),
[User::claimNextSyncVersion](../backend/app/Models/User.php),
[HasSyncTimestamp](../backend/app/Models/Concerns/HasSyncTimestamp.php),
[SyncRequest](../backend/app/Http/Requests/Api/V1/SyncRequest.php).

## D07 — Fila offline e aplicação das respostas

O Android marca alterações com `needsSync`, captura o lote em transação e faz a
chamada de rede fora da transação do Room. Aplica a resposta e grava o cursor
juntos, sem sobrescrever uma edição local feita durante a requisição.
Há um ciclo por conta, proteção contra respostas atrasadas e reenvio em falhas
de conexão. Tarefa concluída e próxima ocorrência pendente são agrupadas no envio.

Antes do primeiro envio, o Android busca o catálogo remoto e concilia as tags
padrão Trabalho, Casa e Faculdade. O modo local ainda cria suas tags padrão;
o backend também as cria no cadastro. A conciliação evita duplicar essas três
tags. Tags arbitrárias de mesmo nome não são mescladas automaticamente.

Há sincronização após alterações, ao retomar o app e periodicamente. O worker
periódico usa intervalo de 15 minutos, sujeito ao agendamento do Android; o
app em primeiro plano solicita atualização a cada 30 segundos. O usuário pode
usar “Sincronizar agora”. Não é comunicação em tempo real por WebSocket.

Fontes: [SyncEngine.kt](../android/app/src/main/java/com/marcuspaulo/tarefas/sync/SyncEngine.kt),
[SyncWorker.kt](../android/app/src/main/java/com/marcuspaulo/tarefas/sync/SyncWorker.kt).

## D08 — Contas e autenticação

O web usa sessão/cookie com CSRF, inclusive nas chamadas à API Sanctum. O Android
usa um token Sanctum por aparelho. Não foi implementado o modelo inicialmente
sugerido de token curto com refresh token; a configuração atual de expiração
global do Sanctum é `null`, e logout/redefinição de senha revogam os tokens.

O token Android é protegido pelo Keystore; a senha não é armazenada. A sessão
fica fora do backup automático, enquanto o vínculo entre contas e bancos é
preservado com os dados.

O espaço local é identificado pelo servidor normalizado e pelo ID do usuário.
A primeira conta assume o banco local legado; outras contas têm bancos e
cursores separados. Mudar de IP/domínio pode selecionar outro espaço mesmo com
o mesmo e-mail: usar uma URL consistente e planejar a transição para produção.

Sair guarda os dados e a fila da conta para o próximo login. O app não deve
expor essa base como modo convidado depois do logout. Widgets, ações e lembretes
devem usar somente a conta ativa. No backend, operações são limitadas ao dono
dos dados, com testes de isolamento e respostas 404 para recursos de outra conta.

Fontes: [SessionStore.kt](../android/app/src/main/java/com/marcuspaulo/tarefas/sync/SessionStore.kt),
[Sanctum](../backend/config/sanctum.php),
[Policies](../backend/app/Policies),
[OwnershipTest](../backend/tests/Feature/Api/OwnershipTest.php).

## D09 — Recuperação de senha e encerramento da sessão

O site e o Android solicitam o link; a nova senha é definida no navegador.
O broker Laravel guarda o token como hash, com validade de 60 minutos e consumo
único. Novo envio depois do intervalo de 60 segundos invalida o token anterior.
Pedidos para e-mails inexistentes e pedidos durante o intervalo recebem a
mesma confirmação genérica, sem revelar contas. Há limites adicionais por rota.

A redefinição usa transação e trava do token, altera a senha, gira o
`remember_token`, revoga todos os tokens da conta e remove suas sessões no
driver de banco. O login web registra o hash de senha na sessão para que a
autenticação também detecte uma sessão antiga. Dados e cursores são preservados.

Foi pedido explicitamente que trocar a senha deslogue o app. Ao receber 401,
o Android descarta a credencial, exige novo login e fecha o acesso às tarefas,
widgets e lembretes daquela conta. Guarda o banco e a fila para a mesma conta.
A checagem ocorre ao abrir/retomar e a cada 30 segundos em primeiro plano,
inclusive na tela de conta, independentemente do backoff da sincronização.
Sem rede, só é possível detectar a revogação no próximo contato com o servidor.

Fontes: [ResetAccountPassword](../backend/app/Actions/Auth/ResetAccountPassword.php),
[PasswordController](../backend/app/Http/Controllers/Api/V1/PasswordController.php),
[SessionValidator.kt](../android/app/src/main/java/com/marcuspaulo/tarefas/sync/SessionValidator.kt).

Ao entrar no site a partir da tela de redefinição (ou de outra página que não a
inicial), o web recarrega a página inicial em vez de abrir o espaço na mesma
página. A tela de redefinição usa `Referrer-Policy: no-referrer` para não vazar o
token, e sem `Referer`/`Origin` o Sanctum não trata as chamadas GET seguintes como
do site: elas respondiam 401 e mostravam “Sua sessão terminou” logo após um login
bem-sucedido (corrigido em 14/11/2026).

## D10 — Confirmação de e-mail

O cadastro envia a confirmação; contas antigas ainda não verificadas podem
solicitar o link depois do login. O link é assinado, dura 60 minutos e vincula o
ID da conta ao hash do endereço atual. Pode ser aberto sem login no navegador,
para funcionar também quando o pedido vem do Android. Não autentica o visitante
nem troca a conta do navegador. Reabrir um link válido é idempotente.

O reenvio exige autenticação e usa apenas o e-mail da conta autenticada. Há
intervalo de um minuto por conta/endereço, compartilhado entre aparelhos, além
do limite da rota. Uma falha no mailer não desfaz o cadastro; o reenvio informa
que o serviço falhou, permitindo tentar depois.

**A confirmação pendente não bloqueia tarefas nem sincronização nesta etapa.**
Tornar a confirmação obrigatória exige uma decisão de produto futura e um
tratamento para contas existentes; não adicionar esse bloqueio implicitamente.

O web atualiza o status ao recuperar o foco ou em “Já confirmei”. O Android usa
a checagem de sessão e a ação manual, persistindo o último status para offline.
Atualizar esse metadado não invalida uma sincronização da mesma credencial;
respostas de outra conta ou de um login anterior continuam sendo descartadas.

Os links de confirmação e recuperação usam o `APP_URL` configurado, não o host
fornecido por quem fez a requisição. As páginas desses links usam `no-store` e
`no-referrer`. O teste local usa o mailer `log`; entrega real ainda está pendente.

Fontes: [SendEmailVerification](../backend/app/Actions/Auth/SendEmailVerification.php),
[EmailVerificationController](../backend/app/Http/Controllers/Api/V1/EmailVerificationController.php),
[AppServiceProvider](../backend/app/Providers/AppServiceProvider.php),
[useEmailVerification](../backend/resources/js/composables/useEmailVerification.js).

## D11 — Visual e experiência

O usuário escolheu a proposta visual **B** para o web e pediu também o tema
escuro. A implementação aprovada usa calendário lateral, área principal de
tarefas, busca e filtros, com cores de destaque amarelas e tipografia Atkinson
Hyperlegible/Bricolage Grotesque. As fontes ficam no repositório com suas licenças.

Temas claro/escuro são persistidos no navegador. Recuperação de senha e
confirmação de e-mail seguem esse visual. Atalhos `N` e `/` valem na área de
tarefas, não nas páginas de autenticação/recuperação/confirmação. O Android
continua com a interface nativa Compose e seus temas.

Fontes: [App.vue](../backend/resources/js/App.vue),
[CSS](../backend/resources/css/app.css),
[fontes](../backend/resources/fonts),
[tema Android](../android/app/src/main/java/com/marcuspaulo/tarefas/ui/theme/Theme.kt).

## D12 — Widget e notificações

O widget mostra pendentes até hoje e permite concluir. As chaves numéricas de
itens derivadas de UUID devem permanecer estáveis e não negativas: isso corrigiu
o erro “Não é possível mostrar conteúdo” do Glance após a migration.

O lembrete de prazo é programado para 9h no fuso do aparelho via WorkManager.
A checagem diária usa 8h, 13h e 18h, avisando quando há pendências. Usa alarme
exato quando permitido e alternativa não exata quando não permitido. Esses
horários estão no código; pontualidade depende das permissões e do sistema.

Validar widgets, alarmes e execução em segundo plano no aparelho. Robolectric
e testes de sincronização não substituem esse teste de interface e de sistema.

Fontes: [widget](../android/app/src/main/java/com/marcuspaulo/tarefas/widget),
[notificações](../android/app/src/main/java/com/marcuspaulo/tarefas/notifications).

## D13 — Validação e operação

Testes de domínio compartilhados, API, isolamento, migrations, backup e
sincronização acompanham o código. Existe integração optativa entre o cliente
HTTP/Room Android e Laravel real, usando exclusivamente `tarefas_test`.
O usuário também valida etapas no celular antes dos pedidos de commit/PR/merge.

Ainda não há CI configurado. Os resultados da última etapa, os comandos e os
limites estão em [RETOMADA.md](RETOMADA.md). SMTP, hospedagem, domínio de produção,
backups remotos e distribuição Android ainda não foram configurados.

O servidor de desenvolvimento deve escutar na rede para atender ao celular.
HTTP é aceito no app debug somente para destinos locais; release exige HTTPS.
Uma falha de conexão após trocar a senha foi causada pelo servidor restrito a
`127.0.0.1`, e não por rejeição da nova senha. Conferir conectividade antes de
alterar credenciais ou dados de conta.

## D14 — Checklist dentro da tarefa

Decidido com o usuário em 14/11/2026:

| Pergunta | Decisão |
|---|---|
| Marcar o último item | Pergunta se quer concluir a tarefa (“Checklist completo”); não conclui sozinho. Concluir a tarefa não marca os itens |
| Próxima ocorrência recorrente | Recebe só os itens que ficaram pendentes, desmarcados, na mesma ordem e com os mesmos IDs. Se todos foram marcados, nasce sem checklist |
| Onde marcar | Direto na lista: o indicador “2/5” abre os itens. Criar, editar, reordenar (setas) e remover ficam no formulário da tarefa |
| Widget | Mostra só o progresso “2/5” ao lado do título |

O checklist é uma lista JSON `[{id, text, done}]` na própria tarefa (coluna
`checklist` no MySQL e no Room), e não uma tabela própria. Assim ele entra sem
código novo na sincronização, nos conflitos, nas exclusões e no backup. O custo
aceito é que o conflito continua por tarefa: duas edições simultâneas do mesmo
checklist em aparelhos diferentes resolvem pela última alteração da tarefa
inteira (D06). Se o uso mostrar perdas reais, migrar para itens com versão própria.

Limites: até 50 itens, texto de 1 a 255 caracteres sem espaços nas pontas, IDs
UUID únicos dentro da tarefa. Os mesmos IDs podem existir em tarefas diferentes,
porque as ocorrências de uma série os herdam; por isso a validação é uma regra
única (`ChecklistItems`) e não `distinct` com curinga, que compararia todas as
tarefas do lote. O MySQL reordena as chaves do JSON, então o model devolve sempre
`id`, `text`, `done`. Na sincronização, a ausência do campo preserva o checklist
guardado, para um app anterior não apagar itens criados no web. O backup JSON
continua no formato 2, com `checklist` opcional. A busca não procura nos itens.

A regra da próxima ocorrência está em `shared/fixtures/rules.json`
(`next_checklist`) e é testada no PHP, no Kotlin e no JavaScript.

Fontes: [Checklist.php](../backend/app/Support/Checklist.php),
[ChecklistItems](../backend/app/Rules/ChecklistItems.php),
[Checklist.kt](../android/app/src/main/java/com/marcuspaulo/tarefas/data/Checklist.kt),
[checklist.js](../backend/resources/js/lib/checklist.js).
