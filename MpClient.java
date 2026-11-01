package com.gtavsource.android;   // must stay in this package: TrainerBridge is package-private

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.*;
import java.util.*;

/**
 * Shared-world client for the Story Mode trainer.
 * Decoded from classes.dex:
 *   TrainerBridge.NATIVE.snapshot()                         -> JSON (ready, busy, epoch, x,y,z,heading, vehicle, entities[])
 *   TrainerBridge.NATIVE.submit(action, text, entityId, epoch, float[]) -> int (<=0 = error)
 *   ped.spawn        text=model, id=0, floats={}            (spawns near player; new entity shows up in snapshot.entities)
 *   entity.move      text="", id=entity, floats={x,y,z,0}   (0 = world coords, 1 = offset)  [mode 0 assumed from UI order]
 *   entity.toggle    text=flag, id=entity, floats={1|0}     (frozen/invincible/visible/collision)
 *   entity.delete    text="", id=entity, floats={}
 * Trainer rules: one command at a time (snapshot.busy), max 64 entities, Story Mode only.
 *
 * Usage (e.g. from your launcher Activity after the game starts):
 *   TrainerBridge.NATIVE.setEnabled(true);
 *   new MpClient("YOUR.SERVER.IP", 7777, "Nick").start();
 */
public class MpClient implements Runnable {
    private static final String GHOST_MODEL = "a_m_y_hipster_01"; // any ped model name your Spawn ped list shows
    private final String host, name; private final int port;
    private volatile boolean running;
    private volatile JSONObject world;                        // latest relay packet
    private final Map<Integer, Integer> ghosts = new HashMap<>();   // remote id -> local entity id
    private final Set<Integer> known = new HashSet<>();             // entity ids already claimed
    private final Deque<Integer> waitingSpawn = new ArrayDeque<>(); // remote ids waiting for entity
    private final Set<Integer> flagged = new HashSet<>();           // ghosts already frozen/invincible

    public MpClient(String host, int port, String name) { this.host = host; this.port = port; this.name = name; }
    public void start() { running = true; new Thread(this, "mp-client").start(); }
    public void stop() { running = false; }

    @Override public void run() {
        try (final DatagramSocket s = new DatagramSocket()) {
            final InetAddress addr = InetAddress.getByName(host);
            Thread rx = new Thread(new Runnable() { public void run() {      // receiver thread
                byte[] buf = new byte[16384];
                while (running) try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length); s.receive(p);
                    world = new JSONObject(new String(p.getData(), 0, p.getLength(), "UTF-8"));
                } catch (Exception ignore) { }
            } }, "mp-rx");
            rx.setDaemon(true); rx.start();

            long lastSend = 0;
            while (running) {
                JSONObject snap = new JSONObject(TrainerBridge.NATIVE.snapshot());
                if (!snap.optBoolean("ready")) { Thread.sleep(250); continue; }   // not in Story Mode yet
                long now = System.currentTimeMillis();
                if (now - lastSend >= 100) { lastSend = now; sendState(s, addr, snap); }
                if (!snap.optBoolean("busy")) step(snap);                          // at most one command per cycle
                Thread.sleep(30);
            }
            byte[] bye = "{\"t\":\"bye\"}".getBytes();
            s.send(new DatagramPacket(bye, bye.length, addr, port));
        } catch (Exception e) { android.util.Log.e("MpClient", "stopped", e); }
    }

    private void sendState(DatagramSocket s, InetAddress a, JSONObject snap) throws Exception {
        JSONObject o = new JSONObject().put("t", "state").put("name", name)
            .put("x", snap.optDouble("x")).put("y", snap.optDouble("y")).put("z", snap.optDouble("z"))
            .put("h", snap.optDouble("heading")).put("veh", snap.optInt("vehicle")).put("model", GHOST_MODEL);
        byte[] b = o.toString().getBytes("UTF-8");
        s.send(new DatagramPacket(b, b.length, a, port));
    }

    /** Decide the single next command. Priority: claim spawned entity > delete gone > spawn new > flags > move. */
    private void step(JSONObject snap) throws Exception {
        int epoch = snap.optInt("epoch");
        JSONArray ents = snap.optJSONArray("entities");
        Set<Integer> present = new HashSet<>();
        List<Integer> fresh = new ArrayList<>();
        if (ents != null) for (int i = 0; i < ents.length(); i++) {
            int id = ents.getJSONObject(i).getInt("id"); present.add(id);
            if (!known.contains(id)) fresh.add(id);
        }
        // 1) a spawn we requested has appeared -> claim it
        if (!waitingSpawn.isEmpty() && !fresh.isEmpty()) {
            int rid = waitingSpawn.poll(), eid = fresh.get(0);
            ghosts.put(rid, eid); known.add(eid); return;
        }
        // drop ghosts whose entity vanished
        for (Iterator<Map.Entry<Integer, Integer>> it = ghosts.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Integer, Integer> e = it.next();
            if (!present.contains(e.getValue())) { known.remove(e.getValue()); flagged.remove(e.getKey()); it.remove(); }
        }
        JSONObject w = world; if (w == null) return;
        JSONArray pl = w.getJSONArray("players");
        Set<Integer> alive = new HashSet<>();
        for (int i = 0; i < pl.length(); i++) alive.add(pl.getJSONObject(i).getInt("id"));
        // 2) player left -> delete its ghost
        for (Iterator<Map.Entry<Integer, Integer>> it = ghosts.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Integer, Integer> e = it.next();
            if (!alive.contains(e.getKey())) {
                submit("entity.delete", "", e.getValue(), epoch, new float[0]);
                known.remove(e.getValue()); flagged.remove(e.getKey()); it.remove(); return;
            }
        }
        for (int i = 0; i < pl.length(); i++) {
            JSONObject p = pl.getJSONObject(i); int rid = p.getInt("id");
            Integer eid = ghosts.get(rid);
            // 3) new player -> spawn a ghost (once; wait until it is claimed)
            if (eid == null) {
                if (waitingSpawn.contains(rid) || !waitingSpawn.isEmpty() || ghosts.size() >= 60) continue;
                if (submit("ped.spawn", p.optString("model", GHOST_MODEL), 0, epoch, new float[0]) > 0) waitingSpawn.add(rid);
                return;
            }
            // 4) make ghost solid/immortal once
            if (!flagged.contains(rid)) {
                flagged.add(rid);
                submit("entity.toggle", "invincible", eid, epoch, new float[]{1f}); return;
            }
        }
        // 5) move one ghost per cycle, round-robin
        if (pl.length() > 0) {
            rr = (rr + 1) % pl.length();
            JSONObject p = pl.getJSONObject(rr); Integer eid = ghosts.get(p.getInt("id"));
            if (eid != null) submit("entity.move", "", eid, epoch,
                new float[]{(float) p.getDouble("x"), (float) p.getDouble("y"), (float) p.getDouble("z"), 0f});
        }
    }
    private int rr = 0;

    private int submit(String action, String text, int id, int epoch, float[] v) {
        int r = TrainerBridge.NATIVE.submit(action, text, id, epoch, v);
        if (r <= 0) android.util.Log.w("MpClient", action + " failed: " + TrainerBridge.error(r));
        return r;
    }
}
