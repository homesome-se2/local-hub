package models;

import java.util.List;

public class GadgetGroup {
    //TODO
    //Create groups.json and read into groupsList in ClientApp
    String name;
    List<Integer> gadget;

    public GadgetGroup(String name, List<Integer> gadget) {
        this.name = name;
        this.gadget = gadget;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Integer> getGadget() {
        return gadget;
    }

    public void setGadget(List<Integer> gadget) {
        this.gadget = gadget;
    }
}

//