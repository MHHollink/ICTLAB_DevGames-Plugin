package nl.devgames.jenkinsplugins.devgames_publisher.apirequesters;

import com.google.gson.Gson;
import nl.devgames.jenkinsplugins.devgames_publisher.models.JenkinsAPIData;
import nl.devgames.urlrequesters.GetURLRequester;
import nl.devgames.urlrequesters.UnirestURLRequester;

public class JenkinsAPIRequester implements APIRequester<JenkinsAPIData> {
    private GetURLRequester urlRequester;

    public JenkinsAPIRequester(GetURLRequester urlRequester) {
        this.urlRequester = urlRequester;
    }

    @Override
    public JenkinsAPIData getObjectFromAPI(String url, String basicAuthUsername, String basicAuthPassword) {
        try {
            Gson gson                   = new Gson();

            String json = urlRequester.getJsonFromUrl(url, null);
            return gson.fromJson(json, JenkinsAPIData.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
