package testIT.jira.tms;

import java.util.ArrayList;
import java.util.List;

public class TmsIssueContextDto {
    public String issueKey;
    public List<TmsWorkItemDto> testCases = new ArrayList<>();

    public TmsIssueContextDto() {
    }

    public TmsIssueContextDto(String issueKey) {
        this.issueKey = issueKey;
    }
}
