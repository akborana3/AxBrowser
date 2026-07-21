package com.akay.feature.browser.webview

import android.webkit.JavascriptInterface
import com.akay.core.domain.model.MediaItem
import com.akay.core.domain.model.MediaType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class MediaDetector {

    private val _detectedMedia = MutableSharedFlow<MediaItem>(extraBufferCapacity = 10)
    val detectedMedia: SharedFlow<MediaItem> = _detectedMedia.asSharedFlow()

    @JavascriptInterface
    fun onMediaDetected(url: String, type: String, mimeType: String, title: String, sourceUrl: String) {
        val mediaType = when (type.lowercase()) {
            "video" -> MediaType.VIDEO
            "audio" -> MediaType.AUDIO
            "hls" -> MediaType.HLS
            "image" -> MediaType.IMAGE
            else -> MediaType.VIDEO
        }

        val mediaItem = MediaItem(
            url = url,
            type = mediaType,
            mimeType = mimeType,
            title = title,
            sourceUrl = sourceUrl
        )

        _detectedMedia.tryEmit(mediaItem)
    }

    companion object {
        const val MEDIA_SCAN_JS = """
            (function() {
                var mediaItems = [];
                
                // Scan video elements
                document.querySelectorAll('video, video source').forEach(function(el) {
                    var src = el.src || el.getAttribute('src');
                    if (src && !src.startsWith('blob:')) {
                        mediaItems.push({
                            url: src,
                            type: 'video',
                            mimeType: el.type || 'video/mp4',
                            title: document.title || '',
                            sourceUrl: window.location.href
                        });
                    }
                });
                
                // Scan audio elements
                document.querySelectorAll('audio, audio source').forEach(function(el) {
                    var src = el.src || el.getAttribute('src');
                    if (src && !src.startsWith('blob:')) {
                        mediaItems.push({
                            url: src,
                            type: 'audio',
                            mimeType: el.type || 'audio/mpeg',
                            title: document.title || '',
                            sourceUrl: window.location.href
                        });
                    }
                });
                
                // Scan links to media files
                var mediaExtensions = /\.(mp4|webm|ogg|mp3|m4a|flac|wav|m3u8|ts)(\?[^"']*)?$/i;
                document.querySelectorAll('a[href]').forEach(function(el) {
                    var href = el.href;
                    if (mediaExtensions.test(href)) {
                        var type = href.includes('.mp3') || href.includes('.m4a') || href.includes('.ogg') || href.includes('.wav') || href.includes('.flac') ? 'audio' : 'video';
                        mediaItems.push({
                            url: href,
                            type: type,
                            mimeType: '',
                            title: el.textContent.trim() || document.title || '',
                            sourceUrl: window.location.href
                        });
                    }
                });
                
                // Report to Android
                mediaItems.forEach(function(item) {
                    AxBrowserMediaDetector.onMediaDetected(
                        item.url,
                        item.type,
                        item.mimeType,
                        item.title,
                        item.sourceUrl
                    );
                });
            })();
        """
    }
}
