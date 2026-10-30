# ExamOfPVE

面向 **JDK 21 + Paper 1.21.11** 的 PVE 考核系统插件。开始考核后，玩家需要在限定时间内击杀指定数量与类型的生物；全部击杀即通过，超时、死亡一次、主动终止或中途退出均判定为失败。

## 功能特性

- **多考核**：可在配置中定义多个考核（`exam1`、`exam2` …），玩家通过命令选择。
- **独立地图（多副本并发）**：每个考核配置一个世界（地图）列表，插件通过 **Multiverse-Core** 自动加载 / 卸载地图，并从中挑选空闲副本，支持多玩家同时考核。
- **生物生成**：支持原版生物（`EntityType`）与 **MythicMobs** 生物，可配置每只生物的生成坐标、数量与生成时间（秒）。
- **区域随机生成**：除固定点位外，也可为点位配置一个矩形区域，在区域内随机挑选安全位置生成；不会生成在方块内部，同一区域点位的相邻生成点保持至少 2 格距离（区域内位置不足时自动放宽）。
- **怪物池**：可定义带权重的怪物池（原版与 MythicMobs 可混排）；每个刷怪点位可用 `pool` 引用命名怪物池，或直接内联一份列表。每次生成一只生物都会按权重独立抽取，生物消失后按原类型重生。
- **时间窗口**：可限定考核仅在多个现实时间段开放（支持非连续多时段与跨午夜）。
- **世界保护与恢复**：可配置是否允许破坏 / 放置方块；允许时，考核期间的所有方块变更（含爆炸、燃烧、液体流动、桶、骨粉、打火石、耕地、作物生长、雪/冰融化、树叶 decay、活塞移动等）会在结束后自动恢复原样（含容器物品与告示牌文字）。
- **奖励**：成功后可发放金币（Vault）、经验值、自定义命令。
- **考核终止**：`/exam abort` 主动终止，判定为失败。
- **约束**：考核中禁止输入任何命令（终止命令、`/exam settings` 个人设置与白名单命令除外）；中途退出游戏直接判定失败。
- **BossBar**：顶部血条实时显示击杀进度与剩余时间。
- **挑战机制**：8 种挑战（生命减半 / 怪物翻倍 / 禁止自然回血 / 禁止任意回血 / 禁用原版辅助道具 / 时间减半 / 禁用剑斧 / 禁止护甲），倍率相乘叠加（如两个 x1.7 叠加为 x2.89）并乘到金币与经验奖励上；按完成的挑战数量发放**完全自定义**的额外奖励（金币 / 经验 / 点券 / 命令，各考核独立配置）。
- **排行榜与统计**：每场考核最快通关 Top10、玩家参加/通过次数与最佳时间、当前进行人数、考核开放状态（数据持久化于 `data.yml`）。
- **考核费用**：参加考核可设置金币费用（Vault），进入时扣除。
- **初次通过奖励**：可配置首通某考核的额外奖励。
- **考核预备**：玩家传送至考核地图后先有准备阶段（默认 5 秒，可配置），期间无法移动、怪物不会生成，每秒播放音效与 subtitle 倒计时，结束后才正式开始考核（计时与刷怪均从结束后起算）。
- **强制考试**：`/exam forcestart <id> [玩家]` 可强制指定玩家开始考核，无视时间段限制与进入费用（需 `examofpve.admin`）。
- **虚空传送**：考核中跌入虚空（低于配置的 Y 坐标）自动传送回考核出生点，并重置摔落距离，不会受到残留的摔落伤害。
- **考核暂停**：考核中连按指定按键触发暂停（默认**三击**，每场最多 2 次、每次 1 分钟，均可配置；R/C/X/Z/V 等键位服务器端无法检测，故以连按次数降低误触），暂停时玩家与生物全部冻结（移动 / 视角 / 物品 / 回血 / 饥饿 / 生物 AI），再连按同键提前恢复；按键默认潜行（Shift，不得用 Shift+F）。暂停 / 预备时若玩家在空中，会临时开启飞行悬浮，避免被服务器判定为飞行踢出。最终成绩与考核日志会标注本场暂停次数。
- **剩余怪物高亮**：剩余目标数不超过总数的 20% 时，存活怪物获得发光效果；尚未生成的目标在后续生成时同步获得发光。玩家可通过个人设置关闭该效果。
- **个人设置**：`/exam settings` 查看与调整个人偏好（目前支持剩余怪物高亮开关），设置持久化在 `data.yml`。
- **提醒音效与提示**：考核剩余时间到达配置值（默认 60/30/10/5 秒）、暂停剩余时间到达配置值（默认 5 秒）、生命值低于配置阈值（默认 20%/10%）时，播放音效并发送提示。
- **考核结束展示**：成功通过后玩家不会立即被传送，而是在场地停留 5 秒并释放数枚烟花，再自动传送回出发点；失败 / 超时 / 终止则立即传送离开。
- **考核日志**：每玩家每考核保留最近 20 次考核记录（时间、结果、用时、挑战、倍率与暂停次数），通过 `/exam log` 查看。

