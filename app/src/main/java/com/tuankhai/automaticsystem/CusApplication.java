package com.tuankhai.automaticsystem;

import android.app.Application;

import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import java.util.ArrayList;

/**
 * Created by tuank on 30/04/2018.
 */

public class CusApplication extends Application {
    public static final String BASE_URL = "https://automaticsystem.herokuapp.com";
    public static ArrayList<Model> arrModel = new ArrayList<>();
    public static Socket mSocket;
}
