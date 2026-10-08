"""牌型定义的单元测试（含基于状态序列化的验证）。"""

import unittest
from collections import Counter

from mahjong import (
    ALL_CODES,
    COPIES_PER_KIND,
    GameState,
    KIND_COUNT,
    TERMINAL_HONOR_CODES,
    TOTAL_TILES,
    Tile,
    build_wall,
    parse_tile,
    sort_tiles,
    tile_index,
    tile_name,
    validate_codes,
)
from tests.helpers import build_state_json

SERIALIZED_HAND = ["9s", "1m", "5z", "9m", "1z", "2p", "3p", "4p", "5s", "6s", "7s", "7z", "2z"]
SORTED_HAND = [
    "1m",
    "9m",
    "2p",
    "3p",
    "4p",
    "5s",
    "6s",
    "7s",
    "9s",
    "1z",
    "2z",
    "5z",
    "7z",
]


class TileDefinitionTest(unittest.TestCase):
    def test_all_codes(self):
        self.assertEqual(len(ALL_CODES), KIND_COUNT)
        self.assertEqual(len(set(ALL_CODES)), KIND_COUNT)

    def test_index_is_dense_and_ordered(self):
        for expected, code in enumerate(ALL_CODES):
            self.assertEqual(tile_index(code), expected)
            self.assertEqual(Tile.from_code(code).code, code)
        self.assertEqual(sorted(tile_index(code) for code in ALL_CODES), list(range(KIND_COUNT)))

    def test_full_wall_composition(self):
        wall = build_wall()
        self.assertEqual(len(wall), TOTAL_TILES)
        counter = Counter(wall)
        self.assertEqual(len(counter), KIND_COUNT)
        self.assertTrue(all(count == COPIES_PER_KIND for count in counter.values()))

    def test_tile_names(self):
        self.assertEqual(tile_name("1m"), "1万")
        self.assertEqual(tile_name("9p"), "9筒")
        self.assertEqual(tile_name("5s"), "5条")
        self.assertEqual(tile_name("1z"), "东")
        self.assertEqual(tile_name("5z"), "中")
        self.assertEqual(tile_name("7z"), "白")

    def test_tile_properties(self):
        self.assertTrue(Tile.from_code("1z").is_honor)
        self.assertFalse(Tile.from_code("5m").is_honor)
        self.assertTrue(Tile.from_code("9s").is_terminal)
        self.assertTrue(Tile.from_code("1m").is_terminal)
        self.assertFalse(Tile.from_code("5m").is_terminal)
        self.assertEqual(len(TERMINAL_HONOR_CODES), 13)

    def test_invalid_codes_raise(self):
        for code in ["", "0m", "10m", "1x", "8z", "z1", "m1", " 1m", "1M"]:
            with self.subTest(code=code):
                with self.assertRaises(ValueError):
                    parse_tile(code)
        with self.assertRaises(ValueError):
            Tile.from_code(5)

    def test_sort_and_validate(self):
        tiles = ["9s", "1m", "5z", "9m", "1z"]
        self.assertEqual(
            sort_tiles(tiles), ["1m", "9m", "9s", "1z", "5z"]
        )
        validate_codes(tiles)
        with self.assertRaises(ValueError):
            validate_codes(["1m", "xx"])


class SerializedTileTest(unittest.TestCase):
    """通过状态序列化链路验证牌编码。"""

    def test_hand_round_trip_and_sorting(self):
        state = GameState.from_json(build_state_json(SERIALIZED_HAND))
        self.assertEqual(state.player(0).hand, SORTED_HAND)
        for code in state.player(0).hand:
            self.assertIn(code, ALL_CODES)

    def test_json_round_trip_keeps_tiles(self):
        original = GameState.from_json(build_state_json(SERIALIZED_HAND))
        restored = GameState.from_json(original.to_json())
        self.assertEqual(restored.player(0).hand, original.player(0).hand)
        self.assertEqual(
            [tile_index(code) for code in restored.player(0).hand],
            sorted(tile_index(code) for code in restored.player(0).hand),
        )

    def test_deserialized_wall_preserves_order(self):
        state = GameState.from_json(build_state_json(SERIALIZED_HAND))
        restored = GameState.from_json(state.to_json())
        self.assertEqual(restored.wall, state.wall)


if __name__ == "__main__":
    unittest.main()
