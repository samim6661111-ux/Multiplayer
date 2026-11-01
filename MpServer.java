package com.gtavsource.android;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.*;
import java.util.*;

/** In-app relay (Java port of server.js) so a phone can host without Termux. */
public final class MpServer {
    private static final class Peer {
        int id; String name; SocketAddress addr; long seen;
        boolean hasState; double x, y, z, h; int veh; String model = "";
    }
    private final int port;
    private volatile boolean running;
    private DatagramSocket sock;
    private final Map<String, Peer> peers = new HashMap<String, Peer>();
    private int nextId = 1;

    public MpServer(int port) { this.port = port; }

    public void start() throws SocketException {
        sock = new DatagramSocket(port);
        running = true;
        Thread rx = new Thread(new Runnable() { public void run() { receiveLoop(); } }, "mp-srv-rx");
        Thread tx = new Thread(new Runnable() { public void run() { broadcastLoop(); } }, "mp-srv-tx");
        rx.setDaemon(true); tx.setDaemon(true); rx.start(); tx.start();
    }

    public void stop() { running = false; if (sock != null) sock.close(); }

    private void receiveLoop() {
        byte[] buf = new byte[4096];
        while (running) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                sock.receive(p);
                JSONObject m = new JSONObject(new String(p.getData(), 0, p.getLength(), "UTF-8"));
                String key = p.getSocketAddress().toString();
                String t = m.optString("t", "");
                synchronized (peers) {
                    if (t.equals("bye")) { peers.remove(key); continue; }
                    Peer q = peers.get(key);
                    if (q == null) {
                        q = new Peer(); q.id = nextId++; q.addr = p.getSocketAddress(); peers.put(key, q);
                    }
                    q.seen = System.currentTimeMillis();
                    String n = m.optString("name", "Player");
                    q.name = n.length() > 24 ? n.substring(0, 24) : n;
                    if (t.equals("state")) {
                        q.hasState = true;
                        q.x = m.optDouble("x"); q.y = m.optDouble("y"); q.z = m.optDouble("z");
                        q.h = m.optDouble("h"); q.veh = m.optInt("veh"); q.model = m.optString("model", "");
                    }
                }
            } catch (Exception e) { if (!running) return; }
        }
    }

    private void broadcastLoop() {
        while (running) {
            try {
                long now = System.currentTimeMillis();
                synchronized (peers) {
                    for (Iterator<Peer> it = peers.values().iterator(); it.hasNext();)
                        if (now - it.next().seen > 5000) it.remove();
                    for (Peer to : peers.values()) {
                        JSONArray arr = new JSONArray();
                        for (Peer q : peers.values()) {
                            if (q == to || !q.hasState) continue;
                            JSONObject o = new JSONObject();
                            o.put("id", q.id); o.put("name", q.name);
                            o.put("x", q.x); o.put("y", q.y); o.put("z", q.z);
                            o.put("h", q.h); o.put("veh", q.veh); o.put("model", q.model);
                            arr.put(o);
                        }
                        JSONObject w = new JSONObject();
                        w.put("t", "world"); w.put("you", to.id); w.put("players", arr);
                        byte[] b = w.toString().getBytes("UTF-8");
                        sock.send(new DatagramPacket(b, b.length, to.addr));
                    }
                }
                Thread.sleep(100);
            } catch (Exception e) { if (!running) return; }
        }
    }
}
