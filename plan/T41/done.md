# T41. Done

PR: PR_URL

## What was done

- **`attachments` feature flag.** Off by default. It shows the attach controls and lets the
  remote-access API take files; the core accepts files either way.
- **Model and limits.** `FileUpload` (name, bytes) comes in; `Attachment` (name, path on the
  task's machine, MIME type, size, removed) rides on `QueuedPrompt` and the user's
  `TranscriptEntry.Text`, so it survives a restart. `Attachments` holds the limits (10 files, 20 MB
  each, images up to 5 MB natively), safe file names, de-duplicated names within one message, the
  image type from the file's magic bytes, and the paths line.
- **Storage per machine.** `Machine.attachments` is an `AttachmentStore`: `LocalAttachments` under
  `<Octo home>/attachments/<task>/<batch>/`, `RemoteAttachments` under `~/.octo/attachments/` on
  the remote machine, moving bytes as base64 through `sh` over the existing launcher (the path is
  an argument, never part of the script). Both refuse a path outside their root.
- **Orchestrator.** `PromptFiles` saves a message's files, builds what the agent gets, and deletes
  files. `sendPrompt`, `NewTask` and queued prompts carry files; `attach` saves an answer's files.
  Removing a queued message or the task deletes its files. A revert deletes the files of later
  messages and marks them `removed`, keeping the messages.
- **Agents.** `Capability.Images` and `AgentSession.prompt(text, images)`. Claude sends
  stream-json `image` blocks. ACP agents (Cursor, Grok, Kimi, Antigravity, plugins) send `image`
  content when `initialize` offered `promptCapabilities.image`, else the paths line. Codex and
  agents without the capability get the paths line. Steering carries paths only.
- **Remote API and CLI.** `attachments` (name + base64) on the prompt and new-task bodies, refused
  while the flag is off; the HTTP body limit is now 32 MiB. `octo task start|send --attach FILE`;
  `octo task read` lists a message's files.
- **UI.** Attach button, drop and Cmd/Ctrl+V paste in the message box, free-text answers and the
  New task prompt; picked files show as removable chips (`FilePicker` holds them in each view
  model); the transcript and the queue list a message's files, marking removed ones.

Known limits: a task moved to another machine leaves its files on the old one; one remote-API
request carries about one 20 MB file; answers and steering give the agent paths, not images.

## How it was checked

`./gradlew check` is green: ktlint, detekt and every test on JVM and macOS arm64. New tests:
- `AttachmentsTest`: safe and distinct names, types, limits, and `RemoteAttachments` against a fake
  shell (round trip, path kept out of the script, paths outside the root refused).
- `AttachmentTest` (orchestrator): native images plus paths, paths only, the first prompt, limits
  refused before saving, queued files, removing a queued message or the task, revert.
- `ClaudeAdapterTest.imagesGoAsContentBlocks` and `GrokAdapterTest` (image content and the paths
  fallback) against recorded-style transcripts.
- `AttachmentsViewModelTest`: files without text, files kept after a failed send, the limit as
  files are picked, answer files, the New task first prompt.
- `RemoteApiTest.aDeviceSendsFilesOnlyWithTheFlag`; `RemoteHttpServerTest` checks the new body
  limit.
