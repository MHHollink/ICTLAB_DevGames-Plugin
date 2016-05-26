package nl.devgames.jenkinsplugins.devgames_publisher.exceptions;

public class KnownInternalServerErrorException extends Exception {

    public KnownInternalServerErrorException() {
    }

    public KnownInternalServerErrorException(String message) {
        super(message);
    }
}
