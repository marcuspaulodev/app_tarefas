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
