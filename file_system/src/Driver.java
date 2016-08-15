import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


public class Driver {
	FileSystem fs;
	String fsName = "";
	
	int ocCounter = 0;
	
	public void createFile(String fileName){
		
		if (fileName.length() > 4){
			System.out.println("error");
			return;
		}
		
		if (fs.setEmptySlotWithNewFile(fileName) == false){
			System.out.println("error");
			return;
		}
		System.out.println(fileName + " created");
	}
	
	public void deleteFile(String fileName){
		if (fs.deleteSlotWithFileName(fileName) == -1){
			System.out.println("error");
			return;
		}
		else{
			System.out.println(fileName + " destroyed");
		}
	}
	
	public void openFile(String fileName){
		int index = fs.findSlotWithFileName(fileName);
		
		if(ocCounter == 3 || index == -1){
			System.out.println("error");
			return;
		}
		ocCounter++;
		System.out.println(fileName + " opened " + index);
	}
	
	public void closeFile (int index){
		int exist = fs.closeFileWithIndex(index);
		
		if( exist == -1){
			System.out.println("error");
			return;
		}
		ocCounter--;
		System.out.println(index + " closed ");
	}
	
	public void readFile(int index, int count){
		int fileDescriptorIndex = fs.findFileDescriptor(index);
		if (fileDescriptorIndex == -1){
			System.out.println("error");
			return;
		}
		fs.readFileWithFileDescriptor(fileDescriptorIndex, index, count);
	}
	
	public void writeFile(int index, char character, int count){
		int fileDescriptorIndex = fs.findFileDescriptor(index);
		if (fileDescriptorIndex == -1){
			System.out.println("error");
			return;
		}
		fs.writeFileWithFileDescriptor(fileDescriptorIndex, index, count, character);
	}
	
	public void seakFile(int index, int count){
		int fileDescriptorIndex = fs.findFileDescriptor(index);
		if (fileDescriptorIndex == -1){
			System.out.println("error");
			return;
		}
		fs.seakFileWithDescriptor(fileDescriptorIndex, index, count);
	}
	
	public void initialize(String diskName){
		
		int endIndex = diskName.indexOf('.');
		String newS = diskName.substring(0, endIndex);
		
		//Path path = Paths.get(newS);
		
		if (fsName.equals(diskName)){
			try {
				Path p = Paths.get("/Users/ruben/Documents/workspace/OS_Project2", newS);
				byte[] data = Files.readAllBytes(p);
				fs.makeDiskEqualToData(data);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.out.println("disk restored");
			ocCounter = 0;
		}
		else{
			fs = new FileSystem();
			ocCounter = 0;
			System.out.println("disk initialized");
		}
	}
	
	public void processCommand(String[] command){
		switch(command[0]){
		case "cr":
			createFile(command[1]);
			break;
		case "de":
			deleteFile(command[1]);
			break;
		case "op" :
			openFile(command[1]);
			break;
		case "cl":
			closeFile(Integer.parseInt(command[1]));
			break;
		case "rd":
			readFile(Integer.parseInt(command[1]), Integer.parseInt(command[2]));
			break;
		case "wr" :
			writeFile(Integer.parseInt(command[1]), command[2].charAt(0),Integer.parseInt(command[3]));
			break;
		case "sk":
			seakFile(Integer.parseInt(command[1]), Integer.parseInt(command[2]));
			break;
		case "dr":
			fs.showDir();
			System.out.println("");
			break;
		case "in" :
			if (command.length == 1){
				fs = new FileSystem();
				System.out.println("disk initialized");
				ocCounter = 0;
			}else 
				initialize(command[1]);
			break;
		case "sv":
			int endIndex = command[1].indexOf('.');
			String newS = command[1].substring(0, endIndex);
			fs.save(newS);
			System.out.println("disk saved");
			this.fsName = command[1];
			this.ocCounter = 0;
			break;
		default:
			System.out.println("");
		}
	}
	
	static public void main (String[] argc){
		
		Driver D = new Driver();
		
		try {
			for (String line : Files.readAllLines(Paths.get("input.txt"))){
				String[] command = line.split("\\s+");
				
				D.processCommand(command);

			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.out.println("Error trying to search for the file");
			e.printStackTrace();
		}
	}

}
