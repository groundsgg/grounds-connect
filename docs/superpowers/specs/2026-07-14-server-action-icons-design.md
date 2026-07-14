# Server Action Icons Design

## Goal

Replace the undersized Unicode glyphs used by the Grounds server screen's refresh and logout buttons with crisp icons that match Minecraft's vanilla UI.

## Design

- Keep both icon-only buttons at the vanilla-compatible size of 20 by 20 pixels.
- Add dedicated 12 by 12 pixel-art sprites for refresh and logout under the Grounds Connect GUI sprite namespace.
- Render both controls with Minecraft's `SpriteIconButton` so the icons are centered and the normal vanilla button states remain intact.
- Preserve the existing translated hover tooltips and narration text.
- Use the standard button background for normal, hovered, focused, and disabled states; the icon sprites do not need separate state variants.

## Scope

This change only affects the refresh and logout controls in `GroundsServersScreen`. Project selection, NATS, bottom actions, loading behavior, and action semantics remain unchanged.

## Verification

- Add focused coverage for the icon identifiers and dimensions where practical without coupling tests to Minecraft rendering internals.
- Run the full Gradle test, formatting, and build sequence.
- Launch the test client and visually verify that both icons are centered, readable, and consistent with vanilla controls at the current GUI scale.
