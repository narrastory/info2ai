# 接收 info2ai 的游戏数据

[English](receiving-data.md)

本教程说明如何让本机程序接收数据。用 `/state` 了解现在的状态，用 `/events` 接收刚发生的事件，用 `/screenshot` 获取游戏画面。

## 1. 连接到游戏

安装模组并进入单人存档。端口以游戏日志为准，下面使用默认端口 `27948`。`127.0.0.1` 表示读取数据的程序与 Minecraft 运行在同一台电脑上。

把下面的游戏目录替换成你正在玩的实例目录。第三方启动器或整合包往往有自己的目录。模组启动时会将令牌写入该目录的 `config/info2ai.token`。请把令牌留在本机，避免写进日志或公开脚本。

### Windows PowerShell

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

### macOS 或 Linux 的 curl

```sh
GAME_DIR='/path/to/your/game/instance'
BASE='http://127.0.0.1:27948'
TOKEN=$(tr -d '\r\n' < "$GAME_DIR/config/info2ai.token")

curl -fsS "$BASE/health"
curl -fsS -H "Authorization: Bearer $TOKEN" \
  "$BASE/state?detail=normal&sections=player,inventory,world"
```

`/health` 无需令牌。`in_world` 为 `true` 表示模组已经有世界快照。如果游戏还在标题界面，先进入存档。

## 2. 只读取需要的字段

| 查询 | 适用场景 |
| --- | --- |
| `/state?detail=lite` | 只需要基本玩家和世界信息的小型状态栏 |
| `/state?detail=normal&sections=player,inventory` | 需要实际槽位数据的血量与物品栏面板 |
| `/state?detail=normal&sections=world.static` | 读取预测结构和采样群系，返回的 JSON 仍按 `world.static` 嵌套 |
| `/state?detail=full&sections=player,inventory,surroundings` | 需要末影箱、物品栏光标、附近掉落物和玩家输入等可用字段 |
| `/state?detail=normal&sections=surroundings&max_entities=8` | 读取周围环境，将实体列表限制为最多 8 项 |

用 `/sections` 查询可选分区。`sections` 接受逗号分隔的分区名，`exclude` 可以排除指定分区。一般接入建议从 `normal` 开始。`lite` 为缩小体积会省略部分字段。游戏当前无法观测的数据也可能缺失或为 `null`。保留 `session.seed` 的字符串形式，避免大整数种子丢失精度。

`surroundings.biome` 描述玩家周围的实际群系。`world.static.biome` 来自生成预测所使用的独立采样点。读取预测前先看 `capabilities.static_world_ready`。用 `world.static.coverage.truncated_by_budget` 判断搜索是否因预算截断。预测列表为空时，不能据此判断附近没有结构。

反复刷新的面板可以使用 `if_changed=1`。保存第一次响应的 `ETag`，下次用相同参数发请求时加上 `If-None-Match`。收到 `304` 就可以保留已有内容，无需解析新的 JSON。可以先按每秒一次轮询，再按显示需求调整。

## 3. 持续接收新事件

1. 连接时从 `/health` 读取 `seq` 和 `instance_id`。将这次的 `seq` 作为第一次请求的 `since`，就能从当前时点开始接收新事件。
2. 请求 `/events?since=<游标>&instance_id=<实例ID>&wait_ms=20000`。客户端的请求超时要大于 20 秒。事件到达后接口可以提前返回，也可能返回只有心跳的空列表。
3. 处理 `events` 数组，再把响应的 `next_since` 用于下一次请求。空心跳也要照样更新游标。每条事件有 `kind`、`seq` 和时间戳，有对应数据时还会提供 `data` 和 `pos`。比如只关注 `item_crafted` 或 `dimension_changed`，就能做一份更聚焦的活动记录。
4. `lost=true` 表示实时缓冲区已淘汰部分尚未读取的事件。可从 `/events/history?since=<上次成功的游标>` 尝试找回仍保留的记录。遇到 `409 instance_changed` 或 `409 cursor_ahead`，重新请求 `/health`，为当前实例开启新游标。

如果想先读取已发生的事件，从 `/events/history?since=0&limit=100` 开始。只要 `has_more` 为 `true`，就用 `next_since` 继续翻页。`/events?since=0` 会从当前实时位置开始。这两个事件接口都接受逗号分隔的 `kinds`，例如 `kinds=item_crafted,ore_found`。

[Python 示例](receive.py) 只用标准库。加 `--once` 打印一次快照，省略此选项则每收到一条新事件就输出一行 JSON。

```sh
python tutorials/receive.py '/path/to/your/game/instance' --once
python tutorials/receive.py '/path/to/your/game/instance' --port 27948
```

在仓库根目录运行命令。按 Ctrl+C 停止接收。

## 4. 保存一张截图

先查看 `capabilities.screenshot`。它为 `true` 时，可以把 `/screenshot` 响应保存为 PNG 文件。

```powershell
Invoke-WebRequest "$base/screenshot?max_width=960" -Headers $headers -OutFile game.png
```

```sh
curl -fsS -H "Authorization: Bearer $TOKEN" \
  "$BASE/screenshot?max_width=960" -o game.png
```

截图需要正在运行的游戏客户端。如果需要将截图与快照或采集时间关联，可以读取 `X-Info2ai-Seq` 和 `X-Info2ai-RealTime` 响应头。截图是单独请求的，对时序有要求时以响应头为准。

## 常见问题

| 结果 | 检查方法 |
| --- | --- |
| 连接被拒绝 | 确认 Minecraft 在运行，并使用游戏日志里的实际端口。 |
| `401 unauthorized` | 从当前运行的游戏实例读取 `config/info2ai.token`，并发送完整的 Bearer 请求头。 |
| `503 not_in_world` | 进入单人存档并等待首个快照。 |
| `503 paused` | 在 `/debug/` 状态页恢复导出。 |
| `503 screenshot_disabled` 或 `no_client` | 检查当前游戏客户端是否可以截图。 |
| `429 too_many_waiters` | 每个读取程序只保留一个正在等待的长轮询，稍等再重试。 |
