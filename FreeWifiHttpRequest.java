package com.phonetest.phonetest;

import android.net.wifi.WifiManager;
import android.os.AsyncTask;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class FreeWifiHttpRequest extends AsyncTask<URL, Integer, Long> {

    private WifiManager wifiManager = null;
    public void setWifiManager(WifiManager wifiManager1){
        this.wifiManager = wifiManager1;
    }

    @Override
    protected Long doInBackground(URL... urls) {

        if (urls.length > 0) {
            URL url = urls[0];
            HttpURLConnection urlConnection = null;
            try {
                urlConnection = (HttpURLConnection) url.openConnection();

                BufferedReader br = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                StringBuilder str = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    str.append(line + "\n");
                }
                log("HTTP response = " + str.toString());
                br.close();

                if (str.toString().toLowerCase().contains("site accessible uniquement")){
                    log("Déjà connecté à un SSID FreeWifi");
                } else if (str.toString().contains("CONNEXION AU SERVICE REUSSIE")) {
                    log("Authentification réussie");
                } else {
                    log("N'est pas connecté à un SSID FreeWifi");
                    log("Authentification nécessaire");
                }

            } catch (Exception e) {
                log("FreeWifiHttpRequest:" + e.getMessage());
            }

        }

        return null;
    }

    @Override
    protected void onPostExecute(Long aLong) {
        super.onPostExecute(aLong);
        //log("MUST UPDATE MAIN LIST HERE");
        log("http end ok");
    }

    private void log(String s) {
        if (s.trim().equals("")) {
            s = " ";
        }
        System.out.println(s);
    }

}
