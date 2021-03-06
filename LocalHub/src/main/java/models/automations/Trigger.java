package models.automations;


public class Trigger {
    private String type;
    private int gadgetID;
    private String stateCondition;
    private float state;


    public Trigger(String type, int gadgetID, String stateCondition, float state) {
        this.type = type;
        this.gadgetID = gadgetID;
        this.stateCondition = stateCondition;
        this.state = state;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getGadgetID() {
        return gadgetID;
    }

    public void setGadgetID(int gadgetID) {
        this.gadgetID = gadgetID;
    }

    public String getStateCondition() {
        return stateCondition;
    }

    public void setStateCondition(String stateCondition) {
        this.stateCondition = stateCondition;
    }

    public float getState() {
        return state;
    }

    public void setState(float state) {
        this.state = state;
    }
}
