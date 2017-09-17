package eecs.arlocation;

import android.location.Location;
import android.view.View;
import android.widget.ImageView;

/**
 * Created by chennosaurus on 9/17/17.
 */

public class Target {

    public Location location;
    public ImageView plumbbob;
    public ImageView pointer;
    public ExpFilter exp;
    public String name;
    private int left;
    private int right;
    private int center;

    public Target(String tname, Location l, int theleft, int theright, int thecenter, View plumb, View point){
        name = tname;
        location = l;
        left = theleft;
        right = theright;
        center = thecenter;
        exp = new ExpFilter(0.25);
        plumbbob = (ImageView) plumb;
        pointer = (ImageView) point;

    }


    public void setLocation(double lon, double lat) {
        location.setLatitude(lat);
        location.setLongitude(lon);
    }

    public Location getLocation() {
        return location;
    }

    public void set_pointer_direction(int direction) {
        if (direction == -1){
            pointer.setImageResource(left);
        }
        else if (direction == 1){
            pointer.setImageResource(right);
        }
        else{
            pointer.setImageResource(center);
        }

    }
}
