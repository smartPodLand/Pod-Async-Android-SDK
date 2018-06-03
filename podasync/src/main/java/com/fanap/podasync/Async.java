package com.fanap.podasync;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;

import com.fanap.podasync.model.AsyncConstant;
import com.fanap.podasync.model.AsyncMessageType;
import com.fanap.podasync.model.ClientMessage;
import com.fanap.podasync.model.Device;
import com.fanap.podasync.model.DeviceResult;
import com.fanap.podasync.model.Message;
import com.fanap.podasync.model.MessageWrapperVo;
import com.fanap.podasync.model.PeerInfo;
import com.fanap.podasync.model.RegistrationRequest;
import com.fanap.podasync.networking.RetrofitHelper;
import com.fanap.podasync.networking.api.TokenApi;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketExtension;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;
import com.neovisionaries.ws.client.WebSocketState;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import retrofit2.Response;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

import static com.neovisionaries.ws.client.WebSocketState.OPEN;

/**
 * By default WebSocketFactory uses for non-secure WebSocket connections (ws:)
 * and for secure WebSocket connections (wss:).
 */

/**
 * Send the request from SSO host to get the device Id
 * deviceIdRequest(websocket,peerInfo);
 */
public class Async extends WebSocketAdapter {

    private WebSocket webSocket;
    private static final int TIMEOUT = 5000;
    private static final int THRESHOLD = 20000;
    private static final int SOCKET_CLOSE_TIMEOUT = 110000;
    private WebSocket webSocketReconnect;
    private static final String TAG = "Async" + " ";
    private static Async instance;
    private boolean isServerRegister;
    private boolean isDeviceRegister;
    private static SharedPreferences sharedPrefs;
    private MessageWrapperVo messageWrapperVo;
    private static AsyncListenerManager listenerManager;
    private static Moshi moshi;
    private String errorMessage;
    private long lastSendMessageTime;
    private long lastReceiveMessageTime;
    private String message;
    private String onError;
    private String state;
    private String appId;
    private String peerId;
    private String deviceID;
    private Exception onConnectException;
    private MutableLiveData<String> stateLiveData = new MutableLiveData<>();
    private MutableLiveData<String> messageLiveData = new MutableLiveData<>();
    private String serverAddress;
    private final Handler pingHandler = new Handler(Looper.getMainLooper());
    private int getMessageCalled;
    private String token;
    private String serverName;

    public Async() {
        //Empty constructor

    }

    /**
     * Because user doesn't have device Id we set UIID for device Id
     */
    public static Async getInstance(Context context) {
        if (instance == null) {
            sharedPrefs = context.getSharedPreferences(AsyncConstant.Constants.PREFERENCE, Context.MODE_PRIVATE);
            moshi = new Moshi.Builder().build();
            instance = new Async();
            listenerManager = new AsyncListenerManager();
        }
        return instance;
    }

    /**
     * @param textMessage that received when socket send message to Async
     */

    @Override
    public void onTextMessage(WebSocket websocket, String textMessage) throws Exception {
        super.onTextMessage(websocket, textMessage);
        int type = 0;
        lastReceiveMessageTime = new Date().getTime();

        JsonAdapter<ClientMessage> jsonAdapter = moshi.adapter(ClientMessage.class);
        ClientMessage clientMessage = jsonAdapter.fromJson(textMessage);
        if (clientMessage != null) {
            type = clientMessage.getType();
        }

        scheduleSendPing(70000);
        @AsyncMessageType.MessageType int currentMessageType = type;
        switch (currentMessageType) {
            case AsyncMessageType.MessageType.ACK:
                handleOnAck(clientMessage);
                break;
            case AsyncMessageType.MessageType.DEVICE_REGISTER:
                handleOnDeviceRegister(websocket, clientMessage);
                break;
            case AsyncMessageType.MessageType.ERROR_MESSAGE:
                handleOnErrorMessage(clientMessage);
                break;
            case AsyncMessageType.MessageType.MESSAGE_ACK_NEEDED:

                handleOnMessageAckNeeded(websocket, clientMessage);
                break;
            case AsyncMessageType.MessageType.MESSAGE_SENDER_ACK_NEEDED:
                handleOnMessageAckNeeded(websocket, clientMessage);
                break;
            case AsyncMessageType.MessageType.MESSAGE:
                handleOnMessage(clientMessage);
                break;
            case AsyncMessageType.MessageType.PEER_REMOVED:
                break;
            case AsyncMessageType.MessageType.PING:
                handleOnPing(websocket, clientMessage);
                break;
            case AsyncMessageType.MessageType.SERVER_REGISTER:
                handleOnServerRegister(textMessage);
                break;
        }
    }


