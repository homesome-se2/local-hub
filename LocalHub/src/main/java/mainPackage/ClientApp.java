package mainPackage;

import communicationResources.ServerConnection;
import models.*;
import models.automations.Action;
import models.automations.Delay;
import models.automations.Trigger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.File;
import java.io.FileReader;
import java.util.*;

public class ClientApp {

    private HashMap<Integer, Gadget> gadgets;
    private ArrayList<Automation> automationsList;
    private HashMap<Integer, List<Action>> actionMap;
    private final Object lockObject_1;
    public volatile boolean terminate;
    private Settings settings;
    private Thread pollingThread;
    private final Object lock_gadgets;
    private boolean timerRunning = false;

    //private static final String configFileJSON = "./config.json";  // When run as JAR on Linux
    private static final String gadgetFileJSON = (new File(System.getProperty("user.dir")).getParentFile().getPath()).concat("/gadgets.json"); // When run from IDE
    private static final String automationFileJSON = (new File(System.getProperty("user.dir")).getParentFile().getPath()).concat("/automations.json"); // When run from IDE
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
        this.automationsList = new ArrayList<Automation>();
        this.actionMap = new HashMap<Integer, List<Action>>();
        this.lockObject_1 = new Object();
        this.terminate = false;
        this.lock_gadgets = new Object();

        this.pollingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    pollGadgets();
                } catch (Exception e) {
                    e.printStackTrace();
                }
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
            readAutomationFile();
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
                    newClientRequestsGadgets(commands[1]);
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
    private void pollGadgets() throws Exception {
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

    //call and pass gadget to check and do automation
    private void automationsHandler(Gadget gadget) throws Exception {
        ArrayList<Integer> gadgetsWithAutomations = new ArrayList<>();
        //create list of all gadgets which has an automation
        for (Automation automation : automationsList) {
            gadgetsWithAutomations.add(automation.getTrigger().getGadgetID());
        }
        //returns if the current gadget does not have an automation
        if (!gadgetsWithAutomations.contains(gadget.id)) {
            return;
        }
        //Assign the current gadgets automation to automation

        Automation automation = automationsList.get(gadgetsWithAutomations.indexOf(gadget.id));
        //if not enable, return
        if (!automation.isEnabled()) {
            return;
        }
        if (automation.getTrigger().getType().equals("event")) {
            switch (automation.getTrigger().getStateCondition()) {
                case "equal_to":
                    if (automation.getTrigger().getState() == gadget.getState()) {
                        doAction(automation, gadget);
                    }
                    break;
                case "lower_than":
                    if (automation.getTrigger().getState() > gadget.getState()) {
                        doAction(automation, gadget);
                    }
                    break;
                case "high_than":
                    if (automation.getTrigger().getState() < gadget.getState()) {
                        doAction(automation, gadget);
                    }
                    break;
                default:
                    System.out.println("wrong state condition: " + automation.getTrigger().getStateCondition());
            }
        } else if (automation.getTrigger().getType().equals("timestamp")) {
            //Do stuff with timestamp
        }
    }

    //Method to do actions from automations
    public void doAction(Automation automation, Gadget gadget) throws Exception {
        //Iterates through all the actions for the automation
        if (timerRunning) {
            return;
        }
        System.out.println("doing automation: " + automation.getName());
        Timer timer = new Timer();
        System.out.println("Delaying automation for (HH:MM:SS): " + automation.getDelay().getHours()
                + "::" + automation.getDelay().getMinutes() + "::" + automation.getDelay().getSeconds());
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                List<Action> actions = actionMap.get(gadget.id);
                for (Action action : actions) {
                    try {
                        alterGadgetState(Integer.toString(action.getGadgetID()), Float.toString(action.getState()));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    System.out.println("Automatically changed " + action.getGadgetID() + " To State: " + action.getState());

                }
                timerRunning = false;
            }
        }, automation.getDelay().timeInMills());
        timerRunning = true;

    }

    //==============================PUBLIC SERVER ---> HUB ==================================
    //121 SuccessfulLogin
    private void loginSuccessful() {
        System.out.println("Login Successful");
        ServerConnection.getInstance().loggedInToServer = true;
    }

    //302 Request of gadgets (newly logged in client)
    private void newClientRequestsGadgets(String cSessionID) {
        StringBuilder msgToServer = new StringBuilder();
        int counter = 0;
        for (int i = 0; i < gadgets.size(); i++) {
            if (gadgets.get(i).isPresent) {
                msgToServer.append(gadgets.get(i).toHoSoProtocol()).append("::");
                counter++;
            }
        }
        ServerConnection.getInstance().writeToServer("303::" + cSessionID + "::" + counter + "::" + msgToServer);
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
        JSONParser parser = new JSONParser();

        JSONArray array = (JSONArray) parser.parse(new FileReader(automationFileJSON));
        ArrayList<Action> actions = new ArrayList<>();

        for (Object object : array) {
            JSONObject automations = (JSONObject) object;
            String name = (String) automations.get("name");
            boolean enabled = (Boolean) automations.get("enabled");

            Map trigger = ((Map) automations.get("trigger"));
            Iterator<Map.Entry> itr1 = trigger.entrySet().iterator();
            Map.Entry pair = itr1.next();
            String stateCondition = (String) pair.getValue();
            pair = itr1.next();
            float state = Float.parseFloat((String) pair.getValue());
            pair = itr1.next();
            String type = (String) pair.getValue();
            pair = itr1.next();
            int id = Integer.parseInt((String) pair.getValue());

            Map delay = ((Map) automations.get("delay"));
            Iterator<Map.Entry> itr2 = delay.entrySet().iterator();
            Map.Entry pair1 = itr2.next();
            int hours = Integer.parseInt((String) pair1.getValue());
            pair1 = itr2.next();
            int seconds = Integer.parseInt((String) pair1.getValue());
            pair1 = itr2.next();
            int minutes = Integer.parseInt((String) pair1.getValue());


            JSONArray jAction = (JSONArray) automations.get("action");
            Iterator itr3 = jAction.iterator();

            while (itr3.hasNext()) {
                itr2 = ((Map) itr3.next()).entrySet().iterator();
                while (itr2.hasNext()) {
                    Map.Entry pair2 = itr2.next();
                    float state2 = Float.parseFloat((String) pair2.getValue());
                    pair2 = itr2.next();
                    int gadgetID = Integer.parseInt((String) pair2.getValue());
                    actions.add(new Action(gadgetID, state2));
                }
            }
            Automation automation = new Automation(name, enabled, new Trigger(type, id, stateCondition, state), new Delay(hours, minutes, seconds));
            automationsList.add(automation);
            actionMap.put(id, actions);
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