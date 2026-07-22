if (typeof eruda === 'undefined') {
    var script = document.createElement('script');
    script.src = 'https://cdn.jsdelivr.net/npm/eruda';
    script.onload = function() {
        eruda.init({
            tool: ['console', 'elements', 'network', 'resources', 'info'],
            useShadowDom: true,
            autoScale: true,
            defaults: {
                displaySize: 60,
                transparency: 0.95,
                theme: 'Dark'
            }
        });
    };
    document.head.appendChild(script);
} else {
    eruda.show();
}
