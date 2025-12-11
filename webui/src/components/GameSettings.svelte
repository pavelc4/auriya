<script>
    import { onMount, createEventDispatcher } from "svelte";
    import { activeGames, supportedRefreshRates } from "../lib/stores";
    import { runCommand, showToast } from "../lib/api";

    export let pkg;
    export let onBack;

    let isEnabled = false;
    let dnd = false;
    let mode = "performance";
    let gov = "performance";
    let fps = "";
    let rate = "";

    let managers = ["performance", "schedutil", "powersave", "interactive"];
    let availableRates = [60, 90, 120];

    const dispatch = createEventDispatcher();

    onMount(async () => {
        const govOutput = await runCommand(
            "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors",
        );
        if (govOutput && !govOutput.error) {
            managers = govOutput.split(" ").filter(Boolean);
        }

        if ($supportedRefreshRates.length > 0) {
            availableRates = $supportedRefreshRates;
        }

        const profile = $activeGames.find((g) => g.package === pkg);
        if (profile) {
            isEnabled = true;
            dnd = profile.enable_dnd || false;
            mode = profile.mode || "performance";
            gov = profile.cpu_governor || "performance";
            fps = profile.target_fps || "";
            rate = profile.refresh_rate || "";
        }
    });

    async function save() {
        const socketPath = "/dev/socket/auriya.sock";
        try {
            if (isEnabled) {
                const isNew = !$activeGames.find((g) => g.package === pkg);
                if (isNew) {
                    await runCommand(
                        `echo "ADD_GAME ${pkg}" | nc -U ${socketPath}`,
                    );
                }

                let updateCmd = `UPDATE_GAME ${pkg} gov=${gov} dnd=${dnd} mode=${mode}`;
                if (fps) updateCmd += ` fps=${fps}`;
                if (rate) updateCmd += ` rate=${rate}`;

                await runCommand(`echo "${updateCmd}" | nc -U ${socketPath}`);
                showToast(`Saved settings for ${pkg}`);
            } else {
                await runCommand(
                    `echo "REMOVE_GAME ${pkg}" | nc -U ${socketPath}`,
                );
                showToast(`Removed ${pkg}`);
            }

            const output = await runCommand(
                `echo "GET_GAMELIST" | nc -U ${socketPath}`,
            );
            if (output && !output.error && !output.startsWith("ERR")) {
                try {
                    activeGames.set(
                        JSON.parse(output.substring(output.indexOf("["))),
                    );
                } catch (e) {}
            }

            onBack();
        } catch (e) {
            showToast(`Error: ${e.message}`);
        }
    }
</script>

