# 15 - UI Design System

> Reconciled against the actual implementation (`frontend/src/App.css`). The
> version of this doc before the Phase 4-9 rebuild described a white
> background with blue accents — that was never true of the shipped app and
> predates the current dark/purple system entirely.

## Color Palette

Dark surface, one reserved accent hue. See [16-brand-guidelines.md](16-brand-guidelines.md)
for the *rule* this palette exists to serve — purple means "the system's
judgment about you," and nothing else uses it.

| Token | Value | Use |
|---|---|---|
| `--background` | `#090a0e` | Page background |
| `--surface` | `#14151a` | Cards, panels |
| `--surface-soft` | `#191a21` | Nested surfaces (poster gradients, chips) |
| `--surface-elevated` | `#20212a` | Modals, popovers |
| `--text` | `#fafafa` | Primary text |
| `--text-muted` | `#a1a1aa` | Secondary text |
| `--text-faint` | `#71717a` | Metadata, timestamps |
| `--primary` | `#a855f7` | Match scores, TasteDNA radar, primary CTA — reserved |
| `--primary-light` | `#c084fc` | Hover/active states on primary elements |
| `--primary-dark` | `#7e22ce` | Gradient stops |
| `--border` | `rgba(255,255,255,0.085)` | Default hairline borders |
| `--border-strong` | `rgba(255,255,255,0.15)` | Emphasized borders (focus-adjacent) |
| `--danger` | `#ef4444` | Errors |
| `--success` | `#4ade80` | Confirmations |

The secondary/comparison hue (a movie's own vector overlaid on a user's
radar, or the evolution timeline's second/third line) is **not** a shade of
purple — see `TraitRadar.jsx`'s `#0284c7` and `EvolutionTimeline.jsx`'s
`LINE_COLORS`, both chosen by running the `dataviz` skill's palette
validator against this dark surface (CVD-safe, ≥15 normal-vision ΔE floor).
Purple is reserved for "this is about you"; nothing else borrows it.

## Spacing

An 8-point scale (`--sp-1` through `--sp-12`, 8px–96px), used in place of
ad-hoc pixel margins so vertical rhythm stays consistent across sections.

## Typography

`Plus Jakarta Sans`, weight carrying hierarchy rather than size alone:

| Token | Weight | Use |
|---|---|---|
| `--fw-hero` | 900 | Page-level headlines (mood-aware hero greeting) |
| `--fw-section` | 700 | Section headings |
| `--fw-card` | 600 | Card titles |
| `--fw-metadata` | 400 | Labels, timestamps |
| `--fw-description` | 350 | Body copy, synopses |

## Motion

`framer-motion` for staggered card entry, spring hovers, and the
"your TasteDNA just moved" shift toast on rating; hand-written CSS
`@keyframes` for the radar/timeline draw-in (kept out of JS so the
`prefers-reduced-motion` block at the end of `App.css` disables all of it
uniformly with one media query, rather than needing per-component checks).

## Components with their own visual language

These aren't generic — each renders real computed data, not a static asset:

- **TraitRadar** (`TraitRadar.jsx`) — hand-rolled SVG, confidence rendered as
  a geometric fuzzy band (band width = `1 − confidence`), not a footnote.
- **EvolutionTimeline** (`EvolutionTimeline.jsx`) — multi-line chart with
  milestone markers annotated by the specific rating that caused the move.
- **MatchRing** (`MatchRing.jsx`) — sequential single-hue progress ring; a
  42% and a 97% must look different at a glance, which a flat badge (the
  pre-rebuild version) never achieved.
- **BrandMark** (`BrandMark.jsx`) — an aperture whose inner segments form a
  decagon, echoing the 10-axis radar shape.

## Accessibility baseline

Every modal (`MovieModal`, the TasteDNA share card) shares one hook,
`useModalA11y.js`: focus trap, Escape-to-close, body scroll lock, and
focus-restore to the triggering element on close. Skip link to
`#main-content`; visible `:focus-visible` rings on all interactive elements,
including the ones that used to be unstyled `<div>`s before the Phase 4
inline-style extraction. See [17-security.md](17-security.md) for the
authorization model and [09-recommendation-engine.md](09-recommendation-engine.md)
/ `docs/CASE-STUDY.md` for how the data behind these components is derived.
