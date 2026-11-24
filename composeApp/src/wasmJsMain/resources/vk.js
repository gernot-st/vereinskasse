// Browser-Funktionen der Vereinskasse (Speichern, Teilen, Dateien) – aufgerufen aus Kotlin/Wasm.
(function () {
  'use strict';
  var DB = 'vereinskasse', STORE = 'kv';

  var dbPromise = new Promise(function (res, rej) {
    var r = indexedDB.open(DB, 1);
    r.onupgradeneeded = function () { r.result.createObjectStore(STORE); };
    r.onsuccess = function () { res(r.result); };
    r.onerror = function () { rej(r.error); };
  });

  function tx(mode, fn) {
    return dbPromise.then(function (db) {
      return new Promise(function (res, rej) {
        var t = db.transaction(STORE, mode);
        var req = fn(t.objectStore(STORE));
        t.oncomplete = function () { res(req ? req.result : undefined); };
        t.onerror = function () { rej(t.error); };
        t.onabort = function () { rej(t.error); };
      });
    });
  }

  // Bitte den Browser, die Daten nicht automatisch zu löschen.
  if (navigator.storage && navigator.storage.persist) navigator.storage.persist().catch(function () {});

  function base64ZuBytes(b64) {
    var s = atob(b64), bytes = new Uint8Array(s.length);
    for (var i = 0; i < s.length; i++) bytes[i] = s.charCodeAt(i);
    return bytes;
  }

  function bytesZuBase64(bytes) {
    var s = '';
    for (var j = 0; j < bytes.length; j += 0x8000) s += String.fromCharCode.apply(null, bytes.subarray(j, j + 0x8000));
    return btoa(s);
  }

  function herunterladen(datei) {
    var a = document.createElement('a');
    a.href = URL.createObjectURL(datei);
    a.download = datei.name;
    document.body.appendChild(a);
    a.click();
    setTimeout(function () { URL.revokeObjectURL(a.href); a.remove(); }, 2000);
  }

  window.vk = {
    get: function (k) { return tx('readonly', function (s) { return s.get(k); }).then(function (v) { return v === undefined ? null : v; }); },
    set: function (k, v) { return tx('readwrite', function (s) { return s.put(v, k); }); },
    del: function (k) { return tx('readwrite', function (s) { return s.delete(k); }); },
    keys: function (prefix) {
      return tx('readonly', function (s) { return s.getAllKeys(); }).then(function (ks) {
        return JSON.stringify(ks.filter(function (k) { return String(k).indexOf(prefix) === 0; })
          .map(function (k) { return String(k).substring(prefix.length); }));
      });
    },

    shareText: function (text) {
      var kopieren = function () {
        if (navigator.clipboard) {
          navigator.clipboard.writeText(text).then(function () {
            alert('Der Text wurde in die Zwischenablage kopiert – z. B. in WhatsApp einfügen.');
          }, function () { herunterladen(new File([text], 'Auswertung.txt', { type: 'text/plain' })); });
        } else herunterladen(new File([text], 'Auswertung.txt', { type: 'text/plain' }));
      };
      if (navigator.share) {
        navigator.share({ text: text }).catch(function (e) { if (e && e.name !== 'AbortError') kopieren(); });
      } else kopieren();
    },

    shareFile: function (name, inhalt, mime, istBase64) {
      var datei = new File([istBase64 ? base64ZuBytes(inhalt) : inhalt], name, { type: mime });
      var teilbar = false;
      try { teilbar = !!(navigator.canShare && navigator.canShare({ files: [datei] })); } catch (e) {}
      if (teilbar) {
        navigator.share({ files: [datei] }).catch(function (e) { if (e && e.name !== 'AbortError') herunterladen(datei); });
      } else herunterladen(datei);
    },

    // Liefert JSON {"name": ..., "b64": ...} oder null (abgebrochen)
    pickFile: function (accept) {
      return new Promise(function (res) {
        var input = document.createElement('input');
        input.type = 'file';
        if (accept) input.accept = accept;
        input.style.display = 'none';
        document.body.appendChild(input);
        input.addEventListener('cancel', function () { input.remove(); res(null); });
        input.onchange = function () {
          var f = input.files && input.files[0];
          input.remove();
          if (!f) { res(null); return; }
          var r = new FileReader();
          r.onload = function () { res(JSON.stringify({ name: f.name, b64: bytesZuBase64(new Uint8Array(r.result)) })); };
          r.onerror = function () { res(null); };
          r.readAsArrayBuffer(f);
        };
        input.click();
      });
    },

    fertig: function () {
      var l = document.getElementById('laden');
      if (l) l.remove();
    }
  };

  if ('serviceWorker' in navigator && (location.protocol === 'https:' || location.hostname === 'localhost')) {
    window.addEventListener('load', function () { navigator.serviceWorker.register('sw.js').catch(function () {}); });
  }
})();
