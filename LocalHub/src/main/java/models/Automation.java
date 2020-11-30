package models;


import models.automations.Action;
import models.automations.Delay;
import models.automations.Trigger;

import java.util.ArrayList;
import java.util.Map;

//class represents a simple automation object of when the masterGadget reaches a
//certain state, a slaveGadget changes to the set state
public class Automation {
    private String name;
    private boolean enabled;
    private Trigger trigger;
    private Delay delay;

    public Automation(String name, boolean enabled, Trigger trigger, Delay delay) {
        this.name = name;
        this.enabled = enabled;
        this.trigger = trigger;
        this.delay = delay;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Trigger getTrigger() {
        return trigger;
    }

    public void setTrigger(Trigger trigger) {
        this.trigger = trigger;
    }

    public Delay getDelay() {
        return delay;
    }

    public void setDelay(Delay delay) {
        this.delay = delay;
    }
}
