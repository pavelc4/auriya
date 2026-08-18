import React, {type ReactNode} from 'react';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import {useLocation} from '@docusaurus/router';
import {translate} from '@docusaurus/Translate';
import {mergeSearchStrings} from '@docusaurus/theme-common';
import DropdownNavbarItem from '@theme/NavbarItem/DropdownNavbarItem';
import IconLanguage from '@theme/Icon/Language';
import type {LinkLikeNavbarItemProps} from '@theme/NavbarItem';
import type {Props} from '@theme/NavbarItem/LocaleDropdownNavbarItem';

function useLocaleDropdownUtils() {
  const {
    i18n: {defaultLocale, localeConfigs, locales},
  } = useDocusaurusContext();
  const location = useLocation();

  const getLocaleConfig = (locale: string) => {
    const localeConfig = localeConfigs[locale];
    if (!localeConfig) {
      throw new Error(
        `Docusaurus bug, no locale config found for locale=${locale}`,
      );
    }
    return localeConfig;
  };

  const getCanonicalPath = () => {
    let pathname = location.pathname || '/';
    // Strip any repetitive or stacked locale prefixes (e.g. /id/id/ -> /)
    let stripped = true;
    while (stripped) {
      stripped = false;
      for (const loc of locales) {
        if (loc !== defaultLocale) {
          const prefix = `/${loc}`;
          if (pathname === prefix) {
            pathname = '/';
            stripped = true;
          } else if (pathname.startsWith(`${prefix}/`)) {
            pathname = pathname.substring(prefix.length);
            stripped = true;
          }
        }
      }
    }
    return pathname.startsWith('/') ? pathname : `/${pathname}`;
  };

  const getTargetURL = (locale: string, options: {queryString: string | undefined}) => {
    const canonicalPath = getCanonicalPath();
    let targetPath = '';
    if (locale === defaultLocale) {
      targetPath = canonicalPath;
    } else {
      targetPath = canonicalPath === '/' ? `/${locale}/` : `/${locale}${canonicalPath}`;
    }

    const finalSearch = mergeSearchStrings(
      [location.search, options.queryString],
      'append',
    );
    const hash = location.hash || '';

    return `${targetPath}${finalSearch}${hash}`;
  };

  return {
    getURL: getTargetURL,
    getLabel: (locale: string) => getLocaleConfig(locale).label,
    getLang: (locale: string) => getLocaleConfig(locale).htmlLang,
  };
}

export default function LocaleDropdownNavbarItem({
  mobile,
  dropdownItemsBefore,
  dropdownItemsAfter,
  queryString,
  ...props
}: Props): ReactNode {
  const utils = useLocaleDropdownUtils();
  const location = useLocation();

  const {
    i18n: {defaultLocale, locales},
  } = useDocusaurusContext();

  // Detect currently active locale from the URL pathname
  let activeLocale = defaultLocale;
  const currentPath = location.pathname || '/';
  for (const loc of locales) {
    if (loc !== defaultLocale && (currentPath === `/${loc}` || currentPath.startsWith(`/${loc}/`))) {
      activeLocale = loc;
      break;
    }
  }

  const localeItems = locales.map((locale): LinkLikeNavbarItemProps => {
    const targetUrl = utils.getURL(locale, {queryString});
    return {
      label: utils.getLabel(locale),
      lang: utils.getLang(locale),
      to: `pathname://${targetUrl}`,
      target: '_self',
      autoAddBaseUrl: false,
      onClick: (e: React.MouseEvent) => {
        if (typeof window !== 'undefined') {
          e.preventDefault();
          window.location.assign(targetUrl);
        }
      },
      className:
        locale === activeLocale
          ? mobile
            ? 'menu__link--active'
            : 'dropdown__link--active'
          : '',
    };
  });

  const items = [...dropdownItemsBefore, ...localeItems, ...dropdownItemsAfter];

  const dropdownLabel = mobile
    ? translate({
        message: 'Languages',
        id: 'theme.navbar.mobileLanguageDropdown.label',
        description: 'The label for the mobile language switcher dropdown',
      })
    : utils.getLabel(activeLocale);

  return (
    <DropdownNavbarItem
      {...props}
      mobile={mobile}
      label={
        <>
          <IconLanguage />
          {dropdownLabel}
        </>
      }
      items={items}
    />
  );
}
