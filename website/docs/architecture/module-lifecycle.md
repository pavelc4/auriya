# Module Lifecycle

1. The root manager installs module files.
2. Boot scripts start the required Android components and Rust daemon.
3. The daemon loads configuration and initializes system observers.
4. Runtime detection selects the active behavior.
5. Shutdown or uninstall stops packages and services before module files are removed.

Exact script names and ordering will be documented from the files under `module/`.
