package com.example.sscollectorv2;
import android.app.Activity;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.widget.TextView;
import com.example.sscollectorv2.databinding.ActivityMainBinding;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.net.Socket;
import java.io.*;

public class MainActivity extends WearableActivity implements SensorEventListener {

    private static final String TAG = "MainActivity";
    private SensorManager sensorManager;
    private Sensor accelerometer, gyroscope;

    private Button btRecord;
    private Button btEstimate;
    private Spinner spinnerAct;
    private EditText repetition;
    private MediaRecorder recorder = null;

    private String[] actions;
    private String action, newName;
    private String ax, ay, az, gx, gy, gz;
    private String filePath, fileName, audioFileName;
    private FileWriter writer;

    private boolean record = false;
    private boolean flagA = false;
    private boolean flagG = false;

    // For Socket
    public static String HOST = "172.20.10.4";
    public static int PORT = 8080;
    // PC and wearOS time stamps
    public static Long WEAROS_TIMESTAMP1 = null;
    public static Long WEAROS_TIMESTAMP2 = null;
    public static Long PC_TIMESTAMP1 = null;
    public static Long PC_TIMESTAMP2 = null;
    public static Long WEAROS_START_RECORDING_TS = null;
    public static Long PC_START_RECORDING_TS = null;
    public static String TRANSFERRING_FILE_PATH = null;

    /*
    Util functions
    get_current_timeStamp: get the current wearOS timestamp, returns a Long
     */
    public static long get_current_timeStamp(){
        // return the Long millisecond timestamp
        return System.currentTimeMillis();
    }
    /*
    Scoket functions
    estimateOffset: Estimate the timestamp offset due to internet delay
    send_start_msg: Sending the message to python PC end to tell the PC start video/audio recoding
    send_end_msg: Send the mesaage to python PC end to tell the PC stop video/audio recoding
    */
    public static class estimateOffset implements Runnable{
        public void run(){
            // This is just estimate the offset between the wearOS device and the PC
            try
            {
                // Estimate the offset between wearOS and PC
                Log.d(TAG, "Offset estimation start.");
                Socket soc = new Socket(HOST, PORT);
                DataOutputStream msgOut = new DataOutputStream(soc.getOutputStream());
                DataInputStream msgIn = new DataInputStream(soc.getInputStream());
                // Send the first wearOS timestamp to PC
                long wearosTimestamp1 = get_current_timeStamp();
                msgOut.writeUTF(Long.toString(wearosTimestamp1));
                msgOut.flush();
                // Receive the first PC timestamp from PC
                long pcTimestamp1 = Long.parseLong((String)msgIn.readUTF());
                // Send the first PC timestamp
                long wearosTimestamp2 = get_current_timeStamp();
                msgOut.writeUTF(Long.toString(wearosTimestamp2));
                msgOut.flush();
                // Receive the second PC timestamp from PC
                long pcTimestamp2 = Long.parseLong((String)msgIn.readUTF());
                WEAROS_TIMESTAMP1 = wearosTimestamp1;
                WEAROS_TIMESTAMP2 = wearosTimestamp2;
                PC_TIMESTAMP1 = pcTimestamp1;
                PC_TIMESTAMP2 = pcTimestamp2;
                soc.close();
                Log.d(TAG, "Offset estimation end.");
            }
            catch(Exception e)
            {
                e.printStackTrace();
                Log.e(TAG, "Fail to perform the offset estimation between PC and wearOS.");
            }
        }
    }

