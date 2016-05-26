package nl.devgames.jenkinsplugins.devgames_publisher.helpers;

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
