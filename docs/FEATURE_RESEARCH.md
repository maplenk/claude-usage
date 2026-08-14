# OpenUsage — Feature Research & Roadmap

Research date: 2026-08-14. Scope: what to build next in OpenUsage (Android today, iOS in
progress), judged against what comparable Claude Code usage tools ship in 2026 and against the
gaps visible in this codebase.

## How this is ranked

Each item carries **Impact** (how much it changes daily use) and **Effort** (rough build cost for
this codebase). Ordering inside each tier is by impact-per-unit-effort, not alphabetical.

---

## Tier 1 — Build these next

### 1. In-app WebView login for the Claude session key
**Impact: very high · Effort: medium**

Today onboarding requires a desktop browser, dev tools, Application → Cookies, and copying
`sessionKey` by hand ([README.md](../README.md) "Extract Your Session Key"). For a rollout across
an internal team that is the single biggest source of friction and support requests. Codex and
Grok already have a device-code flow; Claude is the odd one out.

Load `claude.ai` in a `WebView` / `WKWebView`, let the user sign in normally, then read the
`sessionKey` cookie from the cookie store. No credentials ever touch app code — the user types
their password into Anthropic's own page.

Ship alongside the existing paste field, not instead of it.

### 2. Session-key expiry detection and re-auth alerts
**Impact: very high · Effort: low**

Claude session keys expire. Right now the failure is silent from the home screen: the widget
just goes stale and the user assumes usage is flat. `UsageError.Unauthorized` already exists in
the domain model — surface it.

- A distinct, high-priority notification: "Claude sign-in expired — tap to reconnect."
- A persistent unauthorized state on the widget (not the generic stale styling), tapping through
  to Settings.
- The same for Codex/Grok when token refresh fails permanently.

This is small, and it is the difference between a tool people trust and one they quietly stop
believing.

### 3. Weekly burn-rate projection
**Impact: high · Effort: low-medium**

`SessionGuardrailEvaluator` already does pace-vs-usual and cap-risk prediction for the 5-hour
session. The weekly window is where the real pain is in 2026 — weekly caps reset at a fixed
account-assigned time and cannot be waited out with a short break, unlike the 5-hour session.

Extend the same evaluator to the weekly metrics: "at your current pace you exhaust the weekly cap
Thursday morning; reset is Saturday 09:00." Pair it with the new 70/80/90/100 weekly alerts so the
notification carries a projection, not just a percentage.

### 4. Opus-vs-Sonnet routing nudge
**Impact: high · Effort: low**

The app already fetches `sevenDayOpus` and `sevenDaySonnet` but the dashboard treats them as
secondary context. Opus is metered and reset separately from other models, so the actionable
insight is a comparison, not two numbers: "Opus weekly 88%, Sonnet weekly 31% — switch to Sonnet
to protect your Opus headroom." Same idea across providers: "Claude 82%, Codex 30% — route today's
work to Codex."

That is the one thing a usage app can tell you that changes what you do in the next five minutes.

### 5. Quiet hours for notifications
**Impact: medium-high · Effort: low**

With the weekly ladder added on top of session milestones, guardrail alerts and session-reset
alerts, notification volume is now high enough that a 03:00 "weekly limit at 70%" is a real
possibility. A start/end time window in Settings that suppresses everything except a
configurable critical tier. Cheap, and it prevents people muting the app entirely.

---

## Tier 2 — Strong candidates

### 6. Quick Settings tile (Android)
**Impact: medium · Effort: low**

A `TileService` showing session percentage in the notification shade. Reaches users who never add
home-screen widgets, and it is maybe 150 lines. Android-only.

### 7. Multiple accounts per provider
**Impact: medium-high · Effort: medium-high**

Many developers hold a personal and a work Claude account. Today `SecureCredentialStore` assumes
one session key and one org. Supporting an account list with a switcher — and a widget bound to a
specific account — is a genuine architectural change to the credential store, cache keys and
widget state, but it is the most-requested shape of feature in this category.

### 8. History export and richer trends
**Impact: medium · Effort: low-medium**

`UsageHistoryStore` already keeps 30 days of CSV-backed samples. Two cheap wins on top of data
that already exists:
- Share-sheet export of the CSV.
- A day-of-week heatmap — "you burn 60% of the weekly cap by Wednesday" is a scheduling insight
  no percentage bar conveys.

### 9. Wear OS complication and tile
**Impact: medium · Effort: medium**

The `wear/` module exists but is empty and excluded from `settings.gradle.kts`; the README calls
it deliberately deferred. A watch complication is arguably the purest expression of this app's
premise — one number, always visible, zero interaction. Worth reviving once iOS settles.

### 10. Team roll-up view
**Impact: medium · Effort: high · ⚠ privacy**

Since this is going to an internal team, an aggregate "who has headroom right now" view is
tempting for routing work. It requires a backend or a shared endpoint, and it turns a local-first
app into one that exports individual developers' activity data.

If built, it must be strictly opt-in per person, share only a percentage bucket rather than raw
usage, and be clearly disclosed. Do not make it default-on. Flagging this deliberately — the
current architecture's "no remote analytics pipeline" property is a feature worth protecting.

