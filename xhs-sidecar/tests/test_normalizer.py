import unittest

from xhs_sidecar.normalizer import normalize_note
from xhs_sidecar.security import access_xhs_url


class NormalizerTests(unittest.TestCase):
    def test_removes_tokens_and_personal_profile_fields(self) -> None:
        item = {
            "id": "note-1",
            "note_card": {
                "type": "normal",
                "title": "体验",
                "desc": "使用后发红",
                "time": 1_700_000_000_000,
                "user": {"user_id": "raw-user", "nickname": "raw-name", "avatar": "raw-avatar"},
                "interact_info": {
                    "liked_count": "12",
                    "collected_count": "3",
                    "comment_count": "2",
                    "share_count": "1",
                },
                "tag_list": [{"name": "护肤"}],
                "image_list": [
                    {"url_default": "https://sns-img-qc.xhscdn.com/one.jpg"},
                    {"info_list": [{"url": "https://sns-img-qc.xhscdn.com/two.jpg"}]},
                ],
            },
        }
        comments = [
            {
                "id": "comment-1",
                "content": "同样的问题",
                "user_info": {"user_id": "raw-comment-user", "nickname": "comment-name"},
                "like_count": 2,
                "create_time": 1_700_000_001_000,
            }
        ]

        result = normalize_note(
            item,
            "https://www.xiaohongshu.com/explore/note-1",
            comments,
            "test-secret",
            "https://www.xiaohongshu.com/explore/note-1?xsec_token=secret&xsec_source=pc_search&unexpected=value",
        )

        serialized = str(result)
        self.assertEqual(result["note_url"], "https://www.xiaohongshu.com/explore/note-1")
        self.assertEqual(
            result["access_url"],
            "https://www.xiaohongshu.com/explore/note-1?xsec_token=secret&xsec_source=pc_search",
        )
        self.assertEqual(len(result["authorId"]), 64)
        self.assertEqual(len(result["comments"][0]["authorId"]), 64)
        self.assertEqual(
            result["images"],
            [
                {"url": "https://sns-img-qc.xhscdn.com/one.jpg"},
                {"url": "https://sns-img-qc.xhscdn.com/two.jpg"},
            ],
        )
        self.assertNotIn("raw-user", serialized)
        self.assertNotIn("raw-comment-user", serialized)
        self.assertNotIn("raw-name", serialized)
        self.assertNotIn("unexpected", serialized)

    def test_rejects_untrusted_access_url_hosts_and_paths(self) -> None:
        self.assertEqual(
            access_xhs_url(
                "https://evilxiaohongshu.com/explore/note-1?xsec_token=value",
                "note-1",
            ),
            "",
        )
        self.assertEqual(
            access_xhs_url(
                "https://www.xiaohongshu.com/explore/other?xsec_token=value",
                "note-1",
            ),
            "",
        )


if __name__ == "__main__":
    unittest.main()
