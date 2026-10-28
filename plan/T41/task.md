---
status: in progress
priority: P1
complexity: 3
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T41. Attach images and files to prompts and answers

A user cannot give an agent a screenshot, a log file or a design mock: the composer takes text only.
T3 Code accepts pasted images and attached files in the composer and in question answers; files are
saved on the machine that runs the task and the agent gets their paths
([question-attachments.md](https://github.com/pingdotgg/t3code/blob/125b61954d/docs/user/question-attachments.md)).

Done means:

- The composer and the custom-answer field accept pasted images, dropped files and an Attach button.
- Files are copied into the task's attachments folder in the Octo home on the task's machine
  (over ssh for remote tasks); removing the task removes them.
- Adapters that declare an image capability send images natively (Claude stream-json image blocks,
  ACP `image` content); the others get a text line with the saved paths.
- Attachments show in the transcript and survive a restart; a revert to a checkpoint drops the
  attachments of discarded messages.
- Core, the Claude adapter, one ACP adapter and the view model have tests.

Also in scope, on the creator's word: the first prompt of the New task dialog, and the remote-access
API plus the `octo` command.

Decisions: a file is at most 20 MB and a message carries at most 10 files; PNG, JPEG, GIF and WebP
images up to 5 MB go to agents with the image capability as images, everything else as a line of
paths. A revert deletes the files of messages after the checkpoint and marks them removed in the
transcript, which keeps the messages.

## Execution plan

1. Core model: `Attachment` (name, path on the task's machine, MIME type, size, removed) on
   `TranscriptEntry.Text` and `QueuedPrompt`; `FileUpload` (name, bytes) as input; limits and
   file-name sanitizing in one place.
2. `AttachmentStore` per `Machine`: local under `<Octo home>/attachments/<task>/`, remote under
   `$HOME/.octo/attachments/<task>/` through the launcher with base64 over stdin; save, read,
   delete, delete all.
3. Orchestrator: `sendPrompt`, `NewTask` and queued prompts carry uploads; `attach` for answers;
   `Capability.Images` and `AgentSession.prompt(text, images)`; others get the paths line;
   `removeTask` deletes the folder; `Checkpoints.revert` deletes and marks later attachments.
4. Adapters: Claude stream-json image blocks; ACP `image` content when the agent's
   `promptCapabilities.image` is set, else the paths line.
5. Remote API (`attachments` on prompt and new-task bodies, larger body limit) and
   `octo task send|start --attach`.
6. UI: attach button, paste and drop in the composer, the custom-answer field and the New task
   prompt; chips in the transcript.
7. Tests: core (orchestrator, store, checkpoints), Claude, one ACP adapter, view models, remote
   API; `./gradlew check` with ktlint and detekt.
