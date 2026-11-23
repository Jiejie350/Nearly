NEARLY WIDGET SYSTEM  (Capacitor / Android)
===========================================

WHAT'S NEW
  11 home-screen widget types (every widget has a ⚙ edit button) covering all 14 feature groups, each with its own
  settings screen, live preview, sync, offline cache, and notifications.
  The original "classic" widget still works unchanged.

  Widget (picker name)      Modes (chosen per widget in its settings)
  ------------------------  -------------------------------------------------------------
  Nearly · Photos           partner photo, couple photo, rotating gallery, favorites, recent,
                            a specific photo | frames, captions, date stamps
  Nearly · Love             I love you, random msg, daily msg, partner's msg, kiss counter,
                            hug button, send-affection (heart/kiss/hug/note)
  Nearly · Together         days together, total time, next anniversary, monthly milestones,
                            timeline, special-date reminders
  Nearly · Partner status   online, last active, mood, activity, battery, status message, full card
  Nearly · Location         shared location, distance, travel estimate, last update, sharing status
  Nearly · Mood             partner mood, daily selector, history, shared, calendar, mood message
  Nearly · Countdown        next date, anniversary, birthday, meetup, vacation, special, custom
  Nearly · Messages         ONE mode, nothing to pick: shows the latest message between you two, text or
                            handwriting. Buttons: ✏️ Write (type and/or draw, edit your last one), 💬 Chat, ⚙ Edit.
                            Checks for new messages every 10 minutes, automatically (fixed, not a setting).
  Nearly · Quick actions    any 4 of: heart, kiss, hug, mood, note, task done, notify partner, game launcher
  Nearly · Couple games     Tic-Tac-Toe, daily question, never-have-I-ever, daily challenge,
                            random question, game invitations
  Nearly · Candle           shared candle, tap to light/blow out, brightness + connection indicator

  Customisation (per widget instance): small/medium/large, light/dark/auto, gradient/solid/
  photo/transparent background, custom colours, corner radius, transparency, font, text size,
  alignment, show/hide title/details/buttons, refresh interval, reset to defaults.
  Several instances of the same widget can sit on the home screen, each fully independent.

INSTALL
  1. Copy res/* into android/app/src/main/res/ (merge folders).
  2. Copy java/*.java into android/app/src/main/java/<your/package/path>/
  3. Package is already set to com.benjamin.nearly. If you use a different one, run:   ./set-package.sh your.package.name
     (or edit the first line of each .java file and the "configure" line in res/xml/*.xml)
     If you already have a MainActivity, keep yours and copy in: registerPlugin(NearlyWidgetPlugin.class)
     before super.onCreate, plus the onResume/onPause/onNewIntent hooks from the provided one
     (they power "online" status and widget-tap deep links).
  4. Paste AndroidManifest-snippet.xml: the permissions before <application>, the rest inside it.
  5. Add nearly-widgets.js to your web app (<script src="nearly-widgets.js"></script>), then:
        NearlyWidgets.configure({ url, code, slot, pslot, name, partnerName })
     (same url/code/slot you already send to the old `update` call; pslot = your partner's slot).
  6. npx cap sync android, build the APK, long-press home screen > Widgets > "Nearly ·".

DATA (REST, same style as before)
  <url>/c/<code>/<slot>/<field>.json      hearts, kisses, hugs, mood, moodMsg, activity, status, msg,
                                          batt, charging, online, active, locShare, loc{lat,lng,ts,name}
  <url>/c/<code>/meta | events | notes | tasks | candle | game | notify | photoIndex
  <url>/p/<code>/<photoId>.json           photo bytes (base64 string) - kept out of the main node
  If your database has security rules, allow read/write on /p/<code> as well as /c/<code>.
  The JS wrapper header documents the shapes (start date, events, notes, tasks).

PRIVACY DEFAULTS (all changeable in any widget's settings or via NearlyWidgets.setPrivacy)
  Share online status: on · mood/activity: on · battery: OFF · location: OFF
  Location precision: approximate (~1 km) · private messages hidden on home screen: on

MESSAGES (chat)
  notes/<id> = { kind:"chat", type:"text"|"draw", text, img, by, ts, edited }.  Handwriting images are
  stored like photos (photoIndex kind "msg", newest 30 kept).  The app's Chat tab and the widget's
  Write screen use the same data.  Pair both phones in the app: Us > Connect.

CHAT + AUDIO CALLS (in the app)
  Chat tab: Messenger-style header (name, Active now / typing...), grouped bubbles with day separators,
  Sent / Seen receipts, double-tap to heart a message, handwriting pad, edit last message.
  Calls: WebRTC audio. Signalling is the "call" node in the same database; both phones need the app
  open (ring checks every ~2.5 s while it is in the foreground). Uses free Google STUN servers only, so
  some mobile networks (strict NAT) may fail to connect: add a TURN server to RTCCFG in www/index.html.
  Extra paths used: seen/<slot>, typing/<slot>, call, notes/<id>/react/<slot>.

KNOWN LIMITS (Android, not bugs)
  - Messages widget: the 10-minute check is an inexact alarm. Android can delay it while the phone is in
    deep sleep or aggressive battery saver (some brands). A 15-min background job backs it up. Instant
    delivery would need Firebase Cloud Messaging.
  - "Real-time" = near real-time: sync runs when the app opens, on every widget tap, via a
    periodic background job (Android minimum 15 min) and the widget update timer. True push
    needs Firebase Cloud Messaging; call NearlyWidgets.syncNow() from your own listener if you add it.
  - Background location needs the user to grant "Allow all the time"; without it location updates
    when the app is open (or if you push positions from JS with NearlyWidgets.setLocation).
  - Auto light/dark theme updates at the next widget refresh, not instantly.
  - Only the user can remove a widget from the home screen; the app can pin new ones
    (NearlyWidgets.pinWidget) and reset/reconfigure existing ones.
  - Travel estimate is straight-line x1.3 at 60 km/h, not real routing.
  - Handwritten notes: draw on a canvas in your app and call NearlyWidgets.saveHandwriting(canvas).

NOT YET BUILT/TESTED
  This was written without an Android SDK: the Java was syntax-checked and the XML validated,
  but not compiled against Android or run on a device. Build once and send me any compiler or
  runtime errors and I'll fix them.
