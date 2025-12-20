<script>
    import { onMount } from "svelte";
    import { _ } from "svelte-i18n";
    import { locale, setLocale, languages } from "../lib/local";
    import { systemInfo, supportedRefreshRates } from "../lib/stores";
    import { parse, stringify } from "smol-toml";
    import { runCommand, showToast } from "../lib/api";
    import Icon from "../components/ui/Icon.svelte";
    import Select from "../components/ui/Select.svelte";

    let gov = "schedutil";
    let globalPreset = "balance";
    let debugMode = false;

    let availableGovernors = ["schedutil", "performance"];
    let availablePresets = ["balance", "performance", "powersave"];

    const configPath = "/data/adb/.config/auriya";

    async function setGlobalGovernor(newGov) {
        try {
            await runCommand(
                `sh -c 'for path in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo ${newGov} > "$path"; done'`,
            );
            await runCommand(
                `sh -c 'for path in /sys/devices/system/cpu/cpufreq/policy*/scaling_governor; do echo ${newGov} > "$path"; done'`,
            );
        } catch (e) {
            console.error("Failed to set governor", e);
        }
    }

    async function loadSettings() {
        const cmd = `
            cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors; echo "|||";
            cat ${configPath}/settings.toml; echo "|||";
            echo "STATUS" | nc -U /dev/socket/auriya.sock
        `;

        const res = await runCommand(cmd);
        if (!res || res.error) {
            console.warn("Init batch failed");
            return;
        }

        const parts = res.split("|||").map((s) => s.trim());
        const govOutput = parts[0];
        const tomlContent = parts[1];
        const statusOutput = parts[2];

        if (govOutput && !govOutput.includes("No such file")) {
            availableGovernors = govOutput.split(/\s+/).filter(Boolean);
        }
        if (tomlContent) {
            try {
                const s = parse(tomlContent);
                if (s.cpu?.default_governor) gov = s.cpu.default_governor;
                if (s.daemon?.default_mode)
                    globalPreset = s.daemon.default_mode;
            } catch (e) {}
        }
        if (statusOutput && statusOutput.includes("LOG_LEVEL=DEBUG")) {
            debugMode = true;
        }
    }

    async function save(toastMsg = "Settings saved") {
        try {
            const content = await runCommand(`cat ${configPath}/settings.toml`);
            let settings = {};
            if (content && !content.error) {
                try {
                    settings = parse(content);
                } catch (e) {}
            }

            if (!settings.cpu) settings.cpu = {};
            settings.cpu.default_governor = gov;

            if (!settings.daemon) settings.daemon = {};
            settings.daemon.default_mode = globalPreset;

            const newContent = stringify(settings);
            await runCommand(
                `echo '${newContent}' > ${configPath}/settings.toml`,
            );

            await setGlobalGovernor(gov);
            showToast(toastMsg);
        } catch (e) {
            showToast(`Error: ${e.message}`);
        }
    }

    async function toggleDebug() {
        const cmd = debugMode ? "SETLOG DEBUG" : "SETLOG INFO";
        await runCommand(`echo "${cmd}" | nc -U /dev/socket/auriya.sock`);
        showToast(`Debug mode ${debugMode ? "enabled" : "disabled"}`);
    }

    async function exportLogs() {
        const logDir = "/sdcard/Download/AuriyaLogs";
        const daemonLog = "/data/adb/auriya/daemon.log";
        try {
            await runCommand(`mkdir -p ${logDir}`);
            await runCommand(`cp ${daemonLog} ${logDir}/auriya.log`);
            await runCommand(`dmesg > ${logDir}/kernel.log`);
            const zipRes = await runCommand(
                `tar -czf /sdcard/Download/AuriyaLogs.tar.gz -C /sdcard/Download AuriyaLogs`,
            );

            if (zipRes && !zipRes.error) {
                showToast(
                    `Logs exported to /sdcard/Download/AuriyaLogs.tar.gz`,
                );
            } else {
                showToast(`Logs exported to ${logDir}`);
            }
        } catch (e) {
            showToast(`Export failed: ${e.message}`);
        }
    }

    function onGovChange() {
        save(`Governor set to ${gov}`);
    }

    async function restartDaemon() {
        showToast("Restarting daemon...");
        try {
            await runCommand(`echo "RESTART" | nc -U /dev/socket/auriya.sock`);

            for (let i = 1; i <= 5; i++) {
                await new Promise((r) => setTimeout(r, 2000));
                try {
                    const res = await runCommand(
                        `echo "PING" | nc -U /dev/socket/auriya.sock`,
                    );
                    if (res && res.includes("PONG")) {
                        showToast("Daemon restarted successfully!");
                        return;
                    }
                } catch (e) {}
            }
            showToast("Daemon restart failed");
        } catch (e) {
            showToast("Failed to restart daemon");
        }
    }

    onMount(loadSettings);
</script>

