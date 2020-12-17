package models;

public abstract class Gadget {
    public final int id;
    public String alias;
    public final GadgetType type;
    private double state;
    public String valueTemplate; //
    public long lastPollTime;
    public final long pollDelaySec;
    private boolean isPresent;
    private boolean enabled;

    // Gadgets instantiated from JSON file (com.homesome.model.json) at system boot??
    public Gadget(int gadgetID, String alias, GadgetType type, String valueTemplate, long pollDelaySeconds, boolean enabled) {
        this.id = gadgetID;
        this.alias = alias;
        this.type = type;
        this.valueTemplate = valueTemplate;
        state = -1;
        pollDelaySec = pollDelaySeconds;
        isPresent = false;
        this.enabled = enabled;
    }

    public Gadget(int gadgetID, String alias, GadgetType type, String valueTemplate, long pollDelaySeconds) {
        this.id = gadgetID;
        this.alias = alias;
        this.type = type;
        this.valueTemplate = valueTemplate;
        this.state = 0;
        pollDelaySec = pollDelaySeconds;
        isPresent = false;
        enabled = true;
    }

    // Request gadget's current state
    public abstract void poll() throws Exception;

    // Request gadget to alter state
    public abstract void alterState(float requestedState) throws Exception;

    // Communications specifics for sending request to a gadget
    protected abstract String sendCommand(String command) throws Exception;

    // Set instance variable 'state' to match actual state (called when a gadget has reported a state change)
    public void setState(double newState) throws Exception {
        boolean isBinaryGadget = (type == GadgetType.SWITCH || type == GadgetType.BINARY_SENSOR);
        if (isBinaryGadget) {
            state = (newState == 1 ? 1 : 0);
        } else {
            state = newState;
        }
    }

    // Translate gadget according to HoSo protocol. Gadget object -> HoSo protocol
    public String toHoSoProtocol() {
        return String.format("%s::%s::%s::%s::%s::%s", id, alias, type, valueTemplate, state, pollDelaySec);
    }

    public boolean isPresent() {
        return isPresent;
    }

    public void setPresent(boolean present) {
        isPresent = present;
    }

    // Set last poll time when polling successed
    public void setLastPollTime(long lastPollTime) {
        this.lastPollTime = lastPollTime;
    }

    public double getState() {
        return state;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
