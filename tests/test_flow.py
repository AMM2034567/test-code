"""对局流程（摸打、和牌、流局）的单元测试，全程经状态序列化。"""

import unittest

from mahjong import (
    GameState,
    StateError,
    declare_draw,
    declare_win,
    discard_tile,
    draw_tile,
    new_game,
    waits_for,
)
from tests.helpers import build_state_json, state_from

WINNING_HAND = ["1m", "2m", "3m", "4m", "5m", "6m", "7m", "8m", "9m", "1p", "1p", "1p", "5s", "5s"]
TENPAI_HAND = ["1m", "2m", "3m", "4m", "5m", "6m", "7m", "8m", "9m", "1p", "1p", "1p", "5s"]


class DrawDiscardFlowTest(unittest.TestCase):
    def restored_game(self, seed=42) -> GameState:
        return GameState.from_json(new_game(seed=seed).to_json())

    def test_full_draw_discard_cycle(self):
        state = self.restored_game()
        first = state.player(0).hand[0]
        discard_tile(state, first)
        self.assertEqual(state.turn, 1)

        drawn = draw_tile(state)
        self.assertIn(drawn, state.player(1).hand)
        self.assertEqual(len(state.player(1).hand), 14)

        second = state.player(1).hand[0]
        if second == drawn:
            second = state.player(1).hand[1]
        discard_tile(state, second)
        self.assertEqual(state.player(1).discards, [second])
        self.assertEqual(state.turn, 2)

        restored = GameState.from_json(state.to_json())
        self.assertEqual(restored, state)
        self.assertEqual(restored.last_action, f"discard:{second}")

    def test_discard_removes_only_one_copy(self):
        state = self.restored_game()
        hand = state.player(0).hand
        tile = next(t for t in hand if hand.count(t) == 1)
        discard_tile(state, tile)
        self.assertNotIn(tile, state.player(0).hand)
        self.assertEqual(state.player(0).discards, [tile])

    def test_wall_shrinks_on_draw(self):
        state = self.restored_game()
        before = len(state.wall)
        discard_tile(state, state.player(0).hand[0])
        draw_tile(state)
        self.assertEqual(len(state.wall), before - 1)

    def test_empty_wall_raises(self):
        state = self.restored_game()
        state.wall = []
        with self.assertRaises(StateError):
            draw_tile(state)


class WinFlowTest(unittest.TestCase):
    def test_declare_win_and_serialize(self):
        state = state_from(WINNING_HAND, seat=0, turn=0, dealer=0)
        self.assertTrue(declare_win(state, 0))
        self.assertEqual(state.phase, "finished")
        self.assertEqual(state.winner, 0)
        self.assertIn("standard", state.win_patterns)
        self.assertTrue(state.last_action.startswith("win:"))

        restored = GameState.from_json(state.to_json())
        self.assertEqual(restored.phase, "finished")
        self.assertEqual(restored.winner, 0)
        self.assertEqual(restored.win_patterns, state.win_patterns)
        self.assertEqual(restored.to_json(), state.to_json())

    def test_declare_win_false_leaves_state_active(self):
        state = state_from(TENPAI_HAND, seat=0, turn=0)
        self.assertFalse(declare_win(state, 0))
        self.assertEqual(state.phase, "playing")
        self.assertIsNone(state.winner)
        self.assertEqual(GameState.from_json(state.to_json()), state)

    def test_declare_win_rejects_finished_game(self):
        state = state_from(WINNING_HAND, seat=0, turn=0, phase="finished", winner=0,
                           win_patterns=["standard"])
        with self.assertRaises(StateError):
            declare_win(state, 0)

    def test_declare_win_with_melds(self):
        hand = ["4m", "5m", "6m", "7m", "8m", "9m", "1p", "2p", "3p", "5s", "5s"]
        state = state_from(hand, [["1m", "2m", "3m"]], seat=0, turn=0)
        self.assertTrue(declare_win(state, 0))
        restored = GameState.from_json(state.to_json())
        self.assertEqual(restored.win_patterns, ["standard"])

    def test_waits_via_serialized_state(self):
        state = GameState.from_json(build_state_json(TENPAI_HAND, seat=0, turn=0))
        self.assertEqual(waits_for(state, 0), ["5s"])
        self.assertEqual(waits_for(GameState.from_json(state.to_json()), 0), ["5s"])

    def test_waits_empty_after_draw(self):
        state = state_from(WINNING_HAND, seat=0, turn=0)
        self.assertEqual(waits_for(state, 0), [])


class ExhaustiveDrawTest(unittest.TestCase):
    def test_drain_wall_then_exhaustive_draw(self):
        state = GameState.from_json(new_game(seed=2026).to_json())
        dealer = state.dealer
        discard_tile(state, state.player(dealer).hand[0])  # 庄家先打
        while state.wall:
            draw_tile(state)
            discard_tile(state, state.player(state.turn).hand[0])
        self.assertEqual(len(state.wall), 0)
        for player in state.players:
            self.assertEqual(len(player.hand), 13)
        total_discards = sum(len(player.discards) for player in state.players)
        self.assertEqual(total_discards, 84)  # 136 张 - 各家手牌 52 张

        declare_draw(state)
        self.assertEqual(state.phase, "finished")
        self.assertIsNone(state.winner)
        self.assertEqual(state.win_patterns, [])

        restored = GameState.from_json(state.to_json())
        self.assertEqual(restored.to_json(), state.to_json())
        self.assertEqual(restored.phase, "finished")

    def test_declare_draw_requires_empty_wall(self):
        state = GameState.from_json(new_game(seed=5).to_json())
        with self.assertRaises(StateError):
            declare_draw(state)

    def test_declare_draw_rejects_finished_game(self):
        state = state_from(WINNING_HAND, seat=0, turn=0, phase="finished", winner=0,
                           win_patterns=["standard"])
        with self.assertRaises(StateError):
            declare_draw(state)


if __name__ == "__main__":
    unittest.main()
