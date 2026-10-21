/**
 * Sofi AI - Hyper-Realistic Living Digital Human & Android Companion Engine
 * 
 * Features:
 * - Dynamic Human Viseme Articulation (Natural speech cadence, lip opening, vowels & consonants)
 * - True Facial Reactions (Attentive listening nods, thinking gaze & brow furrow, warm smile)
 * - Anatomical Eyelid Blinking (Rapid eyelid sweep with natural half-blink and double-blinks)
 * - Organic Eye Gaze & Micro-Saccades (Involuntary eye jumps, social triangle tracking, mouse tracking)
 * - Respiratory Sinusoidal Biomechanics (Natural chest breathing, posture sway, head tilt)
 * - Layered Seamless Alpha Compositing (Zero-seam blending without image crossfading ghosting)
 * - Holographic Cybernetic Aura, Audio Spectrum & Acoustic Pulse Rings
 */

if (typeof CanvasRenderingContext2D !== 'undefined' && !CanvasRenderingContext2D.prototype.roundRect) {
    CanvasRenderingContext2D.prototype.roundRect = function (x, y, w, h, r) {
        if (typeof r === 'undefined') r = 0;
        if (typeof r === 'number') r = { tl: r, tr: r, br: r, bl: r };
        this.beginPath();
        this.moveTo(x + r.tl, y);
        this.lineTo(x + w - r.tr, y);
        this.quadraticCurveTo(x + w, y, x + w, y + r.tr);
        this.lineTo(x + w, y + h - r.br);
        this.quadraticCurveTo(x + w, y + h, x + w - r.br, y + h);
        this.lineTo(x + r.bl, y + h);
        this.quadraticCurveTo(x, y + h, x, y + h - r.bl);
        this.lineTo(x, y + r.tl);
        this.quadraticCurveTo(x, y, x + r.tl, y);
        this.closePath();
        return this;
    };
}

class SofiAvatar {
    constructor(canvasId) {
        this.canvas = document.getElementById(canvasId);
        this.ctx = this.canvas.getContext('2d');
        this.state = 'IDLE'; // IDLE, LISTENING, SPEAKING, THINKING, SLEEPING
        this.speaking = false;
        
        // Timing
        this.lastTime = performance.now();
        this.time = 0;
        
        // Gaze & Micro-Saccades
        this.gazeX = 0;
        this.gazeY = 0;
        this.targetGazeX = 0;
        this.targetGazeY = 0;
        this.saccadeTimer = 2.0;
        this.saccadeX = 0;
        this.saccadeY = 0;
        this.mouseInWindow = false;
        
        // Head & Posture Biomechanics
        this.headTilt = 0;       // Roll (tilt to side)
        this.targetHeadTilt = 0;
        this.headPitch = 0;      // Nodding up/down
        this.targetHeadPitch = 0;
        this.headYaw = 0;        // Turning left/right
        this.targetHeadYaw = 0;
        this.nodPhase = 0;       // Attentive listening / speaking nod cycle
        this.jawOffset = 0;      // Jaw drop on wide vowels
        
        // Respiratory Dynamics
        this.breathPhase = 0;
        this.scanlineY = 0;
        this.radarAngle = 0;
        this.gyroAngle1 = 0;
        this.gyroAngle2 = 0;
        
        // Anatomical Blinking System
        this.blinkProgress = 0; // 0 = open, 0.5 = half, 1 = closed
        this.blinkClosure = 0;  // 0.0 = fully open, 1.0 = fully closed
        this.isBlinking = false;
        this.blinkTimer = 4.5;  // seconds to next blink
        this.isDoubleBlink = false;
        
        // Dynamic Viseme Speech Synthesizer
        this.speechTimer = 0;
        this.syllableTimer = 0;
        this.syllableDuration = 0.16;
        this.activeViseme = 'small';
        this.targetViseme = 'mid';
        this.visemeBlend = 1.0;
        this.mouthOpenness = 0.0;
        this.targetMouthOpenness = 0.0;
        this.currentViseme = 'mid';
        this.pauseTimer = 2.4;
        this.isPausing = false;
        
        // Smooth Viseme Weights [0, 1]
        this.weights = {
            mouthSmall: 0,
            mouthAh: 0,
            mouthMid: 0,
            mouthEe: 0,
            mouthOh: 0,
            mouthOo: 0,
            mouthSmile: 0,
            eyebrowsRaised: 0,
            eyebrowsFurrowed: 0,
            eyesHalf: 0,
            eyesClosed: 0
        };
        
        // Holographic Rings & Particles
        this.soundRings = [];
        this.particles = [];
        this.initParticles(50);
        
        // Equalizer Spectrum (42 bars)
        this.audioBars = new Array(42).fill(0.08);

        // Real-Time Audio Energy & Synchronization
        this.audioLevel = 0.0;
        this.targetAudioLevel = 0.0;
        
        // Load High-Precision Human Facial Layers
        this.assets = {
            base: { src: 'assets/idle.jpg', img: new Image(), loaded: false },
            mouthSmall: { src: 'assets/layers/mouth_small.png', img: new Image(), loaded: false },
            mouthAh: { src: 'assets/layers/mouth_open_ah.png', img: new Image(), loaded: false },
            mouthMid: { src: 'assets/layers/mouth_mid.png', img: new Image(), loaded: false },
            mouthEe: { src: 'assets/layers/mouth_ee.png', img: new Image(), loaded: false },
            mouthOh: { src: 'assets/layers/mouth_oh.png', img: new Image(), loaded: false },
            mouthOo: { src: 'assets/layers/mouth_oo.png', img: new Image(), loaded: false },
            mouthSmile: { src: 'assets/layers/mouth_smile.png', img: new Image(), loaded: false },
            eyesClosed: { src: 'assets/layers/eyes_closed.png', img: new Image(), loaded: false },
            eyesHalf: { src: 'assets/layers/eyes_half.png', img: new Image(), loaded: false },
            eyebrowsRaised: { src: 'assets/layers/eyebrows_raised.png', img: new Image(), loaded: false },
            eyebrowsFurrowed: { src: 'assets/layers/eyebrows_furrowed.png', img: new Image(), loaded: false }
        };
        this.loadAssets();
        
        // Resize & Events (debounced with requestAnimationFrame)
        this.resize();
        this._resizeScheduled = false;
        const requestResize = () => {
            if (!this._resizeScheduled) {
                this._resizeScheduled = true;
                requestAnimationFrame(() => {
                    this._resizeScheduled = false;
                    this.resize();
                });
            }
        };
        window.addEventListener('resize', requestResize);
        
        // ResizeObserver guarantees zero-lag resize sync when window or panels resize
        const parent = this.canvas.parentElement;
        if (typeof ResizeObserver !== 'undefined' && parent) {
            this.resizeObserver = new ResizeObserver(() => {
                requestResize();
            });
            this.resizeObserver.observe(parent);
        }

        window.addEventListener('mousemove', (e) => this.onMouseMove(e));
        window.addEventListener('mouseleave', () => { this.mouseInWindow = false; });
        
        // Capped 60 FPS loop with drift compensation
        this.targetFps = 60;
        this.frameInterval = 1000 / this.targetFps; // ~16.66ms
        this.lastRenderTime = performance.now();
        
        const loop = (t) => {
            requestAnimationFrame(loop);
            // Suspend rendering completely when tab/window is hidden or minimized to save CPU/GPU
            if (document.hidden) {
                this.lastRenderTime = performance.now();
                return;
            }
            const now = t || performance.now();
            const elapsed = now - this.lastRenderTime;
            // 2.5ms V-Sync tolerance window prevents alternating 60/30 FPS judder on 60Hz displays
            if (elapsed >= this.frameInterval - 2.5) {
                this.lastRenderTime = now - (elapsed % this.frameInterval);
                this.render(now);
            }
        };
        requestAnimationFrame(loop);

        // Resume rendering immediately when returning to foreground
        document.addEventListener('visibilitychange', () => {
            if (!document.hidden) {
                this.lastTime = performance.now();
                this.lastRenderTime = performance.now();
                this.render(performance.now());
            }
        });
    }
    
