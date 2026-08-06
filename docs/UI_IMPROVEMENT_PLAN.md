# UI Improvement Plan — OpenUsage App & Widget

Target designs: `OpenUsage Android Redesign.dc.html` for the app and the supplied
`Headroom Widget.dc.html` v1.0 specification for the home-screen widget.
This plan is grounded in the current code (`app/`, minSdk 26, targetSdk 35, stable `material3`,
Glance widget) and the design review findings.

## Implementation status

- **Completed:** Phase 0, A1–A2, and B1–B3.
- **Next:** A3 navigation/settings/auth, then A4 motion and accessibility.
- **Still deferred:** Wear OS.

The app now uses bundled Roboto Flex/Mono, semantic guardrail tokens and vector glyphs,
shared usage indicators, the rebuilt card hierarchy, loading/stale/offline trust states,
and a widget rebuilt around three equal providers. The widget now has four exact responsive
geometries (160×172, 340×72, 340×172, and 340×250dp), weekly-first values, Claude's five-hour
live hairline, severity-only fills, deep links, a matching picker preview, offline/stale states,
and a 48dp refresh target.

---

## 0. Decisions to make before building

| # | Decision | Recommendation |
|---|----------|----------------|
| D1 | Dashboard provider marks: monochrome (macOS convention, §10e rule) or accent-tinted (current mocks + current app)? | **Accent-tinted** — matches shipped app and §2a mocks; update the §10e rule text |
| D2 | Hero = Claude session only, or generalise? Codex's API exposes a primary (session-class) window (`CodexUsageMapper` currently discards it). | Generalise `SessionHero(provider, primaryWindow, nestedSecondary)` now; surface Codex primary later with zero redesign |
| D3 | Guardrail band edges (50/75/90) vs notification milestones (75/80/85/90/100 in `UsageThresholdEvaluator`) are conflated in the design's settings row. | Keep bands fixed for display; expose milestones separately in Alerts settings |
| D4 | Wavy indicator: adopt `material3-expressive` alpha, or custom `Canvas` sine on stable M3? | **Custom Canvas** — stay on stable M3; ~1 day, no alpha dependency |
| D5 | History screen in scope? (Data capture exists; README lists it as a pillar; no UI.) | **Yes** — needs a compact-width entry point (§2 has none; only the 600dp+ nav rail) |
| D6 | Guardrail insights card (`SessionGuardrailCard`) currently collapses unless cap-risk fires. The redesign has no slot for it. | Keep collapsible, restyle into the system; design its visible calm state later if wanted |

---

## Phase 0 — Foundations (shared by both tracks)

| # | Task | Files | Size |
|---|------|-------|------|
| 0.1 | Bundle Roboto Flex + Roboto Mono (variable TTFs in `res/font/`); rebuild typography per redesign §6: `metricHero` 76sp, `metricLarge` 44sp, `metricMedium` 30sp (all `tnum`, Flex 600), `providerLabel`/`statusLabel` 11sp tracked, `countdown`/`countdownSmall` Roboto Mono tabular. Rule: anything that ticks is mono. | `res/font/`, `ui/theme/Type.kt` | M |
| 0.2 | Add `Guardrail` enum + `OpenUsageColors` (fg/container/onContainer per level + 3 provider accents) via `CompositionLocal`, per §6. Migrate `HeadroomStatus.foreground()` etc. onto it. Guardrail ramp stays outside `ColorScheme` so dynamic colour can never recolour a warning. | new `ui/theme/Guardrail.kt`, `HeadroomStatus.kt`, `Theme.kt` | M |
| 0.3 | **Replace guardrail text glyphs with vector drawables.** ●◐◆▲◷ are not in Roboto; Android falls back to OEM symbol fonts inconsistently. This is a live production bug — the app (`HeadroomStatus.kt`) and widget (`UsageWidgetContent.kt`) both render them as `Text`. Create `ic_guardrail_{normal,elevated,high,critical,unknown}.xml`; keep glyph+word+colour triple-coding. | `res/drawable/`, `HeadroomStatus.kt`, widget | S |
| 0.4 | Shape/spacing/elevation tokens per §5: 28dp actionable cards / 20dp tiles / grouped settings corners (20/20/6/6…), spacing scale, dark theme = zero shadows. | `ui/theme/Shape.kt` (new) | S |
| 0.5 | Dynamic colour: re-pin `secondary` (Codex hue) after `dynamic*ColorScheme` — two providers must never collapse into one wallpaper hue. Decide whether the sync "Fresh" dot uses pinned `tertiary` or `OpenUsageColors.normal`. | `Theme.kt` | S |

---

## Track A — Application

### A1 · Dashboard core

