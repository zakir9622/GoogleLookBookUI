"""
Image-to-image / edit flow. There is no separate "Image Edit" tab in this app — attaching a
reference photo on the Image tab (composer_add_reference) is what switches generation into
edit/img2img mode; clearing it (tapping the thumbnail, composer_reference_thumb) switches back
to text-to-image. This test drives that exact real flow, not a synthetic one.

Requires a device/emulator with at least one image already present in its gallery/Photos (the
system picker needs something to pick) — provision that as part of test setup on whatever
device runs this, not something this suite can do itself.
"""

import time

import pytest
from appium.webdriver.common.appiumby import AppiumBy

from conftest import by_tag, tag_exists

HOME_TAB_IMAGE = "home_tab_image"
PROMPT_INPUT = "composer_prompt_input"
SEND_BUTTON = "composer_send_button"
ADD_REFERENCE_BUTTON = "composer_add_reference"
REFERENCE_IMAGE_THUMB = "composer_reference_thumb"
RESULT_IMAGE_READY = "result_image_ready"
RESULT_FAILED = "result_failed"

GENERATION_TIMEOUT_SECONDS = 360


def _goto_tab(driver, tab_tag: str):
    by_tag(driver, tab_tag).click()


class TestImageEdit:
    def test_attaching_a_reference_photo_shows_a_thumbnail(self, driver):
        _goto_tab(driver, HOME_TAB_IMAGE)
        assert tag_exists(driver, ADD_REFERENCE_BUTTON), (
            "Add-reference button not found on the Image tab — image-edit entry point missing"
        )
        by_tag(driver, ADD_REFERENCE_BUTTON).click()

        # System photo picker: tap the first available image. Selector is best-effort across
        # OEM pickers — if this fails on a given device/OS version, that's real information
        # about picker compatibility, not something to silently swallow.
        time.sleep(2)
        thumbnails = driver.find_elements(AppiumBy.CLASS_NAME, "android.widget.ImageView")
        assert thumbnails, "System photo picker opened but no selectable images were found"
        thumbnails[0].click()

        assert tag_exists(driver, REFERENCE_IMAGE_THUMB), (
            "Reference image thumbnail did not appear after picking a photo — "
            "referenceUri state likely not reaching PromptComposer"
        )

    def test_clearing_the_reference_returns_to_text_to_image(self, driver):
        _goto_tab(driver, HOME_TAB_IMAGE)
        if not tag_exists(driver, REFERENCE_IMAGE_THUMB):
            pytest.skip("No reference image attached — run the attach test first or attach manually")

        by_tag(driver, REFERENCE_IMAGE_THUMB).click()

        time.sleep(1)
        assert not tag_exists(driver, REFERENCE_IMAGE_THUMB), (
            "Reference thumbnail still present after tapping it to clear — onClearReference "
            "likely not wired or not reaching state"
        )

    def test_edit_generation_with_reference_reaches_a_terminal_state(self, driver):
        _goto_tab(driver, HOME_TAB_IMAGE)
        if not tag_exists(driver, REFERENCE_IMAGE_THUMB):
            by_tag(driver, ADD_REFERENCE_BUTTON).click()
            time.sleep(2)
            thumbnails = driver.find_elements(AppiumBy.CLASS_NAME, "android.widget.ImageView")
            if not thumbnails:
                pytest.skip("No image available in the system picker to attach as a reference")
            thumbnails[0].click()

        field = by_tag(driver, PROMPT_INPUT)
        field.clear()
        field.send_keys("change the background to a soft studio gradient")
        by_tag(driver, SEND_BUTTON).click()

        deadline = time.time() + GENERATION_TIMEOUT_SECONDS
        outcome = None
        while time.time() < deadline:
            if tag_exists(driver, RESULT_IMAGE_READY):
                outcome = RESULT_IMAGE_READY
                break
            if tag_exists(driver, RESULT_FAILED):
                outcome = RESULT_FAILED
                break
            time.sleep(2)

        assert outcome is not None, (
            f"Image edit did not reach a terminal state within {GENERATION_TIMEOUT_SECONDS}s"
        )
        if outcome == RESULT_FAILED:
            message = by_tag(driver, RESULT_FAILED).text
            pytest.fail(f"Image edit (img2img) failed: {message!r}")
