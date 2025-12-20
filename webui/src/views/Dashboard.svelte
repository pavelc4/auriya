<script>
    import { systemInfo } from "../lib/stores";
    import { _ } from "svelte-i18n";
    import { openExternalLink } from "../lib/api";
    import ferrisHappy from "../assets/ferris_happy.svg";
    import ferrisSleep from "../assets/ferris_sleep.svg";
    import Icon from "../components/ui/Icon.svelte";

    $: isDaemonRunning =
        $systemInfo.pid !== "Service not running" && $systemInfo.pid !== "null";
    $: daemonIcon = isDaemonRunning ? ferrisHappy : ferrisSleep;
    $: statusClass = $systemInfo.version.toLowerCase().includes("beta")
        ? "bg-yellow-500/10 text-yellow-500"
        : "bg-green-500/10 text-green-500";

    $: statusText = $systemInfo.version.toLowerCase().includes("beta")
        ? "BETA"
        : "STABLE";

    $: versionParts = $systemInfo.version.match(/^([\d.]+)\s*\((.+)\)$/) || [
        $systemInfo.version,
        $systemInfo.version,
        "",
    ];
    $: mainVersion = versionParts[1];
    $: buildInfo = versionParts[2];

    $: tempVal = parseInt($systemInfo.temp) || 0;
    $: tempColorVar =
        tempVal >= 45
            ? "var(--error)"
            : tempVal >= 40
              ? "var(--primary)"
              : "var(--tertiary)";

    $: tempCardClass =
        tempVal >= 45
            ? "bg-[var(--error)]/5"
            : tempVal >= 40
              ? "bg-[var(--primary)]/5"
              : "bg-[var(--tertiary)]/5 hover:bg-[var(--tertiary)]/10";
    $: tempIconBgClass =
        tempVal >= 45
            ? "bg-[var(--error)]/20"
            : tempVal >= 40
              ? "bg-[var(--primary)]/20"
              : "bg-[var(--tertiary)]/20";
    $: tempTextClass =
        tempVal >= 45
            ? "text-[var(--error)]"
            : tempVal >= 40
              ? "text-[var(--primary)]"
              : "text-[var(--tertiary)]";
    $: tempBadgeBgClass =
        tempVal >= 45
            ? "bg-[var(--error)]/10"
            : tempVal >= 40
              ? "bg-[var(--primary)]/10"
              : "bg-[var(--tertiary)]/10";

    function handleImageError(e) {
        e.target.src = ferrisSleep;
    }
</script>

