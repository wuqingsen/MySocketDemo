package com.example.qd.mysocketdemo;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

/**
 * demo圆形”：https://github.com/nkzawa/socket.io-android-chat
 * 另一个：https://github.com/vilyever/AndroidSocketClient
 */

public class MainActivity extends AppCompatActivity {
    @BindView(R.id.recyclerView)
    RecyclerView recyclerView;//消息列表
    @BindView(R.id.ib_send)
    ImageButton ib_send;//发消息
    @BindView(R.id.et_context)
    EditText et_context;//内容输入款
    @BindView(R.id.btn_back)
    Button btn_back;//退出登陆
    private Socket mSocket;
    private RecyclerView.Adapter mAdapter;
    private Handler mTypingHandler = new Handler();
    private List<Message> messageList = new ArrayList<>();//消息列表
    private static final int TYPING_TIMER_LENGTH = 600;//打字时间长度
    private String mUsername;//用户名
    /**
     * mTyping是否正在输入：true正在输入；false未在输入
     */
    private boolean mTyping = false;

    /**
     * isConnected是否断开连接：true断开；false未断开
     */
    private Boolean isConnected = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        mUsername = getIntent().getStringExtra("username");
        int numUsers = Integer.valueOf(getIntent().getStringExtra("numUsers"));

        setAdapter();

        addLog(getResources().getString(R.string.message_welcome));
        addParticipantsLog(numUsers);

        mSocket = MyApplication.getSocket();
        mSocket.on(Socket.EVENT_CONNECT, onConnect);
        mSocket.on(Socket.EVENT_DISCONNECT, onDisconnect);
        mSocket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
        mSocket.on(Socket.EVENT_CONNECT_TIMEOUT, onConnectError);
        mSocket.on("new message", onNewMessage);
        mSocket.on("user joined", onUserJoined);
        mSocket.on("user left", onUserLeft);
        mSocket.on("typing", onTyping);
        mSocket.on("stop typing", onStopTyping);
        mSocket.connect();
        setListener();
    }

    private void setAdapter() {
        mAdapter = new MessageAdapter(MainActivity.this, messageList);
        recyclerView.setLayoutManager(new LinearLayoutManager(MainActivity.this));
        recyclerView.setAdapter(mAdapter);
    }

    private void setListener() {
        //输入框监听
        et_context.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (null == mUsername) return;
                if (!mSocket.connected()) return;

                if (!mTyping) {
                    mTyping = true;
                    mSocket.emit("typing");
                }

                mTypingHandler.removeCallbacks(onTypingTimeout);
                mTypingHandler.postDelayed(onTypingTimeout, TYPING_TIMER_LENGTH);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        //发送
        ib_send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendMessage();
            }
        });
        //退出登陆
        btn_back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                logOut();
            }
        });
    }

