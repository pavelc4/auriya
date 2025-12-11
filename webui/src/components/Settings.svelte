<script>
    import { onMount } from "svelte";
    import { parse, stringify } from "smol-toml";
    import { runCommand, showToast } from "../lib/api";

    let gov = "schedutil";
    let fps = "60";
    let debugMode = false;

    let availableGovernors = ["schedutil", "performance"];
    const availableFps = [30, 45, 60, 90, 120];

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
                if (s.fas?.target_fps) fps = s.fas.target_fps;
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

            if (!settings.fas) settings.fas = {};
            settings.fas.target_fps = parseInt(fps);

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

    function onFpsChange() {
        save(`Target FPS set to ${fps}`);
    }

    onMount(loadSettings);
</script>

<div class="view-section space-y-4">
    <div
        class="bg-surface-container p-6 rounded-[28px] card_border text-on-surface"
    >
        <h2 class="text-lg font-semibold mb-4">Performance</h2>
        <div class="flex items-center justify-between p-2 mb-2">
            <div class="flex items-center gap-3">
                <div
                    class="w-10 h-10 rounded-xl bg-surface-variant text-white flex items-center justify-center shrink-0"
                >
                    <span class="material-symbols-rounded">memory</span>
                </div>
                <div>
                    <p class="font-medium">CPU Governor</p>
                    <p class="text-xs opacity-70">Global scaling governor</p>
                </div>
            </div>
            <select
                bind:value={gov}
                on:change={onGovChange}
                class="select bg-surface-container-high w-32 h-10 min-h-0 rounded-xl text-sm px-3"
            >
                {#each availableGovernors as g}
                    <option value={g}>{g}</option>
                {/each}
            </select>
        </div>
        <div class="flex items-center justify-between p-2">
            <div class="flex items-center gap-3">
                <div
                    class="w-10 h-10 rounded-xl bg-surface-variant text-white flex items-center justify-center shrink-0"
                >
                    <span class="material-symbols-rounded">speed</span>
                </div>
                <div>
                    <p class="font-medium">Target FPS</p>
                    <p class="text-xs opacity-70">Global frame rate target</p>
                </div>
            </div>
            <select
                bind:value={fps}
                on:change={onFpsChange}
                class="select bg-surface-container-high w-24 h-10 min-h-0 rounded-xl px-3"
            >
                {#each availableFps as f}
                    <option value={f}>{f}</option>
                {/each}
            </select>
        </div>
    </div>

    <div
        class="bg-surface-container p-6 rounded-[28px] card_border text-on-surface"
    >
        <h2 class="text-lg font-semibold mb-4">Language</h2>
        <div class="flex items-center justify-between p-2">
            <div class="flex items-center gap-3">
                <div
                    class="w-10 h-10 rounded-xl bg-surface-variant text-white flex items-center justify-center shrink-0"
                >
                    <span class="material-symbols-rounded">language</span>
                </div>
                <div>
                    <p class="font-medium">English</p>
                    <p class="text-xs opacity-70">Application language</p>
                </div>
            </div>
            <span
                class="text-xs font-bold px-3 py-1 rounded-full bg-surface-variant/20 text-on-surface-variant"
                >EN</span
            >
        </div>
    </div>

    <div
        class="bg-surface-container p-6 rounded-[28px] card_border text-on-surface"
    >
        <h2 class="text-lg font-semibold mb-4">System</h2>
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
                    <span class="material-symbols-rounded">bug_report</span>
                </div>
                <div>
                    <p class="font-medium">Export Logs</p>
                    <p class="text-xs opacity-70">Save kernel & daemon logs</p>
                </div>
            </div>
            <span class="material-symbols-rounded text-on-surface-variant"
                >chevron_right</span
            >
        </div>
        <div class="flex items-center justify-between p-2 mt-2">
            <div class="flex items-center gap-3">
                <div
                    class="w-10 h-10 rounded-xl bg-surface-variant text-white flex items-center justify-center shrink-0"
                >
                    <span class="material-symbols-rounded">terminal</span>
                </div>
                <div>
                    <p class="font-medium">Debug Mode</p>
                    <p class="text-xs opacity-70">Enable verbose logging</p>
                </div>
            </div>
            <input
                type="checkbox"
                bind:checked={debugMode}
                on:change={toggleDebug}
                class="toggle"
            />
        </div>
    </div>
</div>
