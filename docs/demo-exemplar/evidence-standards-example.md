# What full-marks evidence looks like (checklist, not a substitute)

> This describes what strong evidence contains for the categories that can't be
> faked with a write-up — Taiga history, CI/CD runs, diagrams, demo video. It is
> **not** a replacement for actually capturing that evidence; a screenshot or log
> has to be real to count for anything. Use this to check our real evidence against
> before we submit, not instead of collecting it.

## Taiga evidence (`docs/taiga-evidence/`)

Full marks means the evidence *shows progression over time*, not just a healthy
end-state. Concretely:

- At least 3-4 dated backlog/taskboard screenshots spread across the sprint
  period (e.g. week 1, mid-sprint, near code-freeze), not one screenshot taken the
  night before submission.
- Each screenshot's filename carries the date (`backlog-2026-10-07.png`) so the
  marker can see the cadence without opening each file.
- At least one screenshot shows a story moving between states (e.g. "in progress"
  → "done") across two dated captures of the same story — this is what actually
  proves the board was used as a working tool, not populated retroactively.
- A burndown/velocity chart if Taiga is tracking points, since it's the one piece
  of evidence that's very hard to produce after the fact.

What loses marks: a single current-state screenshot, or screenshots all exported
within minutes of each other regardless of their claimed dates.

## CI/CD evidence (`docs/ci-cd-evidence/`)

- Run history for both `ci.yml` and `cd.yml` showing more than one run — a single
  green run proves the pipeline works once, not that it's been used throughout
  development.
- At least one run that **failed**, with the fix in a later commit. A pipeline
  that's never caught anything is weaker evidence than one that demonstrably did
  its job once.
- A full `mvn test` output (or CI log excerpt) showing the suite count and
  BUILD SUCCESS, matched against what the pipeline itself reports, so the two
  aren't inconsistent.

## Diagrams (`docs/diagrams/`)

- Every class/sequence diagram uses correct UML stereotypes (`<<boundary>>`,
  `<<control>>`, `<<entity>>` for B-C-E) consistently — mixing notations across
  diagrams is a common mark-loser.
- No leftover placeholder text, template instructions, or "TODO" labels exported
  into the final PNG.
- Diagrams are exported at a resolution where text is actually legible when the
  marker opens the file at 100%, not shrunk to fit a page.
- Where our real architecture diverges from the original design (e.g. a service
  method added later than the diagram was drawn), the diagram is updated to match
  — a diagram that documents an earlier version of the code is misleading evidence
  of the final B-C-E structure, not a snapshot worth keeping.

## Demo video (`docs/demo-video/`)

- Script covers all four roles (Customer, Interior Designer, User Admin, Platform
  Management) with roughly even time, not three roles in depth and one in 10
  seconds.
- Uses the seeded test data (`MockDataGenerator`) rather than empty/freshly-created
  accounts, so the UI shows realistic volume (lists with several rows, non-trivial
  charts) rather than a one-row empty state.
- Shows at least one "why this matters" moment per role tied to a specific user
  story (e.g. "this is PM-07 — the coverage table shows Lighting has more bookings
  than active designers"), not just a silent click-through.
