package com.akay.feature.browser.fingerprint

import com.akay.core.domain.model.FingerprintProfile

data class FingerprintConfig(
    val seed: String,
    val spoofCanvas: Boolean,
    val spoofWebGl: Boolean,
    val spoofHardware: Boolean,
    val spoofAudio: Boolean,
    val desktop: Boolean,
    val timeZone: String?,
    val locale: String?
)

/**
 * Document-start script that makes the page's JS view of this device match
 * [FingerprintProfile]. It has to run before page scripts (including iframes);
 * a classic same-origin Worker is wrapped so it sees the same navigator values.
 * Module workers and service workers are left alone — wrapping those breaks them.
 */
object FingerprintSpoofing {

    fun buildScript(config: FingerprintConfig): String {
        val profile = FingerprintProfile.fromSeed(config.seed, config.desktop)
        val timeZone = FingerprintProfile.normalizeTimeZone(config.timeZone)
        val locale = FingerprintProfile.normalizeLocale(config.locale)
        // Helpers live in [core] so a Worker blob, which receives only that text,
        // still has __axNoise / __axDefine. The worker hook itself stays outside
        // so nested workers are not re-wrapped.
        val core = buildString {
            append(helperBlock(profile.noiseSeed))
            append(navigatorBlock(profile, config.spoofHardware, locale))
            append(clientHintsBlock(profile))
            append(screenBlock(profile))
            append(webRtcBlock())
            if (config.spoofHardware) append(fontBlock())
            if (timeZone != null || locale != null) append(timeBlock(timeZone, locale))
            if (config.spoofAudio) append(audioBlock())
            if (config.spoofWebGl) append(webGlBlock(profile))
            if (config.spoofCanvas) append(canvasBlock())
        }
        return """
            (function() {
                $core
                ${workerBlock(core)}
            })();
        """.trimIndent()
    }

    private fun helperBlock(noiseSeed: Int): String = """
        var __axNoiseSeed = $noiseSeed;
        function __axNoise(i) {
            var x = (__axNoiseSeed ^ Math.imul(i + 1, 374761393)) >>> 0;
            x = Math.imul(x ^ (x >>> 13), 1274126177) >>> 0;
            return (x % 3) - 1;
        }
        function __axDefine(obj, prop, value) {
            if (!obj) return;
            try {
                Object.defineProperty(obj, prop, { configurable: true, get: function() { return value; } });
            } catch (e) {}
        }
    """.trimIndent()

    /** Quote a value as a JS string literal. */
    internal fun jsQuote(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\u2028' -> append("\\u2028")
                '\u2029' -> append("\\u2029")
                '<' -> append("\\u003c")
                else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }

    private fun navigatorBlock(profile: FingerprintProfile, spoofHardware: Boolean, locale: String?): String {
        val hardware = if (!spoofHardware) "" else """
            __axDefine(navigator, 'hardwareConcurrency', ${profile.hardwareConcurrency});
            __axDefine(navigator, 'deviceMemory', ${profile.deviceMemory});
        """.trimIndent()
        val language = if (locale == null) "" else """
            __axDefine(navigator, 'language', ${jsQuote(locale)});
            try {
                Object.defineProperty(navigator, 'languages', {
                    configurable: true,
                    get: function() { return [${jsQuote(locale)}, ${jsQuote(locale.substringBefore('-'))}]; }
                });
            } catch (e) {}
        """.trimIndent()
        return """
            __axDefine(navigator, 'userAgent', ${jsQuote(profile.userAgent)});
            __axDefine(navigator, 'appVersion', ${jsQuote(profile.userAgent.removePrefix("Mozilla/"))});
            __axDefine(navigator, 'platform', ${jsQuote(profile.navigatorPlatform)});
            __axDefine(navigator, 'vendor', 'Google Inc.');
            __axDefine(navigator, 'maxTouchPoints', ${profile.maxTouchPoints});
            __axDefine(navigator, 'webdriver', false);
            $hardware
            $language
        """.trimIndent()
    }

