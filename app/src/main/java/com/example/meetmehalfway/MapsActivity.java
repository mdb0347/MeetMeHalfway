package com.example.meetmehalfway;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.PolyUtil;
import com.google.maps.android.SphericalUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {
    public static LatLng addr1;
    public static LatLng addr2;
    public static LatLng center;
    public int radius;
    private GoogleMap mMap;
    MarkerOptions place1, place2;
    ArrayList markerPoints= new ArrayList();
    Circle circle;
    String locationType;
    boolean safeLocations;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        Toolbar toolbar = findViewById(R.id.map_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Meet Me halfway");

        addr1 = getIntent().getParcelableExtra("Addr1LatLng");
        addr2 = getIntent().getParcelableExtra("Addr2LatLng");
        radius = 1;
    }

    //This is where the radius gets updated
    public void updateRadius(SharedPreferences prefs) {
        String radiusPref = prefs.getString("radius_key", "1");

        if(circle != null) {
            circle.remove();
            mMap.clear();
            // if radius is valid, try will execute
            try {
                radius = Integer.parseInt(radiusPref);
            }
            // if the radius is not valid, the catch will execute.
            catch (NumberFormatException e) {
                radiusError(MapsActivity.this);
                radius = 1;
            }
        } else {
            radius = 1;
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("radius_key", "1");
            editor.apply();
        }
    }

    public void safeLocationsOnly(SharedPreferences prefs) {
        safeLocations = prefs.getBoolean("safe_places_key", false);
    }

    public void updateLocationType(SharedPreferences prefs) {
        locationType = prefs.getString("place_type_key", "restaurant");
        if (safeLocations) {
            locationType = "police";
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch(item.getItemId()) {
            case R.id.map_options:
                Intent modifyMapOptions=new Intent(MapsActivity.this,MapsOptionsActivity.class);
                startActivity(modifyMapOptions);
                return true;

            case R.id.action_about:
                final EditText aboutUs = new EditText(this);
                AlertDialog about = new AlertDialog.Builder(this)
                        .setTitle(R.string.about_title)
                        .setMessage(R.string.about_descr)
                        .setView(aboutUs)
                        .setPositiveButton("OK", null)
                        .create();
                about.show();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }
    public void OpenLandingActivity() {
        Intent intent = new Intent(this, LandingPage.class);
        Bundle bundle = new Bundle();
        intent.putExtras(bundle);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        }
    }
    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        updateRadius(prefs);
        safeLocationsOnly(prefs);
        updateLocationType(prefs);

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onPause() {
        super.onPause();
    }
    /**
     * Called when the map is ready.
     */
    @Override
    public void onMapReady(GoogleMap map) {
        mMap = map;
        place1 = new MarkerOptions().position(addr1);
        mMap.addMarker(place1.title("Address 1"));
        place2 = new MarkerOptions().position(addr2);
        mMap.addMarker(place2.title("Address 2"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(addr1));

        markerPoints.add(addr1);
        markerPoints.add(addr2);

        String url = getDirectionsUrl(place1.getPosition(), place2.getPosition());
        FetchUrl fetchUrl = new FetchUrl();
        // Start downloading json data from Google Directions API
        fetchUrl.execute(url);
    }

    private class FetchUrl extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... url) {
            // For storing data from web service
            String data = "";

            try {
                // Fetching the data from web service
                data = downloadUrl(url[0]);
                Log.d("Background Task data", data.toString());
            } catch (Exception e) {
                Log.d("Background Task", e.toString());
            }
            return data;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            ParserTask parserTask = new ParserTask();
            // Invokes the thread for parsing the JSON data
            parserTask.execute(result);

        }
    }

    private String downloadUrl(String strUrl) throws IOException {
        String data = "";
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(strUrl);
            // Creating an http connection to communicate with url
            urlConnection = (HttpURLConnection) url.openConnection();
            // Connecting to url
            urlConnection.connect();
            // Reading data from url
            iStream = urlConnection.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(iStream));
            StringBuffer sb = new StringBuffer();
            String line = "";
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            data = sb.toString();
            Log.d("downloadUrl", data.toString());
            br.close();
        } catch (Exception e) {
            Log.d("Exception", e.toString());
        } finally {
            iStream.close();
            urlConnection.disconnect();
        }
        return data;
    }

    public String getDirectionsUrl(LatLng origin, LatLng dest) {
        String str_origin = "origin=" + origin.latitude + "," + origin.longitude;
        String str_dest = "destination=" + dest.latitude + "," + dest.longitude;
        String mode = "mode=driving";
        String parameters = str_origin + "&" + str_dest + "&" + mode;
        String url = "https://maps.googleapis.com/maps/api/directions/json?"
                + parameters + "&key=" + getString(R.string.google_maps_key);
        return url;
    }

    private class ParserTask extends AsyncTask<String, Integer, List<List<HashMap<String, String>>>> {
        // Parsing the data in non-ui thread
        @Override
        protected List<List<HashMap<String, String>>> doInBackground(String... jsonData) {

            JSONObject jObject;
            List<List<HashMap<String, String>>> routes = null;

            try {
                jObject = new JSONObject(jsonData[0]);
                Log.d("ParserTask",jsonData[0].toString());
                DataParser parser = new DataParser();
                Log.d("ParserTask", parser.toString());

                // Starts parsing data
                routes = parser.parse(jObject);
                Log.d("ParserTask","Executing routes");
                Log.d("ParserTask",routes.toString());

            } catch (Exception e) {
                Log.d("ParserTask",e.toString());
                e.printStackTrace();
            }
            return routes;
        }

        private LatLng extrapolate(List<LatLng> path, LatLng origin, double distance) {
            LatLng extrapolated = null;

            if (!PolyUtil.isLocationOnPath(origin, path, false, 1)) { // If the location is not on path non geodesic, 1 meter tolerance
                return null;
            }

            float accDistance = 0f;
            boolean foundStart = false;
            List<LatLng> segment = new ArrayList<>();

            for (int i = 0; i < path.size() - 1; i++) {
                LatLng segmentStart = path.get(i);
                LatLng segmentEnd = path.get(i + 1);

                segment.clear();
                segment.add(segmentStart);
                segment.add(segmentEnd);

                double currentDistance = 0d;

                if (!foundStart) {
                    if (PolyUtil.isLocationOnPath(origin, segment, false, 1)) {
                        foundStart = true;

                        currentDistance = SphericalUtil.computeDistanceBetween(origin, segmentEnd);

                        if (currentDistance > distance) {
                            double heading = SphericalUtil.computeHeading(origin, segmentEnd);
                            extrapolated = SphericalUtil.computeOffset(origin, distance - accDistance, heading);
                            break;
                        }
                    }
                } else {
                    currentDistance = SphericalUtil.computeDistanceBetween(segmentStart, segmentEnd);

                    if (currentDistance + accDistance > distance) {
                        double heading = SphericalUtil.computeHeading(segmentStart, segmentEnd);
                        extrapolated = SphericalUtil.computeOffset(segmentStart, distance - accDistance, heading);
                        break;
                    }
                }

                accDistance += currentDistance;
            }

            return extrapolated;
        }

        // Executes in UI thread, after the parsing process
        @Override
        protected void onPostExecute(List<List<HashMap<String, String>>> result) {
            ArrayList<LatLng> points;
            PolylineOptions lineOptions = null;

            // Traversing through all the routes
            for (int i = 0; i < result.size(); i++) {
                points = new ArrayList<>();
                lineOptions = new PolylineOptions();

                // Fetching i-th route
                List<HashMap<String, String>> path = result.get(i);

                // Fetching all the points in i-th route
                for (int j = 0; j < path.size(); j++) {
                    HashMap<String, String> point = path.get(j);

                    double lat = Double.parseDouble(point.get("lat"));
                    double lng = Double.parseDouble(point.get("lng"));
                    LatLng position = new LatLng(lat, lng);

                    points.add(position);
                }
                // Adding all the points in the route to LineOptions
                lineOptions.addAll(points);
                lineOptions.width(10);
                lineOptions.color(Color.RED);

                Log.d("onPostExecute","onPostExecute lineoptions decoded");
            }

            // Drawing polyline in the Google Map for the i-th route
            if(lineOptions != null) {
                mMap.addPolyline(lineOptions);
            }
            else {
                Log.d("onPostExecute","without Polylines drawn");
            }

            double middleDistance = SphericalUtil.computeLength(lineOptions.getPoints());

            center = extrapolate(lineOptions.getPoints(), lineOptions.getPoints().get(0), (middleDistance/2));

            mMap.addMarker(new MarkerOptions().position(center).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
            CameraPosition cameraPosition = new CameraPosition.Builder()
                    .target(center)
                    .zoom(10).build();

            //creates radius circle
             circle = mMap.addCircle(new CircleOptions()
                    .center(center)
                    .radius(radius*1609.34)
                    .strokeColor(Color.BLUE));

            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    center, getZoomLevel(circle)));

            StringBuilder pnValue = new StringBuilder(pnMethod(center, radius, locationType));
            PlacesTask placesTask = new PlacesTask();
            placesTask.execute(pnValue.toString()); //OG

        }
    }

    class DataParser {

        List<List<HashMap<String,String>>> parse(JSONObject jObject){

            List<List<HashMap<String, String>>> routes = new ArrayList<>() ;
            JSONArray jRoutes;
            JSONArray jLegs;
            JSONArray jSteps;

            try {

                jRoutes = jObject.getJSONArray("routes");

                /** Traversing all routes */
                for(int i=0;i<jRoutes.length();i++){
                    jLegs = ( (JSONObject)jRoutes.get(i)).getJSONArray("legs");
                    List path = new ArrayList<>();

                    /** Traversing all legs */
                    for(int j=0;j<jLegs.length();j++){
                        jSteps = ( (JSONObject)jLegs.get(j)).getJSONArray("steps");

                        /** Traversing all steps */
                        for(int k=0;k<jSteps.length();k++){
                            String polyline = "";
                            polyline = (String)((JSONObject)((JSONObject)jSteps.get(k)).get("polyline")).get("points");
                            List<LatLng> list = decodePoly(polyline);

                            /** Traversing all points */
                            for(int l=0;l<list.size();l++){
                                HashMap<String, String> hm = new HashMap<>();
                                hm.put("lat", Double.toString((list.get(l)).latitude) );
                                hm.put("lng", Double.toString((list.get(l)).longitude) );
                                path.add(hm);
                            }
                        }
                        routes.add(path);
                    }
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }catch (Exception e){
            }
            return routes;
        }

        /**
         * Method to decode polyline points
         * */
        private List<LatLng> decodePoly(String encoded) {

            List<LatLng> poly = new ArrayList<>();
            int index = 0, len = encoded.length();
            int lat = 0, lng = 0;

            while (index < len) {
                int b, shift = 0, result = 0;
                do {
                    b = encoded.charAt(index++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
                lat += dlat;

                shift = 0;
                result = 0;
                do {
                    b = encoded.charAt(index++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
                lng += dlng;

                LatLng p = new LatLng((((double) lat / 1E5)),
                        (((double) lng / 1E5)));
                poly.add(p);
            }

            return poly;
        }
    }

    protected void onPostExecute(List<List<HashMap<String, String>>> result) {
        ArrayList<LatLng> points;
        PolylineOptions lineOptions = null;

        // Traversing through all the routes
        for (int i = 0; i < result.size(); i++) {
            points = new ArrayList<>();
            lineOptions = new PolylineOptions();

            // Fetching i-th route
            List<HashMap<String, String>> path = result.get(i);

            // Fetching all the points in i-th route
            for (int j = 0; j < path.size(); j++) {
                HashMap<String, String> point = path.get(j);

                double lat = Double.parseDouble(point.get("lat"));
                double lng = Double.parseDouble(point.get("lng"));
                LatLng position = new LatLng(lat, lng);

                points.add(position);
            }

            // Adding all the points in the route to LineOptions
            lineOptions.addAll(points);
            lineOptions.width(10);
            lineOptions.color(Color.RED);

            Log.d("onPostExecute","onPostExecute lineoptions decoded");

        }
        // Drawing polyline in the Google Map for the i-th route
        if(lineOptions != null) {
            mMap.addPolyline(lineOptions);
        }
        else {
            Log.d("onPostExecute","without Polylines drawn");
        }
    }

    public StringBuilder pnMethod(LatLng cent, int rad, String locationType) {
        Log.d("latitude = "+ cent.latitude, "longitude = "+ cent.longitude);

        StringBuilder pn = new StringBuilder("https://maps.googleapis.com/maps/api/place/nearbysearch/json?");
        pn.append("location=" + cent.latitude + "," + cent.longitude);

        pn.append("&radius=" +(rad*1609));
       if (locationType != "") {
           pn.append("&types=" + locationType);
       }
       else {
           pn.append("");
       }
        pn.append("&sensor=true");
        //Key value = AlzaSyBguDOBAg_Zi2K5DAFRO83idl4ucvNhyGo
        pn.append("&key=" + getString(R.string.google_maps_key));

        Log.d("Map", "api: " + pn.toString());

        return pn;
    }

    private class PlacesTask extends AsyncTask<String, Integer, String> {
        // Invoked by execute() method of this object
        @Override
        protected String doInBackground(String... url) {

            String data = "";
            try {
                data = downloadUrl(url[0]);
            } catch (Exception e) {
                Log.d("Background Task", e.toString());
            }
            return data;
        }

        // Executed after the complete execution of doInBackground() method
        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            ParsingNearby parseNearby = new ParsingNearby();
            // Start parsing the Google places in JSON format
            // Invokes the "doInBackground()" method of the class ParserTask
            parseNearby.execute(result);
        }
    }

    private class ParsingNearby extends AsyncTask<String, Integer, List<HashMap<String, String>>> {
        // Invoked by execute() method of this object
        @Override
        protected List<HashMap<String, String>> doInBackground(String... jsonData) {

            JSONObject jObject;
            MarkerOptions pn1;
            List<HashMap<String, String>> places = null;

            try {
                jObject = new JSONObject(jsonData[0]);
                Log.d("ParsingNearby", jsonData[0].toString());
                Place_JSON placeJson = new Place_JSON();
                Log.d("ParsingNearby", placeJson.toString());

                places = placeJson.parse(jObject);
                Log.d("ParsingNearby", "Executing nearby places");
                Log.d("ParsingNearby", places.toString());

            } catch (Exception e) {
                Log.d("Exception", e.toString());
                e.printStackTrace();
            }
            return places;
        }

        // Executed after the complete execution of doInBackground() method
        @Override
        protected void onPostExecute(List<HashMap<String, String>> list) {

            Log.d("Map", "list size: " + list.size());



                if (list.size() <= 0)
                {
                    final EditText noMarkers = new EditText(MapsActivity.this);
                    AlertDialog markers = new AlertDialog.Builder(MapsActivity.this)
                            .setTitle("No Results")
                            .setMessage("Please Enter A Larger Radius")
                            .setView(noMarkers)
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Intent modifyMapOptions=new Intent(MapsActivity.this,MapsOptionsActivity.class);
                                    startActivity(modifyMapOptions);
                                }
                            })
                            .create();
                    markers.show();

                }


                for (int i = 0; i < list.size(); i++) {
                // Creating a marker
                MarkerOptions markerOptions = new MarkerOptions();
                // Getting a place from the places list
                HashMap<String, String> hmPlace = list.get(i);
                // Getting latitude of the place
                double lat = Double.parseDouble(hmPlace.get("lat"));
                // Getting longitude of the place
                double lng = Double.parseDouble(hmPlace.get("lng"));
                // Getting name
                String name = hmPlace.get("place_name");
                Log.d("Map", "place: " + name);
                // Getting vicinity
                String vicinity = hmPlace.get("vicinity");
                LatLng latLng = new LatLng(lat, lng);
                // Setting the position for the marker
                    if (!name.equals(vicinity)) {
                        Log.d("Name: " , name);
                        Log.d("Vicinity" , vicinity);

                markerOptions.position(latLng);
                markerOptions.title(name + " : " + vicinity); //OG
                markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_MAGENTA));
                    // Placing a marker on the touched position
                    mMap.addMarker(markerOptions);
                }
            }
        }
    }

    public class Place_JSON {
        //Receives a JSONObject and returns a list
        public List<HashMap<String, String>> parse(JSONObject jObject) {

            JSONArray jPlaces = null;
            try {
                // Retrieves all the elements in the 'places' array */
                jPlaces = jObject.getJSONArray("results");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            // Invoking getPlaces with the array of json object where each json object represent a place
            return getPlaces(jPlaces);
        }

        private List<HashMap<String, String>> getPlaces(JSONArray jPlaces) {
            int placesCount = jPlaces.length();
            List<HashMap<String, String>> placesList = new ArrayList<HashMap<String, String>>();
            HashMap<String, String> place = null;

            // Taking each place, parses and adds to list object */
            for (int i = 0; i < placesCount; i++) {
                try {
                    // Call getPlace with place JSON object to parse the place */
                    place = getPlace((JSONObject) jPlaces.get(i));
                    placesList.add(place);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            return placesList;
        }

        // Parsing the Place JSON object
        private HashMap<String, String> getPlace(JSONObject jPlace) {

            HashMap<String, String> place = new HashMap<String, String>();
            String placeName = "-NA-";
            String vicinity = "-NA-";
            String latitude = "";
            String longitude = "";
            String reference = "";
            //String placeAddress = "-NA-"

            try {
                // Extracting Place name, if available
                if (!jPlace.isNull("name")) {
                    placeName = jPlace.getString("name");
                }

                // Extracting the Places Nearby, if available
                if (!jPlace.isNull("vicinity")) {
                    vicinity = jPlace.getString("vicinity");
                }

                latitude = jPlace.getJSONObject("geometry").getJSONObject("location").getString("lat");
                longitude = jPlace.getJSONObject("geometry").getJSONObject("location").getString("lng");
                reference = jPlace.getString("reference");

                place.put("place_name", placeName);
                place.put("vicinity", vicinity);
                place.put("lat", latitude);
                place.put("lng", longitude);
                place.put("reference", reference);

            } catch (JSONException e) {
                e.printStackTrace();
            }
            return place;
        }
    }

    public int getZoomLevel(Circle circle) {
        int zoomLevel = 11;
        if (circle != null) {
            double radius = circle.getRadius() + circle.getRadius() / 2;
            double scale = radius / 500;
            zoomLevel = (int) (16 - Math.log(scale) / Math.log(2));
        }
        return zoomLevel;
    }

    private void radiusError (Context context) {
        final EditText fixRadius = new EditText(context);
        AlertDialog message = new AlertDialog.Builder(context)
                .setTitle("Invalid Radius")
                .setMessage("Please enter a valid Radius")
                .setView(fixRadius)
                .setPositiveButton("OK", null)
                .create();
        message.show();
    }
}



