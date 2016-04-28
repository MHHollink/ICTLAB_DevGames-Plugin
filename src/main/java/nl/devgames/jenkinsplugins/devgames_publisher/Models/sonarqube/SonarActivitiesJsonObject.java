package nl.devgames.jenkinsplugins.devgames_publisher.Models.sonarqube;

import java.util.List;

public class SonarActivitiesJsonObject {
    private Paging paging;
    private List<Tasks> tasks;

    public Paging getPaging() {
        return paging;
    }

    public void setPaging(Paging paging) {
        this.paging = paging;
    }

    public List<Tasks> getTasks() {
        return tasks;
    }

    public void setTasks(List<Tasks> tasks) {
        this.tasks = tasks;
    }

    public class Paging{
        private int pageIndex;
        private int pageSize;
        private int total;

        public int getPageIndex() {
            return pageIndex;
        }

        public void setPageIndex(int pageIndex) {
            this.pageIndex = pageIndex;
        }

        public int getPageSize() {
            return pageSize;
        }

        public void setPageSize(int pageSize) {
            this.pageSize = pageSize;
        }

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }
    }

    public class Tasks {
        private String id;
        private String type;
        private String componentId;
        private String componentKey;
        private String status;
        private String submittedAt;
        private String startedAt;
        private String executedAt;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getComponentId() {
            return componentId;
        }

        public void setComponentId(String componentId) {
            this.componentId = componentId;
        }

        public String getComponentKey() {
            return componentKey;
        }

        public void setComponentKey(String componentKey) {
            this.componentKey = componentKey;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getSubmittedAt() {
            return submittedAt;
        }

        public void setSubmittedAt(String submittedAt) {
            this.submittedAt = submittedAt;
        }

        public String getStartedAt() {
            return startedAt;
        }

        public void setStartedAt(String startedAt) {
            this.startedAt = startedAt;
        }

        public String getExecutedAt() {
            return executedAt;
        }

        public void setExecutedAt(String executedAt) {
            this.executedAt = executedAt;
        }
    }
}
