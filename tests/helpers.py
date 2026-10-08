"""测试辅助：把手牌 + 副露组装成可序列化的完整对局状态 JSON。"""

from __future__ import annotations

import json
from collections import Counter
from typing import Dict, Iterable, List, Optional, Sequence

from mahjong.tiles import ALL_CODES, sort_tiles


def build_state_json(
    hand: Iterable[str],
    melds: Sequence[Iterable[str]] = (),
    *,
    seat: int = 0,
    turn: Optional[int] = None,
    dealer: Optional[int] = None,
    phase: str = "playing",
    winner: Optional[int] = None,
    win_patterns: Sequence[str] = (),
    last_action: Optional[str] = None,
) -> str:
    """构造状态 JSON 文本：目标座位放指定手牌，其余三家各 13 张，剩余进牌山。

    牌总数恒为 136 张（每种 4 张补足），因此生成的状态可直接通过校验。
    """
    hand_list = list(hand)
    meld_list = [list(meld) for meld in melds]
    used: Counter[str] = Counter(hand_list)
    for meld in meld_list:
        used.update(meld)
    for code, count in used.items():
        if count > 4:
            raise ValueError(f"{code} 超过 4 张: {count}")

    pool: List[str] = []
    for code in ALL_CODES:
        pool.extend([code] * (4 - used.get(code, 0)))

    other_hands: Dict[int, List[str]] = {}
    cursor = 0
    for other in range(4):
        if other == seat:
            continue
        other_hands[other] = pool[cursor : cursor + 13]
        cursor += 13
    wall = pool[cursor:]

    players = []
    for current in range(4):
        if current == seat:
            players.append(
                {
                    "seat": current,
                    "hand": sort_tiles(hand_list),
                    "melds": [sort_tiles(meld) for meld in meld_list],
                    "discards": [],
                }
            )
        else:
            players.append(
                {
                    "seat": current,
                    "hand": other_hands[current],
                    "melds": [],
                    "discards": [],
                }
            )

    data = {
        "version": 1,
        "phase": phase,
        "dealer": seat if dealer is None else dealer,
        "turn": seat if turn is None else turn,
        "winner": winner,
        "win_patterns": list(win_patterns),
        "last_action": last_action,
        "wall": wall,
        "players": players,
    }
    return json.dumps(data, ensure_ascii=False, sort_keys=True)


def state_from(hand: Iterable[str], melds: Sequence[Iterable[str]] = (), **kwargs):
    """便捷入口：由手牌/副露反序列化出 GameState（走完整 JSON 链路）。"""
    from mahjong.state import GameState

    return GameState.from_json(build_state_json(hand, melds, **kwargs))
