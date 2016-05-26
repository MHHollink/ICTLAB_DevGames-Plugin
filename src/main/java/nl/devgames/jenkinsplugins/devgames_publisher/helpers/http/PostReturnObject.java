package nl.devgames.jenkinsplugins.devgames_publisher.helpers.http;

public class PostReturnObject {
    private int statusCode;
    private String responseString;

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getResponseString() {
        return responseString;
    }

    public void setResponseString(String responseString) {
        this.responseString = responseString;
    }
}