    loadAssets() {
        let loadedCount = 0;
        const total = Object.keys(this.assets).length;
        
        const markLoaded = (item) => {
            if (!item.loaded) {
                item.loaded = true;
                loadedCount++;
                if (loadedCount >= total) {
                    this.allLoaded = true;
                }
            }
        };

        for (const [key, item] of Object.entries(this.assets)) {
            item.img.onload = () => markLoaded(item);
            item.img.onerror = () => {
                console.warn(`[Avatar] Failed to load ${item.src}`);
                item.loaded = false;
            };
            item.img.src = item.src;
            if (item.img.complete && item.img.naturalWidth > 0) {
                markLoaded(item);
            }
        }
    }
    
    resize() {
        const parent = this.canvas.parentElement;
        const rect = parent ? parent.getBoundingClientRect() : null;
        this.dpr = Math.min(window.devicePixelRatio || 1, 2);
        const newW = (rect && rect.width > 20) ? Math.floor(rect.width) : (window.innerWidth ? Math.floor(window.innerWidth * 0.54) : 580);
        const newH = (rect && rect.height > 20) ? Math.floor(rect.height) : (window.innerHeight ? Math.floor(window.innerHeight - 106) : 640);
        
        const targetCanvasW = Math.round(newW * this.dpr);
        const targetCanvasH = Math.round(newH * this.dpr);

        // Dead-zone check: prevent subpixel layout oscillations (e.g. scrollbars, CSS flex jitter)
        // from constantly reallocating canvas memory and wiping pixels
        if (this.width && this.height &&
            Math.abs(this.width - newW) < 2 &&
            Math.abs(this.height - newH) < 2 &&
            Math.abs(this.canvas.width - targetCanvasW) < 4 &&
            Math.abs(this.canvas.height - targetCanvasH) < 4) {
            return;
        }

        this.width = newW;
        this.height = newH;
        this.canvas.width = targetCanvasW;
        this.canvas.height = targetCanvasH;
        this.ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
        
        // Immediate render avoids any blank frame during window drag or fullscreen toggle
        this.render(performance.now());
    }
    
    onMouseMove(e) {
        this.mouseInWindow = true;
        const rect = this.canvas.getBoundingClientRect();
        const cx = rect.left + rect.width * 0.5;
        const cy = rect.top + rect.height * 0.42;
        
        const dx = (e.clientX - cx) / (rect.width * 0.5);
        const dy = (e.clientY - cy) / (rect.height * 0.5);
        
        this.targetGazeX = Math.max(-0.85, Math.min(0.85, dx));
        this.targetGazeY = Math.max(-0.75, Math.min(0.75, dy));
    }
    
