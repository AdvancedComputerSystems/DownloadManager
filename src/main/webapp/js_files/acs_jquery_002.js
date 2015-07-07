$.widget('ui.tabs', $.ui.tabs, {
    options: {
    	tab_switch_notification:{},
		superTabServerProperties:{},
    	select: function(event, ui){   				
    				var panelId=ui.panel.id;
    				tabChangeVisibility(panelId);
    				var tabSwitchNotification=$(this).tabs( "option", "tab_switch_notification" );    				
    				if(tabSwitchNotification[panelId]){
    	           		var panelIndex=ui.index;
    	           		tabSwitch(panelId, panelIndex);
    				}
	    }
    
    },
    
    tab_switch_notification:function(targetId,value){
    	if(arguments.length>0){
    		if(this.options.tab_switch_notification==null){
        		this.options.tab_switch_notification=[];
        	}
        	this.options.tab_switch_notification[targetId]=value;
    	}else{
    		return this.options.tab_switch_notification;
    	}
    }
	


});