package nl.devgames.jenkinsplugins.devgames_publisher.Models;

public class JenkinsJsonObject {
    private String result;
    private long timestamp;
    private ChangeSet changeSet;
    private Culprits[] culprits;

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

    public ChangeSet getChangeSet() {
        return changeSet;
    }

    public void setChangeSet(ChangeSet changeSet) {
        this.changeSet = changeSet;
    }

    public Culprits[] getCulprits() {
        return culprits;
    }

    public void setCulprits(Culprits[] culprits) {
        this.culprits = culprits;
    }

    public class ChangeSet {
        private String kind;
        private Items[] items;

        public String getKind() {
            return kind;
        }

        public void setKind(String kind) {
            this.kind = kind;
        }

        public Items[] getItems() {
            return items;
        }

        public void setItems(Items[] items) {
            this.items = items;
        }

        public class Items {
            private String commitId;
            private String msg;
            private String date;

            public String getCommitId() {
                return commitId;
            }

            public void setCommitId(String commitId) {
                this.commitId = commitId;
            }

            public String getMsg() {
                return msg;
            }

            public void setMsg(String msg) {
                this.msg = msg;
            }

            public String getDate() {
                return date;
            }

            public void setDate(String date) {
                this.date = date;
            }
        }
    }

    public class Culprits {
        private String fullName;

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }
    }
}