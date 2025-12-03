import { exec, toast } from 'kernelsu'
import { parse, stringify } from 'smol-toml'
import ferrisHappy from './public/ferris_happy.svg'
import ferrisSleep from './public/ferris_sleep.svg'

const configPath = '/data/adb/.config/auriya'
const modPath = '/data/adb/modules/auriya'

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
            searchQuery: ''
        }
    }

    async runCommand(cmd, cwd = null) {
        try {
            if (typeof window !== 'undefined' && !window.ksu && !window.$auriya) {
                console.log(`[Mock Exec] ${cmd}`)
                if (cmd.includes('pidof')) return "1234"
                if (cmd.includes('module.prop')) return "1.0.0"
                if (cmd.includes('current_profile')) return "1"
                if (cmd.includes('ro.board.platform')) return "taro"
                if (cmd.includes('uname')) return "5.10.101"
                if (cmd.includes('scaling_available_governors')) return "schedutil performance powersave"
                if (cmd.includes('cat') && cmd.includes('settings.toml')) return "fas = { enabled = true, default_mode = 'performance' }\ndnd = { default_enable = false }\ncpu = { default_governor = 'schedutil' }"
                return "Mock Output"
            }

            const timeoutPromise = new Promise((_, reject) =>
                setTimeout(() => reject(new Error("Command timed out")), 5000)
            )

            const execPromise = exec(cmd, cwd ? { cwd } : {})
            const { errno, stdout, stderr } = await Promise.race([execPromise, timeoutPromise])

            if (errno !== 0) {
                console.warn(`Command failed: ${cmd}`, stderr)
                return { error: stderr || "Unknown error" }
            }
            return stdout.trim()
        } catch (e) {
            console.error("Exec error:", e)
            return { error: e.message || "Exec exception" }
        }
    }

    async init() {
        this.setupNavigation()
        this.setupEventListeners()
        try {
            await this.loadSystemInfo()
            await this.loadSettings()
            this.loadActiveGames()
        } catch (e) {
            console.error("Init data load failed", e)
        }
    }

    setupNavigation() {
        const navBtns = document.querySelectorAll('.nav-btn')
        const views = document.querySelectorAll('.view-section')

        navBtns.forEach(btn => {
            btn.addEventListener('click', () => {
                const targetId = btn.dataset.target

                navBtns.forEach(b => {
                    b.classList.remove('active', 'text-white')
                    b.classList.add('text-on-surface', 'opacity-60')
                    const icon = b.querySelector('.material-symbols-rounded');
                    if (icon) icon.classList.remove('icon-filled');
                })
                btn.classList.add('active', 'text-white')
                btn.classList.remove('text-on-surface', 'opacity-60')

                views.forEach(view => {
                    if (view.id === targetId) {
                        view.classList.remove('hidden')
                    } else {
                        view.classList.add('hidden')
                    }
                })

                if (targetId === 'view-games' && this.state.packages.length === 0) {
                    this.loadPackages()
                }
            })
        })

        const searchInput = document.getElementById('game-search')
        if (searchInput) {
            searchInput.addEventListener('input', (e) => {
                this.state.searchQuery = e.target.value.toLowerCase()
                this.renderGameList()
            })
        }
    }

    async loadPackages() {
        const listContainer = document.getElementById('game-list')
        listContainer.innerHTML = '<div class="text-center py-8 opacity-50">Loading packages...</div>'

        const socketPath = '/dev/socket/auriya.sock'
        const cmd = `echo "LIST_PACKAGES" | nc -U ${socketPath}`

        let output = await this.runCommand(cmd, null, 10000)
        if (output && (output.error || output.includes('ERR'))) {
            console.warn("IPC ListPackages failed, trying direct pm...", output)
            output = await this.runCommand('/system/bin/pm list packages', null, 10000)
        }

        if (typeof output === 'string') {
            const lines = output.split('\n')
            this.state.packages = lines
                .filter(line => line.includes('package:'))
                .map(line => line.split('package:')[1]?.trim())
                .filter(Boolean)
                .sort()

            if (this.state.packages.length === 0) {
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

            this.renderGameList()
        } else {
            console.error("Failed to load packages:", output)
            listContainer.innerHTML = `<div class="p-4 text-center text-error text-sm">
                Failed to load packages.<br>
                <span class="opacity-50 text-xs">${output?.error || "Unknown error"}</span>
            </div>`
        }
    }

    async loadActiveGames() {
        const socketPath = '/dev/socket/auriya.sock'
        const cmd = `echo "GET_GAMELIST" | nc -U ${socketPath}`

        const output = await this.runCommand(cmd)

        if (output && !output.error && !output.startsWith('ERR')) {
            try {
                this.state.activeGames = JSON.parse(output)
            } catch (e) {
                console.error("Failed to parse active games JSON", e)
                this.state.activeGames = []
            }
        } else {
            this.state.activeGames = []
        }
        this.renderGameList()
    }

    renderGameList() {
        const listContainer = document.getElementById('game-list')
        if (!listContainer) return

        const filtered = this.state.packages.filter(pkg =>
            pkg.toLowerCase().includes(this.state.searchQuery)
        )

        if (filtered.length === 0) {
            listContainer.innerHTML = '<div class="text-center py-8 opacity-50">No packages found</div>'
            return
        }

        listContainer.innerHTML = filtered.map(pkg => {
            const activeProfile = this.state.activeGames.find(g => g.package === pkg)
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

    async openGameSettings(pkg) {
        try {
            const activeProfile = this.state.activeGames.find(g => g.package === pkg)

            document.getElementById('modal-pkg-name').textContent = pkg

            const enableToggle = document.getElementById('modal-enable-toggle')
            const govSelect = document.getElementById('modal-gov-select')
            const dndToggle = document.getElementById('modal-dnd-toggle')
            const saveBtn = document.getElementById('modal-save-btn')
            const modal = document.getElementById('game-settings-modal')

            this.loadCpuGovernors(govSelect)

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

                await this.saveGameSettings(pkg, isEnabled, gov, dnd)
                modal.close()
            }

            modal.showModal()
        } catch (e) {
            console.error("openGameSettings failed", e)
            toast(`Error opening settings: ${e.message}`)
        }
    }

    async loadCpuGovernors(selectElement) {
        const defaults = ['performance', 'schedutil', 'powersave', 'interactive', 'conservative', 'ondemand', 'userspace']
        const render = (list) => {
            selectElement.innerHTML = ''
            list.forEach(gov => {
                const opt = document.createElement('option')
                opt.value = gov
                opt.textContent = gov
                selectElement.appendChild(opt)
            })
            if (this.state.gameGov && list.includes(this.state.gameGov)) {
                selectElement.value = this.state.gameGov
            }
        }

        if (this.state.availableGovernors && this.state.availableGovernors.length > 0) {
            render(this.state.availableGovernors)
        } else {
        }
        try {
            if (this.state.availableGovernors && this.state.availableGovernors.length > 0) {
                return
            }

            const paths = [
                '/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors',
                '/sys/devices/system/cpu/cpufreq/policy0/scaling_available_governors',
                '/sys/devices/system/cpu/cpu0/cpufreq/scaling_governors'
            ]

            let output = null
            for (const path of paths) {
                let res = await this.runCommand(`cat ${path}`)
                if (res && !res.error && res.length > 0) {
                    output = res
                    break
                }

                res = await this.runCommand(`busybox cat ${path}`)
                if (res && !res.error && res.length > 0) {
                    output = res
                    break
                }
            }

            if (output) {
                const realGovs = output.split(/\s+/).filter(g => g)
                if (realGovs.length > 0) {
                    this.state.availableGovernors = realGovs
                    render(realGovs)

                }
            }
        } catch (e) {
            console.warn("Failed to fetch governors, keeping defaults", e)

        }
    }

    async saveGameSettings(pkg, isEnabled, gov, dnd) {
        const socketPath = '/dev/socket/auriya.sock'


        try {
            if (isEnabled) {

                const activeProfile = this.state.activeGames.find(g => g.package === pkg)
                if (!activeProfile) {

                    const res = await this.runCommand(`echo "ADD_GAME ${pkg}" | nc -U ${socketPath}`)

                }

                const updateCmd = `UPDATE_GAME ${pkg} gov=${gov} dnd=${dnd}`

                const res = await this.runCommand(`echo "${updateCmd}" | nc -U ${socketPath}`)


                toast(`Saved settings for ${pkg}`)
            } else {

                await this.runCommand(`echo "REMOVE_GAME ${pkg}" | nc -U ${socketPath}`)
                toast(`Removed ${pkg} from optimization`)
            }


            await this.loadActiveGames()
        } catch (e) {
            console.error("Save failed", e)
            toast(`Save failed: ${e.message}`)
        }
    }

    async loadSystemInfo() {
        // Module Version
        const version = await this.runCommand(`/system/bin/grep "^version=" ${modPath}/module.prop | /system/bin/cut -d= -f2`)
        document.getElementById('module-version').textContent = (typeof version === 'string' && version) ? version : "Unknown"

        // Profile
        const profileCode = await this.runCommand(`/system/bin/cat ${configPath}/current_profile`)
        const profiles = { "0": "Init", "1": "Performance", "2": "Balance", "3": "Powersave" }
        document.getElementById('current-profile').textContent = (typeof profileCode === 'string' && profiles[profileCode]) ? profiles[profileCode] : "Unknown"

        // Kernel
        const kernel = await this.runCommand(`/system/bin/uname -r`)
        document.getElementById('kernel-version').textContent = (typeof kernel === 'string' && kernel) ? kernel : "Unknown"

        // Chipset
        const chipset = await this.runCommand(`/system/bin/getprop ro.board.platform`)
        document.getElementById('chipset-name').textContent = (typeof chipset === 'string' && chipset) ? chipset : "Unknown"

        // Codename
        const codename = await this.runCommand(`/system/bin/getprop ro.product.device`)
        document.getElementById('device-codename').textContent = (typeof codename === 'string' && codename) ? codename : "Unknown"

        // SDK
        const sdk = await this.runCommand(`/system/bin/getprop ro.build.version.sdk`)
        document.getElementById('android-sdk').textContent = (typeof sdk === 'string' && sdk) ? sdk : "Unknown"

        // Daemon Status
        const pid = await this.runCommand('/system/bin/toybox pidof auriya || echo null')
        const icon = document.getElementById('daemon-status-icon')

        if (pid !== "null" && pid.length > 0) {
            document.getElementById('daemon-status').textContent = "Working ✨"
            document.getElementById('daemon-pid').textContent = `Daemon PID: ${pid}`
            if (icon) {
                icon.src = ferrisHappy
                icon.classList.remove('opacity-0')
            }
        } else {
            document.getElementById('daemon-status').textContent = "Stopped 💤"
            document.getElementById('daemon-pid').textContent = "Service not running"
            if (icon) {
                icon.src = ferrisSleep
                icon.classList.remove('opacity-0')
            }
        }
    }

    async loadSettings() {
        // Governors
        const govOutput = await this.runCommand('/system/bin/cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors')
        const govs = (typeof govOutput === 'string' && govOutput) ? govOutput.split(/\s+/).filter(g => g) : []
        const govSelect = document.getElementById('cpu-gov-select')
        govSelect.innerHTML = ''
        govs.forEach(gov => {
            const opt = document.createElement('option')
            opt.value = gov
            opt.textContent = gov
            govSelect.appendChild(opt)
        })

        const gameGovSelect = document.getElementById('game-cpu-gov-select')
        gameGovSelect.innerHTML = ''
        govs.forEach(gov => {
            const opt = document.createElement('option')
            opt.value = gov
            opt.textContent = gov
            gameGovSelect.appendChild(opt)
        })

        const content = await this.runCommand(`/system/bin/cat ${configPath}/settings.toml`)
        if (content && !content.error) {
            try {
                const settings = parse(content)

                this.state.fasEnabled = settings.fas?.enabled ?? false
                document.getElementById('fas-switch').checked = this.state.fasEnabled
                document.getElementById('fas-mode-container').style.display = this.state.fasEnabled ? 'block' : 'none'

                this.state.fasMode = settings.fas?.default_mode ?? 'performance'
                document.getElementById('fas-mode-select').value = this.state.fasMode

                this.state.dndEnabled = settings.dnd?.default_enable ?? false
                document.getElementById('dnd-switch').checked = this.state.dndEnabled

                this.state.defaultGov = settings.cpu?.default_governor ?? 'schedutil'
                govSelect.value = this.state.defaultGov

                this.state.gameGov = settings.cpu?.game_governor ?? 'performance'
                gameGovSelect.value = this.state.gameGov
            } catch (e) {
                console.error("TOML Parse Error", e)
            }
        }
    }

    async saveSettings() {
        try {
            const content = await this.runCommand(`/system/bin/cat ${configPath}/settings.toml`)
            let settings = {}
            if (content && !content.error) {
                try { settings = parse(content) } catch (e) { }
            }

            if (!settings.fas) settings.fas = {}
            settings.fas.enabled = this.state.fasEnabled
            settings.fas.default_mode = this.state.fasMode

            if (!settings.dnd) settings.dnd = {}
            settings.dnd.default_enable = this.state.dndEnabled

            if (!settings.cpu) settings.cpu = {}
            settings.cpu.default_governor = this.state.defaultGov
            settings.cpu.game_governor = this.state.gameGov

            const newContent = stringify(settings)
            await this.runCommand(`echo '${newContent}' > ${configPath}/settings.toml`)
        } catch (e) {
            console.error("Save Error", e)
        }
    }

    setupEventListeners() {

        document.getElementById('donate-btn').addEventListener('click', () => {
            console.log('Donate btn clicked')
            window.open('https://t.me/Pavellc', '_blank')
        })
    }
}
