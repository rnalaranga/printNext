/** <pre>
 *-------------------------------------------------------------------------------

 *

 *-------------------------------------------------------------------------------
 * File: ServerAssignment.js?v=1717026199ta
 *-------------------------------------------------------------------------------
 * DESCRIPTION:
 * This script is used in server assignment popups
 *
 * AUTHOR		: Shiva Krishnan
 * PROJECT      : [TOPACCESS]
 *
 * Date Of Creation	: 25-Mar-2015
 *
 *-------------------------------------------------------------------------------
 * </pre>
 */

var sourceID="";
var ServerAssignment_TableTemplateLocation = "/HandlebarTemplates/scrollableTableTemplate.html?v=1717026199ta";

function loadServerAssignment(availableServerList,assignedServerList){
    sourceID = window.opener.$("#spnButtonSource").val();
    var JSONFormatString = "";
    JSONFormatString = convertArrayToJSON(availableServerList);
    loadAvailableTable(JSONFormatString);
    JSONFormatString = convertArrayToJSON(assignedServerList);
    loadAssignedTable(JSONFormatString);
}

function loadAvailableTable(serverList) {	
	var columnWidth = "";
	var tabHeader = "";
    if(sourceID=="LDAP" || sourceID == "cardAuthLDAP"){ //RCR_1766
		columnWidth = ['0%','50%','50%','0%','0%','0%','0%'];
        tabHeader = ['',lblDirectoryServiceName, lblServerIPAddress,'','','',''];		
    }else if(sourceID=="PIN"){
        columnWidth = ['0%','50%','50%','0%','0%','0%','0%']; //DCR: EBX_DCR_53951: PIN/Card Authentication
		tabHeader = ['',lblDirectoryServiceName, lblServerIPAddress,'','','',''];		
    }else if(sourceID=="HOMEDIRBTN"){
        columnWidth = ['0%','50%','50%','0%'];
		tabHeader = ['',lblDirectoryServiceName, lblServerIPAddress,''];
    }else if(sourceID=="CARDAUTHBTN"){
        columnWidth = ['0%','50%','50%','0%','0%','0%','0%','0%']; //DCR: EBX_DCR_53951: PIN/Card Authentication];
        tabHeader = ['',lblDirectoryServiceName, lblServerIPAddress,'','','','',''];
    }else if(sourceID=="EmailLDAP"){
        columnWidth = ['0%','50%','50%','0%','0%','0%','0%'];
        tabHeader = ['',lblDirectoryServiceName, lblServerIPAddress,'','','',''];
    }else if(sourceID=="EmailLDAP_FAX"){
        columnWidth = ['0%','50%','50%','0%','0%','0%','0%'];
        tabHeader = ['',lblDirectoryServiceName, lblServerIPAddress,'','','',''];
    }
    
    serverList = jQuery.parseJSON(serverList)
    for(i in serverList) {
        for (j in serverList[i]){
                serverList[i][j] = fnnConvertHtmlEntity(serverList[i][j]);
                // EBX_STFR_22449
                // This change is required to fix above issue in LDAP server List popup
                serverList[i][j] = serverList[i][j].replace(/ /g, '\u00A0');

        }
        if($.trim(serverList[i][1]) == ""){
            if(serverList[i][1].indexOf('　') == -1)
                serverList[i][1] = "&nbsp;";
        }
    }
    
    
	var Lsettings = {
        "caption": lblAvailableServer,
        "tabHeader": tabHeader,
        //"tabData": jQuery.parseJSON(serverList),
        "tabData": serverList,
		"columnWidth": columnWidth,
		"tableWidth" : 300,
		"tableHeight" : '160px',
		"headerTableID" : 'leftTableHeader',
		"dataTableID" : 'leftTable'	
    };
	$.get(ServerAssignment_TableTemplateLocation, function (tableTemplate) {
			var scrollableTable = Handlebars.compile(tableTemplate);
			var finalHTML = scrollableTable(Lsettings);
			$("#LTableHolder").append(finalHTML);
            fixColumnWidth('leftTableHeader','leftTable');
        //for scroll bar
        var bodyMinWidth = $("#buttondiv").width() + $("#LTableHolder").width() + $("#RTableHolder").width() + 68;
        $("body").css('min-width',bodyMinWidth+'px');
    });
}

