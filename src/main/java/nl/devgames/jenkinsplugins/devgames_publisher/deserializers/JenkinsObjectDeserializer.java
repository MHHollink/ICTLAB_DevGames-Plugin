package nl.devgames.jenkinsplugins.devgames_publisher.deserializers;

import com.google.gson.*;
import nl.devgames.jenkinsplugins.devgames_publisher.models.JenkinsObject;

import javax.xml.bind.DatatypeConverter;
import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class JenkinsObjectDeserializer implements JsonDeserializer<JenkinsObject> {
    @Override
    public JenkinsObject deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) {
        JsonObject jsonObject                       = jsonElement.getAsJsonObject();
        JenkinsObject jenkinsObject                 = new JenkinsObject();
        JenkinsObject.ChangeSet changeSet           = jenkinsObject.new ChangeSet();

        String result                               = (jsonObject.get("result") != null)                ? jsonObject.get("result").getAsString()        : null;
        JsonObject changeSetObject                  = jsonObject.getAsJsonObject("changeSet");
        String kind                                 = (changeSetObject.get("kind") != null)             ? changeSetObject.get("kind").getAsString()     : null;
        JsonArray itemsArray                        = changeSetObject.getAsJsonArray("items");

        List<JenkinsObject.ChangeSet.Item> items    = new ArrayList<>();
        for (JsonElement itemElement : itemsArray){
            JsonObject itemObject                   = itemElement.getAsJsonObject();
            JenkinsObject.ChangeSet.Item item       = changeSet.new Item();

            String commitId                         = itemObject.get("commitId").getAsString();
            String msg                              = itemObject.get("msg").getAsString();
            String date                             = itemObject.get("date").getAsString();

            JsonObject authorObject                 = itemObject.get("author").getAsJsonObject();
            String user                             = authorObject.get("absoluteUrl").getAsString();
            String fullName                         = authorObject.get("fullName").getAsString();

            JenkinsObject.ChangeSet.Item.Author author = item.new Author();
            author.setScmUser(user);
            author.setFullName(fullName);

            Date commitDate = null;
            try {
                if ("git".equals(kind)) {
                    SimpleDateFormat dateFormatter  = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
                    commitDate                      = dateFormatter.parse(date);
                } else if ("svn".equals(kind)) {
                    commitDate = DatatypeConverter.parseDate(date).getTime();
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }

            item.setCommitId(commitId);
            item.setAuthor(author);
            item.setDate(commitDate);
            item.setMsg(msg);

            items.add(item);
        }

        changeSet.setKind(kind);
        changeSet.setItems(items);

        jenkinsObject.setResult(result);
        jenkinsObject.setChangeSet(changeSet);

        return jenkinsObject;
    }
}
