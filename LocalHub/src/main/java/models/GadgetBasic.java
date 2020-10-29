package models;

import communicationResources.ServerConnection;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;

public class GadgetBasic extends Gadget {
    private BufferedReader input;
    private BufferedWriter output;
    private InetSocketAddress inetSocketAddress;
    private Socket socket;
    private String request;
    private int port;
    private String ip;

    /**
     * Class representing interface to native HomeSome hardware (physical) com.homesome.model of all GadgetTypes.
     * The com.homesome.model interacted with via this class are commonly built upon Arduino based WiFi-modules.
     */


    public GadgetBasic(int gadgetID, String alias, GadgetType type, String valueTemplate, float state, long pollDelaySeconds, int port, String ip) {
        super(gadgetID, alias, type, valueTemplate, state, pollDelaySeconds);
        this.port = port;
        this.ip = ip;
    }

    @Override
    public void poll() throws Exception {
        String newState = sendCommand("341");

        //if state changed
        checkStateChange(newState);
    }

    @Override
    public void alterState(float requestedState) throws Exception {
        //Call method sendCommand
        //Forwards reqState to sendCommand that sends to gadget
        String newState = sendCommand("313::" + requestedState);

        //if state changed
        checkStateChange(newState);
    }

    @Override
    protected String sendCommand(String command) throws IOException {
        //This communicates with a gadget
        this.inetSocketAddress = new InetSocketAddress(ip, port);
        socket.connect(inetSocketAddress);
        //set a timeOut
        socket.setSoTimeout(2000);
        this.input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

        output.write(command.concat(String.format("%n")));

        //eturn the response from gadget
        return input.readLine();
    }

    private void checkStateChange(String newState) {
        if (!newState.equalsIgnoreCase(String.valueOf(getState()))) {
            setState(Float.parseFloat(newState));
        }
    }

    @Override
    public void setState(float newState) {
        //TODO
        //Implement
        //We should send a new state to the client?
        super.setState(newState);
        ServerConnection.getInstance().writeToServer("315::" + id + "::" + newState);
    }
}