    setState(newState) {
        this.state = (newState || 'IDLE').toUpperCase();
        this.speaking = (this.state === 'SPEAKING');
        
        if (this.state === 'LISTENING') {
            // Attentive listening posture: head tilts slightly, brows raise attentively
            this.targetHeadTilt = -0.065; // ~3.7 degrees tilt
            this.targetHeadPitch = 0.02;
            this.targetHeadYaw = -0.03;
        } else if (this.state === 'THINKING') {
            // Cognitive search gaze: looks upward & to the side, brows furrow
            this.targetHeadTilt = 0.045;
            this.targetHeadPitch = -0.03;
            this.targetHeadYaw = 0.06;
            this.targetGazeX = 0.45;
            this.targetGazeY = -0.35;
        } else if (this.state === 'SPEAKING') {
            this.targetHeadTilt = 0.01;
            this.targetHeadPitch = 0.0;
            this.targetHeadYaw = 0.0;
            this.isPausing = false;
            this.pauseTimer = 2.4;
            this.syllableTimer = 0;
            this.activeViseme = 'small';
            this.targetViseme = 'mid';
            this.visemeBlend = 1.0;
            this.mouthOpenness = 0.0;
            this.targetMouthOpenness = 0.35;
        } else {
            // Idle relaxed posture
            this.targetHeadTilt = 0.0;
            this.targetHeadPitch = 0.0;
            this.targetHeadYaw = 0.0;
        }

        if (!this.speaking) {
            this.targetAudioLevel = 0.0;
            this.audioLevel = 0.0;
            this.targetMouthOpenness = 0.0;
            this.mouthOpenness = 0.0;
            this.activeViseme = 'small';
            this.targetViseme = 'small';
            this.visemeBlend = 1.0;
            this.currentViseme = 'rest';
            this.jawOffset = 0.0;
            this.weights.mouthSmall = 0.0;
            this.weights.mouthAh = 0.0;
            this.weights.mouthMid = 0.0;
            this.weights.mouthEe = 0.0;
            this.weights.mouthOh = 0.0;
            this.weights.mouthOo = 0.0;
        }
    }
    
    setAudioLevel(level) {
        this.targetAudioLevel = Math.max(0.0, Math.min(1.0, typeof level === 'number' ? level : 0.0));
    }
    
    initParticles(count) {
        this.particles = [];
        for (let i = 0; i < count; i++) {
            this.particles.push({
                x: Math.random() * 800,
                y: Math.random() * 800,
                r: 0.8 + Math.random() * 2.0,
                vy: 0.35 + Math.random() * 0.75,
                vx: (Math.random() - 0.5) * 0.25,
                alpha: 0.2 + Math.random() * 0.6,
                hue: Math.random() > 0.4 ? '0, 240, 255' : '189, 0, 255'
            });
        }
    }
    
