# Orientações do projeto Tarefas

## Antes de retomar

1. Leia [docs/RETOMADA.md](docs/RETOMADA.md): entregas, estado validado,
   pendências, ambiente e próximos passos.
2. Consulte [docs/DECISOES.md](docs/DECISOES.md): decisões e motivos que devem
   orientar as próximas alterações.
3. Use o [README.md](README.md) para instalação, testes e contrato da API.
4. Confira `git status --short --branch` e o histórico recente. Os documentos
   são registros datados; não substituem a inspeção do estado atual.
5. Para arquivos em `backend/`, siga também [backend/AGENTS.md](backend/AGENTS.md)
   e as instruções locais aplicáveis.

## Forma de trabalhar combinada com o usuário

- Comunicar em português. O usuário testa o app num celular físico e acompanha
  o desenvolvimento em etapas.
- Usar uma branch separada por etapa, partindo da base atualizada e preservando
  alterações que já existam no diretório.
- Implementar e executar as verificações apropriadas; entregar instruções de
  teste no aparelho quando houver mudança de comportamento.
- Fazer commit, PR e merge conforme o pedido vigente. Um pedido de commit e PR
  não inclui merge; autorizações de etapas anteriores não se estendem
  automaticamente à próxima. Quando a ação já estiver autorizada, concluí-la
  sem pedir confirmação novamente.
- Atualizar a documentação ao encerrar uma etapa: estado, decisões novas,
  verificações realizadas e o que falta. Registrar limites e testes pulados.

## Cuidados que fazem parte das decisões existentes

- Preservar os dados do celular. Atualizar o APK por cima, com assinatura
  compatível; não desinstalar nem limpar dados para contornar uma falha de
  instalação. Há chaves debug diferentes no Android Studio Flatpak e no CLI
  desta máquina; veja a retomada.
- Manter migrations explícitas do Room a partir da versão 4 e versionar os
  schemas. O schema atual é 7. Não reintroduzir fallback destrutivo geral.
- Usar `tarefas_test` para testes de banco. Não executar testes destrutivos
  contra `tarefas`. A integração Android ↔ Laravel não deve disputar o banco
  de teste com outra suíte em execução.
- Não versionar `.env`, credenciais, tokens, chaves de assinatura, dumps do
  banco ou logs. Ao consultar logs, limitar a leitura ao trecho necessário e
  evitar reproduzir URLs de recuperação/assinaturas em documentação.
- Obter credenciais por entrada segura quando necessário; não pedir senhas em
  mensagens comuns nem gravá-las nestes documentos.
- Manter Laravel/MySQL/Vue no servidor e Kotlin no Android. Mudanças nas regras
  de data/recorrência devem continuar coerentes com os casos compartilhados.
- Preservar o isolamento por conta, a fila offline e a proteção contra
  respostas atrasadas de sessões anteriores.
- A confirmação de e-mail pendente atualmente permite tarefas e sincronização.
  Isso é uma decisão registrada, não uma falha a ser corrigida implicitamente.

## Documentação e validação

As decisões estão centralizadas em `docs/DECISOES.md`; o estado e o backlog,
em `docs/RETOMADA.md`. Evitar criar cópias divergentes em memórias de um agente.
Para uma mudança só de documentação, conferir referências, coerência e diff;
não é necessário repetir as suítes do aplicativo.
