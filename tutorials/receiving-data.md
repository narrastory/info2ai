# Read game data and send commands with info2ai

[简体中文](receiving-data.zh-CN.md)

This guide connects a local program to the game. Use `/state` to see what is true now, `/events` to see what just happened, and `/screenshot` for the current image. Use the Director to [send a command to the game](#5-send-a-command-to-the-game).

## 1. Connect to the game

Install the mod and enter a single-player world. Use the port shown in the game log. The examples use the default port `27948`. `127.0.0.1` means the program reading the data runs on the same computer as Minecraft.

Replace the game directory below with the folder for the instance you are actually playing. A custom launcher or modpack may use its own directory. The mod writes the token to `config/info2ai.token` there on startup. Keep the token out of logs and shared scripts.

### PowerShell on Windows

```powershell
$gameDir = "$env:APPDATA\.minecraft"
$base = 'http://127.0.0.1:27948'
$token = (Get-Content "$gameDir/config/info2ai.token" -Raw).Trim()
$headers = @{ Authorization = "Bearer $token" }

Invoke-RestMethod "$base/health"
$state = Invoke-RestMethod "$base/state?detail=normal&sections=player,inventory,world" -Headers $headers
$state.player | Select-Object pos, health, max_health, food
$state.inventory.hotbar
```

### curl on macOS or Linux

```sh
GAME_DIR='/path/to/your/game/instance'
BASE='http://127.0.0.1:27948'
TOKEN=$(tr -d '\r\n' < "$GAME_DIR/config/info2ai.token")

curl -fsS "$BASE/health"
curl -fsS -H "Authorization: Bearer $TOKEN" \
  "$BASE/state?detail=normal&sections=player,inventory,world"
```

`/health` needs no token. An `in_world` value of `true` means the mod has a world snapshot. If the mod is running but you are still at the title screen, enter a world first.

## 2. Pick the fields you need

| Query | Good for |
| --- | --- |
| `/state?detail=lite` | A small HUD or status widget that needs basic player and world data |
| `/state?detail=normal&sections=player,inventory` | A health and inventory dashboard with actual slot data |
| `/state?detail=normal&sections=world.static` | Predicted structures and the sampled biome. The JSON still uses the `world.static` nesting. |
| `/state?detail=full&sections=player,inventory,surroundings` | More detail such as the ender chest, inventory cursor, nearby drops, and player inputs when those fields are available |
| `/state?detail=normal&sections=surroundings&max_entities=8` | Nearby entities capped at eight entries |

Read `/sections` to check the section names. `sections` selects a comma-separated list and `exclude` removes sections. Use `normal` for general integrations. `lite` may omit fields to stay small. A field can also be missing or `null` if the current game cannot observe it. Keep `session.seed` as a string so large seeds retain their exact value.

`surroundings.biome` describes the area around the player. `world.static.biome` comes from a separate sample used for generated-terrain predictions. Check `capabilities.static_world_ready` before reading predictions. Use `world.static.coverage.truncated_by_budget` to flag partial searches, and treat an empty prediction list as inconclusive.

For a dashboard that refreshes repeatedly, request `if_changed=1`. Save the first reply's `ETag` header and send it as `If-None-Match` on the next request with the same parameters. A `304` means the response has no new JSON to read. It is reasonable to start by polling once per second and adjust to your display's needs.

## 3. Follow new events

1. Read `seq` and `instance_id` from `/health` when you connect. Use that `seq` as your first `since` value to receive new events from that point.
2. Request `/events?since=<cursor>&instance_id=<id>&wait_ms=20000`. Set your HTTP timeout above 20 seconds. The request returns early when events arrive and can return an empty heartbeat.
3. Process the `events` array, then use its response's `next_since` on the next request, including after an empty heartbeat. Each event has a `kind`, `seq`, and timestamp, plus `data` and `pos` when available. For example, filter on `item_crafted` or `dimension_changed` to build a focused activity feed.
4. If `lost` is `true`, your reader fell behind the live buffer. Check `/events/history?since=<last-successful-cursor>` for events still retained. If you get `409 instance_changed` or `409 cursor_ahead`, fetch `/health` again and start a cursor for the current instance.

For events already recorded, begin with `/events/history?since=0&limit=100` and use `next_since` while `has_more` is true. `/events?since=0` starts at the current live position. Either endpoint can take a comma-separated `kinds`, such as `kinds=item_crafted,ore_found`.

The [Python example](receive.py) uses the standard library. It prints one snapshot with `--once`, or prints each new event as a JSON line when you omit that flag.

```sh
python tutorials/receive.py '/path/to/your/game/instance' --once
python tutorials/receive.py '/path/to/your/game/instance' --port 27948
```

Run the commands from the repository root. Press Ctrl+C to stop listening.

## 4. Save a screenshot

Read `capabilities.screenshot` first. When it is `true`, save the `/screenshot` response as a PNG file.

```powershell
Invoke-WebRequest "$base/screenshot?max_width=960" -Headers $headers -OutFile game.png
```

```sh
curl -fsS -H "Authorization: Bearer $TOKEN" \
  "$BASE/screenshot?max_width=960" -o game.png
```

Screenshots require an active game client. Use the returned `X-Info2ai-Seq` and `X-Info2ai-RealTime` headers if you need to associate the image with a snapshot and its capture time. The image is requested separately, so check those headers if timing matters.

## 5. Send a command to the game

Enter a single-player world and leave the game unpaused. Press **F8** if you need to turn the Director on. With the same Bearer token from section 1, call `GET /director/status` and check that `enabled` and `available` are both `true`.

Send one Minecraft command as the `command` string in a JSON request. A leading `/` is optional. Start with `POST /director/check`, which checks the command without running it. After it returns `status: "ok"`, send the same command to `POST /director/run` with an `intent` describing why you are running it. The intent must be non-blank and at most 120 characters. The examples play a chime for the player.

### PowerShell

```powershell
Invoke-RestMethod "$base/director/status" -Headers $headers
$command = 'playsound minecraft:block.note_block.chime record @p ~ ~ ~ 1 1.2'
$checked = Invoke-RestMethod "$base/director/check" -Method Post -Headers $headers `
  -ContentType 'application/json; charset=utf-8' -Body (@{ command = $command } | ConvertTo-Json -Compress)
$checked

if ($checked.status -eq 'ok') {
    Invoke-RestMethod "$base/director/run" -Method Post -Headers $headers `
      -ContentType 'application/json; charset=utf-8' `
      -Body (@{ command = $command; intent = 'Play a test chime' } | ConvertTo-Json -Compress)
}
```

### curl

```sh
curl -fsS -H "Authorization: Bearer $TOKEN" "$BASE/director/status"
curl -fsS -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  --data '{"command":"playsound minecraft:block.note_block.chime record @p ~ ~ ~ 1 1.2"}' \
  "$BASE/director/check"
```

Once the check returns `status: "ok"`, run it with an intent.

```sh
curl -fsS -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  --data '{"command":"playsound minecraft:block.note_block.chime record @p ~ ~ ~ 1 1.2","intent":"Play a test chime"}' \
  "$BASE/director/run"
```

### Read the result

Inspect `status` and `success` in the JSON. A successful request to play the chime returns `status: "executed"` and `success: true`. HTTP `200` can also carry a rejected or failed command.

| Result | What to check |
| --- | --- |
| `parse_error` | Read `parse_error.message` and fix the command syntax. |
| `rejected` | Read `policy.reason` and adjust the command to fit the mod's rules. |
| `failed` or `rolled_back` | Check `errors` and `guard.violations` for details. |
| `timeout` | Check `GET /director/actions` before retrying. A submitted command can still finish after the HTTP timeout. |
| `403 director_disabled` or `409 game_paused` | Enable the Director with **F8**, or return to the game from the pause menu. |
| `429 rate_limited` | Wait for the number of seconds in `reset_in_s`. |

For supported commands and examples, request `GET /director/help` or `GET /director/help?command=playsound` with the token. If a run reports `undoable: true`, send `POST /director/undo` with `{"action_id": 42}`, replacing `42` with the ID returned by the run. This requests an undo of the recorded changes.

## Troubleshooting

| Result | What to check |
| --- | --- |
| Connection refused | Make sure Minecraft is running and use the actual port in the game log. |
| `401 unauthorized` | Read `config/info2ai.token` from the instance that is running and send the full Bearer header. |
| `503 not_in_world` | Enter a single-player world and wait for the first state snapshot. |
| `503 paused` | Resume export from the status page at `/debug/`. |
| `503 screenshot_disabled` or `no_client` | Check that screenshot capture is available in the active game client. |
| `429 too_many_waiters` | Reuse one outstanding long-poll request per reader. Retry after a short delay. |
