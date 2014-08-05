package com.example.android.sunshine;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * A placeholder fragment containing a simple view.
 */
public class ForecastFragment extends Fragment {

    private ArrayAdapter<String> mForecastAdapter;

    public ForecastFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Agregar esta linea para que este fragmento pueda manejar eventos de menu
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.forecastfragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh){
            updateWeather();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateWeather() {
        FetchWeatherTask weatherTask = new FetchWeatherTask();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String location = prefs.getString(getString(R.string.pref_location_key),
                getString(R.string.pref_location_default));
        weatherTask.execute(location);
    }

    @Override
    public void onStart() {
        super.onStart();
        updateWeather();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        //El adaptador va a tomar los datos desde una fuente y los usara
        //para llenar el ListView asociado a el
        mForecastAdapter =
                new ArrayAdapter<String>(
                    getActivity(), //Contexto actual
                    R.layout.list_item_forecast, //Id del layout de la lista
                    R.id.list_item_forecast_textview, //Id del textview para el listado
                    new ArrayList<String>() //Datos
        );

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        //Referencia del ListView y settear el adaptador a el
        ListView listView = (ListView) rootView.findViewById(R.id.listview_forecast);
        listView.setAdapter(mForecastAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener(){
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                String forecast = mForecastAdapter.getItem(position);
                Toast.makeText(getActivity(), forecast, Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(getActivity(), DetailActivity.class)
                        .putExtra(Intent.EXTRA_TEXT, forecast);
                startActivity(intent);
            }
        });

        return rootView;
    }

    public class FetchWeatherTask extends AsyncTask<String, Void, String[]>{

        private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();

        private String getReadableDateString(long time){
            //Debido a que la API retorna un timestamp unix (expresado en segundos)
            //se debe convertir a milisegundos para ser convertida a una fecha valida
            Date date = new Date(time * 1000);
            SimpleDateFormat format = new SimpleDateFormat("E, MMM, d");
            return  format.format(date).toString();
        }

        /*
         * Preparar los high/low del clima para la presentacion
         */
        private String formatHighLows(double high, double low){
            //Los datos son mostrados en Celsius por defecto.
            //Si un usuario prefiere verlos en Fahrenheit, convierte los valores aqui
            //Vamos a hacer que en vez de traer los datos Fahrenheir directamente desde el
            //servidor, el usuario pueda cambiarlo trayendo los datos una unica vez.
            //Para eso almacenaremos los valores en una base de datos.
            SharedPreferences sharedPrefs =
                    PreferenceManager.getDefaultSharedPreferences(getActivity());
            String unitType = sharedPrefs.getString(
                    getString(R.string.pref_units_key),
                    getString(R.string.pref_units_metric));
            if (unitType.equals(getString(R.string.pref_units_imperial))){
                high = (high * 1.8) + 32;
                low = (low * 1.8) + 32;
            } else if ( !unitType.equals(getString(R.string.pref_units_metric))){
                Log.d(LOG_TAG, "Unit type not found: " + unitType);
            }
            long roundedHigh = Math.round(high);
            long roundedLow = Math.round(low);

            String highLowStr = roundedHigh + "/" + roundedLow;
            return highLowStr;
        }

        /*
         * Toma un String con la representacion completa del clima en formato JSON y tomamos
         * los Strings necesarios
         */
        private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays)
                throws JSONException {

            //Nombres de los objetos JSON que necesitamos extraer
            final String OWN_LIST = "list";
            final String OWN_WEATHER = "weather";
            final String OWN_TEMPERATURE = "temp";
            final String OWN_MAX = "max";
            final String OWN_MIN = "min";
            final String OWN_DATETIME = "dt";
            final String OWN_DESCRIPTION = "main";

            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.getJSONArray(OWN_LIST);

            String[] resultStr = new String[weatherArray.length()];
            for (int i = 0; i < weatherArray.length(); i++){
                //Por ahora, usaremos el formato "Dia, descripcion, high/low"
                String day;
                String description;
                String highAndLow;

                //Obtener el JSON object que representa el dia
                JSONObject dayForecast = weatherArray.getJSONObject(i);

                //El date/time es retornado como un long. Necesitamos convertirlo
                //a algo legible por un humano
                long dateTime = dayForecast.getLong(OWN_DATETIME);
                day = getReadableDateString(dateTime);

                //La descripcion esta en un arreglo hijo llamado "weather" que es 1 elemento long
                JSONObject weatherObject = dayForecast.getJSONArray(OWN_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWN_DESCRIPTION);

                //La temperatura esta en un arregl hijo llamado "temp"
                JSONObject temperatureObject = dayForecast.getJSONObject(OWN_TEMPERATURE);
                double high = temperatureObject.getDouble(OWN_MAX);
                double low = temperatureObject.getDouble(OWN_MIN);

                highAndLow = formatHighLows(high, low);
                resultStr[i] = day + " - " + description + " - " + highAndLow;
            }
            return resultStr;
        }

        @Override
        protected String[] doInBackground(String... params) {

            if (params.length == 0){
                return null;
            }

            //Usar API para el Clima con peticiones HTTP
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            //Aqui se almacenara la respuesta JSON como String
            String forecastJsonStr = null;

            String format = "json";
            String units = "metric";
            int numDays = 7;

            try {
                //Construir la Url para la consulta OpenWeatherMap con los posibles
                //parametros disponibles en http://openweathermap.org/API#forecast
                final String FORECAST_BASE_URL =
                        "http://api.openweathermap.org/data/2.5/forecast/daily?";
                final String QUERY_PARAM = "q";
                final String FORMAT_PARAM = "mode";
                final String UNITS_PARAM = "units";
                final String DAYS_PARAM = "cnt";

                Uri builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                        .appendQueryParameter(QUERY_PARAM, params[0])
                        .appendQueryParameter(FORMAT_PARAM, format)
                        .appendQueryParameter(UNITS_PARAM, units)
                        .appendQueryParameter(DAYS_PARAM, Integer.toString(numDays))
                        .build();

                URL url = new URL(builtUri.toString());
                //Crear la peticion a OpenWeatherMap y abrir la conexion
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                //Leer el Input Stream y guardarlo como String
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null){
                    //Nada que hacer
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null){
                    //Mientras sea JSON, agregar una nueva linea no es necesario
                    //Pero esto hace la depuracion mas sencilla si se quiere imprimir
                    //el buffer completo
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0){
                    //Stream esta vacio
                    return null;
                }
                forecastJsonStr = buffer.toString();

            }catch (IOException e){
                Log.e(LOG_TAG, "Error", e);
                //Si no se consiguieron los datos del clima con exito no tiene caso analizarlo
                return null;
            }finally {
                if (urlConnection != null){
                    urlConnection.disconnect();
                }
                if (reader != null){
                    try {
                        reader.close();
                    }catch (final IOException e){
                        Log.e(LOG_TAG, "Error closing stream", e);
                    }
                }
            }
            try {
                return getWeatherDataFromJson(forecastJsonStr, numDays);
            }catch (JSONException e){
                Log.e(LOG_TAG, e.getMessage(), e);
                e.printStackTrace();
            }

            //Esto solo ocurrira si hubo un error obteniendo o formateando el forecast
            return null;
        }

        @Override
        protected void onPostExecute(String[] result) {
            if (result != null){
                mForecastAdapter.clear();
                for (String dayForecastStr : result){
                    mForecastAdapter.add(dayForecastStr);
                }
                //Nuevos datos vienen desde el servidor
            }
        }
    }
}
