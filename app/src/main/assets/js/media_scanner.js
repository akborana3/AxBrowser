(function() {
    var found = [];
    
    document.querySelectorAll('video, audio').forEach(function(el) {
        var src = el.src || el.currentSrc;
        if (src && src.startsWith('http')) found.push({url: src, type: el.tagName.toLowerCase()});
        el.querySelectorAll('source').forEach(function(s) {
            if (s.src) found.push({url: s.src, type: 'source', quality: s.getAttribute('label') || ''});
        });
    });
    
    document.querySelectorAll('a[href]').forEach(function(a) {
        var href = a.href;
        if (/\.(mp4|webm|mp3|m4a|mkv|avi|mov|flac|wav)(\?|$)/i.test(href)) {
            found.push({url: href, type: 'link', title: a.textContent.trim()});
        }
    });
    
    return JSON.stringify(found);
})();
