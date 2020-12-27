package mainPackage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
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
import java.io.IOException;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class ClientApp {
    private HashMap<Integer, Gadget> gadgets;
    private ArrayList<GadgetGroup> gadgetGroup;
    private ArrayList<Automation> automationsList;
    private HashMap<Integer, List<Action>> actionMap;
    private final Object lockObject_1;
    public volatile boolean terminate;
    private Settings settings;
    private Thread pollingThread;
    private final Object lock_gadgets;
    private boolean timerRunning = false;

    //FILES
    private static final String gadgetFileJSON = (new File(System.getProperty("user.dir")).getParentFile().getPath()).concat("/gadgets.json"); // When run from IDE
    private static final String automationFileJSON = (new File(System.getProperty("user.dir")).getParentFile().getPath()).concat("/automations.json"); // When run from IDE
    private static final String gadgetGroupFile = (new File(System.getProperty("user.dir")).getParentFile().getPath()).concat("/gadgetGroup.json");
    //private static final String configFileJSON = "./config.json";  // When run as JAR on Linux
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
        this.gadgets = new HashMap<>();
        this.gadgetGroup = new ArrayList<>();
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

            //configure gadgets, automations, groups and settings
            configSettings();
            readGadgetFile();
            readGroupsFile();
            readAutomationFile();
            //TODO read groups

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

        //poll gadget
        //check if gadget present or not
        //If present set last pollTime
        //If state change notify clients
        //If not present set to notPresent and notify clients

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
        //if not enabled, return
        if (!automation.isEnabled()) {
            return;
        }
        //Selects based on trigger type
        if (automation.getTrigger().getType().equals("event")) {
            //Selects based on trigger condition
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

        class AutomationTask extends TimerTask {
            public void run() {
                List<Action> actions = actionMap.get(gadget.id);
                for (Action action : actions) {
                    try {
                        System.out.println(automation + "ending automation task at: " + new Date());
                        alterGadgetState(Integer.toString(action.getGadgetID()), Float.toString(action.getState()));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    automation.setRunning(false);
                }
            }
        }

        Date date = new Date(System.currentTimeMillis() + automation.getDelay().timeInMills());//task Execution date
        Timer timer = new Timer(); // creating timer
        TimerTask task = new AutomationTask(); // creating timer task

        if (automation.getTrigger().getState() == gadget.getState() && !automation.isRunning()){
            System.out.println(automation + " starting automation at: " + new Date());
            automation.setRunning(true);
            timer.schedule(task, date);// scheduling the task if trigger is on

        }
    }

    //==============================HUB ---> PUBLIC SERVER ==================================
    //351 new gadget detected
    private void newGadgetDetected(int gadgetID) {
        //TODO needs to be tested
        if (ServerConnection.getInstance().loggedInToServer) {
            ServerConnection.getInstance().writeToServer("351::" + gadgets.get(gadgetID).toHoSoProtocol());
        }
    }

    //353 gadget connection lost
    private void gadgetConnectionLost(int gadgetID) {
        //TODO needs to be tested
        if (ServerConnection.getInstance().loggedInToServer) {
            ServerConnection.getInstance().writeToServer("353::" + gadgetID);
        }
    }

    //402 Request to alter gadget alias
    private void alterGadgetAlias(int gadgetID, String newAlias) {
        gadgets.get(gadgetID).setAlias(newAlias);
        if (ServerConnection.getInstance().loggedInToServer) {
            ServerConnection.getInstance().writeToServer("403::" + gadgetID + "::" + newAlias);
        }
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
            if (gadgets.get(i).isPresent()) {
                msgToServer.append(gadgets.get(i).toHoSoProtocol()).append("::");
                counter++;
            }
        }
        if (ServerConnection.getInstance().loggedInToServer){
            ServerConnection.getInstance().writeToServer("303::" + cSessionID + "::" + counter + "::" + msgToServer);
        }
    }

    //312 Alter gadget state
    private void alterGadgetState(String gadgetID, String newState) throws Exception {
        synchronized (lock_gadgets) {
            if (gadgets.get(Integer.parseInt(gadgetID)).type == GadgetType.SWITCH || gadgets.get(Integer.parseInt(gadgetID)).type == GadgetType.SET_VALUE) {
                gadgets.get(Integer.parseInt(gadgetID)).alterState(Float.parseFloat(newState));
            }
            /*for (int key : gadgets.keySet()) {
                if (gadgets.get(key).id == Integer.parseInt(gadgetID) && gadgets.get(key).isPresent()) {

                    gadgets.get(key).alterState(Float.parseFloat(newState));
                }
            }*/
        }
    }

    //371 request of gadget Groups
    private void requestOfGadgetGroups(String cSessionID) {
        //TODO needs to be tested
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("372::").append(cSessionID);
        for (GadgetGroup aGadgetGroup : gadgetGroup) {
            stringBuilder.append(aGadgetGroup.toHosoArrayFormat());
        }
        if (ServerConnection.getInstance().loggedInToServer){
            ServerConnection.getInstance().writeToServer(stringBuilder.toString());
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
                    int id = Integer.parseInt((String) gadget.get("id"));
                    String alias = (String) gadget.get("alias");
                    GadgetType type = GadgetType.valueOf((String) gadget.get("type"));
                    String valueTemplate = (String) gadget.get("valueTemplate");
                    String requestSpec = (String) gadget.get("requestSpec");
                    long pollDelaySeconds = Long.parseLong((String) gadget.get("pollDelaySec"));
                    int port = Integer.parseInt((String) gadget.get("port"));
                    String ip = (String) gadget.get("ip");

                    GadgetBasic gadgetBasic = new GadgetBasic(id, alias, type, valueTemplate, requestSpec, -1, pollDelaySeconds, port, ip);
                    gadgets.put(id, gadgetBasic);
                }
            }
        } catch (Exception e) {
            throw new Exception("Problem reading gadgets.json");
        }
    }

    private void configSettings() throws Exception {
        this.settings = Settings.getInstance();
    }

    private void readAutomationFile() throws Exception {
        JSONParser parser = new JSONParser();

        JSONArray array = (JSONArray) parser.parse(new FileReader(automationFileJSON));
       // ArrayList<Action> actions = new ArrayList<>();

        for (Object object : array) {
            ArrayList<Action> actions = new ArrayList<>();
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
                        "State: " + gadgets.get(key).getState());
            }
            System.out.println("====================");
        }
    }
}
