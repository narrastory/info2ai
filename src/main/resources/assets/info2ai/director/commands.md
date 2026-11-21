# 指令说明

<!-- topic: selectors -->

## 选择器

| 写法 | 含义 |
| --- | --- |
| `@p` | 玩家（单人存档只有一个） |
| `@e[type=minecraft:zombie,distance=..16]` | 16 格内的僵尸 |
| `@e[tag=info2ai]` | 你生成的所有实体 |
| `@e[tag=info2ai_a17]` | 第 17 号动作生成的实体 |
| `@e[type=minecraft:cow,limit=1,sort=nearest]` | 最近的一头牛 |
| `@e[type=!minecraft:player,distance=..8]` | 8 格内除玩家外的实体 |
| `@s` | **对你无效**（伙伴没有实体）；只在 `execute as ...` 之后有意义 |

距离从指令原点（玩家脚下）算起。`distance=5..` 表示 5 格以外。

<!-- topic: coordinates -->

## 坐标

| 写法 | 含义 |
| --- | --- |
| `~ ~ ~` | 玩家脚下 |
| `~ ~-1 ~` | 玩家脚下那一块方块 |
| `^ ^ ^5` | 玩家正前方 5 格，同一高度（只跟随他面对的水平方向，不受抬头低头影响） |
| `^ ^ ^-6` | 正后方 6 格 |
| `^3 ^ ^` | 左边 3 格（`^` 的第一个数：正数是左） |
| `execute positioned ^ ^ ^10 positioned over world_surface run ...` | 前方 10 格的**地表**。放树、放结构必备 |

`over` 可用：`world_surface`（最高的非空气方块之上）、`motion_blocking`（忽略花草）、
`motion_blocking_no_leaves`（还忽略树叶）、`ocean_floor`（水底）。

在地下（`can_see_sky=false`）时不要用 `over world_surface`，那会把东西放到他头顶几十格的地面上。

<!-- topic: text -->

## 文字组件

用在 `tellraw`、`title`、`bossbar`、`scoreboard` 显示名、物品名、实体名、告示牌。

```text
{"text":"内容","color":"gold","bold":true,"italic":false}
[{"text":"<伙伴> ","color":"gold"},{"text":"后半句","color":"white"}]
```

颜色：`black` `dark_blue` `dark_green` `dark_aqua` `dark_red` `dark_purple` `gold` `gray`
`dark_gray` `blue` `green` `aqua` `red` `light_purple` `yellow` `white`，或 `"#FF88CC"` 这样的十六进制。

在指令参数里（`tellraw`、`title`）直接写 JSON；在物品组件和 NBT 里，要包成**单引号字符串**：
`custom_name='{"text":"花"}'`、`CustomName:'{"text":"老王"}'`。

<!-- topic: components -->

## 物品组件（1.20.5 以后的写法）

### 新旧写法对照

| 东西 | 正确写法 | 常见错误（旧版本写法） |
| --- | --- | --- |
| 物品自定义名 | `minecraft:poppy[minecraft:custom_name='{"text":"花"}']` | `poppy{display:{Name:...}}` |
| 文字组件 | JSON 字符串，外面用**单引号**：`'{"text":"x","color":"red"}'` | 双引号套双引号不转义 |
| 斜体 | 自定义名默认斜体，加 `"italic":false` | |
| 附魔 | `minecraft:enchantments={levels:{"minecraft:sharpness":3}}` | `Enchantments:[{id:...,lvl:3}]` |
| 附魔书 | `minecraft:stored_enchantments={levels:{"minecraft:mending":1}}` | |
| 实体 NBT 里的物品 | `{id:"minecraft:iron_sword",count:1}`，组件放 `components:{...}` | `Count:1b`、`tag:{...}` |
| 实体属性 | `attributes:[{id:"minecraft:generic.max_health",base:40d}]` | `Attributes:[{Name:...,Base:...}]` |
| 属性 ID | 1.21.1 仍带 `generic.` 前缀：`minecraft:generic.movement_speed` | |
| 盔甲顺序 | `ArmorItems:[脚,腿,胸,头]` | 顺序反了 |
| 时间长度 | 数字默认 tick；`120s` 秒，`2d` 游戏日。**没有 `m`** | `2m` |
| 粒子参数 | `minecraft:dust{color:[1.0,0.2,0.2],scale:1.5}` | `dust 1.0 0.2 0.2 1.5` |
| TNT 引信 | `{fuse:40}`（小写） | `Fuse` |
| 自己 | `@p`（伙伴没有实体，`@s` 为空） | `@s` |

