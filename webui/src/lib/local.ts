import { register, init, getLocaleFromNavigator, locale, waitLocale } from 'svelte-i18n';
import type { Readable } from 'svelte/store';

import en from './locales/en.json';
import id from './locales/id.json';

register('en', () => Promise.resolve(en));
register('id', () => Promise.resolve(id));

const savedLocale = typeof localStorage !== 'undefined'
	? localStorage.getItem('locale')
	: null;

const initialLocale: string = savedLocale || getLocaleFromNavigator()?.split('-')[0] || 'en';

init({
	fallbackLocale: 'en',
	initialLocale,
});

export const localeReady: Promise<void> = waitLocale(initialLocale);

export const languages: { code: string; name: string }[] = [
	{ code: 'en', name: 'English' },
	{ code: 'id', name: 'Indonesia' },
];

export function setLocale(loc: string): void {
	locale.set(loc);
	if (typeof localStorage !== 'undefined') {
		localStorage.setItem('locale', loc);
	}
}

export { locale };