<div class="view-section space-y-4">
    <div
        class="bg-surface-container p-6 rounded-[28px] card_border text-on-surface"
    >
        <h2 class="text-lg font-semibold mb-4">{$_("settings.performance")}</h2>
        <div class="flex items-center justify-between p-2 mb-2">
            <div class="flex items-center gap-3">
                <div
                    class="w-10 h-10 rounded-xl bg-surface-variant text-white flex items-center justify-center shrink-0"
                >
                    <Icon name="memory" />
                </div>
                <div>
                    <p class="font-medium">{$_("settings.cpuGovernor")}</p>
                    <p class="text-xs opacity-70">
                        {$_("settings.globalScaling")}
                    </p>
                </div>
            </div>
            <div class="w-36">
                <Select
                    bind:value={gov}
                    options={availableGovernors}
                    on:change={onGovChange}
                    placeholder="Governor"
                />
            </div>
        </div>
        <div class="flex items-center justify-between p-2">
            <div class="flex items-center gap-3">
                <div
                    class="w-10 h-10 rounded-xl bg-surface-variant text-white flex items-center justify-center shrink-0"
                >
                    <Icon name="tune" />
                </div>
                <div>
                    <p class="font-medium">{$_("settings.globalPreset")}</p>
                    <p class="text-xs opacity-70">
                        {$_("settings.defaultProfile")}
                    </p>
                </div>
            </div>
            <div class="w-36">
                <Select
                    bind:value={globalPreset}
                    options={availablePresets}
                    on:change={() => save(`Preset set to ${globalPreset}`)}
                    placeholder="Preset"
                />
            </div>
        </div>
    </div>

    <div
        class="bg-surface-container p-6 rounded-[28px] card_border text-on-surface"
    >
        <h2 class="text-lg font-semibold mb-4">{$_("settings.language")}</h2>
        <div class="flex items-center justify-between p-2">
            <div class="flex items-center gap-3">
                <div
                    class="w-10 h-10 rounded-xl bg-surface-variant text-white flex items-center justify-center shrink-0"
                >
                    <Icon name="language" />
                </div>
                <div>
                    <p class="font-medium">
                        {languages.find((l) => l.code === $locale)?.name ||
                            "English"}
                    </p>
                    <p class="text-xs opacity-70">
                        {$_("settings.appLanguage")}
                    </p>
                </div>
            </div>
            <div class="w-36">
                <Select
                    value={$locale}
                    options={languages.map((l) => ({
                        value: l.code,
                        label: l.name,
                    }))}
                    on:change={(e) => setLocale(e.detail.value)}
                    placeholder="Language"
                />
            </div>
        </div>
    </div>

    <div
        class="bg-surface-container p-6 rounded-[28px] card_border text-on-surface"
    >
        <h2 class="text-lg font-semibold mb-4">{$_("settings.system")}</h2>
        <div
            class="flex items-center justify-between p-2 cursor-pointer hover:bg-surface-variant/10 rounded-xl transition-colors"
            on:click={exportLogs}
            role="button"
            tabindex="0"
            on:keydown={(e) => e.key === "Enter" && exportLogs()}
        >
            <div class="flex items-center gap-3">
                <div
                    class="w-10 h-10 rounded-xl bg-surface-variant text-white flex items-center justify-center shrink-0"
                >
                    <Icon name="bug_report" />
                </div>
                <div>
                    <p class="font-medium">{$_("settings.exportLogs")}</p>
                    <p class="text-xs opacity-70">
                        {$_("settings.exportLogsDesc")}
                    </p>
                </div>
            </div>
            <Icon name="chevron_right" className="text-on-surface-variant" />
        </div>
        <div class="flex items-center justify-between p-2 mt-2">
            <div class="flex items-center gap-3">
                <div
                    class="w-10 h-10 rounded-xl bg-surface-variant text-white flex items-center justify-center shrink-0"
                >
                    <Icon name="terminal" />
                </div>
                <div>
                    <p class="font-medium">{$_("settings.debugMode")}</p>
                    <p class="text-xs opacity-70">
                        {$_("settings.debugModeDesc")}
                    </p>
                </div>
            </div>
            <input
                type="checkbox"
                bind:checked={debugMode}
                on:change={toggleDebug}
                class="toggle"
            />
        </div>
        <div class="flex items-center justify-between p-2 mt-2">
            <div class="flex items-center gap-3">
                <div
                    class="w-10 h-10 rounded-xl bg-surface-variant text-white flex items-center justify-center shrink-0"
                >
                    <Icon name="restart_alt" />
                </div>
                <div>
                    <p class="font-medium">{$_("settings.restartDaemon")}</p>
                    <p class="text-xs opacity-70">
                        {$_("settings.restartDaemonDesc")}
                    </p>
                </div>
            </div>
            <button
                on:click={restartDaemon}
                class="btn btn-sm bg-surface-variant text-on-surface rounded-lg px-4"
            >
                {$_("settings.restart")}
            </button>
        </div>
    </div>
</div>
