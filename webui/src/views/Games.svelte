<script>
    import { onMount } from "svelte";
    import { _ } from "svelte-i18n";
    import {
        packages,
        activeGames,
        searchQuery,
        filteredGames,
        gamePage,
    } from "../lib/stores";
    import GameSettings from "./GameSettings.svelte";
    import Icon from "../components/ui/Icon.svelte";
    import { runCommand } from "../lib/api";

    let showSystemApps = false;
    let isLoading = false;
    let selectedPkg = null;

    const ITEMS_PER_PAGE = 20;

    $: slicedGames = $filteredGames.slice(0, $gamePage * ITEMS_PER_PAGE);
    $: remaining = $filteredGames.length - slicedGames.length;

    async function loadPackages() {
        isLoading = true;
        const cmd = showSystemApps ? "pm list packages" : "pm list packages -3";
        let output = await runCommand(cmd, null);

        if (!output || output.error) {
            output = await runCommand(
                `echo "LIST_PACKAGES" | nc -U /dev/socket/auriya.sock`,
            );
        }

        if (typeof output === "string") {
            const lines = output.split("\n");
            const pkgs = lines
                .filter((line) => line.includes("package:"))
                .map((line) => line.split("package:")[1]?.trim())
                .filter(Boolean)
                .sort();

            packages.set(pkgs);
        }
        isLoading = false;
    }

    async function loadActiveGames() {
        const output = await runCommand(
            `echo "GET_GAMELIST" | nc -U /dev/socket/auriya.sock`,
        );
        if (output && !output.error && !output.startsWith("ERR")) {
            try {
                let jsonStr = output.substring(output.indexOf("["));
                activeGames.set(JSON.parse(jsonStr));
            } catch (e) {
                console.warn("Failed to parse gamelist", e);
            }
        }
    }

    function toggleFilter() {
        showSystemApps = !showSystemApps;
        loadPackages();
    }

    onMount(() => {
        loadPackages();
        loadActiveGames();
    });
</script>

