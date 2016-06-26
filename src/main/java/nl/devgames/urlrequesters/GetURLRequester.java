package nl.devgames.urlrequesters;

import java.util.Map;

public interface GetURLRequester {
    String getJsonFromUrl(String url, Map<String, String> additionalHeaders) throws Exception;
    String getJsonFromUrl(String url, Map<String, String> additionalHeaders, String basicAuthUsername, String basicAuthPassword) throws Exception;
}
