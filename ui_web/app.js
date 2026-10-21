/**
 * Sofi AI - UI Controller & Application Bridge
 */

function pyAction(action, data = {}) {
    const payload = encodeURIComponent(JSON.stringify(data));
    window.location.href = `pyaction://${action}?data=${payload}`;
}

/**
 * SofiAudioManager
 * High-performance Web Audio API engine featuring:
 * - Dynamic GainNode amplification (default 1.85x, 1.5x - 2.0x boost range)
 * - Automatic peak normalization without distortion
 * - Soft-knee dynamic limiter to prevent clipping
 * - Web Audio API stream player for raw PCM16 buffers and audio URLs
 */
class SofiAudioManager {
    constructor(defaultGain = 1.85) {
        this.ctx = null;
        this.gainNode = null;
        this.compressor = null;
        this.analyser = null;
        this.gainValue = defaultGain;
        this.isInitialized = false;
        this.activeSources = new Set();
    }

    init() {
        if (this.isInitialized && this.ctx) return;
        try {
            const AudioCtx = window.AudioContext || window.webkitAudioContext;
            this.ctx = new AudioCtx({ sampleRate: 24000 });
        } catch (_) {
            this.ctx = new (window.AudioContext || window.webkitAudioContext)();
        }

        // Master Gain Node for dynamic amplification (1.5x - 2.0x boost)
        this.gainNode = this.ctx.createGain();
        this.gainNode.gain.setValueAtTime(this.gainValue, this.ctx.currentTime);

        // Dynamics Compressor for studio-grade peak limiting and anti-clipping
        this.compressor = this.ctx.createDynamicsCompressor();
        this.compressor.threshold.setValueAtTime(-1.5, this.ctx.currentTime);
        this.compressor.knee.setValueAtTime(12.0, this.ctx.currentTime);
        this.compressor.ratio.setValueAtTime(16.0, this.ctx.currentTime);
        this.compressor.attack.setValueAtTime(0.002, this.ctx.currentTime);
        this.compressor.release.setValueAtTime(0.12, this.ctx.currentTime);

        // Analyser for real-time waveform / speech energy sync
        this.analyser = this.ctx.createAnalyser();
        this.analyser.fftSize = 256;
        this.analyser.smoothingTimeConstant = 0.4;

        // Route: Source -> GainNode -> Compressor -> Analyser -> Destination
        this.gainNode.connect(this.compressor);
        this.compressor.connect(this.analyser);
        this.analyser.connect(this.ctx.destination);

        this.isInitialized = true;
    }

    async ensureActive() {
        if (!this.isInitialized) {
            this.init();
        }
        if (this.ctx && this.ctx.state === 'suspended') {
            await this.ctx.resume();
        }
    }

    setGain(gain) {
        this.gainValue = Math.max(0.1, Math.min(4.0, Number(gain) || 1.85));
        if (this.gainNode && this.ctx) {
            this.gainNode.gain.setTargetAtTime(this.gainValue, this.ctx.currentTime, 0.05);
        }
        console.log(`[SofiAudio] Dynamic TTS Gain set to ${this.gainValue.toFixed(2)}x`);
    }

    getGain() {
        return this.gainValue;
    }

    /**
     * Peak normalizes float32 channel samples ensuring maximum volume clarity
     */
    normalizeFloat32(channelData, targetPeak = 0.96, boostFactor = 1.85) {
        let maxPeak = 0;
        for (let i = 0; i < channelData.length; i++) {
            const abs = Math.abs(channelData[i]);
            if (abs > maxPeak) maxPeak = abs;
        }
        if (maxPeak < 0.001) return channelData; // Silence or background noise

        let effectiveGain = boostFactor;
        if (maxPeak * effectiveGain > targetPeak) {
            effectiveGain = targetPeak / maxPeak;
        }

        for (let i = 0; i < channelData.length; i++) {
            channelData[i] = Math.max(-1.0, Math.min(1.0, channelData[i] * effectiveGain));
        }
        return channelData;
    }

