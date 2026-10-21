"""
Sofi AI - Advanced Next-Gen Companion Interface
Ultra-Modern Glassmorphism Cyberpunk UI with Interactive Animated 2.5D Digital Human Avatar
"""

from __future__ import annotations

import base64
import json
import os
os.environ["QTWEBENGINE_CHROMIUM_FLAGS"] = "--disable-gpu-watchdog --log-level=3 --autoplay-policy=no-user-gesture-required"
import platform
import subprocess
import sys
import threading
import time
import urllib.parse
from pathlib import Path

import psutil
from PyQt6.QtCore import (
    QCoreApplication, QEvent, QObject, QPoint, QSize, Qt, QTimer, QUrl, pyqtSignal, pyqtSlot,
)
from PyQt6.QtGui import (
    QColor, QDragEnterEvent, QDropEvent, QIcon, QKeySequence, QShortcut,
)
from PyQt6.QtWebEngineCore import QWebEnginePage, QWebEngineSettings
from PyQt6.QtWebEngineWidgets import QWebEngineView
from PyQt6.QtWidgets import (
    QApplication, QFrame, QHBoxLayout, QMainWindow, QVBoxLayout, QWidget,
)

def _base_dir() -> Path:
    if getattr(sys, "frozen", False):
        return Path(sys.executable).parent
    return Path(__file__).resolve().parent

BASE_DIR   = _base_dir()
CONFIG_DIR = BASE_DIR / "config"
API_FILE   = CONFIG_DIR / "api_keys.json"
WEB_DIR    = BASE_DIR / "ui_web"


def _read_full_config() -> dict:
    try:
        return json.loads(API_FILE.read_text(encoding="utf-8"))
    except Exception:
        return {}


class SofiWebEnginePage(QWebEnginePage):
    """Intercepts pyaction:// URLs for zero-latency, foolproof Python-JS communication."""
    def __init__(self, window: MainWindow):
        super().__init__(window)
        self._window = window

    def acceptNavigationRequest(self, url: QUrl, navType, isMainFrame) -> bool:
        if url.scheme() == "pyaction":
            action = url.host()
            raw_query = url.query()
            self._window._handle_py_action(action, raw_query)
            return False
        return super().acceptNavigationRequest(url, navType, isMainFrame)


