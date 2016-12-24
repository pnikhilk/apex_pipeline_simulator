package processor;

public class Register{
	private String regName;
	private int value;
	private int updateCycle;
	private int pc;
	private boolean allocated = false;
	private boolean valid = false;
	private boolean zeroFlag = true;

	public Register(){

	}

	public Register(String regName, int value, int updateCycle, int pc) {
		setRegName(regName);
		setValue(value);
		setUpdateCycle(updateCycle);
		setPc(pc);
	}

	public String getRegName() {
		return regName;
	}

	public int getUpdateCycle() {
		return updateCycle;
	}

	public int getValue() {
		return value;
	}

	public void setRegName(String regName) {
		this.regName = regName;
	}

	public void setUpdateCycle(int updateCycle) {
		this.updateCycle = updateCycle;
	}

	public void setValue(int value) {
		this.value = value;
	}

	public int getPc() {
		return pc;
	}

	public void setPc(int pc) {
		this.pc = pc;
	}

	public boolean isAllocated() {
		return allocated;
	}

	public void setAllocated(boolean allocated) {
		this.allocated = allocated;
	}

	public boolean isValid() {
		return valid;
	}

	public void setValid(boolean valid) {
		this.valid = valid;
	}

	public boolean isZeroFlag() {
		return zeroFlag;
	}

	public void setZeroFlag(boolean zeroFlag) {
		this.zeroFlag = zeroFlag;
	}

}
