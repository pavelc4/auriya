# Configuration

Auriya separates global behavior from application matching:

- `settings.toml` controls daemon and scheduler behavior.
- `gamelist.toml` identifies applications that should use managed profiles.

Edit only documented keys. Invalid or unsupported values may be rejected or replaced by defaults depending on the parser.
