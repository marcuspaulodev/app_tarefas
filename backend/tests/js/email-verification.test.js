import test from 'node:test';
import assert from 'node:assert/strict';
import { effectScope, ref } from 'vue';
import { useEmailVerification } from '../../resources/js/composables/useEmailVerification.js';

const account = { id: 1, email: 'one@example.test', email_verified: false };

function setup(t, overrides = {}) {
    const scope = effectScope();
    t.after(() => scope.stop());
    const user = ref({ ...account });
    const errors = [];
    const form = scope.run(() => useEmailVerification({
        user, send: async () => ({ email_verified: false, message: 'Link enviado.' }),
        fetchUser: async () => ({ ...account, email_verified: true }),
        onAuthError: error => errors.push(error), ...overrides,
    }));
    return { user, errors, ...form };
}

test('reenviar mostra confirmação e consultar atualiza somente o status da conta', async t => {
    const form = setup(t);
    await form.sendVerification();
    assert.equal(form.verificationNotice.value, 'Link enviado.');
    assert.equal(form.user.value.email_verified, false);
    await form.refreshVerification();
    assert.deepEqual(form.user.value, { ...account, email_verified: true });
    assert.equal(form.verificationNotice.value, 'E-mail confirmado.');
    assert.equal(form.verificationBusy.value, false);
});

test('resposta pendente não afirma confirmação e erros não afirmam envio', async t => {
    const form = setup(t, { fetchUser: async () => account, send: async () => { throw { status: 429, message: 'Aguarde um minuto.' }; } });
    await form.refreshVerification();
    assert.match(form.verificationNotice.value, /pendente/);
    await form.sendVerification();
    assert.equal(form.verificationNotice.value, '');
    assert.equal(form.verificationError.value, 'Aguarde um minuto.');
    assert.equal(form.user.value.email_verified, false);
});

test('resposta antiga não confirma outra conta e não interfere numa nova solicitação', async t => {
    let finish;
    const form = setup(t, { fetchUser: () => new Promise(resolve => { finish = resolve; }) });
    const previous = form.refreshVerification();
    form.user.value = { id: 2, email: 'two@example.test', email_verified: false };
    await form.sendVerification();
    finish({ ...account, email_verified: true });
    await previous;
    assert.equal(form.user.value.id, 2);
    assert.equal(form.user.value.email_verified, false);
    assert.equal(form.verificationNotice.value, 'Link enviado.');
});

test('ignora cliques duplicados e respostas que pertencem a outra conta', async t => {
    let finish;
    let calls = 0;
    const form = setup(t, { fetchUser: () => { calls++; return new Promise(resolve => { finish = resolve; }); } });
    const pending = form.refreshVerification();
    await form.refreshVerification();
    assert.equal(calls, 1);
    finish({ id: 2, email: 'two@example.test', email_verified: true });
    await pending;
    assert.deepEqual(form.user.value, account);
});

test('expiração de sessão segue o fluxo de login e rede offline mantém a conta', async t => {
    let failure = { status: 0, message: 'Sem conexão.' };
    const form = setup(t, { fetchUser: async () => { throw failure; } });
    await form.refreshVerification(true);
    assert.equal(form.verificationError.value, '');
    assert.deepEqual(form.user.value, account);
    failure = { status: 401, message: 'Sessão encerrada.' };
    await form.refreshVerification();
    assert.deepEqual(form.errors, [failure]);
});

test('contas confirmadas ou sem login não solicitam novos envios', async t => {
    let calls = 0;
    const form = setup(t, { send: async () => { calls++; } });
    form.user.value = { ...account, email_verified: true };
    await form.sendVerification();
    form.user.value = null;
    await form.sendVerification();
    assert.equal(calls, 0);
});
