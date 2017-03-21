package com.dlms.meter.dlmspoc.activities;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.dlms.meter.dlmspoc.R;
import com.dlms.meter.dlmspoc.interfaces.IDlmsDataReceiver;
import com.dlms.meter.dlmspoc.services.DLMSService;

import gurux.common.ReceiveEventArgs;

public class MainActivity extends AppCompatActivity implements IDlmsDataReceiver {

    private boolean mBounded = false;
    private DLMSService mServer;

    Button syncButton;
    Button aarqButton;
    Button readButton;

    TextView view;

    @Override
    public void onDataReceived(Object sender, ReceiveEventArgs e) {
        view.setText("Something received from meter ::::::" + e.toString());
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent mIntent = new Intent(this, DLMSService.class);
        bindService(mIntent, mConnection, BIND_AUTO_CREATE);
    };

    ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceDisconnected(ComponentName name) {
            Toast.makeText(MainActivity.this, "Service is disconnected", Toast.LENGTH_LONG).show();
            mBounded = false;
            mServer = null;
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            Toast.makeText(MainActivity.this, "Service is connected", Toast.LENGTH_LONG).show();
            mBounded = true;
            DLMSService.LocalBinder mLocalBinder = (DLMSService.LocalBinder)service;
            mServer = mLocalBinder.getServerInstance();
            mServer.setDlmsDataReceiver(MainActivity.this);
        }
    };

    @Override
    protected void onStop() {
        super.onStop();
        if(mBounded) {
            unbindService(mConnection);
            mBounded = false;
        }
    };

    public static void main(String args[]) {
        System.out.print(System.getProperty("sun.arch.data.model"));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        syncButton = (Button) findViewById(R.id.sync_usb);
        syncButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    mServer.getGxCommunicate().initializeConnection();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        aarqButton = (Button) findViewById(R.id.aarq);
        aarqButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        readButton = (Button) findViewById(R.id.read);
        readButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        view = (TextView) findViewById(R.id.textView);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
