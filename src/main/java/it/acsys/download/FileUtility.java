package it.acsys.download;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class FileUtility {
	
	
	public static void mergeDirectory(File source, File destination) throws IOException {
		if(!destination.exists()) {
			Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} else {
			File[] files = source.listFiles();
			for(int n=0; n<files.length; n++) {
				Files.move(files[n].toPath(), FileSystems.getDefault().getPath(destination.getAbsolutePath(), files[n].getName()), StandardCopyOption.REPLACE_EXISTING);
			}
		}
    	
	}
	
	public static void delete(File file) throws IOException {
		if(!file.exists()) {
			return;
		}
    	if(file.isDirectory()){
 
    		//directory is empty, then delete it
    		if(file.list().length==0){
 
    		   file.delete();
 
    		}else{
 
    		   //list all the directory contents
        	   String files[] = file.list();
 
        	   for (String temp : files) {
        	      //construct the file structure
        	      File fileDelete = new File(file, temp);
 
        	      //recursive delete
        	     delete(fileDelete);
        	   }
 
        	   //check the directory again, if empty then delete it
        	   if(file.list().length==0){
           	     file.delete();
        	   }
    		}
 
    	}else{
    		//if file, then delete it
    		file.delete();
    	}
	}

}
