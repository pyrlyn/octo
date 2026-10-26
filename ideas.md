# Ideas

Not approved yet.

- Reuse one SSH connection per remote machine (`ControlMaster`/`ControlPersist` with a socket in
  the Octo home), so git and agent processes on it skip the handshake. The socket path must not
  contain spaces, which rules out the macOS app data folder as is.