### 常用组件

格式：`物品id[组件1=值,组件2=值] 数量`。组件 id 可以省略 `minecraft:`，但写全更不容易错。

| 组件 | 例子 | 用途 |
| --- | --- | --- |
| `custom_name` | `custom_name='{"text":"花","color":"red","italic":false}'` | 名字 |
| `lore` | `lore=['{"text":"第一行"}','{"text":"——伙伴"}']` | 说明文字（列表） |
| `enchantments` | `enchantments={levels:{"minecraft:unbreaking":3}}` | 附魔 |
| `stored_enchantments` | 同上 | 附魔书里的附魔 |
| `enchantment_glint_override` | `enchantment_glint_override=true` | 发光但不附魔 |
| `dyed_color` | `dyed_color={rgb:16711680}` | 皮革染色（十进制 RGB） |
| `potion_contents` | `potion_contents={potion:"minecraft:night_vision"}` | 药水 |
| `potion_contents` | `potion_contents={custom_color:16738740,custom_effects:[{id:"minecraft:speed",amplifier:1,duration:600}]}` | 自定义药水 |
| `food` | `food={nutrition:6,saturation:7.2f}` | 让任意物品可以吃 |
| `custom_data` | `custom_data={note:"anything"}` | 自己的标记（系统会自动给你送的东西加 `info2ai`，你不用加） |
| `max_stack_size` | `max_stack_size=1` | |
| `unbreakable` | `unbreakable={}` | 不会坏 |
| `rarity` | `rarity="epic"` | 名字颜色等级 |

```mcfunction
give @p minecraft:potion[minecraft:potion_contents={custom_color:16738740,custom_effects:[{id:"minecraft:speed",amplifier:1,duration:600}]},minecraft:custom_name='{"text":"跑得快","color":"light_purple","italic":false}'] 1
give @p minecraft:leather_helmet[minecraft:dyed_color={rgb:16738740},minecraft:custom_name='{"text":"粉色帽子","italic":false}'] 1
give @p minecraft:enchanted_book[minecraft:stored_enchantments={levels:{"minecraft:mending":1}}] 1
give @p minecraft:cookie[minecraft:food={nutrition:8,saturation:9.6f},minecraft:custom_name='{"text":"很顶饱的饼干","italic":false}'] 4
```

查组件 id：`mc_lookup(kind="component")`。

<!-- topic: entity_nbt -->

## 实体 NBT（`summon` 的最后一个参数）

| 键 | 例子 | 说明 |
| --- | --- | --- |
| `CustomName` | `CustomName:'{"text":"老王","color":"dark_red"}'` | 文字组件字符串 |
| `CustomNameVisible` | `1b` | 名字一直显示 |
| `PersistenceRequired` | `1b` | 不会因为玩家走远而消失 |
| `Health` | `40f` | 初始生命（配合 max_health 属性） |
| `attributes` | `[{id:"minecraft:generic.max_health",base:40d}]` | 属性 |
| `HandItems` | `[{id:"minecraft:iron_sword",count:1},{}]` | 主手、副手 |
| `HandDropChances` | `[0f,1f]` | 掉落率 0–1 |
| `ArmorItems` | `[{脚},{腿},{胸},{头}]` | 注意顺序 |
| `ArmorDropChances` | `[0f,0f,0f,0f]` | |
| `Tags` | `["boss"]` | 你自己的标签（系统会额外加 `info2ai`） |
| `NoAI` | `1b` | 不动（做雕像） |
| `Silent` | `1b` | 不出声 |
| `Glowing` | `1b` | 发光轮廓 |
| `Invulnerable` | `1b` | 无敌 |
| `IsBaby` | `1b` | 小僵尸 |
| `Passengers` | `[{id:"minecraft:skeleton"}]` | 骑在上面的实体（蜘蛛骑士） |

