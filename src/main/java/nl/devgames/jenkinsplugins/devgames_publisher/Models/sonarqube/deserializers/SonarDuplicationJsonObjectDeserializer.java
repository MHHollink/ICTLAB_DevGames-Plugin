package nl.devgames.jenkinsplugins.devgames_publisher.Models.sonarqube.deserializers;

import com.google.gson.*;
import nl.devgames.jenkinsplugins.devgames_publisher.Models.sonarqube.SonarDuplicationsJsonObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SonarDuplicationJsonObjectDeserializer implements JsonDeserializer<SonarDuplicationsJsonObject> {

    @Override
    public SonarDuplicationsJsonObject deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        SonarDuplicationsJsonObject sonarDuplicationJsonObject = new SonarDuplicationsJsonObject();

        JsonArray duplicationsArray = jsonObject.getAsJsonArray("duplications");
        List<SonarDuplicationsJsonObject.Duplications> duplications = new ArrayList<>();
        for(JsonElement elem : duplicationsArray){
            JsonObject elementObject = elem.getAsJsonObject();

            SonarDuplicationsJsonObject.Duplications duplication = sonarDuplicationJsonObject.new Duplications();
            List<SonarDuplicationsJsonObject.Duplications.Blocks> blocks = new ArrayList<>();

            JsonArray blocksArray = elementObject.getAsJsonArray("blocks");
            for(JsonElement blockElem : blocksArray){
                JsonObject blockElemObject = blockElem.getAsJsonObject();
                SonarDuplicationsJsonObject.Duplications.Blocks block = duplication.new Blocks();

                block.setFrom(blockElemObject.get("from").getAsInt());
                block.setSize(blockElemObject.get("size").getAsInt());
                block.set_ref(blockElemObject.get("_ref").getAsString());
                blocks.add(block);
            }
            duplication.setBlocks(blocks);
            duplications.add(duplication);
        }

        JsonObject files = jsonObject.getAsJsonObject("files");
        List<SonarDuplicationsJsonObject.Files> filesList = new ArrayList<>();
        for(Map.Entry<String,JsonElement> entry: files.entrySet()){
            JsonObject filesElemObject = entry.getValue().getAsJsonObject();

            SonarDuplicationsJsonObject.Files file = sonarDuplicationJsonObject.new Files();
            file.setRef(entry.getKey());
            file.setKey(filesElemObject.get("key").getAsString());
            file.setName(filesElemObject.get("name").getAsString());
            file.setUuid(filesElemObject.get("uuid").getAsString());
            filesList.add(file);
        }

        sonarDuplicationJsonObject.setDuplications(duplications);
        sonarDuplicationJsonObject.setFiles(filesList);

        return sonarDuplicationJsonObject;
    }
}

