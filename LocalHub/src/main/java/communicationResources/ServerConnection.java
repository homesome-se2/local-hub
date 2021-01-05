package communicationResources;

import mainPackage.ClientApp;
import models.ServerStatus;

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
    public volatile ServerStatus serverStatus;
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
        this.serverStatus = ServerStatus.WAITING_CONNECTION;
        this.session = null;
        this.manageConnThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    manageConnection();
                } catch (Exception e) {
                    System.out.println("Serverconnection management lost..");
                }
            }
        });
    }

    public void connectToServer(String loginRequest) {
        this.loginRequest = loginRequest;
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            String uri = "ws://134.209.198.123:8084/homesome";
            container.connectToServer(WebSocketClient.class, URI.create(uri));
        } catch (Exception e) {
            serverStatus = ServerStatus.CONNECTION_ERROR;
            e.printStackTrace();
        }
    }

    public void closeConnection() {
        synchronized (lockObject_close) {
            if (serverStatus == ServerStatus.CONNECTED) {
                serverStatus = ServerStatus.NOT_CONNECTED;
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
        serverStatus = ServerStatus.CONNECTED;
        System.out.println("Connected To Server");

        if (!manageConnThread.isAlive()) {
            manageConnThread.start();
        }

        writeToServer(loginRequest);
    }

    public void onServerClose() {
        serverStatus = ServerStatus.CONNECTION_ERROR;
        loggedInToServer = false;
        System.out.println("Closed Server Connection, attempting to reconnect in 45 sec..");
    }

    public void writeToServer(String msg) {
        synchronized (lockObject_output) {
            debugLog("Msg to server", msg);
            try {
                if ((serverStatus == ServerStatus.CONNECTED) && session.isOpen()) {
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
        debugLog("Msg from server", msg);
        try {
            incomingServerCommands.put(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void manageConnection() throws Exception {
        long pingIntervall = 45 * 1000;
        while (!ClientApp.getInstance().terminate) {
            Thread.sleep(pingIntervall);
            if (loggedInToServer) {
                writeToServer("ping");
            } else {
                connectToServer(loginRequest);
            }
        }
    }

    private void debugLog(String title, String log) {
        if(ClientApp.getInstance().settings.debugMode) {
            System.out.println(String.format("%-17s%s", title.concat(":"), log));
        }
    }
}
