package testIT.jira.tms;

import java.util.ArrayList;
import java.util.List;

public class TmsWorkItemDto {
    public String id;
    public Long globalId;
    public String name;
    public String projectId;
    public String url;
    public List<TmsTestResultDto> results = new ArrayList<>();

    public TmsWorkItemDto() {
    }

    public TmsWorkItemDto(String id, Long globalId, String name, String projectId, String url) {
        this.id = id;
        this.globalId = globalId;
        this.name = name;
        this.projectId = projectId;
        this.url = url;
    }
}
