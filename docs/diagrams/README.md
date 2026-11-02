# Diagrams

Use-case, class and sequence diagrams for the B-C-E design.

Wait until the Thursday code freeze before redrawing - diagrams must use the
exact class/method names from the code at freeze time, not the names in the
earlier draft.

Rules (from the team's diagram review):
- Stereotype classes as `«Boundary»`, `«Control»`, `«Entity»` - same style for everyone
- Repositories belong inside the Entity layer, marked `«Entity»`
- Remove anything the code doesn't actually have (e.g. date filters, currency,
  `SuspensionAction`, `ProjectEstimate`)
- Delete any "design assumptions / confirm with the team" placeholder text
- Export as PNG at 200%+ scale - no draw.io screenshots, no grid lines visible
- Use the same description template for every diagram

Drop exported PNGs directly in this folder.
