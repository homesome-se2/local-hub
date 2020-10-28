package models;

public class GadgetBasic extends Gadget {

    /**
     * Class representing interface to native HomeSome hardware (physical) com.homesome.model of all GadgetTypes.
     * The com.homesome.model interacted with via this class are commonly built upon Arduino based WiFi-modules.
     */
    private int port;
    private String ip;

    public GadgetBasic(int gadgetID, String alias, GadgetType type, String valueTemplate, float state, long pollDelaySeconds, int port, String ip) {
        super(gadgetID, alias, type, valueTemplate, state, pollDelaySeconds);
        this.port = port;
        this.ip = ip;
    }

    @Override
    public void poll() {
        //TODO
        //Implement
        //Call method sendCommand
    }

    @Override
    public void alterState(float requestedState) throws Exception {
        //TODO
        //Implement
        //Call method sendCommand
        //Forwards reqState to sendCommand that sends to gadget
    }

    @Override
    protected String sendCommand(String command) throws Exception {
        //TODO
        //Implement
        //Here we can have the socket implementation we get back
        //This communicates with a gadget
        //1 send req
        //2 get req
        //call setState if state has changed
        return null;
    }

    @Override
    public void setState(float newState) {
        //TODO
        // Implement
        super.setState(newState);
    }
}
