//
// --------------------------------------------------------------------------
//  Gurux Ltd
// 
//
//
// Filename:        $HeadURL$
//
// Version:         $Revision$,
//                  $Date$
//                  $Author$
//
// Copyright (c) Gurux Ltd
//
//---------------------------------------------------------------------------
//
//  DESCRIPTION
//
// This file is a part of Gurux Device Framework.
//
// Gurux Device Framework is Open Source software; you can redistribute it
// and/or modify it under the terms of the GNU General Public License 
// as published by the Free Software Foundation; version 2 of the License.
// Gurux Device Framework is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of 
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
// See the GNU General Public License for more details.
//
// More information of Gurux products: http://www.gurux.org
//
// This code is licensed under the GNU General Public License v2. 
// Full text may be retrieved at http://www.gnu.org/licenses/gpl-2.0.txt
//---------------------------------------------------------------------------

package gurux.dlms;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import gurux.common.GXSync;
import gurux.common.GXSynchronousMediaBase;
import gurux.common.IGXMedia;
import gurux.common.IGXMediaListener;
import gurux.common.PropertyChangedEventArgs;
import gurux.common.ReceiveEventArgs;
import gurux.common.ReceiveParameters;
import gurux.common.TraceEventArgs;
import gurux.common.enums.TraceLevel;
import gurux.common.enums.TraceTypes;
import gurux.dlms.enums.AvailableMediaSettings;
import gurux.io.BaudRate;
import gurux.io.Parity;
import gurux.io.StopBits;

import static android.content.ContentValues.TAG;

/**
 * The GXSerial component determines methods that make the communication
 * possible using serial port connection.
 */
public class GXSerial implements IGXMedia, AutoCloseable {

    /**
     * Read buffer size.
     */
    static final int DEFUALT_READ_BUFFER_SIZE = 256;

    /**
     * Amount of default data bits.
     */
    static final int DEFAULT_DATA_BITS = 8;

    // Values are saved if port is not open and user try to set them.
    /**
     * Serial port baud rate.
     */
    private BaudRate baudRate = BaudRate.BAUD_RATE_9600;
    /**
     * Used data bits.
     */
    private int dataBits = DEFAULT_DATA_BITS;
    /**
     * Stop bits.
     */
    private StopBits stopBits = StopBits.ONE;
    /**
     * Used parity.
     */
    private Parity parity = Parity.NONE;

    /**
     * Closing handle for serial port library.
     */
    private long closing = 0;
    /**
     * Write timeout.
     */
    private int writeTimeout;
    /**
     * Read timeout.
     */
    private int readTimeout;
    /**
     * Is serial port initialized.
     */
    private static boolean initialized;
    /**
     * Read buffer size.
     */
    private int readBufferSize;
    /**
     * Receiver thread.
     */
    private GXReceiveThread receiver;
    /**
     * Handle to serial port.
     */
    private int hWnd;
    /**
     * Name of serial port.
     */
    private String portName;
    /**
     * Synchronously class.
     */
    private GXSynchronousMediaBase syncBase;
    /**
     * Amount of bytes sent.
     */
    private long bytesSend = 0;
    /**
     * Synchronous counter.
     */
    private int synchronous = 0;
    /**
     * Trace level.
     */
    private TraceLevel trace = TraceLevel.OFF;
    /**
     * End of packet.
     */
    private Object eop;
    /**
     * Configurable settings.
     */
    private int configurableSettings;
    /**
     * Media listeners.
     */
    private List<IGXMediaListener> mediaListeners =
            new ArrayList<IGXMediaListener>();

    private Context mCtx;

    private UsbManager mUsbManager;

    private UsbSerialPort sPort = null;

    private SerialInputOutputManager mSerialIoManager;

    private UsbSerialDriver driver;

    private UsbDeviceConnection connection;

    private UsbDevice device;

    private boolean isOpen = false;

    public final static int READ_TIMEOUT = 5000;

    private static final int MESSAGE_REFRESH = 101;
    private static final long REFRESH_TIMEOUT_MILLIS = 5000;