    @Override
    public void onStateChanged(WebSocket websocket, WebSocketState newState) throws Exception {
        super.onStateChanged(websocket, newState);
        stateLiveData.postValue(newState.toString());
        setState(newState.toString());
        Log.i("onStateChanged", newState.toString());
    }

    @Override
    public void onError(WebSocket websocket, WebSocketException cause) throws Exception {
        super.onError(websocket, cause);
        Log.e("onError", cause.toString());
        cause.getCause().printStackTrace();
        setOnError(cause.toString());
    }

    @Override
    public void onConnectError(WebSocket websocket, WebSocketException exception) throws Exception {
        super.onConnectError(websocket, exception);
        Log.e("onConnected", exception.toString());
        setonConnectError(exception);
    }

    @Override
    public void onConnected(WebSocket websocket, Map<String, List<String>> headers) throws Exception {
        super.onConnected(websocket, headers);
        Log.i("onConnected", headers.toString());
    }

    /**
     * After error event its start reconnecting again.
     * Note that you should not trigger reconnection in onError() method because onError()
     * may be called multiple times due to one error.
     * Instead, onDisconnected() is the right place to trigger reconnection.
     */
    @Override
    public void onMessageError(WebSocket websocket, WebSocketException cause, List<WebSocketFrame> frames) throws Exception {
        super.onMessageError(websocket, cause, frames);
        Log.e("onMessageError", cause.toString());
    }

