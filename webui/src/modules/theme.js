export function setupTheme() {
    const themeBtn = document.getElementById('theme-btn')
    const savedTheme = localStorage.getItem('theme') || 'dark'
    const icon = themeBtn ? themeBtn.querySelector('.material-symbols-rounded') : null

    const updateIcon = (isDark) => {
        if (icon) {
            icon.textContent = isDark ? 'light_mode' : 'dark_mode'
        }
    }

    // Apply saved theme
    if (savedTheme === 'light') {
        document.documentElement.setAttribute('data-theme', 'light')
        updateIcon(false)
    } else {
        document.documentElement.removeAttribute('data-theme')
        updateIcon(true)
    }

    // Handle toggle
    if (themeBtn) {
        themeBtn.addEventListener('click', () => {
            const currentTheme = document.documentElement.getAttribute('data-theme')
            if (currentTheme === 'light') {
                // Switch to Dark
                document.documentElement.removeAttribute('data-theme')
                localStorage.setItem('theme', 'dark')
                updateIcon(true)
            } else {
                // Switch to Light
                document.documentElement.setAttribute('data-theme', 'light')
                localStorage.setItem('theme', 'light')
                updateIcon(false)
            }
        })
    }
}
