"""对局状态及其序列化（JSON）。

状态可与 JSON 互相转换，序列化结果为确定性输出（键排序、无多余空白），
便于存档、回放以及基于序列化的单元测试。
"""

from __future__ import annotations

import json
from collections import Counter
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional, Sequence

from .tiles import ALL_CODES, COPIES_PER_KIND, TOTAL_TILES, Tile
from .win import is_valid_meld

STATE_VERSION = 1
PHASES: Sequence[str] = ("playing", "finished")
_SEATS = 4
_MELD_SIZE = 3
_OPEN_MELDS_MAX = 4


class StateError(ValueError):
    """状态不合法或无法反序列化。"""


def _expect_str_list(value: Any, where: str) -> List[str]:
    if not isinstance(value, list) or not all(isinstance(item, str) for item in value):
        raise StateError(f"{where} 必须是字符串数组")
    return list(value)


def _expect_int(value: Any, where: str) -> int:
    if not isinstance(value, int) or isinstance(value, bool):
        raise StateError(f"{where} 必须是整数")
    return value


@dataclass
class PlayerState:
    """一名玩家的状态。"""

    seat: int
    hand: List[str] = field(default_factory=list)
    melds: List[List[str]] = field(default_factory=list)
    discards: List[str] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "seat": self.seat,
            "hand": list(self.hand),
            "melds": [list(meld) for meld in self.melds],
            "discards": list(self.discards),
        }

    @classmethod
    def from_dict(cls, data: Any) -> "PlayerState":
        if not isinstance(data, dict):
            raise StateError("玩家状态必须是对象")
        for key in ("seat", "hand", "melds", "discards"):
            if key not in data:
                raise StateError(f"玩家状态缺少字段: {key}")
        seat = _expect_int(data["seat"], "seat")
        hand = _expect_str_list(data["hand"], "hand")
        discards = _expect_str_list(data["discards"], "discards")
        melds_raw = data["melds"]
        if not isinstance(melds_raw, list):
            raise StateError("melds 必须是数组")
        melds = [_expect_str_list(meld, "melds[]") for meld in melds_raw]
        return cls(seat=seat, hand=hand, melds=melds, discards=discards)


