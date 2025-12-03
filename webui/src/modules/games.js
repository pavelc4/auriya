import { toast } from 'kernelsu'
import { runCommand } from './utils.js'
import { loadCpuGovernors } from './settings.js'

export async function loadPackages(webui) {
    const listContainer = document.getElementById('game-list')
    listContainer.innerHTML = '<div class="text-center py-8 opacity-50">Loading packages...</div>'

    const socketPath = '/dev/socket/auriya.sock'
    const cmd = `echo "LIST_PACKAGES" | nc -U ${socketPath}`

    let output = await runCommand(cmd, null, 10000)
    if (output && (output.error || output.includes('ERR'))) {
        console.warn("IPC ListPackages failed, trying direct pm...", output)
        output = await runCommand('/system/bin/pm list packages', null, 10000)
    }

    if (typeof output === 'string') {
        const lines = output.split('\n')
        webui.state.packages = lines
            .filter(line => line.includes('package:'))
            .map(line => line.split('package:')[1]?.trim())
            .filter(Boolean)
            .sort()

        if (webui.state.packages.length === 0) {
            console.warn("No packages parsed. Raw output length:", output.length)
            listContainer.innerHTML = `<div class="p-4 text-center text-error text-sm">
                No packages found.<br>
                <span class="opacity-50 text-xs">Raw output length: ${output.length}</span>
                <br>
                <button class="btn btn-xs btn-outline mt-2" onclick="alert('Raw Output:\\n' + '${output.replace(/\n/g, '\\n').substring(0, 500)}...')">Show Raw Output</button>
                <button class="btn btn-xs btn-white mt-2 ml-2" onclick="window.webui.loadPackages()">Retry</button>
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
            webui.state.activeGames = JSON.parse(output)
        } catch (e) {
            console.error("Failed to parse active games JSON", e)
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

    const filtered = webui.state.packages.filter(pkg =>
        pkg.toLowerCase().includes(webui.state.searchQuery)
    )

    if (filtered.length === 0) {
        listContainer.innerHTML = '<div class="text-center py-8 opacity-50">No packages found</div>'
        return
    }

    listContainer.innerHTML = filtered.map(pkg => {
        const activeProfile = webui.state.activeGames.find(g => g.package === pkg)
        const isEnabled = !!activeProfile
        return `
        <div class="flex items-center justify-between p-4 mb-2 bg-surface-container-highest/50 rounded-2xl border border-outline/5 hover:bg-surface-container-highest transition-colors cursor-pointer"
            onclick="window.webui.openGameSettings('${pkg}')">
            <div class="flex items-center gap-4 overflow-hidden">
                <div class="p-2.5 rounded-xl ${isEnabled ? 'bg-primary/20 text-primary' : 'bg-surface-container-highest text-on-surface-variant'} shrink-0 transition-colors flex items-center justify-center">
                    <span class="material-symbols-rounded">android</span>
                </div>
                <div class="min-w-0">
                    <p class="text-sm font-medium truncate text-on-surface">${pkg}</p>
                    <p class="text-xs ${isEnabled ? 'text-primary' : 'text-on-surface-variant'} opacity-80 truncate">
                        ${isEnabled ? `Active • ${activeProfile.cpu_governor}` : 'Not Optimized'}
                    </p>
                </div>
            </div>
            <div class="text-on-surface-variant opacity-50 flex items-center">
                <span class="material-symbols-rounded">chevron_right</span>
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


        await loadActiveGames(webui)
    } catch (e) {
        console.error("Save failed", e)
        toast(`Save failed: ${e.message}`)
    }
}
