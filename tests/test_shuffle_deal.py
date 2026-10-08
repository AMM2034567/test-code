"""洗牌与发牌的单元测试（含基于状态序列化的验证）。"""

import random
import unittest
from collections import Counter

from mahjong import (
    COPIES_PER_KIND,
    TOTAL_TILES,
    GameState,
    StateError,
    build_wall,
    deal_tiles,
    discard_tile,
    draw_tile,
    new_game,
    shuffle_tiles,
    sort_tiles,
)


def state_tile_counter(state: GameState) -> Counter:
    counter: Counter = Counter(state.wall)
    for player in state.players:
        counter.update(player.hand)
        counter.update(player.discards)
        for meld in player.melds:
            counter.update(meld)
    return counter


class ShuffleTest(unittest.TestCase):
    def setUp(self):
        self.wall = build_wall()

    def test_shuffle_is_permutation(self):
        shuffled = shuffle_tiles(self.wall, random.Random(42))
        self.assertEqual(len(shuffled), TOTAL_TILES)
        self.assertEqual(Counter(shuffled), Counter(self.wall))

    def test_shuffle_does_not_mutate_input(self):
        before = list(self.wall)
        shuffle_tiles(self.wall, random.Random(1))
        self.assertEqual(self.wall, before)

    def test_seeded_shuffle_reproducible(self):
        first = shuffle_tiles(self.wall, random.Random(7))
        second = shuffle_tiles(self.wall, random.Random(7))
        self.assertEqual(first, second)
        self.assertNotEqual(first, sort_tiles(first))

    def test_different_seeds_differ(self):
        first = shuffle_tiles(self.wall, random.Random(1))
        second = shuffle_tiles(self.wall, random.Random(2))
        self.assertNotEqual(first, second)


class DealTest(unittest.TestCase):
    def test_deal_counts(self):
        hands, wall = deal_tiles(build_wall(), dealer=0)
        self.assertEqual([len(hand) for hand in hands], [14, 13, 13, 13])
        self.assertEqual(len(wall), 83)
        counter = Counter(tile for hand in hands for tile in hand) + Counter(wall)
        self.assertEqual(sum(counter.values()), TOTAL_TILES)
        self.assertTrue(all(count == COPIES_PER_KIND for count in counter.values()))

    def test_deal_rejects_bad_input(self):
        with self.assertRaises(ValueError):
            deal_tiles(build_wall(), dealer=4)
        with self.assertRaises(ValueError):
            deal_tiles(build_wall(), num_players=3)
        with self.assertRaises(ValueError):
            deal_tiles(build_wall()[:52], dealer=0)


class NewGameTest(unittest.TestCase):
    def test_initial_layout(self):
        state = new_game(seed=20261008)
        self.assertEqual(state.dealer, 0)
        self.assertEqual(state.turn, 0)
        self.assertEqual(state.phase, "playing")
        self.assertEqual([len(p.hand) for p in state.players], [14, 13, 13, 13])
        self.assertEqual(len(state.wall), 83)
        counter = state_tile_counter(state)
        self.assertEqual(sum(counter.values()), TOTAL_TILES)
        self.assertTrue(all(count == COPIES_PER_KIND for count in counter.values()))
        for player in state.players:
            self.assertEqual(player.hand, sort_tiles(player.hand))

    def test_dealer_seat(self):
        state = new_game(dealer=2, seed=7)
        self.assertEqual(state.dealer, 2)
        self.assertEqual(state.turn, 2)
        self.assertEqual([len(p.hand) for p in state.players], [13, 13, 14, 13])

    def test_same_seed_same_state(self):
        first = new_game(seed=99)
        second = new_game(seed=99)
        self.assertEqual(first, second)
        self.assertEqual(first.to_json(), second.to_json())

    def test_different_seeds_differ(self):
        first = new_game(seed=1)
        second = new_game(seed=2)
        self.assertNotEqual(first.wall, second.wall)


class SerializedDealTest(unittest.TestCase):
    """发牌结果经序列化往返后仍可继续对局。"""

    def test_round_trip_equality(self):
        state = new_game(seed=42)
        restored = GameState.from_json(state.to_json())
        self.assertEqual(restored, state)
        self.assertEqual(restored.to_json(), state.to_json())

    def test_restored_game_can_continue(self):
        state = GameState.from_json(new_game(seed=42).to_json())
        dealer = state.turn
        tile = state.players[dealer].hand[0]
        discard_tile(state, tile)
        self.assertNotIn(tile, state.players[dealer].hand)
        self.assertEqual(len(state.players[dealer].hand), 13)
        self.assertEqual(state.players[dealer].discards, [tile])
        self.assertEqual(state.turn, (dealer + 1) % 4)

        drawn = draw_tile(state)
        self.assertEqual(len(state.players[state.turn].hand), 14)
        self.assertIn(drawn, state.players[state.turn].hand)

        again = GameState.from_json(state.to_json())
        self.assertEqual(again, state)
        self.assertEqual(again.last_action, state.last_action)

    def test_draw_requires_discard_first(self):
        state = GameState.from_json(new_game(seed=3).to_json())
        with self.assertRaises(StateError):
            draw_tile(state)  # 庄家起手 14 张，须先打牌

    def test_discard_requires_draw_first(self):
        state = GameState.from_json(new_game(seed=3).to_json())
        discard_tile(state, state.player(0).hand[0])
        before = state.to_json()
        with self.assertRaises(StateError):
            discard_tile(state, state.player(1).hand[0])  # 下家尚未摸牌
        self.assertEqual(state.to_json(), before)  # 失败操作不改动状态

    def test_discard_unknown_tile(self):
        state = GameState.from_json(new_game(seed=3).to_json())
        hand = state.player(0).hand
        missing = next(code for code in build_wall() if code not in hand)
        with self.assertRaises(StateError):
            discard_tile(state, missing)


if __name__ == "__main__":
    unittest.main()