function loadAssignedTable(serverList) {
   	
	var columnWidth = "";
	var tabHeader = "";
    if(sourceID=="LDAP" || sourceID == "cardAuthLDAP"){
		columnWidth = ['0%','50%','50%','0%','0%','0%','0%'];
        tabHeader = ['',lblDirectoryServiceName, lblServerIPAddress,'','','',''];		
    }else if(sourceID=="PIN"){
        columnWidth = ['0%','50%','50%','0%','0%','0%','0%']; //DCR: EBX_DCR_53951: PIN/Card Authentication
		tabHeader = ['',lblDirectoryServiceName, lblServerIPAddress,'','','',''];		
    }else if(sourceID=="HOMEDIRBTN"){
        columnWidth = ['0%','50%','50%','0%'];
		tabHeader = ['',lblDirectoryServiceName, lblServerIPAddress,''];
    }else if(sourceID=="CARDAUTHBTN"){
        columnWidth = ['0%','50%','50%','0%','0%','0%','0%','0%']; //DCR: EBX_DCR_53951: PIN/Card Authentication];
        tabHeader = ['',lblDirectoryServiceName, lblServerIPAddress,'','','','',''];
    }else if(sourceID=="EmailLDAP"){
        columnWidth = ['0%','50%','50%','0%','0%','0%','0%'];
        tabHeader = ['',lblDirectoryServiceName, lblServerIPAddress,'','','',''];
    }else if(sourceID=="EmailLDAP_FAX"){
        columnWidth = ['0%','50%','50%','0%','0%','0%','0%'];
        tabHeader = ['',lblDirectoryServiceName, lblServerIPAddress,'','','',''];
    }
    
    
    serverList = jQuery.parseJSON(serverList)
    for(i in serverList) {
        for (j in serverList[i]){
                serverList[i][j] = fnnConvertHtmlEntity(serverList[i][j]);
                   // EBX_STFR_22449
                // This change is required to fix above issue in LDAP server List popup
                serverList[i][j] = serverList[i][j].replace(/ /g, '\u00A0');
        }
        if($.trim(serverList[i][1]) == ""){
            if(serverList[i][1].indexOf('　') == -1)
                serverList[i][1] = "&nbsp;";
        }
    }

	var Rsettings = {
        "caption": lblAssignedServer,
        "tabHeader": tabHeader,
        //"tabData": jQuery.parseJSON(serverList),
        "tabData": serverList,
		"columnWidth": columnWidth,
		"tableWidth" : 300,
		"tableHeight" : '160px',
		"headerTableID" : 'rightTableHeader',
		"dataTableID" : 'rightTable'	
    };
	$.get(ServerAssignment_TableTemplateLocation, function (tableTemplate) {
			var scrollableTable = Handlebars.compile(tableTemplate);
			var finalHTML = scrollableTable(Rsettings);
			$("#RTableHolder").append(finalHTML);
        fixColumnWidth('rightTableHeader','rightTable');
        //for scroll bar
        var bodyMinWidth = $("#buttondiv").width() + $("#LTableHolder").width() + $("#RTableHolder").width() + 68;
        $("body").css('min-width',bodyMinWidth+'px');
    });
}

