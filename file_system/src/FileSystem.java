import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.stream.IntStream;


public class FileSystem {
	ArrayList<PackableMemory> lDisk;
	ArrayList<OFT> openFileTable;
	byte [] empty = {-1, -1, -1, -1};
	ArrayList<String> fileNames;
	
	public FileSystem(){
		lDisk = new ArrayList<PackableMemory>();
		openFileTable = new ArrayList<OFT>();
		
		IntStream.range(0, 64).forEachOrdered( n -> { this.lDisk.add(new PackableMemory(64)); });
		IntStream.range(0, 24).forEachOrdered( n -> { this.openFileTable.add(new OFT()); });
		lDisk.get(0).setBitMap();// hopefuly sets all the bits to 1 in bitmap
		lDisk.get(1).pack(0,0);
		lDisk.get(1).pack(7, 4);
		lDisk.get(0).setMemByteAt(7, (byte) 1);
		fileNames = new ArrayList<String>();
	}
	
	public boolean setEmptySlotWithNewFile(String fileName){
		boolean set = false;
		int dirSlotIndex = 1;
		int currentSize = lDisk.get(1).unpack(0);
		
		for(String name: fileNames){
			if(name.equals(fileName)){
				return false;
			}
		}
		
		do {
			int indexForFileSlots = lDisk.get(1).unpack(dirSlotIndex * 4); // get index from last 1 of the 3 blocks from directory descriptor
			if(indexForFileSlots == -1){
				indexForFileSlots = findFreeBlock();
				if( indexForFileSlots == -1) return false;
			}
			if (checkBitMapAt(indexForFileSlots)){ // check if whole block is good to use for directory slots
				int descriptorIndex = findFreeBlockFileDescriptor();
				if (descriptorIndex == 0)  
					return false;
				setDefaultFileDescriptor(descriptorIndex);
				set = setNewSlotSpaceWithInfoAt(indexForFileSlots, 0, fileName, descriptorIndex);
			}
			else{
				int emptySlotIndex = findEmptySlot(indexForFileSlots);
				if(emptySlotIndex != -1){
					int descriptorIndex = findFreeBlockFileDescriptor();
					if (descriptorIndex == 0)  
						return false;
					setDefaultFileDescriptor(descriptorIndex);
					set = setNewSlotSpaceWithInfoAt(indexForFileSlots, emptySlotIndex, fileName, descriptorIndex);
				}
			}
			
			dirSlotIndex++;
		}while(set != true && dirSlotIndex <= 3);
		lDisk.get(1).pack(currentSize + 1, 0);
		fileNames.add(fileName);
		
		return true;
	}
	
	public boolean checkBitMapAt(int index){
		return lDisk.get(0).getMemByteAt(index) == (byte) 0 ? true : false;
	}
	
	public boolean setNewSlotSpaceWithInfoAt(int indexForFileSlots, int index, String fileName, int fileDescriptorIndex){
		lDisk.get(indexForFileSlots).setBytes(fileName.getBytes(), index * 8); // set up name
		lDisk.get(indexForFileSlots).pack(fileDescriptorIndex, index * 8 + 4); // set up file descriptor location
		return true;
	}
	
	public int findEmptySlot(int indexForFileSlots){
		for(int i = 0; i < 64; i = i + 8){
			if (lDisk.get(indexForFileSlots).unpack(i) == -1 )
				return i/8;
		}
		return -1;
	}
	
	public int findFreeBlock(){
		for(int i = 7; i < 64; ++i){
			if (checkBitMapAt(i))
				return i;
		}
		return -1;
	}
	public int findFreeBlockFileDescriptor(){
		
		for(int i = 1; i < 7; ++i){
			for(int j = 0; j < 4; ++j){
				int fileLength = lDisk.get(i).unpack(j * 16);
				if( fileLength == -1 ){
					return ((i-1) * 4) + j;
				}
			}
		}
		return 0;
	}
	public void setDefaultFileDescriptor(int fileDescriptorIndex){
		int row = fileDescriptorIndex/4 + 1;
		int col = (fileDescriptorIndex % 4) * 16;
		
		lDisk.get(row).pack(0, col);
		for(int i = col + 4; i < col + 16; i = i + 4){
			lDisk.get(row).setBytes(empty, i);
		}
	}
	
