import json
import os
import platform
import re
import subprocess
import sys
import threading
import time
import urllib.parse
import urllib.request
from base64 import b64encode
from http.server import HTTPServer, BaseHTTPRequestHandler
from pathlib import Path

try:
    import pyautogui
    _PYAUTOGUI = True
except ImportError:
    _PYAUTOGUI = False

def _base_dir() -> Path:
    if getattr(sys, "frozen", False):
        return Path(sys.executable).parent
    return Path(__file__).resolve().parent.parent

def _load_twilio_config() -> dict:
    config_path = _base_dir() / "config" / "api_keys.json"
    try:
        if config_path.exists():
            with open(config_path, "r", encoding="utf-8") as f:
                cfg = json.load(f)
                return {
                    "account_sid": cfg.get("twilio_account_sid", "").strip(),
                    "auth_token": cfg.get("twilio_auth_token", "").strip(),
                    "phone_number": cfg.get("twilio_phone_number", "").strip(),
                    "auto_attend_calls": cfg.get("auto_attend_calls", True),
                    "auto_attend_delay": cfg.get("auto_attend_delay_seconds", 10),
                    "auto_attend_greeting": cfg.get(
                        "auto_attend_greeting",
                        "Hello! Boss is unavailable right now. I am Sofi, Boss's AI assistant. Please state your name and message, and I will deliver it to Boss."
                    ),
                }
    except Exception as e:
        print(f"[phone_call] Warning reading config: {e}")
    return {
        "account_sid": "", "auth_token": "", "phone_number": "",
        "auto_attend_calls": True, "auto_attend_delay": 10,
        "auto_attend_greeting": "Hello! Boss is unavailable right now. I am Sofi, Boss's AI assistant. Please leave a message."
    }

# ── Auto-Attend State & Timers ────────────────────────────────────────────────
_RINGING_CALL_ID = None
_USER_ANSWERED = False
_AUTO_ATTEND_TIMER = None
_INBOUND_SERVER_STARTED = False

def _clean_phone_number(raw: str) -> str:
    cleaned = re.sub(r"[^\d+]", "", raw.strip())
    if not cleaned.startswith("+") and len(cleaned) >= 10:
        cleaned = "+" + cleaned
    return cleaned

def html_escape(text: str) -> str:
    return (
        text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
        .replace("'", "&apos;")
    )

def _twilio_request(endpoint: str, data_dict: dict, cfg: dict) -> tuple[bool, str]:
    account_sid = cfg["account_sid"]
    auth_token = cfg["auth_token"]
    if not account_sid or not auth_token:
        return False, "Twilio credentials missing in config/api_keys.json"

    url = f"https://api.twilio.com/2010-04-01/Accounts/{account_sid}/{endpoint}.json"
    encoded_data = urllib.parse.urlencode(data_dict).encode("utf-8")

    req = urllib.request.Request(url, data=encoded_data, method="POST")
    auth_str = f"{account_sid}:{auth_token}"
    b64_auth = b64encode(auth_str.encode("utf-8")).decode("ascii")
    req.add_header("Authorization", f"Basic {b64_auth}")
    req.add_header("Content-Type", "application/x-www-form-urlencoded")

    try:
        with urllib.request.urlopen(req, timeout=12) as response:
            res_body = response.read().decode("utf-8")
            res_json = json.loads(res_body)
            sid = res_json.get("sid", "unknown")
            return True, f"Success (SID: {sid})"
    except Exception as e:
        return False, f"Twilio API request failed: {e}"

def _make_twilio_call(to_number: str, message: str, cfg: dict) -> tuple[bool, str]:
    from_number = cfg.get("phone_number")
    if not from_number:
        return False, "Twilio phone number missing in config"

    twiml = f"<Response><Say voice='alice'>{html_escape(message)}</Say></Response>"
    data = {
        "To": to_number,
        "From": from_number,
        "Twiml": twiml,
    }
    return _twilio_request("Calls", data, cfg)

def _send_twilio_sms(to_number: str, message: str, cfg: dict) -> tuple[bool, str]:
    from_number = cfg.get("phone_number")
    if not from_number:
        return False, "Twilio phone number missing in config"

    data = {
        "To": to_number,
        "From": from_number,
        "Body": message,
    }
    return _twilio_request("Messages", data, cfg)

def _dial_system_protocol(phone_number: str) -> tuple[bool, str]:
    sys_name = platform.system()
    tel_url = f"tel:{phone_number}"

    try:
        if sys_name == "Windows":
            subprocess.Popen(f"start {tel_url}", shell=True)
        elif sys_name == "Darwin":
            subprocess.Popen(["open", tel_url])
        else:
            subprocess.Popen(["xdg-open", tel_url])
        return True, f"Launched system call protocol for {phone_number}"
    except Exception as e:
        return False, f"Failed to launch system tel: protocol: {e}"

