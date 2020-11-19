package models;

import java.util.List;

public class GadgetGroup {
    //TODO
    //Create groups.json and read into groupsList in ClientApp

    public String groupName;
    private int[] gadgets;

    public GadgetGroup(String groupName, int[] gadget) {
        this.groupName = groupName;
        this.gadgets = gadget;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public int[] getGadgets() {
        return gadgets;
    }

    public void setGadgets(int[] gadgets) {
        this.gadgets = gadgets;
    }

    public String toHosoArrayFormat(){
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("::" + this.groupName);
        for (int i : this.gadgets){
            stringBuilder.append(":" + i);
        }
        return stringBuilder.toString();
    }
}