function moveRow(srcTableID,destTableId,id,action){
    var td = $("#"+ srcTableID +" tr.selected").children('td');
	var otherServer = fnGetLocaleString("103323","LDAP Server (Other than Windows Server)");
	var windowsServer = fnGetLocaleString("102028","Windows Server"); 

    if(!td.length )
        return;

    var colData = [];
    var columnWidth = [];

    //validation before move
	 var selectedDomainName = $.trim(td[1].innerText || td[1].textContent);
	 //var doNotAdd = false;
	 var doNotValidate = false;
	 if(action=="delete"){
		doNotValidate = true;
	 }
    if(selectedDomainName == ""){
        //doNotAdd = true; //in case of empty row
        //Support double byte space in Sever Name
        if((td[1].innerText.indexOf('　') == -1) || (td[1].textContent.indexOf('　') == -1)){
            //in case of empty row deletion, donot add it to available table
	        $("#"+ srcTableID +" tr.selected").remove();
	        return;
        }
    }
    //}else if(id=="LDAP"){ //RCR_1766
    if(id=="LDAP" || id == "cardAuthLDAP"){
        //var authMethod = $.trim(td[3].innerText || td[3].textContent);
        //var isWindowsServer = $.trim(td[4].innerText || td[4].textContent);
        //var userName = $.trim(td[5].innerText || td[5].textContent);
        var authMethod = (td[3].innerText || td[3].textContent);
        var isWindowsServer = (td[4].innerText || td[4].textContent);
        var userName = (td[5].innerText || td[5].textContent);
        if(!doNotValidate && (isWindowsServer === otherServer)){
            //if(userName==undefined || userName.length<=0 || userName === "&nbsp;"){
            if(userName==undefined || userName.length<=0){
                alert(lblErrorMsg + "\n (" + lblErrorSpec + ")");
                return;
            }
        }
        /*
        * Firefox, Safari Opera supports textContext
         * Chrome, IE supports text content
         */
        columnWidth = ['0%','50%','50%','0%','0%','0%','0%'];
        for(var i=0;i<=6;i++){
		    //var Cval =  $.trim(td[i].innerText || td[i].textContent);
		    var Cval = (td[i].innerText || td[i].textContent);
			if(Cval.length){
				colData.push(Cval);
			}else{
				//colData.push("&nbsp;");
				colData.push("");
			}
        }
    }else if(id == "PIN"){
        //var attrNameForUser = $.trim(td[4].innerText || td[4].textContent);
        //var attrNameForPIN = $.trim(td[5].innerText || td[5].textContent);
		//var isWindowsServer = $.trim(td[3].innerText || td[3].textContent);
        var attrNameForUser = (td[4].innerText || td[4].textContent);
        var attrNameForPIN = (td[5].innerText || td[5].textContent);
		var isWindowsServer = (td[3].innerText || td[3].textContent);
		if(!doNotValidate){
			//if(attrNameForPIN==undefined || attrNameForPIN.length<=0 || attrNameForPIN === "&nbsp;"){
			if(attrNameForPIN==undefined || attrNameForPIN.length<=0){
					alert(lblErrorMsg + "\n (" + lblErrorSpec1 + ")");
					return;
			}
			if(isWindowsServer == otherServer){
				//if(attrNameForUser==undefined || attrNameForUser.length<=0 || attrNameForUser === "&nbsp;" ){
				if(attrNameForUser==undefined || attrNameForUser.length<=0 ){
					alert(lblErrorMsg + "\n (" + lblErrorSpec + ")");
					return;
				}
			} 			
		}
		columnWidth = ['0%','50%','50%','0%','0%','0%','0%']; //DCR: EBX_DCR_53951: PIN/Card Authentication
        //for(var i=0;i<=5;i++){
        for(var i=0;i<=6;i++){
            //var Cval =  $.trim(td[i].innerText || td[i].textContent);
            var Cval = (td[i].innerText || td[i].textContent);
			if(Cval.length){
				colData.push(Cval);
			}else{
				//colData.push("&nbsp;");
				colData.push("");
			}
        }
    }else if(id == "HOMEDIRBTN"){
        //var attrNameForUser = $.trim(td[3].innerText || td[3].textContent);
        columnWidth = ['0%','50%','50%','0%'];
        for(var i=0;i<=3;i++){
            //var Cval =  $.trim(td[i].innerText || td[i].textContent);
            var Cval = (td[i].innerText || td[i].textContent);
			if(Cval.length){
				colData.push(Cval);
			}else{
				//colData.push("&nbsp;");
				colData.push("");
			}
        }
    }else if(id == "CARDAUTHBTN"){
		//var attrNameForUser = $.trim(td[4].innerText || td[4].textContent);
		//var isWindowsServer = $.trim(td[3].innerText || td[3].textContent);
		var attrNameForUser = (td[4].innerText || td[4].textContent);
		var isWindowsServer = (td[3].innerText || td[3].textContent);
		
		if(!doNotValidate && (isWindowsServer == otherServer)){
			//if(attrNameForUser==undefined || attrNameForUser.length<=0 || attrNameForUser === "&nbsp;"){
			if(attrNameForUser==undefined || attrNameForUser.length<=0){
				alert(lblErrorMsg + "\n (" + lblErrorSpec + ")");
				return;
			}
		}
        columnWidth = ['0%','50%','50%','0%','0%','0%','0%','0%']; //DCR: EBX_DCR_53951: PIN/Card Authentication];
        //for(var i=0;i<=6;i++){
        for(var i=0;i<=7;i++){ //DCR: EBX_DCR_53951: PIN/Card Authentication
            //var Cval =  $.trim(td[i].innerText || td[i].textContent);
            var Cval = (td[i].innerText || td[i].textContent);
			if(Cval.length){
				colData.push(Cval);
			}else{
				//colData.push("&nbsp;");
				colData.push("");
			}
        }
    }else if(id=="EmailLDAP"){
         //var emailAddr = $.trim(td[4].innerText || td[4].textContent);
         //var fromName = $.trim(td[5].innerText || td[5].textContent);
         //var userName = $.trim(td[3].innerText || td[3].textContent);
         var emailAddr = (td[4].innerText || td[4].textContent);
         var fromName = (td[5].innerText || td[5].textContent);
         var userName = (td[3].innerText || td[3].textContent);
         if(!doNotValidate){
             //if(userName==undefined || userName.length<=0 || userName === "&nbsp;"){
             if(userName==undefined || userName.length<=0){
                 alert(lblErrorMsg + "\n (" + lblErrorSpec + ")");
                 return;
             }
             //if(emailAddr==undefined || emailAddr.length<=0 || emailAddr === "&nbsp;"){
             if(emailAddr==undefined || emailAddr.length<=0){
                 alert(lblErrorMsg + "\n (" + fnGetLocaleString("100588","Email Address") + ")");
                 return;
             }
             //if(fromName==undefined || fromName.length<=0 || fromName === "&nbsp;"){
             if(fromName==undefined || fromName.length<=0){
                 alert(lblErrorMsg + "\n (" + fnGetLocaleString("100774","From Name") + ")");
                 return;
             }
         }
         /*
         * Firefox, Safari Opera supports textContext
          * Chrome, IE supports text content
          */
         columnWidth = ['0%','50%','50%','0%','0%','0%','0%'];
         for(var i=0;i<=6;i++){
            //var Cval =  $.trim(td[i].innerText || td[i].textContent);
            var Cval =  (td[i].innerText || td[i].textContent);
            if(Cval.length){
                colData.push(Cval);
            }else{
                //colData.push("&nbsp;");
                colData.push("");
            }
         }
    }else if(id=="EmailLDAP_FAX"){
        //var emailAddr = $.trim(td[4].innerText || td[4].textContent);
        //var userName = $.trim(td[3].innerText || td[3].textContent);
        var emailAddr = (td[4].innerText || td[4].textContent);
        var userName = (td[3].innerText || td[3].textContent);
        if(!doNotValidate){
            //if(userName==undefined || userName.length<=0 || userName === "&nbsp;"){
            if(userName==undefined || userName.length<=0){
                alert(lblErrorMsg + "\n (" + lblErrorSpec + ")");
                return;
            }
            //if(emailAddr==undefined || emailAddr.length<=0 || emailAddr === "&nbsp;"){
            if(emailAddr==undefined || emailAddr.length<=0){
                alert(lblErrorMsg + "\n (" + fnGetLocaleString("100588","Email Address") + ")");
                return;
            }
        }
        /*
         * Firefox, Safari Opera supports textContext
         * Chrome, IE supports text content
         */
        columnWidth = ['0%','50%','50%','0%','0%','0%','0%'];
        for(var i=0;i<=6;i++){
            //var Cval =  $.trim(td[i].innerText || td[i].textContent);
            var Cval = (td[i].innerText || td[i].textContent);
            if(Cval.length){
                colData.push(Cval);
            }else{
                //colData.push("&nbsp;");
                colData.push("");
            }
        }
    }

     /*if(doNotAdd == true){
	   //in case of empty row deletion, donot add it to available table
	   $("#"+ srcTableID +" tr.selected").remove();
	   return;
	 }*/
    var col="";
    $.each(colData, function( colindex, value ) {
	    if(columnWidth[colindex] == '0%' || columnWidth[colindex] == '0px'){
         col= col + "<td class ='clsBTableElement' style='display:none;' width='"+ columnWidth[colindex] +"'>" + fnConvertXMLEntities(value); + "</td>";
		}else{
		 col= col + "<td class ='clsBTableElement' style='border:1px solid #778899; border-left:0px;border-top:0px; white-space:nowrap;' width='"+ columnWidth[colindex] +"'><span style='white-space:nowrap '>" + fnConvertXMLEntities(value) + "</span></td>";
		}
    });
    var row="<tr>"+ col +"</tr>";
    $("#"+destTableId).append(row);
    fixColumnWidth(destTableId+"Header", destTableId);
    $("#"+ srcTableID +" tr.selected").remove();
    fixColumnWidth(srcTableID+"Header", srcTableID);

    //for scroll bar
    var bodyMinWidth = $("#buttondiv").width() + $("#LTableHolder").width() + $("#RTableHolder").width() + 68;
    $("body").css('min-width',bodyMinWidth+'px');
}