	public void deleteFileDescriptor(int fileDescriptorIndex){
		int row = fileDescriptorIndex/4 + 1;
		int col = (fileDescriptorIndex % 4) * 16;
		
		lDisk.get(row).pack(-1, col);// sets 4 bits to -1?
		for(int i = col + 4; i < col + 16; i = i + 4){
			int blockIndex = lDisk.get(row).unpack(i);
			if (blockIndex != -1){
				lDisk.get(blockIndex).setDefaultBlock();
				lDisk.get(0).setMemByteAt(blockIndex, (byte) 0);
				lDisk.get(row).setBytes(empty, i);
			}
		}
	}
	
	public int deleteSlotWithFileName(String fileName){
		int currentSize = lDisk.get(1).unpack(0);
		//System.out.println(lDisk.get(7).unpackName(0));
		
		if(this.fileNames.contains(fileName) == false)
			return -1;
		for(int i = 4; i < 16; i = i + 4){
			int filesBlockIndex = lDisk.get(1).unpack(i);
			for(int j = 0; j < 64; j = j + 8){
				if(lDisk.get(filesBlockIndex).unpackName(j).equals(fileName)){
					int fileDescriptorIndex = lDisk.get(filesBlockIndex).unpack(j + 4);
					eraseBlockFromOFTWithFD(fileDescriptorIndex);
					lDisk.get(filesBlockIndex).removeDirSlot(j);
					deleteFileDescriptor(fileDescriptorIndex);
					fileNames.remove(fileName);
					lDisk.get(1).pack(currentSize - 1, 0);
					return 1;
				}
			}
		}
		return -1;
	}
	
	public void eraseBlockFromOFTWithFD(int fileDescriptorIndex){
		for(int i = 0; i < openFileTable.size(); ++i){
			if(openFileTable.get(i).getFileDescriptorIndex() == fileDescriptorIndex){
				openFileTable.get(i).setFree();
				break;
			}
		}
	}
	
	public int openFile(int fileDescriptor){
		int stop = 1;
		int row = fileDescriptor/4 + 1;
		int col = (fileDescriptor % 4) * 16;
		OFT temp = null;
		
		for(int i = 0 ; i < openFileTable.size(); i++){
			if (openFileTable.get(i).getFileDescriptorIndex() == fileDescriptor)
				return -1;
		}
		
		while(openFileTable.get(stop).getFileDescriptorIndex() != -1) ++stop;
		int fileLength = lDisk.get(row).unpack(col);
		if(fileLength == 0)
			temp = new OFT(fileDescriptor);
		else
			temp = new OFT(fileDescriptor, fileLength, lDisk.get(lDisk.get(row).unpack(col + 4)));
		openFileTable.set(stop, temp );
		
		return stop;
	}
	
	public int findSlotWithFileName(String fileName){
		if(this.fileNames.contains(fileName) == false)
			return -1;

		for(int i = 4; i < 16; i = i + 4){
			int filesBlockIndex = lDisk.get(1).unpack(i);
			for(int j = 0; j < 64; j = j + 8){
				if(lDisk.get(filesBlockIndex).unpackName(j).equals(fileName)){
					int fileDescriptorIndex = lDisk.get(filesBlockIndex).unpack(j + 4);
					
					return openFile(fileDescriptorIndex);
				}
			}
		}
		
		return -1;
	}
	
	public int closeFileWithIndex(int index){
		int currentPosition = openFileTable.get(index).getCurrentPosition();
		int fileDescriptorIndex = openFileTable.get(index).getFileDescriptorIndex();
		int row = fileDescriptorIndex/4 + 1;
		int col = (fileDescriptorIndex % 4) * 16;
		int newIndex = 0;
		
		if (openFileTable.get(index).getPackableMemory() != null){
			if (currentPosition <= 64)
				newIndex = 1;
			else if (currentPosition > 64 && currentPosition <= 128)
				newIndex = 2;
			else
				newIndex = 3;
			lDisk.get(lDisk.get(row).unpack(col + (newIndex * 4))).setBytes(openFileTable.get(index).getPackableMemory().getMem(), 0);
			lDisk.get(row).pack(openFileTable.get(index).getLength(), col);
			openFileTable.get(index).setFree();
			return 1;
		}
		
		return -1;
	}
		
