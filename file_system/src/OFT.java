
public class OFT {
	int currentPosition;
	int fileDescriptorIndex;
	PackableMemory buffer;
	int length;
	
	public OFT(){
		currentPosition = -1;
		fileDescriptorIndex = -1;
		buffer = null;
		length = -1;
	}
	public OFT(int fileDescriptorIndex){
		currentPosition = 0;
		this.fileDescriptorIndex = fileDescriptorIndex;
		this.buffer = null;
		this.length = 0;
	}
	public OFT(int fileDescriptorIndex, int length, PackableMemory buffer){
		currentPosition = 0;
		this.fileDescriptorIndex = fileDescriptorIndex;
		this.buffer = buffer;
		this.length = length;
	}
	public void setBuffer(PackableMemory buffer){
		this.buffer = buffer;
	}
	public int getFileDescriptorIndex(){
		return fileDescriptorIndex;
	}
	public int getCurrentPosition(){
		return currentPosition;
	}
	public PackableMemory getPackableMemory(){
		return buffer;
	}
	public void setCurrentPosition(int currentPosition){
		this.currentPosition = currentPosition;
	}
	public void setLength(int length){
		this.length = length;
	}
	public int getLength(){
		return this.length;
	}
	public void setFree(){
		currentPosition = -1;
		fileDescriptorIndex = -1;
		buffer = null;
		length = -1;
	}
}