常用属性：`generic.max_health`、`generic.movement_speed`、`generic.attack_damage`、
`generic.armor`、`generic.knockback_resistance`、`generic.follow_range`、`generic.scale`（体型，0.0625–16）。

```mcfunction
summon minecraft:spider ^ ^ ^-10 {CustomName:'{"text":"八条腿的骑兵","color":"dark_gray"}',Passengers:[{id:"minecraft:skeleton",HandItems:[{id:"minecraft:bow",count:1},{}],HandDropChances:[0f,0f]}]}
summon minecraft:zombie ^ ^ ^-12 {CustomName:'{"text":"大个子","color":"dark_green"}',attributes:[{id:"minecraft:generic.scale",base:1.6d},{id:"minecraft:generic.max_health",base:50d}],Health:50f}
```

**键名写错不会报错**，会被静默忽略。生成后用 `data get entity @e[tag=info2ai_a<编号>,limit=1]` 确认。

<!-- topic: block_nbt -->

## 方块状态与方块实体 NBT

格式：`方块id[状态=值]{NBT}`。

```mcfunction
setblock ~2 ~ ~ minecraft:oak_sign[rotation=8]{front_text:{messages:['{"text":"伙伴到此一游"}','{"text":""}','{"text":""}','{"text":""}']}}
setblock ~ ~-2 ~3 minecraft:barrel[facing=up]{CustomName:'{"text":"埋着的桶","italic":false}',LootTable:"minecraft:chests/buried_treasure"}
```

| 方块实体 | 常用 NBT |
| --- | --- |
| 箱子、木桶、潜影盒 | `CustomName`、`Items:[{Slot:0b,id:"...",count:1}]`、`LootTable:"minecraft:chests/..."` |
| 告示牌 | `front_text:{messages:[4 个文字组件字符串]}`、`back_text` |
| 刷怪笼 | `SpawnData:{entity:{id:"minecraft:zombie"}}` |

战利品表 id 用 `mc_lookup(kind="loot_table", query="chests")` 查。

<!-- topic: limits -->

## 限制一览

被拒绝或回滚时，返回里会写原因和规则编号。

| 限制 | 说明 |
| --- | --- |
| 不能直接杀死或伤害生物 | `kill`、`damage`、瞬间伤害效果；对你刚生成的生物同样生效（残血怪在 NBT 里写 `Health`） |
| 不能直接扣玩家的血 | 包括改 `Health`、降低最大生命值（不能移除最大生命修饰器或清除生命提升效果） |
| 不能传送或推动已存在的实体 | `tp`、`spreadplayers`、`ride`、改 `Pos`/`Motion` |
| 单条指令最多改 4096 个方块 | 大工程拆成多条 |
| 单条最多生成 8 个实体；你生成的实体全世界最多 32 个 | 超出时先撤销旧的 |
| 玩家周围 3×3、脚下 2 格到头顶 2 格之间不能放岩浆/火/仙人掌等伤人方块；脚下 3 格内不能挖空；不能把方块放进他身体里 | 范围以外不受限 |
| 不能放矿脉地物（`place feature minecraft:ore_*`） | 放矿用 `fill … minecraft:iron_ore replace minecraft:stone` |
| 改容器内容（`item replace block`、`loot insert`、`data modify block`）、`fillbiome` | 可以做，撤销不了 |
| `clear` 必须写物品和数量 | `clear @p minecraft:bread 2` |
| 不能撤销玩家的进度和配方 | `advancement revoke`、`recipe take` 禁止 |
| `place structure` 只能放小型结构 | `mc_lookup(kind="structure")` 看 `place_allowed` |
| 禁止的指令 | `gamemode` `gamerule` `difficulty` `function` `schedule` `forceload` `tick` `worldborder` `locate` `reload` `datapack` `publish` |
| 不能生成凋灵、末影龙 | |
| 每分钟最多 10 条指令 | |
| 游戏暂停或玩家死亡时不能执行 | |

<!-- cmd: give -->
## give — 把物品放进玩家背包

