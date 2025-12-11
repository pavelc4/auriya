import { defineConfig } from 'vite'
import { svelte } from '@sveltejs/vite-plugin-svelte'
import { viteSingleFile } from 'vite-plugin-singlefile'
import Icons from 'unplugin-icons/vite'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [
    svelte(),
    tailwindcss(),
    Icons({
      compiler: 'svelte',
      autoInstall: true
    }),
    viteSingleFile()
  ],
})
