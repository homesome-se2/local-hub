package models;

import mainPackage.ClientApp;

import java.text.SimpleDateFormat;
import java.util.Date;

public class GadgetPerson extends Gadget {

    public final String nameID; // Corresponds to the user name logged in to the Android device
    public long lastUpdate; // millis (when last position update was received from public server)
    public double latitude;
    public double longitude;

    // Gadget instantiated via update from Android device
    public GadgetPerson(int gadgetID, String nameID) {
        super(gadgetID, nameID, GadgetType.BINARY_SENSOR, "person", 60);
        this.nameID = nameID;
        // Set temp values
        lastUpdate = 0;
        latitude = 0;
        longitude = 0;
    }

    // Gadget instantiated via json file at hub boot.
    public GadgetPerson(int gadgetID, String alias, long pollDelaySec, String nameID, String lastUpdate, double lastState, boolean enabled) throws Exception {
        super(gadgetID, alias, GadgetType.BINARY_SENSOR, "person", pollDelaySec, enabled);
        this.nameID = nameID;
        this.lastUpdate = simpleDateToMillis(lastUpdate);
        latitude = 0;
        longitude = 0;
        setState(lastState);
    }

    @Override
    public void poll() {
        long currentMillis = System.currentTimeMillis();
        setPresent((currentMillis - lastUpdate) < (45 * 1000 * 60));

    }

    @Override
    public void alterState(float requestedState) throws Exception {
        lastUpdate = System.currentTimeMillis();
        // is longitude & latitude within hub area ? 1 : 0;
        float hubLongitude = ClientApp.getInstance().settings.hubLongitude;
        float hubLatitude = ClientApp.getInstance().settings.hubLatitude;
        float hubRadius = ClientApp.getInstance().settings.hubRadius;

        double distanceKM = getDistanceInKm(hubLatitude, hubLongitude, latitude, longitude);
        boolean isHome = (distanceKM * 1000) <= hubRadius;
        setState(isHome ? 1 : 0);

    }

    private static double getDistanceInKm(double hubLatitude, double hubLongitude, double personLatitude, double personLongitude) {
        int R = 6371; // Radius of the earth in km
        double dLat = deg2rad(hubLatitude - personLatitude);
        double dLon = deg2rad(hubLongitude - personLongitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(deg2rad(hubLatitude)) * Math.cos(deg2rad(personLatitude)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        //Returns distance in Kilometers
        return R * c;
    }

    private static double deg2rad(double deg) {
        return deg * (Math.PI / 180);
    }

    @Override
    protected String sendCommand(String command) throws Exception {
        return null;
    }

    public String lastUpdateToSimpleDate() {
        // Millis to date
        Date resultDate = new Date(lastUpdate);
        String pattern = "yyyy-MM-dd HH:mm";
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
        return simpleDateFormat.format(resultDate);
    }

    private long simpleDateToMillis(String date){
        try {
            Date simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm").parse(date);
            return simpleDateFormat.getTime();
        }catch (Exception e){
            return 0;
        }
    }
}
