package com.dlms.meter.dlmspoc.interfaces;

import gurux.common.ReceiveEventArgs;

/**
 * Created by Manojkumar on 3/21/2017.
 */

public interface IDlmsDataReceiver {
    public void onDataReceived(Object sender, ReceiveEventArgs e);
}
