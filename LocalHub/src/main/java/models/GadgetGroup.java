package models;

public class GadgetGroup {
    String name;
    int[] gadgets;

    public GadgetGroup(String name, int[] gadgets){
        this.name = name;
        this.gadgets = gadgets;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int[] getGadgets() {
        return gadgets;
    }

    public void setGadgets(int[] gadgets) {
        this.gadgets = gadgets;
    }
}
