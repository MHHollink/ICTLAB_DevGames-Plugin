package nl.devgames.jenkinsplugins.devgames_publisher.helpers;

/**
 * A class which mimics a tuple
 */
public class Tuple<K,V> {
    private K key;
    private V value;

    public Tuple(K key, V value) {
        this.key = key;
        this.value = value;
    }

    public K getKey() {
        return key;
    }

    public V getValue() {
        return value;
    }
}