## 环境要求

| 项目 | 要求 |
| --- | --- |
| JDK | 21 |
| 服务端 | Paper 1.21.11 |
| 构建工具 | Maven 3.8+ |
| 主要依赖 | Multiverse-Core 5.8.1（加载/卸载地图） |
| 可选插件 | MythicMobs 5.13.0（MythicMobs 生物）、Vault + 经济插件（金币奖励）、PlaceholderAPI 2.12.3（占位符） |

> 四个外部插件均为**软依赖**：缺失时插件仍能启动，仅自动禁用对应功能。

## 1.21.11 兼容性验证

本次已针对 Paper 1.21.11 完成以下验证：

| 项目 | 验证结果 |
| --- | --- |
| 编译 API | `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`，`BUILD SUCCESS` |
| 服务端 | Paper 1.21.11 build 132，JDK 21，插件正常加载并启用 |
| Multiverse-Core | 5.8.1，正常启用，插件通过 `MultiverseCoreApi` 初始化 |
| MythicMobs | 5.13.0，正常启用，插件 MythicMobs 服务初始化无异常 |
| PlaceholderAPI | 2.12.3，插件成功注册 `examofpve` 扩展 |
| Vault | 1.7.3-b131，正常启用；无经济插件时仅禁用金币功能 |
| 无软依赖启动 | 仅安装 ExamOfPVE 时也能启用，缺失 Multiverse 会提示地图加载功能不可用 |
| 命令冒烟测试 | `/exam list`、`/exam reload` 在服务器控制台正常返回 |

`plugin.yml` 的 `api-version` 已更新为 `1.21`。本次验证未使用真实玩家客户端跑完整一局（地图加载、刷怪、结算等完整流程）；如果需要在真实服务器上做最终验收，建议重点观察首次地图加载与 MythicMobs 生物生成。

## 构建

```bash
mvn clean package
```

产物：`target/ExamOfPVE-1.4.1.jar`，放入服务器 `plugins/` 目录后重启或 `/reload`。

> 单元测试：`mvn test`（怪物池权重、区域解析、挑战 reload、时间窗、倍率格式等纯逻辑测试，不依赖服务器运行）。

> MythicMobs 依赖坐标使用 `io.lumine:Mythic-Dist`（仓库 `mvn.lumine.io`），版本由 `pom.xml` 中的 `mythicmobs.version` 控制（默认 `5.13.0`，已验证 1.21.11）。若你使用的 MythicMobs 版本不同，请修改该属性；若版本 API 有变动，相应调整 `MythicMobsService.java`。

> Multiverse-Core 依赖使用官方 wiki 推荐的坐标 `org.mvplugins.multiverse.core:multiverse-core`（仓库 `repo.onarandombox.com`），版本由 `pom.xml` 中的 `multiverse.version` 控制（默认 `5.8.1`，已验证 1.21.11）。Multiverse 5 采用 `MultiverseCoreApi` 入口，结果类型为 vavr 的 `Option` 与 Multiverse 自有的 `Attempt`（不再是旧版的空值判断），相关逻辑集中在 `WorldManager.java`；因此编译期还需 vavr（`io.vavr:vavr`，provided，已加入 pom）。`WorldManager.loadWorld(String)` 在 5.8.1 中仍可用（已标记 deprecated），后续可改用 `LoadWorldOptions` 版本。

> 若 `repo.onarandombox.com` 出现 TLS/连接失败，可从 Modrinth 下载 `multiverse-core-5.8.1.jar`，用 `mvn install:install-file` 安装到本地仓库后再构建；本机验证即采用这种方式。

## 地图准备（重要）

