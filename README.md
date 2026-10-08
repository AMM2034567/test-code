# test-code
测试opencode action的仓库

## 麻将核心逻辑

纯 Python 实现（零第三方依赖），包含牌型定义、洗牌、发牌、状态序列化与胡牌判定。

### 目录结构

```
mahjong/
  tiles.py   牌型定义：牌编码/索引/排序、全副牌(34种×4=136张)
  win.py     胡牌判定：四面子一将、七对子、十三幺、听牌张计算
  state.py   对局状态：JSON 序列化/反序列化与合法性校验
  game.py    洗牌、发牌、摸打、和牌与流局流程
tests/
  helpers.py                 由手牌/副露生成完整可序列化状态
  test_tiles.py              牌型定义测试
  test_shuffle_deal.py       洗牌/发牌测试
  test_win.py                胡牌判定测试（含随机构造牌例）
  test_state_serialization.py 状态序列化往返与非法状态拒绝
  test_flow.py               摸打/和牌/流局流程测试
```

### 牌编码

| 类别 | 编码 | 示例 |
|------|------|------|
| 万 | `1m`~`9m` | `5m` = 5万 |
| 筒 | `1p`~`9p` | `9p` = 9筒 |
| 条 | `1s`~`9s` | `1s` = 1条 |
| 字 | `1z`~`7z` | 东南西北中发白，`5z` = 中 |

### 用法示例

```python
from mahjong import GameState, new_game, draw_tile, discard_tile, declare_win

state = new_game(seed=42)            # 洗牌 + 发牌（庄家14张，其余13张，牌山83张）
text = state.to_json()               # 序列化（确定性输出，可存档/回放）
state = GameState.from_json(text)    # 反序列化并校验，可继续对局

discard_tile(state, state.player(0).hand[0])   # 庄家打牌
tile = draw_tile(state)                        # 下家摸牌，手牌满 14 张
# declare_win(state, state.turn)              # 若手牌可和则置为终局并返回 True
# declare_draw(state)                          # 牌山摸完无人和牌 → 流局
```

### 运行测试

```bash
python3 -m unittest discover -s tests -t . -v
```
