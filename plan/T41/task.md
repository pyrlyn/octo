---
status: todo
priority: P1
complexity: 3
readiness: 0%
agent:
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
