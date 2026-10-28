# T41. Done

PR: https://github.com/pyrlyn/octo/pull/54

## What was done

- **Model.** `Attachment` (name, path on the task's machine, MIME type) sits on `TranscriptEntry.Text`,
  `TranscriptEntry.Request` and `QueuedPrompt`, so it is persisted with them. The UI hands in
  `NewAttachment` (name, MIME type, bytes), at most 20 MB each.
- **Storage.** `AttachmentStore` per `Machine`:
  - `LocalAttachmentStore` writes `<Octo home>/attachments/<task id>/` through kotlinx-io;
  - `RemoteAttachmentStore` pipes base64 over the machine's `ProcessLauncher` (ssh) into
    `$HOME/.octo/attachments/<task id>/`;
  - file names are sanitized and get a random prefix; deletes only touch paths inside the task folders.
- **Lifecycle** (`TaskAttachments`).
  - Files are saved before the prompt is sent or queued, and deleted again if sending fails.
  - Removing a queued prompt deletes its files; removing a task deletes its folder.
  - A revert to a checkpoint deletes the files of later messages and takes them off those entries.
- **Prompts.** Every agent gets the text plus a list of the saved paths. Adapters with the new
  `Capability.Images` also get the pictures through `AgentSession.prompt(text, images)`; queued prompts
  read them back from the store when they go out. Steering sends the paths only.
- **Answers.** `respond(..., attachments)` takes files per question and appends their paths to that
  question's answer; the request's transcript entry lists them.
- **Adapters.**
  - Claude sends stream-json `image` blocks (png, jpeg, gif, webp up to 5 MB) before the text.
  - `AcpSession` sends ACP `image` content when the agent declares `promptCapabilities.image`,
    else text only. The generic ACP adapter and Cursor declare `Capability.Images`.
  - Codex gets paths only.
- **UI.** The composer and the free-text answer field take Ctrl/Cmd+V of files or a picture, dropped
  files and an Attach button. Pending files show as removable chips. The transcript and the queue list
  the attached names.

## Follow-up

A second PR brings files to the rest of the places a prompt comes from:

- the New task dialog's first prompt (`NewTask.attachments`; files alone start the first turn);
- the Remote API: `attachments` of `{name, mimeType?, data}` in base64 on `POST /tasks` and
  `POST /tasks/{id}/prompt`, checked before any task is touched; the HTTP body limit grows to fit
  one 20 MB file;
- `octo task start|send --attach <file>`, read relative to the caller's directory.

It also shares the reading of a file from disk (`NewAttachment.read`) between the window and the CLI,
and leaves out the empty lines before the file list when a message is files alone.

## How it was checked

`./gradlew check` is green locally: ktlint, detekt and all tests.

- `AttachmentsTest` (core): paths in the prompt, pictures only for `Images` agents, queued files kept and
  deleted, answer paths, the revert drop, task removal and survival across a restart.
- `RemoteAttachmentStoreTest` (JVM): round trip through real `sh` and `base64` in a temp dir, and deletes
  limited to owned paths.
- `ClaudeAdapterTest.picturesGoOutAsImageBlocks` and
  `CursorAdapterTest.picturesGoOutAsImageContentWhenTheAgentTakesThem` replay recorded stdin; a changed
  image byte fails both.
- `AttachmentsViewModelTest`: attach, detach, files-only send, a failed send keeps the files, answer files.

## Limits

- Moving a task to another machine leaves its attachments on the old one.
- A forked task points at its parent's files, which go when the parent is removed.
- Transcript trimming can drop an entry whose files then stay until the task is removed.
- The remote-access web client does not send attachments.
- Codex could take local images natively (`localImage`); it gets paths for now.
