import { writable, derived } from 'svelte/store';
import type { Writable, Readable } from 'svelte/store';

export const view: Writable<string> = writable('dashboard');
export const searchQuery: Writable<string> = writable('');
export const gamePage: Writable<number> = writable(1);

export const packages: Writable<string[]> = writable([]);

export interface GameProfile {
	package: string;
	enable_dnd?: boolean;
	mode?: string;
	cpu_governor?: string;
	target_fps?: number | number[];
	refresh_rate?: string;
}

export const activeGames: Writable<GameProfile[]> = writable([]);
export const supportedRefreshRates: Writable<number[]> = writable([]);

export interface SystemInfo {
	version: string;
	commit: string;
	arch: string;
	deviceArch: string;
	updateTime: string;
	profile: string;
	kernel: string;
	chipset: string;
	codename: string;
	sdk: string;
	battery: string;
	temp: string;
	daemonStatus: string;
	pid: string | null;
	ram: string;
}

export const systemInfo: Writable<SystemInfo> = writable({
	version: '...',
	commit: '...',
	arch: '...',
	deviceArch: '...',
	updateTime: '...',
	profile: '...',
	kernel: '...',
	chipset: '...',
	codename: '...',
	sdk: '...',
	battery: '...',
	temp: '...',
	daemonStatus: 'stopped',
	pid: null,
	ram: '-'
});

export const filteredGames: Readable<string[]> = derived(
	[packages, searchQuery, activeGames],
	([$packages, $searchQuery, $activeGames]) => {
		const query = $searchQuery.toLowerCase();
		let result = $packages.filter(pkg =>
			pkg.toLowerCase().includes(query)
		);

		result.sort((a, b) => {
			const aActive = !!$activeGames.find(g => g.package === a);
			const bActive = !!$activeGames.find(g => g.package === b);
			if (aActive && !bActive) return -1;
			if (!aActive && bActive) return 1;
			return a.localeCompare(b);
		});

		return result;
	}
);
