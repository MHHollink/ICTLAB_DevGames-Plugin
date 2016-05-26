package nl.devgames.jenkinsplugins.devgames_publisher.models;

import java.util.Date;
import java.util.List;

public class JenkinsObject {
    private String result;
    private ChangeSet changeSet;

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public ChangeSet getChangeSet() {
        return changeSet;
    }

    public void setChangeSet(ChangeSet changeSet) {
        this.changeSet = changeSet;
    }

    public class ChangeSet {
        private String kind;
        private List<Item> items;

        public String getKind() {
            return kind;
        }

        public void setKind(String kind) {
            this.kind = kind;
        }

        public List<Item> getItems() {
            return items;
        }

        public void setItems(List<Item> items) {
            this.items = items;
        }

        public class Item {
            private String commitId;
            private Author author;
            private Date date;
            private String msg;

            public String getCommitId() {
                return commitId;
            }

            public void setCommitId(String commitId) {
                this.commitId = commitId;
            }

            public Author getAuthor() {
                return author;
            }

            public void setAuthor(Author author) {
                this.author = author;
            }

            public Date getDate() {
                return date;
            }

            public void setDate(Date date) {
                this.date = date;
            }

            public String getMsg() {
                return msg;
            }

            public void setMsg(String msg) {
                this.msg = msg;
            }

            public class Author {
                private String scmUser;
                private String fullName;

                public String getScmUser() {
                    return scmUser;
                }

                public void setScmUser(String scmUser) {
                    this.scmUser = scmUser;
                }

                public String getFullName() {
                    return fullName;
                }

                public void setFullName(String fullName) {
                    this.fullName = fullName;
                }
            }
        }
    }
}
