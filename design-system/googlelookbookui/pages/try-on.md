# Try On Page Overrides

> **PROJECT:** GoogleLookBookUI
> **Generated:** 2026-08-27 11:36:13
> **Page Type:** General

> ⚠️ **IMPORTANT:** Rules in this file **override** the Master file (`design-system/MASTER.md`).
> Only deviations from the Master are documented here. For all other rules, refer to the Master.

---

## Page-Specific Rules

### Layout Overrides

- **Max Width:** 1200px (standard)
- **Layout:** Full-width sections, centered content
- **Sections:** Full-screen interactive element > Guided product tour > Key benefits revealed > CTA after completion

### Spacing Overrides

- No overrides — use Master spacing

### Typography Overrides

- No overrides — use Master typography

### Color Overrides

- **Strategy:** Immersive experience colors. Dark background for focus. Highlight interactive elements.

### Component Overrides

- Avoid: No indication of progress
- Avoid: Auto-play high-resolution loops without pause or captions
- Avoid: Default keyboard for all inputs

---

## Page-Specific Components

- No unique components for this page

---

## Recommendations

- Effects: Small hover (50-100ms), loading spinners, success/error state anim, gesture-triggered (swipe/pinch), haptic
- Feedback: Step indicators or progress bar
- Sustainability: Prefer click-to-play; provide pause and captions; stop off-screen and honor reduced motion
- Forms: Use inputmode attribute
- CTA Placement: After interaction complete + Skip option for impatient users
