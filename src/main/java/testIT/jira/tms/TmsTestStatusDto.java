package testIT.jira.tms;

public class TmsTestStatusDto {
    public String name;
    public String code;
    public String type;
    public Integer priority;

    public TmsTestStatusDto() {
    }

    public TmsTestStatusDto(String name, String code, String type) {
        this(name, code, type, null);
    }

    public TmsTestStatusDto(String name, String code, String type, Integer priority) {
        this.name = name;
        this.code = code;
        this.type = type;
        this.priority = priority;
    }
}