def _dial_softphone_app(app_name: str, phone_number: str) -> tuple[bool, str]:
    app_lower = app_name.lower().strip()

    if app_lower in ("skype", "skype call"):
        skype_url = f"skype:{phone_number}?call"
        sys_name = platform.system()
        try:
            if sys_name == "Windows":
                subprocess.Popen(f"start {skype_url}", shell=True)
            elif sys_name == "Darwin":
                subprocess.Popen(["open", skype_url])
            else:
                subprocess.Popen(["xdg-open", skype_url])
            return True, f"Initiated Skype call to {phone_number}"
        except Exception as e:
            return False, f"Failed to trigger Skype: {e}"

    if _PYAUTOGUI:
        try:
            pyautogui.press("win")
            time.sleep(0.5)
            pyautogui.write(app_name, interval=0.04)
            time.sleep(0.6)
            pyautogui.press("enter")
            time.sleep(2.0)

            if phone_number:
                pyautogui.write(phone_number, interval=0.05)
                time.sleep(0.5)
                pyautogui.press("enter")
            return True, f"Opened {app_name} and entered {phone_number}"
        except Exception as e:
            return False, f"Softphone desktop automation failed: {e}"

    return False, f"PyAutoGUI not installed for softphone UI automation"

def _attend_softphone_call() -> tuple[bool, str]:
    if not _PYAUTOGUI:
        return False, "PyAutoGUI not available to attend desktop softphone call"

    try:
        sys_name = platform.system()
        if sys_name == "Windows":
            # Common desktop call answer shortcuts: Enter, Windows+Alt+K, Ctrl+Shift+A
            pyautogui.hotkey("win", "alt", "k")
            time.sleep(0.2)
            pyautogui.press("enter")
        else:
            pyautogui.press("enter")
        return True, "Attended call via desktop shortcut"
    except Exception as e:
        return False, f"Failed to attend call via UI automation: {e}"

def _save_voicemail_memory(caller: str, message: str):
    try:
        from memory.memory_manager import update_memory
        entry_key = f"voicemail_{int(time.time())}"
        val = f"Call from {caller}: {message}"
        update_memory({"notes": {entry_key: {"value": val}}})
        print(f"[PhoneCall] 💾 Voicemail saved: {val}")
    except Exception as e:
        print(f"[PhoneCall] ⚠️ Failed to save voicemail to memory: {e}")

# ── Twilio Inbound Call Webhook Server ───────────────────────────────────────
class InboundCallHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        content_length = int(self.headers.get("Content-Length", 0))
        post_data = self.rfile.read(content_length).decode("utf-8")
        params = urllib.parse.parse_qs(post_data)

        from_number = params.get("From", ["Unknown Caller"])[0]
        call_sid = params.get("CallSid", [""])[0]
        transcription = params.get("TranscriptionText", [""])[0]

        cfg = _load_twilio_config()
        delay = cfg.get("auto_attend_delay", 10)
        greeting = cfg.get("auto_attend_greeting", "Hello! Boss is unavailable. Please leave a message.")

        if transcription:
            _save_voicemail_memory(from_number, transcription)
            self.send_response(200)
            self.send_header("Content-Type", "text/xml")
            self.end_headers()
            self.wfile.write(b"<Response><Hangup/></Response>")
            return

        global _USER_ANSWERED, _RINGING_CALL_ID
        _USER_ANSWERED = False
        _RINGING_CALL_ID = call_sid

        print(f"[PhoneCall] 📞 Incoming call from {from_number}. Waiting {delay}s for user answer...")

        # Sleep auto_attend_delay seconds checking if user answers
        start_t = time.time()
        while time.time() - start_t < delay:
            if _USER_ANSWERED:
                print(f"[PhoneCall] 👤 User answered call {call_sid}. Sofi stepping back.")
                self.send_response(200)
                self.send_header("Content-Type", "text/xml")
                self.end_headers()
                self.wfile.write(b"<Response></Response>")
                return
            time.sleep(0.5)

        # 10s elapsed without pickup -> Sofi attends and speaks!
        print(f"[PhoneCall] 🤖 10s elapsed! Sofi attending call from {from_number}...")

        twiml_resp = (
            f"<Response>"
            f"<Say voice='alice'>{html_escape(greeting)}</Say>"
            f"<Record maxLength='60' transcribe='true' transcribeCallback='/voice/incoming'/>"
            f"</Response>"
        )

        self.send_response(200)
        self.send_header("Content-Type", "text/xml")
        self.end_headers()
        self.wfile.write(twiml_resp.encode("utf-8"))

    def log_message(self, format, *args):
        pass

def start_inbound_server(port=5050):
    global _INBOUND_SERVER_STARTED
    if _INBOUND_SERVER_STARTED:
        return
    _INBOUND_SERVER_STARTED = True

    def run_server():
        try:
            server = HTTPServer(("0.0.0.0", port), InboundCallHandler)
            print(f"[PhoneCall] 📞 Inbound call webhook server active on port {port}")
            server.serve_forever()
        except Exception as e:
            print(f"[PhoneCall] Warning: Inbound server port {port} error: {e}")

    t = threading.Thread(target=run_server, daemon=True)
    t.start()

