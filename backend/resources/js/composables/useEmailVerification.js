import { ref, watch } from 'vue';

export function useEmailVerification({ user, send, fetchUser, onAuthError }) {
    const verificationBusy = ref(false);
    const verificationNotice = ref('');
    const verificationError = ref('');
    let sequence = 0;

    watch(() => user.value?.id, () => {
        sequence++;
        verificationBusy.value = false;
        verificationNotice.value = '';
        verificationError.value = '';
    }, { flush: 'sync' });

    async function run(resend, quiet = false) {
        const account = user.value;
        if (!account || account.email_verified || verificationBusy.value) return;
        const current = ++sequence;
        verificationBusy.value = true;
        verificationNotice.value = '';
        verificationError.value = '';
        try {
            const response = await (resend ? send() : fetchUser());
            if (current !== sequence || user.value !== account) return;
            if (!resend && (response.id !== account.id || response.email !== account.email)) return;
            user.value = { ...account, email_verified: response.email_verified === true };
            if (!quiet) verificationNotice.value = resend ? response.message
                : response.email_verified ? 'E-mail confirmado.' : 'A confirmação ainda está pendente. Abra o link enviado para seu e-mail.';
        } catch (failure) {
            if (current !== sequence || user.value !== account) return;
            if (failure.status === 401 || failure.status === 419) onAuthError(failure);
            else if (!quiet) verificationError.value = failure.message;
        } finally {
            if (current === sequence) verificationBusy.value = false;
        }
    }

    return {
        verificationBusy, verificationNotice, verificationError,
        sendVerification: () => run(true),
        refreshVerification: (quiet = false) => run(false, quiet),
    };
}
