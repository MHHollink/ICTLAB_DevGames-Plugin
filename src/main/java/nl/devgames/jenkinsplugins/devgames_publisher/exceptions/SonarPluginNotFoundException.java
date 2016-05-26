package nl.devgames.jenkinsplugins.devgames_publisher.exceptions;

public class SonarPluginNotFoundException extends Exception {

    public SonarPluginNotFoundException() {
    }

    public SonarPluginNotFoundException(String message) {
        super(message);
    }
}
