/** <pre>
 *-------------------------------------------------------------------------------

 * 

 *-------------------------------------------------------------------------------
 * File: AjaxReqRespHandler.js?v=1680753140ta
 *-------------------------------------------------------------------------------
 * DESCRIPTION:
 * It handles the client request as well as server response and
 * delegates to appropriate JS/HTML.
 *
 * AUTHOR	  :   Kalyan
 * MODIFIEDBY :   Tarkeshwar, Bijay and Sangeetha

 * PROJECT    :	  [TOPACCESS]
 * Date Of Creation	: 14-Sep-2006
 * ReOrganised on   : 13-Apr-2007
 * Version No.		: 2.0
 *-------------------------------------------------------------------------------
 * DEPENDENCIES
 *-------------------------------------------------------------------------------
 * </pre>
 */

/** Common Global variables */
var loc = window.location.href;
var serverUrl = "";
var XpathWidgetHashObj = null;
var WidgetValueHashObj = null;
// var ajaxObj = null;
var calledFromPage = "";
var AgentSelect = "";
var diff = 0;
var xmlDoc = null; // Dom Object of response XML
var startTime = null;
var endTime = null;
var showTime = false;
var showalert = false;
var GroupListArraySize = 201;
var gblBoolHttpRespErrorFlag = false;
// var gblAsync= true;
var gblModuleName = "TopAccess";
// var gblGETRequestXMLArray = null;
// var gblGETBoolHandleRespArray = null;
// var gblSETRequestXMLArray = null;
// var gblSETExtendedRequestXMLArray = null;
// var gblSETBoolHandleRespArray = null;
// var gblHashMapArray = null;
// var glbContentWebServerCmdArray = null;
// var gblBoolHandleRespArray = null;
// var glbContentWebServerSaveSessionArray = null;
// var glbContentWebServerGetSessionArray = null;
// var glbContentWebServerGetJobListArray = null;
// var gblCWSCommandLibArray=null;
// var contentWebServerRequest = "";
var gblDomObjArray = new Array();
var requestTree = "";
var dataExportTypeList = "";
var dataExportNode = "";
var gblSessionTimeOutStatus = "";
var gblIsEWB = false;
var gblNtwLoadFlag = true; //false if Network Commit is in progress i.e., Network/Status/state
var DontExecFillGeneral = true;
var sharedSpace = null;

var glbAuthDom = null;
var AuthStatusConstants = {
    "STATUS_OK": "STATUS_OK",
    "STATUS_FAILED": "STATUS_FAILED",
    "STATUS_INVALID_XPATH": "STATUS_INVALID_XPATH",
    "STATUS_BAD_REQUEST": "STATUS_BAD_REQUEST",
    "STATUS_AL_INVALID_SESSION_ID": "STATUS_AL_INVALID_SESSION_ID",
    "STATUS_AUTHENTICATION_FAILED": "STATUS_AUTHENTICATION_FAILED",
    "STATUS_IMG_NOT_FOUND": "STATUS_IMG_NOT_FOUND"
    /// List grows according to requirement
};
var JSAlertsHashTable = {
    "Device": 0,
    "JobStatus-Print": 0,
    "JobStatus-Scan": 0,
    "JobStatus-Fax": 0,
    "Registration-JobTemplates": 0,
    "Registration-AddressBook": 0,
    "Logs": 0,
    "Counter-TotalCounter": 0,
    "Counter-DeptCounter": 0,
    "UserRegistration": 0,
    "Admin-Setup-General": 0
    /// List grows according to requirement
};
serverUrl = window.location.protocol + '//' + ((window.location.hostname.indexOf(":") != -1 && window.location.hostname.indexOf("]") == -1) ? (("[" + window.location.hostname + "]") + ((window.location.port != "") ? ":" + window.location.port : "")) : window.location.host) + '/contentwebserver';
/**
 * This function is called by  HTML page for unLoading the page.
 */
function PageUnload() {
    try {
        /* WidgetValueHashObj = null;
        if(ajaxObj != null && ajaxObj.readyState !=4){
            //ajaxObj.abort();
        }*/
    } catch (e) {
        errHandler(e, 'PageUnload()', 'AjaxReqRespHandler.js?v=1680753140ta', "");
    }

}
/**
 * During the loading html page, this function is invoked.
 * It handles the 1st AJAX request  html page on laod.
 * Parameter : requestType  (Possible values : GET/SET/SETCMDGET/CMD/CMDGET) [case-insensitive]
 */


function InitiateServerRequest(requestType, store, requestObj) {
    if (sharedSpace == null) {
        if (store) {
            sharedSpace = function () {
                return {
                    getStore: function () {
                        return store;
                    }
                }
            }();
        }
    }

    try {
        //fnDisplayLoadingMsg(true);
        if (requestType == "" || requestType == null) {
            alert("RequestType Parameter is empty in InitiateServerRequest()");
            return;
        }
        var winopenerObj = window.top;
        var isEfiling = false;

        var main;
        var pageTrackValue = "";
        try {
            pageTrackValue = fnGetClientCookie("pageTrack");
        } catch (e) {
            var topWindow = winopenerObj.top;
            while (topWindow.opener != null) {
                topWindow = topWindow.opener.top;
                try {
                    pageTrackValue = topWindow.fnGetClientCookie("pageTrack");
                    break;
                } catch (e) {}
            }


        }


        main = pageTrackValue.split("&")[0].split("=")[1];


        try {
            if(window.location === window.opener.location)
                while (winopenerObj.opener != null && typeof winopenerObj.gblTopWindow == 'undefined') {
                    if (winopenerObj.location.href.indexOf("?MAIN=EFILING") != -1) { //winopenerObj.location.href.indexOf("/efiling/Efb.html?v=1680753140ta") != -1){
                        isEfiling = true;
                        break;
                    }
                    winopenerObj = winopenerObj.opener.top;
                }
        } catch (e) {}
        if (winopenerObj.location.href.indexOf("?MAIN=EFILING") != -1) { //winopenerObj.location.href.indexOf("/efiling/Efb.html?v=1680753140ta") != -1){
            if (winopenerObj.fnGetClientCookie("COPYSESSID") != "") {
                if (winopenerObj.fnGetCookie("Session") != winopenerObj.fnGetClientCookie("COPYSESSID")) {
                    winopenerObj.fnSetClientCookie("COPYSESSID", "", -30);
                    winopenerObj.fnSetClientCookie("COPYSESSID", null, -30);
                    fnClearAllSessionCookiesAndRedirect();
                    return;
                }
            }
        }
        requestType = requestType.toUpperCase();
        requestObj.contentWebServerRequest = constructContentWebServerMsg(requestType, requestObj);
        if (requestObj.calledFromPage == "EndEfilingSession" || requestObj.calledFromPage == "GetSessionInformation" || requestObj.calledFromPage == "SaveSessionInformation" || requestObj.calledFromPage == "TopAccessSettings" || requestObj.calledFromPage == "GetDiagnosticSettings" || requestObj.calledFromPage == "validate_fax_pwds" || requestObj.calledFromPage == "GetImageLogSettings-EnableImgLogs" || requestObj.calledFromPage == "IPFax-ValidateSecureRxLinePwd") {
            constructHttpRequest(serverUrl, JSON.stringify(requestObj));
        } else {
            splitValue = pageTrackValue.split("&")[2];
            var cat;
            if (splitValue != null && splitValue != undefined)
                cat = splitValue.split("=")[1];
            else {
                cat = "";
            }
            constructHttpRequest(serverUrl, JSON.stringify(requestObj))
            // setTimeout("constructHttpRequest('"+serverUrl+"','"+JSON.stringify(requestObj)+"')",0);
            if (calledFromPage == "Admin-Setup-Save" && (cat == "NET" || cat == "PRNTSRVC")) {
                //setTimeout("parent.document.location.href = 'NicInit.html?v=1680753140ta'",200);
                fnShowNicInitMsg();
            } else if (calledFromPage == "Set-AirPrintSettings") {
                parent.frames[0].document.getElementById("NicInitDivID").style.display = "block";

                parent.frames[0].document.getElementById("AirPrintTitleId").style.display = "none";
                parent.frames[1].document.getElementById("AirPrintSettingsID").style.display = "none";
            }
        }
    } catch (e) {
        errHandler(e, 'InitiateServerRequest()', 'AjaxReqRespHandler.js?v=1680753140ta', "");
    }

}

function fnShowNicInitMsg() {
    parent.frames[0].document.getElementById("SetupDivID").style.display = "none";
    parent.frames[0].document.getElementById("NicInitDivID").style.display = "block";
    parent.frames[0].document.getElementById("HRID").style.display = "none";
    var pageTrackValue = fnGetClientCookie("pageTrack");
    var splitValue = pageTrackValue.split("&")[2];
    var cat;
    if (splitValue != null && splitValue != undefined)
        cat = splitValue.split("=")[1];
    else {
        cat = "";
    }
    if (cat == "PRNTSRVC") {
        parent.frames[1].document.getElementById("PrntSrvcDiv").style.display = "none";
    } else if (cat == "NET") {
        parent.frames[1].document.getElementById("div_container").style.display = "none";
    }
    if (cat == "IPFAX") {
        parent.frames[1].document.getElementById("IPFaxDiv").style.display = "none";
    }
}


function fnSetClientCookie(name, value, expires, path) {
    var argv = fnSetClientCookie.arguments;
    var argc = fnSetClientCookie.arguments.length;
    var expires = (argc > 2) ? argv[2] : null;
    if (expires != null) {
        var date = new Date();
        date.setTime(date.getTime() + (expires * 86400000));
        expires = date;
    }
    var path = (argc > 3) ? argv[3] : null;
    var domain = (argc > 4) ? argv[4] : null;
    var secure = (argc > 5) ? argv[5] : false;
    
    if(name === 'Session' && window.location.protocol === 'https:') {
        secure = true;
        var date1 = new Date();
        date1.setTime(date1.getTime() + (-30 * 86400000));
        expires = date1;
        document.cookie = name + "=" + escape(value) +
        ((expires == null) ? "" : ("; expires=" + expires.toGMTString())) +
        ((path == null) ? "" : ("; path=" + "/")) ;
        name = "__secure-" + name ;
    }
    else if(name === 'Session'){
        var date1 = new Date();
        var name2 = "__secure-" + name ;
        date1.setTime(date1.getTime() + (-30 * 86400000));
        expires = date1;
        document.cookie = name2 + "=" + escape(value) +
        ((expires == null) ? "" : ("; expires=" + expires.toGMTString())) +
        ((path == null) ? "" : ("; path=" + "/")) ;
    }

    document.cookie = name + "=" + escape(value) +
        ((expires == null) ? "" : ("; expires=" + expires.toGMTString())) +
        ((path == null) ? "" : ("; path=" + path)) +
        ((domain == null) ? "" : ("; domain=" + domain)) +
        ((secure == true) ? "; secure" : "");
}

/***
 * This function construct the Content Web Server xml for Http Requst.
 */
function constructContentWebServerMsg(requestType, requestObj) {
    try {
        var contentWebServerCommand = "";
        var getValue = "";
        var setValue = "";
        var setValueExt = "";
        var command = "";
        var savecws = "";
        var getcws = "";
        var getCwsJobList = "";
        contentWebServerCommand = '<DeviceInformationModel>';
        if (requestType.indexOf("GET") != -1 && requestObj.gblGETRequestXMLArray != null && requestObj.gblGETRequestXMLArray[0] != null) {
            for (var i = 0; i < requestObj.gblGETRequestXMLArray.length; i++) {
                getValue += '<GetValue>' + requestObj.gblGETRequestXMLArray[i] + '</GetValue>';
            }
            contentWebServerCommand += getValue;
        }
        if (requestType.indexOf("SET") != -1 && requestObj.gblSETRequestXMLArray != null && requestObj.gblSETRequestXMLArray[0] != null) {

            for (var i = 0; i < requestObj.gblSETRequestXMLArray.length; i++) {
                setValue += '<SetValue>' + requestObj.gblSETRequestXMLArray[i] + '</SetValue>';
            }
            contentWebServerCommand += setValue;
        }
        if (requestType.indexOf("STXF") != -1 && requestObj.gblSETExtendedRequestXMLArray != null && requestObj.gblSETExtendedRequestXMLArray[0] != null) {

            for (var i = 0; i < requestObj.gblSETExtendedRequestXMLArray.length; i++) {
                setValueExt += '<SetValue overrideDelta="false">' + requestObj.gblSETExtendedRequestXMLArray[i] + '</SetValue>';
            }
            contentWebServerCommand += setValueExt;
        }
        if (requestType.indexOf("CMD") != -1 && requestObj.glbContentWebServerCmdArray != null && requestObj.glbContentWebServerCmdArray[0] != null) {
            for (var i = 0; i < requestObj.glbContentWebServerCmdArray.length; i++) {
                command += '<Command>' + requestObj.glbContentWebServerCmdArray[i] + '</Command>';
            }
            contentWebServerCommand += command;
        }
        if (requestType.indexOf("SCWS") != -1 && requestObj.glbContentWebServerSaveSessionArray != null && requestObj.glbContentWebServerSaveSessionArray[0] != null) {
            for (var i = 0; i < requestObj.glbContentWebServerSaveSessionArray.length; i++) {
                savecws += '<SaveSessionInformation>' + requestObj.glbContentWebServerSaveSessionArray[i] + '</SaveSessionInformation>';
            }
            contentWebServerCommand += savecws;
        }
        if (requestType.indexOf("GCWS") != -1 && requestObj.glbContentWebServerGetSessionArray != null && requestObj.glbContentWebServerGetSessionArray[0] != null) {
            for (var i = 0; i < requestObj.glbContentWebServerGetSessionArray.length; i++) {
                getcws += '<GetSessionInformation>' + requestObj.glbContentWebServerGetSessionArray[i] + '</GetSessionInformation>';
            }
            contentWebServerCommand += getcws;
        }
        if (requestType.indexOf("GJOBCWS") != -1 && requestObj.glbContentWebServerGetJobListArray != null && requestObj.glbContentWebServerGetJobListArray[0] != null) {
            for (var i = 0; i < requestObj.glbContentWebServerGetJobListArray.length; i++) {
                getCwsJobList += '<GetJobList>' + requestObj.glbContentWebServerGetJobListArray[i] + '</GetJobList>';
            }
            contentWebServerCommand += getCwsJobList;
        }
        if (requestType.indexOf("LIB") != -1 && requestObj.gblCWSCommandLibArray != null && requestObj.gblCWSCommandLibArray[0] != null) {
            for (var i = 0; i < requestObj.gblCWSCommandLibArray.length; i++) {
                getcws += '<CommandLib>' + requestObj.gblCWSCommandLibArray[i] + '</CommandLib>';
            }
            contentWebServerCommand += getcws;
        }
        contentWebServerCommand += '</DeviceInformationModel>';
        //alert("contentWebServer command = "+contentWebServerCommand);
        return contentWebServerCommand;


    } catch (e) {
        errHandler(e, 'constructContentWebServerMsg()', 'AjaxReqRespHandler.js?v=1680753140ta', "")
    }

}

/**
 *  Create the XMLHttpRequest or ActiveXObject object depending on the browser support
 */
function XMLHttp() {
    try {
        if (window.XMLHttpRequest && navigator.appName != "Microsoft Internet Explorer") {
            return new XMLHttpRequest();
        } else if (window.ActiveXObject || "ActiveXObject" in window) {
            //modified the above else if condition for the DCR_51850 L6.02/03
            var avers = ["Microsoft.XmlHttp", "MSXML2.XmlHttp", "MSXML2.XmlHttp.3.0", "MSXML2.XMLHTTP.6.0"];
            for (var i = avers.length - 1; i >= 0; i--) {
                try {
                    httpObj = new ActiveXObject(avers[i]);
                    return httpObj;
                } catch (e) {}
            }
        }
        return null;
    } catch (e) {
        errHandler(e, 'XMLHttp()', 'AjaxReqRespHandler.js?v=1680753140ta', "");
        return null;
    }
}
/**
 *  Handle the AJAX request to server for  Async call
 *  and get the server response and delegate the final response to the method handleServerResponse.
 */
function constructHttpRequest(url, requestObj) {
    /// Async call to Server
    requestObj = JSON.parse(requestObj)
    try {
        var ajaxObj = XMLHttp();
        if (ajaxObj == null) {
            alert("AJAX is not supported by your Browser");
            //nonAjaxHandling(url,xml);
            return;
        }
        ajaxObj.open('POST', url, requestObj.gblAsync);
        // 2 line add Because of Mac OS(Mountain lion) and Safari issue : Due to cache the next similar command was not sent to Plug-in or CWS.
        ajaxObj.setRequestHeader('Cache-Control', 'no-cache');
        ajaxObj.setRequestHeader('Pragma', 'no-cache');
        ajaxObj.setRequestHeader('Content-Type', 'text/plain; charset=utf-8');
        // ajaxObj.onreadystatechange = (function (calledFrom) {
        //     alert(calledFrom)
        //     return fnOnReadyStateHandler(calledFrom)
        // });
        ajaxObj.onreadystatechange = fnOnReadyStateHandler.bind(this, requestObj, ajaxObj);
        //inserting session id as csrf id in the header while sending every POST request
        var sessionID = null;
        var offset = document.cookie.indexOf("Session=");
        if (offset != -1) // if cookie exists
        {
            offset += "Session=".length; // set index of beginning of value
            var end = document.cookie.indexOf(";", offset); // set index of end of cookie value
            if (end == -1)
                end = document.cookie.length;
            sessionID = unescape(document.cookie.substring(offset, end));
        }
        if (sessionID == null || sessionID == "") {
            location.reload();
            return;
        } else {
            ajaxObj.setRequestHeader('csrfpId', sessionID);
        }
        // end of the set CSRFId


        ajaxObj.send(requestObj.contentWebServerRequest);
        if (/Firefox[\/\s](\d+\.\d+)/.test(navigator.userAgent) && (requestObj.calledFromPage == "GetSessionInformation" || requestObj.calledFromPage == "TopAccessSettings" || requestObj.calledFromPage == "GetDiagnosticSettings") && requestObj.gblAsync == false && requestObj.gblBoolHandleRespArray[0] == true) {
            //alert("response:"+ajaxObj.responseText);
            fnOnReadyStateHandler(requestObj, ajaxObj);
        }
        //ajaxObj.send('sender='+contentWebServerXml+'&uniqueId='+Math.random()+'&Objtype=xmlhttprequest'); // Soap Server
    } catch (e) {
        alert(e.message + "(" + (e.number & 0xFFFF) + ")");
        //errHandler(e,'constructHttpRequest()','AjaxReqRespHandler.js?v=1680753140ta',"");
        gblBoolHttpRespErrorFlag = true;
    }
}

