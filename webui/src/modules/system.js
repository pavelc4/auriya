import ferrisHappy from '../public/ferris_happy.svg'
import ferrisSleep from '../public/ferris_sleep.svg'
import { runCommand } from './utils.js'

const configPath = '/data/adb/.config/auriya'
const modPath = '/data/adb/modules/auriya'

export async function loadSystemInfo() {
    const setText = (id, text) => {
        const el = document.getElementById(id)
        if (el) el.textContent = text
    }

    const cmd = `
        grep "^version=" ${modPath}/module.prop | cut -d= -f2; echo "|||";
        grep "^versionCode=" ${modPath}/module.prop | cut -d= -f2; echo "|||";
        getprop ro.product.cpu.abi; echo "|||";
        stat -c %Y ${modPath}/module.prop
    `

    let output = await runCommand(cmd)
    if (typeof output !== 'string' || output.error) {
        console.warn("Batch loadSystemInfo failed, falling back or showing unknown", output);
        output = "";
    }

    const parts = output.split('|||').map(s => s.trim())
    const versionStr = parts[0] || "Unknown"
    setText('module-version', versionStr)

    // Status Logic
    const statusEl = document.getElementById('module-status-badge')
    if (statusEl) {
        const v = versionStr.toLowerCase()
        if (v.includes('beta') || v.includes('alpha') || v.includes('debug') || v.includes('canary')) {
            statusEl.textContent = 'BETA'
            statusEl.className = 'px-2 py-0.5 rounded-full bg-yellow-500/10 text-yellow-500 text-[10px] font-bold border border-yellow-500/20'
        } else {
            statusEl.textContent = 'STABLE'
            statusEl.className = 'px-2 py-0.5 rounded-full bg-green-500/10 text-green-500 text-[10px] font-bold border border-green-500/20'
        }
    }

    const codeStr = parts[1] || "Unknown"
    setText('module-commit', codeStr)

    const arch = parts[2]
    let archStr = "Unknown"
    if (arch) {
        if (arch.includes('arm64')) archStr = 'v8a'
        else if (arch.includes('armeabi')) archStr = 'v7a'
        else if (arch.includes('x86_64')) archStr = 'x64'
        else if (arch.includes('x86')) archStr = 'x86'
        else archStr = arch
    }
    setText('module-arch', archStr)
    setText('device-arch', archStr)

    const modTime = parts[3]
    if (modTime && !isNaN(modTime)) {
        const now = Math.floor(Date.now() / 1000)
        const diff = now - parseInt(modTime)
        let timeStr = "Just now"

        if (diff < 60) timeStr = "Just now"
        else if (diff < 3600) timeStr = `Updated ${Math.floor(diff / 60)}m ago`
        else if (diff < 86400) timeStr = `Updated ${Math.floor(diff / 3600)}h ago`
        else timeStr = `Updated ${Math.floor(diff / 86400)}d ago`

        setText('module-update-time', timeStr)
    }

    const batchCmd = `
        cat ${configPath}/current_profile; echo "|||";
        uname -r; echo "|||";
        getprop ro.board.platform; echo "|||";
        getprop ro.product.device; echo "|||";
        getprop ro.build.version.sdk; echo "|||";
        cat /sys/class/power_supply/battery/capacity; echo "|||";
        cat /sys/class/thermal/thermal_zone*/temp 2>/dev/null | head -n 5; echo "|||"; 
        PID=$(pidof auriya || echo "null"); echo $PID; echo "|||";
        if [ "$PID" != "null" ]; then grep VmRSS /proc/$PID/status | awk '{print $2}'; else echo "-"; fi
    `

    let batchOutput = await runCommand(batchCmd)
    if (typeof batchOutput !== 'string' || batchOutput.error) {
        console.warn("Mega-Batch command failed", batchOutput)
        batchOutput = ""
    }

    const bParts = batchOutput.split('|||').map(s => s.trim())


    const profileCode = bParts[0]
    const profiles = { "0": "Init", "1": "Performance", "2": "Balance", "3": "Powersave" }
    setText('current-profile', (profiles[profileCode]) ? profiles[profileCode] : "Unknown")
    setText('kernel-version', bParts[1] || "Unknown")
    setText('chipset-name', bParts[2] || "Unknown")
    setText('device-codename', bParts[3] || "Unknown")
    setText('android-sdk', bParts[4] || "Unknown")
    const batt = bParts[5]
    setText('battery-level', (batt && !isNaN(batt)) ? `${batt}%` : "Unknown")

    const temps = bParts[6] ? bParts[6].split('\n') : []
    let finalTemp = "Unknown"
    for (const t of temps) {
        const val = parseInt(t)
        if (!isNaN(val) && val > 1000) {
            finalTemp = `${Math.round(val / 1000)}°C`
            break
        }
    }
    setText('thermal-temp', finalTemp)

    const pid = bParts[7]
    const rss = bParts[8]
    const icon = document.getElementById('daemon-status-icon')

    if (pid !== "null" && pid.length > 0) {
        setText('daemon-status', "Working ✨")
        setText('daemon-pid', `PID: ${pid}`)

        if (rss && rss !== "-") {
            const mb = (parseInt(rss) / 1024).toFixed(1)
            setText('daemon-ram', `${mb} MB`)
        } else {
            setText('daemon-ram', "...")
        }

        if (icon) {
            icon.src = ferrisHappy
            icon.classList.remove('opacity-0')
        }
    } else {
        setText('daemon-status', "Stopped 💤")
        setText('daemon-pid', "Service not running")
        setText('daemon-ram', "-")
        if (icon) {
            icon.src = ferrisSleep
            icon.classList.remove('opacity-0')
        }
    }
    if (window.webui) {
        const socketPath = '/dev/socket/auriya.sock'
        const output = await runCommand(`echo "GET_SUPPORTED_RATES" | nc -U ${socketPath}`)
        if (output && !output.error && !output.startsWith('ERR')) {
            try {
                const jsonStart = output.indexOf('[')
                if (jsonStart !== -1) {
                    const cleanJson = output.substring(jsonStart)
                    const rates = JSON.parse(cleanJson)
                    if (Array.isArray(rates)) {
                        window.webui.state.supportedRefreshRates = rates
                    }
                }
            } catch (e) {
                console.warn("Failed to parse supported rates", e)
            }
        }
    }
}
