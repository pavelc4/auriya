import ferrisHappy from '../public/ferris_happy.svg'
import ferrisSleep from '../public/ferris_sleep.svg'
import { runCommand } from './utils.js'

const configPath = '/data/adb/.config/auriya'
const modPath = '/data/adb/modules/auriya'

export async function loadSystemInfo() {
    // Module Version
    const version = await runCommand(`/system/bin/grep "^version=" ${modPath}/module.prop | /system/bin/cut -d= -f2`)
    document.getElementById('module-version').textContent = (typeof version === 'string' && version) ? version : "Unknown"

    // Profile
    const profileCode = await runCommand(`/system/bin/cat ${configPath}/current_profile`)
    const profiles = { "0": "Init", "1": "Performance", "2": "Balance", "3": "Powersave" }
    document.getElementById('current-profile').textContent = (typeof profileCode === 'string' && profiles[profileCode]) ? profiles[profileCode] : "Unknown"

    // Kernel
    const kernel = await runCommand(`/system/bin/uname -r`)
    document.getElementById('kernel-version').textContent = (typeof kernel === 'string' && kernel) ? kernel : "Unknown"

    // Chipset
    const chipset = await runCommand(`/system/bin/getprop ro.board.platform`)
    document.getElementById('chipset-name').textContent = (typeof chipset === 'string' && chipset) ? chipset : "Unknown"

    // Codename
    const codename = await runCommand(`/system/bin/getprop ro.product.device`)
    document.getElementById('device-codename').textContent = (typeof codename === 'string' && codename) ? codename : "Unknown"

    // SDK
    const sdk = await runCommand(`/system/bin/getprop ro.build.version.sdk`)
    document.getElementById('android-sdk').textContent = (typeof sdk === 'string' && sdk) ? sdk : "Unknown"

    // Daemon Status
    const pid = await runCommand('/system/bin/toybox pidof auriya || echo null')
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