`give <玩家> <物品>[组件] [数量]`

```mcfunction
give @p minecraft:golden_apple 1
give @p minecraft:iron_pickaxe[minecraft:enchantments={levels:{"minecraft:efficiency":3,"minecraft:unbreaking":2}},minecraft:custom_name='{"text":"老伙计的镐","color":"gold","italic":false}',minecraft:lore=['{"text":"——伙伴","color":"gold","italic":false}']] 1
```

- 背包满了会掉在他脚下。
- 物品名、lore、附魔等写法见 topic `components`。
- 系统会自动给“之前是空格子”的礼物打上隐藏标记，之后他吃掉、用掉会有 `director_gift_used` 事件。
- 背包满时物品掉在地上，拾取会有 `director_item_picked_up` 事件。

<!-- cmd: summon -->
## summon — 生成实体

`summon <实体> [坐标] [NBT]`

```mcfunction
summon minecraft:lightning_bolt ^6 ^ ^14
summon minecraft:item ^ ^1 ^3 {Item:{id:"minecraft:cake",count:1}}
summon minecraft:armor_stand ^ ^ ^4 {CustomName:'{"text":"稻草人"}',CustomNameVisible:1b,NoGravity:1b,ArmorItems:[{},{},{id:"minecraft:leather_chestplate",count:1},{id:"minecraft:carved_pumpkin",count:1}]}
```

- NBT 写法见 topic `entity_nbt`。
- 每条最多 8 个；你在世界里的实体总数最多 32。
- 生成在墙里的生物会窒息。用 `execute positioned ... if block ~ ~ ~ #minecraft:replaceable if block ~ ~1 ~ #minecraft:replaceable run summon ...` 防止；返回“没有成功”说明那里是墙。
- 想要一只残血的怪，直接在 NBT 里写 `Health:8f`（不能生成后再打它）。

一只有名字、有装备、掉钻石的僵尸，先用声音和黑暗预告：

```mcfunction
playsound minecraft:entity.zombie.ambient hostile @p ^ ^ ^-8 1 0.6
effect give @p minecraft:darkness 4 0 true
execute positioned ^ ^ ^-8 if block ~ ~ ~ #minecraft:replaceable if block ~ ~1 ~ #minecraft:replaceable run summon minecraft:zombie ~ ~ ~ {CustomName:'{"text":"老王","color":"dark_red","bold":true}',CustomNameVisible:1b,PersistenceRequired:1b,Health:40f,attributes:[{id:"minecraft:generic.max_health",base:40d},{id:"minecraft:generic.movement_speed",base:0.28d},{id:"minecraft:generic.attack_damage",base:5d}],HandItems:[{id:"minecraft:iron_sword",count:1},{id:"minecraft:diamond",count:2}],HandDropChances:[0f,1f],ArmorItems:[{},{},{id:"minecraft:leather_chestplate",count:1,components:{"minecraft:dyed_color":{rgb:8388608}}},{id:"minecraft:carved_pumpkin",count:1}],ArmorDropChances:[0f,0f,0f,0f]}
```

烟花：

```mcfunction
summon minecraft:firework_rocket ^ ^5 ^8 {LifeTime:20,FireworksItem:{id:"minecraft:firework_rocket",count:1,components:{"minecraft:fireworks":{flight_duration:1,explosions:[{shape:"large_ball",colors:[I;16711680,16766720],has_twinkle:true}]}}}}
```

烟花爆炸会伤到 5 格内的生物。
- 禁止：`minecraft:wither`、`minecraft:ender_dragon`。

<!-- cmd: setblock -->
## setblock — 放一个方块

`setblock <坐标> <方块>[状态]{NBT} [destroy|keep|replace]`

```mcfunction
setblock ^ ^ ^4 minecraft:campfire[lit=true]
setblock ~3 ~ ~ minecraft:chest{CustomName:'{"text":"小箱子","italic":false}',Items:[{Slot:13b,id:"minecraft:poppy",count:1}]}
```

- `keep`：只在原位置是空气时放置，适合“不破坏任何东西”。
- 会覆盖原有方块（包括他的建筑）。撤销可以恢复同步改动。
- 地表上的树桩：`execute positioned ^2 ^ ^8 positioned over motion_blocking_no_leaves run setblock ~ ~ ~ minecraft:oak_log[axis=y]`