    update(dt) {
        this.time += dt;
        this.radarAngle = (this.radarAngle + dt * 2.2) % (Math.PI * 2);
        this.gyroAngle1 = (this.gyroAngle1 + dt * 0.4) % (Math.PI * 2);
        this.gyroAngle2 = (this.gyroAngle2 - dt * 0.3) % (Math.PI * 2);
        this.scanlineY = (this.scanlineY + dt * 130) % 800;
        
        // 1. Organic Micro-Saccades & Human Eye Wandering
        this.saccadeTimer -= dt;
        if (this.saccadeTimer <= 0) {
            this.saccadeTimer = 1.4 + Math.random() * 2.2;
            if (!this.mouseInWindow) {
                // Involuntary social triangle saccade (left eye, right eye, mouth)
                this.saccadeX = (Math.random() - 0.5) * 0.16;
                this.saccadeY = (Math.random() - 0.5) * 0.12;
            } else {
                this.saccadeX = (Math.random() - 0.5) * 0.05;
                this.saccadeY = (Math.random() - 0.5) * 0.04;
            }
        }
        
        // Gaze spring-damping
        const effectiveTargetX = (this.state === 'THINKING') ? 0.45 : (this.targetGazeX + this.saccadeX);
        const effectiveTargetY = (this.state === 'THINKING') ? -0.32 : (this.targetGazeY + this.saccadeY);
        this.gazeX += (effectiveTargetX - this.gazeX) * (this.mouseInWindow ? 0.09 : 0.06);
        this.gazeY += (effectiveTargetY - this.gazeY) * (this.mouseInWindow ? 0.09 : 0.06);
        
        // 2. Respiratory Biomechanics (Natural chest & posture cycle ~3.4s)
        this.breathPhase += dt * 1.85;
        
        // 3. Head & Posture Gestures
        if (this.state === 'LISTENING') {
            // Attentive affirmative nod every ~3.2 seconds
            this.nodPhase += dt * 1.95;
            const nod = Math.sin(this.nodPhase);
            const nodOffset = (nod > 0.4) ? (nod - 0.4) * 0.035 : 0;
            this.headPitch += (this.targetHeadPitch + nodOffset - this.headPitch) * 0.08;
            this.headTilt += (this.targetHeadTilt + Math.sin(this.time * 0.7) * 0.015 - this.headTilt) * 0.05;
        } else if (this.state === 'SPEAKING') {
            // Cadence head rhythm with vocal emphasis
            this.nodPhase += dt * 5.5;
            const speechNod = Math.sin(this.nodPhase) * 0.02 + Math.cos(this.nodPhase * 1.7) * 0.015;
            const speechTilt = Math.sin(this.nodPhase * 0.6) * 0.025;
            this.headPitch += (speechNod - this.headPitch) * 0.12;
            this.headTilt += (speechTilt - this.headTilt) * 0.08;
        } else {
            this.headPitch += (this.targetHeadPitch - this.headPitch) * 0.05;
            this.headTilt += (this.targetHeadTilt - this.headTilt) * 0.05;
        }
        this.headYaw += (this.targetHeadYaw + this.gazeX * 0.04 - this.headYaw) * 0.06;
        
        // 4. Natural Anatomical Blinking
        this.blinkTimer -= dt;
        if (this.blinkTimer <= 0 && !this.isBlinking) {
            this.isBlinking = true;
            this.blinkProgress = 0;
            this.isDoubleBlink = (Math.random() < 0.08); // Natural rare double-blink (8%)
        }
        
        if (this.isBlinking) {
            this.blinkProgress += dt * 6.5; // ~150ms natural human blink duration
            if (this.blinkProgress >= 1.0) {
                if (this.isDoubleBlink) {
                    this.isDoubleBlink = false;
                    this.blinkProgress = 0; // Seamlessly cycle second blink without teleporting
                } else {
                    this.isBlinking = false;
                    this.blinkProgress = 0;
                    this.blinkClosure = 0.0;
                    this.weights.eyesClosed = 0.0;
                    this.weights.eyesHalf = 0.0;
                    this.blinkTimer = 4.0 + Math.random() * 4.5; // Organic human cadence (4.0s to 8.5s)
                }
            }
        }
        
        // Continuous Anatomical Eyelid Closure Curve [0.0 = fully open, 1.0 = fully closed]
        // Eliminates visual snapping, pop-ins, and interface blinking glitches
        if (this.isBlinking) {
            const bp = Math.min(1.0, Math.max(0.0, this.blinkProgress));
            if (bp <= 0.45) {
                this.blinkClosure = Math.sin((bp / 0.45) * Math.PI * 0.5);
            } else if (bp <= 0.55) {
                this.blinkClosure = 1.0;
            } else {
                this.blinkClosure = Math.cos(((bp - 0.55) / 0.45) * Math.PI * 0.5);
            }
            this.weights.eyesHalf = (this.blinkClosure < 0.50) ? (this.blinkClosure / 0.50) : 1.0;
            this.weights.eyesClosed = (this.blinkClosure >= 0.50) ? ((this.blinkClosure - 0.50) / 0.50) : 0.0;
        } else {
            this.blinkClosure = 0.0;
            this.weights.eyesHalf = 0.0;
            this.weights.eyesClosed = 0.0;
        }
        
        // 5. Facial Reactions: Eyebrows & Emotional Expression
        let targetBrowRaise = 0;
        let targetBrowFurrow = 0;
        let targetSmile = 0.05; // Natural resting pleasant expression
        
        if (this.state === 'LISTENING') {
            targetBrowRaise = 0.45;
            targetSmile = 0.32; // Welcoming engaged smile
        } else if (this.state === 'THINKING') {
            targetBrowFurrow = 0.65;
            targetSmile = 0.0;
        } else if (this.state === 'SPEAKING') {
            // Eyebrow inflection on emphasized words
            targetBrowRaise = 0.25 + Math.max(0, Math.sin(this.time * 4.2)) * 0.35;
            targetSmile = 0.22;
        }
        this.weights.eyebrowsRaised += (targetBrowRaise - this.weights.eyebrowsRaised) * 0.12;
        this.weights.eyebrowsFurrowed += (targetBrowFurrow - this.weights.eyebrowsFurrowed) * 0.14;
        
        // 6. Dynamic Viseme Speech Articulation Engine
        // Fast audio energy tracking with peak hold and natural release
        const targetEnergy = this.speaking ? Math.max(this.targetAudioLevel, 0.0) : 0.0;
        const attackRate = (targetEnergy > this.audioLevel) ? 0.65 : 0.28;
        this.audioLevel += (targetEnergy - this.audioLevel) * attackRate;

        if (this.speaking) {
            const hasLiveAudio = (this.audioLevel > 0.02);
            this.syllableTimer -= dt;
            this.pauseTimer -= dt;
            
            // Natural human pause between phrases when no loud live sound
            if (!hasLiveAudio) {
                if (this.pauseTimer <= 0) {
                    if (!this.isPausing && Math.random() < 0.22) {
                        this.isPausing = true;
                        this.pauseDuration = 0.14 + Math.random() * 0.18;
                        this.pauseTimer = this.pauseDuration;
                    } else {
                        this.isPausing = false;
                        this.pauseTimer = 1.6 + Math.random() * 2.2;
                    }
                }
            } else {
                this.isPausing = false;
            }
            
            if (this.isPausing) {
                this.targetMouthOpenness = 0.0;
                this.targetViseme = 'small';
            } else {
                if (this.syllableTimer <= 0) {
                    // Start new syllable with clear consonant/vowel progression
                    this.activeViseme = this.targetViseme;
                    this.visemeBlend = 0.0;
                    
                    const baseDur = hasLiveAudio ? (0.10 + (1.0 - Math.min(1.0, this.audioLevel)) * 0.08) : 0.14;
                    this.syllableDuration = baseDur + Math.random() * 0.06;
                    this.syllableTimer = this.syllableDuration;
                    
                    // Phonetic distribution:
                    // 'ah' (wide open vowel): loud open syllables
                    // 'mid' (conversational neutral): standard vowels & consonants
                    // 'small' (narrow consonant occlusion): t, d, s, n, l, m
                    // 'oh' (rounded): o, aw
                    // 'oo' (puckered): u, w, oo
                    // 'ee' (spread teeth smile): ee, i, ay
                    const visemes = ['ah', 'mid', 'small', 'oh', 'ee', 'mid', 'oo', 'ah', 'small'];
                    const weights = [0.22, 0.24, 0.14, 0.12, 0.10, 0.08, 0.05, 0.03, 0.02];
                    let r = Math.random();
                    let acc = 0;
                    for (let i = 0; i < visemes.length; i++) {
                        acc += weights[i];
                        if (r <= acc) {
                            this.targetViseme = visemes[i];
                            break;
                        }
                    }
                }
                
                // Progress through syllable: 0 -> 1
                const progress = Math.max(0, Math.min(1, 1.0 - (this.syllableTimer / this.syllableDuration)));
                this.visemeBlend = Math.min(1.0, this.visemeBlend + dt * 14.0); // fast blend transition

                // Syllable trajectory bell curve: mouth articulates open at peak, dips on closure
                const syllableCurve = Math.sin(progress * Math.PI); // 0 -> 1 -> 0
                const energyMultiplier = hasLiveAudio ? (0.45 + this.audioLevel * 0.95) : 0.85;

                // Peak openness depends on viseme type
                let visemeMaxOpen = 0.85;
                if (this.targetViseme === 'ah') visemeMaxOpen = 1.0;
                else if (this.targetViseme === 'mid') visemeMaxOpen = 0.75;
                else if (this.targetViseme === 'small') visemeMaxOpen = 0.35;
                else if (this.targetViseme === 'oh') visemeMaxOpen = 0.80;
                else if (this.targetViseme === 'oo') visemeMaxOpen = 0.55;
                else if (this.targetViseme === 'ee') visemeMaxOpen = 0.65;

                // Opening rises to peak then dips towards consonant occlusion
                this.targetMouthOpenness = Math.min(1.0, (0.15 + syllableCurve * 0.85) * visemeMaxOpen * energyMultiplier);

                // Physical jaw drop on open vowels
                this.jawOffset = (this.targetViseme === 'ah' || this.targetViseme === 'oh')
                    ? (1.5 + this.audioLevel * 3.5) * syllableCurve
                    : (0.8 + this.audioLevel * 1.5) * syllableCurve;
            }
            
            // Radiating acoustic sound rings from temple on vocal emphasis
            if ((hasLiveAudio && this.audioLevel > 0.32 && Math.random() < 0.25) || (!hasLiveAudio && Math.random() < 0.10)) {
                this.soundRings.push({
                    r: 55,
                    maxR: 220,
                    alpha: 0.85,
                    color: Math.random() > 0.4 ? '255, 0, 128' : '0, 240, 255'
                });
            }
        } else if (this.state === 'THINKING') {
            this.targetMouthOpenness = 0.12;
            this.targetViseme = 'oo';
            this.activeViseme = 'oo';
            this.visemeBlend = 1.0;
            this.jawOffset = 0.0;
        } else {
            this.targetMouthOpenness = 0.0;
            this.jawOffset = 0.0;
        }
        
        // Smoothly interpolate mouth openness (rapid opening attack, smooth release)
        const openLerpSpeed = this.speaking ? 0.38 : 0.24;
        this.mouthOpenness += (this.targetMouthOpenness - this.mouthOpenness) * openLerpSpeed;
        if (this.mouthOpenness < 0.005) this.mouthOpenness = 0.0;

        // Telemetry weight tracking
        this.weights.mouthSmall = (this.targetViseme === 'small' ? this.mouthOpenness : 0);
        this.weights.mouthAh = (this.targetViseme === 'ah' ? this.mouthOpenness : 0);
        this.weights.mouthMid = (this.targetViseme === 'mid' ? this.mouthOpenness : 0);
        this.weights.mouthEe = (this.targetViseme === 'ee' ? this.mouthOpenness : 0);
        this.weights.mouthOh = (this.targetViseme === 'oh' ? this.mouthOpenness : 0);
        this.weights.mouthOo = (this.targetViseme === 'oo' ? this.mouthOpenness : 0);
        this.weights.mouthSmile += (targetSmile - this.weights.mouthSmile) * 0.15;
        
        // 7. Soundwave Rings Lifecycle
        for (let i = this.soundRings.length - 1; i >= 0; i--) {
            const ring = this.soundRings[i];
            ring.r += dt * 120;
            ring.alpha = Math.max(0, 0.85 * (1 - ring.r / ring.maxR));
            if (ring.r >= ring.maxR) {
                this.soundRings.splice(i, 1);
            }
        }
        
        // 8. Equalizer Audio Spectrum
        const hasLiveSpectrum = (this.speaking && this.audioLevel > 0.02);
        for (let i = 0; i < this.audioBars.length; i++) {
            let targetH = 0.08;
            if (this.speaking) {
                const energy = hasLiveSpectrum ? Math.max(0.25, this.audioLevel * 1.35) : 0.8;
                targetH = energy * (0.2 + Math.abs(Math.sin(this.time * 8.0 + i * 0.45)) * 0.75);
            } else if (this.state === 'LISTENING') {
                targetH = 0.18 + Math.abs(Math.sin(this.time * 4.2 + i * 0.35)) * 0.48;
            } else if (this.state === 'THINKING') {
                targetH = 0.12 + Math.abs(Math.cos(this.time * 5.0 + i * 0.5)) * 0.32;
            }
            this.audioBars[i] += (targetH - this.audioBars[i]) * 0.28;
        }
    }
    
