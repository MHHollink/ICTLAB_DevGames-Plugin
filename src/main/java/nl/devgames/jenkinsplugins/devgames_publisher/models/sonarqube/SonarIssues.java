package nl.devgames.jenkinsplugins.devgames_publisher.models.sonarqube;

import java.util.Date;
import java.util.List;

public class SonarIssues {
    private Integer total;
    private Integer p;
    private Integer ps;
    private List<Issue> issues;

    public Integer getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public Integer getP() {
        return p;
    }

    public void setP(Integer p) {
        this.p = p;
    }

    public Integer getPs() {
        return ps;
    }

    public void setPs(Integer ps) {
        this.ps = ps;
    }

    public List<Issue> getIssues() {
        return issues;
    }

    public void setIssues(List<Issue> issues) {
        this.issues = issues;
    }

    public class Issue {
        private String key;
        private String rule;
        private String severity;
        private String component;
        private String project;
        private TextRange textRange;
        private String resolution;
        private String status;
        private String message;
        private String debt;
        private String author;
        private List<String> tags;
        private String creationDate;
        private String updateDate;
        private String closeDate;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getRule() {
            return rule;
        }

        public void setRule(String rule) {
            this.rule = rule;
        }

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

        public String getProject() {
            return project;
        }

        public void setProject(String project) {
            this.project = project;
        }

        public TextRange getTextRange() {
            return textRange;
        }

        public void setTextRange(TextRange textRange) {
            this.textRange = textRange;
        }

        public String getResolution() {
            return resolution;
        }

        public void setResolution(String resolution) {
            this.resolution = resolution;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
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

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }

        public String getCreationDate() {
            return creationDate;
        }

        public void setCreationDate(String creationDate) {
            this.creationDate = creationDate;
        }

        public String getUpdateDate() {
            return updateDate;
        }

        public void setUpdateDate(String updateDate) {
            this.updateDate = updateDate;
        }

        public String getCloseDate() {
            return closeDate;
        }

        public void setCloseDate(String closeDate) {
            this.closeDate = closeDate;
        }

        public class TextRange {
            private Integer startLine;
            private Integer endLine;
            private Integer startOffset;
            private Integer endOffset;

            public Integer getStartLine() {
                return startLine;
            }

            public void setStartLine(int startLine) {
                this.startLine = startLine;
            }

            public Integer getEndLine() {
                return endLine;
            }

            public void setEndLine(int endLine) {
                this.endLine = endLine;
            }

            public Integer getStartOffset() {
                return startOffset;
            }

            public void setStartOffset(int startOffset) {
                this.startOffset = startOffset;
            }

            public Integer getEndOffset() {
                return endOffset;
            }

            public void setEndOffset(int endOffset) {
                this.endOffset = endOffset;
            }
        }
    }
}
