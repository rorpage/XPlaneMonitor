package net.waynepiekarski.xplanemonitor;

import android.app.Activity;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.TreeMap;

public class MainActivity extends Activity implements UDPReceiver.OnReceiveUDP {

    UDPReceiver data_listener, dref_listener;
    TextView ipAddress;
    TextView debugText;
    Button resetButton;
    Button simButton;
    TreeMap<String, Float> mapDREF;
    TreeMap<String, String> mapDATA;
    int sequence;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ipAddress = (TextView)findViewById(R.id.ipAddress);
        debugText = (TextView)findViewById(R.id.debugText);
        resetButton = (Button)findViewById(R.id.reset);
        resetButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Log.d(Const.TAG, "Resetting internal map");
                mapDREF.clear();
                mapDATA.clear();
                updateUI();
            }
        });
        simButton = (Button)findViewById(R.id.simulate);
        simButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Log.d(Const.TAG, "Simulating some data");
                onReceiveUDP(sample_groundspeed);
                onReceiveUDP(sample_data);
            }
        });


        mapDREF = new TreeMap<>();
        mapDATA = new TreeMap<>();
        sequence = 0;
    }

    @Override
    protected void onResume() {
        super.onResume();

        WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
        String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
        Log.d(Const.TAG, "onResume(), starting listeners with IP address " + ip);
        ipAddress.setText(ip);

        dref_listener = new UDPReceiver(Const.UDP_DREF_PORT, null, this);
        data_listener = new UDPReceiver(Const.UDP_DATA_PORT, null, this);
    }

    @Override
    protected void onPause() {
        Log.d(Const.TAG, "onPause(), cancelling UDP listeners");
        dref_listener.cancel(true);
        data_listener.cancel(true);
        super.onPause();
    }

    public static final byte[] sample_groundspeed = new byte[] { (byte)0x44, (byte)0x52, (byte)0x45, (byte)0x46, (byte)0x2B, (byte)0xC0, (byte)0xBB, (byte)0xB0, (byte)0x35, (byte)0x66, (byte)0x61, (byte)0x6b, (byte)0x65, (byte)0x2f, (byte)0x66, (byte)0x6C, (byte)0x69, (byte)0x67, (byte)0x68, (byte)0x74, (byte)0x6D, (byte)0x6F, (byte)0x64, (byte)0x65, (byte)0x6C, (byte)0x2F, (byte)0x70, (byte)0x6F, (byte)0x73, (byte)0x69, (byte)0x74, (byte)0x69, (byte)0x6F, (byte)0x6E, (byte)0x2F, (byte)0x67, (byte)0x72, (byte)0x6F, (byte)0x75, (byte)0x6E, (byte)0x64, (byte)0x73, (byte)0x70, (byte)0x65, (byte)0x65, (byte)0x64, (byte)0x5B, (byte)0x30, (byte)0x5D };
    public static final byte[] sample_data = hexStringToByteArray("444154412A000000000E88624133339F4100C079C4A7A6903D4C43903DA78D8F3D0000803F0000803F0200000000005C420000A6428045B14A00C079C400C079C400C079C400C079C400C079C40300000009000000AC660B3561810B3562810B3500C079C40A0000003A8A20353B8A20350D0000000000000000000000000000000000803F0000803F0000803F00000000000000000E0000000000803FA0C7993E000000000000000000C079C400C079C400C079C400C079C4220000006ED897436ED897434FFAA4434FFAA44300000000000000000000000000000000");

    // From http://stackoverflow.com/questions/140131/convert-a-string-representation-of-a-hex-dump-to-a-byte-array-using-java
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    public String getCompactFloat(float in) {
        // Round floats so ridiculously tiny values get called 0
        if (in < 0.00001)
            return "0";
        else
            return "" + in;

    }

    public void onReceiveUDP(byte[] buffer) {
        Log.d(Const.TAG, "onReceiveUDP bytes=" + buffer.length);
        sequence++;

        if ((buffer.length >= 5) && (buffer[0] == 'D') && (buffer[1] == 'R') && (buffer[2] == 'E') && (buffer[3] == 'F')) {
            // Handle DREF+ packet type here
            // ["DREF+"=5bytes] [float=4bytes] ["label/name/var[0]\0"=remaining_bytes]
            if (buffer[4] != '+') {
                Log.e(Const.TAG, "Cannot parse [" + buffer[4] + "] when expected '+' symbol");
            }
            float f = ByteBuffer.wrap(buffer, +5, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            int zero;
            for (zero = 9; zero < buffer.length && buffer[zero] != '\0'; zero++)
                ;
            String name = new String(buffer, +9, zero - 9);
            Log.d(Const.TAG, "Parsed DREF+ with float=" + f + " for variable=" + name);

            mapDREF.put(name, f);
            updateUI();
        } else if ((buffer.length >= 5) && (buffer[0] == 'D') && (buffer[1] == 'A') && (buffer[2] == 'T') && (buffer[3] == 'A') && (buffer[4] == '*')) {
            Log.d(Const.TAG, "Found DATA* packet");
            // A data blob is an int (4 bytes) followed by 8 floats (8*4) = total 36
            // There can be many blobs in a single UDP packet (5 byte header + n*36 == packet size)
            final int blob_length = 4 + 8*4;
            int start;
            for (start = 5; start < buffer.length; start += blob_length) {
                int id = ByteBuffer.wrap(buffer, start, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                int idx = start + 4;
                float[] array = new float[8];
                String debugArray = "[";
                for (int f = 0; f < 8; f++) {
                    array[f] = ByteBuffer.wrap(buffer, idx, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
                    // Round floats so ridiculously tiny values get called 0
                    debugArray += XPlaneData.names[id*8+f] + "(" + XPlaneData.units[id*8+f] + ")=";
                    debugArray += getCompactFloat(array[f]);
                    if (f < 7)
                        debugArray += ", ";
                }
                debugArray += "]";
                Log.d(Const.TAG, "DATA* id=" + id + " array=" + debugArray);
                mapDATA.put("DATA*" + id, debugArray);
                updateUI();
            }
            if (start != buffer.length)
                Log.e(Const.TAG, "Mismatch in buffer size, end was " + start + " but buffer length was " + buffer.length);
        } else {
            Log.e(Const.TAG, "Ignoring unknown packet");
        }
    }

    public void updateUI() {
        // Dump out current list of everything
        String out = "sequence=" + sequence + "\n";
        for (TreeMap.Entry<String, Float> entry : mapDREF.entrySet()) {
            // Log.d(Const.TAG, "Key=" + entry.getKey() + " Value=" + entry.getValue());
            out = out + "\n" + entry.getKey() + " = " + getCompactFloat(entry.getValue());
        }
        out = out + "\n";
        for (TreeMap.Entry<String, String> entry : mapDATA.entrySet()) {
            // Log.d(Const.TAG, "Key=" + entry.getKey() + " Value=" + entry.getValue());
            out = out + "\n" + entry.getKey() + " = " + entry.getValue();
        }
        debugText.setText(out);
    }
}