@dataclass
class GameState:
    """整局对局状态：牌山 + 4 名玩家 + 流程信息。"""

    players: List[PlayerState] = field(default_factory=list)
    wall: List[str] = field(default_factory=list)
    dealer: int = 0
    turn: int = 0
    phase: str = "playing"
    winner: Optional[int] = None
    win_patterns: List[str] = field(default_factory=list)
    last_action: Optional[str] = None
    version: int = STATE_VERSION

    # ---------- 序列化 ----------

    def to_dict(self) -> Dict[str, Any]:
        return {
            "version": self.version,
            "phase": self.phase,
            "dealer": self.dealer,
            "turn": self.turn,
            "winner": self.winner,
            "win_patterns": list(self.win_patterns),
            "last_action": self.last_action,
            "wall": list(self.wall),
            "players": [player.to_dict() for player in self.players],
        }

    @classmethod
    def from_dict(cls, data: Any, *, validate: bool = True) -> "GameState":
        if not isinstance(data, dict):
            raise StateError("状态必须是对象")
        for key in ("players", "wall"):
            if key not in data:
                raise StateError(f"状态缺少字段: {key}")
        version = data.get("version", STATE_VERSION)
        if version != STATE_VERSION:
            raise StateError(f"不支持的状态版本: {version!r}")
        players_raw = data["players"]
        if not isinstance(players_raw, list):
            raise StateError("players 必须是数组")
        players = [PlayerState.from_dict(player) for player in players_raw]
        wall = _expect_str_list(data["wall"], "wall")
        phase = data.get("phase", "playing")
        if not isinstance(phase, str):
            raise StateError("phase 必须是字符串")
        winner = data.get("winner")
        if winner is not None:
            winner = _expect_int(winner, "winner")
        win_patterns = data.get("win_patterns", [])
        win_patterns = _expect_str_list(win_patterns, "win_patterns")
        last_action = data.get("last_action")
        if last_action is not None and not isinstance(last_action, str):
            raise StateError("last_action 必须是字符串或 null")
        state = cls(
            players=players,
            wall=wall,
            dealer=_expect_int(data.get("dealer", 0), "dealer"),
            turn=_expect_int(data.get("turn", 0), "turn"),
            phase=phase,
            winner=winner,
            win_patterns=win_patterns,
            last_action=last_action,
            version=version,
        )
        if validate:
            state.validate()
        return state

    def to_json(self) -> str:
        return json.dumps(
            self.to_dict(), ensure_ascii=False, sort_keys=True, separators=(",", ":")
        )

    @classmethod
    def from_json(cls, text: str, *, validate: bool = True) -> "GameState":
        if not isinstance(text, str):
            raise StateError("JSON 状态必须是字符串")
        try:
            data = json.loads(text)
        except json.JSONDecodeError as exc:
            raise StateError(f"JSON 解析失败: {exc}") from exc
        return cls.from_dict(data, validate=validate)

    # ---------- 校验 ----------

    def validate(self) -> None:
        """校验状态合法性，不合法时抛 StateError。"""
        if self.version != STATE_VERSION:
            raise StateError(f"不支持的状态版本: {self.version!r}")
        if len(self.players) != _SEATS:
            raise StateError(f"必须是 {_SEATS} 名玩家，当前 {len(self.players)}")
        for index, player in enumerate(self.players):
            if player.seat != index:
                raise StateError(f"座位号应为 {index}，实际 {player.seat}")
        if self.phase not in PHASES:
            raise StateError(f"非法对局阶段: {self.phase!r}")
        for name, value in (("dealer", self.dealer), ("turn", self.turn)):
            if not isinstance(value, int) or isinstance(value, bool) or not 0 <= value < _SEATS:
                raise StateError(f"{name} 必须是 0-{_SEATS - 1} 的座位号: {value!r}")
        if self.winner is not None:
            if not isinstance(self.winner, int) or isinstance(self.winner, bool) or not 0 <= self.winner < _SEATS:
                raise StateError(f"winner 必须是 0-{_SEATS - 1} 的座位号或 null: {self.winner!r}")
            if self.phase != "finished":
                raise StateError("winner 只能出现在终局状态")
        if self.win_patterns and self.phase != "finished":
            raise StateError("win_patterns 只能出现在终局状态")
        if not isinstance(self.wall, list) or not all(isinstance(tile, str) for tile in self.wall):
            raise StateError("wall 必须是字符串数组")

        counts: Counter[int] = Counter()

        def scan(codes: Sequence[str], where: str) -> None:
            for code in codes:
                if not isinstance(code, str):
                    raise StateError(f"{where} 中存在非字符串的牌: {code!r}")
                try:
                    counts[Tile.from_code(code).index] += 1
                except ValueError as exc:
                    raise StateError(f"{where} 中存在非法牌编码: {code!r}") from exc

        scan(self.wall, "牌山")
        for player in self.players:
            where = f"座位 {player.seat}"
            if len(player.melds) > _OPEN_MELDS_MAX:
                raise StateError(f"{where} 副露超过 {_OPEN_MELDS_MAX} 组")
            for meld in player.melds:
                scan(meld, f"{where} 副露")
                if not is_valid_meld(meld):
                    raise StateError(f"{where} 的副露非法: {meld}")
            scan(player.hand, f"{where} 手牌")
            scan(player.discards, f"{where} 牌河")
            allowed = (13 - _MELD_SIZE * len(player.melds), 14 - _MELD_SIZE * len(player.melds))
            if len(player.hand) not in allowed:
                raise StateError(
                    f"{where} 手牌张数非法: {len(player.hand)}（副露 {len(player.melds)} 组时应为"
                    f" {allowed[0]} 或 {allowed[1]} 张）"
                )
        for index, total in counts.items():
            if total > COPIES_PER_KIND:
                raise StateError(
                    f"{ALL_CODES[index]} 共出现 {total} 次，超过 {COPIES_PER_KIND} 张"
                )
        total_tiles = sum(counts.values())
        if total_tiles > TOTAL_TILES:
            raise StateError(f"牌总数 {total_tiles} 超过 {TOTAL_TILES} 张")

    # ---------- 便捷访问 ----------

    def player(self, seat: int) -> PlayerState:
        if not isinstance(seat, int) or isinstance(seat, bool) or not 0 <= seat < len(self.players):
            raise StateError(f"非法座位号: {seat!r}")
        return self.players[seat]
