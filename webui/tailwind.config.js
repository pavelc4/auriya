/** @type {import('tailwindcss').Config} */
export default {
    content: [
        "./index.html",
        "./src/**/*.{js,ts,jsx,tsx}",
    ],
    theme: {
        extend: {
            colors: {
                'surface': '#1a110d',
                'on-surface': '#f0ded8',
                'on-surface-variant': '#d8c2bc',
                'surface-container': '#251a16',
                'surface-container-high': '#302420',
                'surface-container-highest': '#3b2e2a',
                'surface-variant': '#53433f',
                'outline': '#a08c87',
            }
        },
    },
    plugins: [
        require('daisyui'),
    ],
    daisyui: {
        themes: [
            {
                auriya: {
                    "primary": "#b7410e",
                    "primary-content": "#481616ff",

                    "secondary": "#77574e",
                    "secondary-content": "#ffffff",

                    "accent": "#6b5d2e",
                    "accent-content": "#ffffff",

                    "neutral": "#1a110d",
                    "neutral-content": "#f0ded8",

                    "base-100": "#171209",
                    "base-200": "#251a16",
                    "base-300": "#3b2e2a",
                    "base-content": "#ece0d1",
                    "info": "#00add8",
                    "success": "#219138",
                    "warning": "#f7df1e",
                    "error": "#ba1a1a",
                    "--rounded-btn": "9999px",
                    "--rounded-box": "1.5rem",
                },
            },
        ],
        darkTheme: "auriya",
    },
}