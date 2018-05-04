package com.tuankhai.automaticsystem;

import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.github.nkzawa.socketio.client.IO;

import org.json.JSONException;
import org.json.JSONObject;

public class TestActivity extends AppCompatActivity implements View.OnClickListener {

    EditText edtTemp, edtHum;
    Button btnOK;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);
        setupActionBar();

        if (CusApplication.mSocket == null || CusApplication.mSocket.connected()) {
            try {
                CusApplication.mSocket = IO.socket(CusApplication.BASE_URL);
            } catch (Exception e) {
                e.printStackTrace();
            }
            CusApplication.mSocket.connect();
        }

        edtTemp = findViewById(R.id.edt_temp);
        edtHum = findViewById(R.id.edt_hum);
        btnOK = findViewById(R.id.btn_ok);

        btnOK.setOnClickListener(this);
    }

    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_ok:
                String temp = "0";
                String hum = "0";
                if (!edtTemp.getText().toString().equals("")) {
                    temp = edtTemp.getText().toString();
                }
                if (!edtHum.getText().toString().equals("")) {
                    hum = edtHum.getText().toString();
                }
                JSONObject object = new JSONObject();
                try {
                    object.put("value", temp + "-" + hum);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                CusApplication.mSocket.emit("clientsenddata_3", object);
                break;
        }
    }
}
