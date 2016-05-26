package nl.devgames.jenkinsplugins.devgames_publisher.deserializers;

import com.google.gson.*;
import nl.devgames.jenkinsplugins.devgames_publisher.models.sonarqube.SonarIssuesObject;

import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class SonarIssuesObjectDeserializer implements JsonDeserializer<SonarIssuesObject> {
    @Override
    public SonarIssuesObject deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) {
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        SonarIssuesObject sonarIssuesObject = new SonarIssuesObject();
        List<SonarIssuesObject.Issue> issues = new ArrayList<>();

        Integer totalIssues             = (jsonObject.get("total") != null)             ? jsonObject.get("total").getAsInt()            : null;
        Integer page                    = (jsonObject.get("p") != null)                 ? jsonObject.get("p").getAsInt()                : null;
        Integer pageSize                = (jsonObject.get("ps") != null)                ? jsonObject.get("ps").getAsInt()               : null;

        sonarIssuesObject.setTotal(totalIssues);
        sonarIssuesObject.setPage(page);
        sonarIssuesObject.setPageSize(pageSize);

        JsonArray issuesArray           = (jsonObject.get("issues") != null)            ? jsonObject.get("issues").getAsJsonArray()     : new JsonArray();

        for (JsonElement issueElement : issuesArray){
            JsonObject issueObject          = issueElement.getAsJsonObject();
            SonarIssuesObject.Issue issue   = sonarIssuesObject.new Issue();

            String key                  = (issueObject.get("key") != null)              ? issueObject.get("key").getAsString()          : null;
            String rule                 = (issueObject.get("rule") != null)             ? issueObject.get("rule").getAsString()         : null;
            String severity             = (issueObject.get("severity") != null)         ? issueObject.get("severity").getAsString()     : null;
            String component            = (issueObject.get("component") != null)        ? issueObject.get("component").getAsString()    : null;
            String project              = (issueObject.get("project") != null)          ? issueObject.get("project").getAsString()      : null;

            SonarIssuesObject.Issue.TextRange textRange = null;
            JsonObject textRangeObject  = (issueObject.get("textRange") != null)        ? issueObject.get("textRange").getAsJsonObject() : null;
            Integer startLine;
            Integer endLine;
            Integer startOffset;
            Integer endOffset;
            if(textRangeObject != null) {
                textRange               = issue.new TextRange();
                startLine               = textRangeObject.get("startLine").getAsInt();
                endLine                 = textRangeObject.get("endLine").getAsInt();
                startOffset             = textRangeObject.get("startOffset").getAsInt();
                endOffset               = textRangeObject.get("endOffset").getAsInt();
                textRange.setEndLine(endLine);
                textRange.setEndOffset(endOffset);
                textRange.setStartLine(startLine);
                textRange.setStartOffset(startOffset);
            }

            String status               = (issueObject.get("status") != null)           ? issueObject.get("status").getAsString()       : null;
            String resolution           = (issueObject.get("resolution") != null)       ? issueObject.get("resolution").getAsString()   : null;
            String message              = (issueObject.get("message") != null)          ? issueObject.get("message").getAsString()      : null;
            String debt                 = (issueObject.get("debt") != null)             ? issueObject.get("debt").getAsString()         : null;
            String author               = (issueObject.get("author") != null)           ? issueObject.get("author").getAsString()       : null;

            JsonArray tagsArray         = (issueObject.get("tags") != null)             ? issueObject.get("tags").getAsJsonArray()      : null;
            List<String> tags           = new ArrayList<>();
            if(tagsArray != null) {
                for (JsonElement tagElement : tagsArray){
                    String tag = tagElement.getAsString();
                    tags.add(tag);
                }
            }

            Date creationDate           = null;
            Date updateDate             = null;
            Date closeDate              = null;
            String creationDateString   = (issueObject.get("creationDate") != null)     ? issueObject.get("creationDate").getAsString() : null;
            String updateDateString     = (issueObject.get("updateDate") != null)       ? issueObject.get("updateDate").getAsString()   : null;
            String closeDateString      = (issueObject.get("closeDate") != null)        ? issueObject.get("closeDate").getAsString()    : null;

            try {
                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
                if(creationDateString != null)
                    creationDate = dateFormatter.parse(creationDateString);
                if(updateDateString != null)
                    updateDate = dateFormatter.parse(updateDateString);
                if(closeDateString != null)
                    closeDate = dateFormatter.parse(closeDateString);
            } catch (ParseException e) {
                e.printStackTrace();
            }

            issue.setKey(key);
            issue.setRule(rule);
            issue.setSeverity(severity);
            issue.setComponent(component);
            issue.setProject(project);
            issue.setTextRange(textRange);
            issue.setResolution(resolution);
            issue.setStatus(status);
            issue.setMessage(message);
            issue.setDebt(debt);
            issue.setAuthor(author);
            issue.setTags(tags);
            issue.setCreationDate(creationDate);
            issue.setUpdateDate(updateDate);
            issue.setCloseDate(closeDate);

            issues.add(issue);
        }

        sonarIssuesObject.setIssues(issues);
        return sonarIssuesObject;
    }
}
