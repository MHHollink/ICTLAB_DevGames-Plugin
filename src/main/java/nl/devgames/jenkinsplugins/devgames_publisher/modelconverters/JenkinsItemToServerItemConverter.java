package nl.devgames.jenkinsplugins.devgames_publisher.modelconverters;

import nl.devgames.dateconverters.DateConverter;
import nl.devgames.dateconverters.JenkinsDateConverter;
import nl.devgames.jenkinsplugins.devgames_publisher.models.JenkinsAPIData;
import nl.devgames.jenkinsplugins.devgames_publisher.models.ServerJsonObject;

import java.text.ParseException;
import java.util.Date;

public class JenkinsItemToServerItemConverter implements ModelConverter<JenkinsAPIData.ChangeSet.Item, ServerJsonObject.Item> {
    private DateConverter dateConverter;

    public JenkinsItemToServerItemConverter() {
        dateConverter = new JenkinsDateConverter();
    }

    @Override
    public ServerJsonObject.Item convert(JenkinsAPIData.ChangeSet.Item base) {
        ServerJsonObject serverJsonObject   = new ServerJsonObject();
        ServerJsonObject.Item newItem       = serverJsonObject.new Item();
        newItem.setCommitId(base.getCommitId());
        newItem.setCommitMsg(base.getMsg());
        try {
            Date itemDate = dateConverter.convertStringToDate(base.getDate());
            newItem.setTimestamp(itemDate.getTime());
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return newItem;
    }
}
