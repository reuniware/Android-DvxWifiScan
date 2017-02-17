package com.phonetest.phonetest;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.CaptivePortal;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telecom.TelecomManager;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    final int REQ_CODE_SPEECH_INPUT = 100;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ScrollView scrollView = (ScrollView) findViewById(R.id.scrollView);
        scrollView.setHorizontalScrollBarEnabled(true);

        TelecomManager tm = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        final TableLayout tableLayout = (TableLayout) findViewById(R.id.tableLayout);
        final TableRow tableRow = new TableRow(this);
        final Button btnStartRecording = new Button(this);
        btnStartRecording.setText("Start Rec");
        final Button btnStopRecording = new Button(this);
        btnStopRecording.setText("Stop Rec");
        btnStartRecording.setEnabled(true);
        btnStopRecording.setEnabled(false);

        tableRow.addView(btnStartRecording);
        tableRow.addView(btnStopRecording);
        tableRow.addView(new Button(this));
        tableLayout.addView(tableRow);

        btnStartRecording.setOnClickListener(new View.OnClickListener() {
            int nRow = 0;

            @Override
            public void onClick(View v) {
                //screenLog("test" + (nRow++));
                //startSpeechRecognizer();

                //test record audio
                //startAudioRecording();
                //btnStartRecording.setEnabled(false);
                //btnStopRecording.setEnabled(true);
                networkProcessing();
            }
        });

        btnStopRecording.setOnClickListener(new View.OnClickListener() {
            int nRow = 0;

            @Override
            public void onClick(View v) {
                //screenLog("test" + (nRow++));
                //startSpeechRecognizer();

                //test record audio
                //stopAudioRecording();
                //btnStartRecording.setEnabled(true);
                //btnStopRecording.setEnabled(false);
            }
        });

    }

    private void showToastMessage(String str) {
        Toast toast = new Toast(this);
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setBackgroundColor(Color.BLACK);
        textView.setTextColor(Color.WHITE);
        toast.setView(textView);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();
    }

    WifiManager wifiManager = null;

    private void networkProcessing() {

        try {
            if (wifiManager == null) {
                wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                if (wifiManager.isWifiEnabled()){
                    log("wifi is enabled");
                } else{
                    log("wifi is not enabled");
                    return;
                }
            }
        } catch (Exception e) {
            log("Exception:" + e.getMessage());
            return;
        }

        int ip = wifiManager.getConnectionInfo().getIpAddress();
        String ipString = String.format("%d.%d.%d.%d", (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
        log("ip=" + ipString);
        log("bssid=" + wifiManager.getConnectionInfo().getBSSID());

        if (!ipString.equals("0.0.0.0")){
            log("Une adresse ip est déjà obtenue");
        } else {
            log("Le périphérique n'est pas affecté d'une adresse IP");
        }

        if (1==1)
          return;

        listVisibleSSIDs();

        listPreconfiguredSSID();

        deleteAllPreconfiguredSSID("FreeWifi");

        addFreeWifiConfiguration();

        connectToFreeWifiConfiguration();
    }

    List<ScanResult> lstScanResult = null;

    private void listVisibleSSIDs() {
        wifiManager.startScan();
        lstScanResult = wifiManager.getScanResults();
        if (lstScanResult != null) {
            log("---- Résultat du scan des SSID visibles aux alentours ----");
            for (int i = 0; i < lstScanResult.size(); i++) {
                String SSID = lstScanResult.get(i).SSID;
                String BSSID = lstScanResult.get(i).BSSID;
                String LEVEL = "" + lstScanResult.get(i).level;
                log("SSID " + i + " visible actuellement : SSID [" + SSID + "] ; BSSID = " + BSSID + " ; level = " + LEVEL);
            }
        }
    }

    private void listPreconfiguredSSID(){
        log("---- Liste des réseaux SSID déjà configurés ----");
        List<WifiConfiguration> lstWifiConf = wifiManager.getConfiguredNetworks();
        for(int i=0;i<lstWifiConf.size();i++) {
            log("SSID préconfiguré " + i + " = " + lstWifiConf.get(i).SSID + " ; Network Id = " + lstWifiConf.get(i).networkId);
        }
    }

    private void deleteAllPreconfiguredSSID(String ssidToDelete) {
        log("---- Suppression de tous les SSID préconfigurés nommés " + ssidToDelete + " ----");
        List<WifiConfiguration> lstWifiConf = wifiManager.getConfiguredNetworks();
        for (int i = 0; i < lstWifiConf.size(); i++) {
            //log("Processing : " + lstWifiConf.get(i).SSID + " ; Searching for : " + ssidToDelete );
            if (lstWifiConf.get(i).SSID.toLowerCase().trim().contains(ssidToDelete.trim().toLowerCase())) {
                log("Un SSID " + ssidToDelete + " préconfiguré a été trouvé ; Suppression en cours.");
                boolean r = wifiManager.removeNetwork(lstWifiConf.get(i).networkId);
                log("Résultat de la suppression = " + r);
            }
        }
    }

    String excludeBSSID = "";//"7a:04:32:XX:XX:XX";
    private void addFreeWifiConfiguration() {
        // Ici reconfigurer une connexion FreeWifi
        log("* Ajout d'une configuration FreeWifi *");
        wifiManager.startScan();
        lstScanResult = wifiManager.getScanResults();
        if (lstScanResult != null) {
            log("---- Recherche d'un SSID FreeWifi visible aux alentours ----");
            for (int i = 0; i < lstScanResult.size(); i++) {
                String SSID = lstScanResult.get(i).SSID;
                String BSSID = lstScanResult.get(i).BSSID;
                String LEVEL = "" + lstScanResult.get(i).level;
                if (SSID.toLowerCase().trim().equals("freewifi") && !BSSID.equals(excludeBSSID)) {
                    log("FreeWifi visible aux alentours : SSID " + SSID + " found ; BSSID = " + BSSID + " ; level = " + LEVEL);
                    WifiConfiguration wifiConf = new WifiConfiguration();
                    wifiConf.SSID = SSID;
                    wifiConf.BSSID = BSSID;
                    wifiConf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                    wifiManager.addNetwork(wifiConf);
                    log("Une Configuration FreeWifi a été ajoutée");
                    break;  // Pour n'ajouter qu'une seule configuration relative au premier SSID FreeWifi détecté
                            // Si non (s'il n'y a pas de break) il y a autant d'ajout que de SSIDs FreeWifi visibles (peut être intéressant à exploiter...)
                }
            }
        }
        log("* Fin Ajout d'une configuration FreeWifi *");
    }

    private void connectToFreeWifiConfiguration(){
        // Connexion au réseau FreeWifi dont la configuration a été ajoutée
        log("---- Liste des réseaux SSID déjà configurés et connexion au SSID FreeWifi configuré ----");
        List<WifiConfiguration> lstWifiConf = wifiManager.getConfiguredNetworks();
        int networkId = -1;
        for(int i=0;i<lstWifiConf.size();i++) {
            log("lstwificonf " + i + "=" + lstWifiConf.get(i).SSID);
            if (lstWifiConf.get(i).SSID.toLowerCase().trim().contains(("freewifi")) && !lstWifiConf.get(i).BSSID.equals(excludeBSSID)) {
                log("Un réseau FreeWifi a été trouvé ; Récupération de l'id");
                networkId = lstWifiConf.get(i).networkId;
                log("networkId FreeWifi = " + networkId);
            }
        }

        if (networkId != -1){
            // Connexion proprement dite au SSID FreeWifi qui vient d'être ajouté
            wifiManager.disconnect();
            wifiManager.enableNetwork(networkId, true);
            boolean r = wifiManager.reconnect();
            log("reconnect r = " + r);
        }


        FreeWifiHttpRequest freeWifiHttpRequest  = new FreeWifiHttpRequest();

        String login="0000000000";
        String password="abcabcabcabca";
        URL url = null;
        try {
            url = new URL("https://wifi.free.fr/Auth?login=" + login + "&password=" + password);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        // Attendre que la connexion soit établie : TODO: Passer en évènement attendu (connecté à FreeWifi)...
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        freeWifiHttpRequest.setWifiManager(wifiManager);
        freeWifiHttpRequest.execute(url);
    }

    public void log(String s) {
        if (s.trim().equals("")){
            s = " ";
        }
        System.out.println(s);
        screenLog(s);
    }

    TableLayout tableLayout2;
    TableRow newTableRow;
    TextView textView;
    ScrollView scrollView;
    private void screenLog(String str){
        tableLayout2 = (TableLayout) findViewById(R.id.tableLayout2);
        newTableRow = new TableRow(getBaseContext());
        textView = new TextView(getBaseContext());
        textView.setText(str);
        textView.setTextColor(Color.BLACK);
        newTableRow.addView(textView);
        tableLayout2.addView(newTableRow);

        scrollView = (ScrollView) findViewById(R.id.scrollView);
        scrollView.pageScroll(View.FOCUS_DOWN);
    }

    String mFileName;
    FileOutputStream mFileOut;
    Socket mSocket;
    ParcelFileDescriptor mPFD;
    FileDescriptor mFD;

    MediaRecorder mRecorder;
    MediaPlayer mPlayer;

    void startAudioRecording(){

        mFileName = Environment.getExternalStorageDirectory().getAbsolutePath();
        mFileName += "/audiorecordtest.mp4";
        // Use a filedescriptor instead of direct file
        // This will enable easy transition to sockets later
        try {
            mFileOut = new FileOutputStream(mFileName);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        try {
            mFD = mFileOut.getFD();
        } catch (IOException e) {
            e.printStackTrace();
        }

        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mRecorder.setOutputFile(mFileName);
        try {
            mRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mRecorder.start();

    }

    private void stopAudioRecording() {
        mFileName = Environment.getExternalStorageDirectory().getAbsolutePath();
        mFileName += "/audiorecordtest.mp4";

        mRecorder.stop();
        mRecorder.release();
        mRecorder = null;
        try {
            mFileOut.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mFileOut = null;

        // Use a filedescriptor instead of direct file
        // This will enable easy transition to sockets later
        FileInputStream fileIn;
        try {
            fileIn = new FileInputStream(mFileName);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        //mFD = fileIn.getFD();

        mPlayer = new MediaPlayer();
        mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            mPlayer.setDataSource(mFileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            mPlayer.prepare(); // might take long! (for buffering, etc)
        } catch (IOException e) {
            e.printStackTrace();
        }
        mPlayer.start();
    }

    /**
     * Démarre la reconnaissance vocale et loggue à l'écran ce qui a été reconnu.
     */
    void startSpeechRecognizer(){
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().getDisplayLanguage());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Vous pouvez parler ...");
        try {
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(getApplicationContext(), "Désolé, votre appareil ne supporte pas d'entrée vocale...", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Evenèment déclenché à la fin d'une reconnaissance vocale.
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        log("onActivityResult");

        switch (requestCode) {
            case REQ_CODE_SPEECH_INPUT: {
                if (resultCode == RESULT_OK && null != data) {
                    ArrayList<String> buffer = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    String result = buffer.get(0);
                    System.out.println("result=" + result);
                    screenLog(result);
                }
                break;
            }
            default:
                break;
        }
    }

}
