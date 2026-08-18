import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  docs: [
    'intro',
    {type: 'category', label: 'Getting Started', items: ['getting-started/requirements', 'getting-started/installation', 'getting-started/first-run', 'getting-started/configuration', 'getting-started/uninstall']},
    {type: 'category', label: 'Architecture', items: ['architecture/overview', 'architecture/components', 'architecture/data-flow', 'architecture/module-lifecycle']},
    {type: 'category', label: 'Internals', items: ['internals/ipc-protocol', 'internals/fps-detection', 'internals/game-detection', 'internals/profile-scheduler', 'internals/system-tweaks']},
    {type: 'category', label: 'Reference', items: ['reference/settings', 'reference/gamelist', 'reference/commands', 'reference/filesystem']},
    {type: 'category', label: 'Development', items: ['development/project-structure', 'development/building', 'development/debugging', 'development/contributing']},
  ],
};

export default sidebars;
