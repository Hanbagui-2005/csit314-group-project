# Data-driven development (Topic 11) — exemplar

> Worked example applying the 9-stage lifecycle to `UserManagementService.riskScore()`
> (`src/main/java/com/csit314/platform/service/UserManagementService.java:60-77`),
> which already exists as a real, if simple, data-driven feature: it turns profile
> fields into a 0–100 "risk" number used in the admin approval queue (ADM-07).
> Treating this small heuristic honestly through all 9 stages scores better than
> describing a fictional ML pipeline the codebase doesn't have — the lifecycle
> applies the same way whether the "model" is a trained classifier or a hand-built
> scoring rule, and markers can check every claim against real code.

## 1. Model requirements

**Goal:** give User Admin a fast signal, in the pending-designer queue, for which
applications are more likely to need a closer look before approval.
**Constraint:** must be explainable to a non-technical admin (no black box), and
must never auto-approve/auto-reject — it's a sort/triage aid, not a decision-maker.
**Success looks like:** admins spend less time on complete, low-effort-to-verify
applications and more time on sparse ones — not "the score is statistically
accurate," because there's no ground truth label for "risk" in this domain yet.

## 2. Data collection

Inputs are fields already collected at designer registration
(`DesignerRegistrationForm`): company description, phone, licence number, contact
name. No new data collection was introduced for this feature — it's entirely
derived from existing required/optional form fields. This matters for the write-up:
a real data-driven feature audit starts by stating exactly where every input comes
from, and here all four inputs are self-reported by the applicant at signup, which
is itself a limitation worth naming (self-reported completeness is easy to game —
anyone can type a space into a name field).

## 3. Cleaning

Each field is null/blank-checked before being scored (`!= null && !isBlank()`).
There's no further cleaning because the inputs are categorical presence checks, not
free text being analysed — worth stating explicitly rather than leaving the stage
blank, since "no cleaning needed, and here's why" is a valid answer when it's true.

## 4. Labelling

This is the stage that most exposes the feature's real limitation: there are no
labelled examples of "this designer turned out to be risky" anywhere in the system.
The score isn't learned from outcomes, it's a fixed heuristic (flat -25 per
completed field). A full-marks write-up says this plainly instead of glossing over
it: without labels, there is no way to validate that completeness actually predicts
anything about designer risk, and the lifecycle stage that would normally close that
loop (Evaluation, below) can't be done rigorously here. That's the central finding
of the exercise, not a gap in the write-up.

## 5. Feature engineering

Four binary features, each worth 25 points, summed and inverted (100 - points
earned = risk). No normalization or weighting beyond the flat 25-point split. Worth
noting as a design choice: equal weighting assumes a missing licence number is as
informative as a one-word contact name, which is almost certainly false — a missing
licence number is a much stronger signal for a business claiming to be licensed
than name formatting is. This is a concrete, specific critique a marker can verify
against the code.

## 6. Training

There is no training step — the weights (25/25/25/25) were chosen by hand, not
fit to data. State this directly: "this is a rule-based heuristic, not a trained
model," and explain why that's a reasonable MVP choice for a student project with
no historical outcome data to train on, rather than pretending it's more
sophisticated than it is.

## 7. Evaluation

Honest answer: it hasn't been evaluated against outcomes, because (per stage 4)
there are no outcome labels. What *was* checked is implementation correctness —
`AdminInsightsServiceUnitTests`/related unit tests confirm the score computes as
expected for known input combinations (e.g. all fields present → 0, all absent →
100). That's a correctness check, not a validity check, and the write-up should
distinguish the two explicitly: "we verified the formula computes correctly; we did
not and currently cannot verify the formula is a good measure of actual risk."

## 8. Deployment

The score is computed on read (`toSummary()`, called from `listAllUsers()`) rather
than stored — it's always derived fresh from the current profile state, so it can
never go stale relative to the data it's based on. That's a deliberate, defensible
deployment choice worth naming: no caching/invalidation problem exists because
there's nothing cached.

## 9. Monitoring

Currently: none. There's no logging of what score each application got, no record
of whether admins approved despite a high score or rejected despite a low one, and
no way to tell later whether the heuristic is steering decisions well or badly. A
full-marks write-up proposes a concrete, small fix: log `(designerId, riskScore,
finalDecision)` at the point `approveDesigner`/`rejectDesigner` run, which would
produce exactly the outcome data that stage 4 (Labelling) is currently missing —
closing the loop for a future version of the feature.

---

**Why this is a stronger write-up than a generic one:** it doesn't invent a machine
learning pipeline that isn't there. It applies the same 9 stages honestly to the
simple heuristic that actually exists, and the strongest marks come from precisely
naming what's missing (labels, evaluation against outcomes, monitoring) rather than
hiding those gaps.
