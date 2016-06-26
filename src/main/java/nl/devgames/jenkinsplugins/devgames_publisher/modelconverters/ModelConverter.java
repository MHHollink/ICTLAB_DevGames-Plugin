package nl.devgames.jenkinsplugins.devgames_publisher.modelconverters;

public interface ModelConverter<T,K> {
    K convert(T base);
}
