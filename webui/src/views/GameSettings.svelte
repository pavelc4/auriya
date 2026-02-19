<script>
  import { onMount, createEventDispatcher } from "svelte";
  import { _ } from "svelte-i18n";
  import { activeGames, supportedRefreshRates } from "../lib/stores";
  import { runCommand, showToast } from "../lib/api";
  import Icon from "../components/ui/Icon.svelte";
  import Select from "../components/ui/Select.svelte";

  export let pkg;
  export let onBack;

  let isEnabled = false;
  let dnd = false;
  let mode = "performance";
  let gov = "performance";
  let fps = "";
  let rate = "";
  let globalGov = "";
  let managers = ["performance", "schedutil", "powersave", "interactive"];
  let availableRates = [60, 90, 120];

  async function getGlobalDefaults() {
    try {
      const content = await runCommand(
        `cat /data/adb/modules/auriya/settings.toml`,
      );
      if (content && !content.error) {
        // Minimal parse
        const govMatch = content.match(
          /default_governor\s*=\s*['"]?([^'"\s]+)['"]?/,
        );
        if (govMatch) globalGov = govMatch[1];
      }
    } catch (e) {}
  }

  const dispatch = createEventDispatcher();

  onMount(async () => {
    await getGlobalDefaults();
    const govOutput = await runCommand(
      "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors",
    );
    if (govOutput && !govOutput.error) {
      managers = govOutput.split(" ").filter(Boolean);
    }

    if ($supportedRefreshRates.length > 0) {
      availableRates = $supportedRefreshRates;
    }
  });

  $: {
    const profile = $activeGames.find((g) => g.package === pkg);
    if (profile) {
      isEnabled = true;
      dnd = profile.enable_dnd || false;
      mode = profile.mode || "performance";
      gov = profile.cpu_governor || "performance";

      // Convert backend format to UI format
      // Backend serializes: Single(60) → 60, Array([30,60,90]) → [30,60,90]
      if (profile.target_fps) {
        if (Array.isArray(profile.target_fps)) {
          const max = Math.max(...profile.target_fps);
          if (max === 60) fps = "auto_60";
          else if (max === 90) fps = "auto_90";
          else if (max === 120) fps = "auto_120";
          else fps = "";
        } else {
          fps = profile.target_fps.toString();
        }
      } else {
        fps = "";
      }
      rate = profile.refresh_rate || "";
    }
  }

  async function save() {
    const socketPath = "/dev/socket/auriya.sock";
    try {
      if (isEnabled) {
        const isNew = !$activeGames.find((g) => g.package === pkg);
        if (isNew) {
          await runCommand(`echo "ADD_GAME ${pkg}" | nc -U ${socketPath}`);
        }

        let updateCmd = `UPDATE_GAME ${pkg} gov=${gov} dnd=${dnd} mode=${mode}`;

        if (fps) {
          if (fps === "auto_60") {
            updateCmd += ` fps_array=30,60`;
          } else if (fps === "auto_90") {
            updateCmd += ` fps_array=30,60,90`;
          } else if (fps === "auto_120") {
            updateCmd += ` fps_array=30,60,90,120`;
          } else {
            updateCmd += ` fps=${fps}`;
          }
        }
        if (rate) updateCmd += ` rate=${rate}`;

        await runCommand(`echo "${updateCmd}" | nc -U ${socketPath}`);
        showToast(`Saved settings for ${pkg}`);
      } else {
        await runCommand(`echo "REMOVE_GAME ${pkg}" | nc -U ${socketPath}`);
        showToast(`Removed ${pkg}`);
      }

      const output = await runCommand(
        `echo "GET_GAMELIST" | nc -U ${socketPath}`,
      );
      if (output && !output.error && !output.startsWith("ERR")) {
        try {
          activeGames.set(JSON.parse(output.substring(output.indexOf("["))));
        } catch (e) {}
      }

      onBack();
    } catch (e) {
      showToast(`Error: ${e.message}`);
    }
  }
</script>

<div class="view-section space-y-6 pb-40 animate-fade-in">
  <div class="flex items-center gap-4 pt-2">
    <button
      on:click={onBack}
      class="btn btn-circle btn-ghost bg-surface-container-high text-on-surface hover:bg-surface-variant/20"
    >
      <Icon name="arrow_back" />
    </button>
    <div class="flex flex-col">
      <h2 class="text-xl font-bold leading-tight">{pkg}</h2>
      <span class="text-xs opacity-60"
        >{$_("gameSettings.gameConfiguration")}</span
      >
    </div>
  </div>
  <div
    class="bg-surface-container p-6 rounded-[32px] card_border text-on-surface space-y-5"
  >
    <div class="flex items-center justify-between p-2">
      <div>
        <p class="font-bold text-lg">
          {$_("gameSettings.optimization")}
        </p>
        <p class="text-xs opacity-60">
          {$_("gameSettings.enableForApp")}
        </p>
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
        <p class="font-bold text-lg">{$_("gameSettings.dnd")}</p>
        <p class="text-xs opacity-60">
          {$_("gameSettings.dndDesc")}
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
        <Icon name="tune" className="text-[var(--primary)]" />
        <span class="font-bold">{$_("gameSettings.profile")}</span>
      </div>
      <div class="w-full">
        <Select
          bind:value={mode}
          disabled={!isEnabled}
          placeholder={$_("gameSettings.selectProfile")}
          options={[
            {
              value: "powersave",
              label: $_("gameSettings.powersave"),
            },
            { value: "balance", label: $_("gameSettings.balance") },
            {
              value: "performance",
              label: $_("gameSettings.performance"),
            },
          ]}
        />
      </div>
    </div>
  </div>

  <div
    class="bg-surface-container p-6 rounded-[32px] card_border text-on-surface space-y-4"
  >
    <h3 class="font-bold opacity-80 px-2">
      {$_("gameSettings.advancedConfig")}
    </h3>
    <div class="grid gap-4">
      <div class="form-control w-full">
        <label class="label" for="game-gov"
          ><span class="label-text text-on-surface font-medium"
            >{$_("gameSettings.cpuGovernor")}</span
          ></label
        >
        <Select
          bind:value={gov}
          disabled={!isEnabled}
          options={managers.map((m) => ({
            value: m,
            label:
              m + (m === globalGov ? ` (${$_("gameSettings.default")})` : ""),
          }))}
        />
      </div>
      <div class="grid grid-cols-2 gap-4">
        <div class="form-control w-full">
          <label class="label" for="game-fps"
            ><span class="label-text text-on-surface font-medium"
              >{$_("gameSettings.targetFps")}</span
            ></label
          >
          <div class="w-full">
            <Select
              bind:value={fps}
              disabled={!isEnabled}
              placeholder="FPS"
              options={[
                {
                  value: "",
                  label: $_("gameSettings.default"),
                },
                { value: "auto_60", label: "Auto (max 60)" },
                { value: "auto_90", label: "Auto (max 90)" },
                { value: "auto_120", label: "Auto (max 120)" },
              ]}
            />
          </div>
        </div>
        <div class="form-control w-full">
          <label class="label" for="settings-refresh-select"
            ><span class="label-text text-on-surface font-medium"
              >{$_("gameSettings.refreshRate")}</span
            ></label
          >
          <div class="w-full">
            <Select
              bind:value={rate}
              disabled={!isEnabled}
              placeholder="Hz"
              options={[
                {
                  value: "",
                  label: $_("gameSettings.default"),
                },
                ...availableRates.map((r) => ({
                  value: r,
                  label: r + " Hz",
                })),
              ]}
            />
          </div>
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
      {$_("gameSettings.save")}
    </button>
  </div>
</div>