1. 搭建好一张考核地图，记下其文件夹名（例如 `exam1_world`）。
2. 复制多份到服务端世界目录（例如 `exam1_world`、`exam1_world_2`），数量决定同场最多并发人数。
3. 用 Multiverse-Core 逐个导入：`/mv import <世界文件夹名> normal`。
4. 在 `config.yml` 对应考核的 `worlds` 列表中填入这些世界名。

> 世界名必须与 Multiverse 中的世界名（文件夹名）完全一致。

## 命令与权限

| 命令 | 说明 | 权限 |
| --- | --- | --- |
| `/exam list` | 查看可参加的考核（含开放状态） | `examofpve.use`（默认所有人） |
| `/exam start <id>` | 开始指定考核（按配置扣除金币费用） | `examofpve.use` |
| `/exam forcestart <id> [玩家]` | 强制指定玩家开始考核（无视时间段与费用限制；缺省玩家则作用于自己） | `examofpve.admin`（默认 OP） |
| `/exam abort` | 终止当前考核（判定失败） | 无需额外权限 |
| `/exam challenges` | 查看全部挑战、倍率与当前开关状态 | `examofpve.use` |
| `/exam challenge <id> [on\|off]` | 开启 / 关闭挑战（考核前设置，无参数则切换） | `examofpve.use` |
| `/exam settings [mob-glow] [on\|off]` | 查看 / 修改个人考核设置（剩余怪物高亮等；考核中也可用） | `examofpve.use` |
| `/exam top <id>` | 查看某考核最快通关排行榜（前 10） | `examofpve.use` |
| `/exam stats <id> [玩家]` | 查看某玩家参加 / 通过某考核的次数与最佳时间 | `examofpve.use` |
| `/exam playing [id]` | 查看某考核（或全部考核）当前进行中人数 | `examofpve.use` |
| `/exam log <id> [玩家]` | 查看某玩家在某考核的最近 20 次考核记录 | `examofpve.use` |
| `/exam reload` | 重载配置 | `examofpve.admin`（默认 OP） |

别名：`/examofpve`、`/pveexam`。终止命令也支持 `/exam cancel|stop|quit`。

## 配置说明

主配置 `plugins/ExamOfPVE/config.yml`：

```yaml
exams:
  exam1:
    display-name: "&a新手考核"        # 显示名，支持 & 颜色
    worlds: [ "exam1_world", "exam1_world_2" ]  # 地图副本列表
    spawn:                            # 可选：进入后的出生点（相对考核世界）
      x: 0.5
      y: 100.0
      z: 0.5
      yaw: 0.0
      pitch: 0.0
    time-limit: 120                   # 限时（秒）
    # game-mode: SURVIVAL             # 可选：强制游戏模式
    # open-window:                    # 可选：现实时间开放窗口，支持非连续多时段与跨午夜
    #   - start: "08:00"
    #     end: "09:00"
    #   - start: "12:00"
    #     end: "13:00"
    world-settings:
      allow-break: false              # 是否允许破坏
      allow-place: false              # 是否允许放置
    mobs:
      - type: ZOMBIE                  # 原版：EntityType 名称
        amount: 3                     # 数量
        delay: 0                      # 生成时间（考核开始后第几秒）
        x: 100.5
        y: 100.0
        z: 100.5
        # name: "&c小僵尸"            # 可选：自定义名称
      - mythicmob: "SkeletonKing"     # MythicMobs 生物名
        amount: 1
        delay: 10
        x: 110.5
        y: 100.0
        z: 100.5
      # 区域随机生成（在 x/z 矩形内随机挑选安全位置）
      # - type: ZOMBIE
      #   amount: 10
      #   delay: 0
      #   region: "0,0,100,100"
    rewards:
      money: 100.0                    # 金币（Vault）
      exp: 50                         # 经验值（经验点）
      commands:                       # 自定义命令（控制台执行）
        - "give %player% diamond 1"
        - "broadcast &e%player% 通过了 %exam% 考核！"
```

### 怪物池

在 `config.yml` 根节点定义命名怪物池，池内条目支持原版生物与 MythicMobs 生物，`weight` 为抽取权重（默认 1）：

```yaml
mob-pools:
  undead:
    - type: ZOMBIE
      weight: 3
    - type: SKELETON
      weight: 2
      name: "&7骷髅射手"          # 可选：该条目生成时的显示名
    - mythicmob: "SkeletonKing"   # 需安装 MythicMobs 且名称存在
      weight: 1
  # 字符串简写：原版生物名（权重 1）；MythicMobs 可写 "mythic:生物名"
  simple:
    - "SPIDER"
    - "mythic:ZombieLord"
```

