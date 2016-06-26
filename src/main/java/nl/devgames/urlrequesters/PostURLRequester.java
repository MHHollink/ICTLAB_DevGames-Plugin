package nl.devgames.urlrequesters;

import java.util.Map;

public interface PostURLRequester {
    Response postJsonToUrl(String json, String url, Map<String, String> additionalHeaders) throws Exception;
    Response postJsonToUrl(String json, String url, Map<String, String> additionalHeaders, String basicAuthUsername, String basicAuthPassword) throws Exception;
}