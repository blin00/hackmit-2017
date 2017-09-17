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
    public static float minf(float[] arr) {
        float min = arr[0];
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] < min) {
                min = arr[i];
            }
        }
        return min;
    }
}
