import { parse, stringify } from 'smol-toml'
import { runCommand } from './utils.js'

const configPath = '/data/adb/.config/auriya'

export function setupSettings(webui) {
    const exportBtn = document.getElementById('export-logs-btn')
    if (exportBtn) {
        exportBtn.onclick = () => exportLogs()
    }

    const debugToggle = document.getElementById('debug-mode-toggle')
    if (debugToggle) {
        debugToggle.onchange = async (e) => {
            const cmd = e.target.checked ? 'SETLOG DEBUG' : 'SETLOG INFO'
            await runCommand(`echo "${cmd}" | nc -U /dev/socket/auriya.sock`)
        }
    }
}

export async function loadSettings(webui) {
    // Governors
    const govOutput = await runCommand('/system/bin/cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors')
    const govs = (typeof govOutput === 'string' && govOutput) ? govOutput.split(/\s+/).filter(g => g) : []
    const govSelect = document.getElementById('cpu-gov-select')
    if (govSelect) {
        govSelect.innerHTML = ''
        govs.forEach(gov => {
            const opt = document.createElement('option')
            opt.value = gov
            opt.textContent = gov
            govSelect.appendChild(opt)
        })

        govSelect.onchange = async (e) => {
            const gov = e.target.value
            webui.state.defaultGov = gov
            await setGlobalGovernor(gov)
            await saveSettings(webui)
        }
    }

    const gameGovSelect = document.getElementById('game-cpu-gov-select')
    if (gameGovSelect) {
        gameGovSelect.innerHTML = ''
        govs.forEach(gov => {
            const opt = document.createElement('option')
            opt.value = gov
            opt.textContent = gov
            gameGovSelect.appendChild(opt)
        })
    }

    const content = await runCommand(`/system/bin/cat ${configPath}/settings.toml`)
    if (content && !content.error) {
        try {
            const settings = parse(content)

            webui.state.fasEnabled = settings.fas?.enabled ?? false
            const fasSwitch = document.getElementById('fas-switch')
            if (fasSwitch) fasSwitch.checked = webui.state.fasEnabled

            const fasModeContainer = document.getElementById('fas-mode-container')
            if (fasModeContainer) fasModeContainer.style.display = webui.state.fasEnabled ? 'block' : 'none'

            webui.state.fasMode = settings.fas?.default_mode ?? 'performance'
            const fasModeSelect = document.getElementById('fas-mode-select')
            if (fasModeSelect) fasModeSelect.value = webui.state.fasMode

            webui.state.dndEnabled = settings.dnd?.default_enable ?? false
            const dndSwitch = document.getElementById('dnd-switch')
            if (dndSwitch) dndSwitch.checked = webui.state.dndEnabled

            webui.state.defaultGov = settings.cpu?.default_governor ?? 'schedutil'
            if (govSelect) govSelect.value = webui.state.defaultGov

            webui.state.gameGov = settings.cpu?.game_governor ?? 'performance'
            if (gameGovSelect) gameGovSelect.value = webui.state.gameGov

            webui.state.targetFps = settings.fas?.target_fps ?? 60
        } catch (e) {
            console.error("TOML Parse Error", e)
        }
    }
    const fpsSelect = document.getElementById('fps-select')
    if (fpsSelect) {
        fpsSelect.value = webui.state.targetFps

        try {
            const fpsRes = await runCommand(`echo "GET_FPS" | nc -U /dev/socket/auriya.sock`)
            if (fpsRes && fpsRes.startsWith("FPS=")) {
                const fps = fpsRes.split('=')[1].trim()
                fpsSelect.value = fps
                webui.state.targetFps = fps
            }
        } catch (e) {
            console.warn("Failed to get FPS", e)
        }

        fpsSelect.onchange = async (e) => {
            const newFps = e.target.value
            webui.state.targetFps = newFps
            // await runCommand(`echo "SET_FPS ${newFps}" | nc -U /dev/socket/auriya.sock`)
            await saveSettings(webui)
        }
    }

    // Load Debug Mode status
    try {
        const statusRes = await runCommand(`echo "STATUS" | nc -U /dev/socket/auriya.sock`)
        if (statusRes && statusRes.includes("LOG_LEVEL=")) {
            const match = statusRes.match(/LOG_LEVEL=(\w+)/)
            if (match && match[1]) {
                const level = match[1].toLowerCase()
                const debugToggle = document.getElementById('debug-mode-toggle')
                if (debugToggle) {
                    debugToggle.checked = (level === 'debug')
                }
            }
        }
    } catch (e) {
        console.warn("Failed to get status for debug mode", e)
    }

    // Load Supported Refresh Rates
    try {
        const ratesRes = await runCommand(`echo "GET_SUPPORTED_RATES" | nc -U /dev/socket/auriya.sock`)
        if (ratesRes && !ratesRes.error && !ratesRes.startsWith('ERR')) {
            try {
                // Find the start of the JSON array
                const jsonStart = ratesRes.indexOf('[')
                if (jsonStart !== -1) {
                    const cleanJson = ratesRes.substring(jsonStart)
                    const rates = JSON.parse(cleanJson)
                    if (Array.isArray(rates)) {
                        webui.state.supportedRefreshRates = rates
                    }
                } else {
                    console.warn("No JSON array found in response:", ratesRes)
                }
            } catch (e) {
                console.warn("Failed to parse refresh rates. Raw:", ratesRes, "Error:", e)
            }
        }
    } catch (e) {
        console.warn("Failed to get supported rates", e)
    }
}

