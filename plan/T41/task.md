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

## Execution plan

1. Core model: `Attachment` (name, path on the task's machine, MIME type) on user `TranscriptEntry.Text`,
   `TranscriptEntry.Request` and `QueuedPrompt`; `NewAttachment` (name, MIME type, bytes) as the UI hands it in.
2. Storage: `AttachmentStore` per `Machine` under `attachments/<task id>/` in the Octo home. This computer
   writes through kotlinx-io; a remote machine pipes base64 through its `ProcessLauncher` (ssh) into
   `$HOME/.octo/attachments`. `removeTask` deletes the folder; `revert` deletes the files of messages after
   the checkpoint and drops them from those entries; removing a queued prompt deletes its files.
3. Prompt path: `OctoCore.sendPrompt(..., attachments)` saves first, then every agent gets a text line with
   the saved paths; adapters with the new `Capability.Images` also get `PromptImage`s through
   `AgentSession.prompt(text, images)`. Answers: `respond(..., attachments)` keyed by question id appends
   the paths to that question's answer.
4. Adapters: Claude sends stream-json `image` blocks; `AcpSession` sends ACP `image` content when the agent
   declares `promptCapabilities.image`, else text only.
5. UI: `TaskViewModel` keeps pending attachments for the draft and each free-text question; the composer
   and custom-answer field take pasted images/files, dropped files and an Attach button; the transcript
   and queue list attachment names.
6. Tests: `AttachmentsTest` (core, fake launcher and temp dir), `ClaudeAdapterTest`, the Cursor ACP test,
   `AttachmentsViewModelTest`. Verify with `./gradlew check` (ktlint, detekt, tests) locally.