| # | Task | Files | Size |
|---|------|-------|------|
| A1.1 | `UsageIndicator` component: flat bar + **threshold ticks at 50/75/90** (1.5dp container-colour gaps — severity readable in greyscale), stop-dot at head, 10/12dp heights; wavy variant (custom Canvas sine, amplitude→0 when not fresh); dashed-desaturated variant for stale. One composable, three styles. | new `ui/components/UsageIndicator.kt` | M |
| A1.2 | Rebuild `SessionHeroCard` per §7: chip always top-right, min-height 236dp (no layout jump between states), mono countdown with precision ladder (**<1h → `m s`**, <24h → `h m s`, >24h → `d h`), nested weekly sub-reading with own chip, Critical = 2dp `error` outline (never a red fill behind the number). | `SessionHeroCard.kt` | M |
| A1.3 | Unify `CodexWeeklyCard`/`GrokWeeklyCard` onto one `ProviderUsageCard` chassis at 60% of hero type scale — the size delta *is* the hierarchy. Consistent qualifiers: `used` + `left` in the same slots on every card. | `ProviderUsageCards.kt` | M |
| A1.4 | Restyle `SessionGuardrailCard` (pace / cap-risk / burn-window / reset-relief) into the system: same 28dp chassis, chip language, no bespoke colours. Keep collapse-when-calm behaviour (D6). | `SessionGuardrailCard.kt` | S |

### A2 · Data states & trust (the redesign's strongest section — currently absent)

| # | Task | Files | Size |
|---|------|-------|------|
| A2.1 | Loading skeletons with exact final geometry (replace full-screen `CircularProgressIndicator` when no cache). | `DashboardScreen.kt` | M |
| A2.2 | Stale state: banner stating age in words + chip swaps to `unknown` glyph + dashed desaturated indicator + countdowns get `— est.` and drop seconds. Single `SyncState` model (Fresh/Ageing/Stale/Offline) shared by all cards. | ViewModel + cards | M |
| A2.3 | Offline as **neutral**, not error: grey banner, refresh disabled not hidden, last-seen time, auto-retry on reconnect. Current `errorContainer` red banner is only for real errors. | `DashboardScreen.kt` | S |
| A2.4 | Auth error scoped per provider: card-level state with "Update key / Sign in again" deep-linking to the correct settings row; other providers keep rendering (already structurally true — keep it, drop the global snackbar duplication). | `DashboardScreen.kt`, cards | S |
| A2.5 | `SyncIndicator` (6dp dot + relative age, absolute on long-press, `liveRegion = Polite` here only) replacing `DashboardMetaCard` content. Countdowns are explicitly **not** live regions. | `DashboardMetaCard.kt` | S |
| A2.6 | Countdown ticker: 1Hz only while resumed, recompute on resume, respect `ANIMATOR_DURATION_SCALE = 0`. | `CountdownTimer.kt` | S |

### A3 · Navigation, settings, auth

| # | Task | Files | Size |
|---|------|-------|------|
| A3.1 | **History entry point on phones** (D5): bottom navigation (Usage / History / Settings) at compact width; `NavigationRail` at medium. History screen itself = simple session samples list (data layer exists). | `AppNavHost.kt`, `Screen.kt`, new history UI | L |
| A3.2 | Settings per §2c: grouped rows with asymmetric corners; providers as first-class rows (masked key + VALID chip / Connect); **floor the poll slider at 30s** (currently 5s — a self-inflicted 429 generator); separate *alert milestones* row (75/80/85/90/100, matching `UsageThresholdEvaluator`) from display bands (D3). | `SettingsScreen.kt`, `RefreshIntervalSlider.kt` | M |
| A3.3 | Add the missing real-world rows: POST_NOTIFICATIONS permission state (requested in `MainActivity` but no denied-state UX), battery-optimisation status (a polling app dies without it), export debug log (scrub keys). | `SettingsScreen.kt` | M |
| A3.4 | **Fix Claude connect guidance** (review finding): the app uses the claude.ai `sessionKey` cookie — never say "API keys" / `sk-ant-api03`. Correct copy + placeholder + help link. | `SessionKeyInput.kt` | S |
| A3.5 | Device-code connect screens for Codex/Grok: user-code display, verification URL, waiting/expiry states. (Flow exists in data layer; design the UI.) | settings/auth UI | M |
| A3.6 | Adaptive layouts per §4: `NavigationRail` 600–839dp, permanent drawer 840dp+, hero width caps (352/392dp), one composable tree via `currentWindowAdaptiveInfo()`. | `DashboardScreen.kt`, scaffold | L |

### A4 · Motion & accessibility

| # | Task | Files | Size |
|---|------|-------|------|
| A4.1 | Motion per §5: springs on value changes, 250ms crossfades for guardrail colour (never flash), digits replace (never slot-machine), wave stops when stale — the state change users notice first. | theme + components | S |
| A4.2 | a11y contract per §7: every card one merged semantics node with a sentence ("Claude session, 65 percent used, elevated, resets in 3 hours 15 minutes"); 48dp targets everywhere; 200% font-scale pass (hero caps at 1.3×, weekly sub-reading stacks past 1.6×); contrast audit of all chip pairs. | all components | M |

