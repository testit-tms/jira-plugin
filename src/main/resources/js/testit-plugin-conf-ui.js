(function ($) {
    var url = AJS.contextPath() + "/rest/testit-conf/1.0/";
    var projectsUrl = url + "projects"

    function formatAjaxError(jqXHR, textStatus, errorThrown) {
        var httpStatus = jqXHR && jqXHR.status ? jqXHR.status : null;
        var body = "";

        // Пытаемся вытащить текст из JSON ответа, если он есть.
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

        // Ограничиваем длину, чтобы alert не стал нечитаемым.
        if (body.length > 500) {
            body = body.substring(0, 500) + "...";
        }

        return "Error" + (httpStatus ? " " + httpStatus : "") + ": " + body;
    }

    $(document).ready(function() {
        AJS.$("#testit-conf").submit(function(e) {
                                e.preventDefault();
                                updateConfig();
                            });

        $.ajax({
            url: projectsUrl,
            dataType: "json"
        }).done(function(projects) {
            $.each(projects, function(key, value) {
                $("#projects").append($("<div><input type=\"checkbox\" id=\"" + key + "\" name=\"project\" value=\"" + key + "\"><label for=\"" + key + "\">" + value + " (" + key + ")</label></div>"));
            });

            $.ajax({
                url: url,
                dataType: "json"
            }).done(function(config) {
                        $("#url").val(config.url || "");
                        $("#privateToken").val(config.privateToken || "");
                        setProjects(config.projects || []);
                    }).fail(function(jqXHR, textStatus, errorThrown) {
                        alert(formatAjaxError(jqXHR, textStatus, errorThrown));
                    });
        }).fail(function(jqXHR, textStatus, errorThrown) {
            alert(formatAjaxError(jqXHR, textStatus, errorThrown));
        });
    });

})(AJS.$ || jQuery);

// Глобальная функция нужна, т.к. updateConfig() объявлен вне IIFE.
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

function getProjects() {
    var result = [];
    $.each($("input[type='checkbox']:checked"), function() {
        result.push($(this).val());
    });
    return result;
}

function setProjects(projects) {
    $.each(projects, function(index, value) {
        $("#" + value).prop('checked', true);
    });
}

function updateConfig() {
  AJS.$.ajax({
    url: AJS.contextPath() + "/rest/testit-conf/1.0/",
    type: "PUT",
    contentType: "application/json",
    data: JSON.stringify({
      url: AJS.$("#url").val(),
      privateToken: AJS.$("#privateToken").val(),
      projects: getProjects()
    }),
    processData: false,
    success: function (data) {
        alert("Plugin configuration has been updated");
        window.location.replace(AJS.contextPath() + "/plugins/servlet/upm");
    },
    error: function(jqXHR, textStatus, errorThrown){
        alert(formatAjaxError(jqXHR, textStatus, errorThrown));
    }});
}