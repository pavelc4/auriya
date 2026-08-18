# IPC Protocol

The daemon exposes a local Unix socket at `/dev/socket/auriya.sock`. Android and CLI clients use this boundary to send commands and receive status.

This page will define each command, request payload, response payload, timeout, and error code after the protocol handlers are fully enumerated from source.
