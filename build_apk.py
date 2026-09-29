"""
build_apk.py — Standalone Native Android APK Helper for Sofi AI Assistant
"""

import os
import sys
import subprocess
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
ANDROID_DIR = BASE_DIR / "android_app"
APK_OUTPUT = ANDROID_DIR / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"

def build_apk():
    print("=" * 65)
    print(" SOFI AI ASSISTANT -- STANDALONE NATIVE ANDROID APK BUILDER")
    print("=" * 65)
    print("\nFeatures Built-in:")
    print(" [x] Standalone Native Mobile App (No PC Web Server Needed)")
    print(" [x] 10-Second Call Auto-Attend (TelecomManager)")
    print(" [x] Automated SMS Auto-Reply (SmsManager & Gemini AI)")
    print(" [x] Priority Urgent Message Notifications")
    print(" [x] Continuous Background Service for Nothing Phone (3)")
    print("-" * 65)

    if APK_OUTPUT.exists():
        print(f"\n[+] Standalone APK ready at:")
        print(f"    {APK_OUTPUT}")
    else:
        print("\n[*] Building APK with Gradle...")
        grad_cmd = "gradlew.bat assembleDebug" if os.name == 'nt' else "./gradlew assembleDebug"
        res = subprocess.run(grad_cmd, cwd=ANDROID_DIR, shell=True)
        if res.returncode == 0 and APK_OUTPUT.exists():
            print(f"\n[+] SUCCESS! Native APK generated at:\n    {APK_OUTPUT}")
        else:
            print("\n[-] Check android_app project folder.")

    print("\n[*] Installation on Nothing Phone (3):")
    print(" 1. Copy 'app-debug.apk' to your phone.")
    print(" 2. Open the file on your Nothing Phone (3) and tap Install.")
    print(" 3. Open Sofi AI and tap 'Grant Mobile Permissions'.")
    print(" 4. Sofi is now ready to auto-attend calls after 10s and reply to SMS!")

if __name__ == "__main__":
    build_apk()
