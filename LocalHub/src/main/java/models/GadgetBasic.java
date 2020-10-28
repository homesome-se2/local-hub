package models;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;

public class GadgetBasic extends Gadget {
    private BufferedReader input;
    private BufferedWriter output;
    private Socket socket;
    private int port;
    private String IP;
    private String request;

    /**
     * Class representing interface to native HomeSome hardware (physical) com.homesome.model of all GadgetTypes.
     * The com.homesome.model interacted with via this class are commonly built upon Arduino based WiFi-modules.
     */

    public GadgetBasic(int gadgetID, String alias, GadgetType type, String valueTemplate, float state, long pollDelaySeconds) {
        super(gadgetID, alias, type, valueTemplate, state, pollDelaySeconds);

    }


    public GadgetBasic(int gadgetID, String alias, GadgetType type, String valueTemplate, float state, long pollDelaySeconds, BufferedReader input, BufferedWriter output, int port, String IP, String request) {
        super(gadgetID, alias, type, valueTemplate, state, pollDelaySeconds);
        this.input = input;
        this.output = output;
        this.port = port;
        this.IP = IP;
        this.request = request;

        socket = new Socket();
        try {
            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void poll() {
        //TODO
        // Implement


    }

    @Override
    public void alterState(float requestedState) throws Exception {
        //TODO
        // Implement
    }

    @Override
    protected String sendCommand(String command) throws Exception {
        //TODO
        // This method communicated with gadgets
        try {
            InetSocketAddress inetSocketAddress = new InetSocketAddress(IP,port);
            socket.connect(inetSocketAddress);
            //set a timeOut
            socket.setSoTimeout(2000);
            output.write(command.concat(String.format("%n")));
        }catch (IOException e){
            System.out.println(e.getMessage());
            input.close();
            output.close();
        }
        return input.readLine();
    }

    @Override
    public void setState(float newState) {
        //TODO
        // Implement
        super.setState(newState);
    }
}