    render(now) {
        const dt = Math.max(0.001, Math.min((now - this.lastTime) / 1000, 0.1));
        this.lastTime = now;
        this.update(dt);
        
        const ctx = this.ctx;
        const w = this.width;
        const h = this.height;
        ctx.clearRect(0, 0, w, h);
        
        const cx = w * 0.5;
        const cy = h * 0.46;
        const breathY = Math.sin(this.breathPhase) * 3.2;
        const breathScale = 1.0 + Math.sin(this.breathPhase) * 0.0035;
        
        // 1. Ambient Holographic Aura & Cyber Backdrop
        this.drawHoloAura(ctx, cx, cy);
        
        // 2. Floating Luminous Data Particles (Back Layer)
        this.drawParticles(ctx, w, h);
        
        // 3. Concentric Gyro HUD Rings behind avatar
        this.drawGyroRings(ctx, cx, cy - 20 + breathY);
        
        // 4. Render Photorealistic Human Living Avatar with Full Facial Reactions
        this.drawLivingAvatar(ctx, cx, cy + breathY, breathScale);
        
        // 5. Holographic Soundwave Rings
        this.drawSoundRings(ctx, cx, cy - 40 + breathY);
        
        // 6. Subtle Cybernetic Framing Reticles (Clear of face!)
        this.drawOpticalHUD(ctx, cx, cy - 35 + breathY);
        
        // 7. Base Pedestal & Audio Equalizer Spectrum
        this.drawPedestalAndSpectrum(ctx, cx, h - 35);
    }
    