    /**
     * Constructor.
     */
    public GXSerial(Context ctx) {
        this.mCtx = ctx;
        initialize();
        readBufferSize = DEFUALT_READ_BUFFER_SIZE;
        syncBase = new GXSynchronousMediaBase(readBufferSize);
        setConfigurableSettings(AvailableMediaSettings.ALL.getValue());
    }

    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Toast.makeText(mCtx, "Stopping IO Manager !", Toast.LENGTH_LONG).show();
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (sPort != null) {
            Toast.makeText(mCtx, "Starting IO Manager !", Toast.LENGTH_LONG).show();
            mSerialIoManager = new SerialInputOutputManager(sPort, mListener);
        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }

    public UsbSerialPort getPort() {
        return this.sPort;
    }

    /**
     * Returns synchronous class used to communicate synchronously.
     * 
     * @return Synchronous class.
     */
    final GXSynchronousMediaBase getSyncBase() {
        return syncBase;
    }

    /**
     * Get handle for closing.
     * 
     * @return Handle for closing.
     */
    final long getClosing() {
        return closing;
    }

    /**
     * Set handle for closing.
     * 
     * @param value
     *            Handle for closing.
     */
    final void setClosing(final long value) {
        closing = value;
    }

    /**
     * Initialize Gurux serial port library.
     */
    void initialize() {
        if (!initialized) {
            mUsbManager = (UsbManager) mCtx.getSystemService(Context.USB_SERVICE);
            mHandler.sendEmptyMessage(MESSAGE_REFRESH);
        }
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_REFRESH:
                    refreshDeviceList();
                    mHandler.sendEmptyMessageDelayed(MESSAGE_REFRESH, REFRESH_TIMEOUT_MILLIS);
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }

    };

    @RequiresApi(api = Build.VERSION_CODES.CUPCAKE)
    private void refreshDeviceList() {

        new AsyncTask<Void, Void, List<UsbSerialPort>>() {
            @Override
            protected List<UsbSerialPort> doInBackground(Void... params) {
                SystemClock.sleep(1000);

                final List<UsbSerialDriver> drivers =
                        UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);

                final List<UsbSerialPort> result = new ArrayList<UsbSerialPort>();
                for (final UsbSerialDriver driver : drivers) {
                    final List<UsbSerialPort> ports = driver.getPorts();
                    result.addAll(ports);
                }

                return result;
            }

            @Override
            protected void onPostExecute(List<UsbSerialPort> result) {
                sPort = !result.isEmpty() ? result.get(0) : null;
                if (sPort != null) {
                    Toast.makeText(mCtx, "Found a port and connection established !", Toast.LENGTH_LONG).show();
                    driver = sPort.getDriver();
                    device = driver.getDevice();
                    connection = mUsbManager.openDevice(sPort.getDriver().getDevice());
                    onDeviceStateChange();
                }
            }

        }.execute((Void) null);
    }

    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {

                @Override
                public void onRunError(Exception e) {
                    Toast.makeText(mCtx, "Meter threw error response !", Toast.LENGTH_LONG).show();
                }

                @Override
                public void onNewData(final byte[] data) {
                    Toast.makeText(mCtx, "Data arrived from meter successfully !", Toast.LENGTH_LONG).show();
                    receiver.setDataToRead(data);
                    receiver.start();
                }
            };

    @Override
    protected final void finalize() throws Throwable {
        super.finalize();
        if (isOpen()) {
            close();
        }
    }

    @Override
    public final TraceLevel getTrace() {
        return trace;
    }

    @Override
    public final void setTrace(final TraceLevel value) {
        trace = value;
        syncBase.setTrace(value);
    }

    /**
     * Notify that property has changed.
     * 
     * @param info
     *            Name of changed property.
     */
    private void notifyPropertyChanged(final String info) {
        for (IGXMediaListener listener : mediaListeners) {
            listener.onPropertyChanged(this,
                    new PropertyChangedEventArgs(info));
        }
    }

    /**
     * Notify clients from error occurred.
     * 
     * @param ex
     *            Occurred error.
     */
    final void notifyError(final RuntimeException ex) {
        for (IGXMediaListener listener : mediaListeners) {
            listener.onError(this, ex);
            if (trace.ordinal() >= TraceLevel.ERROR.ordinal()) {
                listener.onTrace(this,
                        new TraceEventArgs(TraceTypes.ERROR, ex));
            }
        }
    }

    /**
     * Notify clients from new data received.
     * 
     * @param e
     *            Received event argument.
     */
    final void notifyReceived(final ReceiveEventArgs e) {
        for (IGXMediaListener listener : mediaListeners) {
            listener.onReceived(this, e);
        }
    }

    /**
     * Notify clients from trace events.
     * 
     * @param e
     *            Trace event argument.
     */
    final void notifyTrace(final TraceEventArgs e) {
        for (IGXMediaListener listener : mediaListeners) {
            listener.onTrace(this, e);
        }
    }

    @Override
    public final int getConfigurableSettings() {
        return configurableSettings;
    }

    @Override
    public final void setConfigurableSettings(final int value) {
        configurableSettings = value;
    }

    /**
     * Displays the copyright of the control, user license, and version
     * information, in a dialog box.
     */
    public final void aboutBox() {
        throw new UnsupportedOperationException();
    }

    @Override
    public final void send(final Object data, final String target)
            throws Exception {
        if (sPort == null) {
            Toast.makeText(mCtx, "Port not opened !", Toast.LENGTH_LONG).show();
            throw new RuntimeException("Serial port is not open.");
        }
        if (trace == TraceLevel.VERBOSE) {
            notifyTrace(new TraceEventArgs(TraceTypes.SENT, data));
        }
        // Reset last position if end of packet is used.
        synchronized (syncBase.getSync()) {
            syncBase.resetLastPosition();
        }
        byte[] buff = GXSynchronousMediaBase.getAsByteArray(data);
        if (buff == null) {
            Toast.makeText(mCtx, "Data send failed. Invalid data.", Toast.LENGTH_LONG).show();
            throw new IllegalArgumentException(
                    "Data send failed. Invalid data.");
        }
        mSerialIoManager.writeAsync(buff);
        this.bytesSend += buff.length;
        Toast.makeText(mCtx, "Data sent successfully !", Toast.LENGTH_LONG).show();
    }

    @Override
    public final void open() throws Exception {
        close();
        if (sPort != null && connection != null) {
            try {
                sPort.open(connection);
                sPort.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, 0);
                sPort.setRTS(true);
                sPort.setDTR(true);
                isOpen = true;
                receiver = new GXReceiveThread(this);
            } catch (IOException e) {
                try {
                    sPort.close();
                    isOpen = false;
                } catch (IOException e1) {
                    isOpen = false;
                    e1.printStackTrace();
                }
            }
        } else {
            Toast.makeText(mCtx, "Port not opened !", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public final void close() {
        if (isOpen && sPort != null) {
            try {
                sPort.close();
                isOpen = false;
            } catch (IOException e) {
                isOpen = false;
                e.printStackTrace();
            }
        } else {
            Toast.makeText(mCtx, "Port not opened to close !", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public final boolean isOpen() {
        return sPort != null && isOpen;
    }

    @Override
    public final <T> boolean receive(final ReceiveParameters<T> args) {
        return syncBase.receive(args);
    }

    @Override
    public final long getBytesSent() {
        return bytesSend;
    }

    @Override
    public final long getBytesReceived() {
        return receiver.getBytesReceived();
    }

    @Override
    public final void resetByteCounters() {
        bytesSend = 0;
        receiver.resetBytesReceived();
    }

    @Override
    public final String getSettings() {
        StringBuilder sb = new StringBuilder();
        return sb.toString();
    }

    @Override
    public final void setSettings(final String value) {

    }

    @Override
    public final void copy(final Object target) {

    }

    @Override
    public final String getName() {
        return sPort != null ? sPort.getSerial() : null;
    }

    @Override
    public final String getMediaType() {
        return "Serial";
    }

    @Override
    public final Object getSynchronous() {
        synchronized (this) {
            int[] tmp = new int[] { synchronous };
            GXSync obj = new GXSync(tmp);
            synchronous = tmp[0];
            return obj;
        }
    }

    @Override
    public final boolean getIsSynchronous() {
        synchronized (this) {
            return synchronous != 0;
        }
    }

    @Override
    public final void resetSynchronousBuffer() {
        synchronized (syncBase.getSync()) {
            syncBase.resetReceivedSize();
        }
    }

    @Override
    public final void validate() {
        if (sPort == null) {
            throw new RuntimeException("Invalid port name.");
        }
    }

    @Override
    public final Object getEop() {
        return eop;
    }

    @Override
    public final void setEop(final Object value) {
        eop = value;
    }

    @Override
    public final void addListener(final IGXMediaListener listener) {
        mediaListeners.add(listener);
    }

    @Override
    public final void removeListener(final IGXMediaListener listener) {
        mediaListeners.remove(listener);
    }
}