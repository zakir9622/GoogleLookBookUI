"""
Pytest fixtures for driving The Lookbook (com.zakir.vestra) via Appium's UiAutomator2 driver.

Locating elements: MainActivity.kt sets `testTagsAsResourceId = true` at the composable root,
which makes every `Modifier.testTag("some_tag")` in composeApp/.../ui/TestTags.kt visible to
UiAutomator as that view's resource-id — the *raw* tag string, not prefixed with the app's
package (that's how Compose's testTagsAsResourceId semantics property is documented to behave).
`by_tag()` below wraps that lookup; if your Appium/UiAutomator2 version resolves resource-ids
differently, adjust the AppiumBy.ANDROID_UIAUTOMATOR selector there — this is the one thing this
suite could not verify without a real device/emulator in the session that authored it.

Run: see appium/README.md for prerequisites and exact commands. Not executed by CI or by any
Claude session — no device or Appium server is available in that environment.
"""

import os

import pytest
from appium import webdriver
from appium.options.android import UiAutomator2Options
from appium.webdriver.common.appiumby import AppiumBy

APP_PACKAGE = "com.zakir.vestra"
APP_ACTIVITY = ".MainActivity"

APPIUM_SERVER_URL = os.environ.get("APPIUM_SERVER_URL", "http://127.0.0.1:4723")
DEVICE_NAME = os.environ.get("APPIUM_DEVICE_NAME", "Android")
# Path to a built sideload debug/release APK. If unset, the fixture assumes the app is already
# installed on the target device/emulator and only launches it (noReset keeps its state).
APP_PATH = os.environ.get("APPIUM_APP_PATH")
NO_RESET = os.environ.get("APPIUM_NO_RESET", "true").lower() != "false"


@pytest.fixture(scope="session")
def driver():
    options = UiAutomator2Options()
    options.platform_name = "Android"
    options.device_name = DEVICE_NAME
    options.app_package = APP_PACKAGE
    options.app_activity = APP_ACTIVITY
    options.no_reset = NO_RESET
    options.new_command_timeout = 120
    if APP_PATH:
        options.app = APP_PATH

    drv = webdriver.Remote(APPIUM_SERVER_URL, options=options)
    yield drv
    drv.quit()


@pytest.fixture(autouse=True)
def _reset_to_home(driver):
    """Best-effort: return to a known state (Home) before each test."""
    yield
    try:
        driver.press_keycode(4)  # KEYCODE_BACK, in case a dialog/sheet is still open
    except Exception:
        pass


def by_tag(driver, tag: str, timeout: float = 15.0):
    """Find one element by its Compose testTag (exposed as resource-id via testTagsAsResourceId)."""
    from selenium.webdriver.support.ui import WebDriverWait
    from selenium.webdriver.support import expected_conditions as EC

    locator = (AppiumBy.ID, tag)
    return WebDriverWait(driver, timeout).until(EC.presence_of_element_located(locator))


def all_by_tag(driver, tag: str):
    return driver.find_elements(AppiumBy.ID, tag)


def tag_exists(driver, tag: str) -> bool:
    return len(all_by_tag(driver, tag)) > 0
