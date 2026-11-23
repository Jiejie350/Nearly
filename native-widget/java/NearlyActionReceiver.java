package com.benjamin.nearly; // CHANGE to your app's package

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;

/**
 * Handles every tap on a widget button. Order matters for feel: update the local cache and repaint
 * first (instant feedback), then push to the cloud (queued if offline).
 */
public class NearlyActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context ctx, final Intent i) {
        final PendingResult pr = goAsync();
        final Context c = ctx.getApplicationContext();
        final int id = i.getIntExtra("id", 0);
        final String act = i.getStringExtra("act") == null ? "" : i.getStringExtra("act");
        final String arg = i.getStringExtra("arg") == null ? "" : i.getStringExtra("arg");
        new Thread(new Runnable() {
            public void run() {
                try {
                    handle(c, id, act, arg);
                } catch (Throwable t) {
                    NearlyStore.setFeedback(c, "Couldn't do that — try again");
                    NearlyWidgets.refreshAll(c);
                } finally {
                    pr.finish();
                }
            }
        }).start();
    }

    static void handle(Context c, int id, String act, String arg) {
        if (act.equals("refresh")) {
            NearlyStore.setFeedback(c, "Refreshing…");
            NearlyWidgets.refreshAll(c);
            NearlySync.run(c);
            return;
        }
        if (!NearlyStore.paired(c)) {
            NearlyStore.setFeedback(c, "Pair in the app first");
            NearlyWidgets.refreshAll(c);
            return;
        }
        NearlyData d = new NearlyData(c);
        String slot = d.slot, pslot = d.pslot;
        String day = NearlyUtil.todayKey();
        long ts = NearlyUtil.now();
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();

        switch (act) {
            case "heart": {
                int n = Math.max(NearlyStore.sp(c).getInt("hearts", 0), d.me().optInt("hearts", 0)) + 1;
                NearlyStore.sp(c).edit().putInt("hearts", n).putLong("hl", ts).apply();
                m.put(slot + "/hearts", n);
                m.put(slot + "/hl", ts);
                NearlyStore.setFeedback(c, "💗 Heart sent");
                break;
            }
            case "kiss": {
                m.put(slot + "/kisses", d.me().optInt("kisses", 0) + 1);
                m.put(slot + "/kl", ts);
                NearlyStore.setFeedback(c, "💋 Kiss sent");
                break;
            }
            case "hug": {
                m.put(slot + "/hugs", d.me().optInt("hugs", 0) + 1);
                m.put(slot + "/gl", ts);
                NearlyStore.setFeedback(c, "🤗 Hug sent");
                break;
            }
            case "setmood":
                NearlyStore.setFeedback(c, NearlyContent.moodEmoji(arg) + " Mood updated");
                NearlySync.setMood(c, arg, null, null, null);
                return;
            case "notify": {
                NearlyStore.setFeedback(c, "🔔 Nudge sent");
                NearlySync.notifyPartner(c, "💭 " + d.myName() + " is thinking of you", "");
                return;
            }
            case "task": {
                List<JSONObject> open = d.openTasks();
                if (open.isEmpty()) { NearlyStore.setFeedback(c, "No open tasks"); NearlyWidgets.refreshAll(c); return; }
                String tid = open.get(0).optString("id");
                m.put("tasks/" + tid + "/done", true);
                m.put("tasks/" + tid + "/doneBy", slot);
                NearlyStore.setFeedback(c, "✅ Done: " + NearlyUtil.clip(open.get(0).optString("title"), 28));
                break;
            }
            case "rdone": {
                for (JSONObject n : d.notes("reminder")) {
                    if (n.optBoolean("done", false)) continue;
                    m.put("notes/" + n.optString("id") + "/done", true);
                    NearlyStore.setFeedback(c, "✅ Reminder done");
                    break;
                }
                break;
            }
            case "candle": {
                boolean lit = d.myFlame() > 0;
                m.put("candle/lit/" + slot, lit ? 0L : ts);
                NearlyStore.setFeedback(c, lit ? "💨 Candle out" : "🕯️ Candle lit");
                break;
            }
            case "next": {
                NearlyStore.setW(c, id, "photoOff", String.valueOf(NearlyStore.wi(c, id, "photoOff", 0) + 1));
                NearlyWidgets.refreshAll(c);
                return;
            }
            case "rnd": {
                NearlyStore.setW(c, id, "rnd", String.valueOf(new Random().nextInt(100000)));
                NearlyWidgets.refreshAll(c);
                return;
            }
            case "loc": {
                boolean now = !NearlyStore.priv(c, "shareLocation", false);
                NearlyStore.setPriv(c, "shareLocation", now);
                NearlyStore.setFeedback(c, now ? "📍 Sharing on" : "📍 Sharing paused");
                NearlyWidgets.refreshAll(c);
                NearlySync.pushDevice(c);
                NearlySync.run(c);
                return;
            }
            case "ttt": {
                int cell = Integer.parseInt(arg);
                JSONObject b = d.o("game").optJSONObject("ttt");
                String board = b == null ? "---------" : b.optString("b", "---------");
                String turn = b == null ? "x" : b.optString("t", "x");
                char me = NearlyWidgetContent.mySym(d);
                if (board.length() != 9 || board.charAt(cell) != '-' || turn.isEmpty() || turn.charAt(0) != me
                        || !(b == null || b.optString("w", "").isEmpty())) return;
                char[] arr = board.toCharArray();
                arr[cell] = me;
                String nb = new String(arr);
                String w = NearlyWidgetContent.tttWinner(nb);
                m.put("game/ttt/b", nb);
                m.put("game/ttt/t", me == 'x' ? "o" : "x");
                m.put("game/ttt/w", w);
                break;
            }
            case "tttnew": {
                m.put("game/ttt/b", "---------");
                m.put("game/ttt/t", "x");
                m.put("game/ttt/w", "");
                break;
            }
            case "chdone": {
                m.put("game/challenge/" + day + "/" + slot, true);
                NearlyStore.setFeedback(c, "✅ Challenge done");
                break;
            }
            case "nhie": {
                JSONObject nh = d.o("game").optJSONObject("nhie");
                int n = NearlyContent.NHIE.length;
                int idx = nh != null && day.equals(nh.optString("day")) ? nh.optInt("idx", 0) : (int) (NearlyUtil.today() % n);
                idx = ((idx % n) + n) % n;
                m.put("game/nhie/day", day);
                m.put("game/nhie/idx", idx);
                m.put("game/nhie/ans/" + idx + "/" + slot, arg);
                break;
            }
            case "nhienext": {
                JSONObject nh = d.o("game").optJSONObject("nhie");
                int n = NearlyContent.NHIE.length;
                int idx = nh != null && day.equals(nh.optString("day")) ? nh.optInt("idx", 0) : (int) (NearlyUtil.today() % n);
                m.put("game/nhie/day", day);
                m.put("game/nhie/idx", (idx + 1) % n);
                break;
            }
            case "invite": {
                JSONObject inv = new JSONObject();
                NearlyUtil.put(inv, "from", slot);
                NearlyUtil.put(inv, "to", pslot);
                NearlyUtil.put(inv, "game", arg);
                NearlyUtil.put(inv, "ts", ts);
                NearlyUtil.put(inv, "st", "");
                m.put("game/invite", inv);
                if (arg.equals("Tic-Tac-Toe")) {
                    m.put("game/ttt/b", "---------");
                    m.put("game/ttt/t", "x");
                    m.put("game/ttt/w", "");
                }
                NearlyStore.setFeedback(c, "🎮 Invited to " + arg);
                break;
            }
            case "inviteyes":
            case "inviteno": {
                m.put("game/invite/st", act.equals("inviteyes") ? "accepted" : "declined");
                NearlyStore.setFeedback(c, act.equals("inviteyes") ? "🎮 Accepted" : "Declined");
                break;
            }
            default:
                return;
        }
        NearlySync.writeAll(c, m);
    }
}
