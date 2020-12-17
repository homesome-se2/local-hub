package models;

public class Settings {

    private boolean remoteAccessEnable;
    private int remoteID;
    private String remotePassword;
    private String alias;
    public float hubLongitude;
    public float hubLatitude;
    public float hubRadius;

    public Settings(){}

    public String loginString() {
        return "120::" + String.valueOf(remoteID) + "::" + remotePassword + "::" + alias;
    }

    public int getRemoteID() {
        return remoteID;
    }

    public String getRemotePassword() {
        return remotePassword;
    }

    public String getAlias() {
        return alias;
    }

    public boolean isRemoteAccessEnable() {
        return remoteAccessEnable;
    }

    @Override
    public String toString() {
        return "\nSETTINGS: \n" +
                "Remote ID: " + remoteID + "\n" +
                "Remote Password: " + remotePassword + "\n" +
                "Alias: " + alias + "\n";
    }
}
