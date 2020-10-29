package mainPackage;

import communicationResources.ServerConnection;
import models.Gadget;
import models.GadgetBasic;
import models.GadgetType;
import models.Settings;
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

    //private static final String configFileJSON = "./config.json";  // When run as JAR on Linux
    private static final String gadgetFileJSON = (new File(System.getProperty("user.dir")).getParentFile().getPath()).concat("/gadgets.json"); // When run from IDE
    //Note: 'config.json' should be located "next to" the project folder: [config.json][PublicServer]

    private static ClientApp instance = null;

    public static ClientApp getInstance(){
        if (instance==null){
            instance = new ClientApp();
        }
        return instance;
    }

    private ClientApp() {
        this.gadgets = new HashMap<Integer, Gadget>();
        this.lockObject_1 = new Object();
        this.terminate = false;

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
            if (settings.isRemoteAccessEnabled()){
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

    private synchronized void outputToServer(String command) {
        Scanner scanner = new Scanner(System.in);
        while (!terminate) {
            String hosoRequest = scanner.nextLine().trim();
            ServerConnection.getInstance().writeToServer(hosoRequest);
        }
    }

    private void inputFromServer() throws Exception {
        //PS --> H
        while (!terminate) {
            String commandFromServer = ServerConnection.getInstance().incomingServerCommands.take();
            String[] commands = commandFromServer.split("::");

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
                    alterGadgetState(commands[1],commands[2]);
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
        while (!terminate) {
            //TODO, check automations aswell, can be implemetned later
            //TODO set last poll time when the gadget has been polled.

            for (int key : gadgets.keySet()) {
                long currentMillis = System.currentTimeMillis();

                //Check if gadget needs polling
                if (currentMillis - gadgets.get(key).lastPollTime > gadgets.get(key).pollDelaySec) {
                    try {
                        gadgets.get(key).poll();
                    } catch (Exception e) {
                        //If not connecting to gadget, set present to false.
                        e.printStackTrace();
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
        for (int key : gadgets.keySet()){
            if (gadgets.get(key).id == Integer.parseInt(gadgetID)){
                gadgets.get(key).alterState(Float.parseFloat(newState));
            }
        }
    }

    //371 request of gadget Groups
    private void requestOfGadgetGroups() {
        //TODO
    }

    //==============================HUB ---> PUBLIC SERVER ==================================

    //========================= FILE HANDLING ===============================================
    private void readGadgetFile() throws Exception {
        JSONParser parser = new JSONParser();
        JSONArray array = (JSONArray) parser.parse(new FileReader(gadgetFileJSON));

        for (Object object : array) {
            JSONObject gadget = (JSONObject) object;
            int id = Integer.valueOf((String) gadget.get("id"));
            String alias = (String) gadget.get("alias");
            GadgetType type = GadgetType.valueOf((String) gadget.get("type"));
            String valueTemplate = (String) gadget.get("valueTemplate");
            long pollDelaySeconds = Long.valueOf((String) gadget.get("pollDelaySec"));
            int port = Integer.valueOf((String) gadget.get("port"));
            String ip = (String) gadget.get("ip");

            GadgetBasic gadgetBasic = new GadgetBasic(id, alias, type, valueTemplate, -1, pollDelaySeconds, port, ip);
            gadgets.put(id, gadgetBasic);
        }
    }

    private void configSettings() throws Exception {
        this.settings = Settings.getInstance();
    }

    private void readAutomationFile() {
        //TODO
    }

    private void readGroupsFile() {
        //TODO
    }

    private void readSettingsFile() {
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

