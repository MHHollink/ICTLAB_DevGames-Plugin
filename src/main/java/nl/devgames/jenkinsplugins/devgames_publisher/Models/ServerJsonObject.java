package nl.devgames.jenkinsplugins.devgames_publisher.Models;

import java.util.List;

public class ServerJsonObject {
    private String projectName;
    private String result;
    private long timestamp;
    private String author;
    private List<Item> items;
    private List<Issue> issues;
    private List<Duplication> duplications;

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public List<Issue> getIssues() {
        return issues;
    }

    public void setIssues(List<Issue> issues) {
        this.issues = issues;
    }

    public List<Duplication> getDuplications() {
        return duplications;
    }

    public void setDuplications(List<Duplication> duplications) {
        this.duplications = duplications;
    }

    public class Item {
        private String commitId;
        private String commitMsg;
        private long timestamp;

        public String getCommitId() {
            return commitId;
        }

        public void setCommitId(String commitId) {
            this.commitId = commitId;
        }

        public String getCommitMsg() {
            return commitMsg;
        }

        public void setCommitMsg(String commitMsg) {
            this.commitMsg = commitMsg;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
    }

    public class Issue {
        private String severity;
        private String component;
        private int startLine;
        private int endLine;
        private String status;
        private String resolution;
        private String message;
        private String debt;
        private long creationDate;
        private long updateDate;
        private long closeDate;

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }

        public String getComponent() {
            return component;
        }

        public void setComponent(String component) {
            this.component = component;
        }

        public int getStartLine() {
            return startLine;
        }

        public void setStartLine(int startLine) {
            this.startLine = startLine;
        }

        public int getEndLine() {
            return endLine;
        }

        public void setEndLine(int endLine) {
            this.endLine = endLine;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getResolution() {
            return resolution;
        }

        public void setResolution(String resolution) {
            this.resolution = resolution;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getDebt() {
            return debt;
        }

        public void setDebt(String debt) {
            this.debt = debt;
        }

        public long getCreationDate() {
            return creationDate;
        }

        public void setCreationDate(long creationDate) {
            this.creationDate = creationDate;
        }

        public long getUpdateDate() {
            return updateDate;
        }

        public void setUpdateDate(long updateDate) {
            this.updateDate = updateDate;
        }

        public long getCloseDate() {
            return closeDate;
        }

        public void setCloseDate(long closeDate) {
            this.closeDate = closeDate;
        }
    }

    public class Duplication {
        private List<File> files;

        public List<File> getFiles() {
            return files;
        }

        public void setFiles(List<File> files) {
            this.files = files;
        }

        public class File {
            private String file;
            private int beginLine;
            private int endLine;
            private int size;

            public String getFile() {
                return file;
            }

            public void setFile(String file) {
                this.file = file;
            }

            public int getBeginLine() {
                return beginLine;
            }

            public void setBeginLine(int beginLine) {
                this.beginLine = beginLine;
            }

            public int getEndLine() {
                return endLine;
            }

            public void setEndLine(int endLine) {
                this.endLine = endLine;
            }

            public int getSize() {
                return size;
            }

            public void setSize(int size) {
                this.size = size;
            }
        }
    }
}
