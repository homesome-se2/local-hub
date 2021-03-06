package models.automations;

import java.util.concurrent.TimeUnit;

public class Delay {
    private int hours;
    private int minutes;
    private int seconds;

    public Delay(int hours, int minutes, int seconds) {
        this.hours = hours;
        this.minutes = minutes;
        this.seconds = seconds;
    }

    public int getHours() {
        return hours;
    }

    public void setHours(int hours) {
        this.hours = hours;
    }

    public int getMinutes() {
        return minutes;
    }

    public void setMinutes(int minutes) {
        this.minutes = minutes;
    }

    public int getSeconds() {
        return seconds;
    }

    public void setSeconds(int seconds) {
        this.seconds = seconds;
    }

    public long timeInMills(){
        return TimeUnit.HOURS.toMillis(this.hours) + TimeUnit.MINUTES.toMillis(this.minutes) + TimeUnit.SECONDS.toMillis(this.seconds);
    }

}
