# Tarefas

App Android (Kotlin + Jetpack Compose + Room/SQLite) para organizar tarefas com
calendário. Tarefas não concluídas até o fim do dia viram automaticamente
pendentes no dia seguinte (e mostram "Atrasada desde ...").

100% offline, sem login, banco SQLite local via Room.

## Como abrir e rodar

1. Instale o [Android Studio](https://developer.android.com/studio) (versão
   Koala/2024.1 ou mais recente).
2. Abra esta pasta (`app_tarefas`) direto no Android Studio: **File > Open**.
3. Aguarde o Gradle sincronizar (a IDE baixa o wrapper e as dependências
   automaticamente na primeira sincronização).
4. Conecte um celular Android (com depuração USB ativada) ou crie um
   emulador, e clique em **Run ▶**.

Requer minSdk 26 (Android 8.0+).

## Estrutura

- `data/` — entidade `Task`, `TaskDao`, `AppDatabase` (Room) e `TaskRepository`
  (inclui a lógica de rollover de tarefas atrasadas).
- `viewmodel/` — `TaskViewModel`, estado da tela via `StateFlow`.
- `ui/` — telas Compose: calendário mensal, lista de tarefas do dia,
  diálogo de nova tarefa.
