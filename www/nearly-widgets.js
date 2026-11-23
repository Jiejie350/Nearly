/*
 * nearly-widgets.js  — friendly wrapper around the native NearlyWidget plugin.
 * Include it before your app code:   <script src="nearly-widgets.js"></script>
 * Everything is a no-op in a normal browser, so the web build keeps working.
 *
 * Data model (what the widgets read; all under  <url>/c/<code>/ ):
 *   meta:   { start:"2024-02-14", bday:{ <slot>:"1999-05-04" } }
 *   events: { id:{ title, date:"YYYY-MM-DD", type:"anniversary|birthday|meetup|vacation|special|custom|milestone", yearly:bool, emoji } }
 *   tasks:  { id:{ title, done:bool, by, ts } }
 *   <slot>: written by the widgets/plugin (hearts, kisses, hugs, mood, activity, loc, batt, online...)
 */
(function () {
  var P = (window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.NearlyWidget) || null;
  var noop = function () { return Promise.resolve({}); };
  var call = function (m, a) { return P && P[m] ? P[m](a || {}) : noop(); };
  var uid = function () { return Date.now().toString(36) + Math.random().toString(36).slice(2, 5); };

  // Resize an <img>/<canvas>/File to a small JPEG base64 (widgets only need ~640px).
  function toBase64(src, max, quality) {
    max = max || 640; quality = quality || 0.72;
    return new Promise(function (res, rej) {
      var draw = function (img) {
        var s = Math.min(1, max / Math.max(img.width, img.height));
        var c = document.createElement('canvas');
        c.width = Math.round(img.width * s); c.height = Math.round(img.height * s);
        c.getContext('2d').drawImage(img, 0, 0, c.width, c.height);
        res(c.toDataURL('image/jpeg', quality).split(',')[1]);
      };
      if (src instanceof HTMLCanvasElement) return res(src.toDataURL('image/png').split(',')[1]);
      var img = new Image();
      img.onload = function () { draw(img); }; img.onerror = rej;
      img.src = (src instanceof Blob) ? URL.createObjectURL(src) : src;
    });
  }

  var W = {
    available: !!P,

    /** Call after pairing / on app start. Same fields as the old `update`, plus optional names + partner slot. */
    configure: function (o) { return call('update', o); },          // {url, code, slot, pslot, name, partnerName, l1,l2,l3, hearts}
    syncNow: function () { return call('syncNow'); },
    refresh: function () { return call('refreshWidgets'); },
    getCache: function () { return call('getCache'); },             // offline snapshot of the couple node

    // ---- love / affection
    sendHeart: function () { return call('sendAffection', { kind: 'heart' }); },
    sendKiss: function () { return call('sendAffection', { kind: 'kiss' }); },
    sendHug: function () { return call('sendAffection', { kind: 'hug' }); },
    ping: function (title, body) { return call('notifyPartner', { title: title, body: body }); },
    setMyMessage: function (text) {   // "custom message from your partner" widget shows this on THEIR side
      return call('write', { path: 'SLOT/msg', value: text });        // replaced below once slot is known
    },

    // ---- status
    setStatus: function (o) { return call('setStatus', o); },       // {mood, moodMsg, activity, status}
    setOnline: function (b) { return call('setOnline', { online: b }); },

    // ---- relationship / events / tasks / notes (generic synced writes)
    setStartDate: function (iso) { return call('write', { path: 'meta/start', value: iso }); },
    setBirthday: function (slot, iso) { return call('write', { path: 'meta/bday/' + slot, value: iso }); },
    addEvent: function (e) { var id = e.id || uid(); delete e.id; return call('write', { path: 'events/' + id, value: e }).then(function () { return id; }); },
    removeEvent: function (id) { return call('write', { path: 'events/' + id, value: null }); },
    addTask: function (title, by) { return call('write', { path: 'tasks/' + uid(), value: { title: title, done: false, by: by || '', ts: Date.now() } }); },
    completeTask: function (id) { return call('write', { path: 'tasks/' + id + '/done', value: true }); },
    // ---- chat: text or handwriting (what the Messages widget shows)
    sendMessage: function (text, editId) { return call('sendMessage', { text: text, editId: editId || '' }); },
    sendDrawing: function (canvas) { return call('sendDrawing', { base64: canvas.toDataURL('image/png') }); },
    getImage: function (id) { return call('getImage', { id: id }).then(function (r) { return r && r.dataUrl || ''; }); },
    pull: function () { return call('pull'); },                                  // light refresh while Chat is open
    signal: function (path, value) { return call('signal', { path: path, value: value }); },   // quiet write (typing, seen, calls)
    fetchPath: function (path) { return call('fetchPath', { path: path }).then(function (r) { return r ? r.data : null; }); },
    /** Chat messages, oldest first: [{id, type:'text'|'draw', text, img, by, ts, edited}] */
    getMessages: function () {
      return call('getCache').then(function (r) {
        var n = (r && r.data && r.data.notes) || {}, out = [];
        Object.keys(n).forEach(function (k) { var j = n[k]; if (j && j.kind === 'chat') { j.id = k; out.push(j); } });
        return out.sort(function (a, b) { return a.ts - b.ts; });
      });
    },
    addNote: function (n) {            // {text, kind:'love|sticky|private|reminder', by, to?, color?}
      n.ts = Date.now(); return call('write', { path: 'notes/' + uid(), value: n });
    },
    write: function (path, value) { return call('write', { path: path, value: value }); },

    // ---- photos  (src = File | Blob | URL | <canvas>)
    addPhoto: function (src, o) {
      o = o || {};
      return toBase64(src).then(function (b64) {
        return call('addPhoto', { base64: b64, kind: o.kind || 'gallery', caption: o.caption || '', fav: !!o.fav, date: o.date, id: o.id });
      });
    },
    setPartnerPhoto: function (src) { return W.addPhoto(src, { kind: 'partner' }); },
    setCouplePhoto: function (src) { return W.addPhoto(src, { kind: 'couple' }); },
    saveHandwriting: function (canvas) { return W.addPhoto(canvas, { kind: 'hw' }); },   // transparent PNG canvas
    setPhotoMeta: function (id, o) { return call('setPhotoMeta', Object.assign({ id: id }, o)); },
    removePhoto: function (id) { return call('removePhoto', { id: id }); },

    // ---- privacy / location
    setPrivacy: function (o) { return call('setPrivacy', o); },     // {shareBattery, shareLocation, locationPrecision:'exact|approx|city', sharePresence, maskPrivate, notifications}
    getPrivacy: function () { return call('getPrivacy'); },
    setLocation: function (lat, lng, name) { return call('setLocation', { lat: lat, lng: lng, name: name }); },
    askPermission: function (what) { return call('askPermission', { what: what }); },   // 'notifications' | 'location' | 'locationAlways'
    permissionState: function () { return call('permissionState'); },

    // ---- widget management
    listWidgets: function () { return call('listWidgets'); },
    pinWidget: function (type) { return call('pinWidget', { type: type }); },   // photo|love|relationship|status|location|mood|countdown|msg|interactive|game|candle
    getWidgetSettings: function (id) { return call('getWidgetSettings', { id: id }); },
    setWidgetSettings: function (id, settings) { return call('setWidgetSettings', { id: id, settings: settings }); },
    resetWidget: function (id) { return call('resetWidget', { id: id }); },
    editWidget: function (id) { return call('editWidget', { id: id }); },       // opens that widget's settings screen

    /** Which page did the user tap in a widget? ('photos','chat','timeline','events','mood','games','location','candle','home') */
    takePendingPage: function () { return call('getPendingPage').then(function (r) { return r && r.page || ''; }); }
  };

  // setMyMessage needs your slot: remember it from configure()
  var _slot = '';
  var _cfg = W.configure;
  W.configure = function (o) { if (o && o.slot) _slot = o.slot; return _cfg(o); };
  W.setMyMessage = function (text) {
    return call('write', { path: _slot + '/msg', value: text }).then(function () {
      return call('write', { path: _slot + '/msgTs', value: Date.now() });
    });
  };

  // Jump to the page a widget was tapped from
  document.addEventListener('visibilitychange', function () {
    if (document.visibilityState === 'visible') W.takePendingPage().then(function (p) {
      if (p) window.dispatchEvent(new CustomEvent('nearly-page', { detail: p }));
    });
  });

  window.NearlyWidgets = W;
})();