export async function saveSettings(webui) {
    try {
        const content = await runCommand(`/system/bin/cat ${configPath}/settings.toml`)
        let settings = {}
        if (content && !content.error) {
            try { settings = parse(content) } catch (e) { }
        }

        if (!settings.fas) settings.fas = {}
        settings.fas.enabled = webui.state.fasEnabled
        settings.fas.default_mode = webui.state.fasMode
        settings.fas.target_fps = parseInt(webui.state.targetFps) || 60

        if (!settings.dnd) settings.dnd = {}
        settings.dnd.default_enable = webui.state.dndEnabled

        if (!settings.cpu) settings.cpu = {}
        settings.cpu.default_governor = webui.state.defaultGov
        settings.cpu.game_governor = webui.state.gameGov

        const newContent = stringify(settings)
        await runCommand(`echo '${newContent}' > ${configPath}/settings.toml`)
    } catch (e) {
        console.error("Save Error", e)
    }
}

export async function setGlobalGovernor(gov) {
    try {
        // Use a loop to correctly handle wildcard expansion for multiple CPUS
        await runCommand(`/system/bin/sh -c 'for path in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo ${gov} > "$path"; done'`)

        // Also update standard linux path just in case (policy*)
        await runCommand(`/system/bin/sh -c 'for path in /sys/devices/system/cpu/cpufreq/policy*/scaling_governor; do echo ${gov} > "$path"; done'`)

        import('../webuix.js').then(wx => wx.showToast(`Governor set to ${gov}`))
    } catch (e) {
        console.error("Failed to set governor", e)
    }
}

export async function loadCpuGovernors(webui, selectElement) {
    const defaults = ['performance', 'schedutil', 'powersave', 'interactive', 'conservative', 'ondemand', 'userspace']
    const render = (list) => {
        selectElement.innerHTML = ''
        list.forEach(gov => {
            const opt = document.createElement('option')
            opt.value = gov
            opt.textContent = gov
            selectElement.appendChild(opt)
        })
        if (webui.state.gameGov && list.includes(webui.state.gameGov)) {
            selectElement.value = webui.state.gameGov
        }
    }

    if (webui.state.availableGovernors && webui.state.availableGovernors.length > 0) {
        render(webui.state.availableGovernors)
    } else {
    }
    try {
        if (webui.state.availableGovernors && webui.state.availableGovernors.length > 0) {
            return
        }

        const paths = [
            '/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors',
            '/sys/devices/system/cpu/cpufreq/policy0/scaling_available_governors',
            '/sys/devices/system/cpu/cpu0/cpufreq/scaling_governors'
        ]

        let output = null
        for (const path of paths) {
            let res = await runCommand(`cat ${path}`)
            if (res && !res.error && res.length > 0) {
                output = res
                break
            }

            res = await runCommand(`busybox cat ${path}`)
            if (res && !res.error && res.length > 0) {
                output = res
                break
            }
        }

        if (output) {
            const realGovs = output.split(/\s+/).filter(g => g)
            if (realGovs.length > 0) {
                webui.state.availableGovernors = realGovs
                render(realGovs)

            }
        }
    } catch (e) {
        console.warn("Failed to fetch governors, keeping defaults", e)
    }
}

export async function exportLogs() {
    const logDir = '/sdcard/Download/AuriyaLogs'
    const daemonLog = '/data/adb/auriya/daemon.log'

    try {
        await runCommand(`mkdir -p ${logDir}`)
        await runCommand(`cp ${daemonLog} ${logDir}/auriya.log`)
        await runCommand(`dmesg > ${logDir}/kernel.log`)

        // Try to zip if possible
        const zipRes = await runCommand(`tar -czf /sdcard/Download/AuriyaLogs.tar.gz -C /sdcard/Download AuriyaLogs`)

        if (zipRes && !zipRes.error) {
            import('../webuix.js').then(wx => wx.showToast(`Logs exported to /sdcard/Download/AuriyaLogs.tar.gz`))
        } else {
            import('../webuix.js').then(wx => wx.showToast(`Logs exported to ${logDir}`))
        }
    } catch (e) {
        console.error("Log export failed", e)
        import('../webuix.js').then(wx => wx.showToast(`Export failed: ${e.message}`))
    }
}
