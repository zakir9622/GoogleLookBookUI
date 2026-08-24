"""
Regression coverage for a real, confirmed bug: tapping a News headline used to write into
GenerativeViewModel's single shared `prompt` StateFlow — the same flow every studio tab
(Image/Video/Code/Audio) reads — so a headline tap silently overwrote whatever the user had
typed in the currently-bound studio tab. Fixed by removing the two dead
`generativeViewModel.setPrompt(...)` calls in HomeScreen.kt/VestraNavHost.kt; NewsChatScreen
already manages its own local chat input and never needed that write.

These tests exercise the underlying per-tab session isolation (`GenerativeViewModel.bindStudio` /
`StudioBag`) end to end, and specifically reproduce the headline-tap scenario so a regression of
either bug shows up as a real UI assertion failure, not just a code read.
"""

from conftest import by_tag, tag_exists

HOME_TAB_IMAGE = "home_tab_image"
HOME_TAB_VIDEO = "home_tab_video"
HOME_TAB_CODE = "home_tab_code"
HOME_TAB_AUDIO = "home_tab_audio"
HOME_TAB_NEWS = "home_tab_news"

PROMPT_INPUT = "composer_prompt_input"
CHAT_HEADLINE_0 = "chat_headline_0"


def _goto_tab(driver, tab_tag: str):
    by_tag(driver, tab_tag).click()


def _read_prompt_text(driver) -> str:
    field = by_tag(driver, PROMPT_INPUT)
    # Compose OutlinedTextField's current text surfaces via the "text" attribute on Android.
    return field.get_attribute("text") or ""


def _type_prompt(driver, text: str):
    field = by_tag(driver, PROMPT_INPUT)
    field.clear()
    field.send_keys(text)


class TestPromptIsolation:
    def test_prompt_does_not_leak_between_studio_tabs(self, driver):
        marker = "UNIQUE_IMAGE_PROMPT_7f3a"

        _goto_tab(driver, HOME_TAB_IMAGE)
        _type_prompt(driver, marker)
        assert marker in _read_prompt_text(driver)

        _goto_tab(driver, HOME_TAB_VIDEO)
        video_text = _read_prompt_text(driver)
        assert marker not in video_text, (
            f"Video tab's prompt box contains text from the Image tab: {video_text!r}"
        )

        _goto_tab(driver, HOME_TAB_CODE)
        code_text = _read_prompt_text(driver)
        assert marker not in code_text, (
            f"Code tab's prompt box contains text from the Image tab: {code_text!r}"
        )

        _goto_tab(driver, HOME_TAB_AUDIO)
        audio_text = _read_prompt_text(driver)
        assert marker not in audio_text, (
            f"Audio tab's prompt box contains text from the Image tab: {audio_text!r}"
        )

        # Isolation must also mean the Image tab's own text survives the round trip — not just
        # that it's absent elsewhere (a bug that clears everything on tab switch would also pass
        # the three assertions above).
        _goto_tab(driver, HOME_TAB_IMAGE)
        assert marker in _read_prompt_text(driver), (
            "Image tab's own prompt was lost after switching away and back"
        )

    def test_tapping_a_news_headline_does_not_pollute_other_tabs(self, driver):
        """Direct regression test for the fixed bug."""
        marker = "UNIQUE_VIDEO_PROMPT_9c1e"

        _goto_tab(driver, HOME_TAB_VIDEO)
        _type_prompt(driver, marker)
        assert marker in _read_prompt_text(driver)

        _goto_tab(driver, HOME_TAB_NEWS)
        if tag_exists(driver, CHAT_HEADLINE_0):
            by_tag(driver, CHAT_HEADLINE_0).click()

        _goto_tab(driver, HOME_TAB_VIDEO)
        video_text = _read_prompt_text(driver)
        assert marker in video_text, (
            "Video tab's prompt was overwritten after visiting News/Chat and tapping a headline "
            f"— got {video_text!r}, expected it to still contain {marker!r}"
        )
        assert "Discuss" not in video_text, (
            f"Video tab's prompt box picked up News/Chat's headline text: {video_text!r}"
        )
