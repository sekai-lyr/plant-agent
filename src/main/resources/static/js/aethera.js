(function () {
    'use strict';

    var video = document.getElementById('heroVideo');
    var nav = document.getElementById('siteNav');
    var menuToggle = document.querySelector('.menu-toggle');
    var fadeDuration = 0.5;
    var replayTimer;
    var animationFrame;
    var replaying = false;
    var openPanel;

    function setVideoOpacity(value) {
        if (video) video.style.opacity = String(Math.max(0, Math.min(1, value)));
    }

    function monitorVideo() {
        if (!video) return;

        var duration = video.duration;
        var currentTime = video.currentTime;

        if (Number.isFinite(duration) && duration > 0 && !replaying) {
            if (currentTime < fadeDuration) {
                setVideoOpacity(currentTime / fadeDuration);
            } else if (duration - currentTime < fadeDuration) {
                setVideoOpacity((duration - currentTime) / fadeDuration);
            } else {
                setVideoOpacity(1);
            }
        }

        animationFrame = window.requestAnimationFrame(monitorVideo);
    }

    function restartVideo() {
        if (!video || replaying) return;

        replaying = true;
        setVideoOpacity(0);
        window.clearTimeout(replayTimer);
        replayTimer = window.setTimeout(function () {
            video.currentTime = 0;
            var playRequest = video.play();
            if (playRequest && typeof playRequest.catch === 'function') {
                playRequest.catch(function () {});
            }
            replaying = false;
        }, 100);
    }

    if (video) {
        video.addEventListener('ended', restartVideo);
        video.addEventListener('loadedmetadata', function () {
            var playRequest = video.play();
            if (playRequest && typeof playRequest.catch === 'function') {
                playRequest.catch(function () {});
            }
        });
        animationFrame = window.requestAnimationFrame(monitorVideo);
    }

    if (menuToggle && nav) {
        menuToggle.addEventListener('click', function () {
            var isOpen = nav.classList.toggle('is-open');
            menuToggle.setAttribute('aria-expanded', String(isOpen));
        });

        nav.addEventListener('click', function (event) {
            if (event.target.closest('a')) {
                nav.classList.remove('is-open');
                menuToggle.setAttribute('aria-expanded', 'false');
            }
        });
    }

    function closePanel(panel) {
        if (!panel) return;
        panel.hidden = true;
        if (openPanel === panel) openPanel = null;
    }

    document.querySelectorAll('[data-panel-target]').forEach(function (trigger) {
        trigger.addEventListener('click', function (event) {
            var panel = document.getElementById(trigger.getAttribute('data-panel-target'));
            if (!panel) return;
            event.preventDefault();
            if (openPanel) closePanel(openPanel);
            panel.hidden = false;
            openPanel = panel;
            var closeButton = panel.querySelector('.panel-close');
            if (closeButton) closeButton.focus();
        });
    });

    document.querySelectorAll('.site-panel').forEach(function (panel) {
        panel.addEventListener('click', function (event) {
            if (event.target === panel || event.target.closest('.panel-close')) {
                closePanel(panel);
            }
        });
    });

    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape' && openPanel) closePanel(openPanel);
    });

    fetch('/api/auth/me')
        .then(function (response) { return response.json(); })
        .then(function (data) {
            if (data.code === 200) window.location.href = '/home';
        })
        .catch(function () {});

    window.addEventListener('pagehide', function () {
        window.cancelAnimationFrame(animationFrame);
        window.clearTimeout(replayTimer);
    });
})();
