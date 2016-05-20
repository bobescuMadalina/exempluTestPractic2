package ro.pub.cs.systems.eim.priacticaltest02.testpractic2;

import android.os.Bundle;
import android.provider.SyncStateContract;
import android.renderscript.Element;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.json.JSONException;
import org.w3c.dom.Document;
import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PracticalTest02MainAcitivity extends AppCompatActivity {

    HashMap<String, WeatherInformation> data = new HashMap<>();
    Button connectButton;
    TextView textViewResult;
    Button connectClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_practical_test02_main_acitivity);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        connectButton = (Button) findViewById(R.id.connect_server_button);
        connectButton.setOnClickListener(new ConnectButtonClickListener());

        textViewResult = (TextView) findViewById(R.id.text_view_result);
        connectClient = (Button) findViewById(R.id.connect_client_button);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_practical_test02_main_acitivity, menu);
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


    private class CommunicationThread extends Thread {

        private Socket socket;

        public CommunicationThread(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            if (socket != null) {
                try {
                    BufferedReader bufferedReader = Utilities.getReader(socket);
                    PrintWriter printWriter = Utilities.getWriter(socket);
                    if (bufferedReader != null && printWriter != null) {
                        Log.i("APP_TAG", "[COMMUNICATION THREAD] Waiting for parameters from client (city / information type)!");
                        String city = bufferedReader.readLine();
                        String informationType = bufferedReader.readLine();
                        HashMap<String, WeatherInformation> data = getData();
                        WeatherInformation weatherForecastInformation = null;
                        if (city != null && !city.isEmpty() && informationType != null && !informationType.isEmpty()) {
                            if (data.containsKey(city)) {
                                Log.i("APP_TAG", "[COMMUNICATION THREAD] Getting the information from the cache...");
                                weatherForecastInformation = data.get(city);
                            } else {
                                Log.i("APP_TAG", "[COMMUNICATION THREAD] Getting the information from the webservice...");
                                HttpClient httpClient = new DefaultHttpClient();
                                HttpPost httpPost = new HttpPost("http://www.wunderground.com/cgi-bin/findweather/getForecast");
                                List<NameValuePair> params = new ArrayList<NameValuePair>();
                                params.add(new BasicNameValuePair("city", city));
                                UrlEncodedFormEntity urlEncodedFormEntity = new UrlEncodedFormEntity(params, HTTP.UTF_8);
                                httpPost.setEntity(urlEncodedFormEntity);
                                ResponseHandler<String> responseHandler = new BasicResponseHandler();
                                String pageSourceCode = httpClient.execute(httpPost, responseHandler);
                                if (pageSourceCode != null) {
                                    /*Document document = Jsoup.parse(pageSourceCode);
                                    Element element = document.child(0);
                                    Elements scripts = element.getElementsByTag(Constants.SCRIPT_TAG);
                                    for (Element script: scripts) {
                                        String scriptData = script.data();
                                        if (scriptData.contains(Constants.SEARCH_KEY)) {
                                            int position = scriptData.indexOf(Constants.SEARCH_KEY) + Constants.SEARCH_KEY.length();
                                            scriptData = scriptData.substring(position);
                                            JSONObject content = new JSONObject(scriptData);
                                            JSONObject currentObservation = content.getJSONObject(Constants.CURRENT_OBSERVATION);
                                            String temperature = currentObservation.getString(Constants.TEMPERATURE);
                                            String windSpeed = currentObservation.getString(Constants.WIND_SPEED);
                                            String condition = currentObservation.getString(Constants.CONDITION);
                                            String pressure = currentObservation.getString(Constants.PRESSURE);
                                            String humidity = currentObservation.getString(Constants.HUMIDITY);
                                            weatherForecastInformation = new WeatherForecastInformation(
                                                    temperature,
                                                    windSpeed,
                                                    condition,
                                                    pressure,
                                                    humidity
                                            );
                                            serverThread.setData(city, weatherForecastInformation);
                                            break;
                                        }
                                    }*/
                                } else {
                                    Log.e("APP_TAG", "[COMMUNICATION THREAD] Error getting the information from the webservice!");
                                }
                            }
                            if (weatherForecastInformation != null) {
                                String result = null;

                                result = weatherForecastInformation.toString();

                                printWriter.println(result);
                                printWriter.flush();
                            } else {
                                Log.e("APP_ATG", "[COMMUNICATION THREAD] Weather Forecast information is null!");
                            }
                        } else {
                            Log.e("APP_TAG", "[COMMUNICATION THREAD] Error receiving parameters from client (city / information type)!");
                        }
                    } else {
                        Log.e("APP_TAG", "[COMMUNICATION THREAD] BufferedReader / PrintWriter are null!");
                    }
                    socket.close();
                } catch (IOException ioException) {
                    Log.e("APP_TAG", "[COMMUNICATION THREAD] An exception has occurred: " + ioException.getMessage());

                    ioException.printStackTrace();

                } /*catch (JSONException jsonException) {
                    Log.e("APP_TAG", "[COMMUNICATION THREAD] An exception has occurred: " + jsonException.getMessage());

                        jsonException.printStackTrace();

                }*/
            } else {
                Log.d("APP_TAG", "[COMMUNICATION THREAD] Socket is null!");
            }

        }
    }

    private ServerThread serverThread;

    private class ServerThread extends Thread {

        private boolean isRunning;
        int serverPort;

        public ServerThread(int serverPort) {
            this.serverPort = serverPort;
        }

        private ServerSocket serverSocket;

        public void startServer() {
            isRunning = true;
            start();

        }

        public ServerSocket getServerSocket() {
            return serverSocket;
        }

        public void stopServer() {
            isRunning = false;
            try {
                if (serverSocket != null) {
                    serverSocket.close();
                }
            } catch (IOException ioException) {

                ioException.printStackTrace();

            }

        }

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(serverPort);
                while (isRunning) {
                    Socket socket = serverSocket.accept();
                    if (socket != null) {
                        CommunicationThread communicationThread = new CommunicationThread(socket);
                        communicationThread.start();
                    }
                }
            } catch (IOException ioException) {

                ioException.printStackTrace();

            }
        }
    }

    private class ClientThread extends Thread {
        Socket socket;
        String address;
        String city;
        String informationType;
        int port;

        public ClientThread(String address, int port, String city, String informationType) {
            this.address = address;
            this.port = port;
            this.city = city;
            this.informationType = informationType;

        }

        @Override
        public void run() {
            try {
                socket = new Socket(address, port);
                if (socket == null) {
                    Log.e("TAG", "[CLIENT THREAD] Could not create socket!");
                    return;
                }
                BufferedReader bufferedReader = Utilities.getReader(socket);
                PrintWriter printWriter = Utilities.getWriter(socket);
                if (bufferedReader != null && printWriter != null) {
                    printWriter.println(city);
                    printWriter.flush();
                    printWriter.println(informationType);
                    printWriter.flush();
                    String weatherInformation;
                    while ((weatherInformation = bufferedReader.readLine()) != null) {
                        final String finalizedWeatherInformation = weatherInformation;
                        textViewResult.post(new Runnable() {
                            @Override
                            public void run() {
                                textViewResult.append(finalizedWeatherInformation + "\n");
                            }
                        });
                    }
                } else {
                    Log.e("APP_TAG", "[CLIENT THREAD] BufferedReader / PrintWriter are null!");
                }
                socket.close();
            } catch (IOException ioException) {
                Log.e("APP_TAG", "[CLIENT THREAD] An exception has occurred: " + ioException.getMessage());

                ioException.printStackTrace();

            }
        }
    }

    private class ConnectButtonClickListener implements Button.OnClickListener {
        @Override
        public void onClick(View view) {
            String serverPort = "2010";
            if (serverPort == null || serverPort.isEmpty()) {
                Toast.makeText(
                        getApplicationContext(),
                        "Server port should be filled!",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }
            serverThread = new ServerThread(Integer.parseInt(serverPort));
            //if (serverThread.getServerSocket() != null) {
            serverThread.startServer();
            Log.d("APP_TAG", "Server started");
            //} else {

        }
    }


    public synchronized void setData(String city, WeatherInformation weatherForecastInformation) {
        this.data.put(city, weatherForecastInformation);
    }

    public synchronized HashMap<String, WeatherInformation> getData() {
        return data;
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serverThread != null) {
            serverThread.stopServer();
        }
    }


    private class GetWeatherForecastButtonClickListener implements Button.OnClickListener {
        @Override
        public void onClick(View view) {
            /*String clientAddress = clientAddressEditText.getText().toString();
            String clientPort    = clientPortEditText.getText().toString();
            if (clientAddress == null || clientAddress.isEmpty() ||
                    clientPort == null || clientPort.isEmpty()) {
                Toast.makeText(
                        getApplicationContext(),
                        "Client connection parameters should be filled!",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }
            if (serverThread == null || !serverThread.isAlive()) {
                Log.e(Constants.TAG, "[MAIN ACTIVITY] There is no server to connect to!");
                return;
            }
            String city = cityEditText.getText().toString();
            String informationType = informationTypeSpinner.getSelectedItem().toString();
            if (city == null || city.isEmpty() ||
                    informationType == null || informationType.isEmpty()) {
                Toast.makeText(
                        getApplicationContext(),
                        "Parameters from client (city / information type) should be filled!",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }
            weatherForecastTextView.setText(Constants.EMPTY_STRING);
            clientThread = new ClientThread(
                    clientAddress,
                    Integer.parseInt(clientPort),
                    city,
                    informationType,
                    weatherForecastTextView);
            clientThread.start();*/
        }
    }
}
