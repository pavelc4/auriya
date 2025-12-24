import { writable, derived } from 'svelte/store';

export const view = writable('dashboard');
export const searchQuery = writable('');
export const gamePage = writable(1);

export const packages = writable([]);
export const activeGames = writable([]);
export const supportedRefreshRates = writable([]);

export const systemInfo = writable({
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

export const filteredGames = derived(
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
