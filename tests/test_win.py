"""胡牌判定的单元测试（牌例以序列化状态 JSON 提供）。"""

import random
import unittest
from collections import Counter

from mahjong import (
    ALL_CODES,
    PATTERN_SEVEN_PAIRS,
    PATTERN_STANDARD,
    PATTERN_THIRTEEN_ORPHANS,
    GameState,
    build_wall,
    is_valid_meld,
    is_win,
    sort_tiles,
    tile_index,
    win_patterns,
    winning_tiles,
)
from tests.helpers import build_state_json, state_from

# ---------- 牌例：可和牌 ----------

WIN_CASES = [
    (
        "四顺子一将",
        ["1m", "2m", "3m", "4m", "5m", "6m", "7m", "8m", "9m", "1p", "1p", "1p", "5s", "5s"],
        [],
        [PATTERN_STANDARD],
    ),
    (
        "含刻子与字牌刻子",
        ["2s", "2s", "2s", "3m", "3m", "3m", "4p", "5p", "6p", "6z", "6z", "6z", "1m", "1m"],
        [],
        [PATTERN_STANDARD],
    ),
    (
        "七对子",
        ["1m", "1m", "2p", "2p", "3s", "3s", "5z", "5z", "6z", "6z", "7s", "7s", "9p", "9p"],
        [],
        [PATTERN_SEVEN_PAIRS],
    ),
    (
        "七对子含四归一",
        ["1m", "1m", "1m", "1m", "2p", "2p", "3s", "3s", "5z", "5z", "7s", "7s", "9p", "9p"],
        [],
        [PATTERN_SEVEN_PAIRS],
    ),
    (
        "十三幺",
        ["1m", "9m", "1p", "9p", "1s", "9s", "1z", "2z", "3z", "4z", "5z", "6z", "7z", "5z"],
        [],
        [PATTERN_THIRTEEN_ORPHANS],
    ),
    (
        "带一副露",
        ["4m", "5m", "6m", "7m", "8m", "9m", "1p", "2p", "3p", "5s", "5s"],
        [["1m", "2m", "3m"]],
        [PATTERN_STANDARD],
    ),
    (
        "带字牌副露",
        ["1m", "1m", "2m", "3m", "4m", "5m", "6m", "7m", "9p", "9p", "9p"],
        [["7z", "7z", "7z"]],
        [PATTERN_STANDARD],
    ),
]

# ---------- 牌例：不可和牌 ----------

NOT_WIN_CASES = [
    (
        "缺将",
        ["1m", "2m", "3m", "4m", "5m", "6m", "7m", "8m", "9m", "1p", "1p", "1p", "5s", "6s"],
        [],
    ),
    (
        "十三幺缺一门",
        ["1m", "9m", "1p", "9p", "1s", "9s", "1z", "2z", "3z", "4z", "5z", "6z", "5z", "5z"],
        [],
    ),
    (
        "对子不足七对",
        ["1m", "1m", "2p", "2p", "3s", "3s", "5z", "5z", "6z", "6z", "7s", "7s", "8p", "9p"],
        [],
    ),
    (
        "差一张未成面子",
        ["1m", "4m", "7m", "2p", "5p", "8p", "3s", "6s", "9s", "1z", "2z", "3z", "4z", "4z"],
        [],
    ),
]


class WinPatternTest(unittest.TestCase):
    def test_win_cases(self):
        for name, hand, melds, expected in WIN_CASES:
            with self.subTest(name=name):
                self.assertTrue(is_win(hand, melds))
                self.assertEqual(win_patterns(hand, melds), expected)

    def test_not_win_cases(self):
        for name, hand, melds in NOT_WIN_CASES:
            with self.subTest(name=name):
                self.assertFalse(is_win(hand, melds))
                self.assertEqual(win_patterns(hand, melds), [])

    def test_wrong_tile_count_raises(self):
        with self.assertRaises(ValueError):
            win_patterns(["1m", "2m", "3m"])
        with self.assertRaises(ValueError):
            is_win(["1m"] * 13)
        with self.assertRaises(ValueError):
            winning_tiles(["1m"] * 14)

    def test_seven_pairs_rejected_when_open_meld(self):
        full = ["1m", "1m", "1m", "1m", "2p", "2p", "3s", "3s", "5z", "5z", "6z", "6z", "7s", "7s"]
        self.assertEqual(win_patterns(full), [PATTERN_SEVEN_PAIRS])
        hand = ["1m", "2p", "2p", "3s", "3s", "5z", "5z", "6z", "6z", "7s", "7s"]
        melds = [["1m", "1m", "1m"]]
        self.assertEqual(win_patterns(hand, melds), [])
        self.assertFalse(is_win(hand, melds))


