# Estado do projeto e retomada

**Atualizado em 08/11/2026 — fuso America/Fortaleza.**

Este registro consolida o trabalho até o PR #9. A base funcional é a `main` no
commit `c8a42bb232127448c43ea610febd0e2511899f79`. Nesse ponto, os PRs de
implementação #1 a #9 estavam mergeados e a `main` local correspondia ao GitHub.
A documentação de retomada foi iniciada depois disso na branch
`documentacao-retomada`. Confirmar branch, alterações locais e remoto antes de
continuar; não presumir que este retrato datado seja o estado corrente do Git.

Repositório: [marcuspaulodev/app_tarefas](https://github.com/marcuspaulodev/app_tarefas).
Decisões e motivos: [DECISOES.md](DECISOES.md).
Operação e API: [README.md](../README.md).
Orientações para agentes: [AGENTS.md](../AGENTS.md).

## Retomada rápida

1. Ler este documento e as decisões relacionadas à etapa solicitada.
2. Conferir `git status --short --branch`, o histórico e a base remota. Preservar
   quaisquer alterações locais, inclusive esta documentação se ainda não
   estiver commitada.
3. Escolher a próxima entrega conforme o pedido atual. A recomendação deixada
   foi **envio real de e-mails**, seguida da publicação do backend e do web.
   Essas etapas ainda não foram iniciadas.
4. Trabalhar em uma branch própria por etapa. Para e-mails, o nome sugerido é
   `email-producao`; esse nome é uma proposta, não uma branch já criada.
5. Não reaplicar comandos de primeira instalação ao ambiente existente sem
   necessidade: preservar `.env`, `APP_KEY`, bancos e dados do aparelho.
6. Testar a alteração, entregar um roteiro curto para o celular e executar
   commit/PR/merge conforme a autorização vigente.

O último pedido que motivou estes arquivos foi salvar o contexto, as decisões e
as pendências para retomar depois. Nenhuma nova funcionalidade foi implementada
como parte dessa documentação.

## Entregas concluídas

Antes dos PRs, o app já tinha calendário, tarefas com descrição, tags,
prioridade, prazos, recorrências diária/semanal, busca e backup JSON. Foram
acrescentados o widget de pendentes e a checagem diária às 8h, 13h e 18h.

| Entrega | Conteúdo principal | PR | Merge na main |
|---|---|---|---|
| Proteção do banco e preparação do sync | Migrations explícitas a partir da versão 4; atraso calculado na leitura | [#1](https://github.com/marcuspaulodev/app_tarefas/pull/1) | `e449720` |
| Migration v5 | UUID, série, timestamps, exclusão lógica, recorrência sem duplicar e backup compatível | [#2](https://github.com/marcuspaulodev/app_tarefas/pull/2) | `d615e4d` |
| Backend e organização | Pastas android/backend/shared; Laravel/MySQL/Sanctum; API; isolamento; correção das chaves do widget | [#3](https://github.com/marcuspaulodev/app_tarefas/pull/3) | `13b225a` |
| Web Vue | Login/cadastro, calendário, tarefas, tags, busca e visual B com temas claro/escuro | [#4](https://github.com/marcuspaulodev/app_tarefas/pull/4) | `64179cf` |
| Sincronização no backend | Lotes atômicos, cursor por usuário, conflitos, exclusões e estado das recorrências | [#5](https://github.com/marcuspaulodev/app_tarefas/pull/5) | `ec74812` |
| Sincronização Android | Login, Room v6, fila offline, importação inicial, bancos por conta, widgets e lembretes isolados | [#6](https://github.com/marcuspaulodev/app_tarefas/pull/6) | `5902f82` |
| Integração automatizada | Cliente Android/Room/HTTP contra Laravel real em banco de teste | [#7](https://github.com/marcuspaulodev/app_tarefas/pull/7) | `fe828a2` |
| Recuperação de senha | Links no web/Android, redefinição no navegador, revogação de sessões e login obrigatório no app | [#8](https://github.com/marcuspaulodev/app_tarefas/pull/8) | `0139d39` |
| Confirmação de e-mail | Envio no cadastro, reenvio, links assinados, status no web/Android e preservação da sincronização | [#9](https://github.com/marcuspaulodev/app_tarefas/pull/9) | `c8a42bb` |

O último commit de implementação é `e2a7a0e` (confirmação de e-mail), já incluído
no merge do PR #9. Os detalhes de implementação continuam no histórico do Git;
as decisões que explicam essas mudanças estão no documento complementar.

## Estado validado da última etapa

Em 08/11/2026, na etapa de confirmação de e-mail:

| Verificação | Resultado registrado |
|---|---|
| PHPUnit/backend | 119 testes no relatório: 118 aprovados, 1 integração optativa ignorada; 752 asserções |
| JavaScript | 19 testes aprovados |
| Android | 88 testes no relatório: 78 aprovados, 10 cenários de integração optativos ignorados |
| Build web | Produção gerada pelo Vite |
| Build Android | APK debug gerado e instalado por atualização, preservando os dados |
| Android lint | Sem erros; 18 avisos preexistentes |
| Formatação/diff | Pint e `git diff --check` aprovados |
| Validação manual | Usuário solicitou confirmação, abriu o link no navegador e viu “E-mail confirmado” no celular |

Nas etapas anteriores, o usuário também confirmou a sincronização e o novo login
após redefinir a senha. A conta usada no teste teve a confirmação registrada no
banco; não é necessário repetir ou alterar esse dado para retomar o projeto.

Os testes optativos Android ↔ Laravel **não foram reexecutados na última etapa**.
Há comandos próprios para eles abaixo. Widgets, alarmes, interface e execução
em segundo plano continuam exigindo teste no aparelho. Não há CI configurado;
os resultados acima são locais, não checks executados pelo GitHub.

## Pendências do roteiro

### E01 — Envio real de e-mails — próxima entrega sugerida

Estado: não iniciado. Hoje `MAIL_MAILER=log` registra a mensagem localmente.

- Definir provedor SMTP e remetente; obter domínio/acessos necessários por
  entrada segura, sem incluir credenciais no código ou nestes arquivos.
- Configurar o ambiente e os links por `APP_URL`, conforme o destino acessível
  no computador/celular. Um endereço local não é uma publicação na internet.
- Validar entrega real da recuperação de senha e da confirmação de e-mail para
  uma caixa de teste autorizada, incluindo o fluxo de abrir os links.
- Preservar os comportamentos atuais de expiração, reenvio e falha no mailer.

Critério de conclusão: as duas mensagens chegam, os links funcionam no ambiente
escolhido e os fluxos seguem preservando contas e dados. O provedor e o remetente
ainda precisam ser definidos; não presumir um serviço já contratado.

### E02 — Publicar backend e web

Estado: ambos funcionam localmente; não há deploy de produção registrado.

- Definir hospedagem, domínio e acessos. Usar HTTPS e configurar a aplicação,
  cookies/sessões, URL pública e banco para esse ambiente.
- Preparar processo de deploy, migrations e atualização de assets, com
  possibilidade de recuperação se uma implantação falhar.
- Configurar backups do MySQL fora do servidor e testar restauração. Backup
  JSON do celular já existe, mas não substitui backup operacional do servidor.
- Verificar login, recuperação, confirmação e sincronização por uma rede externa.
- Planejar a mudança do endereço usado pelo Android: servidor faz parte da
  identidade do espaço local. Preservar a fila offline e os dados existentes.

Critério de conclusão: site e API acessíveis por HTTPS, cliente sincronizando
com o servidor publicado e procedimento de backup/restauração validado.

### E03 — Subtarefas/checklists

Estado: adiado durante a preparação da sincronização; ainda sem implementação.

- Definir criação, edição, ordenação e conclusão dos itens dentro de uma tarefa.
- Definir relação entre conclusão dos itens e da tarefa, além do comportamento
  dos itens ao gerar uma nova ocorrência recorrente.
- Evoluir schema/API/Room com migrations que preservem dados; incluir os itens
  na sincronização, nos conflitos, nas exclusões e no backup.
- Entregar no web e no Android e testar alterações offline entre clientes.

Critério de conclusão: o checklist se comporta de forma consistente nos dois
clientes, inclusive em recorrências e sem conexão. Esses detalhes de produto
ainda não foram fechados.

### E04 — Planos e cobrança

Estado: futuro, quando o usuário decidir comercializar.

Definir planos, recursos pagos, provedor e fluxo de cobrança. Cashier foi
mencionado como possibilidade, mas não foi instalado nem escolhido como uma
integração já definida. Conferir as regras vigentes dos canais de distribuição
antes de decidir como vender assinaturas. Esta documentação não fixa requisitos
legais ou de loja como se já estivessem verificados.

## Preparações recomendadas para distribuição

### E05 — Release Android

Estado: usamos build debug no aparelho; distribuição oficial não foi preparada.

- Revisar nome do produto e `applicationId` antes da distribuição. O ID atual
  é `com.marcuspaulo.tarefas`; `versionCode=1` e `versionName=1.0`.
- Definir assinatura de produção e armazenamento seguro da chave, gerar
  APK/AAB e estabelecer versionamento das atualizações.
- Configurar o endereço oficial do servidor e validar a exigência de HTTPS.
- Planejar transição de assinatura debug para release e de servidor local
  para produção, preservando os dados do usuário.

Critério de conclusão: pacote de produção testado, com estratégia de atualização
e preservação de dados. Publicação em loja depende do canal escolhido pelo usuário.

### E06 — CI para PRs

Estado: o repositório não tem workflows de CI. Essa melhoria foi recomendada.

Automatizar testes PHP/MySQL e JavaScript, build web, testes/build/lint Android.
Preparar JDK/SDK/Gradle no ambiente de CI; o executável `gradlew` ainda não está
no repositório. Decidir em que job executar a integração optativa sem disputar
`tarefas_test` com outra suíte. Não tratar um PR sem checks como se tivesse CI
aprovado: registrar quais verificações foram executadas localmente.

## Assuntos futuros ou decisões ainda abertas

- Exportação/exclusão de conta e textos de privacidade/termos foram mencionados
  no planejamento para outras pessoas/comercialização. Não estão entregues;
  o backup JSON atual não é um fluxo completo de gestão de conta. Definir o
  escopo e conferir requisitos aplicáveis ao público e canal escolhidos.
- Confirmar se algum dia o e-mail verificado será obrigatório. Atualmente a
  pendência permite tarefas e sincronização, por decisão da etapa entregue.
- Login Google/Socialite e contrato OpenAPI/Scramble foram sugestões antigas,
  não requisitos da próxima entrega. Avaliar apenas se houver demanda.
- iOS, equipes e tarefas compartilhadas não têm etapa ativa.
- Paginação do retorno de sync, retenção de registros excluídos e resolução de
  conflitos mais granular podem ser avaliadas conforme o volume/uso crescer.
  A versão atual retorna todas as alterações após o cursor e resolve por registro.

## Ambiente local e problemas já diagnosticados

Informações observadas em 07–08/11/2026; conferir novamente antes de usar.

| Item | Contexto útil |
|---|---|
| Projeto | `~/projetos/app_tarefas`; abrir `android/` no Android Studio |
| Backend | PHP local 8.5.12; Laravel instalado 13.40.0; MySQL 8.4; conferir versões pelo Composer antes de usar APIs |
| Bancos | `tarefas` para desenvolvimento e `tarefas_test` para testes |
| Web | Vue 3, Vite; Node 22 observado na preparação |
| Android | Gradle 8.9, JDK 21; AGP 8.6.0/Kotlin 2.0.20; minSdk 26, compileSdk/targetSdk 35 |
| JDK utilizado | `~/.jdks/jbr-21.0.11` — o Java 25 não funcionou com este Gradle |
| SDK | `~/Android/Sdk`; adb em `platform-tools/adb` |
| Aparelho de teste | Motorola moto g82 5G, conectado por USB durante os testes |
| E-mail local | `MAIL_MAILER=log`; mensagens em `backend/storage/logs/laravel.log` |
| URL dos links no último teste | `APP_URL=http://localhost:8000`; links foram abertos no computador |

### Acesso do celular ao servidor

No teste físico, o app usou `http://192.168.3.121:8000`, o IP do computador
naquela rede. Esse IP pode mudar. Ele é um registro do teste, não um endereço
para colocar como padrão de produção.

Uma falha apresentada como “Não foi possível conectar” depois da troca de senha
veio do servidor ouvindo apenas em `127.0.0.1`. O acesso pela LAN foi habilitado
e conferido a partir do telefone. Ao retomar, verificar o listener e a rede;
não presumir que os processos iniciados durante a sessão continuem ativos.

Para iniciar normalmente, dentro de `backend/`:

```sh
php artisan serve --host=0.0.0.0 --port=8000
```

Conferir processos existentes antes de usar a porta. No harness do T3, houve
casos em que `PHP_BINARY` apontava para o AppImage em subprocessos. A alternativa
usada foi executar `/usr/bin/php -S` com o router do Laravel, a partir de
`backend/public/`. Não servir a raiz do repositório como diretório público.

```sh
cd backend/public
/usr/bin/php -S 0.0.0.0:8000 ../vendor/laravel/framework/src/Illuminate/Foundation/resources/server.php
```

`APP_URL` define os links das mensagens; não define em quais interfaces o
servidor escuta. `localhost` no telefone é o próprio telefone. Para abrir o link
nele, configurar uma URL acessível e solicitar uma nova mensagem após
`php artisan config:clear`. Para abrir no computador, localhost continua válido.

### Assinatura do APK

A instalação pelo CLI encontrou `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: o Android
Studio Flatpak usa uma chave debug diferente da chave padrão do CLI.

- Chave do Android Studio: `~/.var/app/com.google.AndroidStudio/config/.android/debug.keystore`.
- Chave padrão do CLI: `~/.android/debug.keystore`.

A solução aplicada foi assinar o APK testado com a chave do Android Studio e
instalar por cima, preservando os dados. Usar o Android Studio com essa mesma
configuração também evita a divergência. Não desinstalar o aplicativo nem limpar
os dados para resolver esse erro. Essas chaves debug não são a assinatura de
produção e não devem entrar no Git.

### Logs e configuração

Erros antigos no log incluíram rota de login ausente, um erro de template e uma
digitação de comando. Eles não devem ser tratados como falhas atuais sem conferir
o horário. Ler somente o trecho relevante ao teste.

`.env`, senhas, `APP_KEY`, tokens, assinaturas de links, arquivos de assinatura e
dados pessoais não estão reproduzidos nestes documentos. A configuração local
existente deve ser preservada; para novos segredos, usar entrada segura.

## Comandos de validação

Estes comandos foram usados no ambiente local; não precisam ser executados
novamente apenas para ler ou atualizar a documentação.

### Backend e web

Executar em `backend/`, com o banco de teste preparado:

```sh
php vendor/bin/phpunit
npm test
npm run build
```

No harness do T3, chamar PHPUnit diretamente evitou problemas de subprocessos
vistos com `php artisan test`. Para alterações PHP, seguir as instruções locais
e executar `php vendor/bin/pint --dirty --format agent`.

### Android

Executar em `android/`. O caminho abaixo é o Gradle já instalado nesta máquina;
em outra, localizar a instalação 8.9 apropriada.

```sh
export JAVA_HOME="$HOME/.jdks/jbr-21.0.11"
tarefas_gradle="$HOME/.gradle/wrapper/dists/gradle-8.9-bin/90cnw93cvbtalezasaz0blq0a/gradle-8.9/bin/gradle"
"$tarefas_gradle" --no-daemon --console=plain :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`.
Relatórios: `android/app/build/reports/`.

### Integração Android ↔ Laravel

Executar em `backend/`. Não rodar simultaneamente com outra suíte que altere
`tarefas_test`. Não apontar esses testes para `tarefas` ou para produção.

```sh
JAVA_HOME="$HOME/.jdks/jbr-21.0.11" \
SYNC_TEST_GRADLE="$HOME/.gradle/wrapper/dists/gradle-8.9-bin/90cnw93cvbtalezasaz0blq0a/gradle-8.9/bin/gradle" \
SYNC_TEST_PHP=/usr/bin/php \
RUN_ANDROID_SYNC_TESTS=1 \
php vendor/bin/phpunit tests/Feature/Api/AndroidSyncIntegrationTest.php
```

A suíte inicia seu próprio servidor local em porta livre, usa contas temporárias
e limpa os dados dessas contas ao terminar. Os detalhes e o roteiro de validação
manual de widgets/notificações estão no [README](../README.md).

## Git, PRs e continuidade do registro

O usuário pede uma branch por etapa e costuma validar no aparelho antes de
solicitar commit/PR e depois merge. Concluir as ações quando autorizadas; não
inferir que o pedido relativo a um PR anterior autoriza merge do seguinte.

O conector GitHub conseguiu leitura, mas recusou escrita com HTTP 403
`Resource not accessible by integration`. O `gh` autenticado nesta máquina foi
usado para criar e mergear os PRs. Conferir o acesso novamente se o ambiente
mudar. Nenhum token de acesso deve ser copiado para os documentos.

No T3, vincular PRs criados/assumidos/atualizados com as ferramentas de link do
ambiente. Se uma ação solicitada depender de checks ou reviews pendentes, usar o
mecanismo de acompanhamento indicado pelas instruções da sessão.

Ao encerrar a próxima etapa, atualizar a data e a base deste registro, marcar
a entrega concluída com seu PR, registrar testes e validação manual e acrescentar
as decisões novas em [DECISOES.md](DECISOES.md). Isso mantém o contexto acessível
ao usuário e a agentes futuros sem depender do histórico do chat.
