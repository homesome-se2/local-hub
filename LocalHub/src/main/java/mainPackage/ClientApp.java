package mainPackage;

import communicationResources.ServerConnection;
import models.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Scanner;

public class ClientApp {

    private HashMap<Integer, Gadget> gadgets;
    private final Object lockObject_1;
    public volatile boolean terminate;
    private Settings settings;
    private Thread pollingThread;
    private final Object lock_gadgets;

    //private static final String configFileJSON = "./config.json";  // When run as JAR on Linux
    private static final String gadgetFileJSON = (new File(System.getProperty("user.dir")).getParentFile().getPath()).concat("/gadgets.json"); // When run from IDE
    //Note: 'config.json' should be located "next to" the project folder: [config.json][PublicServer]

    private static ClientApp instance = null;

    public static ClientApp getInstance() {
        if (instance == null) {
            instance = new ClientApp();
        }
        return instance;
    }

    private ClientApp() {
        this.gadgets = new HashMap<Integer, Gadget>();
        this.lockObject_1 = new Object();
        this.terminate = false;
        this.lock_gadgets = new Object();

        this.pollingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                pollGadgets();
            }
        });
    }

    public void startHub() {
        try {
            //Print message
            System.out.println("\nWelcome to LocalHub\n");

            //configure settings
            configSettings();

            //configure gadgets, automations, and groups
            readGadgetFile();
            //TODO read automations
            //TODO read groups

            //Start polling thread (handles automations aswell)
            pollingThread.start();

            //Start connection with server using websockets (if remote access) Login happens here aswell
            if (settings.isRemoteAccessEnabled()) {
                ServerConnection.getInstance().connectToServer(settings.loginString());
            }

            //Proccess inputs read from server
            inputFromServer();
        } catch (Exception e) {
            e.printStackTrace();
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
            Scanner scanner = new Scanner(System.in);
            String commandFromServer = ServerConnection.getInstance().incomingServerCommands.take();
            String[] commands = commandFromServer.trim().split("::");

            switch (commands[0]) {
                case "121":
                    //H login successful
                    loginSuccessful();
                    break;
                case "302":
                    //Request of gadgets (newly logged in client)
                    break;
                case "312":
                    //Request to alter gadget state
                    alterGadgetState(commands[1], commands[2]);
                    break;
                case "371":
                    //request to get gadget groups
                    break;
                case "901":
                    System.out.println("ExceptionMessage: " + commands[1]);
                    break;
                default:
                    System.out.println("\n\nUnknown message from server \n\n");
                    break;
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

                    //Check if gadget needs polling
                    if ((currentMillis - gadget.lastPollTime) > (gadget.pollDelaySec * 1000)) {
                        try {
                            gadget.poll();
                            if (gadget.isPresent) {
                                gadget.setLastPollTime(System.currentTimeMillis());
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    //==============================PUBLIC SERVER ---> HUB ==================================
    //121 SuccessfulLogin
    private void loginSuccessful() {
        System.out.println("Login Successful");
        ServerConnection.getInstance().loggedInToServer = true;
    }

    //302 Request of gadgets (newly logged in client)
    private void newClientRequestsGadgets() {
        //TODO
    }

    //312 Alter gadget state
    private void alterGadgetState(String gadgetID, String newState) throws Exception {
        //TODO Synchronise gadgetList??
        for (int key : gadgets.keySet()) {
            if (gadgets.get(key).id == Integer.parseInt(gadgetID) && gadgets.get(key).isPresent) {
                gadgets.get(key).alterState(Float.parseFloat(newState));
            }
        }
    }

    //371 request of gadget Groups
    private void requestOfGadgetGroups() {
        //TODO
    }

    //========================= FILE HANDLING ===============================================
    private void readGadgetFile() throws Exception {
        JSONParser parser = new JSONParser();
        JSONArray array = (JSONArray) parser.parse(new FileReader(gadgetFileJSON));

        for (Object object : array) {
            JSONObject gadget = (JSONObject) object;
            if (gadget.get("enable").equals("true")) {
                int id = Integer.valueOf((String) gadget.get("id"));
                String alias = (String) gadget.get("alias");
                GadgetType type = GadgetType.valueOf((String) gadget.get("type"));
                String valueTemplate = (String) gadget.get("valueTemplate");
                String requestSpec = (String) gadget.get("requestSpec");
                long pollDelaySeconds = Long.valueOf((String) gadget.get("pollDelaySec"));
                int port = Integer.valueOf((String) gadget.get("port"));
                String ip = (String) gadget.get("ip");

                GadgetBasic gadgetBasic = new GadgetBasic(id, alias, type, valueTemplate, requestSpec, -1, pollDelaySeconds, port, ip);
                gadgets.put(id, gadgetBasic);
            }
        }
    }

    private void configSettings() throws Exception {
        this.settings = Settings.getInstance();
    }

    private void readAutomationFile() throws Exception {
        //TODO
        JSONParser parser = new JSONParser();

        JSONArray array = (JSONArray) parser.parse(new FileReader(gadgetFileJSON));


        for (Object object : array) {
            JSONObject automations = (JSONObject) object;
            int masterId = Integer.valueOf((String) automations.get("masterId"));
            int slaveId = Integer.valueOf((String) automations.get("slaveId"));
            float masterState = Float.valueOf((String) automations.get("masterState"));
            float slaveState = Float.valueOf((String) automations.get("slaveState"));

            Automation automation = new Automation(masterId, slaveId, masterState, slaveState);
        }
    }

    private void readGroupsFile() {
        //TODO
    }

    private void printGadgets() {
        if (!gadgets.isEmpty()) {
            System.out.println("=== ALL GADGETS ===\n");
            for (int key : gadgets.keySet()) {
                System.out.println("Alias: " + gadgets.get(key).alias + "\n" +
                        "State: " + gadgets.get(key).getState());
            }
            System.out.println("====================");
        }
    }
}