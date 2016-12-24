package processor;

import java.util.LinkedHashMap;

public class RegisterFile{
	int archSize;
	int phySize;
	
	public RegisterFile(){
		archSize = Constants.ARCH_REG;
		phySize = Constants.PHY_REG;
	}
	
	public RegisterFile(int size) {
		// TODO Auto-generated constructor stub
		archSize = Constants.ARCH_REG;
		phySize = size - (archSize + 1);
		
	}
	LinkedHashMap<String, Register> ARF;
	
	LinkedHashMap<String, Register> PRF;
	
	public LinkedHashMap<String, Register> getRegisterFile() {
		return ARF;
	}

	public void init(){
		ARF = new LinkedHashMap<String, Register>();
		for (int i = 0; i < archSize; i++){
			ARF.put("R" + i, new Register("R" + i,0, 0,0));
		}
		ARF.put("X", new Register("X", 0, 0,0));
		
		PRF = new LinkedHashMap<String, Register>();
		for (int i = 0; i < phySize; i++){
			PRF.put("P" +i, new Register("P" + i,0, 0,0));
		}
	}


	public LinkedHashMap<String, Register> getARF() {
		return ARF;
	}

	public void setARF(LinkedHashMap<String, Register> aRF) {
		ARF = aRF;
	}

	public LinkedHashMap<String, Register> getPRF() {
		return PRF;
	}

	public void setPRF(LinkedHashMap<String, Register> pRF) {
		PRF = pRF;
	}

}
