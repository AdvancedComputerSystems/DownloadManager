$.widget('blueimp.fileupload', $.blueimp.fileupload, {
    options: {
        /*
        *option are FxUploader and FxButtonUploader
        */
        fileUploadInterfaceType:"FxUploader",
        resetInterface:function(){
            var that = $(this).data('fileupload');
            if(that.options.fileUploadInterfaceType=="FxUploader"){
                $('#TA'+this.id).val("");
                $('#SNF'+this.id).empty().append("Size: 0 KB. 0 File");  
            }
        },
        add:function (e, data) {
            var textValue='';
            var numFile=0;
            var sizeFile=0;
            var errorString="";
            var that = $(this).data('fileupload');
            $.each(data.files, function (index, file) {
                textValue+= file.name +"\n";
                numFile=index+1;
                sizeFile= sizeFile+file.size;
                file.error=that._hasError(file);
                if(file.error){
                   errorString+=file.error+"\n";    
                }
             });
             /*
             *
             */
             if(errorString!=""){
                data.errorThrown=errorString;
                that._trigger('fail', e, data);
             }else{
               if(that.options.fileUploadInterfaceType=="FxUploader"){
                  var fxUId=this.id;
                  $('#TA'+fxUId).val(textValue);
                  $('#SNF'+fxUId).empty().append("Size: "+ Math.floor(sizeFile/1024) +" KB. "+numFile+" File");
                  $("#BTN"+fxUId).click(function () {                                                        
                     data.submit();
                     that._trigger('resetInterface');  
                  });         
               }else{
                 data.submit();
               }  
             }         
           },
        failed:function(e, data) {
               alert(data.errorThrown);
        },
        done: function (e, data) {
                alert('ok');
       }
      
    }
});