    private fun clientHintsBlock(profile: FingerprintProfile): String {
        val model = jsQuote(profile.clientModel)
        val platform = jsQuote(profile.clientPlatform)
        val full = jsQuote(profile.chromeFull)
        return """
            try {
                var __axBrands = [
                    { brand: 'Chromium', version: '${profile.chromeMajor}' },
                    { brand: 'Google Chrome', version: '${profile.chromeMajor}' },
                    { brand: 'Not-A.Brand', version: '99' }
                ];
                var __axUad = {
                    brands: __axBrands,
                    mobile: ${profile.mobile},
                    platform: $platform,
                    getHighEntropyValues: function() {
                        return Promise.resolve({
                            brands: __axBrands,
                            mobile: ${profile.mobile},
                            platform: $platform,
                            platformVersion: ${jsQuote(profile.clientPlatformVersion)},
                            architecture: ${jsQuote(profile.clientArchitecture)},
                            bitness: ${jsQuote(profile.clientBitness)},
                            model: $model,
                            uaFullVersion: $full,
                            fullVersionList: [
                                { brand: 'Chromium', version: $full },
                                { brand: 'Google Chrome', version: $full },
                                { brand: 'Not-A.Brand', version: '99.0.0.0' }
                            ],
                            wow64: false
                        });
                    },
                    toJSON: function() { return { brands: this.brands, mobile: this.mobile, platform: this.platform }; }
                };
                __axDefine(navigator, 'userAgentData', __axUad);
            } catch (e) {}
        """.trimIndent()
    }

    private fun screenBlock(profile: FingerprintProfile): String = """
        if (typeof screen !== 'undefined') {
            __axDefine(screen, 'width', ${profile.screenWidth});
            __axDefine(screen, 'height', ${profile.screenHeight});
            __axDefine(screen, 'availWidth', ${profile.screenWidth});
            __axDefine(screen, 'availHeight', ${profile.availHeight});
            __axDefine(screen, 'colorDepth', ${profile.colorDepth});
            __axDefine(screen, 'pixelDepth', ${profile.colorDepth});
        }
    """.trimIndent()

    private fun webRtcBlock(): String = """
        try {
            var __axNativeRtc = window.RTCPeerConnection || window.webkitRTCPeerConnection;
            if (__axNativeRtc) {
                function __axHideCandidate(ev, fn) {
                    try {
                        var line = ev && ev.candidate && ev.candidate.candidate;
                        if (line && (line.indexOf(' typ host') >= 0 || line.indexOf(' typ srflx') >= 0)) return;
                    } catch (e) {}
                    if (typeof fn === 'function') return fn.call(this, ev);
                }
                function __axRelayConfig(config) {
                    var cfg = {};
                    if (config) {
                        for (var k in config) {
                            if (Object.prototype.hasOwnProperty.call(config, k)) cfg[k] = config[k];
                        }
                    }
                    cfg.iceTransportPolicy = 'relay';
                    return cfg;
                }
                var __axRtc = function(config, constraints) {
                    return new __axNativeRtc(__axRelayConfig(config), constraints);
                };
                __axRtc.prototype = __axNativeRtc.prototype;
                window.RTCPeerConnection = __axRtc;
                window.webkitRTCPeerConnection = __axRtc;
                var __axSetConfig = __axNativeRtc.prototype.setConfiguration;
                if (__axSetConfig) {
                    __axNativeRtc.prototype.setConfiguration = function(config) {
                        return __axSetConfig.call(this, __axRelayConfig(config));
                    };
                }
                var __axAdd = __axNativeRtc.prototype.addEventListener;
                if (__axAdd) {
                    __axNativeRtc.prototype.addEventListener = function(type, listener, options) {
                        if (type === 'icecandidate' && typeof listener === 'function') {
                            var self = this;
                            var wrapped = function(ev) { return __axHideCandidate.call(self, ev, listener); };
                            return __axAdd.call(this, type, wrapped, options);
                        }
                        return __axAdd.call(this, type, listener, options);
                    };
                }
            }
        } catch (e) {}
    """.trimIndent()

    private fun fontBlock(): String = """
        try {
            var __axFonts = {
                'sans-serif': 1, 'serif': 1, 'monospace': 1, 'cursive': 1, 'fantasy': 1,
                'system-ui': 1, 'ui-sans-serif': 1, 'ui-serif': 1, 'ui-monospace': 1,
                'roboto': 1, 'noto sans': 1, 'noto serif': 1, 'noto color emoji': 1,
                'droid sans': 1, 'droid serif': 1
            };
            function __axSanitizeFont(font) {
                var s = String(font || '16px sans-serif');
                var match = s.match(/^(.*?\d+(?:\.\d+)?px)\s*(.*)$/i);
                var prefix = match ? match[1] : '16px';
                var families = (match ? match[2] : s).split(',');
                var kept = [];
                for (var i = 0; i < families.length; i++) {
                    var name = families[i].replace(/["']/g, '').trim().toLowerCase();
                    if (__axFonts[name]) kept.push(families[i].trim());
                }
                if (!kept.length) kept.push('sans-serif');
                return prefix + ' ' + kept.join(', ');
            }
            if (typeof CanvasRenderingContext2D !== 'undefined' && CanvasRenderingContext2D.prototype.measureText) {
                var __axMeasure = CanvasRenderingContext2D.prototype.measureText;
                CanvasRenderingContext2D.prototype.measureText = function(text) {
                    var previous = this.font;
                    try {
                        this.font = __axSanitizeFont(previous);
                        return __axMeasure.apply(this, arguments);
                    } finally {
                        try { this.font = previous; } catch (e) {}
                    }
                };
            }
        } catch (e) {}
    """.trimIndent()

