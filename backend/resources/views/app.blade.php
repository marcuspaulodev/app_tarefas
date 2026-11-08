<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="theme-color" content="#E9EDF2">
    <meta name="description" content="Organize seu dia com tarefas, calendário e tags.">
    <title>Tarefas — um dia de cada vez</title>
    <link rel="icon" type="image/svg+xml" href="{{ asset('favicon.svg') }}">
    <script>
        try {
            const saved = localStorage.getItem('tarefas.theme');
            document.documentElement.dataset.theme = ['light', 'dark'].includes(saved)
                ? saved : (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
        } catch {
            document.documentElement.dataset.theme = matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
        }
    </script>
    @vite(['resources/css/app.css', 'resources/js/app.js'])
</head>
<body>
    <div id="app"></div>
    <noscript>Ative o JavaScript do navegador para usar o Tarefas.</noscript>
    @php
        $endpoints = [
            'home' => route('home', absolute: false),
            'forgotPassword' => route('password.email', absolute: false),
            'resetPassword' => route('password.update', absolute: false),
            'passwordRecovery' => $passwordRecovery ?? null,
            'emailVerification' => $emailVerification ?? null,
            'sendVerification' => route('api.v1.verification.send', absolute: false),
            'login' => route('login', absolute: false),
            'register' => route('register', absolute: false),
            'logout' => route('logout', absolute: false),
            'user' => route('api.v1.user', absolute: false),
            'tasks' => route('api.v1.tasks.index', absolute: false),
            'counts' => route('api.v1.tasks.pending-counts', absolute: false),
            'tags' => route('api.v1.tags.index', absolute: false),
            'csrf' => route('sanctum.csrf-cookie', absolute: false),
        ];
    @endphp
    <script id="app-config" type="application/json">@json($endpoints)</script>
</body>
</html>
