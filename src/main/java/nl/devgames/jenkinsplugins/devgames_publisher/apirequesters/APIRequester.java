package nl.devgames.jenkinsplugins.devgames_publisher.apirequesters;

public interface APIRequester<T> {
    T getObjectFromAPI(String url, String basicAuthUsername, String basicAuthPassword);
}
