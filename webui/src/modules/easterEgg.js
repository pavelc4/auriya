export function setupEasterEgg() {
    const ferrisLogo = document.getElementById('daemon-status-icon')
    let clickCount = 0
    let resetTimer

    if (ferrisLogo) {
        ferrisLogo.addEventListener('click', () => {
            clickCount++
            clearTimeout(resetTimer)

            // Reset count if no click for 2 seconds
            resetTimer = setTimeout(() => {
                clickCount = 0
            }, 2000)

            if (clickCount === 5) {
                ferrisLogo.classList.add('animate-barrel-roll')

                // Stop suddenly after 3 seconds
                setTimeout(() => {
                    ferrisLogo.classList.remove('animate-barrel-roll')
                }, 3000)

                clickCount = 0
            }
        })
    }
}
