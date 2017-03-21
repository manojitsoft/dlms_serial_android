package com.dlms.meter.dlmspoc.services;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;


import com.dlms.meter.dlmspoc.interfaces.IDlmsDataReceiver;

import gurux.common.IGXMediaListener;
import gurux.common.MediaStateEventArgs;
import gurux.common.PropertyChangedEventArgs;
import gurux.common.ReceiveEventArgs;
import gurux.common.TraceEventArgs;
import gurux.dlms.GXCommunicate;
import gurux.dlms.GXDLMSClient;
import gurux.dlms.GXSerial;

/**
 * Created by Manojkumar on 3/19/2017.
 */

public class DLMSService extends Service implements IGXMediaListener {

    IBinder mBinder = new LocalBinder();

    GXCommunicate communicate = null;
    GXSerial serial;
    GXDLMSClient secureClient;
    String path = "ManufacturerSettings";
    String id = "";

    IDlmsDataReceiver dataReceiver;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onError(Object sender, RuntimeException ex) {

    }

    @Override
    public void onReceived(Object sender, ReceiveEventArgs e) {
        dataReceiver.onDataReceived(sender, e);
    }

    @Override
    public void onMediaStateChange(Object sender, MediaStateEventArgs e) {

    }

    @Override
    public void onTrace(Object sender, TraceEventArgs e) {

    }

    @Override
    public void onPropertyChanged(Object sender, PropertyChangedEventArgs e) {

    }

    public void setDlmsDataReceiver(IDlmsDataReceiver rec) {
        dataReceiver = rec;
    }

    public class LocalBinder extends Binder {
        public DLMSService getServerInstance() {
            serial = new GXSerial(getApplicationContext());
            serial.addListener(DLMSService.this);
            secureClient = new GXDLMSClient();
            try {
                communicate = new GXCommunicate(5000, secureClient, serial);
            } catch (Exception e) {
            }
            return DLMSService.this;
        }
    }

    public GXCommunicate getGxCommunicate() {
        return this.communicate;
    }
}
