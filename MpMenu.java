package com.gtavsource.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/** One-tap multiplayer panel. Call MpMenu.show(activity) from any button/menu item. */
public final class MpMenu {
    private static MpClient client;
    private static MpServer server;

    public static void show(final Activity a) {
        final SharedPreferences sp = a.getSharedPreferences("mp", Activity.MODE_PRIVATE);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * a.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, 0);

        final CheckBox host = new CheckBox(a);
        host.setText("Host server on this phone (others join my IP)");
        host.setChecked(sp.getBoolean("host", false));
        final EditText ip = field(a, "Server IP (ignored when hosting)", sp.getString("ip", ""), InputType.TYPE_CLASS_TEXT);
        final EditText port = field(a, "Port", sp.getString("port", "7777"), InputType.TYPE_CLASS_NUMBER);
        final EditText nick = field(a, "Nickname", sp.getString("nick", "Player"), InputType.TYPE_CLASS_TEXT);
        box.addView(host); box.addView(ip); box.addView(port); box.addView(nick);

        String title = "Multiplayer";
        if (client != null) title += server != null ? " (hosting on " + localIp() + ")" : " (connected)";

        AlertDialog.Builder b = new AlertDialog.Builder(a)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("Connect", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                int p;
                try { p = Integer.parseInt(port.getText().toString().trim()); } catch (Exception e) { p = 7777; }
                boolean hosting = host.isChecked();
                String h = hosting ? "127.0.0.1" : ip.getText().toString().trim();
                if (h.isEmpty()) { Toast.makeText(a, "Enter server IP", Toast.LENGTH_SHORT).show(); return; }
                sp.edit().putString("ip", ip.getText().toString().trim()).putString("port", "" + p)
                    .putString("nick", nick.getText().toString()).putBoolean("host", hosting).apply();
                stopAll();
                if (hosting) {
                    try { server = new MpServer(p); server.start(); }
                    catch (Exception e) { server = null; Toast.makeText(a, "Cannot start server: " + e.getMessage(), Toast.LENGTH_LONG).show(); return; }
                }
                TrainerBridge.NATIVE.setEnabled(true);
                client = new MpClient(h, p, nick.getText().toString().trim());
                client.start();
                Toast.makeText(a, hosting ? "Hosting! Friends join: " + localIp() + ":" + p
                                          : "Connecting... (Story Mode e control pele ghost dekhbe)", Toast.LENGTH_LONG).show();
            } })
            .setNegativeButton("Close", null);
        if (client != null) b.setNeutralButton("Disconnect", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { stopAll(); } });
        b.show();
    }

    private static void stopAll() {
        if (client != null) { client.stop(); client = null; }
        if (server != null) { server.stop(); server = null; }
    }

    private static String localIp() {
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs.hasMoreElements()) {
                Enumeration<InetAddress> as = ifs.nextElement().getInetAddresses();
                while (as.hasMoreElements()) {
                    InetAddress x = as.nextElement();
                    if (x instanceof Inet4Address && !x.isLoopbackAddress() && x.isSiteLocalAddress()) return x.getHostAddress();
                }
            }
        } catch (Exception ignore) { }
        return "?";
    }

    private static EditText field(Activity a, String hint, String val, int type) {
        EditText e = new EditText(a);
        e.setHint(hint); e.setText(val); e.setInputType(type); e.setSingleLine(true);
        return e;
    }
}