	public int findFileDescriptor(int index){
		return openFileTable.get(index).getFileDescriptorIndex();
	}
	
	public void readFileWithFileDescriptor(int fileDescriptorIndex,int OFTindex, int bytesToRead){
		int row = fileDescriptorIndex/4 + 1;
		int col = (fileDescriptorIndex % 4) * 16;
		int fileLength = lDisk.get(row).unpack(col);
		if(fileLength == 0){
			System.out.println("0 bytes read");
			return;
		}

		int endIndex = openFileTable.get(OFTindex).getCurrentPosition() + bytesToRead;
		int startingBlock = openFileTable.get(OFTindex).getCurrentPosition() / 64; // 0,1,2
		
		for(int i = startingBlock; i < 3; ++i){
			int blockIndex = lDisk.get(row).unpack(col + ((i + 1) * 4));
			int generalStartIndex = openFileTable.get(OFTindex).getCurrentPosition() % 64;// 0...63
			if(blockIndex == -1)
				break;
			openFileTable.get(OFTindex).setBuffer(lDisk.get(blockIndex));
			openFileTable.get(OFTindex).setCurrentPosition(readWhileIncreasingCounter(OFTindex, i, generalStartIndex, endIndex));
			if(openFileTable.get(OFTindex).getCurrentPosition() == endIndex)
				break;
		}
		System.out.println("");
	}
	
	public int readWhileIncreasingCounter(int OFTindex, int startingBlock, int generalStartIndex, int endIndex){
		int counter = openFileTable.get(OFTindex).getCurrentPosition();
		
		for(int i = generalStartIndex; i < 64; ++i, ++counter){
			if(counter == endIndex || counter == openFileTable.get(OFTindex).getLength())
				return endIndex;
			System.out.print((char)openFileTable.get(OFTindex).getPackableMemory().getMemByteAt(i));
		}
		
		return counter;
	}
	
	public void writeFileWithFileDescriptor(int fileDescriptorIndex,int OFTindex, int bytesToWrite, char character){
		int row = fileDescriptorIndex/4 + 1;
		int col = (fileDescriptorIndex % 4) * 16;
		int initialPosition = openFileTable.get(OFTindex).getCurrentPosition();
		int endIndex = openFileTable.get(OFTindex).getCurrentPosition() + bytesToWrite;
		int startingBlock = openFileTable.get(OFTindex).getCurrentPosition() / 64; // 0,1,2
		
		for(int i = startingBlock; i < 3; ++i){
			int blockIndex = lDisk.get(row).unpack(col + ((i + 1) * 4));
			if (blockIndex == -1){
				blockIndex = findFreeBlock();
				if(blockIndex == -1){
					System.out.println("0 bytes written");
					return;
				}
				lDisk.get(0).setMemByteAt(blockIndex, (byte) 1);
				lDisk.get(row).pack(blockIndex, col + ((i + 1) * 4));
			}
			int generalStartIndex = openFileTable.get(OFTindex).getCurrentPosition() % 64;// 0...63
			openFileTable.get(OFTindex).setBuffer(lDisk.get(blockIndex));
			openFileTable.get(OFTindex).setCurrentPosition(writeWhileIncreasingCounter(OFTindex, i, generalStartIndex, endIndex, character));
			//lDisk.get(blockIndex).setBytes(openFileTable.get(OFTindex).getPackableMemory().getMem(), generalStartIndex);
			if(openFileTable.get(OFTindex).getCurrentPosition() == endIndex){
				lDisk.get(row).pack(endIndex, col);
				break;
			}
			else if (openFileTable.get(OFTindex).getCurrentPosition() > 191){
				lDisk.get(row).pack(191, col);
				break;
			}
//			else{
//				blockIndex = findFreeBlock();
//				if(blockIndex == -1){
//					break;
//				}
//				if(i + 1 != 3){
//					lDisk.get(0).setMemByteAt(blockIndex, (byte) 1);
//					lDisk.get(row).pack(blockIndex, col + ((i + 2) * 4));
//				}
//			}
		}
		
		if (openFileTable.get(OFTindex).getCurrentPosition() > openFileTable.get(OFTindex).getLength()){
			openFileTable.get(OFTindex).setLength(openFileTable.get(OFTindex).getCurrentPosition()); 
		}
		
		System.out.println(openFileTable.get(OFTindex).getCurrentPosition() - initialPosition + " bytes written");
	}
	