    drawHoloAura(ctx, cx, cy) {
        let auraColor = 'rgba(0, 240, 255, ';
        let outerColor = 'rgba(138, 43, 226, ';
        
        if (this.state === 'SPEAKING') {
            auraColor = 'rgba(255, 0, 128, ';
            outerColor = 'rgba(189, 0, 255, ';
        } else if (this.state === 'LISTENING') {
            auraColor = 'rgba(0, 255, 170, ';
            outerColor = 'rgba(0, 210, 255, ';
        } else if (this.state === 'THINKING') {
            auraColor = 'rgba(255, 170, 0, ';
            outerColor = 'rgba(220, 80, 255, ';
        }
        
        const grad = ctx.createRadialGradient(cx, cy - 30, 40, cx, cy - 30, 360);
        grad.addColorStop(0, auraColor + '0.24)');
        grad.addColorStop(0.5, outerColor + '0.12)');
        grad.addColorStop(0.85, 'rgba(8, 12, 30, 0.05)');
        grad.addColorStop(1, 'rgba(0, 0, 0, 0)');
        
        ctx.fillStyle = grad;
        ctx.fillRect(cx - 380, cy - 400, 760, 760);
    }
    
    drawGyroRings(ctx, cx, cy) {
        ctx.save();
        ctx.translate(cx, cy);
        
        let ringColor = 'rgba(0, 240, 255, 0.26)';
        let subColor = 'rgba(189, 0, 255, 0.20)';
        
        if (this.state === 'SPEAKING') {
            ringColor = 'rgba(255, 0, 128, 0.32)';
            subColor = 'rgba(189, 0, 255, 0.26)';
        } else if (this.state === 'LISTENING') {
            ringColor = 'rgba(0, 255, 170, 0.36)';
            subColor = 'rgba(0, 240, 255, 0.26)';
        }
        
        ctx.strokeStyle = ringColor;
        ctx.lineWidth = 1.2;
        ctx.beginPath();
        ctx.arc(0, 0, 225, 0, Math.PI * 2);
        ctx.stroke();
        
        const numTicks = 36;
        ctx.beginPath();
        for (let i = 0; i < numTicks; i++) {
            const angle = this.gyroAngle1 + (i * Math.PI * 2 / numTicks);
            const r1 = 221;
            const r2 = (i % 4 === 0) ? 232 : 227;
            ctx.moveTo(Math.cos(angle) * r1, Math.sin(angle) * r1);
            ctx.lineTo(Math.cos(angle) * r2, Math.sin(angle) * r2);
        }
        ctx.stroke();
        
        ctx.strokeStyle = subColor;
        ctx.lineWidth = 1.4;
        ctx.setLineDash([14, 12]);
        ctx.beginPath();
        ctx.arc(0, 0, 195, this.gyroAngle2, this.gyroAngle2 + Math.PI * 1.6);
        ctx.stroke();
        ctx.setLineDash([]);
        
        if (this.state === 'LISTENING') {
            ctx.strokeStyle = 'rgba(0, 255, 170, 0.35)';
            ctx.lineWidth = 1.5;
            ctx.beginPath();
            ctx.moveTo(0, 0);
            ctx.lineTo(Math.cos(this.radarAngle) * 225, Math.sin(this.radarAngle) * 225);
            ctx.stroke();
        }
        
        ctx.restore();
    }
    
