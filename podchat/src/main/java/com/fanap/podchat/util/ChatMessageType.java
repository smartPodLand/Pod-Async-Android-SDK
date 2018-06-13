package com.fanap.podchat.util;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class ChatMessageType {

    private final int messageType;

    public ChatMessageType(@Constants int messageType) {
        this.messageType = messageType;
    }


    @IntDef({Constants.INVITATION, Constants.MESSAGE,
            Constants.SENT,
            Constants.DELIVERY,
            Constants.SEEN,
            Constants.PING,
            Constants.BLOCK,
            Constants.UNBLOCK,
            Constants.LEAVE_THREAD,
            Constants.RENAME,
            Constants.ADD_PARTICIPANT,
            Constants.GET_STATUS,
            Constants.GET_CONTACTS,
            Constants.GET_THREADS,
            Constants.GET_HISTORY,
            Constants.LAST_SEEN_TYPE,
            Constants.REMOVE_PARTICIPANT,
            Constants.MUTE_THREAD,
            Constants.CHANGE_TYPE,
            Constants.UN_MUTE_THREAD,
            Constants.UPDATE_METADATA,
            Constants.FORWARD_MESSAGE,
            Constants.USER_INFO,
            Constants.USER_STATUS,
            Constants.USER_S_STATUS,
            Constants.RELATION_INFO,
            Constants.THREAD_PARTICIPANTS,
            Constants.EDIT_MESSAGE,
            Constants.DELETE_MESSAGE,
            Constants.ERROR,

    })

    @Retention(RetentionPolicy.SOURCE)
    public @interface Constants {
        int INVITATION = 1;
        int MESSAGE = 2;
        int SENT = 3;
        int DELIVERY = 4;
        int SEEN = 5;
        int PING = 6;
        int BLOCK = 7;
        int UNBLOCK = 8;
        int LEAVE_THREAD = 9;
        int RENAME = 10;
        int ADD_PARTICIPANT = 11;
        int GET_STATUS = 12;
        int GET_CONTACTS = 13;
        int GET_THREADS = 14;
        int GET_HISTORY = 15;
        int CHANGE_TYPE = 16;
        int LAST_SEEN_TYPE = 17;
        int REMOVE_PARTICIPANT = 18;
        int MUTE_THREAD = 19;
        int UN_MUTE_THREAD = 20;
        int UPDATE_METADATA = 21;
        int FORWARD_MESSAGE = 22;
        int USER_INFO = 23;
        int USER_STATUS = 24;
        int USER_S_STATUS = 25;
        int RELATION_INFO = 26;
        int THREAD_PARTICIPANTS = 27;
        int EDIT_MESSAGE = 28;
        int DELETE_MESSAGE = 29;
        int ERROR = 999;
    }
}