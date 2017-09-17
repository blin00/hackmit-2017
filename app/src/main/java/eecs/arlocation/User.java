package eecs.arlocation;

import android.location.Location;

import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class User {
    public String name;
    public double latitude;
    public double longitude;

    public User() {

    }

    public User(String name) {
        this.name = name;
    }

    public void updateLocation(Location location) {
        latitude = location.getLatitude();
        longitude = location.getLongitude();
    }
}
