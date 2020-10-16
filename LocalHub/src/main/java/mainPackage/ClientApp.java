package mainPackage;

import communicationResources.ServerConnection;
import models.Gadget;
import models.GadgetBasic;
import models.GadgetType;

import java.util.HashMap;
import java.util.Scanner;

public class ClientApp {

    private HashMap<Integer, Gadget> gadgets;
    private Thread outputThread;
    private final Object lockObject_1;
    private volatile boolean terminate;

    public ClientApp() {
        this.gadgets = new HashMap<Integer, Gadget>();
        this.lockObject_1 = new Object();
        this.terminate = false;
        this.outputThread = new Thread(new Runnable() {
            public void run() {
                try {
                    outputToServer();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    closeApp();
                    System.out.println("\n\nOutput thread is now closed\n\n");
                }
            }
        });
    }


    public void startHub() {
        try {
            //Print message
            System.out.println("\nWelcome to LocalHub\n");
            //Start connection with server using websockets
            ServerConnection.getInstance().connectToServer();
            //read clint input to send to server(outputThread)
            outputThread.start();
            //proccess inputs read from server
            inputFromServer();
        }catch (Exception e){
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

    private void outputToServer() {
        Scanner scanner = new Scanner(System.in);
        //Log in directly (within 2 sec)
        String logIn = "101::Test::Test123";
        ServerConnection.getInstance().writeToServer(logIn);

        while (!terminate) {
            String hosoRequest = scanner.nextLine().trim();

            ServerConnection.getInstance().writeToServer(hosoRequest);
        }
    }

    private void inputFromServer() throws Exception {
        while (!terminate) {
            String commandFromServer = ServerConnection.getInstance().incomingServerCommands.take();
            String[] commands = commandFromServer.split("::");

            switch (commands[0]) {
                case "102":
                    successfulManualLogin(commands);
                    break;
                case "304":
                    recevieAllGadgets(commands);
                    break;
                case "316":
                    gadgetStateUpdate(commands);
                    break;
                case "901":
                    System.out.println("ExceptionMessage: " + commands[1]);
                    break;
                default:
                    System.out.println("\n\n Unknown message from server \n\n");
                    break;
            }
        }
    }

    private void successfulManualLogin(String[] commands)throws Exception{
        String name = commands[1];
        boolean admin = commands[2].equals("true");
        String homeAlias = commands[3];
        String sessionKey = commands[4];

        System.out.println("LOGIN GOOD \n" +
                "Name: " + name + "\n" +
                "Admin: " + admin + "\n" +
                "HomeAlias: " + homeAlias + "\n" +
                "SessionKey: " + sessionKey + "\n");
    }

    private void recevieAllGadgets(String[] commands)throws Exception{
        int nbrOfGadgets = Integer.parseInt(commands[1]);
        int count = 2;
        for (int i = 0; i < nbrOfGadgets; i++) {
            int gadgetID = Integer.parseInt(commands[count++]);
            String alias = commands[count++];
            GadgetType type = GadgetType.valueOf(commands[count++]);
            String valueTemplate = commands[count++];
            float state = Float.parseFloat(commands[count++]);
            long pollDelaySeconds = Long.parseLong(commands[count++]);

            gadgets.put(gadgetID, new GadgetBasic(gadgetID, alias, type, valueTemplate, state, pollDelaySeconds));
        }
        printGadgets();
    }

    private void gadgetStateUpdate(String[] commands)throws Exception{
        int gadgetID = Integer.parseInt(commands[1]);
        float newState = Float.parseFloat(commands[2]);

        gadgets.get(gadgetID).setState(newState);

        printGadgets();
    }

    private void printGadgets(){
        if (!gadgets.isEmpty()){
            System.out.println("=== ALL GADGETS ===\n");
            for(int key : gadgets.keySet()){
                System.out.println("Alias: " + gadgets.get(key).alias +"\n" +
                        "State: " + gadgets.get(key).getState());
            }
            System.out.println("====================");
        }


    }
}
