---
status: in progress
priority: P3
complexity: 4
readiness: 100%
agent: Claude Code / claude-opus-5-5
---

# T36. Rich file previews

The agent produces more than code: office files, sqlite databases, archives, notebooks and
diagrams. Today they show up only as paths in the diff. A preview lets the user check them
without leaving Octo, as T3 Code does ([d69810a](https://github.com/pingdotgg/t3code/commit/d69810a),
[9d17a02](https://github.com/pingdotgg/t3code/commit/9d17a02)).

Done means: the diff view opens a preview for at least images, notebooks, sqlite databases,
archives and Mermaid diagrams; an unknown type falls back to the text or binary view; tests
cover each renderer's parsing.

## Decisions (creator)

- No new dependencies: the JDK, Skia (already under Compose) and the project's
  kotlinx-serialization.
- Mermaid renders through an installed `mmdc` (mermaid-cli); without it the source shows.
- Local tasks only; a task on a remote machine keeps the plain diff.

## Execution plan

1. `CoreFlags.PREVIEWS` (`previews`); `Octo.previews`; `Main.kt` hands a `FilePreviewer` to
   `ViewModels` only while the flag is on.
2. `ui/.../preview/FilePreviewer.kt`: picks a renderer by extension and reads the file from
   the task's worktree. Repository files are untrusted: the real path must stay inside the
   worktree (no symlink escape) and the file must be under a size limit.
   - Images (png, jpg, gif, webp, bmp, ico) through Skia; SVG through Skia's SVG DOM.
   - `.ipynb`: markdown and code cells, text, error and PNG outputs.
   - Zip-family archives: the entry list from the central directory, capped.
   - SQLite (magic header checked): tables and their first rows through the `sqlite3` CLI,
     read-only and in safe mode.
   - `.mmd`/`.mermaid`: `mmdc` to SVG in a temp directory; the source when it is missing or fails.
3. `DiffViewModel` loads the preview for the selected file; `DiffScreen` shows it with a
   Preview/Diff toggle. Anything else keeps the existing diff or binary view.
4. Tests in `ui/src/test` for each renderer, the path guard and the view model; README and
   architecture flag rows.

Check: `./gradlew check` is green; the tests above pass.
