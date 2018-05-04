package com.tuankhai.automaticsystem;

/**
 * Created by tuank on 30/04/2018.
 */

public class Model {
    String temp;
    String hum;

    public Model(String temp, String hum) {
        this.temp = temp;
        this.hum = hum;
    }

    public String getTemp() {
        return temp;
    }

    public void setTemp(String temp) {
        this.temp = temp;
    }

    public String getHum() {
        return hum;
    }

    public void setHum(String hum) {
        this.hum = hum;
    }
}
