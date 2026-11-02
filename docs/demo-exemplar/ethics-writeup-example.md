# Ethics discussion (Topic 5) — exemplar

> This is a worked example of a full-marks ethics write-up for this platform. It's
> grounded in code that exists today, with file/line references so the claims are
> checkable. A generic "AI can be biased, privacy matters" essay loses marks; this
> doesn't, because every claim traces to a specific decision in our code.

## 1. Bias in automated decisions: the designer risk score

`UserManagementService.riskScore()` (`src/main/java/com/csit314/platform/service/UserManagementService.java:60-77`)
assigns a pending designer a score from 0–100 based purely on **form completeness**:
whether the company description, phone, and licence number fields are filled in, and
whether the contact name contains a space. Each missing field costs 25 points.

Ethical issues this raises:

- **Proxy bias, not fraud signal.** The score is presented to User Admin as a "risk"
  indicator (ADM-07), but it measures data-entry effort, not trustworthiness or fraud
  likelihood. A single-word legal business name (common for sole traders, or for
  non-Western naming conventions where "contact name contains a space" doesn't hold)
  is penalised the same as a blank field, even though neither implies risk.
- **Automation bias in the human reviewer.** Because the number is framed as "risk,"
  an admin skimming a long pending-approval queue is likely to defer to it rather
  than open the application and judge it themselves — exactly the failure mode this
  course's ethics material warns about with opaque scoring.
- **No disclosure to the person being scored.** The designer whose profile is being
  scored is never told a score exists, what it measures, or how to improve it beyond
  "fill in your profile." There's no appeal path if an admin rejects them partly on
  the strength of a misleading score.

What a full-marks write-up does with this, beyond naming it: propose a concrete
mitigation that fits the existing code, e.g. renaming the field and UI label from
"risk score" to "profile completeness" (it's honest about what it actually measures),
and keeping the approve/reject decision explicitly manual rather than threshold-driven
(which it already is — `approveDesigner`/`rejectDesigner` are separate, deliberate
admin actions, not auto-triggered by the score). That second point is worth stating
explicitly: the current design already avoids the worst version of this problem
(fully automated rejection), and the write-up should credit that rather than only
listing flaws.

## 2. Bias in recommendations: favourites-based suggestions

`FavouriteService.recommendationsFor()` and `compareShortlistedDesigners()`
(`FavouriteService.java:71-105`) build recommendations and designer comparisons
purely from a customer's existing shortlist. The code comment is honest about why:
*"search history" isn't tracked anywhere in this app, so this is purely
favourites-based* (line 72).

Ethical issue: a cold-start customer with one shortlisted project gets
recommendations drawn from a single category, which can narrow their exposure to
designers rather than broaden it (a filter-bubble effect at small scale). It's a
much smaller version of the same problem as large-platform recommender bias, worth
naming precisely because the mechanism is simple enough to fully explain — most
real recommender systems are not this inspectable, so this is a good case study.

Mitigation worth proposing: surface the basis for the recommendation in the UI
("because you shortlisted X") so the customer understands why they're seeing a
narrow set, rather than presenting it as a neutral "recommended for you."

## 3. Data privacy: registration IP tracking

`UserManagementService.registrationAlerts()` (lines 179-202) groups every account
created in the last hour by `registrationIp` to flag sign-up bursts (ADM-14). This
means the platform stores and processes IP addresses — personal data under most
privacy frameworks — for every registrant, including customers who never see or
consent to this use.

Points to raise: purpose limitation (IP is collected for fraud detection, so it
should not be reused for anything else, e.g. profiling or marketing — and today
there's nothing in the code stopping that reuse, which is itself a gap), retention
(there's no code path that ever deletes old `registrationIp` values), and that the
alert is only ever shown to User Admin, not acted on automatically — which is the
right call ethically (a human decides what a burst means) but should be stated as
a deliberate design choice, not an oversight.

## 4. Consent around shortlisting/viewing

Shortlisting a project (`FavouriteService.add`) is an explicit customer action, so
consent is clear there. But the resulting shortlist count is visible to the designer
(`shortlistersFor`, IDR-11) and the daily trend is visible to them too (IDR-12) —
customers are not told, anywhere in the registration or shortlisting flow, that
their shortlisting activity is visible to the designer whose project they saved.
That's a straightforward disclosure gap worth naming and fixing with a one-line UI
note, not a redesign.

## 5. Complaint moderation

Complaints against designers go through User Admin, who can suspend the designer
(`UserManagementService.suspend`) — but there's no corresponding path for a designer
to dispute a complaint before action is taken, and no size/evidence threshold in the
code before a suspension is possible on a single complaint. A full-marks write-up
flags this as a due-process gap: the system gives the accused no opportunity to
respond before an irreversible-feeling action (suspension) is available to the admin.

---

**Why this is a stronger write-up than a generic one:** every claim names the exact
method and line range, states the mechanism precisely enough that a marker can verify
it by opening the file, and pairs each problem with a specific, implementable
mitigation rather than a vague "we should be careful about bias."