    private fun timeBlock(timeZone: String?, locale: String?): String {
        val zoneLiteral = timeZone?.let { jsQuote(it) } ?: "null"
        val localeLiteral = locale?.let { jsQuote(it) } ?: "undefined"
        return """
            try {
                var __axZone = $zoneLiteral;
                var __axLocale = $localeLiteral;
                var __axRealDTF = Intl.DateTimeFormat;
                if (__axZone) {
                    function __axZoneOffsetMinutes(date) {
                        var dtf = new __axRealDTF('en-US', {
                            timeZone: __axZone, hourCycle: 'h23',
                            year: 'numeric', month: '2-digit', day: '2-digit',
                            hour: '2-digit', minute: '2-digit', second: '2-digit'
                        });
                        var parts = dtf.formatToParts(date);
                        var map = {};
                        for (var i = 0; i < parts.length; i++) map[parts[i].type] = parts[i].value;
                        var asUTC = Date.UTC(+map.year, +map.month - 1, +map.day, (+map.hour) % 24, +map.minute, +map.second);
                        return Math.round((asUTC - date.getTime()) / 60000);
                    }
                    var __axOrigOffset = Date.prototype.getTimezoneOffset;
                    Date.prototype.getTimezoneOffset = function() {
                        try { return -__axZoneOffsetMinutes(this); } catch (e) { return __axOrigOffset.call(this); }
                    };
                    function __axShifted(date) {
                        var delta = __axOrigOffset.call(date) - date.getTimezoneOffset();
                        return new Date(date.getTime() + delta * 60000);
                    }
                    function __axPatchGetter(name) {
                        var orig = Date.prototype[name];
                        Date.prototype[name] = function() {
                            try { return orig.call(__axShifted(this)); } catch (e) { return orig.call(this); }
                        };
                    }
                    ['getFullYear', 'getMonth', 'getDate', 'getDay', 'getHours', 'getMinutes', 'getSeconds', 'getMilliseconds'].forEach(__axPatchGetter);
                    var __axOrigToString = Date.prototype.toString;
                    Date.prototype.toString = function() {
                        try {
                            return new __axRealDTF('en-US', {
                                timeZone: __axZone, timeZoneName: 'longOffset', hourCycle: 'h23',
                                weekday: 'short', year: 'numeric', month: 'short', day: '2-digit',
                                hour: '2-digit', minute: '2-digit', second: '2-digit'
                            }).format(this);
                        } catch (e) { return __axOrigToString.call(this); }
                    };
                }
                var __axDTF = function(locales, options) {
                    var opts = options ? {} : {};
                    if (options) {
                        for (var k in options) {
                            if (Object.prototype.hasOwnProperty.call(options, k)) opts[k] = options[k];
                        }
                    }
                    if (__axZone && !opts.timeZone) opts.timeZone = __axZone;
                    var loc = locales || __axLocale;
                    return new __axRealDTF(loc, opts);
                };
                __axDTF.prototype = __axRealDTF.prototype;
                __axDTF.supportedLocalesOf = __axRealDTF.supportedLocalesOf.bind(__axRealDTF);
                Intl.DateTimeFormat = __axDTF;
            } catch (e) {}
        """.trimIndent()
    }

    private fun audioBlock(): String = """
        try {
            if (typeof AudioBuffer !== 'undefined') {
                var __axSeenAudio = (typeof WeakSet !== 'undefined') ? new WeakSet() : null;
                var __axChannel = AudioBuffer.prototype.getChannelData;
                AudioBuffer.prototype.getChannelData = function() {
                    var data = __axChannel.apply(this, arguments);
                    try {
                        if (data && (!__axSeenAudio || !__axSeenAudio.has(data))) {
                            if (__axSeenAudio) __axSeenAudio.add(data);
                            for (var i = 0; i < data.length; i += 64) data[i] = data[i] + __axNoise(i) * 1e-7;
                        }
                    } catch (e) {}
                    return data;
                };
            }
            if (typeof AnalyserNode !== 'undefined' && AnalyserNode.prototype.getFloatFrequencyData) {
                var __axFreq = AnalyserNode.prototype.getFloatFrequencyData;
                AnalyserNode.prototype.getFloatFrequencyData = function(arr) {
                    var result = __axFreq.apply(this, arguments);
                    try {
                        if (arr) for (var i = 0; i < arr.length; i += 32) arr[i] = arr[i] + __axNoise(i) * 1e-4;
                    } catch (e) {}
                    return result;
                };
            }
        } catch (e) {}
    """.trimIndent()

