package nl.devgames.urlrequesters;

public class Response {
    private int statusCode;
    private String responseBody;

    public Response(int statusCode, String responseBody) {
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
