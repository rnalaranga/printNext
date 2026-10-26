//for password Policy Settings///////////////////////////////////////////
var domainXML = "";
var gblAuthDom = "";
var gblAuthenticationXpathsHM = new Object();

var directoryServiceListArray = null;
var LDAPAuthListArray =null;
var cardLDAPAuthListArray=null; //RCR_1766
var EmailLDAPServerListArray=null;
var cardAuthListArray=null;
var pinAuthListArray=null;
var homeDirAuthListArray=null;
var windowsAuthListArray=null;
var original_windowsAuthListArray=null;
var cardWindowsAuthListArray=null; //RCR_1766
var original_cardWindowsAuthListArray=null; //RCR_1766

var availableLDAPServerList=null;
var assignedLDAPServerList=null;
var original_assignedLDAPServerList=null;
//RCR_1766
var availableCardAuthLDAPServerList=null;
var assignedCardAuthLDAPServerList=null;
var original_assignedCardAuthLDAPServerList=null;


var availableEmailLDAPServerList=null;
var assignedEmailLDAPServerList=null;
var original_assignedEmailLDAPServerList=null;

var availablePinServerList=null;
var assignedPinServerList=null;
var original_assignedPinServerList=null;

var availableHomeDirServerList=null;
var assignedHomeDirServerList=null;
var original_assignedHomeDirServerList=null;

var availableCardServerList=null;
var assignedCardServerList=null;
var original_assignedCardServerList=null;

var diagnostic_08_3644 = "";

var originalAuthMode = "";

var Security_TableTemplateLocation = "/HandlebarTemplates/scrollableTableTemplate.html?v=1717026199ta";
var scrollableTable = null;

var isProjectCodeRegistered = true;

var RBA_LDAPSELID_Global = "";

var CARD_AUTH_Global="";

var MOB_AUTH_Global="";

var MOBAUTH_AUTOCHANGE_Global="";
var PwdPolicy_Xpath_Widget_HashTable = {

    "SecurityConfiguration/PasswordSettings/PasswordPolicy/User/PasswordPolicyInfo/minCharacters"                                                    : "UsrPwdLen",
    "SecurityConfiguration/PasswordSettings/PasswordPolicy/User/PasswordPolicyInfo/PasswdExpiryInfo/state"                                           : "UsrAvlPrdDrpDwn",
    "SecurityConfiguration/PasswordSettings/PasswordPolicy/User/PasswordPolicyInfo/PasswdExpiryInfo/passwdExpirationLimit"                           : "UsrExpDays",
    "SecurityConfiguration/PasswordSettings/PasswordPolicy/User/PasswordPolicyInfo/LockAccInfo/state"                                                : "UsrLockoutDrpDwn",
    "SecurityConfiguration/PasswordSettings/PasswordPolicy/User/PasswordPolicyInfo/LockAccInfo/loginFailureLimit"                                    : "UsrNoOfRetry",
    "SecurityConfiguration/PasswordSettings/PasswordPolicy/User/PasswordPolicyInfo/LockAccInfo/lockOutTime"                                          : "UsrLockOutTime",
    "SecurityConfiguration/PasswordSettings/PasswordPolicy/User/PasswordPolicyInfo/RequirementsToApply/state"                                        : "UsrReqtsDrpDwn",
    "SecurityConfiguration/PasswordSettings/PasswordPolicy/User/PasswordPolicyInfo/RequirementsToApply/RestrictedPassword/password[@id='1']"        : "Hidden_UsrPwdStrList1",
    "SecurityConfiguration/PasswordSettings/PasswordPolicy/User/PasswordPolicyInfo/RequirementsToApply/RestrictedPassword/password[@id='2']"        : "Hidden_UsrPwdStrList2",
    "SecurityConfiguration/PasswordSettings/PasswordPolicy/User/PasswordPolicyInfo/RequirementsToApply/RestrictedPassword/password[@id='3']"        : "Hidden_UsrPwdStrList3",
    "SecurityConfiguration/PasswordSettings/PasswordPolicy/User/PasswordPolicyInfo/RequirementsToApply/RestrictedPassword/password[@id='4']"        : "Hidden_UsrPwdStrList4",
    "SecurityConfiguration/PasswordSettings/PasswordPolicy/User/PasswordPolicyInfo/RequirementsToApply/RestrictedPassword/password[@id='5']"        : "Hidden_UsrPwdStrList5",

    "SecurityConfiguration/PasswordSettings/PasswordPolicy/Admin/PasswordPolicyInfo/minCharacters"                                                   : "AdminPwdLen",
    "SecurityConfiguration/PasswordSettings/PasswordPolicy/Admin/PasswordPolicyInfo/PasswdExpiryInfo/state"                                          : "AdminAvlPrdDrpDwn",
    "SecurityConfiguration/PasswordSettings/PasswordPolicy/Admin/PasswordPolicyInfo/PasswdExpiryInfo/passwdExpirationLimit"                          : "AdminExpDays",
    "SecurityConfiguration/PasswordSettings/PasswordPolicy/Admin/PasswordPolicyInfo/LockAccInfo/state"                                               : "AdminLockoutDrpDwn",
    "SecurityConfiguration/PasswordSettings/PasswordPolicy/Admin/PasswordPolicyInfo/LockAccInfo/loginFailureLimit"                                   : "AdminNoOfRetry",
    "SecurityConfiguration/PasswordSettings/PasswordPolicy/Admin/PasswordPolicyInfo/LockAccInfo/lockOutTime"                                         : "AdminLockOutTime",
    "SecurityConfiguration/PasswordSettings/PasswordPolicy/Admin/PasswordPolicyInfo/RequirementsToApply/state"                                       : "AdminReqtsDrpDwn",
    "SecurityConfiguration/PasswordSettings/PasswordPolicy/Admin/PasswordPolicyInfo/RequirementsToApply/RestrictedPassword/password[@id='1']"       : "Hidden_AdminPwdStrList1",
    "SecurityConfiguration/PasswordSettings/PasswordPolicy/Admin/PasswordPolicyInfo/RequirementsToApply/RestrictedPassword/password[@id='2']"       : "Hidden_AdminPwdStrList2",
    "SecurityConfiguration/PasswordSettings/PasswordPolicy/Admin/PasswordPolicyInfo/RequirementsToApply/RestrictedPassword/password[@id='3']"       : "Hidden_AdminPwdStrList3",
    "SecurityConfiguration/PasswordSettings/PasswordPolicy/Admin/PasswordPolicyInfo/RequirementsToApply/RestrictedPassword/password[@id='4']"       : "Hidden_AdminPwdStrList4",
    "SecurityConfiguration/PasswordSettings/PasswordPolicy/Admin/PasswordPolicyInfo/RequirementsToApply/RestrictedPassword/password[@id='5']"       : "Hidden_AdminPwdStrList5",

    "SecurityConfiguration/PasswordSettings/PasswordPolicy/Others/PasswordPolicyInfo/minCharacters"                                                  : "PwdLen",
    "SecurityConfiguration/PasswordSettings/PasswordPolicy/Others/PasswordPolicyInfo/LockAccInfo/state"                                              : "LockoutDrpDwn",
    "SecurityConfiguration/PasswordSettings/PasswordPolicy/Others/PasswordPolicyInfo/LockAccInfo/loginFailureLimit"                                  : "NoOfRetry",
    "SecurityConfiguration/PasswordSettings/PasswordPolicy/Others/PasswordPolicyInfo/LockAccInfo/lockOutTime"                                        : "LockOutTime",
    "SecurityConfiguration/PasswordSettings/PasswordPolicy/Others/PasswordPolicyInfo/RequirementsToApply/state"                                      : "ReqtsDrpDwn"


}

var authSettingErrHashMap = {
    "STATUS_FAILED"           : fnGetLocaleString("103472","Internal Error has occurred. Please try again.") ,
    "STATUS_INVALID_INPUT"    : fnGetLocaleString("103472","Internal Error has occurred. Please try again.") ,
    "STATUS_FATAL_ERROR"      : fnGetLocaleString("103472","Internal Error has occurred. Please try again.") ,
    "STATUS_UNKNOWN_ERROR"    : fnGetLocaleString("103472","Internal Error has occurred. Please try again.") ,
    "STATUS_INVALID_USERTOKEN": fnGetLocaleString("103472","Internal Error has occurred. Please try again.") ,
    "STATUS_USER_NOT_FOUND"   : fnGetLocaleString("103472","Internal Error has occurred. Please try again.") ,
    "STATUS_DB_CORRUPTED"     : fnGetLocaleString("103472","Internal Error has occurred. Please try again.") ,
    "STATUS_PERMISSION_CHECK_ERROR"     : fnGetLocaleString("103490","You do not have the permission."),
    "STATUS_NO_PERMISSION"    : fnGetLocaleString("DUMMYRESID","Invalid User")
};