class ConstructedWinTest(unittest.TestCase):
    """反向构造：随机生成的和牌必须全部被判为可和。"""

    @staticmethod
    def random_standard_hand(rng):
        pool = Counter(build_wall())
        hand = []

        def take(code, count=1):
            if pool[code] < count:
                return False
            pool[code] -= count
            hand.extend([code] * count)
            return True

        while len(hand) < 12:
            if rng.random() < 0.5:
                candidates = [code for code in ALL_CODES if pool[code] >= 3]
                if not candidates:
                    continue
                take(rng.choice(candidates), 3)
            else:
                candidates = [
                    ALL_CODES[i]
                    for i in range(27)
                    if (i % 9) <= 6 and all(pool[ALL_CODES[i + offset]] > 0 for offset in (0, 1, 2))
                ]
                if not candidates:
                    continue
                start = rng.choice(candidates)
                base = tile_index(start)
                for offset in (0, 1, 2):
                    take(ALL_CODES[base + offset])
        pair_candidates = [code for code in ALL_CODES if pool[code] >= 2]
        take(rng.choice(pair_candidates), 2)
        return sort_tiles(hand)

    def test_random_standard_hands(self):
        rng = random.Random(20261008)
        for i in range(300):
            hand = self.random_standard_hand(rng)
            with self.subTest(i=i, hand=hand):
                self.assertEqual(len(hand), 14)
                self.assertTrue(is_win(hand))

    def test_random_hands_via_serialized_state(self):
        rng = random.Random(7)
        for _ in range(50):
            hand = self.random_standard_hand(rng)
            state = state_from(hand)
            self.assertTrue(is_win(state.player(0).hand))

    def test_random_seven_pairs(self):
        rng = random.Random(9)
        for _ in range(100):
            pool = Counter(build_wall())
            pairs = []
            candidates = list(ALL_CODES)
            while len(pairs) < 14:
                code = rng.choice(candidates)
                if pool[code] >= 2:
                    pool[code] -= 2
                    pairs.extend([code, code])
            self.assertTrue(is_win(sort_tiles(pairs)))

    def test_random_standard_hands_never_accept_garbage(self):
        rng = random.Random(3)
        rejected = 0
        for _ in range(200):
            tiles = rng.sample(build_wall(), 14)
            if is_win(tiles):
                continue
            rejected += 1
        self.assertGreater(rejected, 190)  # 随机 14 张几乎不可能和牌


class WaitingTilesTest(unittest.TestCase):
    def test_single_wait(self):
        hand = ["1m", "2m", "3m", "4m", "5m", "6m", "7m", "8m", "9m", "1p", "1p", "1p", "5s"]
        self.assertEqual(winning_tiles(hand), ["5s"])

    def test_two_way_wait(self):
        hand = ["1m", "2m", "3m", "4m", "5m", "6m", "7m", "8m", "9m", "1p", "1p", "5s", "6s"]
        self.assertEqual(winning_tiles(hand), ["4s", "7s"])

    def test_not_tenpai(self):
        hand = ["1m", "4m", "7m", "2p", "5p", "8p", "3s", "6s", "9s", "1z", "2z", "3z", "4z"]
        self.assertEqual(winning_tiles(hand), [])


class SerializedWinTest(unittest.TestCase):
    """所有牌例经状态序列化/反序列化后再判定。"""

    def test_win_cases_via_state(self):
        for name, hand, melds, expected in WIN_CASES:
            with self.subTest(name=name):
                state = state_from(hand, melds)
                player = state.player(0)
                self.assertEqual(win_patterns(player.hand, player.melds), expected)
                self.assertTrue(is_win(player.hand, player.melds))

    def test_not_win_cases_via_state(self):
        for name, hand, melds in NOT_WIN_CASES:
            with self.subTest(name=name):
                state = state_from(hand, melds)
                player = state.player(0)
                self.assertFalse(is_win(player.hand, player.melds))

    def test_waiting_tiles_via_serialized_state(self):
        hand = ["1m", "2m", "3m", "4m", "5m", "6m", "7m", "8m", "9m", "1p", "1p", "1p", "5s"]
        text = build_state_json(hand, seat=0, turn=0, dealer=0)
        state = GameState.from_json(text)
        restored = GameState.from_json(state.to_json())
        self.assertEqual(winning_tiles(restored.player(0).hand), ["5s"])

    def test_meld_shape_validation(self):
        self.assertTrue(is_valid_meld(["1m", "2m", "3m"]))
        self.assertTrue(is_valid_meld(["7z", "7z", "7z"]))
        self.assertTrue(is_valid_meld(["9s", "9s", "9s"]))
        self.assertFalse(is_valid_meld(["1z", "2z", "3z"]))  # 字牌无顺子
        self.assertFalse(is_valid_meld(["1m", "3m", "4m"]))  # 不连续
        self.assertFalse(is_valid_meld(["1m", "1p", "1s"]))  # 不同花色
        self.assertFalse(is_valid_meld(["1m", "2m"]))  # 张数不对
        self.assertFalse(is_valid_meld(["1m", "2m", "2m"]))


if __name__ == "__main__":
    unittest.main()
