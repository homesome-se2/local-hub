package models.automations;

public class Action {
    private int gadgetID;
    private float state;

    public Action(int gadgetID, float state) {
        this.gadgetID = gadgetID;
        this.state = state;
    }


    public int getGadgetID() {
        return gadgetID;
    }

    public void setGadgetID(int gadgetID) {
        this.gadgetID = gadgetID;
    }

    public float getState() {
        return state;
    }

    public void setState(float state) {
        this.state = state;
    }
}