<div class="view-section space-y-6">
    <!-- Status Card -->
    <div
        class="relative overflow-hidden bg-gradient-to-br from-surface-container via-surface-container to-[var(--primary)]/10 p-8 rounded-[32px] card_border text-on-surface shadow-lg shadow-[var(--primary)]/5"
    >
        <div class="relative z-10 flex items-center justify-between">
            <div>
                <div class="flex items-center gap-3 mb-1">
                    <p
                        class="text-sm font-medium opacity-60 uppercase tracking-wider"
                    >
                        {$_("dashboard.daemonStatus")}
                    </p>
                </div>
                <h2 class="text-2xl font-bold mb-2">
                    {$_("dashboard." + $systemInfo.daemonStatus)}
                </h2>
                <div class="flex flex-wrap gap-2">
                    <div
                        class="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-surface-variant/20 border border-white/5"
                    >
                        <span
                            class="w-2 h-2 rounded-full bg-[var(--tertiary)] animate-pulse"
                        ></span>
                        <p class="text-[10px] font-mono opacity-80">
                            PID: {$systemInfo.pid === "Service not running" ||
                            !$systemInfo.pid ||
                            $systemInfo.pid === "null"
                                ? $_("dashboard.serviceNotRunning")
                                : $systemInfo.pid}
                        </p>
                    </div>
                </div>
            </div>
            <div class="bg-surface-variant p-4 rounded-[24px]">
                <img
                    src={daemonIcon}
                    class="w-16 h-16 transition-opacity duration-500"
                    alt="Status"
                />
            </div>
        </div>
    </div>

    <div>
        <h3 class="text-lg font-bold mb-3 px-2">
            {$_("dashboard.liveMetrics")}
        </h3>
        <div class="bg-surface-container p-5 rounded-[32px] card_border">
            <div class="grid grid-cols-2 gap-3">
                <div
                    class="bg-[var(--primary)]/5 hover:bg-[var(--primary)]/10 transition-colors p-5 rounded-[24px] text-on-surface flex flex-col justify-between h-36"
                >
                    <div
                        class="w-10 h-10 rounded-full bg-[var(--primary)]/20 text-[var(--primary)] flex items-center justify-center"
                    >
                        <Icon name="bolt" className="text-[24px]" />
                    </div>
                    <div>
                        <p class="text-xs opacity-60 font-medium">
                            {$_("dashboard.profile")}
                        </p>
                        <p class="text-lg font-bold truncate">
                            {$systemInfo.profile}
                        </p>
                    </div>
                </div>
                <div
                    class="bg-[var(--secondary)]/5 p-5 rounded-[24px] text-on-surface flex flex-col justify-between h-36"
                >
                    <div class="flex justify-between items-start">
                        <div
                            class="w-10 h-10 rounded-full bg-[var(--secondary)]/20 text-[var(--secondary)] flex items-center justify-center"
                        >
                            <Icon name="memory" className="text-[20px]" />
                        </div>
                        <span
                            class="text-[10px] font-bold uppercase tracking-wider opacity-60 bg-[var(--secondary)]/10 text-[var(--secondary)] px-2 py-1 rounded-full"
                            >RAM</span
                        >
                    </div>
                    <div>
                        <p class="text-xs opacity-60 font-medium">
                            {$_("dashboard.daemonUsage")}
                        </p>
                        <p class="text-lg font-bold truncate">
                            {$systemInfo.ram}
                        </p>
                    </div>
                </div>
                <div
                    class="bg-[var(--tertiary)]/5 p-5 rounded-[24px] text-on-surface flex flex-col justify-between h-36"
                >
                    <div class="flex justify-between items-start">
                        <div
                            class="w-10 h-10 rounded-full bg-[var(--tertiary)]/20 text-[var(--tertiary)] flex items-center justify-center"
                        >
                            <Icon name="battery_full" className="text-[20px]" />
                        </div>
                        <span
                            class="text-[8px] font-bold uppercase tracking-wider opacity-60 bg-[var(--tertiary)]/10 text-[var(--tertiary)] px-2 py-1 rounded-full"
                            >Power</span
                        >
                    </div>
                    <div>
                        <p class="text-xs opacity-60 font-medium">
                            {$_("dashboard.battery")}
                        </p>
                        <p class="text-xl font-bold truncate">
                            {$systemInfo.battery}
                        </p>
                    </div>
                </div>
                <div
                    class="{tempCardClass} transition-colors p-5 rounded-[24px] text-on-surface flex flex-col justify-between h-36"
                >
                    <div class="flex justify-between items-start">
                        <div
                            class="w-10 h-10 rounded-full {tempIconBgClass} {tempTextClass} flex items-center justify-center"
                        >
                            <Icon name="thermostat" className="text-[20px]" />
                        </div>
                        <span
                            class="text-[8px] font-bold uppercase tracking-wide opacity-60 {tempBadgeBgClass} {tempTextClass} px-1 py-[1px] rounded-full"
                            >Temp</span
                        >
                    </div>
                    <div>
                        <p class="text-xs opacity-60 font-medium">
                            {$_("dashboard.thermal")}
                        </p>
                        <p class="text-xl font-bold truncate">
                            {$systemInfo.temp}
                        </p>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <div>
        <h3 class="text-lg font-bold mb-3 px-2">
            {$_("dashboard.deviceDetails")}
        </h3>
        <div class="bg-surface-container p-5 rounded-[32px] card_border">
            <div class="grid grid-cols-2 gap-3">
                <div
                    class="bg-surface-container-high p-5 rounded-[24px] text-on-surface flex flex-col justify-between h-32"
                >
                    <div
                        class="w-10 h-10 rounded-xl bg-surface-variant/20 flex items-center justify-center text-on-surface-variant"
                    >
                        <Icon name="developer_board" className="text-[24px]" />
                    </div>
                    <div>
                        <p class="text-xs opacity-60 font-medium">
                            {$_("dashboard.chipset")}
                        </p>
                        <p class="text-lg font-bold truncate">
                            {$systemInfo.chipset}
                        </p>
                    </div>
                </div>
                <div
                    class="bg-surface-container-high p-5 rounded-[24px] text-on-surface flex flex-col justify-between h-32"
                >
                    <div
                        class="w-10 h-10 rounded-xl bg-surface-variant/20 flex items-center justify-center text-on-surface-variant"
                    >
                        <Icon name="memory" className="text-[24px]" />
                    </div>
                    <div>
                        <p class="text-xs opacity-60 font-medium">
                            {$_("dashboard.architecture")}
                        </p>
                        <p class="text-lg font-bold truncate">
                            {$systemInfo.deviceArch}
                        </p>
                    </div>
                </div>
                <div
                    class="bg-surface-container-high p-5 rounded-[24px] text-on-surface flex flex-col justify-between h-32"
                >
                    <div
                        class="w-10 h-10 rounded-xl bg-surface-variant/20 flex items-center justify-center text-on-surface-variant"
                    >
                        <Icon name="smartphone" className="text-[24px]" />
                    </div>
                    <div>
                        <p class="text-xs opacity-60 font-medium">
                            {$_("dashboard.codename")}
                        </p>
                        <p class="text-lg font-bold truncate">
                            {$systemInfo.codename}
                        </p>
                    </div>
                </div>
                <div
                    class="bg-surface-container-high p-5 rounded-[24px] text-on-surface flex flex-col justify-between h-32"
                >
                    <div
                        class="w-10 h-10 rounded-xl bg-surface-variant/20 flex items-center justify-center text-on-surface-variant"
                    >
                        <Icon name="android" className="text-[24px]" />
                    </div>
                    <div>
                        <p class="text-xs opacity-60 font-medium">
                            {$_("dashboard.androidSdk")}
                        </p>
                        <p class="text-lg font-bold truncate">
                            {$systemInfo.sdk}
                        </p>
                    </div>
                </div>
                <div
                    class="col-span-2 bg-surface-container-high p-5 rounded-[24px] text-on-surface"
                >
                    <div class="flex items-center mb-1">
                        <p class="text-xl opacity-90 font-bold">
                            {$_("dashboard.kernelVersion")}
                        </p>
                    </div>
                    <p
                        class="text-sm font-mono opacity-90 break-words leading-relaxed"
                    >
                        {$systemInfo.kernel}
                    </p>
                </div>
            </div>
        </div>
    </div>

    <div>
        <h3 class="text-lg font-bold mb-3 px-2">
            {$_("dashboard.moduleInfo")}
        </h3>

        <div class="bg-surface-container p-5 rounded-[32px] card_border">
            <div class="grid grid-cols-2 gap-4">
                <div
                    class="relative z-50 bg-surface-container-high p-4 pb-6 mb-[-18px] rounded-t-[24px] rounded-bl-none rounded-br-none text-on-surface h-48 flex flex-col justify-between group"
                >
                    <div
                        class="absolute bottom-0 -right-[16px] w-[16px] h-[16px] bg-surface-container-high overflow-hidden"
                    >
                        <div
                            class="w-full h-full bg-surface-container rounded-bl-[16px]"
                        ></div>
                    </div>
                    <div class="flex justify-between items-start">
                        <div class="relative">
                            <div
                                class="w-16 h-16 rounded-2xl overflow-hidden border-2 border-surface shadow-md"
                            >
                                <img
                                    src="https://github.com/Pavelc4.png"
                                    on:error={handleImageError}
                                    alt="Pavelc4"
                                    class="w-full h-full object-cover"
                                />
                            </div>
                            <div
                                class="absolute -bottom-1 -right-1 w-5 h-5 bg-surface rounded-full flex items-center justify-center"
                            >
                                <div
                                    class="w-3 h-3 bg-[var(--tertiary)] rounded-full animate-pulse"
                                ></div>
                            </div>
                        </div>
                        <span
                            class="px-2 py-0.5 rounded-lg bg-surface-variant/20 text-[9px] font-bold tracking-wider uppercase opacity-70 whitespace-nowrap"
                        >
                            Dev
                        </span>
                    </div>
                    <div>
                        <p
                            class="text-[10px] text-on-surface-variant font-medium uppercase tracking-widest mb-1"
                        >
                            {$_("dashboard.maintainer")}
                        </p>
                        <div class="flex items-center gap-1">
                            <h4 class="text-lg font-bold leading-none">
                                Pavelc4
                            </h4>
                            <Icon
                                name="verified"
                                className="text-[var(--primary)] text-[18px]"
                            />
                        </div>
                        <p class="text-xs opacity-50 font-mono mt-1">
                            @pavelc4
                        </p>
                    </div>
                </div>
                <div
                    class="relative z-20 bg-surface-container-high p-4 rounded-[24px] text-on-surface flex flex-col justify-between h-44"
                >
                    <div class="flex justify-between items-start">
                        <div
                            class="w-10 h-10 rounded-xl bg-[var(--primary)]/10 text-[var(--primary)] flex items-center justify-center"
                        >
                            <Icon
                                name="deployed_code"
                                className="text-[24px]"
                            />
                        </div>
                        <div class="flex flex-col items-end">
                            <span
                                class="{statusClass} px-2 py-0.5 rounded-full text-[10px] font-bold"
                            >
                                {statusText}
                            </span>
                            <span class="text-[10px] opacity-40 font-mono mt-1"
                                >{$systemInfo.commit}</span
                            >
                        </div>
                    </div>
                    <div>
                        <p
                            class="text-[10px] text-on-surface-variant font-medium uppercase tracking-widest mb-0.5"
                        >
                            {$_("dashboard.release")}
                        </p>
                        <span class="text-2xl font-bold tracking-tighter"
                            >{mainVersion}</span
                        >
                        {#if buildInfo}
                            <p class="text-[10px] opacity-50 font-mono mt-0.5">
                                {buildInfo}
                            </p>
                        {/if}
                        <p class="text-[10px] opacity-40 mt-1">
                            {$systemInfo.updateTime}
                        </p>
                    </div>
                </div>
                <div
                    class="relative z-10 col-span-2 bg-surface-container-high p-4 pt-7 rounded-b-[24px] rounded-tr-[24px] rounded-tl-none border-t-0 text-on-surface"
                >
                    <div class="mb-4">
                        <p class="text-sm opacity-80 leading-snug font-normal">
                            {@html $_("dashboard.descriptionHtml", {
                                default: `A fully <span class="text-[var(--tertiary)] font-bold">free</span> and open-source hobby project. Built with <span class="text-[var(--primary)] font-bold">Rust</span> for performance.`,
                            })}
                        </p>
                    </div>

                    <div class="grid grid-cols-2 gap-3 mt-2">
                        <button
                            on:click={() =>
                                openExternalLink(
                                    "https://github.com/Pavelc4/Auriya",
                                )}
                            class="btn btn-sm h-10 bg-surface-container hover:bg-surface-variant/20 text-on-surface rounded-xl normal-case font-medium gap-2"
                        >
                            <img
                                src="https://github.com/fluidicon.png"
                                on:error={handleImageError}
                                class="w-4 h-4 opacity-70 grayscale"
                                alt="GitHub"
                            />
                            GitHub
                        </button>
                        <button
                            on:click={() =>
                                openExternalLink("https://t.me/pvlcply")}
                            class="btn btn-sm h-10 bg-[var(--primary)]/10 hover:bg-[var(--primary)]/20 text-[var(--primary)] rounded-xl normal-case font-medium gap-2"
                        >
                            <svg
                                xmlns="http://www.w3.org/2000/svg"
                                class="w-4 h-4 opacity-80"
                                viewBox="0 0 240 240"
                                fill="currentColor"
                            >
                                <path
                                    d="M120,0C53.7,0,0,53.7,0,120s53.7,120,120,120s120-53.7,120-120S186.3,0,120,0z M175.9,79.4l-20.5,96.8 c-1.5,6.7-5.5,8.3-11.1,5.2l-30.7-22.6l-14.8,14.3c-1.6,1.6-3,3-6.1,3l2.2-31.2l56.8-51.3c2.5-2.2-0.5-3.4-3.9-1.2l-70.2,44.1 L46,122.5c-6.6-2.1-6.7-6.6,1.4-9.7l120-46.3C173.6,64.5,178.1,68.7,175.9,79.4z"
                                />
                            </svg>
                            {$_("dashboard.updates")}
                        </button>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>
