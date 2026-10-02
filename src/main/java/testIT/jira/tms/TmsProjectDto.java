package testIT.jira.tms;

public class TmsProjectDto {
    public String id;
    public Long globalId;
    public String name;

    public TmsProjectDto() {
    }

    public TmsProjectDto(String id, Long globalId, String name) {
        this.id = id;
        this.globalId = globalId;
        this.name = name;
    }
}