<!-- cmd: fill -->
## fill — 填充一个长方体

`fill <角1> <角2> <方块> [destroy|hollow|keep|outline|replace [过滤]]`

```mcfunction
fill ^-2 ^-1 ^6 ^2 ^-1 ^10 minecraft:moss_block replace minecraft:grass_block
fill ~-3 ~-1 ~-3 ~3 ~-1 ~3 minecraft:mossy_cobblestone replace minecraft:cobblestone
```

- 体积 = 三边长度（含两端）相乘，最多 4096。
- `replace <方块>` 只替换指定方块，最适合“悄悄改变地面”。
- `^` 坐标的长方体会跟着视线方向。

前方 6 格一个 3×3、3 格深、底部是蜘蛛网的坑；雪地里把雪块换成细雪：

```mcfunction
execute positioned ^ ^ ^6 run fill ~-1 ~-3 ~-1 ~1 ~-1 ~1 minecraft:air
execute positioned ^ ^ ^6 run fill ~-1 ~-3 ~-1 ~1 ~-3 ~1 minecraft:cobweb
execute positioned ^ ^ ^7 run fill ~-1 ~-1 ~-1 ~1 ~-1 ~1 minecraft:powder_snow replace minecraft:snow_block
```

<!-- cmd: clone -->
## clone — 复制一块区域

`clone <源角1> <源角2> <目标角> [replace|masked] [normal|force|move]`

```mcfunction
clone ~-2 ~ ~-2 ~2 ~4 ~2 ~-2 ~ ~8 masked
```

- `masked` 不复制空气。`move` 会清空源区域。
- 体积上限 4096。

<!-- cmd: place -->
## place — 放置地物、结构、模板

```mcfunction
execute positioned ^ ^ ^10 positioned over world_surface run place feature minecraft:birch ~ ~ ~
execute positioned ^ ^ ^8 positioned over world_surface run place feature minecraft:patch_pumpkin ~ ~ ~
execute positioned ^ ^ ^30 positioned over world_surface run place structure minecraft:ruined_portal ~ ~ ~
```

| 子指令 | 用途 | 限制 |
| --- | --- | --- |
| `place feature <id> [坐标]` | 树、花丛、石头、矿脉、洞穴藤蔓…… | 无白名单；失败常因地面不合适 |
| `place structure <id> [坐标]` | 雪屋、沉船、废弃传送门、女巫小屋、沙漠/丛林神殿、海底废墟、埋藏的宝藏、下界化石 | 只允许小型结构 |
| `place template <id> [坐标] [旋转] [镜像]` | 结构模板文件 | 无白名单 |
| `place jigsaw` | | 禁止 |

好用的地物：树 `oak` `fancy_oak` `birch` `spruce` `cherry` `azalea_tree` `huge_red_mushroom`；
花草 `flower_meadow` `flower_cherry` `patch_berry_bush` `patch_pumpkin` `patch_sunflower`；
地形 `forest_rock` `ice_spike` `desert_well` `fossil_coal` `monster_room`（地牢，带刷怪笼）。
全部列表：`mc_lookup(kind="feature")`。

**矿脉地物（`ore_` 开头）禁止**：它们绕过方块记录，无法撤销，玩家还要等区块重载才看得见。
放矿用 `fill`，只替换石头，看起来就像天然的：

```mcfunction
fill ^-1 ^ ^4 ^1 ^1 ^5 minecraft:iron_ore replace minecraft:stone
fill ^-1 ^ ^4 ^1 ^1 ^5 minecraft:deepslate_iron_ore replace minecraft:deepslate
```

<!-- cmd: loot -->
## loot — 用战利品表生成东西

`loot <目标> loot <战利品表>`，目标：`give <玩家>`、`insert <坐标>`、`spawn <坐标>`、`replace ...`

```mcfunction
loot give @p loot minecraft:chests/igloo_chest
loot spawn ^ ^1 ^3 loot minecraft:chests/buried_treasure
```

