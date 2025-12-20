import { mount } from 'svelte'
import { localeReady } from './lib/local'
import './app.css'
import App from './App.svelte'

localeReady.then(() => {
  mount(App, {
    target: document.getElementById('app'),
  })
})
