package nl.devgames.jenkinsplugins.devgames_publisher.helpers.http;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.HttpRequest;
import com.mashape.unirest.request.HttpRequestWithBody;
import nl.devgames.jenkinsplugins.devgames_publisher.helpers.Tuple;

import java.util.Map;

/**
 * Helper class which handles getting and posting JSON from/to an URL.
 */
public class URLRequester {

    private URLRequester() {
    }

    public static String getJsonFromURL(String url, Map<String, String> additionalHeaders, Map<String, String> routeParameters, Tuple<String, String> basicAuthParams) throws UnirestException {
        HttpRequest request = Unirest.get(url);

        if(additionalHeaders != null && !additionalHeaders.isEmpty())
            request = request.headers(additionalHeaders);

        if(routeParameters != null && !routeParameters.isEmpty()) {
            for (Map.Entry<String, String> entry : routeParameters.entrySet()) {
                request = request.routeParam(entry.getKey(), entry.getValue());
            }
        }

        if(basicAuthParams != null) {
            request = request.basicAuth(basicAuthParams.getKey(), basicAuthParams.getValue());
        }

        HttpResponse<JsonNode> response = request.asJson();
        return response.getBody().toString();
    }

    public static PostReturnObject postJsonToURL(String json, String url, Map<String, String> additionalHeaders, Map<String, String> routeParameters, Tuple<String, String> basicAuthParams) throws UnirestException {
        HttpRequestWithBody request = Unirest.post(url);

        if(additionalHeaders != null && !additionalHeaders.isEmpty())
            request = request.headers(additionalHeaders);

        if(routeParameters != null && !routeParameters.isEmpty()) {
            for (Map.Entry<String, String> entry : routeParameters.entrySet()) {
                request = request.routeParam(entry.getKey(), entry.getValue());
            }
        }

        if(basicAuthParams != null) {
            request = request.basicAuth(basicAuthParams.getKey(), basicAuthParams.getValue());
        }

        HttpResponse<String> response = request.body(json).asString();
        String responseString = response.getBody();
        int responseHttpStatus = response.getStatus();

        PostReturnObject returnObject = new PostReturnObject();
        returnObject.setStatusCode(responseHttpStatus);
        returnObject.setResponseString(responseString);

        return returnObject;
    }
}
