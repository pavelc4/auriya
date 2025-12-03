import { setupTheme } from './modules/theme.js'
import { setupNavigation } from './modules/navigation.js'
import { setupEasterEgg } from './modules/easterEgg.js'
import { loadSystemInfo } from './modules/system.js'
import { loadSettings, saveSettings } from './modules/settings.js'
import { loadPackages, loadActiveGames, renderGameList, openGameSettings, saveGameSettings, setupGames } from './modules/games.js'
import { runCommand } from './modules/utils.js'

export class WebUI {
    constructor() {
        this.state = {
            fasEnabled: false,
            dndEnabled: false,
            fasMode: 'performance',
            defaultGov: '',
            gameGov: '',
            availableGovernors: [],
            packages: [],
            activeGames: [],
            searchQuery: '',
            showSystemApps: false
        }
    }

    async init() {
        setupTheme()
        setupNavigation(this)
        setupEasterEgg()
        setupGames(this)
        this.setupEventListeners()

        try {
            await loadSystemInfo()
            await loadSettings(this)
            loadActiveGames(this)
        } catch (e) {
            console.error("Init data load failed", e)
        }
    }

    setupEventListeners() {
        document.getElementById('donate-btn').addEventListener('click', () => {
            console.log('Donate btn clicked')
            window.open('https://t.me/Pavellc', '_blank')
        })
    }

    loadPackages() {
        return loadPackages(this)
    }

    renderGameList() {
        return renderGameList(this)
    }

    openGameSettings(pkg) {
        return openGameSettings(this, pkg)
    }

    saveSettings() {
        return saveSettings(this)
    }
}
