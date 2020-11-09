package models;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class Settings {
    private final String settingsFileJSON = (new File(System.getProperty("user.dir")).getParentFile().getPath()).concat("/settings.json");
    private boolean remoteAccessEnabled;
    private int remoteID;
    private String remotePassword;
    private String alias;

    //Singelton
    private static Settings instance = null;

    public static Settings getInstance() throws IOException, ParseException {
        if (instance == null) {
            instance = new Settings();
        }
        return instance;
    }

    private Settings() throws IOException, ParseException {
        configSettings();
    }

    private void configSettings() throws IOException, ParseException {
        JSONParser parser = new JSONParser();

        JSONObject object = (JSONObject) parser.parse(new FileReader(settingsFileJSON));
        this.remoteAccessEnabled = Boolean.valueOf((String) object.get("remoteAccessEnable"));
        this.remoteID = Integer.valueOf((String) object.get("remoteID"));
        this.remotePassword = (String) object.get("remotePassword");
        this.alias = (String) object.get("alias");
    }

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

    public boolean isRemoteAccessEnabled() {
        return remoteAccessEnabled;
    }

    @Override
    public String toString() {
        return "\nSETTINGS: \n" +
                "Remote ID: " + remoteID + "\n" +
                "Remote Password: " + remotePassword + "\n" +
                "Alias: " + alias + "\n";
    }
}
