package models;


//class represents a simple automation object of when the masterGadget reaches a
//certain state, a slaveGadget changes to the set state
public class Automation {
    private int masterId;
    private int slaveId;
    private float masterState;
    private float slaveState;

    public Automation(int masterId, int slaveId, float masterState, float slaveState) {
        this.masterId = masterId;
        this.slaveId = slaveId;
        this.masterState = masterState;
        this.slaveState = slaveState;
    }

    public int getMasterId() {
        return masterId;
    }

    public void setMasterId(int masterId) {
        this.masterId = masterId;
    }

    public int getSlaveId() {
        return slaveId;
    }

    public void setSlaveId(int slaveId) {
        this.slaveId = slaveId;
    }

    public float getMasterState() {
        return masterState;
    }

    public void setMasterState(float masterState) {
        this.masterState = masterState;
    }

    public float getSlaveState() {
        return slaveState;
    }

    public void setSlaveState(float slaveState) {
        this.slaveState = slaveState;
    }
}
