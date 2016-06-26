package nl.devgames.jenkinsplugins.devgames_publisher.modelconverters;

import nl.devgames.jenkinsplugins.devgames_publisher.models.ServerJsonObject;
import nl.devgames.jenkinsplugins.devgames_publisher.models.sonarqube.SonarDuplications;

import java.util.List;
import java.util.stream.Collectors;

public class SonarQubeDuplicationToServerDuplicationConverter implements ModelConverter<SonarDuplications, ServerJsonObject.Duplication>{
    @Override
    public ServerJsonObject.Duplication convert(SonarDuplications base) {
        ServerJsonObject serverJsonObject           = new ServerJsonObject();
        ServerJsonObject.Duplication newDuplication = serverJsonObject.new Duplication();

        for (SonarDuplications.Duplication sonarDuplication : base.getDuplications()){
            List<ServerJsonObject.Duplication.File> duplicationFiles =
                    sonarDuplication.getBlocks().stream()
                            .map(block -> {
                                ServerJsonObject.Duplication.File duplicationFile = newDuplication.new File();
                                duplicationFile.setBeginLine(block.getFrom());
                                duplicationFile.setSize(block.getSize());

                                // Find the file belonging to this block
                                SonarDuplications.File blockFile = null;
                                for (SonarDuplications.File file : base.getFiles()) {
                                    if (file.getRef().equals(block.get_ref())) {
                                        blockFile = file;
                                        break;
                                    }
                                }
                                duplicationFile.setFile(blockFile.getName());
                                return duplicationFile;
                            }).collect(Collectors.toList());

            newDuplication.setFiles(duplicationFiles);
        }
        return newDuplication;
    }
}