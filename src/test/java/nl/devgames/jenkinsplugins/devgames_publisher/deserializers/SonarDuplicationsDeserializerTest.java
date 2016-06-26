package nl.devgames.jenkinsplugins.devgames_publisher.deserializers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import nl.devgames.jenkinsplugins.devgames_publisher.models.sonarqube.SonarDuplications;
import org.junit.Test;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class SonarDuplicationsDeserializerTest {

    @Test
    public void testSonarDuplicationJsonGetsParsedCorrectly() throws Exception {
        File file = new File(
                this.getClass().getClassLoader().getResource("SonarDuplications.json").getFile().replaceAll("%20"," ")
        );
        byte[] bytes = Files.readAllBytes(file.toPath());
        String json = new String(bytes, Charset.defaultCharset());
        GsonBuilder gsonBuilder = new GsonBuilder()
                .registerTypeAdapter(SonarDuplications.class, new SonarDuplicationsDeserializer());
        Gson gson = gsonBuilder.create();

        SonarDuplications sonarDuplications = gson.fromJson(json, SonarDuplications.class);
        assertTrue(sonarDuplications != null);
    }

    @Test(expected = ClassCastException.class)
    public void testWrongDuplicationsJsonThrowsException() throws Exception {
        File file = new File(
                this.getClass().getClassLoader().getResource("wrongSonarDuplications.json").getFile().replaceAll("%20"," ")
        );
        byte[] bytes = Files.readAllBytes(file.toPath());
        String json = new String(bytes, Charset.defaultCharset());
        GsonBuilder gsonBuilder = new GsonBuilder()
                .registerTypeAdapter(SonarDuplications.class, new SonarDuplicationsDeserializer());
        Gson gson = gsonBuilder.create();

        SonarDuplications sonarDuplications = gson.fromJson(json, SonarDuplications.class);
    }
}