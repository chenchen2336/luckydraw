# 幸运抽奖机（Fabric 26.3 · 服务器端）

一个只装在服务器上的抽奖模组：自定义单抽/十连价格、两套独立奖池、50 抽保底、环形粒子 + 三段音效，
左上角奖池预览，钱从经济账户里直接扣、奖品也发回同一个账户。

因为完全使用原版方块、原版界面、原版粒子和原版音效，**客户端不需要安装本模组**。

普通玩家**不需要管理员权限**也能正常拿奖：物品奖品直接进背包，`commands` 类奖品在发奖时会临时借用
管理员权限执行（命令跑不了还会退一步用控制台），所以不用给玩家发 OP。

## 安装

1. 把这个 jar 放进服务器的 `mods/` 文件夹（需要 Fabric API）。
2. 经济模组（Savs Common Economy）装不装都可以：
   - 装了：自动对接，扣费/发奖都走它的钱包；
   - 没装：使用模组自带的内置钱包，管理员用 `/lottery give` 发币。
3. 启动服务器后会自动生成 `config/luckydraw/lottery.json`，改完用 `/lottery reload` 生效。

## 怎么玩

- 默认右键 **磁石（lodestone）** 打开抽奖界面，或手持 **时钟（clock）** 右键打开；也可以输入 `/lottery open`。
- 界面第一行是「单抽奖池」预览，第二行是「十连奖池」预览，鼠标悬停能看到稀有度和概率。
- 绿色按钮 = 单抽，钻石按钮 = 十连，星星 = 保底进度，金锭 = 我的钱包，书本 = 玩法说明。
- 点按钮后界面会关掉，脚下环形火花升起、滴答声逐渐加速，开奖时彩色粒子炸开 + 号角声，结果发到聊天栏。

## 配置文件 `config/luckydraw/lottery.json`

```json
{
  "enabled": true,
  "machine": {
    "block": "minecraft:lodestone",
    "item": "minecraft:clock",
    "requireSneak": false
  },
  "economy": {
    "provider": "auto",
    "startingBalance": 1000,
    "symbol": "币",
    "symbolBefore": false
  },
  "price": { "single": 100, "ten": 900 },
  "pity": { "enabled": true, "threshold": 50, "announce": true },
  "effects": { "particles": true, "sounds": true, "animationTicks": 40 },
  "prizeCommands": { "runAs": "auto", "silent": true },
  "pools": {
    "single": [
      { "item": "minecraft:bread", "count": 6, "weight": 30 },
      { "item": "minecraft:diamond", "count": 2, "weight": 8, "rare": true },
      { "money": 500, "weight": 2 }
    ],
    "ten": [ ]
  }
}
```

- `machine.block`：右键哪个原版方块打开界面，写 `""` 关闭方块触发。
- `machine.item`：手持哪个原版物品右键打开界面，写 `""` 关闭物品触发。
- `economy.provider`：`auto`（默认，有 Savs 就用）、`savs`（强制）、`internal`（只用内置钱包）。
- `price.single` / `price.ten`：单抽、十连价格，可以是小数。
- `pity.threshold`：累计多少抽没出稀有后，下一抽强制出稀有。
- `effects.animationTicks`：开奖动画时长，20 tick = 1 秒。
- `prizeCommands`：奖品里 `commands` 用谁的身份执行——这是"普通玩家要不要 OP"的关键。
  - `runAs` 默认 `auto`：先以玩家为执行者、临时借用管理员权限（`@s`、`~` 相对坐标都能用），
    这条命令跑不了时再用服务器控制台身份重试一次。所以非管理员玩家抽到
    `give %player% diamond 1` 也能真的拿到手。
  - 想固定身份就写 `operator`（玩家身份 + 管理员权限）、`console`（纯控制台）、
    `player`（完全按玩家自己的权限，玩家不是管理员时会失败）。
  - `silent` 默认 `true`，把命令的回显藏起来，聊天栏只留抽奖结果，不会刷 "已给予玩家…"。
- `pools.single` / `pools.ten`：两套独立奖池，各自配置权重。权重越大越容易出。
- 单个奖品支持三种写法（可混用）：
  - `item` + `count`：发物品，背包满了会掉在脚下；
  - `money`：直接发钱，走同一个经济账户；
  - `commands`：中奖后执行命令，`%player%` 会替换成玩家名，例如
    `"commands": ["lp user %player% permission set vip"]`；默认借用管理员权限执行，
    普通玩家中奖也能发出去；
  - `commandAs`：单独给这一条奖品换执行身份（`auto` / `operator` / `console` / `player`），
    比如某个插件命令只认控制台，就写 `"commandAs": "console"`；
  - `rare: true` 标记为稀有奖，保底只从稀有奖里挑；
  - `name` 可给奖品起个自定义名字。

改完配置记得 `/lottery reload`，不用重启服务器。

## 保底与数据

- 每个玩家的累计抽数、保底进度存在 `世界目录/lottery/players.json`，重启、重登都不会丢。
- 出稀有后计数清零；没开保底也没关系，`pity.enabled = false` 即可关闭。

## 命令

| 命令 | 说明 | 权限 |
| --- | --- | --- |
| `/lottery` 或 `/lottery open` | 打开抽奖界面 | 所有人 |
| `/lottery help` | 玩法说明 | 所有人 |
| `/lottery pity` | 查看自己的保底进度 | 所有人 |
| `/lottery balance` | 查询余额 | 所有人 |
| `/lottery pool` | 列出两套奖池的概率 | 所有人 |
| `/lottery info` | 查看价格、触发方块、经济来源、命令奖品身份 | 所有人 |
| `/lottery pity <玩家>` | 查看别人的保底进度 | OP（2 级） |
| `/lottery give <玩家> <金额>` | 发币（仅内置钱包） | OP（2 级） |
| `/lottery take <玩家> <金额>` | 扣币（仅内置钱包） | OP（2 级） |
| `/lottery reload` | 重新读取配置 | OP（2 级） |

## 常见问题

- **为什么不是一个新方块？** 新方块需要客户端也装模组才能显示。为了"服务器装了就够"，
  抽奖机用原版方块做触发点（默认磁石，可换），界面用原版箱子界面，效果全用原版粒子和音效。
- **扣了钱但没看到奖品？** 先看聊天栏的中奖记录；物品会直接进背包，背包满了会掉在脚下。
- **普通玩家能拿到命令类奖品吗？** 能。奖品里的 `commands` 默认以玩家为执行者、临时借用管理员权限来跑，
  所以 `give %player% diamond 1`、`lp user %player% permission set vip` 这类命令对非管理员玩家也生效，
  不需要给玩家发 OP。`/lottery info` 里能看到当前的执行身份。
  极少数插件会按玩家 UUID 单独查自己的权限表（不看命令来源），那种命令属于插件自己的限制，
  需要在那个插件里给玩家加上对应权限节点。
- **命令奖品没发出去怎么排查？** 服务器日志里搜 `[抽奖机] 奖品命令执行失败`，后面会写明命令原文和失败原因；
  如果提示和权限/控制台有关，给这条奖品加 `"commandAs": "console"` 再 `/lottery reload` 试试。
- **余额不足？** 装 Savs 时用 `/pay`/管理员命令充值；内置钱包用 `/lottery give`。
- **想换抽奖机外观方块？** 改 `machine.block`，例如 `minecraft:redstone_block`。