刷怪点位可以用 `pool: <池名>` 引用命名池（不区分大小写），也可以直接在点位内联一份列表：

```yaml
mobs:
  - pool: "undead"          # 引用命名池：每只独立随机抽取
    amount: 5
    delay: 0
    x: 100.5
    y: 100.0
    z: 100.5
  - pool:                   # 内联池：只对当前点位生效
      - type: SPIDER
      - type: CAVE_SPIDER
    amount: 2
    delay: 10
    x: 110.5
    y: 100.0
    z: 100.5
```

每生成一只生物都会独立抽取一次，因此同一场考核中可能混有多种生物；生物意外消失后，会按当时抽中的类型在原点位重生。若引用的池不存在或池为空，该点位会被跳过并输出警告。

### 区域随机生成

点位可以用 `region` 代替 `x/y/z`，在该矩形区域内为每只生物随机挑选安全位置：

```yaml
mobs:
  - type: ZOMBIE
    amount: 10
    delay: 0
    region: "0,0,100,100"        # x1,z1,x2,z2
  - mythicmob: "SkeletonKing"
    amount: 3
    region:                      # 推荐显式 Y 范围，避免生成到屋顶
      min-x: 0
      min-z: 0
      max-x: 100
      max-z: 100
      min-y: 60
      max-y: 100
```

规则：

- 不会生成在方块内部：脚部和头部必须不是固体 / 液体 / 火，脚下需要是固体方块。
- 同一区域点位的生成点至少间隔 2 格；多次尝试找不到满足间距的位置后（视为区域已填满），会放宽间距限制，但仍不会生成在方块内部。
- 省略 Y 范围时，会从该 X/Z 的最高方块向下寻找第一块固体地面，再在其上方生成。
- `min-y` / `max-y` 表示生物脚部所在 Y 的范围（含边界）。
- `region` 与 `x/y/z` 同时存在时，`x/y/z` 会被忽略。
- 字符串格式也支持 `x1,z1,x2,z2,min-y,max-y`。

全局设置：

```yaml
settings:
  allowed-commands-during-exam:       # 考核中额外放行的命令（以 / 开头）
    - "/msg"
  voucher-command: "points give %player% %amount%"  # 点券发放命令模板（%player%/%amount%），留空禁用点券奖励
  list-hide-closed: false             # /exam list 是否隐藏未开放时间的考核
  void-teleport-y: -64.0              # 虚空传送阈值：考核中 Y 低于此值即传回考核出生点（传送后重置摔落距离）
  pause-enabled: true                 # 考核暂停开关
  pause-key: "SNEAK"                  # 暂停触发键：SNEAK(潜行/Shift，默认) / SPRINT / SWAP(F)（不得用 Shift+F）
  pause-clicks-required: 3            # 触发暂停所需连按次数（默认 3 = 三击；2 = 双击）
  pause-click-window-ms: 700          # 连按判定窗口（毫秒，自第一次按下起算；旧键 pause-double-click-ms 仍兼容）
  pause-duration-seconds: 60          # 每次暂停时长（秒）
  max-pauses: 2                       # 每场最多暂停次数
  time-warning-at: [60, 30, 10, 5]    # 考核剩余时间提醒点（秒），到达时播放音效并提示
  pause-warning-at: 5                 # 暂停剩余时间提醒点（秒）
  low-health-warning-at: [20, 10]     # 低血量提醒阈值（最大生命百分比）
  prep-time-seconds: 5                # 考核预备阶段（秒），0 = 关闭；期间冻结移动、不刷怪，倒计时后正式开始
```

挑战（全局倍率，多个挑战相乘叠加）与考核附加配置：

