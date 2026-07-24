(function (global) {
    var ISSUE_KEY_FROM_PROJECTS = /\/projects\/[^/]+\/issues\/([A-Z][A-Z0-9]+-\d+)/i;
    var ISSUE_KEY_FROM_BROWSE = /\/browse\/([A-Z][A-Z0-9]+-\d+)/i;

    function formatAjaxError(jqXHR, textStatus, errorThrown) {
        var httpStatus = jqXHR && jqXHR.status ? jqXHR.status : null;
        var body = "";
        if (jqXHR && jqXHR.responseText) {
            try {
                var parsed = JSON.parse(jqXHR.responseText);
                body = parsed.message || parsed.error || jqXHR.responseText;
            } catch (e) {
                body = jqXHR.responseText;
            }
        } else if (jqXHR && jqXHR.responseJSON) {
            body = jqXHR.responseJSON.message || jqXHR.responseJSON.error || "";
        }
        if (!body) {
            body = textStatus || errorThrown || "Unknown error";
        }
        if (body.length > 500) {
            body = body.substring(0, 500) + "...";
        }
        return "Error" + (httpStatus ? " " + httpStatus : "") + ": " + body;
    }

    function resolveIssueKeyFromUrl() {
        var match = window.location.pathname.match(ISSUE_KEY_FROM_PROJECTS);
        if (match) {
            return match[1];
        }
        match = window.location.pathname.match(ISSUE_KEY_FROM_BROWSE);
        return match ? match[1] : null;
    }

    function readMetaIssueKey() {
        if (!global.AJS || !AJS.Meta || typeof AJS.Meta.get !== "function") {
            return null;
        }
        return AJS.Meta.get("issueKey") || AJS.Meta.get("issue-key") || null;
    }

    function readJiraIssueKey() {
        if (!global.JIRA || !JIRA.Issue || typeof JIRA.Issue.getIssueKey !== "function") {
            return null;
        }
        return JIRA.Issue.getIssueKey() || null;
    }

    function resolveIssueKey(options) {
        options = options || {};
        if (options.explicitKey) {
            return options.explicitKey;
        }
        var steps = options.mode === "panel"
            ? ["url", "jira", "meta", "element"]
            : ["element", "meta", "jira", "url"];
        for (var i = 0; i < steps.length; i++) {
            var key = readIssueKeyStep(steps[i], options.$element);
            if (key) {
                return key;
            }
        }
        return null;
    }

    function readIssueKeyStep(step, $element) {
        if (step === "element") {
            return $element && $element.attr("data-issue-key") ? $element.attr("data-issue-key") : null;
        }
        if (step === "meta") {
            return readMetaIssueKey();
        }
        if (step === "jira") {
            return readJiraIssueKey();
        }
        if (step === "url") {
            return resolveIssueKeyFromUrl();
        }
        return null;
    }

    global.TestITCommon = {
        formatAjaxError: formatAjaxError,
        resolveIssueKeyFromUrl: resolveIssueKeyFromUrl,
        resolveIssueKey: resolveIssueKey
    };
})(typeof window !== "undefined" ? window : this);
