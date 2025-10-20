package com.example.languagetranslator;

import static android.R.layout.simple_spinner_item;

import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.ObjectMapper;

public class MainActivity extends AppCompatActivity {

    Button translateButton;
    EditText fromtext;
    TextView totext;
    Spinner fromlanguage, tolangugae;
    ImageButton clearText, playDestinationAudio, playSourceAudio;

    TranslatedResponse currentResponse;
    Translator translator;

    MediaPlayer mediaPlayer;
    List<String> languages;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        translateButton = findViewById(R.id.translate);
        fromtext = findViewById(R.id.fromtext);
        totext = findViewById(R.id.totext);
        fromlanguage = findViewById(R.id.fromlanguage);
        tolangugae = findViewById(R.id.tolanguage);
        clearText = findViewById(R.id.clearText);
        playDestinationAudio = findViewById(R.id.playDestinationAudio);
        playSourceAudio = findViewById(R.id.playSourceAudio);

        translator = new Translator();
        currentResponse = new TranslatedResponse();

        mediaPlayer = new MediaPlayer();

        languages = Arrays.asList(translator.Languages);
        ArrayAdapter<String> toAdapter = new ArrayAdapter<>(this, simple_spinner_item,languages);
        toAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        tolangugae.setAdapter(toAdapter);
        tolangugae.setSelection(7);

        List<String> fromLanguages = new ArrayList<>();
        fromLanguages.add("Detect Language");
        fromLanguages.addAll(languages);
        ArrayAdapter<String> fromAdapter = new ArrayAdapter<>(this,simple_spinner_item,fromLanguages);
        fromAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fromlanguage.setAdapter(fromAdapter);
        fromlanguage.setSelection(0);

        ImageView swap = findViewById(R.id.swap);
        Animation rotate = AnimationUtils.loadAnimation(this, R.anim.rotate_swap);

        swap.setOnClickListener(v -> {
            

            int from = fromlanguage.getSelectedItemPosition() - 1;
            if(from == -1){
                return;
            }

            int to = tolangugae.getSelectedItemPosition() + 1;

            fromlanguage.setSelection(to);
            tolangugae.setSelection(from);

            String translatedText = totext.getText().toString();
            if(translatedText.equals("Translating...")){
                return;
            }

            fromtext.setText(translatedText);
            v.startAnimation(rotate);
            translate();

        });

        translateButton.setOnClickListener(v -> {
            translate();
        });

        clearText.setOnClickListener(v -> {
            fromtext.setText("");
        });

        playDestinationAudio.setOnClickListener(v -> {
            playAudio(currentResponse.destinationAudio);
        });

        playSourceAudio.setOnClickListener(v -> {
            playAudio(currentResponse.sourceAudio);
        });

        fromtext.setOnKeyListener((v, keyCode, event) -> {
            playSourceAudio.setVisibility(TextView.INVISIBLE);
            playDestinationAudio.setVisibility(TextView.INVISIBLE);
            return false;
        });

        fromtext.addTextChangedListener(new TextWatcher() {


            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // called before text is changed
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                playSourceAudio.setVisibility(TextView.INVISIBLE);
                playDestinationAudio.setVisibility(TextView.INVISIBLE);
            }

            @Override
            public void afterTextChanged(Editable s) {
                // called after text is changed
            }
        });
    }

    private CompletableFuture<Void> playAudio(String audioUrl) {
        return CompletableFuture.runAsync(() -> {
            MediaPlayer mediaPlayer = new MediaPlayer();
            try {
                mediaPlayer.setDataSource(audioUrl);
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

                // prepare and start must happen on main thread
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    mediaPlayer.setOnPreparedListener(MediaPlayer::start);
                    mediaPlayer.prepareAsync();
                });

                mediaPlayer.setOnCompletionListener(mp -> {
                    mp.stop();
                    mp.release();
                });

            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }


    private void translate(){
        String fromCode = translator.langHashMap.get(fromlanguage.getSelectedItem().toString());
        String toCode = translator.langHashMap.get(tolangugae.getSelectedItem().toString());


        totext.setText("Translating...");
        totext.setTextColor(Color.GRAY);
        translateButton.setAlpha(0.5f);
        translateButton.setEnabled(false);
        CompletableFuture<TranslatedResponse> future;
        if(fromCode == null){
            future = translator.translate(fromtext.getText().toString(),toCode);
        }
        else{
            future = translator.translate(fromtext.getText().toString(),toCode,fromCode);
        }

        future.thenAccept(translatedResponse -> {
            runOnUiThread(() -> {
                totext.setText(translatedResponse.responseText);
                totext.setTextColor(Color.BLACK);
                currentResponse = translatedResponse;

                playSourceAudio.setVisibility(TextView.VISIBLE);
                playDestinationAudio.setVisibility(TextView.VISIBLE);

                String currentLanguage = translator.langCodeHashMap.get(currentResponse.sourceCode);
                int index = languages.indexOf(currentLanguage);
                fromlanguage.setSelection(index+1);

                translateButton.setAlpha(1f);
                translateButton.setEnabled(true);

            });
        });
    }
}

