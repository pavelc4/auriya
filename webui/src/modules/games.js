import { toast } from 'kernelsu'
import { runCommand } from './utils.js'
import { loadCpuGovernors } from './settings.js'

export function setupGames(webui) {
    webui.state.showSystemApps = false

    const filterBtn = document.getElementById('filter-btn')
    if (filterBtn) {
        filterBtn.onclick = () => {
            webui.state.showSystemApps = !webui.state.showSystemApps
            loadPackages(webui)
        }
    }
}

export async function loadPackages(webui) {
    const listContainer = document.getElementById('game-list')
    listContainer.innerHTML = '<div class="text-center py-8 opacity-50 flex flex-col items-center"><span class="loading loading-spinner loading-lg text-secondary mb-4"></span><p>Loading packages...</p></div>'

    const filterBtn = document.getElementById('filter-btn')
    if (filterBtn) {
        const icon = filterBtn.querySelector('span')
        if (webui.state.showSystemApps) {
            filterBtn.classList.add('bg-surface-variant', 'text-on-surface-variant')
            filterBtn.classList.remove('bg-surface-container-highest', 'text-on-surface-variant')
            if (icon) icon.textContent = 'filter_alt_off'
        } else {
            filterBtn.classList.remove('bg-surface-variant', 'text-on-surface-variant')
            filterBtn.classList.add('bg-surface-container-highest', 'text-on-surface-variant')
            if (icon) icon.textContent = 'filter_list'
        }
    }

    const cmdStr = webui.state.showSystemApps ? '/system/bin/pm list packages' : '/system/bin/pm list packages -3'
    let output = await runCommand(cmdStr, null, 10000)

    if (!output || output.error) {
        console.warn("pm list packages failed, trying socket...", output)
        const socketPath = '/dev/socket/auriya.sock'
        const cmd = `echo "LIST_PACKAGES" | nc -U ${socketPath}`
        output = await runCommand(cmd, null, 10000)
    }

    if (typeof output === 'string') {
        const lines = output.split('\n')
        webui.state.packages = lines
            .filter(line => line.includes('package:'))
            .map(line => line.split('package:')[1]?.trim())
            .filter(Boolean)
            .sort()

        if (webui.state.packages.length === 0) {
            listContainer.innerHTML = `<div class="p-4 text-center text-error text-sm">
                No packages found.<br>
                <button class="btn btn-xs btn-white mt-2" onclick="window.webui.loadPackages()">Retry</button>
            </div>`
            return
        }

        renderGameList(webui)
    } else {
        console.error("Failed to load packages:", output)
        listContainer.innerHTML = `<div class="p-4 text-center text-error text-sm">
            Failed to load packages.<br>
            <span class="opacity-50 text-xs">${output?.error || "Unknown error"}</span>
        </div>`
    }
}

export async function loadActiveGames(webui) {
    const socketPath = '/dev/socket/auriya.sock'
    const cmd = `echo "GET_GAMELIST" | nc -U ${socketPath}`

    const output = await runCommand(cmd)

    if (output && !output.error && !output.startsWith('ERR')) {
        try {
            let jsonStr = output
            const jsonStartIndex = jsonStr.indexOf('[')
            if (jsonStartIndex !== -1) {
                jsonStr = jsonStr.substring(jsonStartIndex)
            }
            webui.state.activeGames = JSON.parse(jsonStr)
        } catch (e) {
            console.error("Failed to parse active games JSON", e, output)
            webui.state.activeGames = []
        }
    } else {
        webui.state.activeGames = []
    }
    renderGameList(webui)
}

