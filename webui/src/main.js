import './style.css'
import { WebUI } from './webui.js'

document.addEventListener('DOMContentLoaded', async () => {
    // Initialize WebUI
    const webui = new WebUI()
    window.webui = webui // Expose for inline handlers

    try {
        await webui.init()
    } catch (e) {
        console.error("Failed to init WebUI:", e)
    }
})
