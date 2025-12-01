import './style.css'
import { WebUI } from './webui.js'

document.addEventListener('DOMContentLoaded', async () => {
    const webui = new WebUI()
    window.webui = webui
    try {
        await webui.init()
    } catch (e) {
        console.error("Failed to init WebUI:", e)
    }
})
