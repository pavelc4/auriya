<script>
  import { onMount } from "svelte";
  import { _ } from "svelte-i18n";
  import { view, systemInfo, supportedRefreshRates } from "./lib/stores";
  import { runCommand, showToast } from "./lib/api";
  import Dashboard from "./views/Dashboard.svelte";
  import Games from "./views/Games.svelte";
  import Settings from "./views/Settings.svelte";
  import About from "./views/About.svelte";
  import Toast from "./components/ui/Toast.svelte";
  import Icon from "./components/ui/Icon.svelte";

  const configPath = "/data/adb/.config/auriya";
  const modPath = "/data/adb/modules/auriya";

  $: CurrentComponent =
    $view === "dashboard"
      ? Dashboard
      : $view === "games"
        ? Games
        : $view === "settings"
          ? Settings
          : $view === "about"
            ? About
            : Dashboard;

  async function loadSystemInfo() {
    const cmd1 = `
            grep "^version=" ${modPath}/module.prop | cut -d= -f2; echo "|||";
            grep "^versionCode=" ${modPath}/module.prop | cut -d= -f2; echo "|||";
            getprop ro.product.cpu.abi; echo "|||";
            stat -c %Y ${modPath}/module.prop
        `;

    const res1 = await runCommand(cmd1);
    if (!res1 || res1.error) {
      console.warn("Init fetch failed");
    } else {
      const parts = res1.split("|||").map((s) => s.trim());
      const version = parts[0] || "Unknown";
      const commit = parts[1] || "Unknown";
      let arch = parts[2] || "Unknown";

      if (arch.includes("arm64")) arch = "v8a";
      else if (arch.includes("armeabi")) arch = "v7a";
      else if (arch.includes("x86_64")) arch = "x64";
      else if (arch.includes("x86")) arch = "x86";

      systemInfo.update((s) => ({
        ...s,
        version,
        commit,
        arch,
        deviceArch: arch,
      }));

      const modTime = parts[3];
      if (modTime && !isNaN(modTime)) {
        const diff = Math.floor(Date.now() / 1000) - parseInt(modTime);
        let timeStr = "Just now";
        if (diff < 3600) timeStr = `Updated ${Math.floor(diff / 60)}m ago`;
        else if (diff < 86400)
          timeStr = `Updated ${Math.floor(diff / 3600)}h ago`;
        else timeStr = `Updated ${Math.floor(diff / 86400)}d ago`;

        systemInfo.update((s) => ({ ...s, updateTime: timeStr }));
      }
    }

    const cmd2 = `
            cat ${configPath}/current_profile; echo "|||";
            uname -r; echo "|||";
            getprop ro.board.platform; echo "|||";
            getprop ro.product.device; echo "|||";
            getprop ro.build.version.sdk; echo "|||";
            cat /sys/class/power_supply/battery/capacity; echo "|||";
            cat /sys/class/thermal/thermal_zone*/temp 2>/dev/null | head -n 5; echo "|||";
            PID=$(pidof auriya || echo "null"); echo $PID; echo "|||";
            if [ "$PID" != "null" ]; then grep VmRSS /proc/$PID/status | awk '{print $2}'; else echo "-"; fi
        `;

    const res2 = await runCommand(cmd2);
    if (res2 && !res2.error) {
      const parts = res2.split("|||").map((s) => s.trim());
      const profiles = {
        "0": "Init",
        "1": "Performance",
        "2": "Balance",
        "3": "Powersave",
      };
      const profile = profiles[parts[0]] || "Unknown";
      const kernel = parts[1] || "Unknown";
      const chipset = parts[2] || "Unknown";
      const codename = parts[3] || "Unknown";
      const sdk = parts[4] || "Unknown";
      const battery = parts[5] && !isNaN(parts[5]) ? `${parts[5]}%` : "Unknown";

      let temp = "Unknown";
      if (parts[6]) {
        const lines = parts[6].split("\n");
        for (const t of lines) {
          const v = parseInt(t);
          if (!isNaN(v) && v > 1000) {
            temp = `${Math.round(v / 1000)}°C`;
            break;
          }
        }
      }

      const pid = parts[7];
      const rss = parts[8];
      let daemonStatus = "stopped";
      let ram = "-";

      if (pid !== "null" && pid.length > 0) {
        daemonStatus = "working";
        if (rss && rss !== "-") {
          ram = `${(parseInt(rss) / 1024).toFixed(1)} MB`;
        }
      }

      systemInfo.update((s) => ({
        ...s,
        profile,
        kernel,
        chipset,
        codename,
        sdk,
        battery,
        temp,
        pid,
        daemonStatus,
        ram,
      }));
    }

    try {
      const ratesRes = await runCommand(
        `echo "GET_SUPPORTED_RATES" | nc -U /dev/socket/auriya.sock`,
      );
      if (ratesRes && !ratesRes.error && !ratesRes.startsWith("ERR")) {
        const start = ratesRes.indexOf("[");
        if (start !== -1) {
          supportedRefreshRates.set(JSON.parse(ratesRes.substring(start)));
        }
      }
    } catch (e) {}
  }

  function setView(v) {
    view.set(v);
  }

  let currentTheme = "auto";

  const themeOrder = ["auto", "dark", "light"];
  const themeIcons = {
    auto: "auto_mode",
    dark: "dark_mode",
    light: "light_mode",
  };

  function toggleTheme() {
    const idx = themeOrder.indexOf(currentTheme);
    const next = themeOrder[(idx + 1) % themeOrder.length];
    document.documentElement.setAttribute("data-theme", next);
    localStorage.setItem("theme", next);
    currentTheme = next;
  }

  onMount(() => {
    const saved = localStorage.getItem("theme");
    if (saved && themeOrder.includes(saved)) {
      document.documentElement.setAttribute("data-theme", saved);
      currentTheme = saved;
    }
    loadSystemInfo();
  });
