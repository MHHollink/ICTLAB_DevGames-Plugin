package nl.devgames.jenkinsplugins.devgames_publisher.Models.sonarqube;

import java.util.List;

public class SonarDuplicationsJsonObject {
    private List<Duplications> duplications;
    private List<Files> files;

    public List<Duplications> getDuplications() {
        return duplications;
    }

    public void setDuplications(List<Duplications> duplications) {
        this.duplications = duplications;
    }

    public List<Files> getFiles() {
        return files;
    }

    public void setFiles(List<Files> files) {
        this.files = files;
    }

    public class Duplications {
        private List<Blocks> blocks;

        public List<Blocks> getBlocks() {
            return blocks;
        }

        public void setBlocks(List<Blocks> blocks) {
            this.blocks = blocks;
        }

        public class Blocks {
            private int from;
            private int size;
            private String _ref;

            public int getFrom() {
                return from;
            }

            public void setFrom(int from) {
                this.from = from;
            }

            public int getSize() {
                return size;
            }

            public void setSize(int size) {
                this.size = size;
            }

            public String get_ref() {
                return _ref;
            }

            public void set_ref(String _ref) {
                this._ref = _ref;
            }
        }
    }

    public class Files {
        private String ref;
        private String key;
        private String uuid;
        private String name;

        public String getRef() {
            return ref;
        }

        public void setRef(String ref) {
            this.ref = ref;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getUuid() {
            return uuid;
        }

        public void setUuid(String uuid) {
            this.uuid = uuid;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
