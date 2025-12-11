import { setupTheme } from './modules/theme.js'
import { setupNavigation } from './modules/navigation.js'

import { loadSystemInfo } from './modules/system.js'
import { loadSettings, saveSettings, setupSettings } from './modules/settings.js'
import { loadPackages, loadActiveGames, renderGameList, openGameSettings, saveGameSettings, setupGames } from './modules/games.js'
import { runCommand, openExternalLink } from './modules/utils.js'

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
			showSystemApps: false,
			targetFps: '60',
			supportedRefreshRates: []
		}
	}

	async init() {
		setupTheme()
		setupNavigation(this)

		setupGames(this)
		setupSettings(this)
		this.setupEventListeners()

		try {
			await Promise.all([
				loadSystemInfo(),
				loadSettings(this),
				loadActiveGames(this)
			])
		} catch (e) {
			console.error("Init data load failed", e)
		}
	}

	setupEventListeners() {

		const donateBtn = document.getElementById('donate-btn')
		if (donateBtn) {
			donateBtn.addEventListener('click', (e) => {
				e.preventDefault()
				openExternalLink('https://t.me/Pavellc')
			})
		}

		const githubBtn = document.querySelector('a[href*="github.com/Pavelc4/Auriya"]')
		if (githubBtn) {
			githubBtn.addEventListener('click', (e) => {
				e.preventDefault()
				openExternalLink(githubBtn.href)
			})
		}

		const telegramBtn = document.querySelector('a[href*="t.me/pvlcply"]')
		if (telegramBtn) {
			telegramBtn.addEventListener('click', (e) => {
				e.preventDefault()
				openExternalLink(telegramBtn.href)
			})
		}

		const joinBtn = document.querySelector('a[href*="t.me/XtraManagerSoftware"]')
		if (joinBtn) {
			joinBtn.addEventListener('click', (e) => {
				e.preventDefault()
				openExternalLink(joinBtn.href)
			})
		}

		const contribLink = document.querySelector('a[href*="github.com/pavelc4/auriya/graphs/contributors"]')
		if (contribLink) {
			contribLink.addEventListener('click', (e) => {
				e.preventDefault()
				openExternalLink(contribLink.href)
			})
		}


		const creatorLink = document.querySelector('a[href="https://github.com/pavelc4"]')
		if (creatorLink) {
			creatorLink.addEventListener('click', (e) => {
				e.preventDefault()
				openExternalLink(creatorLink.href)
			})
		}
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
