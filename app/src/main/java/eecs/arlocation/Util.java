package eecs.arlocation;

import android.app.Activity;
import android.view.Gravity;
import android.widget.Toast;

public class Util {
    public static void makeToast(Activity activity, String string) {
        Toast t = Toast.makeText(activity, string, Toast.LENGTH_LONG);
        t.setGravity(Gravity.CENTER, 0, 0);
        t.show();
    }
}
