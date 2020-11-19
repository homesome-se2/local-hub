package models;

import communicationResources.ServerConnection;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;

public class GadgetBasic extends Gadget {
    private BufferedReader input;
    private BufferedWriter output;
    private Socket socket;
    private int port;
    private String ip;
    private String requestSpec;

    /**
     * Class representing interface to native HomeSome hardware (physical) com.homesome.model of all GadgetTypes.
     * The com.homesome.model interacted with via this class are commonly built upon Arduino based WiFi-modules.
     */

    public GadgetBasic(int gadgetID, String alias, GadgetType type, String valueTemplate, String requestSpec, float state, long pollDelaySeconds, int port, String ip) {
        super(gadgetID, alias, type, valueTemplate, state, pollDelaySeconds);
        this.port = port;
        this.ip = ip;
        this.requestSpec = requestSpec;
    }
    @Override
    public void poll() {
        try {
            String response = sendCommand("{\"command\":341,\"requestSpec\":\"" + requestSpec + "\"}");

            String splittedResponse[] = response.split("::");
            //if state changed
            if (splittedResponse[0].equalsIgnoreCase("314")) {
                checkStateChange(splittedResponse[1]);
                setPresent(true);
                return;
            }
        } catch (Exception e) {

        }
        System.out.println("Gadget: " + this.id + " is not present..");
        setPresent(false);
    }

    @Override
    public void alterState(float requestedState) {
        try {
            System.out.println("Alter state of gadget: " + this.id);
            String response = sendCommand("{\"command\":313,\"requestSpec\":" + "\"" + requestSpec + "\",\"requestedState\":" + requestedState + "}");

            String splittedResponse[] = response.split("::");
            //if state changed
            if (splittedResponse[0].equalsIgnoreCase("314")) {
                checkStateChange(splittedResponse[1]);
            }
        } catch (Exception e) {

        }
    }

    @Override
    protected String sendCommand(String command) throws IOException {
        try {

            this.socket = new Socket();
            this.socket.connect(new InetSocketAddress(this.ip, this.port), 1500);
            //set a timeOut on read
            this.socket.setSoTimeout(3500);
            this.input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            //Write to gadget
            this.output.write(encryptDecrypt(command.concat("\n")));
            this.output.flush();

            //return the response from gadget
            return encryptDecrypt(input.readLine());
        } catch (Exception e) {
            return null;
        } finally {
            this.output.close();
            this.output.close();
        }
    }

    //This method will encrypt and decrypt
    private static String encryptDecrypt(String input) {
        char[] key = {'A', 'K', 'M','F','S'};
        StringBuilder output = new StringBuilder();
        for(int i = 0 ; i < input.length() ; i++) {
            output.append((char)(input.charAt(i) ^ key[i % key.length]));
        }
        return output.toString();
    }

    private void checkStateChange(String newState) {
        if (Float.parseFloat(newState) != getState()) {
            setState(Float.parseFloat(newState));
        } else {
            //ignore, no change of state in gadget
        }
    }

    @Override
    public void setState(float newState) {
        super.setState(newState);
        ServerConnection.getInstance().writeToServer("315::" + this.id + "::" + newState);
    }


    private void closeConnections() {
        try {
            if (socket != null) {
                socket.close();
            }
            if (this.input != null) {
                this.input.close();
            }
            if (this.output != null) {
                this.output.close();
            }
        } catch (Exception e) {
            System.out.println("Problem closing gadget connections");
        }
    }
}
