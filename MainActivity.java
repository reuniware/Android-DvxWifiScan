/**
 * Investdata / Reuniware - email: reunisoft@gmail.com
 * Developed by Investdata Systems
 */

package eu.iledelareunion.www.dvxwifiscan;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends ActionBarActivity {

    WifiManager myWifiManager = null;

    private DvxWifiScanDbHelper dbHelper;

    public void dropTable() {
        SQLiteDatabase dbw = dbHelper.getWritableDatabase();
        dbw.execSQL("drop table if exists accesspoint", new String[]{});
        dbw.close();
    }

    public void deleteAllRecords(){
        SQLiteDatabase dbw = dbHelper.getWritableDatabase();
        //dbw.execSQL("delete from accesspoint", new String[]{});
        dbw.delete("accesspoint", "", new String[]{});
        dbw.close();
    }

    public void insertRecord(String ssid, String bssid, String level, String freq, String caps) {
        SQLiteDatabase dbw = dbHelper.getWritableDatabase();
        dbw.execSQL("insert into accesspoint (ssid, bssid, level, freq, caps) values (?,?,?,?,?)", new String[]{ssid, bssid, level, freq, caps});
        dbw.close();
    }

    public void updateRecord(String ssid, String bssid, String level, String freq, String caps) {
        SQLiteDatabase dbw = dbHelper.getWritableDatabase();
        dbw.execSQL("update accesspoint set ssid = ?, level = ?, freq = ?, caps = ? where bssid = ?", new String[]{ssid, level, bssid, freq, caps});
        dbw.close();
    }

    public void getAllRecords() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery("select * from accesspoint", new String[]{});
        //log("nb rec in db = " + c.getCount());
        for(int i=0;i<c.getCount();i++) {
            c.moveToNext();
            log("DB:" + c.getString(0) + " " + c.getString(1) + " " + c.getString(2) + " " + c.getString(3));
        }
        c.close();
        db.close();
    }

    public int getRecordsCount(){
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery("select * from accesspoint", new String[]{});
        int nb = c.getCount();
        c.close();
        return nb;
    }

    public boolean bssidExistsInDb(String bssid){
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery("select * from accesspoint where bssid=?", new String[]{bssid});
        if (c.getCount()>0){
            c.close();
            db.close();
            return true;
        }
        else {
            c.close();
            db.close();
            return false;
        }
    }


    private long scanPeriod = 2500; // toutes les 1 seconde
    private long timetowaitBetweenEachScan = 2500;
    private int nbLastSsidShown = 8; // nombre de ssid affichés (tous les ssid sont stockés dans une arrayListScanResultSsid)
    Timer timerLoopScan = null;
    private boolean clearScanLogFileWhenLoopScanLaunched = false;
    private boolean clearDbUponStartup = false;

    public void loopScanLauncher() {

        if (timerLoopScan != null) {
            showToastMessage("Loop Scan already started. Stopping it.");
            timerLoopScan.cancel();
            timerLoopScan.purge();
            timerLoopScan = null;

            if ((myWifiManager != null) && (myWifiManager.isWifiEnabled()))
                myWifiManager.reconnect();

            log("There are " + getRecordsCount() + " records in db");
            //getAllRecords();

            /*if (gpsProcessingIsWellStarted) {
                stopGpsProcessing();
            }*/
            return;
        }

        //clearMainLogFile();
        //gpsProcessingIsWellStarted = startGpsProcessing();
        log("New scan started at " + getTimeStamp());
        logScan("New scan started at " + getTimeStamp());

        if (clearScanLogFileWhenLoopScanLaunched) clearScanLogFile();
        showToastMessage("Scan log file = " + getScanLogFilePath());

        if (clearDbUponStartup) {
            deleteAllRecords();
        }

        if (timerLoopScan != null) {
            timerLoopScan.cancel();
            timerLoopScan.purge();
            timerLoopScan = null;
        }

        timerLoopScan = new Timer();
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                //clearScreen();
                final LinearLayout linearLayout = (LinearLayout) findViewById(R.id.linearLayout);
                linearLayout.post(new Runnable() {
                    @Override
                    public void run() {
                        loopScan();
                    }
                });
                findViewById(R.id.scrollView).post(new Runnable() {
                    public void run() {
                        ((ScrollView) findViewById(R.id.scrollView)).fullScroll(View.FOCUS_DOWN);
                    }
                });

            }
        };
        timerLoopScan.scheduleAtFixedRate(timerTask, 0, scanPeriod);
    }

    //--

    ArrayList<String> arrayListScanResultSsid = new ArrayList<String>();
    ArrayList<String> arrayListScanResultBssid = new ArrayList<String>();
    ArrayList<String> arrayListLevel = new ArrayList<String>();
    ArrayList<String> arrayListFreq = new ArrayList<String>();
    ArrayList<String> arrayListCaps = new ArrayList<String>();
    ArrayList<Location> arrayListLocation = new ArrayList<Location>();

    boolean forceDisconnectFromKnownNetwork = false;


    public void loopScan() {
        //Scan en boucle

        if (myWifiManager.isWifiEnabled()) {
            //clearScreen();
            if ((myWifiManager != null) && (myWifiManager.isWifiEnabled())) {
                try {

                    //log("Scanning every " + (scanPeriod + timetowaitBetweenEachScan) + "ms. Last " + nbLastSsidShown + " shown.");

                    myWifiManager.startScan();
                    List<ScanResult> lstScanResult = myWifiManager.getScanResults();

                    ArrayList<Integer> arrayListIndexOfSsidToLogToScanLogFile = new ArrayList<Integer>();

                    if (lstScanResult != null) {
                        for (int i = 0; i < lstScanResult.size(); i++) {
                            String strSsid = lstScanResult.get(i).SSID;
                            String strBssid = lstScanResult.get(i).BSSID;
                            String strLevel = "" + lstScanResult.get(i).level;
                            String strFrequency = "" + lstScanResult.get(i).frequency;
                            String strCaps = lstScanResult.get(i).capabilities;

                            if (!arrayListScanResultBssid.contains(strBssid)) { // la clé unique est le bssid (=mac address?)
                                if (!arrayListScanResultSsid.contains(strSsid)) {
                                    arrayListScanResultSsid.add(strSsid);
                                    if (!strBssid.isEmpty()) {
                                        arrayListScanResultBssid.add(strBssid);
                                    } else {
                                        arrayListScanResultBssid.add("n/a");
                                    }
                                    if (!strLevel.isEmpty()) {
                                        arrayListLevel.add(strLevel);
                                    } else {
                                        arrayListLevel.add("n/a");
                                    }

                                    if (!strFrequency.isEmpty()) {
                                        arrayListFreq.add(strFrequency);
                                    } else {
                                        arrayListFreq.add("n/a");
                                    }

                                    if (!strCaps.isEmpty()) {
                                        arrayListCaps.add(strCaps);
                                    } else {
                                        arrayListCaps.add("n/a");
                                    }


                                    int indexOfLastArrayListScanResultSsid = arrayListScanResultSsid.size() - 1;
                                    //logDebug("New SSID/BSSID scanned = " + arrayListScanResultSsid.get(indexOfLastArrayListScanResultSsid) + " ; " + arrayListScanResultBssid.get(indexOfLastArrayListScanResultSsid));
                                    //logDebug("--> this SSID/BSSID will be added to ScanLogFile");
                                    arrayListIndexOfSsidToLogToScanLogFile.add(indexOfLastArrayListScanResultSsid);
                                }
                            }
                        }
                    }
                    
                    // Logguer dans ScanLogFile si nécessaire
                    for (int i = 0; i < arrayListIndexOfSsidToLogToScanLogFile.size(); i++) {
                        int indexOfSsidToLog = arrayListIndexOfSsidToLogToScanLogFile.get(i);

                        String ssid = arrayListScanResultSsid.get(indexOfSsidToLog);
                        String bssid = arrayListScanResultBssid.get(indexOfSsidToLog);
                        String level = arrayListLevel.get(indexOfSsidToLog);
                        String freq = arrayListFreq.get(indexOfSsidToLog);
                        String caps = arrayListCaps.get(indexOfSsidToLog);

                        logScan(String.format("%03d", indexOfSsidToLog) + " | " + ssid + " | " + bssid.replace(":", "") + " | " + level + "dB" + " | " + freq + "MHz" + " | " + caps);
                        log(String.format("%03d", indexOfSsidToLog) + " | " + ssid + " | " + bssid.replace(":", "") + " | " + level + "dB" + " | " + freq + "MHz" + " | " + caps);


                        if(bssidExistsInDb(bssid))
                        {
                            updateRecord(ssid, bssid, level, freq, caps);
                        }
                        else
                        {
                            insertRecord(ssid, bssid, level, freq, caps);
                        }
                    }

                    int arrayListsSize = arrayListScanResultSsid.size();
                    //log(arrayListScanResultSsid.size() + " networks scanned. " + getTimeStamp());

                    //myWifiManager.reconnect();
                    
                    //Thread.sleep(timetowaitBetweenEachScan);
                } catch (Exception e) {
                    log("loopScan:Exception:" + e.getMessage());
                }
            }
        } else {
            log("Wifi is not started.");
        }

    }

    public void clearScreen() {
        final LinearLayout linearLayout = (LinearLayout) findViewById(R.id.linearLayout);
        linearLayout.post(new Runnable() {
            @Override
            public void run() {
                linearLayout.removeAllViews();
            }
        });
    }

    public void showToastMessage(String str) {
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

    public String getTimeStamp() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        String s = simpleDateFormat.format(new Date());
        return s;
    }

    public void logDebug(String str) {
        Log.d("DvxWifiScan", str);
    }


    public void clearMainLogFile() {
        File file = Environment.getExternalStorageDirectory();
        String pathToMntSdCard = file.getPath();

        File myFile = new File(pathToMntSdCard + "/" + mainLogFileName);
        boolean deleted = false;
        if (myFile.exists()) {
            deleted = myFile.delete();
        } else {
            showToastMessage("Main log file does not exist.");
        }
        if (deleted == true) {
            showToastMessage("Main log file has been deleted.");
        }
    }

    public void clearScanLogFile() {
        File file = Environment.getExternalStorageDirectory();
        String pathToMntSdCard = file.getPath();

        File myFile = new File(pathToMntSdCard + "/" + scanLogFileName);
        boolean deleted = false;
        if (myFile.exists()) {
            deleted = myFile.delete();
        } else {
            showToastMessage("Scan log file does not exist.");
        }
        if (deleted == true) {
            showToastMessage("Scan log file has been deleted.");
        }
    }

    String mainLogFileName = "dvxwifiscan.log";
    String scanLogFileName = "dvxwifiscan-scan.log";

    public String getMainLogFilePath() {
        File file = Environment.getExternalStorageDirectory();
        String pathToMntSdCard = file.getPath();
        String pathToLogFile = pathToMntSdCard + "/" + mainLogFileName;
        return pathToLogFile;
    }

    public String getScanLogFilePath() {
        File file = Environment.getExternalStorageDirectory();
        String pathToMntSdCard = file.getPath();
        String pathToLogFile = pathToMntSdCard + "/" + scanLogFileName;
        return pathToLogFile;
    }

    private int currentTextColor = Color.GREEN;
    /**
     * Loggue à l'écran (systématiquement) et dans le fichier wififun.log (si la checkbox Log to File est cochée)
     * @param str
     */
    public void log(String str) {
        final LinearLayout linearLayout = (LinearLayout) findViewById(R.id.linearLayout);

        // Si plus de 1000 lignes dans le log à l'écran alors effacer la zone de log
        if (linearLayout.getChildCount() > 1024) {
            linearLayout.removeAllViews();
        }

        final TextView textView = new TextView(MainActivity.this);
        textView.setTextColor(currentTextColor);
        if (currentTextColor == Color.GREEN) currentTextColor = Color.LTGRAY; else currentTextColor = Color.GREEN;

        textView.setTextSize(12);
        textView.setText(str);
        linearLayout.addView(textView);

        /*final CheckBox checkBox = (CheckBox) findViewById(R.id.checkBox);
        boolean logToFile = checkBox.isChecked();
        if (logToFile == true && timerLoopScan == null) { // ne pas logguer dans wififun.log si le LoopScan est activé (mais loggué ailleurs dans wififun-scan.log)
            // loggue dans le fichier /mnt/sdcard/wififun.log ; Utiliser Speed Software File Explorer pour accéder par exemple
            try {
                File myFile = new File(getMainLogFilePath());
                if (!myFile.exists()) {
                    myFile.createNewFile();
                }

                FileWriter fileWriter = new FileWriter(myFile, true);
                BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
                bufferedWriter.write(str + "\r\n");
                bufferedWriter.close();
                fileWriter.close();

            } catch (Exception e) {
            }
        }*/

        ((ScrollView) findViewById(R.id.scrollView)).post(new Runnable() {
            public void run() {
                ((ScrollView) findViewById(R.id.scrollView)).fullScroll(View.FOCUS_DOWN);
            }
        });

    }


    /*
    Loggue uniquement dans le fichier wififun-scan.log (objectif = ce fichier fait office de rapport de scan)
    */
    public void logScan(String str) {
        // loggue dans le fichier /mnt/sdcard/wififun-scan.log ; Utiliser Speed Software File Explorer pour accéder par exemple
        try {
            File myFile = new File(getScanLogFilePath());
            if (!myFile.exists()) {
                myFile.createNewFile();
            }

            FileWriter fileWriter = new FileWriter(myFile, true);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            bufferedWriter.write(str + "\r\n");
            bufferedWriter.close();
            fileWriter.close();

        } catch (Exception e) {
        }
    }

    boolean enableGpsLogToScreen = true;

    public void logGps(String str) {
        // loggue dans le fichier /mnt/sdcard/wififun-gps.log ; Utiliser Speed Software File Explorer pour accéder par exemple
        if (enableGpsLogToScreen == true) {
            log(str);
        }

        try {
            File myFile = new File(getGpsLogFilePath());
            if (!myFile.exists()) {
                myFile.createNewFile();
            }

            FileWriter fileWriter = new FileWriter(myFile, true);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            bufferedWriter.write(str + "\r\n");
            bufferedWriter.close();
            fileWriter.close();

        } catch (Exception e) {
        }
    }

    //--

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Sauvegarder chaque contenu texte des textviews dans le linearlayout (pour restauration en cas de rotation par ex)
        super.onSaveInstanceState(outState);
        LinearLayout linearLayout = (LinearLayout) findViewById(R.id.linearLayout);
        int childCount = linearLayout.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TextView textView = (TextView) linearLayout.getChildAt(i);
            outState.putString("textview" + i, textView.getText().toString());
            //Log.d("wififun:saving:",textView.getText().toString());
        }
    }

    //--

    public boolean gpsProcessingIsWellStarted = false;

    //--

    private AdView mAdView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        LinearLayout linearLayout = (LinearLayout) findViewById(R.id.linearLayout);
        linearLayout.setBackgroundColor(Color.BLACK);

        // restaurer chaque textview dans le linearlayout ; à priori pas besoin de restaurer l'état de la checkbox
        if (savedInstanceState != null) {
            for (int i = 0; i < savedInstanceState.size(); i++) {
                if (savedInstanceState.containsKey("textview" + i)) {
                    TextView textView = new TextView(this);
                    textView.setText(savedInstanceState.getString("textview" + i));
                    linearLayout.addView(textView);
                }
            }
        }

        dbHelper = new DvxWifiScanDbHelper(this.getApplicationContext());

        try {
            myWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        } catch (Exception e) {
            log("Exception:" + e.getMessage());
        }

        /*CheckBox checkBox = (CheckBox) findViewById(R.id.checkBox);
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b == true) {
                    showToastMessage("Log file = " + getMainLogFilePath());
                }
            }
        });*/

        Button btnStartWifi = (Button) findViewById(R.id.button);
        Button btnStopWifi = (Button) findViewById(R.id.button2);
        Button btnQuit = (Button) findViewById(R.id.button3);
        Button btnGetIp = (Button) findViewById(R.id.button4);
        Button btnScanSsid = (Button) findViewById(R.id.button5);
        Button btnClearScreen = (Button) findViewById(R.id.button9);
        //Button btnClearLogFile = (Button) findViewById(R.id.button6);
        Button btnStartLoopScan = (Button) findViewById(R.id.button8);
        //Button btnTest = (Button) findViewById(R.id.button7);

        /*btnTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                test();
            }
        });*/

        btnStartLoopScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loopScanLauncher();
            }
        });

        /*btnClearLogFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clearMainLogFile();
            }
        });*/

        btnClearScreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clearScreen();
            }
        });

        btnScanSsid.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                scan();
            }
        });

        btnStartWifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startWifi();
            }
        });

        btnStopWifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopWifi();
            }
        });

        btnQuit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                exitApplication();
            }
        });

        btnGetIp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getIpAddress();
            }
        });

    }

    //--

    public void startWifi() {
        //log("startwifi");
        try {
            //WifiManager myWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            boolean wasEnabled = myWifiManager.isWifiEnabled();

            if (wasEnabled == false) {
                myWifiManager.setWifiEnabled(true);
                log("Enabling Wifi...");
                while (!myWifiManager.isWifiEnabled()) {
                    // attendre
                }

                if (myWifiManager.isWifiEnabled() == true) {
                    log("Wifi has been started.");

                    WifiInfo wifiInfo = myWifiManager.getConnectionInfo();
                    if (wifiInfo != null) {
                        //log("wifiInfo is not null");
                        log("MAC=" + wifiInfo.getMacAddress());
                    } else {
                        log("Cannot retrieve MAC.");
                    }

                } else {
                    log("Wifi has not been started.");
                }
            } else {
                log("Wifi is already started.");
            }
        } catch (Exception e) {
            log("Exception:" + e.getMessage());
        }
    }

    public void stopWifi() {

        try {
            WifiManager myWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

            boolean isEnabled = myWifiManager.isWifiEnabled();
            if (isEnabled == true) {
                myWifiManager.setWifiEnabled(false);
                log("Stopping Wifi...");
                while (myWifiManager.isWifiEnabled()) {
                    // attendre
                }

                if (myWifiManager.isWifiEnabled() == true) {
                    log("Wifi has not been stopped.");
                } else {
                    myWifiManager = null;
                    log("Wifi has been stopped.");
                }
            } else {
                log("Wifi is not started.");
            }
        } catch (Exception e) {
            log("Exception:" + e.getMessage());
        }
    }

    public void scan() {
        if ((myWifiManager != null) && (myWifiManager.isWifiEnabled())) {
            log("**** SCAN SSIDs : START ****");
            boolean wasConnectedToSSID = false;
            try {
                if (!getIpAddr().equals("0.0.0.0")) {
                    log("Wifi seems connected to SSID : " + myWifiManager.getConnectionInfo().getSSID());
                    log("Disconnecting...");
                    boolean disconnected = myWifiManager.disconnect();
                    if (disconnected == true) {
                        log("Disconnected.");
                    }
                    wasConnectedToSSID = true;
                }
                log("Starting SSID scan...");
                myWifiManager.startScan();
                List<ScanResult> lstScanResult = myWifiManager.getScanResults();
                if (lstScanResult != null) {
                    log("----");
                    for (int i = 0; i < lstScanResult.size(); i++) {
                        log("SSID=" + lstScanResult.get(i).SSID);
                        log("   BSSID=" + lstScanResult.get(i).BSSID);
                        log("   CAPS=" + lstScanResult.get(i).capabilities);
                        log("   FREQ=" + lstScanResult.get(i).frequency);
                        log("   LEVEL=" + lstScanResult.get(i).level);
                        log("----");
                    }
                }

                if (lstScanResult != null) {
                    log(lstScanResult.size() + " Wifi networks detected around.");
                }

                if (wasConnectedToSSID) {
                    myWifiManager.reconnect();
                }

            } catch (Exception e) {
                log("Exception:" + e.getMessage());
            }
            log("**** SCAN SSIDs : END ****");
        } else {
            log("SSID Scan : Please start wifi.");
        }
    }

    private void getIpAddress() {
        if ((myWifiManager != null) && (myWifiManager.isWifiEnabled() == true)) {
            try {
                if (myWifiManager.isWifiEnabled()) {
                    log("Get IP Address: IP=" + getIpAddr());
                }
            } catch (Exception e) {
                log("Get IP Address:Exception:" + e.getMessage());
            }
        } else {
            //Log.d("wififun","please enable wifi");
            log("Get IP Address:Please start Wifi.");
        }
    }

    public String getIpAddr() {
        WifiManager wifiManager = myWifiManager;
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ip = wifiInfo.getIpAddress();
        String ipString = String.format("%d.%d.%d.%d", (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
        return ipString;
    }

    private void exitApplication() {
        /*if (myWifiManager != null) {
            try {
                myWifiManager.disconnect();
                myWifiManager = null;
            } catch (Exception e) {
                log("Exception:" + e.getMessage());
            }
        }*/
        System.exit(0);
    }

    //--


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