export function renderGameList(webui) {
    const listContainer = document.getElementById('game-list')
    if (!listContainer) return

    let filtered = webui.state.packages.filter(pkg =>
        pkg.toLowerCase().includes(webui.state.searchQuery)
    )

    if (filtered.length === 0) {
        listContainer.innerHTML = '<div class="text-center py-8 opacity-50">No packages found</div>'
        return
    }

    // Sort: Active games first
    filtered.sort((a, b) => {
        const aActive = !!webui.state.activeGames.find(g => g.package === a)
        const bActive = !!webui.state.activeGames.find(g => g.package === b)
        if (aActive && !bActive) return -1
        if (!aActive && bActive) return 1
        return a.localeCompare(b)
    })

    listContainer.innerHTML = filtered.map(pkg => {
        const activeProfile = webui.state.activeGames.find(g => g.package === pkg)
        const isEnabled = !!activeProfile

        const cardClass = isEnabled
            ? 'bg-surface-variant/20 border-surface-variant/50 shadow-lg'
            : 'bg-surface-container-highest/30 border-outline/5 hover:bg-surface-container-highest/50'

        const iconClass = isEnabled
            ? 'bg-surface-variant text-on-surface-variant shadow-md'
            : 'bg-surface-container text-on-surface-variant/70'

        const iconName = isEnabled ? 'sports_esports' : 'android'
        const statusText = isEnabled ? 'Optimized' : 'Tap to optimize'
        const statusColor = isEnabled ? 'text-on-surface-variant' : 'text-on-surface-variant opacity-60'

        return `
        <div class="relative group overflow-hidden p-4 mb-3 rounded-[24px] border transition-all duration-300 cursor-pointer ${cardClass}"
            onclick="window.webui.openGameSettings('${pkg}')">
            
            <div class="flex items-center gap-4 relative z-10">
                <div class="w-12 h-12 rounded-2xl ${iconClass} flex items-center justify-center shrink-0 transition-transform duration-300 group-hover:scale-110">
                    <span class="material-symbols-rounded text-[24px]">${iconName}</span>
                </div>
                
                <div class="min-w-0 flex-grow">
                    <div class="flex items-center gap-2 mb-0.5">
                        <p class="text-base font-semibold truncate text-on-surface">${pkg}</p>
                        ${isEnabled ? `<span class="border border-[#dea584]/30 bg-[#dea584]/10 text-[#dea584] rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider shadow-sm">Active</span>` : ''}
                    </div>
                    <div class="flex items-center gap-2 text-xs">
                        <span class="${statusColor} font-medium">${statusText}</span>
                        ${isEnabled ? `<span class="w-1 h-1 rounded-full bg-on-surface/20"></span> <span class="opacity-60">${activeProfile.cpu_governor}</span>` : ''}
                    </div>
                </div>

                <div class="w-10 h-10 rounded-full bg-surface-variant/20 flex items-center justify-center text-on-surface opacity-0 group-hover:opacity-100 transition-all duration-300 transform translate-x-4 group-hover:translate-x-0">
                    <span class="material-symbols-rounded">edit</span>
                </div>
            </div>
        </div>
    `
    }).join('')
}

export async function openGameSettings(webui, pkg) {
    try {
        const activeProfile = webui.state.activeGames.find(g => g.package === pkg)

        document.getElementById('modal-pkg-name').textContent = pkg

        const enableToggle = document.getElementById('modal-enable-toggle')
        const govSelect = document.getElementById('modal-gov-select')
        const dndToggle = document.getElementById('modal-dnd-toggle')
        const saveBtn = document.getElementById('modal-save-btn')
        const modal = document.getElementById('game-settings-modal')

        loadCpuGovernors(webui, govSelect)

        if (activeProfile) {
            enableToggle.checked = true
            govSelect.value = activeProfile.cpu_governor || 'performance'
            dndToggle.checked = activeProfile.enable_dnd || false
            govSelect.disabled = false
            dndToggle.disabled = false
        } else {
            enableToggle.checked = false
            govSelect.value = 'performance'
            dndToggle.checked = false
            govSelect.disabled = true
            dndToggle.disabled = true
        }

        enableToggle.onchange = (e) => {
            govSelect.disabled = !e.target.checked
            dndToggle.disabled = !e.target.checked
        }

        saveBtn.onclick = async () => {
            const isEnabled = enableToggle.checked
            const gov = govSelect.value
            const dnd = dndToggle.checked

            await saveGameSettings(webui, pkg, isEnabled, gov, dnd)
            modal.close()
        }

        modal.showModal()
    } catch (e) {
        console.error("openGameSettings failed", e)
        toast(`Error opening settings: ${e.message}`)
    }
}

export async function saveGameSettings(webui, pkg, isEnabled, gov, dnd) {
    const socketPath = '/dev/socket/auriya.sock'

    try {
        if (isEnabled) {

            const activeProfile = webui.state.activeGames.find(g => g.package === pkg)
            if (!activeProfile) {

                const res = await runCommand(`echo "ADD_GAME ${pkg}" | nc -U ${socketPath}`)

            }

            const updateCmd = `UPDATE_GAME ${pkg} gov=${gov} dnd=${dnd}`

            const res = await runCommand(`echo "${updateCmd}" | nc -U ${socketPath}`)


            toast(`Saved settings for ${pkg}`)
        } else {

            await runCommand(`echo "REMOVE_GAME ${pkg}" | nc -U ${socketPath}`)
            toast(`Removed ${pkg} from optimization`)
        }


        // Small delay to ensure daemon processes the update
        await new Promise(r => setTimeout(r, 500))
        await loadActiveGames(webui)
    } catch (e) {
        console.error("Save failed", e)
        toast(`Save failed: ${e.message}`)
    }
}
