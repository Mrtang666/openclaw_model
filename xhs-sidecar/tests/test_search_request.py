import unittest

from xhs_sidecar.models import SearchRequest


class SearchRequestTests(unittest.TestCase):
    def test_parses_supported_search_strategy(self) -> None:
        request = SearchRequest.parse(
            {
                "query": "brand",
                "limit": 40,
                "sortMode": "comments",
                "timeRange": "week",
                "noteType": "image",
                "commentLimit": 300,
            }
        )

        self.assertEqual(request.sort_mode, "COMMENTS")
        self.assertEqual(request.time_range, "WEEK")
        self.assertEqual(request.note_type, "IMAGE")
        self.assertEqual(request.comment_limit, 300)

    def test_rejects_unknown_strategy(self) -> None:
        with self.assertRaises(ValueError):
            SearchRequest.parse({"query": "brand", "limit": 20, "sortMode": "random"})


if __name__ == "__main__":
    unittest.main()
