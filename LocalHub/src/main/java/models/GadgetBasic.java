package models;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;

public class GadgetBasic extends Gadget {
    private BufferedReader input;
    private BufferedWriter output;
    private Socket socket;
    private String request;

    /**
     * Class representing interface to native HomeSome hardware (physical) com.homesome.model of all GadgetTypes.
     * The com.homesome.model interacted with via this class are commonly built upon Arduino based WiFi-modules.
     */
    private int port;
    private String ip;

    public GadgetBasic(int gadgetID, String alias, GadgetType type, String valueTemplate, float state, long pollDelaySeconds,int port, String ip) {
        super(gadgetID, alias, type, valueTemplate, state, pollDelaySeconds);
        this.port = port;
        this.ip = ip;
    }

    public GadgetBasic(int gadgetID, String alias, GadgetType type, String valueTemplate, float state, long pollDelaySeconds, BufferedReader input, BufferedWriter output, int port, String IP, String request) throws IOException {
        super(gadgetID, alias, type, valueTemplate, state, pollDelaySeconds);
        this.input = input;
        this.output = output;
        this.port = port;
        this.ip = IP;
        this.request = request;

        input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

    }

    @Override
    public void poll() throws Exception{
        //TODO
        // Implement
        sendCommand("341");
    }

    @Override
    public void alterState(float requestedState) throws Exception {
        //TODO
        //Implement
        //Call method sendCommand
        //Forwards reqState to sendCommand that sends to gadget
        sendCommand("313::" + requestedState);
    }

    @Override
    protected String sendCommand(String command) throws IOException {
        //TODO
        //Implement
        //Here we can have the socket implementation we get back
        //This communicates with a gadget
        //1 send req
        //2 get res
        //call setState if state has changed

        try {
            InetSocketAddress inetSocketAddress = new InetSocketAddress(ip, port);
            socket.connect(inetSocketAddress);
            //set a timeOut
            socket.setSoTimeout(2000);
            output.write(command.concat(String.format("%n")));

            //We get the state of the gadget back
            //Compare old state to the new state
            //if not same, the gadget is updated
            //we send out to client

            String newState = input.readLine();
            if (!newState.equalsIgnoreCase(String.valueOf(getState()))){
                setState(Float.parseFloat(newState));
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
            //Unable to reach gadget
            //If not present set false
        }

        //respond
        return input.readLine();

    }

    @Override
    public void setState(float newState) {
        //TODO
        // Implement
        //We should send a new state to the client?
        super.setState(newState);
    }
}
