package mainPackage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
//import com.sun.security.ntlm.Server;
import communicationResources.ServerConnection;
import models.*;
import models.automations.Action;
import models.automations.Delay;
import models.automations.Trigger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.lang.reflect.Type;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ClientApp {
    private HashMap<Integer, Gadget> gadgets;
    private ArrayList<GadgetGroup> gadgetGroup;
    private ArrayList<Automation> automationsList;
    private HashMap<Integer, List<Action>> actionMap;
    private final Object lockObject_1;
    public volatile boolean terminate;
    public Settings settings;
    private Thread pollingThread;
    private final Object lock_gadgets;
    private GadgetAdder gadgetAdder;


    //FILES
    // When run from IDE
    //private static final String gadgets_basic_fileJSON = (new File(System.getProperty("user.dir")).getParentFile().getPath()).concat("/gadgets_basic.json");
    //private static final String gadgets_person_fileJSON = (new File(System.getProperty("user.dir")).getParentFile().getPath()).concat("/gadgets_person.json");
    //private static final String gadgets_plugin_fileJSON = (new File(System.getProperty("user.dir")).getParentFile().getPath()).concat("/gadgets_plugin.json");
    //private static final String automationFileJSON = (new File(System.getProperty("user.dir")).getParentFile().getPath()).concat("/automations.json");
    //private static final String gadgetGroupFile = (new File(System.getProperty("user.dir")).getParentFile().getPath()).concat("/gadgetGroup.json");
    //private final String settingsFileJSON = (new File(System.getProperty("user.dir")).getParentFile().getPath()).concat("/settings.json");


    // When run as JAR on Linux
    private static final String gadgets_basic_fileJSON = "./gadgets_basic.json";
    private static final String gadgets_person_fileJSON = "./gadgets_person.json";
    private static final String gadgets_plugin_fileJSON = "./gadgets_plugin.json";
    private static final String automationFileJSON = "./automations.json";
    private static final String gadgetGroupFile = "./gadgetGroup.json";
    private final String settingsFileJSON = "./settings.json";
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
        this.gadgetAdder = null;

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
            readInSettings();
            readGadgetBasicFile();
            readGadgetPersonFile();
            readGadgetPluginFile();
            readGroupsFile();
            readAutomationFile();
            //TODO read groups

            printGadgets();
            //printGroups();

            //Start polling thread (handles automations aswell)
            pollingThread.start();

            if (settings.enableAddGadgets) {
                gadgetAdder = new GadgetAdder(settings.tcpPortAddGadgets);
                gadgetAdder.launch();
            }

            //Start connection with server using websockets && process input read from server
            if (settings.isRemoteAccessEnable()) {
                ServerConnection.getInstance().connectToServer(settings.loginString());
                while (ServerConnection.getInstance().serverStatus != ServerStatus.CONNECTED) {
                    if (ServerConnection.getInstance().serverStatus == ServerStatus.CONNECTION_ERROR) {
                        ServerConnection.getInstance().connectToServer(settings.loginString());
                    } else {
                        Thread.sleep(2000);
                        System.out.println("Unable To Connect To Server");
                    }
                }
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
                updatePersonFile();
                ServerConnection.getInstance().closeConnection();
            }
        }
    }

    private void inputFromServer() throws Exception {
        //PS --> H
        while (!terminate) {
            try {
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
                    case "503":
                        geoLocUpdate(commands);
                        break;
                    case "620":
                        addGadgets(commands);
                        break;
                    case "901":
                        System.out.println("ExceptionMessage: " + commands[1]);
                        break;
                    default:
                        System.out.println("\n\nUnknown message from server \n\n");
                        break;
                }
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
    }

    //==============================POLLING AND AUTOMATION HANDLING =========================
    private void pollGadgets() {
        while (!terminate) {
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

            boolean printChanges = false;

            for (int i = 0; i < nbrOfGadgets; i++) {
                synchronized (lock_gadgets) {
                    //TODO, check automations aswell, can be implemented later
                    Gadget gadget = gadgets.get(gadgetKeys[i]);
                    if (gadget.isEnabled()) {
                        long currentMillis = System.currentTimeMillis();
                        boolean presentBefore = gadget.isPresent();
                        double stateBefore = gadget.getState();

                        //Check if gadget needs polling
                        if ((currentMillis - gadget.lastPollTime) > (gadget.pollDelaySec * 1000)) {
                            try {
                                gadget.poll();
                                if (gadget.isPresent()) {
                                    gadget.setLastPollTime(System.currentTimeMillis());
                                }

                                //will compare the gadget.isPresent before and after the polling to see availabilityChange.
                                if (presentBefore != gadget.isPresent()) {
                                    //The gadget has either became available or it have turned unavailable
                                    if (gadget.isPresent()) {
                                        //The gadget has become available and is present
                                        debugLog("Gadget found", gadget.alias);
                                        newGadgetDetected(gadget.id);
                                    } else {
                                        //The gadget has become unavailable and is not present
                                        gadgetConnectionLost(gadget.id);
                                        debugLog("Gadget lost", gadget.alias);
                                    }
                                    printChanges = true;
                                } else if (gadget.isPresent()) {
                                    if (gadget.getState() != stateBefore) {
                                        newStateOfGadgetDetected(gadget.id, gadget.getState());
                                    }
                                }
                                if (gadget.isPresent()){
                                    automationsHandler(gadget);
                                }
                            } catch (Exception e) {
                                //System.out.println(e.getMessage());
                            }
                        }
                    }
                }
            }
            if(printChanges) {
                printGadgets();
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
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
                        doEvent(automation, gadget);
                    }
                    break;
                case "lower_than":
                    if (automation.getTrigger().getState() > gadget.getState()) {
                        doEvent(automation, gadget);
                    }
                    break;
                case "high_than":
                    if (automation.getTrigger().getState() < gadget.getState()) {
                        doEvent(automation, gadget);
                    }
                    break;
                default:
                    System.out.println("this is a wrong state condition: " + automation.getTrigger().getStateCondition());
            }
        }
    }

    //Method to do Events from automations
    public void doEvent(Automation automation, Gadget gadget) throws Exception {
        List<Action> actions = actionMap.get(gadget.id);
        class AutomationTask extends TimerTask {
            public void run() {
                for (Action action : actions) {
                    try {
                        alterGadgetState(Integer.toString(action.getGadgetID()), Float.toString(action.getState()));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                automation.setRunning(false);
            }
        }

        Date date = new Date(System.currentTimeMillis() + automation.getDelay().timeInMills());//task Execution date
        Timer timer = new Timer(); // creating timer
        TimerTask task = new AutomationTask(); // creating timer task

        if (!automation.isRunning()){
            automation.setRunning(true);
            timer.schedule(task, date);// scheduling the task if trigger is on

        }
    }


    //==============================HUB ---> PUBLIC SERVER ==================================
    //315 new state of gadget
    private void newStateOfGadgetDetected(int id, double newState) {
        if (ServerConnection.getInstance().loggedInToServer) {
            ServerConnection.getInstance().writeToServer("315::" + id + "::" + newState);
        }
    }

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
        synchronized (lock_gadgets) {
            try {
                for (int key : gadgets.keySet()) {
                    if (gadgets.get(key).isPresent() && gadgets.get(key).isEnabled()) {
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
    }

    //312 Alter gadget state
    private void alterGadgetState(String gadgetID, String newState) throws Exception {
        synchronized (lock_gadgets) {
            int ID = Integer.parseInt(gadgetID);
            if (gadgets.get(ID).isEnabled()) {
                float newFloatState;
                try {
                    newFloatState = Float.parseFloat(newState);
                } catch (Exception e) {
                    System.out.println("Invalid State Request");
                    return;
                }
                try {
                    if (gadgets.get(ID).type == GadgetType.SWITCH || gadgets.get(ID).type == GadgetType.SET_VALUE) {
                        gadgets.get(ID).alterState(newFloatState);
                        ServerConnection.getInstance().writeToServer("315::" + ID + "::" + gadgets.get(ID).getState());
                    }
                } catch (Exception e) {
                    ServerConnection.getInstance().writeToServer("353::" + gadgetID); //TODO?? Remove and just send back same state as before...
                    System.out.println("Problem when altering state of gadget: " + gadgetID);
                }
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

    // #503
    private void geoLocUpdate(String[] request) throws Exception {
        //TODO when writing to file make sure the lastUdate is in simpleDateFormat.
        String nameID = request[1];
        double longitude = Float.parseFloat(request[2]);
        double latitude = Float.parseFloat(request[3]);

        synchronized (lock_gadgets) {
            for (int key : gadgets.keySet()) {
                if (gadgets.get(key) instanceof GadgetPerson) {
                    GadgetPerson gadget = (GadgetPerson) gadgets.get(key);
                    if (gadget.nameID.equalsIgnoreCase(nameID)) {
                        if (gadget.isEnabled()) {
                            gadget.longitude = longitude;
                            gadget.latitude = latitude;
                            double beforeState = gadget.getState(); // 1/0 = Home/Away
                            // Verify if person (gadget) is home/away:
                            gadget.alterState(-1); // Pass nonsense
                            // If the state has changed:
                            if (beforeState != gadget.getState()) {
                                ServerConnection.getInstance().writeToServer(String.format("315::%s::%s", gadget.id, gadget.getState()));
                            }
                        }
                        return;
                    }
                }
            }
            // If gadget is not in list: Add it
            int gadgetID = generateGadgetID();
            GadgetPerson newGadget = new GadgetPerson(gadgetID, nameID);
            newGadget.longitude = longitude;
            newGadget.latitude = latitude;
            newGadget.setPresent(true);
            newGadget.alterState(-1); // Pass nonsense
            gadgets.put(gadgetID, newGadget);

            //Write new gadget to JSON
            writeGadgetPersonToFile(newGadget);
            //Send new gadget to clients = New gadget detected
            ServerConnection.getInstance().writeToServer("351::" + newGadget.toHoSoProtocol());

        }
    }

    // Used when new gadgets are added via GadgetAdder or GPS.
    // If client has gadgets with IDs: [1, 2, 152] -> The new gadget will get ID 153.
    private int generateGadgetID() {
        synchronized (lock_gadgets) {
            int newID = 0;
            for (int gadgetID : gadgets.keySet()) {
                if (gadgetID >= newID) {
                    newID = gadgetID + 1;
                }
            }
            return newID;
        }
    }

    // Used when adding gadgets (plug & play) to verify that a gadget do not already exist in hub.
    private boolean gadgetAlreadyAdded(String unitMac) {
        synchronized (lock_gadgets) {
            for (int key : gadgets.keySet()) {
                Gadget gadget = gadgets.get(key);
                if (gadget instanceof GadgetBasic) {
                    if(((GadgetBasic) gadget).getUnitMac().equals(unitMac)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    // #620
    private void addGadgets(String[] request) {
        try {
            String gadgetIp = request[request.length-1]; // Appended by class GadgetAdder.
            String unitMac = request[1];
            if(!gadgetAlreadyAdded(unitMac)) {
                int gadgetPort = Integer.parseInt(request[2]);
                int nbrOfGadgets = Integer.parseInt(request[3]);
                int count = 3;
                for (int i = 0; i < nbrOfGadgets; i++) {
                    String alias = request[++count];
                    GadgetType type = GadgetType.valueOf(request[++count]);
                    String requestSpec = request[++count];
                    // Generate the rest of the values:
                    int gadgetID = generateGadgetID();
                    String valueTemplate = "default";
                    long pollDelaySeconds = 5; //TODO: Increase to ~30sec AFTER PRESENTATION
                    GadgetBasic newGadget = new GadgetBasic(gadgetID, alias, type, valueTemplate, requestSpec, pollDelaySeconds, gadgetPort,gadgetIp, true, unitMac);
                    synchronized (lock_gadgets) {
                        gadgets.put(gadgetID, newGadget);
                        writeToGadgetBasic(newGadget);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error on reading in new gadgets.");
            // Cancel operation
        }
        printGadgets();
    }

    //========================= FILE HANDLING ===============================================
    private void readGadgetBasicFile() throws Exception {
        try {
            JSONParser parser = new JSONParser();
            JSONArray array = (JSONArray) parser.parse(new FileReader(gadgets_basic_fileJSON));
            for (Object object : array) {
                JSONObject gadget = (JSONObject) object;

                int id = Integer.parseInt(String.valueOf(gadget.get("id")));
                String alias = (String) gadget.get("alias");
                GadgetType type = GadgetType.valueOf((String) gadget.get("type"));
                String valueTemplate = (String) gadget.get("valueTemplate");
                String requestSpec = (String) gadget.get("requestSpec");
                long pollDelaySeconds = Long.parseLong(String.valueOf(gadget.get("pollDelaySec")));
                int port = Integer.parseInt(String.valueOf(gadget.get("port")));
                String ip = (String) gadget.get("ip");
                boolean enabled = (Boolean) gadget.get("enable");
                String unitMac = (String) gadget.get("unitMac");

                GadgetBasic gadgetBasic = new GadgetBasic(id, alias, type, valueTemplate, requestSpec, pollDelaySeconds, port, ip, enabled,unitMac);
                gadgets.put(id, gadgetBasic);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Problem reading gadgets_basic.json");
        }
    }

    private void writeToGadgetBasic(GadgetBasic newGadget) throws Exception{
        JSONObject gadget = new JSONObject();
        gadget.put("pollDelaySec", newGadget.pollDelaySec);
        gadget.put("port", newGadget.getPort());
        gadget.put("enable", newGadget.isEnabled());
        gadget.put("requestSpec", newGadget.getRequestSpec());
        gadget.put("ip", newGadget.getIp());
        gadget.put("unitMac", newGadget.getUnitMac());
        gadget.put("alias", newGadget.alias);
        gadget.put("id", newGadget.id);
        gadget.put("type", newGadget.type);
        gadget.put("valueTemplate", newGadget.valueTemplate);


        try {
            JSONParser parser = new JSONParser();
            JSONArray array = (JSONArray) parser.parse(new FileReader(gadgets_basic_fileJSON));
            array.add(gadget);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String prettyJson = gson.toJson(array);
            try (FileWriter writer = new FileWriter(gadgets_basic_fileJSON)) {
                writer.write(prettyJson);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Problem writing gadgets_basic.json");
        }
    }

    private void readGadgetPersonFile() throws Exception {
        try {
            JSONParser parser = new JSONParser();
            JSONArray array = (JSONArray) parser.parse(new FileReader(gadgets_person_fileJSON));
            for (Object object : array) {
                JSONObject gadget = (JSONObject) object;

                int id = Integer.parseInt(String.valueOf(gadget.get("id")));
                String alias = (String) gadget.get("alias");
                long pollDelaySeconds = Long.parseLong(String.valueOf(gadget.get("pollDelaySec")));
                String nameID = (String) gadget.get("nameID");
                String lastUpdate = (String) gadget.get("lastUpdate");
                double lastState = (Double) gadget.get("lastState");
                boolean enabled = (Boolean) gadget.get("enable");

                GadgetPerson gadgetPerson = new GadgetPerson(id, alias, pollDelaySeconds, nameID, lastUpdate, lastState, enabled);
                gadgets.put(id, gadgetPerson);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Problem reading gadgets_Person.json");
        }
    }

    private void writeGadgetPersonToFile(GadgetPerson gadgetPerson) throws Exception {
        JSONObject gadget = new JSONObject();
        gadget.put("id", gadgetPerson.id);
        gadget.put("alias", gadgetPerson.alias);
        gadget.put("pollDelaySec", gadgetPerson.pollDelaySec);
        gadget.put("nameID", gadgetPerson.nameID);
        gadget.put("lastUpdate", gadgetPerson.lastUpdateToSimpleDate());
        gadget.put("lastState", gadgetPerson.getState());
        gadget.put("enable", gadgetPerson.isEnabled());

        try {
            JSONParser parser = new JSONParser();
            JSONArray array = (JSONArray) parser.parse(new FileReader(gadgets_person_fileJSON));
            array.add(gadget);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String prettyJson = gson.toJson(array);
            try (FileWriter writer = new FileWriter(gadgets_person_fileJSON)) {
                writer.write(prettyJson);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Problem writing gadgets_person.json");
        }
    }

    private void readInSettings() throws Exception {
        try (FileReader reader = new FileReader(settingsFileJSON)) {
            settings = new Gson().fromJson(reader, Settings.class);
        } catch (FileNotFoundException e) {
            throw new Exception("Unable to read settings from config.json");
        }
    }

    private void alterAliasInJson(int gadgetID, String newAlias) throws Exception {
        String fileName = "";
        if (gadgets.get(gadgetID) instanceof GadgetBasic) {
            fileName = gadgets_basic_fileJSON;
        } else if (gadgets.get(gadgetID) instanceof GadgetPerson) {
            fileName = gadgets_person_fileJSON;
        }

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
            Float state = Float.parseFloat((String) pair.getValue());
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

    private void readGadgetPluginFile() {
        JSONParser parser = new JSONParser();
        try {
            JSONObject jsonObject = (JSONObject) parser.parse(new FileReader(gadgets_plugin_fileJSON));

            JSONObject gadgetLocalPi = (JSONObject) jsonObject.get("local_pi_cpu_temp");
            if((Boolean) gadgetLocalPi.get("enable")) {
                int gadgetID = Integer.parseInt(String.valueOf(gadgetLocalPi.get("id")));
                String gadgetAlias = (String) gadgetLocalPi.get("alias");
                long pollDelaySeconds = Long.parseLong(String.valueOf(gadgetLocalPi.get("pollDelaySec")));
                synchronized (lock_gadgets) {
                    gadgets.put(gadgetID, new GadgetLocalPi(gadgetID, gadgetAlias, pollDelaySeconds));
                }
            }
        } catch(Exception e) {
            System.out.println("Unable to read plugin gadgets file (gadgets_plugin.json)");
        }
    }

    private void printGroups() {
        if (!gadgetGroup.isEmpty()) {
            System.out.println(String.format("%n%s%n%s%n%-20s%s%n%s", "GROUPS:", line(), "NAME", "GADGETS", line()));
            for (GadgetGroup aGadgetGroup : gadgetGroup) {
                System.out.println(String.format("%-20s%s", aGadgetGroup.getGroupName(), Arrays.toString(aGadgetGroup.getGadgets())));
            }
            System.out.println(line());
        }
    }

    private void printGadgets() {
        if(settings.debugMode) {
            System.out.println(String.format("%s%n%s%n%-4s%-18s%-5s%5s%-10s%-9s%-16s%s%n%s", "GADGETS:",
                    line(), "ID", "ALIAS", "STATE", "", "PRESENT", "CLASS", "TYPE", "VALUE TEMPLATE", line()));
            synchronized (lock_gadgets) {
                for (int gadgetID : gadgets.keySet()) {
                    Gadget gadget = gadgets.get(gadgetID);
                    String gadgetClass = gadget.getClass().getSimpleName().substring(6);
                    //gadgetClass = gadgetClass.substring(6);
                    String gadgetState = gadget.getState() == -1 ? "N/A" : String.format("%.1f", gadget.getState());
                    String present = gadget.isPresent() ? "Yes" : "No";
                    System.out.println(String.format("%-4s%-18s%5s%5s%-10s%-9s%-16s%s",
                            gadget.id, gadget.alias, gadgetState, "", present, gadgetClass, gadget.type.toString(), gadget.valueTemplate));
                }
            }
            System.out.println(line());
        }
    }
    private String line() {
        return "==================================================================================";
    }

    /*private void printGadgets() {
        if (!gadgets.isEmpty()) {
            System.out.println("=== ALL GADGETS ===");
            for (int key : gadgets.keySet()) {
                System.out.println("Alias: " + gadgets.get(key).alias + "\n" +
                        "State: " + gadgets.get(key).getState() + "\n" + "Present: " + gadgets.get(key).isPresent());
            }
            System.out.println("====================");
        }
    }*/

    //========================= SHUT DOWN SEQ ===============================================
    private void updatePersonFile() {
        System.out.println("Updating personfile");
        try {
            JSONParser parser = new JSONParser();
            JSONArray array = (JSONArray) parser.parse(new FileReader(gadgets_person_fileJSON));

            for (int gadgetID : gadgets.keySet()) {
                if (gadgets.get(gadgetID) instanceof GadgetPerson) {
                    double lastState = gadgets.get(gadgetID).getState();
                    String lastUpdate = ((GadgetPerson) gadgets.get(gadgetID)).lastUpdateToSimpleDate();

                    for (Object object : array) {
                        JSONObject gadget = (JSONObject) object;
                        if ((Long) gadget.get("id") == gadgetID) {
                            gadget.put("lastState", lastState);
                            gadget.put("lastUpdate", lastUpdate);
                            break;
                        }
                    }
                }
            }

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String prettyJson = gson.toJson(array);
            try (FileWriter writer = new FileWriter(gadgets_person_fileJSON)) {
                writer.write(prettyJson);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Problem updating personFile");
        }
    }

    private void debugLog(String title, String log) {
        if(settings.debugMode) {
            System.out.println(String.format("%-18s%s", title.concat(":"), log));
        }
    }
}
