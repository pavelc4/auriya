<script>
	import { _ } from "svelte-i18n";
	import { openExternalLink } from "../lib/api";
	import Icon from "../components/ui/Icon.svelte";
	import ferrisSleep from "../assets/ferris_sleep.svg";

	function handleImageError(e) {
		e.target.src = ferrisSleep;
	}

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
		<h2 class="text-lg font-semibold mb-2">{$_("about.support")}</h2>
		<p class="mb-6 opacity-80 text-sm leading-relaxed">
			{$_("about.supportDesc")}
		</p>
		<button
			on:click={() => openExternalLink("https://t.me/pvlcply")}
			class="btn bg-[var(--primary)] text-on-primary w-full rounded-full hover:bg-[var(--primary)]/90 border-none h-12 text-base font-medium normal-case gap-2"
		>
			<Icon name="local_cafe" />
			{$_("about.buyMeCoffee")}
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
					<Icon name="lightbulb" className="text-[20px]" />
				</div>
				<h2 class="text-lg font-semibold">{$_("about.philosophy")}</h2>
			</div>
			<p class="text-sm opacity-80 leading-relaxed flex-grow">
				{$_("about.philosophyDescSimple")}
			</p>
		</div>

		<div
			class="bg-surface-container p-6 rounded-[28px] card_border text-on-surface h-full flex flex-col"
		>
			<div class="flex items-center gap-3 mb-3">
				<div
					class="w-10 h-10 rounded-xl bg-[var(--primary)]/20 text-[var(--primary)] flex items-center justify-center shrink-0"
				>
					<Icon name="code" className="text-[20px]" />
				</div>
				<h2 class="text-lg font-semibold">{$_("about.whyRust")}</h2>
			</div>
			<p class="text-sm opacity-80 leading-relaxed flex-grow">
				{$_("about.whyRustDescSimple")}
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
				<Icon name="group" className="text-[20px]" />
			</div>
			<h2 class="text-lg font-semibold">{$_("about.contributors")}</h2>
		</div>

		<div class="space-y-3">
			<button
				on:click={() => openExternalLink("https://github.com/pavelc4")}
				class="flex w-full text-left items-center gap-4 p-3 rounded-2xl bg-surface-variant/20 hover:bg-surface-variant/40 transition-colors"
			>
				<img
					src="https://github.com/pavelc4.png"
					on:error={handleImageError}
					class="w-10 h-10 rounded-full bg-surface-variant shrink-0"
					alt="pavelc4"
				/>
				<div>
					<p class="text-sm font-medium">pavelc4</p>
					<p class="text-xs opacity-60">{$_("about.creatorLead")}</p>
				</div>
			</button>

			<button
				on:click={() =>
					openExternalLink(
						"https://github.com/pavelc4/auriya/graphs/contributors",
					)}
				class="flex w-full items-center justify-center gap-2 p-3 rounded-2xl hover:bg-surface-variant/10 transition-colors text-sm opacity-70 hover:opacity-100"
			>
				<Icon name="open_in_new" className="text-lg" />
				{$_("about.viewAllContributors")}
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
				<Icon name="extension" className="text-[20px]" />
			</div>
			<div>
				<h2 class="text-lg font-semibold">
					{$_("about.openSourceFoundation")}
				</h2>
				<p class="text-xs opacity-60">
					{$_("about.shouldersOfGiants")}
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
					<Icon name="forum" className="text-[20px]" />
				</div>
				<h2 class="text-lg font-semibold">
					{$_("about.joinCommunity")}
				</h2>
			</div>
			<p class="text-sm opacity-80 mb-4">
				{$_("about.joinCommunityDesc")}
			</p>
			<button
				on:click={() =>
					openExternalLink("https://t.me/paveldiscussion")}
				class="btn btn-sm bg-[var(--tertiary)]/20 hover:bg-[var(--tertiary)]/30 text-[var(--tertiary)] border-none rounded-full px-4 w-full"
			>
				{$_("about.joinDiscussion")}
			</button>
		</div>

		<div
			class="bg-surface-container p-6 rounded-[28px] card_border text-on-surface"
		>
			<div class="flex items-center gap-3 mb-3">
				<div
					class="w-10 h-10 rounded-xl bg-surface-variant text-white flex items-center justify-center shrink-0"
				>
					<Icon name="policy" className="text-[20px]" />
				</div>
				<h2 class="text-lg font-semibold">{$_("about.license")}</h2>
			</div>
			<p class="text-sm opacity-80 mb-5">
				{$_("about.licenseDescSimple")}
			</p>

			<div
				class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-surface-variant/20 border border-white/5 text-xs font-medium text-on-surface-variant"
			>
				<Icon name="copyright" className="text-[14px]" />
				<span>2025 pavelc4</span>
			</div>
		</div>
	</div>
</div>
