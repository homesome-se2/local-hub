package models;

import java.util.List;

public class GadgetGroup {
    //TODO
    //Create groups.json and read into groupsList in ClientApp

    String name;
    private int[] gadget;

    public GadgetGroup(String name, int[] gadget) {
        this.name = name;
        this.gadget = gadget;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int[] getGadget() {
        return gadget;
    }

    public void setGadget(int[] gadget) {
        this.gadget = gadget;
    }
}