</script>

<main class="container mx-auto p-4 pb-28 max-w-2xl">
  <div class="flex items-center justify-between mb-8 pt-4 px-2">
    <div class="flex items-center gap-2">
      <h1 class="text-3xl font-bold tracking-tight text-gradient">Auriya</h1>
    </div>

    <button
      on:click={toggleTheme}
      class="btn btn-ghost btn-circle text-on-surface-variant hover:bg-surface-variant/20 transition-transform active:scale-95"
    >
      <Icon
        name={themeIcons[currentTheme]}
        className="text-2xl transition-all duration-300"
      />
    </button>
  </div>

  <svelte:component this={CurrentComponent} />

  <Toast />
</main>

<div
  class="bg-surface-container-high fixed bottom-4 z-[100] h-20 rounded-[28px] w-[calc(100%-2rem)] max-w-2xl mx-auto left-0 right-0 grid grid-cols-4 items-center shadow-lg"
>
  <button
    on:click={() => setView("dashboard")}
    class="nav-btn group text-on-surface {$view === 'dashboard'
      ? 'active'
      : 'opacity-60'} flex flex-col items-center justify-center w-full h-full"
  >
    <div
      class="indicator-container p-1 px-5 rounded-full transition-colors duration-300 group-[.active]:bg-surface-variant/30 flex items-center justify-center"
    >
      <Icon
        name="dashboard"
        className="group-[.active]:text-[var(--primary)] text-[24px] {$view ===
        'dashboard'
          ? 'icon-filled'
          : ''}"
      />
    </div>
    <span class="btm-nav-label text-xs font-medium mt-1">{$_("nav.home")}</span>
  </button>

  <button
    on:click={() => setView("games")}
    class="nav-btn group text-on-surface {$view === 'games'
      ? 'active'
      : 'opacity-60'} flex flex-col items-center justify-center w-full h-full"
  >
    <div
      class="indicator-container p-1 px-5 rounded-full transition-colors duration-300 group-[.active]:bg-surface-variant/30 flex items-center justify-center"
    >
      <Icon
        name="sports_esports"
        className="group-[.active]:text-[var(--primary)] text-[24px] {$view ===
        'games'
          ? 'icon-filled'
          : ''}"
      />
    </div>
    <span class="btm-nav-label text-xs font-medium mt-1">{$_("nav.games")}</span
    >
  </button>

  <button
    on:click={() => setView("about")}
    class="nav-btn group text-on-surface {$view === 'about'
      ? 'active'
      : 'opacity-60'} flex flex-col items-center justify-center w-full h-full"
  >
    <div
      class="indicator-container p-1 px-5 rounded-full transition-colors duration-300 group-[.active]:bg-surface-variant/30 flex items-center justify-center"
    >
      <Icon
        name="info"
        className="group-[.active]:text-[var(--primary)] text-[24px] {$view ===
        'about'
          ? 'icon-filled'
          : ''}"
      />
    </div>
    <span class="btm-nav-label text-xs font-medium mt-1">{$_("nav.about")}</span
    >
  </button>

  <button
    on:click={() => setView("settings")}
    class="nav-btn group text-on-surface {$view === 'settings'
      ? 'active'
      : 'opacity-60'} flex flex-col items-center justify-center w-full h-full"
  >
    <div
      class="indicator-container p-1 px-5 rounded-full transition-colors duration-300 group-[.active]:bg-surface-variant/30 flex items-center justify-center"
    >
      <Icon
        name="settings"
        className="group-[.active]:text-[var(--primary)] text-[24px] {$view ===
        'settings'
          ? 'icon-filled'
          : ''}"
      />
    </div>
    <span class="btm-nav-label text-xs font-medium mt-1"
      >{$_("nav.settings")}</span
    >
  </button>
</div>
