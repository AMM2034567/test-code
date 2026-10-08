"""胡牌判定。

支持的和牌牌型：

- ``standard``         四副（顺子/刻子）+ 一对将
- ``seven_pairs``      七对子（仅 14 张门清时，四张相同计为两对）
- ``thirteen_orphans`` 十三幺（13 种幺九字牌各一张，其中一种成对）
"""

from __future__ import annotations

from typing import Iterable, List, Sequence

from .tiles import ALL_CODES, TERMINAL_HONOR_CODES, tile_index

PATTERN_STANDARD = "standard"
PATTERN_SEVEN_PAIRS = "seven_pairs"
PATTERN_THIRTEEN_ORPHANS = "thirteen_orphans"

_MELD_SIZE = 3
_HAND_SIZE = 14
_TERMINAL_HONOR_INDEXES = frozenset(tile_index(code) for code in TERMINAL_HONOR_CODES)


def _count_array(codes: Iterable[str]) -> List[int]:
    counts = [0] * 34
    for code in codes:
        counts[tile_index(code)] += 1
    return counts


def _can_form_melds(counts: List[int]) -> bool:
    """剩余牌能否全部拆成顺子/刻子（回溯搜索）。"""
    i = 0
    while i < len(counts) and counts[i] == 0:
        i += 1
    if i == len(counts):
        return True
    # 刻子
    if counts[i] >= _MELD_SIZE:
        counts[i] -= _MELD_SIZE
        ok = _can_form_melds(counts)
        counts[i] += _MELD_SIZE
        if ok:
            return True
    # 顺子：仅数牌，且必须是同花色的起点（i 为最左侧非零，只能当首张）
    if i < 27 and (i % 9) <= 6 and counts[i + 1] > 0 and counts[i + 2] > 0:
        counts[i] -= 1
        counts[i + 1] -= 1
        counts[i + 2] -= 1
        ok = _can_form_melds(counts)
        counts[i] += 1
        counts[i + 1] += 1
        counts[i + 2] += 1
        if ok:
            return True
    return False


def _is_standard(counts: List[int]) -> bool:
    for pair in range(len(counts)):
        if counts[pair] < 2:
            continue
        counts[pair] -= 2
        ok = _can_form_melds(counts)
        counts[pair] += 2
        if ok:
            return True
    return False


def _is_seven_pairs(counts: List[int]) -> bool:
    if sum(counts) != 14:
        return False
    pairs = 0
    for count in counts:
        if count not in (0, 2, 4):
            return False
        pairs += count // 2
    return pairs == 7


def _is_thirteen_orphans(counts: List[int]) -> bool:
    if sum(counts) != 14:
        return False
    pairs = 0
    for index in range(34):
        count = counts[index]
        if index in _TERMINAL_HONOR_INDEXES:
            if count == 2:
                pairs += 1
            elif count != 1:
                return False
        elif count != 0:
            return False
    return pairs == 1


def is_valid_meld(codes: Sequence[str]) -> bool:
    """一组副露是否为合法刻子或顺子。"""
    if len(codes) != _MELD_SIZE:
        return False
    try:
        indexes = sorted(tile_index(code) for code in codes)
    except ValueError:
        return False
    if len(set(indexes)) == 1:
        return True
    first, second, third = indexes
    if first >= 27:  # 字牌无顺子
        return False
    return second == first + 1 and third == first + 2 and first // 9 == third // 9


def win_patterns(hand: Iterable[str], melds: Iterable[Iterable[str]] = ()) -> List[str]:
    """返回手牌能构成的和牌牌型列表（可能同时命中多种）。"""
    hand_list = list(hand)
    meld_list = [list(meld) for meld in melds]
    total = len(hand_list) + _MELD_SIZE * len(meld_list)
    if total != _HAND_SIZE:
        raise ValueError(f"和牌需要 {_HAND_SIZE} 张（含副露），当前 {total} 张")

    counts = _count_array(hand_list)
    patterns: List[str] = []
    if _is_standard(counts):
        patterns.append(PATTERN_STANDARD)
    if not meld_list:
        if _is_seven_pairs(counts):
            patterns.append(PATTERN_SEVEN_PAIRS)
        if _is_thirteen_orphans(counts):
            patterns.append(PATTERN_THIRTEEN_ORPHANS)
    return patterns


def is_win(hand: Iterable[str], melds: Iterable[Iterable[str]] = ()) -> bool:
    """是否和牌。"""
    return bool(win_patterns(hand, melds))


def winning_tiles(hand: Iterable[str], melds: Iterable[Iterable[str]] = ()) -> List[str]:
    """听牌时能和的牌，按牌种类编号升序返回。"""
    hand_list = list(hand)
    meld_list = [list(meld) for meld in melds]
    total = len(hand_list) + _MELD_SIZE * len(meld_list)
    if total != _HAND_SIZE - 1:
        raise ValueError(f"听牌需要 {_HAND_SIZE - 1} 张（含副露），当前 {total} 张")
    return [code for code in ALL_CODES if is_win(hand_list + [code], meld_list)]
