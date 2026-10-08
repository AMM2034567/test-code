"""洗牌、发牌与对局流程。

流程约定（国标/常见四人麻将简化规则，无王牌区）：

1. ``new_game`` 洗牌并发牌：庄家 14 张，其余各家 13 张，剩余 83 张为牌山；
2. 当前玩家若手牌为 13-3×副露 张，先 ``draw_tile`` 摸牌；
3. ``discard_tile`` 打出一张并轮转到下家；
4. ``declare_win`` 自摸和牌，或牌山摸完后 ``declare_draw`` 流局。
"""

from __future__ import annotations

import random
from typing import List, Optional, Sequence, Tuple

from .state import GameState, PlayerState, StateError
from .tiles import build_wall, sort_tiles
from .win import winning_tiles, win_patterns

_SEATS = 4
_MELD_SIZE = 3


def shuffle_tiles(tiles: Sequence[str], rng: Optional[random.Random] = None) -> List[str]:
    """Fisher–Yates 洗牌，返回新列表；可用 rng 注入以便测试复现。"""
    deck = list(tiles)
    source = rng if rng is not None else random.Random()
    for i in range(len(deck) - 1, 0, -1):
        j = source.randrange(i + 1)
        deck[i], deck[j] = deck[j], deck[i]
    return deck


def deal_tiles(
    deck: Sequence[str], dealer: int = 0, num_players: int = _SEATS
) -> Tuple[List[List[str]], List[str]]:
    """发牌：庄家 14 张，其余各家 13 张。返回 (各家手牌, 剩余牌山)。"""
    if num_players != _SEATS:
        raise ValueError(f"仅支持 {_SEATS} 人麻将")
    if not 0 <= dealer < num_players:
        raise ValueError(f"庄家座位号非法: {dealer}")
    if len(deck) < 13 * num_players + 1:
        raise ValueError("牌数量不足以发牌")

    hands: List[List[str]] = [[] for _ in range(num_players)]
    cursor = 0

    def take(count: int) -> List[str]:
        nonlocal cursor
        tiles = list(deck[cursor : cursor + count])
        cursor += count
        return tiles

    for _ in range(3):  # 每轮每家 4 张
        for seat in range(num_players):
            hands[seat].extend(take(4))
    for seat in range(num_players):  # 补齐各家第 13 张
        hands[seat].extend(take(1))
    hands[dealer].append(take(1)[0])  # 庄家第 14 张
    return hands, list(deck[cursor:])


def new_game(
    dealer: int = 0, seed: Optional[int] = None, rng: Optional[random.Random] = None
) -> GameState:
    """洗牌 + 发牌，返回可序列化的初始对局状态。"""
    source = rng if rng is not None else random.Random(seed)
    deck = shuffle_tiles(build_wall(), source)
    hands, wall = deal_tiles(deck, dealer=dealer)
    players = [
        PlayerState(seat=seat, hand=sort_tiles(hands[seat])) for seat in range(_SEATS)
    ]
    state = GameState(
        players=players,
        wall=wall,
        dealer=dealer,
        turn=dealer,
        phase="playing",
    )
    state.validate()
    return state


def draw_tile(state: GameState) -> str:
    """当前玩家从牌山摸一张牌。"""
    state.validate()
    player = state.player(state.turn)
    expected = 13 - _MELD_SIZE * len(player.melds)
    if len(player.hand) != expected:
        raise StateError(
            f"座位 {state.turn} 应先打牌再摸牌（当前 {len(player.hand)} 张，应为 {expected} 张）"
        )
    if not state.wall:
        raise StateError("牌山已空，无法摸牌（流局）")
    tile = state.wall.pop()
    player.hand = sort_tiles(player.hand + [tile])
    state.last_action = f"draw:{tile}"
    return tile


def discard_tile(state: GameState, code: str) -> str:
    """当前玩家打出一张手牌并轮转到下家。"""
    state.validate()
    player = state.player(state.turn)
    expected = 14 - _MELD_SIZE * len(player.melds)
    if len(player.hand) != expected:
        raise StateError(
            f"座位 {state.turn} 应先摸牌再打牌（当前 {len(player.hand)} 张，应为 {expected} 张）"
        )
    if code not in player.hand:
        raise StateError(f"座位 {state.turn} 手牌中没有 {code}")
    player.hand.remove(code)
    player.discards.append(code)
    state.turn = (state.turn + 1) % _SEATS
    state.last_action = f"discard:{code}"
    return code


def declare_win(state: GameState, seat: int) -> bool:
    """当前对局中该玩家自摸和牌；和牌则置为终局并返回 True。"""
    state.validate()
    if state.phase != "playing":
        raise StateError(f"对局已结束（阶段 {state.phase}），无法和牌")
    player = state.player(seat)
    if len(player.hand) + _MELD_SIZE * len(player.melds) != 14:
        return False
    patterns = win_patterns(player.hand, player.melds)
    if not patterns:
        return False
    state.phase = "finished"
    state.winner = seat
    state.win_patterns = list(patterns)
    state.last_action = f"win:{'+'.join(patterns)}"
    return True


def declare_draw(state: GameState) -> None:
    """牌山摸完无人和牌，流局。"""
    state.validate()
    if state.phase != "playing":
        raise StateError(f"对局已结束（阶段 {state.phase}），无法流局")
    if state.wall:
        raise StateError(f"牌山还有 {len(state.wall)} 张，不能流局")
    state.phase = "finished"
    state.winner = None
    state.win_patterns = []
    state.last_action = "draw_game"


def waits_for(state: GameState, seat: int) -> List[str]:
    """该玩家听的牌：13 张（含副露）时计算，14 张（已摸牌）返回空列表。"""
    state.validate()
    player = state.player(seat)
    total = len(player.hand) + _MELD_SIZE * len(player.melds)
    if total == 13:
        return winning_tiles(player.hand, player.melds)
    if total == 14:
        return []
    raise StateError(f"座位 {seat} 手牌张数非法，无法计算听牌")