function fnOnReadyStateHandler(requestObj, ajaxObj) {
    try {
        var slicedXML = "";
        var intCounter = 0;
        var ajaxObjStatus;
        var responseText;
        if (ajaxObj.readyState == 4) {
            try {
                ajaxObjStatus = ajaxObj.status;
            } catch (Exp) {
                ajaxObj.abort();
                return;
            }
            if (ajaxObjStatus != 200) {
                if (ajaxObjStatus != 0 && ajaxObjStatus != 301 && ajaxObjStatus != 307) {
                    if(ajaxObjStatus == 502){
                       window.location.replace('/');
                    }
                    else if (HTTPError_Status[ajaxObj.status] == undefined || HTTPError_Status[ajaxObj.status] == "undefined")
                        resptype = 'Error : Status ' + ajaxObj.status + ' returned.';
                    else if (requestObj.calledFromPage != "RebootController" && requestObj.calledFromPage != "CloneApplyStatus")
                        alert(fnGetLocaleString("103698", "Server Error has occurred. Please try again.") + "('" + ajaxObjStatus + "')");
                }
            } else { ///if status = 200
                gblIsEWB = navigator.userAgent.indexOf("QtEmbedded") != -1; // isEWB --> false is browser is not EWB
                if (gblIsEWB) {
                    try {
                        responseText = ajaxObj.responseXML;
                    } catch (ex) {
                        alert("Server Error!! Reason :: Parsing failed");
                        return;
                    }
                } else {
                    responseText = ajaxObj.responseText;
                    if (/^MODULE_ERROR:/.test(responseText)) {
                        if (requestTree != "Network" && requestObj.calledFromPage != "RebootController" && requestObj.calledFromPage != "CloneApplyStatus") {
                            alert(fnGetLocaleString("103698", "Server Error has occurred. Please try again.") + "('" + responseText.substring(13) + "')");
                        }
                        return;
                    }
                    if (requestObj.calledFromPage != "LogoutUser" && requestObj.calledFromPage != "LogoutUser_Efiling" && requestObj.calledFromPage != "PwdResetLogoutUser_Efiling" && requestObj.calledFromPage != "PwdResetLogoutUser" && requestObj.calledFromPage != "Nav_Conditions" && responseText.indexOf("<Response><statusOfOperation>STATUS_SESSION_FOLDER_NOT_EXISTS</statusOfOperation></Response>") != -1) {
                        var loginMode = fnGetCookie("LOGINMODE");

                        if (loginMode == "SECURED") {
                            fnSetClientCookie("pageTrack", "MAIN=LOGIN");
                        } else {
                            fnSetClientCookie("pageTrack", "MAIN=DEVICE");
                        }

                        fnSetCookie("SESSID", null, -30);
                        fnSetCookie("USERCRED", null, -30);
                        fnSetCookie("LOGINSTATUS", null, -30);
                        fnSetCookie("USERROLE", null, -30);
                        fnSetCookie("LOGINMODE", null, -30);
                        fnSetCookie("TAPERMISSIONS", null, -30);
                        fnSetCookie("EFICtrlInstalled", null, -30);
                        fnSetCookie("SECURITYLEVEL", null, -30);
                        fnSetCookie("TA_SETTINGS", null, -30);
                        fnSetCookie("LICENSE_SETTINGS", null, -30);
                        fnSetCookie("DiagnosticMode", null, -30);
                        fnSetCookie("IgnoreSessionTimeout", "0");
                        fnSetClientCookie("forcedPasswordReset", "false");
                        location.reload();
                        return;
                    }
                    if (responseText == "<DeviceInformationModel><Response><statusOfOperation>STATUS_AL_CWS_UNAUTHORIZED</statusOfOperation></Response></DeviceInformationModel>" && requestObj.calledFromPage != "EndEfilingSession") {
                        // var reduxStore = sharedSpace.getStore();
                        alert(fnGetLocaleString("103490", "You do not have the permission."));
                        // reduxStore.dispatch({type: "LOGOUT"});
                        // alert(fnGetLocaleString("103490","You do not have the permission."));
                        fnSetClientCookie("Session", null, -30, "/");
                        fnSetClientCookie("IgnoreSessionTimeout", null, -30, "/");
                        fnSetClientCookie("forcedPasswordReset", "false");
                        location.reload();
                        return;
                    }
                }
                if (requestObj.gblBoolHandleRespArray[0]) {
                    //Extract the XML out of Web Server Response.
                    if (gblIsEWB) {
                        slicedXML = responseText;
                    } else {
                        slicedXML = extractRespXML(responseText);
                    }
                    if (slicedXML == null) {
                        gblBoolHttpRespErrorFlag = true;
                        return;
                    }
                }
                // if gblBoolHttpRespErrorFlag is false handleserverresponse
                if (!gblBoolHttpRespErrorFlag) {
                    if (requestObj.calledFromPage != "PrintService_DeleteAll" && requestObj.calledFromPage != "PrintService_ExecConnectTest" && requestObj.calledFromPage != "PrintService_SharedPrint" && requestObj.calledFromPage != "ConnectionTest") {
                        if (gblIsEWB) {
                            gblDomObjArray[0] = responseText;
                        } else {
                            gblDomObjArray[0] = parseXML(slicedXML);
                        }
                        xmlDoc = gblDomObjArray[0];
                    } else {
                        if (gblIsEWB) {
                            xmlDoc = responseText;
                        } else {
                            xmlDoc = parseXML(slicedXML);
                        }
                    }

                    if (xmlDoc != null && xmlDoc != undefined) {
                        var arrSOP = xmlDoc.getElementsByTagName("statusOfOperation");
                        var child = null;
                        for (var i = 0; arrSOP[0] != null && i < arrSOP.length; i++) {
                            child = arrSOP[i].childNodes[0];
                            //	if(gblModuleName!="TopAccess")       // For Testing Purpose it is hardcoded.
                            //		child.nodeValue="STATUS_NOT_ALLOWED";
                            if (child == null) {
                                alert("Status of operation is empty.");
                                return;
                            }
                            if ((child.nodeValue == "STATUS_PERMISSION_CHECK_ERROR" && calledFromPage != "GetDiagnosticSettings") || (gblModuleName != "TopAccess" && child.nodeValue == "STATUS_FUNCTION_DISABLED")) {
                                // var reduxStore = sharedSpace.getStore();
                                alert(fnGetLocaleString("103490", "You do not have the permission."));
                                // reduxStore.dispatch({type: "LOGOUT"});
                                // alert(fnGetLocaleString("103490","You do not have the permission."));
                                // if(calledFromPage == 'UpdateUserInfo')
                                //     document.frmUserEdit.Save.disabled = false;
                                // if(calledFromPage == 'RegisterNewUser')
                                //     document.frmUserCreate.Save.disabled = false;
                                // if(calledFromPage == "DeleteAllUsersInfo")
                                //     document.frmUserList.DeleteAll.disabled = false;
                                return;
                            } else if (child.nodeValue == AuthStatusConstants["STATUS_AL_INVALID_SESSION_ID"]) {
                                fnClearAllSessionCookiesAndRedirect(1);
                                return;
                            } else if (child.nodeValue == "STATUS_NOT_ALLOWED") {
                                alert(fnGetLocaleString("101418", "Please wait. Please try again later.") + "\n" + fnGetLocaleString("100251", "Backup or restoration process in progress."));
                                glLoginCancel = "LoginCanceled";
                                var winopenerObj = window.top;
                                var isEfiling = false;
                                while (winopenerObj.opener != null && typeof winopenerObj.gblTopWindow == 'undefined') {
                                    if (winopenerObj.location.href.indexOf("?MAIN=EFILING") != -1) { //winopenerObj.location.href.indexOf("/efiling/Efb.html?v=1680753140ta") != -1){                                       
                                        isEfiling = true;
                                        break;
                                    }
                                    winopenerObj = winopenerObj.opener.top;
                                }
                                winopenerObj.close();
                                return;
                            }
                        }
                        if (requestObj.gblBoolHandleRespArray[0]) {
                            handleServerResponse(xmlDoc, requestObj);
                        }
                    }
                    // switch (calledFromPage) {
                    //     case "Device"		: fillDevicePage();
                    //         break;
                    //     case "General"		:
                    //         if(DontExecFillGeneral)
                    //             DontExecFillGeneral=false;
                    //         else
                    //             fillGeneralPage();
                    //         break;
                    //     case "SaveAsFile"	: fillSaveAsFile(xmlDoc);
                    //         break;
                    //     case "Network"		:
                    //                             if(gblNtwLoadFlag){
                    //                                 fillNetworkPage(xmlDoc);
                    //                             }
                    //         break;
                    // }
                    /* if(calledFromPage != "TopAccessSettings")
                         fnDisplayLoadingMsg(false);*/
                } else {
                    /*if(calledFromPage != "TopAccessSettings")
                        fnDisplayLoadingMsg(false);*/
                    alert("Error : Cannot handle the ServerResponse in onreadystatechange()");
                    return;
                }
                if (window.navigator.userAgent.indexOf("Edge") > -1 && calledFromPage == "IsLoginRequired")
                    delete["ajaxObj.onreadystatechange"];
                else
                    delete(ajaxObj.onreadystatechange);
            } //if (ajaxObj.status != 200)
        } //if (ajaxObj.readyState == 4)

    } catch (e) {
        errHandler(e, 'onreadystatechange()', 'AjaxReqRespHandler.js?v=1680753140ta', "");
        gblBoolHttpRespErrorFlag = true;
    }

}


/***
 * Handle the Server Response
 */
