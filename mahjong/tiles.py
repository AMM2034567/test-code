"""牌型定义。

牌编码规则（与主流麻将平台一致的紧凑字符串编码）::

    万(m)  1m..9m
    筒(p)  1p..9p
    条(s)  1s..9s
    字(z)  1z..7z  东南西北中发白

全副牌共 34 种、136 张，每种 4 张。
"""

from __future__ import annotations

import re
from typing import Dict, Iterable, List, NamedTuple, Sequence, Tuple

SUITS: Tuple[str, ...] = ("m", "p", "s")
HONOR: str = "z"

SUIT_NAMES: Dict[str, str] = {"m": "万", "p": "筒", "s": "条"}
HONOR_NAMES: Tuple[str, ...] = ("东", "南", "西", "北", "中", "发", "白")

_TILE_RE = re.compile(r"^(?:([1-9])[mps]|([1-7])z)$")

_OFFSETS: Dict[str, int] = {"m": 0, "p": 9, "s": 18, "z": 27}
KIND_COUNT = 34
TOTAL_TILES = 136
COPIES_PER_KIND = 4


class Tile(NamedTuple):
    """一张牌：花色 + 序数。"""

    suit: str
    rank: int

    @property
    def code(self) -> str:
        return f"{self.rank}{self.suit}"

    @property
    def index(self) -> int:
        """在 0..33 的牌种类编号。"""
        return _OFFSETS[self.suit] + self.rank - 1

    @property
    def name(self) -> str:
        """中文牌名，如 1万 / 中。"""
        if self.suit == HONOR:
            return HONOR_NAMES[self.rank - 1]
        return f"{self.rank}{SUIT_NAMES[self.suit]}"

    @property
    def is_honor(self) -> bool:
        return self.suit == HONOR

    @property
    def is_terminal(self) -> bool:
        """幺九牌：序数 1/9 或字牌。"""
        return self.is_honor or self.rank in (1, 9)

    @classmethod
    def from_code(cls, code: str) -> "Tile":
        if not isinstance(code, str):
            raise ValueError(f"牌编码必须是字符串: {code!r}")
        match = _TILE_RE.match(code)
        if match is None:
            raise ValueError(f"非法牌编码: {code!r}")
        if match.group(1) is not None:
            return cls(suit=code[1], rank=int(match.group(1)))
        return cls(suit=HONOR, rank=int(match.group(2)))


ALL_CODES: Tuple[str, ...] = tuple(
    [Tile(suit, rank).code for suit in SUITS for rank in range(1, 10)]
    + [Tile(HONOR, rank).code for rank in range(1, 8)]
)

# 十三幺所需的 13 种幺九/字牌
TERMINAL_HONOR_CODES: Tuple[str, ...] = tuple(
    code for code in ALL_CODES if Tile.from_code(code).is_terminal
)


def parse_tile(code: str) -> Tile:
    """解析牌编码，非法输入抛 ValueError。"""
    return Tile.from_code(code)


def tile_index(code: str) -> int:
    """牌种类编号 0..33。"""
    return Tile.from_code(code).index


def tile_name(code: str) -> str:
    """牌的中文名。"""
    return Tile.from_code(code).name


def sort_tiles(codes: Iterable[str]) -> List[str]:
    """按 万→筒→条→字、序数升序排列。"""
    return sorted(codes, key=tile_index)


def build_wall() -> List[str]:
    """构建未洗牌的全副牌（136 张）。"""
    return [code for code in ALL_CODES for _ in range(COPIES_PER_KIND)]


def validate_codes(codes: Sequence[str]) -> None:
    """校验一组牌编码均可解析，非法输入抛 ValueError。"""
    for code in codes:
        Tile.from_code(code)
