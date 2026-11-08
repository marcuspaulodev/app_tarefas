import assert from 'node:assert/strict';
import { test } from 'node:test';
import { useAuthForm } from '../../resources/js/composables/useAuthForm.js';

function form(options = {}) {
    return useAuthForm({
        authenticate: async () => assert.fail('Não deve autenticar automaticamente'),
        requestRecovery: async () => assert.fail('Não deve pedir outro link'),
        resetPassword: async () => assert.fail('Não deve alterar a senha'),
        ...options,
    });
}

test('forgot password sends only the email and keeps the confirmation visible', async () => {
    const message = 'Se este e-mail estiver cadastrado, você receberá um link.';
    const state = form({ requestRecovery: async body => {
        assert.deepEqual(body, { email: 'test@example.test' });
        return { message };
    } });
    state.form.email = ' test@example.test ';
    state.form.password = 'senha digitada no login';
    state.changeMode('forgot');
    assert.equal(state.form.password, '');
    await state.submit();
    assert.equal(state.mode.value, 'forgot');
    assert.equal(state.status.value, message);
    assert.equal(state.error.value, '');
    assert.equal(state.busy.value, false);
});

test('a reset link prefills the email and a successful reset returns to login without signing in', async () => {
    let clearedLink = false;
    const state = form({
        recovery: { mode: 'reset', token: 'test-token', email: 'test@example.test' },
        leaveRecovery: () => { clearedLink = true; },
        resetPassword: async body => {
            assert.deepEqual(body, { email: 'test@example.test', token: 'test-token', password: 'nova-senha', password_confirmation: 'nova-senha' });
            return { message: 'Senha alterada. Entre novamente.' };
        },
    });
    assert.equal(state.resetting.value, true);
    assert.equal(state.form.email, 'test@example.test');
    state.form.password = state.form.password_confirmation = 'nova-senha';
    await state.submit();
    assert.equal(state.mode.value, 'login');
    assert.equal(state.status.value, 'Senha alterada. Entre novamente.');
    assert.equal(state.form.password, '');
    assert.equal(state.form.password_confirmation, '');
    assert.equal(clearedLink, true);
});

test('an expired link displays its error and can request a new one without reusing the token', async () => {
    let clearedLink = false;
    const state = form({
        recovery: { mode: 'reset', token: 'expired', email: 'test@example.test' },
        leaveRecovery: () => { clearedLink = true; },
        resetPassword: async () => { throw { message: 'Confira os campos.', errors: { token: ['Este link expirou.'] } }; },
        requestRecovery: async body => {
            assert.deepEqual(body, { email: 'test@example.test' });
            return { message: 'Confira seu e-mail.' };
        },
    });
    state.form.password = state.form.password_confirmation = 'nova-senha';
    await state.submit();
    assert.equal(state.mode.value, 'reset');
    assert.equal(state.error.value, 'Este link expirou.');
    assert.equal(state.status.value, '');
    assert.equal(clearedLink, false);
    state.changeMode('forgot');
    assert.equal(clearedLink, true);
    assert.equal(state.form.password, '');
    assert.deepEqual(state.errors.value, {});
    await state.submit();
    assert.equal(state.status.value, 'Confira seu e-mail.');
});

test('password validation can be corrected without discarding a valid link', async () => {
    let attempts = 0;
    const state = form({
        recovery: { mode: 'reset', token: 'valid', email: 'test@example.test' },
        resetPassword: async body => {
            assert.equal(body.token, 'valid');
            if (++attempts === 1) throw { message: 'Confira os campos.', errors: { password: ['As senhas devem ser iguais.'] } };
            return { message: 'Senha alterada.' };
        },
    });
    state.form.password = 'nova-senha';
    state.form.password_confirmation = 'diferente';
    await state.submit();
    assert.deepEqual(state.errors.value.password, ['As senhas devem ser iguais.']);
    assert.equal(state.mode.value, 'reset');
    state.form.password_confirmation = state.form.password;
    await state.submit();
    assert.equal(attempts, 2);
    assert.equal(state.mode.value, 'login');
    assert.deepEqual(state.errors.value, {});
});

test('duplicate submits are ignored and network or rate-limit failures do not show a success notice', async () => {
    let reject;
    let calls = 0;
    const state = form({ requestRecovery: () => {
        calls++;
        return new Promise((resolve, rejectRequest) => { reject = rejectRequest; });
    } });
    state.changeMode('forgot');
    state.form.email = 'test@example.test';
    const pending = state.submit();
    await state.submit();
    assert.equal(calls, 1);
    assert.equal(state.busy.value, true);
    reject(new Error('Muitas tentativas. Aguarde um minuto.'));
    await pending;
    assert.equal(state.busy.value, false);
    assert.equal(state.status.value, '');
    assert.equal(state.error.value, 'Muitas tentativas. Aguarde um minuto.');
});

test('login and registration still use authentication and clear passwords after success', async () => {
    const calls = [];
    const state = form({ authenticate: async (mode, credentials) => calls.push({ mode, credentials }) });
    state.form.email = ' test@example.test ';
    state.form.password = 'senha-atual';
    await state.submit();
    assert.equal(calls[0].mode, 'login');
    assert.equal(calls[0].credentials.email, 'test@example.test');
    assert.equal(calls[0].credentials.password, 'senha-atual');
    assert.equal(state.form.password, '');
    state.changeMode('register');
    state.form.name = ' Nome ';
    state.form.password = state.form.password_confirmation = 'senha-nova';
    await state.submit();
    assert.equal(calls[1].mode, 'register');
    assert.equal(calls[1].credentials.name, 'Nome');
    assert.equal(calls[1].credentials.password_confirmation, 'senha-nova');
    assert.equal(state.form.password_confirmation, '');
});
