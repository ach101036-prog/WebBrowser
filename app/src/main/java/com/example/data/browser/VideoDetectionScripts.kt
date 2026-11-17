package com.example.data.browser

object VideoDetectionScripts {

    /**
     * Injected into every page loaded inside WebView.
     * Detects HTML5 <video> elements (especially when actively playing), extracts dimensions
     * and duration, and hooks fetch/XHR requests to surface HLS (.m3u8) / DASH (.mpd) / MP4
     * streams behind MSE blob: URLs.
     */
    val VIDEO_SNIFFER_JS: String = """
        (function() {
            if (window.__flowBrowserSnifferInstalled) {
                if (typeof window.__flowScanVideos === 'function') {
                    window.__flowScanVideos();
                }
                return;
            }
            window.__flowBrowserSnifferInstalled = true;

            function bridgeEmit(payload) {
                try {
                    if (window.FlowVideoBridge && typeof window.FlowVideoBridge.onVideoDetected === 'function') {
                        window.FlowVideoBridge.onVideoDetected(JSON.stringify(payload));
                    }
                } catch (e) {}
            }

            function resolveVideoSrc(video) {
                if (!video) return '';
                if (video.currentSrc && video.currentSrc.indexOf('blob:') !== 0) {
                    return video.currentSrc;
                }
                if (video.src && video.src.indexOf('blob:') !== 0) {
                    return video.src;
                }
                var sources = video.querySelectorAll('source');
                for (var i = 0; i < sources.length; i++) {
                    var s = sources[i].src || sources[i].getAttribute('src') || '';
                    if (s && s.indexOf('blob:') !== 0) {
                        return s;
                    }
                }
                return video.currentSrc || video.src || '';
            }

            function emitVideoElement(video, forcePlaying) {
                try {
                    var src = resolveVideoSrc(video);
                    if (!src) return;
                    var isPlaying = !!(forcePlaying || (!video.paused && !video.ended && video.readyState >= 2));
                    var dur = (video.duration && isFinite(video.duration)) ? video.duration : 0;
                    bridgeEmit({
                        type: 'dom_video',
                        url: src,
                        pageUrl: window.location.href || '',
                        title: document.title || 'Web Video',
                        width: video.videoWidth || 0,
                        height: video.videoHeight || 0,
                        durationSec: dur,
                        isPlaying: isPlaying
                    });
                } catch (e) {}
            }

            function inspectRoot(root) {
                try {
                    var videos = root.querySelectorAll('video');
                    for (var i = 0; i < videos.length; i++) {
                        emitVideoElement(videos[i], false);
                    }
                    var all = root.querySelectorAll('*');
                    for (var j = 0; j < all.length; j++) {
                        if (all[j].shadowRoot) {
                            inspectRoot(all[j].shadowRoot);
                        }
                    }
                } catch (e) {}
            }

            window.__flowScanVideos = function() {
                inspectRoot(document);
            };

            var mediaEvents = ['play', 'playing', 'loadedmetadata', 'canplay', 'pause', 'ended'];
            for (var k = 0; k < mediaEvents.length; k++) {
                (function(evtName) {
                    document.addEventListener(evtName, function(e) {
                        var target = e && e.target;
                        if (target && target.tagName && target.tagName.toLowerCase() === 'video') {
                            emitVideoElement(target, evtName === 'play' || evtName === 'playing');
                        }
                    }, true);
                })(mediaEvents[k]);
            }

            // Hook HTMLMediaElement.prototype.play for programmatic playback detection
            try {
                var origPlay = HTMLMediaElement.prototype.play;
                HTMLMediaElement.prototype.play = function() {
                    if (this && this.tagName && this.tagName.toLowerCase() === 'video') {
                        emitVideoElement(this, true);
                    }
                    return origPlay.apply(this, arguments);
                };
            } catch (e) {}

            // Hook fetch & XHR to catch dynamic .m3u8 / .mpd / .mp4 streams when <video> uses blob:
            function maybeEmitNetworkUrl(rawUrl) {
                try {
                    if (!rawUrl || typeof rawUrl !== 'string') return;
                    var abs = new URL(rawUrl, window.location.href).href;
                    var lower = abs.toLowerCase();
                    if (lower.indexOf('.m3u8') !== -1 ||
                        lower.indexOf('.mpd') !== -1 ||
                        lower.indexOf('.mp4') !== -1 ||
                        lower.indexOf('.webm') !== -1 ||
                        lower.indexOf('.mov') !== -1) {
                        bridgeEmit({
                            type: 'network_hint',
                            url: abs,
                            pageUrl: window.location.href || '',
                            title: document.title || 'Web Video',
                            width: 0,
                            height: 0,
                            durationSec: 0,
                            isPlaying: false
                        });
                    }
                } catch (e) {}
            }

            try {
                var origFetch = window.fetch;
                if (origFetch) {
                    window.fetch = function(input) {
                        try {
                            var reqUrl = (typeof input === 'string') ? input : (input && input.url);
                            maybeEmitNetworkUrl(reqUrl);
                        } catch (e) {}
                        return origFetch.apply(this, arguments);
                    };
                }
            } catch (e) {}

            try {
                var origXhrOpen = XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open = function(method, url) {
                    maybeEmitNetworkUrl(url);
                    return origXhrOpen.apply(this, arguments);
                };
            } catch (e) {}

            window.__flowScanVideos();
            setInterval(window.__flowScanVideos, 1800);
        })();
    """.trimIndent()
}
