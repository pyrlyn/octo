# T36. Rich file previews — done

PR: https://github.com/pyrlyn/octo/pull/47

## What was done

The new `previews` flag makes the diff view show a changed file as a preview, with a Diff
toggle back to the diff. `FilePreviewer` (`ui/preview`) chooses the renderer from the file
extension:

- **Images:** png, jpg, gif, webp, bmp and ico are decoded by Skia. SVG is drawn from its view
  box.
- **Jupyter notebooks:** cells and their outputs (stream, text, error and PNG).
- **Zip-family archives:** the entry list, read from the central directory.
- **SQLite databases:** the tables and their first rows, read by the `sqlite3` CLI with
  `-readonly -safe` and `trusted_schema=OFF`.
- **Mermaid diagrams:** drawn to PNG by `mmdc`. Without `mmdc`, or when it fails, the source is
  shown with the reason.
- **Anything else:** keeps the existing text or binary view.

Remote tasks and deleted files keep the plain diff.

Guards for untrusted repository content:

- the file's real path must be inside the worktree;
- the file must be 20 MB or less;
- pictures are capped at 40 megapixels;
- table names in SQL are quoted.

There are no new dependencies. Office files (docx, xlsx, pptx) were left out because the done
criteria did not ask for them.

## How it was checked

- `./gradlew check` is green.
- `FilePreviewerTest` (7 tests) covers each renderer's parsing, the path guard and the
  fallbacks.
- `DiffViewModelTest` (3 tests) covers the preview state, the toggle, and the cases with no
  preview.
- The `sqlite3` arguments were run against a real database with sqlite3 3.54. Safe mode
  rejects ATTACH.
