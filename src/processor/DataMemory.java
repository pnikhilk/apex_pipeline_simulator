package processor;

public class DataMemory {
	private int[] memory = new int[Constants.MAX_MEM+1];

	public int getMemory(int index) {
		return memory[index];
	}
	public void init(){
		for (int i = Constants.MIN_MEM; i < Constants.MAX_MEM; i++){
			memory[i] = 0;
		}
	}

	public void setMemory(int index, int value) {
		this.memory[index] = value;
	}
}