class MainWindow(QMainWindow):
    # Signals for thread-safe cross-thread calls
    _state_sig       = pyqtSignal(str)
    _log_sig         = pyqtSignal(str)
    _content_sig     = pyqtSignal(str, str)
    _reconfig_sig    = pyqtSignal()
    _camera_sig      = pyqtSignal(bytes)
    _cam_stream_sig  = pyqtSignal(bool)
    _audio_level_sig = pyqtSignal(float)

    def __init__(self, face_path: str = ""):
        super().__init__()
        self.setWindowTitle("Sofi AI - Personal Companion")
        self.resize(1080, 750)
        self.setMinimumSize(880, 600)

        # Center on primary screen
        screen = QApplication.primaryScreen()
        if screen:
            geo = screen.availableGeometry()
            x = (geo.width() - 1080) // 2
            y = (geo.height() - 750) // 2
            self.move(max(0, x), max(0, y))

        # Frameless cyber glass window styling
        self.setWindowFlags(Qt.WindowType.FramelessWindowHint | Qt.WindowType.Window)
        self.setStyleSheet("background: #060814;")

        self._muted = False
        self._ready = self._check_config()
        self._current_file: str | None = None
        self._assistant_name = _read_full_config().get("assistant_name", "Sofi") or "Sofi"
        self._cam_streaming = False
        self._cam_stop = threading.Event()

        # Window drag helper
        self._drag_pos = QPoint()

        # Callbacks expected by main.py
        self.on_text_command   = None
        self.on_remote_clicked = None
        self.on_interrupt      = None

        # Build Web UI
        self._init_web_view()

        # Connect Signals
        self._state_sig.connect(self._on_state_signal)
        self._log_sig.connect(self._on_log_signal)
        self._content_sig.connect(self._on_content_signal)
        self._reconfig_sig.connect(self._on_reconfig_signal)
        self._camera_sig.connect(self._on_camera_frame_signal)
        self._cam_stream_sig.connect(self._on_cam_stream_state)
        self._audio_level_sig.connect(self._on_audio_level_signal)

        # Telemetry background timer (CPU, RAM)
        self._telemetry_timer = QTimer(self)
        self._telemetry_timer.setInterval(2000)
        self._telemetry_timer.timeout.connect(self._update_telemetry)
        self._telemetry_timer.start()

        # Global Hotkeys
        sc_mute = QShortcut(QKeySequence("F4"), self)
        sc_mute.activated.connect(self._toggle_mute)
        sc_intr = QShortcut(QKeySequence("Escape"), self)
        sc_intr.activated.connect(self._do_interrupt)
        sc_full = QShortcut(QKeySequence("F11"), self)
        sc_full.activated.connect(self._toggle_fullscreen)

        # Enable Drag & Drop for files
        self.setAcceptDrops(True)

    def _init_web_view(self):
        container = QWidget(self)
        layout = QVBoxLayout(container)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(0)

        self._web = QWebEngineView(container)
        self._page = SofiWebEnginePage(self)
        self._page.setBackgroundColor(QColor(6, 8, 20))
        self._web.setPage(self._page)
        self._web.setStyleSheet("background: #060814;")

        # Configure WebEngine Settings
        settings = self._web.settings()
        settings.setAttribute(QWebEngineSettings.WebAttribute.LocalContentCanAccessRemoteUrls, True)
        settings.setAttribute(QWebEngineSettings.WebAttribute.LocalContentCanAccessFileUrls, True)
        settings.setAttribute(QWebEngineSettings.WebAttribute.JavascriptEnabled, True)
        settings.setAttribute(QWebEngineSettings.WebAttribute.Accelerated2dCanvasEnabled, True)
        settings.setAttribute(QWebEngineSettings.WebAttribute.WebGLEnabled, True)
        settings.setAttribute(QWebEngineSettings.WebAttribute.PlaybackRequiresUserGesture, False)
        self._page.setAudioMuted(False)

        # Load ui_web/index.html
        index_file = WEB_DIR / "index.html"
        self._web.load(QUrl.fromLocalFile(str(index_file)))

        layout.addWidget(self._web)
        self.setCentralWidget(container)

    def _run_js(self, script: str):
        """Thread-safe JavaScript evaluator."""
        try:
            self._web.page().runJavaScript(script)
        except Exception as e:
            print(f"[UI] JS Eval Error: {e}")

    # ==================== PYACTION HANDLER ====================
    def _handle_py_action(self, action: str, raw_query: str):
        data = {}
        if raw_query:
            try:
                parsed = urllib.parse.parse_qs(raw_query)
                if "data" in parsed:
                    data = json.loads(parsed["data"][0])
            except Exception as e:
                print(f"[UI] Query parse error: {e}")

        if action == "send_text":
            text = data.get("text", "").strip()
            if text and self.on_text_command:
                self.on_text_command(text)

        elif action == "toggle_mute":
            self._toggle_mute()

        elif action == "interrupt":
            self._do_interrupt()

        elif action == "toggle_camera":
            if self._cam_streaming:
                self.stop_camera_stream()
            else:
                self.start_camera_stream()

        elif action == "remote_click":
            if self.on_remote_clicked:
                res = self.on_remote_clicked()
                if res:
                    url, key, auto_url, manual = res
                    self._run_js(
                        f"window.sofiApp.showRemoteModal({json.dumps(url)}, {json.dumps(key)}, {json.dumps(auto_url)}, {json.dumps(manual)});"
                    )

        elif action == "save_setup":
            key = data.get("key", "").strip()
            os_name = data.get("os", "windows").strip()
            if key:
                CONFIG_DIR.mkdir(parents=True, exist_ok=True)
                cfg = _read_full_config()
                cfg["gemini_api_key"] = key
                cfg["os_system"] = os_name
                API_FILE.write_text(json.dumps(cfg, indent=4), encoding="utf-8")
                self._ready = True
                self._run_js("window.sofiApp.addMessage('system', 'Configuration saved successfully! Sofi is online.');")
                self._on_state_signal("LISTENING")

        elif action == "window_minimize":
            self.showMinimized()

        elif action == "window_maximize":
            if self.isMaximized():
                self.showNormal()
            else:
                self.showMaximized()

        elif action == "window_close":
            self.close()

        elif action == "window_drag_start":
            wh = self.windowHandle()
            if wh:
                wh.startSystemMove()

    # ==================== WINDOW DRAGGING ====================
    def mousePressEvent(self, event):
        if event.button() == Qt.MouseButton.LeftButton and event.position().y() <= 44:
            self._drag_pos = event.globalPosition().toPoint() - self.frameGeometry().topLeft()
            event.accept()

    def mouseMoveEvent(self, event):
        if event.buttons() == Qt.MouseButton.LeftButton and not self._drag_pos.isNull():
            self.move(event.globalPosition().toPoint() - self._drag_pos)
            event.accept()

    def mouseReleaseEvent(self, event):
        self._drag_pos = QPoint()

    # ==================== SLOTS FOR SIGNALS ====================
    def _on_state_signal(self, state: str):
        self._run_js(f"if (window.sofiApp) window.sofiApp.setState({json.dumps(state)});")

    def _on_log_signal(self, text: str):
        if not text:
            return
        role = "system"
        msg = text
        
        # Parse sender tags from logs
        if text.startswith("YOU:") or text.startswith("USER:"):
            role = "user"
            msg = text.split(":", 1)[1].strip()
        elif text.startswith("SOFI:") or text.startswith("JARVIS:") or text.startswith("AI:"):
            role = "sofi"
            msg = text.split(":", 1)[1].strip()
        elif text.startswith("SYS:"):
            role = "system"
            msg = text.split(":", 1)[1].strip()
            
        self._run_js(f"if (window.sofiApp) window.sofiApp.addMessage({json.dumps(role)}, {json.dumps(msg)});")

    def _on_content_signal(self, title: str, text: str):
        self._run_js(f"if (window.sofiApp) window.sofiApp.showContentModal({json.dumps(title)}, {json.dumps(text)});")

    def _on_reconfig_signal(self):
        self._run_js("document.getElementById('setupModal').classList.add('active');")

    def _on_camera_frame_signal(self, img_bytes: bytes):
        if not img_bytes:
            self._run_js("if (window.sofiApp) window.sofiApp.showCameraFrame('');")
            return
        b64 = base64.b64encode(img_bytes).decode("ascii")
        self._run_js(f"if (window.sofiApp) window.sofiApp.showCameraFrame('{b64}');")

    def _on_cam_stream_state(self, active: bool):
        self._cam_streaming = active
        if not active:
            self._run_js("if (window.sofiApp) window.sofiApp.showCameraFrame('');")

    def _on_audio_level_signal(self, level: float):
        last = getattr(self, "_last_audio_level", -1.0)
        # Skip redundant IPC calls when change is negligible; always propagate silence transition
        if abs(level - last) >= 0.01 or (level == 0.0 and last > 0.0):
            self._last_audio_level = level
            self._run_js(f"if (window.sofiApp) window.sofiApp.setAudioLevel({level:.3f});")

    def set_volume_gain(self, gain: float):
        """Set dynamic Web Audio gain amplification factor (e.g. 1.5x - 2.0x)."""
        self._run_js(f"if (window.sofiApp && window.sofiApp.setVolumeGain) window.sofiApp.setVolumeGain({float(gain)});\n")

    def play_audio_chunk(self, pcm_data: bytes, sample_rate: int = 24000):
        """Streams normalized audio chunk to Web Audio API in ui_web."""
        if not pcm_data:
            return
        b64 = base64.b64encode(pcm_data).decode("ascii")
        self._run_js(f"if (window.sofiApp && window.sofiApp.playAudioChunk) window.sofiApp.playAudioChunk('{b64}', {sample_rate});\n")

    def _update_telemetry(self):
        try:
            cpu = int(psutil.cpu_percent())
            ram = int(psutil.virtual_memory().percent)
            self._run_js(f"if (window.sofiApp) window.sofiApp.updateTelemetry({cpu}, {ram});")
        except Exception:
            pass

    # ==================== ACTIONS ====================
    def _toggle_mute(self):
        self._muted = not self._muted
        self._run_js(f"if (window.sofiApp) window.sofiApp.setMuted({str(self._muted).lower()});")

    def _do_interrupt(self):
        if self.on_interrupt:
            self.on_interrupt()

    def _toggle_fullscreen(self):
        if self.isFullScreen():
            self.showNormal()
        else:
            self.showFullScreen()

    def _check_config(self) -> bool:
        if not API_FILE.exists():
            return False
        try:
            d = json.loads(API_FILE.read_text(encoding="utf-8"))
            return bool(d.get("gemini_api_key")) and bool(d.get("os_system"))
        except Exception:
            return False

    def notify_phone_connected(self) -> None:
        self._run_js("if (window.sofiApp) window.sofiApp.addMessage('system', '📱 Phone Connected via Inbound Webhook.');")

    # ==================== CAMERA STREAM ====================
    def start_camera_stream(self) -> None:
        if self._cam_streaming:
            return
        self._cam_stop.clear()
        self._cam_stream_sig.emit(True)
        t = threading.Thread(target=self._cam_loop, daemon=True, name="cam-stream")
        t.start()

    def stop_camera_stream(self) -> None:
        self._cam_stop.set()
        self._cam_stream_sig.emit(False)

    def _cam_loop(self) -> None:
        try:
            import cv2
            cam_idx = 0
            try:
                cfg = _read_full_config()
                cam_idx = int(cfg.get("camera_index", 0))
            except Exception:
                pass
            backend = cv2.CAP_DSHOW if platform.system() == "Windows" else cv2.CAP_ANY
            cap = cv2.VideoCapture(cam_idx, backend)
            if not cap.isOpened():
                cap = cv2.VideoCapture(0)
            if not cap.isOpened():
                self._cam_stream_sig.emit(False)
                return

            while not self._cam_stop.wait(0.04) and cap.isOpened():
                ret, frame = cap.read()
                if ret and frame is not None:
                    _, buf = cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY, 60])
                    self._camera_sig.emit(buf.tobytes())
            cap.release()
        except Exception as e:
            print(f"[Camera] Stream Error: {e}")
        finally:
            self._cam_stream_sig.emit(False)

    # ==================== DRAG AND DROP ====================
    def dragEnterEvent(self, event: QDragEnterEvent):
        if event.mimeData().hasUrls():
            event.acceptProposedAction()

    def dropEvent(self, event: QDropEvent):
        urls = event.mimeData().urls()
        if urls:
            path = urls[0].toLocalFile()
            if path:
                self._current_file = path
                self._run_js(f"if (window.sofiApp) window.sofiApp.addMessage('system', '📂 Attached File: {os.path.basename(path)}');")
                event.acceptProposedAction()


