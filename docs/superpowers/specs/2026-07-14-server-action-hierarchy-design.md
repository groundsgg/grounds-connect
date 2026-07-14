# Server Action Hierarchy Design

## Context

The Grounds server screen currently presents project navigation, server connection, deployment
operations, and diagnostics as equally prominent buttons. In particular, the generic `Retry` label
is easy to confuse with refreshing the server list even though it retries the selected server's
latest build.

The screen should prioritize joining a server and leaving the screen while keeping project tools
and deployment operations available in locations that reflect their scope.

## Goals

- Make `Join` the first and most prominent action in the bottom row.
- Separate project actions from selected-server actions.
- Move deployment and diagnostic actions to a dedicated management screen.
- Make icon-only project controls understandable through tooltips.
- Preserve the selected server and search state when returning from management.
- Prevent unavailable or unauthorized actions from appearing usable.

## Main Server Screen

The top row contains, from left to right:

1. Project selector
2. Refresh icon button
3. `NATS` button
4. Logout icon button

Refresh reloads the selected project's servers. NATS remains a labeled project-level action because
it is available independently of server rows. Refresh and Logout use icon-only buttons and expose
localized Minecraft-style hover tooltips describing their actions.

The bottom row contains, from left to right:

1. `Join`
2. `Manage...`
3. `Back`

Join and Manage require a selected server. Both are disabled while server data is loading or when
no server is selected. Back remains available. This replaces the current bottom-row Retry,
Rollback, NATS, Join, Logs, and Back arrangement.

## Server Management Screen

Selecting `Manage...` opens a dedicated screen for the selected server. The screen receives an
immutable server name and project ID when it opens, avoiding a dependency on a selection that may
change while the screen is active.

The screen shows:

- title: `<server name> management`
- current project as secondary context
- `Open logs`
- `Retry build`
- `Rollback...`
- `Back`

The wording `Retry build` makes the operation distinct from refreshing the server list. `Open logs`
opens the existing log screen. `Rollback...` opens the existing rollback picker. Back restores the
same Grounds server screen instance so its project, selected server, and search text remain intact.

## Availability and Feedback

- Retry Build is disabled while its request is running, preventing duplicate retries.
- Retry success and failure feedback is rendered on the management screen.
- Rollback remains visible but disabled for users without the `owner` or `editor` role.
- Hovering a disabled Rollback explains that the current project role lacks permission.
- If the selected project or server context is unavailable before the management screen opens, the
  Manage button remains disabled.

## Component Boundaries

- `GroundsServersScreen` owns project controls, server selection, Join, Manage, and Back.
- A dedicated server management screen owns the Logs, Retry Build, and Rollback actions and their
  action-specific status.
- Existing action services continue to perform deployment requests; the screens only coordinate
  availability, navigation, and feedback.
- The existing log and rollback screens remain unchanged except for navigation wiring if required.

## Testing

Automated coverage verifies:

- the main-screen action order and grouping;
- Join and Manage availability for loading, empty, unselected, and selected states;
- project-level Refresh and NATS availability remains independent of server selection;
- management-screen action order;
- Rollback role gating and its explanatory tooltip;
- Retry Build disables itself until the asynchronous request completes;
- Retry success and failure restore the button and display the correct status;
- returning from management preserves server selection and search text.

## Out of Scope

- Changing deployment APIs or retry semantics
- Redesigning the existing log or rollback screens
- Adding more deployment operations
- Replacing NATS with an icon-only control
