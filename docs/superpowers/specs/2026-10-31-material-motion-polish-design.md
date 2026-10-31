# Material Motion Polish Pass (v2.4.2) — Design

Date: 2026-10-31
Status: Approved by Matt

## Intent

Make the app feel alive and deliberate: motion that communicates change, not decoration.

## Goals

1. **Animated totals** — tips/hours/wages figures count up/down smoothly when the month changes or entries are added/edited.
2. **Screen transitions** — staggered slide/fade entrance for hero + mini cards on app open; Material fade-through/shared-axis on month arrows and dialog open/close.
3. **List life** — record rows animate on add/edit/delete instead of jumping.
4. **Micro-interactions** — chip presses ripple with scale; paid/unpaid status flips animate.

Order: main screen first (main month screen + add/edit dialogs), then dashboard, year view, settings.

## Non-goals

- No theme/color/type redesign — colors and cards stay as-is.
- No new screens.
- No animation on/off toggle — motion is subtle and always-on.
- Widget excluded (RemoteViews cannot animate).

## Constraints

- Views system, no Compose.
- **No new dependencies**: use `com.google.android.material.transition` (MaterialFadeThrough, MaterialSharedAxis, MaterialFade — already in the material artifact), `ViewPropertyAnimator`, RecyclerView `ItemAnimator`.
- Money/hours strings render through the existing `Locale.ENGLISH` format helpers; animated interim values are display-only and never parsed (the parseAmount bug class must not regress).
- Dark mode must work everywhere (colors from theme attrs, not hardcoded).
- The exact-alarm reminder fix (this session's earlier commit) ships in the same v2.4.2.

## Approaches considered

- **Material motion via Views API (CHOSEN)** — `MaterialFadeThrough`/`MaterialSharedAxis`/`MaterialFade` + ViewPropertyAnimator + custom ItemAnimator. Small, idiomatic, everything already on the classpath.
- MotionLayout — XML choreography, overkill and brittle for dynamic calendar data.
- Lottie — new dependency, JSON animations for what the framework does free.
- Compose migration — massive scope, rewrites everything.

## Left to the builder

- Exact durations/easings (follow Material motion defaults).
- Whether count-up animates on data edits within the same month (yes — keep it consistent, it feels the same as a month change).
- Stagger delays per card (keep under ~250ms total; don't make the user wait).
- ItemAnimator custom vs `DefaultItemAnimator` with tweaks — builder's call, must handle add/edit/remove/move.
- Settings screen gets the lightest treatment (fade-through on entry at most).

## Verification

- Unit tests green (`.\gradlew.bat testDebugUnitTest`), assembleDebug green.
- Install debug build over v2.4.1 on the S24 Ultra; human eyeballs the motion (subjective, user decides).
- Reminder test: user enables reminder in Settings, set time ~2 min ahead, verify notification fires on the minute.