比手写一箱东西更“像游戏本身给的”。

放一个带名字的箱子、填战利品、再塞一张纸条：

```mcfunction
execute positioned ^ ^ ^3 run setblock ~ ~ ~ minecraft:chest{CustomName:'{"text":"伙伴留的箱子","color":"gold","italic":false}'}
execute positioned ^ ^ ^3 run loot insert ~ ~ ~ loot minecraft:chests/simple_dungeon
execute positioned ^ ^ ^3 run item replace block ~ ~ ~ container.26 with minecraft:paper[minecraft:custom_name='{"text":"纸条","color":"yellow","italic":false}',minecraft:lore=['{"text":"路上捡的，都给你。","color":"gray","italic":false}']]
```

往容器里放东西（`loot insert`、`item replace block`）撤销不了。

<!-- cmd: item -->
## item — 直接修改某个格子

`item replace entity <实体> <格子> with <物品> [数量]`
`item replace block <坐标> <格子> with <物品> [数量]`

```mcfunction
item replace entity @p armor.head with minecraft:carved_pumpkin
item replace entity @p weapon.offhand with minecraft:torch 16
```

格子名：`weapon.mainhand` `weapon.offhand` `armor.head` `armor.chest` `armor.legs` `armor.feet`
`hotbar.0`–`hotbar.8` `inventory.0`–`inventory.26` `container.0`–`container.26`（箱子）。

替换会**覆盖**原物品（原物品消失）。
`item replace block`（改箱子里的格子）**撤销不了**。

<!-- cmd: clear -->
## clear — 拿走物品

`clear <玩家> <物品> <数量>` —— **物品和数量都必须写**

```mcfunction
clear @p minecraft:rotten_flesh 8
```

```text
clear @p                         ← 会被拒绝
clear @p minecraft:bread         ← 会被拒绝（没写数量就是全部拿走）
```

<!-- cmd: effect -->
## effect — 状态效果

`effect give <目标> <效果> [秒数] [等级] [隐藏粒子]`、`effect clear <目标> [效果]`

```mcfunction
effect give @p minecraft:night_vision 60 0 true
effect give @p minecraft:darkness 4 0 true
effect give @p minecraft:glowing 10 0 true
effect give @e[tag=info2ai,distance=..20] minecraft:speed 30 1
```

- 等级从 0 开始（0 = I 级）。
- 中毒、凋零、饥饿、缓慢、失明、反胃都可以用。
- **瞬间伤害对任何生物都会被系统拦下**（那是直接扣血）。

<!-- cmd: enchant -->
## enchant — 给手持物附魔

```mcfunction
enchant @p minecraft:unbreaking 2
```

只对玩家主手上能接受该附魔的物品有效，冲突附魔会失败。想送附魔物品，用 `give` 加 `enchantments` 组件更可控。

<!-- cmd: attribute -->
## attribute — 修改属性

```mcfunction
attribute @p minecraft:generic.movement_speed modifier add info2ai:quick 0.02 add_value
attribute @p minecraft:generic.movement_speed modifier remove info2ai:quick
attribute @p minecraft:generic.scale modifier add info2ai:tiny -0.3 add_multiplied_base
attribute @p minecraft:generic.scale modifier remove info2ai:tiny
```

- 修饰器 id 用 `info2ai:` 开头，便于之后移除。
- 运算：`add_value`、`add_multiplied_base`、`add_multiplied_total`。
- **`generic.max_health` 只能加，而且加上之后不能移除**（移除会直接压低血量）。
  所以不要用修饰器加血量上限；想临时多给几颗心，用效果：

```mcfunction
effect give @p minecraft:health_boost 120 1
```

<!-- cmd: data -->
## data — 读写 NBT

```mcfunction
data get entity @p SelectedItem
data get entity @p Inventory
data merge entity @e[tag=info2ai,limit=1,sort=nearest] {CustomName:'{"text":"老王二世","color":"dark_red"}'}
```

- 读取玩家数据很有用（`data get entity @p ...`）。玩家数据不能被修改（原版限制）。
- **不能**改任何实体的 `Pos`、`Motion`、`Rotation`、`Health`、`Dimension`、`Passengers`，
  也不能用 `{}` 开头的根过滤器路径。
