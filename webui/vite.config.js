import { defineConfig } from 'vite'
import tailwindcss from '@tailwindcss/vite'
import { viteSingleFile } from "vite-plugin-singlefile"

// https://vite.dev/config/
export default defineConfig({
    plugins: [
        tailwindcss(),
        viteSingleFile(),
    ],
    build: {
        target: 'esnext',
        assetsInlineLimit: 100000000, // Inline everything
        chunkSizeWarningLimit: 100000000,
        cssCodeSplit: false,
        brotliSize: false,
        rollupOptions: {
            inlineDynamicImports: true,
        },
    }
})
