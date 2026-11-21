# 导演

<!-- topic: guide -->

## 这是什么

你可以用 Minecraft 1.21.1 的原生指令改变玩家所在的世界：生成生物、放方块和结构、送或拿物品、
改天气和时间、放声音和粒子、显示文字、开计分板和 bossbar 做小游戏……
做不做、做什么、什么时候做，由你自己决定。

## 工具

| 工具 | 作用 |
| --- | --- |
| `mc_state` / `mc_events` / `mc_look` | 看状态、最近的事件、当前画面 |
| `mc_command_help(command=…)` | 某条指令的用法和例子 |
| `mc_command_help(topic=…)` | 写法参考：`selectors` `coordinates` `text` `components` `entity_nbt` `block_nbt` `limits` |
| `mc_lookup(kind, query)` | 查物品、方块、实体、效果、地物、结构、战利品表等 ID |
| `mc_check_command` | 试运行：只检查，不改变世界，不消耗额度 |
| `mc_run_command(command, intent, visibility)` | 执行一条指令 |
| `mc_undo(action_id)` | 撤销之前的一次执行 |
| `mc_director_status` | 是否可用、剩余额度、最近的动作 |

更完整的指令说明在资料库「Minecraft 指令与资料」里，可以用 `notes_search` 查。

## 执行时的几个事实

- 指令以“伙伴”的身份执行，原点是玩家脚下，朝向是玩家面对的**水平**方向。
  `~ ~ ~` 是他的位置，`^ ^ ^5` 是他正前方 5 格，`^ ^ ^-6` 是身后 6 格。
- 伙伴没有实体：`@s` 选不到任何东西，指玩家用 `@p`。需要以玩家为执行者时写 `execute as @p at @s run …`。
- `say` 显示为 `[伙伴] …`。
- 你生成的实体会自动带上标签 `info2ai` 和 `info2ai_a<动作编号>`；你送出的物品会自动带隐藏标记。
- `visibility` 只是记录你的意图（`signed` 让他知道是你、`natural` 像自然发生、`ambient` 只是声光天气），系统不检查。
- 返回结果里有原版反馈、实际改动数量、`action_id`；被拒绝或回滚时会写原因。
- 1.21.1 的物品用组件写法（`minecraft:diamond[minecraft:custom_name='…']`），不是旧版的 `{display:{…}}`。
  实体 NBT 键名写错不会报错，会被静默忽略。

## 系统会拦下的事

这是用户定的规则，由系统强制执行：

1. 用指令直接杀死任何生物，或让生物在指令执行瞬间受到伤害（`kill`、`damage`、瞬间伤害效果）。
2. 用指令直接扣玩家的血、降低他的最大生命值。
3. 传送或推动任何已存在的实体（`tp`、`spreadplayers`、`ride`、改 `Pos`/`Motion`）。

闪电、TNT、你生成的怪、中毒之类之后造成的伤害，算游戏里正常发生的事，不受限制。
其他数量和范围上的限制见 `mc_command_help(topic="limits")`。

## 能看到的后果

执行后，玩家和你放的东西之间发生的事会作为事件出现：
`director_entity_killed`（他打死了你的怪）、`director_entity_hurt_player`、`director_container_opened`（他打开了你的箱子）、
`director_item_picked_up`、`director_gift_used`、`director_block_broken`、`director_entity_gone`。
快照的 `director` 段列出你放在世界里、还存在的东西。

撤销只能恢复指令当时直接造成的改动；TNT 爆炸、火蔓延、岩浆流动这类之后发生的事撤销不了。
