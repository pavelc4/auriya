/** @type {import('tailwindcss').Config} */
import daisyui from 'daisyui'

export default {
    content: [
        "./index.html",
        "./src/**/*.{js,ts,jsx,tsx}",
    ],
    theme: {
        extend: {
            colors: {
                'surface': '#120d0a',
                'on-surface': '#ede0db',
                'on-surface-variant': '#d7c1be',
                'surface-container': '#1f1612',
                'surface-container-high': '#2a201a',
                'surface-container-highest': '#3b2e2a',
                'surface-variant': '#5e4d48',
                'outline': '#a08c87',
            }
        },
    },
    plugins: [
        daisyui,
    ],
    daisyui: {
        themes: [
            {
                auriya: {
                    "primary": "#ff914d",
                    "primary-content": "#481a00",

                    "secondary": "#ffcf40",
                    "secondary-content": "#412d00",

                    "accent": "#eec193",
                    "accent-content": "#462a0b",

                    "neutral": "#120d0a",
                    "neutral-content": "#ede0db",

                    "base-100": "#120d0a",
                    "base-200": "#1f1612",
                    "base-300": "#2a201a",
                    "base-content": "#ede0db",

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