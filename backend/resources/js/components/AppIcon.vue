<script setup>
defineProps({ name: String, size: { type: Number, default: 20 } });

const paths = {
    sun: 'M12 3v1m0 16v1M3 12h1m16 0h1M5.6 5.6l.7.7m11.4 11.4.7.7M5.6 18.4l.7-.7M17.7 6.3l.7-.7M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0',
    moon: 'M20.7 13A9 9 0 0 1 11 3.3 9 9 0 1 0 20.7 13Z',
    plus: 'M12 5v14M5 12h14',
    search: 'M21 21l-5-5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0',
    close: 'm6 6 12 12M6 18 18 6',
    left: 'm15 6-6 6 6 6',
    right: 'm9 6 6 6-6 6',
    trash: 'M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7m4-7v7',
    check: 'm5 12 4 4L19 6',
    repeat: 'M4 10V5h14l3 3m0-3v5H7l-3-3m16 7v5H6l-3-3m0 3v-5h14l3 3',
    menu: 'M4 6h16M4 12h16M4 18h16',
    logout: 'M9 4H4v16h5M9 12h12m-4-4 4 4-4 4',
    edit: 'm16 3 5 5L8 21H3v-5ZM13 6l5 5',
    calendar: 'M4 5h16v16H4ZM8 2v6m8-6v6M4 10h16',
    arrow: 'M5 12h14m-5-5 5 5-5 5',
    tag: 'M3 3h8l10 10-8 8L3 11ZM7 7h.01',
    up: 'm6 15 6-6 6 6',
    down: 'm6 9 6 6 6-6',
    checklist: 'M10 6h11M10 12h11M10 18h11M3 6l1.5 1.5L7 5M3 12l1.5 1.5L7 11M3 18l1.5 1.5L7 17',
};
</script>

<template>
    <svg :width="size" :height="size" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path :d="paths[name] ?? paths.plus" />
    </svg>
</template>