    drawLivingAvatar(ctx, cx, cy, breathScale) {
        const baseImg = this.assets.base.img;
        const baseReady = this.assets.base.loaded || (baseImg && baseImg.complete && baseImg.naturalWidth > 0);
        if (!baseReady) {
            ctx.save();
            ctx.fillStyle = 'rgba(0, 240, 255, 0.85)';
            ctx.font = '13px monospace';
            ctx.textAlign = 'center';
            ctx.fillText('CONNECTING TO NEURAL CYBERNETIC CORE...', cx, cy);
            ctx.restore();
            return;
        }
        
        const baseSize = Math.min(this.width * 0.92, this.height * 0.88, 520);
        const drawSize = baseSize * breathScale;
        
        // 3D Parallax & Head Gaze displacement
        const parallaxX = this.gazeX * 16 + this.headYaw * 35;
        const parallaxY = this.gazeY * 10 + this.headPitch * 25;
        
        ctx.save();
        ctx.translate(cx + parallaxX, cy + parallaxY);
        ctx.rotate(this.headTilt);
        
        // Rounded Cybernetic Capsule Vignette Clip
        const radius = drawSize * 0.48;
        ctx.save();
        ctx.beginPath();
        ctx.roundRect(-drawSize * 0.46, -drawSize * 0.48, drawSize * 0.92, drawSize * 0.96, radius * 0.4);
        ctx.clip();
        
        // ── 1. BASE LIVING FACE LAYER ───────────────────────────
        ctx.drawImage(baseImg, -drawSize * 0.5, -drawSize * 0.5, drawSize, drawSize);
        
        // ── 2. DYNAMIC SMILE EXPRESSION ─────────────────────────
        const isAssetReady = (a) => a && (a.loaded || (a.img && a.img.complete && a.img.naturalWidth > 0));
        if (this.weights.mouthSmile > 0.01 && isAssetReady(this.assets.mouthSmile)) {
            ctx.globalAlpha = Math.min(1, this.weights.mouthSmile);
            ctx.drawImage(this.assets.mouthSmile.img, -drawSize * 0.5, -drawSize * 0.5, drawSize, drawSize);
            ctx.globalAlpha = 1.0;
        }
        
        // ── 3. REAL-TIME CLEAR VISEME MOUTH ARTICULATION ────────
        if (this.mouthOpenness > 0.01) {
            const getVisemeAsset = (name) => {
                switch (name) {
                    case 'ah': return this.assets.mouthAh;
                    case 'mid': return this.assets.mouthMid;
                    case 'small': return this.assets.mouthSmall;
                    case 'oh': return this.assets.mouthOh;
                    case 'oo': return this.assets.mouthOo;
                    case 'ee': return this.assets.mouthEe;
                    default: return this.assets.mouthSmall;
                }
            };

            const primaryAsset = getVisemeAsset(this.activeViseme);
            const secondaryAsset = getVisemeAsset(this.targetViseme);
            const jawDropY = this.jawOffset * (drawSize / 1024);

            if (this.mouthOpenness < 0.28) {
                // Subtle opening transition: blend mouthSmall over idle base
                const openAlpha = this.mouthOpenness / 0.28;
                if (isAssetReady(this.assets.mouthSmall)) {
                    ctx.globalAlpha = Math.min(1.0, openAlpha);
                    ctx.drawImage(this.assets.mouthSmall.img, -drawSize * 0.5, -drawSize * 0.5 + jawDropY, drawSize, drawSize);
                    ctx.globalAlpha = 1.0;
                }
            } else {
                // Clear, solid open mouth:
                // Pass 1: Render primary viseme at 100% OPAQUE (Zero ghosting of closed lips!)
                if (isAssetReady(primaryAsset)) {
                    ctx.globalAlpha = 1.0;
                    ctx.drawImage(primaryAsset.img, -drawSize * 0.5, -drawSize * 0.5 + jawDropY, drawSize, drawSize);
                } else if (isAssetReady(this.assets.mouthMid)) {
                    ctx.globalAlpha = 1.0;
                    ctx.drawImage(this.assets.mouthMid.img, -drawSize * 0.5, -drawSize * 0.5 + jawDropY, drawSize, drawSize);
                }

                // Pass 2: Smooth crossfade to secondary/target viseme
                if (secondaryAsset && secondaryAsset !== primaryAsset && this.visemeBlend > 0.02 && isAssetReady(secondaryAsset)) {
                    ctx.globalAlpha = Math.min(1.0, this.visemeBlend);
                    ctx.drawImage(secondaryAsset.img, -drawSize * 0.5, -drawSize * 0.5 + jawDropY, drawSize, drawSize);
                    ctx.globalAlpha = 1.0;
                }
            }
        }
        
        // ── 4. EYELIDS & ANATOMICAL BLINKING (Continuous Dual-Pass Sweep) ──
        if (this.blinkClosure > 0.01) {
            if (this.blinkClosure < 0.50) {
                // Eyelids sweeping down/up (half-closed phase)
                if (isAssetReady(this.assets.eyesHalf)) {
                    ctx.globalAlpha = Math.min(1.0, this.blinkClosure / 0.50);
                    ctx.drawImage(this.assets.eyesHalf.img, -drawSize * 0.5, -drawSize * 0.5, drawSize, drawSize);
                    ctx.globalAlpha = 1.0;
                }
            } else {
                // Fully shielding base (100% eyesHalf covers underlying iris completely)
                if (isAssetReady(this.assets.eyesHalf)) {
                    ctx.drawImage(this.assets.eyesHalf.img, -drawSize * 0.5, -drawSize * 0.5, drawSize, drawSize);
                }
                // Seamlessly blend into full eyelid closure
                if (isAssetReady(this.assets.eyesClosed)) {
                    const closedAlpha = (this.blinkClosure - 0.50) / 0.50;
                    ctx.globalAlpha = Math.min(1.0, closedAlpha);
                    ctx.drawImage(this.assets.eyesClosed.img, -drawSize * 0.5, -drawSize * 0.5, drawSize, drawSize);
                    ctx.globalAlpha = 1.0;
                }
            }
        }
        
        // ── 5. EYEBROW EXPRESSIONS (Listening / Thinking) ──────
        if (this.weights.eyebrowsRaised > 0.01 && isAssetReady(this.assets.eyebrowsRaised)) {
            ctx.globalAlpha = Math.min(1, this.weights.eyebrowsRaised);
            ctx.drawImage(this.assets.eyebrowsRaised.img, -drawSize * 0.5, -drawSize * 0.5, drawSize, drawSize);
            ctx.globalAlpha = 1.0;
        }
        if (this.weights.eyebrowsFurrowed > 0.01 && isAssetReady(this.assets.eyebrowsFurrowed)) {
            ctx.globalAlpha = Math.min(1, this.weights.eyebrowsFurrowed);
            ctx.drawImage(this.assets.eyebrowsFurrowed.img, -drawSize * 0.5, -drawSize * 0.5, drawSize, drawSize);
            ctx.globalAlpha = 1.0;
        }
        
        // ── 6. SOFT VIGNETTE EDGE FRAMING ──────────────────────
        const edgeGrad = ctx.createRadialGradient(0, 0, radius * 0.65, 0, 0, radius * 1.05);
        edgeGrad.addColorStop(0, 'rgba(6, 8, 20, 0)');
        edgeGrad.addColorStop(0.8, 'rgba(6, 8, 20, 0.15)');
        edgeGrad.addColorStop(1, 'rgba(6, 8, 20, 0.85)');
        ctx.fillStyle = edgeGrad;
        ctx.fillRect(-drawSize * 0.5, -drawSize * 0.5, drawSize, drawSize);
        
        ctx.restore(); // Restore clip
        
        // Outer Glowing Cybernetic Capsule Border
        let borderColor = 'rgba(0, 240, 255, 0.45)';
        if (this.state === 'SPEAKING') borderColor = 'rgba(255, 0, 128, 0.65)';
        else if (this.state === 'LISTENING') borderColor = 'rgba(0, 255, 170, 0.65)';
        else if (this.state === 'THINKING') borderColor = 'rgba(255, 170, 0, 0.55)';
        
        ctx.strokeStyle = borderColor;
        ctx.lineWidth = 1.8;
        ctx.beginPath();
        ctx.roundRect(-drawSize * 0.46, -drawSize * 0.48, drawSize * 0.92, drawSize * 0.96, radius * 0.4);
        ctx.stroke();
        
        // Temple Halo Sensor Arc Highlight
        this.drawTempleSensorSweep(ctx, drawSize);
        
        ctx.restore(); // Restore transform
    }
    
