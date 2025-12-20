import { register, init, getLocaleFromNavigator, locale, waitLocale } from 'svelte-i18n';
import { derived } from 'svelte/store';

import en from './locales/en.json';
import id from './locales/id.json';

register('en', () => Promise.resolve(en));
register('id', () => Promise.resolve(id));

const savedLocale = typeof localStorage !== 'undefined'
    ? localStorage.getItem('locale')
    : null;

const initialLocale = savedLocale || getLocaleFromNavigator()?.split('-')[0] || 'en';

init({
    fallbackLocale: 'en',
    initialLocale,
});

export const localeReady = waitLocale(initialLocale);

export const languages = [
    { code: 'en', name: 'English' },
    { code: 'id', name: 'Indonesia' },
];

export function setLocale(loc) {
    locale.set(loc);
    if (typeof localStorage !== 'undefined') {
        localStorage.setItem('locale', loc);
    }
}


export { locale };
