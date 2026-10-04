---
name: cee-computer-craft-apps
description: How to build apps (Lua programs with GUIs) for in-game computers - CC:Tweaked computers and the C:EE addon's Electric Computer - in the user's NeoForge 1.21.1 instances, including talking to the user's real PC over localhost, playing sounds on Electric Speakers, Portuguese/Latin-1 text, and in-game Claude chat. Built from the Folio app (C:\JavaProjects\Folio), whose code is the reference implementation. Load when asked to write, extend or debug any program that runs on a ComputerCraft/CC:Tweaked or CEE computer, a companion PC bridge for one, or a headless test of one.
---

# Apps for CC:Tweaked / CEE computers

Everything below was verified against real source (CC:Tweaked `z_reference_mods/CC-Tweaked-mc-1.21.y`, the extracted CEE addon jar) or by running code, unless marked *unverified*.

## Where things are

- **Reference app: Folio** - `C:\JavaProjects\Folio\folio.lua` (~2,700 lines: GUI kit, file manager, editor, Claude chat, splash, sounds) + `folio_bridge.py` (PC companion) + `start_folio_bridge.bat`. Reuse its code instead of rewriting: `fit/trim/wrap/segments`, `box/ask/form/prompt/pick/dropdown`, `fieldKey/fieldInsert`, `toCC/toPC`, `findSpeakers/sound`, `splash`, `nextEvent`.
- **Headless test rig**: `C:\JavaProjects\Folio\tests\` - `mock_cc.lua` (generic CC mock), `harness_folio.py` (scripted run + screen dumps), `fake_api.py` (fake Anthropic Messages/Models API), `fake_claude.py` (fake `claude` CLI incl. `auth status/login/logout`).
- **Deploy**: copy the program into `E:\Minecraft Neoforge Builds\1.21.1 Testing\config\<app>\` next to its bridge; in game `wget http://127.0.0.1:<port>/<app>.lua <app>.lua` (served by the bridge; `-f` to overwrite).
- **Sources**: CC rom Lua at `z_reference_mods/CC-Tweaked-mc-1.21.y/projects/core/src/main/resources/data/computercraft/lua/rom/` (e.g. `apis/http/http.lua`, `apis/textutils.lua`); CC Java at `projects/core/src/main/java/dan200/computercraft/core/`. CEE addon (jar only, no source): `z_reference_mods/computercraft_cee_addon-1.0.0+04ab359/`; its full Lua API index is `data/computercraft_cee_addon/lua/cee/help.lua` (in game: `cee/help`, `cee/help <method>`).
- Installed: CC:Tweaked 1.120.2 (`cc-tweaked-1.21.1-forge-1.120.2.jar`), `computercraft_cee_addon-1.0.0+04ab359.jar`.

## Screen, input and events