function fnSaveAuthChanges(){
    var noOfRows=0;
    var noOfCols=0;
    var tmpObj = null;
    var id;
    noOfRows =  $('#rightTable tr').length;
    noOfCols = $("#rightTable").find("tr:first td").length;

    // populate new Assigned Server List
    var rowDatas = new Array(noOfRows);
    $("#rightTable tr").each(function(i, row){
        rowDatas[i] = Array(noOfCols);
        id= $(this).find('td:first').text();
       /* $(this).children('td').each(function(j, data){
		    var Cval = $.trim($(this).text());
            rowDatas[i][j] = Cval.length <= 0 ? "&nbsp;" : fnConvertXMLEntities(Cval);
        });*/
        tmpObj = null;
        for(var k=0;k<availableServerList2DArray_org.length;k++){
            if(availableServerList2DArray_org[k][0] == id){
                tmpObj = availableServerList2DArray_org[k];
                break;
            }
        }
        if(tmpObj == null){
            for(var k=0;k<assignedServerList2DArray_org.length;k++){
                //ReportSetting and Email LDAP, if there is blank line, id is Server Name.
                //If Server Name is "&" -> id is &#38;.
                if(fnnConvertHtmlEntity(assignedServerList2DArray_org[k][0]) == id){
                    tmpObj = assignedServerList2DArray_org[k];
                    break;
                }
            }
        }
        $(tmpObj).each(function(j, data){
            //var Cval = $.trim(data);
            var Cval = data;
            //rowDatas[i][j] = Cval.length <= 0 ? "&nbsp;" : Cval;
            rowDatas[i][j] = Cval.length <= 0 ? "" : Cval;
        });
    })
//    window.parent.assignedLDAPServerList = rowDatas;
    assignedServerList2DArray = rowDatas;

    // populate new Available Server List
    noOfRows =  $('#leftTable tr').length;
    rowDatas = new Array(noOfRows);
    $("#leftTable tr").each(function(i, row){
        rowDatas[i] = Array(noOfCols);
        id= $(this).find('td:first').text();
        /*$(this).children('td').each(function(j, data){
            rowDatas[i][j] = $.trim($(this).text());
        });*/
        tmpObj = null;
        for(var k=0;k<availableServerList2DArray_org.length;k++){
            if(availableServerList2DArray_org[k][0] == id){
                tmpObj = availableServerList2DArray_org[k];
                break;
            }
        }
        if(tmpObj == null){
            for(var k=0;k<assignedServerList2DArray_org.length;k++){
                if(fnnConvertHtmlEntity(assignedServerList2DArray_org[k][0]) == id){
                    tmpObj = assignedServerList2DArray_org[k];
                    break;
                }
            }
        }
        $(tmpObj).each(function(j, data){
            //rowDatas[i][j] = $.trim(data);
            rowDatas[i][j] = data;
        });
    })
    //window.parent.availableLDAPServerList = rowDatas;
    availableServerList2DArray = rowDatas;

    if(sourceID=="LDAP"){
        //var colWidth = ['0%','15%','18%','0%','20%', '20%','0%'];
        var colWidth = ['0px','213px','213px','0px','213px', '213px','0px'];
        rePopulateParentTable("ldapAuthTable",colWidth,assignedServerList2DArray);
    }else if(sourceID=="cardAuthLDAP"){ //RCR_1766
	    var colWidth = ['0px','213px','213px','0px','213px', '213px','0px']; 
        rePopulateParentTable("cardLdapAuthTable",colWidth,assignedServerList2DArray);
    }else if(sourceID=="PIN"){
	    var colWidth=['0px','213px','213px','213px','213px', '213px','0px']; //DCR: EBX_DCR_53951: PIN/Card Authentication
        rePopulateParentTable("pinAuthTable",colWidth,assignedServerList2DArray);
    }else if(sourceID=="HOMEDIRBTN"){
        var colWidth=['0px','213px','213px','0px'];
        rePopulateParentTable("homeDirAuthTable",colWidth,assignedServerList2DArray);
    }else if(sourceID=="CARDAUTHBTN"){
        // 08 code to decide whether to show/hide card issue time column in card auth table
        diagnostic_08_3644=window.opener.top.fnTrackCookie("DiagnosticMode","GET","3644");
        var colWidth=[];
		if(diagnostic_08_3644=="1") { // show Card Issue Time Column
			colWidth = ['0px','213px','213px','213px','213px','213px','213px','0px']; //DCR: EBX_DCR_53951: PIN/Card Authentication];
        }else{ // hide Card Issue Time Column
			colWidth = ['0px','213px','213px','213px','213px','213px','0px','0px']; //DCR: EBX_DCR_53951: PIN/Card Authentication];
        }
        rePopulateParentTable("cardAuthTable",colWidth,assignedServerList2DArray);
    }else if(sourceID=="EmailLDAP"){
        var colWidth = ['0px','213px','213px','213px', '213px','213px','0px'];
        rePopulateParentTable("EmailldapAuthTable",colWidth,assignedServerList2DArray);
    }else if(sourceID=="EmailLDAP_FAX"){
        var colWidth = ['0px','213px','213px','213px', '213px','0px','0px'];
        rePopulateParentTable("EmailldapAuthTable",colWidth,assignedServerList2DArray);
    }
    fillHiddenAvailableAndAssignedText(availableServerList2DArray,assignedServerList2DArray);

    opener.closePopup();
}


