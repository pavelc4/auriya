import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

const config: Config = {
  title: 'Auriya Wiki',
  tagline: 'Technical documentation for Auriya',
  url: 'https://auriya-wiki.pages.dev',
  baseUrl: '/',
  organizationName: 'pavelc4',
  projectName: 'auriya',
  onBrokenLinks: 'throw',
  future: {v4: true},
  i18n: {defaultLocale: 'en', locales: ['en']},
  presets: [
    [
      'classic',
      {
        docs: {
          routeBasePath: '/',
          sidebarPath: './sidebars.ts',
          editUrl: 'https://github.com/pavelc4/auriya/edit/main/website/',
          showLastUpdateAuthor: true,
          showLastUpdateTime: true,
        },
        blog: false,
        theme: {customCss: './src/css/custom.css'},
      } satisfies Preset.Options,
    ],
  ],
  themeConfig: {
    colorMode: {respectPrefersColorScheme: true},
    navbar: {
      title: 'Auriya Wiki',
      hideOnScroll: true,
      items: [
        {type: 'docSidebar', sidebarId: 'docs', label: 'Documentation', position: 'left'},
        {to: '/architecture/overview', label: 'Architecture', position: 'left'},
        {to: '/internals/fps-detection', label: 'Internals', position: 'left'},
        {
          href: 'https://github.com/pavelc4/auriya',
          position: 'right',
          className: 'header-github-link',
          'aria-label': 'Auriya GitHub repository',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {title: 'Documentation', items: [{label: 'Getting started', to: '/getting-started/installation'}, {label: 'Architecture', to: '/architecture/overview'}]},
        {title: 'Project', items: [{label: 'Source code', href: 'https://github.com/pavelc4/auriya'}, {label: 'Issue tracker', href: 'https://github.com/pavelc4/auriya/issues'}]},
      ],
      copyright: `Copyright © ${new Date().getFullYear()} Auriya contributors.`,
    },
    prism: {theme: prismThemes.github, darkTheme: prismThemes.dracula},
  } satisfies Preset.ThemeConfig,
};

export default config;
