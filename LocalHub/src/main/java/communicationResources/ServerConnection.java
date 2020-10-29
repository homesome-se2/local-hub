package communicationResources;

import mainPackage.ClientApp;

import javax.websocket.ContainerProvider;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import java.net.URI;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class ServerConnection {

    //This queue holds all commands incoming from server.
    public BlockingQueue<String> incomingServerCommands;
    //An instance representing the communication between the client and server
    private Session session;
    private volatile boolean connectedToServer;
    public volatile boolean loggedInToServer;
    private String loginRequest;


    //Manage session, ping and reconnect to public server
    private Thread manageConnThread;

    private final Object lockObject_output;
    private final Object lockObject_close;

    //Singleton
    private static ServerConnection instance;

    public static ServerConnection getInstance() {
        if (instance == null) {
            instance = new ServerConnection();
        }
        return instance;
    }

    private ServerConnection() {
        this.incomingServerCommands = new ArrayBlockingQueue<>(10);
        this.lockObject_output = new Object();
        this.lockObject_close = new Object();
        this.connectedToServer = false;
        this.session = null;
    }

    public void connectToServer(String loginRequest) {
        this.loginRequest = loginRequest;
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            String uri = "ws://134.209.198.123:8084/homesome";
            container.connectToServer(WebSocketClient.class, URI.create(uri));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void closeConnection() {
        synchronized (lockObject_close) {
            if (connectedToServer) {
                connectedToServer = false;
                loggedInToServer = false;
                manageConnThread.interrupt();
                try {
                    if (session.isOpen()) {
                        session.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void onServerConnect(Session session) {
        this.session = session;
        connectedToServer = true;
        System.out.println("Connected To Server");


        if (!manageConnThread.isAlive()){
            manageConnThread.start();
        }

        writeToServer(loginRequest);
    }

    public void onServerClose() {
        connectedToServer = false;
        loggedInToServer = false;
        System.out.println("Closed Server Connection, attempting to reconnect in 45 sec..");
    }

    public void writeToServer(String msg) {
        synchronized (lockObject_output) {
            System.out.println("\n\nMessage to server: " + msg + "\n\n");
            try {
                if (connectedToServer && session.isOpen()) {
                    session.getBasicRemote().sendText(msg);
                } else {
                    System.out.println("Not Connected To Server");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void newCommandFromServer(String msg) {
        System.out.println("\n\nMSG from server: " + msg + "\n\n");
        try {
            incomingServerCommands.put(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void manageConnection() throws Exception{
        long pingIntervall = 45 *1000;
        while (!ClientApp.getInstance().terminate){
            //TODO
            Thread.sleep(pingIntervall);
            if (loggedInToServer){
                writeToServer("ping");
            } else {
                connectToServer(loginRequest);
            }
        }
    }
}
