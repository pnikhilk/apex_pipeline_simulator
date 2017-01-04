package processor;

public class Instruction {
	private int PC;
	private int cycle;
	private String type;
	private String opCode;
	private Register rDest, rSrc1, rSrc2;
	private int literal;
	private boolean zeroFlag, isDependency, src1Dependency, src2Dependency;

	public Instruction(String opCode){
		rDest = new Register();
		rSrc1 = new Register();
		rSrc2 = new Register();
		this.opCode = opCode;
		setType(opCode);
	}

	public Instruction(Instruction instruction) {
		if(instruction != null){
			this.setPC(instruction.getPC());
			this.cycle = instruction.getCycle();
			this.literal = instruction.getLiteral();
			this.type =instruction.getType();
			this.opCode = instruction.getOpCode();
			this.rDest = new Register(instruction.getrDest().getRegName(),instruction.getrDest().getValue(), instruction.getrDest().getUpdateCycle(), instruction.getrDest().getPc());
			this.rSrc1 = new Register(instruction.getrSrc1().getRegName(),instruction.getrSrc1().getValue(), instruction.getrSrc1().getUpdateCycle(), instruction.getrSrc1().getPc());
			this.rSrc2 = new Register(instruction.getrSrc2().getRegName(),instruction.getrSrc2().getValue(), instruction.getrSrc2().getUpdateCycle(), instruction.getrSrc2().getPc());
			this.zeroFlag = instruction.isZeroFlag();
			this.isDependency = instruction.isDependency();
			this.src1Dependency = instruction.isSrc1Dependency();
			this.src2Dependency = instruction.isSrc2Dependency();
		}
	}

	public int getCycle() {
		return cycle;
	}

	public String getInstructionInfo(){
		StringBuilder sb = new StringBuilder("PC = ");
		sb.append(getPC());
		sb.append(" : " + getOpCode());

		if(getrDest().getRegName() != null){
			sb.append(" " + getrDest().getRegName() + "(" + getrDest().getValue() + ")");
		}
		if(getrSrc1().getRegName() != null){
			sb.append(", " + getrSrc1().getRegName() + "(" + getrSrc1().getValue() + ")");
		}
		if(getrSrc2().getRegName() != null){
			sb.append(", " + getrSrc2().getRegName() + "(" + getrSrc2().getValue() + ")");
		}

		return sb.toString();
	}

	public int getLiteral() {
		return literal;
	}

	public String getOpCode() {
		return opCode;
	}

	public int getPC() {
		return PC;
	}

	public Register getrDest() {
		return rDest;
	}

	public Register getrSrc1() {
		return rSrc1;
	}

	public Register getrSrc2() {
		return rSrc2;
	}

	public String getType() {
		return type;
	}

	public boolean isDependency() {
		return isDependency;
	}

	public boolean isRegister(String reg){
		if(reg.startsWith("R")){
			return true;
		}
		return false;
	}

	public boolean isSrc1Dependency() {
		return src1Dependency;
	}

	public boolean isSrc2Dependency() {
		return src2Dependency;
	}

	public boolean isZeroFlag() {
		return zeroFlag;
	}

	public void setCycle(int cycle) {
		this.cycle = cycle;
	}

	public void setDependency(boolean isDependency) {
		this.isDependency = isDependency;
	}

	public void setLiteral(int literal) {
		this.literal = literal;
	}

	public void setOpCode(String opCode) {
		this.opCode = opCode;
	}

	public void setPC(int pC) {
		PC = pC;
	}

	public void setrDest(Register rDest) {

		this.rDest = rDest;
	}

	public void setrSrc1(Register rSrc1) {
		this.rSrc1 = rSrc1;
	}

	public void setrSrc2(Register rSrc2) {
		this.rSrc2 = rSrc2;
	}

	public void setSrc1Dependency(boolean src1Dependency) {
		this.src1Dependency = src1Dependency;
	}

	public void setSrc2Dependency(boolean src2Dependency) {
		this.src2Dependency = src2Dependency;
	}

	public void setType(String opCode) {
		switch(opCode){
		case Constants.INST_ADD:
		case Constants.INST_SUB:
		case Constants.INST_MOVC:
		case Constants.INST_AND:
		case Constants.INST_OR:
		case Constants.INST_EXOR:
			this.type = Constants.TYPE_RTR;
			break;
		case Constants.INST_MUL:
			this.type = Constants.TYPE_MUL;
			break;
		case Constants.INST_LOAD:
		case Constants.INST_STORE:
			this.type = Constants.TYPE_MEM;
			break;
		case Constants.INST_BZ:
		case Constants.INST_BNZ:
		case Constants.INST_JUMP:
		case Constants.INST_BAL:
		case Constants.INST_HALT:
			this.type = Constants.TYPE_BR;
			break;
		case Constants.NOP:
			this.type = Constants.NOP;
			break;
		default:
			throw new IllegalArgumentException("Instruction type is invalid..."+  opCode);
		}
	}

	public void setZeroFlag(boolean zeroFlag) {
		this.zeroFlag = zeroFlag;
	}
}