---

## Track B — Widget (Glance)

> **Completed against the newer widget specification.** It supersedes the original QUAD/RAIL/HERO
> backlog below: three providers are peers, Claude weekly is the primary Claude value, and its
> five-hour window is the thin live rail. The four launcher buckets use 12sp-or-larger interactive
> text (10.5sp sync footer exception), exact light/dark tokens, stable disconnected rows, provider
> deep links, a 15-minute WorkManager floor, and a 48dp refresh target. The tables are retained as
> the original audit trail; sizing, ring, threshold-tick, and mark-free-preview notes no longer apply.

### B1 · Legibility & trust (highest impact)

| # | Task | Files | Size |
|---|------|-------|------|
| B1.1 | **Raise the type floor: 11sp labels / 10sp captions minimum.** Current widget renders 7–10sp. Requires re-laying the rail rows (wider % and reset columns, shorter labels) and verifying at RAIL min width 250dp. | `UsageWidgetContent.kt` | M |
| B1.2 | Refresh affordance to a real target: wrap the 17dp ↻ circle in a 48dp clickable box. Same for any future tap zones. | `UsageWidgetContent.kt` | S |
| B1.3 | Threshold ticks 50/75/90 on every bar (three 1.5dp container-colour gaps). | `UsageBar` | S |
| B1.4 | Use the vector guardrail glyphs from 0.3 via `ImageProvider` — kills the `Text("◷")` fallback risk in the widget. | `UsageWidgetContent.kt` | S |
| B1.5 | Fix `worstStatusLabel`: it prefixes **◆ for every severity including NORMAL**. Use the per-level glyph+colour; show the footer promo only when worst ≥ Elevated. | `UsageWidgetContent.kt` | S |
| B1.6 | Stale parity with the app: 62% alpha, segmented/dashed-looking bar, `~` prefix (exists), footer "Data from 14 min ago · tap to retry" wired to `RefreshActionCallback`. | `UsageWidgetContent.kt` | M |
| B1.7 | Disconnected rows: keep fixed row count, wire "Add" to open Settings at the right provider row (currently plain text, no action). | `UsageWidgetContent.kt`, `WidgetActionCallback.kt` | S |

### B2 · Structure & sizes

| # | Task | Files | Size |
|---|------|-------|------|
| B2.1 | Rebalance breakpoints and re-verify min sizes: QUAD ring+label legibility at 120×120 (drop ring sublabels if needed — ring + % survives), HERO threshold at 160dp height is tight for ring+3 rails+footer; consider 170dp. | `UsageWidget.kt` | M |
| B2.2 | Deep links: tapping a row/ring opens the app scrolled to that provider (intent extra → nav argument). Whole-widget tap → dashboard (exists). | `WidgetActionCallback.kt`, nav | M |
| B2.3 | Widget picker preview: replace `glance_default_loading_layout` with a real preview layout (Android 15 honours generated previews); keep preview images free of provider marks per brand policy. | `res/xml/usage_widget_info.xml`, `res/layout/` | S |
| B2.4 | Cadence: `updatePeriodMillis` 30min + push-on-fetch is fine; add a 15-min WorkManager floor so the widget never sits stale for hours when the app isn't opened. | widget/worker code | S |

### B3 · Polish

| # | Task | Files | Size |
|---|------|-------|------|
| B3.1 | Empty state: real copy + "Connect" deep link at legible sizes (currently 9sp "Tap to connect"). | `UsageWidgetContent.kt` | S |
| B3.2 | Reconcile keyguard category: verify legibility on the lock screen or drop `keyguard` from `widgetCategory`. | `usage_widget_info.xml` | S |
| B3.3 | Full-sentence `contentDescription`s for rows and footer (rings have them; rail rows don't). | `UsageWidgetContent.kt` | S |

---

## Sequencing

```
Phase 0 (foundations) ──► A1 + B1 in parallel ──► A2 ──► B2 ──► A3 ──► B3 ──► A4
```

- **First shippable increment:** 0.1–0.4 + A1 + B1 — the visual system with legible widget. ~2–3 weeks.
- **Second increment:** A2 states + B2 structure. ~2 weeks.
- **Third:** A3 navigation/settings/auth. ~2–3 weeks (History screen is the big rock).
- **Then:** A4 polish pass.

Dependencies: B1.4 needs 0.3; A1.2/A2.x need 0.1–0.2; A3.6 should land after A1 stabilises the cards.

## Explicit non-goals (for now)

- Wear OS (module is an empty scaffold; spec §8b must be updated for Grok first)
- Lock-screen / keyguard-specific layouts beyond B3.2
- Per-model detail screens (Opus/Sonnet data exists in the domain layer)
- 429/rate-limit dedicated UI (fold into A2.2 stale language for now)
- Editable guardrail band edges (D3 keeps them fixed)
