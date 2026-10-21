import json
import sys
from pathlib import Path

def get_base_dir() -> Path:
    if getattr(sys, "frozen", False):
        return Path(sys.executable).parent
    return Path(__file__).resolve().parent.parent

BASE_DIR    = get_base_dir()
CONFIG_DIR  = BASE_DIR / "config"
CONFIG_FILE = CONFIG_DIR / "api_keys.json"

def ensure_config_dir() -> None:
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)

def config_exists() -> bool:
    return CONFIG_FILE.exists()

def save_api_keys(gemini_api_key: str) -> None:
    ensure_config_dir()

    data: dict = {}
    if CONFIG_FILE.exists():
        try:
            data = json.loads(CONFIG_FILE.read_text(encoding="utf-8"))
        except Exception:
            data = {}

    data["gemini_api_key"] = gemini_api_key.strip()

    CONFIG_FILE.write_text(
        json.dumps(data, indent=2),
        encoding="utf-8"
    )

def load_api_keys() -> dict:
    if not CONFIG_FILE.exists():
        return {}
    try:
        return json.loads(CONFIG_FILE.read_text(encoding="utf-8"))
    except Exception as e:
        print(f"❌ Failed to load api_keys.json: {e}")
        return {}

def get_gemini_key() -> str | None:
    return load_api_keys().get("gemini_api_key")

def get_gemini_model() -> str:
    """Return the configured Gemini model name, or 'gemini-2.5-flash' by default."""
    return load_api_keys().get("gemini_model", "gemini-2.5-flash") or "gemini-2.5-flash"

def save_gemini_model(model_name: str) -> None:
    """Persist the Gemini model choice to config."""
    ensure_config_dir()
    data: dict = {}
    if CONFIG_FILE.exists():
        try:
            data = json.loads(CONFIG_FILE.read_text(encoding="utf-8"))
        except Exception:
            data = {}
    data["gemini_model"] = model_name.strip() or "gemini-2.5-flash"
    CONFIG_FILE.write_text(json.dumps(data, indent=4), encoding="utf-8")

def is_configured() -> bool:
    key = get_gemini_key()
    return bool(key and len(key) > 15)


def get_assistant_name() -> str:
    """Return the configured assistant name, or 'Sofi' if not set."""
    return load_api_keys().get("assistant_name", "Sofi") or "Sofi"


def get_user_name() -> str:
    """Return the configured user name for addressing, defaulting to 'Sakthi'."""
    return load_api_keys().get("user_name", "Sakthi") or "Sakthi"


def get_voice_name() -> str:
    """Return the configured voice name, defaulting to 'Aoede' (soft female voice)."""
    return load_api_keys().get("voice_name") or load_api_keys().get("tts_voice") or "Aoede"


def save_assistant_config(assistant_name: str, user_name: str, voice_name: str = "Aoede") -> None:
    """Persist assistant name, user name, and voice choice to config."""
    ensure_config_dir()
    data: dict = {}
    if CONFIG_FILE.exists():
        try:
            data = json.loads(CONFIG_FILE.read_text(encoding="utf-8"))
        except Exception:
            data = {}
    data["assistant_name"] = assistant_name.strip() or "Sofi"
    data["user_name"] = user_name.strip() or "Sakthi"
    data["voice_name"] = voice_name.strip() or "Aoede"
    CONFIG_FILE.write_text(json.dumps(data, indent=4), encoding="utf-8")


def get_brief_enabled() -> bool:
    return load_api_keys().get("morning_brief_enabled", True)


def save_brief_enabled(enabled: bool) -> None:
    ensure_config_dir()
    data: dict = {}
    if CONFIG_FILE.exists():
        try:
            data = json.loads(CONFIG_FILE.read_text(encoding="utf-8"))
        except Exception:
            data = {}
    data["morning_brief_enabled"] = enabled
    CONFIG_FILE.write_text(json.dumps(data, indent=4), encoding="utf-8")