class Translator {
    String[] Languages;
    String[] LanguageCodes;
    HashMap<String, String> langHashMap;
    HashMap<String, String> langCodeHashMap;
    public Translator(){
        CompletableFuture<HashMap<String, String>> task = getLanguageHashMap();
        langHashMap = task.join();
        LanguageCodes = langHashMap.values().toArray(new String[0]);
        Languages = langHashMap.keySet().toArray(new String[0]);
    }


    public CompletableFuture<TranslatedResponse> translate(String Text, String targetLangCode, String srcLangCode){
        String text = Text.replace(" ", "%20");
        return CompletableFuture.supplyAsync(() -> {
            try{

                String translateUrl = "https://ftapi.pythonanywhere.com/translate?sl="+srcLangCode+"&dl="+targetLangCode+"&text="+text;

                URL url = new URL(translateUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();

                    String json = response.toString().trim();

                    ObjectMapper mapper = new ObjectMapper();
                    HashMap responseMap = mapper.readValue(json, HashMap.class);
                    HashMap pronunciationMap = (HashMap) responseMap.get("pronunciation");

                    String responseText = responseMap.get("destination-text").toString();
                    String sourceCode = responseMap.get("source-language").toString();
                    String sourceAudio = pronunciationMap.get("source-text-audio").toString();
                    String destinationAudio = pronunciationMap.get("destination-text-audio").toString();
//
                    return new TranslatedResponse(responseText,sourceCode,sourceAudio,destinationAudio);
                }
                else {
                    System.out.println("API request failed with response code: " + responseCode);
                }
                connection.disconnect();

            }
            catch(Exception e){
                e.printStackTrace();
            }
            return new TranslatedResponse();
        });
    }

    public CompletableFuture<TranslatedResponse> translate(String Text, String targetLangCode){
        String text = Text.replace(" ", "%20");
        return CompletableFuture.supplyAsync(() -> {
            try{

                String translateUrl = "https://ftapi.pythonanywhere.com/translate?dl="+targetLangCode+"&text="+text;

                URL url = new URL(translateUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();

                    String json = response.toString().trim();

                    ObjectMapper mapper = new ObjectMapper();
                    HashMap responseMap = mapper.readValue(json, HashMap.class);
                    HashMap pronunciationMap = (HashMap) responseMap.get("pronunciation");

                    String responseText = responseMap.get("destination-text").toString();
                    String sourceCode = responseMap.get("source-language").toString();
                    String sourceAudio = pronunciationMap.get("source-text-audio").toString();
                    String destinationAudio = pronunciationMap.get("destination-text-audio").toString();
//
                    return new TranslatedResponse(responseText,sourceCode,sourceAudio,destinationAudio);
                }
                else {
                    System.out.println("API request failed with response code: " + responseCode);
                }
                connection.disconnect();

            }
            catch(Exception e){
                e.printStackTrace();
            }
            return new TranslatedResponse();
        });
    }

    CompletableFuture<HashMap<String, String>> getLanguageHashMap() {
        return CompletableFuture.supplyAsync(() -> {
            HashMap<String, String> map = new HashMap<>();
            try {
                URL url = new URL("https://ftapi.pythonanywhere.com/languages");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    in.close();

                    String json = response.toString().trim();
                    if (json.startsWith("{") && json.endsWith("}")) {
                        json = json.substring(1, json.length() - 1); // remove { }
                        String[] entries = json.split(",");
                        for (String entry : entries) {
                            String[] kv = entry.split(":", 2);
                            if (kv.length == 2) {
                                String key = kv[1].trim().replaceAll("^\"|\"$", "");
                                String value = kv[0].trim().replaceAll("^\"|\"$", "");
                                map.put(key, value);
                            }
                        }
                    }

                    json = response.toString().trim();
                    ObjectMapper mapper = new ObjectMapper();
                    langCodeHashMap = mapper.readValue(json, HashMap.class);

                } else {
                    System.out.println("API request failed with response code: " + responseCode);
                }
                connection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return map;
        });
    }
}

class TranslatedResponse{
    String responseText;
    String sourceCode;
    String sourceAudio;
    String destinationAudio;

    TranslatedResponse(){
        responseText = "";
        sourceCode = "";
        sourceAudio = "";
        destinationAudio = "";
    }

    TranslatedResponse(String responseText, String sourceCode, String sourceAudio, String destinationAudio){
        this.responseText = responseText;
        this.sourceCode = sourceCode;
        this.sourceAudio = sourceAudio;
        this.destinationAudio = destinationAudio;
    }
}
