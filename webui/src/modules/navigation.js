export function setupNavigation(webui) {
    const navBtns = document.querySelectorAll('.nav-btn')
    const sections = document.querySelectorAll('.view-section')

    navBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            // Remove active state from all buttons
            navBtns.forEach(b => {
                b.classList.remove('active')
                b.classList.add('opacity-60')
                const icon = b.querySelector('.material-symbols-rounded');
                if (icon) icon.classList.remove('icon-filled');
            })

            // Add active state to clicked button
            btn.classList.add('active')
            btn.classList.remove('opacity-60')
            const icon = btn.querySelector('.material-symbols-rounded');
            if (icon) icon.classList.add('icon-filled');

            // Hide all sections
            sections.forEach(s => s.classList.add('hidden'))

            // Show target section
            const targetId = btn.dataset.target
            document.getElementById(targetId).classList.remove('hidden')

            if (targetId === 'view-games' && webui.state.packages.length === 0) {
                webui.loadPackages()
            }
        })
    })

    const debounce = (func, wait) => {
        let timeout
        return function executedFunction(...args) {
            const later = () => {
                clearTimeout(timeout)
                func(...args)
            }
            clearTimeout(timeout)
            timeout = setTimeout(later, wait)
        }
    }

    const searchInput = document.getElementById('game-search')
    if (searchInput) {
        searchInput.addEventListener('input', debounce((e) => {
            webui.state.searchQuery = e.target.value.toLowerCase()
            webui.renderGameList()
        }, 300))
    }
}
