<script context="module">
    import { writable as w } from "svelte/store";
    const _toasts = w([]);
    export const toastStore = _toasts;

    export const addToast = (message) => {
        const isError = /error|failed|gagal/i.test(message);
        const id = Date.now();
        _toasts.update((t) => [...t, { id, message, isError }]);
        setTimeout(() => {
            _toasts.update((t) => t.filter((x) => x.id !== id));
        }, 3000);
    };
</script>

<script>
    import { onMount } from "svelte";
    import { writable } from "svelte/store";
    import { fade, fly } from "svelte/transition";
    import ferrisHappy from "../assets/ferris_happy.svg";
    import ferrisSleep from "../assets/ferris_sleep.svg";

    export const toasts = writable([]);

    export function showToast(message) {
        const isError = /error|failed|gagal/i.test(message);
        const id = Date.now();
        toasts.update((t) => [...t, { id, message, isError }]);
        setTimeout(() => {
            toasts.update((t) => t.filter((x) => x.id !== id));
        }, 3000);
    }
</script>

<div
    class="fixed bottom-24 left-1/2 -translate-x-1/2 z-[9999] flex flex-col items-center gap-2 pointer-events-none w-full max-w-sm px-4"
>
    {#each $toastStore as t (t.id)}
        <div
            in:fly={{ y: 20, duration: 300 }}
            out:fade={{ duration: 200 }}
            class="flex items-center gap-3 bg-surface-container-high border border-outline/10 shadow-xl text-on-surface p-3 pr-5 rounded-full pointer-events-auto"
        >
            <div class="shrink-0 w-8 h-8 flex items-center justify-center">
                <img
                    src={t.isError ? ferrisSleep : ferrisHappy}
                    class="w-full h-full object-contain"
                    alt="Ferris"
                />
            </div>
            <span class="font-medium text-sm line-clamp-2">{t.message}</span>
        </div>
    {/each}
</div>
