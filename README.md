# info2ai

[English](#english) · [简体中文](#简体中文)

## English

info2ai is a Fabric mod for Minecraft 1.21.1 that lets an AI companion on your computer understand what you are doing in the game. You can ask it how much bread you have, whether there are mobs nearby, or what structures might be in the area.

### What it does

- Shares your player status, inventory, nearby mobs and blocks, game events, and screenshots.
- Uses Minecraft's own generation rules to look for nearby structures and biomes. No separate `worldgen` service is required.
- Lets a connected app use the Director to do things like place decorations, give items, or play sounds within set limits. Press **F8** to toggle the Director at any time.

The mod connects the game to a separate companion app, such as mochi-agent, which provides the AI conversation.

### Install and use

1. Use **Minecraft Java Edition 1.21.1**, **Java 21**, and **Fabric Loader 0.20.1 or newer**. Install **Fabric API** for Minecraft 1.21.1.
2. Download `info2ai-0.1.0.jar` from [Releases](https://github.com/narrastory/info2ai/releases) and put it in the game instance's `mods` folder.
3. Start Minecraft, enter a **single-player world**, and start your AI companion app.
4. To check the connection, open <http://127.0.0.1:27948/debug/> and enter the token from `config/info2ai.token` in your game folder. If the port is already in use, check the game log for the port to use.

Connected apps read game data through a local interface. You can pause sharing from the status page. Keep the access token on your computer. Structure and biome predictions describe generated terrain, so they do not account for anything players build later.

### Build from source

Use JDK 21 and run `./gradlew build` (`.\gradlew.bat build` on Windows). The mod JAR is written to `build/libs/`.

This is an unofficial Minecraft mod. It is not affiliated with Mojang or Microsoft.

## 简体中文

info2ai 是一个 Minecraft 1.21.1 Fabric 模组，让同一台电脑上的 AI 陪伴程序知道你在游戏里做什么。比如，你可以问它背包里还有多少面包、附近有没有怪物，或者这片地形里可能有什么结构。

### 它能做什么

- 提供玩家状态、背包、附近的生物和方块、游戏事件与截图。
- 使用游戏自己的生成规则查找附近可能存在的结构和群系，无需启动额外的 `worldgen` 服务。
- 允许已连接的程序通过「导演」功能在限制范围内放置装饰、给予物品或播放音效。按 **F8** 可随时开关导演。

模组负责连接游戏，AI 对话由另外运行的陪伴程序提供，例如大团子（mochi-agent）。

### 安装与使用

1. 使用 **Minecraft Java 版 1.21.1**、**Java 21**、**Fabric Loader 0.20.1 或更新版本**，并安装适用于 1.21.1 的 **Fabric API**。
2. 到 [Releases](https://github.com/narrastory/info2ai/releases) 下载 `info2ai-0.1.0.jar`，放进游戏实例的 `mods` 文件夹。
3. 启动游戏并进入一个**单人存档**，再启动你的 AI 陪伴程序。
4. 可在浏览器打开 <http://127.0.0.1:27948/debug/>，用游戏目录中 `config/info2ai.token` 的内容登录状态页。端口被占用时以游戏日志显示的端口为准。

游戏数据通过本机接口供已连接的程序读取。你可以在状态页暂停分享，访问令牌请留在本机。群系和结构预测基于世界生成规则，不包含玩家后来建造的内容。

### 从源码编译

使用 JDK 21 运行 `./gradlew build`（Windows 下运行 `.\gradlew.bat build`）。模组 JAR 位于 `build/libs/`。

本项目是独立制作的 Minecraft 模组，与 Mojang 或 Microsoft 无关联。