function fillHiddenAvailableAndAssignedText(available,assigned){
    var availableString = "";
    var assignedString = "";
    $.each(available, function(index, item) {
        availableString = availableString + item.join(gblSep2) + gblSep1;
    });
    window.opener.$("#spnAvailableServerList").val(availableString);
    $.each(assigned, function(index, item) {
        assignedString = assignedString + item.join(gblSep2) + gblSep1;
    });
    window.opener.$("#spnAssignedServerList").val(assignedString);
}


// Repopulate parent table with new Assigned values
function rePopulateParentTable(parentTableID,colWidth,assignedListArr){
    window.opener.$("#"+ parentTableID + " tr").remove();
    var row="";
    var tmpObj ;
    $.each(assignedListArr , function( index, obj ) {//availableServerList2DArray
        row = "<tr>";
        tmpobj = null;
        for(var i=0;i<availableServerList2DArray_org.length;i++){
            if(availableServerList2DArray_org[i][0] == obj[0]){
                tmpobj = availableServerList2DArray_org[i];
            }
        }
        if(tmpObj == null){
            for(var i=0;i<assignedServerList2DArray_org.length;i++){
                if(assignedServerList2DArray_org[i][0] == obj[0]){
                    tmpobj = assignedServerList2DArray_org[i];
                }
            }
        }
        $.each(tmpobj, function( colindex, value ) {
		    if(colWidth[colindex] == '0%' || colWidth[colindex] == '0px'){
				//row= row + "<td class='clsBTableElement' style='display:none;' width='"+ colWidth[colindex] +"'>" + fnnConvertHtmlEntity(value) + "</td>";
                row= row + "<td class='clsBTableElement' style='display:none;' width='"+ colWidth[colindex] +"'>" + fnnConvertHtmlEntity(value) + "</td>";
			}else{
                //row= row + "<td class='clsBTableElement' style='border:1px solid #778899; border-left:0px;border-top:0px;' width='"+ colWidth[colindex] +"'><span style='white-space:nowrap'>" + fnnConvertHtmlEntity(value) + "</span></td>";
				row= row + "<td class='clsBTableElement' style='border:1px solid #778899; border-left:0px;border-top:0px;' width='"+ colWidth[colindex] +"'><span style='white-space:nowrap'>" + fnnConvertHtmlEntity(value) + "</span></td>";
			}
        });
        row=row+"</tr>";
        window.opener.$("#"+ parentTableID).append(row);

    });

    //28-09-2015 - Adjust Column Width
    if(parentTableID=="ldapAuthTable"){
        window.opener.fixColumnWidth('ldapAuthTableHeader', 'ldapAuthTable');
    }else if(parentTableID=="pinAuthTable"){
        window.opener.fixColumnWidth('pinAuthTableHeader', 'pinAuthTable');
    }else if(parentTableID=="cardAuthTable"){
        window.opener.fixColumnWidth('cardAuthTableHeader', 'cardAuthTable');
    }else if(parentTableID=="homeDirAuthTable"){
        window.opener.fixColumnWidth('homeDirAuthTableHeader', 'homeDirAuthTable');
    }else if(parentTableID=="EmailldapAuthTable"){
        window.opener.fixColumnWidth('EmailldapAuthTableHeader', 'EmailldapAuthTable');
    }else if(parentTableID=="cardLdapAuthTable"){ //RCR_1766
        window.opener.fixColumnWidth('cardAuthLdapAuthTableHeader', 'cardLdapAuthTable');;
    }
}

