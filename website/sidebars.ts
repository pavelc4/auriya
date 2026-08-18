import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  docs: [
    {type: 'category', label: 'Getting Started', items: ['getting-started/requirements', 'getting-started/installation', 'getting-started/first-run', 'getting-started/configuration', 'getting-started/performance-tuning', 'getting-started/uninstall']},
    {type: 'category', label: 'Architecture', items: ['architecture/overview', 'architecture/components', 'architecture/data-flow', 'architecture/data-model', 'architecture/use-cases', 'architecture/module-lifecycle']},
    {type: 'category', label: 'Internals', items: ['internals/ipc-protocol', 'internals/companion', 'internals/fps-detection', 'internals/kala-research', 'internals/game-detection', 'internals/profile-scheduler', 'internals/system-tweaks']},
    {type: 'category', label: 'Reference', items: ['reference/settings', 'reference/gamelist', 'reference/commands', 'reference/stats-api', 'reference/filesystem']},
    {type: 'category', label: 'Development', items: ['development/project-structure', 'development/building', 'development/ci-cd', 'development/debugging', 'development/contributing']},
  ],
};

export default sidebars;
