var objectDictionary=new Array();
var gatewayURL=window.location.pathname + "DownloadsMonitorServlet";
var lastScrollPosition=0;


/*
 * CHIUDERE I GRUPPI
 * $('#58remote2').jqGrid('groupingToggle','58remote2ghead_0_0')
 */


 /*$(function() {
        $( document ).tooltip();
    });*/

function addToObjectDictionary(objectName, objectData){
    objectDictionary[objectName]=objectData;
}

var logInterval;
function tabChangeVisibility(panelName){
	if(panelName=="ngeo_if_LOGFILE"){
		loadLog();
		logInterval = setInterval(function(){loadLog();},10000);
	}else{
		clearInterval(logInterval);
	}	
}

function tabSwitch(targetId, tabIndex){
    
    var payloads={'tabswitch_params':{'objid':targetId}};
    
    /*
    *DALLE INTERFACCE FLEX VENIVANO MANDATI QUESTI PARAMETRI
    *{'objid': title, 'canvasreference': xxx , 'index': event.index, 'serverProperties':superTabServerProperties }
    */
    
    var data=objectDictionary[targetId];
    
    if(data==null){
        alert("InterfaceClass not found!!");
    }else{
        var interfaceClass=data['interfaceClass'];
        var interfaceId=data['interfaceId'];
        sibEventHandler(interfaceClass, 'TAB_SWITCH', targetId, payloads, interfaceId); 
    }
    
   
}

function loadLog(){
	sibEventHandler("pds_ngEO", 'LOAD_LOG', "logText", {}, "ngeo_if_LOGFILE"); 
}

function addToLogTextArea(response){
	var targetId=response['targetId'];
	var payload=response['payload'];
	var str=payload["content"];
	$('#'+targetId).empty();	
    $('#'+targetId).append(str.replace(/\n/g, "<br><br>"));	
}

function exportTo(targetId,exportType){
    var gridData={ vcols: _.filter( $("#"+targetId).getGridParam("colModel"),function(a){ return a.hidden==false;} )};
    var payloads={};
    jQuery.extend(payloads,startPayloadData,gridData);
    var idData=objectDictionary[targetId];
    
    var eventObject={_explicitType:'acs_sibAction',
                    interfaceClass:idData['interfaceClass'],
                    command:exportType,
                    targetId:targetId,
                    payloads:payloads,
                    interfaceId:idData['interfaceId']};
                    
    $('#exportForm').attr('action', gatewayURL);                
    if(exportType=='PRINT'){
        $('#exportForm').attr('target','_blank');
    }else{
        $('#exportForm').attr('target','_self');
    }
    
    var jsonData=$.base64Encode($.toJSON(eventObject));
     
    $('#acs_sibAction').attr("value", jsonData);
   
    $('#exportForm').submit();
 }
 
$("input[type=submit]").live("click", function(event){
    var id=event.currentTarget.id;
    var idData=objectDictionary[id];
    
    var payload=new Object();
    payload['properties']=new Object();
    
    var formName=idData['formName'];
    
    if(formName){
       _.each($("#"+formName).serializeArray(), function(item){
           payload['properties'][item['name']]=item['value'];
           $("#"+item['name']).attr('value','');
       });
       if($("#"+formName).find('table[class="ui-jqgrid-btable"]')){
          var gridId=$("#ngeo_if_resultForm").find('table[class="ui-jqgrid-btable"]').attr('id');
          var gridPayload ={};
          var postData=$("#"+gridId).getGridParam("postData");          
          jQuery.extend(gridPayload,postData,getSelectedItems(gridId));          
          payload[gridId]=gridPayload;
       }
    }
    
    var payloads={};
    payloads[id]=payload;
    
   // alert(event.currentTarget.id);
    sibEventHandler(idData['interfaceClass'], "click", id, payloads, idData['interfaceId']);
    event.preventDefault();
    
    return false;
});

function addControlsImage(cellvalue,options,rowObject){
    var colName=options['colModel']['name'];
    var gridId=options['gid'];
    var rowId=options['rowId'];
   // var objId=rowObject.id;
    //'<span class="ui-controlImage"></span>';
    return '<img alt="Rimuovi riga" onclick="callCustomButtonAction(\''+gridId+'\',\''+rowId+'\',\''+colName+'\')" src="./edit.gif" ></img>';
}

function callCustomButtonAction(gridId,rowId,colName){
    jQuery("#"+gridId).jqGrid('setSelection',rowId);
    
    var  gridData=objectDictionary[gridId];
    
    var interfaceClass=gridData['interfaceClass'];
    var interfaceId=gridData["interfaceId"];
    var payloads={};
    payloads.subtargetid=colName;
    
    var payload=new Object();
    payload=getSelectedItems(gridId);
    payload.properties=new Object();
    payload.properties.id=rowId;
    
    payloads[gridId]=payload;
    
	sibEventHandler(interfaceClass, 'fxgridevent_item_button_click', gridId, payloads, interfaceId);
	//jQuery("#"+gridId).GridToForm(rowId,"#FrmGrid_"+gridId);
}

function addPopUP(response){
	  
	 var payload=response['payload'];
	 var targetId=response['targetId'];
	 if($("#"+targetId)){
	      $("#"+targetId).remove();
	 }   
	 $('body').append(payload['content']);
}

function addTo(response){
   var targetId=response['targetId'];
   var payload=response['payload'];
    
   $('#'+targetId).append(payload['content']);
}

/*
*AGGIUNGE UN NUOVO TAB 
*/
function addTab(response){
   var targetId=response['targetId'];
   var arg={'interfaceId':response['interfaceId'], 'interfaceClass':response['interfaceClass']};
   var payload=response['payload'];
   
   $('#'+targetId).append(payload['content']);
   if(payload['tabTitle']!=undefined){
	   $('#'+targetId).find( ".ui-tabs-nav" ).append( payload['tabTitle'] ); 
	   $('#'+targetId).tabs( "refresh" );
   }
   addToObjectDictionary(targetId, arg );
}