    @Override
    public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame, boolean closedByServer) throws Exception {
        super.onDisconnected(websocket, serverCloseFrame, clientCloseFrame, closedByServer);
        Handler handler = new Handler();
        handler.postDelayed(() -> {
            try {
                reConnect();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (WebSocketException e) {
                e.printStackTrace();
            }
        }, 3000);
    }

    @Override
    public void onCloseFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        super.onCloseFrame(websocket, frame);
        Log.e("onCloseFrame", frame.getCloseReason());
        reConnect();
    }

    /**
     * Connect webSocket to the Async
     *
     * @Param socketServerAddress
     * @Param appId
     */


    private void handleOnAck(ClientMessage clientMessage) throws IOException {
        setMessage(clientMessage.getContent());
        listenerManager.callOnTextMessage(clientMessage.getContent());
    }

    private void handleOnDeviceRegister(WebSocket websocket, ClientMessage clientMessage) {
        isDeviceRegister = true;
        if (!peerIdExistence()) {
            String peerId = clientMessage.getContent();
            savePeerId(peerId);
        }
        /**
         When socket closes by any reason
         , server is still registered and we sent a lot of message but
         they are still in the queue
         */
        //TODO handle queue message
        if (isServerRegister && peerId.equals(getPeerId())) {
            if (websocket.getState() == OPEN) {
                if (websocket.getFrameQueueSize() > 0) {

                }
            }

        } else {
            /*
              Register server when its not registered
              */
            serverRegister(websocket);
        }
    }

    private void serverRegister(WebSocket websocket) {
        RegistrationRequest registrationRequest = new RegistrationRequest();
        registrationRequest.setName(getServerName());
        JsonAdapter<RegistrationRequest> jsonRegistrationRequestVoAdapter = moshi.adapter(RegistrationRequest.class);
        String jsonRegistrationRequestVo = jsonRegistrationRequestVoAdapter.toJson(registrationRequest);
        String jsonMessageWrapperVo = getMessageWrapper(moshi, jsonRegistrationRequestVo, AsyncMessageType.MessageType.SERVER_REGISTER);
        sendData(websocket, jsonMessageWrapperVo);
    }

    private void sendData(WebSocket websocket, String jsonMessageWrapperVo) {
        Log.i("Ping", "Sent at" + lastSendMessageTime);
        lastSendMessageTime = new Date().getTime();
        websocket.sendText(jsonMessageWrapperVo);
    }

    private void handleOnErrorMessage(ClientMessage clientMessage) {
        Log.e(TAG + "OnErrorMessage", clientMessage.getContent());
        setErrorMessage(clientMessage.getContent());
    }

    private void handleOnMessage(ClientMessage clientMessage) throws IOException {
        setMessage(clientMessage.getContent());
        messageLiveData.postValue(clientMessage.getContent());
        listenerManager.callOnTextMessage(clientMessage.getContent());
    }

    private void handleOnPing(WebSocket websocket, ClientMessage clientMessage) {

        if (clientMessage.getContent() != null || !clientMessage.getContent().equals("")) {
            if (getDeviceId() == null) {
                deviceIdRequest();
            } else {
                deviceRegister(webSocket);
            }
        } else {
            Log.i("This a empty ping", String.valueOf(new Date().getTime()));
        }
    }

    private void handleOnServerRegister(String textMessage) {
        Log.i("Ready for chat", textMessage);
        isServerRegister = true;
    }

    private void handleOnMessageAckNeeded(WebSocket websocket, ClientMessage clientMessage) throws IOException {
        handleOnMessage(clientMessage);

        Message messageSenderAckNeeded = new Message();
        messageSenderAckNeeded.setMessageId(clientMessage.getSenderMessageId());

        JsonAdapter<Message> jsonSenderAckNeededAdapter = moshi.adapter(Message.class);
        String jsonSenderAckNeeded = jsonSenderAckNeededAdapter.toJson(messageSenderAckNeeded);
        String jsonSenderAckNeededWrapper = getMessageWrapper(moshi, jsonSenderAckNeeded, AsyncMessageType.MessageType.ACK);
        sendData(websocket, jsonSenderAckNeededWrapper);
        lastSendMessageTime = new Date().getTime();
    }

    private void deviceRegister(WebSocket websocket) {
        PeerInfo peerInfo = new PeerInfo();
        if (getPeerId() != null) {
            peerInfo.setRefresh(true);
        } else {
            peerInfo.setRenew(true);
        }
        peerInfo.setAppId(getAppId());
        peerInfo.setDeviceId(getDeviceId());

        JsonAdapter<PeerInfo> jsonPeerMessageAdapter = moshi.adapter(PeerInfo.class);
        String peerMessageJson = jsonPeerMessageAdapter.toJson(peerInfo);
        String jsonPeerInfoWrapper = getMessageWrapper(moshi, peerMessageJson, AsyncMessageType.MessageType.DEVICE_REGISTER);
        sendData(websocket, jsonPeerInfoWrapper);
        lastSendMessageTime = new Date().getTime();
    }

    private void deviceIdRequest() {
        RetrofitHelper retrofitHelper = new RetrofitHelper("http://172.16.110.76");
        TokenApi tokenApi = retrofitHelper.getService(TokenApi.class);
        rx.Observable<Response<DeviceResult>> listObservable = tokenApi.getDeviceId("Bearer" + " " + getToken());
        listObservable.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(deviceResults -> {
            if (deviceResults.isSuccessful()) {
                ArrayList<Device> devices = deviceResults.body().getDevices();
                for (Device device : devices) {
                    if (device.isCurrent()) {
                        saveDeviceId(device.getUid());
                        deviceRegister(webSocket);
                        return;
                    }
                }
            }
        }, throwable -> Log.i("Error get devices", throwable.toString()));
    }


    public void connect(String socketServerAddress, final String appId, String serverName, String token) {
        WebSocketFactory webSocketFactory = new WebSocketFactory();
        webSocketFactory.setVerifyHostname(false);
        setAppId(appId);
        setServerAddress(socketServerAddress);
        setToken(token);
        setServerName(serverName);
        try {
            webSocket = webSocketFactory
                    .createSocket(socketServerAddress)
                    .addListener(this);

            webSocket.setMaxPayloadSize(100);
            webSocket.addExtension(WebSocketExtension.PERMESSAGE_DEFLATE);
            webSocket.connectAsynchronously();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @NonNull
    private String getMessageWrapper(Moshi moshi, String json, int messageType) {
        messageWrapperVo = new MessageWrapperVo();
        messageWrapperVo.setContent(json);
        messageWrapperVo.setType(messageType);
        JsonAdapter<MessageWrapperVo> jsonMessageWrapperVoAdapter = moshi.adapter(MessageWrapperVo.class);
        return jsonMessageWrapperVoAdapter.toJson(messageWrapperVo);
    }

    /**
     * @Param textContent
     * @Param messageType it could be 3, 4, 5
     * @Param []receiversId the Id's that we want to send
     */
    public void sendMessage(String textContent, int messageType, long[] receiversId) {
        Message message = new Message();
        message.setContent(textContent);
        message.setReceivers(receiversId);
        JsonAdapter<Message> jsonAdapter = moshi.adapter(Message.class);
        String jsonMessage = jsonAdapter.toJson(message);
        String wrapperJsonString = getMessageWrapper(moshi, jsonMessage, messageType);
        sendData(webSocket, wrapperJsonString);
        lastSendMessageTime = new Date().getTime();
    }

    public void sendMessage(String textContent, int messageType) {
        long ttl = new Date().getTime();
        Message message = new Message();
        message.setContent(textContent);
        message.setPriority(1);
        message.setPeerName(getServerName());
        message.setTtl(ttl);

        String json = JsonUtil.getJson(message);

        messageWrapperVo = new MessageWrapperVo();
        messageWrapperVo.setContent(json);
        messageWrapperVo.setType(messageType);

        String json1 = JsonUtil.getJson(messageWrapperVo);

        sendData(webSocket, json1);
    }

    public void sendMessage(String textContent, long[] receiversId) {
        Message message = new Message();
        message.setContent(textContent);
        message.setReceivers(receiversId);
        JsonAdapter<Message> jsonAdapter = moshi.adapter(Message.class);
        String jsonMessage = jsonAdapter.toJson(message);
        String wrapperJsonString = getMessageWrapper(moshi, jsonMessage, AsyncMessageType.MessageType.MESSAGE);
        sendData(webSocket, wrapperJsonString);
        lastSendMessageTime = new Date().getTime();
    }

    public void closeSocket() {
        webSocket.sendClose();
    }

    public LiveData<String> getStateLiveData() {
        return stateLiveData;
    }

    public LiveData<String> getMessageLiveData() {
        return messageLiveData;
    }

    /**
     * If peerIdExistence we set {@param refresh = true} to the
     * Async else we set {@param renew = true}  to the Async to
     * get the new PeerId
     */
    private void reConnect() throws IOException, WebSocketException {
        WebSocketFactory webSocketFactory = new WebSocketFactory();
        webSocketFactory.setVerifyHostname(false);
        try {
            webSocketReconnect = webSocketFactory
                    .createSocket(getServerAddress())
                    .addListener(this);
            webSocketReconnect.connectAsynchronously();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Remove the peerId and send ping again but this time
     * peerId that was set in the server was removed
     */
    public void logOut() {
        removePeerId(AsyncConstant.Constants.PEER_ID, null);
        isServerRegister = false;
        isDeviceRegister = false;
        webSocket.sendClose();
    }

    private void removePeerId(String peerId, String o) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(peerId, o);
        editor.apply();
    }

    private void ping() {
        message = getMessageWrapper(moshi, "", AsyncMessageType.MessageType.PING);
        sendData(webSocket, message);
        lastSendMessageTime = new Date().getTime();
        ScheduleCloseSocket();
    }

    private void ScheduleCloseSocket() {
        Handler handler = new Handler();
        handler.postDelayed(() -> {
            if (lastSendMessageTime - lastReceiveMessageTime >= SOCKET_CLOSE_TIMEOUT) {
                closeSocket();
            }
        }, SOCKET_CLOSE_TIMEOUT);
    }

    /*After a delay Time it calls the method in the Run*/
    private void scheduleSendPing(int delayTime) {
        Runnable runnable;
        runnable = this::sendPing;
        pingHandler.postDelayed(runnable, delayTime);
    }

    /**
     * When its send message the lastSendMessageTime gets updated.
     * if the {@param currentTime} - {@param lastSendMessageTime} was bigger than 10 second
     * it means we need to send ping to keep socket alive.
     * we don't need to set ping interval because its send ping automatically by itself
     * with the {@param type}type that not 0.
     * We set {@param type = 0} with empty content.
     */
    private void sendPing() {
        lastSendMessageTime = new Date().getTime();
        long currentTime = new Date().getTime();
        if (currentTime - lastReceiveMessageTime >= 70000) {
            ping();
        }
    }

    private void stopPing() {
        webSocket.sendClose();
        pingHandler.removeCallbacksAndMessages(null);
    }

    private boolean peerIdExistence() {
        boolean isPeerIdExistence;
        String peerId = sharedPrefs.getString(AsyncConstant.Constants.PEER_ID, null);
        setPeerId(peerId);
        isPeerIdExistence = peerId != null;
        return isPeerIdExistence;
    }

    //Save peerId in the SharedPreferences

    private void savePeerId(String peerId) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(AsyncConstant.Constants.PEER_ID, peerId);
        editor.apply();
    }

    //  it's (relatively) easily resettable because it only persists as long as the app is installed.
    private static synchronized String getUniqueDeviceID() {
        String uniqueID = UUID.randomUUID().toString();
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(AsyncConstant.Constants.DEVICE_ID, uniqueID);
        editor.apply();
        return uniqueID;
    }

    //Save deviceId in the SharedPreferences
    private static void saveDeviceId(String deviceId) {
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(AsyncConstant.Constants.DEVICE_ID, deviceId);
        editor.apply();
    }

    private void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getServerName() {
        return serverName;
    }

    private String getDeviceId() {
        return sharedPrefs.getString(AsyncConstant.Constants.DEVICE_ID, null);
    }

    public void setDeviceID(String deviceID) {
        this.deviceID = deviceID;
    }

    public String getPeerId() {
        return sharedPrefs.getString(AsyncConstant.Constants.PEER_ID, null);
    }

    private void setPeerId(String peerId) {
        this.peerId = peerId;
    }

    private String getAppId() {
        return appId;
    }

    private void setAppId(String appId) {
        this.appId = appId;
    }

    public String getState() {
        return state;
    }

    private void setState(String state) {
        this.state = state;
    }

    public int getMessageCalled() {
        return getMessageCalled;
    }

    private void setMessageCalled(int getMessageCalled) {
        this.getMessageCalled = getMessageCalled;
    }

    private void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    private void setMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    private String getServerAddress() {
        return serverAddress;
    }

    private void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    private void setOnError(String onError) {
        this.onError = onError;
    }

    public String getOnError() {
        return onError;
    }

    private void setonConnectError(Exception exception) {
        this.onConnectException = exception;
    }

    public Exception getOnConnectError() {
        return onConnectException;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    /**
     * Add a listener to receive events on this Async.
     *
     * @param listener A listener to add.
     * @return {@code this} object.
     */
    public Async addListener(AsyncListener listener) {
        listenerManager.addListener(listener);

        return this;
    }

    public Async addListeners(List<AsyncListener> listeners) {
        listenerManager.addListeners(listeners);

        return this;
    }

    public Async removeListener(AsyncListener listener) {
        listenerManager.removeListener(listener);

        return this;
    }

    /**
     * Get the manager that manages registered listeners.
     */
    AsyncListenerManager getListenerManager() {
        return listenerManager;
    }
}