### 11. Widget theming
**Impact: medium · Effort: low-medium**

Material You dynamic color, a monochrome mode for minimal launchers, and an accent picker. The
four-limit widget currently hardcodes its palette in
[FourLimitWidgetContent.kt](../app/src/main/java/com/qbapps/claudeusage/widget/FourLimitWidgetContent.kt).

### 12. Siri / App Intents and Android App Shortcuts
**Impact: medium · Effort: low-medium**

"Hey Siri, what's my Claude usage?" On iOS, App Intents also unlock interactive widgets and
Shortcuts automations later. On Android, dynamic app shortcuts to a specific provider.

---

## Tier 3 — Later, or blocked

### 13. iOS Live Activity / Dynamic Island
**Impact: high on iOS · Effort: medium · 🚫 blocked**

An active 5-hour session on the Lock Screen and in the Dynamic Island is the best possible surface
for this app. Blocked by the free personal Apple ID: Live Activities need push-capable
entitlements. Unblocked by a $99 Developer Program membership.

### 14. iOS Lock Screen accessory widgets
**Impact: high on iOS · Effort: low once widgets exist · 🚫 blocked**

`accessoryCircular` / `accessoryRectangular` are a natural fit. Blocked by the same App Groups
limitation that pushed iOS widgets to phase 2.

### 15. Additional providers
**Impact: medium · Effort: medium each**

Gemini, Cursor, GitHub Copilot and Windsurf all now meter usage. The three-provider architecture
generalises cleanly, but each integration carries the same maintenance risk already documented for
the Codex WHAM endpoint — these are not public API contracts and they break without notice.

### 16. Anthropic Console API spend
**Impact: low-medium · Effort: medium**

Separate from subscription limits and only relevant to API-key users. Anthropic's own Console
already covers this well, with per-workspace and per-model breakdowns and CSV export. Low
differentiation.

### 17. Outbound webhooks (Slack / Discord)
**Impact: medium · Effort: low-medium · ⚠ privacy**

Several 2026 monitors ship Slack/Discord/Telegram alerts before rate limits hit. Straightforward
to add, but it sends usage data off-device, so it belongs behind an explicit opt-in with the
destination URL entered by the user.

---

## What the field looks like in 2026

Useful context for positioning, from the current tool landscape:

- **`ccusage`** (~16.5k stars) parses local Claude Code session logs, groups usage into the 5-hour
  billing windows, and reports burn rate in tokens/minute with cost projection. Desktop CLI,
  log-based. Its `blocks --live` monitor was removed in v18.0.0.
- **Claude Code Usage Monitor** and similar tools do percentile-based prediction over a trailing
  window (P90 over ~192 hours) rather than naive linear extrapolation — worth borrowing for the
  weekly projection in item 3.
- **Tokemon** is a native macOS menu-bar app — same "always visible, one number" thesis as this
  project's widget, on a different surface.
- **Torii** and similar enterprise platforms attack the org-level problem: per-employee and
  per-model spend, overage forecasting, and duplicate-tool detection across Claude Code, Copilot
  and Cursor.
- **First-party** `/usage`, `/status` and `/cost` inside Claude Code, plus Settings → Usage in the
  Claude apps, are the baseline everything else is measured against.

**Where OpenUsage is differentiated:** it is the only one of these on the phone home screen, and
the only one showing Claude, Codex and Grok side by side. Nearly every competitor is a desktop CLI
or menu-bar tool that reads local log files, which means it cannot tell you anything while you are
away from the machine. The roadmap above leans into that — glanceability, prediction, and
cross-provider routing — rather than chasing per-token cost accounting where the log-based tools
are structurally better.

**Where it is behind:** burn-rate and projection are richer in the CLI tools, and they have
per-model cost breakdowns this app does not attempt.

## Sources

- [5 Claude Code Usage Dashboards and Monitoring Tools for 2026 — Torii](https://www.toriihq.com/articles/five-claude-code-usage-dashboards-and-monitoring-tools)
- [6 Tools to Track Claude AI Token Usage in 2026 — Torii](https://www.toriihq.com/articles/six-tools-to-track-claude-ai-token-usage)
- [ccusage — Blocks Reports](https://ccusage.com/guide/blocks-reports)
- [What is CC Usage Tool for Claude Code — ClaudeLog](https://claudelog.com/faqs/what-is-ccusage-tool/)
- [Claude Code Usage Monitor — GitHub](https://github.com/Maciek-roboblog/Claude-Code-Usage-Monitor)
- [Claude Code Usage Limits (2026) — Morph](https://www.morphllm.com/claude-code-usage-limits)
- [Claude Weekly Limit Explained (2026) — ClaudeLimit](https://claudelimit.com/claude-weekly-limit/)
- [When does Claude Code usage reset in 2026 — CometAPI](https://www.cometapi.com/when-does-claude-code-usage-reset/)
- [Claude Token Usage Monitoring: Complete 2026 Guide — Tokemon](https://www.tokemon.ai/blog/claude-token-monitoring-guide)
- [How to monitor Claude Code token usage — Faros](https://www.faros.ai/blog/claude-code-token-usage)
