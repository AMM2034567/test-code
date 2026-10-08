"""状态序列化（JSON 往返与非法状态拒绝）的单元测试。"""

import json
import unittest

from mahjong import (
    GameState,
    PlayerState,
    STATE_VERSION,
    StateError,
    new_game,
)
from tests.helpers import build_state_json, state_from

WINNING_HAND = ["1m", "2m", "3m", "4m", "5m", "6m", "7m", "8m", "9m", "1p", "1p", "1p", "5s", "5s"]


def base_dict():
    return json.loads(build_state_json(WINNING_HAND))


class RoundTripTest(unittest.TestCase):
    def test_dict_round_trip(self):
        state = GameState.from_json(build_state_json(WINNING_HAND))
        self.assertEqual(GameState.from_dict(state.to_dict()), state)

    def test_json_round_trip_is_stable(self):
        state = new_game(seed=123)
        text = state.to_json()
        restored = GameState.from_json(text)
        self.assertEqual(restored, state)
        self.assertEqual(restored.to_json(), text)
        self.assertEqual(GameState.from_json(restored.to_json()).to_json(), text)

    def test_json_is_canonical(self):
        text = build_state_json(WINNING_HAND)
        state = GameState.from_json(text)
        self.assertEqual(state.to_json(), json.dumps(
            state.to_dict(), ensure_ascii=False, sort_keys=True, separators=(",", ":")
        ))

    def test_to_dict_returns_copies(self):
        state = GameState.from_json(build_state_json(WINNING_HAND))
        data = state.to_dict()
        data["wall"].append("1m")
        data["players"][0]["hand"].append("2m")
        self.assertNotIn("1m", state.wall)
        self.assertEqual(len(state.player(0).hand), 14)

    def test_from_dict_returns_copies(self):
        state = GameState.from_json(build_state_json(WINNING_HAND))
        data = state.to_dict()
        restored = GameState.from_dict(data)
        restored.wall.append("1m")
        restored.player(0).hand.append("2m")
        restored.player(0).melds.append(["3m", "3m", "3m"])
        self.assertEqual(GameState.from_dict(data), state)

    def test_finished_state_round_trip(self):
        state = state_from(WINNING_HAND, seat=0, turn=0, phase="finished", winner=0,
                           win_patterns=["standard"], last_action="win:standard")
        restored = GameState.from_json(state.to_json())
        self.assertEqual(restored.phase, "finished")
        self.assertEqual(restored.winner, 0)
        self.assertEqual(restored.win_patterns, ["standard"])
        self.assertEqual(restored, state)

    def test_version_field(self):
        self.assertEqual(GameState.from_json(build_state_json(WINNING_HAND)).version, STATE_VERSION)


class MalformedJsonTest(unittest.TestCase):
    def test_invalid_json_text(self):
        for text in ["", "{", "[]", '"hello"', "null"]:
            with self.subTest(text=text):
                with self.assertRaises(StateError):
                    GameState.from_json(text)

    def test_non_string_input(self):
        with self.assertRaises(StateError):
            GameState.from_json(b'{"players": [], "wall": []}')

    def test_non_object_root(self):
        with self.assertRaises(StateError):
            GameState.from_dict(["players", "wall"])

    def test_missing_required_fields(self):
        for key in ("players", "wall"):
            data = base_dict()
            del data[key]
            with self.subTest(key=key):
                with self.assertRaises(StateError):
                    GameState.from_dict(data)

    def test_missing_player_fields(self):
        data = base_dict()
        del data["players"][0]["hand"]
        with self.assertRaises(StateError):
            GameState.from_dict(data)

    def test_unsupported_version(self):
        data = base_dict()
        data["version"] = 99
        with self.assertRaises(StateError):
            GameState.from_dict(data)


class InvalidStateTest(unittest.TestCase):
    def test_unknown_tile_code(self):
        data = base_dict()
        data["players"][0]["hand"][0] = "xx"
        with self.assertRaises(StateError):
            GameState.from_dict(data)

    def test_too_many_copies(self):
        data = base_dict()
        data["players"][0]["hand"] = ["1m"] * 14
        with self.assertRaises(StateError):
            GameState.from_dict(data)

    def test_invalid_hand_length(self):
        data = base_dict()
        data["players"][1]["hand"] = data["players"][1]["hand"][:12]
        with self.assertRaises(StateError):
            GameState.from_dict(data)

    def test_invalid_meld_sequence(self):
        with self.assertRaises(StateError):
            state_from(
                ["4m", "5m", "6m", "7m", "8m", "9m", "1p", "2p", "3p", "5s", "5s"],
                [["1z", "2z", "3z"]],
            )

    def test_invalid_meld_shape(self):
        data = base_dict()
        data["players"][2]["melds"] = [["1m", "3m", "4m"]]
        data["players"][2]["hand"] = data["players"][2]["hand"][:11]
        with self.assertRaises(StateError):
            GameState.from_dict(data)

    def test_invalid_phase(self):
        data = base_dict()
        data["phase"] = "paused"
        with self.assertRaises(StateError):
            GameState.from_dict(data)

    def test_invalid_seat_numbers(self):
        for field, value in (("dealer", 9), ("turn", -1), ("winner", 4)):
            data = base_dict()
            data[field] = value
            if field == "winner":
                data["phase"] = "finished"
            with self.subTest(field=field):
                with self.assertRaises(StateError):
                    GameState.from_dict(data)

    def test_winner_requires_finished_phase(self):
        data = base_dict()
        data["winner"] = 0
        with self.assertRaises(StateError):
            GameState.from_dict(data)

    def test_player_count(self):
        data = base_dict()
        data["players"] = data["players"][:3]
        with self.assertRaises(StateError):
            GameState.from_dict(data)

    def test_seat_order(self):
        data = base_dict()
        data["players"][1]["seat"] = 3
        with self.assertRaises(StateError):
            GameState.from_dict(data)

    def test_type_checks(self):
        cases = [
            {"hand": "1m2m3m"},
            {"melds": "abc"},
            {"discards": [1, 2, 3]},
            {"seat": True},
        ]
        for patch in cases:
            data = base_dict()
            data["players"][0].update(patch)
            with self.subTest(patch=patch):
                with self.assertRaises(StateError):
                    GameState.from_dict(data)

    def test_validate_flag_skips_checks(self):
        data = base_dict()
        data["phase"] = "paused"
        state = GameState.from_dict(data, validate=False)
        self.assertEqual(state.phase, "paused")
        with self.assertRaises(StateError):
            state.validate()


class PlayerStateTest(unittest.TestCase):
    def test_round_trip(self):
        player = PlayerState(seat=1, hand=["1m"], melds=[["2p", "2p", "2p"]], discards=["9s"])
        restored = PlayerState.from_dict(player.to_dict())
        self.assertEqual(restored, player)
        self.assertIsNot(restored.hand, player.hand)

    def test_missing_field(self):
        with self.assertRaises(StateError):
            PlayerState.from_dict({"seat": 0, "hand": [], "melds": []})


if __name__ == "__main__":
    unittest.main()