# ── Main Entry Point ──────────────────────────────────────────────────────────
def phone_call(parameters: dict, response=None, player=None, speak=None) -> str:
    global _USER_ANSWERED, _AUTO_ATTEND_TIMER

    action = parameters.get("action", "make_call").lower().strip()
    raw_number = parameters.get("phone_number", parameters.get("number", "")).strip()
    message = parameters.get("message", parameters.get("text", "Hello, this is Sofi calling.")).strip()
    app = parameters.get("app", "").strip()

    phone_num = _clean_phone_number(raw_number) if raw_number else ""
    cfg = _load_twilio_config()

    if player and hasattr(player, "write_log"):
        player.write_log(f"PHONE: Action '{action}' for target '{raw_number or app}'")

    if action in ("make_call", "call", "dial"):
        if not phone_num:
            return "Please provide a valid phone number to call."

        if cfg["account_sid"] and cfg["auth_token"]:
            success, msg = _make_twilio_call(phone_num, message, cfg)
            if success:
                return f"Placing phone call to {phone_num} via Twilio. {msg}"
            if player and hasattr(player, "write_log"):
                player.write_log(f"PHONE: Twilio failed ({msg}) — using system default fallback.")

        success, sys_msg = _dial_system_protocol(phone_num)
        if success:
            return f"Initiated system phone call to {phone_num}."
        return f"Could not place phone call to {phone_num}: {sys_msg}"

    elif action in ("send_sms", "sms", "text"):
        if not phone_num:
            return "Please provide a valid recipient phone number."
        if not message:
            return "Please provide a message text to send."

        if cfg["account_sid"] and cfg["auth_token"]:
            success, msg = _send_twilio_sms(phone_num, message, cfg)
            if success:
                return f"SMS sent successfully to {phone_num} via Twilio."
            return f"Failed to send SMS via Twilio: {msg}"

        return f"Twilio credentials not configured in config/api_keys.json. Cannot send SMS directly to mobile network."

    elif action in ("dial_softphone", "softphone", "app_call"):
        target_app = app or "Skype"
        success, msg = _dial_softphone_app(target_app, phone_num)
        return msg

    elif action in ("incoming_call_ring", "call_ringing"):
        _USER_ANSWERED = False
        delay = cfg.get("auto_attend_delay", 10)
        greeting = cfg.get("auto_attend_greeting", "Hello! Boss is currently unavailable. I am Sofi.")

        if player and hasattr(player, "write_log"):
            player.write_log(f"PHONE 🔔 Incoming call ringing! Sofi auto-attend scheduled in {delay} seconds.")

        def _do_auto_attend():
            global _USER_ANSWERED
            if not _USER_ANSWERED:
                if player and hasattr(player, "write_log"):
                    player.write_log(f"PHONE 🤖 10s timeout! Sofi attending call on behalf of Boss.")
                _attend_softphone_call()
                if speak:
                    speak(greeting)

        _AUTO_ATTEND_TIMER = threading.Timer(delay, _do_auto_attend)
        _AUTO_ATTEND_TIMER.start()

        return f"Incoming call timer started. Sofi will auto-attend in {delay} seconds if unanswered."

    elif action in ("attend_call", "answer_call", "pick_up"):
        _USER_ANSWERED = True
        if _AUTO_ATTEND_TIMER:
            _AUTO_ATTEND_TIMER.cancel()
        success, msg = _attend_softphone_call()
        return f"Call answered: {msg}"

    elif action in ("cancel_auto_attend", "user_answered"):
        _USER_ANSWERED = True
        if _AUTO_ATTEND_TIMER:
            _AUTO_ATTEND_TIMER.cancel()
        return "Auto-attend timer cancelled. User has answered/handled the call."

    elif action in ("end_call", "hangup", "disconnect"):
        _USER_ANSWERED = True
        if _AUTO_ATTEND_TIMER:
            _AUTO_ATTEND_TIMER.cancel()
        if _PYAUTOGUI:
            try:
                os_name = platform.system()
                if os_name == "Windows":
                    pyautogui.hotkey("alt", "f4")
                else:
                    pyautogui.hotkey("command", "w")
                return "Call disconnected/window closed."
            except Exception as e:
                return f"Failed to disconnect softphone: {e}"
        return "Call hangup signal sent."

    elif action in ("call_status", "status"):
        has_twilio = bool(cfg["account_sid"] and cfg["auth_token"])
        auto_att = cfg.get("auto_attend_calls", True)
        delay = cfg.get("auto_attend_delay", 10)
        status = f"Twilio Phone Integration: {'Active' if has_twilio else 'Not configured (using system tel: fallback)'}\n"
        status += f"Auto-Attend Status: {'Enabled' if auto_att else 'Disabled'} ({delay}s delay)"
        if cfg["phone_number"]:
            status += f"\nAssigned Phone Number: {cfg['phone_number']}"
        return status

    return f"Unknown phone action: '{action}'. Available: make_call, send_sms, dial_softphone, incoming_call_ring, attend_call, cancel_auto_attend, end_call, call_status."
