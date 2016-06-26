package nl.devgames.jenkinsplugins.devgames_publisher.apirequesters;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import nl.devgames.jenkinsplugins.devgames_publisher.deserializers.SonarDuplicationsDeserializer;
import nl.devgames.jenkinsplugins.devgames_publisher.models.sonarqube.SonarDuplications;
import nl.devgames.urlrequesters.GetURLRequester;
import nl.devgames.urlrequesters.UnirestURLRequester;

public class SonarQubeDuplicationsAPIRequester implements APIRequester<SonarDuplications> {
    private GetURLRequester urlRequester;

    public SonarQubeDuplicationsAPIRequester(GetURLRequester urlRequester) {
        this.urlRequester = urlRequester;
    }

    @Override
    public SonarDuplications getObjectFromAPI(String url, String basicAuthUsername, String basicAuthPassword) {
        try {
            GsonBuilder gsonBuilder     = new GsonBuilder()
                    .registerTypeAdapter(SonarDuplications.class, new SonarDuplicationsDeserializer());
            Gson gson                   = gsonBuilder.create();

            String json = urlRequester.getJsonFromUrl(url, null, basicAuthUsername, basicAuthPassword);
            return gson.fromJson(json, SonarDuplications.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
