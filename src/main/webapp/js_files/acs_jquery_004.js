/*var oldSortData = $.fn.jqGrid.sortData;
$.jgrid.extend({
    editCell: function (iRow,iCol, ed){
        var ret;
        // do someting before
        ret = oldEditCell.call (this, iRow, iCol, ed);
        // do something after
        return ret; // return original or modified results
    }
});*/; 



$.jgrid.extend({
    
    refreshParams:{},
    propertiesDataProvider:function(value){
        value.each(function(pname,pvalue){
             this[pname]=pvalue;
        });
    },
    setMultiRowsSelection:function(aRowids){
        var ts= this[0];
        $.each(aRowids,function(int,id){
            $(ts).jqGrid("setSelection",id,true);
           
        });
    },
    invertSelection: function(){
       var ts= this[0]; 
       if(ts.p.multiselect){
           var checkHtml='<input title="Invert Selection" type="checkbox" class="cbox" id="cbinv_'+ts.p.id+'" role="checkbox">';
           $("#jqgh_"+ts.p.id+"_cb").append(checkHtml);
           $("#cbinv_"+ts.p.id).bind('click',function() {         
               ts.p.selarrrow = [];
               var froz = ts.p.frozenColumns === true ? ts.p.id + "_frozen" : "";
               $(ts.rows).each(function(i) {
                    if (i>0) {
                        if(!$(this).hasClass("ui-subgrid") && !$(this).hasClass("jqgroup") && !$(this).hasClass('ui-state-disabled')){
                            var checkName="jqg_"+$.jgrid.jqID(ts.p.id)+"_"+$.jgrid.jqID(this.id);             
                            var currentValue=$("#"+checkName ).attr("checked");
                            
                            if(currentValue=="checked")currentValue=false;
                            else currentValue=true;
                            
                            $("#jqg_"+$.jgrid.jqID(ts.p.id)+"_"+$.jgrid.jqID(this.id) )[ts.p.useProp ? 'prop': 'attr']("checked",currentValue);
                             
                            if(currentValue==true){                           
                                ts.p.selarrrow.push(this.id);
                                ts.p.selrow = this.id;
                                $(this).addClass("ui-state-highlight").attr("aria-selected","true");
                            }else{
                                $(this).removeClass("ui-state-highlight").attr("aria-selected","false");
                            }
                            if(froz) {
                                currentValue=$("#jqg_"+$.jgrid.jqID(ts.p.id)+"_"+$.jgrid.jqID(this.id), ts.grid.fbDiv ).attr("checked");
                                if(currentValue=="checked")currentValue=false;
                                else currentValue=true;
                                $("#jqg_"+$.jgrid.jqID(ts.p.id)+"_"+$.jgrid.jqID(this.id), ts.grid.fbDiv )[ts.p.useProp ? 'prop': 'attr']("checked",currentValue);
                                if(currentValue=="checked"){
                                    $("#"+$.jgrid.jqID(this.id), ts.grid.fbDiv).addClass("ui-state-highlight");
                                }
                            }
                        }
                    }
                    ts.p.selectedRowsArr=ts.p.selarrrow;
                    
                 });/*end each*/         
                 $("#cbinv_"+ts.p.id).attr("checked", false);
                 $("#cb_"+ts.p.id).attr("checked", false);
                 
              });/*end bind function*/
        }/*end if multiselect*/
    },/*end invertSelection function*/
    totalLocalSearchTooltip:"Live Search",
    totalLocalSearch:function(){
        var ts= this[0]; 
        var inputHtml='<img src="js_files/zoom.png" style="float:right; margin:2px; padding-right:16px;" alt="Search" width="16px" height="16px"><input title="'+this.totalLocalSearchTooltip+'" type="text" value="" id="totSearch'+(ts.p.id)+'" style="width:150px; padding: 2px 10px; float:right;">';
        $("#gview_"+ts.p.id).find('.ui-jqgrid-titlebar').append(inputHtml);
        $("#totSearch"+ts.p.id).bind('keyup',function() {
           var searchString = jQuery(this).val().toLowerCase();
           
           $(ts.rows).each(function(i,row) {     
             var hidden=true;
             if(i>0){
                $(row.cells).each(function(iCell,cell) {
                    if(ts.p.colModel[iCell].hidden==false && ts.p.colModel[iCell].hidedlg==false){
                        var textData=cell.innerHTML;
                        if(textData.indexOf('class="') < 0 && textData.toLowerCase().indexOf(searchString) != -1) {
                            hidden=false;
                            return false;
                      }
                    }
                });//each cell
                if(hidden==true){
                    jQuery('#'+this.id).hide();
                }else{
                    jQuery('#'+this.id).show();
               }
               }//if
           });//each    
       });/*bind*/  
    }/*end totalLocalSearch*/
});/*end*/