function refreshGrid(targetId){
    $('#'+targetId).trigger("reloadGrid");
}

function execMethod(response){
   var targetId=response['targetId'];
   var payload=response['payload'];
   var methodName = response['methodName'];
   switch(methodName){
   	  case 'setFlowChartData':
    	   window[targetId+"_class"].setFlowChartData(payload['data']);
    	  // circulationChart_class.setFlowChartData(payload['data']);
    	   break;
   	   case '_ADDTO_OBJECT_DICTIONARY_':
            addToObjectDictionary(response['targetId'],response['payload']);
            break;
        case '_CLOSE_POPUP_':
            $('#'+targetId).remove();
            break;
        case 'triggerRefresh':
        	/*
        	 * STOP THE TRIGGER-REFRESH THE AUTO_REFRESH IS ACTIVE
        	 */
        	if($( "#ngeo_if_grid_slider" ).slider( "value" )==0){
        		 refreshGrid(targetId);
        	}          
            break;
        case 'addJSONData':
            $('#'+targetId).clearGridData();
	        var mygrid = jQuery('#'+targetId)[0];
	            
			var myjsongrid = eval('('+$.toJSON(payload['data'])+')');
			mygrid.addJSONData(myjsongrid);
			myjsongrid = null;
			jsonresponse =null;
			
			var groupStatus=$('#'+targetId).jqGrid("getGridParam", "selectedTreeNodeArr");		
			
			for(var groupName in groupStatus) {
				$('#'+targetId).jqGrid('groupingToggle',groupName);
			}
			
			var selectedRowsArr=$('#'+targetId).jqGrid("getGridParam", "selectedRowsArr");
			$('#'+targetId).jqGrid("setMultiRowsSelection",selectedRowsArr);
			$('#'+targetId).closest(".ui-jqgrid-bdiv").scrollTop(lastScrollPosition);
			
			
			

/*setGridParam({rowNum:newvalue})*/
			break;
        case 'setRowsData':
        	$.each(payload['data'],function(id,itemData){
        		$('#'+targetId).jqGrid('setRowData',id,itemData);
        	});
        	break;
  
        
        //_.invoke([$('#'+targetId)], jqGrid, payload);
        
    }
}



function getSelectedItems(targetId){
      var payload= new Object();
      if($('#'+targetId).getGridParam('multiselect')==true){
            /*
            * Gives the currently selected rows when multiselect is set to true
            */
            var selectedItems=$('#'+targetId).getGridParam("selarrrow");
             payload.selectedItems=new Array();
             _.each(selectedItems, function(rowId){               
                payload.selectedItems.push($('#'+targetId).getRowData(rowId));
            });
        }else{
            /*
            * selrow It contains the id of the last selected row
            */
            payload.selectedItems=new Object();
            payload.selectedItems.id=$('#'+targetId).getGridParam("selrow");
        }
       return payload;
}

function changeComboBox(interfaceClass, targetId, interfaceId, isGridActions){
    var comboValue=$('#'+targetId).val(); 
    var payloads={};
    var payload={};
    if(isGridActions){
        var targetIdArr=targetId.split("_actions");
        targetId=targetIdArr[0];
        payloads['subtargetid']=comboValue;
        payload=getSelectedItems(targetId);
       
    }else{        
        payload['type'] = 'ComboBox';
        payload['properties']= new Array();
        payload['properties']['selectedItemKey']=comboValue;
    }
    payloads[targetId ] = payload;
    sibEventHandler(interfaceClass, 'COMBOCHANGE', targetId, payloads, interfaceId);    
}


function sibExecCommand(actionlistArr){
    _.each(actionlistArr, function(response){
        switch(response['command']){
        	case 'LOAD_LOG':
     	   		addToLogTextArea(response);
     	   		break;
        	case 'ADDPOPUP':
                addPopUP(response);
                break;
            case 'ADDTO':
                addTo(response);
                break;
            case 'ADDTAB':
                addTab(response);
                break;
            case 'EXEC':
                execMethod(response);
                break;
            case 'MESSAGE':
            case 'ERROR':
            	alert(response['payload']['message']);
                break;
         }
                
    })
}


function sibEventHandler(interfaceClass, command, targetId, objectPayloads, interfaceId){
   if(gatewayURL==null){
        alert("gateway url is null");
        return false;
   }
   var payloads={};
   jQuery.extend(payloads,startPayloadData,objectPayloads);
   var eventObject={_explicitType:'acs_sibAction',
                    interfaceClass:interfaceClass,
                    command:command,
                    targetId:targetId,
                    payloads:payloads,
                    interfaceId:interfaceId};
   var jsonData1=$.toJSON(eventObject);                  
   var jsonData=$.base64Encode($.toJSON(eventObject));  
   $.ajax({
            type: "POST",
            url: gatewayURL,
            data: {'acs_sibAction':jsonData},
            success: function(actionlist){
                     var actionlistArr=$.parseJSON($.base64Decode(actionlist));
                     sibExecCommand(actionlistArr);
                     return false;
            },
            error:function(err){
            	$("#box_error_message").show();
            	$("#box_error_message").empty();
                $("#box_error_message").append("DM Server not responding: please close this web page");
            	
            	//alert(window.location.hostname + ': Error! Unable to connect to server.');
            }
            
        }); 
}


/*window.alert = function(title, message){
    var myElementToShow = document.getElementById("someElementId");
    myElementToShow.innerHTML = title + "</br>" + message; 
}*/
 
