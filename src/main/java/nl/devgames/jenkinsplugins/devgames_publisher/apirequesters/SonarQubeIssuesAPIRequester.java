package nl.devgames.jenkinsplugins.devgames_publisher.apirequesters;

import com.google.gson.Gson;
import nl.devgames.jenkinsplugins.devgames_publisher.models.sonarqube.SonarIssues;
import nl.devgames.urlrequesters.GetURLRequester;
import nl.devgames.urlrequesters.UnirestURLRequester;

public class SonarQubeIssuesAPIRequester implements APIRequester<SonarIssues> {
    private GetURLRequester urlRequester;

    public SonarQubeIssuesAPIRequester(GetURLRequester urlRequester) {
        this.urlRequester = urlRequester;
    }

    @Override
    public SonarIssues getObjectFromAPI(String url, String basicAuthUsername, String basicAuthPassword) {
        try {
            Gson gson                   = new Gson();

            String json = urlRequester.getJsonFromUrl(url, null, basicAuthUsername, basicAuthPassword);
            return gson.fromJson(json, SonarIssues.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
