package mainPackage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import communicationResources.ServerConnection;
import models.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

public class ClientApp {
    private HashMap<Integer, Gadget> gadgets;
    private ArrayList<GadgetGroup> gadgetGroup;
    private final Object lockObject_1;
    public volatile boolean terminate;
    private Settings settings;
    private Thread pollingThread;
    private final Object lock_gadgets;

    //FILES
    // When run from IDE
    //private static final String gadgetFileJSON = (new File(System.getProperty("user.dir")).getParentFile().getPath()).concat("/gadgets.json");
    //private static final String automationFileJSON = (new File(System.getProperty("user.dir")).getParentFile().getPath()).concat("/automations.json");
    //private static final String gadgetGroupFile = (new File(System.getProperty("user.dir")).getParentFile().getPath()).concat("/gadgetGroup.json");

    // When run as JAR on Linux
    private static final String gadgetFileJSON = "./gadgets.json";
    private static final String automationFileJSON = "./automations.json";
    private static final String gadgetGroupFile = "./gadgetGroup.json";
    //Note: 'config.json' should be located "next to" the project folder: [config.json][PublicServer]

    private static ClientApp instance = null;

    public static ClientApp getInstance() {
        if (instance == null) {
            instance = new ClientApp();
        }
        return instance;
    }

    private ClientApp() {
        this.gadgets = new HashMap<>();
        this.gadgetGroup = new ArrayList<>();
        this.lockObject_1 = new Object();
        this.terminate = false;
        this.lock_gadgets = new Object();

        this.pollingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    pollGadgets();
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            }
        });
    }

    public void startHub() {
        try {
            //Print message
            System.out.println("\nLocal Hub Running\n");

            //configure gadgets, automations, groups and settings
            configSettings();
            readGadgetFile();
            readGroupsFile();
            //TODO read automations

            //Start polling thread (handles automations aswell)
            pollingThread.start();

            //Start connection with server using websockets && process input read from server
            if (settings.isRemoteAccessEnabled()) {
                ServerConnection.getInstance().connectToServer(settings.loginString());
                inputFromServer();
            } else {
                System.out.println("Remote Access = False");
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public void closeApp() {
        synchronized (lockObject_1) {
            if (!terminate) {
                terminate = true;
                ServerConnection.getInstance().closeConnection();
            }
        }
    }

    private void inputFromServer() throws Exception {
        //PS --> H
        while (!terminate) {
            String commandFromServer = ServerConnection.getInstance().incomingServerCommands.take();
            String[] commands = commandFromServer.trim().split("::");
            try {
                switch (commands[0]) {
                    case "121":
                        //H login successful
                        loginSuccessful();
                        break;
                    case "302":
                        //Request of gadgets (newly logged in client)
                        newClientRequestsGadgets(commands[1]);
                        break;
                    case "312":
                        //Request to alter gadget state
                        alterGadgetState(commands[1], commands[2]);
                        break;
                    case "371":
                        //request to get gadget groups
                        requestOfGadgetGroups(commands[1]);
                        break;
                    case "402":
                        //request to alter gadget alias
                        alterGadgetAlias(Integer.parseInt(commands[2]), commands[3]);
                        break;
                    case "901":
                        System.out.println("ExceptionMessage: " + commands[1]);
                        break;
                    default:
                        System.out.println("\n\nUnknown message from server \n\n");
                        break;
                }
            } catch (Exception e) {
                throw new Exception(e.getMessage());
            }
        }
    }

    //==============================POLLING AND AUTOMATION HANDLING =========================
    private void pollGadgets() {
        int nbrOfGadgets;
        int[] gadgetKeys;
        synchronized (lock_gadgets) {
            gadgetKeys = new int[gadgets.size()];
            nbrOfGadgets = gadgetKeys.length;
            int counter = 0;
            for (int i : gadgets.keySet()) {
                gadgetKeys[counter] = i;
                counter++;
            }
        }

        while (!terminate) {
            for (int i = 0; i < nbrOfGadgets; i++) {
                synchronized (lock_gadgets) {
                    //TODO, check automations aswell, can be implemented later
                    Gadget gadget = gadgets.get(gadgetKeys[i]);
                    long currentMillis = System.currentTimeMillis();
                    boolean presentNow = gadget.isPresent();

                    //Check if gadget needs polling
                    if ((currentMillis - gadget.lastPollTime) > (gadget.pollDelaySec * 1000)) {
                        try {
                            gadget.poll();
                            if (gadget.isPresent()) {
                                gadget.setLastPollTime(System.currentTimeMillis());
                            }
                            //will compare the gadget.isPresent before and after the polling to see availabilityChange.
                            if (presentNow != gadget.isPresent()) {
                                //The gadget has either became available or it have turned unavailable
                                if (gadget.isPresent()) {
                                    //The gadget has become available and is present
                                    newGadgetDetected(gadget.id);
                                } else {
                                    //The gadget has become unavailable and is not present
                                    gadgetConnectionLost(gadget.id);
                                }
                            }
                        } catch (Exception e) {
                            //System.out.println(e.getMessage());
                        }
                    }
                }
            }
        }
    }

    //==============================HUB ---> PUBLIC SERVER ==================================
    //351 new gadget detected
    private void newGadgetDetected(int gadgetID) throws Exception {
        try {
            if (ServerConnection.getInstance().loggedInToServer) {
                ServerConnection.getInstance().writeToServer("351::" + gadgets.get(gadgetID).toHoSoProtocol());
            }
        } catch (Exception e) {
            throw new Exception("Problem when writing to public server. Command = 351.");
        }
    }

    //353 gadget connection lost
    private void gadgetConnectionLost(int gadgetID) throws Exception {
        try {
            if (ServerConnection.getInstance().loggedInToServer) {
                ServerConnection.getInstance().writeToServer("353::" + gadgetID);
            }
        } catch (Exception e) {
            throw new Exception("Problem when writing to server. Command = 353.");
        }

    }

    //402 Request to alter gadget alias
    private void alterGadgetAlias(int gadgetID, String newAlias) throws Exception {
        try {
            gadgets.get(gadgetID).setAlias(newAlias);
            alterAliasInJson(gadgetID, newAlias);
            if (ServerConnection.getInstance().loggedInToServer) {
                ServerConnection.getInstance().writeToServer("403::" + gadgetID + "::" + newAlias);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Problem when writing to Server. Command = 403.");
        }
    }

    //==============================PUBLIC SERVER ---> HUB ==================================
    //121 SuccessfulLogin
    private void loginSuccessful() {
        System.out.println("Login Successful");
        ServerConnection.getInstance().loggedInToServer = true;
    }

    //302 Request of gadgets (newly logged in client)
    private void newClientRequestsGadgets(String cSessionID) throws Exception {
        StringBuilder msgToServer = new StringBuilder();
        int counter = 0;
        try {
            for (int key : gadgets.keySet()) {
                if (gadgets.get(key).isPresent()) {
                    msgToServer.append(gadgets.get(key).toHoSoProtocol()).append("::");
                    counter++;
                }
            }
            if (ServerConnection.getInstance().loggedInToServer) {
                ServerConnection.getInstance().writeToServer("303::" + cSessionID + "::" + counter + "::" + msgToServer);
            }
        } catch (Exception e) {
            throw new Exception("Problem when writing to server. Command = 303.");
        }
    }

    //312 Alter gadget state
    private void alterGadgetState(String gadgetID, String newState) throws Exception {
        synchronized (lock_gadgets) {
            float newFloatState;
            try {
                newFloatState = Float.parseFloat(newState);
            } catch (Exception e) {
                System.out.println("Invalid State Request");
                return;
            }
            try {
                if (gadgets.get(Integer.parseInt(gadgetID)).type == GadgetType.SWITCH || gadgets.get(Integer.parseInt(gadgetID)).type == GadgetType.SET_VALUE) {
                    gadgets.get(Integer.parseInt(gadgetID)).alterState(newFloatState);
                }
            } catch (Exception e) {
                ServerConnection.getInstance().writeToServer("353::" + gadgetID);
                System.out.println("Problem when altering state of gadget: " + gadgetID);
            }
        }
    }

    //371 request of gadget Groups
    private void requestOfGadgetGroups(String cSessionID) throws Exception {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("372::").append(cSessionID);
        try {
            for (GadgetGroup aGadgetGroup : gadgetGroup) {
                stringBuilder.append(aGadgetGroup.toHosoArrayFormat());
            }
            if (ServerConnection.getInstance().loggedInToServer) {
                ServerConnection.getInstance().writeToServer(stringBuilder.toString());
            }
        } catch (Exception e) {
            throw new Exception("Problem when writing to server. Command = 372.");
        }
    }

    //========================= FILE HANDLING ===============================================
    private void readGadgetFile() throws Exception {
        try {
            JSONParser parser = new JSONParser();
            JSONArray array = (JSONArray) parser.parse(new FileReader(gadgetFileJSON));
            for (Object object : array) {
                JSONObject gadget = (JSONObject) object;
                if (gadget.get("enable").equals("true")) {
                    int id = Integer.parseInt(String.valueOf(gadget.get("id")));
                    String alias = (String) gadget.get("alias");
                    GadgetType type = GadgetType.valueOf((String) gadget.get("type"));
                    String valueTemplate = (String) gadget.get("valueTemplate");
                    String requestSpec = (String) gadget.get("requestSpec");
                    long pollDelaySeconds = Long.parseLong(String.valueOf(gadget.get("pollDelaySec")));
                    int port = Integer.parseInt(String.valueOf(gadget.get("port")));
                    String ip = (String) gadget.get("ip");

                    GadgetBasic gadgetBasic = new GadgetBasic(id, alias, type, valueTemplate, requestSpec, -1, pollDelaySeconds, port, ip);
                    gadgets.put(id, gadgetBasic);
                }
            }
            printGadgets();
        } catch (Exception e) {
            throw new Exception("Problem reading gadgets.json");
        }
    }

    private void alterAliasInJson(int gadgetID, String newAlias) throws Exception {
        String fileName = "";
        if (gadgets.get(gadgetID) instanceof GadgetBasic) {
            fileName = gadgetFileJSON;
        }
        // else instance of gadgetPerson.

        try {
            JSONParser parser = new JSONParser();
            JSONArray array = (JSONArray) parser.parse(new FileReader(fileName));
            for (Object object : array) {
                JSONObject gadget = (JSONObject) object;
                if ((Long) gadget.get("id") == gadgetID) {
                    gadget.put("alias", newAlias);
                    break;
                }
            }
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String prettyJson = gson.toJson(array);
            try (FileWriter writer = new FileWriter(fileName)) {
                writer.write(prettyJson);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Problem updating alias in gadgets.json");
        }
    }

    private void configSettings() throws Exception {
        this.settings = Settings.getInstance();
    }

    private void readAutomationFile() throws Exception {
        //TODO
        JSONParser parser = new JSONParser();
        JSONArray array = (JSONArray) parser.parse(new FileReader(automationFileJSON));

        for (Object object : array) {
            JSONObject automations = (JSONObject) object;
            int masterId = Integer.parseInt((String) automations.get("masterId"));
            int slaveId = Integer.parseInt((String) automations.get("slaveId"));
            float masterState = Float.parseFloat((String) automations.get("masterState"));
            float slaveState = Float.parseFloat((String) automations.get("slaveState"));

            Automation automation = new Automation(masterId, slaveId, masterState, slaveState);
        }
    }

    private void readGroupsFile() throws Exception {
        try (FileReader fileReader = new FileReader(gadgetGroupFile)) {
            Gson gson = new Gson();
            Type type = new TypeToken<ArrayList<GadgetGroup>>() {
            }.getType();
            this.gadgetGroup = gson.fromJson(fileReader, type);
        } catch (IOException e) {
            throw new Exception("Unable to read the GadgetGroup json file");
        }
    }

    private void printGroups() {
        if (!gadgetGroup.isEmpty()) {
            System.out.println("=== ALL GROUPS ===");
            for (GadgetGroup aGadgetGroup : gadgetGroup) {
                System.out.println("GroupName: " + aGadgetGroup.getGroupName());
                System.out.println("Gadgets: " + Arrays.toString(aGadgetGroup.getGadgets()));
            }
            System.out.println("====================");
        }
    }

    private void printGadgets() {
        if (!gadgets.isEmpty()) {
            System.out.println("=== ALL GADGETS ===");
            for (int key : gadgets.keySet()) {
                System.out.println("Alias: " + gadgets.get(key).alias + "\n" +
                        "State: " + gadgets.get(key).getState() + "\n" + "Present: " + gadgets.get(key).isPresent());
            }
            System.out.println("====================");
        }
    }
}
