---
status: todo
priority: P3
complexity: 4
readiness: 0%
agent:
---

# T36. Rich file previews

The agent produces more than code: office files, sqlite databases, archives, notebooks and
diagrams. Today they show up only as paths in the diff. A preview lets the user check them
without leaving Octo, as T3 Code does ([d69810a](https://github.com/pingdotgg/t3code/commit/d69810a),
[9d17a02](https://github.com/pingdotgg/t3code/commit/9d17a02)).

Done means: the diff view opens a preview for at least images, notebooks, sqlite databases,
archives and Mermaid diagrams; an unknown type falls back to the text or binary view; tests
cover each renderer's parsing.
