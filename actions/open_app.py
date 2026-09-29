import json
import os
import platform
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

try:
    import psutil
    _PSUTIL = True
except ImportError:
    _PSUTIL = False

_SYSTEM = platform.system()

def _base_dir() -> Path:
    if getattr(sys, "frozen", False):
        return Path(sys.executable).parent
    return Path(__file__).resolve().parent.parent

def _get_target_os() -> str:
    config_path = _base_dir() / "config" / "api_keys.json"
    try:
        if config_path.exists():
            cfg = json.loads(config_path.read_text(encoding="utf-8"))
            user_os = cfg.get("os_system", "").strip().lower()
            if user_os in ("windows", "win"):
                return "Windows"
            elif user_os in ("mac", "darwin", "macos", "ios"):
                return "iOS" if user_os == "ios" else "Darwin"
            elif user_os in ("android"):
                return "Android"
            elif user_os in ("linux"):
                return "Linux"
    except Exception:
        pass
    return _SYSTEM

_APP_ALIASES: dict[str, dict[str, str]] = {
    "chrome":             {"Windows": "chrome",                  "Darwin": "Google Chrome",        "Linux": "google-chrome",         "Android": "com.android.chrome",            "iOS": "googlechrome://"},
    "google chrome":      {"Windows": "chrome",                  "Darwin": "Google Chrome",        "Linux": "google-chrome",         "Android": "com.android.chrome",            "iOS": "googlechrome://"},
    "firefox":            {"Windows": "firefox",                 "Darwin": "Firefox",              "Linux": "firefox",               "Android": "org.mozilla.firefox",           "iOS": "firefox://"},
    "edge":               {"Windows": "msedge",                  "Darwin": "Microsoft Edge",       "Linux": "microsoft-edge",        "Android": "com.microsoft.emmx",            "iOS": "microsoft-edge://"},
    "brave":              {"Windows": "brave",                   "Darwin": "Brave Browser",        "Linux": "brave-browser",         "Android": "com.brave.browser",             "iOS": "brave://"},
    "safari":             {"Windows": "msedge",                  "Darwin": "Safari",               "Linux": "firefox",               "Android": "com.android.chrome",            "iOS": "mobile-safari://"},
    "opera":              {"Windows": "opera",                   "Darwin": "Opera",                "Linux": "opera",                 "Android": "com.opera.browser",             "iOS": "opera-http://"},
    "whatsapp":           {"Windows": "WhatsApp",                "Darwin": "WhatsApp",             "Linux": "whatsapp",              "Android": "com.whatsapp",                  "iOS": "whatsapp://"},
    "telegram":           {"Windows": "Telegram",                "Darwin": "Telegram",             "Linux": "telegram",              "Android": "org.telegram.messenger",        "iOS": "tg://"},
    "discord":            {"Windows": "Discord",                 "Darwin": "Discord",              "Linux": "discord",               "Android": "com.discord",                   "iOS": "discord://"},
    "slack":              {"Windows": "Slack",                   "Darwin": "Slack",                "Linux": "slack",                 "Android": "com.Slack",                     "iOS": "slack://"},
    "zoom":               {"Windows": "Zoom",                    "Darwin": "zoom.us",              "Linux": "zoom",                  "Android": "us.zoom.videomeetings",         "iOS": "zoomus://"},
    "teams":              {"Windows": "msteams",                 "Darwin": "Microsoft Teams",      "Linux": "teams",                 "Android": "com.microsoft.teams",           "iOS": "msteams://"},
    "skype":              {"Windows": "skype",                   "Darwin": "Skype",                "Linux": "skype",                 "Android": "com.skype.raider",              "iOS": "skype://"},
    "signal":             {"Windows": "signal",                  "Darwin": "Signal",               "Linux": "signal",                "Android": "org.thoughtcrime.securesms",    "iOS": "signal://"},
    "spotify":            {"Windows": "Spotify",                 "Darwin": "Spotify",              "Linux": "spotify",               "Android": "com.spotify.music",             "iOS": "spotify://"},
    "vlc":                {"Windows": "vlc",                     "Darwin": "VLC",                  "Linux": "vlc",                   "Android": "org.videolan.vlc",              "iOS": "vlc://"},
    "netflix":            {"Windows": "Netflix",                 "Darwin": "Netflix",              "Linux": "firefox",               "Android": "com.netflix.mediaclient",       "iOS": "nflx://"},
    "youtube":            {"Windows": "chrome",                  "Darwin": "Safari",               "Linux": "google-chrome",         "Android": "com.google.android.youtube",    "iOS": "youtube://"},
    "instagram":          {"Windows": "Instagram",               "Darwin": "Instagram",            "Linux": "firefox",               "Android": "com.instagram.android",         "iOS": "instagram://"},
    "tiktok":             {"Windows": "TikTok",                  "Darwin": "TikTok",               "Linux": "firefox",               "Android": "com.zhiliaoapp.musically",      "iOS": "snssdk1128://"},
    "notion":             {"Windows": "Notion",                  "Darwin": "Notion",               "Linux": "notion",                "Android": "notion.id",                     "iOS": "notion://"},
    "maps":               {"Windows": "ms-drive-to:",            "Darwin": "Maps",                 "Linux": "gnome-maps",            "Android": "com.google.android.apps.maps",  "iOS": "maps://"},
    "camera":             {"Windows": "microsoft.windows.camera:", "Darwin": "Photo Booth",         "Linux": "cheese",                "Android": "com.android.camera",            "iOS": "camera://"},
    "settings":           {"Windows": "ms-settings:",            "Darwin": "System Preferences",   "Linux": "gnome-control-center",  "Android": "com.android.settings",          "iOS": "app-settings:"},
    "calculator":         {"Windows": "calc.exe",                "Darwin": "Calculator",           "Linux": "gnome-calculator",      "Android": "com.google.android.calculator", "iOS": "calculator://"},
    "phone":              {"Windows": "dialer.exe",              "Darwin": "FaceTime",             "Linux": "linphone",              "Android": "com.google.android.dialer",     "iOS": "tel://"},
    "messages":           {"Windows": "chat:",                   "Darwin": "Messages",             "Linux": "chat",                  "Android": "com.google.android.apps.messaging", "iOS": "sms://"},
}

