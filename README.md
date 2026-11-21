# info2ai

[简体中文](README.zh-CN.md)

info2ai is a Fabric mod that lets other programs read live data from a Minecraft Java Edition single-player world. A dashboard can show your coordinates and health, a tracker can record what you craft and find, and a screenshot tool can save the current game view. The mod serves data over HTTP on the same computer.

An AI agent can use the same snapshots, events, and screenshots to follow what you do in the game. When the Director is enabled, the agent can use `POST /director/run` to interact within the mod's limits, for example by placing blocks or playing sounds. Press **F8** to toggle the Director.

## What you can read

| Data | Examples |
| --- | --- |
| Session and world | Save name and seed, difficulty, game mode, dimension, in-game day and time, moon phase, rain and thunder |
| Player | Coordinates, facing, movement, health, hunger, air, armor, experience, status effects, and held items |
| View and interface | The block or entity under the crosshair, the open screen, recent sounds, and on-screen information such as boss bars |
| Inventory | Hotbar and inventory slots, stack counts, equipment, offhand, durability, and enchantments. `full` can also include the ender chest and the item on the inventory cursor. |
| Surroundings | Current biome, light level, whether the sky is visible, nearby entities and their counts, observed containers, and structures already detected near the player. `full` can include dropped items. |
| Generated terrain | A sampled biome and predicted nearby structures with coordinates, distance, and direction. Check `world.static.coverage` for a partial search. Predictions describe generated terrain and exclude later player edits. |
| Progress and history | Advancements, unlocked recipes, selected statistics, recent positions, deaths, dimension changes, and visited structures |
| Events and signals | Events such as crafting, mining ore, opening a chest, taking damage, or changing dimensions. Signals summarize ongoing conditions such as low health or nearby threats. |
| Screenshots | A PNG image of the current game view on request |

## Install and connect

1. Use **Minecraft Java Edition 1.21.1**, **Java 21**, **Fabric Loader 0.20.1 or newer**, and **Fabric API** for 1.21.1.
2. Download `info2ai-0.1.0.jar` and put it in your game instance's `mods` folder. [Download from GitHub Releases](https://github.com/narrastory/info2ai/releases).
3. Launch Minecraft and enter a **single-player world**. The default address is `http://127.0.0.1:27948`. Check the game log for the actual port if the default is busy.
4. Try `GET /health` to check that the mod is running. For game data, read `config/info2ai.token` in the game instance directory and send it in the `Authorization: Bearer <token>` header.

| Request | What it returns |
| --- | --- |
| `GET /state?detail=normal` | The current snapshot as JSON. Use `detail=lite` for a compact summary or `detail=full` for extra fields. |
| `GET /events` | New events as JSON. Pass the previous response's `next_since` as `since` for the next request. |
| `GET /events/history?since=0` | Earlier events still in the history buffer. |
| `GET /screenshot` | PNG bytes that you can save as an image. |
| `GET /sections` | The names you can pass to `sections` to select parts of a snapshot. |

Available fields depend on the detail level and what the game can observe. Check `capabilities` and allow for missing or `null` fields. You can inspect the connection and pause live snapshots and screenshots from the [local status page](http://127.0.0.1:27948/debug/).

The tutorial covers curl, PowerShell, and Python, including how to keep track of new events. [Receiving game data](tutorials/receiving-data.md).

## Build from source

Run `./gradlew build` with JDK 21 (`.\gradlew.bat build` on Windows). The mod JAR is written to `build/libs/`.

This is an unofficial Minecraft mod. It is not affiliated with Mojang or Microsoft.
