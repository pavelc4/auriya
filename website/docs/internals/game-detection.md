# Game Detection

Foreground process tracking determines whether the current package matches an entry in `gamelist.toml`. Detection state is then forwarded to the runtime scheduler. Document package-source fallbacks and debounce behavior here once verified from `pid_tracker` and daemon handlers.
