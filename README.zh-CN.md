# info2ai

[English](README.md)

info2ai 是一个 Fabric 模组，让其他程序读取 Minecraft Java 版单人存档中的实时数据。你可以用它做一个显示坐标和血量的面板、记录合成和采矿的工具，或者定期保存游戏截图。模组通过本机 HTTP 接口提供数据。

AI 智能体也能读取这些快照、事件和截图，了解你在游戏里做了什么。导演功能开启时，它还可以通过 `POST /director/run` 在模组限制内放置方块、播放音效等。按 **F8** 可开关导演。

## 可以接收哪些信息

| 数据 | 示例 |
| --- | --- |
| 会话与世界 | 存档名、种子、难度、游戏模式、维度、游戏日期和时间、月相、下雨与雷暴 |
| 玩家状态 | 坐标、朝向、移动状态、生命值、饥饿值、氧气、护甲、经验、状态效果和手持物品 |
| 视角与界面 | 准星对着的方块或实体、当前打开的界面、近期声音、Boss 血条等界面信息 |
| 物品栏 | 快捷栏和背包槽位、堆叠数量、装备、副手、耐久和附魔。`full` 还可以提供末影箱与物品栏光标所拿物品。 |
| 周围环境 | 当前群系、光照、能否看到天空、附近实体及数量、已观测到的容器和附近已探测到的结构。`full` 可以提供掉落物。 |
| 世界生成预测 | 采样点的群系，以及预测结构的坐标、距离和方向。可通过 `world.static.coverage` 判断搜索是否截断。预测依据世界生成规则，不包含玩家后续改动。 |
| 进度与历史 | 已解锁的进度和配方、部分统计数据、近期坐标、死亡和维度切换记录、访问过的结构 |
| 事件与信号 | 合成物品、挖到矿石、打开箱子、受伤或切换维度等事件，以及低血量和附近威胁等持续状态 |
| 截图 | 按需获取当前游戏画面的 PNG 图片 |

## 安装与连接

1. 使用 **Minecraft Java 版 1.21.1**、**Java 21**、**Fabric Loader 0.20.1 或更新版本**，并安装适用于 1.21.1 的 **Fabric API**。
2. 下载 `info2ai-0.1.0.jar`，放进游戏实例的 `mods` 文件夹。[从 GitHub Releases 下载](https://github.com/narrastory/info2ai/releases)。
3. 启动游戏并进入一个**单人存档**。默认地址为 `http://127.0.0.1:27948`。如果端口被占用，以游戏日志显示的实际端口为准。
4. 用 `GET /health` 检查模组是否在运行。读取游戏数据时，从游戏实例目录下的 `config/info2ai.token` 读取令牌，并发送 `Authorization: Bearer <令牌>` 请求头。

| 请求 | 返回内容 |
| --- | --- |
| `GET /state?detail=normal` | 当前状态快照，格式为 JSON。`detail=lite` 返回精简摘要，`detail=full` 返回更多字段。 |
| `GET /events` | 新事件，格式为 JSON。将上次响应的 `next_since` 作为下次请求的 `since`。 |
| `GET /events/history?since=0` | 历史缓冲区里仍保留的事件。 |
| `GET /screenshot` | 可保存成图片的 PNG 数据。 |
| `GET /sections` | 可用于 `sections` 参数的快照分区名。 |

能读到的字段随详细程度和游戏状态变化。可以查看 `capabilities`，并处理字段缺失或为 `null` 的情况。你可以在[本机状态页](http://127.0.0.1:27948/debug/)查看连接情况，或暂停实时快照和截图。

教程提供 curl、PowerShell 和 Python 示例，还说明了如何连续接收新事件。[如何接收游戏数据](tutorials/receiving-data.zh-CN.md)。

## 从源码编译

使用 JDK 21 运行 `./gradlew build`（Windows 下运行 `.\gradlew.bat build`）。模组 JAR 位于 `build/libs/`。

这是独立制作的 Minecraft 模组，与 Mojang 或 Microsoft 无关联。