	public int writeWhileIncreasingCounter(int OFTindex, int startingBlock, int generalStartIndex, int endIndex, char character){
		int counter = openFileTable.get(OFTindex).getCurrentPosition();
		
		for(int i = generalStartIndex; i < 64; ++i, ++counter){
			if(counter == endIndex)
				return endIndex;
			openFileTable.get(OFTindex).getPackableMemory().setMemByteAt(i,(byte) character);
		}
		
		return counter;
	}
	
	public void seakFileWithDescriptor(int fileDescriptorIndex, int OFTindex, int position){
		int row = fileDescriptorIndex/4 + 1;
		int col = (fileDescriptorIndex % 4) * 16;
		int fileLength = lDisk.get(row).unpack(col);
		int initialPosition = openFileTable.get(OFTindex).getCurrentPosition();
		
		if(fileLength < position){
			System.out.println("error");
			return;
		}
		if(fileLength == 0){
			System.out.println("position is 0");
			return;
		}
		
		if ((position < 64 && initialPosition < 64) || 
			((position < 128 && position > 63) && (initialPosition < 128 && initialPosition > 63)) ||
			((position < 192 && position > 127) && (initialPosition < 192 && initialPosition > 127))){
			openFileTable.get(OFTindex).setCurrentPosition(position);
		}
		else{
			if(position < 64){
				openFileTable.get(OFTindex).setBuffer(lDisk.get(lDisk.get(row).unpack(col + 4)));
			}
			else if(position < 128 && position > 63){
				openFileTable.get(OFTindex).setBuffer(lDisk.get(lDisk.get(row).unpack(col + 8)));
			}
			else if(position < 192 && position > 127){
				openFileTable.get(OFTindex).setBuffer(lDisk.get(lDisk.get(row).unpack(col + 12)));
			}
		}
		openFileTable.get(OFTindex).setCurrentPosition(position);
		
		System.out.println("position is " + position);
	}
	
	public int seakWhileIncreasingCounter(int OFTindex, int startingBlock, int generalStartIndex, int endIndex){
		int counter = openFileTable.get(OFTindex).getCurrentPosition();
		
		for(int i = generalStartIndex; i < 64; ++i, ++counter){
			if(counter == endIndex)
				return endIndex;
			System.out.print(openFileTable.get(OFTindex).getPackableMemory().getMemByteAt(i));
		}
		
		return counter;
	}
	
	public void showDir(){
		int counter = 0;
		int filesCount = lDisk.get(1).unpack(0);
		
		for(int i = 1; i < 4; i++){
			int filesBlock = lDisk.get(1).unpack(i * 4);
			
			for(int j = 0; j < 64; j = j + 8){
				if(lDisk.get(filesBlock).getMemByteAt(j) != (byte) -1){
					System.out.print(lDisk.get(filesBlock).unpackName(j) + " ");
					//System.out.print(" ");
					if(++counter == filesCount)
						//System.out.println("");
						return;
				}
			}
		}
	}
	
	public void save(String fileName){
		for(int i = 1; i < openFileTable.size(); ++i ){
			closeFileWithIndex(i);
		}
		byte[] container = new byte[64 * 64];
		
		for(int i = 0; i < 64; ++i){
			copyMemFrom(container, lDisk.get(i).getMem(), i * 64) ;	
		}
		
		Path file = Paths.get(fileName);
		try {
			Files.write(file, container);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void copyMemFrom(byte[] container, byte[] bytes, int i){
		   for(byte b : bytes){
			   container[i++] = b;
		   }
	}
	
	public void makeDiskEqualToData(byte[] data){
		for(int i = 0; i < 64; i++){
			for(int j = i * 64, counter = 0; j < (i*64) + 64; j++, counter++){
				lDisk.get(i).setMemByteAt(counter, data[j]);
			}
		}
		
	}
}