$.extend($.jgrid.defaults, {
	selectedTreeNodeArr:[],
	onClickGroup: function(hid,collapsed) {
		  if(collapsed==true){
			  this.p.selectedTreeNodeArr[hid]=true;
		  }else if(this.p.selectedTreeNodeArr[hid]!=undefined){
			  delete this.p.selectedTreeNodeArr[hid];
		  }
	},
    selectedRowsArr:[],
    onSelectRow:function (id, isSelected) {
        if(this.p.multiselect==true){
            this.p.selectedRowsArr=this.p.selarrrow;
        }else{
            this.selectedRowsArr=[];
            if(selected==true){
                this.p.selectedRowsArr.push(id);
            }
        }
    },
    onSelectAll:function(aRowids, status){
        if(status==true){
           this.p.selectedRowsArr=aRowids; 
        }else{
           this.p.selectedRowsArr=[];
        }    
    }
});

$.extend($.jgrid,{
    search : {
        caption: "Search",
        Find: "Find",
        Reset: "Reset",
        odata :   ["equals", "not equal","is less than","is less than or equal to","is greater than","is greater than or equal to","like","not like","is blank","is not blank","between","not between","is any of ","is none of"],
        groupOps : [{ op: "AND", text: "AND" }, { op: "OR",  text: "OR" },{ op: "NOT AND",  text: "NOT AND" },{ op: "NOT OR",  text: "NOT OR" }],
        matchText: " match",
        rulesText: " rules",
        ops : [
            {"name": "eq", "description": "equal", "operator":"="},
            {"name": "ne", "description": "not equal", "operator":"<>"},
            {"name": "lt", "description": "is less than", "operator":"<"},
            {"name": "le", "description": "is less than or equal to","operator":"<="},
            {"name": "gt", "description": "is greater than", "operator":">"},
            {"name": "ge", "description": "is greater than or equal to", "operator":">="},
            {"name": "bw", "description": "like", "operator":"LIKE"},
            {"name": "bn", "description": "not like", "operator":"NOT LIKE"},            
            {"name": "in", "description": "is any of", "operator":"IN"},
            {"name": "ni", "description": "is none of", "operator":"NOTIN"},
            {"name": "be", "description": "between", "operator":"BETWEEN"},
            {"name": "nb", "description": "not between", "operator":"NOTBETWEEN"},
            {"name": "nu", "description": "is null", "operator":"NULL"},
            {"name": "nn", "description": "is not null", "operator":"NOTNULL"}
        ]
    }
});

$.fn.fmatter.progressbar=function(cval, opts) {
	var idP=opts.pos+'_'+opts.rowId;   
	return '<div id="'+idP+'"><div id="'+idP+'_label" class="progress-label">'+cval+'%</div></div><script>$("#'+idP+'").progressbar({value: '+cval+'});</script>';
};

/*$.fn.fmatter.addControlImage=function {
    var colName=options['colModel']['name'];
    var gridId=options['gid'];
    var rowId=options['rowId'];
    var objId=rowObject.id;
    
    return '<img alt="Rimuovi riga" onclick="callCustomButtonAction(\''+gridId+'\',\''+rowId+'\',\''+colName+'\')" src="resources/icons/edit.gif" ></img>';
}*/





