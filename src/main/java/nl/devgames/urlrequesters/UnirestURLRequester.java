package nl.devgames.urlrequesters;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.BaseRequest;
import com.mashape.unirest.request.HttpRequest;
import com.mashape.unirest.request.HttpRequestWithBody;

import java.util.Map;

public class UnirestURLRequester implements GetURLRequester,PostURLRequester {
    @Override
    public String getJsonFromUrl(String url, Map<String, String> additionalHeaders) throws UnirestException {
        HttpRequest request = Unirest.get(url);
        addHeaders(request, additionalHeaders);
        HttpResponse<JsonNode> response = request.asJson();
        return response.getBody().toString();
    }

    @Override
    public String getJsonFromUrl(String url, Map<String, String> additionalHeaders, String basicAuthUsername, String basicAuthPassword) throws UnirestException {
        HttpRequest request = Unirest.get(url);
        addHeaders(request, additionalHeaders);

        if(!"".equals(basicAuthUsername) || !"".equals(basicAuthPassword))
            request = request.basicAuth(basicAuthUsername, basicAuthPassword);

        HttpResponse<JsonNode> response = request.asJson();
        return response.getBody().toString();
    }

    @Override
    public Response postJsonToUrl(String json, String url, Map<String, String> additionalHeaders) throws UnirestException {
        HttpRequestWithBody request = Unirest.post(url);
        addHeaders(request, additionalHeaders);
        request = request.header("Content-Type","application/json");

        return getResponseFromRequest(request.body(json));
    }

    @Override
    public Response postJsonToUrl(String json, String url, Map<String, String> additionalHeaders, String basicAuthUsername, String basicAuthPassword) throws UnirestException {
        HttpRequestWithBody request = Unirest.post(url);
        addHeaders(request, additionalHeaders);
        request = request.header("Content-Type","application/json");

        if(!"".equals(basicAuthUsername) || !"".equals(basicAuthPassword))
            request = request.basicAuth(basicAuthUsername, basicAuthPassword);

        return getResponseFromRequest(request.body(json));
    }

    private void addHeaders(HttpRequest request, Map<String, String> headers) {
        if(headers != null && !headers.isEmpty())
            request = request.headers(headers);
    }

    private Response getResponseFromRequest(BaseRequest request) throws UnirestException {
        HttpResponse<String> response = request.asString();
        String responseString = response.getBody();
        int responseHttpStatus = response.getStatus();
        return new Response(responseHttpStatus, responseString);
    }
}