{#if selectedPkg}
    <GameSettings pkg={selectedPkg} onBack={() => (selectedPkg = null)} />
{:else}
    <div class="view-section space-y-4">
        <div class="pt-4 pb-2">
            <div
                class="bg-surface-container-high h-14 rounded-full flex items-center px-2 w-full shadow-sm transition-all focus-within:bg-surface-container-highest focus-within:shadow-md"
            >
                <button
                    class="btn btn-ghost btn-circle btn-sm w-10 h-10 min-h-0 text-on-surface-variant"
                >
                    <Icon name="search" />
                </button>
                <input
                    type="text"
                    placeholder={$_("games.searchPlaceholder")}
                    bind:value={$searchQuery}
                    class="bg-transparent border-none text-on-surface placeholder:text-on-surface-variant/70 flex-grow h-full px-2 focus:outline-none text-base font-normal"
                />
                {#if $searchQuery}
                    <button
                        on:click={() => searchQuery.set("")}
                        class="btn btn-ghost btn-circle btn-sm w-10 h-10 min-h-0 text-on-surface-variant hover:text-on-surface"
                    >
                        <Icon name="close" className="text-xl" />
                    </button>
                {/if}
                <div
                    class="divider divider-horizontal mx-0 my-3 w-[1px] bg-outline/20"
                ></div>
                <button
                    on:click={toggleFilter}
                    class="btn btn-ghost btn-circle w-10 h-10 min-h-0 ml-1 transition-colors {showSystemApps
                        ? 'text-[var(--primary)] bg-[var(--primary)]/10'
                        : 'text-on-surface-variant hover:text-on-surface'}"
                    title={showSystemApps
                        ? "Filter: User Apps"
                        : "Filter: System Apps"}
                >
                    <Icon
                        name={showSystemApps
                            ? "filter_list_off"
                            : "filter_list"}
                        className="text-xl"
                    />
                </button>
                <div class="w-2"></div>
            </div>
        </div>

        <div class="min-h-[50vh]">
            {#if isLoading}
                <div
                    class="text-center py-12 opacity-50 flex flex-col items-center"
                >
                    <span
                        class="loading loading-spinner loading-lg text-[var(--primary)] mb-4"
                    ></span>
                    <p>{$_("games.loading")}</p>
                </div>
            {:else if slicedGames.length === 0}
                <div
                    class="flex flex-col items-center justify-center py-16 px-4"
                >
                    <div
                        class="bg-surface-container p-8 rounded-[32px] w-full max-w-sm text-center"
                    >
                        <div
                            class="w-16 h-16 rounded-2xl bg-surface-variant/30 text-on-surface-variant flex items-center justify-center mx-auto mb-4"
                        >
                            <Icon name="search_off" className="text-[32px]" />
                        </div>
                        <h3 class="text-xl font-semibold text-on-surface mb-2">
                            {$_("games.noPackages")}
                        </h3>
                        <p
                            class="text-sm text-on-surface-variant opacity-80 mb-6"
                        >
                            {$_("games.noPackagesDesc")}
                        </p>
                        {#if $searchQuery}
                            <button
                                on:click={() => searchQuery.set("")}
                                class="btn bg-surface-variant/20 hover:bg-surface-variant/30 text-on-surface rounded-full px-6 normal-case border-none h-10 min-h-0"
                            >
                                {$_("games.clearSearch")}
                            </button>
                        {:else}
                            <button
                                on:click={toggleFilter}
                                class="btn bg-[var(--primary)] text-on-primary hover:opacity-90 rounded-full px-6 normal-case border-none h-10 min-h-0"
                            >
                                {$_("common.show")}
                                {showSystemApps
                                    ? $_("games.showUserApps")
                                    : $_("games.showSystemApps")}
                            </button>
                        {/if}
                    </div>
                </div>
            {:else}
                <div class="pb-20 space-y-2">
                    {#each slicedGames as pkg (pkg)}
                        {@const activeProfile = $activeGames.find(
                            (g) => g.package === pkg,
                        )}
                        {@const isEnabled = !!activeProfile}
                        <div
                            class="relative group overflow-hidden p-4 mb-3 rounded-[24px] transition-all duration-300 cursor-pointer
                            {isEnabled
                                ? 'bg-surface-variant/20 shadow-lg'
                                : 'bg-surface-container-highest/30 hover:bg-surface-container-highest/50'}"
                            on:click={() => (selectedPkg = pkg)}
                            role="button"
                            tabindex="0"
                            on:keydown={(e) =>
                                e.key === "Enter" && (selectedPkg = pkg)}
                        >
                            <div class="flex items-center gap-4 relative z-10">
                                <div
                                    class="w-12 h-12 rounded-2xl flex items-center justify-center shrink-0 transition-transform duration-300 group-hover:scale-110
                                    {isEnabled
                                        ? 'bg-[var(--primary)] text-[var(--onPrimary)] shadow-md'
                                        : 'bg-surface-variant/10 text-on-surface-variant/70'}"
                                >
                                    <Icon
                                        name={isEnabled
                                            ? "sports_esports"
                                            : "android"}
                                        className="text-[24px]"
                                    />
                                </div>

                                <div class="min-w-0 flex-grow">
                                    <div class="flex items-center gap-2 mb-0.5">
                                        <p
                                            class="text-base font-semibold truncate text-on-surface"
                                        >
                                            {pkg}
                                        </p>
                                        {#if isEnabled}
                                            <span
                                                class="bg-[var(--primary)]/10 text-[var(--primary)] rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider shadow-sm"
                                                >{$_("games.active")}</span
                                            >
                                        {/if}
                                    </div>
                                    <div
                                        class="flex items-center gap-2 text-xs"
                                    >
                                        <span
                                            class="font-medium {isEnabled
                                                ? 'text-on-surface-variant'
                                                : 'text-on-surface-variant opacity-60'}"
                                            >{isEnabled
                                                ? $_("games.optimized")
                                                : $_(
                                                      "games.tapToOptimize",
                                                  )}</span
                                        >
                                        {#if isEnabled}
                                            <span
                                                class="w-1 h-1 rounded-full bg-on-surface/20"
                                            ></span>
                                            <span class="opacity-60"
                                                >{activeProfile.cpu_governor}</span
                                            >
                                        {/if}
                                    </div>
                                </div>

                                <div
                                    class="w-10 h-10 rounded-full bg-surface-variant/20 flex items-center justify-center text-on-surface opacity-0 group-hover:opacity-100 transition-all duration-300 transform translate-x-4 group-hover:translate-x-0"
                                >
                                    <Icon name="edit" />
                                </div>
                            </div>
                        </div>
                    {/each}

                    {#if remaining > 0}
                        <div class="flex justify-center py-4">
                            <button
                                on:click={() => gamePage.update((n) => n + 1)}
                                class="px-6 py-2 rounded-full bg-primary/10 text-primary hover:bg-primary/20 transition-colors font-medium text-sm"
                            >
                                {$_("games.loadMore", {
                                    values: { remaining },
                                })}
                            </button>
                        </div>
                    {/if}
                </div>
            {/if}
        </div>
    </div>
{/if}