```yaml
challenges:                 # 倍率可覆盖，如把禁用护甲调为 6.0
  half-health: 1.4          # 生命值减半
  double-mobs: 1.7          # 怪物数量x2
  no-natural-regen: 1.7     # 禁止自然回血
  no-regen: 3.2             # 禁止任意形式回血
  no-vanilla-tools: 1.5     # 禁用原版辅助道具
  half-time: 2.0            # 考核时间减半
  no-sword-axe: 2.0         # 禁用剑和斧
  no-armor: 5.0             # 不允许穿戴护甲

exams:
  exam1:
    entry-fee: 100.0        # 参加费用（金币，Vault；0 = 免费）
    # allowed-challenges:   # 可选：允许携带的挑战（不写 = 允许全部）
    #   - half-health
    challenge-rewards:      # 完成挑战数量的额外奖励（取 完成数 <= N 的最大档位），每档可独立自定义
      3:                    # 完成 3 个挑战
        money: 1000.0       # 金币
        exp: 0              # 经验
        points: 0           # 点券（走 settings.voucher-command）
        commands: []        # 自定义命令（%player%/%exam%）
      # 5:
      #   points: 3
      # 6:
      #   commands:
      #     - "lp user %player% parent add vip 3d"
    first-win-rewards:      # 初次通过该考核的额外奖励
      money: 200.0
      exp: 100
      commands:
        - "give %player% netherite_ingot 1"
```

## 占位符

奖励命令中可用：`%player%`（玩家名）、`%exam%`（考核 id）、`%multiplier%`（最终挑战倍率，如 `1.44`）。

消息（`messages.yml`）中可用：`{exam}` `{time}` `{kills}` `{total}` `{display}` `{window}` `{status}` `{name}` `{id}` `{multiplier}` `{count}` `{points}` `{fee}` `{player}` `{plays}` `{wins}` `{best}` `{rank}` `{key}` `{seconds}` `{left}` `{index}` `{time-used}` `{extra}`。

## PlaceholderAPI 变量

安装 PlaceholderAPI 后，插件会注册 `%examofpve_*%` 变量（PlaceholderAPI 已加入 `softdepend` 与 Maven 依赖）。`<id>` 为考核 id（如 `exam1`），可含下划线。

**玩家相关**（由在线玩家触发，或用 PAPI 的 `_player` 指定目标）：

| 变量 | 含义 |
| --- | --- |
| `%examofpve_in_exam%` | 是否正在考核（true/false） |
| `%examofpve_exam_id%` | 当前考核 id（未考核为空） |
| `%examofpve_multiplier%` | 当前考核最终挑战倍率（未考核为 1） |
| `%examofpve_selectedchallenges%` | 已选挑战数量 |
| `%examofpve_selectedchallenges_list%` | 已选挑战 id 列表（逗号分隔） |
| `%examofpve_challenge_<id>%` | 玩家是否已启用挑战 `<id>`（true/false，如 `%examofpve_challenge_half-health%`；占位符中 `_` 与 `-` 均兼容，如 `%examofpve_challenge_half_health%`） |
| `%examofpve_stats_<id>_plays%` | 某考核参加次数 |
| `%examofpve_stats_<id>_wins%` | 某考核成功次数 |
| `%examofpve_stats_<id>_best%` | 某考核最佳用时（秒） |
| `%examofpve_stats_<id>_best_fmt%` | 某考核最佳用时（`mm:ss`，无记录为"无"） |

**全局**：

| 变量 | 含义 |
| --- | --- |
| `%examofpve_available_exams%` | 当前可参加（开放中）的考核 id 列表（逗号分隔，无则空） |
| `%examofpve_total_playing%` | 所有考核进行中总人数 |
| `%examofpve_playing_<id>%` | 某考核当前人数 |
| `%examofpve_open_<id>%` | 某考核是否开放（true/false） |
| `%examofpve_top_<id>_<rank>_name%` | 某考核排行榜第 rank 名玩家名（rank 1~10） |
| `%examofpve_top_<id>_<rank>_time%` | 某考核排行榜第 rank 名成绩（秒） |

例如：`%examofpve_stats_exam1_wins%`、`%examofpve_playing_exam1%`、`%examofpve_top_exam1_1_name%`、`%examofpve_available_exams%`、`%examofpve_challenge_no-armor%`。

## 考核流程与判定

1. 玩家执行 `/exam start <id>` → 校验（存在性、时间窗口、空闲地图）→ 加载地图 → 传送入场 → **预备阶段**（默认 5 秒，期间冻结移动、不刷怪，倒计时音效 + subtitle）→ 计时开始 → 按计划生成生物。
2. 击杀进度实时计入 BossBar；全部目标击杀 → **通过**，发放奖励并传送回原地。
3. 超时 / 死亡一次 / `/exam abort` / 中途退出 → **失败**。
4. 考核中可连按暂停键（默认三击）暂停 / 恢复（见暂停设置），暂停期间计时与生物行动冻结；成功通过后玩家在场地停留 5 秒并释放烟花，再自动传送回出发点。
5. 结束后自动恢复地图原样并卸载世界（无其他玩家占用时）。

