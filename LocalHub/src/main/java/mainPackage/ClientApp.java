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
    private volatile boolean terminate;
    private Settings settings;
    private Thread pollingThread;

    //private static final String configFileJSON = "./config.json";  // When run as JAR on Linux
    private static final String gadgetFileJSON = (new File(System.getProperty("user.dir")).getParentFile().getPath()).concat("/gadgets.json"); // When run from IDE
    //Note: 'config.json' should be located "next to" the project folder: [config.json][PublicServer]

    public ClientApp() {
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

            //Start polling thread (handles automations aswell)
            //TODO

            //Start connection with server using websockets (if remote access)
            ServerConnection.getInstance().connectToServer();

            //Log in to ps (if remote access)
            loginToPublicServer();

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

    private void outputToServer(String command) {
        Scanner scanner = new Scanner(System.in);
        //Log in directly (within 2 sec)


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
        //TODO
    }

    //==============================PUBLIC SERVER ---> HUB ==================================
    //121 SuccessfulLogin
    private void loginSuccessful() {
        System.out.println("Login Successful");
    }

    //302 Request of gadgets (newly logged in client)
    private void newClientRequestsGadgets() {

    }


    private void receiveAllGadgets(String[] commands) throws Exception {
        int nbrOfGadgets = Integer.parseInt(commands[1]);
        int count = 2;
        for (int i = 0; i < nbrOfGadgets; i++) {
            int gadgetID = Integer.parseInt(commands[count++]);
            String alias = commands[count++];
            GadgetType type = GadgetType.valueOf(commands[count++]);
            String valueTemplate = commands[count++];
            float state = Float.parseFloat(commands[count++]);
            long pollDelaySeconds = Long.parseLong(commands[count++]);
        }
    }

    //312 Alter gadget state
    private void alterGadgetState() {
    }

    //371 request of gadget Groups
    private void requestOfGadgetGroups() {
    }


    //==============================HUB ---> PUBLIC SERVER ==================================
    //120 Login
    private void loginToPublicServer() {
        ServerConnection.getInstance().writeToServer(settings.loginString());
    }

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
            int state = Integer.valueOf((String) gadget.get("state"));
            long pollDelaySeconds = Long.valueOf((String) gadget.get("pollDelaySec"));
            int port = Integer.valueOf((String) gadget.get("port"));
            String ip = (String) gadget.get("ip");

            GadgetBasic gadgetBasic = new GadgetBasic(id, alias, type, valueTemplate, state, pollDelaySeconds, port, ip);
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

