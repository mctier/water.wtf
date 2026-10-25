# cgui — utilitymod custom GUI toolkit

`com.utilitymod.cgui` — additive toolkit next to the house GUI. No behavior of
existing modules changes; settings only move through the real settings API, so
profiles and the web Live Control stay in sync.

Open with **CTRL+SHIFT+G** (edge-polled, no Fabric keybind registration).

## Hot-reload JSON GUI
`config/utilitymod/cgui/home.json` builds the screen on every open. Delete the
file to get the built-in crystal window. Malformed entries print one chat line
and are skipped — a bad file never crashes the game.

```json
{
  "title": "home",
  "windows": [
    {
      "title": "Belt", "x": 40, "y": 60, "width": 210,
      "widgets": [
        {"type": "label", "text": "belt controls"},
        {"type": "module", "name": "Shard Rig"},
        {"type": "setting", "module": "Shard Rig", "setting": "Place Delay"},
        {"type": "button", "text": "flip belt", "module": "Shard Rig"},
        {"type": "image", "namespace": "utilitymod", "path": "textures/gui/logo.png", "height": 64}
      ]
    }
  ]
}
```

Widget types: `label`, `module` (Enabled toggle + every setting row), `setting`
(one adapter row), `button` (module power flip), `image`.

## Session persistence
Window position, width and collapsed state save per screen title into
`config/utilitymod/cgui_layout.dat` (config codec, same gzip+mask family as the
module config). Restored when the screen rebuilds.

## Building windows in code
```java
CuiScreen s = GuiBuilder.screen("home")
        .window("Belt", 40, 60, 210)
        .toggle("Enabled", () -> m.isEnabled(), m::setEnabled)
        .slider("Delay", 0, 200, 1, m.delay::getValue, v -> m.delay.setValue((int) v), "ms")
        .dropdown("Mode", ... , () -> mode.value(), mode::setValue)
        .field("Tag", "current", tag -> {})
        .progress("Charge", charge::asDouble)
        .scroll(new ScrollPane(...))
        .tabs(tabs)
        .open();
```

## Component map
- `CuiScreen` — host: window list, click-to-front, popup layer, focus bus, fixed draw scale (`s()`, virtual units everywhere).
- `Window` — drag header, `-`/`+` collapse, bottom-right width grip, content-driven height, `onClose`.
- `Pane` / `VStack` / `HStack` — tree + flow layout, reflowed every frame, cycle-proof `add`.
- Widgets: `Label Button Toggle Checkbox Slider TextField Dropdown KeybindButton ScrollPane Tabs ProgressBar ColorPicker Image`.
- `SettingAdapters.of(setting)` → the matching live row (bool/int/float/color/mode/keybind/multi).
- `CuiLoader` / `CuiLayout` — json screens + session store, both fail-closed.

## House laws honored
- All draws through `dev.koifih.client.render.{Draw, Text}`; scrollbar/clip via `Scissor`.
- CharSequence setting labels display through `CloakUtil.heapSafe` (cloak form), never plain.
- No plaintext cheat-signature literals in this package; module labels stay carrier-decoded.
- No per-frame allocation in the draw path; hover uses screens delta law.