    public static class send_start_msg implements Runnable{
        // send the msg to the wearOS to tell it start recording
        public void run() {
            try
            {
                // Estimate the offset between wearOS and PC
                Socket soc = new Socket(HOST, PORT);
                DataOutputStream msgOut = new DataOutputStream(soc.getOutputStream());
                DataInputStream msgIn = new DataInputStream(soc.getInputStream());
                // Send the start recording msg to PC
                long wearos_start_recording_ts = get_current_timeStamp();
                msgOut.writeUTF(Long.toString(wearos_start_recording_ts));
                msgOut.flush();
                // Receive the first PC timestamp from PC
                long pc_start_recording_ts = Long.parseLong((String)msgIn.readUTF());
                WEAROS_START_RECORDING_TS = wearos_start_recording_ts;
                PC_START_RECORDING_TS = pc_start_recording_ts;
                System.out.println(WEAROS_START_RECORDING_TS);
                System.out.println(PC_START_RECORDING_TS);
                soc.close();
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    public static class send_end_msg implements Runnable{
        // send the msg to the wearOS to tell it start recording
        public void run() {
            try
            {
                // Estimate the offset between wearOS and PC
                Socket soc = new Socket(HOST, PORT);
                DataOutputStream msgOut = new DataOutputStream(soc.getOutputStream());
                DataInputStream msgIn = new DataInputStream(soc.getInputStream());
                // Send the start recording msg to PC
                long wearos_end_recording_ts = get_current_timeStamp();
                msgOut.writeUTF(Long.toString(wearos_end_recording_ts));
                msgOut.flush();
                soc.close();
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    private static class sendFile implements Runnable{
        // Send files to PC end
        public void run() {
            try (Socket socket = new Socket(HOST, PORT))
            {
                int bytes = 0;
                DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
                DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
                File file = new File(TRANSFERRING_FILE_PATH);
                FileInputStream fileInputStream = new FileInputStream(file);
                dataOutputStream.writeLong(file.length());
                byte[] buffer = new byte[4 * 1024];
                while ((bytes = fileInputStream.read(buffer))
                        != -1) {
                    // Send the file to Server Socket
                    dataOutputStream.write(buffer, 0, bytes);
                    dataOutputStream.flush();
                }
                fileInputStream.close();
                dataInputStream.close();
                dataInputStream.close();
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Init sensor recording
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        // Init spinner
        spinnerAct = (Spinner)findViewById(R.id.spinnerAct);
        String[] arraySpinner = new String[] {
                "Jackpot", "Pull up", "Push up"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, arraySpinner);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAct.setAdapter(adapter);

        // Start recording button
        btRecord = findViewById(R.id.btRecord);
        btRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                try {
                    dataCollector(arg0);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        // Offset estimation button
        btEstimate = findViewById(R.id.btEstimate);
        btEstimate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                offset_estimation(arg0);
            }
        });

        // Init save path
        repetition = findViewById(R.id.rep);
        filePath = Environment.getExternalStorageDirectory() + "/SensorData";
        File file = new File(Environment.getExternalStorageDirectory()
                + "/SensorData");
        if (!file.exists())
            file.mkdirs();
        setAmbientEnabled();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor;

        if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            flagA = true;
            ax = String.format(Locale.ROOT,"%.4f", event.values[0]);
            ay = String.format(Locale.ROOT,"%.4f", event.values[1]);
            az = String.format(Locale.ROOT,"%.4f", event.values[2]);
        }

        if (sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            flagG = true;
            gx = String.format(Locale.ROOT,"%.4f", event.values[0]);
            gy = String.format(Locale.ROOT,"%.4f", event.values[1]);
            gz = String.format(Locale.ROOT,"%.4f", event.values[2]);
        }

        if (flagA && flagG) {

            String date = String.valueOf(System.currentTimeMillis());
            date +=  "," + ax + "," + ay + "," + az + "," + gx + "," + gy + "," + gz + "\n";

            if (!date.isEmpty() && record)
                dataReader(date);

            flagA = false;
            flagG = false;
        }
    }

    public void startSensor(){
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, 10000);
            Log.d(TAG, "Registered accelerometer listener");
        }
        if (gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, 10000);
            Log.d(TAG, "Registered gyroscope listener");
        }
    }

    public void stopSensor(){
        sensorManager.unregisterListener(this);
        Log.d(TAG, "Stop sensor");
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void offset_estimation(View arg0) {
        // Start a new thread to estimate the network offset
        Thread estimate_offset_thread;
        estimate_offset_thread = new Thread(new estimateOffset());
        estimate_offset_thread.start();
        // Wait one second for the offset estimation
        try {
            Thread.sleep(1000);
        } catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public void dataCollector(View arg0) throws InterruptedException {
        if (record == false) {


            String ActionCategory = spinnerAct.getSelectedItem().toString();

            newName = repetition.getText().toString().trim();
            if (newName.equals("")){
                Toast.makeText(getApplicationContext(), "Please input file name",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            audioFileName = Environment.getExternalStorageDirectory() + "/SensorData";
            Toast.makeText(getApplicationContext(), "Recording",
                    Toast.LENGTH_SHORT).show();

            Date date = new Date(System.currentTimeMillis());
            String timeMilli = "" + date.getTime();
            record = true;
            fileName = timeMilli + "_" + newName + ".csv";
            audioFileName += "/"+timeMilli + "_" + newName+".wav";

            // Send start recording message
            Thread start_recording_sending;
            start_recording_sending = new Thread(new send_start_msg());
            start_recording_sending.start();

            // Start sensor and audio recording
            startRecording();
            try {
                writer = new FileWriter(new File(filePath, fileName));
            } catch (IOException e) {
                e.printStackTrace();
            }
            btRecord.setText("Stop");
        }

        else {
            record = false;
            repetition.setEnabled(true);
            // Send end recording message
            Thread stop_recording_sending;
            stop_recording_sending = new Thread(new send_end_msg());
            stop_recording_sending.start();
            btRecord.setText("Start");
            stopRecording();
            Toast.makeText(getApplicationContext(), "Saving",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void dataReader(String data) {
        try {
            writer.write(data);
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startRecording() {
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setOutputFile(audioFileName);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            recorder.prepare();

        } catch (IOException e) {
            Log.e("start", "prepare() failed");
        }
        startSensor();
        recorder.start();
    }

    private void stopRecording() {
        stopSensor();
        recorder.stop();
        recorder.release();
        recorder = null;
    }
}