    /**
     * Play raw 16-bit PCM buffer (Int16Array, Uint8Array, ArrayBuffer, or base64)
     */
    async playPcmBuffer(data, sampleRate = 24000) {
        await this.ensureActive();
        let int16Array;
        if (typeof data === 'string') {
            const binary = atob(data);
            const bytes = new Uint8Array(binary.length);
            for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
            int16Array = new Int16Array(bytes.buffer);
        } else if (data instanceof Uint8Array) {
            int16Array = new Int16Array(data.buffer, data.byteOffset, data.byteLength / 2);
        } else if (data instanceof Int16Array) {
            int16Array = data;
        } else if (data instanceof ArrayBuffer) {
            int16Array = new Int16Array(data);
        } else {
            return;
        }

        if (int16Array.length === 0) return;

        const floatData = new Float32Array(int16Array.length);
        for (let i = 0; i < int16Array.length; i++) {
            floatData[i] = int16Array[i] / 32768.0;
        }

        // Peak normalize and amplify
        this.normalizeFloat32(floatData, 0.96, 1.85);

        const audioBuf = this.ctx.createBuffer(1, floatData.length, sampleRate);
        audioBuf.copyToChannel(floatData, 0);

        const source = this.ctx.createBufferSource();
        source.buffer = audioBuf;
        source.connect(this.gainNode);

        this.activeSources.add(source);
        source.onended = () => {
            this.activeSources.delete(source);
        };
        source.start();
        return source;
    }

    /**
     * Play an audio URL (e.g. EdgeTTS/Kokoro wav or mp3) with gain amplification
     */
    async playAudioUrl(url) {
        await this.ensureActive();
        try {
            const response = await fetch(url);
            const arrayBuffer = await response.arrayBuffer();
            const decoded = await this.ctx.decodeAudioData(arrayBuffer);

            for (let ch = 0; ch < decoded.numberOfChannels; ch++) {
                const data = decoded.getChannelData(ch);
                this.normalizeFloat32(data, 0.96, 1.85);
            }

            const source = this.ctx.createBufferSource();
            source.buffer = decoded;
            source.connect(this.gainNode);
            this.activeSources.add(source);
            source.onended = () => this.activeSources.delete(source);
            source.start();
            return source;
        } catch (err) {
            console.error('[SofiAudio] Play URL error:', err);
        }
    }

    stopAll() {
        for (const src of this.activeSources) {
            try { src.stop(); } catch (_) {}
        }
        this.activeSources.clear();
    }
}

class SofiApp {
    constructor() {
        this.avatar = new SofiAvatar('avatarCanvas');
        this.audio = new SofiAudioManager(1.85);
        this.messagesContainer = document.getElementById('messagesStream');
        this.statusText = document.getElementById('statusText');
        this.statusRing = document.getElementById('statusRing');
        this.micBtn = document.getElementById('masterMicBtn');
        this.chatInput = document.getElementById('chatInput');
        this.sendBtn = document.getElementById('sendBtn');
        this.cpuPill = document.getElementById('cpuPill');
        this.ramPill = document.getElementById('ramPill');
        this.clockPill = document.getElementById('clockPill');
        
        this.muted = false;
        this.state = 'IDLE';
        
        this.initEvents();
        this.startClock();
        
        // Welcome message
        this.addMessage('sofi', 'Vanakkam Boss Sakthi! Naan unga personal AI companion Sofi. Ungalukku eppadi udhava mudiyum?');
    }
    
