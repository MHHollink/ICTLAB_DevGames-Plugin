package nl.devgames.jenkinsplugins.devgames_publisher.modelconverters;

import nl.devgames.dateconverters.DateConverter;
import nl.devgames.dateconverters.SonarQubeDateConverter;
import nl.devgames.jenkinsplugins.devgames_publisher.models.ServerJsonObject;
import nl.devgames.jenkinsplugins.devgames_publisher.models.sonarqube.SonarIssues;

import java.text.ParseException;
import java.util.Date;

public class SonarQubeIssueToServerIssueConverter implements ModelConverter<SonarIssues.Issue, ServerJsonObject.Issue> {
    private DateConverter dateConverter;

    public SonarQubeIssueToServerIssueConverter() {
        dateConverter = new SonarQubeDateConverter();
    }

    @Override
    public ServerJsonObject.Issue convert(SonarIssues.Issue base) {
        ServerJsonObject serverJsonObject   = new ServerJsonObject();
        ServerJsonObject.Issue newIssue     = serverJsonObject.new Issue();
        newIssue.setKey(base.getKey());
        newIssue.setSeverity(base.getSeverity());
        newIssue.setComponent(base.getComponent());
        newIssue.setStatus(base.getStatus());
        newIssue.setResolution(base.getResolution());
        newIssue.setMessage(base.getMessage());

        if (base.getCreationDate() != null) {
            try {
                Date issueCreationDate = dateConverter.convertStringToDate(base.getCreationDate());
                newIssue.setCreationDate(issueCreationDate.getTime());
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

        if (base.getUpdateDate() != null) {
            try {
                Date issueUpdateDate = dateConverter.convertStringToDate(base.getUpdateDate());
                newIssue.setUpdateDate(issueUpdateDate.getTime());
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        if (base.getCloseDate() != null) {
            try {
                Date issueCloseDate = dateConverter.convertStringToDate(base.getCloseDate());
                newIssue.setCloseDate(issueCloseDate.getTime());
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

        if (base.getTextRange() != null) {
            newIssue.setStartLine(base.getTextRange().getStartLine());
            newIssue.setEndLine(base.getTextRange().getEndLine());
        }

        int debt = 0;
        // Takes the debt string, removes the non-numerical characters and converts it into an int.
        // If the debt string contains an "h", convert in into minutes
        if (base.getDebt().contains("h")) {
            int positionH = base.getDebt().indexOf("h");
            int hours = Integer.parseInt(base.getDebt().substring(0, positionH));

            if (positionH != base.getDebt().length()-1){
                int posMIN = base.getDebt().indexOf("m");
                int minutes = Integer.parseInt(base.getDebt().substring(positionH+1, posMIN));
                debt = (hours*60)+minutes;
            } else {
                debt = hours*60;
            }
        } else {
            int posMIN = base.getDebt().indexOf("m");
            int minutes = Integer.parseInt(base.getDebt().substring(0, posMIN));
            debt = minutes;
        }
        newIssue.setDebt(debt);
        return newIssue;
    }
}
