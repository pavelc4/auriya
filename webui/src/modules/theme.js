export function setupTheme() {
    const themeToggle = document.getElementById('theme-toggle')
    const savedTheme = localStorage.getItem('theme') || 'dark'

    // Apply saved theme
    if (savedTheme === 'light') {
        document.documentElement.setAttribute('data-theme', 'light')
        if (themeToggle) themeToggle.checked = false
    } else {
        document.documentElement.removeAttribute('data-theme')
        if (themeToggle) themeToggle.checked = true
    }

    // Handle toggle
    if (themeToggle) {
        themeToggle.addEventListener('change', (e) => {
            if (e.target.checked) {
                // Dark Mode
                document.documentElement.removeAttribute('data-theme')
                localStorage.setItem('theme', 'dark')
            } else {
                // Light Mode
                document.documentElement.setAttribute('data-theme', 'light')
                localStorage.setItem('theme', 'light')
            }
        })
    }
}
