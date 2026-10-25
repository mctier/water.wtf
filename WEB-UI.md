# Bundled live Web UI

## Open it
1. Load `utilitymod-webui-1.0.0.jar` in the Minecraft 1.21.11 client using your normal mod setup. Replace the previous mod JAR; do not load duplicate copies.
2. Open the in-game client menu, select **Misc**, and enable **Web UI**.
3. Your default browser opens a paired dashboard. No Node server, database, backend ZIP, or hosted site is required.
4. Toggle any module in the list. Select a module to edit its settings. Numeric fields and sliders commit on change; Apply buttons also commit explicitly. Text, lists, and ranges use Apply.
5. Disable Web UI in the game to stop the server and revoke the browser session. To reopen a closed browser, toggle Web UI off and on.

## Synchronization
- Lists every ModuleManager module and every AnubisBridge module. Uses existing setters and lifecycle methods rather than maintaining a second configuration.
- Browser writes apply on the Minecraft thread. The browser fetches current state every 250 ms while connected and retries every 1.5 seconds after disconnection. Network or game-thread stalls can increase latency.
- Per-module revisions reject stale changes instead of silently overwriting newer game/tab edits. Failed setting edits stay visible. Select the module again to discard the draft, or retry after reviewing the current state.
- Includes booleans, integers/floats, ranges, modes/multi-selects, colors, key codes, hotbar slots, registry IDs, text/lists, and nested input-simulation controls. Inactive dependent settings are shown disabled.
- Uses the existing GuiConfig save boundary. Existing persistence semantics remain unchanged (not every legacy custom/nested setting is persisted by that serializer).
- Server binds only to 127.0.0.1 on a free port. Each enable creates a fresh random pairing token; it travels in a URL fragment, then sessionStorage and the Authorization header. Requests are host/origin checked and size bounded. No telemetry or cloud service.
- Private Anubis values are write-only. ExitGate and PanicPay require explicit enable confirmation.

## References examined
- https://github.com/Skid-Technology/Cobble-Recode — configuration architecture reference.
- https://github.com/Skid-Technology/Cobble-Client — module snapshot and configuration-sync reference.
- The supplied cobble-backend.zip contains twenty SQL backup parts, not an application backend. No database contents were imported. This implementation is independent; it does not contact the references' services or reuse their credentials/code.

## Verification
Run `powershell -ExecutionPolicy Bypass -File tools/test-web-ui.ps1` from the checkout. It compiles the real HTTP server and schema boundary and tests them against inert module fixtures. `--preview` serves the same dashboard with clearly labeled fixture modules for browser QA, not Minecraft.

The build and fixture/browser tests do not establish end-to-end behavior inside a running Minecraft instance. See artifacts/web-ui-20261017/VERIFICATION.txt for exact commands/results and rollback instructions.
