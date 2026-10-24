/**
 * TraceX Intelligence Platform - Core Application Scripts
 * Manages theme switching, navigation active state, demo credential shortcuts, interactive UI states
 */
(function () {
  'use strict';

  var currentTheme = localStorage.getItem('tracex-theme') || 'dark';
  document.documentElement.setAttribute('data-theme', currentTheme);

  function toggleTheme() {
    var active = document.documentElement.getAttribute('data-theme') || 'dark';
    var next = active === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', next);
    localStorage.setItem('tracex-theme', next);
    updateThemeIcon(next);
  }

  function updateThemeIcon(theme) {
    var iconContainer = document.getElementById('theme-icon');
    if (!iconContainer) return;
    if (theme === 'light') {
      iconContainer.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"></path></svg>';
    } else {
      iconContainer.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="5"></circle><line x1="12" y1="1" x2="12" y2="3"></line><line x1="12" y1="21" x2="12" y2="23"></line><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"></line><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"></line><line x1="1" y1="12" x2="3" y2="12"></line><line x1="21" y1="12" x2="23" y2="12"></line><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"></line><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"></line></svg>';
    }
  }

  document.addEventListener('DOMContentLoaded', function () {
    // 1. Theme Toggle
    var btn = document.getElementById('theme-toggle-btn');
    if (btn) {
      btn.addEventListener('click', toggleTheme);
      updateThemeIcon(document.documentElement.getAttribute('data-theme') || 'dark');
    }

    // 2. Client-Side Active Nav Highlight (safe, 100% reliable)
    var navLinks = document.querySelectorAll('.nav-links a');
    var path = window.location.pathname;
    navLinks.forEach(function (a) {
      var href = a.getAttribute('href');
      if (href === path || (href !== '/' && path.indexOf(href) === 0)) {
        a.classList.add('active');
      }
    });

    // 3. Demo Login Fast-Fill
    var demoPills = document.querySelectorAll('.demo-pill');
    demoPills.forEach(function (pill) {
      pill.addEventListener('click', function () {
        var user = this.getAttribute('data-user');
        var pass = this.getAttribute('data-pass');
        var userInput = document.querySelector('input[name="username"]');
        var passInput = document.querySelector('input[name="password"]');
        if (userInput && passInput) {
          userInput.value = user;
          passInput.value = pass;
        }
      });
    });
  });
})();