- Advanced / CEE Electric Computer terminal: **51x19**, 16 colours. `help.lua` wraps at 51 for this reason. Always call `term.getSize()` and handle `term_resize`.
- **Double buffer**: `buf = window.create(term.current(), 1, 1, W, H, false)`; each frame `buf.setVisible(false)` -> draw -> `buf.setVisible(true)`. Set the cursor pos/blink on `buf` *before* making it visible.
- **Custom palette**: call `setPaletteColour` on the window *and* the native term - a window pushes its own palette to the parent on redraw. Restore on exit with `native.setPaletteColour(c, term.nativePaletteColour(c))`. Folio's blue/white/cream theme repurposes slots (`colours.orange` = cream, `colours.yellow` = light cream, `colours.magenta` = dark cream, `colours.cyan` = bright navy). Use British `colours.grey/lightGrey` (or `colors.gray`).
- Run `main` inside `pcall`; `os.pullEvent` raises `"Terminated"` on Ctrl+T. Restore palette, clear, then `printError` unless terminated.
- **Keys**: holding Ctrl+T / Ctrl+R / Ctrl+S ~1 s terminates / reboots / shuts down, so tapping Ctrl+S as a save shortcut is fine but warn users. Esc closes the terminal GUI and never reaches the program - every dialog needs a mouse Cancel and a keyboard route (Folio: Backspace cancels lists, Tab/arrows move focus, Enter activates).
- **Ctrl tracking**: track `leftCtrl/rightCtrl` key/key_up yourself. On Windows **AltGr arrives as Ctrl+RightAlt** - treat Ctrl as held only when RightAlt isn't, or characters like `@ { } EUR` get swallowed. Reset the ctrl flag after any action that may have eaten the key_up.
- **Paste (Ctrl+V) arrives as a `paste` event while Ctrl is still held** - accept `paste` regardless of the ctrl flag (Folio had paste silently broken everywhere until this was fixed). Paste is first line only, max 512 chars, already Latin-1.
- Mouse: `mouse_click(btn, x, y)`, `mouse_drag`, `mouse_scroll(dir -1 up / 1 down, x, y)`, `mouse_up`. *Unverified*: whether the CEE Electric Computer's screen takes clicks without a Trackball Mouse block - give every feature a keyboard shortcut.
- **CEE Trackball Mouse pointer**: the addon draws a 3-pixel square in hard-coded pure white (`CEEMouseScreens.CURSOR_COLOUR = -1`, not tied to the palette), and pointer movement never reaches Lua (only click/up/drag/scroll events), so a program can neither recolour it nor draw its own. **Never use pure `#FFFFFF` for a background**; tint every near-white slightly so the pointer stays visible (Folio: paper `EDF1F7`, light cream `F6EEDC`). Keep the fix inside the program: apps must run on plain CC:Tweaked + the addon, with no helper mod (the user rejected patching the pointer through CIO for this reason).
- **One event pump**: route every loop (main, dialogs, splash) through a single `nextEvent()` that tracks modifiers, handles resize and hands background events (streams, timers) to their owner. Otherwise a dialog swallows them.
- Timers: `local id = os.startTimer(s)`, then match the `timer` event's id. Use timers for animation; `sleep()` eats events.
- **Every C:EE Lua call is a main-thread call** (`@LuaFunction(mainThread = true)` on all of `cee.*` and on the radio modem's methods - verified in the jar). Each one costs about a tick, and while it waits the calling coroutine DROPS every other event: timers, clicks, keys, `cee_radio_message`. Polling a few values twice a second froze Radiotronic 1.0's splash and lost its clicks and pongs. Rule: the UI coroutine makes no C:EE calls. Run polling in its own coroutine and hardware actions (sends, setters, speaker notes) as jobs in a worker coroutine, all under `parallel.waitForAny(ui, poller, worker)` (Radiotronic 1.1 is the reference). Use flags as well as events for cross-coroutine requests, because a coroutine that is mid-call misses events. Animations should run off `os.clock()` with a hard time cap, and re-arm their timer if it goes quiet.
- Plain CC calls such as `redstone.*`, `fs.*` and the printer's `getInkLevel` are not main-thread calls. The printer's `newPage` and `endPage` are.

## Text encoding (Portuguese and friends)

- The CC charset is **Latin-1**: the font shows bytes 160-255 as Latin-1, typing `a-acute` gives the single byte 0xE1 (`StringUtil.unicodeToTerminal`), paste is converted the same way, and Java<->Lua strings are Latin-1 with `?` for anything above 255 (`LuaValues.encode`).
- Files on the PC are usually UTF-8, so **convert at every PC boundary**: `toCC` (PC -> computer) and `toPC` (computer -> PC) in folio.lua.
  - `toCC` decodes UTF-8 leniently: invalid bytes are taken as Windows-1252 or Latin-1. It folds curly quotes, dashes, `...`, arrows and bullets to CC glyphs or ASCII, turns everything else into `?`, and counts the losses so you can tell the user.
  - `toPC` skips text that is already valid UTF-8.
- `textutils.unserializeJSON` turns `\uXXXX` into UTF-8 **one escape at a time**, so an emoji becomes two encoded surrogate halves (CESU). A strict `utf8.len` check rejects the whole string, which is why `toCC` has its own decoder. `serializeJSON(t, { unicode_strings = true })` treats strings as UTF-8; `pcall` it and fall back.
- Keep source files ASCII-only: a literal accented character in `.lua` source is UTF-8 bytes, not Latin-1. Use `\225`-style escapes, or describe characters in words, e.g. in prompts.
- HTTP headers: percent-encode non-ASCII values (`textutils.urlEncode(toPC(x))`), and have the server `unquote` them.

## Files

- Open with `"rb"` / `"wb"`; since 1.109 handles are raw bytes. Wrap `write`/`close` in `pcall` (out of space). `fs.getFreeSpace` may return the string `"unlimited"`.
- Keep app data under one root (Folio: `/folio/docs`, settings in `/folio/settings.lua` via `textutils.serialize`). Load settings key-by-key against defaults with type checks.
- Preserve CRLF when editing PC files: detect `\r\n` on load, rejoin with it on save.

## HTTP and the localhost bridge

- **Server config** (`config/computercraft-server.toml`; the Testing instance already has this): localhost is denied by the default `{ host = "$private", action = "deny" }` rule. Add `{ host = "127.0.0.0/8", action = "allow" }` **above** it - earlier rules override later ones (`AddressRule.apply` merges in order). Loopback only; keep LAN denied.
- **Error strings** worth translating for users: `Domain not permitted` (rules), `Could not connect`, `Timed out`, `Unknown host`.
- **Blocking helpers**: `http.get/post{ url, method, headers, body, timeout }` - the table form supports PUT/DELETE and so on.
  - **They consume every other event while they wait.** Never call them while a stream or animation is running; Folio refuses those actions while Claude is replying.
  - A status >= 400 returns `nil, err, failedResponse`; read `failedResponse` for the body.
- **Non-blocking**: `http.request{ url, headers, timeout }`, then handle `http_success(url, handle)` and `http_failure(url, err, handle)` in the event pump, matching on the exact URL.
- **Why a bridge**: a computer can't see Windows drives (its files live in the world save), so a small stdlib-only Python server does the work. Copy `folio_bridge.py`'s security model:
  - Bind to `127.0.0.1`, and reject any `Host` header other than localhost (stops DNS rebinding).
  - Require a custom `X-Folio-Client` header: a browser can't send it cross-site without a CORS preflight, which the server never approves.
  - Optionally require a shared token, compared with `hmac.compare_digest`.
  - Allow only paths inside `allowed_roots` (realpath + normcase + commonpath), and only text file types from an extension allowlist.
  - Refuse `.`/`..`, `:`, control characters and Windows reserved names (`CON`, `NUL`, ...) in names; write atomically (temp file + `os.replace`); cap body size; return 409 unless `overwrite=1`.
  - **Mark the bridge's own config, secrets and work folders private** (`Bridge.private`): refused even inside an allowed root, and hidden from listings.
  - Default roots: `..` when the script sits in `<instance>/config/<app>/`, so the whole config folder is reachable.
- **Streaming to the game** (no websockets needed): `POST /job` returns an id; the game long-polls `GET /poll?job=&since=N` (the server waits up to ~8 s for new text) and re-arms each poll from the `http_success` handler. `POST /cancel` stops it.
- The user's Python: `C:\Python312` needs admin rights for pip, so use `python -m pip install --user <pkg>`. `start_*.bat` runs `python "%~dp0<script>.py"` then `pause`.

## Sound (CEE Electric Speaker / CC speaker)

- The CEE computer has **no Lua call to make sound itself**; its startup, fan and keyboard noises are played automatically by the block. Program sound needs a speaker.
- **Electric Speakers**: `cee.getSpeaker(pin)` for pins `1..cee.getSignalCount()` (9), which returns a combined handle for every speaker on that pin, or nil. Also include plain CC speakers from `peripheral.find("speaker")`. Wrap every call in `pcall`.
- **Calls**: `playNote(instrument, volume 0-3, pitch 0-24)`; `playSound(eventId, volume, pitch)`. Several `playNote`s in one tick play as a chord. Electric Speakers also offer `speakWav/Ogg/PCM`, `stop`, `hasPower`, `getStatus`.
- **Power**: an unpowered speaker (< 60 V) just stays silent. The CEE computer itself needs >= 110 V (`cee.hasPower()`).
- **CEE sound ids** (`computercraft_cee_addon:` + name): `startup`, `fan`, `shutdown`, `random_com_sounds` (computer chatter), `monitor_on`, `monitor_off`, `keyboard_key`, `keyboard_space` (spacebar clunk), `mouse_left`, `mouse_right`, `hq_stream`, `psu_fan`, `memory_startup`, `memory_save`, `memory_recall`, `memory_running`, `relay` (relay click).
- Use a CEE sound id only when `type(cee) == "table"`; otherwise fall back to note blocks (`bit` for retro beeps, `pling`, `hat`, `bass`). Folio's start-up jingle is beep-beep-boop: `bit` at pitches 18, 18, 11, 0.2 s apart, driven by the splash timer.
- **Other CEE devices** are also reached by pin, not side:
  - `cee.getMonitor(pin)` returns an ordinary terminal (`setTextScale` 0.5-5).
  - Data Scribers (chart recorders), signal ports for redstone I/O, Data Hubs, fabric messaging and Power/Memory modules are all in `help.lua`.
  - CEE-specific mouse events use the same names as CC's.

## Claude inside an app

- **ClaudeCraft mod** (optional; the user doesn't have it installed): it adds a global `claude` API.
  - `claude.sendMessage(messagesTable, toolsOrNil, systemOrNil)` returns a request id and then streams `claude_delta(id, text)`, `claude_done(id, stop, inTok, outTok)` and `claude_error(id, msg)`.
  - Also available: `claude.isApiKeyConfigured()`, `claude.getModel()`, `claude.cancelRequest(id)`.
  - Its key is set by an operator in Minecraft chat: `/claudecraft setkey`. Text is Latin-1 both ways.
  - Only its API-key mode is usable: its "channel" (Claude Code) mode makes Claude answer only through ClaudeCraft's own tools.
  - Source: `z_reference_mods/claudecraft-fix-neoforge-compat-1.21.1`.
- **Through the bridge, using the user's own Claude Code** (their Pro plan) - the one legitimate subscription route. Anthropic does not allow third-party apps to offer claude.ai login unless approved, so **never read, copy or reuse Claude Code's OAuth token**. Instead:
  - Run `claude -p --output-format stream-json --verbose --include-partial-messages --tools "" --disallowedTools "mcp__*" --permission-mode dontAsk --append-system-prompt-file <f> [--model m] [--resume <session_id>]`.
  - Pass the **prompt on stdin, never on the command line** (Windows batch-file argument injection).
  - Run it in an empty sandbox folder, and remove `ANTHROPIC_API_KEY` from its environment so the plan is used.
  - Parse `stream_event` lines whose event is `content_block_delta` with a `text_delta`; read `session_id` from `system/init` or `result`; `result` carries `is_error` and `total_cost_usd`.
  - For sign-in, run `claude auth login` in a new console window (`cmd /c start "<title>" cmd /k claude auth login`). `claude auth status` prints JSON and exits 0 when signed in; it includes `loggedIn`, `authMethod: "claude.ai"`, `email`, `subscriptionType`, `orgName`.
  - A turn takes ~10 s (cold start). Show "on your Claude plan", not the `$` estimate, for `claude.ai` logins.
- **The user's Claude Code is the VS Code extension's bundled binary, which is not on PATH**: `%USERPROFILE%\.vscode\extensions\anthropic.claude-code-<ver>-win32-x64\resources\native-binary\claude.exe` (2.1.283 at time of writing; signed in, pro).
  - Discover the newest copy (`folio_bridge.find_installed_claude`); don't hard-code the versioned path.
  - `--help` lists the file variant only as `--append-system-prompt[-file]`, but it works.
- **API key (pay-per-use)**: the official `anthropic` Python SDK in the bridge (1.8.0 installed with `--user`).
  - Stream with `client.messages.stream(...)` and top-level `cache_control={"type": "ephemeral"}`, plus `output_config={"effort": ...}` except on Haiku.
  - On `claude-opus-5` also send `betas=["server-side-fallback-2026-07-01"], fallbacks="default"`.
  - The in-game key flow: paste into a masked field, validate with `models.list(limit=1)`, store at `~/.folio/anthropic_api_key` (outside every game-reachable root), and show only `sk-ant-...last4`.
  - **Load the `claude-api` skill for current model IDs, pricing and parameters.** Don't reuse IDs from this file blindly.
- **System prompt for a 51-column screen**: no Markdown, straight ASCII punctuation, accented Latin letters are fine, no emoji, reply in the player's language, code in ``` fences (so "Keep" can save just the code).
- **Chats as documents**: save readable `=== You ===` / `=== Claude ===` transcripts (escape body lines starting with `===` or `\`). They then work as ordinary files: continue, attach to another chat, open in the editor, export to the PC. Attach files as `<file name="...">...</file>` blocks and collapse them to one line on screen.

## Testing without Minecraft

- `pip install --user lupa`, then load `mock_cc.lua` into `lupa.lua54.LuaRuntime(unpack_returned_tuples=True)` and run the app with a scripted `EVENTS` queue. `PY_SNAP` dumps the 51x19 grid; `ROW()` renders CC glyphs and Latin-1; a background-colour map shows block graphics.
- **Two queues**: `SYS` (http results, timers, mod events) is drained before scripted user `EVENTS`, like real timing. Set `USER_FIRST = true` to press a key mid-animation. `{"char_byte", 225}` types a real Latin-1 byte, and `install_cee_mock()` / `install_claudecraft_mock()` add those globals. `SOUND_LOG` records every speaker call.
- **Real bridge, fake services**: run a real bridge copy on a spare port (18765) in a scratch folder. Point `ANTHROPIC_BASE_URL` at `fake_api.py`, and set `"claude_command": ["python", ".../fake_claude.py"]` and `"login_in_new_window": false`. The fakes log exactly what they received, so assert on request bodies (headers, betas, effort, prompt on stdin, environment without the API key).
- **Gotchas**:
  - Use `lua54.lua_type` (not `lupa.lua_type`) to recognise tables.
  - Python can't decode Latin-1 Lua strings; read them through a Lua helper (`SHOW`).
  - Set `PYTHONIOENCODING=utf-8` for readable output.
  - The `py` launcher dies (exit 109) if `USERPROFILE` is overridden; use `python`, e.g. when faking a home folder so the key file stays out of the real one.
  - Files left by a previous run (export folder, key file) shift scripted dialogs; use fresh folders.
- A real Claude Code smoke test (import `folio_bridge`, call `Claude.run_code` once) costs a little plan usage - say so first.
- **Model the event loop when hardware calls are involved**: Folio's mock returns events from a plain function, so it cannot show dropped events. `C:\JavaProjects\Radiotronic\tests\harness.py` runs the app in a coroutine, drops events that don't match the filter (as CC's BIOS does), makes mock C:EE methods wait a tick for `task_complete`, and has a CC-style `parallel.waitForAny`. It prints `dropped events` and `main-thread calls`. Use it, or copy it, for any app that polls C:EE hardware. It reproduced Radiotronic 1.0's stalled splash exactly.
- Headless tests don't prove in-game behaviour (fonts, sound, real clicks). Say what was only tested headless.