<div class="view-section space-y-6 pb-20 fade-in">
    <div class="flex items-center gap-4 pt-2">
        <button
            on:click={onBack}
            class="btn btn-circle btn-ghost bg-surface-container-high text-on-surface hover:bg-surface-variant/20"
        >
            <span class="material-symbols-rounded">arrow_back</span>
        </button>
        <div class="flex flex-col">
            <h2 class="text-xl font-bold leading-tight">{pkg}</h2>
            <span class="text-xs opacity-60">Game Configuration</span>
        </div>
    </div>
    <div
        class="bg-surface-container p-6 rounded-[32px] card_border text-on-surface space-y-5"
    >
        <div class="flex items-center justify-between p-2">
            <div>
                <p class="font-bold text-lg">Optimization</p>
                <p class="text-xs opacity-60">Enable Auriya for this app</p>
            </div>
            <input
                type="checkbox"
                bind:checked={isEnabled}
                class="toggle toggle-lg"
            />
        </div>

        <div class="divider my-0 opacity-10"></div>

        <div class="flex items-center justify-between p-2">
            <div>
                <p class="font-bold text-lg">Do Not Disturb</p>
                <p class="text-xs opacity-60">
                    Block notifications while playing
                </p>
            </div>
            <input
                type="checkbox"
                bind:checked={dnd}
                disabled={!isEnabled}
                class="toggle"
            />
        </div>

        <div class="divider my-0 opacity-10"></div>

        <div class="space-y-3 pt-2">
            <div class="flex items-center gap-2 mb-2">
                <span class="material-symbols-rounded text-[var(--primary)]"
                    >tune</span
                >
                <span class="font-bold">Profile</span>
            </div>
            <div class="grid grid-cols-3 gap-3">
                {#each ["powersave", "balance", "performance"] as m}
                    <label class="cursor-pointer group">
                        <input
                            type="radio"
                            group={mode}
                            value={m}
                            disabled={!isEnabled}
                            class="peer sr-only"
                        />
                        <div
                            class="h-24 flex flex-col items-center justify-center p-2 rounded-[24px] bg-surface-container-high text-on-surface-variant peer-checked:bg-[var(--primary)] peer-checked:text-[var(--onPrimary)] text-center transition-all duration-300 shadow-sm peer-checked:shadow-md"
                        >
                            <span
                                class="material-symbols-rounded block mb-1 text-[28px]"
                            >
                                {m === "powersave"
                                    ? "battery_saver"
                                    : m === "balance"
                                      ? "balance"
                                      : "rocket_launch"}
                            </span>
                            <span
                                class="text-xs font-bold capitalize tracking-wide"
                                >{m}</span
                            >
                        </div>
                    </label>
                {/each}
            </div>
        </div>
    </div>

    <div
        class="bg-surface-container p-6 rounded-[32px] card_border text-on-surface space-y-4"
    >
        <h3 class="font-bold opacity-80 px-2">Advanced Config</h3>
        <div class="grid gap-4">
            <div class="form-control w-full">
                <label class="label" for="game-gov"
                    ><span class="label-text text-on-surface font-medium"
                        >CPU Governor</span
                    ></label
                >
                <select
                    id="game-gov"
                    bind:value={gov}
                    disabled={!isEnabled}
                    class="select w-full bg-surface-container-high h-12 rounded-2xl"
                >
                    {#each managers as m}
                        <option value={m}>{m}</option>
                    {/each}
                </select>
            </div>
            <div class="grid grid-cols-2 gap-4">
                <div class="form-control w-full">
                    <label class="label" for="game-fps"
                        ><span class="label-text text-on-surface font-medium"
                            >Target FPS</span
                        ></label
                    >
                    <select
                        id="game-fps"
                        bind:value={fps}
                        disabled={!isEnabled}
                        class="select w-full bg-surface-container-high h-12 rounded-2xl"
                    >
                        <option value="">Default</option>
                        {#each [30, 45, 60, 90, 120] as f}
                            <option value={f}>{f}</option>
                        {/each}
                    </select>
                </div>
                <div class="form-control w-full">
                    <label class="label" for="settings-refresh-select"
                        ><span class="label-text text-on-surface font-medium"
                            >Refresh Rate</span
                        ></label
                    >
                    <select
                        id="settings-refresh-select"
                        bind:value={rate}
                        disabled={!isEnabled}
                        class="select w-full bg-surface-container-high h-12 rounded-2xl"
                    >
                        <option value="">Default</option>
                        {#each availableRates as r}
                            <option value={r}>{r} Hz</option>
                        {/each}
                    </select>
                </div>
            </div>
        </div>
    </div>

    <!-- FAB Save -->
    <div class="pt-2 flex justify-end">
        <button
            on:click={save}
            class="btn bg-surface-container-high text-on-surface hover:bg-surface-variant border-none rounded-2xl min-h-0 h-12 px-8 text-base font-bold"
        >
            Save
        </button>
    </div>
</div>

<style>
    .fade-in {
        animation: fade 0.3s ease-out;
    }
    @keyframes fade {
        from {
            opacity: 0;
            transform: translateY(10px);
        }
        to {
            opacity: 1;
            transform: translateY(0);
        }
    }
</style>