function handleServerResponse(xmlDoc, requestObj) {
    try {
        if (requestObj.calledFromPage == "GetSessionInformation") {
            fnHandleGetSessionInformation(xmlDoc);
            return;
        }
        var reduxStore = sharedSpace.getStore();

        if (requestObj.calledFromPage === "get_mntnc_create_new_export_file") {
            reduxStore.dispatch({
                type: "ROOT_RESPONSE_HANDLER",
                payload: [xmlDoc, requestObj.calledFromPage, requestObj.calledFromComponent]
            });
            return
        }
        reduxStore.dispatch({
            type: "ROOT_RESPONSE_HANDLER",
            payload: [xmlDoc, requestObj.calledFromPage]
        });

        //                 switch (calledFromPage) {
        //                     case "GetSessionInformation"  :   fnHandleGetSessionInformation(xmlDoc);
        //                                                         break;
        //                     case "SaveSessionInformation" :   fnHandleSaveSessionInformation(xmlDoc);
        //                                                       break;
        //                     case "IsLoginRequired"  :  fnHandleIsLoginRequired(xmlDoc);
        //                                                   break;
        // 					case "GetLoginPassword"  : fnHandleGetLoginPassword(xmlDoc);
        //                                                   break;                                                  	
        //                     case "TopAccessSettings" : fnHandleTopAccessSettings(xmlDoc);
        //                                                 break;
        //                     case "GetDiagnosticSettings" : fnHandleGetDiagnosticMode(xmlDoc);
        //                                                 break;
        //                     case "GetTopMenuLogAuthSettings" : fnHandleGetLogAuthenSettings(xmlDoc);
        //                                                 break;
        //                     case "Login-AuthenticationSettings" : fnHandleAuthenticationMode(xmlDoc);
        //                                                 break;
        // 					case "RelayEnd-GetSelectedAddrBkContact": fnBindRelayEndDest(xmlDoc);
        //                                                   break;
        // 					case "GetInstalledLanguages": fnHandleLanguagesPage(xmlDoc);
        //                                                   break;
        //                     case "SetMachineDefaultLanguage": fnHandleSetMachineDefaultLanguage(xmlDoc);
        //                                                   break;
        //                     case "DeleteLanguagePack": fnHandleDeleteLanguagePack(xmlDoc);
        //                                                   break;
        //                     case "ImportLanguagePack": fnHandleImportLanguagePack(xmlDoc);
        //                                                   break;
        //                     case "SNMPExport" : fnHandleSNMPExportInfo(xmlDoc);
        //                                         break;
        //                     case "Network-GetDDNSFileInfo" : fnnFillDDNSInfo(xmlDoc);
        //                                         break;
        //                     case "Network-GetCertInstalledStatus" : fnHandleCertInstalStatus(xmlDoc);
        //                                         break;
        //                     case "Network-GetSNMPV3UserInfo" : fnUpdateSNMPV3UserInfo(xmlDoc);
        //                                         break;
        //                     case "Network-DelSNMPV3User" : fnHandleDelSNMPV3Usr(xmlDoc);
        //                                         break;
        //                     case "RebootMFPcheck" : 
        //                     case "BeforeSendingReboot" : fnHandleCheckMFPState(xmlDoc);
        //                                         break;
        //                     case "Network-SNMPPwdValidation" : fnHandleValidateSNMPPwd(xmlDoc);
        //                                         break;
        //                     case "Get-AirPrintSettings" :  fnFillAirPrintSettings(xmlDoc);
        //                         break;
        //                     case "Network-Firewall" :   fillFirewallPage(xmlDoc);
        //                                         break;
        //                     case "Network-SMB"  :   handleGetSMBSettings(xmlDoc);
        //                                         break;
        // 	                case "Network-iFrame-save"  :
        //                     case "Network-Save" : handleNetworkSave(xmlDoc);
        //                                         break;
        //                     case "Network-SNMP" : fillSNMP(xmlDoc);
        //                                         break;
        // 	                case "Network-SMTP-Client" : handleSMTPClientSetting(xmlDoc);
        // 		                                break;
        //                     case "Network-SYSLOG" : handleSyslogSetting(xmlDoc);
        //                                         break;
        //                     case "Network-GetVNCSettings" : handleVNCSetting(xmlDoc);
        //                                         break;
        //                     case "SaveVNC" : handleSaveVNC(xmlDoc);
        //                         break;

        //                     /*case "Set-AirPrintSettings" : fnHandleSetAirPrintSettings(xmlDoc);
        //                         break;*/
        //                     case "GetSharedAddrBkSettings" : fnSharedSettingsHandler(xmlDoc);
        //                                                           break;
        //                     case "Sync-SharedAddrBk" : fnSyncHandler(xmlDoc);
        //                                                           break;
        //                     case "SyncAll-SharedAddrBk" : fnSyncAllHandler(xmlDoc);
        //                                                           break;
        //                     case "GetSharedAddrBkInfo-SharedAddrBk" : fnFillSharedAddrBkDetails(xmlDoc);
        //                                                           break;
        //                     case "SetSharedAddrBkInfo-SharedAddrBk" : fnSaveHandler(xmlDoc);
        //                                                           break;
        //                     case "PrintService_ExecConnectTest" : fnExecConnectTestHandler(xmlDoc);
        //                                                             break;
        //                     case "PrintService_DeleteAll" : fnDeleteAllHandler(xmlDoc);
        //                                                             break;
        //                     case "PrintService_SharedPrint" : setSharedPrintValues(xmlDoc);
        //                                                             break;
        //                     case "PrintService" : fnChkNtwStatus(xmlDoc);
        //                             break;
        //                     case "Network" : fillNetworkPage(xmlDoc);
        //                                         break;
        //                     case "InstalledWebHelpLanguages" : fnHandleInstalledWebHelpLanguages(xmlDoc);
        //                                         break;
        //                     case "IPSecFlushConnections"
        //                     :   fnHandleFlushConnections(xmlDoc);
        //                         break;
        //                     case "SessionTimeOut"
        //                     :   fnHandleTopAccessSessionTimeOut(xmlDoc);
        //                         break;
        //                     case "ICC-GetSettings" : fnHandleGetICCSettings(xmlDoc);
        //                                              break;
        //                     case "RGB-Maintenance-GetSettings" : fnReloadICCSettings(xmlDoc);
        //                         break;
        //                     case "ICC-GetProfList" : fnFillICCList(xmlDoc);
        //                                              break;
        //                     case "ICC-SaveICCDefaultSettings" :
        //                     case "ICC-FactoryDefault" : fnRefreshICC(xmlDoc);
        //                                              break;
        //                     case "ICC-ImportProfile" :
        //                     case "ICC-DeleteProfile" : fnRefreshMaintenance(xmlDoc);
        //                                              break;
        //                     case "ICC-ExportProfile" : fnHandleExport(xmlDoc);
        //                                              break;
        //                     case "EWB-GetSettings" : fnHandleGetEWBSettings(xmlDoc);
        //                                              break;
        //                     case "EWB-ChildGetSettings" : fnHandleChildGetEWBSettings(xmlDoc);
        //                                              break;
        //                     case "EWB-AddEditMenuUrl" :
        //                     case "EWB-DeleteMenuUrl"  : fnHandleAddEditDeleteMenuURL(xmlDoc);
        //                                              break;
        //                     case "EWB-AddDeleteTrustedServer" : fnHandleAddDeleteEwbTrustedServer(xmlDoc);
        //                                              break;
        //                     case "EWB-AddDeleteMenuUrl" : fnHandleAddDeleteEwbMenuUrl(xmlDoc);
        //                                              break;
        //                     case "EWB-Licensecheck" : fnHandleEWBLicenseCheck(xmlDoc);
        //                                              break;
        //                     case "PDLFilter-Import" : fnHandleImportPDLFilter(xmlDoc);
        //                                              break;
        //                     case "PDLFilter-GetFilter" : fnHandleGetFilters(xmlDoc);
        //                                              break;
        //                     case "PDLFilter-DeleteFilter" : fnHandleDeleteFilter(xmlDoc);
        //                                              break;
        //                     case "PDLFilter-DeleteFilterFollowOn" : fnHandleDeleteFilterFollowOn(xmlDoc);
        //                                              break;
        //                     case "PDLFilter-ExportFilter" : fnHandleExportFilter(xmlDoc);
        //                                              break;
        //                     case "SoftwareUpdate-Import" : fnHandleImportSystemUpdate(xmlDoc);
        //                                              break;
        //                     case "SoftwareUpdate-InstallComponents" : fnHandleInstallComponents(xmlDoc);
        //                                              break;
        //                     case "SoftwareUpdate-GetUpdateHistory" : fnHandleGetUpdateHistory(xmlDoc);
        //                                              break;
        //                     case "SoftwareUpdate-CheckMFPState" : fnHandleSysUpdCheckForMFPState(xmlDoc);
        //                                              break;
        //                     case "SaveNotification"
        //                     :   fnNotificationRespHandler(xmlDoc);
        //                         break;
        //                     case "Admin-Setup-Save"
        //                     :
        //                         var splitValue = fnGetClientCookie("pageTrack");
        //                         splitValue = splitValue.split("&");
        //                         if(splitValue.length>2) {
        //                             splitValue = splitValue[2];
        //                             splitValue = splitValue.split("=")[1];
        //                             if (splitValue == 'PRINT')
        //                                 parent.parent.fraTitle.fnAdminSetupRespHandler(xmlDoc);
        //                             else
        //                                 parent.fraTitle.fnAdminSetupRespHandler(xmlDoc);
        //                         }
        //                         break;
        //                     case "Security-SecureErase"
        //                     :   fillSecureErase(xmlDoc);
        //                         break;
        //                     /*case "Security-FirewallOperations"
        //                     :   refreshFirewallPage(xmlDoc);
        //                         break;*/
        //                     case "Security-SaveSecureErase"
        //                     :   redirectSecureErase(xmlDoc);
        //                         break;
        //                     case "Security-GetCryptographicSettings"
        //                     :   fillCryptographicSettings(xmlDoc);
        //                         break;
        //                     case "Security-SaveCryptSettings"
        //                     :   redirectCryptSettings(xmlDoc);
        //                         break;
        //                     case "Security-GetTimeStampSettings"
        //                     :   fillTimeStampDetails(xmlDoc);
        //                         break;
        //                     case "Security-SaveTimeStampSettings"
        //                     :   redirectTimeStampDetails(xmlDoc);
        //                         break;
        //                     case "Security-GetPwdSettings"
        //                     :   fillPwdSettings(xmlDoc);
        //                         break;
        //                     case "Security-SavePwdSettings"
        //                     :   redirectPwdSettings(xmlDoc);
        //                         break;
        //                     case "Security-SaveAuthSettings"
        //                     :   redirectAuthSettings(xmlDoc);
        //                         break;
        //                     case "Security-AuthSettings"
        //                     :   fnFillAuthenticationSettings(xmlDoc);
        //                         break;
        //                     case "Security-RestrictDestSetting"
        //                     :   fnFillRestrictDestSettings(xmlDoc);
        //                         break;
        //                     case "Security-LDAPAuthSetting"
        //                     :   fnFillLDAPAuthSettings(xmlDoc);
        //                         break;
        //                     case "Security-GetSecurityLevel"
        //                     :   fillSecurityLevel(xmlDoc);
        //                         break;
        //                     case "Security-CheckRebootReqd"
        //                     :   fnCheckRebootForSecLevel(xmlDoc);
        //                         break;
        //                     case "Security-SetSecurityLevel"
        //                     :   redirectSetSecLevel(xmlDoc);
        //                         break;
        //                     case "Security-GetCertInfo" :
        //                         fillCertPage(xmlDoc);
        //                         break;
        //                     case "Security-GenSelfSignedCert" :
        //                         fnHandleGenSelfSignedCert(xmlDoc);
        //                         break;
        //                     case "Security-GenExtCert" :
        //                         redirectGenExtCert(xmlDoc);
        //                         break;
        //                     case "Security-GetExportCertInfo" :
        //                         fnHandleGetExportCertInfo(xmlDoc);
        //                         break;
        //                     case "Security-ExportPubKey" :
        //                         fnCertExportKeyHandler(xmlDoc);
        //                         break;
        //                     case "Security-ImportCertificate" :
        //                         fnCertImportHandler(xmlDoc);
        //                         break;
        //                     case "Security-SaveCertSettings" :
        //                         redirectSaveCertSettings(xmlDoc);
        //                         break;
        //                     case "Security-DelCert" :
        //                         fnHandleDelCACRLCert(xmlDoc);
        //                         break;
        //                     case "Security-CreateClientCert" :
        //                         fnHandleCreateClientCert(xmlDoc);
        //                         break;
        //                     case "Security-ResetTemplate" :
        //                         fnHandleResetTemplate(xmlDoc);
        //                         break;
        //                     case "Security-GetIPFirewallInfo"
        //                     :
        //                     //fillFirewallPage(xmlDoc);
        //                         break;
        //                     case "Admin-FaxFwd":
        //                     case "AdminFaxFwd-Onload-ContinueHandler" 
        //                     :   fnOnloadBindToAgentChkBoxAndRedirect(xmlDoc);
        //                         break;
        //                     case "AdminFaxFwd-Onload-ContinueHandler-SelectAgentClick"
        //                     :   fnHandleContinueSeleAgentAndRedirect(xmlDoc);
        //                         break;
        //                     case "Admin-FaxFwd-CommitRespHandler"
        //                     :   fnCommitRespHandler(xmlDoc);
        //                         break;
        //                     case "AddressBook-Mailbox-links" :  fnHandleAddrBkMailboxLinksDisplay(xmlDoc);
        //                                                         break;
        //                     case "Admin-FaxFwd-ValidateSecurePDF"
        //                     :	fnHandleValidateSecurePDFPwds(xmlDoc);
        //                         break;
        //                     case "GetFTPForceSSLSetting" :	fnHandleGetFTPForceSSLSetting(xmlDoc);
        //                                                                             break;
        //                     case "Log-Scan"				:
        //                     case "Log-Print"		    :
        //                     case "Log-FaxTx"		    :
        //                     case "Log-FaxReception"		:
        //                     case "Log-Application"		:
        //                     case "Log-Message"			: fillLogPage(xmlDoc,calledFromPage);
        //                         break;
        //                     case "GetLogSettings"       : fillLogSetting(xmlDoc);
        //                         break ;
        //                     case "setLogSettingsValue"  : redirectToLogSetting(xmlDoc) ;
        //                         break ;
        //                     case "GetLogArchiveStatus"  : redirectToArchiveStatus(xmlDoc);
        //                         break ;
        //                     case "ArchiveProcess"      : redirectToArchiveProgress(xmlDoc) ;
        //                         break ;
        //                     case "getArchiveDetails"   : fillArchiveDetails(xmlDoc);
        //                         break ;
        //                     case "deleteArchiveDetails"  : redirectDeletArchive(xmlDoc);
        //                         break ;
        //                     case "GetLogValidateStatus"  : redirectToValidateStatus(xmlDoc);
        //                         break ;
        //                     case "ValidateJobLog"      : fillJobLog(xmlDoc);
        //                         break ;
        //                     case "GetLogProgressBar"  :  fillProgressBar(xmlDoc);
        //                         break ;
        //                     case "ExportLogFile"	  : getExporedtLogFileInfo(xmlDoc);
        // 						break ;
        //                     case "CreateExportLogs"   : redirectToExportLog(xmlDoc) ;
        //                         break ;
        //                     case "GetControllerData"   : clearLogAfterControllerData(xmlDoc) ;
        //                         break ;
        //                     case "ClearExportedLogs"   :  fillLogExportInfo(xmlDoc);
        //                         break ;

        //                     case "Authentication-Scan2Email-LDAP"
        //                     :	fillLDAPdropdown(xmlDoc);
        //                         break;
        //                     case "Authentication-Scan2EMail-SaveSMTPClientAlso"
        //                     :	fnnCommitSMTPClientSettings();
        //                         break;
        //                     case "Authentication"		:
        //                         fnnPopulateDeptAdminTable(xmlDoc);
        //                         fnnPopulateUserAuthTable(xmlDoc);
        //                         fnnPopulateSMTPAuthTable(xmlDoc);
        //                         break;
        //                     case "EmailAuthentication"		:
        //                         fnEmailAuthentication(xmlDoc);
        //                         break;
        //                     case "AdminEmail"           :   fillAdminEmailPage(xmlDoc);
        //                         break;
        //                     case "InternetFax"		:
        //                         fillAdminIfaxPage(xmlDoc);
        //                         break;
        //                     case "Authentication-PageRefresh"
        //                     :   refreshAuthenPage();
        //                         break;
        //                     case "JobList-Print"		:   fillPrintJobListPage(xmlDoc);
        //                         break;
        //                     case "JobList-Suspend"		:   fillSuspendPrintJobListPage(xmlDoc);
        //                         break;
        //                     case "JobList-Scan"			:   fillScanJobListPage(xmlDoc);
        //                         break;
        //                     case "JobList-Fax"			:   fillFaxJobListPage(xmlDoc);
        //                         break;
        //                     case "ScanJobCancel"        :   refreshJobPage(xmlDoc, 'Scan');
        //                         break;
        //                     case "PrintJobResume"       :

        //                     case "PrintJobCancel"       :   refreshJobPage(xmlDoc, 'Print');
        //                         break;
        //                     case "DeleteAllPrintJob"       :   deleteAllJobsHandler(xmlDoc, 'PrintJob');
        //                         break;
        //                     case "DeleteAllPrivateJob"       :   deleteAllJobsHandler(xmlDoc, 'Private');
        //                         break;
        //                     case "DeleteAllHoldJob"       :   deleteAllJobsHandler(xmlDoc, 'Hold');
        //                         break;
        //                     case "DeleteAllScanJob"       :   deleteAllJobsHandler(xmlDoc, 'ScanJob');
        //                         break;
        //                     case "DeleteAllFaxJob"       :   deleteAllJobsHandler(xmlDoc, 'FaxJob');
        //                         break;
        //                     case "DeleteAllAnywhereJobs"  : deleteSelectedJobsHandler(xmlDoc,'Anywhere-jobs');
        //                         break;
        //                     case "DeleteAllAnywherePrint"  : deleteSelectedJobsHandler(xmlDoc,'Anywhere-print');
        //                         break;
        //                     case "DeleteSelectedPrintJob"       :   deleteSelectedJobsHandler(xmlDoc, 'PrintJob');
        //                         break;
        //                     case "DeleteSelectedPrivateJob"       :   deleteSelectedJobsHandler(xmlDoc, 'Private');
        //                         break;
        //                     case "DeleteSelectedHoldJob"       :   deleteSelectedJobsHandler(xmlDoc, 'Hold');
        //                         break;
        //                     case "DeleteSelectedScanJob"       :   deleteSelectedJobsHandler(xmlDoc, 'ScanJob');
        //                         break;
        //                     case "DeleteSelectedFaxJob"       :   deleteSelectedJobsHandler(xmlDoc, 'FaxJob');
        //                         break;
        //                     case "DeleteSelectedAnywhereJobs"  : deleteSelectedJobsHandler(xmlDoc,'Anywhere-jobs');
        //                         break;
        //                     case "DeleteSelectedAnywherePrint"  : deleteSelectedJobsHandler(xmlDoc,'Anywhere-print');
        //                         break;
        //                     case "FaxJobCancel"         :   refreshJobPage(xmlDoc, 'Fax');
        //                         break;
        //                     case "PrintSkippedJob"      :   getSkippedJobStatus(xmlDoc);
        //                         break;
        //                     case "ValidateDepartment"   :   validatedDeptCodeResp(xmlDoc);
        //                         break;
        //                     case "Counters"				:   fillCounterPage(xmlDoc);
        //                         break;
        //                     case "GenerateCloneFile"    :   fnHandleGenerateCloneFile(xmlDoc);
        //                         break;
        //                     case "CloneGenerationStatus"    :   fnHandleGetCloneGenerationStatus(xmlDoc);
        //                         break;
        //                     case "GetGeneratedCloneFileMetaData"    :   fnHandleGetGeneratedCloneFileMetaData(xmlDoc);
        //                         break;
        //                     case "ApplyCloneFile"    :   fnHandleApplyCloneFile(xmlDoc);
        //                         break;
        //                     case "CloneApplyStatus"    :   fnHandleCloneApplyStatus(xmlDoc);
        //                         break;
        //                     //Templates Related
        //                     case "TemplatePrivateListAll"
        //                                                 :   fnnLoadPrivateTemplate(xmlDoc);
        //                         break;
        //                     case "TemplateGroups"		:   fillTemplateGroupPage(xmlDoc);
        //                         break;
        //                     case "ForcedEncryptionStatus"
        //                                                 :   fnForcedEncryptionStatus(xmlDoc);
        //                         break;
        //                     case "TemplateList"			:

        //                     case "PrivateTemplateList"	:

        //                     case "AdminPublicPanel"		:   fillTemplateListPage(xmlDoc);
        //                         break;
        //                     case "ChkGrpRBACStatus"		:   fnChkGrpRBACStatus(xmlDoc);
        //                         break;
        //                     case "ChkAdminTmpRBACStatus":

        //                     case "ChkTmpRBACStatus"		:

        //                     case "ChkTmpRBACStatusAfterPasswordChange"
        //                                                 :   fnChkTmpRBACStatus(xmlDoc);
        //                             break;
        //                     case "deleteTemplate"		:   redirectFromTemplateProperties(xmlDoc);
        //                         break;
        //                     case "TemplateGroupPassword_Check"
        //                                                 :   fillGroupPwdPage(xmlDoc);
        //                         break;
        //                     case "PrivateTemplateEdit"
        //                                                 :   fillPrivateTemplateEdit(xmlDoc);
        //                         break;
        //                     case "DisplayTemplateProperties"
        //                                                 :
        //                     case "TemplatePasswordCheck":   fillTemplatePropertiesPage(xmlDoc);
        //                         break;
        //                     case "TemplatePasswordChange"
        //                                                 :    fillTemplatePasswordPage(xmlDoc);
        //                         break;
        //                     case "DeleteGroupTemplate"	:   redirectFromDeleteGroup(xmlDoc);
        //                         break;
        //                     case "AdminPublicTempReset"	:   redirectFromDeleteAdminPublicTemp(xmlDoc);
        //                         break;
        //                     case "UpdateGroupMetadata"	:   fillPvtUpdateGroupPage(xmlDoc);
        //                         break;
        //                     case "CreateNewGroup"		:   redirectFromCreateNewGroup(xmlDoc);
        //                         break;
        //                     case "UpdateGroupMetadata_click"
        //                                                 :   redirectFromUpdateGroup(xmlDoc);
        //                         break;
        //                     case "ChangePrivateGroupPassword"
        //                                                 :   fillPvtGroupChangePwdPage(xmlDoc);
        //                         break;
        //                     case "ChangePrivateTemplatePassword"
        //                                                 :   fillPvtTemplateChangePwdPage(xmlDoc);
        //                         break;
        //                     case "ChangePrivateGroupPassword_click"
        //                                                 :   redirectFromChangeGroupPwd(xmlDoc);
        //                         break;
        //                     case "panelSettings"        :   fnnPanelSettings(xmlDoc);
        //                         break;
        //                     case "CheckAuthenticateTemplate"
        //                                                 :
        //                     case "TemplatePasswordChange_click"
        //                                                 :   redirectToTemplatePropertyList(xmlDoc);
        //                         break;
        //                     case "CopySaveAsFile"       :   fillCopySaveAsFile(xmlDoc);
        //                         break;
        //                     case "ScanSaveAsFile"       :   fillScanSaveAsFile(xmlDoc);
        //                         break;
        //                     case "StoreToEfiling"   	:   fnfillEfilingBoxDropDown(xmlDoc);
        //                         break;
        //                     case "FaxIFaxMode"			:   fillFaxIFaxSettings(xmlDoc);
        //                         break;
        //                     case "ScanEmailEfiling"		:   fillScanEmailEfiling(xmlDoc);
        //                         break;
        //                     case "ScanToEMail"			:   fillScanToEMail(xmlDoc);
        //                         break;
        //                     case "ScanToUSB"			:   fillScanToUSB(xmlDoc);
        //                         break;
        //                     case "MetaScanToEMail"		:   fillMetaScanToEMail(xmlDoc);
        //                         break;
        //                     case "MetaScanSaveAsFile"	:   fillMetaScanSaveAsFile(xmlDoc);
        //                         break;
        //                     case "MetaScanToUSB"        :   fillMetaScanToUSB(xmlDoc);
        //                         break;
        //                 case "MetaScanEmailSaveAsFile"  : fillMetaScanEmailSaveAsFile(xmlDoc);
        //                 break;
        //                     case "LDAPEmailAddressSetting" : fnnLdapSettings(xmlDoc);
        //                         break;
        //                     case "fetchLDAPEmailAddress" : fillScanToEMail(xmlDoc);
        //                         break;                
        //                     case "ScanSaveAsFileEfiling":   fillScanSaveAsFileEfiling(xmlDoc);
        //                         break;
        //                     case "CopyStoreToEfiling"   :   fillCopyEfiling(xmlDoc);
        //                         break;
        //                     case "ScanStoreToEfilingSettings"
        //                                                 :	fillScanEfilingSettings(xmlDoc);
        //                         break;
        //                     case "ScanToEfilingUSBSettings"
        //                                                 :	fillScanToEfilingUSBSettings(xmlDoc);
        //                         break;
        //                     case "ScanToFileAndUSB"     :	fillScanToFileAndUSB(xmlDoc);
        //                         break;
        //                     case "ScanEmailSaveAsFile"  :	fillScanEmailSaveAsFile(xmlDoc);
        //                         break;
        //                     case "ScanToEMailAndUSB"     :	fillScanToEMailAndUSB(xmlDoc);
        //                         break;
        //                     case "CopyModeEdit"         :   fillCopyModeEdit(xmlDoc);
        //                         break;
        //                     case "CopySaveAsFileEdit"   :   fillCopySaveAsFileEdit(xmlDoc);
        //                         break;
        //                     case "CopyEfilingEdit"      :   fillCopyEfilingEdit(xmlDoc);
        //                         break;
        //                     case "FaxIFaxModeEdit"      :   fillFaxIFaxSettingsEdit(xmlDoc);
        //                         break;
        //                     case "FaxSaveAsFile"        :   fillFaxSaveAsFile(xmlDoc);
        //                         break;
        //                     case "FaxSaveAsFileEdit"    :   fillFaxSaveAsFileEdit(xmlDoc);
        //                         break;
        //                     case "SBMFTPOther"          :   fillSBMFTPOther(xmlDoc);
        //                         break;
        //                     case "SBMFTPOtherEdit"      :   fillSBMFTPOtherEdit(xmlDoc);
        //                         break;
        //                     case "FaxSettingGetFaxInstallStatus"
        //                                                 :   fillFaxSettings(xmlDoc);
        //                         break;
        //                     case "PublicTempExtendedFieldList"
        //                                                 :   fillPublicTempExtendedFieldAllList(xmlDoc);
        //                         break;
        //                     case "Templates-ValidateSecurePDF"
        //                                                 :	fnHandleValidateSecurePDFPwds(xmlDoc);
        //                         break;
        //                     case "GetDiagnosticCode_ColorMode"
        //                                                 :   fnSetDiagnosticCode_ColorMode(xmlDoc);
        //                         break;
        //                     case "TemplatePasswordSettings"
        //                                                 :   fnHandleValidateTemplatePwd(xmlDoc);
        //                         break;

        //                     //MetaScan - Extended Field Definition related
        //                     case "LicenseEnableCheck"  : fnHandleLicenseEnableChk(xmlDoc);
        //                                                   break;
        //                     case "ExtnFieldDefList"     :   fillExtnFieldDefList(xmlDoc);
        //                         break;
        //                     case "UpdateNewExtnFieldDef" :
        //                     case "CreateNewExtnFieldDef"
        //                                                 :   fnRedirectFromCreateNewExtnFieldDef(xmlDoc);
        //                         break;
        //                     case "DeleteExtnFieldDef"
        //                                                 :   fnChkStatusDeleteExtnFieldDef(xmlDoc);
        //                         break;
        //                     case "ExtnFieldSettings"
        //                                                 :   fnRedirectExtnFieldSettings(xmlDoc);
        //                         break;
        //                     case "AddExtendedField"
        //                                                 :   fnChkStatusAddExtnFields(xmlDoc);
        //                         break;
        //                     case "DeleteExtendedField"
        //                                                 :   fnChkStatusDeleteExtnField(xmlDoc);
        //                         break;
        //                     case "EditExtendedField"
        //                                                 :   fnChkStatusEditExtnField(xmlDoc);
        //                         break;
        //                     case "MetaScanXMLFormatFileList"
        //                                                 :   fnFillXMLFormatFileList(xmlDoc);
        //                         break;

        //                     //MetaScan - XML-File format related
        //                      case  "XMLFileFormatDetail"  :  getXMLFileFormatDetail(xmlDoc);
        //                          break ;
        //                      case "DeleteXMLFileFormat" :    refreshXMLFile(xmlDoc);
        //                          break ;

        //                     case "PublicAddExtendedField"
        //                                                 :
        //                     case "PrivateAddExtendedField"
        //                                                 :   fnChkStatusAddExtnFields(xmlDoc);
        //                         break;
        //                     case "PublicEditExtendedField"
        //                                                 :
        //                     case "PrivateEditExtendedField"
        //                                                 :   fnChkStatusEditExtnField(xmlDoc);
        //                         break;
        //                     case "PublicDeleteExtendedField"
        //                                                 :
        //                     case "PrivateDeleteExtendedField"
        //                                                 :   fnChkStatusDeleteExtnField(xmlDoc);
        //                         break;

        //                     //User Management Related
        //                      case "GetDepartmentForUserSearch" :   getDeptList(xmlDoc);
        //                          break;
        //                      case "RegisterNewUser"      :   getUserRegStatus(xmlDoc);
        //                          break;
        //                      case "RegisterUserAccInfo"  :   getUserAccRegStatus(xmlDoc);
        //                          break;
        //                      case "UserInfoList"         :   fillAllUserInfoList(xmlDoc);
        //                          break;
        //                      case "GetNewUserInfo"       :   fillNewUserInfo(xmlDoc);
        //                          break;
        //                      case "LoginUserDetails"     :   getMyAccountInfo(xmlDoc);
        //                          break;
        //                      case "GetMyAccountingInfo"  :   fillMyAccountingInfo(xmlDoc);
        //                          break;
        //                      case "SelectedUserInfoList" :   fillSelectedUserInfoList(xmlDoc);
        //                          break;
        //                      case "GetSelectedUserAccInfo"
        //                                                  :   fillSelectedUserAccInfo(xmlDoc);
        //                          break;
        //                      case "UpdateUserInfo"       :   redirectUserInfo(xmlDoc);
        //                          break;
        //                      case "UpdateUserAccInfo"    :   redirectUserAccInfo(xmlDoc);
        //                          break;
        //                     case "ResetPassword"         :   fnResetPwdStatusCheck(xmlDoc);
        //                         break;
        //                     case "DeleteUserCacheInfo"   :
        //                     case "DeleteUserAccInfo"     :
        //                     case "DeleteUserInfo"        :
        //                     case "DeleteAllUsersInfo"    :
        //                     case "UnlockUserInfo"        : 
        //                     case "DeleteAllUsersAccInfo" :   fnDeleteUserAccInfoStatus(xmlDoc);
        //                          break;
        //                     case "UserExport"            :   getUserExportLogInfo(xmlDoc);
        //                          break;
        //                     case "CreateExportFile"      :   fnFillUserExport(xmlDoc);
        //                         break;
        //                     case "ResetUserQuota"     :
        //                     case "ResetAllUserQuota" :
        //                     case "ResetEditUserCounters" :
        //                     case "ResetUserCounters"     :
        //                     case "ResetAllUserCounters"  :   fnChkUserCounterAndQuotaStatus(xmlDoc);
        //                         break;
        //                     case "ChkNewUserPRMStatus"      :

        //                     case "ChkEditUserPRMStatus"      :   fnChkUserPRMStatus(xmlDoc);
        //                         break;

        //                     //Role Management Related
        //                     case "RoleInfoList"         :   fillAllRoleInfoList(xmlDoc);
        //                         break;
        //                     case "CreateRoleChkStatus"  :   fnnCreateRoleChkStatus(xmlDoc);
        //                         break;
        //                     case "ModifyRoleStatus"		:   fnnModifyRoleStatus(xmlDoc);
        //                         break;
        //                     case "Delete-OnlyRoleNamesList":   fillDeleteOnlyRoleNamesList(xmlDoc);
        //                         break;
        //                     case "GetNewRoleInfo"       :   fillNewRoleInformation(xmlDoc);
        //                         break;
        //                     case "GetRbacSetting"       :   rbacSettingAndEwbLicenceHandler(xmlDoc);
        //                         break;
        //                     case "DeleteRoleStatus"     :   fnnDeleteRoleStatus(xmlDoc);
        //                         break;
        //                     case "GetEditRoleInfo"      :   fillEditRoleInfo(xmlDoc);
        //                         break;
        //                     case "StatusChkTransferRole":   fnStatusChkTransferRole(xmlDoc);
        //                         break;

        //                 //Group Management Related
        //                     case "GetAllGroups"         :   fillAllGroupsInfoList(xmlDoc);
        //                         break;
        //                     case "GetSelectedGroupInfo" :   fillSelectedGroupInfo(xmlDoc);
        //                         break;
        //                     case "CreateGroupChkStatus" :   fnnCreateGroupChkStatus(xmlDoc);
        //                         break;
        //                     case "GetNewGroupInfo"      :   fillNewGroupInformation(xmlDoc);
        //                         break;
        //                     case "GetEditGroupInfo"     :   fillEditGroupInformation(xmlDoc);
        //                         break;
        //                     case "ChkStatusUpdateGroup" :   fnChkStatusUpdateGroup(xmlDoc);
        //                         break;
        //                     case "DeleteGroupStatus"    :   fnnChkDeleteGroupStatus(xmlDoc);
        //                         break;
        //                     case "AddrBk-GetFaxLine2InstallStatus"    :   fnnHandleFaxLine2InstallStatus(xmlDoc);
        //                         break;

        // 					case "AddressBookContactList" :    
        //                         getAddressBookContacts(xmlDoc);
        //                         break;
        //                     case "AddressBookContactList-Pagination" :
        //                         getAddressBookContactsPagination(xmlDoc);
        //                         break;
        //                     case "AddressBookGroupList" :
        //                         fillAddressBookGroupsList(xmlDoc);
        //                         break;

        //                     case "RelayEnd-AddressBookContacts"
        //                     :

        //                     case "AddressBookContacts"	:

        //                     case "AddressBookGrpResContacts"
        //                     :   fillAddressBookContacts(xmlDoc);
        //                         break;
        //                     case "GetAddressBookContactsForSelectedGrp"
        //                     :   getAddressBookContactsForSelectedGrp(xmlDoc);
        //                         break;
        //                     case "AddressBookGrpContacts"
        //                     :   fillAddressBookGrpContactDetails(xmlDoc);
        //                         break;
        //                     case "AddressBookContactDetails"
        //                     :   fillAddressBookContactDetails(xmlDoc);
        //                         break;
        //                     case "AddressBookGroups"	:

        //                     case "AddressBookGroupNames-Pagination":
        //                     case "FavAddressBookGroups":
        //                     case "AddressBookGroupNames":   fillAddressBookGroups(xmlDoc);
        //                         break;
        //                     case "AddNewGroup"          :

        //                     case "UpdateGroup"          :

        //                     case "DeleteGroup"          :   redirectFromCreateGroup(xmlDoc);
        //                         break;
        //                     case "AddNewContact"        :

        //                     case "UpdateContact"        :

        //                     case "DeleteContact"        :   redirectFromCreateContact(xmlDoc);
        //                         break;
        //                     case "GetDestRestriction"     :
        //                                                     fnGetLDAPList(xmlDoc);
        //                                                     break;
        //                     case "GetDirectoryList"     :
        //                         fnGetDirectoryList(xmlDoc);
        //                         break;
        //                     case "SearchContact"        :
        //                         fnGetSearchedContactList(xmlDoc)
        //                         break;
        //                     case "AddrSerchStatus"      : UpdateErrorMsg(xmlDoc);
        //                             break ;
        //                     case "AddRemoteContacts"    :   fnRedirectAddRemoteContact(xmlDoc);
        //                         break ;

        //                     case "MailBoxList"          :   fillMailBoxList(xmlDoc);
        //                         break;
        //                     case "CreateMailBox"        :   refreshMailBxPage(xmlDoc,calledFromPage);
        //                         break;
        //                     case "ValidateMailBoxPwd"	:	fnRedirectToDisplayMailBox(xmlDoc);
        //                         break;
        //                     case "ValidateMailBoxPwdRBAC":	fnnHandleAuthMailBxPwdRBAC(xmlDoc);
        //                         break;
        //                     case "MailBox-GetSingleBox"	:	fillParticularMBox(xmlDoc);
        //                         break;
        //                     case "MailBox-UnlockMailBox":	fnHandleUnlockMailBox(xmlDoc);
        //                         break;
        //                     case "ParticularMailBoxProp":	fnHandleGetParticularMailBoxProp(xmlDoc);
        //                         break;
        //                     case "GetDefaultMailBoxSettings"
        //                     :	fnHandleDefaultValuesAndRedirect(xmlDoc,gblQueryString);
        //                         break;
        //                     case "MailBox-Delete"		:	refreshMailBxPage(xmlDoc,calledFromPage);
        //                         break;
        //                     case "FaxFwd-MailBox-EfilingBoxlist"
        //                     :	fnConstructBoxListDropDown(xmlDoc);
        //                         break;
        //                     case "AdminLogin"           :
        //                     case "UserLogin"            :
        //                     case "UserMgmtLogin"        :
        //                     case "AuthLogin"            :   getAuthenticationStatus(xmlDoc);
        //                         break;
        //                     case "DeptAdmin"            :
        //                     case "DeptAdminTitle"       :   getAuthDeptAdminStatus(xmlDoc);
        //                         break;
        //                     case "ForgotPasswrdEmail"   :  redirectAndGetSecurityQuestion(xmlDoc);
        //                         break;
        //                     case "ForgotPasswrdSecAns"  :  redirectFromSecAns(xmlDoc);
        //                         break;
        //                     case "ForgotPwdReset"       :  redirectFromSecReset(xmlDoc);
        //                         break;
        //                     case "SecurityReset"        :
        //                     case "PasswordReset"        :
        //                         fnnHandlePasswordReset(xmlDoc);
        //                         break;
        //                     case "ChangePassword"       :
        //                     case "ChangeSecQuesAns"     :   getAuthStatusForChangePWDSecret(xmlDoc);
        //                         break;
        //                     case "LogoutUser"           :   redirectUnAuthenticationStatus(xmlDoc);
        //                         break;
        //                     case "LogoutUser_Efiling"    :   redirectUnAuthenticationStatus(xmlDoc);
        //                         break;
        //                     case "PwdResetLogoutUser"    :   redirectUnAuthenticationStatus(xmlDoc);
        //                         break;
        //                     case "PwdResetLogoutUser_Efiling"  :   redirectUnAuthenticationStatus(xmlDoc);
        //                         break;

        //                    case "EfilingStatus"          : redirectEfilingStatus(xmlDoc) ;
        //                             break ;
        //                     case "EfilingNavigation"    :
        //                         handleEFilingBoxList(xmlDoc);
        //                         break;
        //                     case "EfilingNavigationFolder"
        //                     :
        //                         handleParticularBoxHierarchy(xmlDoc);
        //                         break;
        //                     case "EfilingNavigationDocument"
        //                     :   handleParticularFolderHierarchy(xmlDoc);
        //                         break;
        //                     case "EfilingNavigationPageList"
        //                     :
        //                         handleParticularDocumentHierarchy(xmlDoc);
        //                         break;
        //                     case "ChangeBoxProperties"  :   redirecChangeBoxProperties(xmlDoc);
        //                         break ;
        //                     case "AuthBoxPassword"      :   redirecBoxPassStatus(xmlDoc);
        //                         break ;
        // 					case "AuthorizeBox"         :  redirecAuthorizeBoxPassStatus(xmlDoc);
        // 					break ;
        //                     case "TestPrint"			:	redirectTestPrintClose(xmlDoc);
        //                         break ;
        //                     case "BoxDocumentPaste"     :    redirectFromPasteBoxContent(xmlDoc);
        //                             break ;
        //                     case "Efiling_DeleteDoc"    :
        //                     case "BoxDocumentCopy"      :

        //                     case "BoxDocumentCut"       :

        //                     case "Efiling_DeleteFolder"	:

        //                     case "Efiling_DeleteBoxContent"
        //                     :   redirectFromDelBoxContent(xmlDoc);
        //                         break ;
        //                     case "Efiling_CreateNewBox" :

        //                     case "EfilingRenameBox"     :   redirectFromCreateNewBox(xmlDoc);
        //                         break;
        //                     case "EfilingBoxProperties" :   redirectGetBoxProperties(xmlDoc);
        //                         break;
        // 					case "EfilingBoxChangeClick" : fnnBoxStatus(xmlDoc);
        // 					    break ;
        //                     case "EfilingFolderProperties"
        //                     :   redirectGetFolderProperties(xmlDoc);
        //                         break;
        //                     case "EfilingDocProperties"
        //                     :   redirectBoxDocProperties(xmlDoc);
        //                         break;
        //                     case "SaveBoxDocument"    :
        //                     case "SaveFolderDocument"    :
        //                     case "EfilingRenameDoc"     :   redirectFromRenamedOpened_BoxOrFolder_Doc(xmlDoc);
        //                         break ; 
        //                     case "Efiling_CreateNewFolder":
        //                     case "RenameOpenedFolder" :
        //                     case "RenameDocument" :
        //                       redirectToOpenedFolder(xmlDoc);
        //                         break;
        //                     case "Efiling_DeleteBox"	:
        //                         redirectFromDeleteBox(xmlDoc);
        //                         break ;
        //                     case "InsertPage"            :  redirectFromInserPages(xmlDoc);
        //                         break ;
        //                     case "GetDeviceName"         : handleGetModelType(xmlDoc);
        //                         break ;
        //                     case "EfilingInsertPages"    : fillInsertPages(xmlDoc);
        //                         break ;
        //                     case "FolderDocumentPaste"   : redirectFromPasteFolderContent(xmlDoc);
        //                         break ;
        //                     case "Efiling_DeleteFolderDoc" :
        //                     case "CopyFolderDocument"   :
        //                     case "CutFolderDocument"    :
        //                     case "Efiling_DeleteFolderContent"
        //                     :	redirectFromDelFolderContent(xmlDoc);
        //                         break ;
        //                     case "CopyBoxDocPage"     :
        //                     case "CutBoxDocPage"      :
        //                         redirectFromBoxDocCopyPage(xmlDoc);
        //                         break ;
        //                     case "EfilingPageProperties"
        //                     :	redirectGetPageProperties(xmlDoc)
        //                         break ;
        //                     case "DocPagePaste"        :
        //                         redirectFromDocPastePage(xmlDoc) ;
        //                         break ;
        //                     case "deleteBoxDocPages"   :  redirectBoxDocPage(xmlDoc) ;
        //                         break ;
        //                     case "EditOPRRefresh"       :  redirectRefreshEditOperation(xmlDoc);
        //                                                     break ;
        //                     case "EfilingAdminBoxLists" :	fillAdminBoxList(xmlDoc)
        //                         break ;
        //                     case "AdminBoxDelete"       :   redirectAdminBoxData(xmlDoc);
        //                         break ;
        //                     case "EditDocument"         :   handleEFilingEditDocument(xmlDoc);
        //                         break ;
        //                     case "EndEditDocument"      :   handleEFilingEndEditDocument(xmlDoc);
        //                         break ;
        //                     case "DocStatusProperties"  :   fillEFilingDocStatus(xmlDoc);
        //                         break ;
        //                     case "EfilingAdmin"         :   getAuthEfilingAdminStatus(xmlDoc);
        //                         break ;
        //                     case "ArchiveBoxDoc"        :   fillArchiveBoxDoc(xmlDoc)  ;
        //                         break ;
        //                     case "setArchiveMoniker"        :   fnHandleArchiveMonikerSet(xmlDoc)  ;
        //                         break ;
        //                     case "ArchiveProgressStatus" :  fillArchiveProgressStatus(xmlDoc);
        //                         break ;
        //                     case "ArchiveResponse"       :  fnnredirectArchiveResponsePage(xmlDoc) ;
        //                         break ;
        //                      case "Cancel_ArchiveBoxDoc"        :  fnnhandleCancelArchive(xmlDoc)  ;
        //                         break ;
        //                     case "UploadArchiveBoxDoc"   : fillUploadArchiveBoxDoc(xmlDoc);
        //                       break ;
        //                     case "UploadArchiveProgressStatus": fillUploadArchiveProgressStatus(xmlDoc);
        //                      break ;
        //                     case "UploadArchiveBoxDoc_Cancel"  : handleUploadArchiveCancel(xmlDoc) ;
        //                         break ;
        //                     case "EfilingUpdateID" :   fillEfilingUpdatedBoxID(xmlDoc);
        // 						break ;
        //                     case "TestPrintPageCount" :  handleTestPrintPagecount(xmlDoc);
        //                         break ;
        //                     case "TesPrint_GetUserQuota" :  fnnHandleTPUserQuota(xmlDoc);
        //                         break ;
        //                     case "TestPrint_GetDepartmentQuota" :  handleTestPrintPagecount(xmlDoc);
        //                         break ;
        //                     case "EFiling-Sesion"        :  fillEflingSession(xmlDoc)  ;
        //                         break ;
        //                     case "Efiling-Department"    : fillEflingDeptStatus(xmlDoc)   ;
        //                         break ;
        //                     case "GetPrintOptionUsrAuthDisable"    : fillEflingPrintOption(xmlDoc)   ;
        //                         break ;
        //                     case "GetDepartmentQuota"    : fillEflingPrintOption(xmlDoc)   ;
        //                         break ;
        //                     case "UpdateBox2PrintOptions" : fillEflingPrintOption(xmlDoc,"true") ;
        //                         break ;
        //                     case "BoxToPrint-initiatJob" : fillBoxToPrintInitiateJob(xmlDoc) ;
        //                         break ;
        //                     case "Efiling-PrintDoc"		: EfilePrintDocRedirection(xmlDoc);
        //                         break ;
        // 					case "BoxDocEmailSend"		: redirectfromEmailBoxDoc(xmlDoc);
        // 						break ;
        // 					case "EfilingDeptcodeValidate" : redirectValidateCode(xmlDoc);
        // 						break ;
        //                     case "Efiling_Box2Email_ValidateSecurePDFPwd" : fnHandleValidateSecurePDFPwds(xmlDoc);
        // 						break ;
        //                     case "EfilingDeptcodeValidate4Email" : redirectValidateCode4Box2Email(xmlDoc);
        // 						break ;
        //                     case "EfilingProjectcodeValidate4Email" : redirectValidateProjectCode4Box2Email(xmlDoc);
        //                         break ;
        //                     case "TestPrint-initiatJob" : fillTestPrintInitiateJob(xmlDoc);
        // 						break ;
        //                     case "TestPrintDocPage-initiatJob" : fillDocPageTestPrintInitiateJob(xmlDoc);
        // 						break ;
        //                     case "TestPrintDeptcodeValidate" : redirectTesPrintValidateCode(xmlDoc);
        //                         break ;
        // 					case "BoxDocumentCopy_ProgressBar" : fillPBarBoxDoc(xmlDoc);
        // 					   break ;
        // 					case "CopyProgressStatus"      : fillCopyProgressStatus(xmlDoc);
        // 						 break ;
        // 					case "GetBoxToMailFormats" :	handleGetBoxToMailFormats(xmlDoc);
        // 						break ;
        // 					case "GetBoxOrFolderDocToMailFormats" : handleGetBoxOrFolderDocToMailFormats(xmlDoc);
        // 					    break ;
        //                 	case "GetMailFormatForPages" : handleMailFormatForPages(xmlDoc);
        // 					    break ;

        // 					case"GetFolderDocToMailFormats"    : handleGetFolderDocToMailFormats(xmlDoc);
        // 					   break ;
        // 					case "BoxDocClipBoard_ProgressBar" : fillPBarBoxDoc(xmlDoc);
        // 					   break ;
        // 					case "BocDocProgressStatus"      : fillCopyProgressStatus(xmlDoc);
        // 						 break ;
        // 				    case "PageOprClipBoard_ProgressBar" :fillPBarForPages(xmlDoc);
        // 					     break ;
        // 				   case "PageOprProgressStatus"   : fillPageOprProgressStatus(xmlDoc);
        // 				         break ;
        //                     case "EfilingBoxListPagination" : handleBoxListDataForPagination(xmlDoc);
        //                         break ;
        //                     case "BoxToEmail-initiatJob" : fillBoxToEmailInitiateJob(xmlDoc);
        //                         break ;
        //                     case  "SMTPLogin_BoxToEmail"   : handleSMTPAuthentcation(xmlDoc);
        //                         break ;
        //                     case  "SMTPInternalLogin_BoxToEmail"   : handleSMTPInternalAuthentcation(xmlDoc);
        //                         break ;
        //                     case  "GetLDAPdata"   :             handleGetLDAPdataForSMTP(xmlDoc);
        //                         break ;
        //                     case "CreateNewDepartment"   :

        //                     case "ResetParticularDepartment"
        //                     :
        //                     case "DeleteParticularDepartment"
        //                     :
        //                     case "ModifyDepartmentInformation"
        //                     :   redirectToAdminCounterFrameFromChildWindow(xmlDoc);
        //                         break ;
        //                     case "ResetAllDepartment"	:

        //                     case "DisableDeptControllerXpathAfterAllDeptDelete"	:

        //                     case "DeleteAllDepartment"	:   redirectToCounterAdmin(xmlDoc);
        //                         break ;
        //                     case "CheckAnyDeptExist"	:   fnHandleCheckAnyDeptExist(xmlDoc);
        //                         break ;
        //                     case "SetDeptControllerXpathDisable"	:   redirectDeptCtrlXpathDisable(xmlDoc);
        //                         break ;
        //                     case "AdminAllDepartmentList"
        //                     :
        //                         fillAdminDepartmentCounterPage(xmlDoc);
        //                         break;
        //                     case "GetDepartment"		:

        //                     case "DepartmentCounter"	:
        //                         fillDepartmentCounterPage(xmlDoc);
        //                         break ;
        //                     case "QuotaManagement"  :
        //                             fnQuotaCommitStatus(xmlDoc);
        //                        break;
        //                     case "UserDeptQuotaType"    :   getUserDeptQuotaRes(xmlDoc);
        //                         break;
        //                     case "DeptQuotaType"        :   getDeptQuotaRes(xmlDoc);
        //                         break;                      
        //                     case "GetSelectedDirService":	fillDirectoryPropPage(xmlDoc);
        //                         break;
        //                     case "DirectoryService-SetDefaultPreRequisite":	fnHandlePreForSetDefaultLdap(xmlDoc);
        //                         break;
        //                     case "CreateDeleteModifyMakeDef-DirService" :
        //                         fnRefreshLdapPage(xmlDoc);
        //                         break;
        //                     case "Network-LDAP"		:   fnnPopulateLDAPTable(xmlDoc);
        //                         break;
        //                     case "Network-LDAP-childWindow"		:   fnnHandleIDGateValue(xmlDoc);
        //                         break;
        //                     case "Backup"				:
        //                         getBackupInfo(xmlDoc);
        //                         break;
        //                     case "DeleteFiles"			:
        //                         getDeleteFilesInfo(xmlDoc);
        //                         break;
        //                     case "Restore"				:
        //                         getBackupInfo(xmlDoc);
        //                         break;
        //                     case "Export"				:
        //                         getExportLogInfo(xmlDoc);
        //                         break;
        //                     case "Export_AddressBook"				:
        //                         getExportLogInfoForAddressBook(xmlDoc);
        //                         break;
        //                     case "Printer"				:   getPrinterData(xmlDoc);
        //                         break;
        //                     case "CreateDeleteModifyMakeDef-Printer"
        //                     :   fnRefreshPrinterPage(xmlDoc);
        //                         break;
        //                     case "PrintLogExport"		:	fillLogExport(xmlDoc);
        //                         break ;
        //                     case "PrintLogExportForAddr"		:	fillLogExportInfoAddressBook(xmlDoc);
        //                         break ;

        //                     case "RebootController"     :   //getRebootStatus(xmlDoc);
        //                         break;
        //                     case "FaxSettings"          :   fnFaxSettings(xmlDoc);
        //                         break;
        //                     case "IPFaxSettings"          :   fnIPFaxSettings(xmlDoc);
        //                         break;
        //                     case "IPFaxNetworkStautusCheck" :   networkStatusCheckHandler(xmlDoc);
        //                         break;
        //                     case "DriverContent"        :   fillDriverContents(xmlDoc);
        //                         break ;
        //                     case "getAllSoftware"       :   fillAllSoftware(xmlDoc)  ;
        //                         break ;
        //                     case "getAllFileDetails"    :   fillAllFileDetails(xmlDoc);
        //                         break ;
        //                     case "removeSoftware"       :   fnRefreshRmvSoftwarePage(xmlDoc);
        // 					    break;
        //                     case "SaveDeviceCustomSettings"
        //                                                 :   fnRedirectOffDeviceSave(xmlDoc);
        // 					    break;
        //                     case "SSFC"                 : fnHandleSSFC(xmlDoc);
        //                         break;
        //                    case "PnPUpload"             : fnHandlePnp(xmlDoc);
        //                         break;
        //                    case "UpdateParticlrUserInfo"  : fnHandlePrtclrUsrInfo(xmlDoc);
        //                         break;
        //                    case "GetUsrPermissionList"    : fnHandlePrtclrPrmsnLst(xmlDoc);
        //                         break;
        //                     case "MenuSettinglist"        : fillMenuSettinglist(xmlDoc);
        //                        break;
        //                     case "DeleteMenuItem"         :  handleDeleteMenuList(xmlDoc);
        //                         break;
        //                     case "MenuTemplateGroups"     :  fnfillTmpltGrpTbl(xmlDoc);
        //                         break;
        //                     case "RegisterTemplateGrp"    :  fnHandleRegisterTmplt(xmlDoc);
        //                          break;
        //                     case "GetURlListURL"          :  fnHandleURL(xmlDoc);
        //                          break;
        //                     case "RegisterURL"            :  fnHandleRgstrdURL(xmlDoc);
        //                          break;
        //                    case "MyAccountTmpltList"      :  fnMyAccntTmpltList(xmlDoc);
        //                          break;
        //                     case "RegisterTmplt"          :  fnMyAccntTmpltResponse(xmlDoc);
        //                          break;
        //                     case "TmpltGrpPwrdVldtion"      :
        //                     case "TmpltPwrdVldtion"         :
        //                     case "InitialGrpPwrdVldtion"    :
        //                     case "tmpltSaveValdtion"        :  fnHandlePwrdVldtion(xmlDoc);
        //                          break;
        //                     case "MenuTemplategroupList"    :  fnHandlegroupList(xmlDoc);
        //                          break;
        //                     case "ChkGrpSessionAuth"        :  fnTmpltGrpSessionAuth(xmlDoc);
        //                          break;
        //                     case "ChkGrpSessionAuthForSave" :  fnGrpSessionAuthForSave(xmlDoc);
        //                          break;
        //                     case "ChkGrpSesAuthForOpnTmplt" :  fnGrpSesAuthForOpnTmplt(xmlDoc);
        //                          break;
        //                     case "ChkTmpltSesAuthForSave"  :  fnTmpltSesAuthSaveHandle(xmlDoc);
        //                          break;
        //                     case "getGroppMetaData"        :  fnFillGroupMetaData(xmlDoc);
        //                          break;
        //                     case "GetDiagnosticCode9000"   :  fnSetDiagnosticCode(xmlDoc);
        //                          break;
        //                     case "GetDiagnosticCode9116"   :  fnHandleDCodeForDept(xmlDoc);
        //                          break;
        //                     case "Copier"                  : fnHandleCopierSettings(xmlDoc);
        //                           break;
        //                     case "UsrMngmntImportMfpStatus" : fnHandleImportMfpStatus(xmlDoc);
        //                           break;
        //                     case "MaintnceImportMfpStatus" : fnHandleMntnceImrtMfpStat(xmlDoc);
        //                           break;
        //                     case "UploadSoft" : fnHandlePrintAndPoint(xmlDoc);
        //                           break;
        //                     case "getTrasacDoc" : fnHandleTrasactionDoc(ajaxObj.responseText);
        //                           break;
        //                    case "RomoteProtocolList"       : fnHandleRmtPrtclList(xmlDoc);
        //                           break;
        //                     case "SetRomoteProtocol"       : fnHandleSetRemtPrtcl(xmlDoc);
        //                           break;
        //                     case "DelRomoteProtocol"       : fnHandleDelRemtPrtcl(xmlDoc);
        //                           break;
        //                     case "GeneralDateTime"       : fnHandleGeneralDateTime(xmlDoc);
        //                           break;
        //                     case "GetDiagnosticCode6086" : fnHandle6086DiaCode(xmlDoc);
        //                           break;
        //                     case "SetCounterSttings"     : fnHandleCuntrStng(xmlDoc);
        //                           break;
        //                     case "CntrUserInfoList"     : fillCntrAllUserInfoList(xmlDoc);
        //                           break;
        //                     case "CntrSelectedUserInfoList"     : fillCntrSelectedUserInfoList(xmlDoc);
        //                           break;
        //                     case "getSelectedDest"     : fnHandleSMBSelectedDest(xmlDoc);
        //                           break;
        //                     case "Fax-ValidateSecureRxLinePwd"    :  fnHandleValidateSecureRxPwds(xmlDoc);
        //                           break;
        //                     case "GetCounterSttings"     : fnHandleQuotaSettings(xmlDoc);
        //                           break;
        //                     case "PublicMenuSync"        : fnHandlePublicMenuSync(xmlDoc);
        //                           break;
        //                     case "NewDepartmentCounter"  : fnHandleNewDeptCntr(xmlDoc);
        //                           break;
        //                     case "InstalledHDDStatus"   : fnHandleHDDStatus(xmlDoc);
        //                           break;
        // 		    		case "EWB-DeleteEWBHist"  : fnHandleDeletEWBHist(xmlDoc);
        //                           break;	  
        //                     case "Login_ControlerData"   : fngetStatusControlerData(xmlDoc) ;
        //                           break;
        //                     case "SyncHandler"   : fnSyncHandler(xmlDoc) ;
        //                           break;
        //                     case "GetMenuStatusHandler"   : fnGetMenuStatusHandler(xmlDoc) ;
        //                           break;

        //                     case "GetImageLogSettings"   : fnGetImageLogHandler(xmlDoc);
        //                           break;
        // 				    case "TransferOrClearLogs" :   fnHandleTransferClearLogs(xmlDoc);
        //                             break;
        //                     case "setImgLogSettingsValue" : redirectToImgLogSetting(xmlDoc);
        //                             break;
        //                     case "GetImageLogSettings-EnableImgLogs" :  getImgLogSettingsHandler(xmlDoc);
        //                           break;
        //                      case "GetDeviceInfoInFooter" :fnGetDeviceIdInFooter(xmlDoc);
        //                     break;
        //                     case "WebServicesSettings" : fnWebServicesSettingsInit(xmlDoc);
        //                         break;
        //                     case "ODCA-DeleteAllEvents" :fnDeleteAllEventsHandler(xmlDoc);
        //                         break;
        //                     case "Notification"         :  fnOnLoadNtfnRespHandler(xmlDoc);
        //                         break;
        //                     case "SetSecStampSettings" : handleSetSecStampSettings(xmlDoc);
        //                         break;
        //                     case "GetSecStampSettings" : handleGetSecStampSettings(xmlDoc);
        //                         break;
        //                     case "DeviceCustomSettings" : handleDeviceCustomSettings(xmlDoc);
        //                         break;
        //                     case "ConnectionTest"      : fnHandleConnectionStatus(xmlDoc);
        //                         break;
        //                     //AnyWhere Print Related
        //                     case "AnyWhere-Jobs": fnHandleAnywhereJobs(xmlDoc);
        //                         break;
        //                     case "AnywhereLicenseEnableCheck": fnHandleAnywhereLicenseEnableChk(xmlDoc)
        //                         break;
        // 					case "MyAccountSyncUsrInfo": fnHandleSyncUserInfo(xmlDoc);
        //                         break;
        // 					case "GetAuthInfo" : fnDisplaySyncButton(xmlDoc);
        // 						break;
        //                     case "AddSlaveMFP-SharedUser"	:	fnSharedUserAddSlaveMFPHandler(xmlDoc);
        // 						break;
        //                     case "GetSharedUserSlaveMFPList" :  fnFillSharedUserSlaveMFPList(xmlDoc);
        //                         break;
        // 					case "DeleteServer-SharedUser" : fnSharedUserDeleteServerHandler(xmlDoc);
        // 						break;
        // 					case "SyncAll-SharedUserSetting" : fnSyncAllHandlerSharedUser(xmlDoc);
        // 						break;
        // 					case "Sync-SharedUserSetting" : fnSyncHandlerSharedUser(xmlDoc);
        // 						break;
        // 					case "AddressBookFavoriteContacts"	: fnFillAddressBookFavoriteContact(xmlDoc);
        // 						break;
        // 					case "AddressBookFavoriteGroups"	:	fnFillAddressBookFavoriteGroup(xmlDoc);
        // 						break;
        // 					case "AddrContactFavOrder"	:	
        // 					case "AddrGrpFavOrder"	:	fnAddrBkFavOrder(xmlDoc);
        // 						break;
        //                     case "AddServer-SharedAddrBk"	:	fnAddServerHandler(xmlDoc);
        // 						break;
        // 					case "General-getHomeDataList":
        //                         populateHomeDataListTable(xmlDoc);
        //                         break;
        //                     case "General-delHomeDataList":
        //                         fnDeleteHomeDataHandler(xmlDoc);
        //                         break;
        //                     case "ProjectMgmt-List" : fnHandleProjectMgmtList(xmlDoc)
        //                         break;
        //                     case "ProjectMgmt-Create" : fnHandleOkBtnClk(xmlDoc)
        //                         break;
        //                     case "Project-Management-Authentication" : fnHandleProjectManagementAuth(xmlDoc)
        //                         break;
        //                     case "ProjectMgmt-Edit" : fnHandleEditOkBtnClk(xmlDoc)
        //                         break;
        //                     case "ProjectMgmt-Delete" : fnHandleDeleteBtnClk(xmlDoc)
        //                         break;
        //                     case "ProjectMgmt-DeleteAll": fnHandleDeleteAllBtnClk(xmlDoc)
        //                         break;
        // 					case "HomeUI-RegisterFromJobHistory" : constructJobHistoryTable(xmlDoc);
        // 						break;
        // 					case "View-UserHome" : constructTilesTable(xmlDoc);
        // 						break;
        //                     case "View-PublicHome" : constructTilesTablePublicHome(xmlDoc);
        //                         break;
        //                     case "SetUserHome" :
        //                     case "SetUserHome-Edit" :
        //                     case "SetPublicHome" :
        //                     case "SetUserHomeSettings-NewURL" :
        //                     case "SetUserHome-FromURLEdit" :
        //                     case "SetUserHome-FromFunctionList":
        //                     case "SetUserHome-FromApplication" :
        //                     case "SetUserHome-FromRecommendNew":
        //                     case "Reset-UserHome" :
        //                     case "SetUserHome-FromJobHistory" :     setUserHomeHandler(xmlDoc);
        //                         break;
        // 					case "DeleteIcon-DefaultHome" :
        //                     case "DeleteIcon-UserHome" : deleteIconUserHomeHandler(xmlDoc);
        //                         break;
        //                     case "GetUserHomeSettings-Theme" : getThemeSettingsHandler(xmlDoc);
        //                         break;
        //                     case "SetUserHomeSettings-Theme" : setThemeSettingsHandler(xmlDoc);
        //                         break;
        //                     case "GetURLList" : constructURLListTable(xmlDoc);
        //                         break;
        //                     case "UserHome-RegisterFromRecommendation" : constructRecommendationList(xmlDoc);
        //                         break;
        //                     case "UserHome-RegisterFromFunctionList" : constructFunctionList(xmlDoc);
        //                         break;
        //                     case "GetApplicationList" :   constructApplicationList(xmlDoc);
        //                         break;
        //                     case "GetUserHomeList" : constructRegisterFromUserHomeTiles(xmlDoc);
        // 						break;
        //                     case "GetPublicHomeList" :  constructRegisterFromPublicHomeTiles(xmlDoc);
        //                         break;
        //                     case "GetIconList" :   fillGalleryList(xmlDoc);
        //                         break;
        //                     case "GetUserHomeSettings" : getHomeUISettingsListHandler(xmlDoc);
        //                         break;
        //                     case "SetUserHomeSettings" : setHomeSettingsHandler(xmlDoc);
        //                         break;
        //                     case "GetPublicHomeSettings-Theme" : getThemeSettingsHandler(xmlDoc);
        // //                    getPublicThemeSettingsHandler(xmlDoc);
        //                         break;
        //                     case "SetPublicUserHomeSettings-Theme" : setPublicThemeSettingsHandler(xmlDoc);
        //                         break;
        //                     case "GetPublicHomeSettings" : getHomeUISettingsListHandler(xmlDoc);
        // //                    getPublicHomeSettingsListHandler(xmlDoc);
        //                         break;
        //                     case "SetPublicHomeSettings" : setPublicHomeSettingsHandler(xmlDoc);
        //                         break;
        //                     case "GetDefaultHomeSettings" : getHomeUISettingsListHandler(xmlDoc);
        // //                    getDefaultHomeSettingsListHandler(xmlDoc);
        //                         break;
        //                     case "SetDefaultHomeSettings" : setDefaultHomeSettingsHandler(xmlDoc);
        //                         break;
        // 					case "JobLogs" : fnDisplayHideAppLog(xmlDoc);
        //                         break;
        //                     case "GetCurrentBackgrounfImageList" : fnFillCurrentBackgroundImageList(xmlDoc);
        //                         break;	
        //                     case "DeleteBgImg" :
        //                     case "DeleteIconImg" :  fnDeleteBgImage(xmlDoc);
        //                         break;
        //                     case "ExportBgImg" :  
        //                     case "ExportIconImg" :  fnExportImg(xmlDoc);
        //                         break;
        //                     case "ExportBgContents" :
        // 					case "ExportIconContents" :  fnExportContents(xmlDoc);
        //                         break;
        //                     case "HDDBackup" :  fnHDDBackupHandler(xmlDoc);
        //                         break;
        //                     case "EBPSettings": getEBPSettingsHandler(xmlDoc);
        //                         break;
        // 					case "GetSecStampSettings-Efiling" : fnGetSecStampEfilingHandler(xmlDoc);
        //                         break;
        //                     case "DeleteAllSharedData" : fnDeleteAllSharedDataHandler(xmlDoc);
        //                         break;
        //                     case "Reg-SubMenu" : fnOnloadRegSubMenuHandler(xmlDoc);
        //                         break;
        //                     case "GetSimpleScreenSettings" : getSimpleScreenSettingHandler(xmlDoc);
        //                         break;
        //                     case "SetSimpleScreenSettings" : setSimpleScreenSettingsHnadler(xmlDoc);
        //                         break;
        //                     case "DriverCustomization-GetInstalledList" : fnHandleGetInstalledList(xmlDoc);
        //                         break;
        //                     case "PluginUpload" : fnHandlePluginUpload(xmlDoc);
        //                         break;
        //                     case "removePlugin" : fnHandleRemovePlugin(xmlDoc);
        //                         break;
        //                     case "removeSoftwareAndPlugin" : fnHandleRemovePluginAndSoftware(xmlDoc);
        //                         break;
        //                     case "PanelCustomize" :  fnPanelCustomizeHandler(xmlDoc);
        //                         break;
        //                     /* L6.11 - 26-SEP-2017 - License Activation Specification */
        //                     case "GetLicenseList":
        //                         getLicenseListHandler(xmlDoc);
        //                         break;
        //                     case "GetLicenseDetails":
        //                         getLicenseDetailsHandler(xmlDoc);
        //                         break;
        //                     case "License_CreateDeactivate":
        //                         sendCreateDeactivateHandler(xmlDoc);
        //                         break;
        //                     case "License_Deactivate":
        //                         sendDeactivateLicenseHandler(xmlDoc);
        //                         break;
        //                     case "License_Export":
        //                         sendExportLicenseHandler(xmlDoc);
        //                         break;
        //                     case "License_Activate":
        //                         sendOnlineActiveHandler(xmlDoc);
        //                         break;
        //                     case "License_Install":
        //                         sendInstallLicenseHandler(xmlDoc);
        //                         break;
        //                     case "gateWayRegistration":
        //                         gateWayRegistrationHandler(xmlDoc);
        //                         break;
        //                     case "deleteGateWay":
        //                         deleteGateWayHanadler(xmlDoc);
        //                         break;
        //                     case "refreshGateWayTable":
        //                         refreshGateWayTableHandler(xmlDoc);
        //                         break;
        //                     case "IPFax-ValidateSecureRxLinePwd"    :  fnHandleIPFAXValidateSecureRxPwds(xmlDoc);
        //                         break;
        //                     case "UPTList" : fnGetUPTListHandler(xmlDoc);break;
        //                     case "UPTImport" : fnImportUPTHandler(xmlDoc);break;
        //                     case "UPTExport" : fnExportUPTHandler(xmlDoc);break;
        //                     case "UPTDelete" : fnDeleteUPTHandler(xmlDoc);break;
        //                     case "UPTEditTypeName" : fnUPTEditTypeNameHandler(xmlDoc);break;
        //                     case "UPTNetworkStatus" : fnUPTNetworkStatusHandler(xmlDoc);break;
        //                     case "IPLicense_Check" : fnIpFaxLicenseCheck(xmlDoc);break;
        //                     case "RequestForIPFax" : fnIPFaxStatusHandler(xmlDoc);break;
        //                     default						:
        //                         for(var i = 0; i < gblHashMapArray.length; i++) {
        //                             BindToWidgets(xmlDoc,gblHashMapArray[i]);
        //                         }
        //                         if(calledFromPage == "Network-HTTP" || calledFromPage == "Network-LLTD"){
        //                             $('#btnSave,#Reset').removeAttr('disabled');
        // 						}
        //                         if(calledFromPage == "Network-FTP-server" || calledFromPage == "Network-SLP" || calledFromPage == "Network-appletalk"){
        //                             $('#btnSave,#Reset').removeAttr('disabled');
        //                         }
        //                         if(calledFromPage == "Network-FTP-client" || calledFromPage == "Network-Bonjour-client" || calledFromPage == "Network-netware"){
        //                             $('#btnSave,#Reset').removeAttr('disabled');
        //                         }
        //                         if(calledFromPage =="Network-SMTP-Server" || calledFromPage == "Network-POP3"){
        //                             $('#btnSave, #btnCancel').removeAttr('disabled');
        //                         }
        //                         if(calledFromPage == "PrinterEFiling"){
        //                             parent.fraTitle.document.getElementsByName("btnSave1")[0].disabled = false;
        //                             parent.fraTitle.document.getElementsByName("Reset")[0].disabled = false;
        // 						}
        // 						if(calledFromPage == "Notification"){
        // 							parent.fraTitle.document.getElementById('btnDel_Notify').disabled=false;
        // 						}
        //                 }
    } catch (e) {
        errHandler(e, 'handleServerResponse(' + calledFromPage + ')', 'AjaxReqRespHandler.js?v=1680753140ta', "");
    }
}
/**
 * construct the actual xml string from the server response.
 */
