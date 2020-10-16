package mainPackage;


public class Main {
    public static void main(String[] args) {
        ClientApp clientApp = new ClientApp();
        //Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            //Cleans up in case of external shutdown/error
            @Override
            public void run() {
                System.out.println("ShutDown Hook Executing");
                clientApp.closeApp();
            }
        }));
        //Launch Hub
        clientApp.startHub();
    }
}
