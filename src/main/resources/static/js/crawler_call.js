$(document).ready(function () {
        $.ajax({
            type: "GET",
            crossDomain: true,
            crossOrigin: true,
            url: "http://193.204.187.192:8087/getState",
            success: function (msg){
                console.log(JSON.stringify(msg));
                if(!(msg === '')) {
                    if(msg){
                        $("#start").prop("disabled",true);
                        $("#stop").prop("disabled",false);
                        $("#status").html("<b>Stato del crawler : IN ESECUZIONE</b>");
                    }else{
                        $("#start").prop("disabled",false);
                        $("#stop").prop("disabled",true);
                        $("#status").html("<b>Stato del crawler : IN ATTESA</b>");
                    }
                    console.log(msg);
                }
            },
            error: function (xhr, status, errorThrown) {
                alert(status, errorThrown);
                // Error block
                console.log("xhr: " + xhr);
                console.log("status: " + status);
                console.log("errorThrown: " + errorThrown);
            }
        });

    $("#start").click(function() {
        $("#status").html("<b>Stato del crawler : IN ESECUZIONE</b>");
        $("#start").prop("disabled",true);
        $("#stop").prop("disabled",false);
        var twitterRequestBody = {};
        twitterRequestBody["state"] = true;
        $.ajax({
            type: "POST",
            crossDomain: true,
            crossOrigin: true,
            url: "http://193.204.187.192:8087/setState",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify(twitterRequestBody),
            success: function (msg) {
                if(!(msg === '')) {

                    console.log(msg);
                }
            },
            error: function (xhr, status, errorThrown) {
                alert(status, errorThrown);
                // Error block
                console.log("xhr: " + xhr);
                console.log("status: " + status);
                console.log("errorThrown: " + errorThrown);
            }
        });
    });

    $("#stop").click(function() {
        $("#status").html("<b>Stato del crawler : IN ATTESA</b>");
        $("#start").prop("disabled",false);
        $("#stop").prop("disabled",true);
        var twitterRequestBody = {};
        twitterRequestBody["state"] = false;
        $.ajax({
            type: "POST",
            crossDomain: true,
            crossOrigin: true,
            url: "http://193.204.187.192/setState",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify(twitterRequestBody),
            success: function (msg) {
                if(!(msg === '')) {

                    console.log(msg);
                }
            },
            error: function (xhr, status, errorThrown) {
                alert(status, errorThrown);
                // Error block
                console.log("xhr: " + xhr);
                console.log("status: " + status);
                console.log("errorThrown: " + errorThrown);
            }
        });
    });
});




