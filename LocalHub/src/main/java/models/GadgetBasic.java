package models;

public class GadgetBasic extends Gadget {

    /**
     * Class representing interface to native HomeSome hardware (physical) com.homesome.model of all GadgetTypes.
     * The com.homesome.model interacted with via this class are commonly built upon Arduino based WiFi-modules.
     */

    public GadgetBasic(int gadgetID, String alias, GadgetType type, String valueTemplate, long pollDelaySeconds) {
        super(gadgetID, alias, type, valueTemplate, pollDelaySeconds);
    }

    public GadgetBasic(int gadgetID, String alias, GadgetType type, String valueTemplate, float state, long pollDelaySeconds) {
        super(gadgetID, alias, type, valueTemplate, state, pollDelaySeconds);
    }

    @Override
    public void poll() {
        // Implement
    }

    @Override
    public void alterState(float requestedState) throws Exception {
        // Implement
    }

    @Override
    protected String sendCommand(String command) throws Exception {
        // Implement
        return null;
    }

    @Override
    public void setState(float newState) {
        // Implement
        super.setState(newState);
    }
}
