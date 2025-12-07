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

    // Module Version & Status
    const version = await runCommand(`/system/bin/grep "^version=" ${modPath}/module.prop | /system/bin/cut -d= -f2`)
    const versionStr = (typeof version === 'string' && version) ? version : "Unknown"
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

    // Commit Hash (Try to get from versionCode or just placeholder)
    // Often versionCode in module.prop is used for commit count or hash
    const versionCode = await runCommand(`/system/bin/grep "^versionCode=" ${modPath}/module.prop | /system/bin/cut -d= -f2`)
    setText('module-commit', (typeof versionCode === 'string' && versionCode) ? versionCode : "Unknown")

    // Arch
    const arch = await runCommand(`/system/bin/getprop ro.product.cpu.abi`)
    let archStr = "Unknown"
    if (typeof arch === 'string') {
        if (arch.includes('arm64')) archStr = 'v8a'
        else if (arch.includes('armeabi')) archStr = 'v7a'
        else if (arch.includes('x86_64')) archStr = 'x64'
        else if (arch.includes('x86')) archStr = 'x86'
        else archStr = arch
    }
    setText('module-arch', archStr)

    // Last Update Time
    const modTime = await runCommand(`/system/bin/stat -c %Y ${modPath}/module.prop`)
    if (modTime && !modTime.error && !isNaN(modTime)) {
        const now = Math.floor(Date.now() / 1000)
        const diff = now - parseInt(modTime)
        let timeStr = "Just now"

        if (diff < 60) timeStr = "Just now"
        else if (diff < 3600) timeStr = `Updated ${Math.floor(diff / 60)}m ago`
        else if (diff < 86400) timeStr = `Updated ${Math.floor(diff / 3600)}h ago`
        else timeStr = `Updated ${Math.floor(diff / 86400)}d ago`

        setText('module-update-time', timeStr)
    }

    // Profile
    const profileCode = await runCommand(`/system/bin/cat ${configPath}/current_profile`)
    const profiles = { "0": "Init", "1": "Performance", "2": "Balance", "3": "Powersave" }
    setText('current-profile', (typeof profileCode === 'string' && profiles[profileCode]) ? profiles[profileCode] : "Unknown")

    // Kernel
    const kernel = await runCommand(`/system/bin/uname -r`)
    setText('kernel-version', (typeof kernel === 'string' && kernel) ? kernel : "Unknown")

    // Chipset
    const chipset = await runCommand(`/system/bin/getprop ro.board.platform`)
    setText('chipset-name', (typeof chipset === 'string' && chipset) ? chipset : "Unknown")

    // Codename
    const codename = await runCommand(`/system/bin/getprop ro.product.device`)
    setText('device-codename', (typeof codename === 'string' && codename) ? codename : "Unknown")

    // SDK
    const sdk = await runCommand(`/system/bin/getprop ro.build.version.sdk`)
    setText('android-sdk', (typeof sdk === 'string' && sdk) ? sdk : "Unknown")

    // Root Method
    let rootMethod = "Unknown"
    const ksuCheck = await runCommand('ls /data/adb/ksu/bin/ksu || echo null')
    const apatchCheck = await runCommand('ls /data/adb/ap/bin/ap || echo null')
    const magiskCheck = await runCommand('/system/bin/magisk -v || echo null')

    if (ksuCheck !== "null" && !ksuCheck.error) {
        rootMethod = "KernelSU"
    } else if (apatchCheck !== "null" && !apatchCheck.error) {
        rootMethod = "APatch"
    } else if (magiskCheck !== "null" && !magiskCheck.error) {
        // Extract version if needed, or just say Magisk
        rootMethod = `Magisk ${magiskCheck}`
    }
    setText('root-method', rootMethod)

    // Battery
    const battery = await runCommand(`/system/bin/cat /sys/class/power_supply/battery/capacity`)
    setText('battery-level', (typeof battery === 'string' && battery) ? `${battery}%` : "Unknown")

    // Thermal
    // Try to find a valid thermal zone
    let temp = "Unknown"
    for (let i = 0; i < 10; i++) {
        const t = await runCommand(`/system/bin/cat /sys/class/thermal/thermal_zone${i}/temp`)
        if (typeof t === 'string' && !t.error && t.length > 0) {
            const val = parseInt(t)
            if (!isNaN(val) && val > 1000) {
                temp = `${Math.round(val / 1000)}°C`
                break
            }
        }
    }
    setText('thermal-temp', temp)

    // Uptime
    const uptimeStr = await runCommand(`cat /proc/uptime | awk '{print $1}'`)
    if (uptimeStr && !uptimeStr.error) {
        const uptimeSeconds = parseInt(uptimeStr)
        const hours = Math.floor(uptimeSeconds / 3600)
        const minutes = Math.floor((uptimeSeconds % 3600) / 60)
        setText('system-uptime', `${hours}h ${minutes}m`)
    } else {
        setText('system-uptime', "Unknown")
    }

    // Daemon Status
    const pid = await runCommand('/system/bin/toybox pidof auriya || echo null')
    const icon = document.getElementById('daemon-status-icon')

    if (pid !== "null" && pid.length > 0) {
        setText('daemon-status', "Working ✨")
        setText('daemon-pid', `PID: ${pid}`)

        // Daemon RAM
        const rss = await runCommand(`grep VmRSS /proc/${pid}/status | awk '{print $2}'`)
        if (rss && !rss.error) {
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
}
