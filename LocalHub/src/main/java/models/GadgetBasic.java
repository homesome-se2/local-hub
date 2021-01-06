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
    private String unitMac;

    /**
     * Class representing interface to native HomeSome hardware (physical) com.homesome.model of all GadgetTypes.
     * The com.homesome.model interacted with via this class are commonly built upon Arduino based WiFi-modules.
     */

    public GadgetBasic(int gadgetID, String alias, GadgetType type, String valueTemplate, String requestSpec, long pollDelaySeconds, int port, String ip,boolean enabled, String unitMac) {
        super(gadgetID, alias, type, valueTemplate, pollDelaySeconds,enabled);
        this.port = port;
        this.ip = ip;
        this.requestSpec = requestSpec;
        this.unitMac = unitMac;
    }

    @Override
    public void poll() {
        try {
            String response = sendCommand("{\"command\":341,\"requestSpec\":\"" + requestSpec + "\"}");

            String[] splittedResponse = response.split("::");
            //if state changed
            if (splittedResponse[0].equalsIgnoreCase("314")) {
                setState(Double.parseDouble(splittedResponse[1]));
                setPresent(true);
                return;
            }
        } catch (Exception e) {
            setPresent(false);
        }
    }

    @Override
    public void alterState(float requestedState) throws Exception{
        try {
            if (type == GadgetType.SWITCH){
                requestedState = requestedState == 1 ? 1 : 0;
            }

            String response = sendCommand("{\"command\":313,\"requestSpec\":" + "\"" + requestSpec + "\",\"requestedState\":" + requestedState + "}");

            String[] splittedResponse = response.split("::");
            //if state changed
            if (splittedResponse[0].equalsIgnoreCase("314")) {
                setState(Float.parseFloat(splittedResponse[1]));
            } else {
                setState(getState());
            }
        } catch (Exception e) {
            setPresent(false);
            throw new Exception();
        }
    }

    @Override
    protected String sendCommand(String command) throws Exception {
        try {
            this.socket = new Socket();
            this.socket.connect(new InetSocketAddress(this.ip, this.port), 1500);
            //set a timeOut on read
            this.socket.setSoTimeout(3500);
            this.input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            //Write to gadget
            //this.output.write(encryptDecrypt(command.concat("\n")));
            this.output.write(command.concat("\n"));
            this.output.flush();

            //return the response from gadget
            //return encryptDecrypt(input.readLine());
            return input.readLine();
        } catch (Exception e) {
            throw new Exception("Unable to connect to gadget.");
        } finally {
            if (this.socket != null){
                this.socket.close();
            }
        }
    }


    //TODO test needs to be implemented in the gadgets aswell for translation?
    //This method will encrypt and decrypt
    private static String encryptDecrypt(String input) {

        char[] key = {'A', 'K', 'M'};

        StringBuilder output = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            output.append((char) (input.charAt(i) ^ key[i % key.length]));
        }
        return output.toString();
    }

    @Override
    public void setState(double newState)throws Exception {
        super.setState(newState);
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

    public String getUnitMac() {
        return unitMac;
    }

    public int getPort() {
        return port;
    }

    public String getIp() {
        return ip;
    }

    public String getRequestSpec() {
        return requestSpec;
    }
}