    drawTempleSensorSweep(ctx, size) {
        ctx.save();
        const tx = -size * 0.22;
        const ty = -size * 0.32;
        const tr = size * 0.12;
        
        ctx.translate(tx, ty);
        ctx.rotate(-this.headTilt * 0.5);
        
        let ringGlow = 'rgba(0, 240, 255, ';
        if (this.state === 'SPEAKING') ringGlow = 'rgba(255, 0, 128, ';
        else if (this.state === 'LISTENING') ringGlow = 'rgba(0, 255, 170, ';
        
        ctx.strokeStyle = ringGlow + '0.55)';
        ctx.lineWidth = 1.5;
        ctx.shadowColor = ringGlow + '0.8)';
        ctx.shadowBlur = 8;
        
        ctx.beginPath();
        ctx.arc(0, 0, tr, this.radarAngle * 1.5, this.radarAngle * 1.5 + Math.PI * 0.85);
        ctx.stroke();
        
        ctx.shadowBlur = 0;
        ctx.restore();
    }
    
    drawSoundRings(ctx, cx, cy) {
        ctx.save();
        const baseSize = Math.min(this.width * 0.92, this.height * 0.88, 520);
        const originX = cx - baseSize * 0.22;
        const originY = cy - baseSize * 0.32;
        for (const ring of this.soundRings) {
            ctx.strokeStyle = `rgba(${ring.color}, ${ring.alpha})`;
            ctx.lineWidth = 1.8;
            ctx.beginPath();
            ctx.arc(originX, originY, ring.r, 0, Math.PI * 2);
            ctx.stroke();
        }
        ctx.restore();
    }
    
    drawOpticalHUD(ctx, cx, cy) {
        ctx.save();
        ctx.translate(cx, cy);
        
        const baseSize = Math.min(this.width * 0.92, this.height * 0.88, 520);
        // Framing brackets at the corners of the portrait capsule
        const tw = baseSize * 0.44;
        const th = baseSize * 0.46;
        const bl = 16;
        
        ctx.strokeStyle = 'rgba(0, 240, 255, 0.32)';
        ctx.lineWidth = 1.4;
        
        // Batch corner framing brackets into a single path
        ctx.beginPath();
        // Top Left
        ctx.moveTo(-tw, -th + bl);
        ctx.lineTo(-tw, -th);
        ctx.lineTo(-tw + bl, -th);
        // Top Right
        ctx.moveTo(tw - bl, -th);
        ctx.lineTo(tw, -th);
        ctx.lineTo(tw, -th + bl);
        // Bottom Left
        ctx.moveTo(-tw, th - bl);
        ctx.lineTo(-tw, th);
        ctx.lineTo(-tw + bl, th);
        // Bottom Right
        ctx.moveTo(tw - bl, th);
        ctx.lineTo(tw, th);
        ctx.lineTo(tw, th - bl);
        ctx.stroke();
        
        // Diagnostic Telemetry Text
        ctx.fillStyle = 'rgba(0, 240, 255, 0.6)';
        ctx.font = '9px monospace';
        ctx.textAlign = 'left';
        ctx.fillText(`AZM: ${(this.gazeX * 15).toFixed(1)}°`, -tw + 4, -th - 6);
        ctx.textAlign = 'right';
        ctx.fillText(`PITCH: ${(this.headPitch * 180 / Math.PI).toFixed(1)}°`, tw - 4, -th - 6);
        
        ctx.restore();
    }
    
    drawPedestalAndSpectrum(ctx, cx, y) {
        ctx.save();
        ctx.translate(cx, y);
        
        // Floor Disc Emitter
        ctx.beginPath();
        ctx.ellipse(0, 0, 160, 22, 0, 0, Math.PI * 2);
        const grad = ctx.createRadialGradient(0, 0, 10, 0, 0, 160);
        let discColor = 'rgba(0, 240, 255, ';
        if (this.state === 'SPEAKING') discColor = 'rgba(255, 0, 128, ';
        else if (this.state === 'LISTENING') discColor = 'rgba(0, 255, 170, ';
        
        grad.addColorStop(0, discColor + '0.40)');
        grad.addColorStop(0.6, 'rgba(120, 0, 255, 0.10)');
        grad.addColorStop(1, 'rgba(0, 0, 0, 0)');
        ctx.fillStyle = grad;
        ctx.fill();
        
        // 42-Bar Dynamic Audio Equalizer Spectrum
        const totalW = 320;
        const barW = totalW / this.audioBars.length - 2.5;
        const startX = -totalW * 0.5;
        
        // Allocate single gradient for all bars rather than 42 gradients per frame
        const barGrad = ctx.createLinearGradient(0, -30, 0, 0);
        if (this.state === 'SPEAKING') {
            barGrad.addColorStop(0, '#ff007f');
            barGrad.addColorStop(1, '#bd00ff');
        } else if (this.state === 'LISTENING') {
            barGrad.addColorStop(0, '#00ffaa');
            barGrad.addColorStop(1, '#00f0ff');
        } else {
            barGrad.addColorStop(0, '#00f0ff');
            barGrad.addColorStop(1, '#6366f1');
        }
        ctx.fillStyle = barGrad;
        
        for (let i = 0; i < this.audioBars.length; i++) {
            const bh = this.audioBars[i] * 30;
            if (bh > 0.5) {
                const bx = startX + i * (barW + 2.5);
                ctx.fillRect(bx, -bh, barW, bh);
            }
        }
        
        ctx.restore();
    }
    
    drawParticles(ctx, w, h) {
        ctx.save();
        for (const p of this.particles) {
            p.y -= p.vy;
            p.x += p.vx;
            if (p.y < 0) {
                p.y = h + 10;
                p.x = Math.random() * w;
            }
            
            ctx.fillStyle = `rgba(${p.hue}, ${p.alpha})`;
            ctx.beginPath();
            ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
            ctx.fill();
        }
        ctx.restore();
    }
}