- `data modify block` 改容器内容是允许的，但**撤销不了**。

<!-- cmd: execute -->
## execute — 组合与条件

`execute <子句...> run <指令>`

| 子句 | 例子 |
| --- | --- |
| `positioned <坐标>` | `positioned ^ ^ ^10` |
| `positioned over <高度图>` | `positioned over world_surface` |
| `at <实体>` / `as <实体>` | `as @e[tag=info2ai] at @s` |
| `rotated as <实体>` | |
| `if block <坐标> <方块或#标签>` | `if block ~ ~-1 ~ #minecraft:dirt` |
| `if entity <选择器>` | `if entity @e[type=minecraft:creeper,distance=..10]` |
| `unless ...` | 反向条件 |
| `if biome <坐标> <群系>` | `if biome ~ ~ ~ minecraft:plains` |
| `store result|success <目标> ...` | 把结果存到计分板、bossbar、方块 NBT |
| `summon <实体>` | 生成并以它为执行者 |

```mcfunction
execute if biome ~ ~ ~ minecraft:plains run say 平原啊
execute as @e[tag=info2ai,type=minecraft:zombie] at @s run particle minecraft:smoke ~ ~1 ~ 0.2 0.4 0.2 0 8
execute positioned ^ ^ ^10 if block ~ ~-1 ~ #minecraft:dirt run place feature minecraft:oak ~ ~ ~
```

- `execute if function` / `unless function` 禁止。
- `execute store ... entity <实体> <路径>` 不能写入 `Pos`、`Health` 等禁止键。

<!-- cmd: particle -->
## particle — 粒子

`particle <粒子>[参数] <坐标> <扩散x> <扩散y> <扩散z> <速度> <数量> [force|normal] [玩家]`

```mcfunction
particle minecraft:end_rod ~ ~1 ~ 1 1 1 0.02 30
particle minecraft:dust{color:[1.0,0.4,0.7],scale:1.5} ~ ~2 ~ 0.6 0.3 0.6 0 40
particle minecraft:totem_of_undying ~ ~1 ~ 0.5 0.5 0.5 0.4 60
particle minecraft:happy_villager ^ ^1 ^3 1 0.5 1 0 20
```

数量不限；上千个粒子会让游戏卡顿。

<!-- cmd: playsound, stopsound -->
## playsound / stopsound — 声音

`playsound <声音> <类别> <玩家> [坐标] [音量] [音调] [最小音量]`

类别：`master` `music` `record` `weather` `block` `hostile` `neutral` `player` `ambient` `voice`。

```mcfunction
playsound minecraft:entity.creeper.primed hostile @p ^ ^ ^-2 0.6 1
playsound minecraft:block.note_block.chime record @p ~ ~ ~ 1 1.2
stopsound @p
```

- 声音的方向感来自坐标：`^ ^ ^-2` 就是他身后。

<!-- cmd: title -->
## title — 屏幕中央的大字

```mcfunction
title @p times 10 60 20
title @p subtitle {"text":"副标题","color":"gray"}
title @p title {"text":"主标题","color":"gold"}
title @p actionbar {"text":"物品栏上方的一行字","color":"yellow"}
```

先设 subtitle 再设 title（title 触发显示）。

<!-- cmd: tellraw, say, me, msg, tell, w -->
## tellraw / say / me / msg — 说话

```mcfunction
tellraw @p [{"text":"<伙伴> ","color":"gold"},{"text":"那边好像有个洞。","color":"white"}]
say 天黑了
me 打了个哈欠
msg @p 你的镐快坏了
```

- `say` 显示为 `[伙伴] ...`，`me` 显示为 `* 伙伴 ...`，`msg` 是私聊样式。
- `tellraw` 最灵活，可以自己写前缀和颜色。

<!-- cmd: scoreboard -->
## scoreboard — 计分板（完全开放）

```mcfunction
scoreboard objectives add info2ai_deaths deathCount {"text":"死亡次数"}
scoreboard objectives add info2ai_mood dummy {"text":"伙伴的心情","color":"light_purple"}
scoreboard players set @p info2ai_mood 5
scoreboard players add @p info2ai_mood 1
scoreboard objectives setdisplay sidebar info2ai_mood
scoreboard objectives setdisplay sidebar
```