    initEvents() {
        // Text command sending
        const send = () => {
            const text = this.chatInput.value.trim();
            if (!text) return;
            this.addMessage('user', text);
            pyAction('send_text', { text: text });
            this.chatInput.value = '';
        };
        
        this.sendBtn.addEventListener('click', send);
        this.chatInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                send();
            }
        });
        
        // Mic Toggle
        this.micBtn.addEventListener('click', () => {
            pyAction('toggle_mute', {});
        });
        
        // Interrupt
        document.getElementById('interruptBtn').addEventListener('click', () => {
            pyAction('interrupt', {});
        });
        
        // Camera Vision Toggle
        document.getElementById('cameraBtn').addEventListener('click', () => {
            pyAction('toggle_camera', {});
        });
        
        // Remote Mobile
        document.getElementById('remoteBtn').addEventListener('click', () => {
            pyAction('remote_click', {});
        });
        
        // Settings
        document.getElementById('settingsBtn').addEventListener('click', () => {
            document.getElementById('setupModal').classList.add('active');
        });
        
        // Setup Modal Save
        document.getElementById('saveSetupBtn').addEventListener('click', () => {
            const key = document.getElementById('geminiApiKeyInput').value.trim();
            const osVal = document.getElementById('osSelect').value;
            if (key) {
                pyAction('save_setup', { key: key, os: osVal });
                document.getElementById('setupModal').classList.remove('active');
            }
        });

        // Voice Output Gain Boost Slider
        const gainSlider = document.getElementById('voiceGainSlider');
        const gainValText = document.getElementById('voiceGainVal');
        if (gainSlider && gainValText) {
            gainSlider.addEventListener('input', (e) => {
                const val = parseFloat(e.target.value);
                this.audio.setGain(val);
                const db = (20 * Math.log10(val)).toFixed(1);
                gainValText.innerText = `${val.toFixed(2)}x (+${db} dB)`;
            });
        }

        // Auto-resume AudioContext on first user interaction
        const unlockAudio = () => this.audio.ensureActive();
        window.addEventListener('click', unlockAudio, { once: true });
        window.addEventListener('keydown', unlockAudio, { once: true });
        
        // Clear chat
        document.getElementById('clearChatBtn').addEventListener('click', () => {
            this.messagesContainer.innerHTML = '';
            this.addMessage('system', 'Chat history cleared.');
        });
        
        // Quick Chips
        document.querySelectorAll('.quick-chip').forEach(chip => {
            chip.addEventListener('click', () => {
                const prompt = chip.getAttribute('data-prompt');
                if (prompt) {
                    this.addMessage('user', prompt);
                    pyAction('send_text', { text: prompt });
                }
            });
        });
        
        // Window Controls
        document.getElementById('minBtn').addEventListener('click', () => pyAction('window_minimize'));
        document.getElementById('maxBtn').addEventListener('click', () => pyAction('window_maximize'));
        document.getElementById('closeBtn').addEventListener('click', () => pyAction('window_close'));
        
        // Native frameless window dragging via titlebar
        const titlebar = document.querySelector('.titlebar');
        if (titlebar) {
            titlebar.addEventListener('mousedown', (e) => {
                if (e.target.closest('.window-controls') || e.target.closest('button')) return;
                pyAction('window_drag_start');
            });
        }
        
        // Global Keyboard Shortcuts
        window.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                pyAction('interrupt', {});
            } else if (e.key === 'F4') {
                e.preventDefault();
                pyAction('toggle_mute', {});
            }
        });

        // Responsive Window Resize Handler
        window.addEventListener('resize', () => {
            if (this.avatar && typeof this.avatar.resize === 'function') {
                this.avatar.resize();
            }
            if (this.messagesContainer) {
                this.messagesContainer.scrollTop = this.messagesContainer.scrollHeight;
            }
        });
    }
    
    startClock() {
        const update = () => {
            const now = new Date();
            const timeStr = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
            if (this.clockPill) this.clockPill.innerText = timeStr;
        };
        update();
        setInterval(update, 1000);
    }
    
    setState(state) {
        this.state = (state || 'IDLE').toUpperCase();
        this.avatar.setState(this.state);
        
        let hudMode = 'MODE: AUTONOMOUS AI';
        let label = 'SOFI SYSTEM ONLINE';
        let ringColor = 'var(--cyan)';
        
        if (this.state === 'LISTENING') {
            label = 'LISTENING TO SAKTHI...';
            ringColor = 'var(--emerald)';
            hudMode = 'MODE: ACOUSTIC SENSING';
            this.micBtn.className = 'master-mic-btn';
            this.micBtn.title = 'Sofi is listening to you';
        } else if (this.state === 'SPEAKING') {
            label = 'SOFI IS SPEAKING...';
            ringColor = 'var(--pink)';
            hudMode = 'MODE: VOCAL SYNTHESIS';
            this.micBtn.className = 'master-mic-btn speaking';
            this.micBtn.title = 'Sofi is speaking';
        } else if (this.state === 'THINKING') {
            label = 'PROCESSING INTENT...';
            ringColor = 'var(--amber)';
            hudMode = 'MODE: NEURAL REASONING';
        } else if (this.state === 'SLEEPING') {
            label = 'SOFI STANDBY';
            ringColor = 'var(--text-muted)';
            hudMode = 'MODE: STANDBY SLEEP';
        }
        
        this.statusText.innerText = label;
        this.statusRing.style.background = ringColor;
        this.statusRing.style.boxShadow = `0 0 10px ${ringColor}`;
        
        const modeTag = document.getElementById('hudModeTag');
        if (modeTag) modeTag.innerText = hudMode;
    }
    
    setAudioLevel(level) {
        if (this.avatar && typeof this.avatar.setAudioLevel === 'function') {
            this.avatar.setAudioLevel(level);
        }
    }
    
    setMuted(isMuted) {
        this.muted = isMuted;
        if (isMuted) {
            this.micBtn.className = 'master-mic-btn muted';
            this.micBtn.innerHTML = '🔇';
            this.statusText.innerText = 'MIC MUTED';
        } else {
            this.micBtn.className = 'master-mic-btn';
            this.micBtn.innerHTML = '🎙️';
            this.setState(this.state);
        }
    }
    
    addMessage(role, text) {
        if (!text) return;
        
        const card = document.createElement('div');
        card.className = `message-card ${role}`;
        
        const author = document.createElement('div');
        author.className = 'message-author';
        
        const now = new Date();
        const timeStr = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        
        if (role === 'user') {
            author.innerHTML = `<span>👤 BOSS SAKTHI</span> <span style="color:var(--text-muted);font-weight:400;margin-left:auto">${timeStr}</span>`;
        } else if (role === 'sofi') {
            author.innerHTML = `<span>✨ SOFI</span> <span style="color:var(--text-muted);font-weight:400;margin-left:auto">${timeStr}</span>`;
        }
        
        const body = document.createElement('div');
        body.innerText = text;
        
        if (role !== 'system') card.appendChild(author);
        card.appendChild(body);
        
        this.messagesContainer.appendChild(card);
        this.messagesContainer.scrollTop = this.messagesContainer.scrollHeight;
    }
    
    updateTelemetry(cpu, ram) {
        if (this.cpuPill && cpu !== undefined) this.cpuPill.innerText = `CPU ${cpu}%`;
        if (this.ramPill && ram !== undefined) this.ramPill.innerText = `RAM ${ram}%`;
    }
    
    showContentModal(title, text) {
        if (!text) return;
        // Strip raw URLs so it looks clean and readable
        const cleanText = text.replace(/https?:\/\/[^\s]+/g, '').trim();
        const header = title ? `📋 ${title}` : '📋 Info';
        this.addMessage('system', `${header}\n${cleanText}`);
    }
    
    showRemoteModal(url, key, autoUrl, manualUrl) {
        document.getElementById('remoteUrlText').innerText = url || '';
        document.getElementById('remoteKeyText').innerText = key || '';
        if (autoUrl) {
            document.getElementById('remoteAutoLink').href = autoUrl;
        }
        document.getElementById('remoteModal').classList.add('active');
    }
    
    showCameraFrame(b64Data) {
        const box = document.getElementById('cameraPreviewBox');
        const img = document.getElementById('cameraPreviewImg');
        if (b64Data) {
            img.src = `data:image/jpeg;base64,${b64Data}`;
            box.style.display = 'block';
        } else {
            box.style.display = 'none';
        }
    }

    setVolumeGain(gain) {
        this.audio.setGain(gain);
        const slider = document.getElementById('voiceGainSlider');
        const valText = document.getElementById('voiceGainVal');
        if (slider) slider.value = this.audio.getGain();
        if (valText) {
            const db = (20 * Math.log10(this.audio.getGain())).toFixed(1);
            valText.innerText = `${this.audio.getGain().toFixed(2)}x (+${db} dB)`;
        }
    }

    playAudioChunk(b64Data, sampleRate = 24000) {
        return this.audio.playPcmBuffer(b64Data, sampleRate);
    }

    playAudioUrl(url) {
        return this.audio.playAudioUrl(url);
    }

    stopAudio() {
        this.audio.stopAll();
    }
}

// Global hook
window.addEventListener('DOMContentLoaded', () => {
    window.sofiApp = new SofiApp();
});