//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        mSocket.disconnect();
//
//        mSocket.off(Socket.EVENT_CONNECT, onConnect);//服务器连接成功
//        mSocket.off(Socket.EVENT_DISCONNECT, onDisconnect);//服务器断开连接
//        mSocket.off(Socket.EVENT_CONNECT_ERROR, onConnectError);//服务器连接错误
//        mSocket.off(Socket.EVENT_CONNECT_TIMEOUT, onConnectError);//服务器连接错误（超时）
//        mSocket.off("new message", onNewMessage);//一条新消息
//        mSocket.off("user joined", onUserJoined);//用户加入
//        mSocket.off("user left", onUserLeft);//有用户退出
//        mSocket.off("typing", onTyping);//正在输入
//        mSocket.off("stop typing", onStopTyping);//没有在输入
//
//        mSocket.off("login", onLogin);
//    }

    private void addLog(String message) {
        messageList.add(new Message.Builder(Message.TYPE_LOG)
                .message(message).build());
        mAdapter.notifyItemInserted(messageList.size() - 1);
        scrollToBottom();
    }

    private void addParticipantsLog(int numUsers) {
        //总人数
        addLog(getResources().getQuantityString(R.plurals.message_participants, numUsers, numUsers));
    }

    private void addMessage(String username, String message) {
        messageList.add(new Message.Builder(Message.TYPE_MESSAGE)
                .username(username).message(message).build());
        mAdapter.notifyItemInserted(messageList.size() - 1);
        scrollToBottom();
    }

    private void addTyping(String username) {
        messageList.add(new Message.Builder(Message.TYPE_ACTION)
                .username(username).build());
        mAdapter.notifyItemInserted(messageList.size() - 1);
        scrollToBottom();
    }

    private void removeTyping(String username) {
        for (int i = messageList.size() - 1; i >= 0; i--) {
            Message message = messageList.get(i);
            if (message.getType() == Message.TYPE_ACTION && message.getUsername().equals(username)) {
                messageList.remove(i);
                mAdapter.notifyItemRemoved(i);
            }
        }
    }

    /**
     * 发送消息
     */
    private void sendMessage() {
        if (null == mUsername) return;
        if (!mSocket.connected()) return;

        mTyping = false;

        String message = et_context.getText().toString().trim();
        if (TextUtils.isEmpty(message)) {
            et_context.requestFocus();
            return;
        }

        et_context.setText("");
        addMessage(mUsername, message);

        // perform the sending message attempt.
        mSocket.emit("new message", message);
    }

    private void scrollToBottom() {
        recyclerView.scrollToPosition(mAdapter.getItemCount() - 1);
    }

    /**
     * 连接服务器成功
     */
    private Emitter.Listener onConnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.e("=====", "重新连接服务器成功");
                    if (!isConnected) {
                        if (null != mUsername)
                            mSocket.emit("add user", mUsername);
                        Toast.makeText(getApplicationContext(),
                                "重新连接服务器成功", Toast.LENGTH_LONG).show();
                        isConnected = true;
                    }
                }
            });
        }
    };

    /**
     * 与服务器断开连接
     */
    private Emitter.Listener onDisconnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.e("=====", "与服务器断开连接");
                    isConnected = false;
                    Toast.makeText(getApplicationContext(),
                            "与服务器断开连接", Toast.LENGTH_LONG).show();
                }
            });
        }
    };

    /**
     * 服务器连接错误
     */
    private Emitter.Listener onConnectError = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.e("=====", "服务器连接错误");
                    Toast.makeText(getApplicationContext(),
                            "服务器连接错误", Toast.LENGTH_LONG).show();
                }
            });
        }
    };

    /**
     * 有新消息
     */
    private Emitter.Listener onNewMessage = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.e("=====", "有一条新消息");
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    String message;
                    try {
                        username = data.getString("username");
                        message = data.getString("message");
                    } catch (JSONException e) {
                        Log.e("=====", e.getMessage());
                        return;
                    }

                    removeTyping(username);
                    addMessage(username, message);
                }
            });
        }
    };

    /**
     * 有用户加入
     */
    private Emitter.Listener onUserJoined = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.e("=====", "有用户加入");
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    int numUsers;
                    try {
                        username = data.getString("username");
                        numUsers = data.getInt("numUsers");
                    } catch (JSONException e) {
                        Log.e("=====", e.getMessage());
                        return;
                    }

                    addLog(getResources().getString(R.string.message_user_joined, username));
                    addParticipantsLog(numUsers);
                }
            });
        }
    };

    /**
     * 有用户退出
     */
    private Emitter.Listener onUserLeft = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.e("=====", "有用户退出");
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    int numUsers;
                    try {
                        username = data.getString("username");
                        numUsers = data.getInt("numUsers");
                    } catch (JSONException e) {
                        Log.e("=====", e.getMessage());
                        return;
                    }

                    addLog(getResources().getString(R.string.message_user_left, username));
                    addParticipantsLog(numUsers);
                    removeTyping(username);
                }
            });
        }
    };

    /**
     * 正在输入
     */
    private Emitter.Listener onTyping = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.e("=====", "正在输入");
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    try {
                        username = data.getString("username");
                    } catch (JSONException e) {
                        Log.e("=====", e.getMessage());
                        return;
                    }
                    addTyping(username);
                }
            });
        }
    };

    /**
     * 停止输入
     */
    private Emitter.Listener onStopTyping = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.e("=====", "停止输入");
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    try {
                        username = data.getString("username");
                    } catch (JSONException e) {
                        Log.e("=====", e.getMessage());
                        return;
                    }
                    removeTyping(username);
                }
            });
        }
    };

    private Runnable onTypingTimeout = new Runnable() {
        @Override
        public void run() {
            if (!mTyping) return;

            mTyping = false;
            mSocket.emit("stop typing");
        }
    };

    /**
     * 退出登陆
     */
    private void logOut() {
        mUsername = null;
        mSocket.disconnect();
        mSocket.connect();
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }
}