function extractRespXML(responseText) {
    try {
        if (responseText.length == 0) {
            return null;
        }
        var contentBodyStartIndex = responseText.indexOf("<DeviceInformationModel>");
        var contentBodyEndIndex = responseText.indexOf("</DeviceInformationModel>");
        responseText = responseText.substring(contentBodyStartIndex, contentBodyEndIndex + 25);
        ///* regular expression to replace(strip off) all the namespaces
        responseText = responseText.replace(/(\s+)(SchemaVersion|xmlns|xsi)[^<]*\">/gi, ">");

        ///* regular expression to replace(strip off) all the namespaces
        responseText = responseText.replace(/<[a-zA-Z]+:/gi, "<");
        responseText = responseText.replace(/<\/[a-zA-Z]+:/gi, "</");
        if (responseText.length == 0) {
            alert("Requested response XML is empty in CallBack tag from the Server");
            return null;
        }
        responseText = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + responseText;
        return responseText;
    } catch (e) {
        errHandler(e, 'extractRespXML()', 'AjaxReqRespHandler.js?v=1680753140ta', "");
        return null;
    }
}

/***
 *  To redirect to Authentication.
 * @param node
 */
function redirectToAuthentication(node) {
    glbAuthDom = node;
    parent.contents.location.href = "authentication/Authentication.html?v=1680753140ta";

}
/**
 *   To get xmldom from the xmlString using  ActiveXObject or DOMParser
 */
function parseXML(xmlString) {
    try {
        //modified the above else if condition for the DCR_51850 L6.02/03
        if (window.ActiveXObject || "ActiveXObject" in window) { // IE Check fnIsIEBrowser()
            var msxmlVers = ['MSXML2.DOMDocument.6.0', "MSXML4.DOMDocument", "MSXML3.DOMDocument", "MSXML2.DOMDocument.4.0",
                "MSXML2.DOMDocument.3.0", "MSXML2.DOMDocument", "MSXML.DOMDocument",
                "Microsoft.XmlDom"
            ];
            for (var i = 0; i < msxmlVers.length; i++) {
                try {
                    xmlDoc = new ActiveXObject(msxmlVers[i]);
                    break;
                } catch (ex) {}
            }
            if (msxmlVers[i] == "MSXML2.DOMDocument.4.0") {
                xmlDoc.setProperty("SelectionLanguage", "XPath");
            }
            xmlDoc.async = true;
            xmlDoc.preserveWhiteSpace = true;
            xmlDoc.loadXML(xmlString);
            /* xmlDoc.onreadystatechange = function () {
                if (xmlDoc.readyState == 4) setXmlLoadflag()
            }; */
        } // code for Mozilla, Netscape etc .
        else if (document.implementation && document.implementation.createDocument) {
            xmlDoc = new DOMParser();
            xmlDoc = xmlDoc.parseFromString(xmlString, "text/xml");
        } else {
            alert('Your browser cannot handle the parsing of the Response XML');
            return null;
        }
        return xmlDoc;
    } catch (e) {
        errHandler(e, 'parseXML()', 'AjaxReqRespHandler.js?v=1680753140ta', "");
        return null;
    }
}

/**
 * Get the node value for xpath from the xmldoc
 * Binding the node value with widget elements.
 */
function BindToWidgets(xmlObj, HashMapObj) {
    try {
        var xmlNode = null;
        var widgetValue = "";
        var missingxpaths = "";
        for (var xpathKey in HashMapObj) {
            /// The Xpath you pass here should not return NodeList.
            xmlNode = evaluateXpath(xmlObj, xpathKey);
            if (getNodeValue(xmlNode) == null && (xmlNode == null || xmlNode.nodeValue)) {
                missingxpaths += "~" + xpathKey;
            } else {
                if (xmlNode.nodeType == 2 || xmlNode.nodeType == 3) {
                    widgetValue = xmlNode.nodeValue;
                } else {
                    widgetValue = getNodeValue(xmlNode);
                }
                //widgetValue = getNodeValue(xmlNode);
                setValue(HashMapObj[xpathKey], widgetValue);
            } //else part of -- if(getNodeValue(xmlNode) == null)
        } //for (var xpathKey in HashMapObj)
        if (missingxpaths != "") {
            missingxpaths = missingxpaths.substring(1, missingxpaths.length);
            //   alert("======Following xpaths are not present in response=====\n\n"+ missingxpaths);
        }
    } catch (e) {
        errHandler(e, 'BindToWidgets(' + xpathKey + ')', 'AjaxReqRespHandler.js?v=1680753140ta', xpathKey);
        return null;
    }
}

/*** Below are the global vars for caching of Xpaths.Lets say there are two xpaths 1) a/b/c/d  2) a/b/c/e
 * And we are doing evaluateXpath on these 2 xpaths.For the first xpath store all xpath Obj's in an array.
 * For the 2nd xpath we can continue from "c" and bypass "a/b" since "c" obj is already stored.
 * STATUS : TBD
 ****/
var cacheXpathObjArray = new Array();
var cacheXpathStringArray = null;
/**
 * Returns the xml node Object for xpath from the XMLDOM.
 */
function evaluateXpath(domObj, strXpath) {
    try {
        var arrXpath = strXpath.split("/");
        var xpathlen = arrXpath.length;
        var tmpNode = null;
        var filterNode = null;
        var start, end, strIndex, strAttrName, tmpArrNode, attrNode, attrStart, attrEnd, strAttrValue, searchFound, arrKeyValue, intIndex;
        var parentNodeName = "";
        for (var i = 0; i < xpathlen; i++) {
            if (arrXpath[i].indexOf("[") != -1) {
                start = arrXpath[i].indexOf("[");
                end = arrXpath[i].indexOf("]");
                strIndex = arrXpath[i].substring(start + 1, end);
                if (strIndex.indexOf("@") != -1) {
                    /// eg : Network/Protocols/Application[@gid=3]
                    attrStart = strIndex.indexOf("@");
                    attrEnd = strIndex.indexOf("=");
                    if (attrEnd == -1) {
                        //Newly added for attribute support
                        /// eg : Network/Protocols/Application[@gid]
                        strAttrName = strIndex.substring(attrStart + 1, end);
                        tmpArrNode = tmpNode.getElementsByTagName(arrXpath[i].substring(0, start));
                        if (tmpArrNode == null || tmpArrNode == undefined)
                            return null;
                        attrNode = tmpArrNode[0].getAttributeNode(strAttrName);
                        if (attrNode == null || attrNode == undefined)
                            return null;
                        return attrNode;
                    }
                    strAttrValue = strIndex.substring(attrEnd + 1, end);
                    //Kalyan. Removed any single or double quotes from the filter value part : 28/12/07
                    strAttrValue = strAttrValue.replace(/[\'\"]/g, "");
                    strAttrName = strIndex.substring(attrStart + 1, attrEnd);
                    tmpArrNode = tmpNode.getElementsByTagName(arrXpath[i].substring(0, start));
                    searchFound = false;
                    for (var j = 0; j < tmpArrNode.length; j++) {
                        if (tmpArrNode[j] != null && tmpArrNode[j].getAttribute(strAttrName) == strAttrValue) {
                            tmpNode = tmpArrNode[j];
                            searchFound = true;
                            break;
                        }
                    }
                    if (searchFound == false) {
                        tmpNode = null;
                        return null;
                    }
                } //if (strIndex.indexOf("@") != -1)
                else if (strIndex.indexOf("=") != -1) {
                    /// eg : Network/Protocols/Application[name=topaccess]
                    //	Here <name> i.e filterTag can be at anyLevel under the <Application>
                    searchFound = false;
                    arrKeyValue = strIndex.split("=");
                    //To remove any single or double quotes from the filter value part : 28/12/07
                    arrKeyValue[1] = arrKeyValue[1].replace(/[\'\"]/g, "");
                    tmpArrNode = tmpNode.getElementsByTagName(arrXpath[i].substring(0, start));
                    filterNode = null;
                    for (var j = 0; j < tmpArrNode.length; j++) {
                        filterNode = tmpArrNode[j].getElementsByTagName(arrKeyValue[0])[0];
                        if (filterNode == null) {
                            continue;
                        }
                        if (filterNode.hasChildNodes()) {
                            if (filterNode.childNodes[0].nodeValue == arrKeyValue[1]) {
                                tmpNode = tmpArrNode[j];
                                searchFound = true;
                                break;
                            }
                        }
                        /*else{
                                                }*/
                    }
                    if (searchFound == false) {
                        tmpNode = null;
                        return null;
                    }

                } //else if (strIndex.indexOf("=") != -1)
                else {
                    /// eg : Network/Protocols/Application[3]
                    intIndex = parseInt(strIndex, 10);
                    if (i == 0)
                        tmpNode = domObj.getElementsByTagName(arrXpath[i].substring(0, start))[intIndex - 1];
                    else {
                        tmpNode = tmpNode.getElementsByTagName(arrXpath[i].substring(0, start))[intIndex - 1];
                    }

                }
                if (tmpNode == null || tmpNode == undefined) {
                    tmpNode = null;
                    return null;
                }
                parentNodeName = arrXpath[i].substring(0, start);
            } //if (arrXpath[i].indexOf("[") != -1)
            else {
                if (i == 0) {
                    tmpNode = domObj.getElementsByTagName(arrXpath[i])[0];
                } else {
                    tmpNode = tmpNode.getElementsByTagName(arrXpath[i]);
                    if (tmpNode.length == 0) {
                        tmpNode = null;
                        return null;
                    } else {
                        /// If multiple(length >1) or single (length = 1) child node(s) are found under parentNode
                        /// verify whether child node is direct child node under parent Node.
                        if (i == xpathlen - 1 && tmpNode.length > 1) {
                            // if i == xpathlen-1
                            //TBD for table population -- still under development

                            //         *	Lets say we searched for xpath "Print/name" and it gave NodeList.
                            //          Two cases are possible.
                            //          1) <name> tag has different Parents
                            //          2) <name> tag has same parent i.e <Print>
                            //          * 1.<Print>
                            //           <name>
                            //             <type>
                            //            <name>
                            //            </type>
                            //            </Print>
                            //           2.<Print>
                            //           <name>
                            //         <name>
                            //       <name>
                            //      </Print>

                            ///Checking for the Case two in the above Illustration
                            for (var z = 0; z < tmpNode.length; z++) {
                                if (tmpNode[z].parentNode.nodeName != parentNodeName) {
                                    break;
                                }
                            } //for (z=0;z<tmpNode.length;z++)
                            /// If z equal to tmoNode.Length , then case is satisfied.
                            if (z == tmpNode.length) {
                                return tmpNode;
                            }
                        } //if (i == xpathlen-1 && tmpNode.length > 1)

                        for (var z = 0; z < tmpNode.length; z++) {
                            if (tmpNode[z].parentNode.nodeName == parentNodeName) {
                                tmpNode = tmpNode[z];
                                break;
                            }
                        } //for (z=0;z<tmpNode.length;z++)
                        if (z == tmpNode.length) {
                            /// If no direct child node is found under parentNode
                            tmpNode = null;
                            return null;
                        } //if (z == tmpNode.length)
                    } // else part of if (tmpNode.length == 0)

                } //if (i==0)
                parentNodeName = arrXpath[i];
            } // else part of if (arrXpath[i].indexOf("[") != -1)
            cacheXpathObjArray[i] = tmpNode;
        } //for (i=0;i<xpathlen;i++)
        if (tmpNode == undefined || tmpNode == null) {
            return null;
        }
        cacheXpathStringArray = arrXpath; ///* Caching purpose.And used for next evaluateXpath call.
        return tmpNode;
    } catch (e) {
        //errHandler(e.toString(),'evaluateXpath('+strXpath+')','AjaxReqRespHandler.js?v=1680753140ta',"");
        return null;
    }
}


/**
 * To save the changes in the server.  It is used by network/controller settings.
 */
function commitValues(setupPage) {
    try {
        var arrkey = null;
        var orgValueNode = null;
        var originalWidgetVal = "";
        var changedWidgetVal = "";
        var xpathWidgetHashObj = gblHashMapArray[0]; //Xpath Map
        var changeCount = 0;
        if (xpathWidgetHashObj == null) {
            alert("Error : HashMap is null in CommitValues() of AjaxReqRespHandler.js?v=1680753140ta");
            return;
        }
        var start, key, rootNode, currentNode, end, strIndex, attrStart, attrEnd, strAttrName, strAttrVal, newElement, path;
        var xmlDocument = null;
        var setValue = null;
        if (fnIsIEBrowser()) {
            xmlDocument = new ActiveXObject('Microsoft.XMLDOM');
            setValue = xmlDocument.createElement('SetValue');
            xmlDocument.appendChild(setValue);
        } else if (document.implementation && document.implementation.createDocument) {
            xmlDocument = document.implementation.createDocument('', 'SetValue', null);
            setValue = xmlDocument.documentElement;
        } else {
            alert(" Browser does not support the serialization.");
            return;
        }
        //var re = /^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$/;
        for (var xpathkey in xpathWidgetHashObj) {
            orgValueNode = evaluateXpath(gblDomObjArray[0], xpathkey);
            originalWidgetVal = "";
            if (orgValueNode != null && orgValueNode != undefined) {
                originalWidgetVal = getNodeValue(orgValueNode);
            }
            changedWidgetVal = getValue(xpathWidgetHashObj[xpathkey]);

            if (xpathkey == "Controller/Settings/DateSettings/DaylightSavingTime/StartDate/time/Hour" ||
                xpathkey == "Controller/Settings/DateSettings/DaylightSavingTime/StartDate/time/Minute" ||
                xpathkey == "Controller/Settings/DateSettings/DaylightSavingTime/EndDate/time/Hour" ||
                xpathkey == "Controller/Settings/DateSettings/DaylightSavingTime/EndDate/time/Minute") {
                try {
                    changedWidgetVal = parseInt(changedWidgetVal);
                    changedWidgetVal = changedWidgetVal.toString();
                } catch (e) {}
            }

            if (originalWidgetVal != undefined) {
                //if ((originalWidgetVal != changedWidgetVal)){
                if ((fnEncodeTextDOM(originalWidgetVal) != fnEncodeTextArea(changedWidgetVal, true))) {
                    changeCount++;
                    key = xpathkey;
                    rootNode = setValue;
                    arrkey = xpathkey.split("/");
                    path = "";
                    for (var j = 0; j < arrkey.length; j++) {
                        if (path != "") {
                            path = path + "/" + arrkey[j];
                        } else {
                            path = arrkey[j];
                        }
                        currentNode = evaluateXpath(xmlDocument, path);

                        if (currentNode != undefined && currentNode != null) {
                            rootNode = currentNode;
                        } else {
                            if (arrkey[j].indexOf("[") != -1) {
                                start = arrkey[j].indexOf("[");
                                end = arrkey[j].indexOf("]");
                                strIndex = arrkey[j].substring(start + 1, end);
                                if (strIndex.indexOf("@") != -1) {
                                    //eg : "Controller/Settings/Notifications/General/email[@index=0]/address"
                                    attrStart = strIndex.indexOf("@");
                                    attrEnd = strIndex.indexOf("=");
                                    strAttrName = strIndex.substring(attrStart + 1, attrEnd);
                                    strAttrVal = strIndex.substring(attrEnd + 1, end);
                                    strAttrVal = strAttrVal.replace(/[\'\"]/g, "");
                                    tagName = arrkey[j].substring(0, start);
                                    newElement = xmlDocument.createElement(tagName); //New Tag name
                                    newElement.setAttribute(strAttrName, strAttrVal);
                                    rootNode.appendChild(newElement);
                                    if (newElement != undefined && newElement != null) {
                                        if (j == arrkey.length - 1) {
                                            newElement.appendChild(xmlDocument.createTextNode(changedWidgetVal));
                                        }
                                        rootNode = newElement;
                                    }
                                }

                            } else {
                                newElement = xmlDocument.createElement(arrkey[j]);
                                rootNode.appendChild(newElement);
                                if (newElement != undefined && newElement != null) {
                                    if (j == arrkey.length - 1) {
                                        //alert("changedWidgetVal "+changedWidgetVal+"create a text node for path "+path)
                                        newElement.appendChild(xmlDocument.createTextNode(changedWidgetVal));
                                    }
                                    rootNode = newElement;
                                }
                            }

                        }
                    }
                } //if ((originalWidgetVal != changedWidgetVal))
            } //if(originalWidgetVal != undefined )
        } //for(var xpath in XpathWidgetHashObj)
        if (changeCount != 0) {
            var setvalue_xml = "";
            if (calledFromPage == "Network") {
                var ipV6Xml = parent.fraList.getValue("IPV6CmdXml");
                //ipV6Xml = '<A><IPV6><name></name><enabled>1</enabled><active>1</active><Connections><connection></connection><connection></connection></Connections><Dependencies><protocol></protocol><protocol></protocol></Dependencies><Dependents><protocol></protocol><protocol></protocol></Dependents><error></error><addressMode>Stateless</addressMode><linkLocalAddress>fe80::280:91ff:fe51:ee15</linkLocalAddress><ipAddress>1:1:1:1:1:1:1:1</ipAddress><prefix>11</prefix><defaultGateway>1:1:1:1:1:1:1:1</defaultGateway><useDHCPV6Options>1</useDHCPV6Options><useDHCPV6Options>0</useDHCPV6Options><primaryDNSIP></primaryDNSIP><secondaryDNSIP></secondaryDNSIP><Stateless><keepConfiguration>1</keepConfiguration><useDHCPV6IPAddress>1</useDHCPV6IPAddress><useDHCPV6Options>1</useDHCPV6Options><IPAddress index="0"><ipAddress>1:1:1:1:1:1:1:1</ipAddress><prefix>1</prefix><gateway>1:1:1:1:1:1:1:1</gateway></IPAddress><IPAddress index="0"><ipAddress>2:2:2:2:2:2:2:2</ipAddress><prefix>22</prefix><gateway>2:2:2:2:2:2:2:2</gateway></IPAddress></Stateless><Stateful><useDHCPV6IPAddress>0</useDHCPV6IPAddress><useDHCPV6Options>0</useDHCPV6Options><IPAddress index="0"><ipAddress>1:1:1:1:1:1:1:1</ipAddress><prefix>11</prefix><gateway>1:1:1:1:1:1:1:1</gateway></IPAddress><IPAddress index="0"><ipAddress></ipAddress><prefix>0</prefix><gateway></gateway></IPAddress></Stateful></IPV6></A>';
                if (ipV6Xml != "") {
                    var networkIPV6node = evaluateXpath(xmlDocument, "Network/Protocols");
                    if (networkIPV6node == null || networkIPV6node === undefined) {
                        networkIPV6node = evaluateXpath(xmlDocument, "Network");
                        networkIPV6node = networkIPV6node.appendChild(xmlDocument.createElement("Protocols"));
                    }
                    var parsedIPV6XmlObj = parseXML("<A>" + ipV6Xml + "</A>");
                    var rootNodeIPV6 = parsedIPV6XmlObj.documentElement; //IPV6 node.
                    traverseXML(rootNodeIPV6, networkIPV6node, xmlDocument);
                }
                var ipSecXml = parent.fraList.getValue("IPSecCmdXml");
                if (ipSecXml != "") {
                    var networkIPSecnode = evaluateXpath(xmlDocument, "Network/Protocols");
                    if (networkIPSecnode == null || networkIPSecnode === undefined) {
                        networkIPSecnode = evaluateXpath(xmlDocument, "Network");
                        networkIPSecnode = networkIPSecnode.appendChild(xmlDocument.createElement("Protocols"));
                    }
                    var parsedIPSecXmlObj = parseXML("<A>" + ipSecXml + "</A>");
                    var rootNodeIPSec = parsedIPSecXmlObj.documentElement; //IPSec node.
                    traverseXML(rootNodeIPSec, networkIPSecnode, xmlDocument);

                }
                var sNMPXml = parent.fraList.getValue("SNMPCmdXml");
                if (sNMPXml != "") {
                    var networkSNMPnode = evaluateXpath(xmlDocument, "Network/Protocols/SNMP");
                    if (networkSNMPnode == null || networkSNMPnode === undefined) {
                        networkSNMPnode = evaluateXpath(xmlDocument, "Network/Protocols");
                        if (networkSNMPnode == null || networkSNMPnode === undefined) {
                            networkSNMPnode = evaluateXpath(xmlDocument, "Network");
                            networkSNMPnode = networkSNMPnode.appendChild(xmlDocument.createElement("Protocols"));
                            networkSNMPnode = networkSNMPnode.appendChild(xmlDocument.createElement("SNMP"));
                        } else {
                            networkSNMPnode = evaluateXpath(xmlDocument, "Network/Protocols");
                            networkSNMPnode = networkSNMPnode.appendChild(xmlDocument.createElement("SNMP"));
                        }
                    }
                    var parsedSNMPXmlObj = parseXML("<A>" + sNMPXml + "</A>");
                    var rootNodeSNMP = parsedSNMPXmlObj.documentElement; //IPV6node.
                    traverseXML(rootNodeSNMP, networkSNMPnode, xmlDocument);
                }
            }
            var finalXMLstr = null;
            if (fnIsIEBrowser()) {
                finalXMLstr = xmlDocument.xml;
            } else if (document.implementation && document.implementation.createDocument) {
                var xmlSerializer = new XMLSerializer();
                finalXMLstr = xmlSerializer.serializeToString(xmlDocument);
                finalXMLstr = finalXMLstr.replace(/<\?xml[^>]*\?>/, "");
            } else {
                alert(" Browser does not support the serialization.")
            }
            if (calledFromPage == "QuotaManagement") {
                setvalue_xml = "<Set><commandNode>Controller</commandNode>" + finalXMLstr + "</Set>";
                glbContentWebServerCmdArray = [setvalue_xml];
            } else if (calledFromPage == "Security-SavePwdSettings") {
                setvalue_xml = "<PasswordConfigurationSettings><commandNode>SecurityConfiguration/PasswordSettings</commandNode>" + finalXMLstr + "</PasswordConfigurationSettings>";
                glbContentWebServerCmdArray = [setvalue_xml];
            } else if (calledFromPage == "FaxSettings" || calledFromPage == "Fax-ValidateSecureRxLinePwd") {
                glbContentWebServerCmdArray = null;
                var remoteSettingsNode = $('#faxSetValueXML').val();
                var delXML = $('#deleteXML').val();
                calledFromPage = "Admin-Setup-Save";

                if (delXML != "") {
                    glbContentWebServerCmdArray = [delXML];
                }

                finalXMLstr = "<Set><commandNode>Controller</commandNode>" + finalXMLstr + "</Set>";
                setvalue_xml = "<Set><commandNode>Controller</commandNode><SetValue><Controller><Settings>" + remoteSettingsNode + "</Settings></Controller></SetValue></Set>";

                if (glbContentWebServerCmdArray == null) {
                    if (remoteSettingsNode != "")
                        glbContentWebServerCmdArray = [setvalue_xml];
                } else {
                    if (remoteSettingsNode != "")
                        glbContentWebServerCmdArray = glbContentWebServerCmdArray.concat(setvalue_xml);
                }
                if (glbContentWebServerCmdArray != null)
                    glbContentWebServerCmdArray = glbContentWebServerCmdArray.concat(finalXMLstr);
                else
                    glbContentWebServerCmdArray = [finalXMLstr];
            } else if (calledFromPage == "IPFax-ValidateSecureRxLinePwd" || calledFromPage == "SaveIPFaxSettings") {
                return finalXMLstr;
            } else if (calledFromPage != "Network" && calledFromPage != "PrintService") {
                setvalue_xml = "<Set><commandNode>Controller</commandNode>" + finalXMLstr + "</Set>";
                glbContentWebServerCmdArray = [setvalue_xml];
                if (calledFromPage == "General") {
                    var sntpCommandXML = parent.fraList.getValue("SNTPSecurityCmdXml");
                    if (sntpCommandXML != "") {
                        glbContentWebServerCmdArray = [sntpCommandXML, glbContentWebServerCmdArray];
                        //glbContentWebServerCmdArray = glbContentWebServerCmdArray.concat(sntpCommandXML);
                        //glbContentWebServerCmdArray = glbContentWebServerCmdArray.concat(parent.fraList.getValue("SNTPSecurityCmdXml"));
                    }
                    var powerSaveCommandXML = parent.fraList.getValue("PowerSaveCmdXml");
                    if (powerSaveCommandXML != "")
                        glbContentWebServerCmdArray = glbContentWebServerCmdArray.concat(powerSaveCommandXML);
                    var powerOffCommandXML = parent.fraList.getValue("PowerOffCmdXml");
                    if (powerOffCommandXML != "")
                        glbContentWebServerCmdArray = glbContentWebServerCmdArray.concat(powerOffCommandXML);

                    var WakeUpSettingCommandXML = parent.fraList.getValue("WakeUpSettingCmdXml");
                    if (WakeUpSettingCommandXML != "") {
                        glbContentWebServerCmdArray = glbContentWebServerCmdArray.concat(WakeUpSettingCommandXML);
                    }
                    // the below setting(restriction,shutdown,pkey) is handled in the above hash map check (original vs modified).
                    /*//RESTRICTION Setting
					
					var RestrictionSettingCommandXML = parent.fraList.getValue("RestrictionSettingCmdXml");
                    if(RestrictionSettingCommandXML != ""){
                        glbContentWebServerCmdArray = glbContentWebServerCmdArray.concat(RestrictionSettingCommandXML);
                    }
                    //SHUTDOWN Setting
                    var ShutdownSettingCommandXML = parent.fraList.getValue("ShutdownSettingCmdXml");
                    if(ShutdownSettingCommandXML != ""){
                        glbContentWebServerCmdArray = glbContentWebServerCmdArray.concat(ShutdownSettingCommandXML);
                    }

                    //Programmable key setting
                    var pKeyXML = parent.fraList.getValue("ID_PKey_XML");
                    if(pKeyXML!="")
                        if(glbContentWebServerCmdArray == null)
                            glbContentWebServerCmdArray = ["<Set><commandNode>Controller</commandNode>"+pKeyXML+"</Set>"];
                        else
                            glbContentWebServerCmdArray = glbContentWebServerCmdArray.concat("<Set><commandNode>Controller</commandNode>"+pKeyXML+"</Set>");
                     */

                    // 06-Dec-2016 Shared Home Setting for L6.02/03
                    //Shared Home Setting
                    var SharedHomeCmdXml = parent.fraList.getValue("SharedHomeCmdXml");
                    if (SharedHomeCmdXml != "") {
                        glbContentWebServerCmdArray = glbContentWebServerCmdArray.concat(SharedHomeCmdXml);
                    }

                }
                if (calledFromPage != "Notification")
                    calledFromPage = "Admin-Setup-Save";
                else
                    calledFromPage = "SaveNotification";

                if (setupPage == "HDDBackup")
                    calledFromPage = "HDDBackup";

                //18-Oct=2016 - Toner Mode settings for caspian
                if (setupPage == "EBPSettings")
                    calledFromPage = "EBPSettings";
            } else {
                if (calledFromPage == "Network") {
                    setvalue_xml = "<Commit><commandNode>Network</commandNode>" + finalXMLstr + "</Commit>";
                    glbContentWebServerCmdArray = [];
                    var firewallCmdArr = parent.fraList.getValue("FirewallCommandXml");
                    if (firewallCmdArr != "") {
                        firewallCmdArr = firewallCmdArr.split(",");
                        for (var i = 0; i < firewallCmdArr.length; i++) {
                            //alert(firewallCmdArr[i])
                            if (firewallCmdArr[i] != "")
                                glbContentWebServerCmdArray = glbContentWebServerCmdArray.concat(firewallCmdArr[i]);
                        }
                    }
                    var SMTPCmdArr = parent.fraList.getValue("SMTPMAXSIZE_CMDXML");
                    if (SMTPCmdArr != "") {
                        glbContentWebServerCmdArray = glbContentWebServerCmdArray.concat(SMTPCmdArr);
                    }
                    glbContentWebServerCmdArray = glbContentWebServerCmdArray.concat(setvalue_xml);
                    /*var soapXML = parent.fraList.getValue("SOAPCmdXml");
                    if(soapXML != ""){
                        glbContentWebServerCmdArray = glbContentWebServerCmdArray.concat(soapXML);
                    }*/
                } else {
                    setvalue_xml = "<Commit><commandNode>Network</commandNode>" + finalXMLstr + "</Commit>";
                    glbContentWebServerCmdArray = [];
                    if (calledFromPage == "PrintService") {
                        var cmdXml = parent.fraList.getValue("cmdXml");
                        if (cmdXml != "") {
                            glbContentWebServerCmdArray = glbContentWebServerCmdArray.concat(cmdXml);
                        }
                        if (!confirm(fnGetLocaleString("102020", "When this configuration is changed, the network connection will be disconnected.  Would you like to continue?"))) {
                            parent.fraTitle.document.getElementsByName("btnSave1")[0].disabled = false;
                            parent.fraTitle.document.getElementsByName("Reset")[0].disabled = false;
                            return;
                        }
                    }
                    glbContentWebServerCmdArray = glbContentWebServerCmdArray.concat(setvalue_xml);
                }
                calledFromPage = "Admin-Setup-Save";
            }

            requestObj.gblBoolHandleRespArray = [true];
            InitiateServerRequest("CMD");
        } else {
            if ((setupPage != null && setupPage !== undefined && (setupPage == 'GEN' || setupPage == 'NET' || setupPage == 'COPY' || setupPage == 'FAX' || setupPage == 'SAVE' || setupPage == 'EMAIL' || setupPage == 'NETFAX' || setupPage == 'PRINT' || setupPage == 'PRNTSRVC' || setupPage == 'ICCPRO' || setupPage == 'PRNTEFILE')) || (calledFromPage == "Notification")) {
                if (setupPage == 'PRNTSRVC') {
                    glbContentWebServerCmdArray = null;
                    var cmdXml = parent.fraList.getValue("cmdXml");
                    var cmdXmlFlag = parent.fraList.getValue("cmdXmlFlag");

                    if (cmdXml == "") {
                        if (!confirm(fnGetLocaleString("102020", "When this configuration is changed, the network connection will be disconnected.  Would you like to continue?"))) {
                            parent.fraTitle.document.getElementsByName("btnSave1")[0].disabled = false;
                            parent.fraTitle.document.getElementsByName("Reset")[0].disabled = false;
                            return;
                        }
                        document.location.reload(false);
                    } else {
                        if (cmdXmlFlag.indexOf("email") != -1) {
                            if (!confirm(fnGetLocaleString("102020", "When this configuration is changed, the network connection will be disconnected.  Would you like to continue?"))) {
                                parent.fraTitle.document.getElementsByName("btnSave1")[0].disabled = false;
                                parent.fraTitle.document.getElementsByName("Reset")[0].disabled = false;
                                return;
                            }
                            calledFromPage = "Admin-Setup-Save";
                        } else {
                            if (!confirm(fnGetLocaleString("100207", "Are you sure?"))) {
                                parent.fraTitle.document.getElementsByName("btnSave1")[0].disabled = false;
                                parent.fraTitle.document.getElementsByName("Reset")[0].disabled = false;
                                return;
                            }

                            calledFromPage = "PrintService_SharedPrint";
                        }

                        glbContentWebServerCmdArray = [cmdXml];
                        requestObj.gblBoolHandleRespArray = [true];
                        InitiateServerRequest("CMD");
                    }


                } else if (calledFromPage == "Notification")
                    parent.frames[1].location.reload();
                else {
                    if (setupPage == 'GEN') {
                        var sntpCommandXML = parent.fraList.getValue("SNTPSecurityCmdXml");
                        var powerSaveCommandXML = parent.fraList.getValue("PowerSaveCmdXml");
                        var powerOffCommandXML = parent.fraList.getValue("PowerOffCmdXml");
                        var WakeUpSettingCommandXML = parent.fraList.getValue("WakeUpSettingCmdXml");

                        // var pKeyXML = parent.fraList.getValue("ID_PKey_XML");
                        glbContentWebServerCmdArray = null;
                        if (sntpCommandXML != "") {
                            if (glbContentWebServerCmdArray == null) {
                                glbContentWebServerCmdArray = [sntpCommandXML];
                                //glbContentWebServerCmdArray = glbContentWebServerCmdArray.concat(parent.fraList.getValue("SNTPSecurityCmdXml"));
                            } else {
                                glbContentWebServerCmdArray = glbContentWebServerCmdArray.concat(sntpCommandXML);
                                //glbContentWebServerCmdArray = glbContentWebServerCmdArray.concat(parent.fraList.getValue("SNTPSecurityCmdXml"));
                            }
                        }

                        if (powerSaveCommandXML != "") {
                            if (glbContentWebServerCmdArray == null) {
                                glbContentWebServerCmdArray = [powerSaveCommandXML];
                            } else {
                                glbContentWebServerCmdArray = glbContentWebServerCmdArray.concat(powerSaveCommandXML);
                            }
                        }
                        if (powerOffCommandXML != "") {
                            if (glbContentWebServerCmdArray == null) {
                                glbContentWebServerCmdArray = [powerOffCommandXML];
                            } else {
                                glbContentWebServerCmdArray = glbContentWebServerCmdArray.concat(powerOffCommandXML);
                            }
                        }
                        //30-May-2105 - STFR_17857 - Wakeup settings changes are not getting saved
                        if (WakeUpSettingCommandXML != "") {
                            if (glbContentWebServerCmdArray == null) {
                                glbContentWebServerCmdArray = [WakeUpSettingCommandXML];
                            } else {
                                glbContentWebServerCmdArray = glbContentWebServerCmdArray.concat(WakeUpSettingCommandXML);
                            }
                        }
                        //Programmable key setting
                        /* pKeyXML = parent.fraList.getValue("ID_PKey_XML");
                        if(pKeyXML!="")
                            if(glbContentWebServerCmdArray == null)
                                glbContentWebServerCmdArray = ["<Set><commandNode>Controller</commandNode><SetValue>"+pKeyXML+"</SetValue></Set>"];
                            else
                                glbContentWebServerCmdArray = glbContentWebServerCmdArray.concat("<Set><commandNode>Controller</commandNode><SetValue>"+pKeyXML+"</SetValue></Set>");
*/

                        // 06-Dec-2016 Shared Home Setting for L6.02/03
                        //Shared Home Setting
                        var SharedHomeCmdXml = parent.fraList.getValue("SharedHomeCmdXml");
                        if (SharedHomeCmdXml != "") {
                            if (glbContentWebServerCmdArray == null) {
                                glbContentWebServerCmdArray = [SharedHomeCmdXml];
                            } else {
                                glbContentWebServerCmdArray = glbContentWebServerCmdArray.concat(SharedHomeCmdXml);
                            }
                        }


                        if (glbContentWebServerCmdArray != null) {
                            calledFromPage = "Admin-Setup-Save";
                            requestObj.gblBoolHandleRespArray = [true];
                            //alert(glbContentWebServerCmdArray)
                            InitiateServerRequest("CMD");
                        } else
                            document.location.reload(false);
                    } else if (setupPage == 'NET') {
                        var ipV6Xml = parent.fraList.getValue("IPV6CmdXml");
                        var sNMPXml = parent.fraList.getValue("SNMPCmdXml");
                        var ipSecXml = parent.fraList.getValue("IPSecCmdXml");
                        var firewallCmdArr = parent.fraList.getValue("FirewallCommandXml");
                        var SMTPCmdArr = parent.fraList.getValue("SMTPMAXSIZE_CMDXML");
                        /*var soapXML = parent.fraList.getValue("SOAPCmdXml");*/
                        var pKeyXML = parent.fraList.getValue("ID_PKey_XML");
                        glbContentWebServerCmdArray = null;
                        if (ipV6Xml != "" || sNMPXml != "" || ipSecXml != "" || firewallCmdArr != "" || SMTPCmdArr != "" || pKeyXML != "") {
                            /* if(soapXML != "") {
                                 if(glbContentWebServerCmdArray == null)
                                     glbContentWebServerCmdArray = [soapXML];
                                 else
                                     glbContentWebServerCmdArray = glbContentWebServerCmdArray.concat(soapXML);

                             }*/
                            if (firewallCmdArr != "") {
                                firewallCmdArr = firewallCmdArr.split(",");
                                for (var i = 0; i < firewallCmdArr.length; i++) {
                                    //alert(firewallCmdArr[i])
                                    if (firewallCmdArr[i] != "") {
                                        if (glbContentWebServerCmdArray == null)
                                            glbContentWebServerCmdArray = [firewallCmdArr[i]];
                                        else
                                            glbContentWebServerCmdArray = glbContentWebServerCmdArray.concat(firewallCmdArr[i]);

                                    }
                                }
                            }
                            if (SMTPCmdArr != "") {
                                if (glbContentWebServerCmdArray == null)
                                    glbContentWebServerCmdArray = [SMTPCmdArr];
                                else
                                    glbContentWebServerCmdArray = glbContentWebServerCmdArray.concat(SMTPCmdArr);

                            }
                            if (pKeyXML != "")
                                if (glbContentWebServerCmdArray == null)
                                    glbContentWebServerCmdArray = ["<Set><commandNode>Controller</commandNode>" + pKeyXML + "</Set>"];
                                else
                                    glbContentWebServerCmdArray = glbContentWebServerCmdArray.concat("<Set><commandNode>Controller</commandNode>" + pKeyXML + "</Set>");

                            if (ipV6Xml != "" || sNMPXml != "" || ipSecXml != "") {
                                var tmpCmdStr = "";
                                if (ipV6Xml != "") {
                                    tmpCmdStr += ipV6Xml;
                                }
                                if (sNMPXml != "") {
                                    tmpCmdStr += "<SNMP>" + sNMPXml + "</SNMP>";
                                }
                                if (ipSecXml != "") {
                                    tmpCmdStr += ipSecXml;
                                }
                                if (glbContentWebServerCmdArray == null)
                                    glbContentWebServerCmdArray = ["<Commit><commandNode>Network</commandNode><SetValue><Network><Protocols>" + tmpCmdStr + "</Protocols></Network></SetValue></Commit>"];
                                else
                                    glbContentWebServerCmdArray = glbContentWebServerCmdArray.concat("<Commit><commandNode>Network</commandNode><SetValue><Network><Protocols>" + tmpCmdStr + "</Protocols></Network></SetValue></Commit>");
                            }

                            calledFromPage = "Admin-Setup-Save";
                            requestObj.gblBoolHandleRespArray = [true];
                            InitiateServerRequest("CMD");
                        } else
                            document.location.reload(false);
                    } else if (setupPage == 'FAX') {
                        var remoteSettingsNode = $('#faxSetValueXML').val();
                        var delXML = $('#deleteXML').val();
                        if (remoteSettingsNode != "" || delXML != "") {
                            setvalue_xml = "<Set><commandNode>Controller</commandNode><SetValue><Controller><Settings>" + remoteSettingsNode + "</Settings></Controller></SetValue></Set>";
                            if (delXML != "")
                                glbContentWebServerCmdArray = [delXML];
                            if (remoteSettingsNode != "") {
                                if (glbContentWebServerCmdArray == null)
                                    glbContentWebServerCmdArray = [setvalue_xml];
                                else
                                    glbContentWebServerCmdArray = glbContentWebServerCmdArray.concat(setvalue_xml);
                            }


                            requestObj.gblBoolHandleRespArray = [true];
                            calledFromPage = "Admin-Setup-Save";
                            InitiateServerRequest("CMD");
                        } else
                            document.location.reload();
                    } else {
                        if (setupPage == 'PRINT') {
                            parent.parent.frames[0].document.getElementsByName("btnSave1")[0].disabled = false;
                            parent.parent.frames[0].document.getElementsByName("Reset")[0].disabled = false;
                        } else {
                            parent.fraTitle.document.getElementsByName("btnSave1")[0].disabled = false;
                            parent.fraTitle.document.getElementsByName("Reset")[0].disabled = false;
                        }
                    }
                }
            } else if (setupPage == "HDDBackup" || setupPage == "EBPSettings") {
                parent.frames[1].location.reload();
            }
        }
    } catch (e) {
        errHandler(e, 'commitValues()', 'AjaxReqRespHandler.js?v=1680753140ta', "")
    }
}

function traverseXML(node, networkNode, xmlDocument) {
    var childNode = node.firstChild;
    while (childNode != null && childNode.nodeType != 3) {
        if (childNode.nodeType == 1) {
            networkNode = networkNode.appendChild(xmlDocument.createElement(childNode.nodeName));
            for (var i = 0; i < childNode.attributes.length; i++) {
                var tmpAttrObj = xmlDocument.createAttribute(childNode.attributes[i].nodeName);
                tmpAttrObj.nodeValue = childNode.attributes[i].nodeValue;
                networkNode.setAttributeNode(tmpAttrObj);
            }
        } else if (childNode.nodeType == 2) {
            networkNode = networkNode.setAttributeNode(xmlDocument.createAttribute(childNode.attributes[i].nodeName));
        }
        traverseXML(childNode, networkNode, xmlDocument);
        childNode = childNode.nextSibling;
        networkNode = networkNode.parentNode;
    }
    if (childNode != null) {
        networkNode.appendChild(xmlDocument.createTextNode(childNode.nodeValue));
    }
}
/*
*
NodeType 	Named Constant
1 	        ELEMENT_NODE
2 	        ATTRIBUTE_NODE
3 	        TEXT_NODE


* */
var gblExtractedXpath = "";

function fnExtractXpathsFromXML(node, calledFrom) {
    try {
        var tmpIdenWhile = false;
        var childNode = node.firstChild;
        while (childNode != null && childNode.nodeType != 3) {
            //ELEMENT_NODE  or ATTRIBUTE_NODE  it comes inside
            tmpIdenWhile = true;
            if (gblExtractedXpath == "") {
                gblExtractedXpath = childNode.nodeName;
            } else {
                gblExtractedXpath = gblExtractedXpath + "/" + childNode.nodeName;
                if (childNode.attributes.length != 0) {
                    var tmpAttrs = "";
                    for (var i = 0; i < childNode.attributes.length; i++) {
                        tmpAttrs += "[@" + childNode.attributes[i].nodeName + "=" + childNode.attributes[i].nodeValue + "]";
                    }
                    gblExtractedXpath = gblExtractedXpath + tmpAttrs;
                }
            }
            fnExtractXpathsFromXML(childNode, calledFrom);
            gblExtractedXpath = gblExtractedXpath.substring(0, gblExtractedXpath.lastIndexOf("/"));
            childNode = childNode.nextSibling;
        }
        if (childNode != null && gblExtractedXpath != "") {
            DelegateToHandler(childNode, gblExtractedXpath, calledFrom);
        } else if (tmpIdenWhile == false && childNode == null && gblExtractedXpath != "") {
            DelegateToHandler(childNode, gblExtractedXpath, calledFrom);
        }
    } catch (e) {
        errHandler(e, 'fnExtractXpathsFromXML(' + gblExtractedXpath + ')', 'AjaxHandler.js?v=1680753140ta', "");
    }
}

function DelegateToHandler(nodeObj, xpathFromXml, calledFrom) {
    try {
        switch (calledFrom) {
            case "Security-SaveAuthSettings": //fnContructSecuritySetvalue(nodeObj,xpathFromXml);
                if (nodeObj == null) {
                    gblAuthenticationXpathsHM[xpathFromXml] = "";
                } else {
                    gblAuthenticationXpathsHM[xpathFromXml] = nodeObj.nodeValue;
                }

                break;
            default:
                alert(fnGetLocaleString("103472", "Internal Error has occurred. Please try again."));
        }
    } catch (e) {
        errHandler(e, 'DelegateToHandler()', 'AjaxHandler.js?v=1680753140ta', "");
    }
}
/**********
 * Returns the XML node value if Node Object is sent .This works only for Leaf node i.e TEXT Node.
 ***********/
function getNodeValue(evaluatedObj) {
    try {
        var xmlNodeValue = "";
        if (evaluatedObj != null && evaluatedObj != undefined) {
            if (evaluatedObj.hasChildNodes()) {
                if (evaluatedObj.childNodes.length > 1) {
                    return null;
                }
                xmlNodeValue = evaluatedObj.childNodes[0].nodeValue;
            } else
                xmlNodeValue = "";

        } else {
            //alert("Exception Occured !! Function : getNodeValue(). Reason: node is null");
            return null;
        }
        return xmlNodeValue;
    } catch (e) {
        alert("Exception Occured !! Function : getNodeValue() .. In Catch Block");
        return "";
    }
}
/**********
 * Returns the XPATH  value if Node Object is sent .This works only for Leaf node i.e TEXT Node.
 ***********/
function getXpathValue(nodeObj, xpathExpression) {
    try {
        var evaluatedObj = null;
        var retXpathVal = null;
        evaluatedObj = evaluateXpath(nodeObj, xpathExpression);
        if (evaluatedObj == null) {
            //alert("Error : return Object is null for xpath => "+xpathExpression);
            return null;
        } else {
            if (evaluatedObj.nodeType == 2 || evaluatedObj.nodeType == 3) {
                retXpathVal = evaluatedObj.nodeValue;
            } else {
                retXpathVal = getNodeValue(evaluatedObj);
            }
        }
        return retXpathVal;
    } catch (e) {
        alert("Exception Occured !! Function : getXpathValue() .. In Catch Block");
        return "";
    }
}

function fnTopAccessSessionTimeOut(winObj) {
    try {
        //alert("fnSessionTimeOut ")
        glbContentWebServerCmdArray = ["<Logoff><commandNode>Authentication/UserCredential</commandNode><Params><appName>TOPACCESS</appName></Params></Logoff>"];
        gblGETRequestXMLArray = ["<Authentication><Status></Status></Authentication>"];
        requestObj.gblBoolHandleRespArray = [true];
        gblHashMapArray = [null];
        calledFromPage = "SessionTimeOut";
        InitiateServerRequest("CMDGET");
    } catch (e) {
        errHandler(e, 'fnSessionTimeOut()', 'AjaxHandler.js?v=1680753140ta', "")
    }

}

function fnHandleTopAccessSessionTimeOut(node) {
    try {
        //alert("redirectUnAuthenticationStatus ...");
        var status = (!node.getElementsByTagName("statusOfOperation")[0].hasChildNodes()) ? 0 : node.getElementsByTagName("statusOfOperation")[0].childNodes[0].nodeValue;
        if (status == AuthStatusConstants["STATUS_OK"] || status == 0 || status == 6 || status == "STATUS_SESSION_FOLDER_NOT_EXISTS") {
            //status == 6 should be removed later after logoff command is properly working
            var winopenerObj = window.top;
            while (winopenerObj.opener != null && typeof winopenerObj.gblTopWindow == 'undefined') {
                winopenerObj = winopenerObj.opener.top;
            }
            /*winopenerObj.fnSetCookie("SESSID",null, -30);
            winopenerObj.fnSetCookie("LOGINSTATUS",null, -30);
            winopenerObj.fnSetCookie("USERROLE",null, -30);
            winopenerObj.fnSetCookie("LOGINMODE",null, -30);
            winopenerObj.fnSetCookie("TA_SETTINGS",null, -30);
            winopenerObj.fnSetCookie("LICENSE_SETTINGS",null, -30);*/
            fnSetClientCookie("pageTrack", "MAIN=LOGIN");
            location.reload();
        } else {
            alert("Logout not successful");
        }
    } catch (e) {
        errHandler(e, 'fnHandleSessionTimeOut()', 'AjaxHandler.js?v=1680753140ta', "")
    }
}

function fnEFilingSessionTimeOut(winObj) {
    //alert("Log out .... Efiling ")
    /*winObj.fnSetCookie("SELECTEDITEM",null, -30);
    winObj.fnSetCookie("Box_Number",null, -30);
    winObj.fnSetCookie("Folder_Number",null, -30);
    winObj.fnSetCookie("Document_Name",null, -30);
    winObj.fnSetCookie("EDITOPR",null ,-30)

    winObj.fnSetCookie("EFILINGSESSPERIOD",null, -30);
    winObj.fnSetCookie("EFILINGSESSTIMER",null, -30);*/
    winObj.fnSetCookie("TEMPVIEW", "");
    topframe.document.body.setAttribute("onunload", "");
    winObj.middleframe.location.replace("EflingSessionTimeOut.html?v=1680753140ta")
    winObj.topframe.location.replace("/Registration/None.html?v=1680753140ta")
    winObj.bottomframe.location.replace("/Registration/None.html?v=1680753140ta")
}

function BindToLocaleMessages(rootNode) {
    try {
        var friendlyId = "";
        var localeData = "";
        var msgParentObj = evaluateXpath(rootNode, "PresentationResources/Messages");
        if (msgParentObj == null)
            return;
        var msgsNode = rootNode.getElementsByTagName("Message");
        if (msgsNode === "undefined" || msgsNode == null || msgsNode.length == 0)
            return;
        for (var i = 0; i < msgsNode.length; i++) {
            friendlyId = msgsNode[i].getAttribute("friendlyId");
            if (friendlyId == null)
                continue;
            localeData = msgsNode[i].getAttribute("description");
            try {
                document.getElementById(friendlyId).innerHTML = localeData;
                for (var j = 1;; j++) {
                    if (document.getElementById(friendlyId + "_" + j) != null)
                        document.getElementById(friendlyId + "_" + j).innerHTML = localeData;
                    else
                        break;
                }
            } catch (E) {}

        }
    } catch (e) {
        alert(friendlyId);
        errHandler(e, 'BindToLocaleMessages()', 'AjaxHandler.js?v=1680753140ta', "")
    }
}

function fnClearAllSessionCookies(redirectFlag) {
    try {
        var winopenerObj = window.top;
        var loginMode = "";
        while (winopenerObj.opener != null && typeof winopenerObj.gblTopWindow == 'undefined') {
            winopenerObj = winopenerObj.opener.top;
        }
        loginMode = winopenerObj.fnGetCookie("LOGINMODE");
        /*winopenerObj.fnSetCookie("SESSID",null, -30);
        winopenerObj.fnSetCookie("LOGINSTATUS",null, -30);
        winopenerObj.fnSetCookie("USERROLE",null, -30);
        winopenerObj.fnSetCookie("LOGINMODE",null, -30);
        winopenerObj.fnSetCookie("TA_SETTINGS",null, -30);
        winopenerObj.fnSetCookie("LICENSE_SETTINGS",null, -30);*/
        winopenerObj.fnSetCookie("IgnoreSessionTimeout", null, -30);

        var pageTrackValue = fnGetClientCookie("pageTrack");
        if (pageTrackValue == "") {
            location.reload();
        } else {
            main = pageTrackValue.split("&")[0].split("=")[1];
        }
        if (loginMode == "SECURED") {
            //alert("if");
            fnSetClientCookie("pageTrack", "MAIN=LOGIN");
            location.reload();
        } else {
            //alert("else");
            if (main == "ADMIN") {
                winopenerObj.location.replace(window.location.protocol + "//" + ((window.location.hostname.indexOf(":") != -1 && window.location.hostname.indexOf("]") == -1) ? (("[" + window.location.hostname + "]") + ((window.location.port != "") ? ":" + window.location.port : "")) : window.location.host) + "/?MAIN=ADMIN");
            } else {
                //window.alert("Session timeout happened.Please restart TopAccess");
                winopenerObj.fnSetCookie("IgnoreSessionTimeout", 0);
                //document.write('<style type="text/css">TD.clsError {FONT-WEIGHT: bold; FONT-SIZE: 10pt; COLOR: white; FONT-STYLE: normal; FONT-FAMILY: Arial, sans-serif; BACKGROUND-COLOR: red; TEXT-DECORATION: none}</style><TABLE BORDER="1"><TR><TD class="clsError">Session timeout.Please restart TopAccess.</TD></TR></TABLE>');
                document.write('<font size="5" color="red">Session timeout.Please restart TopAccess.</font><Br><Br><Br><Br>');
            }
        }
    } catch (e) {
        errHandler(e, 'fnClearAllSessionCookies()', 'AjaxHandler.js?v=1680753140ta', "")
    }
}

function fnClearAllSessionCookiesAndRedirect(redirectFlag) {
    try {
        var winopenerObj = window.top;
        var isEfiling = false;
        var loginMode = "";

        try {
            while (winopenerObj.opener != null && typeof winopenerObj.gblTopWindow == 'undefined') {
                if (winopenerObj.location.href.indexOf("?MAIN=EFILING") != -1) { //winopenerObj.location.href.indexOf("/efiling/Efb.html?v=1680753140ta") != -1){
                    isEfiling = true;
                    break;
                }
                winopenerObj = winopenerObj.opener.top;
            }
        } catch (e) {}
        if (isEfiling == false && winopenerObj.location.href.indexOf("?MAIN=EFILING") != -1) { //winopenerObj.location.href.indexOf("/efiling/Efb.html?v=1680753140ta") != -1){
            isEfiling = true;
        }
        loginMode = winopenerObj.fnGetCookie("LOGINMODE");
        //winopenerObj.fnSetClientCookie("COPYSESSID",null, -30,"/");
        window.fnSetCookie("LOGINSTATUS", null, -30, "/");
        window.fnSetCookie("USERROLE", null, -30, "/");
        window.fnSetCookie("LOGINMODE", null, -30, "/");
        window.fnSetCookie("IgnoreSessionTimeout", null, -30, "/");
        window.fnSetCookie("SESSID", null, -30, "/");
        window.fnSetClientCookie("clicked", null, -30, "/");
        window.fnSetClientCookie("gid", null, -30, "/");
        window.fnSetClientCookie("addrLastVisited", null, -30, "/");
        window.fnSetClientCookie("lastVisited", null, -30, "/");
        window.fnSetClientCookie("Session", null, -30, "/");
        window.fnSetClientCookie("forcedPasswordReset", "false");

        alert(fnGetLocaleString("103392", "Session Timed Out."));
        // var reduxStore = sharedSpace.getStore();
        // reduxStore.dispatch({type: "LOGOUT"});
        if (loginMode == "SECURED") {
            //alert("SECURED");           
            if (isEfiling == true) {
                try {
                    winopenerObj.fnSetClientCookie("COPYSESSID", null, -30, "/");
                } catch (e) {}
                var TAWindowObj = winopenerObj;
                try {
                    while (TAWindowObj.opener != null && typeof TAWindowObj.gblTopWindow == 'undefined') {
                        TAWindowObj = TAWindowObj.opener.top;
                    }
                    var navAppName = navigator.appName;
                    if ((navAppName == "Netscape")) /*Check for all netscape family-firefox,safari,netscape etc*/ {
                        var usrAg = navigator.userAgent; /* stores user agent string*/
                        /*Checking for the string Netscape or Navigator in user agent and retuns the index position*/
                        if ((iIndx = navigator.userAgent.indexOf("Netscape")) != -1 || (iIndx = navigator.userAgent.indexOf("Navigator")) != -1);
                        var netscapeVersion = 0; /*For storing the netscape version*/
                        if (iIndx != -1) /*Do this only if netscape*/ {
                            usrAg = usrAg.substring(iIndx); /*Get the substring Netscape or Navigator*/
                            iIndx = usrAg.indexOf('/'); /*Get the index of the first / encountered*/
                            usrAg = usrAg.substring((iIndx + 1)); /*Get the substring after that / which will contain the version info*/
                            netscapeVersion = parseFloat(usrAg); /*Get the float number out of the string which is the version of netscape*/
                        }
                        if (netscapeVersion >= 7 || navigator.userAgent.indexOf("Firefox") != -1) /*do this for netscape ver >=7 and firefox*/ {
                            winopenerObj.frames[0].frames[0].frames[0].closeAllOpenedWindow();
                            winopenerObj.frames[0].frames[0].frames[0].document.body.setAttribute("onbeforeunload", "");
                        } else {
                            /* do this for safari and netscape ver < 7*/
                            winopenerObj.frames[0].frames[0].frames[0].closeAllOpenedWindow();
                            winopenerObj.frames[0].frames[0].frames[0].document.body.setAttribute("onunload", "");
                        }
                    } else {
                        /*do this for IE*/
                        if (navAppName.indexOf("Microsoft Internet Explorer") != -1) {
                            winopenerObj.frames[0].frames[0].frames[0].closeAllOpenedWindow();
                            winopenerObj.frames[0].frames[0].frames[0].document.body.setAttribute("onbeforeunload", "");
                        } else {
                            winopenerObj.frames[0].frames[0].frames[0].closeAllOpenedWindow();
                            winopenerObj.frames[0].frames[0].frames[0].document.body.setAttribute("onunload", "");
                        }
                    }
                } catch (e) {}

                try {
                    if (TAWindowObj != winopenerObj) {
                        //TAWindowObj  => Topaccees window obj
                        //winopenerObj => efiling window obj
                        fnSetClientCookie("pageTrack", "MAIN=LOGIN");
                        try {
                            TAWindowObj.location.replace(window.location.protocol + "//" + ((window.location.hostname.indexOf(":") != -1 && window.location.hostname.indexOf("]") == -1) ? (("[" + window.location.hostname + "]") + ((window.location.port != "") ? ":" + window.location.port : "")) : window.location.host) + "/?MAIN=TOPACCESS");
                            winopenerObj.close(); //efiling close()
                        } catch (e) {
                            winopenerObj.location.replace(window.location.protocol + "//" + ((window.location.hostname.indexOf(":") != -1 && window.location.hostname.indexOf("]") == -1) ? (("[" + window.location.hostname + "]") + ((window.location.port != "") ? ":" + window.location.port : "")) : window.location.host) + "/?MAIN=EFILING");
                        }
                    } else {
                        winopenerObj.location.replace(window.location.protocol + "//" + ((window.location.hostname.indexOf(":") != -1 && window.location.hostname.indexOf("]") == -1) ? (("[" + window.location.hostname + "]") + ((window.location.port != "") ? ":" + window.location.port : "")) : window.location.host) + "/?MAIN=EFILING");
                    }
                } catch (e) {
                    winopenerObj.close(); //efiling close()
                }
            } else {
                //alert("timeout in Topaccess");
                //alert("Session Timed Out.");
                window.fnSetClientCookie("pageTrack", "MAIN=LOGIN");
                window.location.reload();
            }
        } else {
            //   alert("NORMAL");
            //alert("NORMAL --"+winopenerObj.location.href);
            /* if(main == "ADMIN"){
               //winopenerObj.location.replace(window.location.protocol+"//"+((window.location.hostname.indexOf(":") != -1 && window.location.hostname.indexOf("]") == -1)?(("["+window.location.hostname+"]")+((window.location.port != "")? ":"+window.location.port : "" )) : window.location.host)+"/?MAIN=ADMIN");
			   winopenerObj.location.replace(window.location.protocol+"//"+((window.location.hostname.indexOf(":") != -1 && window.location.hostname.indexOf("]") == -1)?(("["+window.location.hostname+"]")+((window.location.port != "")? ":"+window.location.port : "" )) : window.location.host)+"/?MAIN=LOGIN");
            }else{
                //window.alert("Session timeout happened.Please restart TopAccess");
                winopenerObj.fnSetCookie("IgnoreSessionTimeout",0);
                //document.write('<style type="text/css">TD.clsError {FONT-WEIGHT: bold; FONT-SIZE: 10pt; COLOR: white; FONT-STYLE: normal; FONT-FAMILY: Arial, sans-serif; BACKGROUND-COLOR: red; TEXT-DECORATION: none}</style><TABLE BORDER="1"><TR><TD class="clsError">Session timeout.Please restart TopAccess.</TD></TR></TABLE>');
                document.write('<font size="5" color="red">Session timed out.Please restart TopAccess.</font><Br><Br><Br><Br>');
            }*/
            winopenerObj.fnSetCookie("IgnoreSessionTimeout", 0, "/");
            //alert("Session Timed Out.");
            if (isEfiling == true) {
                //alert("timeout in efiling");
                try {
                    winopenerObj.fnSetClientCookie("COPYSESSID", null, -30, "/");
                } catch (e) {}

                var TAWindowObj = winopenerObj;
                try {
                    while (TAWindowObj.opener != null && typeof TAWindowObj.gblTopWindow == 'undefined') {
                        TAWindowObj = TAWindowObj.opener.top;
                    }
                    var navAppName = navigator.appName;
                    if ((navAppName == "Netscape")) /*Check for all netscape family-firefox,safari,netscape etc*/ {
                        var usrAg = navigator.userAgent; /* stores user agent string*/
                        /*Checking for the string Netscape or Navigator in user agent and retuns the index position*/
                        if ((iIndx = navigator.userAgent.indexOf("Netscape")) != -1 || (iIndx = navigator.userAgent.indexOf("Navigator")) != -1);
                        var netscapeVersion = 0; /*For storing the netscape version*/
                        if (iIndx != -1) /*Do this only if netscape*/ {
                            usrAg = usrAg.substring(iIndx); /*Get the substring Netscape or Navigator*/
                            iIndx = usrAg.indexOf('/'); /*Get the index of the first / encountered*/
                            usrAg = usrAg.substring((iIndx + 1)); /*Get the substring after that / which will contain the version info*/
                            netscapeVersion = parseFloat(usrAg); /*Get the float number out of the string which is the version of netscape*/
                        }
                        if (netscapeVersion >= 7 || navigator.userAgent.indexOf("Firefox") != -1) /*do this for netscape ver >=7 and firefox*/ {
                            winopenerObj.frames[0].frames[0].frames[0].closeAllOpenedWindow();
                            winopenerObj.frames[0].frames[0].frames[0].document.body.setAttribute("onbeforeunload", "");
                        } else {
                            /* do this for safari and netscape ver < 7*/
                            winopenerObj.frames[0].frames[0].frames[0].closeAllOpenedWindow();
                            winopenerObj.frames[0].frames[0].frames[0].document.body.setAttribute("onunload", "");
                        }
                    } else {
                        /*do this for IE*/
                        if (navAppName.indexOf("Microsoft Internet Explorer") != -1) {
                            winopenerObj.frames[0].frames[0].frames[0].closeAllOpenedWindow();
                            winopenerObj.frames[0].frames[0].frames[0].document.body.setAttribute("onbeforeunload", "");
                        } else {
                            winopenerObj.frames[0].frames[0].frames[0].closeAllOpenedWindow();
                            winopenerObj.frames[0].frames[0].frames[0].document.body.setAttribute("onunload", "");
                        }
                    }
                } catch (e) {}

                try {
                    if (TAWindowObj != winopenerObj) {
                        debugger;
                        //alert("efiling opened from TopAccess.So close efiling window and redirect TA to defaultpage");
                        fnSetClientCookie("pageTrack", "MAIN=DEVICE");
                        try {
                            TAWindowObj.location.replace(window.location.protocol + "//" + ((window.location.hostname.indexOf(":") != -1 && window.location.hostname.indexOf("]") == -1) ? (("[" + window.location.hostname + "]") + ((window.location.port != "") ? ":" + window.location.port : "")) : window.location.host) + "/?MAIN=TOPACCESS");
                            winopenerObj.close(); //efiling close()
                        } catch (e) {
                            winopenerObj.location.replace(window.location.protocol + "//" + ((window.location.hostname.indexOf(":") != -1 && window.location.hostname.indexOf("]") == -1) ? (("[" + window.location.hostname + "]") + ((window.location.port != "") ? ":" + window.location.port : "")) : window.location.host) + "/?MAIN=EFILING");
                        }
                    } else {
                        winopenerObj.location.replace(window.location.protocol + "//" + ((window.location.hostname.indexOf(":") != -1 && window.location.hostname.indexOf("]") == -1) ? (("[" + window.location.hostname + "]") + ((window.location.port != "") ? ":" + window.location.port : "")) : window.location.host) + "/?MAIN=EFILING");
                    }
                } catch (e) {
                    winopenerObj.close(); //efiling close()
                }
            } else {
                window.fnSetClientCookie("pageTrack", "MAIN=DEVICE");
                window.location.reload();
                // winopenerObj.location.replace(window.location.protocol+"//"+((window.location.hostname.indexOf(":") != -1 && window.location.hostname.indexOf("]") == -1)?(("["+window.location.hostname+"]")+((window.location.port != "")? ":"+window.location.port : "" )) : window.location.host)+"/?MAIN=TOPACCESS");
            }
        }
        /*if(redirectFlag == 1){
            winopenerObj.location.replace(window.location.protocol+"//"+((window.location.hostname.indexOf(":") != -1 && window.location.hostname.indexOf("]") == -1)?(("["+window.location.hostname+"]")+((window.location.port != "")? ":"+window.location.port : "" )) : window.location.host));
        }else if(redirectFlag == 2){
            //future purpose
            //winopenerObj.location.replace(window.location.protocol+"//"+((window.location.hostname.indexOf(":") != -1 && window.location.hostname.indexOf("]") == -1)?(("["+window.location.hostname+"]")+((window.location.port != "")? ":"+window.location.port : "" )) : window.location.host)+"/?MAIN=LOGIN");
        }*/
    } catch (e) {
        alert("fnClearAllSessionCookiesAndRedirect() .Exception caught in AjaxHandler.js?v=1680753140ta");
    }
}

function fnDisplayLoadingMsg(dispFlag) {
    var frameObj = window.top;
    var loadingDiv;
    while (frameObj.frames.length > 0) {
        if (frameObj.document.getElementsByTagName("iframe")[0] != null)
            break;
        frameObj = frameObj.frames[0];
    }
    if (frameObj.name == "Loginframe" || frameObj.name == "topframe") {
        loadingDiv = frameObj.document.getElementById("LoadingMsgDiv");
        if (!dispFlag && loadingDiv != null) {
            loadingDiv.style.display = "none";
            return;
        }
        if (loadingDiv == null) {
            fnCreateDisplayDiv(frameObj);
        } else {
            loadingDiv.style.display = "";
        }
    } else {
        //alert(opener);
        if (window.opener != null) {
            if (!dispFlag) {
                frameObj.document.getElementById("LoadingMsgDiv").style.display = "none";
                return;
            }
            fnCreateDisplayDiv(frameObj);
        }
    }
}

function fnCreateDisplayDiv(frameObj) {
    var divObj = frameObj.document.createElement("div");
    divObj.id = "LoadingMsgDiv";
    divObj.className = "busy_indicator";
    //divObj.style.zIndex=1000;
    divObj.style.display = "none";
    divObj.style.position = "absolute";
    divObj.style.top = "-5";
    divObj.innerHTML = "Loading...";
    //divObj.style.color = "#ffffff"
    frameObj.document.body.appendChild(divObj);
    divObj.style.left = (fnGetWindowWidth(frameObj) / 2) - 100;
    //divObj.style.vAlign="middle";
    //Rounded("LoadingMsgDiv","rgb(245,155,150)","rgb(245,199,150)");
    divObj.style.display = "";
}

function fnGetWindowWidth(frameObj) {
    var x = 0;
    if (frameObj.innerHeight) {
        x = frameObj.innerWidth;
    } else if (frameObj.document.documentElement && frameObj.document.documentElement.clientHeight) {
        x = frameObj.document.documentElement.clientWidth;
    } else if (frameObj.document.body) {
        x = frameObj.document.body.clientWidth;
    }
    return x;
}

function errHandler(e, fnName, thispage, strFldName) {
    errmsg = "Exception Page: " + thispage + ".Fn : " + fnName + ".Msg : " + e.message + ". Type : " + e.name + ". Desc:" + e.description + ". ErrNo=" + (e.number & 0xFFFF);
    if (strFldName)
        errmsg = errmsg + " FieldName:" + strFldName;
    window.status = errmsg;
    alert("Exception Occured !! " + errmsg);
}


// "HTTP_Code"        "Error Message and Description"

var HTTPError_Status = {

    "12001": "ERROR_INTERNET_OUT_OF_HANDLES :  No more handles could be generated at this time.",
    "12002": "ERROR_INTERNET_TIMEOUT : The request has timed out.",
    "12003": "ERROR_INTERNET_EXTENDED_ERROR.",
    "12004": "ERROR_INTERNET_INTERNAL_ERROR  :  An internal server error has occurred.",
    "12005": "ERROR_INTERNET_INVALID_URL",
    "12006": "ERROR_INTERNET_UNRECOGNIZED_SCHEME : The URL scheme could not be recognized or is not supported.",
    "12007": "ERROR_INTERNET_NAME_NOT_RESOLVED : The server name could not be resolved.",
    "12008": "ERROR_INTERNET_PROTOCOL_NOT_FOUND The requested protocol could not be located.",
    "12009": "ERROR_INTERNET_INVALID_OPTION :  A request to InternetQueryOption or InternetSetOption specified an invalid option value.",
    "12010": "ERROR_INTERNET_BAD_OPTION_LENGTH  : The length of an option supplied to InternetQueryOption or InternetSetOption is incorrect for the type of option specified.",
    "12011": "ERROR_INTERNET_OPTION_NOT_SETTABLE :  The request option cannot be set, only queried.",
    "12012": "ERROR_INTERNET_SHUTDOWN : The Win32 Internet function support is being shut down or unloaded.",
    "12013": "ERROR_INTERNET_INCORRECT_USER_NAME  :  The request to connect and log on to an FTP server could not be completed because the supplied user name is incorrect.",
    "12014": "ERROR_INTERNET_INCORRECT_PASSWORD :  The request to connect and log on to an FTP server could  not be completed because the supplied password is incorrect.",
    "12015": "ERROR_INTERNET_LOGIN_FAILURE   : The request to connect to and log on to an FTP server failed.",
    "12016": "ERROR_INTERNET_INVALID_OPERATION : The requested operation is invalid.",
    "12017": "ERROR_INTERNET_OPERATION_CANCELLED : The operation was canceled, usually because the handle on which the request was operating was closed before the operation completed.",
    "12018": "ERROR_INTERNET_INCORRECT_HANDLE_TYPE : The type of handle supplied is incorrect for this  operation.",
    "12019": "ERROR_INTERNET_INCORRECT_HANDLE_STATE :  The requested operation cannot be carried out because the handle supplied is not in the correct state.",
    "12020": "ERROR_INTERNET_NOT_PROXY_REQUEST : The request cannot be made via a proxy.",
    "12021": "ERROR_INTERNET_REGISTRY_VALUE_NOT_FOUND : A required registry value could not be located.",
    "12022": "ERROR_INTERNET_BAD_REGISTRY_PARAMETER : A required registry value was located but is an incorrect type or has an invalid value.",
    "12023": "ERROR_INTERNET_NO_DIRECT_ACCESS : Direct network access cannot be made at this time.",
    "12024": "ERROR_INTERNET_NO_CONTEXT : An asynchronous request could not be made because a zero context value was supplied.",
    "12025": "ERROR_INTERNET_NO_CALLBACK : An asynchronous request could not be made because a callback function has not been set.",
    "12026": "ERROR_INTERNET_REQUEST_PENDING : The required operation could not be completed because one or more requests are pending.",
    "12027": "ERROR_INTERNET_INCORRECT_FORMAT :  The format of the request is invalid.",
    "12028": "ERROR_INTERNET_ITEM_NOT_FOUND :  The requested item could not be located.",
    "12029": "ERROR_INTERNET_CANNOT_CONNECT :  The attempt to connect to the server failed.",
    "12030": "ERROR_INTERNET_CONNECTION_ABORTED :  The connection with the server has been terminated.",
    "12031": "ERROR_INTERNET_CONNECTION_RESET :  The connection with the server has been reset.",
    "12032": "ERROR_INTERNET_FORCE_RETRY :  Calls for the Win32 Internet function to redo the request.",
    "12033": "ERROR_INTERNET_INVALID_PROXY_REQUEST :  The request to the proxy was invalid.",
    "12036": "ERROR_INTERNET_HANDLE_EXISTS :  The request failed because the handle already exists.",
    "12037": "ERROR_INTERNET_SEC_CERT_DATE_INVALID :  SSL certificate date that was received from the server is bad. The certificate is expired.",
    "12038": "ERROR_INTERNET_SEC_CERT_CN_INVALID : SSL certificate common name (host name field) is incorrect. For example, if you entered www.server.com and the common name on the certificate says www.different.com.",
    "12039": "ERROR_INTERNET_HTTP_TO_HTTPS_ON_REDIR :  The application is moving from a non-SSL to an SSL  connection because of a redirect.",
    "12040": "ERROR_INTERNET_HTTPS_TO_HTTP_ON_REDIR : The application is moving from an SSL to an non-SSL connection because of a redirect.",
    "12041": "ERROR_INTERNET_MIXED_SECURITY : Indicates that the content is not entirely secure. Some of the content being viewed may have come from unsecured servers.",
    "12042": "ERROR_INTERNET_CHG_POST_IS_NON_SECURE : The application is posting and attempting to change multiple lines of text on a server that is not secure.",
    "12043": "ERROR_INTERNET_POST_IS_NON_SECURE :  The application is posting data to a server that is not secure.",
    "12110": "ERROR_FTP_TRANSFER_IN_PROGRESS : The requested operation cannot be made on the FTP session handle because an operation is already in progress.",
    "12111": "ERROR_FTP_DROPPED : The FTP operation was not completed because the session was aborted.",
    "12130": "ERROR_GOPHER_PROTOCOL_ERROR :  An error was detected while parsing data returned from the gopher server.",
    "12131": "ERROR_GOPHER_NOT_FILE : The request must be made for a file locator.",
    "12132": "ERROR_GOPHER_DATA_ERROR :  An error was detected while receiving data from the gopher server.",
    "12133": "ERROR_GOPHER_END_OF_DATA : The end of the data has been reached.",
    "12134": "ERROR_GOPHER_INVALID_LOCATOR : The supplied locator is not valid.",
    "12135": "ERROR_GOPHER_INCORRECT_LOCATOR_TYPE : The type of the locator is not correct for this operation.",
    "12136": "ERROR_GOPHER_NOT_GOPHER_PLUS  : The requested operation can only be made against a Gopher server or with a locator that specifies a Gopher+ operation.",
    "12137": "ERROR_GOPHER_ATTRIBUTE_NOT_FOUND :The requested attribute could not be located.",
    "12138": "ERROR_GOPHER_UNKNOWN_LOCATOR The locator type is unknown.",
    "12150": "ERROR_HTTP_HEADER_NOT_FOUND : The requested header could not be located.",
    "12151": "ERROR_HTTP_DOWNLEVEL_SERVER : The server did not return any headers.",
    "12152": "ERROR_HTTP_INVALID_SERVER_RESPONSE : The server response could not be parsed.",
    "12153": "ERROR_HTTP_INVALID_HEADER : The supplied header is invalid.",
    "12154": "ERROR_HTTP_INVALID_QUERY_REQUEST : The request made to HttpQueryInfo is invalid.",
    "12155": "ERROR_HTTP_HEADER_ALREADY_EXISTS : The header could not be added because it already exists.",
    "12156": "ERROR_HTTP_REDIRECT_FAILED : The redirection failed because either the scheme changed (for example, HTTP to FTP) or all attempts made to redirect failed (default is five attempts)."
};