class _RootShim:
    """Provides Tkinter-compatible mainloop adapter for main.py."""
    def __init__(self, app: QApplication):
        self._app = app

    def mainloop(self):
        sys.exit(self._app.exec())

    def protocol(self, *_):
        pass


class JarvisUI:
    """Unified UI controller for main.py."""
    def __init__(self, face_path: str = "", size=None):
        QCoreApplication.setAttribute(Qt.ApplicationAttribute.AA_ShareOpenGLContexts, True)
        self._app = QApplication.instance() or QApplication(sys.argv)
        self._app.setStyle("Fusion")
        self._win = MainWindow(face_path)
        self._win.show()
        self.root = _RootShim(self._app)

    @property
    def muted(self) -> bool:
        return self._win._muted

    @muted.setter
    def muted(self, v: bool):
        if v != self._win._muted:
            self._win._toggle_mute()

    @property
    def current_file(self) -> str | None:
        return self._win._current_file

    @property
    def on_text_command(self):
        return self._win.on_text_command

    @on_text_command.setter
    def on_text_command(self, cb):
        self._win.on_text_command = cb

    @property
    def on_remote_clicked(self):
        return self._win.on_remote_clicked

    @on_remote_clicked.setter
    def on_remote_clicked(self, cb):
        self._win.on_remote_clicked = cb

    @property
    def on_interrupt(self):
        return self._win.on_interrupt

    @on_interrupt.setter
    def on_interrupt(self, cb):
        self._win.on_interrupt = cb

    def notify_phone_connected(self) -> None:
        self._win.notify_phone_connected()

    def set_state(self, state: str):
        self._win._state_sig.emit(state)

    def write_log(self, text: str):
        self._win._log_sig.emit(text)

    def wait_for_api_key(self):
        while not self._win._ready:
            time.sleep(0.1)

    def show_content(self, title: str, text: str):
        self._win._content_sig.emit(title[:48], text[:4000])

    def prompt_reconfig(self):
        self._win._ready = False
        self._win._reconfig_sig.emit()

    def show_camera_frame(self, img_bytes: bytes):
        self._win._camera_sig.emit(img_bytes)

    def start_camera_stream(self) -> None:
        self._win.start_camera_stream()

    def stop_camera_stream(self) -> None:
        self._win.stop_camera_stream()

    @property
    def assistant_name(self) -> str:
        return self._win._assistant_name

    def start_speaking(self):
        self.set_state("SPEAKING")

    def stop_speaking(self):
        self.set_speech_energy(0.0)
        if not self.muted:
            self.set_state("LISTENING")

    def set_speech_energy(self, level: float):
        self._win._audio_level_sig.emit(float(level))

    def set_volume_gain(self, gain: float):
        self._win.set_volume_gain(gain)

    def play_audio_chunk(self, pcm_data: bytes, sample_rate: int = 24000):
        self._win.play_audio_chunk(pcm_data, sample_rate)


if __name__ == "__main__":
    QCoreApplication.setAttribute(Qt.ApplicationAttribute.AA_ShareOpenGLContexts, True)
    app = QApplication(sys.argv)
    face_file = BASE_DIR / "core" / "face.png"
    jarvis_ui = JarvisUI(face_path=str(face_file) if face_file.exists() else "")
    sys.exit(app.exec())