- 准则（criteria）可以追踪原版统计：`minecraft.mined:minecraft.diamond_ore`、
  `minecraft.picked_up:minecraft.iron_ingot`、`minecraft.killed:minecraft.zombie`、
  `minecraft.used:minecraft.bread`、`minecraft.custom:minecraft.jump`、`deathCount`、`health`。
- 计分项以 `info2ai_` 开头便于清理。
- 用 `scoreboard players get @p <计分项>` 读分数，结果在返回值里。

一个“捡 10 个铁锭”的挑战（配合 bossbar）：

```mcfunction
scoreboard objectives add info2ai_iron minecraft.picked_up:minecraft.iron_ingot {"text":"铁锭挑战","color":"gold"}
scoreboard objectives setdisplay sidebar info2ai_iron
bossbar add info2ai:challenge {"text":"日落前捡起 10 个铁锭","color":"gold"}
bossbar set info2ai:challenge max 10
bossbar set info2ai:challenge players @p
execute store result bossbar info2ai:challenge value run scoreboard players get @p info2ai_iron
```

结束时 `bossbar remove info2ai:challenge`、`scoreboard objectives remove info2ai_iron`。

<!-- cmd: bossbar -->
## bossbar — 顶部进度条

```mcfunction
bossbar add info2ai:hunt {"text":"老王的复仇","color":"red"}
bossbar set info2ai:hunt color red
bossbar set info2ai:hunt style notched_10
bossbar set info2ai:hunt max 100
bossbar set info2ai:hunt value 100
bossbar set info2ai:hunt players @p
bossbar remove info2ai:hunt
```

id 用 `info2ai:` 开头。`remove` 删除。

<!-- cmd: team, tag -->
## team / tag

```mcfunction
team add info2ai_crew {"text":"伙伴小队"}
team modify info2ai_crew color gold
team join info2ai_crew @e[tag=info2ai]
tag @e[type=minecraft:cow,distance=..8,limit=1,sort=nearest] add info2ai_pet
```

把你的怪加进队伍可以给它们统一的名字颜色和发光颜色。

<!-- cmd: time -->
## time

```mcfunction
time set night
time add 1000
time query daytime
```

`set` 可用 `day` `noon` `night` `midnight` 或数字。

<!-- cmd: weather -->
## weather

```mcfunction
weather rain 120s
weather thunder 60s
weather clear
```

时长：数字是 tick，`s` 秒，`d` 游戏日，没有 `m`。

远处一道闪电：`execute positioned ^8 ^ ^24 positioned over motion_blocking run summon minecraft:lightning_bolt ~ ~ ~`

<!-- cmd: experience, xp -->
## experience / xp

```mcfunction
xp add @p 5 levels
xp add @p 30 points
xp query @p levels
```

可以扣经验：`xp add @p -3 levels`。

<!-- cmd: spawnpoint, setworldspawn -->
## spawnpoint / setworldspawn

```mcfunction
spawnpoint @p ~ ~ ~
```

改重生点后，他死后会出现在新位置。

<!-- cmd: advancement, recipe -->
## advancement / recipe

```mcfunction
recipe give @p minecraft:cake
advancement grant @p only minecraft:story/mine_stone
```

`revoke` 和 `take` 禁止。

<!-- cmd: random -->
## random — 掷骰子

```mcfunction
random value 1..6
```

结果在返回值里，只有你看得到。`random roll 1..6` 会把结果广播到聊天栏，适合“一起掷骰子”。

<!-- cmd: fillbiome -->
## fillbiome — 改群系

```mcfunction
fillbiome ~-7 ~-7 ~-7 ~7 ~7 ~7 minecraft:cherry_grove
```

会改变草和树叶的颜色、天气、刷怪。体积上限 4096（15×15×15 以内）。**不可撤销**。

<!-- cmd: help, list, seed, return, trigger -->
## help / list / seed / return / trigger

辅助指令，均允许。`seed` 返回世界种子。
