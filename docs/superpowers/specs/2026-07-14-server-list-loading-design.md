# Server List Loading Design

## Goal

Grounds Connect must replace stale server rows with a Minecraft-native loading indicator whenever it loads projects or servers. This applies when the screen first opens, when the selected project changes, when the user refreshes the list, and when the platform recovers and triggers a reload.

## User Experience

The project selector, search field, refresh button, logout button, and back navigation remain visible while data loads. The server list area is cleared immediately and replaced by Minecraft 26.2's `LoadingDotsWidget`, centered in the same content region.

The widget displays the localized status for the active operation:

- `grounds_connect.status.loadingProjects` while projects load
- `grounds_connect.status.loadingServers` while servers load

The server-related actions `Join`, `Logs`, `Retry`, `Rollback`, and `NATS` are disabled while loading. No old server remains visible or interactive behind the loader.

## State and Data Flow

`GroundsServersScreen` owns an explicit server-content loading state and applies it consistently through one UI-state method.

Starting a load performs these steps before dispatching the asynchronous request:

1. Clear the server model and the selected server.
2. Hide the server list.
3. Show the loading widget with the operation-specific message.
4. Disable all server-related actions.

Only a response whose project ID matches `currentProjectId` may end the server-loading state. Existing guards continue to ignore results and errors from previously selected projects.

On a successful response with servers, the screen hides the loader, shows the populated list, and enables the server-related actions. On a successful empty response, it hides the loader, keeps the list empty, shows the existing no-servers message, and keeps the actions disabled.

Project loading uses the same loader region. After projects load, the rebuilt screen advances into server loading for the selected project.

## Error Handling

If project or server loading fails, the loader disappears and the existing localized error message appears in the list area. The list remains empty and server-related actions remain disabled.

A stale success or error callback cannot change the loader, list, message, or action state for the currently selected project.

## Components

- `GroundsServersScreen` coordinates loading transitions, widget visibility, and action availability.
- Minecraft's existing `LoadingDotsWidget` provides the animated, native-looking indicator and narration-compatible widget behavior.
- `GroundsServerList` remains responsible only for displaying server entries; the loader is not represented as a selectable list row.
- `ServerDataLoader` keeps its existing asynchronous callback and stale-result boundaries.

## Testing and Verification

A small Minecraft-independent state test covers these transitions:

- Loading hides content and disables server actions.
- Successful content loading shows content and enables server actions.
- Empty and failed loading stop the loader while leaving content and actions disabled.

The implementation must also preserve the current stale-project callback guards.

Run the repository's full Gradle verification:

```text
./gradlew test
./gradlew spotlessApply
./gradlew build
```

Verify visually in the client:

1. Open Grounds Connect for the first time.
2. Switch projects while the old project has visible servers.
3. Refresh the current project.
4. Load an empty project or exercise a server-load failure.

Acceptance requires that the Vanilla loading animation stays centered in the server-list region and that stale server rows are never visible or interactive during a load.
