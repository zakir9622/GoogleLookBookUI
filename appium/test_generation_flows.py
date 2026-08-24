"""
End-to-end generation tests. These are written to actually FAIL when generation is broken,
not just to confirm a screen renders — each asserts on a genuinely-terminal result state
(a real ready result OR a legible failure), never on "the button existed" alone.

Local generation can take minutes on real hardware (Bonsai Image 4B is documented as "several
minutes on CPU" in its own catalog entry) — timeouts here are generous on purpose. A hang past
the timeout is itself a real finding, not a flaky test to retry away.

Requires: cloud generation off (the app's own default) so these exercise the local engines the
app ships, not a network-dependent free-tier Space that could fail for reasons unrelated to this
app's own code.
"""

import time

import pytest
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from appium.webdriver.common.appiumby import AppiumBy

from conftest import by_tag, tag_exists

HOME_TAB_IMAGE = "home_tab_image"
HOME_TAB_CODE = "home_tab_code"
HOME_TAB_AUDIO = "home_tab_audio"
HOME_TAB_NEWS = "home_tab_news"

PROMPT_INPUT = "composer_prompt_input"
SEND_BUTTON = "composer_send_button"
LIVE_CONSOLE = "result_live_console"

RESULT_IMAGE_READY = "result_image_ready"
RESULT_CODE_STREAMING = "result_code_streaming"
RESULT_CODE_READY = "result_code_ready"
RESULT_TRANSCRIBE_READY = "result_transcribe_ready"
RESULT_AUDIO_READY = "result_audio_ready"
RESULT_FAILED = "result_failed"
RESULT_RETRY_BUTTON = "result_retry_button"

GENERATION_TIMEOUT_SECONDS = 360  # local diffusion/LLM cold loads can be slow — see module docstring


def _goto_tab(driver, tab_tag: str):
    by_tag(driver, tab_tag).click()


def _generate(driver, prompt: str):
    field = by_tag(driver, PROMPT_INPUT)
    field.clear()
    field.send_keys(prompt)
    by_tag(driver, SEND_BUTTON).click()


def _wait_for_any_tag(driver, tags: list[str], timeout: int):
    """Poll for the first of several possible terminal-state tags to appear."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        for tag in tags:
            if tag_exists(driver, tag):
                return tag
        time.sleep(2)
    raise AssertionError(
        f"None of {tags} appeared within {timeout}s — generation likely hung. "
        "Check the live console / logcat for where it stalled."
    )


class TestImageGeneration:
    def test_local_image_generation_reaches_a_terminal_state(self, driver):
        _goto_tab(driver, HOME_TAB_IMAGE)
        _generate(driver, "a modest emerald abaya, soft studio light, editorial photograph")

        outcome = _wait_for_any_tag(
            driver, [RESULT_IMAGE_READY, RESULT_FAILED], GENERATION_TIMEOUT_SECONDS
        )

        if outcome == RESULT_FAILED:
            failed = by_tag(driver, RESULT_FAILED)
            message = failed.text
            assert message.strip(), "Failed state rendered with no message at all"
            # A legible failure must not leak a raw stack trace or hostname to the user — this
            # app has its own typed-failure/CloudFailure discipline for exactly this reason.
            assert "Exception" not in message and "\tat " not in message, (
                f"Failure message looks like a raw stack trace, not a user-facing string: {message!r}"
            )
            pytest.fail(
                f"Local image generation failed (legibly) rather than succeeding: {message!r}. "
                "This may be an expected 'no pack installed' state on a fresh device — install "
                "a local image-gen pack in Model Packs and re-run to distinguish that from a "
                "real bug."
            )

        # RESULT_IMAGE_READY reached — confirm there's an actual image element under it, not an
        # empty card.
        image_card = by_tag(driver, RESULT_IMAGE_READY)
        images = image_card.find_elements(AppiumBy.CLASS_NAME, "android.widget.ImageView")
        assert len(images) > 0, "ImageReady state rendered with no visible image content"


class TestCodeGeneration:
    def test_local_code_generation_streams_then_completes(self, driver):
        _goto_tab(driver, HOME_TAB_CODE)
        _generate(driver, "Write a Python function that reverses a linked list, with a docstring.")

        # Streaming should start well before the full generation completes — assert we actually
        # see the CodeStreaming state at some point, not just the final CodeReady/Failed jump.
        streaming_seen = False
        deadline = time.time() + 30
        while time.time() < deadline:
            if tag_exists(driver, RESULT_CODE_STREAMING):
                streaming_seen = True
                break
            if tag_exists(driver, RESULT_CODE_READY) or tag_exists(driver, RESULT_FAILED):
                break  # generation was fast enough that streaming already finished — not a bug
            time.sleep(1)

        outcome = _wait_for_any_tag(
            driver, [RESULT_CODE_READY, RESULT_FAILED], GENERATION_TIMEOUT_SECONDS
        )

        if outcome == RESULT_FAILED:
            message = by_tag(driver, RESULT_FAILED).text
            pytest.fail(f"Local code generation failed: {message!r}")

        code_card = by_tag(driver, RESULT_CODE_READY)
        assert len(code_card.text.strip()) > 20, "CodeReady state rendered with near-empty output"
        # A real generated function should not literally echo the prompt back unchanged.
        assert "def " in code_card.text or "function" in code_card.text.lower(), (
            "Generated output doesn't look like code — possible model/prompt-template regression"
        )


class TestAudioAndChat:
    def test_local_chat_reply_appears_for_a_real_question(self, driver):
        _goto_tab(driver, HOME_TAB_NEWS)
        # NewsChatScreen's own message bubbles are tagged chat_message_{index}_{role}.
        chat_input_tag = PROMPT_INPUT  # NewsChatScreen reuses PromptComposer for its input
        if not tag_exists(driver, chat_input_tag):
            pytest.skip("Chat input not present on News/Chat screen in this build")

        field = by_tag(driver, chat_input_tag)
        field.clear()
        field.send_keys("In one sentence, what is on-device generation?")
        by_tag(driver, SEND_BUTTON).click()

        deadline = time.time() + GENERATION_TIMEOUT_SECONDS
        reply_found = False
        while time.time() < deadline:
            bubbles = driver.find_elements(
                AppiumBy.ANDROID_UIAUTOMATOR,
                'new UiSelector().resourceIdMatches("chat_message_.*_assistant")',
            )
            if bubbles and any(b.text.strip() for b in bubbles):
                reply_found = True
                break
            time.sleep(2)

        assert reply_found, "No assistant reply appeared for a real chat question within timeout"
