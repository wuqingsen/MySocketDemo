package com.example.qd.mysocketdemo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

import org.json.JSONException;
import org.json.JSONObject;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;


/**
 * A login screen that offers login via username.
 */
public class LoginActivity extends Activity {
    @BindView(R.id.btn_login)
    Button btn_login;
    @BindView(R.id.et_name)
    EditText et_name;

    private String mUsername;

    private Socket mSocket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        ButterKnife.bind(this);

        mSocket = MyApplication.getSocket();
        mSocket.connect();

        btn_login.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                loginSocket();
            }
        });

        mSocket.on("login", onLogin);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mSocket.off("login", onLogin);
    }

    /**
     * 登陆
     */
    private void loginSocket() {
        if (et_name.getText().toString().trim().equals("")) return;
        mUsername = et_name.getText().toString().trim();
        //尝试登陆
        mSocket.emit("add user", mUsername);
    }

    private Emitter.Listener onLogin = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            JSONObject data = (JSONObject) args[0];

            int numUsers;
            try {
                numUsers = data.getInt("numUsers");
            } catch (JSONException e) {
                return;
            }

            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.putExtra("username", mUsername + "");
            intent.putExtra("numUsers", numUsers + "");
            startActivity(intent);
            finish();
        }
    };
}



