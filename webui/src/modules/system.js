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

    // Module Version
    const version = await runCommand(`/system/bin/grep "^version=" ${modPath}/module.prop | /system/bin/cut -d= -f2`)
    setText('module-version', (typeof version === 'string' && version) ? version : "Unknown")

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

    // Daemon Status
    const pid = await runCommand('/system/bin/toybox pidof auriya || echo null')
    const icon = document.getElementById('daemon-status-icon')

    if (pid !== "null" && pid.length > 0) {
        setText('daemon-status', "Working ✨")
        setText('daemon-pid', `Daemon PID: ${pid}`)
        if (icon) {
            icon.src = ferrisHappy
            icon.classList.remove('opacity-0')
        }
    } else {
        setText('daemon-status', "Stopped 💤")
        setText('daemon-pid', "Service not running")
        if (icon) {
            icon.src = ferrisSleep
            icon.classList.remove('opacity-0')
        }
    }
}
