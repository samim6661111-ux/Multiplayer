package com.gtavsource.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

/** One-tap multiplayer panel. Call MpMenu.show(activity) from any button/menu item. */
public final class MpMenu {
    private static MpClient client;

    public static void show(final Activity a) {
        final SharedPreferences sp = a.getSharedPreferences("mp", Activity.MODE_PRIVATE);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * a.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, 0);

        final EditText ip = field(a, "Server IP", sp.getString("ip", ""), InputType.TYPE_CLASS_TEXT);
        final EditText port = field(a, "Port", sp.getString("port", "7777"), InputType.TYPE_CLASS_NUMBER);
        final EditText nick = field(a, "Nickname", sp.getString("nick", "Player"), InputType.TYPE_CLASS_TEXT);
        box.addView(ip); box.addView(port); box.addView(nick);

        AlertDialog.Builder b = new AlertDialog.Builder(a)
            .setTitle(client == null ? "Multiplayer" : "Multiplayer (connected)")
            .setView(box)
            .setPositiveButton("Connect", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                String h = ip.getText().toString().trim();
                int p;
                try { p = Integer.parseInt(port.getText().toString().trim()); } catch (Exception e) { p = 7777; }
                if (h.isEmpty()) { Toast.makeText(a, "Enter server IP", Toast.LENGTH_SHORT).show(); return; }
                sp.edit().putString("ip", h).putString("port", "" + p).putString("nick", nick.getText().toString()).apply();
                if (client != null) client.stop();
                TrainerBridge.NATIVE.setEnabled(true);
                client = new MpClient(h, p, nick.getText().toString().trim());
                client.start();
                Toast.makeText(a, "Connecting... (Story Mode e control pele ghost dekhbe)", Toast.LENGTH_LONG).show();
            } })
            .setNegativeButton("Close", null);
        if (client != null) b.setNeutralButton("Disconnect", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { client.stop(); client = null; } });
        b.show();
    }

    private static EditText field(Activity a, String hint, String val, int type) {
        EditText e = new EditText(a);
        e.setHint(hint); e.setText(val); e.setInputType(type); e.setSingleLine(true);
        return e;
    }
}