def _normalize(raw: str, target_os: str) -> str:
    key = raw.lower().strip()
    if key in _APP_ALIASES:
        return _APP_ALIASES[key].get(target_os, raw)
    for alias_key, os_map in _APP_ALIASES.items():
        if alias_key in key or key in alias_key:
            return os_map.get(target_os, raw)
    return raw

def _launch_windows(app_name: str) -> bool:
    if shutil.which(app_name) or shutil.which(app_name.split(".")[0]):
        try:
            subprocess.Popen(app_name, shell=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            time.sleep(1.0)
            return True
        except Exception:
            pass

    if ":" in app_name:
        try:
            subprocess.Popen(f"start {app_name}", shell=True)
            time.sleep(1.0)
            return True
        except Exception:
            pass

    try:
        import pyautogui
        pyautogui.PAUSE = 0.1
        pyautogui.press("win")
        time.sleep(0.5)
        pyautogui.write(app_name, interval=0.04)
        time.sleep(0.6)
        pyautogui.press("enter")
        time.sleep(1.5)
        return True
    except Exception:
        pass

    return False

def _launch_macos(app_name: str) -> bool:
    try:
        result = subprocess.run(["open", "-a", app_name], capture_output=True, timeout=8)
        if result.returncode == 0:
            return True
    except Exception:
        pass

    if "://" in app_name or ":" in app_name:
        try:
            subprocess.run(["open", app_name], capture_output=True, timeout=8)
            return True
        except Exception:
            pass

    return False

def _launch_linux(app_name: str) -> bool:
    binary = shutil.which(app_name) or shutil.which(app_name.lower())
    if binary:
        try:
            subprocess.Popen([binary], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            return True
        except Exception:
            pass
    return False

def _launch_android(app_name: str) -> bool:
    # 1. Check if ADB tool is available to launch package on Android device/emulator
    if shutil.which("adb"):
        try:
            cmd = ["adb", "shell", "monkey", "-p", app_name, "-c", "android.intent.category.LAUNCHER", "1"]
            r = subprocess.run(cmd, capture_output=True, text=True, timeout=6)
            if r.returncode == 0 and "Events injected: 1" in r.stdout:
                return True
        except Exception:
            pass

    # 2. Scheme / Deep Link launch
    if "://" in app_name or app_name.startswith("intent:"):
        sys_name = platform.system()
        try:
            if sys_name == "Windows":
                subprocess.Popen(f"start {app_name}", shell=True)
            else:
                subprocess.Popen(["xdg-open", app_name])
            return True
        except Exception:
            pass

    print(f"[open_app] Android package trigger: {app_name}")
    return True

def _launch_ios(app_name: str) -> bool:
    scheme = app_name if "://" in app_name or ":" in app_name else f"{app_name}://"
    try:
        if platform.system() == "Darwin":
            subprocess.run(["open", scheme], capture_output=True, timeout=5)
        else:
            import webbrowser
            webbrowser.open(scheme)
        return True
    except Exception as e:
        print(f"[open_app] iOS scheme launch fallback: {e}")
        return True

_OS_LAUNCHERS = {
    "Windows": _launch_windows,
    "Darwin":  _launch_macos,
    "Linux":   _launch_linux,
    "Android": _launch_android,
    "iOS":     _launch_ios,
}

def open_app(parameters=None, response=None, player=None, session_memory=None) -> str:
    app_name = (parameters or {}).get("app_name", "").strip()
    if not app_name:
        return "No application name provided."

    target_os = _get_target_os()
    launcher = _OS_LAUNCHERS.get(target_os, _launch_windows)

    normalized = _normalize(app_name, target_os)
    print(f"[open_app] Launching: '{app_name}' → '{normalized}' ({target_os})")

    if player and hasattr(player, "write_log"):
        player.write_log(f"[open_app] {app_name} ({target_os})")

    try:
        if launcher(normalized):
            return f"Opened {app_name} on {target_os}."
        if normalized.lower() != app_name.lower():
            if launcher(app_name):
                return f"Opened {app_name} on {target_os}."
        return f"Initiated launch for {app_name} on {target_os}."
    except Exception as e:
        print(f"[open_app] Error: {e}")
        return f"Failed to open {app_name}: {e}"