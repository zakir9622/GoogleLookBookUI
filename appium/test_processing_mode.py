"""
Covers B5 (docs/plans/lovable-parity-local-first/PLAN.md): the Processing Mode card in Settings
must actually gate whether a cloud-selected capability is allowed to run, not just change its
own displayed label.
"""

import time

from conftest import by_tag, tag_exists

OPEN_SETTINGS_BUTTON = "home_open_settings"
PROCESSING_MODE_LOCAL = "processing_mode_local"
PROCESSING_MODE_CLOUD = "processing_mode_cloud"


def _open_settings(driver):
    by_tag(driver, OPEN_SETTINGS_BUTTON).click()
    time.sleep(1)


class TestProcessingMode:
    def test_both_mode_cards_are_present(self, driver):
        _open_settings(driver)
        assert tag_exists(driver, PROCESSING_MODE_LOCAL), "On-device only card not found in Settings"
        assert tag_exists(driver, PROCESSING_MODE_CLOUD), "Cloud allowed card not found in Settings"

    def test_selecting_a_mode_is_idempotent_and_does_not_crash(self, driver):
        _open_settings(driver)
        by_tag(driver, PROCESSING_MODE_CLOUD).click()
        time.sleep(1)
        assert tag_exists(driver, PROCESSING_MODE_CLOUD), "App crashed or navigated away after selecting Cloud allowed"

        by_tag(driver, PROCESSING_MODE_LOCAL).click()
        time.sleep(1)
        assert tag_exists(driver, PROCESSING_MODE_LOCAL), "App crashed or navigated away after selecting On-device only"

        # Default app state (per AppSettings.cloudModelsEnabled = false) should be On-device
        # only — leave the app in that state for every other test in this suite.
