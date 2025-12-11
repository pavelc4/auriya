<script>
    import { openExternalLink } from "../lib/api";

    const dependencies = [
        "tokio",
        "serde",
        "anyhow",
        "tracing",
        "notify",
        "toml",
    ];
</script>

<div class="view-section space-y-4">
    <div
        class="bg-surface-container p-6 rounded-[28px] card_border text-on-surface"
    >
        <h2 class="text-lg font-semibold mb-2">Support</h2>
        <p class="mb-6 opacity-80 text-sm leading-relaxed">
            Auriya is a free, open-source passion project. If you enjoy the
            performance boost, consider fueling the next update with a cup of
            coffee.
        </p>
        <button
            on:click={() => openExternalLink("https://t.me/pvlcply")}
            class="btn bg-[var(--primary)] text-on-primary w-full rounded-full hover:bg-[var(--primary)]/90 border-none h-12 text-base font-medium normal-case gap-2"
        >
            <span class="material-symbols-rounded">local_cafe</span>
            Buy me a coffee
        </button>
    </div>

    <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div
            class="bg-surface-container p-6 rounded-[28px] card_border text-on-surface h-full flex flex-col"
        >
            <div class="flex items-center gap-3 mb-3">
                <div
                    class="w-10 h-10 rounded-xl bg-surface-variant text-white flex items-center justify-center shrink-0"
                >
                    <span class="material-symbols-rounded text-[20px]"
                        >lightbulb</span
                    >
                </div>
                <h2 class="text-lg font-semibold">The Philosophy</h2>
            </div>
            <p class="text-sm opacity-80 leading-relaxed flex-grow">
                Auriya redefines system optimization by fusing a
                high-performance <b>Rust daemon</b> with a reactive
                <b>Svelte WebUI</b>. It runs as an intelligent background
                service that dynamically manages resources for peak efficiency.
            </p>
        </div>

        <div
            class="bg-surface-container p-6 rounded-[28px] card_border text-on-surface h-full flex flex-col"
        >
            <div class="flex items-center gap-3 mb-3">
                <div
                    class="w-10 h-10 rounded-xl bg-[var(--primary)]/20 text-[var(--primary)] flex items-center justify-center shrink-0"
                >
                    <span class="material-symbols-rounded text-[20px]"
                        >code</span
                    >
                </div>
                <h2 class="text-lg font-semibold">Why Rust?</h2>
            </div>
            <p class="text-sm opacity-80 leading-relaxed flex-grow">
                We chose <b>Rust</b> for its speed and stability. The daemon works
                efficiently in the background to smartly tune system parameters,
                delivering maximum performance with a minimal memory footprint.
            </p>
        </div>
    </div>

    <div
        class="bg-surface-container p-6 rounded-[28px] card_border text-on-surface"
    >
        <div class="flex items-center gap-3 mb-4">
            <div
                class="w-10 h-10 rounded-xl bg-[var(--secondary)]/20 text-[var(--secondary)] flex items-center justify-center shrink-0"
            >
                <span class="material-symbols-rounded text-[20px]">group</span>
            </div>
            <h2 class="text-lg font-semibold">Contributors</h2>
        </div>

        <div class="space-y-3">
            <button
                on:click={() => openExternalLink("https://github.com/pavelc4")}
                class="flex w-full text-left items-center gap-4 p-3 rounded-2xl bg-surface-variant/20 hover:bg-surface-variant/40 transition-colors"
            >
                <img
                    src="https://github.com/pavelc4.png"
                    class="w-10 h-10 rounded-full bg-surface-variant shrink-0"
                    alt="pavelc4"
                />
                <div>
                    <p class="text-sm font-medium">pavelc4</p>
                    <p class="text-xs opacity-60">Creator & Lead Developer</p>
                </div>
            </button>

            <button
                on:click={() =>
                    openExternalLink(
                        "https://github.com/pavelc4/auriya/graphs/contributors",
                    )}
                class="flex w-full items-center justify-center gap-2 p-3 rounded-2xl hover:bg-surface-variant/10 transition-colors text-sm opacity-70 hover:opacity-100"
            >
                <span class="material-symbols-rounded text-lg">open_in_new</span
                >
                View all contributors on GitHub
            </button>
        </div>
    </div>

    <div
        class="bg-surface-container p-6 rounded-[28px] card_border text-on-surface"
    >
        <div class="flex items-center gap-3 mb-4">
            <div
                class="w-10 h-10 rounded-xl bg-surface-variant/20 flex items-center justify-center shrink-0 text-on-surface"
            >
                <span class="material-symbols-rounded text-[20px]"
                    >extension</span
                >
            </div>
            <div>
                <h2 class="text-lg font-semibold">Open Source Foundation</h2>
                <p class="text-xs opacity-60">
                    Standing on the shoulders of giants.
                </p>
            </div>
        </div>

        <div class="flex flex-wrap gap-2 pt-1">
            {#each dependencies as dep}
                <div
                    class="inline-flex items-center px-3 py-1 rounded-full bg-surface-variant/10 border border-outline/10 text-xs font-mono font-medium text-on-surface-variant hover:bg-surface-variant/20 hover:text-on-surface transition-colors cursor-default"
                >
                    {dep}
                </div>
            {/each}
        </div>
    </div>

    <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div
            class="bg-surface-container p-6 rounded-[28px] card_border text-on-surface"
        >
            <div class="flex items-center gap-3 mb-3">
                <div
                    class="w-10 h-10 rounded-xl bg-[var(--tertiary)]/20 text-[var(--tertiary)] flex items-center justify-center shrink-0"
                >
                    <span class="material-symbols-rounded text-[20px]"
                        >forum</span
                    >
                </div>
                <h2 class="text-lg font-semibold">Join Community</h2>
            </div>
            <p class="text-sm opacity-80 mb-4">
                Join our Telegram group to discuss features, report bugs, or
                just hang out.
            </p>
            <button
                on:click={() =>
                    openExternalLink("https://t.me/paveldiscussion")}
                class="btn btn-sm bg-[var(--tertiary)]/20 hover:bg-[var(--tertiary)]/30 text-[var(--tertiary)] border-none rounded-full px-4 w-full"
            >
                Join Discussion
            </button>
        </div>

        <div
            class="bg-surface-container p-6 rounded-[28px] card_border text-on-surface"
        >
            <div class="flex items-center gap-3 mb-3">
                <div
                    class="w-10 h-10 rounded-xl bg-surface-variant text-white flex items-center justify-center shrink-0"
                >
                    <span class="material-symbols-rounded text-[20px]"
                        >policy</span
                    >
                </div>
                <h2 class="text-lg font-semibold">License</h2>
            </div>
            <p class="text-sm opacity-80 mb-5">
                Auriya is free software licensed under the <b>MIT License</b>.
            </p>

            <div
                class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-surface-variant/20 border border-white/5 text-xs font-medium text-on-surface-variant"
            >
                <span class="material-symbols-rounded text-[14px]"
                    >copyright</span
                >
                <span>2025 pavelc4</span>
            </div>
        </div>
    </div>
</div>