function fnGetPwdSettings() {
    try {
		gblSETRequestXMLArray = ["<SecurityConfiguration></SecurityConfiguration>"];
		//glbContentWebServerCmdArray = ["<GetPasswordConfiguration><commandNode>SecurityConfiguration/PasswordSettings</commandNode></GetPasswordConfiguration>"];
        glbContentWebServerCmdArray = ["<GetPasswordConfiguration><commandNode>SecurityConfiguration/PasswordSettings</commandNode></GetPasswordConfiguration>","<GetDefaultPasswordConfiguration><commandNode>SecurityConfiguration/PasswordSettings</commandNode></GetDefaultPasswordConfiguration>"];
        gblGETRequestXMLArray = ["<SecurityConfiguration><PasswordSettings/></SecurityConfiguration>"];
        gblBoolHandleRespArray = [true];
        gblHashMapArray = [PwdPolicy_Xpath_Widget_HashTable];
        calledFromPage="Security-GetPwdSettings";
        InitiateServerRequest("SETCMDGET");
    } catch(e){errHandler(e,'fnGetPwdSettings()','PasswordPolicy.html?v=1717026199ta',"")}
}
function fillPwdSettings(node){
    try {
        var statusOper = getXpathValue(node,"statusOfOperation");
        if(statusOper=="STATUS_OK" || statusOper==0 || statusOper=="0"){
            BindToWidgets(node,PwdPolicy_Xpath_Widget_HashTable);
            setValue("UsrPwdStrList1",getValue("Hidden_UsrPwdStrList1"));
            setValue("AdminPwdStrList1",getValue("Hidden_AdminPwdStrList1"));
            setValue("UsrPwdStrList2",getValue("Hidden_UsrPwdStrList2"));
            setValue("AdminPwdStrList2",getValue("Hidden_AdminPwdStrList2"));
            setValue("UsrPwdStrList3",getValue("Hidden_UsrPwdStrList3"));
            setValue("AdminPwdStrList3",getValue("Hidden_AdminPwdStrList3"));
            setValue("UsrPwdStrList4",getValue("Hidden_UsrPwdStrList4"));
            setValue("AdminPwdStrList4",getValue("Hidden_AdminPwdStrList4"));
            setValue("UsrPwdStrList5",getValue("Hidden_UsrPwdStrList5"));
            setValue("AdminPwdStrList5",getValue("Hidden_AdminPwdStrList5"));
            //setValue("UsrPwdStrList",((temp =getXpathValue(node, "SecurityConfiguration/PasswordSettings/PasswordPolicy/User/PasswordPolicyInfo/RequirementsToApply/RestrictedPassword")) == "" || temp == null)?  '' : temp);
            //setValue("AdminPwdStrList",((temp =getXpathValue(node, "SecurityConfiguration/PasswordSettings/PasswordPolicy/Admin/PasswordPolicyInfo/RequirementsToApply/RestrictedPassword")) == "" || temp == null)?  '' : temp);
        }else
            alert(fnGetLocaleString("DUMMYRESID","Error : Invalid Server Response. Command Execution Failed at Server"));
        parent.fraTitle.document.getElementsByName("Save")[0].disabled = false;
        parent.fraTitle.document.getElementsByName("Reset")[0].disabled = false;
    }catch(e){errHandler(e,'fillPwdSettings()','PasswordPolicy.html?v=1717026199ta',"")}
}
function fnSavePwdSettings(){
    try {
        calledFromPage="Security-SavePwdSettings";
        commitPwdValues();
    }catch(e){errHandler(e,'fnSavePwdSettings()','PasswordPolicy.html?v=1717026199ta',"")}
}
function commitPwdValues() {
    try {
        var arrkey = null;
        var orgValueNode = null;
        var originalWidgetVal = "";
        var changedWidgetVal = "";
        var xpathWidgetHashObj = PwdPolicy_Xpath_Widget_HashTable;  //Xpath Map
        var changeCount = 0;

        if (xpathWidgetHashObj == null) {
            alert(fnGetLocaleString("103472","Internal Error has occurred. Please try again."));
            return;
        }
        // create example document from scratch
        var xmlDocument = null;
        var setValue = null;
        if (fnIsIEBrowser()) {
            xmlDocument = new ActiveXObject('Microsoft.XMLDOM');
            setValue = xmlDocument.createElement('SetValue');
            xmlDocument.appendChild(setValue);
        }  else if (document.implementation && document.implementation.createDocument){
            xmlDocument = document.implementation.createDocument('','SetValue', null);
            if(xmlDocument) setValue = xmlDocument.documentElement;
        } else {
            alert(fnGetLocaleString("DUMMYRESID","Browser does not support the serialization."));
            return;
        }
        for(var xpathkey in xpathWidgetHashObj){
                orgValueNode = evaluateXpath(gblDomObjArray[0],xpathkey);
                originalWidgetVal = "";
                if (orgValueNode != null && orgValueNode != undefined) {
                    originalWidgetVal = getNodeValue(orgValueNode);
                }
                changedWidgetVal = getValue(xpathWidgetHashObj[xpathkey]);
                if(originalWidgetVal != undefined ){
                    if ((originalWidgetVal != changedWidgetVal)){
                        changeCount++;
                        var key = xpathkey;
                        var rootNode = setValue;
                        arrkey = xpathkey.split("/");
                        var path = "";
                        for(var j=0 ; j<arrkey.length; j++) {
                            if(path != "") {
                                path = path + "/" + arrkey[j];
                            }  else {
                                path =  arrkey[j];
                            }
                            var currentNode = evaluateXpath( xmlDocument, path )

                            if(currentNode !=undefined && currentNode !=null)  {
                                rootNode = currentNode;
                            } else {
                                if(arrkey[j].indexOf("[")!= -1)
                                {
                                    var start = arrkey[j].indexOf("[");
                                    var end   = arrkey[j].indexOf("]");
                                    var strIndex = arrkey[j].substring(start+1,end);
                                    if (strIndex.indexOf("@") != -1) {
                                        var attrStart = strIndex.indexOf("@");
                                        var attrEnd   = strIndex.indexOf("=");
                                        var strAttrName = strIndex.substring(attrStart+1,attrEnd);
                                        var strAttrVal = strIndex.substring(attrEnd+1,end);
                                        //alert(strAttrVal)
                                        strAttrVal = strAttrVal.replace(/[\'\"]/g,"");
                                        var tagName =   arrkey[j].substring(0,start) ;
                                        var newElement = xmlDocument.createElement(tagName);
                                        newElement.setAttribute(strAttrName,strAttrVal);
                                        rootNode.appendChild(newElement);
                                        if(newElement !=undefined && newElement !=null)  {
                                            if(j == arrkey.length -1 ) {
                                                newElement.appendChild(xmlDocument.createTextNode(changedWidgetVal));
                                            }
                                            rootNode =  newElement;
                                        }
                                    }
                                }
                                else {
                                    var newElement = xmlDocument.createElement( arrkey[j]);
                                    rootNode.appendChild(newElement);
                                    if(newElement !=undefined && newElement !=null)  {
                                        if(j == arrkey.length -1 )   {
                                            //alert("changedWidgetVal "+changedWidgetVal+"create a text node for path "+path)
                                            newElement.appendChild(xmlDocument.createTextNode(changedWidgetVal));
                                        }
                                        rootNode =  newElement;
                                }

                            }
                        }
                    } //if ((originalWidgetVal != changedWidgetVal))
                } //if(originalWidgetVal != undefined )
            } //for(var xpath in XpathWidgetHashObj)
        }
        if (changeCount != 0) {
            var setvalue_xml = "";

            var finalXMLstr = null;
            if (fnIsIEBrowser()) {
                finalXMLstr = xmlDocument.xml;
            } else if (document.implementation && document.implementation.createDocument){
                var xmlSerializer = new XMLSerializer();
                finalXMLstr = xmlSerializer.serializeToString(xmlDocument);
                finalXMLstr = finalXMLstr.replace(/<\?xml[^>]*\?>/,"");
            } else {
                alert(fnGetLocaleString("DUMMYRESID","Browser does not support the serialization."));
            }
            setvalue_xml = "<PasswordConfigurationSettings><commandNode>SecurityConfiguration/PasswordSettings</commandNode>"+finalXMLstr+"</PasswordConfigurationSettings>";
            gblBoolHandleRespArray = [true];

            var efilingCmdXml = getValue("EfilingCmdXml");
            if(getValue("EfilingCmdXml") == ""){
                glbContentWebServerCmdArray = [setvalue_xml];
            }else{
                glbContentWebServerCmdArray = [setvalue_xml,efilingCmdXml];
            }
            parent.fraTitle.document.getElementsByName("Save")[0].disabled = true;
            InitiateServerRequest("CMD");
        } else {
            document.location.reload(false);
        }

    } catch(e){errHandler(e,'commitPwdValues()','Security.js?v=1717026199ta',"")}
}
function redirectPwdSettings(node){
    //var statusOper = (!node.getElementsByTagName("statusOfOperation")[0].hasChildNodes())? 0 : node.getElementsByTagName("statusOfOperation")[0].childNodes[0].nodeValue;
    var statusOper = getXpathValue(node,"PasswordConfigurationSettings/Response/statusOfOperation");
    if(statusOper=="STATUS_OK" || statusOper==0 || statusOper=="0"){
       document.location.reload(false);
    }else{
       alert(fnGetLocaleString("103472","Internal Error has occurred. Please try again."));
       parent.fraTitle.document.getElementsByName("save")[0].disabled = false;
    }
}

/*******************for Authentication Settings**************/
function fnGetAuthenticationSettings() {
    try {
        var isEWBElectronicKey = fnTrackCookie("LICENSE_SETTINGS","GET","EWB");
        var ewbLicenseCommand = "";
            if(isEWBElectronicKey == "") {
                glbContentWebServerCmdArray = ["<GetSettings><commandNode>Authentication/AuthenticationSettings</commandNode></GetSettings>","<CheckBillingCodeRegistered><commandNode>Accounting/BillingCodes</commandNode></CheckBillingCodeRegistered>","<GetRbacSettings><commandNode>RBACManager/RbacSettings</commandNode><Params><settingDetails contentType='XPath'>RBACManager/RbacSettings</settingDetails></Params></GetRbacSettings>","<GetDiagnosticMode><commandNode>DiagnosticMode</commandNode></GetDiagnosticMode>","<GetLocalAuthModeSwitchover><commandNode>DevAuthMgmtPlugIn</commandNode></GetLocalAuthModeSwitchover>","<GetPINSettings><commandNode>SecurityConfiguration/PINSettings</commandNode></GetPINSettings>","<LicenseEnableCheck><commandNode>LicenseMgmt/Licenses</commandNode><Params><FunctionName contentType='Value'>EWB</FunctionName></Params></LicenseEnableCheck>","<GetProjectCodes><commandNode>Accounting/ProjectCodes</commandNode></GetProjectCodes>"];
            }

            else{
            glbContentWebServerCmdArray = ["<GetSettings><commandNode>Authentication/AuthenticationSettings</commandNode></GetSettings>","<CheckBillingCodeRegistered><commandNode>Accounting/BillingCodes</commandNode></CheckBillingCodeRegistered>","<GetRbacSettings><commandNode>RBACManager/RbacSettings</commandNode><Params><settingDetails contentType='XPath'>RBACManager/RbacSettings</settingDetails></Params></GetRbacSettings>","<GetDiagnosticMode><commandNode>DiagnosticMode</commandNode></GetDiagnosticMode>","<GetLocalAuthModeSwitchover><commandNode>DevAuthMgmtPlugIn</commandNode></GetLocalAuthModeSwitchover>","<GetPINSettings><commandNode>SecurityConfiguration/PINSettings</commandNode></GetPINSettings>","<GetProjectCodes><commandNode>Accounting/ProjectCodes</commandNode></GetProjectCodes>"];
            }

        gblSETRequestXMLArray = ["<DevAuthMgmtPlugIn></DevAuthMgmtPlugIn>","<DiagnosticMode><Category><Mode>08</Mode><MainCode><Code>3642</Code></MainCode></Category></DiagnosticMode>","<SecurityConfiguration></SecurityConfiguration>"];

        gblGETRequestXMLArray = ["<Authentication><AuthenticationSettings/></Authentication>","<Controller><Settings><AdminSystemSettings><EMail><fromAddr></fromAddr></EMail><Authentication></Authentication><Printer><AutoRelease></AutoRelease></Printer><NFCSettings></NFCSettings></AdminSystemSettings><Functions><MDS><Authentication/></MDS></Functions></Settings></Controller>","<Network><Services><DirectoryServices/></Services><Protocols><SMTP></SMTP></Protocols></Network>","<RBACManager><RbacSettings></RbacSettings></RBACManager>","<DiagnosticMode></DiagnosticMode>","<DevAuthMgmtPlugIn></DevAuthMgmtPlugIn>","<SecurityConfiguration></SecurityConfiguration>","<MFP><System><IDGate><Installation/><NFC></NFC></IDGate></System></MFP>","<Accounting><ProjectCodes></ProjectCodes></Accounting>"];
        gblBoolHandleRespArray = [true];
        gblHashMapArray = [null];
        calledFromPage="Security-AuthSettings";
        InitiateServerRequest("SETCMDGET");
    } catch(e){errHandler(e,'fnGetAuthenticationSettings()','Authentication.html?v=1717026199ta',"");}
}

function fnFillAuthenticationSettings(node) {
    try {
        gblAuthDom = node;
        if(node == null){
            alert(fnGetLocaleString("103472","Internal Error has occurred. Please try again."));
            return;
        }
        var sopObjs = node.getElementsByTagName("statusOfOperation");
        if(sopObjs == null){
            alert(fnGetLocaleString("103472","Internal Error has occurred. Please try again."));
            return;
        }
        if(sopObjs[0].childNodes[0].nodeValue != "STATUS_OK" || sopObjs[2].childNodes[0].nodeValue != "STATUS_OK") {
            alert(fnGetLocaleString("103472","Internal Error has occurred. Please try again."));
            return;
        }
        if(sopObjs[4].childNodes[0].nodeValue != "Normal") {
            alert(fnGetLocaleString("103472","Internal Error has occurred. Please try again."));
            return;
        }

        //SOP DiagnosticMode 3500
        var diagnosticMode3500_SOP = getXpathValue(node,"GetDiagnosticMode/Response/statusOfOperation");
        if(diagnosticMode3500_SOP == "STATUS_PERMISSION_CHECK_ERROR") {
            alert(fnGetLocaleString("103490","You do not have the permission."));
            return;
        } else /*if(diagnosticMode3500_SOP == "Normal")*/ {
            setValue("CARDAUTHENABLED",getXpathValue(node,"MFP/System/IDGate/Installation"));
        }

        Diag_3642_0_code = getXpathValue(node,"DiagnosticMode/Category/MainCode[@MainCode='3642']/SubCode[@index='0']/ValueList/Value/Value");

        var isEWBElectronicKey = fnTrackCookie("LICENSE_SETTINGS","GET","EWB");
        if(isEWBElectronicKey==""){
            var EWBLicenseStatusChk = getXpathValue(node,"LicenseEnableCheck/Response/statusOfOperation");
            if ( EWBLicenseStatusChk == "STATUS_OK") {
                fnTrackCookie("LICENSE_SETTINGS","SET","EWB","YES");
            }
            else {
                fnTrackCookie("LICENSE_SETTINGS","SET","EWB","NO");
            }
            isEWBElectronicKey = fnTrackCookie("LICENSE_SETTINGS","GET","EWB");
        }
        if(isEWBElectronicKey == "NO")
           $('[name=SELAUTHEWB]').attr('disabled', true);
       else
            $('[name=SELAUTHEWB]').removeAttr('disabled');
        //hardcoded
        //setValue("CARDAUTHENABLED",1);
        /*for(var i=0;i<sopObjs.length;i++){
         if(sopObjs[i].childNodes[0].nodeValue != "STATUS_OK"){
         alert("Error : Invalid Server Response. Command Execution Failed at Server");
         return;
         }
         }*/

        var strDisable = fnGetLocaleString("100526","Disable");

        //Single Sign On
        setValue("SELSINGLESIGNON",getXpathValue(node, "Controller/Settings/AdminSystemSettings/Authentication/SingleSignOn"))
        var deptExists = getXpathValue(node,"CheckBillingCodeRegistered/Response/statusOfOperation");
        var deptMgmtStatus = getXpathValue(node,"Controller/Settings/AdminSystemSettings/Authentication/deptManagement");
        var deptMgmtEnf = getXpathValue(node,"Controller/Settings/AdminSystemSettings/Authentication/deptCodeEnforcement");
        var deptNumReq = getXpathValue(node,"Controller/Settings/AdminSystemSettings/Authentication/NeedDeptInfo");
        var deptMgmtCopy = getXpathValue(node,"Controller/Settings/AdminSystemSettings/Authentication/DeptCounterSetting/Copy");
        var deptMgmtFax = getXpathValue(node,"Controller/Settings/AdminSystemSettings/Authentication/DeptCounterSetting/FAX");
        var deptMgmtPrint = getXpathValue(node,"Controller/Settings/AdminSystemSettings/Authentication/DeptCounterSetting/Print");
        var deptMgmtScan = getXpathValue(node,"Controller/Settings/AdminSystemSettings/Authentication/DeptCounterSetting/Scan");
        var deptMgmtList = getXpathValue(node,"Controller/Settings/AdminSystemSettings/Authentication/DeptCounterSetting/List");
        var deptMgmtUserFn = getXpathValue(node,"Controller/Settings/AdminSystemSettings/Authentication/DeptCounterSetting/UserFunctions");
        var deptMgmtEWB = getXpathValue(node,"Controller/Settings/AdminSystemSettings/Authentication/DeptCounterSetting/EWB"); //added on 19April2017[EBX_DCR_53407]
        var MDSMode = getXpathValue(node,"Controller/Settings/Functions/MDS/Authentication");
        if(deptExists == "STATUS_DEPARTMENT_EXISTS") {
            document.frmAuth.SELDEPTCODE.options[0]=new Option(fnGetLocaleString("100526","Disable"),"false");
            document.frmAuth.SELDEPTCODE.options[1]=new Option(fnGetLocaleString("100604","Enable"),"true");
        }
        else if(deptExists == "STATUS_DEPARTMENT_NONEXISTS") {
            document.frmAuth.SELDEPTCODE.options[0]=new Option(fnGetLocaleString("100526","Disable"),"false");
        }
        setValue("SELDEPTCODE",deptMgmtStatus);
        setValue("SELDEPTENFORCE",deptMgmtEnf);
        setValue("SELDEPTNUMREQ",deptNumReq);
        setValue("SELDEPTMGMTCOPY",deptMgmtCopy);
        setValue("SELDEPTMGMTFAX",deptMgmtFax);
        setValue("SELDEPTMGMTPRINT",deptMgmtPrint);
        setValue("SELDEPTMGMTSCAN",deptMgmtScan);
        setValue("SELDEPTMGMTLIST",deptMgmtList);
        setValue("SELDEPTMGMTUSERFN",deptMgmtUserFn);
        setValue("SELDEPTMGMTEWB",deptMgmtEWB); //added on 19April2017[EBX_DCR_53407]
        //User Auth Settings
        var authObj = evaluateXpath(node,"Authentication/AuthenticationSettings");
        var userAuthStatus = ((temp =getXpathValue(authObj, "openAccess")) == "" || temp == null)?  '' : temp;

        setValue("SELUSERAUTH",userAuthStatus);
        var userAuthMode = getXpathValue(authObj, "AuthenticationType/authenticationName");
        originalAuthMode = userAuthMode;
        //userAuthMode = "Ldap";

        var chkAutoCreateUser = getXpathValue(authObj,"AuthenticationType/autoRegistration");
        if(chkAutoCreateUser == "true")
            document.frmAuth.AUTOCREATEUSER.checked = true;
        else
            document.frmAuth.AUTOCREATEUSER.checked = false;
        //RCR_1766 AUTOMATIC DELETION OF UN-LOGGED IN USER
        var autoDeleteUser = getXpathValue(authObj,"deletionOfUnloggedinLocalUsers/deleteLocalUser");
        if (autoDeleteUser == "true") {
            document.frmAuth.AUTODELETEUSER.checked = true;
            document.frmAuth.VALIDITYLOCALUSER.disabled = false;
        }else 
        {
            document.frmAuth.AUTODELETEUSER.checked = false;
            document.frmAuth.VALIDITYLOCALUSER.disabled = true;
        }
    setValue("VALIDITYLOCALUSER", getXpathValue(authObj, "deletionOfUnloggedinLocalUsers/validityPeriodOfLocalUser"));
        //---- RCR_1766 END -----
        
        // Card Auth S&S 3rd
        var chkUseCacheInfo = getXpathValue(authObj,"CacheInformation/isCacheEnabled");
        if(chkUseCacheInfo == "true")
            document.frmAuth.USECACHEINFO.checked = true;
        else
            document.frmAuth.USECACHEINFO.checked = false;
        setValue("VALIDITYCACHE",getXpathValue(authObj, "CacheInformation/availablePeriod"));
        var chkRegLocalUser = getXpathValue(authObj,"OneTimeUser/isOneTimeUserEnabled");
        if(chkRegLocalUser == "true")
            document.frmAuth.REGGUESTLOCALUSER.checked = true;
        else
            document.frmAuth.REGGUESTLOCALUSER.checked = false;
        setValue("VALIDITYGUESTUSER",getXpathValue(authObj, "OneTimeUser/availablePeriod"));
        ///End
        var pwdAuthPrintJob = getXpathValue(node,"Controller/Settings/AdminSystemSettings/Authentication/PrintJobPasswordAuth");
        if(pwdAuthPrintJob == "true")
            document.frmAuth.PWDAUTHPRINTJOB.checked = true;
        else
            document.frmAuth.PWDAUTHPRINTJOB.checked = false;

        var enableGuestUser = getXpathValue(authObj,"allowGuestUser");
        if(enableGuestUser == "true")
            document.frmAuth.USERGUEST.checked = true;
        else
            document.frmAuth.USERGUEST.checked = false;

        setValue("SELUSERAUTHENFORCE",getXpathValue(authObj, "unauthorizedPrintJob"));
        setValue("SELAUTORELONLOGIN",getXpathValue(node,"Controller/Settings/AdminSystemSettings/Printer/AutoRelease"));
        //setValue("SELRESTRICTRSERVEDJOB",getXpathValue(node,"Controller/Settings/RestrictionForReceivedJob"));
        setValue("USERAUTHTYPE",userAuthMode);


        setValue("SELAUTHCOPY",getXpathValue(authObj,"AuthenticatedTarget/copy"));
        setValue("SELAUTHFAX",getXpathValue(authObj,"AuthenticatedTarget/fax"));
        setValue("SELAUTHPRINT",getXpathValue(authObj,"AuthenticatedTarget/print"));
        setValue("SELAUTHSCAN",getXpathValue(authObj,"AuthenticatedTarget/scan"));
        setValue("SELAUTHLIST",getXpathValue(authObj,"AuthenticatedTarget/list"));
        setValue("SELAUTHUSERFN",getXpathValue(authObj,"AuthenticatedTarget/userFunctions"));
        setValue("SELAUTHADMIN",getXpathValue(authObj,"adminAuthentication"));
        setValue("SELUSERAUTHACCTOFUNCT",getXpathValue(authObj,"functionwiseAuthentication"));
        setValue("SELAUTHEWB",getXpathValue(authObj,"AuthenticatedTarget/EWB"));
        setValue("SELPROJECTMGMT",getXpathValue(node,"Controller/Settings/AdminSystemSettings/Authentication/ProjectManagement")); //Project Code
        setValue("SELPROJECTJOB",getXpathValue(node,"Controller/Settings/AdminSystemSettings/Authentication/ProjectCodeEnforcement")); //Project Code

        var pinCodeAuthType = getXpathValue(authObj,"PINAuthentication/authenticationType")
        if(pinCodeAuthType == "NONE") {
            setValue("PINCODEAUTH","false");
            document.frmAuth.CARDREQPINCODE.checked = false;
            document.frmAuth.CARDREQPINCODE.disabled = true;
        } else if(pinCodeAuthType == "PIN") {
            setValue("PINCODEAUTH","true");
            document.frmAuth.CARDREQPINCODE.checked = false;
            document.frmAuth.CARDREQPINCODE.disabled = false;
        }  else if(pinCodeAuthType == "ID_GATE_PIN") {
            setValue("PINCODEAUTH","true");
            document.frmAuth.CARDREQPINCODE.checked = true;
            document.frmAuth.CARDREQPINCODE.disabled = false;
        }
        setValue("PINCODELEN",getXpathValue(node,"SecurityConfiguration/PINSettings/minCharacters"));
        // RCR_1766 START//AUTO REG CHECKBOX VALUE SAVE
	if(getXpathValue(authObj, "CardAuthentication/autoRegistrationForCardAuth") == "true"){
            document.frmAuth.USERINFOCREATE.checked = true;
		showDivs("cardAuthAutoReg_div");
        }else{
            document.frmAuth.USERINFOCREATE.checked = false;
		hideDivs("cardAuthAutoReg_div");
        }
        
        //SET CARD AUTH TYPE
        var cardAuthMode = getXpathValue(authObj, "CardAuthentication/reqExtUserAuthentication/authenticationType/authenticationName");
        if(cardAuthMode == "WindowsDomain"){
            setValue("CARDUSERAUTHTYPE","CardAuthWindowsDomain");
        }else if(cardAuthMode == "Ldap"){
            setValue("CARDUSERAUTHTYPE","CardAuthLdap")
        }

        //RCR_1766
        if(getXpathValue(authObj,"CardAuthentication/reqExtUserAuthentication/isEnable") == "true"){
            document.frmAuth.USERAUTHAUTOREG.checked = true;
            showDivs("CardAuthTypeSelDiv");
        }else{
            document.frmAuth.USERAUTHAUTOREG.checked = false;
            hideDivs("CardAuthTypeSelDiv");
        }
        // RCR_1766 END

        if(getValue("CARDAUTHENABLED") !="NotInstalled") {
            if(getXpathValue(node, "DevAuthMgmtPlugIn/DevAuthInfo/LocalAuthModeSwitchover") == "UserName")
                document.frmAuth.CARDIDUSERNAME.checked = true;
            else
                document.frmAuth.CARDIDUSERNAME.checked = false;
        }


        setValue("CARDIDSPLSRN",getXpathValue(authObj,"CardAuthentication/screenForCardAuthentication"));


        setValue("SELAUTOCHNGLGN",getXpathValue(node,"Authentication/AuthenticationSettings/CardAuthentication/allowCardAuthAlways"));

       //NFC

        setValue("SELMOBAUTH",getXpathValue(node,"Controller/Settings/AdminSystemSettings/NFCSettings/MobileAuthentication"));

        MOB_AUTH_Global=getValue("SELMOBAUTH");

        //setValue("SELCARDAUTH",getXpathValue(node,"DevAuthMgmtPlugIn/DevAuthSetting/CardAuthentication"));
        setValue("SELCARDAUTH",getXpathValue(node,"Controller/Settings/AdminSystemSettings/Authentication/CardAuthentication"));

        CARD_AUTH_Global=getValue("SELCARDAUTH");


        setValue("SELAUTOCHNG",getXpathValue(node,"Controller/Settings/AdminSystemSettings/NFCSettings/MobileOverwriteLogin"));

        MOBAUTH_AUTOCHANGE_Global=getValue("SELAUTOCHNG");

        setValue("NFCCARDREAD",getXpathValue(node,"MFP/System/IDGate/NFC/State"));

        setValue("NFCFUNCTION",getXpathValue(node,"Controller/Settings/AdminSystemSettings/NFCSettings/NfcFunction"));
        hideDivs("LDAPAuthentication_id");
        hideDivs("WindowsDomainAuthentication_id");
        hideDivs("RBASETTINGS");

	   setValue("ROLEAUTH",getXpathValue(node,"RBACManager/RbacSettings/rbacType"));

        ///hideDivs("LDAPAuthentication_id")
        var ntDomain = getXpathValue(authObj, "AuthenticationType/WindowsDomain/ntDomain");
        if(ntDomain == "true" || ntDomain == "1")
            document.frmAuth.NTServer.checked = true;
        else
            document.frmAuth.NTServer.checked = false;
        //RCR_1766 SET CARD AUTO REG NT DOMAIN
        var cardAuthNtDomain = getXpathValue(authObj, "CardAuthentication/reqExtUserAuthentication/authenticationType/WindowsDomain/ntDomain");
        if(cardAuthNtDomain == "true")
            document.frmAuth.CardAuthNTServer.checked = true;
        else
            document.frmAuth.CardAuthNTServer.checked = false;

        if(evaluateXpath(authObj, "AuthenticationType/WindowsDomain") != null) {
           /* for(var i=1;i<17;i++) {
                setValue("DOMAINNAME"+i, ((temp=getXpathValue(authObj, "AuthenticationType/WindowsDomain/Domain[@ID='"+i+"']/domainName")) == null || temp === undefined)? "": temp);
         		if(document.frmAuth.NTServer.checked == true) {
                    setValue("PDC"+i, ((temp=getXpathValue(authObj, "AuthenticationType/WindowsDomain/Domain[@ID='"+i+"']/pdc")) == null || temp === undefined)? "": temp);
                    setValue("BDC"+i, ((temp=getXpathValue(authObj, "AuthenticationType/WindowsDomain/Domain[@ID='"+i+"']/bdc")) == null || temp === undefined)? "": temp);
                }
            }*/
            var domainChecked = ((temp=getXpathValue(authObj, "AuthenticationType/WindowsDomain/primaryDomainID")) == null || temp === undefined)? "": (temp < 1 || temp > 16 )? "1" : temp;
            setValue("DOMAINCHECKED",domainChecked);
           // fnEnDisDomainRdBt();
        }

        fnUserAuthChange();
        setValue("TIMEOUTSEC",((temp=getXpathValue(authObj, "AuthenticationType/WindowsDomain/connectionTimeOut")) == null || temp === undefined)? "": temp);

        //Email Authentication
        //Email Authentication -> SMTP
        var frmObject = document.frmAuth;
        setValue("AUTHMETH",getXpathValue(node,"Controller/Settings/AdminSystemSettings/Authentication/Scan2Email/mode"));
        if(getXpathValue(node,"Controller/Settings/AdminSystemSettings/Authentication/Scan2Email/denyIFax") == "true")
            setValue("AUTHFAX","true");
        else
            setValue("AUTHFAX","false");
        setValue("SERVADDRESS",((temp = getXpathValue(node,"Protocols/SMTP/Client/server")) == "" || temp == null)? "" : temp);
        setValue("AUTH",((temp = getXpathValue(node,"Protocols/SMTP/Client/Authentication/desiredType")) == "" || temp == null)? "" : temp);

        if(getValue("SELUSERAUTH") == "1")
            setValue("AUTHMETH","None");

        //Email Address Setting
        //Acquire address of "From Address"
        setValue("SELFROMADDRESSSETTING",getXpathValue(node,"Controller/Settings/AdminSystemSettings/Authentication/FromField/FromAddressSetting"));

        setValue("STRFROMADR",((temp = getXpathValue(node,"Controller/Settings/AdminSystemSettings/EMail/fromAddr"))== "" || temp == null)?  "" : temp);
        setValue("DOMAINNAME",(getXpathValue(node,"Controller/Settings/AdminSystemSettings/Authentication/FromField/DomainName") == null )? "" : getXpathValue(node,"Controller/Settings/AdminSystemSettings/Authentication/FromField/DomainName"));

        setValue("LDAPDOMAINNAME",(getXpathValue(node,"Controller/Settings/AdminSystemSettings/Authentication/FromField/DomainName") == null)? "" :getXpathValue(node,"Controller/Settings/AdminSystemSettings/Authentication/FromField/DomainName"));

        //editable--->Do not allow "From" address to be edited in "Scan to Email" workflow
        if(getXpathValue(node,"Controller/Settings/AdminSystemSettings/Authentication/FromField/editable") == "true")
            frmObject.EDITFORBIT.checked = false;
        else
            frmObject.EDITFORBIT.checked = true;

        //Home Directory Service
        setValue("HOMEDIR",getXpathValue(node,"Authentication/AuthenticationSettings/UserProperties/isHomeDirectoryEnabled"));
        if(getXpathValue(node,"Authentication/AuthenticationSettings/UserProperties/propertiesServerTypeForUserAuth") == "LOCAL_SERVER" && getXpathValue(node,"Authentication/AuthenticationSettings/UserProperties/propertiesServerTypeForCardAuth") == "LOCAL_SERVER")
            frmObject.AUTH_SERVER_TYPE[0].checked = true;
        else if(getXpathValue(node,"Authentication/AuthenticationSettings/UserProperties/propertiesServerTypeForUserAuth") == "AUTH_SERVER" && getXpathValue(node,"Authentication/AuthenticationSettings/UserProperties/propertiesServerTypeForCardAuth") == "AUTH_SERVER")
            frmObject.AUTH_SERVER_TYPE[1].checked = true;
        else if(getXpathValue(node,"Authentication/AuthenticationSettings/UserProperties/propertiesServerTypeForUserAuth") == "AUTH_SERVER" && getXpathValue(node,"Authentication/AuthenticationSettings/UserProperties/propertiesServerTypeForCardAuth") == "SPECIFIED_SERVER")
            frmObject.AUTH_SERVER_TYPE[2].checked = true;
        else if(getXpathValue(node,"Authentication/AuthenticationSettings/UserProperties/propertiesServerTypeForUserAuth") == "SPECIFIED_SERVER" && getXpathValue(node,"Authentication/AuthenticationSettings/UserProperties/propertiesServerTypeForCardAuth") == "SPECIFIED_SERVER")
                frmObject.AUTH_SERVER_TYPE[3].checked = true;
        fnHomeDirOnChange();

        //Use From Address "Account Name" + E-mail Address "From Name"
        //From Name
        setValue("EMAILADDRSETFRMNAME",getXpathValue(node,"Controller/Settings/AdminSystemSettings/Authentication/FromField/FromName/selected"))
        //if(getValue("EMAILADDRSETFRMNAME") == "SearchLDAP") {
            fillLDAPdropdown(node);
        //}

        //Use From Address "Account Name" + E-mail Address "From Name"
        setValue("EMAILDESTRESTRICT",getXpathValue(node,"Controller/Settings/AdminSystemSettings/Authentication/EmailAddressEdit"));
        if(userAuthStatus == "1" && (Diag_3642_0_code == 1 || Diag_3642_0_code == "1"))
            document.frmAuth.SELUSERAUTHENFORCE.disabled = true;
        if(top.fnGetCookie("SECURITYLEVEL") == "HIGH") {
            document.frmAuth.SELUSERAUTH.disabled = true;
            document.frmAuth.USERGUEST.disabled = true;
            document.frmAuth.PWDAUTHPRINTJOB.disabled = true;
            document.frmAuth.SELAUTOCHNGLGN.disabled = true;
            document.frmAuth.SELAUTHCOPY.disabled = true;
            document.frmAuth.SELAUTHFAX.disabled = true;
            document.frmAuth.SELAUTHPRINT.disabled = true;
            document.frmAuth.SELAUTHSCAN.disabled = true;
            document.frmAuth.SELAUTHLIST.disabled = true;
            document.frmAuth.SELAUTHUSERFN.disabled = true;
        }


        // 08 code to decide whether to show/hide card issue time column in card auth table
        diagnostic_08_3644 = top.fnTrackCookie("DiagnosticMode","GET","3644");

        // L6.0 - fill parent table server assignments
		fillParentLdapAuthTable();
        fillParentCardAuthLdapAuthTable();//RCR_1766
        fillParentPinAuthTable();
        fillParentHomeDirAuthTable();
        fillParentCardAuthTable();
		fillParentWindowsDomainTable(windowsAuthListArray,"idWinDomainAuthTable");
		fillParentWindowsDomainTable(cardWindowsAuthListArray,"idCardAuthWinDomainAuthTable"); //RCR_1766
        fillParentEmailLdapAuthTable();

        parent.fraTitle.document.forms[0].Save.disabled = false;
        parent.fraTitle.document.forms[0].Reset.disabled = false;

        //MDS_Integrated_Mode_For_L6.0
        if(MDSMode=="true" || MDSMode=="CloudMDS")
        {
            var arr = ['SELDEPTCODE','SELUSERAUTH', 'SELUSERAUTHACCTOFUNCT','USERAUTHTYPE','SELAUTOCHNGLGN'];
            for ( var i = 0, l = arr.length; i < l; i++ ) {
               $('[name='+arr[i]+']').prop('disabled',true);
            }
        }
        var sharedUser = getXpathValue(authObj,"UserSyncSetting/SyncronizationType");
        setValue("SHAREDUSERSETTING",sharedUser);

        var con_TimeOutSec =((temp=getXpathValue(authObj,"UserSyncSetting/UserSyncTimeout")) == null) ? "" : temp;

		fnDisplayConTimeOut(sharedUser);
		setValue("CON_TIMEOUTSEC",con_TimeOutSec);
        if(userAuthStatus == "1" && userAuthMode == "Local")
        {
            if(sharedUser == "Master")
                top.gblIsMasterMFP = true;
            else if(sharedUser == "Slave")
                top.gblIsSlaveMFP = true;
            else{
                top.gblIsMasterMFP = false;
                top.gblIsSlaveMFP = false;
            }
        }else{
            top.gblIsMasterMFP = false;
            top.gblIsSlaveMFP = false;
        }

        var projectList = node.getElementsByTagName('ProjectCode');
        if( projectList.length == 1 ) {
           isProjectCodeRegistered =  false;
           document.frmAuth.SELPROJECTMGMT.disabled = true;
           document.frmAuth.SELPROJECTJOB.disabled = true;
        }

    } catch(e){errHandler(e,'fnFillAuthenticationSettings()','Authentication.html?v=1717026199ta',"");}
}

function fnDisplayConTimeOut(value) {
    if(value == "" || value == null)
	    return;
    if(value!= "Disable"){
          showDivs("CON_TIMEOUT_ID");
        }
    else
        hideDivs("CON_TIMEOUT_ID");
    setValue("CON_TIMEOUTSEC","30");
    if(value == "Slave"){
        document.frmAuth.USERINFOCREATE.disabled = true;
        document.frmAuth.USERINFOCREATE.checked = false;
        hideDivs("cardAuthAutoReg_div"); //RCR_1766
    }
    else{
        if (getValue("SELUSERAUTH") == 0) {
            document.frmAuth.USERINFOCREATE.disabled = true;
        }
        else {
            document.frmAuth.USERINFOCREATE.disabled = false;
        }
    }
}
//Code to fill LDAP Table using jquery templating lblLdapUserName
function fillParentLdapAuthTable(){
    var JSONFormatLDAPString = "";
    JSONFormatLDAPString = convertArrayToJSON(assignedLDAPServerList);
    JSONFormatLDAPString = jQuery.parseJSON(JSONFormatLDAPString);
    for(i in JSONFormatLDAPString) {
        for (j in JSONFormatLDAPString[i]){
            JSONFormatLDAPString[i][j] = fnnConvertHtmlEntity(JSONFormatLDAPString[i][j]);
        }
        if($.trim(JSONFormatLDAPString[i][1]) == ""){
            if(JSONFormatLDAPString[i][1].indexOf('　') == -1)
                JSONFormatLDAPString[i][1] = "&nbsp;";
        }
    }
	var LdapTableSettings = {
        "caption": '',
        "tabHeader": ['',lblLdapServer,lblServerIPAddress,'',lblType,lblLdapUserName,''],
        //"tabData": jQuery.parseJSON(JSONFormatLDAPString),
        "tabData": JSONFormatLDAPString,
		//"columnWidth": ['0%','15%','18%','0%','20%', '20%','0%'],
        "columnWidth": ['0px','213px','213px','0px','213px', '213px','0px'],
		"tableWidth" : 880,
		"tableHeight" : '80px',
		"headerTableID" : 'ldapAuthTableHeader',
		"dataTableID" : 'ldapAuthTable'
    };
	renderTableFromTemplate("idLDAPAuthTable",LdapTableSettings);
}
// RCR_1766 Code to fill cARD AUTH LDAP Table using jquery templating lblLdapUserName
function fillParentCardAuthLdapAuthTable(){
    var JSONFormatLDAPString = "";
    JSONFormatLDAPString = convertArrayToJSON(assignedCardAuthLDAPServerList);
    JSONFormatLDAPString = jQuery.parseJSON(JSONFormatLDAPString);
    for(i in JSONFormatLDAPString) {
        for (j in JSONFormatLDAPString[i]){
            JSONFormatLDAPString[i][j] = fnnConvertHtmlEntity(JSONFormatLDAPString[i][j]);
        }
        if($.trim(JSONFormatLDAPString[i][1]) == ""){
            if(JSONFormatLDAPString[i][1].indexOf('　') == -1)
                JSONFormatLDAPString[i][1] = "&nbsp;";
        }
    }
	var LdapTableSettings = {
        "caption": '',
        "tabHeader": ['',lblLdapServer,lblServerIPAddress,'',lblType,lblLdapUserName,''],
        //"tabData": jQuery.parseJSON(JSONFormatLDAPString),
        "tabData": JSONFormatLDAPString,
		//"columnWidth": ['0%','15%','18%','0%','20%', '20%','0%'],
        "columnWidth": ['0px','213px','213px','0px','213px', '213px','0px'],
		"tableWidth" : 880,
		"tableHeight" : '80px',
		"headerTableID" : 'cardAuthLdapAuthTableHeader',
		"dataTableID" : 'cardLdapAuthTable'
    };
	renderTableFromTemplate("idCardAuthLDAPAuthTable",LdapTableSettings);
}
//Code to fill Email LDAP Table using jquery templating lblLdapUserName
function fillParentEmailLdapAuthTable(){
    var JSONFormatLDAPString = "";
    JSONFormatLDAPString = convertArrayToJSON(assignedEmailLDAPServerList);
    JSONFormatLDAPString = jQuery.parseJSON(JSONFormatLDAPString);
    for(i in JSONFormatLDAPString) {
        for (j in JSONFormatLDAPString[i]){
            JSONFormatLDAPString[i][j] = fnnConvertHtmlEntity(JSONFormatLDAPString[i][j]);
        }
        if($.trim(JSONFormatLDAPString[i][1]) == ""){
            if(JSONFormatLDAPString[i][1].indexOf('　') == -1)
                JSONFormatLDAPString[i][1] = "&nbsp;";
        }
    }
    var EmailLdapTableSettings = {
        "caption": '',
        "tabHeader": ['',lblLdapServer,lblServerIPAddress,lblLdapUserName,lblLdapFromAddr,lblLdapFromName,''],
        //"tabData": jQuery.parseJSON(JSONFormatLDAPString),
        "tabData": JSONFormatLDAPString,
		"columnWidth": ['0px','163px','163px','163px', '163px','163px','0px'],
		"tableWidth" : 850,
		"tableHeight" : '65px',
		"headerTableID" : 'EmailldapAuthTableHeader',
		"dataTableID" : 'EmailldapAuthTable'
    };
    renderTableFromTemplate("idEmailLDAPAuthTable",EmailLdapTableSettings);
}

//Code to fill Pin Auth Table using jquery templating
function fillParentPinAuthTable(){
    var JSONFormatPinAuthString = "";
    JSONFormatPinAuthString = convertArrayToJSON(assignedPinServerList);
    JSONFormatPinAuthString = jQuery.parseJSON(JSONFormatPinAuthString);
    for(i in JSONFormatPinAuthString) {
        for (j in JSONFormatPinAuthString[i]){
            JSONFormatPinAuthString[i][j] = fnnConvertHtmlEntity(JSONFormatPinAuthString[i][j]);
        }
        if($.trim(JSONFormatPinAuthString[i][1]) == ""){
            if(JSONFormatPinAuthString[i][1].indexOf('　') == -1)
                JSONFormatPinAuthString[i][1] = "&nbsp;";
        }
    }
    
	var PinTableSettings = {
        "caption": "",
//DCR: EBX_DCR_53951
//[L6.11]TopAccess PIN/Card Authentication Multi Domain Support
        "tabHeader": ['',lblLdapServer,lblServerIPAddress,lblType,lblLdapUserName,lblAttrTypePin,''],
        //"tabData": jQuery.parseJSON(JSONFormatPinAuthString),
        "tabData": JSONFormatPinAuthString,
		"columnWidth": ['0px','213px','213px','213px','213px', '213px', '0px'],
		"tableWidth" : 1100,
		"tableHeight" : '80px',
		"headerTableID" : 'pinAuthTableHeader',
		"dataTableID" : 'pinAuthTable'
    };
	renderTableFromTemplate("idPinAuthTable",PinTableSettings);
}

//Code to fill Home Dir Auth Table using jquery templating
function fillParentHomeDirAuthTable(){
    var JSONFormatHomeDirAuthString = "";
    JSONFormatHomeDirAuthString = convertArrayToJSON(assignedHomeDirServerList);
    JSONFormatHomeDirAuthString = jQuery.parseJSON(JSONFormatHomeDirAuthString);
    for(i in JSONFormatHomeDirAuthString) {
        for (j in JSONFormatHomeDirAuthString[i]){
            JSONFormatHomeDirAuthString[i][j] = fnnConvertHtmlEntity(JSONFormatHomeDirAuthString[i][j]);
        }
        if($.trim(JSONFormatHomeDirAuthString[i][1]) == ""){
            if(JSONFormatHomeDirAuthString[i][1].indexOf('　') == -1)
                JSONFormatHomeDirAuthString[i][1] = "&nbsp;";
        }
    }
    
	var HomeDirTableSettings = {
        "caption": "",
        "tabHeader": ['',lblLdapServer,lblServerIPAddress,''],
        //"tabData": jQuery.parseJSON(JSONFormatHomeDirAuthString),
        "tabData": JSONFormatHomeDirAuthString,
		"columnWidth": ['0%','213px','213px','0%'],
		"tableWidth" : 440,
		"tableHeight" : '48px',
		"headerTableID" : 'homeDirAuthTableHeader',
		"dataTableID" : 'homeDirAuthTable'
    };
	renderTableFromTemplate("idHomeDirAuthTable",HomeDirTableSettings);
}

//Code to fill Card Auth Table using jquery templating
function fillParentCardAuthTable(){
    var JSONFormatCardAuthString = "";
    JSONFormatCardAuthString = convertArrayToJSON(assignedCardServerList);
    JSONFormatCardAuthString = jQuery.parseJSON(JSONFormatCardAuthString);
    for(i in JSONFormatCardAuthString) {
        for (j in JSONFormatCardAuthString[i]){
            JSONFormatCardAuthString[i][j] = fnnConvertHtmlEntity(JSONFormatCardAuthString[i][j]);
        }
        if($.trim(JSONFormatCardAuthString[i][1]) == ""){
            if(JSONFormatCardAuthString[i][1].indexOf('　') == -1)
                JSONFormatCardAuthString[i][1] = "&nbsp;";
        }
    }

    var tabHeaderClassArray = "";
    var dataColumnClassArray ="";
    var headerColumnClassArray ="";
    var innerDivStyleClass="";
    var headerTableStyleClass="";

	var tableHeaderArray = [];
    var columnWidth = [];
	var tableWidth = 0;
	if(diagnostic_08_3644=="1"){ // show Card Issue Time Column
        tableHeaderArray = ['',lblLdapServer,lblServerIPAddress,lblType,lblLdapUserName,lblAttrTypeCardInfo,lblAttrTypeCardIssueTime,'']; //DCR: EBX_DCR_53951: PIN/Card Authentication
		columnWidth = ['0%','213px','213px','213px','213px','213px','213px', '0%'];
		tableWidth = 1320;
		$("#cardAuthSettingButtonsDiv").css("width", tableWidth+"px");
    }else{ // hide Card Issue Time Column

		tableHeaderArray = ['',lblLdapServer,lblServerIPAddress,lblType,lblLdapUserName,lblAttrTypeCardInfo,'','']; //DCR: EBX_DCR_53951: PIN/Card Authentication
		columnWidth = ['0%','213px','213px','213px','213px','213px','0%','0%'];
		tableWidth = 1100;
		$("#cardAuthSettingButtonsDiv").css("width", tableWidth+"px");
    }
	var CardAuthTableSettings = {
        "caption": "",
        "tabHeader": tableHeaderArray,
        //"tabData": jQuery.parseJSON(JSONFormatCardAuthString),
        "tabData": JSONFormatCardAuthString,
		"columnWidth": columnWidth,
		"tableWidth" : tableWidth,
		"tableHeight" : '80px',
		"headerTableID" : 'cardAuthTableHeader',
		"dataTableID" : 'cardAuthTable'
    };
	renderTableFromTemplate("idCardAuthTable",CardAuthTableSettings);
}

//Code to fill Windows Domain Table using jquery templating
function fillParentWindowsDomainTable(listArray,tableName){
    var JSONFormatWindowsDomainString = "";
   
    JSONFormatWindowsDomainString = convertArrayToJSON(listArray);
    JSONFormatWindowsDomainString = jQuery.parseJSON(JSONFormatWindowsDomainString);
    for(i in JSONFormatWindowsDomainString) {
        for (j in JSONFormatWindowsDomainString[i]){
            JSONFormatWindowsDomainString[i][j] = fnnConvertHtmlEntity(listArray[i][j]);
        }
    }
    /*
    var tmpArr = [];
    for(var i=0;i<windowsAuthListArray.length;i++){
        tmpArr[i] = new Array();
        for(var j=0;j<windowsAuthListArray[i].length;j++){
            tmpArr[i][j] = fnConvertXMLEntities(windowsAuthListArray[i][j]);
        }
    }
    JSONFormatWindowsDomainString = convertArrayToJSON(tmpArr);
    */

    var WindowsDomainTableSettings = {
        "caption": "",
        "tabHeader": [lblDomainName,strPDC,strBDC],
        //"tabData": jQuery.parseJSON(JSONFormatWindowsDomainString),
        "tabData": JSONFormatWindowsDomainString,
		"columnWidth": ['213px','213px','213px'],
		"tableWidth" : 660,
		"tableHeight" : '80px',
		"headerTableID" : 'WinDomainAuthTableHeader',
		"dataTableID" : 'WinDomainAuthTable'
    };
    //RCR_1766
    var cardAuthWindowsDomainTableSettings = {
        "caption": "",
        "tabHeader": [lblDomainName,strPDC,strBDC],
        //"tabData": jQuery.parseJSON(JSONFormatWindowsDomainString),
        "tabData": JSONFormatWindowsDomainString,
		"columnWidth": ['213px','213px','213px'],
		"tableWidth" : 660,
		"tableHeight" : '80px',
		"headerTableID" : 'cardAuthWinDomainTableHeader',
		"dataTableID" : 'cardWinDomainAuthTable'
    };
    //RCR_1766
    if(tableName == "idWinDomainAuthTable"){
        renderTableFromTemplate("idWinDomainAuthTable",WindowsDomainTableSettings);
    }else if(tableName == "idCardAuthWinDomainAuthTable"){
        renderTableFromTemplate("idCardAuthWinDomainAuthTable",cardAuthWindowsDomainTableSettings); 
    }
    
	if(listArray.length >=16){
        if(tableName == "idWinDomainAuthTable"){
            $("#btnWinDomainAdd").attr('disabled','disabled');
        }else{
            $("#btnCardAuthWinDomainAdd").attr('disabled','disabled');
        }
	}
}

//24-SEP-2015 - When saving Ldap/Pin/Card/Home Auth list the empty rows should be omitted
function fnRemoveEmptyRows(rowsArray){
    var newRowList = new Array();
    for(var i=0;i<rowsArray.length;i++){
        // rowsArray[i][1] is server name. it will never come as empty. If it is empty means row is empty
        if(rowsArray[i][1] != "" && rowsArray[i][1] != "&nbsp;" ){
            newRowList.push(rowsArray[i]);
        }
    }
    return newRowList;
}

// Construct LDAP Auth Setting XML while save
function constructLDAPSettingXML(originalServerList,assignedServerList){
    var settingXML = "";
    var ldapIDCount = 1;
	var windowsServer = fnGetLocaleString("102028","Windows Server");

    //convert &nbsp; to empty
    /*for(var x=0;x<assignedServerList.length;x++){
        for(var y=0;y<assignedServerList[x].length;y++){
            if(assignedServerList[x][y]=='&nbsp;'){
                assignedServerList[x][y]='';
            }
        }
    }*/

    //All this below checks are made because we should not form the request XML if particular row is not modified
    if(assignedServerList.length >= originalServerList.length){
        for(var i=0; i<originalServerList.length; i++) {
            if(originalServerList[i][0]!=assignedServerList[i][0]){
                settingXML += "<Ldap ID='"+ ldapIDCount +"'>";
                settingXML += "<ldapServerID>"+fnConvertXMLEntities(assignedServerList[i][0])+"</ldapServerID>";
                var isWindowsServer = "false";
				if(assignedServerList[i][4] === windowsServer){
				   isWindowsServer = "true";
				}
                settingXML += "<isWindowsServer>"+fnConvertXMLEntities(isWindowsServer)+"</isWindowsServer>";
                settingXML += "<attrNameForUser>"+fnConvertXMLEntities(assignedServerList[i][5])+"</attrNameForUser>";
                settingXML += "</Ldap>";
            }
            ldapIDCount++;
        }
        if(assignedServerList.length > originalServerList.length  ){
            for(i=originalServerList.length;i<assignedServerList.length;i++){
                settingXML += "<Ldap ID='"+ ldapIDCount +"'>";
                settingXML += "<ldapServerID>"+fnConvertXMLEntities(assignedServerList[i][0])+"</ldapServerID>";
				var isWindowsServer = "false";
				if(assignedServerList[i][4] === windowsServer){
				   isWindowsServer = "true";
				}
                settingXML += "<isWindowsServer>"+fnConvertXMLEntities(isWindowsServer)+"</isWindowsServer>";
                settingXML += "<attrNameForUser>"+fnConvertXMLEntities(assignedServerList[i][5])+"</attrNameForUser>";
                settingXML += "</Ldap>";
                ldapIDCount++;
            }
        }
    }else if(assignedServerList.length < originalServerList.length){
        for(var i=0; i<assignedServerList.length; i++) {
            if(assignedServerList[i][0]!=originalServerList[i][0]){
                settingXML += "<Ldap ID='"+ ldapIDCount +"'>";
                settingXML += "<ldapServerID>"+fnConvertXMLEntities(assignedServerList[i][0])+"</ldapServerID>";
				var isWindowsServer = "false";
				if(assignedServerList[i][4] === windowsServer){
				   isWindowsServer = "true";
				}
                settingXML += "<isWindowsServer>"+fnConvertXMLEntities(isWindowsServer)+"</isWindowsServer>";
                settingXML += "<attrNameForUser>"+fnConvertXMLEntities(assignedServerList[i][5])+"</attrNameForUser>";
                settingXML += "</Ldap>";
            }
            ldapIDCount++;
        }
        //fill empty for deleted ldap list
        for(i=assignedServerList.length;i<16;i++){
            settingXML += "<Ldap ID='"+ ldapIDCount +"'>";
            settingXML += "<ldapServerID></ldapServerID>";
            settingXML += "<isWindowsServer></isWindowsServer>";
            settingXML += "<attrNameForUser></attrNameForUser>";
            settingXML += "</Ldap>";
            ldapIDCount++;
        }

    }
    return settingXML;
}


// Construct Email LDAP Auth Setting XML while save
function constructEmailLDAPSettingXML(originalServerList,assignedServerList){

    var settingXML = "";
    var ldapIDCount = 1;

    //convert &nbsp; to empty
    /*for(var x=0;x<assignedServerList.length;x++){
        for(var y=0;y<assignedServerList[x].length;y++){
            if(assignedServerList[x][y]=='&nbsp;'){
                assignedServerList[x][y]='';
            }
        }
    }*/

    //All this below checks are made because we should not form the request XML if particular row is not modified
    if(assignedServerList.length >= originalServerList.length){
        for(var i=0; i<originalServerList.length; i++) {
            if(originalServerList[i][0]!=assignedServerList[i][0]){
                if(ldapIDCount==1)
                    settingXML="<LDAPServer>"+fnConvertXMLEntities(assignedServerList[i][1])+"</LDAPServer>";
                else
                    settingXML=settingXML+"<LDAPServer"+ldapIDCount+">"+fnConvertXMLEntities(assignedServerList[i][1])+"</LDAPServer"+ldapIDCount+">";
            }
            ldapIDCount++;
        }
        if(assignedServerList.length > originalServerList.length  ){
            for(i=originalServerList.length;i<assignedServerList.length;i++){
                if(ldapIDCount==1)
                    settingXML="<LDAPServer>"+fnConvertXMLEntities(assignedServerList[i][1])+"</LDAPServer>";
                else
                    settingXML=settingXML+"<LDAPServer"+ldapIDCount+">"+fnConvertXMLEntities(assignedServerList[i][1])+"</LDAPServer"+ldapIDCount+">";
                ldapIDCount++;
            }
        }
    }else if(assignedServerList.length < originalServerList.length){
        for(var i=0; i<assignedServerList.length; i++) {
            if(assignedServerList[i][0]!=originalServerList[i][0]){
                if(ldapIDCount==1)
                    settingXML="<LDAPServer>"+fnConvertXMLEntities(assignedServerList[i][1])+"</LDAPServer>";
                else
                    settingXML=settingXML+"<LDAPServer"+ldapIDCount+">"+fnConvertXMLEntities(assignedServerList[i][1])+"</LDAPServer"+ldapIDCount+">";
            }
            ldapIDCount++;
        }
        //fill empty for deleted ldap list
        for(i=assignedServerList.length;i<4;i++){
            if(ldapIDCount==1)
                settingXML="<LDAPServer></LDAPServer>";
            else
                settingXML=settingXML+"<LDAPServer"+ldapIDCount+"></LDAPServer"+ldapIDCount+">";
            ldapIDCount++;
        }
    }
    return settingXML;
}

// Construct Pin Auth Setting XML while save
function constructPinAuthSettingXML(originalServerList,assignedServerList){
    var settingXML = "";
    var pinIDCount = 1;
	var windowsServer = fnGetLocaleString("102028","Windows Server");

    //convert &nbsp; to empty
    /*for(var x=0;x<assignedServerList.length;x++){
        for(var y=0;y<assignedServerList[x].length;y++){
            if(assignedServerList[x][y]=='&nbsp;'){
                assignedServerList[x][y]='';
            }
        }
    }*/

    if(assignedServerList.length >= originalServerList.length){
        for(var i=0; i<originalServerList.length; i++) {
            if(originalServerList[i][0]!=assignedServerList[i][0]){
                settingXML += "<Server ID='"+ pinIDCount +"'>";
                settingXML += "<serverIDForPIN>"+fnConvertXMLEntities(assignedServerList[i][0])+"</serverIDForPIN>";
				var isWindowsServer = "false";
				if(assignedServerList[i][3] === windowsServer){
				   isWindowsServer = "true";
				}
                settingXML += "<isWindowsServer>"+fnConvertXMLEntities(isWindowsServer)+"</isWindowsServer>";
                settingXML += "<attrNameForUser>"+fnConvertXMLEntities(assignedServerList[i][4])+"</attrNameForUser>";
                settingXML += "<attrNameForPIN>"+fnConvertXMLEntities(assignedServerList[i][5])+"</attrNameForPIN>";
                settingXML += "<attrNameForServerName>"+fnConvertXMLEntities(assignedServerList[i][6])+"</attrNameForServerName>"; //DCR: EBX_DCR_53951: PIN/Card Authentication
                settingXML += "</Server>";
            }
            pinIDCount++;
        }
        if(assignedServerList.length > originalServerList.length  ){
            for(i=originalServerList.length;i<assignedServerList.length;i++){
                settingXML += "<Server ID='"+ pinIDCount +"'>";
                settingXML += "<serverIDForPIN>"+fnConvertXMLEntities(assignedServerList[i][0])+"</serverIDForPIN>";
				var isWindowsServer = "false";
				if(assignedServerList[i][3] === windowsServer){
				   isWindowsServer = "true";
				}
                settingXML += "<isWindowsServer>"+fnConvertXMLEntities(isWindowsServer)+"</isWindowsServer>";
                settingXML += "<attrNameForUser>"+fnConvertXMLEntities(assignedServerList[i][4])+"</attrNameForUser>";
                settingXML += "<attrNameForPIN>"+fnConvertXMLEntities(assignedServerList[i][5])+"</attrNameForPIN>";
                settingXML += "<attrNameForServerName>"+fnConvertXMLEntities(assignedServerList[i][6])+"</attrNameForServerName>"; //DCR: EBX_DCR_53951: PIN/Card Authentication
                settingXML += "</Server>";
                pinIDCount++;
            }
        }
    }else if(assignedServerList.length < originalServerList.length){
        for(var i=0; i<assignedServerList.length; i++) {
            if(assignedServerList[i][0]!=originalServerList[i][0]){
                settingXML += "<Server ID='"+ pinIDCount +"'>";
                settingXML += "<serverIDForPIN>"+fnConvertXMLEntities(assignedServerList[i][0])+"</serverIDForPIN>";
				var isWindowsServer = "false";
				if(assignedServerList[i][3] === windowsServer){
				   isWindowsServer = "true";
				}
                settingXML += "<isWindowsServer>"+fnConvertXMLEntities(isWindowsServer)+"</isWindowsServer>";
                settingXML += "<attrNameForUser>"+fnConvertXMLEntities(assignedServerList[i][4])+"</attrNameForUser>";
                settingXML += "<attrNameForPIN>"+fnConvertXMLEntities(assignedServerList[i][5])+"</attrNameForPIN>";
                settingXML += "<attrNameForServerName>"+fnConvertXMLEntities(assignedServerList[i][6])+"</attrNameForServerName>"; //DCR: EBX_DCR_53951: PIN/Card Authentication
                settingXML += "</Server>";
            }
            pinIDCount++;
        }
        //fill empty for deleted pin list
        for(i=assignedServerList.length;i<3;i++){
            settingXML += "<Server ID='"+ pinIDCount +"'>";
            settingXML += "<serverIDForPIN></serverIDForPIN>";
            settingXML += "<isWindowsServer></isWindowsServer>";
            settingXML += "<attrNameForUser></attrNameForUser>";
            settingXML += "<attrNameForPIN></attrNameForPIN>";
            settingXML += "<attrNameForServerName></attrNameForServerName>"; //DCR: EBX_DCR_53951: PIN/Card Authentication
            settingXML += "</Server>";
            pinIDCount++;
        }

    }
    return settingXML;
}

// Construct Home Directory Auth Setting XML while save
function constructHomeDirAuthSettingXML(originalServerList,assignedServerList){
    var settingXML = "";
    var homeDirIDCount = 1;
    //convert &nbsp; to empty
    /*for(var x=0;x<assignedServerList.length;x++){
        for(var y=0;y<assignedServerList[x].length;y++){
            if(assignedServerList[x][y]=='&nbsp;'){
                assignedServerList[x][y]='';
            }
        }
    }*/

	if(assignedServerList.length >= originalServerList.length){
        for(var i=0; i<originalServerList.length; i++) {
            if(originalServerList[i][0]!=assignedServerList[i][0]){
                settingXML += "<userPropertiesServer ID='"+ homeDirIDCount +"'>";
                settingXML += fnConvertXMLEntities(assignedServerList[i][0]);
                settingXML += "</userPropertiesServer>";
            }
            homeDirIDCount++;
        }
        if(assignedServerList.length > originalServerList.length  ){
            for(i=originalServerList.length;i<assignedServerList.length;i++){
                settingXML += "<userPropertiesServer ID='"+ homeDirIDCount +"'>";
                settingXML += fnConvertXMLEntities(assignedServerList[i][0]);
                settingXML += "</userPropertiesServer>";
                homeDirIDCount++;
            }
        }
    }else if(assignedServerList.length < originalServerList.length){
        for(var i=0; i<assignedServerList.length; i++) {
            if(assignedServerList[i][0]!=originalServerList[i][0]){
                settingXML += "<userPropertiesServer ID='"+ homeDirIDCount +"'>";
                settingXML += fnConvertXMLEntities(assignedServerList[i][0]);
                settingXML += "</userPropertiesServer>";
            }
            homeDirIDCount++;
        }
        //fill empty for deleted Home Directory list
        for(i=assignedServerList.length;i<3;i++){
            settingXML += "<userPropertiesServer ID='"+ homeDirIDCount +"'>";
            settingXML += ""; // add zero in future if req changes
            settingXML += "</userPropertiesServer>";
            homeDirIDCount++;
        }

    }
    return settingXML;
}

// Construct Card Auth Setting XML while save
function constructCardAuthSettingXML(originalServerList,assignedServerList){
    var settingXML = "";
    var cardIDCount = 1;
	var windowsServer = fnGetLocaleString("102028","Windows Server");

    //convert &nbsp; to empty
    /*for(var x=0;x<assignedServerList.length;x++){
        for(var y=0;y<assignedServerList[x].length;y++){
            if(assignedServerList[x][y]=='&nbsp;'){
                assignedServerList[x][y]='';
            }
        }
    }*/

    if(assignedServerList.length >= originalServerList.length){
        for(var i=0; i<originalServerList.length; i++) {
            if(originalServerList[i][0]!=assignedServerList[i][0]){
                settingXML += "<cardServerID ID='"+ cardIDCount +"'>"+fnConvertXMLEntities(assignedServerList[i][0])+"</cardServerID>";
				var isWindowsServer = "false";
				if(assignedServerList[i][3] === windowsServer){
				   isWindowsServer = "true";
				}
                settingXML += "<isWindowsServer ID='"+ cardIDCount +"'>"+fnConvertXMLEntities(isWindowsServer)+"</isWindowsServer>";
                settingXML += "<attrNameForUser ID='"+ cardIDCount +"'>"+fnConvertXMLEntities(assignedServerList[i][4])+"</attrNameForUser>";
                settingXML += "<atrNameForCardID ID='"+ cardIDCount +"'>"+fnConvertXMLEntities(assignedServerList[i][5])+"</atrNameForCardID>";
                settingXML += "<attrNameForCardRevision ID='"+ cardIDCount +"'>"+fnConvertXMLEntities(assignedServerList[i][6])+"</attrNameForCardRevision>";
                settingXML += "<attrNameForServerName ID='"+ cardIDCount +"'>"+fnConvertXMLEntities(assignedServerList[i][7])+"</attrNameForServerName>"; //DCR: EBX_DCR_53951: PIN/Card Authentication
            }
            cardIDCount++;
        }
        if(assignedServerList.length > originalServerList.length  ){
            for(i=originalServerList.length;i<assignedServerList.length;i++){
                settingXML += "<cardServerID ID='"+ cardIDCount +"'>"+fnConvertXMLEntities(assignedServerList[i][0])+"</cardServerID>";
				var isWindowsServer = "false";
				if(assignedServerList[i][3] === windowsServer){
				   isWindowsServer = "true";
				}
                settingXML += "<isWindowsServer ID='"+ cardIDCount +"'>"+fnConvertXMLEntities(isWindowsServer)+"</isWindowsServer>";
                settingXML += "<attrNameForUser ID='"+ cardIDCount +"'>"+fnConvertXMLEntities(assignedServerList[i][4])+"</attrNameForUser>";
                settingXML += "<atrNameForCardID ID='"+ cardIDCount +"'>"+fnConvertXMLEntities(assignedServerList[i][5])+"</atrNameForCardID>";
                settingXML += "<attrNameForCardRevision ID='"+ cardIDCount +"'>"+fnConvertXMLEntities(assignedServerList[i][6])+"</attrNameForCardRevision>";
                settingXML += "<attrNameForServerName ID='"+ cardIDCount +"'>"+fnConvertXMLEntities(assignedServerList[i][7])+"</attrNameForServerName>"; //DCR: EBX_DCR_53951: PIN/Card Authentication
                cardIDCount++;
            }
        }
    }else if(assignedServerList.length < originalServerList.length){
        for(var i=0; i<assignedServerList.length; i++) {
            if(assignedServerList[i][0]!=originalServerList[i][0]){
                settingXML += "<cardServerID ID='"+ cardIDCount +"'>"+fnConvertXMLEntities(assignedServerList[i][0])+"</cardServerID>";
				var isWindowsServer = "false";
				if(assignedServerList[i][3] === windowsServer){
				   isWindowsServer = "true";
				}
                settingXML += "<isWindowsServer ID='"+ cardIDCount +"'>"+fnConvertXMLEntities(isWindowsServer)+"</isWindowsServer>";
                settingXML += "<attrNameForUser ID='"+ cardIDCount +"'>"+fnConvertXMLEntities(assignedServerList[i][4])+"</attrNameForUser>";
                settingXML += "<atrNameForCardID ID='"+ cardIDCount +"'>"+fnConvertXMLEntities(assignedServerList[i][5])+"</atrNameForCardID>";
                settingXML += "<attrNameForCardRevision ID='"+ cardIDCount +"'>"+fnConvertXMLEntities(assignedServerList[i][6])+"</attrNameForCardRevision>";
                settingXML += "<attrNameForServerName ID='"+ cardIDCount +"'>"+fnConvertXMLEntities(assignedServerList[i][7])+"</attrNameForServerName>"; //DCR: EBX_DCR_53951: PIN/Card Authentication
            }
            cardIDCount++;
        }
        //fill empty for deleted card list
        for(i=assignedServerList.length;i<16;i++){
            settingXML += "<cardServerID ID='"+ cardIDCount +"'></cardServerID>";
            settingXML += "<isWindowsServer ID='"+ cardIDCount +"'></isWindowsServer>";
            settingXML += "<attrNameForUser ID='"+ cardIDCount +"'></attrNameForUser>";
            settingXML += "<atrNameForCardID ID='"+ cardIDCount +"'></atrNameForCardID>";
            settingXML += "<attrNameForCardRevision ID='"+ cardIDCount +"'></attrNameForCardRevision>";
            settingXML += "<attrNameForServerName ID='"+ cardIDCount +"'></attrNameForServerName>"; //DCR: EBX_DCR_53951: PIN/Card Authentication
            cardIDCount++;
        }

    }
    return settingXML;
}
function decodeEntities(encodedString) {
    var textArea = document.createElement('textarea');
    textArea.innerHTML = encodedString;
    return textArea.value;
}
// Construct Windows Domain Setting XML while save
function constructWindowsDomainSettingXML(originalServerList,assignedServerList,serverName){
    var settingXML = "";
    var WinDomainIDCount = 1;
    var NTServer='';
    //RCR_1766
   if(serverName == "NTServer"){
    NTServer = document.frmAuth.NTServer.checked;
   }else{
    NTServer = document.frmAuth.CardAuthNTServer.checked;
   }
    //convert &nbsp; to empty
    //In order to be able to register '&nbsp;'
    /*for(var x=0;x<assignedServerList.length;x++){
        for(var y=0;y<assignedServerList[x].length;y++){
            if(assignedServerList[x][y]=='&nbsp;'){
                assignedServerList[x][y]='';
            }
        }
    }*/

    for(var i=0;i<assignedServerList.length;i++){
        //do not add empty rows
        if(assignedServerList[i][0]!='') {
            settingXML += "<Domain ID='" + WinDomainIDCount + "'>";
            settingXML += "<domainName>" + fnConvertXMLEntities(assignedServerList[i][0]) + "</domainName>";
            if (NTServer) {
                settingXML += "<pdc>" + fnConvertXMLEntities(assignedServerList[i][1]) + "</pdc>";
                settingXML += "<bdc>" + fnConvertXMLEntities(assignedServerList[i][2]) + "</bdc>";
            }
            settingXML += "</Domain>";
            WinDomainIDCount++;
        }
    }
    for(var i=WinDomainIDCount;i<=16;i++){
        settingXML += "<Domain ID='"+ WinDomainIDCount +"'>";
        settingXML += "<domainName></domainName>";
        if(NTServer){
            settingXML += "<pdc></pdc>";
            settingXML += "<bdc></bdc>";
        }
        settingXML += "</Domain>";
        WinDomainIDCount++;
    }
    return settingXML;
}

function fnSaveAuthenticationSettings() {
    try {
        gblSETRequestXMLArray = null;
        //Dept settings
        var frmObject = document.frmAuth;
        var deptSettingXml = "<deptManagement>"+getValue("SELDEPTCODE")+"</deptManagement><NeedDeptInfo>"+getValue("SELDEPTNUMREQ")+"</NeedDeptInfo><deptCodeEnforcement>"+getValue("SELDEPTENFORCE")+"</deptCodeEnforcement><DeptCounterSetting><Copy>"+getValue("SELDEPTMGMTCOPY")+"</Copy><FAX>"+getValue("SELDEPTMGMTFAX")+"</FAX><Print>"+getValue("SELDEPTMGMTPRINT")+"</Print><Scan>"+getValue("SELDEPTMGMTSCAN")+"</Scan><List>"+getValue("SELDEPTMGMTLIST")+"</List><UserFunctions>"+getValue("SELDEPTMGMTUSERFN")+"</UserFunctions><EWB>"+getValue("SELDEPTMGMTEWB")+"</EWB></DeptCounterSetting>";
        var userMgmtSetting = "";
        var roleBasedAccessSettingXML = "";
        var scanToEmailXml = "";
        var networkAmailAuthSMTPXML = "";
        var LDAPAuthSettingXml = "";
        var cardLDAPAuthSettingXml=""; //RCR_1766
        var denyIFaxXML = "";
        var devAuthMgmtXML = "";
        var cardAuthLDAPXML = "";
        var pinCodeAuthXML = "";
        var userMgmtSettingXML = "";
        var pinCodeLenSecurityConfXML = "";
        var pinCodeLenSecurityConfCmdXML = "";
        var strCardAuthSettingXML = "";
        var nfcXML="";
        var cardAuthXML="";
        //User Authentication Settings
        var chkAutoCreateUser;
        if(document.frmAuth.AUTOCREATEUSER.checked == true)
             chkAutoCreateUser = "true";
        else if(document.frmAuth.AUTOCREATEUSER.checked == false)
                chkAutoCreateUser = "false";
         //Card Auth S&S 3rd
        var chkUseCacheInfo;
        if(document.frmAuth.USECACHEINFO.checked == true)
             chkUseCacheInfo = "true";
        else if(document.frmAuth.USECACHEINFO.checked == false)
                chkUseCacheInfo = "false";
        var chkRegLocalUser;
        if(document.frmAuth.REGGUESTLOCALUSER.checked == true)
             chkRegLocalUser= "true";
        else if(document.frmAuth.REGGUESTLOCALUSER.checked == false)
                chkRegLocalUser = "false";

        ///End

    //---- RCR_1766 START-----
        var autoDeleteLocalUser;

        if (document.frmAuth.AUTODELETEUSER.checked == true) {
           var autoDeleteUserEnable = "true";
            autoDeleteLocalUser = "<deletionOfUnloggedinLocalUsers><deleteLocalUser>"+autoDeleteUserEnable+"</deleteLocalUser><validityPeriodOfLocalUser>" + getValue("VALIDITYLOCALUSER") + "</validityPeriodOfLocalUser></deletionOfUnloggedinLocalUsers>";
        } else if (document.frmAuth.AUTODELETEUSER.checked == false) {
             var autoDeleteUserEnable = "false";
            autoDeleteLocalUser = "<deletionOfUnloggedinLocalUsers><deleteLocalUser>"+autoDeleteUserEnable+"</deleteLocalUser></deletionOfUnloggedinLocalUsers>";  
        }

    // ---- RCR_1766 END ----

        // Windows Domain Auth
        var ntDomain="";
        var strWindowsDomainAuthSettingXML = "";
        var strCardWindowsDomainAuthSettingXML=""; //RCR_1766
        if(getValue('SELUSERAUTH') == "1") {
            if(getValue('USERAUTHTYPE') == "WindowsDomain") {
                if(frmObject.NTServer.checked == true)
                    ntDomain = "true";
                else if(frmObject.NTServer.checked == false)
                    ntDomain = "false";
                strCardAuthSettingXML = "<CacheInformation><isCacheEnabled>"+chkUseCacheInfo+"</isCacheEnabled><availablePeriod>"+getValue('VALIDITYCACHE')+"</availablePeriod></CacheInformation><OneTimeUser><isOneTimeUserEnabled>"+chkRegLocalUser+"</isOneTimeUserEnabled><availablePeriod>"+getValue('VALIDITYGUESTUSER')+"</availablePeriod></OneTimeUser>";
                //roleBasedAccessSettingXML = "<RBACManager><RbacSettings><rbacType>"+getValue("ROLEAUTH")+"</rbacType><rbacServerID>"+getValue("RBA_SELENALDAPAUTH")+"</rbacServerID></RbacSettings></RBACManager>";
                userMgmtSetting = "<authenticationName>"+getValue('USERAUTHTYPE')+"</authenticationName>"+"<autoRegistration>"+chkAutoCreateUser+"</autoRegistration>"+strWindowsDomainAuthSettingXML;
            } else if(getValue('USERAUTHTYPE') == "Ldap") {
                //roleBasedAccessSettingXML = "<RBACManager><RbacSettings><rbacType>"+getValue("ROLEAUTH")+"</rbacType><rbacServerID>"+getValue("RBA_SELENALDAPAUTH")+"</rbacServerID></RbacSettings></RBACManager>";
                /*assignedLDAPServerList = fnRemoveEmptyRows(assignedLDAPServerList);
                LDAPAuthSettingXml = constructLDAPSettingXML(original_assignedLDAPServerList,assignedLDAPServerList);
                var primaryLdapID = (assignedLDAPServerList.length>0) ? "1" : "";*/
                strCardAuthSettingXML = "<CacheInformation><isCacheEnabled>"+chkUseCacheInfo+"</isCacheEnabled><availablePeriod>"+getValue('VALIDITYCACHE')+"</availablePeriod></CacheInformation><OneTimeUser><isOneTimeUserEnabled>"+chkRegLocalUser+"</isOneTimeUserEnabled><availablePeriod>"+getValue('VALIDITYGUESTUSER')+"</availablePeriod></OneTimeUser>";
                userMgmtSetting = "<authenticationName>"+getValue('USERAUTHTYPE')+"</authenticationName><autoRegistration>"+chkAutoCreateUser+"</autoRegistration>";
            } else if(getValue('USERAUTHTYPE') == "Local") {
                devAuthMgmtXML = "<SetLocalAuthModeSwitchover><commandNode>DevAuthMgmtPlugIn</commandNode><SetValue><DevAuthMgmtPlugIn><DevAuthInfo><LocalAuthModeSwitchover>"+(getValue("CARDIDUSERNAME") == "true" ? "UserName" : "CardId")+"</LocalAuthModeSwitchover></DevAuthInfo></DevAuthMgmtPlugIn></SetValue></SetLocalAuthModeSwitchover>";
                userMgmtSetting = "<authenticationName>"+getValue('USERAUTHTYPE')+"</authenticationName>";
            }
            //Email Authentication
            if(getValue('USERAUTHTYPE') == "Ldap")
                scanToEmailXml = "<mode>"+getValue("AUTHMETH")+"</mode><LDAPAuthSetting><LDAPServer>"+fnConvertXMLEntities(getText("SELENALDAPAUTH"))+"</LDAPServer></LDAPAuthSetting>";
            else
                scanToEmailXml = "<mode>"+getValue("AUTHMETH")+"</mode>";
        } else if(getValue('SELUSERAUTH') == "0") { //User Authentication Disable
            //Email Authentication
            if(getValue("AUTHMETH") == "None")
                scanToEmailXml = "<mode>"+getValue("AUTHMETH")+"</mode>";
            else if(getValue("AUTHMETH") == "LDAP") {
                var serverType = "OtherServer";
                if(fnConvertXMLEntities(getText("EMAILAUTH_LdapServerType"))=="true"){
                    serverType = "WindowsServer";
                }
                LDAPAuthSettingXml += "<LDAPAuthSetting>";
				//ServerType : OtherServer or WindowsServer
                    LDAPAuthSettingXml += "<LDAPServer>"+fnConvertXMLEntities(getText("EMAILAUTH_SELENALDAPAUTH"))+"</LDAPServer><serverType>"+ serverType +"</serverType><attrType>"+fnConvertXMLEntities(getValue("SERVERUSERNAME"))+"</attrType>";
                LDAPAuthSettingXml += "</LDAPAuthSetting>";

                //userMgmtSetting = "<Ldap><ldapServerID>"+fnConvertXMLEntities(getValue("EMAILAUTH_SELENALDAPAUTH"))+"</ldapServerID></Ldap>";
				userMgmtSetting = "";

                if(frmObject.AUTHFAX.checked)
                    denyIFaxXML = "<denyIFax>true</denyIFax>";
                else
                    denyIFaxXML = "<denyIFax>false</denyIFax>";
            }
            else if(getValue("AUTHMETH") == "SMTP") {
                    networkAmailAuthSMTPXML = "<Commit><commandNode>Network</commandNode><SetValue><Network><Protocols><SMTP><Client><server>"+fnConvertXMLEntities(getValue('SERVADDRESS'))+"</server><Authentication><desiredType>"+getValue("AUTH")+"</desiredType></Authentication></Client></SMTP></Protocols></Network></SetValue></Commit>";
                    LDAPAuthSettingXml = "";
                    if(frmObject.AUTHFAX.checked)
                        denyIFaxXML = "<denyIFax>true</denyIFax>";
                    else
                        denyIFaxXML = "<denyIFax>false</denyIFax>";
                }
            scanToEmailXml = "<mode>"+getValue("AUTHMETH")+"</mode>"+denyIFaxXML+LDAPAuthSettingXml+"";
        }


        //Save Role Based Ldap ID if modified
        if((getValue('SELUSERAUTH') == "1") && (getValue('USERAUTHTYPE') == "WindowsDomain" || getValue('USERAUTHTYPE') == "Ldap")) {
            roleBasedAccessSettingXML = "<RBACManager><RbacSettings><rbacType>"+getValue("ROLEAUTH")+"</rbacType><rbacServerID>"+getValue("RBA_SELENALDAPAUTH")+"</rbacServerID></RbacSettings></RBACManager>";
        }else{
            //check if Role based value is modified
            if(RBA_LDAPSELID_Global != getValue("RBA_SELENALDAPAUTH")){
                roleBasedAccessSettingXML = "<RBACManager><RbacSettings><rbacType>"+getValue("ROLEAUTH")+"</rbacType><rbacServerID>"+getValue("RBA_SELENALDAPAUTH")+"</rbacServerID></RbacSettings></RBACManager>";
            }
        }

        //Save Card Authentication

        if(CARD_AUTH_Global !=getValue("SELCARDAUTH"))
        {
           cardAuthXML="<CardAuthentication>"+getValue("SELCARDAUTH")+"</CardAuthentication>";
        }

        if((MOB_AUTH_Global !=getValue("SELMOBAUTH")) && (MOBAUTH_AUTOCHANGE_Global !=getValue("SELAUTOCHNG")))
        {
            nfcXML="<NFCSettings><MobileAuthentication>"+getValue("SELMOBAUTH")+"</MobileAuthentication>"+"<MobileOverwriteLogin>"+getValue("SELAUTOCHNG")+"</MobileOverwriteLogin></NFCSettings>";
        }

        else if (MOB_AUTH_Global !=getValue("SELMOBAUTH"))
        {
            nfcXML="<NFCSettings><MobileAuthentication>"+getValue("SELMOBAUTH")+"</MobileAuthentication></NFCSettings>";
        }

        else if (MOBAUTH_AUTOCHANGE_Global !=getValue("SELAUTOCHNG"))
        {
            nfcXML="<NFCSettings><MobileOverwriteLogin>"+getValue("SELAUTOCHNG")+"</MobileOverwriteLogin></NFCSettings>";
        }
        //Save LDAP List
        assignedLDAPServerList = fnRemoveEmptyRows(assignedLDAPServerList);
        LDAPAuthSettingXml = constructLDAPSettingXML(original_assignedLDAPServerList, assignedLDAPServerList);
        var primaryLdapID = (assignedLDAPServerList.length > 0) ? "1" : "";
        if(LDAPAuthSettingXml.length > 0) {
            userMgmtSetting += "<primaryLdapID>" + primaryLdapID + "</primaryLdapID>" + LDAPAuthSettingXml;
        }
        // RCR_1766-Save Card Auth LDAP List
        var strCardAuthLDAPXML="";
        assignedCardAuthLDAPServerList = fnRemoveEmptyRows(assignedCardAuthLDAPServerList);
        cardLDAPAuthSettingXml = constructLDAPSettingXML(original_assignedCardAuthLDAPServerList, assignedCardAuthLDAPServerList);
        if(cardLDAPAuthSettingXml.length > 0) {
            strCardAuthLDAPXML = cardLDAPAuthSettingXml;
        }
        //Save Card List
       // if(getValue("CARDAUTHENABLED") !="NotInstalled") {
            assignedCardServerList = fnRemoveEmptyRows(assignedCardServerList);
            cardAuthLDAPXML += "<CardAuthentication>";
            cardAuthLDAPXML += constructCardAuthSettingXML(original_assignedCardServerList,assignedCardServerList);
            var primaryCardAuthID = (assignedCardServerList.length>0) ? "1" : "";
            cardAuthLDAPXML += "<primaryCardServerID>"+primaryCardAuthID+"</primaryCardServerID>";
            cardAuthLDAPXML += "</CardAuthentication>";
            //cardAuthLDAPXML = "<CardAuthentication><cardServerID>"+getValue("CARDAUTH_LDAPSERVER")+"</cardServerID></CardAuthentication>";
        //}

        //Save Windows Domain List
        var domainXML = "";
        if(getValue('SELUSERAUTH') == "1" && getValue('USERAUTHTYPE') == "WindowsDomain") {
            domainXML = constructWindowsDomainSettingXML(original_windowsAuthListArray, windowsAuthListArray,"NTServer");
            var primaryWindowsDomainID = (windowsAuthListArray.length > 0) ? "1" : "";
            domainXML += "<primaryDomainID>" + primaryWindowsDomainID + "</primaryDomainID>";
            strWindowsDomainAuthSettingXML = "<WindowsDomain><ntDomain>"+ntDomain+"</ntDomain>"+domainXML+"<connectionTimeOut>"+getValue("TIMEOUTSEC")+"</connectionTimeOut></WindowsDomain>";
        }else{
            var isWinDomainContainsEmptyRow = checkEmptyRow(windowsAuthListArray);
            if(isWinDomainContainsEmptyRow){
                domainXML = constructWindowsDomainSettingXML(original_windowsAuthListArray, windowsAuthListArray,"NTServer");
                var primaryWindowsDomainID = (windowsAuthListArray.length > 0) ? "1" : "";
                domainXML += "<primaryDomainID>" + primaryWindowsDomainID + "</primaryDomainID>";
                strWindowsDomainAuthSettingXML = "<WindowsDomain>"+domainXML+"</WindowsDomain>";
            }
        }
        userMgmtSetting += strWindowsDomainAuthSettingXML;

        //RCR_1766 CARD AUTH WINDOWS DOMAIN
        var cardAuthDomainXML = "";
        var autoRegChk = document.frmAuth.USERAUTHAUTOREG.checked;
        var cardNtDomain="";

        if(frmObject.CardAuthNTServer.checked == true)
            cardNtDomain = "true";
        else if(frmObject.CardAuthNTServer.checked == false)
            cardNtDomain = "false";

        if(getValue('CARDUSERAUTHTYPE') == "CardAuthWindowsDomain" && autoRegChk == true) {
            cardAuthDomainXML = constructWindowsDomainSettingXML(original_cardWindowsAuthListArray, cardWindowsAuthListArray,"cardNTServer");
            strCardWindowsDomainAuthSettingXML = "<WindowsDomain><ntDomain>"+cardNtDomain+"</ntDomain>"+cardAuthDomainXML+"</WindowsDomain>";
        }else{
            var isCardWinDomainContainsEmptyRow = checkEmptyRow(cardWindowsAuthListArray);
            if(isCardWinDomainContainsEmptyRow){
                cardAuthDomainXML = constructWindowsDomainSettingXML(original_cardWindowsAuthListArray, cardWindowsAuthListArray,"cardNTServer");
                strCardWindowsDomainAuthSettingXML = "<WindowsDomain>"+cardAuthDomainXML+"</WindowsDomain>";
            }
        }
    
        
        //Pin Authentication
        var pinCodeAuthType = getValue("PINCODEAUTH");
        var pinCodeAuth;
        if(pinCodeAuthType == "false")
            pinCodeAuth = "NONE";
        else if(pinCodeAuthType == "true" && !document.frmAuth.CARDREQPINCODE.checked)
            pinCodeAuth = "PIN";
        else if(pinCodeAuthType == "true" && document.frmAuth.CARDREQPINCODE.checked)
            pinCodeAuth = "ID_GATE_PIN";
        pinCodeAuthXML = "<PINAuthentication><authenticationType>"+pinCodeAuth+"</authenticationType>";
        //if(getValue('SELUSERAUTH') == "1" && (getValue('USERAUTHTYPE') == "Ldap" || getValue('USERAUTHTYPE') == "WindowsDomain" || originalAuthMode == "Ldap" || originalAuthMode == "WindowsDomain") && pinCodeAuthType == "true") {
            assignedPinServerList = fnRemoveEmptyRows(assignedPinServerList);
            pinCodeAuthXML+=constructPinAuthSettingXML(original_assignedPinServerList,assignedPinServerList);
            var primaryPINServerID = (assignedPinServerList.length>0) ? "1" : "";
            pinCodeAuthXML += "<primaryPINServerID>"+primaryPINServerID+"</primaryPINServerID>";
        //}
        pinCodeAuthXML += "</PINAuthentication>";
        pinCodeLenSecurityConfXML = "<SecurityConfiguration><PINSettings><minCharacters>"+getValue("PINCODELEN")+"</minCharacters></PINSettings></SecurityConfiguration>";
        pinCodeLenSecurityConfCmdXML = "<SetPINSettings><commandNode>SecurityConfiguration/PINSettings</commandNode></SetPINSettings>";

        //Email Address Setting
        /*
         * <option value=MachineDefault>Acquire address of "From Address"
         <option value=UserName>Use User Name +@+ Mail Domain Name
         <option value=LDAPUserName>Use Address of LDAP User Name

         * */
        var fromFieldXml = "<FromField>";
        if(getValue("SELFROMADDRESSSETTING") == "MachineDefault")
            fromFieldXml += "<FromAddressSetting>"+getValue("SELFROMADDRESSSETTING")+"</FromAddressSetting>";
        else if(getValue("SELFROMADDRESSSETTING") == "UserName")
            fromFieldXml += "<FromAddressSetting>"+getValue("SELFROMADDRESSSETTING")+"</FromAddressSetting><DomainName>"+fnConvertXMLEntities(getValue("DOMAINNAME"))+"</DomainName>";
        else if(getValue("SELFROMADDRESSSETTING") == "LDAPUserName") {
            //assignedEmailLDAPServerList = fnRemoveEmptyRows(assignedEmailLDAPServerList);
            //EmailLDAPAuthSettingXml = constructEmailLDAPSettingXML(original_assignedEmailLDAPServerList, assignedEmailLDAPServerList);
            fromFieldXml += "<FromAddressSetting>"+getValue("SELFROMADDRESSSETTING")+"</FromAddressSetting><DomainName>"+fnConvertXMLEntities(getValue("LDAPDOMAINNAME"))+"</DomainName>";
        }
        else if(getValue("SELFROMADDRESSSETTING") == "LocalUserEmail")
            fromFieldXml += "<FromAddressSetting>"+getValue("SELFROMADDRESSSETTING")+"</FromAddressSetting>";

        //Save Email Authentication List
        assignedEmailLDAPServerList = fnRemoveEmptyRows(assignedEmailLDAPServerList);
        EmailLDAPAuthSettingXml = constructEmailLDAPSettingXML(original_assignedEmailLDAPServerList, assignedEmailLDAPServerList);
        if(EmailLDAPAuthSettingXml!=""){
            var finalEmailLdapXML = "<LDAPServerSetting>"+EmailLDAPAuthSettingXml+"</LDAPServerSetting>"
            fromFieldXml += finalEmailLdapXML;
        }

        if(frmObject.EDITFORBIT.checked)
            fromFieldXml += "<editable>false</editable>";
        else
            fromFieldXml += "<editable>true</editable>";

        fromFieldXml += "<FromName><selected>"+fnConvertXMLEntities(getValue("EMAILADDRSETFRMNAME"))+"</selected>";
        fromFieldXml += "</FromName>";

        fromFieldXml += "</FromField>";

        var singleSignOnXml = "<SingleSignOn>"+getValue("SELSINGLESIGNON")+"</SingleSignOn>";

        var pwdAuthPrintJob;
        if(frmObject.PWDAUTHPRINTJOB.checked == true)
            pwdAuthPrintJob = "true";
        else if(frmObject.PWDAUTHPRINTJOB.checked == false)
            pwdAuthPrintJob = "false";

        var adminSysSettingsXML = "";
        adminSysSettingsXML = "<Controller><Settings><AdminSystemSettings><Printer><AutoRelease>"+getValue("SELAUTORELONLOGIN")+"</AutoRelease></Printer><Authentication>"+cardAuthXML+"<PrintJobPasswordAuth>"+pwdAuthPrintJob+"</PrintJobPasswordAuth>"+deptSettingXml+singleSignOnXml+"<EmailAddressEdit>"+getValue("EMAILDESTRESTRICT")+"</EmailAddressEdit><Scan2Email>"+scanToEmailXml+"</Scan2Email>"+fromFieldXml+"<ProjectManagement>"+getValue("SELPROJECTMGMT")+"</ProjectManagement><ProjectCodeEnforcement>"+getValue("SELPROJECTJOB")+"</ProjectCodeEnforcement></Authentication>"+nfcXML+"</AdminSystemSettings></Settings></Controller>";
        //adminSysSettingsXML = "<Controller><Settings><RestrictionForReceivedJob>"+getValue("SELRESTRICTRSERVEDJOB")+"</RestrictionForReceivedJob><AdminSystemSettings><Printer><AutoRelease>"+getValue("SELAUTORELONLOGIN")+"</AutoRelease></Printer><Authentication>"+deptSettingXml+singleSignOnXml+"<EmailAddressEdit>"+getValue("EMAILDESTRESTRICT")+"</EmailAddressEdit><Scan2Email>"+scanToEmailXml+"</Scan2Email>"+fromFieldXml+"</Authentication></AdminSystemSettings></Settings></Controller>";

        if(userMgmtSetting != "")
            userMgmtSettingXML += "<AuthenticationType>"+userMgmtSetting+"</AuthenticationType>";
        var enableGuestUser;
        if(frmObject.USERGUEST.checked == true)
            enableGuestUser = "true";
        else if(frmObject.USERGUEST.checked == false)
            enableGuestUser = "false";

        var enableUserInfoCreate;
        if(frmObject.USERINFOCREATE.checked == true)
            enableUserInfoCreate = "true";
        else if(frmObject.USERINFOCREATE.checked == false)
            enableUserInfoCreate = "false";

        var enableSplSrnCrdAuth;

        enableSplSrnCrdAuth=getValue("CARDIDSPLSRN");
        //Home Directory Service
        var serverTypeForUserAuth;
        var serverTypeForCardAuth;
        if(frmObject.AUTH_SERVER_TYPE[0].checked == true) {
            serverTypeForUserAuth = "LOCAL_SERVER";
            serverTypeForCardAuth = "LOCAL_SERVER";
        }
        else if(frmObject.AUTH_SERVER_TYPE[1].checked == true) {
            serverTypeForUserAuth = "AUTH_SERVER";
            serverTypeForCardAuth = "AUTH_SERVER";
        } else if(frmObject.AUTH_SERVER_TYPE[2].checked == true) {
            serverTypeForUserAuth = "AUTH_SERVER";
            serverTypeForCardAuth = "SPECIFIED_SERVER";
        } else if(frmObject.AUTH_SERVER_TYPE[3].checked == true) {
            serverTypeForUserAuth = "SPECIFIED_SERVER";
            serverTypeForCardAuth = "SPECIFIED_SERVER";
        }
        var homeDirXML = "";
        assignedHomeDirServerList = fnRemoveEmptyRows(assignedHomeDirServerList);
        homeDirXML += constructHomeDirAuthSettingXML(original_assignedHomeDirServerList,assignedHomeDirServerList);
        var primaryHomeDirServerID=(assignedHomeDirServerList.length>0) ? "1" : "";
        var homeDir = "<UserProperties><propertiesServerTypeForUserAuth>"+serverTypeForUserAuth+"</propertiesServerTypeForUserAuth><propertiesServerTypeForCardAuth>"+serverTypeForCardAuth+"</propertiesServerTypeForCardAuth><primaryPropertiesServerID>"+primaryHomeDirServerID+"</primaryPropertiesServerID><isHomeDirectoryEnabled>"+getValue("HOMEDIR")+"</isHomeDirectoryEnabled>"+homeDirXML+"</UserProperties>";
        parent.fraTitle.document.forms[0].Save.disabled = true;

        calledFromPage = "Security-SaveAuthSettings";


        var sharedUserSettingXML = "";
        if(getValue('USERAUTHTYPE') == "Local"){
            sharedUserSettingXML= "<UserSyncSetting><SyncronizationType>"+getValue("SHAREDUSERSETTING")+"</SyncronizationType><UserSyncTimeout>"+getValue("CON_TIMEOUTSEC")+"</UserSyncTimeout></UserSyncSetting>";
        }
        //RCR_1766 CARD AUTH AUTH TYPE SAVE DATA
        var cardAuthenticationName;
        var enableUserAutoRegXML;
         //RCR_1766 autoDeleteLocalUser Save data
         if(document.frmAuth.AUTOCREATEUSER.checked == true && getValue("USERAUTHTYPE") == 'Local') {
             var enableUserAutoReg;
             if(frmObject.USERAUTHAUTOREG.checked == true){
                 enableUserAutoReg = "true";
             }
             else if(frmObject.USERAUTHAUTOREG.checked == false){
                 enableUserAutoReg = "false";
             }
             var enableUserAutoRegXML ="<isEnable>"+enableUserAutoReg+"</isEnable>";
             if(getValue('CARDUSERAUTHTYPE') == "CardAuthWindowsDomain"){
                var cardAuthType = "WindowsDomain";
                strCardAuthLDAPXML ="";
            }else{
                var cardAuthType = "Ldap";
                   strCardWindowsDomainAuthSettingXML ="";
             }
                 cardAuthenticationName= "<authenticationName>"+cardAuthType+"</authenticationName>";
         }else{
                enableUserAutoRegXML="";
                cardAuthenticationName="";
         }
        var reqExtUserAuthentication ="<reqExtUserAuthentication><authenticationType>"+cardAuthenticationName+strCardWindowsDomainAuthSettingXML+strCardAuthLDAPXML+"</authenticationType>"+enableUserAutoRegXML+"</reqExtUserAuthentication>"
        var authXML = "<Authentication><AuthenticationSettings><openAccess>"+getValue('SELUSERAUTH')+"</openAccess><unauthorizedPrintJob>"+getValue("SELUSERAUTHENFORCE")+"</unauthorizedPrintJob>"+cardAuthLDAPXML+userMgmtSettingXML+pinCodeAuthXML+strCardAuthSettingXML+autoDeleteLocalUser+"<allowGuestUser>"+enableGuestUser+"</allowGuestUser><CardAuthentication>"+reqExtUserAuthentication+"<allowCardAuthAlways>"+getValue("SELAUTOCHNGLGN")+"</allowCardAuthAlways><autoRegistrationForCardAuth>"+enableUserInfoCreate+"</autoRegistrationForCardAuth><screenForCardAuthentication>"+enableSplSrnCrdAuth+"</screenForCardAuthentication></CardAuthentication>"+homeDir+"<AuthenticatedTarget><copy>"+getValue("SELAUTHCOPY")+"</copy><fax>"+getValue("SELAUTHFAX")+"</fax><print>"+getValue("SELAUTHPRINT")+"</print><scan>"+getValue("SELAUTHSCAN")+"</scan><list>"+getValue("SELAUTHLIST")+"</list><userFunctions>"+getValue("SELAUTHUSERFN")+"</userFunctions><EWB>"+getValue("SELAUTHEWB")+"</EWB></AuthenticatedTarget><adminAuthentication>"+getValue("SELAUTHADMIN")+"</adminAuthentication><functionwiseAuthentication>"+getValue("SELUSERAUTHACCTOFUNCT")+"</functionwiseAuthentication>"+sharedUserSettingXML+"</AuthenticationSettings></Authentication>";

        fnExtractXpathsFromXML(parseXML(authXML),calledFromPage);
        var selXml = "";
        for(var key in gblAuthenticationXpathsHM) {
            selXml += "<Payload><path>"+key+"</path><value>"+fnConvertXMLEntities(gblAuthenticationXpathsHM[key])+"</value></Payload>";
        }
        gblSETExtendedRequestXMLArray = [selXml];
        if(roleBasedAccessSettingXML != "") {
            gblSETRequestXMLArray = [roleBasedAccessSettingXML];
            glbContentWebServerCmdArray = ["<Set><commandNode>Controller</commandNode><SetValue>"+adminSysSettingsXML+"</SetValue></Set>","<AuthSettings><commandNode>Authentication/AuthenticationSettings</commandNode></AuthSettings>","<SetRbacSettings><commandNode>RBACManager/RbacSettings</commandNode><Params><settingDetails contentType='XPath'>RBACManager/RbacSettings</settingDetails></Params></SetRbacSettings>"];
        } else {
            gblSETRequestXMLArray = null;
            glbContentWebServerCmdArray = ["<Set><commandNode>Controller</commandNode><SetValue>"+adminSysSettingsXML+"</SetValue></Set>","<AuthSettings><commandNode>Authentication/AuthenticationSettings</commandNode></AuthSettings>"];
        }

        //authXML = "<Authentication><AuthenticationSettings><openAccess>1</openAccess><unauthorizedPrintJob>Print</unauthorizedPrintJob><AuthenticationType><authenticationName>WindowsDomain</authenticationName><WindowsDomain><ntDomain>true</ntDomain><Domain ID='1'><domainName>d12</domainName><pdc>99</pdc><bdc>99</bdc></Domain><Domain ID='2'><domainName>d22a</domainName><pdc>88a</pdc><bdc>88a</bdc></Domain><Domain ID='3'><domainName></domainName><pdc></pdc><bdc></bdc></Domain><connectionTimeOut>120</connectionTimeOut></WindowsDomain></AuthenticationType><allowGuestUser>false</allowGuestUser><CardAuthentication><autoRegistrationForCardAuth>false</autoRegistrationForCardAuth><rules><rule>a</rule><rule></rule><rule>c</rule><rule>last</rule></rules></CardAuthentication></AuthenticationSettings></Authentication>";
        /*if(roleBasedAccessSettingXML != "") {
            var authXML = "<Authentication><AuthenticationSettings><openAccess>"+getValue('SELUSERAUTH')+"</openAccess><unauthorizedPrintJob>"+getValue("SELUSERAUTHENFORCE")+"</unauthorizedPrintJob>"+cardAuthLDAPXML+userMgmtSettingXML+pinCodeAuthXML+strCardAuthSettingXML+"<allowGuestUser>"+enableGuestUser+"</allowGuestUser><CardAuthentication><allowCardAuthAlways>"+getValue("SELAUTOCHNGLGN")+"</allowCardAuthAlways><autoRegistrationForCardAuth>"+enableUserInfoCreate+"</autoRegistrationForCardAuth><screenForCardAuthentication>"+enableSplSrnCrdAuth+"</screenForCardAuthentication></CardAuthentication>"+homeDir+"<AuthenticatedTarget><copy>"+getValue("SELAUTHCOPY")+"</copy><fax>"+getValue("SELAUTHFAX")+"</fax><print>"+getValue("SELAUTHPRINT")+"</print><scan>"+getValue("SELAUTHSCAN")+"</scan><list>"+getValue("SELAUTHLIST")+"</list><userFunctions>"+getValue("SELAUTHUSERFN")+"</userFunctions><EWB>"+getValue("SELAUTHEWB")+"</EWB></AuthenticatedTarget><adminAuthentication>"+getValue("SELAUTHADMIN")+"</adminAuthentication><functionwiseAuthentication>"+getValue("SELUSERAUTHACCTOFUNCT")+"</functionwiseAuthentication></AuthenticationSettings></Authentication>";
            fnExtractXpathsFromXML(parseXML(authXML),calledFromPage);
            var selXml = "";
            for(var key in gblAuthenticationXpathsHM) {
                selXml += "<Payload><path>"+key+"</path><value>"+fnConvertXMLEntities(gblAuthenticationXpathsHM[key])+"</value></Payload>";
            }
            gblSETRequestXMLArray = [roleBasedAccessSettingXML];
            gblSETExtendedRequestXMLArray = [selXml];
            glbContentWebServerCmdArray = ["<Set><commandNode>Controller</commandNode><SetValue>"+adminSysSettingsXML+"</SetValue></Set>","<AuthSettings><commandNode>Authentication/AuthenticationSettings</commandNode></AuthSettings>","<SetRbacSettings><commandNode>RBACManager/RbacSettings</commandNode><Params><settingDetails contentType='XPath'>RBACManager/RbacSettings</settingDetails></Params></SetRbacSettings>"];
        } else {
            var sharedUserSettingXML= "<UserSyncSetting><SyncronizationType>"+getValue("SHAREDUSERSETTING")+"</SyncronizationType><UserSyncTimeout>"+getValue("CON_TIMEOUTSEC")+"</UserSyncTimeout></UserSyncSetting>";
            var authXML = "<Authentication><AuthenticationSettings><openAccess>"+getValue('SELUSERAUTH')+"</openAccess><unauthorizedPrintJob>"+getValue("SELUSERAUTHENFORCE")+"</unauthorizedPrintJob>"+cardAuthLDAPXML+userMgmtSettingXML+pinCodeAuthXML+strCardAuthSettingXML+"<allowGuestUser>"+enableGuestUser+"</allowGuestUser><CardAuthentication><allowCardAuthAlways>"+getValue("SELAUTOCHNGLGN")+"</allowCardAuthAlways><autoRegistrationForCardAuth>"+enableUserInfoCreate+"</autoRegistrationForCardAuth><screenForCardAuthentication>"+enableSplSrnCrdAuth+"</screenForCardAuthentication></CardAuthentication>"+homeDir+"<AuthenticatedTarget><copy>"+getValue("SELAUTHCOPY")+"</copy><fax>"+getValue("SELAUTHFAX")+"</fax><print>"+getValue("SELAUTHPRINT")+"</print><scan>"+getValue("SELAUTHSCAN")+"</scan><list>"+getValue("SELAUTHLIST")+"</list><userFunctions>"+getValue("SELAUTHUSERFN")+"</userFunctions><EWB>"+getValue("SELAUTHEWB")+"</EWB></AuthenticatedTarget><adminAuthentication>"+getValue("SELAUTHADMIN")+"</adminAuthentication><functionwiseAuthentication>"+getValue("SELUSERAUTHACCTOFUNCT")+"</functionwiseAuthentication>"+sharedUserSettingXML+"</AuthenticationSettings></Authentication>";
            fnExtractXpathsFromXML(parseXML(authXML),calledFromPage);
            var selXml = "";
            for(var key in gblAuthenticationXpathsHM) {
                selXml += "<Payload><path>"+key+"</path><value>"+fnConvertXMLEntities(gblAuthenticationXpathsHM[key])+"</value></Payload>";
            }
            gblSETRequestXMLArray = null;
            gblSETExtendedRequestXMLArray = [selXml];
            glbContentWebServerCmdArray = ["<Set><commandNode>Controller</commandNode><SetValue>"+adminSysSettingsXML+"</SetValue></Set>","<AuthSettings><commandNode>Authentication/AuthenticationSettings</commandNode></AuthSettings>"];
        }*/

        var CWSCmdArrlen = glbContentWebServerCmdArray.length;
        if(networkAmailAuthSMTPXML != "")
            glbContentWebServerCmdArray[CWSCmdArrlen] = networkAmailAuthSMTPXML;
        if(devAuthMgmtXML != "")
            glbContentWebServerCmdArray[CWSCmdArrlen] = devAuthMgmtXML;

        if(pinCodeLenSecurityConfXML != "" && pinCodeLenSecurityConfXML != undefined && pinCodeLenSecurityConfXML != null) {
            if(gblSETRequestXMLArray == null)
                gblSETRequestXMLArray = [pinCodeLenSecurityConfXML]
            else {
                var setReqXMLArrlen = gblSETRequestXMLArray.length;
                gblSETRequestXMLArray[setReqXMLArrlen] =  pinCodeLenSecurityConfXML;
            }
            CWSCmdArrlen = glbContentWebServerCmdArray.length;
            glbContentWebServerCmdArray[CWSCmdArrlen] = pinCodeLenSecurityConfCmdXML;
        }
        gblBoolHandleRespArray = [true];
        InitiateServerRequest("SETSTXFCMD");
    } catch(e){errHandler(e,'fnSaveAuthenticationSettings()','Authentication.html?v=1717026199ta',"");}
}

function fnContructSecuritySetvalue(nodeObj,xpathFromXml) {
    if(nodeObj == null){
        gblAuthenticationXpathsHM[xpathFromXml] = "";
    }else{
        gblAuthenticationXpathsHM[xpathFromXml] = nodeObj.nodeValue;
    }
}
function htmlEncode(value){
    return $('<div/>').text(value).html();
}

function htmlDecode(value){
    return $('<div/>').html(value).text();
}
/*
This method parse the Directory Service List
 */
function parseDirectoryServiceInfo(xmlObj){
	var otherServer = fnGetLocaleString("103323","LDAP Server (Other than Windows Server)");
	var windowsServer = fnGetLocaleString("102028","Windows Server");

    var dirSerList = xmlObj.getElementsByTagName("DirectoryService");

    var arrDirLen = dirSerList.length;
    //var totalProperties = 12;
    var totalProperties = 13; //DCR: EBX_DCR_53951: PIN/Card Authentication
    var serverList = new Array(arrDirLen);
	var makeEmptyRows = [];
    if (dirSerList !== undefined && dirSerList != null && dirSerList.length > 0){
	    var count=0;
        for(var i=0; i <arrDirLen; i++) {
            //below loop is for showing blank row
			var dirID = ((temp=dirSerList[i].getAttribute("id"))==null)?"":temp; // ID
			while(count != parseInt(dirID)){
			   makeEmptyRows.push(count);
			   count++;
			}
				serverList[count] = new Array(totalProperties);
				serverList[count][0] = dirID;
				serverList[count][1] = ((temp=getXpathValue(dirSerList[i],"name"))==null)?"":temp; //Directory Service Name
				serverList[count][2] = ((temp=getXpathValue(dirSerList[i],"ipAddress"))==null)?"":temp; //IP Address
				serverList[count][3] = ((temp=getXpathValue(dirSerList[i],"authenticationMethod"))==null)?"":temp; //Auth Method
				serverList[count][4] = "";
				var isWindowsServer = ((temp=getXpathValue(dirSerList[i],"WindowsServer"))==null)?"":temp; // is windows server
				if(isWindowsServer === "true"){
					serverList[count][4] = windowsServer;
				}else if(isWindowsServer === "false"){
					serverList[count][4] = otherServer;
				}
				serverList[count][5] = ((temp=getXpathValue(dirSerList[i],"attrUserName"))==null)?"":temp; // User Name
				serverList[count][6] = "0"; // temp flag used to check whether DSN is assigned or unassigned in all the scenarios -> currently using this flag when splitting the available/assigned servers
                serverList[count][7] = ((temp=getXpathValue(dirSerList[i],"attrPIN"))==null)?"":temp; // Attribute Pin
                serverList[count][8] = ((temp=getXpathValue(dirSerList[i],"attrCardID"))==null)?"":temp; // Attribute Card
                serverList[count][9] = ((temp=getXpathValue(dirSerList[i],"attrCardRevision"))==null)?"":temp; // Attribute Card Revision
                serverList[count][10] = ((temp=getXpathValue(dirSerList[i],"attrFromAddr"))==null)?"":temp; // Attribute From Address
				serverList[count][11] = ((temp=getXpathValue(dirSerList[i],"attrFromName"))==null)?"":temp; // Attribute From Name
				serverList[count][12] = ((temp=getXpathValue(dirSerList[i],"attrLdapServerName"))==null)?"":temp; // Attribute From LDAPServerName
				count++;
        }

		for(var k=0;k<makeEmptyRows.length;k++){
			serverList[makeEmptyRows[k]] = new Array(totalProperties);
			serverList[makeEmptyRows[k]][0] = makeEmptyRows[k].toString();
		   //for(var cols=1;cols<=11;cols++){
		   for(var cols=1;cols<=12;cols++){
		       serverList[makeEmptyRows[k]][cols]="";
		   }
		}
    }
    directoryServiceListArray = serverList;
}

/*
 This method parse the WINDOWS AUTHENTICATION INFO
 */
function parseWindowsAuthInfo(xmlObj,tableName){
    //RCR_1766
    if(tableName == "windowsDomain"){
        xmlObj = evaluateXpath(xmlObj,"Authentication/AuthenticationSettings/AuthenticationType/WindowsDomain");
    }else{
        xmlObj = evaluateXpath(xmlObj,"Authentication/AuthenticationSettings/CardAuthentication/reqExtUserAuthentication/authenticationType/WindowsDomain");
    }

    var windowsAuthList = xmlObj.getElementsByTagName("Domain");

    var arrDirLen = windowsAuthList.length;
    var totalProperties = 3;
    var authList = [];
    var count = 0;
    var assigned=false;
    if (windowsAuthList !== undefined && windowsAuthList != null && windowsAuthList.length > 0){
        for(var i=arrDirLen-1; i>=0; i--) {
            var domainName = ((temp=getXpathValue(windowsAuthList[i],"domainName"))==null)?"":temp;
            if(domainName != ""){
                assigned =true;
            }

            if(assigned==true){
				authList[count] = new Array(totalProperties);
				authList[count][0] = domainName;
				temp=$.trim(getXpathValue(windowsAuthList[i],"pdc"));
				authList[count][1] = (temp==null || temp=="")?"":temp;
				temp=$.trim(getXpathValue(windowsAuthList[i],"bdc"));
				authList[count][2] = (temp==null || temp=="")?"":temp;
                count++;
            }
        }
    }
    authList.reverse();
    //RCR_1766
    if(tableName == "windowsDomain"){
        windowsAuthListArray = authList;
        original_windowsAuthListArray = cloneArray(windowsAuthListArray);
    }else{
        cardWindowsAuthListArray=authList; 
        original_cardWindowsAuthListArray = cloneArray(cardWindowsAuthListArray);

    }
}

/*
 This method parse the LDAP AND AUTO REG CARD  AUTHENTICATION INFO
 */
function  parseLDAPAuthInfo(xmlObj,tableName){
    if(tableName == 'LDAP'){
        xmlObj = evaluateXpath(xmlObj,"Authentication/AuthenticationSettings/AuthenticationType");
        var LDAPAuthList = xmlObj[0].getElementsByTagName("Ldap");
    }else{
        xmlObj = evaluateXpath(xmlObj,"Authentication/AuthenticationSettings/CardAuthentication/reqExtUserAuthentication/authenticationType");
        var LDAPAuthList = xmlObj.getElementsByTagName("Ldap");
    }
    
	 var otherServer = fnGetLocaleString("103323","LDAP Server (Other than Windows Server)");
	 var windowsServer = fnGetLocaleString("102028","Windows Server");

    var arrDirLen = LDAPAuthList.length;
    var totalProperties = 3;
    var authList = new Array(arrDirLen);
    if (LDAPAuthList !== undefined && LDAPAuthList != null && LDAPAuthList.length > 0){
        for(var i=0; i <arrDirLen; i++) {
            authList[i] = new Array(totalProperties);
            authList[i][0] =((temp=getXpathValue(LDAPAuthList[i],"ldapServerID"))==null)?"":temp;
			var isWindowsServer = ((temp=getXpathValue(LDAPAuthList[i],"isWindowsServer"))==null)?"":temp; // is windows server
			if(isWindowsServer === "true"){
				authList[i][1] = windowsServer;
			}else{
			    authList[i][1] = otherServer;
			}
            authList[i][2] = ((temp=getXpathValue(LDAPAuthList[i],"attrNameForUser"))==null)?"":temp;
        }

        //17-Nov-2015 - Assign -1 for deleted servers
        var deletedFlag = "0";
        for(var i=arrDirLen-1;i>=0;i--){
            if(authList[i][0] !='0'){
                deletedFlag = "-1";
            }
            if(authList[i][0] == '0' || authList[i][0] == ''){
                authList[i][0] = deletedFlag;
            }
        }
    }
    if(tableName == 'LDAP'){
        LDAPAuthListArray = authList;
    }else{
        cardLDAPAuthListArray = authList;
    }
}

/*
 This method parse the Email LDAP AUTHENTICATION INFO
 */
function parseEmailLDAPAuthInfo(xmlObj){

    var totalLDAPServers=4;
    var EmailLDAPServerList=new Array();

    var serverName = "";
    var assigned = false;
    for(var i=3;i>=0;i--) {
      if(i==0)
          serverName =((temp=getXpathValue(xmlObj,"Controller/Settings/AdminSystemSettings/Authentication/FromField/LDAPServerSetting/LDAPServer"))==null)?"":temp;
      else
          serverName =((temp=getXpathValue(xmlObj,"Controller/Settings/AdminSystemSettings/Authentication/FromField/LDAPServerSetting/LDAPServer"+(i+1)))==null)?"":temp;

        //if middle node is empty consider it as deleted(display empty row)
        if(serverName!=""){
            assigned = true;
        }
        //add only assigned
        if(assigned) {
            EmailLDAPServerList.push(serverName);
        }
    }
          EmailLDAPServerList.reverse();
          EmailLDAPServerListArray = EmailLDAPServerList;
}

/*
 This method parse the Card AUTHENTICATION INFO
 */
function parseCardAuthInfo(xmlObj){
    var cardAuthList = evaluateXpath(xmlObj,"Authentication/AuthenticationSettings/CardAuthentication/cardServerID");
	var otherServer = fnGetLocaleString("103323","LDAP Server (Other than Windows Server)");
	var windowsServer = fnGetLocaleString("102028","Windows Server");

    var arrDirLen = cardAuthList.length;
    //var totalProperties = 5;
    var totalProperties = 6; //DCR: EBX_DCR_53951: PIN/Card Authentication
    var authList = new Array(arrDirLen);
    if (cardAuthList !== undefined && cardAuthList != null){
        for(var i=0; i < arrDirLen; i++) {
            authList[i] = new Array(totalProperties);
            authList[i][0] =((temp=getXpathValue(xmlObj,"Authentication/AuthenticationSettings/CardAuthentication/cardServerID[@ID='"+ (i+1) +"']"))==null)?"":temp;
            var isWindowsServer = ((temp=getXpathValue(xmlObj,"Authentication/AuthenticationSettings/CardAuthentication/isWindowsServer[@ID='"+ (i+1) +"']"))==null)?"":temp; // is windows server
			if(isWindowsServer === "true"){
				authList[i][1] = windowsServer;
			}else{
			    authList[i][1] = otherServer;
			}
            authList[i][2] =((temp=getXpathValue(xmlObj,"Authentication/AuthenticationSettings/CardAuthentication/attrNameForUser[@ID='"+ (i+1) +"']"))==null)?"":temp;
            authList[i][3] =((temp=getXpathValue(xmlObj,"Authentication/AuthenticationSettings/CardAuthentication/atrNameForCardID[@ID='"+ (i+1) +"']"))==null)?"":temp;
            authList[i][4] =((temp=getXpathValue(xmlObj,"Authentication/AuthenticationSettings/CardAuthentication/attrNameForCardRevision[@ID='"+ (i+1) +"']"))==null)?"":temp;
            authList[i][5] =((temp=getXpathValue(xmlObj,"Authentication/AuthenticationSettings/CardAuthentication/attrNameForServerName[@ID='"+ (i+1) +"']"))==null)?"":temp;  //DCR: EBX_DCR_53951: PIN/Card Authentication

        }

        //17-Nov-2015 - Assign -1 for deleted servers
        var deletedFlag = "0";
        for(var i=arrDirLen-1;i>=0;i--){
            if(authList[i][0] !='0'){
                deletedFlag = "-1";
            }
            if(authList[i][0] == '0' || authList[i][0] == ''){
                authList[i][0] = deletedFlag;
            }
        }
    }
    cardAuthListArray = authList;
}

/*
 This method parse the PIN AUTHENTICATION INFO
 */
function parsePinAuthInfo(xmlObj){
    xmlObj = evaluateXpath(xmlObj,"Authentication/AuthenticationSettings/PINAuthentication");
    var pinAuthList = xmlObj.getElementsByTagName("Server");
	var otherServer = fnGetLocaleString("103323","LDAP Server (Other than Windows Server)");
	var windowsServer = fnGetLocaleString("102028","Windows Server");

    var arrDirLen = pinAuthList.length;
    //var totalProperties = 4;
    var totalProperties = 5; //DCR: EBX_DCR_53951: PIN/Card Authentication
    var authList = new Array(arrDirLen);
    if (pinAuthList !== undefined && pinAuthList != null){
        for(var i=0; i < arrDirLen; i++) {
            authList[i] = new Array(totalProperties);
            authList[i][0] =((temp=getXpathValue(pinAuthList[i],"serverIDForPIN"))==null)?"":temp;
			var isWindowsServer = ((temp=getXpathValue(pinAuthList[i],"isWindowsServer"))==null)?"":temp; // is windows server
			if(isWindowsServer === "true"){
				authList[i][1] = windowsServer;
			}else{
			    authList[i][1] = otherServer;
			}
            authList[i][2] =((temp=getXpathValue(pinAuthList[i],"attrNameForPIN"))==null)?"":temp;
            authList[i][3] =((temp=getXpathValue(pinAuthList[i],"attrNameForUser"))==null)?"":temp;
            authList[i][4] =((temp=getXpathValue(pinAuthList[i],"attrNameForServerName"))==null)?"":temp; //DCR: EBX_DCR_53951: PIN/Card Authentication
        }
        //17-Nov-2015 - Assign -1 for deleted servers
        var deletedFlag = "0";
        for(var i=arrDirLen-1;i>=0;i--){
            if(authList[i][0] != '0'){
                deletedFlag = "-1";
            }
            if(authList[i][0] == '0' || authList[i][0] == ''){
                authList[i][0] = deletedFlag;
            }
        }
    }
    pinAuthListArray = authList;
}

/*
 This method parse the Home Directory INFO
 */
function parseHomeDirAuthInfo(xmlObj){
    var homeDirAuthList = evaluateXpath(xmlObj,"Authentication/AuthenticationSettings/UserProperties/userPropertiesServer");
    //getXpathValue(node, "Authentication/AuthenticationSettings/UserProperties/userPropertiesServer[@ID='"+i+"']")
    var arrDirLen = homeDirAuthList.length;
    var totalProperties = 1;
    var authList = new Array(arrDirLen);
    if (homeDirAuthList !== undefined && homeDirAuthList != null){
        for(var i=0; i < arrDirLen; i++) {
            authList[i] = new Array(totalProperties);
            authList[i][0] =((temp=getXpathValue(xmlObj,"Authentication/AuthenticationSettings/UserProperties/userPropertiesServer[@ID='"+ (i+1) +"']"))==null)?"":temp;
        }

        //17-Nov-2015 - Assign -1 for deleted servers
        var deletedFlag = "0";
        for(var i=arrDirLen-1;i>=0;i--){
            if(authList[i][0] !='0'){
                deletedFlag = "-1";
            }
            if(authList[i][0] == '0'  || authList[i][0] == ''){
                authList[i][0] = deletedFlag;
            }
        }
    }
    homeDirAuthListArray = authList;
}

/*
 This method splits Card Assigned and Available server list
 */
function fnCardAuthServerSplitter(directoryServerList,cardList){
    if(cardList == undefined || cardList == null ||directoryServerList == undefined || directoryServerList == null ){
        alert('internal error : invalid data');
        return;
    }
    availableCardServerList = new Array();
    assignedCardServerList = new Array();

    var cardListLength = cardList.length;
    var directoryServerListLength  = directoryServerList.length; //total registered servers
    //var totalProperties = 7; //total properties required for assigned and available list
    var totalProperties = 8; //total properties required for assigned and available list //DCR: EBX_DCR_53951: PIN/Card Authentication

    //initialize all directory service assigned flag
    for(var i=1;i<directoryServerListLength;i++) {
        directoryServerList[i][6] = "0"; // temp flag used to check whether DSN is assigned or unassigned in all the scenarios
    }

    // below loop is to populate assigned server list
    for(var i=0;i<cardListLength;i++) {
        if (cardList[i][0] != 0) { // if assigned
		var foundInDS = false;
            for(var j=0;j<directoryServerListLength;j++){ //get the id of assigned server
                if(directoryServerList[j][0]==cardList[i][0]){ // merging both directory service and card client
					foundInDS = true;
                    var tmpAssignedArray = new Array(totalProperties);
                    tmpAssignedArray[0] = directoryServerList[j][0]; // ID
                    tmpAssignedArray[1] = directoryServerList[j][1]; // Directory Service Name
                    tmpAssignedArray[2] = directoryServerList[j][2]; // IP Address
                    tmpAssignedArray[3] = directoryServerList[j][4];  // is windows server
                    tmpAssignedArray[4] = directoryServerList[j][5]; // attrNameForUser
                    tmpAssignedArray[5] = directoryServerList[j][8];  // attr type card info
                    tmpAssignedArray[6] = directoryServerList[j][9];  // attr type card issuing time
                    tmpAssignedArray[7] = directoryServerList[j][12];  // attrLDAPServerName
                    directoryServerList[j][6] = "1"; // mark this DSN as assigned
                    assignedCardServerList.push(tmpAssignedArray); // push the assigned server to assigned list
                    break;
                }
            }

			//if assigned CARD ID is not found in Directory Service List
				//Create a empty directory service with the same CARD ID
				if(!foundInDS){
					var serverList=[];
					serverList[0]= new Array(directoryServerList.length);
					serverList[0][0] = cardList[i][0];
					//for(var cols=1;cols<=11;cols++){
					for(var cols=1;cols<=12;cols++){
						serverList[0][cols]="";
					}
					var tmpAssignedArray = new Array(totalProperties);
                    tmpAssignedArray[0] = serverList[0][0]; // ID
                    tmpAssignedArray[1] = ""; // Directory Service Name
                    tmpAssignedArray[2] = ""; // IP Address
                    tmpAssignedArray[3] = "";
					tmpAssignedArray[4] = "";
					tmpAssignedArray[5] = "";
					tmpAssignedArray[6] = "";
					tmpAssignedArray[7] = ""; //DCR: EBX_DCR_53951: PIN/Card Authentication
                    serverList[0][6] = "1"; // mark this DSN as assigned
					//directoryServerList.push(serverList);
                    assignedCardServerList.push(tmpAssignedArray); // push the new assigned server to assigned list
				}
        }
    }
    original_assignedCardServerList = cloneArray(assignedCardServerList);
    // below loop is to populate available server list
    for(var i=1;i<directoryServerListLength;i++) { //creating available server list
        if(directoryServerList[i][6]=="0"){
		//donot add deleted directory service as available
		  //if($.trim(directoryServerList[i][1]) != ""){
		  if(directoryServerList[i][1] != ""){
            var tmpAssignedArray = new Array(totalProperties);
            tmpAssignedArray[0] = directoryServerList[i][0]; // ID
            tmpAssignedArray[1] = directoryServerList[i][1]; // Directory Service Name
            tmpAssignedArray[2] = directoryServerList[i][2]; // IP Address
            tmpAssignedArray[3] = directoryServerList[i][4];  // is windows server
            tmpAssignedArray[4] = directoryServerList[i][5]; // attrNameForUser
            tmpAssignedArray[5] = directoryServerList[i][8];  // attr type card info
            tmpAssignedArray[6] = directoryServerList[i][9];  // attr type card issuing time
            tmpAssignedArray[7] = directoryServerList[i][12];  // attrLDAPServerName
            availableCardServerList.push(tmpAssignedArray);
		  }
        }
    }

}

/*
 This method splits Home Directory Assigned and Available server list
 */
function fnHomeDirAuthServerSplitter(directoryServerList,homeDirList){
    if(homeDirList == undefined || homeDirList == null ||directoryServerList == undefined || directoryServerList == null ){
        alert('internal error : invalid data');
        return;
    }
    availableHomeDirServerList = new Array();
    assignedHomeDirServerList = new Array();

    var homeDirListLength = homeDirList.length; // always 3 as per req
    var directoryServerListLength  = directoryServerList.length; //total registered servers
    var totalProperties = 4; //total properties required for assigned and available list

    //initialize all directory service assigned flag
    for(var i=1;i<directoryServerListLength;i++) {
        directoryServerList[i][6] = "0"; // temp flag used to check whether DSN is assigned or unassigned in all the scenarios
    }

    // below loop is to populate assigned server list
    for(var i=0;i<homeDirListLength;i++) {
        if (homeDirList[i][0] != 0) { // if assigned
			var foundInDS = false;
            for(var j=0;j<directoryServerListLength;j++){ //get the id of assigned server
                if(directoryServerList[j][0]==homeDirList[i][0]){ // merging both directory service and home client
					foundInDS = true;
                    var tmpAssignedArray = new Array(totalProperties);
                    tmpAssignedArray[0] = directoryServerList[j][0]; // ID
                    tmpAssignedArray[1] = directoryServerList[j][1]; // Directory Service Name
                    tmpAssignedArray[2] = directoryServerList[j][2]; // IP Address
                    tmpAssignedArray[3] = directoryServerList[j][5]; // attrNameForUser
                    directoryServerList[j][6] = "1"; // mark this DSN as assigned
                    assignedHomeDirServerList.push(tmpAssignedArray); // push the assigned server to assigned list
                    break;
                }
            }

				//if assigned HOME ID is not found in Directory Service List
				//Create a empty directory service with the same HOME ID
				if(!foundInDS){
					var serverList=[];
					serverList[0]= new Array(directoryServerList.length);
					serverList[0][0] = homeDirList[i][0];
					//for(var cols=1;cols<=11;cols++){
					for(var cols=1;cols<=12;cols++){
						serverList[0][cols]="";
					}
					var tmpAssignedArray = new Array(totalProperties);
                    tmpAssignedArray[0] = serverList[0][0]; // ID
                    tmpAssignedArray[1] = ""; // Directory Service Name
                    tmpAssignedArray[2] = ""; // IP Address
                    tmpAssignedArray[3] = "";
                    serverList[0][6] = "1"; // mark this DSN as assigned
					//directoryServerList.push(serverList);
                    assignedHomeDirServerList.push(tmpAssignedArray); // push the new assigned server to assigned list
				}
        }
    }
    original_assignedHomeDirServerList = cloneArray(assignedHomeDirServerList);

    // below loop is to populate available server list
    for(var i=1;i<directoryServerListLength;i++) { //creating available server list
        if(directoryServerList[i][6]=="0"){
		  //donot add deleted directory service as available
		  //if($.trim(directoryServerList[i][1]) != ""){
		  if(directoryServerList[i][1] != ""){
            var tmpAssignedArray = new Array(totalProperties);
            tmpAssignedArray[0] = directoryServerList[i][0]; // ID
            tmpAssignedArray[1] = directoryServerList[i][1]; //Directory Service Name
            tmpAssignedArray[2] = directoryServerList[i][2]; // IP Address
            tmpAssignedArray[3] = directoryServerList[i][5]; // attrNameForUser
            availableHomeDirServerList.push(tmpAssignedArray);
		  }
        }
    }
}

/*
 This method splits Pin Assigned and Available server list
 */
function fnPinAuthServerSplitter(directoryServerList,pinList){
    if(pinList == undefined || pinList == null ||directoryServerList == undefined || directoryServerList == null ){
        alert('internal error : invalid data');
        return;
    }
    availablePinServerList = new Array();
    assignedPinServerList = new Array();

    var pinListLength = pinList.length; // always 3 as per req
    var directoryServerListLength  = directoryServerList.length; //total registered servers
    //var totalProperties = 6; //total properties required for assigned and available list
    var totalProperties = 7; //total properties required for assigned and available list //DCR: EBX_DCR_53951: PIN/Card Authentication


    //initialize all directory service assigned flag
    for(var i=1;i<directoryServerListLength;i++) {
        directoryServerList[i][6] = "0"; // temp flag used to check whether DSN is assigned or unassigned in all the scenarios
    }

    // below loop is to populate assigned server list
    for(var i=0;i<pinListLength;i++) {
        if (pinList[i][0] != 0) { // if assigned
			var foundInDS = false;
            for(var j=0;j<directoryServerListLength;j++){ //get the id of assigned server
                if(directoryServerList[j][0]==pinList[i][0]){ // merging both directory service and pin client
					foundInDS = true;
                    var tmpAssignedArray = new Array(totalProperties);
                    tmpAssignedArray[0] = directoryServerList[j][0]; // ID
                    tmpAssignedArray[1] = directoryServerList[j][1]; // Directory Service Name
                    tmpAssignedArray[2] = directoryServerList[j][2]; // IP Address
                    tmpAssignedArray[3] = directoryServerList[j][4];  // is windows server
                    tmpAssignedArray[4] = directoryServerList[j][5]; // attrNameForUser
                    tmpAssignedArray[5] = directoryServerList[j][7];  // attrNameForPIN
                    tmpAssignedArray[6] = directoryServerList[j][12];  // attrLDAPServerName

                    directoryServerList[j][6] = "1"; // mark this DSN as assigned
                    assignedPinServerList.push(tmpAssignedArray); // push the assigned server to assigned list
                    break;
                }
            }
				//if assigned PIN ID is not found in Directory Service List
				//Create a empty directory service with the same PIN ID
				if(!foundInDS){
					var serverList=[];
					serverList[0]= new Array(directoryServerList.length);
					serverList[0][0] = pinList[i][0];
					//for(var cols=1;cols<=11;cols++){
					for(var cols=1;cols<=12;cols++){
						serverList[0][cols]="";
					}

					var tmpAssignedArray = new Array(totalProperties);
                    tmpAssignedArray[0] = serverList[0][0]; // ID
                    tmpAssignedArray[1] = ""; // Directory Service Name
                    tmpAssignedArray[2] = ""; // IP Address
                    tmpAssignedArray[3] = "";
                    tmpAssignedArray[4] = "";
                    tmpAssignedArray[5] = "";
                    tmpAssignedArray[6] = ""; //DCR: EBX_DCR_53951: PIN/Card Authentication
                    serverList[0][6] = "1"; // mark this DSN as assigned
					//directoryServerList.push(serverList);
                    assignedPinServerList.push(tmpAssignedArray); // push the new assigned server to assigned list
				}
        }
    }
    original_assignedPinServerList = cloneArray(assignedPinServerList);
    // below loop is to populate available server list
    for(var i=1;i<directoryServerListLength;i++) { //creating available server list
        if(directoryServerList[i][6]=="0"){
		//donot add deleted directory service as available
		  //if($.trim(directoryServerList[i][1]) != ""){
		  if(directoryServerList[i][1] != ""){
            var tmpAssignedArray = new Array(totalProperties);
            tmpAssignedArray[0] = directoryServerList[i][0]; // ID
            tmpAssignedArray[1] = directoryServerList[i][1]; //Directory Service Name
            tmpAssignedArray[2] = directoryServerList[i][2]; // IP Address
            tmpAssignedArray[3] = directoryServerList[i][4];  // is windows server
            tmpAssignedArray[4] = directoryServerList[i][5]; // attrNameForUser
            tmpAssignedArray[5] = directoryServerList[i][7];  // attrNameForPIN
            tmpAssignedArray[6] = directoryServerList[i][12];  // attrNameForLDAPServerName
            availablePinServerList.push(tmpAssignedArray);
		  }
        }
    }

}

/*
 This method splits LDAP Assigned and Available server list
  */
 function fnLDAPAuthServerSplitter(directoryServerList,ldapList){
     if(ldapList == undefined || ldapList == null ||directoryServerList == undefined || directoryServerList == null ){
         alert('internal error : invalid data');
         return;
     }
     availableLDAPServerList = new Array();
     assignedLDAPServerList = new Array();
     var ldapListLength = ldapList.length; // always 16 as per req
     var directoryServerListLength  = directoryServerList.length; //total registered servers
     var totalProperties = 7; //total properties required for assigned and available list

     //initialize all directory service assigned flag
     for(var i=1;i<directoryServerListLength;i++) {
         directoryServerList[i][6] = "0"; // temp flag used to check whether DSN is assigned or unassigned in all the scenarios
     }


     // below loop is to populate assigned server list
     for(var i=0;i<ldapListLength;i++) {
         if (ldapList[i][0] != 0) { // if assigned
 			var foundInDS = false;
             for(var j=0;j<directoryServerListLength;j++){ //get the id of assigned server
                 if(directoryServerList[j][0]==ldapList[i][0]){ // merging both directory service and ldap client
 				    foundInDS = true;
                     var tmpAssignedArray = new Array(totalProperties);
                     tmpAssignedArray[0] = directoryServerList[j][0]; // ID
                     tmpAssignedArray[1] = directoryServerList[j][1]; // Directory Service Name
                     tmpAssignedArray[2] = directoryServerList[j][2]; // IP Address
                     tmpAssignedArray[3] = directoryServerList[j][3]; // Auth Method
                     tmpAssignedArray[4] = directoryServerList[j][4];  // is windows server
                     tmpAssignedArray[5] = directoryServerList[j][5];  // attrNameForUser
                     tmpAssignedArray[6] = "0" // is primary server - 0 is false

                     directoryServerList[j][6] = "1"; // mark this DSN as assigned
                     assignedLDAPServerList.push(tmpAssignedArray); // push the assigned server to assigned list
                     break;
                 }
             }
 				//if assigned LDAP ID is not found in Directory Service List
 				//Create a empty directory service with the same LDAP ID
 				if(!foundInDS){
 					var serverList=[];
 					serverList[0]= new Array(directoryServerList.length);
 					serverList[0][0] = ldapList[i][0];
 					//for(var cols=1;cols<=11;cols++){
 					for(var cols=1;cols<=12;cols++){
 						serverList[0][cols]="";
 					}

 					var tmpAssignedArray = new Array(totalProperties);
                     tmpAssignedArray[0] = serverList[0][0]; // ID
                     tmpAssignedArray[1] = ""; // Directory Service Name
                     tmpAssignedArray[2] = ""; // IP Address
                     tmpAssignedArray[3] = ""; // Auth Method
                     tmpAssignedArray[4] = "";  // is windows server
                     tmpAssignedArray[5] = "";  // attrNameForUser
                     tmpAssignedArray[6] = "0" // is primary server - 0 is false
                     serverList[0][6] = "1"; // mark this DSN as assigned

 					//directoryServerList.push(serverList);
                     assignedLDAPServerList.push(tmpAssignedArray); // push the new assigned server to assigned list
 				}
         }
     }
     original_assignedLDAPServerList = cloneArray(assignedLDAPServerList);
     // below loop is to populate available server list
     for(var i=1;i<directoryServerListLength;i++) { //creating available server list
         if(directoryServerList[i][6]=="0"){
 		//donot add deleted directory service as available
 		   //if($.trim(directoryServerList[i][1]) != ""){
 		   if(directoryServerList[i][1] != ""){
             var tmpAssignedArray = new Array(totalProperties);
             tmpAssignedArray[0] = directoryServerList[i][0]; // ID
             tmpAssignedArray[1] = directoryServerList[i][1]; //Directory Service Name
             tmpAssignedArray[2] = directoryServerList[i][2]; // IP Address
             tmpAssignedArray[3] = directoryServerList[i][3]; // Auth Method
             tmpAssignedArray[4] = directoryServerList[i][4];  // is windows server
             tmpAssignedArray[5] = directoryServerList[i][5];  // attrNameForUser
             tmpAssignedArray[6] = "0" // is primary server - 0 is false
             availableLDAPServerList.push(tmpAssignedArray);
 		  }
         }
     }
 }

 /* RCR_1766
 This method splits Card Auth LDAP Assigned and Available server list
  */
function fnCardAuthLDAPAuthServerSplitter(directoryServerList,ldapList){
    if(ldapList == undefined || ldapList == null ||directoryServerList == undefined || directoryServerList == null ){
        alert('internal error : invalid data');
        return;
    }
    availableCardAuthLDAPServerList = new Array();
    assignedCardAuthLDAPServerList = new Array();
    var ldapListLength = ldapList.length; // always 16 as per req
    var directoryServerListLength  = directoryServerList.length; //total registered servers
    var totalProperties = 7; //total properties required for assigned and available list

    //initialize all directory service assigned flag
    for(var i=1;i<directoryServerListLength;i++) {
        directoryServerList[i][6] = "0"; // temp flag used to check whether DSN is assigned or unassigned in all the scenarios
    }


    // below loop is to populate assigned server list
    for(var i=0;i<ldapListLength;i++) {
        if (ldapList[i][0] != 0) { // if assigned
            var foundInDS = false;
            for(var j=0;j<directoryServerListLength;j++){ //get the id of assigned server
                if(directoryServerList[j][0]==ldapList[i][0]){ // merging both directory service and ldap client
                    foundInDS = true;
                    var tmpAssignedArray = new Array(totalProperties);
                    tmpAssignedArray[0] = directoryServerList[j][0]; // ID
                    tmpAssignedArray[1] = directoryServerList[j][1]; // Directory Service Name
                    tmpAssignedArray[2] = directoryServerList[j][2]; // IP Address
                    tmpAssignedArray[3] = directoryServerList[j][3]; // Auth Method
                    tmpAssignedArray[4] = directoryServerList[j][4];  // is windows server
                    tmpAssignedArray[5] = directoryServerList[j][5];  // attrNameForUser
                    tmpAssignedArray[6] = "0" // is primary server - 0 is false

                    directoryServerList[j][6] = "1"; // mark this DSN as assigned
                    assignedCardAuthLDAPServerList.push(tmpAssignedArray); // push the assigned server to assigned list
                    break;
                }
            }
                //if assigned LDAP ID is not found in Directory Service List
                //Create a empty directory service with the same LDAP ID
                if(!foundInDS){
                    var serverList=[];
                    serverList[0]= new Array(directoryServerList.length);
                    serverList[0][0] = ldapList[i][0];
                    //for(var cols=1;cols<=11;cols++){
                    for(var cols=1;cols<=12;cols++){
                        serverList[0][cols]="";
                    }

                    var tmpAssignedArray = new Array(totalProperties);
                    tmpAssignedArray[0] = serverList[0][0]; // ID
                    tmpAssignedArray[1] = ""; // Directory Service Name
                    tmpAssignedArray[2] = ""; // IP Address
                    tmpAssignedArray[3] = ""; // Auth Method
                    tmpAssignedArray[4] = "";  // is windows server
                    tmpAssignedArray[5] = "";  // attrNameForUser
                    tmpAssignedArray[6] = "0" // is primary server - 0 is false
                    serverList[0][6] = "1"; // mark this DSN as assigned

                    //directoryServerList.push(serverList);
                    assignedCardAuthLDAPServerList.push(tmpAssignedArray); // push the new assigned server to assigned list
                }
        }
    }
    original_assignedCardAuthLDAPServerList = cloneArray(assignedCardAuthLDAPServerList);
    // below loop is to populate available server list
    for(var i=1;i<directoryServerListLength;i++) { //creating available server list
        if(directoryServerList[i][6]=="0"){
        //donot add deleted directory service as available
           //if($.trim(directoryServerList[i][1]) != ""){
           if(directoryServerList[i][1] != ""){
            var tmpAssignedArray = new Array(totalProperties);
            tmpAssignedArray[0] = directoryServerList[i][0]; // ID
            tmpAssignedArray[1] = directoryServerList[i][1]; //Directory Service Name
            tmpAssignedArray[2] = directoryServerList[i][2]; // IP Address
            tmpAssignedArray[3] = directoryServerList[i][3]; // Auth Method
            tmpAssignedArray[4] = directoryServerList[i][4];  // is windows server
            tmpAssignedArray[5] = directoryServerList[i][5];  // attrNameForUser
            tmpAssignedArray[6] = "0" // is primary server - 0 is false
            availableCardAuthLDAPServerList.push(tmpAssignedArray);
          }
        }
    }
}

/*
This method splits Email LDAP Assigned and Available server list
 */
function fnEmailLDAPAuthServerSplitter(directoryServerList,ldapList){
    if(ldapList == undefined || ldapList == null ||directoryServerList == undefined || directoryServerList == null ){
        alert('internal error : invalid data');
        return;
    }
    availableEmailLDAPServerList = new Array();
    assignedEmailLDAPServerList = new Array();
    var ldapListLength = ldapList.length; // always 4 as per req
    var directoryServerListLength  = directoryServerList.length; //total registered servers
    var totalProperties = 7; //total properties required for assigned and available list

    //initialize all directory service assigned flag
    for(var i=1;i<directoryServerListLength;i++) {
        directoryServerList[i][6] = "0"; // temp flag used to check whether DSN is assigned or unassigned in all the scenarios
    }

    // below loop is to populate assigned server list
    for(var i=0;i<ldapListLength;i++) {
        //if (ldapList[i] != "") { // if assigned
			var foundInDS = false;
            for(var j=0;j<directoryServerListLength;j++){ //get the id of assigned server
                if(directoryServerList[j][1]==ldapList[i]){ // merging both directory service and ldap client
				    foundInDS = true;
                    var tmpAssignedArray = new Array(totalProperties);
                    tmpAssignedArray[0] = directoryServerList[j][0]; // ID
                    tmpAssignedArray[1] = directoryServerList[j][1]; // Directory Service Name
                    tmpAssignedArray[2] = directoryServerList[j][2]; // IP Address
                    tmpAssignedArray[3] = directoryServerList[j][5];  // attrNameForUser
                    tmpAssignedArray[4] = directoryServerList[j][10];  // attrFromAddr
                    tmpAssignedArray[5] = directoryServerList[j][11];  // attrFromName
                    tmpAssignedArray[6] = "0" // is primary server - 0 is false

                    directoryServerList[j][6] = "1"; // mark this DSN as assigned
                    assignedEmailLDAPServerList.push(tmpAssignedArray); // push the assigned server to assigned list
                    break;
                }
            }
				//if assigned LDAP ID is not found in Directory Service List
				//Create a empty directory service with the same LDAP ID
				if(!foundInDS){
					var serverList=[];
					serverList[0]= new Array(directoryServerList.length);
					serverList[0][0] = ldapList[i][0];
					//for(var cols=1;cols<=11;cols++){
					for(var cols=1;cols<=12;cols++){
						serverList[0][cols]="";
					}

					var tmpAssignedArray = new Array(totalProperties);
                    tmpAssignedArray[0] = serverList[0][0]; // ID
                    tmpAssignedArray[1] = ""; // Directory Service Name
                    tmpAssignedArray[2] = ""; // IP Address
                    tmpAssignedArray[3] = ""; // attrNameForUser
                    tmpAssignedArray[4] = "";  // attrFromAddr
                    tmpAssignedArray[5] = "";  // attrFromName
                    tmpAssignedArray[6] = "0" // is primary server - 0 is false
                    serverList[0][6] = "1"; // mark this DSN as assigned

					//directoryServerList.push(serverList);
                    assignedEmailLDAPServerList.push(tmpAssignedArray); // push the new assigned server to assigned list
				}
        //}
    }
    original_assignedEmailLDAPServerList = cloneArray(assignedEmailLDAPServerList);
    // below loop is to populate available server list
    for(var i=1;i<directoryServerListLength;i++) { //creating available server list
        if(directoryServerList[i][6]=="0"){
		//donot add deleted directory service as available
		   //if($.trim(directoryServerList[i][1]) != ""){
		   if(directoryServerList[i][1] != ""){
            var tmpAssignedArray = new Array(totalProperties);
            tmpAssignedArray[0] = directoryServerList[i][0]; // ID
            tmpAssignedArray[1] = directoryServerList[i][1]; //Directory Service Name
            tmpAssignedArray[2] = directoryServerList[i][2]; // IP Address
            tmpAssignedArray[3] = directoryServerList[i][5]; // attrNameForUser
            tmpAssignedArray[4] = directoryServerList[i][10];  // attrFromAddr
            tmpAssignedArray[5] = directoryServerList[i][11];  // attrFromName
            tmpAssignedArray[6] = "0" // is primary server - 0 is false
            availableEmailLDAPServerList.push(tmpAssignedArray);
		  }
        }
    }
}


function fillLDAPdropdown(xmlObj) {
    if (xmlObj == null || xmlObj == undefined){
        alert(fnGetLocaleString("103472","Internal Error has occurred. Please try again."));
        return;
    }

    //25-03-2015
    parseDirectoryServiceInfo(xmlObj);
    parseWindowsAuthInfo(xmlObj,"windowsDomain"); //RCR_1766
    parseWindowsAuthInfo(xmlObj,"cardAuthWindowsDomain"); //RCR_1766
    parseLDAPAuthInfo(xmlObj,"LDAP");
    parseLDAPAuthInfo(xmlObj,"CardAuthLDAP"); //RCR_1766
    parseCardAuthInfo(xmlObj);
    parsePinAuthInfo(xmlObj);
    parseHomeDirAuthInfo(xmlObj);
    parseEmailLDAPAuthInfo(xmlObj);

    fnLDAPAuthServerSplitter(directoryServiceListArray,LDAPAuthListArray); // to split assigned and available LDAP servers
    fnPinAuthServerSplitter(directoryServiceListArray,pinAuthListArray); // to split assigned and available Pin Auth servers
    fnHomeDirAuthServerSplitter(directoryServiceListArray,homeDirAuthListArray); // to split assigned and available Home Directory Auth servers
    fnCardAuthServerSplitter(directoryServiceListArray,cardAuthListArray) // to split assigned and available Card Auth servers
    fnEmailLDAPAuthServerSplitter(directoryServiceListArray,EmailLDAPServerListArray);//to split assigned and available Email LDAP servers
    fnCardAuthLDAPAuthServerSplitter(directoryServiceListArray,cardLDAPAuthListArray); //RCR_1766 - to split assigned and available Card Auth LDAP servers 

    var ldapServicesObj = xmlObj.getElementsByTagName("DirectoryService");
    if (ldapServicesObj == null){
        gblLDAPServicesExists = false;
        return;
    }
    if (ldapServicesObj.length == 0){
        gblLDAPServicesExists = false;
        return;
    }
    //EMAILAUTH_SELENALDAPAUTH
    //SELENALDAPAUTH
    var isSelected4AuthPresent = false;
    var RBA_ldapComboObj = document.frmAuth.RBA_SELENALDAPAUTH;
    var EmailAuth_ldapComboObj = document.frmAuth.EMAILAUTH_SELENALDAPAUTH;
    var ldapID ;
    var ldapName ;
    var ldapSelectedIndex =0 ;
    var temp;
    //var AUTHTYPE_LDAPID = getXpathValue(xmlObj,"Authentication/AuthenticationSettings/AuthenticationType/Ldap/ldapServerID");
    var RBA_LDAPSELID = getXpathValue(xmlObj,"RBACManager/RbacSettings/rbacServerID");
    RBA_LDAPSELID_Global = RBA_LDAPSELID;
    var EmailAddr_LDAPSEL = getXpathValue(xmlObj,"Controller/Settings/AdminSystemSettings/Authentication/FromField/LDAPServerSetting/LDAPServer");
    var EmailAuth_LDAPSEL = getXpathValue(xmlObj,"Controller/Settings/AdminSystemSettings/Authentication/Scan2Email/LDAPAuthSetting/LDAPServer");
    var FromAddr_LdapServer = getXpathValue(xmlObj,"Controller/Settings/AdminSystemSettings/Authentication/FromField/FromName/LDAPServerSetting/LDAPServer");

	//08-May-2015
	var EmailAuth_ldapServerTypeComboObj = document.frmAuth.EMAILAUTH_LdapServerType;
	var EmailAuth_ldapAttrNameComboObj = document.frmAuth.EMAILAUTH_LdapAttrName;

	//default to first value if LDAP server is not set initially
	if(EmailAuth_LDAPSEL === undefined || EmailAuth_LDAPSEL === "undefined" || $.trim(EmailAuth_LDAPSEL)== ''){
	   if(ldapServicesObj.length > 1){
	    // 0 is always localhost. So assign 1st value
	    EmailAuth_LDAPSEL = ldapServicesObj[1].getElementsByTagName("name")[0].childNodes[0].nodeValue;
	   }
	}

	for(var i=0;i<ldapServicesObj.length;i++) {
        ldapID = ldapServicesObj[i].getAttribute("id");
        //ldapName = ((temp=ldapServicesObj[i].getAttribute("name"))==null)? "" : temp ;
        ldapName = ldapServicesObj[i].getElementsByTagName("name")[0].childNodes[0].nodeValue;
		var ldapType="";
		if(ldapServicesObj[i].getElementsByTagName("WindowsServer").length > 0){
		  if(ldapServicesObj[i].getElementsByTagName("WindowsServer")[0].childNodes.length>0){
		   ldapType = ldapServicesObj[i].getElementsByTagName("WindowsServer")[0].childNodes[0].nodeValue;
		  }
		}
		var ldapAttrName="";
		if(ldapServicesObj[i].getElementsByTagName("attrUserName").length > 0){
		 if(ldapServicesObj[i].getElementsByTagName("attrUserName")[0].childNodes.length>0){
		   ldapAttrName = ldapServicesObj[i].getElementsByTagName("attrUserName")[0].childNodes[0].nodeValue;
		 }
		}
        if(ldapID != "0") {
            RBA_ldapComboObj.options[i-1]=new Option(ldapName,ldapID);
            EmailAuth_ldapComboObj.options[i-1]=new Option(ldapName,ldapID);
			//08-May-2015 - Fundamental Server assignment and rearrange changes
			EmailAuth_ldapServerTypeComboObj.options[i-1] = new Option(ldapType,ldapName);
			EmailAuth_ldapAttrNameComboObj.options[i-1] = new Option(ldapAttrName,ldapName);

            if(ldapName == EmailAuth_LDAPSEL) {
                EmailAuth_ldapComboObj.options[i-1].selected = true;
				document.frmAuth.SERVERUSERNAME.value = ldapAttrName;
            }
            if(ldapID == RBA_LDAPSELID) {
                RBA_ldapComboObj.options[i-1].selected = true;
            }
        }
    }
}

function redirectAuthSettings(node) {
    var statusOper = getXpathValue(node,"AuthSettings/Response/statusOfOperation");
    if(statusOper=="STATUS_OK" || statusOper==0 || statusOper=="0" || statusOper == "Normal") {
        document.location.reload(false);
    } else {
        parent.fraTitle.document.forms[0].Save.disabled = false;
        var errMsg = authSettingErrHashMap[statusOper];
        if(errMsg != null && errMsg != undefined)
            alert(errMsg);
        else
            alert(fnGetLocaleString("103472","Internal Error has occurred. Please try again."));
    }
}


/*
 This method converts a 2D array to JSON format string
 * @param serverList  : any 2D array
 * @param totalProperties  : Number of columns which needs to be converted to JSON string [optional, if not provided all the columns will be taken]
 */
function convertArrayToJSON(serverList,totalProperties){
    var noOfEntries = serverList.length;
    if(totalProperties == undefined && serverList.length>0) {
        totalProperties = serverList[0].length;
    }else if(totalProperties == undefined && serverList.length<=0) {
        totalProperties=0;
    }
    var jsonFormatString ="[";
    for(var i=0; i <noOfEntries; i++) {
        jsonFormatString +="["
        for(j=0;j<totalProperties;j++) {
		    //var strstr = ($.trim(serverList[i][j]) == "") ? "&nbsp;" : serverList[i][j];
		    var strstr = serverList[i][j];
            //var convertedstring = strstr.replace(/\\/g,'&#92;').replace(/,/g,"&#44;").replace(/"/g,'\\"');
            var convertedstring = strstr.replace(/\\/g,'&#92;').replace(/"/g,'\\"');
            jsonFormatString = jsonFormatString + "\"" + convertedstring + "\"";
            if(j!=(totalProperties-1)){jsonFormatString+=",";}
        }
        jsonFormatString+="]";
        if(i!=noOfEntries-1){jsonFormatString+=",";}
    }
    jsonFormatString+="]";
    return jsonFormatString;
}

/*
 This method clones the array of any dimension
 */
function cloneArray(oldArray) {
    var i;
    var newArray = [];
    for(i=0; i<oldArray.length; i++){
        if(Object.prototype.toString.call(oldArray[i]) == '[object Array]') {
            newArray.push(cloneArray(oldArray[i]));
        } else{
            newArray.push(oldArray[i]);
        }
    }
    return newArray;
}

function construct2DArray(strval){
    var finalArray;
    var lstArray = strval.split(gblSep1);
    finalArray = new Array(lstArray.length - 1);
    for(var i=0; i <lstArray.length-1; i++) {
        var dataArray = lstArray[i].split(gblSep2);
        finalArray[i] = new Array(dataArray.length);
        for(var j=0;j<dataArray.length;j++){
            //finalArray[i][j]=dataArray[j];
            finalArray[i][j] = dataArray[j].length <= 0 ? "" : dataArray[j];
        }
    }
    return finalArray;
}

function renderTableFromTemplate(divId,settings){
  try{
     if(scrollableTable === null){
		$.get(Security_TableTemplateLocation, function (tableTemplate) {
			scrollableTable = Handlebars.compile(tableTemplate);
			var finalHTML = scrollableTable(settings);
			$("#"+divId).append(finalHTML);
            checkColumnWidth(divId);
		});
	}else{
		var finalHTML = scrollableTable(settings);
		$("#"+divId).append(finalHTML);
         checkColumnWidth(divId);
	}
 } catch(e){errHandler(e,'renderTableFromTemplate()','Authentication.html?v=1717026199ta',"")}
}

function checkColumnWidth(parentDivID){
    //28-Sep-2015 - Adjust Column Width
    if(parentDivID=="idLDAPAuthTable"){
        fixColumnWidth('ldapAuthTableHeader', 'ldapAuthTable');
    }else if(parentDivID=="idCardAuthLDAPAuthTable"){ //RCR_1766
        fixColumnWidth('cardAuthLdapAuthTableHeader', 'cardLdapAuthTable');
    }else if(parentDivID=="idPinAuthTable"){
        fixColumnWidth('pinAuthTableHeader', 'pinAuthTable');
    }else if(parentDivID=="idCardAuthTable"){
        fixColumnWidth('cardAuthTableHeader', 'cardAuthTable');
    }else if(parentDivID=="idHomeDirAuthTable"){
        fixColumnWidth('homeDirAuthTableHeader', 'homeDirAuthTable');
    }else if(parentDivID=="idEmailLDAPAuthTable"){
        fixColumnWidth('EmailldapAuthTableHeader', 'EmailldapAuthTable');
    }else if(parentDivID=="idWinDomainAuthTable"){
        fixColumnWidth('WinDomainAuthTableHeader', 'WinDomainAuthTable');
    }else if(parentDivID=="idCardAuthWinDomainAuthTable"){
        fixColumnWidth('cardAuthWinDomainTableHeader', 'cardWinDomainAuthTable');
    }
}

//28-Sep-2015 - Fix Server Assignment table width
/*
function fixColumnWidth(headerTable, dataTable, defaultWidth, windowResizeCall){
    var flag = false;
    var acceptableLength = 20;
    var tableContainsData = false;
//    if(dataTable=="EmailldapAuthTable"){
  //      acceptableLength = 22;
   // }
        $("#"+dataTable+" tr td").each(function(){
            tableContainsData = true;
            //if($(this).is(":visible")){
            if($(this).css('display').toLowerCase() != 'none'){
                var text = $(this).text();
                var length = text.length;
                if(text.length > acceptableLength){
                    flag=true;
                }
            }
        });

    //windowResizeCall will be true when this function is called on window resize or browser zoom in / zoom out
    if(!flag || windowResizeCall){
        //code to shrink the table back to normal size
        if(defaultWidth !== undefined){
            var totalWidth =0;
            $("#"+headerTable).find('tr:first td').each(function(index){
                if($(this).css('display').toLowerCase() != 'none') {
                    $(this).attr('width', defaultWidth + "px");
                    totalWidth = totalWidth + defaultWidth + 7;
                }
            });

            $("#"+headerTable).attr('width',totalWidth+'px');
            $("#"+dataTable).attr('width',totalWidth+'px');

            var bodyDivWidth = totalWidth +17; //17 for scroll width
            $("#"+headerTable).parent().css('width',totalWidth + "px");
            $("#"+dataTable).parent().css('width',bodyDivWidth + "px");

            $("#"+dataTable).find('tr').each(function(){
                $(this).find('td').each(function(index){
                    var t = '';
                        t = defaultWidth + "px";
                    //alert(t);
                        $(this).attr('width', t);
                });
            });
            $("#"+headerTable).css('table-layout','fixed');
            $("#"+dataTable).css('table-layout','fixed');
            $("#"+dataTable).hide();
            $("#"+headerTable).hide();
            $("#"+dataTable).show();
            $("#"+headerTable).show();
        }
        if(!windowResizeCall || !tableContainsData) {
            return; // dont do modification if column width is sufficient
        }
    }
    var headerWidthArray = new Array();
    var dataWidthArray = new Array();

    //removing table-layout fixed
    $("#"+headerTable).css('table-layout','');
    $("#"+dataTable).css('table-layout','');

    // calculate new width of header and data table
    // headerTable.attr('cellpadding','0');
    //dataTable.attr('cellpadding','0');
    $("#"+headerTable).find('tr:first td').each(function(){
        var t = 0;
        if($(this).css('display').toLowerCase() != 'none') {
            t = $(this).width(); // +7 ==> padding 6 + right border 1
        }
        headerWidthArray.push(t);
    });
    $("#"+dataTable).find('tr:first td').each(function(){
        //$(this).css('padding','3px'); //default padding for all cells
        var t = 0;
        if($(this).css('display').toLowerCase() != 'none') {
            t = $(this).width();
        }
            dataWidthArray.push(t);
    });

    var finalColWidthArray = new Array();
    var totalWidth = 0;

    for(var i=0; i<dataWidthArray.length;i++){
        if(dataWidthArray[i] > headerWidthArray[i]){
            finalColWidthArray.push(dataWidthArray[i]);
        }else{
            finalColWidthArray.push(headerWidthArray[i]);
        }
        if(finalColWidthArray[i]!=0) {
            totalWidth = totalWidth + (finalColWidthArray[i]) + 7;
        }
    }


    //for safari browser hide n display again if column width is modified
   // $("#"+dataTable).hide();
   // $("#"+headerTable).hide();

    $("#"+headerTable).find('tr:first td').each(function(index){
        if($(this).css('display').toLowerCase() != 'none') {
            $(this).attr('width', finalColWidthArray[index] + "px");
        }
    });

    $("#"+headerTable).attr('width',totalWidth+'px');
    $("#"+dataTable).attr('width',totalWidth+'px');

    var bodyDivWidth = totalWidth +17; //17 for scroll width
    $("#"+headerTable).parent().css('width',totalWidth + "px");
    $("#"+dataTable).parent().css('width',bodyDivWidth + "px");

    $("#"+dataTable).find('tr').each(function(){
        $(this).find('td').each(function(index){
                var t = '';
                if (index > finalColWidthArray.length - 1) {
                    //return false;
                    //do nothing
                } else {
                    t = finalColWidthArray[index] + "px";
                    $(this).attr('width', t);
                }
        });
    });
    $("#"+headerTable).css('table-layout','fixed');
    $("#"+dataTable).css('table-layout','fixed');
    $("#"+dataTable).show();
    $("#"+headerTable).show();
}
*/

//09-Aug-2016 - Fix Server Assignment table width
function fixColumnWidth(headerTable, dataTable, minWidth){
    try {
        var headerWidthArray = new Array();
        var dataWidthArray = new Array();
        var totalWidth = 0;
        var bodyDivWidth = 0;

        if (minWidth === undefined || minWidth < 213)
            minWidth = 213;

        //change table width to 0px
        $("#" + headerTable).attr('width', 0 + 'px');
        $("#" + dataTable).attr('width', 0 + 'px');

        //change div width to 0px
        $("#" + headerTable).parent().css('width', 0 + "px");
        $("#" + dataTable).parent().css('width', 0 + "px");


        //change each cell width to 0px
        $("#" + headerTable).find('tr:first td').each(function (index) {
            if ($(this).css('display').toLowerCase() != 'none') {
                $(this).attr('width', 0 + "px");
            }
        });


        if ($("#" + dataTable).find('td').length > 0) {
            $("#" + headerTable).find('tr:first td').each(function (index) {
                if ($(this).css('display').toLowerCase() != 'none') {
                    $(this).attr('width', 0 + "px");
                }
            });
        }

        //removing table-layout fixed
        $("#" + headerTable).css('table-layout', '');
        $("#" + dataTable).css('table-layout', '');

        // calculate new width of header table
        $("#" + headerTable).find('tr:first td').each(function () {
            var t = 0;
            if ($(this).css('display').toLowerCase() != 'none') {
                t = $(this).width();
                if (t > minWidth)
                    headerWidthArray.push(t);
                else
                    headerWidthArray.push(minWidth);
            }
            else
                headerWidthArray.push(t);

        });


        // calculate new width of data table
        if ($("#" + dataTable).find('td').length > 0) {

            // calculate new width of data table
            $("#" + dataTable).find('tr:first td').each(function () {
                //$(this).css('padding','3px'); //default padding for all cells
                var t = 0;
                if ($(this).css('display').toLowerCase() != 'none') {
                    t = $(this).width();
                    if (t > minWidth)
                        dataWidthArray.push(t);
                    else
                        dataWidthArray.push(minWidth);
                }
                else
                    dataWidthArray.push(t);
            });

            //calculate Final width and total width
            var finalColWidthArray = new Array();

            for (var i = 0; i < dataWidthArray.length; i++) {
                if (dataWidthArray[i] > headerWidthArray[i]) {
                    finalColWidthArray.push(dataWidthArray[i]);
                } else {
                    finalColWidthArray.push(headerWidthArray[i]);
                }
                if (finalColWidthArray[i] != 0) {
                    if (finalColWidthArray[i] != 0)
                        totalWidth = totalWidth + (finalColWidthArray[i]);
                    //if(firstCall)
                    totalWidth += 7;
                }
            }

            // apply final width to header table cells
            $("#" + headerTable).find('tr:first td').each(function (index) {
                if ($(this).css('display').toLowerCase() != 'none') {
                    $(this).attr('width', finalColWidthArray[index] + "px");
                }
            });

            // apply final width to tables
            $("#" + headerTable).attr('width', totalWidth + 'px');
            $("#" + dataTable).attr('width', totalWidth + 'px');

            bodyDivWidth = totalWidth;
            bodyDivWidth += 17; //17 for scroll width

            // apply final width to div
            $("#" + headerTable).parent().css('width', totalWidth + "px");
            $("#" + dataTable).parent().css('width', bodyDivWidth + "px");

            // apply final width to data table cells
            $("#" + dataTable).find('tr').each(function () {
                $(this).find('td').each(function (index) {
                    var t = '';
                    if (index > finalColWidthArray.length - 1) {

                    } else {
                        t = finalColWidthArray[index] + "px";
                        $(this).attr('width', t);
                    }
                });
            });
        }

        else { // if table contains no data
            //calculate total width
            for (i = 0; i < headerWidthArray.length; i++) {
                if (headerWidthArray[i] != 0) {
                    totalWidth = totalWidth + headerWidthArray[i];
                    //if (firstCall)
                    totalWidth += 7;
                }
            }

            // apply final width to header table cells
            $("#" + headerTable).find('tr:first td').each(function (index) {
                if ($(this).css('display').toLowerCase() != 'none') {
                    $(this).attr('width', headerWidthArray[index] + "px");
                }
            });


            bodyDivWidth = totalWidth + 17; //17 for scroll width

            // apply final width to tables
            $("#" + headerTable).attr('width', totalWidth + 'px');
            $("#" + dataTable).attr('width', bodyDivWidth + 'px');

            // apply final width to div
            $("#" + headerTable).parent().css('width', totalWidth + "px");
            $("#" + dataTable).parent().css('width', bodyDivWidth + "px");
        }
        //apply table-layout:fixed
        $("#" + headerTable).css('table-layout', 'fixed');
        $("#" + dataTable).css('table-layout', 'fixed');

        //hiding tbles (for opera and safari browsers)
        $("#" + dataTable).hide();
        $("#" + headerTable).hide();

        $("#" + dataTable).show();
        $("#" + headerTable).show();
    }
    catch (e){errHandler(e,'fixColumnWidth()-'+$("#" + dataTable),'Security.js?v=1717026199ta',"")}
}


function checkEmptyRow(listArr){
    for(var x=0;x<listArr.length;x++){
        if(listArr[x][0]==''){
          return true;
        }
    }
    return false;
}
