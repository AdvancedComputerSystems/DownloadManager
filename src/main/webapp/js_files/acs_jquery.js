/*
 * jQuery DateTime Plugin 1.0.0
 * 
 * Author:Francesca Bitto 
 * 
 * Copyright 2012-2013 Advanced Computer Systems S.p.A. (A.C.S. S.p.A.)  
 *
 */
( function($) {
	// definizione dell'estensione

	$.widget( "ui.fxDateTime", {

		version: "1.0.0",

		options : {
			menuListData          : ["now","today","disabled","enabled"],
			yearNavigationEnabled : true,
			viewDateField         : true,
			viewTimeField         : true,
			enabledDateTime       : true,
			setShowToday          : true,
			dateFormatString      : "mm/dd/yy",
			timeFormatString      : "HH:MM:SS.sss",
			//viewCheckBox          : false,
			//hasEvents             : false,
			aftermsec             : "",

			/*
			 *SI ASPETTA UN DATA OBJECT
			 */
			dateTimeValue         : null, 

			/*
			 * Insert the universal time UTC
			 * the number of milliseconds between midnight on January 1, 1970,
			 */
			datetime              : null,

			timeTodayValue        : "00:00:00"
		},    

		dateF:null,
		fxTime:null,
		imgMenu:null,
		menuButton:null,
		menuObj:null,

		currentFormatString:"29:59:59.999",
		currentDefaultValue:"000000000",            

		_setOption: function( key, value ) {
			this.options[ key ] = value;
			switch(key){
			case 'menuListData':
				if(this.menuObj){
					this.menuObj.remove();
					this._addMenu();
				}
				break;
			case 'yearNavigationEnabled':     
				this.changeDateFieldProperties('changeYear', value);                  
				break;
			case 'viewDateField':
				this._addRemoveDateField();
				break;
			case 'viewTimeField':
				this._addRemoveTimeField();
				break;
			case 'enabledDateTime':
				this.enabledDateTime(value);
				break;
			case 'setShowToday':
				if(value==true){
					this._setDateTimeValue(new Date());
				}
				break;
			case 'dateFormatString':
				this.changeDateFieldProperties('dateFormat', value);
				break;
			case 'timeFormatString':
				this._checkFormatString();
				if(this.fxTime)
					this.fxTime.setMask({mask:this.currentFormatString, defaultValue:this.currentDefaultValue});
				break;
			case "aftermsec":
				if(this.options.dateTimeValue){
					//do something
				}
				break;
			case "dateTimeValue":
				this._setDateTimeValue(value);
				break;
			case "datetime":
				this.datetime(value);
				break;
			case "timeTodayValue":
				//
				break;
			}
		},

		/*
		 *This property allows you to set the Time value for "TODAY" menu option.
		 *default is "00:00:00"
		 */
		timeTodayValue:function(value){
			if(arguments.length>0){
				this.options.timeTodayValue=value
			}else
				return this.options.timeTodayValue;
		},


		//"MM/DD/YYYY HH:MM:SS.sss",
		dateTimeFormatString:function(value){
			if( arguments.length>0 ) {
				var arrTmp=value.split(" ");
				this._setOption('dateFormatString',arrTmp[0]);
				this._setOption('timeFormatString',arrTmp[1]);
			}else{
				return this.options.dateFormatString+" "+this.options.timeFormatString;
			}

		},

		_addRemoveDateField:function(){
			var myId=this.element[0].id;
			if(this.options.viewDateField==true && this.dateF==null){
				this.element.append('<input type="text" id="datepicker'+myId+'" tabindex="1" />');
				this.dateF=$( "#datepicker"+myId);

				this.dateF.datepicker({showOn: "button", 
					dateFormat:this.options.dateFormatString,
					changeYear:this.options.yearNavigationEnabled
				});

			}else if(this.options.viewDateField==false && this.dateF!=null){
				this.dateF.remove();
				this.dateF=null;
			}
		},

		_addRemoveTimeField:function(){
			var myId=this.element[0].id;
			if(this.options.viewTimeField && this.fxTime==null){
				this.element.append('<input id="fxtime'+myId+'" type="text" tabindex="2" class="hasTime">');
				this._checkFormatString();
				this.fxTime=$('#fxtime'+myId);
				this.fxTime.setMask({mask:this.currentFormatString, defaultValue:this.currentDefaultValue});
			}else if(this.options.viewTimeField==false && this.fxTime!=null){
				this.fxTime.remove();
				this.fxTime=null;
			}
		},


		// "_" crea un metodo privato 
		_create: function() {
			this.element.addClass('fxDateTime');
			var myId=this.element[0].id;

			this._addRemoveDateField();
			this._addRemoveTimeField();

			if(this.menuButton==null){
				this.element.append('<button id="button'+myId+'" class="fxDateTimeButton"></button>');
				this._addMenu();
				this.menuButton=$( "#button"+myId );
				this.menuButton.button({text: false}).click(function() {
					$("#menu"+myId).menu().show().position({
						my: "left top",
						at: "left bottom",
						of: this
					});           
				});

			}

			if(this.options.dateTimeValue!=null){
				this._setDateTimeValue(this.options.dateTimeValue);
			}else if (this.options.datetime!=null){
				this.datetime(this.options.datetime);
			}else if(this.options.setShowToday==true){
				this._setDateTimeValue(new Date());
			}
		},
		//HH:MM:SS.sss
		_checkFormatString:function(){
			var opt=this.options;
			if(opt.timeFormatString!="HH:MM:SS.sss"){                      
				var arrMask=[];
				var defValue="";
				var mill="";
				if(opt.timeFormatString.find("HH")){
					arrMask.push("29");
					defValue="00"; 
				};

				if(opt.timeFormatString.find("MM")){
					arrMask.push("59");
					defValue+="00"; 
				};
				if(opt.timeFormatString.find("SS")){
					arrMask.push("59");
					defValue+="00"; 
				};
				if(opt.timeFormatString.find("sss")){
					mill=".sss";
					if(aftermsec){

					}
					defValue+="000"; 
				};

				this.currentFormatString = arrMask.join(':')+mill;
				this.currentDefaultValue = defValue;
			}                  
		},

		_addMenu:function(){
			var menuId='menu'+this.element[0].id;
			var menuHTML='<ul id="'+menuId+'">' 
			var opt=this.options;
			$.each(opt.menuListData, function(id,labelValue) {  
				menuHTML+='<li><a href="#">'+labelValue+'</a></li>';
			});

			menuHTML+='</ul>';
			this.element.append(menuHTML);

			this.menuObj=$("#"+menuId);
			this.menuObj.menu({
				blur:function (event,ui){ 
					//$( this).hide();
				},
				select: function(event,ui){
					switch(ui.item.context.textContent){
					case "today":
						$( this ).parent().fxDateTime('enabledDateTime',true);
						var v=new Date();// current date and time
						var timeTodayValue=$( this ).parent().fxDateTime('timeTodayValue');

						if(timeTodayValue!="00:00:00"){
							var timeTodayValue_arr=timeTodayValue.split(':');
							if(timeTodayValue_arr.length==3){
								v.setHours(timeTodayValue_arr[0]);
								v.setSeconds(timeTodayValue_arr[1]);
								v.setMinutes(timeTodayValue_arr[2]);
							}
						}else{
							v.setHours(00);
							v.setSeconds(00);
							v.setMinutes(00);
						}
						v.setMilliseconds(000);
						$( this ).parent().fxDateTime("dateTimeValue", v);
						break;
					case "now":
						$( this ).parent().fxDateTime('enabledDateTime',true);
						$( this ).parent().fxDateTime("dateTimeValue", new Date());
						break;
					case "disabled":
						$( this ).parent().fxDateTime('enabledDateTime',false);
						break;
					case "enabled":
						$( this ).parent().fxDateTime('enabledDateTime',true);
						break;
					}
					$( this).hide();
				}
			}).hide();
		},

		enabledDateTime:function(value){
			if(this.options.enabledDateTime!=value){
				if(value==false){
					if(this.dateF!=null)this.dateF.datepicker( "disable" );
					if(this.fxTime!=null)this.fxTime.attr('disabled',true);
				}else{
					if(this.dateF!=null)this.dateF.datepicker( "enable" );
					if(this.fxTime!=null)this.fxTime.attr('disabled',false);
				}
				this.options.enabledDateTime=value;
			}
		},

		/*
		 * Insert the universal time UTC
		 * the number of milliseconds between midnight on January 1, 1970,
		 */
		datetime:function(timeValue){
			if ( arguments.length>0 ) {
				/*
				 *SET
				 */
				this.options.datetime=timeValue;
				var d=new Date();
				d.setTime(timeValue);
				this._setDateTimeValue(timeValue);
			}else{
				/*
				 *GET
				 */  
				this.options.datetime=this._getDateTimeValue().getTime();  
				return this.options.datetime;
			}
		},


		dateTimeValue:function(newValue){
			if ( arguments.length>0 ) {
				this._setDateTimeValue(newValue);  
			}else{
				this._getDateTimeValue();
				return this.options.datetime;
			}
		},   

		_getDateTimeValue:function()
		{
			if(!this.options.enabledDateTime)return null;

			//TODO DA RIVEDERE 

			if(dateF.datepicker( "getDate" )==null)return null;
			this.dateTimeValue=dateF.datepicker( "getDate" );

			var tmpValue=this.fxTime.val();
			var tmpArr;
			var tmpArr2;
			tmpArr=tmpValue.slit('.');
			tmpArr2=tmpArr[0].split(':');   

			this.options.dateTimeValue.setHours(tmpArr2[0]);
			this.options.dateTimeValue.setMinutes(tmpArr2[1]);
			this.options.dateTimeValue.setSeconds(tmpArr2[2]);

			if(tmpArr.lenght==2){
				if(String(tmpArr[1]).length>3){
					this.options.aftermsec=String(tmpArr[1]).slice(2);
				}
				this.options.dateTimeValue.setMilliseconds(Number(tmpArr[1]).slice(0,2));                
			}else{
				this.options.dateTimeValue.setMilliseconds(000);
			}


			return this.options.dateTimeValue;
		},

		_checkValue:function(value, isMill=false){
			if(isMill){
				var tt=String(value).length;
				if( String(value).length==1) return "00"+String(value);
				if( String(value).length==2) return "0"+String(value);
			}else if(String(value).length==1) return "0"+String(value);
			return value;
		},


		_setStringForFxTime:function(newValue){
			var tmpStr="";
			if(this.options.timeFormatString=="HH:MM:SS.sss"){
				tmpStr=this._checkValue(newValue.getHours())+':'+this._checkValue(newValue.getMinutes())+':'+this._checkValue(newValue.getSeconds())+'.'+this._checkValue(newValue.getMilliseconds(), true);                
			}            
			this.fxTime.val(tmpStr);      
		},
		
		/*
		 *   Restituisce il numero di millisecondi a partire dalla mezzanotte del 1/1/1970.
		 */
		//getTime() setTime()

		_setDateTimeValue:function(newValue){
			this.options.dateTimeValue=newValue;
			this.dateF.datepicker( "setDate", newValue );
			this._setStringForFxTime(newValue);
		},


		changeDateFieldProperties:function(propertiesName,propertiesValue){    
			try {
				this.dateF.datepicker( "option", propertiesName, propertiesValue );
			}
			catch(err){
				trace(err.message)
			};
		}          
	});  
}) ( jQuery );