(function ($) {
    var currentIssueKey = null;
    var eventsBound = false;
    var activeRequest = null;
    var loadSeq = 0;

    function restBase() {
        return AJS.contextPath() + "/rest/testit-conf/1.0/tms/issues/";
    }

    function formatAjaxError(jqXHR, textStatus, errorThrown) {
        return TestITCommon.formatAjaxError(jqXHR, textStatus, errorThrown);
    }

    function issueKeysEqual(a, b) {
        if (!a || !b) {
            return false;
        }
        return String(a).toUpperCase() === String(b).toUpperCase();
    }

    function resolveIssueKey($panel, explicitKey) {
        return TestITCommon.resolveIssueKey({ mode: "panel", explicitKey: explicitKey, $element: $panel });
    }

    function formatPlanLabel(plan) {
        return plan.name ? plan.name : "Тест-план";
    }

    function statusLozengeClass(statusType) {
        if (!statusType) {
            return "aui-lozenge";
        }
        if (statusType === "Completed") {
            return "aui-lozenge aui-lozenge-success";
        }
        if (statusType === "InProgress") {
            return "aui-lozenge testit-tp-status-inprogress";
        }
        return "aui-lozenge";
    }

    function statusTypeCountClass(statusType) {
        if (!statusType) {
            return "";
        }
        if (statusType === "Pending") {
            return "testit-tp-count-waiting";
        }
        if (statusType === "InProgress") {
            return "testit-tp-count-inprogress";
        }
        if (statusType === "Succeeded") {
            return "testit-tp-count-passed";
        }
        if (statusType === "Failed") {
            return "testit-tp-count-failed";
        }
        if (statusType === "Incomplete") {
            return "testit-tp-count-skipped";
        }
        return "";
    }

    function appendTestsLine($cell, label, value, cls) {
        $cell.append(
            $("<div>").addClass("testit-tp-tests-line " + (cls || "")).append(
                $("<span>").text(label + ": "),
                $("<span>").text(value != null ? value : 0)
            )
        );
    }

    function renderTestsCell(tests) {
        var $cell = $("<td>").addClass("testit-tp-tests-cell");
        if (!tests) {
            $cell.text("—");
            return $cell;
        }
        appendTestsLine($cell, "Всего", tests.total, "");
        if (tests.statuses && tests.statuses.length) {
            $.each(tests.statuses, function (_, status) {
                appendTestsLine(
                    $cell,
                    status.label || "—",
                    status.count,
                    statusTypeCountClass(status.statusType)
                );
            });
            return $cell;
        }
        $cell.append($("<div>").addClass("description").text("—"));
        return $cell;
    }

    function renderTable(testPlans) {
        if (!testPlans || !testPlans.length) {
            return $("<div>").addClass("description").text("Нет связанных тест-планов");
        }

        // Data columns only — actions are always prepended as the leftmost column.
        var dataCols = [
            {
                key: "id",
                header: "ID",
                colClass: "testit-tp-col-id",
                cell: function (plan) {
                    return $("<td>").addClass("testit-tp-id-cell")
                        .text(plan.globalId != null ? plan.globalId : "—");
                }
            },
            {
                key: "plan",
                header: "Тест-план",
                colClass: "testit-tp-col-plan",
                cell: function (plan) {
                    var $planCell = $("<td>").addClass("testit-tp-plan-cell");
                    if (plan.url) {
                        $planCell.append(
                            $("<a>").attr({ href: plan.url, target: "_blank", rel: "noopener noreferrer" })
                                .text(formatPlanLabel(plan))
                        );
                    } else {
                        $planCell.text(formatPlanLabel(plan));
                    }
                    return $planCell;
                }
            },
            {
                key: "version",
                header: "Версия",
                colClass: "testit-tp-col-version",
                cell: function (plan) {
                    return $("<td>").text(plan.build || "—");
                }
            },
            {
                key: "product",
                header: "Продукт",
                colClass: "testit-tp-col-product",
                cell: function (plan) {
                    return $("<td>").text(plan.productName || "—");
                }
            },
            {
                key: "start",
                header: "Начало тестирования",
                colClass: "testit-tp-col-start",
                cell: function (plan) {
                    return $("<td>").text(plan.startDate || "—");
                }
            },
            {
                key: "end",
                header: "Окончание тестирования",
                colClass: "testit-tp-col-end",
                cell: function (plan) {
                    return $("<td>").text(plan.endDate || "—");
                }
            },
            {
                key: "status",
                header: "Статус",
                colClass: "testit-tp-col-status",
                cell: function (plan) {
                    return $("<td>").append(
                        $("<span>").addClass(statusLozengeClass(plan.statusType))
                            .text(plan.statusLabel || "—")
                    );
                }
            },
            {
                key: "tests",
                header: "Тесты",
                colClass: "testit-tp-col-tests",
                cell: function (plan) {
                    return renderTestsCell(plan.tests);
                }
            }
        ];

        var $table = $("<table>").addClass("aui testit-testplans-table");
        var $colgroup = $("<colgroup>").append(
            $("<col>").addClass("testit-tp-col-actions")
        );
        var $headerRow = $("<tr>").append($("<th>").text(""));
        $.each(dataCols, function (_, col) {
            $colgroup.append($("<col>").addClass(col.colClass));
            $headerRow.append($("<th>").text(col.header));
        });
        var $thead = $("<thead>").append($headerRow);
        var $tbody = $("<tbody>");
        $.each(testPlans, function (_, plan) {
            var $actions = $("<td>").addClass("testit-tp-actions-cell");
            if (plan.url) {
                $actions.append(
                    $("<button>")
                        .attr({
                            type: "button",
                            title: "Отвязать",
                            "aria-label": "Отвязать",
                            "data-plan-url": plan.url
                        })
                        .addClass("testit-unlink-x testit-testplan-unlink-btn")
                        .append(
                            $("<span>").addClass("aui-icon aui-icon-small aui-iconfont-close-dialog")
                                .attr("aria-hidden", "true")
                        )
                );
            }
            var $row = $("<tr>").append($actions);
            $.each(dataCols, function (_, col) {
                $row.append(col.cell(plan));
            });
            $tbody.append($row);
        });
        $table.append($colgroup).append($thead).append($tbody);
        return $("<div>").addClass("testit-table-scroll").append($table);
    }

    function renderData(data) {
        var $content = $("#testit-testplans-content").empty();
        $content.append(renderTable(data && data.testPlans ? data.testPlans : []));
    }

    function showError(message) {
        $("#testit-testplans-loading").hide();
        $("#testit-testplans-content").hide();
        $("#testit-testplans-error").text(message || "Error").show();
    }

    function showContent() {
        $("#testit-testplans-loading").hide();
        $("#testit-testplans-error").hide().text("");
        $("#testit-testplans-content").show();
    }

    function loadTestPlans(issueKey, options) {
        options = options || {};
        if (!issueKey) {
            showError("Cannot determine issue key");
            return;
        }
        if (activeRequest) {
            activeRequest.abort();
            activeRequest = null;
        }
        var seq = ++loadSeq;
        if (!options.background) {
            $("#testit-testplans-loading").show();
            $("#testit-testplans-content").hide();
            $("#testit-testplans-error").hide().text("");
        }
        var request = $.ajax({
            url: restBase() + encodeURIComponent(issueKey) + "/testplans",
            dataType: "json",
            timeout: 45000
        });
        activeRequest = request;
        request.done(function (data) {
            if (seq !== loadSeq) {
                return;
            }
            if (data && data.issueKey && !issueKeysEqual(data.issueKey, issueKey)) {
                return;
            }
            if (!issueKeysEqual(currentIssueKey, issueKey)) {
                return;
            }
            renderData(data);
            showContent();
        }).fail(function (jqXHR, textStatus, errorThrown) {
            if (seq !== loadSeq || textStatus === "abort") {
                return;
            }
            if (!options.background) {
                showError(formatAjaxError(jqXHR, textStatus, errorThrown));
            }
        }).always(function () {
            if (activeRequest === request) {
                activeRequest = null;
            }
        });
    }

    function addTestPlan(issueKey) {
        var url = $.trim($("#testit-testplan-url").val());
        if (!url) {
            showError("Введите URL тест-плана");
            return;
        }
        if (!issueKey) {
            showError("Cannot determine issue key");
            return;
        }
        var $btn = $(".testit-testplan-add-btn").prop("disabled", true);
        $.ajax({
            url: restBase() + encodeURIComponent(issueKey) + "/testplans",
            method: "POST",
            contentType: "application/json",
            data: JSON.stringify({ url: url }),
            dataType: "json",
            timeout: 45000
        }).done(function () {
            $("#testit-testplan-url").val("");
            loadTestPlans(issueKey);
        }).fail(function (jqXHR, textStatus, errorThrown) {
            showError(formatAjaxError(jqXHR, textStatus, errorThrown));
        }).always(function () {
            $btn.prop("disabled", false);
        });
    }

    function unlinkTestPlan(issueKey, planUrl, $btn) {
        if (!issueKey || !planUrl) {
            return;
        }
        $btn.prop("disabled", true);
        $.ajax({
            url: restBase() + encodeURIComponent(issueKey) + "/testplans",
            method: "DELETE",
            contentType: "application/json",
            data: JSON.stringify({ url: planUrl }),
            dataType: "json",
            timeout: 45000
        }).done(function () {
            loadTestPlans(issueKey);
        }).fail(function (jqXHR, textStatus, errorThrown) {
            showError(formatAjaxError(jqXHR, textStatus, errorThrown));
            $btn.prop("disabled", false);
        });
    }

    function bindEvents() {
        if (eventsBound) {
            return;
        }
        eventsBound = true;
        $(document).on("click", "#testit-testplans-panel .testit-testplan-add-btn", function (e) {
            e.preventDefault();
            addTestPlan(currentIssueKey);
        });
        $(document).on("keydown", "#testit-testplan-url", function (e) {
            if (e.key === "Enter") {
                e.preventDefault();
                addTestPlan(currentIssueKey);
            }
        });
        $(document).on("click", "#testit-testplans-panel .testit-testplans-refresh-btn", function (e) {
            e.preventDefault();
            loadTestPlans(currentIssueKey);
        });
        $(document).on("click", "#testit-testplans-panel .testit-testplan-unlink-btn", function (e) {
            e.preventDefault();
            var $btn = $(this);
            unlinkTestPlan(currentIssueKey, $btn.attr("data-plan-url"), $btn);
        });
    }

    function boot(issueKey) {
        var $panel = $("#testit-testplans-panel");
        var key = resolveIssueKey($panel, issueKey);
        if (key) {
            currentIssueKey = key;
            if ($panel.length) {
                $panel.attr("data-issue-key", key);
            }
        }
        bindEvents();
        loadTestPlans(key);
    }

    window.TestITTestPlansPanel = {
        boot: boot,
        loadTestPlans: loadTestPlans
    };

    $(document).ready(function () {
        var $panel = $("#testit-testplans-panel");
        if ($panel.length) {
            boot(resolveIssueKey($panel));
        }
    });

    if (window.JIRA && JIRA.Events && typeof JIRA.bind === "function") {
        JIRA.bind(JIRA.Events.NEW_CONTENT_ADDED, function () {
            var $panel = $("#testit-testplans-panel");
            if (!$panel.length) {
                return;
            }
            var key = resolveIssueKey($panel);
            if (key && key.toUpperCase() !== (currentIssueKey || "").toUpperCase()) {
                boot(key);
            }
        });
    }
})(AJS.$ || jQuery);
