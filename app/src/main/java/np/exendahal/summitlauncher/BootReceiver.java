package np.exendahal.summitlauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * "Open when TV starts" (PRD 6.9): marks the boot so the first Home resume opens the TV source once.
 * Starting an activity from a receiver is blocked on newer Android, so Home does the launch.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) return;
        Prefs p = Prefs.get(c);
        if (p.bool(Prefs.LIVE_ON_BOOT)) p.set(Prefs.BOOT_PENDING, "true");
    }
}