## 注意事项

- 生物死亡按“任意方式死亡”计入进度；若生物在考核中自行消失（脱战消失等），插件会自动在配置位置重新生成，保证可通关；若生成失败（如 MythicMobs 名称错误），该目标会被豁免并输出警告，避免无法通关。
- 地图恢复基于“记录考核期间的方块变更”实现，若考核世界被外部插件直接改动物体（未触发方块事件），可能无法完全恢复。建议为考核地图设置专用副本。
- 挑战在进入考核时生效并结算：生命值减半、脱下护甲在进入时应用、结束（含失败）时恢复；其余挑战（回血、道具、剑斧、护甲）由事件拦截实现。挑战选择、统计、排行榜与考核日志持久化在 `plugins/ExamOfPVE/data.yml`，可直接备份。
- 考核费用在进入时扣除，仅在场地加载失败时退还；失败、终止、退出均不退费。
- 暂停限制：暂停触发采用"连按次数"（默认三击，`settings.pause-clicks-required`），因为 R/C/X/Z/V 等键位在原版协议中不产生任何服务器事件，无法检测；连按窗口为 `settings.pause-click-window-ms`。暂停期间玩家视角与位置冻结（通过取消移动事件实现），生物关闭 AI（MythicMobs 基于 OnTimer 等非 AI 触发的技能无法被完全阻止）；暂停中仍可输入 `/exam abort` 终止考核。暂停 / 预备冻结时若玩家在空中，插件会临时开启飞行悬浮（`allowFlight` + `flying`）并在解除时还原，避免被服务器判定为飞行踢出。
- 本场使用过暂停时，结算消息会附带暂停次数；`/exam log` 的每条记录也会标注暂停次数。
- 剩余高亮以“未击杀目标数（含尚未生成的目标）≤ 总数 20%”为阈值；已生成的存活怪物立即发光，之后才生成的目标在生成时同步发光。可用 `/exam settings mob-glow off` 关闭，考核中也可切换。
- 区域随机生成会在每个生成点之间保持至少 2 格距离；若多次随机都找不到满足间距的位置（通常表示区域已被填满），会放宽间距但仍避开方块内部。省略 Y 范围时使用该 X/Z 的最高固体地面。
- 个人设置持久化在 `data.yml` 的 `player-settings.<uuid>` 下（目前为 `mob-glow`）。
- 强制开始（`/exam forcestart`）跳过时间段与费用校验，但场地加载、空闲地图等技术性限制仍然有效；未扣除的费用不会在加载失败时退还。未能启动时会提示管理员，不再误报成功。
- 预备阶段（`settings.prep-time-seconds`）内玩家不可移动、不可暂停、怪物不生成，考核计时与刷怪均从倒计时结束后起算。
- 通关用时从预备阶段结束、考核正式开始时计算，不包含地图加载、预备阶段与暂停时长。
- 成功通过后的 5 秒停留窗口内，场地仍然禁止方块改动与命令，也不能立刻开始下一场考核（防止地图在会话已移除的窗口里被破坏）。
- 考核开始与结束时会对场地做一次临时实体清理（掉落物、经验球、弹射物、烟花、点燃的 TNT、下落方块、药水云），盔甲架 / 展示框等地图实体不受影响，避免掉落物跨场残留。
- 玩家在考核中退出时，插件会立即尝试还原护甲、游戏模式、生命上限与飞行权限；若离线状态下无法写回，会记录到 `data.yml` 的 `pending-effects`，并在玩家下次进入服务器（或重生）后补做。
- 死亡后通过重生事件把玩家送回出发点，不再修改玩家的持久化重生点（床 / 重生锚不受影响）。
- `/exam reload` 会重新读取挑战倍率；配置中删除的挑战不会残留旧倍率。
- 命令白名单按命令标签精确匹配（配置 `/msg` 不会放行 `/msgfoo`）；如需限制到具体参数，可写完整前缀如 `/msg hello`。
- `reload` 会更新挑战倍率、提醒、暂停等全局设置；已开始考核的限时、挑战选择与场地配置沿用启动时的快照。
