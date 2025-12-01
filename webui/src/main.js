import './style.css'
import { WebUI } from './webui.js'

document.addEventListener('DOMContentLoaded', async () => {
    // Navigation Logic
    const views = {
        'nav-dashboard': 'view-dashboard',
    }

    function switchView(navId) {
        // Update Nav Buttons
        Object.keys(views).forEach(id => {
            const btn = document.getElementById(id)
            if (id === navId) {
                btn.classList.add('active', 'text-primary')
                btn.classList.remove('text-on-surface', 'opacity-60')
            } else {
                btn.classList.remove('active', 'text-primary')
                btn.classList.add('text-on-surface', 'opacity-60')
            }
        })

        // Show/Hide Views
        const targetView = views[navId]
        Object.values(views).forEach(viewId => {
            const el = document.getElementById(viewId)
            if (viewId === targetView) {
                el.classList.remove('hidden')
                requestAnimationFrame(() => {
                    el.classList.remove('opacity-0')
                })
            } else {
                el.classList.add('opacity-0')
                setTimeout(() => {
                    if (el.classList.contains('opacity-0')) {
                        el.classList.add('hidden')
                    }
                }, 200) // Match transition duration
            }
        })
    }

    // Attach listeners immediately
    Object.keys(views).forEach(id => {
        const el = document.getElementById(id)
        if (el) {
            el.addEventListener('click', () => switchView(id))
        }
    })

    // Initialize WebUI
    window.webUI = new WebUI()
    try {
        await window.webUI.init()
    } catch (e) {
        console.error("Failed to init WebUI:", e)
    }
})
