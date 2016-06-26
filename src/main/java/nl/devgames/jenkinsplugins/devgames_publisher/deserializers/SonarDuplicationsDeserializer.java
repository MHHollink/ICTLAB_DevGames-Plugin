package nl.devgames.jenkinsplugins.devgames_publisher.deserializers;

import com.google.gson.*;
import nl.devgames.jenkinsplugins.devgames_publisher.models.sonarqube.SonarDuplications;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SonarDuplicationsDeserializer implements JsonDeserializer<SonarDuplications> {

    @Override
    public SonarDuplications deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext)  {
        JsonObject jsonObject                                               = jsonElement.getAsJsonObject();
        SonarDuplications sonarDuplicationJsonObject                  = new SonarDuplications();
        List<SonarDuplications.Duplication> duplications              = new ArrayList<>();
        List<SonarDuplications.File> filesList                        = new ArrayList<>();

        JsonArray duplicationsArray                                         = jsonObject.getAsJsonArray("duplications");
        for(JsonElement duplicationElement : duplicationsArray){
            JsonObject duplicationObject                                    = duplicationElement.getAsJsonObject();

            SonarDuplications.Duplication duplication                 = sonarDuplicationJsonObject.new Duplication();
            List<SonarDuplications.Duplication.Block> blocks          = new ArrayList<>();

            JsonArray blocksArray                                           = duplicationObject.getAsJsonArray("blocks");
            for(JsonElement blockElement : blocksArray){
                JsonObject blockObject                                      = blockElement.getAsJsonObject();
                SonarDuplications.Duplication.Block block             = duplication.new Block();

                block.setFrom(blockObject.get("from").getAsInt());
                block.setSize(blockObject.get("size").getAsInt());
                block.set_ref(blockObject.get("_ref").getAsString());
                blocks.add(block);
            }
            duplication.setBlocks(blocks);
            duplications.add(duplication);
        }

        JsonObject files                                                    = jsonObject.getAsJsonObject("files");
        for(Map.Entry<String,JsonElement> entry: files.entrySet()){
            JsonObject fileObject                                           = entry.getValue().getAsJsonObject();
            SonarDuplications.File file                               = sonarDuplicationJsonObject.new File();

            file.setRef(entry.getKey());
            file.setKey(fileObject.get("key").getAsString());
            file.setName(fileObject.get("name").getAsString());
            file.setUuid(fileObject.get("uuid").getAsString());
            filesList.add(file);
        }

        sonarDuplicationJsonObject.setDuplications(duplications);
        sonarDuplicationJsonObject.setFiles(filesList);

        return sonarDuplicationJsonObject;
    }
}