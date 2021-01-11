package models;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

public class GadgetLocalPi extends Gadget {
    /** 
     * Note: This plugin is configured to work in Hubs
     * running on a Raspberry Pi 3 Model B+ with Debian GNU/Linux.
     */

    public GadgetLocalPi(int gadgetID, String alias, long pollDelaySec) {
        super(gadgetID, alias, GadgetType.SENSOR, "temp", pollDelaySec, true);
    }
    @Override
    public void poll() throws Exception {
        try {
            setState(read_CPU_temp());
            setPresent(true);
        } catch (Exception e) {
            setPresent(false);
        }
    }

    @Override
    public void alterState(float requestedState) throws Exception {

    }

    @Override
    protected String sendCommand(String command) throws Exception {
        return null;
    }

    private double read_CPU_temp() throws Exception {
        String fileName = "/sys/class/thermal/thermal_zone0/temp";
        String line = null;
        int tempC = 0;
        try {
            try (BufferedReader bufferedReader = new BufferedReader(new FileReader(fileName))) {

                while ((line = bufferedReader.readLine()) != null) {
                    tempC = (Integer.parseInt(line) / 1000);
                }
            }
        } catch (FileNotFoundException ex) {
            throw new Exception("Unable to open file '" + fileName + "'");
        } catch (IOException ex) {
            throw new Exception("Error reading file '" + fileName + "'");
        } catch (NumberFormatException e) {
            throw new Exception("Invalid number format of value: " + line);
        }
        return tempC;
    }
}
