(function ($) {
    function restBase() {
        return AJS.contextPath() + "/rest/testit-conf/1.0/tms/";
    }
    var PAGE_SIZE = 20;
    var state = {
        skip: 0,
        take: PAGE_SIZE,
        totalItems: 0,
        q: "",
        globalId: "",
        selectedProjectId: null,
        issueKey: null,
        loading: false
    };
    var $dialog;
    var searchTimer = null;

    function formatAjaxError(jqXHR, textStatus, errorThrown) {
        return TestITCommon.formatAjaxError(jqXHR, textStatus, errorThrown);
    }

    function resolveIssueKey($link) {
        return TestITCommon.resolveIssueKey({ mode: "link", $element: $link });
    }

    function digitsOnly(value) {
        return String(value || "").replace(/\D/g, "");
    }

    function scheduleSearch() {
        if (searchTimer) {
            clearTimeout(searchTimer);
        }
        searchTimer = setTimeout(function () {
            state.skip = 0;
            state.selectedProjectId = null;
            loadProjects();
        }, 300);
    }

    function ensureDialog() {
        if ($dialog && $dialog.length) {
            return $dialog;
        }
        var html =
            '<section id="testit-create-dialog" class="aui-dialog2 aui-dialog2-medium aui-layer" role="dialog" aria-hidden="true">' +
            '  <header class="aui-dialog2-header">' +
            '    <h2 class="aui-dialog2-header-main">Create TestCase in Test IT</h2>' +
            '    <a class="aui-dialog2-header-close" href="#" id="testit-create-close">' +
            '      <span class="aui-icon aui-icon-small aui-iconfont-close-dialog">Close</span>' +
            '    </a>' +
            '  </header>' +
            '  <div class="aui-dialog2-content">' +
            '    <div class="aui-message aui-message-error" id="testit-create-error" style="display:none;"></div>' +
            '    <div class="aui-message aui-message-success" id="testit-create-success" style="display:none;"></div>' +
            '    <form class="aui" action="#" id="testit-create-form">' +
            '      <div class="field-group">' +
            '        <label for="testit-project-search">Search by name</label>' +
            '        <input class="text long-field" type="text" id="testit-project-search" placeholder="Project name"/>' +
            '      </div>' +
            '      <div class="field-group">' +
            '        <label for="testit-project-globalid">Global ID</label>' +
            '        <input class="text long-field" type="text" id="testit-project-globalid" inputmode="numeric" pattern="[0-9]*" placeholder="e.g. 30270"/>' +
            '        <div class="description">Digits only. If set, search uses Global ID instead of name.</div>' +
            '      </div>' +
            '      <div class="field-group">' +
            '        <label>Projects</label>' +
            '        <div id="testit-project-list" style="max-height:240px;overflow:auto;border:1px solid #ccc;padding:8px;"></div>' +
            '        <div id="testit-project-pageinfo" class="description" style="margin-top:6px;"></div>' +
            '      </div>' +
            '    </form>' +
            '  </div>' +
            '  <footer class="aui-dialog2-footer">' +
            '    <div class="aui-dialog2-footer-actions">' +
            '      <button type="button" class="aui-button" id="testit-project-prev">Previous</button>' +
            '      <button type="button" class="aui-button" id="testit-project-next">Next</button>' +
            '      <button type="button" class="aui-button aui-button-primary" id="testit-create-submit" disabled>Create</button>' +
            '      <button type="button" class="aui-button aui-button-link" id="testit-create-cancel">Cancel</button>' +
            '    </div>' +
            '  </footer>' +
            '</section>';
        $("body").append(html);
        $dialog = $("#testit-create-dialog");

        $("#testit-create-close, #testit-create-cancel").on("click", function (e) {
            e.preventDefault();
            closeDialog();
        });
        $("#testit-project-prev").on("click", function () {
            if (state.skip <= 0 || state.loading) {
                return;
            }
            state.skip = Math.max(0, state.skip - state.take);
            loadProjects();
        });
        $("#testit-project-next").on("click", function () {
            if (state.loading || state.skip + state.take >= state.totalItems) {
                return;
            }
            state.skip = state.skip + state.take;
            loadProjects();
        });
        $("#testit-create-submit").on("click", function () {
            createWorkItem();
        });
        $("#testit-project-search").on("input", function () {
            state.q = $(this).val() || "";
            scheduleSearch();
        });
        $("#testit-project-globalid").on("input", function () {
            var cleaned = digitsOnly($(this).val());
            if ($(this).val() !== cleaned) {
                $(this).val(cleaned);
            }
            state.globalId = cleaned;
            scheduleSearch();
        });
        return $dialog;
    }

    function openDialog(issueKey) {
        state.issueKey = issueKey;
        state.skip = 0;
        state.q = "";
        state.globalId = "";
        state.selectedProjectId = null;
        ensureDialog();
        $("#testit-project-search").val("");
        $("#testit-project-globalid").val("");
        $("#testit-create-error").hide().text("");
        $("#testit-create-success").hide().text("");
        $("#testit-create-submit").prop("disabled", true);
        if (AJS.dialog2 && AJS.dialog2($dialog).show) {
            AJS.dialog2($dialog).show();
        } else {
            $dialog.attr("aria-hidden", "false").show();
        }
        loadProjects();
    }

    function closeDialog() {
        if (!$dialog) {
            return;
        }
        if (AJS.dialog2 && AJS.dialog2($dialog).hide) {
            AJS.dialog2($dialog).hide();
        } else {
            $dialog.attr("aria-hidden", "true").hide();
        }
    }

    function setError(message) {
        $("#testit-create-success").hide();
        $("#testit-create-error").text(message || "Unknown error").show();
    }

    function setSuccess(message) {
        $("#testit-create-error").hide();
        $("#testit-create-success").text(message || "Done").show();
    }

    function renderProjects(items) {
        var $list = $("#testit-project-list").empty();
        if (!items || !items.length) {
            $list.append($("<div>").text("No projects found"));
            $("#testit-create-submit").prop("disabled", true);
            return;
        }
        $.each(items, function (_, project) {
            var id = "testit-project-" + project.id;
            var $row = $("<div>").css({ marginBottom: "6px" });
            var $radio = $("<input>")
                .attr({ type: "radio", name: "testit-project", id: id, value: project.id });
            if (state.selectedProjectId === project.id) {
                $radio.prop("checked", true);
            }
            $radio.on("change", function () {
                state.selectedProjectId = project.id;
                $("#testit-create-submit").prop("disabled", false);
            });
            var displayId = project.globalId != null ? project.globalId : project.id;
            var $label = $("<label>").attr("for", id).text(project.name + " (" + displayId + ")");
            $row.append($radio).append(" ").append($label);
            $list.append($row);
        });
        $("#testit-create-submit").prop("disabled", !state.selectedProjectId);
    }

    function updatePager() {
        var from = state.totalItems === 0 ? 0 : state.skip + 1;
        var to = Math.min(state.skip + state.take, state.totalItems);
        $("#testit-project-pageinfo").text(from + "–" + to + " of " + state.totalItems);
        $("#testit-project-prev").prop("disabled", state.skip <= 0 || state.loading);
        $("#testit-project-next").prop("disabled", state.skip + state.take >= state.totalItems || state.loading);
    }

    function loadProjects() {
        state.loading = true;
        updatePager();
        $("#testit-project-list").text("Loading...");
        var data = {
            skip: state.skip,
            take: state.take
        };
        if (state.q) {
            data.q = state.q;
        }
        if (state.globalId) {
            data.globalId = state.globalId;
        }
        $.ajax({
            url: restBase() + "projects",
            dataType: "json",
            data: data
        }).done(function (page) {
            state.skip = page.skip != null ? page.skip : state.skip;
            state.take = page.take != null ? page.take : state.take;
            state.totalItems = page.totalItems != null ? page.totalItems : 0;
            renderProjects(page.items || []);
        }).fail(function (jqXHR, textStatus, errorThrown) {
            renderProjects([]);
            setError(formatAjaxError(jqXHR, textStatus, errorThrown));
        }).always(function () {
            state.loading = false;
            updatePager();
        });
    }

    function createWorkItem() {
        if (!state.selectedProjectId || !state.issueKey) {
            setError("Select a project first");
            return;
        }
        $("#testit-create-submit").prop("disabled", true);
        $("#testit-create-error").hide();
        $.ajax({
            url: restBase() + "workitems",
            type: "POST",
            contentType: "application/json",
            dataType: "json",
            data: JSON.stringify({
                issueKey: state.issueKey,
                projectId: state.selectedProjectId
            })
        }).done(function (result) {
            var label = (result && result.name) ? result.name : "TestCase";
            var idPart = (result && result.id) ? (" (id: " + result.id + ")") : "";
            setSuccess("Created: " + label + idPart);
            $(document).trigger("testit:workitem-created", {
                issueKey: state.issueKey,
                globalId: result && result.globalId != null ? result.globalId : null,
                projectId: result && result.projectId ? result.projectId : state.selectedProjectId,
                name: result && result.name ? result.name : label,
                url: result && result.url ? result.url : null
            });
        }).fail(function (jqXHR, textStatus, errorThrown) {
            setError(formatAjaxError(jqXHR, textStatus, errorThrown));
            $("#testit-create-submit").prop("disabled", false);
        });
    }

    $(document).ready(function () {
        $(document).on("click", "#testit_issue_link", function (e) {
            e.preventDefault();
            e.stopPropagation();
            var issueKey = resolveIssueKey($(this));
            if (!issueKey) {
                alert("Cannot determine issue key");
                return false;
            }
            openDialog(issueKey);
            return false;
        });
    });
})(AJS.$ || jQuery);
