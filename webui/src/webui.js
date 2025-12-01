import { exec, toast } from 'kernelsu'
import { parse, stringify } from 'smol-toml'

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
            availableGovernors: []
        }
    }

    async runCommand(cmd, cwd = null) {
        try {
            if (typeof window !== 'undefined' && !window.ksu && !window.$auriya) {
                console.log(`[Mock Exec] ${cmd}`)
                // Mock responses for development
                if (cmd.includes('pidof')) return "1234"
                if (cmd.includes('module.prop')) return "1.0.0"
                if (cmd.includes('current_profile')) return "1" // Performance
                if (cmd.includes('ro.board.platform')) return "taro"
                if (cmd.includes('uname')) return "5.10.101"
                if (cmd.includes('scaling_available_governors')) return "schedutil performance powersave"
                if (cmd.includes('cat') && cmd.includes('settings.toml')) return "fas = { enabled = true, default_mode = 'performance' }\ndnd = { default_enable = false }\ncpu = { default_governor = 'schedutil' }"
                return "Mock Output"
            }

            const { errno, stdout, stderr } = await exec(cmd, cwd ? { cwd } : {})
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
        await this.loadSystemInfo()
        await this.loadSettings()
        this.setupEventListeners()
    }

    async loadSystemInfo() {
        // Module Version
        const version = await this.runCommand(`/system/bin/grep "^version=" ${modPath}/module.prop | /system/bin/cut -d= -f2`)
        document.getElementById('module-version').textContent = (typeof version === 'string' && version) ? version : "Unknown"

        // Profile
        const profileCode = await this.runCommand(`/system/bin/cat ${configPath}/current_profile`)
        const profiles = { "0": "Init", "1": "Performance", "2": "Normal", "3": "Powersave" }
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
        if (pid !== "null" && pid.length > 0) {
            document.getElementById('daemon-status').textContent = "Working ✨"
            document.getElementById('daemon-pid').textContent = `Daemon PID: ${pid}`
        } else {
            document.getElementById('daemon-status').textContent = "Stopped 💤"
            document.getElementById('daemon-pid').textContent = "Service not running"
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

        // Game Governor Select
        const gameGovSelect = document.getElementById('game-cpu-gov-select')
        gameGovSelect.innerHTML = ''
        govs.forEach(gov => {
            const opt = document.createElement('option')
            opt.value = gov
            opt.textContent = gov
            gameGovSelect.appendChild(opt)
        })

        // TOML Config
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
            // toast("Settings saved")
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
