# Tarefas

Organizador de tarefas com calendário. Tarefas não concluídas até o fim do dia
aparecem como pendentes no dia seguinte (com o aviso "Atrasada desde ...").

## Estrutura

- `android/` — app Android (Kotlin + Jetpack Compose + Room/SQLite). Funciona
  offline e, em breve, sincroniza com o backend.
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

Requer minSdk 26 (Android 8.0+). Testes: `./gradlew :app:testDebugUnitTest`
dentro de `android/` (ou pelo Android Studio).

## Backend

Requer PHP 8.3+, Composer e MySQL 8.4. Para o front, Node.js 22.12+ e npm.

```sh
cd backend
composer install
cp .env.example .env        # ajuste DB_USERNAME e DB_PASSWORD
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

### Rotas da API (`/api/v1`)

| Método | Rota | O que faz |
|---|---|---|
| POST | `register`, `login` | Cria a conta ou entra; devolve um token por aparelho |
| POST | `logout` | Encerra a sessão deste aparelho |
| GET | `user` | Usuário logado |
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

A API está pronta; a conexão do Android será feita na próxima etapa. O endpoint
usa o mesmo token por aparelho das demais rotas. Para receber o estado inicial:

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
automaticamente. A etapa Android fará a conciliação das tags padrão antes do envio.