    private fun webGlBlock(profile: FingerprintProfile): String {
        val vendor = jsQuote(profile.gpuVendor)
        val renderer = jsQuote(profile.gpuRenderer)
        return """
            try {
                function __axPatchGl(proto) {
                    if (!proto || proto.__axGl) return;
                    proto.__axGl = true;
                    var orig = proto.getParameter;
                    proto.getParameter = function(param) {
                        if (param === 37445) return $vendor;
                        if (param === 37446) return $renderer;
                        return orig.apply(this, arguments);
                    };
                }
                if (window.WebGLRenderingContext) __axPatchGl(WebGLRenderingContext.prototype);
                if (window.WebGL2RenderingContext) __axPatchGl(WebGL2RenderingContext.prototype);
            } catch (e) {}
        """.trimIndent()
    }

    private fun canvasBlock(): String = """
        try {
            if (typeof HTMLCanvasElement !== 'undefined' && typeof CanvasRenderingContext2D !== 'undefined') {
                var __axGetImageData = CanvasRenderingContext2D.prototype.getImageData;
                var __axPutImageData = CanvasRenderingContext2D.prototype.putImageData;
                var __axToDataURL = HTMLCanvasElement.prototype.toDataURL;
                var __axToBlob = HTMLCanvasElement.prototype.toBlob;
                function __axTint(data) {
                    for (var i = 0; i < data.length; i += 4) {
                        var v = data[i] + __axNoise(i);
                        data[i] = v < 0 ? 0 : (v > 255 ? 255 : v);
                    }
                }
                function __axNoisyCanvas(source) {
                    var w = source.width, h = source.height;
                    if (!(w > 0 && h > 0) || w * h > 4096 * 4096) return null;
                    var copy = document.createElement('canvas');
                    copy.width = w;
                    copy.height = h;
                    var ctx = copy.getContext('2d');
                    if (!ctx) return null;
                    ctx.drawImage(source, 0, 0);
                    var img = __axGetImageData.call(ctx, 0, 0, w, h);
                    __axTint(img.data);
                    __axPutImageData.call(ctx, img, 0, 0);
                    return copy;
                }
                CanvasRenderingContext2D.prototype.getImageData = function() {
                    var img = __axGetImageData.apply(this, arguments);
                    try { __axTint(img.data); } catch (e) {}
                    return img;
                };
                HTMLCanvasElement.prototype.toDataURL = function() {
                    try {
                        var copy = __axNoisyCanvas(this);
                        if (copy) return __axToDataURL.apply(copy, arguments);
                    } catch (e) {}
                    return __axToDataURL.apply(this, arguments);
                };
                HTMLCanvasElement.prototype.toBlob = function(callback) {
                    try {
                        if (typeof callback === 'function') {
                            var type = arguments[1] || 'image/png';
                            var quality = arguments[2];
                            var dataUrl = this.toDataURL(type, quality);
                            var comma = dataUrl.indexOf(',');
                            var bin = atob(comma >= 0 ? dataUrl.substring(comma + 1) : '');
                            var arr = new Uint8Array(bin.length);
                            for (var i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i);
                            callback(new Blob([arr], { type: type }));
                            return;
                        }
                    } catch (e) {}
                    return __axToBlob.apply(this, arguments);
                };
            }
        } catch (e) {}
    """.trimIndent()

    private fun workerBlock(core: String): String = """
        try {
            function __axWrapWorker(NativeCtor) {
                if (!NativeCtor) return null;
                var source = ${jsQuote(core)};
                var Wrapped = function(url, options) {
                    try {
                        if (options && options.type === 'module') return new NativeCtor(url, options);
                        if (typeof url !== 'string' || url.indexOf('blob:') === 0 || url.indexOf('data:') === 0) {
                            return new NativeCtor(url, options);
                        }
                        var parsed = new URL(url, location.href);
                        if (parsed.origin !== location.origin) return new NativeCtor(url, options);
                        var src = source + '\nimportScripts(' + JSON.stringify(parsed.href) + ');\n';
                        var blob = new Blob([src], { type: 'application/javascript' });
                        return new NativeCtor(URL.createObjectURL(blob), options);
                    } catch (e) {
                        return new NativeCtor(url, options);
                    }
                };
                Wrapped.prototype = NativeCtor.prototype;
                try { Object.setPrototypeOf(Wrapped, NativeCtor); } catch (e) {}
                return Wrapped;
            }
            if (window.Worker) window.Worker = __axWrapWorker(window.Worker);
            if (window.SharedWorker) window.SharedWorker = __axWrapWorker(window.SharedWorker);
        } catch (e) {}
    """.trimIndent()
}
